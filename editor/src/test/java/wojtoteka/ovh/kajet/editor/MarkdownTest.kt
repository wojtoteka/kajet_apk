package wojtoteka.ovh.kajet.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.editor.text.Markdown

class MarkdownTest {

    @Test
    fun `naglowki zamieniaja sie na znaczniki h`() {
        assertThat(Markdown.toHtml("# Tytuł")).contains("<h1>Tytuł</h1>")
        assertThat(Markdown.toHtml("### Podpunkt")).contains("<h3>Podpunkt</h3>")
    }

    @Test
    fun `krzyzyk bez spacji nie robi naglowka`() {
        assertThat(Markdown.toHtml("#hashtag")).contains("<p>#hashtag</p>")
    }

    @Test
    fun `pogrubienie i kursywa dzialaja osobno`() {
        assertThat(Markdown.inline("**mocno**")).isEqualTo("<strong>mocno</strong>")
        assertThat(Markdown.inline("*ukosem*")).isEqualTo("<em>ukosem</em>")
        assertThat(Markdown.inline("**mocno** i *ukosem*"))
            .isEqualTo("<strong>mocno</strong> i <em>ukosem</em>")
    }

    @Test
    fun `podkreslnik w srodku slowa nie robi kursywy`() {
        assertThat(Markdown.inline("nazwa_pliku_tekstowego")).isEqualTo("nazwa_pliku_tekstowego")
    }

    @Test
    fun `kod w tekscie chroni gwiazdki`() {
        assertThat(Markdown.inline("`a * b`")).isEqualTo("<code>a * b</code>")
    }

    @Test
    fun `lista zadan ma pole do odhaczenia z numerem wiersza`() {
        val html = Markdown.toHtml("- [ ] Zrobić zadanie\n- [x] Przeczytać")
        assertThat(html).contains("data-wiersz=\"0\"")
        assertThat(html).contains("data-wiersz=\"1\"")
        assertThat(html).contains("checked")
        assertThat(html).contains("class=\"zrobione\"")
    }

    @Test
    fun `wyroznienie staje sie znacznikiem mark`() {
        assertThat(Markdown.inline("zwykłe ==ważne==")).isEqualTo("zwykłe <mark>ważne</mark>")
    }

    @Test
    fun `podkreslenie i kolor przechodza jako znacznik HTML`() {
        assertThat(Markdown.inline("<u>tak</u>")).isEqualTo("<u>tak</u>")
        assertThat(Markdown.inline("""<span style="color:#C81E1E">czerwone</span>"""))
            .isEqualTo("""<span style="color:#C81E1E">czerwone</span>""")
    }

    @Test
    fun `nawiasy trojkatne spoza znacznikow nadal uciekaja`() {
        assertThat(Markdown.inline("a < b")).isEqualTo("a &lt; b")
    }

    @Test
    fun `blok kodu zachowuje wciecia i nie interpretuje znacznikow`() {
        val html = Markdown.toHtml("```python\nif a * b:\n    print(\"**test**\")\n```")
        assertThat(html).contains("<pre class=\"kod\" data-jezyk=\"python\">")
        assertThat(html).contains("    print(\"**test**\")")
        assertThat(html).doesNotContain("<strong>")
    }

    @Test
    fun `wzor w osobnej linii zostaje dla KaTeX`() {
        val html = Markdown.toHtml("$$\\int_0^1 x^2 dx$$")
        assertThat(html).contains("class=\"wzor\"")
        assertThat(html).contains("\\int_0^1 x^2 dx")
    }

    @Test
    fun `wzor w tekscie dostaje nawiasy zrozumiale dla KaTeX`() {
        assertThat(Markdown.inline("pole to \$a^2\$ metrów")).contains("\\(a^2\\)")
    }

    @Test
    fun `obrazek z katalogu notatki dostaje przechwytywany adres`() {
        val html = Markdown.inline("![wykres](assets/wykres.png)")
        assertThat(html).contains(Markdown.ATTACHMENT_BASE_URL + "wykres.png")
        assertThat(html).contains("alt=\"wykres\"")
    }

    @Test
    fun `znaki html w tresci sa bezpieczne`() {
        assertThat(Markdown.inline("a < b & c > d")).isEqualTo("a &lt; b &amp; c &gt; d")
    }

    @Test
    fun `cytat sklada kilka wierszy w jeden blok`() {
        val html = Markdown.toHtml("> Pierwszy wiersz\n> Drugi wiersz")
        assertThat(html).contains("<blockquote>Pierwszy wiersz Drugi wiersz</blockquote>")
    }

    @Test
    fun `akapit sklada wiersze bez pustej linii`() {
        val html = Markdown.toHtml("Pierwszy wiersz\ndrugi wiersz\n\nOsobny akapit")
        assertThat(html).contains("<p>Pierwszy wiersz drugi wiersz</p>")
        assertThat(html).contains("<p>Osobny akapit</p>")
    }

    @Test
    fun `lista numerowana i zwykla nie mieszaja sie`() {
        val html = Markdown.toHtml("- jeden\n- dwa\n\n1. pierwszy\n2. drugi")
        assertThat(html).contains("<ul>")
        assertThat(html).contains("<ol>")
        assertThat(html.indexOf("</ul>")).isLessThan(html.indexOf("<ol>"))
    }

    @Test
    fun `pierwsza linia sluzy jako podpowiedz tytulu`() {
        assertThat(Markdown.firstLine("\n\n## Całki oznaczone\ntreść")).isEqualTo("Całki oznaczone")
    }
}
