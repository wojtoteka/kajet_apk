package wojtoteka.ovh.kajet.core.model

object TextMarkers {

    val colorPattern = Regex("""<span style="color:[^"]*">([^<]*)</span>""")

    val sizePattern = Regex("""<span style="font-size:(\d+(?:\.\d+)?)px">([^<]*)</span>""")

    val underline = Regex("""<u>([^<]*)</u>""")

    val highlightMarker = Regex("""==([^=]+)==""")

    /**
     * Znaczniki na początku wiersza, które są składnią, a nie treścią:
     * `# `, `> `, `- `, `- [ ] `, `1. `.
     *
     * Spacja po myślniku czy gwiazdce jest tu OBOWIĄZKOWA i to jest cały sens
     * tego wyrażenia. Wcześniej podgląd notatki obcinał znaki z początku
     * wiersza po jednym (`trimStart('#', '>', '-', '*', ' ')`), więc z zapisu
     * `**Prompt:** treść` znikały dwie pierwsze gwiazdki, a domykająca para
     * zostawała w spisie jako gołe `**`.
     *
     * To samo wyrażenie stoi w `note-title.ts` na serwerze (LEADING_SYNTAX)
     * i musi liczyć to samo - inaczej te same notatki przetytułowywałyby się
     * nawzajem przy synchronizacji.
     */
    val leadingSyntax = Regex("""^\s*(#{1,6}\s+|>\s?|[-*+]\s+(\[[ xX]]\s+)?|\d+[.)]\s+)""")

    /** Linia pozioma: `---`, `***`, `___`. To nie jest treść. */
    val horizontalRule = Regex("""^(-{3,}|\*{3,}|_{3,})$""")

    /*
      Otwarcie i domknięcie zdejmowane OSOBNO, nie parą: stare notatki miewają
      znaczniki zagnieżdżone jeden w drugim albo osierocone domknięcia, a para
      regexowa zostawiała je wtedy w treści - goły HTML w spisie i w indeksie.
    */
    private val spanOpening = Regex("""<span (?:style="(?:color|font-size):[^"]*"|class="h[1-6]")>""")
    private val spanClosing = Regex("""</span>""")

    private val bold = Regex("""\*\*([^*]+)\*\*""")
    private val boldUnderscore = Regex("""__([^_]+)__""")
    private val italic = Regex("""(?<![*\w])\*([^*]+)\*(?![*\w])""")
    private val strikethrough = Regex("""~~([^~]+)~~""")
    private val inlineCode = Regex("""`([^`]+)`""")
    private val link = Regex("""\[([^\]]+)]\(([^)]+)\)""")

    fun plain(line: String): String = ParagraphAlign.unwrap(line)
        .replace(spanOpening, "")
        .replace(spanClosing, "")
        .replace(underline, "$1")
        .replace(bold, "$1")
        .replace(boldUnderscore, "$1")
        .replace(italic, "$1")
        .replace(strikethrough, "$1")
        .replace(highlightMarker, "$1")
        .replace(inlineCode, "$1")
        .replace(link, "$1")

    fun colorHex(argb: Int): String = "#%06X".format(argb and 0x00FFFFFF)

    fun colorFromHex(hex: String): Int? {
        val withoutHash = hex.removePrefix("#")
        val value = withoutHash.toLongOrNull(16) ?: return null
        return when (withoutHash.length) {
            6 -> value.toInt() or 0xFF000000.toInt()
            8 -> value.toInt()
            else -> null
        }
    }
}
