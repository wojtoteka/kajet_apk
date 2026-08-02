package wojtoteka.ovh.kajet.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.editor.tekst.Markdown

class MarkdownTest {

    @Test
    fun `naglowki zamieniaja sie na znaczniki h`() {
        assertThat(Markdown.doHtml("# Tytuł")).contains("<h1>Tytuł</h1>")
        assertThat(Markdown.doHtml("### Podpunkt")).contains("<h3>Podpunkt</h3>")
    }

    @Test
    fun `krzyzyk bez spacji nie robi naglowka`() {
        assertThat(Markdown.doHtml("#hashtag")).contains("<p>#hashtag</p>")
    }

    @Test
    fun `pogrubienie i kursywa dzialaja osobno`() {
        assertThat(Markdown.wLinii("**mocno**")).isEqualTo("<strong>mocno</strong>")
        assertThat(Markdown.wLinii("*ukosem*")).isEqualTo("<em>ukosem</em>")
        assertThat(Markdown.wLinii("**mocno** i *ukosem*"))
            .isEqualTo("<strong>mocno</strong> i <em>ukosem</em>")
    }

    @Test
    fun `podkreslenie w srodku slowa nie robi kursywy`() {
        assertThat(Markdown.wLinii("nazwa_pliku_tekstowego")).isEqualTo("nazwa_pliku_tekstowego")
    }

    @Test
    fun `kod w tekscie chroni gwiazdki`() {
        assertThat(Markdown.wLinii("`a * b`")).isEqualTo("<code>a * b</code>")
    }

    @Test
    fun `lista zadan ma pole do odhaczenia z numerem wiersza`() {
        val html = Markdown.doHtml("- [ ] Zrobić zadanie\n- [x] Przeczytać")
        assertThat(html).contains("data-wiersz=\"0\"")
        assertThat(html).contains("data-wiersz=\"1\"")
        assertThat(html).contains("checked")
        assertThat(html).contains("class=\"zrobione\"")
    }

    @Test
    fun `odhaczenie zadania zmienia tylko wskazany wiersz`() {
        val zrodlo = "- [ ] Pierwsze\n- [ ] Drugie"
        val po = Markdown.przelaczZadanie(zrodlo, 1)
        assertThat(po).isEqualTo("- [ ] Pierwsze\n- [x] Drugie")
    }

    @Test
    fun `odhaczenie dziala w obie strony`() {
        val zrodlo = "- [x] Gotowe"
        assertThat(Markdown.przelaczZadanie(zrodlo, 0)).isEqualTo("- [ ] Gotowe")
    }

    @Test
    fun `blok kodu zachowuje wciecia i nie interpretuje znacznikow`() {
        val html = Markdown.doHtml("```python\nif a * b:\n    print(\"**test**\")\n```")
        assertThat(html).contains("<pre class=\"kod\" data-jezyk=\"python\">")
        assertThat(html).contains("    print(\"**test**\")")
        assertThat(html).doesNotContain("<strong>")
    }

    @Test
    fun `wzor w osobnej linii zostaje dla KaTeX`() {
        val html = Markdown.doHtml("$$\\int_0^1 x^2 dx$$")
        assertThat(html).contains("class=\"wzor\"")
        assertThat(html).contains("\\int_0^1 x^2 dx")
    }

    @Test
    fun `wzor w tekscie dostaje nawiasy zrozumiale dla KaTeX`() {
        assertThat(Markdown.wLinii("pole to \$a^2\$ metrów")).contains("\\(a^2\\)")
    }

    @Test
    fun `obrazek z katalogu notatki dostaje przechwytywany adres`() {
        val html = Markdown.wLinii("![wykres](assets/wykres.png)")
        assertThat(html).contains(Markdown.ADRES_ZALACZNIKOW + "wykres.png")
        assertThat(html).contains("alt=\"wykres\"")
    }

    @Test
    fun `znaki html w tresci sa bezpieczne`() {
        assertThat(Markdown.wLinii("a < b & c > d")).isEqualTo("a &lt; b &amp; c &gt; d")
    }

    @Test
    fun `cytat sklada kilka wierszy w jeden blok`() {
        val html = Markdown.doHtml("> Pierwszy wiersz\n> Drugi wiersz")
        assertThat(html).contains("<blockquote>Pierwszy wiersz Drugi wiersz</blockquote>")
    }

    @Test
    fun `akapit sklada wiersze bez pustej linii`() {
        val html = Markdown.doHtml("Pierwszy wiersz\ndrugi wiersz\n\nOsobny akapit")
        assertThat(html).contains("<p>Pierwszy wiersz drugi wiersz</p>")
        assertThat(html).contains("<p>Osobny akapit</p>")
    }

    @Test
    fun `lista numerowana i zwykla nie mieszaja sie`() {
        val html = Markdown.doHtml("- jeden\n- dwa\n\n1. pierwszy\n2. drugi")
        assertThat(html).contains("<ul>")
        assertThat(html).contains("<ol>")
        assertThat(html.indexOf("</ul>")).isLessThan(html.indexOf("<ol>"))
    }

    @Test
    fun `pierwsza linia sluzy jako podpowiedz tytulu`() {
        assertThat(Markdown.pierwszaLinia("\n\n## Całki oznaczone\ntreść")).isEqualTo("Całki oznaczone")
    }
}
