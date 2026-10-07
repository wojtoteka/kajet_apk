package wojtoteka.ovh.kajet.editor.text

/**
 * Cyfry i znaki działań w indeksie górnym i dolnym - znaki Unicode, które
 * mają swoje odpowiedniki w obu indeksach. Używa tego [TextCommands.script].
 */
internal object ScriptDigits {

    private const val PLAIN = "0123456789+-=()"
    private const val SUPER = "⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾"
    private const val SUB = "₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎"

    /** Znak, który ma odpowiednik w indeksie (albo już w którymś jest). */
    fun isScriptable(ch: Char): Boolean = plainIndex(ch) >= 0

    /** Czy znak stoi już w indeksie górnym ([superscript]) albo dolnym. */
    fun isIn(ch: Char, superscript: Boolean): Boolean =
        (if (superscript) SUPER else SUB).indexOf(ch) >= 0

    /** Znak w wybranym indeksie; znak spoza spisu zostaje, jaki był. */
    fun to(ch: Char, superscript: Boolean): Char {
        val index = plainIndex(ch)
        if (index < 0) return ch
        return (if (superscript) SUPER else SUB)[index]
    }

    /** Znak z indeksu z powrotem w zwykłej postaci. */
    fun plain(ch: Char): Char {
        val index = plainIndex(ch)
        return if (index < 0) ch else PLAIN[index]
    }

    /**
     * Początek liczby, która kończy się tuż przed [at] - zwykłymi cyframi
     * albo już w indeksie. Zwykłe „+" i „-" nie wchodzą: w „2-3" minus jest
     * działaniem, a nie znakiem wykładnika. Kto chce x⁻¹, zaznacza „-1".
     */
    fun numberStart(text: String, at: Int): Int {
        var start = at.coerceIn(0, text.length)
        while (start > 0) {
            val ch = text[start - 1]
            val belongs = ch in '0'..'9' || isIn(ch, true) || isIn(ch, false)
            if (!belongs) break
            start--
        }
        return start
    }

    private fun plainIndex(ch: Char): Int {
        if (ch == '−') return PLAIN.indexOf('-')
        PLAIN.indexOf(ch).let { if (it >= 0) return it }
        SUPER.indexOf(ch).let { if (it >= 0) return it }
        return SUB.indexOf(ch)
    }
}
