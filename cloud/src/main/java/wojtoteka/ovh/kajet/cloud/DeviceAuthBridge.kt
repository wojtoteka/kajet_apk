package wojtoteka.ovh.kajet.cloud

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Delivers `kajet://auth?code=…` from [android.app.Activity] intent handling
 * to the account screen / [AccountViewModel] without tying them to the Activity.
 */
object DeviceAuthBridge {
    private val _pending = MutableStateFlow<Uri?>(null)
    val pending: StateFlow<Uri?> = _pending.asStateFlow()

    fun offer(uri: Uri?) {
        if (uri == null) return
        if (uri.scheme != "kajet" || uri.host != "auth") return
        _pending.value = uri
    }

    fun clear() {
        _pending.value = null
    }
}
