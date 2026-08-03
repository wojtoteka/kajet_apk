package wojtoteka.ovh.kajet.editor.text

import kotlin.math.roundToInt

sealed interface Block {
    val key: String

    data class Text(override val key: String, val content: String) : Block

    data class Task(
        override val key: String,
        val done: Boolean,
        val content: String,
    ) : Block

    data class Image(
        override val key: String,
        val alt: String,
        val url: String,
        /**
         * Ile szerokości notatki zajmuje zdjęcie, od 0.1 do 1. Zapisuje się
         * w tytule obrazka w markdownie, więc plik nadal czyta każdy inny
         * program, tylko procent go nie obchodzi.
         */
        val width: Float = FULL_WIDTH,
    ) : Block {
        val attachmentName: String?
            get() = if (url.startsWith(ATTACHMENT_PREFIX)) {
                url.removePrefix(ATTACHMENT_PREFIX)
            } else {
                null
            }
    }

    companion object {
        const val ATTACHMENT_PREFIX = "assets/"
        const val FULL_WIDTH = 1f
        const val SMALLEST_WIDTH = 0.1f
    }
}

object Blocks {

    private val imageOnly =
        Regex("""^\s*!\[([^\]]*)]\(([^)\s]+)(?:\s+"([^"]*)")?\)\s*$""")

    private val percentTitle = Regex("""^(\d{1,3})%$""")

    private val taskOnly = Regex("""^\s*[-*+] \[([ xX])] ?(.*)$""")

    fun split(markdown: String): List<Block> {
        val result = mutableListOf<Block>()
        val buffer = StringBuilder()
        var inCode = false
        var number = 0

        fun closeText() {
            if (buffer.isEmpty()) return
            // Puste wiersze na obu końcach są tylko odstępem między akapitami,
            // a odstęp dokłada z powrotem join. Gdybyśmy je tu zostawili,
            // notatka rosłaby o pusty wiersz przy każdym otwarciu.
            val content = buffer.toString().trim('\n')
            buffer.clear()
            if (content.isNotEmpty()) result += Block.Text("t${number++}", content)
        }

        for (line in markdown.split('\n')) {
            if (line.trimStart().startsWith("```")) {
                inCode = !inCode
                buffer.append(line).append('\n')
                continue
            }

            val image = if (inCode) null else imageOnly.find(line)
            if (image != null) {
                closeText()
                result += Block.Image(
                    key = "o${number++}",
                    alt = image.groupValues[1],
                    url = image.groupValues[2],
                    width = widthFromTitle(image.groupValues[3]),
                )
                continue
            }

            val task = if (inCode) null else taskOnly.find(line)
            if (task != null) {
                closeText()
                result += Block.Task(
                    key = "z${number++}",
                    done = task.groupValues[1].lowercase() == "x",
                    content = task.groupValues[2],
                )
            } else {
                buffer.append(line).append('\n')
            }
        }
        closeText()

        // Pusta notatka też ma mieć jeden blok, bo inaczej nie byłoby gdzie pisać.
        if (result.isEmpty()) result += Block.Text("t0", "")

        // Pod ostatnim zdjęciem musi zostać miejsce na pisanie. Bez tego notatka,
        // która kończy się zdjęciem, nie ma już żadnego pola tekstowego i nie da
        // się w niej dopisać ani słowa. Do treści ten pusty akapit nie trafia,
        // bo join zdejmuje puste akapity z końca.
        if (result.last() is Block.Image) result += Block.Text("t${number++}", "")
        return result
    }

    private fun widthFromTitle(title: String): Float {
        val percent = percentTitle.find(title.trim())?.groupValues?.get(1)?.toIntOrNull()
            ?: return Block.FULL_WIDTH
        return (percent / 100f).coerceIn(Block.SMALLEST_WIDTH, Block.FULL_WIDTH)
    }

    fun join(blocks: List<Block>): String {
        // Puste akapity z końca to miejsce na pisanie, a nie treść notatki.
        // Gdyby szły do pliku, notatka rosłaby o pusty wiersz przy każdym zapisie.
        val written = blocks.dropLastWhile { it is Block.Text && it.content.isEmpty() }
        return buildString {
            for ((index, block) in written.withIndex()) {
                append(render(block))
                if (index != written.lastIndex) append(separator(block, written[index + 1]))
            }
        }
    }

    private fun render(block: Block): String = when (block) {
        is Block.Text -> block.content
        is Block.Image -> if (block.width >= Block.FULL_WIDTH) {
            "![${block.alt}](${block.url})"
        } else {
            "![${block.alt}](${block.url} \"${(block.width * 100).roundToInt()}%\")"
        }
        is Block.Task -> "- [${if (block.done) "x" else " "}] ${block.content}"
    }

    private fun separator(before: Block, after: Block): String =
        if (before is Block.Task && after is Block.Task) "\n" else "\n\n"

    fun setText(blocks: List<Block>, key: String, content: String): List<Block> =
        blocks.map { block ->
            when {
                block.key != key -> block
                block is Block.Text -> block.copy(content = content)
                block is Block.Task -> block.copy(content = content)
                else -> block
            }
        }

    fun toggleTask(blocks: List<Block>, key: String): List<Block> =
        blocks.map { block ->
            if (block is Block.Task && block.key == key) {
                block.copy(done = !block.done)
            } else {
                block
            }
        }

    fun splitTask(blocks: List<Block>, key: String, content: String): List<Block> {
        val position = blocks.indexOfFirst { it.key == key }
        if (position < 0) return blocks
        val done = (blocks[position] as? Block.Task)?.done ?: false

        val before = content.substringBefore('\n')
        val after = content.substringAfter('\n').replace("\n", " ").trim()

        val result = blocks.toMutableList()
        if (before.isBlank() && after.isBlank()) {
            // Puste zadanie i klawisz nowej linii: koniec listy.
            result[position] = Block.Text(freshKey(blocks), "")
            return result
        }

        result[position] = Block.Task(key, done, before)
        result.add(position + 1, Block.Task(freshKey(blocks), done = false, content = after))
        return result
    }

    private fun freshKey(blocks: List<Block>): String {
        var number = blocks.size
        val taken = blocks.mapTo(HashSet()) { it.key }
        while ("n$number" in taken) number++
        return "n$number"
    }

    fun remove(blocks: List<Block>, key: String): List<Block> {
        val left = blocks.filterNot { it.key == key }
        return left.ifEmpty { listOf(Block.Text("t0", "")) }
    }

    fun move(blocks: List<Block>, key: String, up: Boolean): List<Block> {
        val from = blocks.indexOfFirst { it.key == key }
        if (from < 0) return blocks
        val to = if (up) from - 1 else from + 1
        if (to !in blocks.indices) return blocks

        return blocks.toMutableList().apply {
            val moved = removeAt(from)
            add(to, moved)
        }
    }

    fun setAlt(blocks: List<Block>, key: String, alt: String): List<Block> =
        blocks.map { block ->
            if (block is Block.Image && block.key == key) block.copy(alt = alt) else block
        }

    fun setImageWidth(blocks: List<Block>, key: String, width: Float): List<Block> =
        blocks.map { block ->
            if (block is Block.Image && block.key == key) {
                block.copy(width = width.coerceIn(Block.SMALLEST_WIDTH, Block.FULL_WIDTH))
            } else {
                block
            }
        }

    fun endPosition(blocks: List<Block>, key: String): Int {
        var position = 0
        for ((index, block) in blocks.withIndex()) {
            position += render(block).length
            if (block.key == key) return position
            if (index != blocks.lastIndex) position += separator(block, blocks[index + 1]).length
        }
        return position
    }
}
