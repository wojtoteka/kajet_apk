package wojtoteka.ovh.kajet.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.core.model.TextMarkers
import wojtoteka.ovh.kajet.editor.text.Block
import wojtoteka.ovh.kajet.editor.text.Blocks
import wojtoteka.ovh.kajet.editor.text.TextFormat

class BlocksTest {

    @Test
    fun `zdjecie w osobnym wierszu staje sie blokiem obrazkowym`() {
        val blocks = Blocks.split("Przed\n\n![mapa](assets/mapa.png)\n\nPo")

        assertThat(blocks).hasSize(3)
        assertThat((blocks[0] as Block.Text).content).isEqualTo("Przed")
        val image = blocks[1] as Block.Image
        assertThat(image.alt).isEqualTo("mapa")
        assertThat(image.url).isEqualTo("assets/mapa.png")
        assertThat(image.attachmentName).isEqualTo("mapa.png")
        assertThat((blocks[2] as Block.Text).content).isEqualTo("Po")
    }

    @Test
    fun `zdjecie w srodku zdania zostaje tekstem`() {
        // Obrazek wpleciony w zdanie nie jest osobnym blokiem, bo wyrwanie go
        // z akapitu zmieniłoby sens tego, co człowiek napisał.
        val blocks = Blocks.split("Zobacz ![to](assets/a.png) tutaj")

        assertThat(blocks).hasSize(1)
        assertThat(blocks[0]).isInstanceOf(Block.Text::class.java)
    }

    @Test
    fun `obrazek w bloku kodu nie jest obrazkiem`() {
        val markdown = "```\n![to](assets/a.png)\n```"
        val blocks = Blocks.split(markdown)

        assertThat(blocks).hasSize(1)
        assertThat(blocks[0]).isInstanceOf(Block.Text::class.java)
    }

    @Test
    fun `podzial i zlozenie oddaja te sama tresc`() {
        val markdown = "# Tytul\n\nAkapit\n\n![zdjecie](assets/z.png)\n\nKoniec"
        assertThat(Blocks.join(Blocks.split(markdown))).isEqualTo(markdown)
    }

    @Test
    fun `pusta notatka ma jeden blok do pisania`() {
        val blocks = Blocks.split("")

        assertThat(blocks).hasSize(1)
        assertThat((blocks[0] as Block.Text).content).isEmpty()
    }

    @Test
    fun `usuniecie ostatniego bloku zostawia miejsce na pisanie`() {
        val blocks = Blocks.split("![z](assets/z.png)")
        val after = Blocks.remove(blocks, blocks[0].key)

        assertThat(after).hasSize(1)
        assertThat(after[0]).isInstanceOf(Block.Text::class.java)
    }

    @Test
    fun `przesuniecie zdjecia zmienia kolejnosc w tresci`() {
        val blocks = Blocks.split("Pierwszy\n\n![z](assets/z.png)")
        val after = Blocks.move(blocks, blocks[1].key, up = true)

        assertThat(Blocks.join(after)).isEqualTo("![z](assets/z.png)\n\nPierwszy")
    }

    @Test
    fun `pod zdjeciem na koncu notatki zostaje miejsce na pisanie`() {
        val blocks = Blocks.split("Akapit\n\n![z](assets/z.png)")

        assertThat(blocks).hasSize(3)
        assertThat((blocks[2] as Block.Text).content).isEmpty()
        // Puste miejsce do pisania nie dokłada pustych wierszy do pliku.
        assertThat(Blocks.join(blocks)).isEqualTo("Akapit\n\n![z](assets/z.png)")
    }

    @Test
    fun `wielkosc zdjecia jedzie tam i z powrotem`() {
        val blocks = Blocks.split("![z](assets/z.png \"50%\")")

        assertThat((blocks[0] as Block.Image).width).isWithin(0.001f).of(0.5f)
        assertThat(Blocks.join(blocks)).isEqualTo("![z](assets/z.png \"50%\")")
    }

    @Test
    fun `zdjecie bez zapisanej wielkosci zajmuje cala szerokosc`() {
        val blocks = Blocks.split("![z](assets/z.png)")

        assertThat((blocks[0] as Block.Image).width).isWithin(0.001f).of(1f)
    }

    @Test
    fun `zmiana wielkosci zdjecia trafia do tresci`() {
        val blocks = Blocks.split("![z](assets/z.png)")
        val after = Blocks.setImageWidth(blocks, blocks[0].key, 0.25f)

        assertThat(Blocks.join(after)).isEqualTo("![z](assets/z.png \"25%\")")
    }

    @Test
    fun `przesuniecie poza liste nic nie zmienia`() {
        val blocks = Blocks.split("Jedyny")
        assertThat(Blocks.move(blocks, blocks[0].key, up = true)).isEqualTo(blocks)
    }

    @Test
    fun `pozycja konca bloku zgadza sie ze zlozonym tekstem`() {
        val blocks = Blocks.split("Pierwszy\n\n![z](assets/z.png)\n\nOstatni")
        val markdown = Blocks.join(blocks)

        val endOfFirst = Blocks.endPosition(blocks, blocks[0].key)
        assertThat(markdown.substring(0, endOfFirst)).isEqualTo("Pierwszy")
    }

    // Zadania. Po skasowaniu podglądu kwadracik do odhaczenia stoi wprost
    // w treści, więc wiersz zadania musi być osobnym blokiem.

    @Test
    fun `wiersz zadania staje sie blokiem zadania`() {
        val blocks = Blocks.split("- [ ] Kupić chleb\n- [x] Oddać książkę")

        assertThat(blocks).hasSize(2)
        val first = blocks[0] as Block.Task
        assertThat(first.done).isFalse()
        assertThat(first.content).isEqualTo("Kupić chleb")
        val second = blocks[1] as Block.Task
        assertThat(second.done).isTrue()
        assertThat(second.content).isEqualTo("Oddać książkę")
    }

    @Test
    fun `lista zadan sklada sie z powrotem bez pustych wierszy`() {
        val markdown = "- [ ] Kupić chleb\n- [x] Oddać książkę"
        assertThat(Blocks.join(Blocks.split(markdown))).isEqualTo(markdown)
    }

    @Test
    fun `zadania obok akapitow zachowuja odstepy`() {
        val markdown = "Lista zakupów\n\n- [ ] chleb\n- [ ] mleko\n\nKoniec"
        assertThat(Blocks.join(Blocks.split(markdown))).isEqualTo(markdown)
    }

    @Test
    fun `odhaczenie zadania zmienia tylko jego wiersz`() {
        val blocks = Blocks.split("- [ ] Pierwsze\n- [ ] Drugie")
        val after = Blocks.toggleTask(blocks, blocks[1].key)

        assertThat(Blocks.join(after)).isEqualTo("- [ ] Pierwsze\n- [x] Drugie")
    }

    @Test
    fun `odhaczenie dziala w obie strony`() {
        val blocks = Blocks.split("- [x] Gotowe")
        val after = Blocks.toggleTask(blocks, blocks[0].key)

        assertThat(Blocks.join(after)).isEqualTo("- [ ] Gotowe")
    }

    @Test
    fun `zadanie w bloku kodu nie jest zadaniem`() {
        val blocks = Blocks.split("```\n- [ ] to jest przykład\n```")

        assertThat(blocks).hasSize(1)
        assertThat(blocks[0]).isInstanceOf(Block.Text::class.java)
    }

    @Test
    fun `klawisz nowej linii w zadaniu zaczyna nastepne zadanie`() {
        val blocks = Blocks.split("- [ ] chleb")
        val after = Blocks.splitTask(blocks, blocks[0].key, "chleb\nmleko")

        assertThat(Blocks.join(after.blocks)).isEqualTo("- [ ] chleb\n- [ ] mleko")
    }

    /*
      Sedno naprawy listy zakupów: kursor MUSI przejść do świeżej pozycji.
      Dopóki zostawał w poprzedniej, wszystko pisane po klawiszu nowej linii
      doklejało się do niej, a lista rosła o same puste kwadraciki.
    */
    @Test
    fun `kursor przechodzi do swiezej pozycji listy`() {
        val blocks = Blocks.split("- [ ] chleb")
        val after = Blocks.splitTask(blocks, blocks[0].key, "chleb\n")

        assertThat(after.focusKey).isNotEqualTo(blocks[0].key)
        assertThat(after.blocks.first { it.key == after.focusKey })
            .isEqualTo(Block.Task(after.focusKey, done = false, content = ""))
    }

    @Test
    fun `nowe zadanie zaczyna sie nieodhaczone`() {
        val blocks = Blocks.split("- [x] chleb")
        val after = Blocks.splitTask(blocks, blocks[0].key, "chleb\n")

        assertThat(Blocks.join(after.blocks)).isEqualTo("- [x] chleb\n- [ ] ")
    }

    @Test
    fun `klawisz w pustym zadaniu konczy liste`() {
        val blocks = Blocks.split("- [ ] ")
        val after = Blocks.splitTask(blocks, blocks[0].key, "\n")

        assertThat(after.blocks).hasSize(1)
        assertThat(after.blocks[0]).isInstanceOf(Block.Text::class.java)
        // Kursor idzie do akapitu, który został po liście — inaczej człowiek
        // wychodzi z listy i nie ma gdzie pisać dalej.
        assertThat(after.focusKey).isEqualTo(after.blocks[0].key)
    }

    @Test
    fun `rozciecie nie gubi zadan stojacych obok`() {
        val blocks = Blocks.split("- [ ] chleb\n- [ ] maslo")
        val after = Blocks.splitTask(blocks, blocks[0].key, "chleb\nmleko")

        assertThat(Blocks.join(after.blocks)).isEqualTo("- [ ] chleb\n- [ ] mleko\n- [ ] maslo")
    }

    /*
      Nagłówek, cytat i punkt to budowa wiersza, tak samo jak kwadracik zadania.
      Doklejone do treści zadania dawały „- [ ] > cytat" — znacznik na wierzchu
      w środku listy. Wiersz może być albo zadaniem, albo cytatem.
    */
    @Test
    fun `cytat zamienia zadanie w cytat, a nie wchodzi do jego srodka`() {
        val blocks = Blocks.split("- [ ] chleb")
        val after = Blocks.taskToLine(blocks, blocks[0].key, "> ")!!

        assertThat(Blocks.join(after.blocks)).isEqualTo("> chleb")
    }

    @Test
    fun `naglowek zamienia zadanie w naglowek`() {
        val blocks = Blocks.split("- [ ] chleb\n- [ ] maslo")
        val after = Blocks.taskToLine(blocks, blocks[1].key, "## ")!!

        assertThat(Blocks.join(after.blocks)).isEqualTo("- [ ] chleb\n\n## maslo")
    }

    @Test
    fun `pusty znacznik zdejmuje kwadracik i zostawia zwykly akapit`() {
        val blocks = Blocks.split("- [x] chleb")
        val after = Blocks.taskToLine(blocks, blocks[0].key, "")!!

        assertThat(Blocks.join(after.blocks)).isEqualTo("chleb")
        assertThat(after.blocks[0]).isInstanceOf(Block.Text::class.java)
    }

    @Test
    fun `zamiana dziala tylko na zadaniu`() {
        val blocks = Blocks.split("zwykly akapit")

        assertThat(Blocks.taskToLine(blocks, blocks[0].key, "> ")).isNull()
    }

    /*
      Miejsce do pisania pod ostatnim blokiem. Notatka kończąca się listą albo
      tabelką nie miała już żadnego pola tekstowego, więc stuknięcie w pustą
      kartkę pod nią nie miało w co trafić.
    */
    @Test
    fun `pod lista zadan dokladamy akapit do pisania`() {
        val blocks = Blocks.split("- [ ] chleb")
        val after = Blocks.appendParagraph(blocks)

        assertThat(after.blocks).hasSize(2)
        assertThat(after.blocks[1]).isInstanceOf(Block.Text::class.java)
        assertThat(after.focusKey).isEqualTo(after.blocks[1].key)
        // Pusty akapit z końca nie jest treścią i do pliku nie trafia.
        assertThat(Blocks.join(after.blocks)).isEqualTo("- [ ] chleb")
    }

    @Test
    fun `pod akapitem nie dokladamy niczego, tylko stajemy w nim`() {
        val blocks = Blocks.split("Ala ma kota")
        val after = Blocks.appendParagraph(blocks)

        assertThat(after.blocks).isSameInstanceAs(blocks)
        assertThat(after.focusKey).isEqualTo(blocks[0].key)
    }

    @Test
    fun `pozycja konca zadania zgadza sie ze zlozonym tekstem`() {
        val blocks = Blocks.split("- [ ] chleb\n- [ ] mleko\n\nKoniec")
        val markdown = Blocks.join(blocks)

        val end = Blocks.endPosition(blocks, blocks[0].key)
        assertThat(markdown.substring(0, end)).isEqualTo("- [ ] chleb")
    }
}

class TextFormatTest {

    @Test
    fun `wiersz zadania rozpoznaje sie po znaczniku`() {
        assertThat(Blocks.isTaskLine("- [ ] mleko")).isTrue()
        assertThat(Blocks.isTaskLine("- [x] mleko")).isTrue()
        assertThat(Blocks.isTaskLine("  - [ ] wciete")).isTrue()
        assertThat(Blocks.isTaskLine("- mleko")).isFalse()
        assertThat(Blocks.isTaskLine("zwykly wiersz")).isFalse()

        assertThat(Blocks.taskContent("- [ ] mleko")).isEqualTo("mleko")
        assertThat(Blocks.taskContent("- [x] chleb")).isEqualTo("chleb")
        // Puste zadanie tuż po naciśnięciu przycisku.
        assertThat(Blocks.taskContent(Blocks.TASK_MARKER.trimEnd())).isEqualTo("")
    }

    @Test
    fun `znacznik zadania z paska od razu daje blok zadania`() {
        // Tak wygląda treść po naciśnięciu przycisku na pustym wierszu.
        val blocks = Blocks.split("Przed\n\n${Blocks.TASK_MARKER}")

        assertThat(blocks.filterIsInstance<Block.Task>()).hasSize(1)
        assertThat(blocks.filterIsInstance<Block.Task>().single().content).isEmpty()
    }

    @Test
    fun `znacznik wiersza dokladany jest na jego poczatku`() {
        val field = TextFieldValue("Pierwszy\nDrugi", TextRange(11))
        val after = TextFormat.beforeLine(field, "- ")

        assertThat(after.text).isEqualTo("Pierwszy\n- Drugi")
        // Kursor przesuwa się razem z tekstem, więc stoi w tym samym miejscu słowa.
        assertThat(after.selection.start).isEqualTo(13)
    }

    @Test
    fun `powtorne nacisniecie zdejmuje znacznik wiersza`() {
        val field = TextFieldValue("- Zakupy", TextRange(4))
        val after = TextFormat.beforeLine(field, "- ")

        assertThat(after.text).isEqualTo("Zakupy")
    }

    @Test
    fun `wstawienie bloku kodu stawia kursor w srodku`() {
        val after = TextFormat.insert(TextFieldValue("", TextRange(0)), "\n```\n\n```\n", stepBack = 5)

        assertThat(after.text).isEqualTo("\n```\n\n```\n")
        // Kursor ma stanąć w pustym wierszu między znacznikami.
        assertThat(after.text.substring(0, after.selection.start)).isEqualTo("\n```\n")
    }

    @Test
    fun `wstawienie zastepuje zaznaczony fragment`() {
        val field = TextFieldValue("Ala ma kota", TextRange(4, 6))
        val after = TextFormat.insert(field, "nie ma", stepBack = 0)

        assertThat(after.text).isEqualTo("Ala nie ma kota")
    }

    // Podkreślenie, barwa i wielkość fragmentu nie chodzą już przez
    // doklejanie znaczników do tekstu — idą przez model zakresów.
    // Ich testy siedzą w TextFormatTest.kt i RichTextTest.kt.
}

/*
  Tabelka. W pliku notatki zostaje zwykłym markdownem, więc najważniejsze jest
  to, żeby przeszła tam i z powrotem bez straty — inaczej strona i eksport
  zobaczyłyby coś innego niż tablet.
*/
class TableBlockTest {

    private val markdown = "| Imię | Wiek |\n| --- | --- |\n| Ala | 7 |\n| Ola | 9 |"

    private fun table(blocks: List<Block>): Block.Table =
        blocks.filterIsInstance<Block.Table>().single()

    @Test
    fun `wiersze z kreskami staja sie jedna tabelka`() {
        val blocks = Blocks.split(markdown)
        val table = table(blocks)

        assertThat(table.rows).hasSize(3)
        assertThat(table.rows[0]).containsExactly("Imię", "Wiek").inOrder()
        assertThat(table.rows[2]).containsExactly("Ola", "9").inOrder()
        // Wiersz z myślnikami to sama składnia, nie treść.
        assertThat(table.rows.none { it.all { cell -> cell == "---" } }).isTrue()
    }

    @Test
    fun `tabelka wraca na markdown bez straty`() {
        assertThat(Blocks.join(Blocks.split(markdown))).isEqualTo(markdown)
    }

    @Test
    fun `tabelka w srodku notatki nie zjada tekstu wokol`() {
        val note = "Przed\n\n$markdown\n\nPo"
        val blocks = Blocks.split(note)

        assertThat(blocks.filterIsInstance<Block.Text>().map { it.content })
            .containsExactly("Przed", "Po").inOrder()
        assertThat(Blocks.join(blocks)).isEqualTo(note)
    }

    @Test
    fun `pod tabelka na koncu notatki zostaje miejsce na pisanie`() {
        val blocks = Blocks.split(markdown)

        assertThat(blocks.last()).isInstanceOf(Block.Text::class.java)
        // Pusty akapit z końca nie trafia do pliku.
        assertThat(Blocks.join(blocks)).isEqualTo(markdown)
    }

    @Test
    fun `zmiana komorki zostaje w tabelce`() {
        val blocks = Blocks.split(markdown)
        val key = table(blocks).key
        val after = Blocks.setCell(blocks, key, row = 1, column = 1, text = "8")

        assertThat(table(after).cell(1, 1)).isEqualTo("8")
        assertThat(Blocks.join(after)).contains("| Ala | 8 |")
    }

    @Test
    fun `kreska pionowa w komorce nie rozbija tabelki`() {
        val blocks = Blocks.split(markdown)
        val key = table(blocks).key
        val after = Blocks.setCell(blocks, key, 1, 0, "Ala | Ola")

        // Po zapisaniu i ponownym odczycie tabelka ma nadal dwie kolumny.
        assertThat(table(Blocks.split(Blocks.join(after))).columns).isEqualTo(2)
    }

    @Test
    fun `dodanie wiersza i kolumny trzyma prostokat`() {
        val blocks = Blocks.split(markdown)
        val key = table(blocks).key

        val withRow = Blocks.addRow(blocks, key, after = 2)
        assertThat(table(withRow).rows).hasSize(4)

        val withColumn = Blocks.addColumn(withRow, key, after = 1)
        val table = table(withColumn)
        assertThat(table.columns).isEqualTo(3)
        assertThat(table.rows.all { it.size == 3 }).isTrue()
        assertThat(Blocks.join(withColumn)).contains("| --- | --- | --- |")
    }

    @Test
    fun `usuwanie zostawia nagłowek i jedna kolumne`() {
        val blocks = Blocks.split(markdown)
        val key = table(blocks).key

        val fewer = Blocks.removeRow(Blocks.removeRow(blocks, key, 2), key, 1)
        assertThat(table(fewer).rows).hasSize(1)
        // Nagłówek zostaje: tabelka bez niego przestaje być tabelką.
        assertThat(table(Blocks.removeRow(fewer, key, 0)).rows).hasSize(1)

        val narrow = Blocks.removeColumn(blocks, key, 1)
        assertThat(table(narrow).columns).isEqualTo(1)
        assertThat(table(Blocks.removeColumn(narrow, key, 0)).columns).isEqualTo(1)
    }

    @Test
    fun `wstawiona pusta tabelka od razu jest blokiem tabelki`() {
        val blocks = Blocks.split(Blocks.emptyTable("Kolumna"))

        assertThat(table(blocks).rows).hasSize(2)
        assertThat(table(blocks).columns).isEqualTo(2)
    }

    @Test
    fun `tabelka w bloku kodu zostaje tekstem`() {
        val note = "```\n| nie | tabelka |\n```"
        val blocks = Blocks.split(note)

        assertThat(blocks.filterIsInstance<Block.Table>()).isEmpty()
        assertThat(Blocks.join(blocks)).isEqualTo(note)
    }
}

class TextMarkersTest {

    @Test
    fun `gwiazdki i podkreslniki znikaja, tresc zostaje`() {
        assertThat(TextMarkers.plain("**mocno** i *ukosem*")).isEqualTo("mocno i ukosem")
        assertThat(TextMarkers.plain("~~skreslone~~")).isEqualTo("skreslone")
        assertThat(TextMarkers.plain("==wazne==")).isEqualTo("wazne")
        assertThat(TextMarkers.plain("`kod`")).isEqualTo("kod")
    }

    @Test
    fun `znacznik koloru i podkreslenia znika, tresc zostaje`() {
        assertThat(TextMarkers.plain("""<span style="color:#C81E1E">czerwone</span>"""))
            .isEqualTo("czerwone")
        assertThat(TextMarkers.plain("<u>podkreslone</u>")).isEqualTo("podkreslone")
    }

    @Test
    fun `zagniezdzone i osierocone znaczniki tez znikaja`() {
        // Stary zepsuty zapis: kolor w rozmiarze i domknięcie bez pary.
        assertThat(
            TextMarkers.plain(
                """<span style="font-size:21px"><span style="color:#665222">duze</span></span>""",
            ),
        ).isEqualTo("duze")
        assertThat(TextMarkers.plain("tekst</span> dalej")).isEqualTo("tekst dalej")
        assertThat(TextMarkers.plain("""<span style="font-size:21px">duze</span>"""))
            .isEqualTo("duze")
    }

    @Test
    fun `z odnosnika zostaje sam opis`() {
        assertThat(TextMarkers.plain("[strona](https://kajet.wojtoteka.ovh)"))
            .isEqualTo("strona")
    }

    @Test
    fun `nazwa pliku z podkreslnikami zostaje bez zmian`() {
        assertThat(TextMarkers.plain("nazwa_pliku_tekstowego"))
            .isEqualTo("nazwa_pliku_tekstowego")
    }

    @Test
    fun `kolor jedzie tam i z powrotem`() {
        val argb = 0xFFC81E1E.toInt()
        val encoded = TextMarkers.colorHex(argb)

        assertThat(encoded).isEqualTo("#C81E1E")
        assertThat(TextMarkers.colorFromHex(encoded)).isEqualTo(argb)
    }

    @Test
    fun `zly zapis koloru nie wywraca odczytu`() {
        assertThat(TextMarkers.colorFromHex("#nie-kolor")).isNull()
        assertThat(TextMarkers.colorFromHex("")).isNull()
    }
}
