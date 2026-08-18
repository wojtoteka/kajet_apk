package wojtoteka.ovh.kajet.editor.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Wygląd notatki tekstowej w polu do pisania.
 *
 * Człowiek nie ma oglądać znaczków: pogrubione ma być grube, a nie „**grube**".
 * Dlatego znaczniki są tu ZDEJMOWANE z tego, co widać — także pod kursorem.
 *
 * Czyta je ten sam [RichTextCodec], którym formatuje pasek narzędzi. To jest
 * cała rzecz: jeden parser na wyświetlanie i na formatowanie, więc nie ma jak
 * się rozjechać. Wcześniej były dwa i to one puszczały do treści goły HTML —
 * pasek pisał znacznik, którego pole do pisania nie umiało odczytać.
 *
 * Robota dzieli się na dwa przejścia:
 *   1. [RichTextCodec.read] zdejmuje formaty fragmentu (gwiazdki, znaczniki
 *      barwy i wielkości) i oddaje czysty tekst z ich zakresami,
 *   2. [blockPass] zdejmuje z tego, co zostało, znaczniki budowy wiersza
 *      (kratki nagłówka, grawisy, adres odnośnika).
 *
 * Uwaga na [OffsetMapping]: Compose sprawdza je przy każdym naciśnięciu
 * i niespójne mapowanie wywraca całe pole. Dlatego oba przeliczenia składają
 * się z tych samych dwóch map — nie da się ich rozjechać.
 */
class InlineStyle(
    private val textColor: Color,
    private val markerColor: Color,
    private val highlightColor: Color,
    private val codeColor: Color,
    private val monoFont: FontFamily,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val plan = plan(text.text)
        return TransformedText(plan.shown, plan.mapping)
    }

    /**
     * Sam wygląd, bez zdejmowania znaczników — do podglądu i do testów, które
     * pilnują, żeby ozdabianie nie ruszało treści.
     */
    fun style(source: String): AnnotatedString {
        val parsed = RichTextCodec.read(source)
        val builder = AnnotatedString.Builder(source)
        for (span in stylesOf(parsed)) {
            val from = parsed.sourceOffset(span.from)
            val to = parsed.sourceEnd(span.to)
            if (to > from) builder.addStyle(span.style, from, to)
        }
        return builder.toAnnotatedString()
    }

    // --- Co gdzie stoi ---

    private data class Span(val from: Int, val to: Int, val style: SpanStyle)

    /** Wynik czytania wiersza: co pokolorować i które kawałki są samą składnią. */
    private class Marked {
        val spans = mutableListOf<Span>()

        /** Zakresy będące wyłącznie znacznikiem — da się je schować. */
        val markers = mutableListOf<IntRange>()

        fun style(from: Int, to: Int, style: SpanStyle) {
            if (to > from) spans += Span(from, to, style)
        }

        fun marker(from: Int, to: Int) {
            if (to > from) markers += from until to
        }
    }

    private class Plan(val shown: AnnotatedString, val mapping: OffsetMapping)

    /**
     * Wszystko, co ma być widać, w miarach czystego tekstu. Najpierw budowa
     * wiersza, potem formaty fragmentu — żeby wybrana barwa wygrywała
     * z barwą nagłówka, a nie odwrotnie.
     */
    private fun stylesOf(parsed: RichTextCodec.Parsed): List<Span> {
        val plain = parsed.rich.text
        return blockPass(plain).spans + attrSpans(plain, parsed.attrs)
    }

    private fun plan(source: String): Plan {
        val parsed = RichTextCodec.read(source)
        val plain = parsed.rich.text
        val block = blockPass(plain)
        val spans = block.spans + attrSpans(plain, parsed.attrs)

        val length = plain.length
        val keep = BooleanArray(length) { true }
        for (range in block.markers) {
            for (i in range) if (i in 0 until length) keep[i] = false
        }

        /*
          Jedna para tablic na oba kierunki. `toShown[i]` mówi, gdzie
          w widocznym tekście wypada i-ty znak czystego tekstu; `toPlain[j]`
          odwrotnie. Znak ukryty wskazuje na początek tego, co go zastąpiło,
          więc kursor postawiony w środku znacznika ląduje tuż przed nim.
        */
        val toShown = IntArray(length + 1)
        val toPlain = ArrayList<Int>(length + 1)
        val visible = StringBuilder(length)

        for (i in 0 until length) {
            toShown[i] = visible.length
            if (keep[i]) {
                toPlain += i
                visible.append(plain[i])
            }
        }
        toShown[length] = visible.length
        toPlain += length

        val builder = AnnotatedString.Builder(visible.toString())
        for (span in spans) {
            val from = toShown[span.from.coerceIn(0, length)]
            val to = toShown[span.to.coerceIn(0, length)]
            if (to > from) builder.addStyle(span.style, from, to)
        }

        val shownLength = visible.length
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                toShown[parsed.plainOffset(offset).coerceIn(0, length)]

            override fun transformedToOriginal(offset: Int): Int =
                parsed.sourceOffset(toPlain[offset.coerceIn(0, shownLength)])
        }

        return Plan(builder.toAnnotatedString(), mapping)
    }

    // --- Formaty fragmentu ---

    /** Ciągi znaków o tym samym formacie, każdy jako jeden wygląd. */
    private fun attrSpans(plain: String, attrs: List<RichTextCodec.Attrs>): List<Span> {
        val spans = mutableListOf<Span>()
        var from = 0
        while (from < plain.length) {
            val current = attrs.getOrElse(from) { RichTextCodec.NONE }
            var to = from + 1
            while (to < plain.length && attrs.getOrElse(to) { RichTextCodec.NONE } == current) to++
            if (current != RichTextCodec.NONE) spans += Span(from, to, styleOf(current))
            from = to
        }
        return spans
    }

    private fun styleOf(attrs: RichTextCodec.Attrs): SpanStyle {
        val decorations = buildList {
            if (attrs.underline) add(TextDecoration.Underline)
            if (attrs.strikethrough) add(TextDecoration.LineThrough)
        }
        return SpanStyle(
            fontWeight = if (attrs.bold) FontWeight.Bold else null,
            fontStyle = if (attrs.italic) FontStyle.Italic else null,
            textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
            background = if (attrs.highlight) highlightColor else Color.Unspecified,
            // Wybrana barwa wygrywa z barwą kodu — kto pokolorował kawałek
            // kodu, chce go widzieć w swojej barwie.
            color = attrs.color?.let { Color(it) }
                ?: if (attrs.code) codeColor else Color.Unspecified,
            fontFamily = if (attrs.code) monoFont else null,
            fontSize = attrs.sizePx?.sp ?: TextUnit.Unspecified,
        )
    }

    // --- Budowa wiersza ---

    /**
     * Znaczniki, które nie są formatem fragmentu, tylko układem notatki:
     * nagłówki, listy, cytaty, bloki kodu, kod w zdaniu, odnośniki i zdjęcia.
     * Formatów fragmentu już tu nie ma — zdjął je [RichTextCodec.read].
     */
    private fun blockPass(plain: String): Marked {
        val marked = Marked()

        val lines = plain.split('\n')
        var lineStart = 0
        var fence: String? = null

        for ((index, line) in lines.withIndex()) {
            val lineEnd = lineStart + line.length
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length

            /*
              Wiersz znacznika chowa się RAZEM ze swoim znakiem końca linii —
              inaczej po schowanym „```" zostawałby pusty wiersz i blok kodu
              rozpychałby notatkę. Znacznik otwierający zabiera koniec wiersza
              stojący ZA nim, domykający ten PRZED nim.
            */
            val withNewlineAfter = if (index < lines.lastIndex) lineEnd + 1 else lineEnd
            val withNewlineBefore = (lineStart - 1).coerceAtLeast(0)

            when {
                fence == null && RichTextCodec.opensFence(trimmed) != null -> {
                    fence = RichTextCodec.opensFence(trimmed)
                    marked.style(lineStart, lineEnd, markerStyle)
                    marked.marker(lineStart, withNewlineAfter)
                }

                fence != null && RichTextCodec.closesFence(trimmed, fence) -> {
                    marked.style(lineStart, lineEnd, markerStyle)
                    marked.marker(withNewlineBefore, lineEnd)
                    fence = null
                }

                // Kod idzie swoją czcionką i barwą, wzór samą czcionką —
                // to nie kod, tylko zapis matematyczny.
                fence == "```" -> marked.style(lineStart, lineEnd, codeStyle)
                fence == "$$" -> marked.style(lineStart, lineEnd, formulaStyle)

                else -> styleLine(marked, trimmed, lineStart + indent, lineEnd)
            }

            lineStart = lineEnd + 1
        }
        return marked
    }

    private fun styleLine(marked: Marked, trimmed: String, from: Int, to: Int) {
        val prefix = RichTextCodec.headingPrefixLength(trimmed)
        if (prefix > 0) {
            val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
            marked.style(from, from + prefix, markerStyle)
            marked.marker(from, from + prefix)
            marked.style(from + prefix, to, headingStyle(level))
            inlineParts(marked, trimmed.drop(prefix), from + prefix)
            return
        }

        if (trimmed.startsWith("> ")) {
            marked.style(from, from + 2, markerStyle)
            marked.marker(from, from + 2)
            marked.style(from + 2, to, quoteStyle)
            inlineParts(marked, trimmed.drop(2), from + 2)
            return
        }

        val task = taskPattern.find(trimmed)
        if (task != null) {
            val length = task.value.length
            marked.style(from, from + length, bulletStyle)
            if (task.groupValues[1].lowercase() == "x") {
                marked.style(from + length, to, doneStyle)
            }
            // Kwadracik zadania zostaje widoczny: to nie ozdoba, tylko
            // informacja, czy rzecz jest zrobiona.
            inlineParts(marked, trimmed.drop(length), from + length)
            return
        }

        val bullet = bulletPattern.find(trimmed) ?: numberPattern.find(trimmed)
        if (bullet != null) {
            marked.style(from, from + bullet.value.length, bulletStyle)
            inlineParts(marked, trimmed.drop(bullet.value.length), from + bullet.value.length)
            return
        }

        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            marked.style(from, to, markerStyle)
            return
        }

        inlineParts(marked, trimmed, from)
    }

    /**
     * Odnośniki i zdjęcia — jedyne znaczniki, jakie tu zostały. Kod w zdaniu
     * jest formatem fragmentu i zdjął go już [RichTextCodec.read].
     */
    private fun inlineParts(marked: Marked, text: String, from: Int) {
        // Zdjęcia i odnośniki: opis zostaje czytelny, adres się chowa.
        for (match in linkPattern.findAll(text)) {
            val alt = match.groups[1] ?: continue
            marked.style(from + alt.range.first, from + alt.range.last + 1, linkStyle)
            val url = match.groups[2] ?: continue
            marked.style(from + url.range.first - 1, from + url.range.last + 2, markerStyle)
            // Z odnośnika zostaje sam opis; adres i nawiasy chowają się.
            marked.marker(from + match.range.first, from + alt.range.first)
            marked.marker(from + alt.range.last + 1, from + match.range.last + 1)
        }
    }

    private fun headingStyle(level: Int) = SpanStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = when (level) {
            1 -> 1.7f.em
            2 -> 1.4f.em
            3 -> 1.2f.em
            else -> 1.05f.em
        },
        color = textColor,
    )

    private val markerStyle = SpanStyle(color = markerColor)
    private val bulletStyle = SpanStyle(color = markerColor, fontWeight = FontWeight.Medium)
    private val quoteStyle = SpanStyle(color = markerColor, fontStyle = FontStyle.Italic)
    private val doneStyle = SpanStyle(
        color = markerColor,
        textDecoration = TextDecoration.LineThrough,
    )
    private val codeStyle = SpanStyle(
        fontFamily = monoFont,
        color = codeColor,
        fontSize = TextUnit.Unspecified,
    )
    private val formulaStyle = SpanStyle(
        fontFamily = monoFont,
        color = textColor,
        fontSize = TextUnit.Unspecified,
    )
    private val linkStyle = SpanStyle(
        color = codeColor,
        textDecoration = TextDecoration.Underline,
    )

    private companion object {
        val taskPattern = Regex("""^[-*+] \[([ xX])] """)
        val bulletPattern = Regex("""^[-*+] """)
        val numberPattern = Regex("""^\d+[.)] """)
        val linkPattern = Regex("""!?\[([^\]\n]*)]\(([^)\s]+)\)""")
    }
}
