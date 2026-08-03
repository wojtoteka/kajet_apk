package wojtoteka.ovh.kajet.cloud

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AccountStore(context: Context) {

    private val preferences: SharedPreferences = createStore(context)

    private val _state = MutableStateFlow(readState())

    val state: StateFlow<SignInState> = _state.asStateFlow()

    fun token(): String? = preferences.getString(KEY_TOKEN, null)

    fun serverUrl(): String = SERVER_URL

    fun isSignedIn(): Boolean = !token().isNullOrBlank()

    fun lastSync(): Long = preferences.getLong(KEY_LAST_SYNC, 0L)

    fun rememberSync(moment: Long) {
        preferences.edit().putLong(KEY_LAST_SYNC, moment).apply()
    }

    fun save(response: SignInResponse) {
        preferences.edit()
            .putString(KEY_TOKEN, response.token)
            .putString(KEY_TOKEN_ID, response.tokenId)
            .putString(KEY_LOGIN, response.account.login)
            .putString(KEY_EMAIL, response.account.email)
            .putBoolean(KEY_ADMIN, response.account.admin)
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
            .apply()
        _state.value = readState()
    }

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
            .apply()
        _state.value = readState()
    }

    private fun readState(): SignInState {
        val token = token()
        return if (token.isNullOrBlank()) {
            SignInState.SignedOut(serverUrl())
        } else {
            SignInState.SignedIn(
                serverUrl = serverUrl(),
                login = preferences.getString(KEY_LOGIN, "") ?: "",
                email = preferences.getString(KEY_EMAIL, "") ?: "",
                admin = preferences.getBoolean(KEY_ADMIN, false),
                quotaBytes = preferences.getLong(KEY_QUOTA, 0L),
                usedBytes = preferences.getLong(KEY_USED, 0L),
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
    }
}

sealed interface SignInState {
    val serverUrl: String

    data class SignedOut(override val serverUrl: String) : SignInState

    data class SignedIn(
        override val serverUrl: String,
        val login: String,
        val email: String,
        val admin: Boolean,
        val quotaBytes: Long,
        val usedBytes: Long,
    ) : SignInState {
        val unlimited: Boolean get() = quotaBytes == 0L

        val usedPercent: Int
            get() = if (unlimited) 0 else ((usedBytes * 100) / quotaBytes.coerceAtLeast(1)).toInt()
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
    val rounded = if (value >= 100) Math.round(value).toString()
    else String.format(java.util.Locale.forLanguageTag("pl"), "%.1f", value)
    return "$rounded ${units[i]}"
}
