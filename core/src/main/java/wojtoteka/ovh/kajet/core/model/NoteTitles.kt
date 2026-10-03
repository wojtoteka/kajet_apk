package wojtoteka.ovh.kajet.core.model

import wojtoteka.ovh.kajet.core.text.EnglishStrings
import wojtoteka.ovh.kajet.core.text.PolishStrings

/**
 * Tytuł podpowiedziany z treści - wierne odbicie note-title.ts z serwera.
 *
 * Notatka bez wpisanego tytułu nazywała się „Bez tytułu" - i przy kilkunastu
 * takich notatkach spis przestawał cokolwiek mówić. Zamiast tego bierzemy
 * pierwszy wiersz treści: to prawie zawsze nagłówek albo pierwsze zdanie,
 * czyli dokładnie to, czym notatka jest. Strona robi to od dawna; tutaj to
 * samo dzieje się na urządzeniu, przy zapisie.
 *
 * Wpisany tytuł ZAWSZE wygrywa. Podpowiedź działa tylko, dopóki tytuł jest
 * jeszcze podstawiony ([isPlaceholder]); pierwsza trafiona podpowiedź go
 * nadpisuje i mechanizm sam się kończy - dokładnie tak jak na serwerze.
 */
object NoteTitles {

    /**
     * Dłuższy pierwszy wiersz obcinamy - w spisie i tak by się nie zmieścił.
     *
     * Osiemdziesiąt znaków zajmowało na karcie telefonu trzy wiersze i tytuł
     * zjadał całą kartę. Czterdzieści osiem mieści się w jednym wierszu
     * z zapasem.
     *
     * Ta sama liczba MUSI stać w note-title.ts na serwerze. Rozjazd znaczyłby,
     * że te same notatki przetytułowują się nawzajem przy każdej synchronizacji.
     */
    private const val LONGEST = 48

    /**
     * Ile znaków musi mieć NIEDOKOŃCZONY wiersz, żeby dało się z niego zrobić
     * tytuł.
     *
     * Autozapis rusza ułamek sekundy po pierwszym znaku. Bez tego progu notatka
     * nazwałaby się od jednej litery i tak już zostało - a tytuł podpowiada się
     * tylko raz, przy pierwszym zapisie z podstawionym tytułem.
     */
    private const val SETTLED_LENGTH = 12

    // Wspólne z podglądem notatki w spisie (IndexText) - jeden zapis składni
    // początku wiersza na całą aplikację.
    private val leadingSyntax = TextMarkers.leadingSyntax

    private val spanOpening = Regex("""<span style="[^"]*">""", RegexOption.IGNORE_CASE)

    /** Nagłówek na kawałku zdania - to samo co w note-title.ts na serwerze. */
    private val headingOpening = Regex("""<span class="h[1-6]">""", RegexOption.IGNORE_CASE)
    private val spanClosing = Regex("""</span>""", RegexOption.IGNORE_CASE)
    private val underlineTag = Regex("""</?u>""", RegexOption.IGNORE_CASE)
    private val linkOrImage = Regex("""!?\[([^\]]*)]\([^)]*\)""")
    private val inlineCode = Regex("""`([^`]+)`""")
    private val bold = Regex("""\*\*([^*]+)\*\*""")
    private val boldUnderscore = Regex("""__([^_]+)__""")
    private val strike = Regex("""~~([^~]+)~~""")
    private val mark = Regex("""==([^=]+)==""")
    private val italic = Regex("""\*([^*]+)\*""")
    private val horizontalRule = TextMarkers.horizontalRule

    /**
     * Obcięcie na ostatniej spacji przed granicą, a nie w połowie słowa.
     * „Pomaganie drugiemu człowiekowi to jedna z najważn..." czyta się jak
     * usterka; ucięte na spacji czyta się jak tytuł.
     *
     * Wyjątek: jedno słowo dłuższe niż cała granica - wtedy nie ma gdzie ciąć.
     */
    private fun shorten(text: String): String {
        if (text.length <= LONGEST) return text

        val cut = text.take(LONGEST)
        val space = cut.lastIndexOf(' ')
        val kept = if (space > LONGEST / 2) cut.take(space) else cut
        return kept.trimEnd() + "..."
    }

    /** Znaczniki w środku wiersza. Zdejmujemy je, zostawiając samą treść. */
    private fun withoutMarkers(line: String): String = line
        // Barwę, rozmiar i podkreślenie zapisujemy znacznikami HTML - otwarcia
        // i domknięcia zdejmowane osobno, żeby przeżyć też zapis zagnieżdżony.
        .replace(spanOpening, "")
        .replace(headingOpening, "")
        .replace(spanClosing, "")
        .replace(underlineTag, "")
        // Odnośnik i zdjęcie: zostaje sam opis.
        .replace(linkOrImage, "$1")
        .replace(inlineCode, "$1")
        .replace(bold, "$1")
        .replace(boldUnderscore, "$1")
        .replace(strike, "$1")
        .replace(mark, "$1")
        .replace(italic, "$1")
        .replace(Regex("""\s+"""), " ")
        .trim()

    /**
     * Tytuł z treści notatki tekstowej. Null, gdy nie ma z czego go zrobić -
     * wtedy zostaje dotychczasowy podstawiony tytuł.
     */
    fun fromMarkdown(markdown: String): String? {
        var insideCodeBlock = false
        val lines = markdown.split("\n")

        for ((index, raw) in lines.withIndex()) {
            // Znacznik ułożenia akapitu (<p style="text-align:...">) to nie treść.
            val line = ParagraphAlign.unwrap(raw.trim()).trim()

            // Płot bloku kodu i wzoru. Treść w środku bywa techniczna i na
            // tytuł się nie nadaje, więc przechodzimy nad nią.
            if (line.startsWith("```") || line == "$$") {
                insideCodeBlock = !insideCodeBlock
                continue
            }
            if (insideCodeBlock) continue

            // Linia pozioma to nie treść.
            if (horizontalRule.matches(line)) continue

            val cleaned = withoutMarkers(line.replace(leadingSyntax, ""))
            if (cleaned.isEmpty()) continue

            // Wiersz musi być dokończony: albo człowiek przeszedł do
            // następnego, albo napisał już tyle, że widać, o czym to jest.
            val settled = index < lines.lastIndex || cleaned.length >= SETTLED_LENGTH
            if (!settled) return null

            return shorten(cleaned)
        }

        return null
    }

    /** To samo dla mapy myśli: tytułem zostaje pierwszy opisany węzeł. */
    fun fromMindMap(nodes: List<MindNode>): String? {
        for (node in nodes) {
            val cleaned = withoutMarkers(node.text.trim())
            if (cleaned.isEmpty()) continue
            // Węzeł mapy wpisuje się w okienku i zatwierdza, więc nie ma tu
            // ryzyka złapania go w pół słowa.
            return shorten(cleaned)
        }
        return null
    }

    /**
     * Czy tytuł jest wciąż tym podstawionym przy założeniu notatki. Sprawdzamy
     * oba języki - notatka mogła powstać przy innym ustawieniu języka, niż
     * jest teraz.
     */
    fun isPlaceholder(title: String): Boolean {
        val trimmed = title.trim()
        return trimmed.isEmpty() ||
            trimmed == PolishStrings.untitled ||
            trimmed == PolishStrings.unnamed ||
            trimmed == EnglishStrings.untitled ||
            trimmed == EnglishStrings.unnamed
    }
}
