package wojtoteka.ovh.kajet.cloud

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import wojtoteka.ovh.kajet.core.text.words

class AccountStore(context: Context) : SyncAccount {

    private val preferences: SharedPreferences = createStore(context)

    private val _state = MutableStateFlow(readState())

    val state: StateFlow<SignInState> = _state.asStateFlow()

    fun token(): String? = preferences.getString(KEY_TOKEN, null)

    fun serverUrl(): String = SERVER_URL

    override fun isSignedIn(): Boolean = !token().isNullOrBlank()

    override fun lastSync(): Long = preferences.getLong(KEY_LAST_SYNC, 0L)

    override fun rememberSync(moment: Long) {
        preferences.edit().putLong(KEY_LAST_SYNC, moment).apply()
    }

    /**
     * Do której chwili znamy nagrobki po notatkach skasowanych na zawsze.
     * Osobno od [lastSync], bo to inny kursor: notatki idą po dacie zmiany,
     * nagrobki po dacie skasowania.
     */
    override fun lastDeletedSync(): Long = preferences.getLong(KEY_LAST_DELETED_SYNC, 0L)

    /**
     * Przesuwa ten znacznik. Wołane DOPIERO po przejściu całego spisu do końca
     * - przerwana w połowie synchronizacja nie ma prawa przeskoczyć nagrobków,
     * których jeszcze nie zastosowano.
     */
    override fun rememberDeletedSync(moment: Long) {
        preferences.edit().putLong(KEY_LAST_DELETED_SYNC, moment).apply()
    }

    fun save(response: SignInResponse) {
        preferences.edit()
            .putString(KEY_TOKEN, response.token)
            .putString(KEY_TOKEN_ID, response.tokenId)
            .putString(KEY_LOGIN, response.account.login)
            .putString(KEY_EMAIL, response.account.email)
            .putBoolean(KEY_ADMIN, response.account.admin)
            // Odpowiedź logowania niesie też zajętość konta. Bez zapisania jej
            // tutaj ekran pokazywał „0 B, bez limitu" aż do ponownego wejścia.
            .putLong(KEY_QUOTA, response.storage.quotaBytes)
            .putLong(KEY_USED, response.storage.usedBytes)
            .remove(KEY_SESSION_EXPIRED)
            .apply()
        _state.value = readState()
    }

    /**
     * Serwer przestał uznawać token (wylogowanie przez stronę, wygaśnięcie).
     *
     * Schodzi token, a razem z nim WSZYSTKO, co mówi o człowieku: login, adres,
     * zajęte miejsce. Sesji nie ma, więc aplikacja nie ma prawa dalej wyświetlać
     * czyjejś nazwy - zostaje sam znacznik, dzięki któremu ekrany mówią wprost
     * „sesja wygasła" zamiast udawać, że nikt się nigdy nie logował. Kolejka
     * wysyłki zostaje nietknięta: po ponownym zalogowaniu zaległe zmiany dojadą.
     *
     * Wołane z jednego miejsca - [CloudClient] przy odpowiedzi odmawiającej
     * tożsamości.
     */
    fun markSessionExpired() {
        if (token().isNullOrBlank()) return
        preferences.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_TOKEN_ID)
            .remove(KEY_LOGIN)
            .remove(KEY_EMAIL)
            .remove(KEY_ADMIN)
            .remove(KEY_QUOTA)
            .remove(KEY_USED)
            .putBoolean(KEY_SESSION_EXPIRED, true)
            .apply()
        _state.value = readState()
    }

    fun refresh(accountState: AccountState) {
        preferences.edit()
            .putString(KEY_LOGIN, accountState.account.login)
            .putString(KEY_EMAIL, accountState.account.email)
            .putBoolean(KEY_ADMIN, accountState.account.admin)
            .putLong(KEY_QUOTA, accountState.storage.quotaBytes)
            .putLong(KEY_USED, accountState.storage.usedBytes)
            // Brak gałęzi „ai" znaczy, że konto nie ma asystenta - i wtedy
            // trzeba wyczyścić to, co pamiętaliśmy, bo uprawnienie mógł
            // właśnie odebrać administrator.
            .putBoolean(KEY_AI, accountState.ai != null)
            .putBoolean(KEY_AI_CONSENT, accountState.ai?.consented == true)
            .apply()
        _state.value = readState()
    }

    /**
     * Zgoda potwierdzona albo wycofana w aplikacji. Serwer już o niej wie -
     * to tylko żeby ekran nie czekał na najbliższe odświeżenie konta.
     */
    fun rememberAiConsent(consented: Boolean) {
        preferences.edit().putBoolean(KEY_AI_CONSENT, consented).apply()
        _state.value = readState()
    }

    fun aiAvailable(): Boolean = preferences.getBoolean(KEY_AI, false)

    fun aiConsented(): Boolean = preferences.getBoolean(KEY_AI_CONSENT, false)

    fun signOut() {
        preferences.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_TOKEN_ID)
            .remove(KEY_LOGIN)
            .remove(KEY_EMAIL)
            .remove(KEY_ADMIN)
            .remove(KEY_QUOTA)
            .remove(KEY_USED)
            .remove(KEY_LAST_SYNC)
            .remove(KEY_LAST_DELETED_SYNC)
            .remove(KEY_SESSION_EXPIRED)
            .remove(KEY_AI)
            .remove(KEY_AI_CONSENT)
            .apply()
        _state.value = readState()
    }

    private fun readState(): SignInState {
        val token = token()
        return if (token.isNullOrBlank()) {
            if (preferences.getBoolean(KEY_SESSION_EXPIRED, false)) {
                SignInState.SessionExpired(serverUrl())
            } else {
                SignInState.SignedOut(serverUrl())
            }
        } else {
            SignInState.SignedIn(
                serverUrl = serverUrl(),
                login = preferences.getString(KEY_LOGIN, "") ?: "",
                email = preferences.getString(KEY_EMAIL, "") ?: "",
                admin = preferences.getBoolean(KEY_ADMIN, false),
                quotaBytes = preferences.getLong(KEY_QUOTA, 0L),
                usedBytes = preferences.getLong(KEY_USED, 0L),
                aiAvailable = preferences.getBoolean(KEY_AI, false),
                aiConsented = preferences.getBoolean(KEY_AI_CONSENT, false),
            )
        }
    }

    private fun createStore(context: Context): SharedPreferences {
        val encrypted = runCatching { openEncrypted(context) }
            .recoverCatching {
                // Zepsuty plik (na przykład klucz przepadł po przywróceniu
                // z kopii zapasowej) wyrzucamy i zakładamy od nowa. Bez tego
                // każde uruchomienie odbijałoby się między magazynem szyfrowanym
                // a zapasowym i logowanie raz było, raz znikało.
                context.deleteSharedPreferences(FILE_NAME)
                openEncrypted(context)
            }
            .getOrNull()

        val fallback = context.getSharedPreferences("$FILE_NAME-fallback", Context.MODE_PRIVATE)
        if (encrypted == null) {
            // Magazyn kluczy bywa zepsuty na niektórych urządzeniach. Notatnik
            // ma wtedy dalej działać, więc zostaje zwykły plik ustawień.
            return fallback
        }

        // Token zapisany kiedyś do pliku zapasowego przenosi się do właściwego
        // magazynu, żeby stan zalogowania nie zależał od tego, który plik
        // akurat dało się otworzyć.
        val stray = fallback.getString(KEY_TOKEN, null)
        if (!stray.isNullOrBlank() && encrypted.getString(KEY_TOKEN, null).isNullOrBlank()) {
            encrypted.edit()
                .putString(KEY_TOKEN, stray)
                .putString(KEY_TOKEN_ID, fallback.getString(KEY_TOKEN_ID, ""))
                .putString(KEY_LOGIN, fallback.getString(KEY_LOGIN, ""))
                .putString(KEY_EMAIL, fallback.getString(KEY_EMAIL, ""))
                .putBoolean(KEY_ADMIN, fallback.getBoolean(KEY_ADMIN, false))
                .putLong(KEY_LAST_SYNC, fallback.getLong(KEY_LAST_SYNC, 0L))
                .apply()
        }
        if (fallback.all.isNotEmpty()) fallback.edit().clear().apply()
        return encrypted
    }

    private fun openEncrypted(context: Context): SharedPreferences {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    companion object {
        const val SERVER_URL = "https://kajet.wojtoteka.ovh"

        private const val FILE_NAME = "kajet-account"
        private const val KEY_TOKEN = "token"
        private const val KEY_TOKEN_ID = "token_id"
        private const val KEY_LOGIN = "login"
        private const val KEY_EMAIL = "email"
        private const val KEY_ADMIN = "admin"
        private const val KEY_QUOTA = "quota"
        private const val KEY_USED = "used"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_LAST_DELETED_SYNC = "last_deleted_sync"
        private const val KEY_SESSION_EXPIRED = "session_expired"
        private const val KEY_AI = "ai"
        private const val KEY_AI_CONSENT = "ai_consent"
    }
}

sealed interface SignInState {
    val serverUrl: String

    data class SignedOut(override val serverUrl: String) : SignInState

    /**
     * Token przestał działać (wylogowanie przez stronę, wygaśnięcie). Bez
     * nazwy konta - ta zeszła razem z sesją; zostaje sama wiadomość, że trzeba
     * zalogować się jeszcze raz.
     */
    data class SessionExpired(override val serverUrl: String) : SignInState

    data class SignedIn(
        override val serverUrl: String,
        val login: String,
        val email: String,
        val admin: Boolean,
        val quotaBytes: Long,
        val usedBytes: Long,
        /**
         * Czy konto ma asystenta. Serwer przysyła gałąź „ai" wyłącznie wtedy,
         * gdy ma - jej brak znaczy „nie ma takiej funkcji", więc fałsz tutaj
         * ma chować asystenta zupełnie, a nie pokazywać go jako wyłączonego.
         */
        val aiAvailable: Boolean = false,
        val aiConsented: Boolean = false,
    ) : SignInState {
        /**
         * Miejsce bez ograniczeń.
         *
         * Ujemny limit, nie zerowy. Zero znaczy teraz „to konto nie ma ani
         * bajta miejsca" - dokładnie to samo co na serwerze. Wcześniej zero
         * było brakiem ograniczeń i konto bez nadanego miejsca chwaliło się
         * tutaj miejscem bez końca.
         */
        val unlimited: Boolean get() = quotaBytes < 0L

        val usedPercent: Int
            get() = when {
                unlimited -> 0
                // Nie ma czego dzielić, a pasek ma być pełny: wolnego miejsca
                // jest zero i każdy zapis odbije się od serwera.
                quotaBytes <= 0L -> 100
                else -> ((usedBytes * 100) / quotaBytes).toInt().coerceIn(0, 100)
            }
    }
}

fun humanSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    if (bytes < 1024) return "$bytes B"
    val units = listOf("kB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024
        i += 1
    }
    // Przecinek albo kropka według języka: w angielskim interfejsie
    // „12,4 MB” wygląda jak pomyłka.
    val separator = if (words.english) java.util.Locale.UK else java.util.Locale.forLanguageTag("pl")
    val rounded = if (value >= 100) Math.round(value).toString()
    else String.format(separator, "%.1f", value)
    return "$rounded ${units[i]}"
}
