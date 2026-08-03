package wojtoteka.ovh.kajet.editor

import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.MindMapContent
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.NotePage
import wojtoteka.ovh.kajet.core.model.TextBoxElement

sealed interface Change {
    fun applyTo(document: NoteDocument): NoteDocument
    fun revert(document: NoteDocument): NoteDocument
}

data class StrokeChange(
    val page: Int,
    val removed: List<Pair<Int, InkStroke>> = emptyList(),
    val added: List<InkStroke> = emptyList(),
) : Change {

    override fun applyTo(document: NoteDocument): NoteDocument =
        document.withPage(page) { sheet ->
            val goneIds = removed.map { it.second.id }.toSet()
            val kept = if (goneIds.isEmpty()) {
                sheet.strokes
            } else {
                sheet.strokes.filterNot { it.id in goneIds }
            }
            sheet.copy(strokes = kept + added)
        }

    override fun revert(document: NoteDocument): NoteDocument =
        document.withPage(page) { sheet ->
            val addedIds = added.map { it.id }.toSet()
            val withoutAdded = if (addedIds.isEmpty()) {
                sheet.strokes.toMutableList()
            } else {
                sheet.strokes.filterNot { it.id in addedIds }.toMutableList()
            }
            for ((position, stroke) in removed.sortedBy { it.first }) {
                withoutAdded.add(position.coerceIn(0, withoutAdded.size), stroke)
            }
            sheet.copy(strokes = withoutAdded)
        }
}

data class FieldChange(
    val page: Int,
    val before: List<TextBoxElement>,
    val after: List<TextBoxElement>,
) : Change {
    override fun applyTo(document: NoteDocument) =
        document.withPage(page) { it.copy(texts = after) }

    override fun revert(document: NoteDocument) =
        document.withPage(page) { it.copy(texts = before) }
}

data class PageChange(
    val before: List<NotePage>,
    val after: List<NotePage>,
) : Change {
    override fun applyTo(document: NoteDocument) = document.withPages(after)
    override fun revert(document: NoteDocument) = document.withPages(before)
}

data class MapChange(
    val before: MindMapContent,
    val after: MindMapContent,
) : Change {
    override fun applyTo(document: NoteDocument) = document.copy(mindMap = after)
    override fun revert(document: NoteDocument) = document.copy(mindMap = before)
}

class ChangeHistory(private val maxSteps: Int = 120) {

    private val back = ArrayDeque<Change>()
    private val forward = ArrayDeque<Change>()

    val canUndo: Boolean get() = back.isNotEmpty()
    val canRedo: Boolean get() = forward.isNotEmpty()

    fun record(change: Change) {
        back.addLast(change)
        while (back.size > maxSteps) back.removeFirst()
        forward.clear()
    }

    fun undo(document: NoteDocument): NoteDocument? {
        val change = back.removeLastOrNull() ?: return null
        forward.addLast(change)
        return change.revert(document)
    }

    fun redo(document: NoteDocument): NoteDocument? {
        val change = forward.removeLastOrNull() ?: return null
        back.addLast(change)
        return change.applyTo(document)
    }

    fun clear() {
        back.clear()
        forward.clear()
    }
}

// Pomocnicze przekształcenia dokumentu

fun NoteDocument.withPage(index: Int, change: (NotePage) -> NotePage): NoteDocument {
    val handwriting = handwriting ?: return this
    if (index !in handwriting.pages.indices) return this
    val pages = handwriting.pages.toMutableList()
    pages[index] = change(pages[index])
    return copy(handwriting = handwriting.copy(pages = pages))
}

fun NoteDocument.withPages(pages: List<NotePage>): NoteDocument {
    val handwriting = handwriting ?: return this
    return copy(handwriting = handwriting.copy(pages = pages))
}

fun NoteDocument.page(index: Int): NotePage? = handwriting?.pages?.getOrNull(index)
