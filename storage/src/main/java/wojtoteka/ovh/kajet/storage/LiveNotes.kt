package wojtoteka.ovh.kajet.storage

import kotlinx.coroutines.flow.StateFlow
import wojtoteka.ovh.kajet.core.model.NoteDocument

/**
 * Edycja na żywo widziana od strony edytora.
 *
 * Edytory (moduł editor) nie znają chmury - tak jak przy [CloudSaveLookup],
 * chmura podpina się tu z zewnątrz ([LibraryRepository.live]). Edytor zgłasza
 * każdy zapis notatki, a dostaje z powrotem treść scaloną ze zmianami innych
 * osób, które w tej samej chwili piszą w tej samej notatce - na stronie, na
 * tablecie, na telefonie.
 */
interface LiveNotes {
    /**
     * Otwiera edycję na żywo notatki spod [path]. null - nie ma jak (brak
     * konta i odnośnika, notatki jeszcze nie ma na serwerze, brak sieci przy
     * notatce udostępnionej tylko odnośnikiem).
     */
    fun open(path: String, document: NoteDocument, editor: LiveEditor): LiveHandle?
}

/** Edytor z otwartą notatką - tyle, ile edycja na żywo musi o nim wiedzieć. */
interface LiveEditor {
    /** Treść na ekranie. Wołane z wątku głównego. */
    fun current(): NoteDocument?

    /**
     * Podmienia treść na [merged], ale tylko wtedy, gdy na ekranie wciąż jest
     * [expected] - jeśli w międzyczasie ktoś coś dopisał, scalanie liczy się
     * od nowa, żeby ta zmiana nie przepadła. Wątek główny.
     */
    fun replaceIf(expected: NoteDocument, merged: NoteDocument, author: String): Boolean

    /** Notatki już nie ma albo odebrano do niej dostęp. */
    fun gone()
}

/** Otwarta edycja na żywo jednej notatki. */
interface LiveHandle {
    val status: StateFlow<LiveStatus>

    /** Inni ludzie z tą notatką otwartą (bez tego urządzenia). */
    val people: StateFlow<List<LivePerson>>

    /** Udostępnienie tylko do czytania - edytor nie przyjmuje zmian. */
    val readOnly: Boolean

    /** Edytor zapisał notatkę na dysku - zmiana ma pojechać do innych. */
    fun saved(document: NoteDocument)

    fun close()
}

enum class LiveStatus { CONNECTING, LIVE, OFFLINE, OFF }

data class LivePerson(
    val client: String,
    val name: String,
    /** Aplikacja (tablet, telefon) czy przeglądarka. */
    val app: Boolean,
    /** Indeks barwy - ten sam człowiek ma ten sam kolor wszędzie. */
    val color: Int,
)
