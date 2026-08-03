package wojtoteka.ovh.kajet.editor.text

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

object TextFormat {

    fun wrap(field: TextFieldValue, marker: String): TextFieldValue {
        val content = field.text
        val from = field.selection.min.coerceIn(0, content.length)
        val to = field.selection.max.coerceIn(from, content.length)
        val middle = content.substring(from, to)

        // Znacznik już tam jest: zdejmujemy go zamiast dokładać drugi.
        val before = content.substring(0, from)
        val after = content.substring(to)
        if (before.endsWith(marker) && after.startsWith(marker)) {
            val next = before.dropLast(marker.length) + middle + after.drop(marker.length)
            val start = from - marker.length
            return TextFieldValue(next, TextRange(start, start + middle.length))
        }

        val inserted = middle.ifEmpty { "tekst" }
        val next = before + marker + inserted + marker + after
        val start = from + marker.length
        return TextFieldValue(next, TextRange(start, start + inserted.length))
    }

    fun wrapPair(field: TextFieldValue, opening: String, closing: String): TextFieldValue {
        val content = field.text
        val from = field.selection.min.coerceIn(0, content.length)
        val to = field.selection.max.coerceIn(from, content.length)
        val middle = content.substring(from, to)

        val before = content.substring(0, from)
        val after = content.substring(to)
        if (before.endsWith(opening) && after.startsWith(closing)) {
            val next = before.dropLast(opening.length) + middle + after.drop(closing.length)
            val start = from - opening.length
            return TextFieldValue(next, TextRange(start, start + middle.length))
        }

        val inserted = middle.ifEmpty { "tekst" }
        val next = before + opening + inserted + closing + after
        val start = from + opening.length
        return TextFieldValue(next, TextRange(start, start + inserted.length))
    }

    fun beforeLine(field: TextFieldValue, marker: String): TextFieldValue {
        val content = field.text
        val cursor = field.selection.start.coerceIn(0, content.length)

        val lineStart = content.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        val lineEnd = content.indexOf('\n', lineStart).let { if (it < 0) content.length else it }
        val line = content.substring(lineStart, lineEnd)

        return if (line.startsWith(marker)) {
            val next = content.removeRange(lineStart, lineStart + marker.length)
            val cursorAfter = (cursor - marker.length).coerceAtLeast(lineStart)
            TextFieldValue(next, TextRange(cursorAfter))
        } else {
            val next = content.substring(0, lineStart) + marker + content.substring(lineStart)
            TextFieldValue(next, TextRange(cursor + marker.length))
        }
    }

    fun insert(field: TextFieldValue, fragment: String, stepBack: Int = 0): TextFieldValue {
        val content = field.text
        val from = field.selection.min.coerceIn(0, content.length)
        val to = field.selection.max.coerceIn(from, content.length)

        val next = content.substring(0, from) + fragment + content.substring(to)
        val cursor = (from + fragment.length - stepBack).coerceIn(0, next.length)
        return TextFieldValue(next, TextRange(cursor))
    }
}
