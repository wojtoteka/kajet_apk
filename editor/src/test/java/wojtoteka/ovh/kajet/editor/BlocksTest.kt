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

        assertThat(Blocks.join(after)).isEqualTo("- [ ] chleb\n- [ ] mleko")
    }

    @Test
    fun `nowe zadanie zaczyna sie nieodhaczone`() {
        val blocks = Blocks.split("- [x] chleb")
        val after = Blocks.splitTask(blocks, blocks[0].key, "chleb\n")

        assertThat(Blocks.join(after)).isEqualTo("- [x] chleb\n- [ ] ")
    }

    @Test
    fun `klawisz w pustym zadaniu konczy liste`() {
        val blocks = Blocks.split("- [ ] ")
        val after = Blocks.splitTask(blocks, blocks[0].key, "\n")

        assertThat(after).hasSize(1)
        assertThat(after[0]).isInstanceOf(Block.Text::class.java)
    }

    @Test
    fun `rozciecie nie gubi zadan stojacych obok`() {
        val blocks = Blocks.split("- [ ] chleb\n- [ ] maslo")
        val after = Blocks.splitTask(blocks, blocks[0].key, "chleb\nmleko")

        assertThat(Blocks.join(after)).isEqualTo("- [ ] chleb\n- [ ] mleko\n- [ ] maslo")
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
    fun `otoczenie zaznaczenia dodaje znaczniki i zostawia je zaznaczone`() {
        val field = TextFieldValue("Ala ma kota", TextRange(4, 6))
        val after = TextFormat.wrap(field, "**")

        assertThat(after.text).isEqualTo("Ala **ma** kota")
        assertThat(after.text.substring(after.selection.min, after.selection.max)).isEqualTo("ma")
    }

    @Test
    fun `otoczenie bez zaznaczenia wstawia slowo do nadpisania`() {
        val after = TextFormat.wrap(TextFieldValue("", TextRange(0)), "*")

        assertThat(after.text).isEqualTo("*tekst*")
        assertThat(after.text.substring(after.selection.min, after.selection.max)).isEqualTo("tekst")
    }

    @Test
    fun `powtorne otoczenie zdejmuje znaczniki`() {
        val field = TextFieldValue("Ala **ma** kota", TextRange(6, 8))
        val after = TextFormat.wrap(field, "**")

        assertThat(after.text).isEqualTo("Ala ma kota")
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

    // Podkreślenie i kolor. Markdown nie ma na nie własnego zapisu,
    // więc otaczamy zaznaczenie parą różnych znaczników.

    @Test
    fun `podkreslenie otacza zaznaczenie para znacznikow`() {
        val field = TextFieldValue("Ala ma kota", TextRange(4, 6))
        val after = TextFormat.wrapPair(field, "<u>", "</u>")

        assertThat(after.text).isEqualTo("Ala <u>ma</u> kota")
        assertThat(after.text.substring(after.selection.min, after.selection.max)).isEqualTo("ma")
    }

    @Test
    fun `powtorne podkreslenie zdejmuje znaczniki`() {
        val field = TextFieldValue("Ala <u>ma</u> kota", TextRange(7, 9))
        val after = TextFormat.wrapPair(field, "<u>", "</u>")

        assertThat(after.text).isEqualTo("Ala ma kota")
    }

    @Test
    fun `kolor bez zaznaczenia wstawia slowo do nadpisania`() {
        val after = TextFormat.wrapPair(
            TextFieldValue("", TextRange(0)),
            "<span style=\"color:#C81E1E\">",
            "</span>",
        )

        assertThat(after.text).isEqualTo("<span style=\"color:#C81E1E\">tekst</span>")
        assertThat(after.text.substring(after.selection.min, after.selection.max)).isEqualTo("tekst")
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
