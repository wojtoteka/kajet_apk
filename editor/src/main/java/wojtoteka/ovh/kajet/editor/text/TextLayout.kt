package wojtoteka.ovh.kajet.editor.text

import androidx.compose.ui.text.TextRange
import wojtoteka.ovh.kajet.core.model.NoteAlign
import wojtoteka.ovh.kajet.core.model.ParagraphAlign
import wojtoteka.ovh.kajet.core.model.RichTextCodec

/** Czym jest wiersz pola tekstowego. */
enum class LineKind {
    PARAGRAPH,
    HEADING,
    QUOTE,
    BULLET,
    NUMBER,
    TASK,
    RULE,

    /** Płot bloku kodu albo wzoru wpisany ręcznie w akapit. */
    FENCE,
    CODE,
    FORMULA,
}

/**
 * Jeden wiersz treści w miarach czystego tekstu (po [RichTextCodec.read]).
 *
 * Wiersz to: znacznik ułożenia ([open], zawsze ukryty), budowa wiersza
 * ([prefix] - kratki nagłówka i znak cytatu są ukryte, znak listy widać),
 * treść i domknięcie znacznika ułożenia ([close], ukryte).
 */
class TextLine(
    val start: Int,
    val end: Int,
    val kind: LineKind,
    val level: Int,
    val align: NoteAlign?,
    val open: Int,
    val prefix: Int,
    val prefixHidden: Boolean,
    val close: Int,
) {
    val contentStart: Int get() = start + open + prefix
    val contentEnd: Int get() = end - close

    /** Ile znaków budowy wiersza widać na początku (znak listy). */
    val visiblePrefix: Int get() = if (prefixHidden) 0 else prefix
}

/**
 * Co w treści pola widać, a co jest samym zapisem.
 *
 * Jedno źródło prawdy dla wyglądu ([InlineStyle]) i dla pisania ([TextEdit]):
 * to, co tu jest ukryte, nie jest widoczne na ekranie i NIE DA się tego
 * ruszyć klawiaturą. Wcześniej wygląd i pisanie liczyły to osobno, więc
 * Backspace potrafił zabrać połowę ukrytego znacznika - i ta połowa
 * wychodziła na wierzch jako goły tekst („#Tytuł", „```print").
 */
class TextLayout private constructor(
    val source: String,
    val parsed: RichTextCodec.Parsed,
    val lines: List<TextLine>,
    private val keep: BooleanArray,
    /** Ukryty ogon odnośnika („](adres)") - kursor staje za nim, a nie w nim. */
    private val linkTail: BooleanArray,
    val visible: String,
    private val toVisible: IntArray,
    private val toPlain: IntArray,
    private val lineVisibleStart: IntArray,
) {
    val plain: String get() = parsed.rich.text

    fun isVisible(plainIndex: Int): Boolean = keep.getOrElse(plainIndex) { false }

    /** Ile widocznych znaków stoi przed znakiem czystego tekstu [plainIndex]. */
    fun visibleOfPlain(plainIndex: Int): Int = toVisible[plainIndex.coerceIn(0, plain.length)]

    fun visibleOfSource(sourceOffset: Int): Int = visibleOfPlain(parsed.plainOffset(sourceOffset))

    /** Znak czystego tekstu pod widocznym znakiem [visibleIndex]. */
    fun plainOfVisible(visibleIndex: Int): Int = toPlain[visibleIndex.coerceIn(0, visible.length)]

    /** Wiersz, w którym stoi widoczna pozycja [visibleIndex]. */
    fun lineAtVisible(visibleIndex: Int): Int {
        var low = 0
        var high = lines.lastIndex
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (lineVisibleStart[middle] <= visibleIndex) low = middle else high = middle - 1
        }
        return low
    }

    fun lineAtPlain(plainIndex: Int): Int {
        var low = 0
        var high = lines.lastIndex
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (lines[middle].start <= plainIndex) low = middle else high = middle - 1
        }
        return low
    }

    fun lineVisibleStart(line: Int): Int = lineVisibleStart[line]

    fun lineVisibleEnd(line: Int): Int = toVisible[lines[line].end]

    /**
     * Miejsce w czystym tekście dla kursora stojącego na widocznej pozycji
     * [visibleIndex].
     *
     * Kursor staje tuż ZA poprzednim znakiem treści, więc pisanie dziedziczy
     * jego format - jak w Wordzie: za pogrubionym słowem pisze się dalej
     * grubo. Na początku wiersza staje przed treścią, ale za ukrytą budową
     * wiersza (kratkami, znakiem cytatu, znacznikiem ułożenia), a na końcu
     * przed domknięciem znacznika ułożenia - nigdy w środku zapisu.
     */
    fun plainCursor(visibleIndex: Int): Int {
        val v = visibleIndex.coerceIn(0, visible.length)
        val index = lineAtVisible(v)
        val line = lines[index]
        val k = v - lineVisibleStart[index]
        if (k <= line.visiblePrefix) return line.contentStart
        var p = toPlain[v - 1] + 1
        while (p < line.contentEnd && linkTail[p]) p++
        return p.coerceIn(line.contentStart, line.contentEnd)
    }

    /** Miejsce w zapisie dla kursora stojącego w czystym tekście na [plainIndex]. */
    fun sourceOfPlainCursor(plainIndex: Int): Int {
        val p = plainIndex.coerceIn(0, plain.length)
        val line = lines[lineAtPlain(p)]
        return if (p > line.contentStart) parsed.sourceEnd(p) else parsed.sourceOffset(p)
    }

    fun sourceCursor(visibleIndex: Int): Int = sourceOfPlainCursor(plainCursor(visibleIndex))

    /**
     * Zaznaczenie widocznych znaków od [from] do [to] w miarach zapisu: od
     * pierwszego zaznaczonego znaku do tuż za ostatnim, bez znaczników
     * dookoła. Puste - zwykły kursor ([sourceCursor]).
     */
    fun sourceSelection(from: Int, to: Int): TextRange {
        val a = from.coerceIn(0, visible.length)
        val b = to.coerceIn(a, visible.length)
        if (a == b) return TextRange(sourceCursor(a))
        return TextRange(parsed.sourceOffset(toPlain[a]), parsed.sourceEnd(toPlain[b - 1] + 1))
    }

    companion object {
        private val taskPattern = Regex("""^[-*+] \[([ xX])] """)
        private val bulletPattern = Regex("""^[-*+] """)
        private val numberPattern = Regex("""^\d+[.)] """)
        private val linkPattern = Regex("""!?\[([^\]\n]*)]\(([^)\s]+)\)""")

        fun of(source: String): TextLayout {
            val parsed = RichTextCodec.read(source)
            val plain = parsed.rich.text
            val keep = BooleanArray(plain.length) { true }
            val linkTail = BooleanArray(plain.length)
            val lines = ArrayList<TextLine>()

            var fence: String? = null
            var start = 0
            while (true) {
                val end = plain.indexOf('\n', start).let { if (it < 0) plain.length else it }
                val text = plain.substring(start, end)
                val trimmed = text.trimStart()

                val line = when {
                    fence != null && RichTextCodec.closesFence(trimmed, fence) -> {
                        fence = null
                        simple(start, end, LineKind.FENCE)
                    }

                    fence != null -> simple(start, end, if (fence == "$$") LineKind.FORMULA else LineKind.CODE)

                    RichTextCodec.opensFence(trimmed) != null -> {
                        fence = RichTextCodec.opensFence(trimmed)
                        simple(start, end, LineKind.FENCE)
                    }

                    else -> shape(text, start, end)
                }
                lines += line

                for (i in line.start until line.start + line.open) keep[i] = false
                if (line.prefixHidden) {
                    for (i in line.start + line.open until line.contentStart) keep[i] = false
                }
                for (i in line.contentEnd until line.end) keep[i] = false

                if (line.kind != LineKind.CODE && line.kind != LineKind.FORMULA && line.kind != LineKind.FENCE) {
                    // Z odnośnika i zdjęcia w zdaniu widać sam opis.
                    val content = plain.substring(line.contentStart, line.contentEnd)
                    for (match in linkPattern.findAll(content)) {
                        val alt = match.groups[1] ?: continue
                        val from = line.contentStart + match.range.first
                        val altFrom = line.contentStart + alt.range.first
                        val altTo = altFrom + alt.value.length
                        val to = line.contentStart + match.range.last + 1
                        for (i in from until altFrom) keep[i] = false
                        for (i in altTo until to) {
                            keep[i] = false
                            linkTail[i] = true
                        }
                    }
                }

                if (end >= plain.length) break
                start = end + 1
            }

            val toVisible = IntArray(plain.length + 1)
            val toPlain = IntArray(plain.length + 1)
            val visible = StringBuilder(plain.length)
            for (i in plain.indices) {
                toVisible[i] = visible.length
                if (keep[i]) {
                    toPlain[visible.length] = i
                    visible.append(plain[i])
                }
            }
            toVisible[plain.length] = visible.length
            toPlain[visible.length] = plain.length

            val lineVisibleStart = IntArray(lines.size) { toVisible[lines[it].start] }

            return TextLayout(
                source = source,
                parsed = parsed,
                lines = lines,
                keep = keep,
                linkTail = linkTail,
                visible = visible.toString(),
                toVisible = toVisible,
                toPlain = toPlain.copyOf(visible.length + 1),
                lineVisibleStart = lineVisibleStart,
            )
        }

        private fun simple(start: Int, end: Int, kind: LineKind) = TextLine(
            start = start,
            end = end,
            kind = kind,
            level = 0,
            align = null,
            open = 0,
            prefix = 0,
            prefixHidden = false,
            close = 0,
        )

        /** Budowa zwykłego wiersza: ułożenie, nagłówek, cytat, lista. */
        private fun shape(text: String, start: Int, end: Int): TextLine {
            val open = ParagraphAlign.openingLength(text)
            val align = if (open > 0) ParagraphAlign.alignOf(text) else null
            val inner = text.substring(open)
            val indent = inner.length - inner.trimStart().length
            val trimmed = inner.substring(indent)

            var kind = LineKind.PARAGRAPH
            var level = 0
            var prefix = 0
            var hidden = false

            val heading = RichTextCodec.headingPrefixLength(trimmed)
            val task = taskPattern.find(trimmed)
            val bullet = bulletPattern.find(trimmed)
            val number = numberPattern.find(trimmed)
            when {
                heading > 0 -> {
                    kind = LineKind.HEADING
                    level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                    prefix = indent + heading
                    hidden = true
                }

                trimmed.startsWith("> ") -> {
                    kind = LineKind.QUOTE
                    prefix = indent + 2
                    hidden = true
                }

                task != null -> {
                    kind = LineKind.TASK
                    prefix = indent + task.value.length
                }

                bullet != null -> {
                    kind = LineKind.BULLET
                    prefix = indent + bullet.value.length
                }

                number != null -> {
                    kind = LineKind.NUMBER
                    prefix = indent + number.value.length
                }

                trimmed == "---" || trimmed == "***" || trimmed == "___" -> kind = LineKind.RULE
            }

            var close = ParagraphAlign.closingLength(text, open)
            if (text.length - close < open + prefix) close = 0

            return TextLine(
                start = start,
                end = end,
                kind = kind,
                level = level,
                align = align,
                open = open,
                prefix = prefix,
                prefixHidden = hidden,
                close = close,
            )
        }
    }
}
