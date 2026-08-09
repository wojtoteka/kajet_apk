package wojtoteka.ovh.kajet.cloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import wojtoteka.ovh.kajet.core.text.conflictsNoted
import wojtoteka.ovh.kajet.core.text.signedInAs
import wojtoteka.ovh.kajet.core.text.syncSummary
import wojtoteka.ovh.kajet.core.text.words
import kotlinx.coroutines.launch

class AccountViewModel(
    private val context: Context,
    private val account: AccountStore,
    private val client: CloudClient,
    private val sync: Sync,
    private val queue: SendQueue,
) : ViewModel() {

    val accountState: StateFlow<SignInState> = account.state
    val syncState: StateFlow<SyncState> = sync.state

    /** Notatki, które wyczerpały próby wysyłki i czekają na ponowienie. */
    val stuckNotes: StateFlow<Int> = sync.stuck

    /** Nowa pula prób dla utkniętych i natychmiastowa synchronizacja. */
    fun retryStuck() {
        sync.retryStuck()
    }

    /**
     * Wycofanie zgody na wysyłanie treści notatek do Google.
     *
     * Serwer przy okazji kasuje rozmowy z asystentem - nie ma w nich treści
     * notatek, ale są polecenia, które człowiek pisał, a skoro wycofuje zgodę
     * na całą funkcję, zostawianie po niej zapisków byłoby dziwne.
     */
    fun withdrawAiConsent() {
        viewModelScope.launch {
            val done = runCatching { client.aiSetConsent(false) }.getOrNull()
            if (done is CloudClient.Result.Ok) {
                account.rememberAiConsent(false)
                _message.value = words.aiConsentWithdrawn
            } else {
                _message.value = words.serverUnreachable
            }
        }
    }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _waitingForBrowser = MutableStateFlow(false)
    val waitingForBrowser: StateFlow<Boolean> = _waitingForBrowser.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _waitingInQueue = MutableStateFlow(queue.size())
    val waitingInQueue: StateFlow<Int> = _waitingInQueue.asStateFlow()

    private var pollJob: Job? = null
    private var activeChallengeCode: String? = null

    private val deviceName: String
        get() = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { words.deviceFallbackName }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _error.value = words.giveEmailAndPassword
            return
        }

        viewModelScope.launch {
            _busy.value = true
            _error.value = null

            val outcome = runCatching { client.signIn(email.trim(), password, deviceName) }
                .getOrElse { failure ->
                    CloudClient.Result.Error(
                        message = failure.message ?: words.serverUnreachable,
                        worthRetrying = true,
                    )
                }

            when (val result = outcome) {
                is CloudClient.Result.Ok -> onSignedIn(result.data)
                is CloudClient.Result.Error -> _error.value = result.message
            }

            _busy.value = false
        }
    }

    /**
     * Opens kajet.wojtoteka.ovh in Custom Tabs so the user can sign in with
     * Google (or password) and approve this device. The app polls until a
     * token is ready — more reliable than relying only on the deep link return.
     */
    fun signInWithBrowser() {
        cancelBrowserSignIn(clearMessage = false)

        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            _message.value = null

            val outcome = runCatching { client.createDeviceChallenge(deviceName) }
                .getOrElse { failure ->
                    CloudClient.Result.Error(
                        message = failure.message ?: words.serverUnreachable,
                        worthRetrying = true,
                    )
                }

            when (val result = outcome) {
                is CloudClient.Result.Ok -> {
                    activeChallengeCode = result.data.code
                    openCustomTab(result.data.verificationUri)
                    _waitingForBrowser.value = true
                    _message.value =
                        words.approveOnSite
                    _busy.value = false
                    startPolling(
                        code = result.data.code,
                        intervalSeconds = result.data.interval.coerceIn(1, 10),
                        expiresInSeconds = result.data.expiresIn.coerceIn(60, 30 * 60),
                    )
                }

                is CloudClient.Result.Error -> {
                    _error.value = result.message
                    _busy.value = false
                }
            }
        }
    }

    /** Deep link `kajet://auth?code=…` — resume or accelerate polling. */
    fun onAuthDeepLink(uri: Uri?) {
        val code = uri?.getQueryParameter("code")?.trim().orEmpty()
        if (code.isBlank()) return

        if (_waitingForBrowser.value && activeChallengeCode == code) {
            // Already polling this challenge; just bring focus back.
            _message.value = words.backFromBrowser
            return
        }

        cancelBrowserSignIn(clearMessage = false)
        activeChallengeCode = code
        _waitingForBrowser.value = true
        _message.value = words.checkingApproval
        startPolling(code = code, intervalSeconds = 2, expiresInSeconds = 10 * 60)
    }

    fun cancelBrowserSignIn(clearMessage: Boolean = true) {
        pollJob?.cancel()
        pollJob = null
        activeChallengeCode = null
        _waitingForBrowser.value = false
        if (clearMessage) {
            _message.value = null
            _busy.value = false
        }
    }

    fun signInWithToken(token: String) {
        if (token.isBlank()) {
            _error.value = words.pasteTokenFromSite
            return
        }

        viewModelScope.launch {
            _busy.value = true
            _error.value = null

            // We save the token straight away, because checking it requires
            // sending it in a header anyway. Should it turn out to be wrong, we
            // take it back off at once.
            account.save(
                SignInResponse(
                    token = token.trim(),
                    tokenId = "",
                    account = AccountData(id = "", login = "…", email = ""),
                    storage = Storage(),
                ),
            )

            val outcome = runCatching { client.accountState() }.getOrElse { failure ->
                CloudClient.Result.Error(
                    message = failure.message ?: words.serverUnreachable,
                    worthRetrying = true,
                )
            }

            when (val result = outcome) {
                is CloudClient.Result.Ok -> {
                    account.refresh(result.data)
                    _message.value = words.signedInAs(result.data.account.login)
                    SyncWork.scheduleNow(context)
                    SyncWork.schedulePeriodic(context)
                }

                is CloudClient.Result.Error -> {
                    account.signOut()
                    _error.value = result.message
                }
            }

            _busy.value = false
        }
    }

    /**
     * Odświeża stan konta prosto z serwera. Wołane przy wejściu na ekran:
     * dzięki temu zajęte miejsce jest aktualne.
     *
     * Martwy token gasi sesję sam, w [CloudClient] — tutaj zostaje wyłącznie
     * powiedzenie o tym na głos. Kolejka wysyłki zostaje, żeby po ponownym
     * zalogowaniu zaległe zmiany dojechały.
     */
    fun refreshFromServer() {
        if (!account.isSignedIn()) return
        viewModelScope.launch {
            val outcome = runCatching { client.accountState() }.getOrNull() ?: return@launch
            when (outcome) {
                is CloudClient.Result.Ok -> account.refresh(outcome.data)
                is CloudClient.Result.Error -> if (outcome.mustSignIn) {
                    _error.value = words.sessionExpiredServer
                }
            }
            refreshState()
        }
    }

    fun signOut() {
        cancelBrowserSignIn()
        account.signOut()
        sync.forgetAllVersions()
        queue.clear()
        SyncWork.stop(context)
        _message.value = words.signedOutNotesStay
        refreshState()
    }

    fun synchroniseNow() {
        // Zajęty znaczy zajęty: kolejne stuknięcia w przycisk nie mają prawa
        // ustawiać kolejnych synchronizacji w ogonku. To one potrafiły ubić
        // aplikację do czarnego ekranu przy spamowaniu przycisku.
        if (_busy.value) return

        viewModelScope.launch {
            _busy.value = true
            _error.value = null

            // Awaria wysyłki nie może zabrać ze sobą aplikacji. Bez tego jeden
            // błąd z serwera zamykał Kajet i zostawał czarny ekran.
            //
            // Synchronizacja chodzi we własnym zakresie Sync, nie w zakresie
            // tego ekranu: wyjście z ekranu w trakcie już jej nie przerywa
            // (przerwana w pół kroku mnożyła kopie „kopia z chmury").
            val result = runCatching { sync.synchroniseInBackground().await() }.getOrElse { failure ->
                SyncResult(
                    reason = failure.message
                        ?: words.syncFailedSafe,
                    worthRetrying = true,
                )
            }

            _message.value = when {
                result.reason != null -> null
                result.sent == 0 && result.fetched == 0 -> words.alreadyInSync
                else -> buildString {
                    append(words.syncSummary(result.sent, result.fetched))
                    if (result.conflicts > 0) {
                        append(words.conflictsNoted(result.conflicts))
                    }
                }.trim()
            }
            _error.value = result.reason

            refreshState()
            _busy.value = false
        }
    }

    fun hideMessage() {
        _message.value = null
        _error.value = null
    }

    private fun startPolling(code: String, intervalSeconds: Int, expiresInSeconds: Int) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            val deadline = System.currentTimeMillis() + expiresInSeconds * 1000L
            val delayMs = intervalSeconds * 1000L

            while (isActive && System.currentTimeMillis() < deadline) {
                delay(delayMs)

                val outcome = runCatching { client.pollDeviceChallenge(code) }
                    .getOrElse { failure ->
                        CloudClient.Result.Error(
                            message = failure.message ?: words.serverUnreachable,
                            worthRetrying = true,
                        )
                    }

                when (val result = outcome) {
                    is CloudClient.Result.Ok -> {
                        when (result.data.status) {
                            "pending" -> continue
                            "ready" -> {
                                val signedIn = result.data.toSignIn()
                                if (signedIn != null) {
                                    cancelBrowserSignIn(clearMessage = false)
                                    onSignedIn(signedIn)
                                    return@launch
                                }
                                _error.value = words.incompleteSignInAnswer
                                cancelBrowserSignIn()
                                return@launch
                            }
                            else -> continue
                        }
                    }

                    is CloudClient.Result.Error -> {
                        if (result.worthRetrying) continue
                        _error.value = result.message
                        cancelBrowserSignIn(clearMessage = false)
                        _message.value = null
                        return@launch
                    }
                }
            }

            if (isActive) {
                _error.value = words.approvalTimedOut
                cancelBrowserSignIn(clearMessage = false)
                _message.value = null
            }
        }
    }

    private fun onSignedIn(response: SignInResponse) {
        account.save(response)
        _message.value = words.signedInAs(response.account.login)
        SyncWork.scheduleNow(context)
        SyncWork.schedulePeriodic(context)
        refreshState()
    }

    private fun openCustomTab(url: String) {
        val uri = Uri.parse(url)
        try {
            val tabs = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            tabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            tabs.launchUrl(context, uri)
        } catch (_: Exception) {
            // Fallback when no browser supports Custom Tabs.
            val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private fun refreshState() {
        _waitingInQueue.value = queue.size()
    }

    class Factory(
        private val context: Context,
        private val account: AccountStore,
        private val client: CloudClient,
        private val sync: Sync,
        private val queue: SendQueue,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AccountViewModel(context, account, client, sync, queue) as T
    }
}
