package wojtoteka.ovh.kajet.editor.text

import wojtoteka.ovh.kajet.core.model.ImageLines
import wojtoteka.ovh.kajet.core.model.NoteAlign
import wojtoteka.ovh.kajet.core.model.NotePhoto

sealed interface Block {
    val key: String

    data class Text(override val key: String, val content: String) : Block

    data class Task(
        override val key: String,
        val done: Boolean,
        val content: String,
    ) : Block

    /**
     * Tabelka. Pierwszy wiersz to nagłówek - tak samo czyta ją markdown
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
         * Kanoniczny zapis to `![opis|60%](assets/plik.png)` - tak samo
         * na stronie. Stary zapis z tabletu, `![opis](assets/plik.png "60%")`,
         * nadal czytamy. Przy zapisie ściągamy do 20-100%, jak serwer.
         */
        val width: Float = FULL_WIDTH,
        /**
         * Gdzie w wierszu stoją zdjęcia: przy lewej krawędzi, na środku albo
         * przy prawej. Dotyczy całego wiersza, więc zdjęcia stojące obok
         * siebie mają to samo ułożenie.
         */
        val align: NoteAlign = NoteAlign.LEFT,
        /**
         * Czy zdjęcie stoi OBOK poprzedniego, w tym samym wierszu.
         *
         * W pliku to po prostu jeden wiersz z dwoma zdjęciami. Pierwsze
         * zdjęcie wiersza ma tu `false` - ono ten wiersz zaczyna.
         */
        val inRow: Boolean = false,
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
        const val FULL_WIDTH = ImageLines.FULL_WIDTH
        /** Najmniejsza szerokość przy zapisie i suwaku - ten sam próg co serwer. */
        const val SMALLEST_WIDTH = ImageLines.SMALLEST_WIDTH
    }
}

object Blocks {

    private val taskOnly = Regex("""^\s*[-*+] \[([ xX])] ?(.*)$""")

    /** Znacznik zadania dokładany przez pasek narzędzi. */
    const val TASK_MARKER = "- [ ] "

    /** Czy wiersz jest zadaniem - czyli czy zmieni budowę notatki. */
    fun isTaskLine(line: String): Boolean = taskOnly.matches(line)

    /** Sama treść zadania, bez kwadracika. */
    fun taskContent(line: String): String =
        taskOnly.find(line)?.groupValues?.get(2).orEmpty()

    private val tableRow = Regex("""^\s*\|(.*)\|\s*$""")

    /**
     * Wiersz z kreskami pod nagłówkiem - sama składnia, nie treść. Myślnik
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
            // kresek. Wiersz z myślnikami to sama składnia - nie treść.
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

            // Kilka zdjęć w jednym wierszu pliku to kilka zdjęć stojących
            // obok siebie w notatce. Każde ma swój blok - własną szerokość,
            // podpis i przyciski - a trzyma je razem znacznik [Block.Image.inRow].
            val photos = if (inCode) null else ImageLines.read(line)
            if (photos != null) {
                closeText()
                photos.forEachIndexed { index, photo ->
                    result += Block.Image(
                        key = "o${number++}",
                        alt = photo.alt,
                        url = photo.url,
                        width = photo.width,
                        align = photo.align,
                        inRow = index > 0,
                    )
                }
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
        is Block.Image -> ImageLines.write(photoOf(block))
        is Block.Task -> "- [${if (block.done) "x" else " "}] ${block.content}"
        is Block.Table -> renderTable(block)
    }

    private fun photoOf(block: Block.Image): NotePhoto =
        NotePhoto(alt = block.alt, url = block.url, width = block.width, align = block.align)

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

    private fun separator(before: Block, after: Block): String = when {
        before is Block.Task && after is Block.Task -> "\n"
        // Zdjęcia obok siebie stoją w jednym wierszu pliku, rozdzielone samym
        // odstępem. Pusty wiersz rozsunąłby je z powrotem jedno pod drugie.
        before is Block.Image && after is Block.Image && after.inRow -> " "
        else -> "\n\n"
    }

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

    /** Wyrównuje wiersze do tej samej liczby kolumn - tabelka ma być prostokątem. */
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
     * znacznikiem w środku. Wiersz może być albo zadaniem, albo cytatem -
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
     * akapit. Do pliku on nie trafia - [join] zdejmuje puste akapity z końca.
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
        val rows = rowNumbers(blocks)
        val left = tidyRows(blocks.filterNot { it.key == key }, rows)
        return left.ifEmpty { listOf(Block.Text("t0", "")) }
    }

    fun move(blocks: List<Block>, key: String, up: Boolean): List<Block> {
        val from = blocks.indexOfFirst { it.key == key }
        if (from < 0) return blocks
        val to = if (up) from - 1 else from + 1
        if (to !in blocks.indices) return blocks

        val rows = rowNumbers(blocks)
        val moved = blocks.toMutableList().apply {
            val taken = removeAt(from)
            add(to, taken)
        }
        return tidyRows(moved, rows)
    }

    // --- Zdjęcia obok siebie ---

    /**
     * Numer wiersza dla każdego bloku. Zdjęcia stojące obok siebie mają ten
     * sam numer, wszystko inne swój własny.
     *
     * Po przesunięciu albo usunięciu zdjęcia znacznik „stoję obok poprzedniego"
     * przestaje pasować do nowego sąsiedztwa: pierwsze zdjęcie wiersza mogło
     * wyjechać wyżej, a to, co po nim zostało, wisiałoby przy akapicie.
     * Numery zdjęte PRZED zmianą mówią, co naprawdę było razem, więc
     * [tidyRows] potrafi to potem złożyć z powrotem.
     */
    private fun rowNumbers(blocks: List<Block>): Map<String, Int> {
        val numbers = HashMap<String, Int>(blocks.size)
        var row = 0
        for ((index, block) in blocks.withIndex()) {
            val together = block is Block.Image &&
                block.inRow &&
                blocks.getOrNull(index - 1) is Block.Image
            if (!together) row++
            numbers[block.key] = row
        }
        return numbers
    }

    /** Znaczniki wiersza dopasowane do nowego sąsiedztwa. */
    private fun tidyRows(blocks: List<Block>, rows: Map<String, Int>): List<Block> =
        blocks.mapIndexed { index, block ->
            if (block !is Block.Image) return@mapIndexed block
            val before = blocks.getOrNull(index - 1)
            val together = before is Block.Image && rows[before.key] == rows[block.key]
            if (block.inRow == together) block else block.copy(inRow = together)
        }

    /**
     * Bloki pogrupowane tak, jak stoją w notatce: zdjęcia obok siebie w jednej
     * grupie, każdy inny blok sam.
     */
    fun rows(blocks: List<Block>): List<List<Block>> {
        val result = mutableListOf<MutableList<Block>>()
        for (block in blocks) {
            val last = result.lastOrNull()
            if (block is Block.Image && block.inRow && last?.lastOrNull() is Block.Image) {
                last.add(block)
            } else {
                result += mutableListOf(block)
            }
        }
        return result
    }

    /** Czy zdjęcie ma przed sobą inne zdjęcie, obok którego może stanąć. */
    fun canStandBeside(blocks: List<Block>, key: String): Boolean {
        val at = blocks.indexOfFirst { it.key == key }
        return at > 0 && blocks[at] is Block.Image && blocks[at - 1] is Block.Image
    }

    /**
     * Stawia zdjęcie obok poprzedniego albo odsuwa je do własnego wiersza.
     *
     * Przy dołączeniu zdjęcie bierze ułożenie wiersza, do którego wchodzi -
     * ułożenie ma cały wiersz, a nie pojedyncze zdjęcie.
     */
    fun setSideBySide(blocks: List<Block>, key: String, beside: Boolean): List<Block> {
        val at = blocks.indexOfFirst { it.key == key }
        if (at < 0) return blocks
        val image = blocks[at] as? Block.Image ?: return blocks
        if (beside && !canStandBeside(blocks, key)) return blocks

        val align = if (beside) (blocks[at - 1] as Block.Image).align else image.align
        return blocks.toMutableList().apply {
            this[at] = image.copy(inRow = beside, align = align)
        }
    }

    /** Ułożenie całego wiersza, w którym stoi to zdjęcie. */
    fun setImageAlign(blocks: List<Block>, key: String, align: NoteAlign): List<Block> {
        val row = rows(blocks).firstOrNull { group -> group.any { it.key == key } } ?: return blocks
        val keys = row.mapTo(HashSet()) { it.key }
        return blocks.map { block ->
            if (block is Block.Image && block.key in keys) block.copy(align = align) else block
        }
    }

    fun setAlt(blocks: List<Block>, key: String, alt: String): List<Block> =
        blocks.map { block ->
            if (block is Block.Image && block.key == key) {
                block.copy(alt = ImageLines.plainAlt(alt))
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
