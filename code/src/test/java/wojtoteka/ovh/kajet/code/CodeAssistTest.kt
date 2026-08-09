package wojtoteka.ovh.kajet.code

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.core.model.CodeLanguage

class CodeAssistTest {

    /** Pisanie jednego znaku: „ab|" + „(" -> stan pola tak, jak zrobiłby to system. */
    private fun type(text: String, sign: Char, language: CodeLanguage = CodeLanguage.PYTHON): TextFieldValue {
        val at = text.indexOf('|')
        val plain = text.replace("|", "")
        val before = TextFieldValue(plain, TextRange(at))
        val after = TextFieldValue(
            plain.substring(0, at) + sign + plain.substring(at),
            TextRange(at + 1),
        )
        return CodeAssist.assist(before, after, language)
    }

    /** Zapis wyniku z zaznaczonym miejscem kursora - czyta się to jak w teście. */
    private fun show(value: TextFieldValue): String =
        value.text.substring(0, value.selection.start) + "|" +
            value.text.substring(value.selection.start)

    @Test
    fun `nawias domyka sie i zostawia kursor w srodku`() {
        assertThat(show(type("print|", '('))).isEqualTo("print(|)")
    }

    @Test
    fun `klamra i kwadratowy tak samo`() {
        assertThat(show(type("x = |", '['))).isEqualTo("x = [|]")
        assertThat(show(type("f |", '{'))).isEqualTo("f {|}")
    }

    @Test
    fun `cudzyslow domyka sie tylko tam gdzie zaczyna napis`() {
        assertThat(show(type("name = |", '"'))).isEqualTo("name = \"|\"")
        // Apostrof w słowie zostaje apostrofem.
        assertThat(show(type("don|", '\''))).isEqualTo("don'|")
    }

    @Test
    fun `nie domyka przed slowem`() {
        // „(" przed istniejącą treścią domknęłoby ją w niewłaściwym miejscu.
        assertThat(show(type("|nazwa", '('))).isEqualTo("(|nazwa")
    }

    @Test
    fun `wlasne domkniecie przechodzi przez to ktore juz stoi`() {
        val before = TextFieldValue("print()", TextRange(6))
        val after = TextFieldValue("print())", TextRange(7))
        assertThat(show(CodeAssist.assist(before, after, CodeLanguage.PYTHON)))
            .isEqualTo("print()|")
    }

    @Test
    fun `cudze domkniecie nie jest przeskakiwane`() {
        // Kursor stoi w środku pary, której nie domykaliśmy sami.
        val before = TextFieldValue("f(a", TextRange(3))
        val after = TextFieldValue("f(a)", TextRange(4))
        assertThat(show(CodeAssist.assist(before, after, CodeLanguage.PYTHON)))
            .isEqualTo("f(a)|")
    }

    @Test
    fun `cofniecie kasuje pusta pare w calosci`() {
        val before = TextFieldValue("print()", TextRange(6))
        val after = TextFieldValue("print)", TextRange(5))
        assertThat(show(CodeAssist.assist(before, after, CodeLanguage.PYTHON)))
            .isEqualTo("print|")
    }

    @Test
    fun `cofniecie nie rusza pary z trescia w srodku`() {
        val before = TextFieldValue("print(a)", TextRange(6))
        val after = TextFieldValue("printa)", TextRange(5))
        assertThat(show(CodeAssist.assist(before, after, CodeLanguage.PYTHON)))
            .isEqualTo("print|a)")
    }

    @Test
    fun `html domyka znacznik`() {
        assertThat(show(type("<p|", '>', CodeLanguage.HTML))).isEqualTo("<p>|</p>")
    }

    @Test
    fun `html domyka znacznik z atrybutami`() {
        assertThat(show(type("""<div class="a"|""", '>', CodeLanguage.HTML)))
            .isEqualTo("""<div class="a">|</div>""")
    }

    @Test
    fun `html nie domyka znacznikow bez tresci`() {
        assertThat(show(type("<br|", '>', CodeLanguage.HTML))).isEqualTo("<br>|")
        assertThat(show(type("<img src=\"a\"|", '>', CodeLanguage.HTML))).isEqualTo("<img src=\"a\">|")
    }

    @Test
    fun `html nie domyka znacznika juz domknietego`() {
        assertThat(show(type("<p>tekst</p|", '>', CodeLanguage.HTML)))
            .isEqualTo("<p>tekst</p>|")
        assertThat(show(type("<span /|", '>', CodeLanguage.HTML))).isEqualTo("<span />|")
    }

    @Test
    fun `poza html znacznik zostaje zwyklym znakiem`() {
        assertThat(show(type("a <p|", '>', CodeLanguage.PYTHON))).isEqualTo("a <p>|")
    }

    @Test
    fun `wklejenie wiekszego kawalka nie jest ruszane`() {
        val before = TextFieldValue("", TextRange(0))
        val after = TextFieldValue("def f():\n    pass", TextRange(17))
        assertThat(CodeAssist.assist(before, after, CodeLanguage.PYTHON)).isEqualTo(after)
    }

    @Test
    fun `enter przepisuje wciecie poprzedniego wiersza`() {
        val before = TextFieldValue("def f():\n    pass", TextRange(17))
        val after = TextFieldValue("def f():\n    pass\n", TextRange(18))
        assertThat(show(CodeAssist.keepIndent(before, after)))
            .isEqualTo("def f():\n    pass\n    |")
    }

    @Test
    fun `enter bez wciecia nic nie dokłada`() {
        val before = TextFieldValue("x = 1", TextRange(5))
        val after = TextFieldValue("x = 1\n", TextRange(6))
        assertThat(CodeAssist.keepIndent(before, after)).isEqualTo(after)
    }
}
