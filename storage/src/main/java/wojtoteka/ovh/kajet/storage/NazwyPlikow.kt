package wojtoteka.ovh.kajet.storage

/**
 * Zamiana nazwy wpisanej przez użytkownika na nazwę, którą przyjmie każdy dysk.
 *
 * Karta pamięci sformatowana jako FAT nie przyjmie dwukropka ani znaku zapytania,
 * a Windows dodatkowo blokuje kilka nazw zastrzeżonych. Prawdziwa nazwa,
 * ta z polskimi znakami i spacjami, zostaje zapisana w pliku folder.json
 * albo w polu title wewnątrz notatki, więc nic nie ginie.
 */
object NazwyPlikow {

    private val ZNAKI_ZABRONIONE = charArrayOf('"', '*', '/', ':', '<', '>', '?', '\\', '|')

    private val NAZWY_ZASTRZEZONE = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
    )

    const val MAKS_DLUGOSC = 96
    const val NAZWA_ZASTEPCZA = "Bez nazwy"

    /**
     * Zwraca nazwę bezpieczną dla dysku. Zachowuje polskie znaki i spacje,
     * bo te przyjmuje każdy współczesny system plików.
     */
    fun bezpieczna(nazwa: String): String {
        var wynik = nazwa.trim()

        wynik = buildString(wynik.length) {
            for (znak in wynik) {
                when {
                    znak in ZNAKI_ZABRONIONE -> append('_')
                    znak.code < 0x20 -> append('_')
                    znak.code == 0x7F -> append('_')
                    else -> append(znak)
                }
            }
        }

        // Kropka i spacja na końcu psują nazwę katalogu w systemie Windows.
        wynik = wynik.trimEnd('.', ' ')

        if (wynik.length > MAKS_DLUGOSC) {
            wynik = wynik.take(MAKS_DLUGOSC).trimEnd('.', ' ')
        }

        if (wynik.isEmpty()) return NAZWA_ZASTEPCZA

        val trzon = wynik.substringBefore('.').uppercase()
        if (trzon in NAZWY_ZASTRZEZONE) return "_$wynik"

        // Kropka na początku ukrywa plik w systemach uniksowych, a katalog .trash
        // jest u nas zarezerwowany na kosz.
        if (wynik.startsWith('.')) return "_" + wynik.drop(1)

        return wynik
    }

    /**
     * Dokłada numer, kiedy nazwa jest już zajęta. Numer trafia przed rozszerzenie,
     * więc katalog notatki dalej kończy się na .note.
     */
    fun unikalna(nazwa: String, zajete: Set<String>, rozszerzenie: String = ""): String {
        val podstawa = bezpieczna(nazwa)
        val pelna = podstawa + rozszerzenie
        if (!zajete.zawieraBezWzgleduNaWielkosc(pelna)) return pelna

        var numer = 2
        while (numer < 1000) {
            val kandydat = "$podstawa ($numer)$rozszerzenie"
            if (!zajete.zawieraBezWzgleduNaWielkosc(kandydat)) return kandydat
            numer++
        }
        return "$podstawa (${System.currentTimeMillis()})$rozszerzenie"
    }

    private fun Set<String>.zawieraBezWzgleduNaWielkosc(nazwa: String): Boolean =
        any { it.equals(nazwa, ignoreCase = true) }

    /** Nazwa katalogu notatki bez końcówki .note. */
    fun bezRozszerzeniaNote(nazwaKatalogu: String): String =
        nazwaKatalogu.removeSuffix(".note")

    /** Czy ten katalog jest notatką Kajetu. */
    fun czyNotatka(nazwa: String): Boolean = nazwa.endsWith(".note", ignoreCase = true)

    /** Katalogi, których aplikacja nie pokazuje w bibliotece. */
    fun czyUkryty(nazwa: String): Boolean = nazwa.startsWith(".")
}
