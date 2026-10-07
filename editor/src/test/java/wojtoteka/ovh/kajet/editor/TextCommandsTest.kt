package wojtoteka.ovh.kajet.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.core.model.SpanType
import wojtoteka.ovh.kajet.editor.text.LineKind
import wojtoteka.ovh.kajet.editor.text.PendingFormat
import wojtoteka.ovh.kajet.editor.text.TextCommands
import wojtoteka.ovh.kajet.editor.text.TextLayout

/*
  Polecenia paska narzędzi. Zaznaczenie i kursor podajemy w tym, co WIDAĆ -
  tak, jak człowiek stawia je palcem - a sprawdzamy zapis notatki i to, że
  zaznaczenie zostało przy tych samych słowach.
*/
private fun at(source: String, from: Int, to: Int = from): TextFieldValue {
    val layout = TextLayout.of(source)
    return TextFieldValue(source, layout.sourceSelection(from, to))
}

/** Zaznaczony tekst tak, jak go widać. */
private fun TextFieldValue.selectedText(): String {
    val layout = TextLayout.of(text)
    val from = layout.visibleOfSource(selection.min)
    val to = layout.visibleOfSource(selection.max)
    return layout.visible.substring(from, to)
}

private fun TextFieldValue.visibleCursor(): Int = TextLayout.of(text).visibleOfSource(selection.start)

class TextCommandsColourTest {

    private val red = 0xFFC81E1E.toInt()
    private val green = 0xFF1F6B3A.toInt()

    @Test
    fun `kolor otacza zaznaczenie i zostawia je zaznaczone`() {
        val after = TextCommands.color(at("Ala ma kota", 4, 6), red).field!!

        assertThat(after.text).isEqualTo("""Ala <span style="color:#c81e1e">ma</span> kota""")
        assertThat(after.selectedText()).isEqualTo("ma")
    }

    @Test
    fun `kolejny kolor podmienia poprzedni zamiast zagniezdzac`() {
        val first = TextCommands.color(at("Ala ma kota", 4, 6), red).field!!
        val second = TextCommands.color(first, green).field!!

        assertThat(second.text).isEqualTo("""Ala <span style="color:#1f6b3a">ma</span> kota""")
        assertThat(second.selectedText()).isEqualTo("ma")
    }

    @Test
    fun `kolor czesci pokolorowanego fragmentu rozcina znacznik`() {
        val source = """<span style="color:#c81e1e">Ala ma</span>"""
        val after = TextCommands.color(at(source, 4, 6), green).field!!

        assertThat(after.text).isEqualTo(
            """<span style="color:#c81e1e">Ala </span><span style="color:#1f6b3a">ma</span>""",
        )
        assertThat(after.selectedText()).isEqualTo("ma")
    }

    @Test
    fun `zdjecie koloru zostawia rozmiar na miejscu`() {
        val source = """<span style="font-size:21px"><span style="color:#c81e1e">ma</span></span>"""
        val after = TextCommands.color(at(source, 0, 2), null).field!!

        assertThat(after.text).isEqualTo("""<span style="font-size:21px">ma</span>""")
    }

    @Test
    fun `kolor i rozmiar skladaja sie zawsze w tym samym porzadku`() {
        // Rozmiar na zewnątrz, barwa przy treści - tak samo pisze serwer.
        val sized = TextCommands.resize(at("Ala ma kota", 4, 6), 4f, 17f).field!!
        val coloured = TextCommands.color(sized, red).field!!

        assertThat(coloured.text).isEqualTo(
            """Ala <span style="font-size:21px"><span style="color:#c81e1e">ma</span></span> kota""",
        )
        assertThat(coloured.selectedText()).isEqualTo("ma")
    }

    @Test
    fun `kursor za slowem - kolor czeka na pisanie i nie rusza tresci`() {
        val result = TextCommands.color(at("Ala ma kota", 6), red)

        assertThat(result.field).isNull()
        assertThat(result.pending.on[SpanType.COLOR]).isEqualTo("#c81e1e")
    }

    @Test
    fun `kursor w srodku slowa koloruje cale slowo, jak w Wordzie`() {
        val after = TextCommands.color(at("Ala ma kota", 9), red).field!!

        assertThat(after.text).isEqualTo("""Ala ma <span style="color:#c81e1e">kota</span>""")
        // Kursor zostaje tam, gdzie stał.
        assertThat(after.visibleCursor()).isEqualTo(9)
    }

    @Test
    fun `rozmiar czyta biezaca wielkosc takze przy zagniezdzonym kolorze`() {
        val source = """<span style="font-size:21px"><span style="color:#c81e1e">ma</span></span>"""
        val after = TextCommands.resize(at(source, 0, 2), 1f, 17f).field!!

        assertThat(after.text).isEqualTo(
            """<span style="font-size:22px"><span style="color:#c81e1e">ma</span></span>""",
        )
    }

    @Test
    fun `powrot do wielkosci notatki zdejmuje znacznik`() {
        val bigger = TextCommands.resize(at("Ala ma kota", 4, 6), 1f, 17f).field!!
        val back = TextCommands.resize(bigger, -1f, 17f).field!!

        assertThat(back.text).isEqualTo("Ala ma kota")
    }

    @Test
    fun `wielkosc bez zaznaczenia za slowem czeka na pisanie`() {
        val result = TextCommands.resize(at("Ala", 3), 1f, 17f)

        assertThat(result.field).isNull()
        assertThat(result.pending.on[SpanType.SIZE]).isEqualTo("18")
    }

    @Test
    fun `zaznaczenie przez granice wiersza koloruje oba kawalki osobno`() {
        val after = TextCommands.color(at("raz\ndwa", 0, 7), red).field!!

        assertThat(after.text).isEqualTo(
            """<span style="color:#c81e1e">raz</span>""" + "\n" +
                """<span style="color:#c81e1e">dwa</span>""",
        )
    }

    @Test
    fun `rozmiar przez punkty listy nie obejmuje znakow listy`() {
        val after = TextCommands.resize(at("- mleko\n- chleb", 2, 13), 2f, 17f).field!!

        assertThat(after.text).isEqualTo(
            """- <span style="font-size:19px">mleko</span>""" + "\n" +
                """- <span style="font-size:19px">chl</span>eb""",
        )
    }
}

class TextCommandsToggleTest {

    @Test
    fun `pogrubienie obejmuje sam zaznaczony fragment`() {
        val after = TextCommands.toggle(at("Ala ma kota", 4, 6), SpanType.BOLD).field!!

        assertThat(after.text).isEqualTo("Ala **ma** kota")
        assertThat(after.selectedText()).isEqualTo("ma")
    }

    @Test
    fun `powtorne nacisniecie zdejmuje format`() {
        val once = TextCommands.toggle(at("Ala ma kota", 4, 6), SpanType.BOLD).field!!
        val twice = TextCommands.toggle(once, SpanType.BOLD).field!!

        assertThat(twice.text).isEqualTo("Ala ma kota")
    }

    @Test
    fun `podkreslenie i przekreslenie skladaja sie z pogrubieniem`() {
        var field = at("Ala ma kota", 4, 6)
        field = TextCommands.toggle(field, SpanType.BOLD).field!!
        field = TextCommands.toggle(field, SpanType.UNDERLINE).field!!

        assertThat(field.text).isEqualTo("Ala **<u>ma</u>** kota")
        val formats = TextCommands.formats(field, PendingFormat(), 17f)
        assertThat(formats.has(SpanType.BOLD)).isTrue()
        assertThat(formats.has(SpanType.UNDERLINE)).isTrue()
    }

    @Test
    fun `kursor za slowem - format czeka na pisanie`() {
        val result = TextCommands.toggle(at("Ala ma kota", 6), SpanType.BOLD)

        assertThat(result.field).isNull()
        assertThat(result.pending.willHave(SpanType.BOLD, active = false)).isTrue()
    }

    @Test
    fun `kursor w srodku slowa pogrubia cale slowo i zostaje na miejscu`() {
        val after = TextCommands.toggle(at("Ala ma kota", 8), SpanType.BOLD).field!!

        assertThat(after.text).isEqualTo("Ala ma **kota**")
        assertThat(after.visibleCursor()).isEqualTo(8)

        // Drugie naciśnięcie w tym samym miejscu zdejmuje.
        assertThat(TextCommands.toggle(after, SpanType.BOLD).field!!.text).isEqualTo("Ala ma kota")
    }

    @Test
    fun `pasek widzi format tylko wtedy, gdy obejmuje cale zaznaczenie`() {
        val inside = TextCommands.formats(at("**raz** dwa", 0, 3), PendingFormat(), 17f)
        assertThat(inside.has(SpanType.BOLD)).isTrue()

        val across = TextCommands.formats(at("**raz** dwa", 0, 7), PendingFormat(), 17f)
        assertThat(across.has(SpanType.BOLD)).isFalse()
    }

    @Test
    fun `na poczatku wiersza pasek pokazuje format pierwszego znaku`() {
        // Pisanie od początku wiersza bierze format pierwszego znaku, więc
        // pasek musi pokazywać właśnie jego, a nie „nic".
        val formats = TextCommands.formats(at("raz\n**dwa**", 4), PendingFormat(), 17f)

        assertThat(formats.has(SpanType.BOLD)).isTrue()
    }

    @Test
    fun `spacja na brzegu zaznaczenia nie trafia w gwiazdki`() {
        val after = TextCommands.toggle(at("Ala ma kota", 0, 4), SpanType.BOLD).field!!

        assertThat(after.text).isEqualTo("**Ala** ma kota")
    }

    // --- Format przez kilka akapitów nie rusza ich budowy ---

    @Test
    fun `pogrubienie przez punkty listy nie rusza znakow listy`() {
        val after = TextCommands.toggle(at("- mleko\n- chleb\n- maslo", 2, 13), SpanType.BOLD).field!!

        assertThat(after.text).isEqualTo("- **mleko**\n- **chl**eb\n- maslo")
        assertThat(after.selectedText()).isEqualTo("mleko\n- chl")
    }

    @Test
    fun `kursywa przez wysrodkowane akapity nie wchodzi w znacznik ulozenia`() {
        val centre = """<p style="text-align:center">"""
        val source = "${centre}Ala</p>\n${centre}kot</p>"
        val after = TextCommands.toggle(at(source, 1, 6), SpanType.ITALIC).field!!

        assertThat(after.text).isEqualTo("${centre}A*la*</p>\n${centre}*ko*t</p>")
    }

    @Test
    fun `pogrubienie przez naglowek i akapit zostawia naglowek naglowkiem`() {
        val after = TextCommands.toggle(at("# Tytul\nAla ma", 0, 9), SpanType.BOLD).field!!

        assertThat(after.text).isEqualTo("# **Tytul**\n**Ala** ma")
    }

    // --- Odnośniki ---

    @Test
    fun `polowa opisu odnosnika pogrubia sie w nawiasach i odnosnik zostaje`() {
        val source = "Zobacz [strone](https://a.pl) teraz"
        val after = TextCommands.toggle(at(source, 7, 10), SpanType.BOLD).field!!

        assertThat(after.text).isEqualTo("Zobacz [**str**one](https://a.pl) teraz")
    }

    @Test
    fun `caly opis odnosnika pogrubia caly odnosnik`() {
        val source = "Zobacz [strone](https://a.pl) teraz"
        val after = TextCommands.toggle(at(source, 7, 13), SpanType.BOLD).field!!

        assertThat(after.text).isEqualTo("Zobacz **[strone](https://a.pl)** teraz")
    }

    @Test
    fun `tekst z kawalkiem odnosnika nie rozcina jego zapisu`() {
        val source = "Zobacz [strone](https://a.pl) teraz"
        val after = TextCommands.toggle(at(source, 0, 10), SpanType.BOLD).field!!

        assertThat(after.text).isEqualTo("**Zobacz** [**str**one](https://a.pl) teraz")
    }
}

/*
  Nagłówek jako format znaku - tak jak w Wordzie: H1 na kawałku zdania
  obejmuje ten kawałek, a nie cały wiersz.
*/
class TextCommandsHeadingTest {

    private fun heading(field: TextFieldValue, level: Int, pending: PendingFormat = PendingFormat()) =
        TextCommands.toggle(field, SpanType.HEADING, level.toString(), pending)

    @Test
    fun `H1 na zaznaczonym fragmencie obejmuje tylko ten fragment`() {
        val after = heading(at("Ala ma kota", 4, 6), 1).field!!

        assertThat(after.text).isEqualTo("""Ala <span class="h1">ma</span> kota""")
        assertThat(after.selectedText()).isEqualTo("ma")
        assertThat(TextLayout.of(after.text).visible).isEqualTo("Ala ma kota")
    }

    @Test
    fun `kursor w srodku slowa robi naglowek z tego slowa`() {
        val after = heading(at("Ala ma kota", 9), 2).field!!

        assertThat(after.text).isEqualTo("""Ala ma <span class="h2">kota</span>""")
        assertThat(after.visibleCursor()).isEqualTo(9)
    }

    @Test
    fun `caly wiersz w jednym poziomie zapisuje sie jak w markdownie`() {
        val after = heading(at("Tytul\nAla", 0, 5), 1).field!!

        assertThat(after.text).isEqualTo("# Tytul\nAla")
        assertThat(after.selectedText()).isEqualTo("Tytul")
    }

    @Test
    fun `H2 na naglowku H1 podmienia poziom zamiast sczepiac kratki`() {
        val after = heading(at("# Tytul", 2), 2).field!!

        assertThat(after.text).isEqualTo("## Tytul")
    }

    @Test
    fun `to samo H2 drugi raz zdejmuje naglowek`() {
        val once = heading(at("Tytul", 2), 2).field!!
        val twice = heading(once, 2).field!!

        assertThat(once.text).isEqualTo("## Tytul")
        assertThat(twice.text).isEqualTo("Tytul")
    }

    @Test
    fun `szybkie H1 H2 H3 konczy sie na H3`() {
        var field = at("Tytul", 2)
        field = heading(field, 1).field!!
        field = heading(field, 2).field!!
        field = heading(field, 3).field!!

        assertThat(field.text).isEqualTo("### Tytul")
    }

    @Test
    fun `H3 na wierszu z poskladanymi kratkami zostawia jeden naglowek`() {
        val after = heading(TextFieldValue("## # Tytul", TextRange(8)), 3).field!!

        assertThat(after.text).isEqualTo("### Tytul")
    }

    @Test
    fun `zdjecie naglowka z polowy wiersza zostawia reszte naglowkiem`() {
        val after = heading(at("# Tytul notatki", 6, 13), 1).field!!

        assertThat(after.text).isEqualTo("""<span class="h1">Tytul </span>notatki""")
        assertThat(TextLayout.of(after.text).visible).isEqualTo("Tytul notatki")
    }

    @Test
    fun `H1 w pustym wierszu robi z niego naglowek od razu`() {
        val once = heading(at("", 0), 1)
        assertThat(once.field!!.text).isEqualTo("# ")
        assertThat(once.field!!.selection.start).isEqualTo(2)

        // Ten sam poziom drugi raz zdejmuje.
        assertThat(heading(once.field!!, 1).field!!.text).isEqualTo("")
    }

    @Test
    fun `H1 za tekstem czeka na pisanie i niczego nie zmienia wstecz`() {
        val result = heading(at("Ala ma kota", 11), 1)

        assertThat(result.field).isNull()
        assertThat(result.pending.on[SpanType.HEADING]).isEqualTo("1")
        val formats = TextCommands.formats(at("Ala ma kota", 11), result.pending, 17f)
        assertThat(formats.heading).isEqualTo(1)
    }

    @Test
    fun `H1 w zadaniu czeka na pisanie zamiast robic kratki`() {
        val result = TextCommands.toggle(at("", 0), SpanType.HEADING, "1", PendingFormat(), lineHeading = false)

        assertThat(result.field).isNull()
        assertThat(result.pending.on[SpanType.HEADING]).isEqualTo("1")
    }

    @Test
    fun `naglowek w punkcie listy zostaje punktem`() {
        val after = heading(at("- mleko", 2, 7), 1).field!!

        assertThat(after.text).isEqualTo("""- <span class="h1">mleko</span>""")
    }

    @Test
    fun `naglowek na wysrodkowanym wierszu zostaje w srodku`() {
        val centre = """<p style="text-align:center">"""
        val after = heading(at("${centre}Tytul</p>", 0, 5), 2).field!!

        assertThat(after.text).isEqualTo("${centre}## Tytul</p>")
    }

    @Test
    fun `pasek widzi poziom naglowka pod kursorem i w pustym naglowku`() {
        assertThat(TextCommands.formats(at("# Tytul", 2), PendingFormat(), 17f).heading).isEqualTo(1)
        assertThat(TextCommands.formats(at("## ", 0), PendingFormat(), 17f).heading).isEqualTo(2)
        assertThat(TextCommands.formats(at("Ala", 2), PendingFormat(), 17f).heading).isNull()
    }

    @Test
    fun `wielkosc na pasku liczy powiekszenie naglowka`() {
        val formats = TextCommands.formats(at("# Tytul", 2), PendingFormat(), 10f)

        assertThat(formats.sizePx).isWithin(0.01f).of(17f)
    }

    @Test
    fun `plus na naglowku rosnie od wielkosci naglowka`() {
        val after = TextCommands.resize(at("# Tytul", 0, 5), 1f, 10f).field!!

        assertThat(after.text).isEqualTo("""# <span style="font-size:18px">Tytul</span>""")
    }
}

class TextCommandsParagraphTest {

    @Test
    fun `punkt dokladany jest na poczatku wiersza i kursor zostaje przy slowie`() {
        val after = TextCommands.paragraphs(at("Pierwszy\nDrugi", 11), LineKind.BULLET)!!

        assertThat(after.text).isEqualTo("Pierwszy\n- Drugi")
        assertThat(after.visibleCursor()).isEqualTo(13)
    }

    @Test
    fun `powtorne nacisniecie zdejmuje punkt`() {
        val after = TextCommands.paragraphs(at("- Zakupy", 4), LineKind.BULLET)!!

        assertThat(after.text).isEqualTo("Zakupy")
    }

    @Test
    fun `punkt na kazdym zaznaczonym akapicie, nie tylko na pierwszym`() {
        val after = TextCommands.paragraphs(at("jeden\ndwa\ntrzy", 0, 14), LineKind.BULLET)!!

        assertThat(after.text).isEqualTo("- jeden\n- dwa\n- trzy")
        assertThat(after.selectedText()).isEqualTo("jeden\n- dwa\n- trzy")
    }

    @Test
    fun `numery na zaznaczonych akapitach ida po kolei`() {
        val after = TextCommands.paragraphs(at("jeden\ndwa\ntrzy", 0, 13), LineKind.NUMBER)!!

        assertThat(after.text).isEqualTo("1. jeden\n2. dwa\n3. trzy")
    }

    @Test
    fun `drugie nacisniecie zdejmuje liste ze wszystkich akapitow`() {
        val after = TextCommands.paragraphs(at("- jeden\n- dwa", 0, 11), LineKind.BULLET)!!

        assertThat(after.text).isEqualTo("jeden\ndwa")
    }

    @Test
    fun `akapity czesciowo w liscie dostaja liste wszystkie`() {
        val after = TextCommands.paragraphs(at("- jeden\ndwa", 0, 11), LineKind.BULLET)!!

        assertThat(after.text).isEqualTo("- jeden\n- dwa")
    }

    @Test
    fun `puste wiersze w zaznaczeniu zostaja odstepem`() {
        val after = TextCommands.paragraphs(at("raz\n\ndwa", 0, 8), LineKind.BULLET)!!

        assertThat(after.text).isEqualTo("- raz\n\n- dwa")
    }

    @Test
    fun `numer na punkcie zastepuje punkt zamiast sie doklejac`() {
        assertThat(TextCommands.paragraphs(at("- mleko", 3), LineKind.NUMBER)!!.text).isEqualTo("1. mleko")
        assertThat(TextCommands.paragraphs(at("1. mleko", 4), LineKind.BULLET)!!.text).isEqualTo("- mleko")
        assertThat(TextCommands.paragraphs(at("- mleko", 3), LineKind.TASK)!!.text).isEqualTo("- [ ] mleko")
        assertThat(TextCommands.paragraphs(at("- mleko", 3), LineKind.QUOTE)!!.text).isEqualTo("> mleko")
        assertThat(TextCommands.paragraphs(at("> cytat", 2), LineKind.BULLET)!!.text).isEqualTo("- cytat")
    }

    @Test
    fun `podlista zostaje podlista po zmianie rodzaju`() {
        val after = TextCommands.paragraphs(at("- owoce\n  - jablka", 10), LineKind.NUMBER)!!

        assertThat(after.text).isEqualTo("- owoce\n  1. jablka")
    }

    @Test
    fun `punkt na naglowku zostawia wyglad naglowka`() {
        val after = TextCommands.paragraphs(at("## Tytul", 2), LineKind.BULLET)!!

        assertThat(after.text).isEqualTo("""- <span class="h2">Tytul</span>""")
    }

    @Test
    fun `zadanie zdejmuje ulozenie akapitu`() {
        val centre = """<p style="text-align:center">"""
        val after = TextCommands.paragraphs(at("${centre}kupic mleko</p>", 3), LineKind.TASK)!!

        assertThat(after.text).isEqualTo("- [ ] kupic mleko")
    }

    @Test
    fun `numerowana lista przelicza sie po wstawieniu w srodek`() {
        val after = TextCommands.paragraphs(at("1. raz\ndwa\n2. trzy", 8), LineKind.NUMBER)!!

        assertThat(after.text).isEqualTo("1. raz\n2. dwa\n3. trzy")
    }
}

class TextCommandsScriptTest {

    @Test
    fun `liczba przed kursorem idzie do indeksu dolnego`() {
        val after = TextCommands.script(at("H2O i H2", 8), superscript = false)!!

        assertThat(after.text).isEqualTo("H2O i H₂")
        assertThat(after.visibleCursor()).isEqualTo(8)
    }

    @Test
    fun `zaznaczenie idzie do indeksu gornego razem ze znakiem`() {
        val after = TextCommands.script(at("x-12 + y", 1, 4), superscript = true)!!

        assertThat(after.text).isEqualTo("x⁻¹² + y")
        assertThat(after.selectedText()).isEqualTo("⁻¹²")
    }

    @Test
    fun `drugie stukniecie wraca do zwyklych cyfr`() {
        val once = TextCommands.script(at("x2", 2), superscript = true)!!
        val twice = TextCommands.script(once, superscript = true)!!

        assertThat(once.text).isEqualTo("x²")
        assertThat(twice.text).isEqualTo("x2")
    }

    @Test
    fun `cyfry z drugiego indeksu przechodza do wybranego`() {
        val after = TextCommands.script(at("H₂", 2), superscript = true)!!

        assertThat(after.text).isEqualTo("H²")
    }

    @Test
    fun `minus przed liczba bez zaznaczenia zostaje dzialaniem`() {
        val after = TextCommands.script(at("2-3", 3), superscript = true)!!

        assertThat(after.text).isEqualTo("2-³")
    }

    @Test
    fun `cyfry w formatowaniu zostaja w znacznikach`() {
        val after = TextCommands.script(at("**CO2**", 3), superscript = false)!!

        assertThat(after.text).isEqualTo("**CO₂**")
    }

    @Test
    fun `adres odnosnika zostaje nietkniety`() {
        val source = "[wzór 2](https://kajet.ovh/n/123)"
        val after = TextCommands.script(at(source, 0, 6), superscript = true)!!

        assertThat(after.text).isEqualTo("[wzór ²](https://kajet.ovh/n/123)")
    }

    @Test
    fun `bez cyfr nie ma czego zmieniac`() {
        assertThat(TextCommands.script(at("Ala ma kota", 3), superscript = true)).isNull()
        assertThat(TextCommands.script(at("Ala ma kota", 0, 3), superscript = false)).isNull()
    }
}
