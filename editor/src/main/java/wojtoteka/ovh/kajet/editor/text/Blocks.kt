package wojtoteka.ovh.kajet.editor.text

import wojtoteka.ovh.kajet.core.model.ImageLines
import wojtoteka.ovh.kajet.core.model.NoteAlign
import wojtoteka.ovh.kajet.core.model.NotePhoto
import wojtoteka.ovh.kajet.core.model.RichTextCodec

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

    /**
     * Blok kodu (```) albo wzoru ($$). W pliku zostaje zwykłym zapisem
     * markdownu z płotami, ale w notatce jest osobnym polem z czcionką
     * maszynową - płotów nie widać i nie da się ich rozbić klawiaturą.
     *
     * Dawniej kod siedział w akapicie, a płoty były w nim tylko schowane:
     * Backspace na początku kodu zjadał ukryty koniec wiersza, płot sklejał
     * się z kodem („```print(1)") i cała treść znikała albo wychodziła
     * na wierzch razem z grawisami.
     */
    data class Code(
        override val key: String,
        /** Wiersz otwierający, znak w znak: „```", „```python" albo „$$". */
        val open: String,
        val content: String,
        /** Wiersz domykający, znak w znak - zwykle taki sam jak otwierający. */
        val close: String = closingFor(open),
    ) : Block {
        val isFormula: Boolean get() = open.trim() == FORMULA_FENCE

        /** Język z wiersza otwierającego („```python" -> „python"). */
        val language: String get() = if (isFormula) "" else open.trim().removePrefix(CODE_FENCE).trim()
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
        const val CODE_FENCE = "```"
        const val FORMULA_FENCE = "$$"

        fun closingFor(open: String): String =
            if (open.trim() == FORMULA_FENCE) FORMULA_FENCE else CODE_FENCE

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

            /*
              Blok kodu albo wzoru: od płotu do płotu. Zbiera się w jeden blok,
              żeby w notatce był osobnym polem bez widocznych płotów. Płot bez
              domknięcia ciągnie się do końca notatki - tak samo czyta go strona.
            */
            val fence = RichTextCodec.opensFence(line.trimStart())
            if (fence != null) {
                closeText()
                val body = mutableListOf<String>()
                at++
                var close: String? = null
                while (at < lines.size) {
                    if (RichTextCodec.closesFence(lines[at].trimStart(), fence)) {
                        close = lines[at]
                        at++
                        break
                    }
                    body += lines[at]
                    at++
                }
                result += Block.Code(
                    key = "k${number++}",
                    open = line,
                    content = body.joinToString("\n"),
                    close = close ?: Block.closingFor(line),
                )
                continue
            }

            // Tabelka: kolejne wiersze z kreskami zbierają się w jeden blok,
            // żeby dało się ją pokazać jako tabelkę, a nie jako wiersze pełne
            // kresek. Wiersz z myślnikami to sama składnia - nie treść.
            if (tableRow.matches(line)) {
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
            val photos = ImageLines.read(line)
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

            val task = taskOnly.find(line)
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
        if (result.last() is Block.Image || result.last() is Block.Table || result.last() is Block.Code) {
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
        // Zadanie, którego cała treść jest nagłówkiem, ma w polu „# Tytuł" -
        // w pliku nagłówek idzie znacznikiem, bo „- [ ] # Tytuł" to dla
        // markdownu kratka w treści zadania.
        is Block.Task -> "- [${if (block.done) "x" else " "}] ${RichTextCodec.headingAsSpan(block.content)}"
        is Block.Table -> renderTable(block)
        is Block.Code -> if (block.content.isEmpty()) {
            "${block.open}\n${block.close}"
        } else {
            "${block.open}\n${block.content}\n${block.close}"
        }
    }

    private fun photoOf(block: Block.Image): NotePhoto =
        NotePhoto(alt = block.alt, url = block.url, width = block.width, align = block.align)

    /** Tabelka z powrotem na markdown: nagłówek, kreski, reszta wierszy. */
    private fun renderTable(table: Block.Table): String {
        val columns = table.columns.coerceAtLeast(1)
        // Komórka, której cała treść jest nagłówkiem, ma w polu „# Tytuł";
        // w tabelce markdownu kratki byłyby zwykłym tekstem.
        fun line(cells: List<String>): String =
            (0 until columns).joinToString(" | ", "| ", " |") {
                RichTextCodec.headingAsSpan(cells.getOrNull(it).orEmpty())
            }

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

    // --- Kod i wzór ---

    fun setCode(blocks: List<Block>, key: String, content: String): List<Block> =
        blocks.map { block ->
            if (block is Block.Code && block.key == key) {
                // Płot wpisany w treść kodu zamknąłby blok w połowie przy
                // najbliższym odczycie - reszta kodu wyszłaby na wierzch.
                // Niewidoczna spacja przed nim zostawia go treścią.
                val fence = if (block.isFormula) Block.FORMULA_FENCE else Block.CODE_FENCE
                block.copy(
                    content = content.split('\n').joinToString("\n") { line ->
                        if (RichTextCodec.closesFence(line.trimStart(), fence)) "\u200B$line" else line
                    },
                )
            } else {
                block
            }
        }

    /**
     * Wstawia pusty blok kodu (albo wzoru) w miejscu kursora.
     *
     * Kursor stoi w akapicie [key], w zapisie na pozycji [cursor]. Pusty
     * wiersz pod kursorem zamienia się w blok; wiersz z treścią zostaje,
     * a blok staje zaraz pod nim - reszta akapitu idzie pod blok. Bez kursora
     * blok idzie na koniec notatki. Pod blokiem zawsze zostaje miejsce na
     * dalsze pisanie.
     */
    fun insertCode(blocks: List<Block>, key: String?, cursor: Int, fence: String): Split {
        val code = Block.Code(key = freshKey(blocks), open = fence, content = "")
        val position = blocks.indexOfFirst { it.key == key }
        val result = blocks.toMutableList()

        val host = blocks.getOrNull(position)
        if (host is Block.Text) {
            val content = host.content
            val at = cursor.coerceIn(0, content.length)
            val lineStart = content.lastIndexOf('\n', (at - 1).coerceAtLeast(0))
                .let { if (it < 0 || at == 0) 0 else it + 1 }
            val lineEnd = content.indexOf('\n', at).let { if (it < 0) content.length else it }
            val emptyLine = content.substring(lineStart, lineEnd).isBlank()
            val before = (if (emptyLine) content.substring(0, lineStart) else content.substring(0, lineEnd))
                .trimEnd('\n')
            val after = content.substring(lineEnd).trimStart('\n')

            result.removeAt(position)
            var at2 = position
            if (before.isNotEmpty()) result.add(at2++, host.copy(content = before))
            result.add(at2++, code)
            if (after.isNotEmpty()) {
                result.add(at2, Block.Text(freshKey(result), after))
            }
        } else if (position >= 0) {
            result.add(position + 1, code)
        } else {
            // Bez kursora: na koniec, ale przed pustym akapitem do pisania.
            val trailing = result.lastOrNull()
            if (trailing is Block.Text && trailing.content.isEmpty() && result.size > 1) {
                result.add(result.lastIndex, code)
            } else {
                result.add(code)
            }
        }

        if (result.last() !is Block.Text) result.add(Block.Text(freshKey(result), ""))
        return Split(result, code.key)
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

    /**
     * Klucz komórki tabelki, w której stoi kursor - pasek formatowania
     * rozpoznaje po nim, że pisze do komórki, a nie do akapitu.
     */
    fun cellKey(table: String, row: Int, column: Int): String = "$table#$row#$column"

    /** Tabelka, wiersz i kolumna z klucza komórki; null, gdy to nie komórka. */
    fun cellOf(key: String): Triple<String, Int, Int>? {
        val parts = key.split('#')
        if (parts.size != 3) return null
        val row = parts[1].toIntOrNull() ?: return null
        val column = parts[2].toIntOrNull() ?: return null
        return Triple(parts[0], row, column)
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

    /** Czy zdjęcie stoi w wierszu z innymi - czyli czy ma z czego zejść. */
    fun standsInRow(blocks: List<Block>, key: String): Boolean {
        val at = blocks.indexOfFirst { it.key == key }
        val image = blocks.getOrNull(at) as? Block.Image ?: return false
        if (image.inRow) return true
        val next = blocks.getOrNull(at + 1)
        return next is Block.Image && next.inRow
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

        if (beside) {
            val align = (blocks[at - 1] as Block.Image).align
            return blocks.toMutableList().apply {
                this[at] = image.copy(inRow = true, align = align)
            }
        }

        /*
          Zdjęcie stojące obok poprzedniego po prostu z wiersza wychodzi. Kiedy
          to ono wiersz ZACZYNA, z wiersza schodzi to, co stoi za nim - inaczej
          pierwszego zdjęcia nie dałoby się odsunąć od reszty, bo znacznik
          „stoję obok" ma sąsiad, a nie ono.
        */
        val leaving = if (image.inRow) at else at + 1
        val target = blocks.getOrNull(leaving) as? Block.Image ?: return blocks
        if (!target.inRow) return blocks
        return blocks.toMutableList().apply {
            this[leaving] = target.copy(inRow = false)
        }
    }

    /** Kierunek, w którym zdjęcie idzie od jednego przesunięcia. */
    enum class PhotoNudge { LEFT, RIGHT, UP, DOWN }

    /**
     * Zdjęcie przesunięte palcem albo rysikiem - o jedno miejsce w podanym
     * kierunku.
     *
     * Wiersz ze zdjęciami leży w poprzek, a notatka w pionie, więc kierunek
     * znaczy w nim co innego:
     *
     * - w bok, mając sąsiada w wierszu: zamiana miejscami z tym sąsiadem;
     * - w prawo, stojąc samo pod zdjęciem: wchodzi do jego wiersza, obok niego;
     * - w pionie, stojąc w wierszu z innymi: schodzi do własnego wiersza;
     * - w pionie, stojąc samo: idzie o jeden blok wyżej albo niżej.
     */
    fun nudgePhoto(blocks: List<Block>, key: String, nudge: PhotoNudge): List<Block> {
        val at = blocks.indexOfFirst { it.key == key }
        if (blocks.getOrNull(at) !is Block.Image) return blocks
        val row = rows(blocks).firstOrNull { group -> group.any { it.key == key } } ?: return blocks
        val inside = row.indexOfFirst { it.key == key }
        val alone = row.size == 1

        return when (nudge) {
            PhotoNudge.LEFT ->
                if (inside > 0) move(blocks, key, up = true) else blocks

            PhotoNudge.RIGHT -> when {
                inside < row.lastIndex -> move(blocks, key, up = false)
                alone && canStandBeside(blocks, key) -> setSideBySide(blocks, key, beside = true)
                else -> blocks
            }

            PhotoNudge.UP ->
                if (alone) move(blocks, key, up = true) else setSideBySide(blocks, key, beside = false)

            PhotoNudge.DOWN ->
                if (alone) move(blocks, key, up = false) else setSideBySide(blocks, key, beside = false)
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
