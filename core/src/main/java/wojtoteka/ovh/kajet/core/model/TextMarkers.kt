package wojtoteka.ovh.kajet.core.model

object TextMarkers {

    val colorPattern = Regex("""<span style="color:[^"]*">([^<]*)</span>""")

    val underline = Regex("""<u>([^<]*)</u>""")

    val highlightMarker = Regex("""==([^=]+)==""")

    private val bold = Regex("""\*\*([^*]+)\*\*""")
    private val boldUnderscore = Regex("""__([^_]+)__""")
    private val italic = Regex("""(?<![*\w])\*([^*]+)\*(?![*\w])""")
    private val strikethrough = Regex("""~~([^~]+)~~""")
    private val inlineCode = Regex("""`([^`]+)`""")
    private val link = Regex("""\[([^\]]+)]\(([^)]+)\)""")

    fun plain(line: String): String = line
        .replace(colorPattern, "$1")
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
