package wojtoteka.ovh.kajet.core.model

/**
 * Ułożenie jednego akapitu notatki tekstowej (lewo, środek, prawo).
 *
 * Markdown nie ma na to własnego zapisu, więc - tak samo jak barwa i rozmiar
 * pisma - siedzi ono w znaczniku HTML obejmującym CAŁY wiersz:
 *
 *   <p style="text-align:center">Tytuł na środku</p>
 *   <p style="text-align:right">## Nagłówek z prawej</p>
 *
 * Znacznik stoi na zewnątrz kratek nagłówka, znaku listy i cytatu, więc
 * budowa wiersza zostaje taka sama jak bez niego. Dokładnie ten zapis czyta
 * i pisze serwer (`src/lib/rich-text.ts`, `PARAGRAPH_ALIGN`).
 *
 * Dawniej ułożenie było jedno na całą notatkę (`TextContent.align`) -
 * kliknięcie „do środka" przy jednym zdaniu przestawiało cały plik. Teraz
 * każdy akapit ma swoje, tak jak w Wordzie.
 */
object ParagraphAlign {

    const val CLOSING = "</p>"

    private val opening = Regex("""^<p style="text-align:(left|center|right)">""")

    fun opening(align: NoteAlign): String = "<p style=\"text-align:${css(align)}\">"

    fun css(align: NoteAlign): String = when (align) {
        NoteAlign.LEFT -> "left"
        NoteAlign.CENTER -> "center"
        NoteAlign.RIGHT -> "right"
    }

    private fun fromCss(value: String): NoteAlign = when (value) {
        "center" -> NoteAlign.CENTER
        "right" -> NoteAlign.RIGHT
        else -> NoteAlign.LEFT
    }

    /** Długość znacznika otwierającego na początku [line]; 0, gdy go nie ma. */
    fun openingLength(line: String): Int =
        if (line.startsWith("<p ")) opening.find(line)?.value?.length ?: 0 else 0

    /** Ułożenie zapisane w wierszu; null, gdy wiersz nie ma własnego. */
    fun alignOf(line: String): NoteAlign? =
        if (line.startsWith("<p ")) opening.find(line)?.groupValues?.get(1)?.let(::fromCss) else null

    /**
     * Długość domknięcia na końcu wiersza, który ma znacznik otwierający
     * długości [open]. Domknięcie bez otwarcia nic nie znaczy i zostaje treścią.
     */
    fun closingLength(line: String, open: Int): Int =
        if (open > 0 && line.length >= open + CLOSING.length && line.endsWith(CLOSING)) {
            CLOSING.length
        } else {
            0
        }

    /** Sam wiersz, bez znacznika ułożenia. */
    fun unwrap(line: String): String {
        val open = openingLength(line)
        if (open == 0) return line
        return line.substring(open, line.length - closingLength(line, open))
    }

    /** Wiersz obłożony znacznikiem; null zdejmuje ułożenie. */
    fun wrap(inner: String, align: NoteAlign?): String =
        if (align == null) inner else opening(align) + inner + CLOSING
}
