package wojtoteka.ovh.kajet.cloud

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Odnośnik do udostępnienia otwarty w aplikacji: https://<serwer>/n/<token>
 * (stuknięty w poczcie, komunikatorze, przeglądarce) albo kajet://n/<token>
 * (przycisk „Otwórz w aplikacji" na stronie). MainActivity podaje go tutaj,
 * a nawigacja otwiera notatkę albo folder.
 */
object SharedLinkBridge {

    /** Odnośnik czekający na otwarcie: token i - przy folderze - notatka w nim. */
    data class Link(val token: String, val noteId: String? = null)

    private val _pending = MutableStateFlow<Link?>(null)
    val pending: StateFlow<Link?> = _pending.asStateFlow()

    fun offer(uri: Uri?) {
        parse(uri?.scheme, uri?.host, uri?.pathSegments.orEmpty(), uri?.getQueryParameter("note"))
            ?.let { _pending.value = it }
    }

    /** Czysta część rozpoznawania - do testów bez Androida. */
    fun parse(scheme: String?, host: String?, segments: List<String>, note: String?): Link? {
        val token = when {
            scheme == "kajet" && host == "n" -> segments.firstOrNull()
            (scheme == "https" || scheme == "http") && host == SERVER_HOST &&
                segments.size >= 2 && segments[0] == "n" -> segments[1]
            else -> null
        }
        if (token.isNullOrBlank() || !TOKEN.matches(token)) return null
        return Link(token, note?.takeIf { it.isNotBlank() })
    }

    fun clear() {
        _pending.value = null
    }

    private val SERVER_HOST = runCatching { java.net.URI(AccountStore.SERVER_URL).host }.getOrNull() ?: "kajet.wojtoteka.ovh"
    private val TOKEN = Regex("^[A-Za-z0-9_-]{8,128}$")
}
