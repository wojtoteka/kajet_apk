package wojtoteka.ovh.kajet.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.editor.text.PendingFormat
import wojtoteka.ovh.kajet.editor.text.RichText
import wojtoteka.ovh.kajet.editor.text.SpanType
import wojtoteka.ovh.kajet.editor.text.TextFormat

/*
  Formatowanie fragmentu notatki.

  Dwie rzeczy, których te testy pilnują przede wszystkim: format obejmuje
  WYŁĄCZNIE zaznaczenie, a bez zaznaczenia nie rusza niczego wstecz — czeka
  na tekst, który człowiek zaraz napisze.
*/
class TextFormatColorTest {

    private val red = 0xFFC81E1E.toInt()
    private val green = 0xFF1F6B3A.toInt()

    private fun selected(field: TextFieldValue): String =
        field.text.substring(field.selection.min, field.selection.max)

    @Test
    fun `kolor otacza zaznaczenie i zostawia je zaznaczone`() {
        val field = TextFieldValue("Ala ma kota", TextRange(4, 6))
        val after = TextFormat.applyColor(field, red)!!

        assertThat(after.text).isEqualTo("""Ala <span style="color:#c81e1e">ma</span> kota""")
        assertThat(selected(after)).isEqualTo("ma")
    }

    @Test
    fun `kolejny kolor podmienia poprzedni zamiast zagniezdzac`() {
        val first = TextFormat.applyColor(TextFieldValue("Ala ma kota", TextRange(4, 6)), red)!!
        val second = TextFormat.applyColor(first, green)!!

        assertThat(second.text).isEqualTo("""Ala <span style="color:#1f6b3a">ma</span> kota""")
        assertThat(selected(second)).isEqualTo("ma")
    }

    @Test
    fun `kolor czesci pokolorowanego fragmentu rozcina znacznik`() {
        val source = """<span style="color:#c81e1e">Ala ma</span>"""
        val at = source.indexOf("ma")
        val after = TextFormat.applyColor(TextFieldValue(source, TextRange(at, at + 2)), green)!!

        assertThat(after.text).isEqualTo(
            """<span style="color:#c81e1e">Ala </span><span style="color:#1f6b3a">ma</span>""",
        )
        assertThat(selected(after)).isEqualTo("ma")
    }

    @Test
    fun `zdjecie koloru zostawia rozmiar na miejscu`() {
        val source = """<span style="font-size:21px"><span style="color:#c81e1e">ma</span></span>"""
        val at = source.indexOf("ma")
        val after = TextFormat.applyColor(TextFieldValue(source, TextRange(at, at + 2)), null)!!

        assertThat(after.text).isEqualTo("""<span style="font-size:21px">ma</span>""")
    }

    @Test
    fun `kolor i rozmiar skladaja sie zawsze w tym samym porzadku`() {
        // Rozmiar na zewnątrz, barwa przy treści — tak samo pisze serwer.
        val sized = TextFormat.resize(TextFieldValue("Ala ma kota", TextRange(4, 6)), 4f, 17f)!!
        val coloured = TextFormat.applyColor(sized, red)!!

        assertThat(coloured.text).isEqualTo(
            """Ala <span style="font-size:21px"><span style="color:#c81e1e">ma</span></span> kota""",
        )
        assertThat(selected(coloured)).isEqualTo("ma")
    }

    @Test
    fun `bez zaznaczenia kolor nie rusza tresci`() {
        // Kursor w słowie: dawniej kolorowało całe słowo, teraz barwa czeka
        // na pisanie. Nic wstecz nie ma prawa się zmienić.
        assertThat(TextFormat.applyColor(TextFieldValue("Ala ma kota", TextRange(5)), red))
            .isNull()
    }

    @Test
    fun `rozmiar czyta biezaca wielkosc takze przy zagniezdzonym kolorze`() {
        val source = """<span style="font-size:21px"><span style="color:#c81e1e">ma</span></span>"""
        val at = source.indexOf("ma")
        val after = TextFormat.resize(TextFieldValue(source, TextRange(at, at + 2)), 1f, 17f)!!

        assertThat(after.text).isEqualTo(
            """<span style="font-size:22px"><span style="color:#c81e1e">ma</span></span>""",
        )
    }

    @Test
    fun `powrot do wielkosci notatki zdejmuje znacznik`() {
        val bigger = TextFormat.resize(TextFieldValue("Ala ma kota", TextRange(4, 6)), 1f, 17f)!!
        val back = TextFormat.resize(bigger, -1f, 17f)!!

        assertThat(back.text).isEqualTo("Ala ma kota")
    }

    @Test
    fun `bez zaznaczenia wielkosc fragmentu oddaje null`() {
        assertThat(TextFormat.resize(TextFieldValue("Ala", TextRange(1)), 1f, 17f)).isNull()
    }

    @Test
    fun `zaznaczenie przez granice wiersza koloruje oba kawalki osobno`() {
        val field = TextFieldValue("raz\ndwa", TextRange(0, 7))
        val after = TextFormat.applyColor(field, red)!!

        assertThat(after.text).isEqualTo(
            """<span style="color:#c81e1e">raz</span>""" + "\n" +
                """<span style="color:#c81e1e">dwa</span>""",
        )
    }
}

class TextFormatToggleTest {

    @Test
    fun `pogrubienie obejmuje sam zaznaczony fragment`() {
        val field = TextFieldValue("Ala ma kota", TextRange(4, 6))
        val after = TextFormat.toggle(field, SpanType.BOLD)!!

        assertThat(after.text).isEqualTo("Ala **ma** kota")
        assertThat(after.text.substring(after.selection.min, after.selection.max)).isEqualTo("ma")
    }

    @Test
    fun `powtorne nacisniecie zdejmuje format`() {
        val once = TextFormat.toggle(TextFieldValue("Ala ma kota", TextRange(4, 6)), SpanType.BOLD)!!
        val twice = TextFormat.toggle(once, SpanType.BOLD)!!

        assertThat(twice.text).isEqualTo("Ala ma kota")
    }

    @Test
    fun `podkreslenie i przekreslenie skladaja sie z pogrubieniem`() {
        var field: TextFieldValue = TextFieldValue("Ala ma kota", TextRange(4, 6))
        field = TextFormat.toggle(field, SpanType.BOLD)!!
        field = TextFormat.toggle(field, SpanType.UNDERLINE)!!

        assertThat(field.text).isEqualTo("Ala **<u>ma</u>** kota")
        assertThat(TextFormat.has(field, SpanType.BOLD)).isTrue()
        assertThat(TextFormat.has(field, SpanType.UNDERLINE)).isTrue()
    }

    @Test
    fun `bez zaznaczenia format nie rusza tresci`() {
        assertThat(TextFormat.toggle(TextFieldValue("Ala ma kota", TextRange(5)), SpanType.BOLD))
            .isNull()
    }

    @Test
    fun `pasek widzi format tylko wtedy, gdy obejmuje cale zaznaczenie`() {
        val field = TextFieldValue("**raz** dwa", TextRange(2, 5))
        assertThat(TextFormat.has(field, SpanType.BOLD)).isTrue()

        val across = TextFieldValue("**raz** dwa", TextRange(2, 11))
        assertThat(TextFormat.has(across, SpanType.BOLD)).isFalse()
    }
}

class PendingFormatTest {

    @Test
    fun `zapamietany format obejmuje dopiero co wpisany tekst`() {
        val typed = TextFieldValue("a", TextRange(1))
        val after = TextFormat.applyPending(
            typed,
            previous = "",
            pending = PendingFormat().with(SpanType.BOLD),
        )!!

        assertThat(after.text).isEqualTo("**a**")
        // Kursor stoi MIĘDZY znacznikami, więc dalsze pisanie idzie dalej grube.
        assertThat(after.selection.start).isEqualTo(3)
    }

    @Test
    fun `dalsze pisanie zostaje w tym samym znaczniku bez zapamietanego formatu`() {
        val typed = TextFieldValue("**ab**", TextRange(4))
        val after = TextFormat.applyPending(typed, "**a**", PendingFormat())

        // Nic do nadania: pole zostaje takie, jakie przyszło z klawiatury.
        assertThat(after).isNull()
        assertThat(RichTextParsed("**ab**")).isEqualTo("ab")
    }

    @Test
    fun `zapamietane zdjecie formatu wyprowadza pisanie poza znacznik`() {
        val typed = TextFieldValue("**abc**", TextRange(6))
        val after = TextFormat.applyPending(
            typed,
            previous = "**ab**",
            pending = PendingFormat().without(SpanType.BOLD),
        )!!

        assertThat(after.text).isEqualTo("**ab**c")
    }

    @Test
    fun `zapamietana barwa obejmuje wpisany tekst`() {
        val typed = TextFieldValue("Ala x", TextRange(5))
        val after = TextFormat.applyPending(
            typed,
            previous = "Ala ",
            pending = PendingFormat().with(SpanType.COLOR, "#c81e1e"),
        )!!

        assertThat(after.text).isEqualTo("""Ala <span style="color:#c81e1e">x</span>""")
    }

    @Test
    fun `kasowanie nie nadaje niczego`() {
        val typed = TextFieldValue("Al", TextRange(2))
        assertThat(
            TextFormat.applyPending(typed, "Ala", PendingFormat().with(SpanType.BOLD)),
        ).isNull()
    }

    @Test
    fun `pusty zapamietany format niczego nie rusza`() {
        val typed = TextFieldValue("Ala", TextRange(3))
        assertThat(TextFormat.applyPending(typed, "Al", PendingFormat())).isNull()
    }

    private fun RichTextParsed(markdown: String): String =
        wojtoteka.ovh.kajet.editor.text.RichText.parse(markdown).text
}

/*
  Klawisz nowej linii. Kursor po sformatowaniu stoi MIĘDZY znacznikami, więc
  wstawiony tam znak końca wiersza rozcinał je na pół i gwiazdki wychodziły
  na wierzch. Nowa linia idzie dlatego przez model.
*/
class NewlineTest {

    private fun enter(previous: String, cursor: Int, pending: PendingFormat = PendingFormat()) =
        TextFormat.typedNewline(
            previous = previous,
            typed = TextFieldValue(
                previous.substring(0, cursor) + "\n" + previous.substring(cursor),
                TextRange(cursor + 1),
            ),
            pending = pending,
        )

    @Test
    fun `nowa linia w pogrubieniu nie rozcina znacznikow`() {
        // Kursor stoi między "o" a domykającymi gwiazdkami.
        val after = enter("**mocno**", cursor = 7)!!

        assertThat(after.field.text).isEqualTo("**mocno**\n")
        assertThat(after.field.text).doesNotContain("mocno\n")
    }

    @Test
    fun `pogrubienie idzie dalej w nowym wierszu`() {
        val after = enter("**mocno**", cursor = 7)!!

        assertThat(after.carry.willHave(SpanType.BOLD, active = false)).isTrue()
    }

    @Test
    fun `wyroznienie i przekreslenie tez nie rozcinaja znacznikow`() {
        assertThat(enter("==wazne==", cursor = 7)!!.field.text).isEqualTo("==wazne==\n")
        assertThat(enter("~~nie~~", cursor = 5)!!.field.text).isEqualTo("~~nie~~\n")
    }

    @Test
    fun `barwa nie rozcina znacznika i idzie dalej`() {
        val source = """<span style="color:#c81e1e">czerwone</span>"""
        val after = enter(source, cursor = source.indexOf("</span>"))!!

        assertThat(after.field.text).isEqualTo("$source\n")
        assertThat(after.carry.on[SpanType.COLOR]).isEqualTo("#c81e1e")
    }

    @Test
    fun `zwykly tekst przechodzi bez ruszania zapisu`() {
        // Nie ma formatu ani listy: nowa linia nie ma po co iść przez model.
        assertThat(enter("zwykly tekst", cursor = 6)).isNull()
    }

    // --- Listy ---

    @Test
    fun `numerowana lista sama zaczyna nastepna pozycje`() {
        val after = enter("1. mleko", cursor = 8)!!

        assertThat(after.field.text).isEqualTo("1. mleko\n2. ")
        assertThat(after.field.selection.start).isEqualTo("1. mleko\n2. ".length)
    }

    @Test
    fun `numer rosnie od biezacej pozycji`() {
        assertThat(enter("7. siedem", cursor = 9)!!.field.text).isEqualTo("7. siedem\n8. ")
        assertThat(enter("1) jeden", cursor = 8)!!.field.text).isEqualTo("1) jeden\n2) ")
    }

    @Test
    fun `punktowana lista ciagnie ten sam znacznik`() {
        assertThat(enter("- mleko", cursor = 7)!!.field.text).isEqualTo("- mleko\n- ")
        assertThat(enter("* mleko", cursor = 7)!!.field.text).isEqualTo("* mleko\n* ")
    }

    @Test
    fun `wciecie podlisty zostaje`() {
        assertThat(enter("  - tluste", cursor = 10)!!.field.text).isEqualTo("  - tluste\n  - ")
    }

    @Test
    fun `zadanie zaczyna sie nieodhaczone`() {
        assertThat(enter("- [x] zrobione", cursor = 14)!!.field.text)
            .isEqualTo("- [x] zrobione\n- [ ] ")
    }

    @Test
    fun `nowa linia w pustej pozycji konczy liste`() {
        val after = enter("1. mleko\n2. ", cursor = 12)!!

        assertThat(after.field.text).isEqualTo("1. mleko\n")
        assertThat(after.field.selection.start).isEqualTo("1. mleko\n".length)
    }

    @Test
    fun `pozycja listy z pogrubieniem ciagnie oba`() {
        val after = enter("1. **mleko**", cursor = 10)!!

        assertThat(after.field.text).isEqualTo("1. **mleko**\n2. ")
        assertThat(after.carry.willHave(SpanType.BOLD, active = false)).isTrue()
    }
}

/*
  Pisanie od początku do końca, tak jak idzie przez pole w edytorze:
  naciśnięcie przycisku, pisanie, Enter, pisanie dalej. Tu wychodzą rzeczy,
  których nie widać, gdy sprawdza się każdą operację osobno.
*/
class TypingTest {

    private var pending = PendingFormat()
    private var field = TextFieldValue("")

    /** To samo, co robi TextEditor.onTyped w widoku blokowym. */
    private fun onTyped(previous: String, typed: TextFieldValue): TextFieldValue? {
        if (previous == typed.text) {
            if (!pending.isEmpty) pending = PendingFormat()
            return null
        }
        TextFormat.typedNewline(previous, typed, pending)?.let {
            pending = it.carry
            return it.field
        }
        TextFormat.typedDeletion(previous, typed)?.let { return it }
        val applied = TextFormat.applyPending(typed, previous, pending) ?: return null
        pending = PendingFormat()
        return applied
    }

    private fun type(text: String) {
        val at = field.selection.start
        val typed = TextFieldValue(
            field.text.substring(0, at) + text + field.text.substring(at),
            TextRange(at + text.length),
        )
        field = onTyped(field.text, typed) ?: typed
    }

    private fun press(type: SpanType) {
        val active = pending.willHave(type, TextFormat.has(field, type))
        val changed = TextFormat.toggle(field, type)
        if (changed != null) {
            field = changed
        } else {
            pending = if (active) pending.without(type) else pending.with(type)
        }
    }

    /** Co widać w polu — bez znaczników, tak jak w notatce. */
    private fun shown(): String = RichText.parse(field.text).text

    @Test
    fun `kod, pisanie, nowa linia i pisanie dalej`() {
        press(SpanType.CODE)
        type("ddd")
        type("\n")
        type("cccc")

        assertThat(shown()).isEqualTo("ddd\ncccc")
        assertThat(field.text).isEqualTo("`ddd`\n`cccc`")
    }

    @Test
    fun `tlo tekstu, pisanie, nowa linia i pisanie dalej`() {
        press(SpanType.HIGHLIGHT)
        type("ddd")
        type("\n")
        type("cccc")

        assertThat(shown()).isEqualTo("ddd\ncccc")
        assertThat(field.text).isEqualTo("==ddd==\n==cccc==")
    }

    @Test
    fun `pogrubienie z kodem naraz`() {
        press(SpanType.BOLD)
        press(SpanType.CODE)
        type("dddcccc")

        assertThat(shown()).isEqualTo("dddcccc")
        assertThat(field.text).isEqualTo("**`dddcccc`**")
    }

    @Test
    fun `format zdjety przed nowa linia nie wraca sam`() {
        press(SpanType.CODE)
        type("kod")
        press(SpanType.CODE)
        type("\n")
        type("zwykly")

        assertThat(shown()).isEqualTo("kod\nzwykly")
        assertThat(field.text).isEqualTo("`kod`\nzwykly")
    }

    @Test
    fun `nowa linia za zamknietym formatem nie ciagnie go dalej`() {
        press(SpanType.CODE)
        type("kod")
        // Kursor za znacznikiem domykającym, a nie w środku.
        field = field.copy(selection = TextRange(field.text.length))
        type("\n")
        type("zwykly")

        assertThat(shown()).isEqualTo("kod\nzwykly")
        assertThat(field.text).isEqualTo("`kod`\nzwykly")
    }
}

/*
  Kasowanie w polu, w którym znaczników nie widać.

  Compose kasuje znaki ZAPISU, nie te, które widać. Backspace za „</span>"
  zabierał z niego „>", rozbity znacznik przestawał być znacznikiem i pokazywał
  się w notatce jako goły tekst. Dokładnie to widać było na tablecie.
*/
class DeletionTest {

    /** Kasowanie jednego znaku zapisu przed [cursor] — tak robi to Compose. */
    private fun backspace(previous: String, cursor: Int): TextFieldValue? =
        TextFormat.typedDeletion(
            previous = previous,
            typed = TextFieldValue(
                previous.removeRange(cursor - 1, cursor),
                TextRange(cursor - 1),
            ),
        )

    @Test
    fun `backspace za znacznikiem barwy nie rozbija go na goly tekst`() {
        val source = """Ala <span style="color:#c81e1e">ma</span> kota"""
        // Kursor tuż za domknięciem: Compose zabrałby z niego „>".
        val after = backspace(source, source.indexOf(" kota"))!!

        assertThat(after.text).doesNotContain("&lt;")
        assertThat(after.text).isEqualTo("""Ala <span style="color:#c81e1e">m</span> kota""")
    }

    @Test
    fun `backspace za gwiazdkami nie zostawia pojedynczej`() {
        val after = backspace("Ala **ma** kota", "Ala **ma**".length)!!

        assertThat(after.text).isEqualTo("Ala **m** kota")
    }

    @Test
    fun `wytarcie calej tresci znacznika zdejmuje go razem z nia`() {
        val once = backspace("Ala **ma** kota", "Ala **ma**".length)!!
        val twice = TextFormat.typedDeletion(
            once.text,
            TextFieldValue(
                once.text.removeRange(once.selection.start - 1, once.selection.start),
                TextRange(once.selection.start - 1),
            ),
        )!!

        // Pusta para nie ma czego objąć — schodzi razem z treścią.
        assertThat(twice.text).isEqualTo("Ala  kota")
        assertThat(twice.text).doesNotContain("*")
    }

    @Test
    fun `wytarcie tresci barwy zdejmuje caly znacznik`() {
        val source = """<span style="color:#c81e1e">x</span>"""
        val after = backspace(source, source.indexOf("</span>"))!!

        assertThat(after.text).isEqualTo("")
    }

    @Test
    fun `kasowanie samego znacznika bez tresci przed nim niczego nie gubi`() {
        // Kursor na samym początku treści, przed nim tylko znacznik otwierający.
        val source = """<span style="color:#c81e1e">ma</span>"""
        val after = backspace(source, source.indexOf("ma"))!!

        assertThat(after.text).isEqualTo(source)
    }

    @Test
    fun `zwykle kasowanie widocznego znaku dziala jak zawsze`() {
        assertThat(backspace("Ala ma kota", 6)!!.text).isEqualTo("Ala m kota")
    }

    @Test
    fun `podmiana zaznaczenia bierze format tego, co zastapila`() {
        // Zaznaczone „ma" w pogrubieniu, wpisane „to".
        val after = TextFormat.typedDeletion(
            previous = "Ala **ma** kota",
            typed = TextFieldValue("Ala **to** kota", TextRange(8)),
        )!!

        assertThat(after.text).isEqualTo("Ala **to** kota")
    }

    @Test
    fun `samo dopisywanie nie idzie ta droga`() {
        assertThat(
            TextFormat.typedDeletion("Ala", TextFieldValue("Alak", TextRange(4))),
        ).isNull()
    }
}
