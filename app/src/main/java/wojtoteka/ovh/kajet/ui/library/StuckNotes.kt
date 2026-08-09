package wojtoteka.ovh.kajet.ui.library

import kotlinx.coroutines.flow.StateFlow
import wojtoteka.ovh.kajet.cloud.SendQueue
import wojtoteka.ovh.kajet.cloud.Sync

/**
 * Notatki, które wyczerpały próby wysyłki i czekają na ręczne ponowienie.
 *
 * Sygnał o nich stał do tej pory wyłącznie na ekranie konta — trzeba było tam
 * samemu zajrzeć, żeby się o czymkolwiek dowiedzieć, więc w praktyce nikt się
 * nie dowiadywał. Biblioteka pokazuje to teraz przy konkretnej notatce.
 *
 * Widok nie sięga do chmury wprost: dostaje tę furtkę, więc na sucho (podgląd
 * Compose, testy) można podstawić cokolwiek.
 */
interface StuckNotes {

    /** Ile wpisów utknęło. Służy za sygnał do przeliczenia, nie do pokazania. */
    val count: StateFlow<Int>

    /** Ścieżki plików, które utknęły — po nich poznaje je spis biblioteki. */
    fun paths(): Set<String>

    /** Nowa pula prób dla wszystkiego, co utknęło, i od razu synchronizacja. */
    fun retry()
}

/**
 * Prawdziwe źródło: licznik z [Sync] mówi, KIEDY coś się zmieniło, a ścieżki
 * przychodzą z kolejki, bo tylko ona wie, KTÓRE to notatki.
 *
 * Czytanie kolejki przy każdej zmianie licznika, zamiast trzymania osobnego
 * strumienia ze ścieżkami, ma tę zaletę, że pierwsze spojrzenie po starcie
 * aplikacji też widzi prawdę: licznik startuje wtedy od zera i urośnie dopiero
 * po pierwszej synchronizacji, a w kolejce wpisy z poprzedniej sesji leżą
 * od początku.
 */
class CloudStuckNotes(
    private val sync: Sync,
    private val queue: SendQueue,
) : StuckNotes {

    override val count: StateFlow<Int> = sync.stuck

    override fun paths(): Set<String> =
        runCatching { queue.all().filter { it.stuck }.map { it.path }.toSet() }
            .getOrDefault(emptySet())

    override fun retry() {
        sync.retryStuck()
    }
}
