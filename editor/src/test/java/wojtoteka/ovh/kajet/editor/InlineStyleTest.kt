package wojtoteka.ovh.kajet.editor

import androidx.compose.ui.graphics.Color
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
}
