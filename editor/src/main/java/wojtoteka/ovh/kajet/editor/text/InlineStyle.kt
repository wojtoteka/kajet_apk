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
import wojtoteka.ovh.kajet.core.model.TextMarkers

class InlineStyle(
    private val textColor: Color,
    private val markerColor: Color,
    private val highlightColor: Color,
    private val codeColor: Color,
    private val monoFont: FontFamily,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText = TransformedText(
        text = style(text.text),
        offsetMapping = OffsetMapping.Identity,
    )

    fun style(source: String): AnnotatedString = buildStyled(source)

    private fun buildStyled(source: String): AnnotatedString {
        val builder = AnnotatedString.Builder(source)

        var lineStart = 0
        var inCodeBlock = false

        for (line in source.split('\n')) {
            val lineEnd = lineStart + line.length
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length

            when {
                trimmed.startsWith("```") -> {
                    inCodeBlock = !inCodeBlock
                    builder.addStyle(markerStyle, lineStart, lineEnd)
                }

                inCodeBlock -> builder.addStyle(codeStyle, lineStart, lineEnd)

                else -> {
                    val fromIndent = lineStart + indent
                    styleLine(builder, trimmed, fromIndent, lineEnd)
                }
            }

            lineStart = lineEnd + 1
        }
        return builder.toAnnotatedString()
    }

    private fun styleLine(
        builder: AnnotatedString.Builder,
        trimmed: String,
        from: Int,
        to: Int,
    ) {
        val level = trimmed.takeWhile { it == '#' }.length
        if (level in 1..6 && trimmed.length > level && trimmed[level] == ' ') {
            builder.addStyle(markerStyle, from, from + level + 1)
            builder.addStyle(headingStyle(level), from + level + 1, to)
            inlineStyles(builder, trimmed.drop(level + 1), from + level + 1)
            return
        }

        if (trimmed.startsWith("> ")) {
            builder.addStyle(markerStyle, from, from + 2)
            builder.addStyle(quoteStyle, from + 2, to)
            inlineStyles(builder, trimmed.drop(2), from + 2)
            return
        }

        val task = taskPattern.find(trimmed)
        if (task != null) {
            val length = task.value.length
            builder.addStyle(bulletStyle, from, from + length)
            if (task.groupValues[1].lowercase() == "x") {
                builder.addStyle(doneStyle, from + length, to)
            }
            inlineStyles(builder, trimmed.drop(length), from + length)
            return
        }

        val bullet = bulletPattern.find(trimmed) ?: numberPattern.find(trimmed)
        if (bullet != null) {
            builder.addStyle(bulletStyle, from, from + bullet.value.length)
            inlineStyles(builder, trimmed.drop(bullet.value.length), from + bullet.value.length)
            return
        }

        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            builder.addStyle(markerStyle, from, to)
            return
        }

        inlineStyles(builder, trimmed, from)
    }

    private fun inlineStyles(builder: AnnotatedString.Builder, text: String, from: Int) {
        val taken = BooleanArray(text.length)

        fun take(range: IntRange) {
            for (i in range) if (i in taken.indices) taken[i] = true
        }

        fun free(range: IntRange): Boolean = range.none { it in taken.indices && taken[it] }

        fun applyPair(pattern: Regex, opening: Int, closing: Int, style: SpanStyle) {
            for (match in pattern.findAll(text)) {
                val range = match.range
                if (!free(range)) continue
                take(range)
                val start = from + range.first
                val end = from + range.last + 1
                builder.addStyle(markerStyle, start, start + opening)
                builder.addStyle(style, start + opening, end - closing)
                builder.addStyle(markerStyle, end - closing, end)
            }
        }

        for (match in codePattern.findAll(text)) {
            take(match.range)
            builder.addStyle(codeStyle, from + match.range.first, from + match.range.last + 1)
        }

        // Images and links: the label stays readable, the address fades.
        for (match in linkPattern.findAll(text)) {
            if (!free(match.range)) continue
            take(match.range)
            val alt = match.groups[1] ?: continue
            builder.addStyle(linkStyle, from + alt.range.first, from + alt.range.last + 1)
            val url = match.groups[2] ?: continue
            builder.addStyle(markerStyle, from + url.range.first - 1, from + url.range.last + 2)
        }

        // Kolor pisma zapisany po ludzku: <span style="color:#RRGGBB">tekst</span>.
        for (match in colorPattern.findAll(text)) {
            if (!free(match.range)) continue
            take(match.range)
            val color = colorFromText(match.groupValues[1]) ?: continue
            val content = match.groups[2] ?: continue
            val start = from + match.range.first
            builder.addStyle(markerStyle, start, from + content.range.first)
            builder.addStyle(SpanStyle(color = color), from + content.range.first, from + content.range.last + 1)
            builder.addStyle(markerStyle, from + content.range.last + 1, from + match.range.last + 1)
        }

        applyPair(underlinePattern, "<u>".length, "</u>".length, underlineStyle)
        applyPair(boldPattern, 2, 2, SpanStyle(fontWeight = FontWeight.Bold))
        applyPair(strikePattern, 2, 2, SpanStyle(textDecoration = TextDecoration.LineThrough))
        applyPair(highlightPattern, 2, 2, SpanStyle(background = highlightColor))
        applyPair(italicPattern, 1, 1, SpanStyle(fontStyle = FontStyle.Italic))
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
    private val underlineStyle = SpanStyle(textDecoration = TextDecoration.Underline)
    private val linkStyle = SpanStyle(
        color = codeColor,
        textDecoration = TextDecoration.Underline,
    )

    private companion object {
        val taskPattern = Regex("""^[-*+] \[([ xX])] """)
        val bulletPattern = Regex("""^[-*+] """)
        val numberPattern = Regex("""^\d+[.)] """)
        val codePattern = Regex("""`[^`\n]+`""")
        val boldPattern = Regex("""\*\*[^*\n]+\*\*""")
        val italicPattern = Regex("""(?<![*\w])\*[^*\n]+\*(?![*\w])""")
        val strikePattern = Regex("""~~[^~\n]+~~""")
        val highlightPattern = Regex("""==[^=\n]+==""")
        val underlinePattern = Regex("""<u>[^<\n]+</u>""")
        val linkPattern = Regex("""!?\[([^\]\n]*)]\(([^)\s]+)\)""")
        val colorPattern = Regex("""<span style="color:(#[0-9a-fA-F]{6,8})">([^<\n]*)</span>""")

        fun colorFromText(value: String): Color? =
            TextMarkers.colorFromHex(value)?.let { Color(it) }
    }
}
