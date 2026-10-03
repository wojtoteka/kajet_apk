package wojtoteka.ovh.kajet.core.model

/**
 * Które pliki z katalogu `assets/` notatki tekstowej są jeszcze w użyciu.
 *
 * Zdjęcie i rysunek wstawione w notatkę to plik obok treści, a w treści
 * odnośnik `![opis](assets/plik.png)`. Rysunek ma do tego drugi plik - swoje
 * kreski (`plik.strokes.json`) - i wpis w [TextContent.drawings].
 *
 * Usunięcie rysunku z notatki zabierało dotąd tylko odnośnik. Pliki zostawały
 * w katalogu notatki, synchronizacja wysyłała je dalej na serwer, a na stronie
 * wisiały w załącznikach jak żywe. Ten sam rachunek liczy serwer
 * (`src/lib/attachment-usage.ts`).
 *
 * Rachunek jest celowo ostrożny: w razie wątpliwości plik jest W UŻYCIU.
 * Lepiej zostawić zbędny plik, niż skasować zdjęcie, na które coś wskazuje.
 */
object TextAttachments {

    /** Czy treść wskazuje na plik [name] - wprost albo z kodowaniem adresu. */
    fun mentioned(markdown: String, name: String): Boolean {
        if (name.isEmpty()) return false
        // Nazwa wprost, z samą spacją zakodowaną albo z zakodowanymi nawiasami.
        val spellings = setOf(name, name.replace(" ", "%20"), encode(name))
        return spellings.any { markdown.contains(ASSETS + it) }
    }

    /**
     * Czy plik [name] jest w użyciu: wskazuje na niego treść albo jest to
     * plik kresek rysunku, który stoi w treści.
     */
    fun inUse(text: TextContent, name: String): Boolean {
        if (mentioned(text.markdown, name)) return true
        return text.drawings.any { it.source == name && mentioned(text.markdown, it.asset) }
    }

    /** Rysunki, których obrazka nie ma już w treści - do sprzątnięcia. */
    fun removedDrawings(text: TextContent): List<InlineDrawing> =
        text.drawings.filterNot { mentioned(text.markdown, it.asset) }

    /** Treść bez wpisów o rysunkach, których już w niej nie ma. */
    fun withoutRemovedDrawings(text: TextContent): TextContent {
        val removed = removedDrawings(text)
        return if (removed.isEmpty()) text else text.copy(drawings = text.drawings - removed.toSet())
    }

    private const val ASSETS = "assets/"

    /** Kodowanie adresu, jakim bywa zapisana nazwa ze spacją albo nawiasem. */
    private fun encode(name: String): String = buildString {
        for (ch in name) {
            when (ch) {
                ' ' -> append("%20")
                '(' -> append("%28")
                ')' -> append("%29")
                else -> append(ch)
            }
        }
    }
}
