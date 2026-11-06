package wojtoteka.ovh.kajet.editor.text

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/*
  Polecenie pisane wprost w notatce - tak, jak przychodzi z klawiatury:
  każdy znak to nowe pole, a to, co oddaje InlinePrompt, staje się polem
  z powrotem. Notatka nie może dostać z polecenia ani jednej litery.
*/
class InlinePromptTest {

    private class Field(text: String, cursor: Int = text.length) {
        val prompt = InlinePrompt()
        var value = TextFieldValue(text, TextRange(cursor))
        val sent = mutableListOf<String>()

        val text: String get() = value.text
        val cursor: Int get() = value.selection.start

        private fun apply(next: TextFieldValue) {
            value = when (val step = prompt.onTyped(value, next)) {
                InlinePrompt.Step.Pass -> next
                is InlinePrompt.Step.Keep -> step.field
                is InlinePrompt.Step.Send -> {
                    sent += step.prompt
                    step.field
                }
            }
        }

        fun type(typed: String) {
            for (char in typed) {
                val at = cursor
                apply(TextFieldValue(text.substring(0, at) + char + text.substring(at), TextRange(at + 1)))
            }
        }

        fun backspace() {
            val at = cursor
            if (at == 0) return
            apply(TextFieldValue(text.removeRange(at - 1, at), TextRange(at - 1)))
        }

        fun moveTo(at: Int) = apply(value.copy(selection = TextRange(at)))

        /** Klawiatura podmienia słowo przed kursorem razem z nową literą. */
        fun recompose(word: String, typed: String) {
            val at = cursor
            val from = at - word.length
            check(text.substring(from, at) == word)
            apply(TextFieldValue(text.substring(0, from) + word + typed + text.substring(at), TextRange(at + typed.length)))
        }
    }

    @Test
    fun `jeden średnik jest zwykłym średnikiem`() {
        val field = Field("Ala")
        field.type("; ma kota;")
        assertThat(field.text).isEqualTo("Ala; ma kota;")
        assertThat(field.prompt.isOpen).isFalse()
    }

    @Test
    fun `dwa średniki znikają i otwierają polecenie`() {
        val field = Field("Zadanie 3: 17*23 = ")
        field.type(";;")
        assertThat(field.text).isEqualTo("Zadanie 3: 17*23 = ")
        assertThat(field.cursor).isEqualTo(field.text.length)
        assertThat(field.prompt.isOpen).isTrue()
    }

    @Test
    fun `polecenie nie trafia do notatki, a Enter je wysyła`() {
        val field = Field("Zadanie 3: 17*23 = ")
        field.type(";;oblicz")
        assertThat(field.text).isEqualTo("Zadanie 3: 17*23 = ")
        assertThat(field.sent).isEmpty()

        field.type("\n")
        assertThat(field.text).isEqualTo("Zadanie 3: 17*23 = ")
        assertThat(field.sent).containsExactly("oblicz")
        assertThat(field.prompt.isOpen).isFalse()

        // Po wysłaniu pisze się już normalnie.
        field.type("391")
        assertThat(field.text).isEqualTo("Zadanie 3: 17*23 = 391")
    }

    @Test
    fun `polecenie w środku tekstu nie rusza tego, co za kursorem`() {
        val field = Field("Ala ma kota", cursor = 4)
        field.type(";;popraw\n")
        assertThat(field.text).isEqualTo("Ala ma kota")
        assertThat(field.cursor).isEqualTo(4)
        assertThat(field.sent).containsExactly("popraw")
    }

    @Test
    fun `Backspace kasuje polecenie, a na pustym oddaje jeden średnik`() {
        val field = Field("x ")
        field.type(";;ab")
        field.backspace()
        field.type("c\n")
        assertThat(field.sent).containsExactly("ac")

        val other = Field("x ")
        other.type(";;a")
        other.backspace()
        other.backspace()
        assertThat(other.prompt.isOpen).isFalse()
        assertThat(other.text).isEqualTo("x ;")
        other.type("y")
        assertThat(other.text).isEqualTo("x ;y")
    }

    @Test
    fun `ruch kursora porzuca polecenie`() {
        val field = Field("Ala ma kota")
        field.type(";;tajne")
        field.moveTo(0)
        assertThat(field.prompt.isOpen).isFalse()
        field.type("Hej ")
        assertThat(field.text).isEqualTo("Hej Ala ma kota")
        assertThat(field.sent).isEmpty()
    }

    @Test
    fun `zamknięcie porzuca polecenie bez śladu`() {
        val field = Field("Ala")
        field.type(";;coś")
        field.prompt.close()
        field.type("\n")
        assertThat(field.text).isEqualTo("Ala\n")
        assertThat(field.sent).isEmpty()
    }

    @Test
    fun `puste polecenie nic nie wysyła`() {
        val field = Field("Ala")
        field.type(";;   \n")
        assertThat(field.text).isEqualTo("Ala")
        assertThat(field.sent).isEmpty()
        assertThat(field.prompt.isOpen).isFalse()
    }

    @Test
    fun `słowo z notatki w kompozycji klawiatury nie wchodzi do polecenia`() {
        val field = Field("Ala ma")
        field.type(";;")
        field.recompose("ma", "k")
        field.recompose("ma", "o")
        field.type("\n")
        assertThat(field.text).isEqualTo("Ala ma")
        assertThat(field.sent).containsExactly("ko")
    }

    @Test
    fun `wklejony tekst z nową linią wysyła to, co przed nią`() {
        val field = Field("")
        field.type(";;")
        val pasted = TextFieldValue("rozwiąż\nreszta", TextRange(14))
        field.value = when (val step = field.prompt.onTyped(field.value, pasted)) {
            is InlinePrompt.Step.Send -> step.field.also { field.sent += step.prompt }
            else -> error("miało pójść: $step")
        }
        assertThat(field.text).isEmpty()
        assertThat(field.sent).containsExactly("rozwiąż")
    }

    @Test
    fun `polecenie niesie tekst sprzed kursora`() {
        assertThat(InlinePrompt.instruction("oblicz", "")).isEqualTo("oblicz")
        assertThat(InlinePrompt.instruction("oblicz", "Zadanie 3: 17*23 = "))
            .isEqualTo("oblicz\n\n(Kursor stoi w notatce tuż za: „Zadanie 3: 17*23 =”)")
        val long = "a".repeat(500)
        assertThat(InlinePrompt.instruction("x", long)).contains("„" + "a".repeat(200) + "”")
    }

    @Test
    fun `w bloku kodu surowego Markdownu średniki są kodem`() {
        val raw = "Tekst\n```kotlin\nfor (;"
        assertThat(InlinePrompt.insideFence(raw, raw.length)).isTrue()
        val closed = "```\nkod\n```\nTekst ;"
        assertThat(InlinePrompt.insideFence(closed, closed.length)).isFalse()
        val formula = "$$\nx;"
        assertThat(InlinePrompt.insideFence(formula, formula.length)).isTrue()
        val inline = "\$\$x\$\$ i dalej;"
        assertThat(InlinePrompt.insideFence(inline, inline.length)).isFalse()
    }
}
