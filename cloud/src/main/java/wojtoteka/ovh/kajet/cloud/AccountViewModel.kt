package wojtoteka.ovh.kajet.cloud

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _waitingInQueue = MutableStateFlow(queue.size())
    val waitingInQueue: StateFlow<Int> = _waitingInQueue.asStateFlow()

    private val deviceName: String
        get() = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Tablet" }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _error.value = "Podaj adres e-mail i hasło."
            return
        }

        viewModelScope.launch {
            _busy.value = true
            _error.value = null

            val outcome = runCatching { client.signIn(email.trim(), password, deviceName) }
                .getOrElse { failure ->
                    CloudClient.Result.Error(
                        message = failure.message ?: "Nie udało się połączyć z serwerem.",
                        worthRetrying = true,
                    )
                }

            when (val result = outcome) {
                is CloudClient.Result.Ok -> {
                    account.save(result.data)
                    _message.value = "Zalogowano jako ${result.data.account.login}."

                    // The first sync will fetch whatever already sits in the cloud.
                    SyncWork.scheduleNow(context)
                    SyncWork.schedulePeriodic(context)
                    refreshState()
                }

                is CloudClient.Result.Error -> _error.value = result.message
            }

            _busy.value = false
        }
    }

    fun signInWithToken(token: String) {
        if (token.isBlank()) {
            _error.value = "Wklej token ze strony."
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
                    message = failure.message ?: "Nie udało się połączyć z serwerem.",
                    worthRetrying = true,
                )
            }

            when (val result = outcome) {
                is CloudClient.Result.Ok -> {
                    account.refresh(result.data)
                    _message.value = "Zalogowano jako ${result.data.account.login}."
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

    fun signOut() {
        account.signOut()
        sync.forgetAllVersions()
        queue.clear()
        SyncWork.stop(context)
        _message.value = "Wylogowano. Notatki zostały na tablecie."
        refreshState()
    }

    fun synchroniseNow() {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null

            // Awaria wysyłki nie może zabrać ze sobą aplikacji. Bez tego jeden
            // błąd z serwera zamykał Kajet i zostawał czarny ekran.
            val result = runCatching { sync.synchronise() }.getOrElse { failure ->
                SyncResult(
                    reason = failure.message
                        ?: "Synchronizacja się nie udała. Notatki są bezpieczne na tablecie.",
                    worthRetrying = true,
                )
            }

            _message.value = when {
                result.reason != null -> null
                result.sent == 0 && result.fetched == 0 -> "Wszystko jest już zsynchronizowane."
                else -> buildString {
                    if (result.sent > 0) append("Wysłano ${result.sent}. ")
                    if (result.fetched > 0) append("Pobrano ${result.fetched}. ")
                    if (result.conflicts > 0) {
                        append(
                            "${result.conflicts} notatek zmieniło się w dwóch miejscach naraz. " +
                                "Wersje z serwera zapisałem obok, żeby nic nie przepadło.",
                        )
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
