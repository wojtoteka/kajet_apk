package wojtoteka.ovh.kajet.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plik albo tekst z systemowego „Udostępnij" / „Otwórz w".
 *
 * [uris] to źródła do skopiowania do katalogu notatek. [text] to treść
 * z `EXTRA_TEXT`, gdy ktoś przysyła sam napis, bez pliku.
 */
data class IncomingShare(
    val text: String?,
    val subject: String?,
    val uris: List<Uri>,
    val mime: String?,
)

/**
 * Intencja z [android.app.Activity] do nawigacji, bez wiązania ekranu
 * z aktywnością — ten sam układ co [wojtoteka.ovh.kajet.cloud.DeviceAuthBridge].
 */
object ShareIncoming {
    private val _pending = MutableStateFlow<IncomingShare?>(null)
    val pending: StateFlow<IncomingShare?> = _pending.asStateFlow()
    private val importing = AtomicBoolean(false)

    fun offer(intent: Intent?) {
        val parsed = parse(intent) ?: return
        _pending.value = parsed
    }

    fun clear() {
        _pending.value = null
        importing.set(false)
    }

    /**
     * Bierze oczekujące udostępnienie do kopii. Drugie wołanie w trakcie
     * pierwszej wraca null — zmiana ekranu nie odpala importu drugi raz,
     * zanim pierwszy zdąży skasować pending.
     *
     * Pending zostaje, dopóki kopia się nie uda: po porażce można spróbować
     * jeszcze raz bez ponownego „Udostępnij", jeśli system wciąż trzyma URI.
     */
    fun tryBegin(): IncomingShare? {
        val share = _pending.value ?: return null
        return if (importing.compareAndSet(false, true)) share else null
    }

    fun finish(success: Boolean) {
        if (success) _pending.value = null
        importing.set(false)
    }
}

fun parse(intent: Intent?): IncomingShare? {
    if (intent == null) return null
    if (intent.data?.scheme == "kajet") return null
    val mime = intent.type
    return when (intent.action) {
        Intent.ACTION_SEND -> {
            val uri = streamUri(intent) ?: clipUris(intent).firstOrNull()
            val text = extraText(intent)
            if (uri == null && text.isNullOrBlank()) null
            else IncomingShare(text, extraSubject(intent), listOfNotNull(uri), mime)
        }
        Intent.ACTION_SEND_MULTIPLE -> {
            val uris = streamUris(intent).ifEmpty { clipUris(intent) }
            if (uris.isEmpty()) null
            else IncomingShare(extraText(intent), extraSubject(intent), uris, mime)
        }
        Intent.ACTION_VIEW -> {
            val data = intent.data ?: return null
            IncomingShare(null, extraSubject(intent), listOf(data), mime)
        }
        else -> null
    }
}

private fun extraText(intent: Intent): String? =
    intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        ?: intent.getStringExtra(Intent.EXTRA_TEXT)

private fun extraSubject(intent: Intent): String? =
    intent.getStringExtra(Intent.EXTRA_SUBJECT)
        ?: intent.getStringExtra(Intent.EXTRA_TITLE)

private fun streamUri(intent: Intent): Uri? =
    if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }

private fun streamUris(intent: Intent): List<Uri> {
    val list = if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
    }
    return list.orEmpty().filterNotNull()
}

private fun clipUris(intent: Intent): List<Uri> {
    val clip = intent.clipData ?: return emptyList()
    return buildList {
        for (index in 0 until clip.itemCount) {
            clip.getItemAt(index).uri?.let(::add)
        }
    }
}
