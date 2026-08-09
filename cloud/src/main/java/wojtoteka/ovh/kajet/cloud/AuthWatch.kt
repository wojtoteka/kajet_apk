package wojtoteka.ovh.kajet.cloud

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pilnuje, żeby stan zalogowania nie rozjeżdżał się z prawdą na serwerze.
 *
 * Bez tego aplikacja dowiadywała się o wylogowaniu przez stronę dopiero wtedy,
 * gdy sama miała coś do wysłania — a Ustawienia mogły całymi godzinami
 * pokazywać „Zalogowano jako…". Teraz token sprawdza się przy starcie
 * aplikacji i przy każdym powrocie z tła.
 *
 * Samo sprzątanie po martwej sesji dzieje się w [CloudClient]: odpowiedź
 * odmawiająca tożsamości gasi sesję niezależnie od tego, kto o nią zapytał.
 * Tutaj chodzi wyłącznie o to, żeby ktoś w ogóle zapytał.
 */
class AuthWatch(
    private val account: AccountStore,
    private val client: CloudClient,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Jedno pytanie na raz — powrót z tła potrafi przyjść dwa razy pod rząd. */
    private val asking = AtomicBoolean(false)

    @Volatile
    private var lastAnswer = 0L

    /**
     * Sprawdza token, jeśli od ostatniego sprawdzenia minęła chwila. Wraca
     * natychmiast — pytanie leci w tle, a wynik i tak trafi do
     * [AccountStore.state], z którego czytają wszystkie ekrany.
     */
    fun check() {
        if (!account.isSignedIn()) return

        val now = SystemClock.elapsedRealtime()
        val answered = lastAnswer
        // Wejście do aplikacji, obrót ekranu i powrót z okna wyboru pliku
        // potrafią przyjść jedno po drugim. Serwer nie musi tego oglądać.
        if (answered != 0L && now - answered < QUIET_PERIOD_MS) return
        if (!asking.compareAndSet(false, true)) return

        scope.launch {
            try {
                // Bez sieci nie ma odpowiedzi, a brak odpowiedzi to nie powód
                // do wylogowania. Znacznik zostaje nietknięty, więc kolejne
                // wejście spróbuje od nowa.
                if (!client.hasNetwork()) return@launch

                when (val answer = runCatching { client.accountState() }.getOrNull()) {
                    is CloudClient.Result.Ok -> {
                        // Przy okazji świeże zajęte miejsce i nazwa konta.
                        account.refresh(answer.data)
                        lastAnswer = SystemClock.elapsedRealtime()
                    }

                    is CloudClient.Result.Error -> {
                        // Martwy token wyczyścił już CloudClient. Tutaj zostaje
                        // tylko zapamiętać, że serwer się odezwał.
                        if (!answer.worthRetrying) lastAnswer = SystemClock.elapsedRealtime()
                    }

                    null -> Unit
                }
            } finally {
                asking.set(false)
            }
        }
    }

    companion object {
        /** Tyle milisekund po udanym sprawdzeniu nie pytamy serwera ponownie. */
        const val QUIET_PERIOD_MS = 15_000L
    }
}
