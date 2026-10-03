package wojtoteka.ovh.kajet.editor.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import wojtoteka.ovh.kajet.core.model.NoteAlign
import wojtoteka.ovh.kajet.core.model.RichTextCodec

/**
 * Wygląd notatki tekstowej w polu do pisania.
 *
 * Człowiek nie ma oglądać znaczków: pogrubione ma być grube, a nie „**grube**".
 * Dlatego znaczniki są tu ZDEJMOWANE z tego, co widać - także pod kursorem.
 *
 * Czyta je ten sam [RichTextCodec], którym formatuje pasek narzędzi. To jest
 * cała rzecz: jeden parser na wyświetlanie i na formatowanie, więc nie ma jak
 * się rozjechać. Wcześniej były dwa i to one puszczały do treści goły HTML -
 * pasek pisał znacznik, którego pole do pisania nie umiało odczytać.
 *
 * Robota dzieli się na dwa przejścia:
 *   1. [RichTextCodec.read] zdejmuje formaty fragmentu (gwiazdki, znaczniki
 *      barwy i wielkości) i oddaje czysty tekst z ich zakresami,
 *   2. [TextLayout] zdejmuje z tego, co zostało, znaczniki budowy wiersza
 *      (kratki nagłówka, znacznik ułożenia akapitu, adres odnośnika).
 *
 * [TextLayout] liczy też pisanie ([TextEdit]), więc to, czego nie widać,
 * nie da się też rozbić klawiaturą.
 *
 * Uwaga na [OffsetMapping]: Compose sprawdza je przy każdym naciśnięciu
 * i niespójne mapowanie wywraca całe pole. Dlatego oba przeliczenia składają
 * się z tych samych dwóch map - nie da się ich rozjechać.
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
     * Sam wygląd, bez zdejmowania znaczników - do podglądu i do testów, które
     * pilnują, żeby ozdabianie nie ruszało treści.
     */
    fun style(source: String): AnnotatedString {
        val layout = TextLayout.of(source)
        val parsed = layout.parsed
        val builder = AnnotatedString.Builder(source)
        for (span in stylesOf(layout)) {
            val from = parsed.sourceOffset(span.from)
            val to = parsed.sourceEnd(span.to)
            if (to > from) builder.addStyle(span.style, from, to)
        }
        return builder.toAnnotatedString()
    }

    // --- Co gdzie stoi ---

    private data class Span(val from: Int, val to: Int, val style: SpanStyle)

    private class Plan(val shown: AnnotatedString, val mapping: OffsetMapping)

    /**
     * Wszystko, co ma być widać, w miarach czystego tekstu. Najpierw budowa
     * wiersza, potem formaty fragmentu - żeby wybrana barwa wygrywała
     * z barwą nagłówka, a nie odwrotnie.
     */
    private fun stylesOf(layout: TextLayout): List<Span> =
        lineSpans(layout) + attrSpans(layout.plain, layout.parsed.attrs)

    private fun plan(source: String): Plan {
        val layout = TextLayout.of(source)
        val spans = stylesOf(layout)

        val builder = AnnotatedString.Builder(shownText(layout))
        for (span in spans) {
            val from = layout.visibleOfPlain(span.from)
            val to = layout.visibleOfPlain(span.to)
            if (to > from) builder.addStyle(span.style, from, to)
        }

        /*
          Ułożenie akapitu. Każdy wiersz ze znacznikiem dostaje własny
          ParagraphStyle - razem ze swoim znakiem końca wiersza, żeby kursor
          w pustym akapicie też stał tam, gdzie będzie tekst.
        */
        val shownLength = layout.visible.length
        for ((index, line) in layout.lines.withIndex()) {
            val align = line.align ?: continue
            val from = layout.lineVisibleStart(index)
            val lineEnd = layout.lineVisibleEnd(index)
            val to = if (index < layout.lines.lastIndex) lineEnd + 1 else lineEnd
            if (to > from && to <= shownLength) {
                builder.addStyle(ParagraphStyle(textAlign = textAlignOf(align)), from, to)
            }
        }

        /*
          Uwaga: Compose sprawdza mapowanie przy każdym naciśnięciu i wartość
          spoza tekstu wywraca pole. Obie strony liczy [TextLayout], ten sam,
          którym pisanie przelicza każdą zmianę - nie mają jak się rozjechać.
        */
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                layout.visibleOfSource(offset.coerceIn(0, source.length)).coerceIn(0, shownLength)

            override fun transformedToOriginal(offset: Int): Int =
                layout.sourceCursor(offset.coerceIn(0, shownLength)).coerceIn(0, source.length)
        }

        return Plan(builder.toAnnotatedString(), mapping)
    }

    /** Widoczny tekst; znak punktu listy pokazuje się jako kropka, jak w edytorach tekstu. */
    private fun shownText(layout: TextLayout): String {
        val shown = StringBuilder(layout.visible)
        for ((index, line) in layout.lines.withIndex()) {
            if (line.kind != LineKind.BULLET) continue
            val at = layout.visibleOfPlain(line.start + line.open)
            // Wcięcie podlisty zostaje, kropka staje w miejscu znaku.
            var marker = at
            while (marker < shown.length && shown[marker] == ' ') marker++
            if (marker < shown.length && shown[marker] in "-*+" &&
                marker < layout.lineVisibleEnd(index)
            ) {
                shown.setCharAt(marker, '\u2022')
            }
        }
        return shown.toString()
    }

    private fun textAlignOf(align: NoteAlign): TextAlign = when (align) {
        NoteAlign.LEFT -> TextAlign.Left
        NoteAlign.CENTER -> TextAlign.Center
        NoteAlign.RIGHT -> TextAlign.Right
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
        val heading = attrs.heading
        return SpanStyle(
            fontWeight = when {
                attrs.bold -> FontWeight.Bold
                heading != null -> FontWeight.SemiBold
                else -> null
            },
            fontStyle = if (attrs.italic) FontStyle.Italic else null,
            textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
            background = if (attrs.highlight) highlightColor else Color.Unspecified,
            // Wybrana barwa wygrywa z barwą kodu - kto pokolorował kawałek
            // kodu, chce go widzieć w swojej barwie.
            color = attrs.color?.let { Color(it) }
                ?: if (attrs.code) codeColor else Color.Unspecified,
            fontFamily = if (attrs.code) monoFont else null,
            // Wielkość nadana fragmentowi wygrywa z wielkością nagłówka -
            // jak bezpośrednie formatowanie ze stylem w Wordzie.
            fontSize = attrs.sizePx?.sp
                ?: heading?.let { RichTextCodec.headingScale(it).em }
                ?: TextUnit.Unspecified,
        )
    }

    // --- Budowa wiersza ---

    /**
     * Wygląd wynikający z budowy wiersza: nagłówki, cytaty, listy, linie,
     * odnośniki. Co z tego jest ukryte, mówi [TextLayout].
     */
    private fun lineSpans(layout: TextLayout): List<Span> {
        val spans = mutableListOf<Span>()
        val plain = layout.plain
        fun style(from: Int, to: Int, style: SpanStyle) {
            if (to > from) spans += Span(from, to, style)
        }

        for (line in layout.lines) {
            val prefixStart = line.start + line.open
            when (line.kind) {
                // Nagłówek to format znaku (RichTextCodec.Attrs.heading) -
                // treść wiersza „# Tytuł" dostaje go przy czytaniu.
                LineKind.HEADING -> Unit
                LineKind.QUOTE -> style(line.contentStart, line.contentEnd, quoteStyle)
                LineKind.TASK -> {
                    style(prefixStart, line.contentStart, bulletStyle)
                    val done = plain.substring(prefixStart, line.contentStart).contains('x', ignoreCase = true)
                    if (done) style(line.contentStart, line.contentEnd, doneStyle)
                }

                LineKind.BULLET, LineKind.NUMBER -> style(prefixStart, line.contentStart, bulletStyle)
                LineKind.RULE, LineKind.FENCE -> style(line.contentStart, line.contentEnd, markerStyle)
                // Kod idzie swoją czcionką i barwą, wzór samą czcionką -
                // to nie kod, tylko zapis matematyczny.
                LineKind.CODE -> style(line.start, line.end, codeStyle)
                LineKind.FORMULA -> style(line.start, line.end, formulaStyle)
                LineKind.PARAGRAPH -> Unit
            }

            if (line.kind == LineKind.CODE || line.kind == LineKind.FORMULA || line.kind == LineKind.FENCE) continue
            // Zdjęcia i odnośniki: opis zostaje czytelny, adres się chowa.
            val content = plain.substring(line.contentStart, line.contentEnd)
            for (match in linkPattern.findAll(content)) {
                val alt = match.groups[1] ?: continue
                val from = line.contentStart + alt.range.first
                style(from, from + alt.value.length, linkStyle)
            }
        }
        return spans
    }

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
        val linkPattern = Regex("""!?\[([^\]\n]*)]\(([^)\s]+)\)""")
    }
}
