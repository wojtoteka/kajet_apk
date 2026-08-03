package wojtoteka.ovh.kajet.export

import wojtoteka.ovh.kajet.core.model.MindMapContent
import wojtoteka.ovh.kajet.core.model.NoteDocument

object MarkdownExport {

    fun convert(document: NoteDocument): String = buildString {
        append("# ").append(document.title).append("\n\n")

        if (document.tags.isNotEmpty()) {
            append(document.tags.joinToString(" ") { "#$it" })
            append("\n\n")
        }

        when {
            document.text != null -> append(document.text!!.markdown)
            document.mindMap != null -> append(fromMindMap(document.mindMap!!))
            document.handwriting != null -> append(fromHandwriting(document))
        }
        if (!endsWith("\n")) append('\n')
    }

    private fun fromMindMap(map: MindMapContent): String = buildString {
        val byId = map.nodes.associateBy { it.id }
        val hasParent = map.edges.map { it.toId }.toSet()
        val children = map.edges.groupBy({ it.fromId }, { it.toId })
        val visited = mutableSetOf<String>()

        fun walk(id: String, depth: Int) {
            if (!visited.add(id)) return
            val node = byId[id] ?: return
            append("  ".repeat(depth))
            append("- ")
            append(node.text.ifBlank { "Bez podpisu" })
            append('\n')
            children[id].orEmpty().forEach { walk(it, depth + 1) }
        }

        map.nodes.filter { it.id !in hasParent }.forEach { walk(it.id, 0) }
        map.nodes.forEach { walk(it.id, 0) }
    }

    private fun fromHandwriting(document: NoteDocument): String = buildString {
        val pages = document.handwriting?.pages.orEmpty()
        var anything = false
        pages.forEachIndexed { index, page ->
            val pieces = (page.recognized.map { it.text } + page.texts.map { it.text })
                .filter { it.isNotBlank() }
            if (pieces.isEmpty()) return@forEachIndexed
            anything = true
            append("## Strona ").append(index + 1).append("\n\n")
            pieces.forEach { append(it).append("\n\n") }
        }
        if (!anything) {
            append(
                "Ta notatka jest pisana odręcznie i nie ma w niej jeszcze tekstu. " +
                    "Zaznacz pismo lassem i wybierz zamianę na tekst, a potem wyeksportuj ponownie.\n",
            )
        }
    }
}
