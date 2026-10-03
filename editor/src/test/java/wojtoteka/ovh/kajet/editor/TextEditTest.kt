package wojtoteka.ovh.kajet.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.core.model.NoteAlign
import wojtoteka.ovh.kajet.editor.text.PendingFormat
import wojtoteka.ovh.kajet.editor.text.SpanType
import wojtoteka.ovh.kajet.editor.text.TextEdit
import wojtoteka.ovh.kajet.editor.text.TextFormat
import wojtoteka.ovh.kajet.editor.text.TextLayout

/*
  Pisanie w polu, w którym znaczników nie widać - dokładnie tak, jak robi to
  Compose: klawiatura pisze i kasuje znaki ZAPISU w miejscu kursora, a kursor
  po stuknięciu stoi tam, gdzie wskaże mapowanie InlineStyle (TextLayout).

  Każdy test pilnuje jednego: to, co widać, zmienia się tak, jak w Wordzie,
  a żaden ukryty znacznik nie wychodzi na wierzch.
*/
class TextEditTest {

    private val centre = """<p style="text-align:center">"""

    /** Pole edytora: zapis, kursor i formaty czekające na pisanie. */
    private inner class Field(text: String, visibleCursor: Int = TextLayout.of(text).visible.length) {
        var value = TextFieldValue(text, TextRange(TextLayout.of(text).sourceCursor(visibleCursor)))
        var pending = PendingFormat()

        val shown: String get() = TextLayout.of(value.text).visible
        val text: String get() = value.text
        val visibleCursor: Int get() = TextLayout.of(value.text).visibleOfSource(value.selection.start)

        /** Stuknięcie w widoczne miejsce tekstu. */
        fun tap(visible: Int) {
            value = value.copy(selection = TextRange(TextLayout.of(value.text).sourceCursor(visible)))
            pending = PendingFormat()
        }

        /** Zaznaczenie widocznego zakresu palcem. */
        fun select(from: Int, to: Int) {
            val layout = TextLayout.of(value.text)
            value = value.copy(selection = TextRange(layout.sourceCursor(from), layout.sourceCursor(to)))
        }

        private fun apply(typed: TextFieldValue) {
            val outcome = TextEdit.typed(value, typed, pending)
            pending = outcome.pending
            value = outcome.field ?: typed
        }

        /** Klawiatura wpisuje [text] w miejsce kursora (albo zaznaczenia). */
        fun type(text: String) {
            val s = value.selection
            val next = value.text.substring(0, s.min) + text + value.text.substring(s.max)
            apply(TextFieldValue(next, TextRange(s.min + text.length)))
        }

        fun enter() = type("\n")

        /** Backspace: Compose kasuje jeden znak ZAPISU przed kursorem. */
        fun backspace() {
            val s = value.selection
            if (!s.collapsed) {
                apply(TextFieldValue(value.text.removeRange(s.min, s.max), TextRange(s.min)))
                return
            }
            if (s.start == 0) return
            apply(TextFieldValue(value.text.removeRange(s.start - 1, s.start), TextRange(s.start - 1)))
        }

        fun press(type: SpanType) {
            val active = pending.willHave(type, TextFormat.has(value, type))
            val changed = TextFormat.toggle(value, type)
            if (changed != null) {
                value = changed
            } else {
                pending = if (active) pending.without(type) else pending.with(type)
            }
        }

        fun align(align: NoteAlign) {
            value = TextFormat.alignLines(value, align, NoteAlign.LEFT)
        }

        fun line(marker: String) {
            value = TextFormat.beforeLine(value, marker)
        }
    }

    // --- Nagłówki: ukryte kratki ---

    @Test
    fun `backspace na poczatku naglowka nie zostawia golej kratki`() {
        val field = Field("# Tytul", visibleCursor = 0)
        field.backspace()

        assertThat(field.shown).isEqualTo("Tytul")
        assertThat(field.text).isEqualTo("Tytul")
    }

    @Test
    fun `backspace na poczatku naglowka w srodku laczy go z poprzednim akapitem`() {
        val field = Field("Ala ma\n## Kota", visibleCursor = 7)
        field.backspace()

        assertThat(field.shown).isEqualTo("Ala maKota")
        assertThat(field.text).isEqualTo("Ala maKota")
        assertThat(field.text).doesNotContain("#")
    }

    @Test
    fun `pisanie na poczatku naglowka idzie do tresci, nie przed kratki`() {
        val field = Field("# Tytul", visibleCursor = 0)
        field.type("Nowy ")

        assertThat(field.text).isEqualTo("# Nowy Tytul")
        assertThat(field.shown).isEqualTo("Nowy Tytul")
    }

    @Test
    fun `enter na koncu naglowka zaczyna zwykly akapit`() {
        val field = Field("# Tytul")
        field.enter()
        field.type("tresc")

        assertThat(field.text).isEqualTo("# Tytul\ntresc")
        assertThat(field.shown).isEqualTo("Tytul\ntresc")
    }

    @Test
    fun `wyczyszczenie naglowka i dalsze kasowanie nie zostawia kratki`() {
        val field = Field("Ala\n# Ty")
        field.backspace()
        field.backspace()
        field.backspace()

        assertThat(field.shown).isEqualTo("Ala")
        assertThat(field.text).isEqualTo("Ala")
    }

    @Test
    fun `cytat tak samo`() {
        val field = Field("> cytat", visibleCursor = 0)
        field.backspace()

        assertThat(field.text).isEqualTo("cytat")
    }

    // --- Listy ---

    @Test
    fun `backspace za kropka listy zdejmuje cala kropke`() {
        val field = Field("- mleko", visibleCursor = 2)
        field.backspace()

        assertThat(field.text).isEqualTo("mleko")
        assertThat(field.shown).isEqualTo("mleko")
    }

    @Test
    fun `lista ciagnie sie enterem i konczy pustym enterem`() {
        val field = Field("1. mleko")
        field.enter()
        field.type("chleb")
        field.enter()
        field.enter()
        field.type("koniec")

        assertThat(field.text).isEqualTo("1. mleko\n2. chleb\nkoniec")
    }

    @Test
    fun `pisanie przed kropka listy idzie do tresci`() {
        val field = Field("- mleko", visibleCursor = 0)
        field.type("swieze ")

        assertThat(field.text).isEqualTo("- swieze mleko")
    }

    // --- Ułożenie akapitu ---

    @Test
    fun `wyrownanie dotyczy tylko akapitu z kursorem`() {
        val field = Field("Pierwszy\nDrugi\nTrzeci", visibleCursor = 11)
        field.align(NoteAlign.CENTER)

        assertThat(field.text).isEqualTo("Pierwszy\n${centre}Drugi</p>\nTrzeci")
        assertThat(field.shown).isEqualTo("Pierwszy\nDrugi\nTrzeci")
        // Kursor zostaje w tym samym miejscu słowa.
        assertThat(field.visibleCursor).isEqualTo(11)
    }

    @Test
    fun `pisanie na koncu wyrownanego akapitu zostaje w akapicie`() {
        val field = Field("${centre}Ala</p>")
        field.type(" ma kota")

        assertThat(field.text).isEqualTo("${centre}Ala ma kota</p>")
        assertThat(field.shown).isEqualTo("Ala ma kota")
    }

    @Test
    fun `enter w wyrownanym akapicie zostawia wyrownanie obu`() {
        val field = Field("${centre}Ala</p>")
        field.enter()
        field.type("kot")

        assertThat(field.text).isEqualTo("${centre}Ala</p>\n${centre}kot</p>")
        assertThat(field.shown).isEqualTo("Ala\nkot")
    }

    @Test
    fun `enter w srodku wyrownanego akapitu tnie go na dwa wyrownane`() {
        val field = Field("${centre}Alakot</p>", visibleCursor = 3)
        field.enter()

        assertThat(field.text).isEqualTo("${centre}Ala</p>\n${centre}kot</p>")
    }

    @Test
    fun `backspace na poczatku wyrownanego akapitu skleja z poprzednim`() {
        val field = Field("${centre}Ala</p>\n${centre}kot</p>", visibleCursor = 4)
        field.backspace()

        assertThat(field.text).isEqualTo("${centre}Alakot</p>")
        assertThat(field.shown).isEqualTo("Alakot")
    }

    @Test
    fun `kasowanie calej tresci wyrownanego akapitu nie zostawia znacznika na wierzchu`() {
        val field = Field("${centre}Ala</p>")
        repeat(3) { field.backspace() }

        assertThat(field.shown).isEqualTo("")
        repeat(3) { field.backspace() }
        assertThat(field.shown).isEqualTo("")
        assertThat(field.text).doesNotContain("text-align:center\"><")
    }

    @Test
    fun `naglowek na srodku i jego zmiana na H2`() {
        val field = Field("Tytul", visibleCursor = 2)
        field.align(NoteAlign.CENTER)
        field.line("# ")
        field.line("## ")

        assertThat(field.text).isEqualTo("${centre}## Tytul</p>")
        assertThat(field.shown).isEqualTo("Tytul")
    }

    @Test
    fun `do lewej zdejmuje znacznik`() {
        val field = Field("${centre}Ala</p>", visibleCursor = 1)
        field.align(NoteAlign.LEFT)

        assertThat(field.text).isEqualTo("Ala")
    }

    @Test
    fun `zaznaczenie przez kilka akapitow wyrownuje wszystkie, ale nie puste odstepy`() {
        val field = Field("raz\n\ndwa")
        field.select(0, 8)
        field.align(NoteAlign.RIGHT)

        val right = """<p style="text-align:right">"""
        assertThat(field.text).isEqualTo("${right}raz</p>\n\n${right}dwa</p>")
    }

    // --- Formaty fragmentu ---

    @Test
    fun `pisanie za pogrubionym slowem pisze dalej grubo, jak w Wordzie`() {
        val field = Field("**Ala**")
        field.type(" ma")

        assertThat(field.text).isEqualTo("**Ala ma**")
    }

    @Test
    fun `wylaczenie pogrubienia przed pisaniem pisze dalej zwykle`() {
        val field = Field("**Ala**")
        field.press(SpanType.BOLD)
        field.type(" ma")

        assertThat(field.text).isEqualTo("**Ala** ma")
        assertThat(field.shown).isEqualTo("Ala ma")
    }

    @Test
    fun `enter w pogrubieniu ciagnie pogrubienie do nowego wiersza`() {
        val field = Field("**Ala**")
        field.enter()
        field.type("kot")

        assertThat(field.text).isEqualTo("**Ala**\n**kot**")
    }

    @Test
    fun `backspace za barwa nie rozbija znacznika`() {
        val field = Field("""Ala <span style="color:#c81e1e">ma</span> kota""", visibleCursor = 6)
        field.backspace()

        assertThat(field.text).isEqualTo("""Ala <span style="color:#c81e1e">m</span> kota""")
        field.backspace()
        assertThat(field.text).isEqualTo("Ala  kota")
    }

    @Test
    fun `zaznaczenie przez dwa wiersze i wpisanie tekstu`() {
        val field = Field("# Tytul\nakapit")
        field.select(2, 9)
        field.type("X")

        assertThat(field.text).isEqualTo("# TyXpit")
        assertThat(field.shown).isEqualTo("TyXpit")
    }

    @Test
    fun `wklejenie kilku wierszy w srodek akapitu`() {
        val field = Field("AlaKot", visibleCursor = 3)
        field.type(" ma\n- punkt\nkoniec ")

        assertThat(field.text).isEqualTo("Ala ma\n- punkt\nkoniec Kot")
    }

    // --- Odnośniki ---

    @Test
    fun `kasowanie w opisie odnosnika zostawia odnosnik`() {
        val field = Field("zobacz [strona](https://a.pl) dalej", visibleCursor = 9)
        field.backspace()

        assertThat(field.text).isEqualTo("zobacz [srona](https://a.pl) dalej")
    }

    @Test
    fun `kasowanie przez granice odnosnika zostawia sam tekst`() {
        val field = Field("zobacz [strona](https://a.pl) dalej")
        field.select(10, 16)
        field.backspace()

        assertThat(field.shown).isEqualTo("zobacz strlej")
        assertThat(field.text).doesNotContain("](")
    }

    // --- Zwykłe pisanie nie rusza pola ---

    @Test
    fun `zwykle pisanie zostawia pole klawiatury nietkniete`() {
        val before = TextFieldValue("Ala ma", TextRange(6))
        val typed = TextFieldValue("Ala ma ", TextRange(7))
        val outcome = TextEdit.typed(before, typed, PendingFormat())

        assertThat(outcome.field).isNull()
    }

    @Test
    fun `zwykle pisanie w wyrownanym akapicie tez`() {
        val text = "${centre}Ala</p>"
        val layout = TextLayout.of(text)
        val cursor = layout.sourceCursor(3)
        val before = TextFieldValue(text, TextRange(cursor))
        val typed = TextFieldValue(text.substring(0, cursor) + "x" + text.substring(cursor), TextRange(cursor + 1))

        assertThat(TextEdit.typed(before, typed, PendingFormat()).field).isNull()
    }
}
