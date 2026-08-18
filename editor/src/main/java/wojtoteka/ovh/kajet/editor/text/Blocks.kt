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

    /**
     * Tabelka. Pierwszy wiersz to nagłówek — tak samo czyta ją markdown
     * i tak samo pokazuje strona. W pliku zostaje zwykłym zapisem
     * markdownu (`| Kolumna | Kolumna |`), więc nic się nie zmienia ani
     * dla serwera, ani dla eksportu.
     */
    data class Table(
        override val key: String,
        val rows: List<List<String>>,
    ) : Block {
        val columns: Int get() = rows.maxOfOrNull { it.size } ?: 0

        fun cell(row: Int, column: Int): String = rows.getOrNull(row)?.getOrNull(column).orEmpty()
    }

    data class Image(
        override val key: String,
        val alt: String,
        val url: String,
        /**
         * Ile szerokości notatki zajmuje zdjęcie, od 0.01 do 1.
         *
         * Kanoniczny zapis to `![opis|60%](assets/plik.png)` — tak samo
         * na stronie. Stary zapis z tabletu, `![opis](assets/plik.png "60%")`,
         * nadal czytamy. Przy zapisie ściągamy do 20–100%, jak serwer.
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
        /** Najmniejsza szerokość przy zapisie i suwaku — ten sam próg co serwer. */
        const val SMALLEST_WIDTH = 0.2f
    }
}

object Blocks {

    // URL łapany leniwie, bo magazyn potrafi nadać nazwę ze spacją i nawiasem
    // ("zdjecie (2).jpg"); tytuł w cudzysłowie (stary zapis szerokości) ma
    // pierwszeństwo przed URL-em.
    private val imageOnly =
        Regex("""^\s*!\[([^\]]*)]\((.+?)(?:\s+"([^"]*)")?\)\s*$""")

    private val altWidth = Regex("""^(.*?)\s*\|\s*(\d{1,3})\s*%$""")
    private val percentTitle = Regex("""^(\d{1,3})%$""")

    private val taskOnly = Regex("""^\s*[-*+] \[([ xX])] ?(.*)$""")

    /** Znacznik zadania dokładany przez pasek narzędzi. */
    const val TASK_MARKER = "- [ ] "

    /** Czy wiersz jest zadaniem — czyli czy zmieni budowę notatki. */
    fun isTaskLine(line: String): Boolean = taskOnly.matches(line)

    /** Sama treść zadania, bez kwadracika. */
    fun taskContent(line: String): String =
        taskOnly.find(line)?.groupValues?.get(2).orEmpty()

    private val tableRow = Regex("""^\s*\|(.*)\|\s*$""")

    /**
     * Wiersz z kreskami pod nagłówkiem — sama składnia, nie treść. Myślnik
     * musi w nim stać: bez tego pusty wiersz „|  |  |" też wyglądałby jak
     * kreski i znikałby z tabelki.
     */
    private val tableRule = Regex("""^\s*\|[\s|:-]*-[\s|:-]*\|\s*$""")

    private fun cellsOf(line: String): List<String> =
        (tableRow.find(line)?.groupValues?.get(1) ?: "").split('|').map { it.trim() }

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

        val lines = markdown.split('\n')
        var at = 0
        while (at < lines.size) {
            val line = lines[at]

            if (line.trimStart().startsWith("```")) {
                inCode = !inCode
                buffer.append(line).append('\n')
                at++
                continue
            }

            // Tabelka: kolejne wiersze z kreskami zbierają się w jeden blok,
            // żeby dało się ją pokazać jako tabelkę, a nie jako wiersze pełne
            // kresek. Wiersz z myślnikami to sama składnia — nie treść.
            if (!inCode && tableRow.matches(line)) {
                closeText()
                val rows = mutableListOf<List<String>>()
                while (at < lines.size && tableRow.matches(lines[at])) {
                    if (!tableRule.matches(lines[at])) rows += cellsOf(lines[at])
                    at++
                }
                if (rows.isNotEmpty()) result += Block.Table("b${number++}", rows)
                continue
            }

            val image = if (inCode) null else imageOnly.find(line)
            if (image != null) {
                closeText()
                val (alt, width) = readImageSize(image.groupValues[1], image.groupValues[3])
                result += Block.Image(
                    key = "o${number++}",
                    alt = alt,
                    url = image.groupValues[2].trim(),
                    width = width,
                )
                at++
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
            at++
        }
        closeText()

        // Pusta notatka też ma mieć jeden blok, bo inaczej nie byłoby gdzie pisać.
        if (result.isEmpty()) result += Block.Text("t0", "")

        // Pod ostatnim zdjęciem musi zostać miejsce na pisanie. Bez tego notatka,
        // która kończy się zdjęciem, nie ma już żadnego pola tekstowego i nie da
        // się w niej dopisać ani słowa. Do treści ten pusty akapit nie trafia,
        // bo join zdejmuje puste akapity z końca.
        if (result.last() is Block.Image || result.last() is Block.Table) {
            result += Block.Text("t${number++}", "")
        }
        return result
    }

    /**
     * Opis i szerokość z obu zapisów: `![opis|60%](url)` oraz
     * `![opis](url "60%")`. Z pliku bierzemy, co stoi (także 10%);
     * dolny próg 20% obowiązuje dopiero przy zapisie.
     */
    private fun readImageSize(rawAlt: String, title: String): Pair<String, Float> {
        val fromAlt = altWidth.find(rawAlt)
        if (fromAlt != null) {
            return fromAlt.groupValues[1] to percentFromFile(fromAlt.groupValues[2])
        }
        val fromTitle = percentTitle.find(title.trim())?.groupValues?.get(1)
        if (fromTitle != null) {
            return rawAlt to percentFromFile(fromTitle)
        }
        return rawAlt to Block.FULL_WIDTH
    }

    private fun percentFromFile(raw: String): Float {
        val percent = raw.toIntOrNull() ?: return Block.FULL_WIDTH
        return percent.coerceIn(1, 100) / 100f
    }

    /** Kanoniczny opis: sam tekst, a przy węższym zdjęciu dopisek `|NN%`. */
    private fun writeImageAlt(alt: String, width: Float): String {
        val clean = readImageSize(alt, "").first
        val percent = (width * 100).roundToInt().coerceIn(
            (Block.SMALLEST_WIDTH * 100).roundToInt(),
            (Block.FULL_WIDTH * 100).roundToInt(),
        )
        return if (width >= Block.FULL_WIDTH || percent >= 100) clean else "$clean|$percent%"
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
        is Block.Image -> "![${writeImageAlt(block.alt, block.width)}](${block.url})"
        is Block.Task -> "- [${if (block.done) "x" else " "}] ${block.content}"
        is Block.Table -> renderTable(block)
    }

    /** Tabelka z powrotem na markdown: nagłówek, kreski, reszta wierszy. */
    private fun renderTable(table: Block.Table): String {
        val columns = table.columns.coerceAtLeast(1)
        fun line(cells: List<String>): String =
            (0 until columns).joinToString(" | ", "| ", " |") { cells.getOrNull(it).orEmpty() }

        val out = mutableListOf(line(table.rows.firstOrNull().orEmpty()))
        out += (0 until columns).joinToString(" | ", "| ", " |") { "---" }
        for (row in table.rows.drop(1)) out += line(row)
        return out.joinToString("\n")
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

    // --- Tabelka ---

    private fun mapTable(
        blocks: List<Block>,
        key: String,
        change: (Block.Table) -> Block.Table,
    ): List<Block> = blocks.map { block ->
        if (block is Block.Table && block.key == key) change(block) else block
    }

    /** Wyrównuje wiersze do tej samej liczby kolumn — tabelka ma być prostokątem. */
    private fun squared(rows: List<List<String>>): List<List<String>> {
        val columns = (rows.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1)
        return rows.map { row -> List(columns) { row.getOrNull(it).orEmpty() } }
    }

    fun setCell(
        blocks: List<Block>,
        key: String,
        row: Int,
        column: Int,
        text: String,
    ): List<Block> = mapTable(blocks, key) { table ->
        val rows = squared(table.rows).toMutableList()
        if (row !in rows.indices) return@mapTable table
        val cells = rows[row].toMutableList()
        if (column !in cells.indices) return@mapTable table
        // Kreska pionowa rozbiłaby tabelkę na kolumny przy najbliższym
        // odczycie, więc w treści komórki jej nie ma.
        cells[column] = text.replace("|", "/").replace("\n", " ")
        rows[row] = cells
        table.copy(rows = rows)
    }

    fun addRow(blocks: List<Block>, key: String, after: Int): List<Block> =
        mapTable(blocks, key) { table ->
            val rows = squared(table.rows).toMutableList()
            val columns = rows.firstOrNull()?.size ?: 1
            rows.add((after + 1).coerceIn(0, rows.size), List(columns) { "" })
            table.copy(rows = rows)
        }

    fun addColumn(blocks: List<Block>, key: String, after: Int): List<Block> =
        mapTable(blocks, key) { table ->
            val at = (after + 1).coerceIn(0, table.columns)
            table.copy(rows = squared(table.rows).map { row -> row.toMutableList().apply { add(at, "") } })
        }

    fun removeRow(blocks: List<Block>, key: String, row: Int): List<Block> =
        mapTable(blocks, key) { table ->
            // Nagłówek zostaje: tabelka bez niego przestaje być tabelką.
            val rows = squared(table.rows)
            if (rows.size <= 1 || row !in rows.indices) {
                table
            } else {
                table.copy(rows = rows.filterIndexed { index, _ -> index != row })
            }
        }

    fun removeColumn(blocks: List<Block>, key: String, column: Int): List<Block> =
        mapTable(blocks, key) { table ->
            val rows = squared(table.rows)
            if (table.columns <= 1 || column !in 0 until table.columns) {
                table
            } else {
                table.copy(rows = rows.map { row -> row.filterIndexed { index, _ -> index != column } })
            }
        }

    /** Pusta tabelka do wstawienia: nagłówek i jeden wiersz. */
    fun emptyTable(heading: String): String =
        "\n| $heading | $heading |\n| --- | --- |\n|  |  |\n"

    fun toggleTask(blocks: List<Block>, key: String): List<Block> =
        blocks.map { block ->
            if (block is Block.Task && block.key == key) {
                block.copy(done = !block.done)
            } else {
                block
            }
        }

    /**
     * Nowy układ bloków i blok, w którym ma stanąć kursor.
     *
     * Klucz jest tu tak samo ważny jak same bloki: nowa pozycja listy dostaje
     * własny klucz, więc jej pole do pisania jest NOWE. Bez oddania tego klucza
     * skupienie zostawało w poprzednim wierszu i wszystko pisane po klawiszu
     * nowej linii doklejało się do niego.
     */
    class Split(val blocks: List<Block>, val focusKey: String)

    fun splitTask(blocks: List<Block>, key: String, content: String): Split {
        val position = blocks.indexOfFirst { it.key == key }
        if (position < 0) return Split(blocks, key)
        val done = (blocks[position] as? Block.Task)?.done ?: false

        val before = content.substringBefore('\n')
        val after = content.substringAfter('\n').replace("\n", " ").trim()

        val result = blocks.toMutableList()
        if (before.isBlank() && after.isBlank()) {
            // Puste zadanie i klawisz nowej linii: koniec listy.
            val fresh = freshKey(blocks)
            result[position] = Block.Text(fresh, "")
            return Split(result, fresh)
        }

        val fresh = freshKey(blocks)
        result[position] = Block.Task(key, done, before)
        result.add(position + 1, Block.Task(fresh, done = false, content = after))
        return Split(result, fresh)
    }

    /**
     * Zamienia zadanie w zwykły wiersz zaczynający się od [marker].
     *
     * Nagłówek, cytat i punkt to budowa wiersza, a nie format fragmentu:
     * doklejone do treści zadania dawały „- [ ] > cytat", czyli zadanie ze
     * znacznikiem w środku. Wiersz może być albo zadaniem, albo cytatem —
     * pusty [marker] robi z zadania zwykły akapit.
     */
    fun taskToLine(blocks: List<Block>, key: String, marker: String): Split? {
        val position = blocks.indexOfFirst { it.key == key }
        if (position < 0) return null
        val task = blocks[position] as? Block.Task ?: return null

        val fresh = freshKey(blocks)
        val result = blocks.toMutableList()
        result[position] = Block.Text(fresh, marker + task.content)
        return Split(result, fresh)
    }

    /**
     * Miejsce do pisania na końcu notatki. Kiedy ostatni blok jest akapitem,
     * wystarczy w nim stanąć; pod listą, zdjęciem albo tabelką dokładamy pusty
     * akapit. Do pliku on nie trafia — [join] zdejmuje puste akapity z końca.
     */
    fun appendParagraph(blocks: List<Block>): Split {
        val last = blocks.lastOrNull()
        if (last is Block.Text) return Split(blocks, last.key)
        val fresh = freshKey(blocks)
        return Split(blocks + Block.Text(fresh, ""), fresh)
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
            if (block is Block.Image && block.key == key) {
                block.copy(alt = readImageSize(alt, "").first)
            } else {
                block
            }
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
