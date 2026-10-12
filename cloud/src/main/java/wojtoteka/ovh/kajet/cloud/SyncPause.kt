package wojtoteka.ovh.kajet.cloud

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wstrzymana synchronizacja - na godzinę, na dzień, do wybranej chwili albo
 * do odwołania, choćby internet był pod ręką.
 *
 * Przez ten czas notatki zachowują się dokładnie tak jak bez sieci: zmiany
 * czekają w kolejce, edycja na żywo pokazuje „Offline", a przy „Zapisane"
 * stoi szara chmura. Konto, KajetAI i udostępnianie działają dalej - to nie
 * są synchronizacja.
 *
 * Chwila końca leży w ustawieniach, więc pauza przeżywa zamknięcie aplikacji
 * i jest widoczna także dla pracy w tle ([SyncWork]).
 */
class SyncPause(context: Context) {

    private val prefs = context.getSharedPreferences("kajet-sync-pause", Context.MODE_PRIVATE)

    private val _until = MutableStateFlow(prefs.getLong(KEY_UNTIL, 0L))

    /**
     * Do kiedy synchronizacja stoi (milisekundy od epoki). 0 - nie stoi,
     * [FOREVER] - do odwołania. Chwila z przeszłości znaczy to samo co 0.
     */
    val until: StateFlow<Long> = _until.asStateFlow()

    fun isPaused(now: Long = System.currentTimeMillis()): Boolean = _until.value > now

    fun pauseUntil(moment: Long) {
        prefs.edit().putLong(KEY_UNTIL, moment).apply()
        _until.value = moment
    }

    fun resume() {
        prefs.edit().remove(KEY_UNTIL).apply()
        _until.value = 0L
    }

    companion object {
        const val FOREVER = Long.MAX_VALUE
        private const val KEY_UNTIL = "until"
    }
}
