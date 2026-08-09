package wojtoteka.ovh.kajet.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.editor.text.InlineStyle

class InlineStyleTest {

    private val style = InlineStyle(
        textColor = Color.Black,
        markerColor = Color.Gray,
        highlightColor = Color.Yellow,
        codeColor = Color.Blue,
        monoFont = FontFamily.Monospace,
    )

    private fun check(source: String) {
        val result = style.style(source)
        assertThat(result.text).isEqualTo(source)
    }

    @Test
    fun `gotowe znaczniki nie zmieniaja tresci`() {
        check("# Nagłówek")
        check("**mocno** i *ukosem* i ~~skreślone~~ i ==ważne==")
        check("<u>podkreślone</u>")
        check("""<span style="color:#C81E1E">czerwone</span>""")
        check("""<span style="font-size:21px">większe</span>""")
        check("- punkt\n1. numer\n> cytat\n- [x] zrobione")
        check("`kod` w zdaniu")
        check("[opis](https://kajet.wojtoteka.ovh)")
        check("![zdjęcie](assets/z.png)")
    }

    @Test
    fun `znacznik dopiero zaczety nie wywraca ozdabiania`() {
        check("**")
        check("*")
        check("**niedokończone")
        check("<u>bez zamknięcia")
        check("""<span style="color:#C81E1E">bez końca""")
        check("==")
        check("~~")
        check("`")
        check("[opis](")
        check("![")
    }

    @Test
    fun `sam znak nagłówka i pusty wiersz sa bezpieczne`() {
        check("#")
        check("# ")
        check("######")
        check("#######  siedem krzyżyków to już nie nagłówek")
        check(">")
        check("> ")
        check("-")
        check("- ")
        check("- [ ]")
        check("")
        check("\n")
        check("\n\n\n")
    }

    @Test
    fun `blok kodu bez zamkniecia nie wywraca ozdabiania`() {
        check("```python\nprint(\"**nie pogrubiaj**\")")
        check("```")
        check("```\n# to nie jest nagłówek\n```\n# a to jest")
    }

    @Test
    fun `znaczniki jeden w drugim sa bezpieczne`() {
        check("**mocno i *ukosem* naraz**")
        check("# Nagłówek z **pogrubieniem** i `kodem`")
        check("- [ ] zadanie z <u>podkreśleniem</u>")
        check("""> cytat z <span style="color:#0F0">kolorem</span>""")
        check("`**to jest kod, nie pogrubienie**`")
    }

    @Test
    fun `zly zapis koloru nie wywraca ozdabiania`() {
        check("""<span style="color:nie-kolor">tekst</span>""")
        check("""<span style="color:">tekst</span>""")
        check("""<span style="color:#ABC">za krótki</span>""")
    }

    @Test
    fun `dlugi wiersz z wieloma znacznikami zostaje bez zmian`() {
        val line = (1..80).joinToString(" ") { "**s$it** *k$it* `c$it`" }
        check(line)
    }

    // --- Znikające znaczniki ---

    /** To, co człowiek naprawdę widzi w polu. */
    private fun shown(source: String): String =
        style.filter(AnnotatedString(source)).text.text

    /** Mapowanie kursora musi być spójne - Compose wywraca się na byle luce. */
    private fun checkMapping(source: String) {
        val transformed = style.filter(AnnotatedString(source))
        val mapping = transformed.offsetMapping
        val shownLength = transformed.text.text.length

        var previous = -1
        for (i in 0..source.length) {
            val there = mapping.originalToTransformed(i)
            assertThat(there).isAtLeast(0)
            assertThat(there).isAtMost(shownLength)
            // Musi rosnąć: na tym stoi kursor i zaznaczenie.
            assertThat(there).isAtLeast(previous)
            previous = there
        }
        for (j in 0..shownLength) {
            val back = mapping.transformedToOriginal(j)
            assertThat(back).isAtLeast(0)
            assertThat(back).isAtMost(source.length)
        }
    }

    @Test
    fun `pogrubienie widac bez gwiazdek`() {
        assertThat(shown("to jest **mocne** slowo")).isEqualTo("to jest mocne slowo")
    }

    @Test
    fun `kolor pisma bez golego HTML-a`() {
        assertThat(shown("""raz <span style="color:#c81e1e">dwa</span> trzy"""))
            .isEqualTo("raz dwa trzy")
    }

    @Test
    fun `naglowek bez kratki`() {
        assertThat(shown("## Tytul")).isEqualTo("Tytul")
    }

    @Test
    fun `cytat podkreslenie i przekreslenie tez sie rozbieraja`() {
        assertThat(shown("> cytat")).isEqualTo("cytat")
        assertThat(shown("<u>pod</u>")).isEqualTo("pod")
        assertThat(shown("~~nie~~")).isEqualTo("nie")
        assertThat(shown("==wazne==")).isEqualTo("wazne")
    }

    @Test
    fun `z odnosnika zostaje sam opis`() {
        assertThat(shown("[Kajet](https://kajet.wojtoteka.ovh)")).isEqualTo("Kajet")
    }

    @Test
    fun `kod w zdaniu bez grawisow`() {
        assertThat(shown("uzyj `println` tutaj")).isEqualTo("uzyj println tutaj")
    }

    @Test
    fun `znaczniki znikaja w kazdym wierszu, takze pod kursorem`() {
        assertThat(shown("**raz**\n**dwa**")).isEqualTo("raz\ndwa")
    }

    @Test
    fun `wielkosc pisma bez golego HTML-a`() {
        assertThat(shown("""raz <span style="font-size:21px">dwa</span> trzy"""))
            .isEqualTo("raz dwa trzy")
    }

    @Test
    fun `ozdoby skladaja sie jedna w drugiej`() {
        // Powiększony fragment w środku pogrubienia: gwiazdki i znacznik
        // wielkości znikają razem, słowo zostaje grube i duże.
        assertThat(shown("""**<span style="font-size:21px">gruby</span>**""")).isEqualTo("gruby")
        assertThat(shown("""<span style="color:#C81E1E">**mocno**</span>""")).isEqualTo("mocno")
    }

    @Test
    fun `zagniezdzone znaczniki nie pokazuja golego HTML-a`() {
        // Kanoniczny zapis koloru z rozmiarem — i jego odwrotność ze starych notatek.
        assertThat(
            shown("""<span style="font-size:21px"><span style="color:#665222">x</span></span>"""),
        ).isEqualTo("x")
        assertThat(
            shown("""<span style="color:#665222"><span style="font-size:21px">x</span></span>"""),
        ).isEqualTo("x")
        // Zepsuty zapis kolor w kolorze — dokładnie to straszyło w notatkach.
        assertThat(
            shown("""<span style="color:#111111"><span style="color:#665222">x</span></span>"""),
        ).isEqualTo("x")
    }

    @Test
    fun `kolor w podkresleniu i osierocone domkniecie tez sie chowaja`() {
        assertThat(shown("""<u>pod <span style="color:#665222">kolor</span></u>"""))
            .isEqualTo("pod kolor")
        assertThat(shown("tekst</span> dalej")).isEqualTo("tekst dalej")
        // Znacznik bez domknięcia: sam znika, kolor działa do końca wiersza.
        assertThat(shown("""raz <span style="color:#665222">bez konca"""))
            .isEqualTo("raz bez konca")
    }

    @Test
    fun `kwadracik zadania zostaje - to nie ozdoba`() {
        assertThat(shown("- [x] zrobione")).isEqualTo("- [x] zrobione")
    }

    @Test
    fun `tresc bez znacznikow zostaje nietknieta`() {
        assertThat(shown("zwykly tekst")).isEqualTo("zwykly tekst")
    }

    @Test
    fun `mapowanie kursora trzyma sie na kazdej tresci`() {
        checkMapping("to jest **mocne** slowo")
        checkMapping("""raz <span style="color:#c81e1e">dwa</span> trzy""")
        checkMapping("""raz <span style="font-size:21px">dwa</span> trzy""")
        checkMapping("""**<span style="font-size:21px">gruby</span>**""")
        checkMapping("""<span style="color:#C81E1E">**mocno**</span>""")
        checkMapping("## Tytul z **mocnym** i `kodem`")
        checkMapping("[Kajet](https://kajet.wojtoteka.ovh)")
        checkMapping("**raz**\n**dwa**")
        checkMapping("**niedokonczone")
        checkMapping("")
        checkMapping("zwykly tekst bez niczego")
        checkMapping("- [ ] zadanie z <u>podkresleniem</u>")
        checkMapping("```\nkod\n```")
        checkMapping("![zdjecie](assets/z.png)")
        checkMapping("""<span style="font-size:21px"><span style="color:#665222">x</span></span>""")
        checkMapping("""<span style="color:#111111"><span style="color:#665222">x</span></span>""")
        checkMapping("tekst</span> dalej")
        checkMapping("""raz <span style="color:#665222">bez konca""")
        checkMapping("""<u>pod <span style="color:#665222">kolor</span></u>""")

        // Kształty, które pisze nowy zapis: zbite gwiazdki i wszystkie
        // siedem formatów jeden w drugim.
        checkMapping("***grube i pochyle***")
        checkMapping("""**<span style="color:#c81e1e">x</span>**""")
        checkMapping("==wazne==")
        checkMapping("~~nie~~")
        checkMapping("<u>pod</u>")
        checkMapping(
            """***~~==<u><span style="font-size:18px"><span style="color:#c81e1e">""" +
                """wszystko</span></span></u>==~~***""",
        )
        checkMapping("- [ ] zadanie ***z gwiazdkami*** i <u>kreska</u>")
    }

    @Test
    fun `wszystkie siedem formatow naraz chowa swoje znaczniki`() {
        val source = """***~~==<u><span style="font-size:18px">""" +
            """<span style="color:#c81e1e">wszystko</span></span></u>==~~***"""
        assertThat(shown(source)).isEqualTo("wszystko")
    }

    @Test
    fun `grube i pochyle naraz nie zostawia gwiazdek`() {
        assertThat(shown("***grube i pochyle***")).isEqualTo("grube i pochyle")
    }

    @Test
    fun `blok kodu chowa swoje ogrodzenie razem z pustym wierszem`() {
        assertThat(shown("przed\n```\nkod\n```\npo")).isEqualTo("przed\nkod\npo")
        assertThat(shown("```python\nprint(1)\n```")).isEqualTo("print(1)")
    }

    @Test
    fun `blok wzoru chowa swoje ogrodzenie`() {
        assertThat(shown("przed\n$$\nx^2\n$$\npo")).isEqualTo("przed\nx^2\npo")
    }

    @Test
    fun `wzor w jednym wierszu zostaje tresci`() {
        // „$$x$$" to nie blok, tylko zapis w wierszu — nie ma czego chować.
        val line = "wzor \$\$x\$\$ w zdaniu"
        assertThat(shown(line)).isEqualTo(line)
    }

    @Test
    fun `znaczniki w bloku kodu i wzoru zostaja tresci`() {
        assertThat(shown("```\n**nie pogrubiaj**\n```")).isEqualTo("**nie pogrubiaj**")
        assertThat(shown("$$\na * b * c\n$$")).isEqualTo("a * b * c")
    }

    @Test
    fun `mapowanie kursora trzyma sie takze w blokach`() {
        checkMapping("przed\n```\nkod\n```\npo")
        checkMapping("```python\nprint(1)\n```")
        checkMapping("przed\n$$\nx^2\n$$\npo")
        checkMapping("```\nbez zamkniecia")
        checkMapping("$$\nbez zamkniecia")
        checkMapping("\n```\n\n```\n")
        checkMapping("\n$$\n\n$$\n")
    }
}
