package wojtoteka.ovh.kajet.storage.index

import wojtoteka.ovh.kajet.core.text.words
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.TextMarkers
import wojtoteka.ovh.kajet.core.text.handwritingSummary
import wojtoteka.ovh.kajet.core.text.nodesCount

object IndexText {

    const val PREVIEW_LENGTH = 160

    fun content(document: NoteDocument): String = buildString {
        document.text?.let { append(it.markdown) }

        document.handwriting?.pages?.forEach { page ->
            page.texts.forEach { box ->
                if (box.text.isNotBlank()) {
                    append(box.text)
                    append('\n')
                }
            }
            page.recognized.forEach { recognized ->
                if (recognized.text.isNotBlank()) {
                    append(recognized.text)
                    append('\n')
                }
            }
        }

        document.mindMap?.nodes?.forEach { node ->
            if (node.text.isNotBlank()) {
                append(node.text)
                append('\n')
            }
        }
    }.trim()

    fun preview(document: NoteDocument): String {
        val raw = content(document)
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("![") }
            .map { it.trimStart('#', '>', '-', '*', ' ') }
            // The note list should carry content, not asterisks and colour marks.
            .map { TextMarkers.plain(it) }
            .filter { it.isNotEmpty() }
            .joinToString(" ")

        if (raw.isEmpty()) {
            val mindMap = document.mindMap
            return when {
                document.handwriting != null -> emptyHandwriting(document)
                mindMap != null -> words.nodesCount(mindMap.nodes.size)
                else -> ""
            }
        }
        return if (raw.length <= PREVIEW_LENGTH) {
            raw
        } else {
            raw.take(PREVIEW_LENGTH).substringBeforeLast(' ') + "..."
        }
    }

    private fun emptyHandwriting(document: NoteDocument): String {
        val pages = document.handwriting?.pages ?: return ""
        val strokes = pages.sumOf { it.strokes.size }
        return words.handwritingSummary(strokes, pages.size)
    }
}
