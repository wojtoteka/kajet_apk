package wojtoteka.ovh.kajet.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.editor.text.FormatSpan
import wojtoteka.ovh.kajet.editor.text.RichText
import wojtoteka.ovh.kajet.editor.text.RichTextCodec
import wojtoteka.ovh.kajet.editor.text.SpanType

/*
  Jeden format źródłowy treści i przekład na zapis notatki w obie strony.

  Te testy pilnują rzeczy, na której stoi cała reszta: model -> zapis -> model
  nie ma prawa niczego zgubić ani przestawić. Zapis jest ten sam, który czyta
  serwer (src/lib/rich-text.ts), więc barwa i wielkość pisma muszą przechodzić
  tak samo w jedną i w drugą stronę.
*/
class RichTextTest {

    private val red = "#c81e1e"
    private val green = "#1f6b3a"
    private val blue = "#1b4f8c"

    /** Model -> zapis -> model. Tego pilnuje zadanie: bez zmian, co do zakresu. */
    private fun round(rich: RichText): RichText = RichText.parse(rich.toMarkdown())

    /** Zapis -> model -> zapis. Tego pilnuje synchronizacja: bez zmian, co do znaku. */
    private fun round(markdown: String): String = RichText.parse(markdown).toMarkdown()

    @Test
    fun `trzy kolory, pogrubienie i rozne rozmiary przechodza bez zmian`() {
        val text = "Ala ma kota i psa oraz rybki"
        val rich = RichText(
            text = text,
            spans = listOf(
                FormatSpan(0, 3, SpanType.COLOR, red),
                FormatSpan(4, 6, SpanType.COLOR, green),
                FormatSpan(7, 11, SpanType.COLOR, blue),
                FormatSpan(0, 6, SpanType.BOLD),
                FormatSpan(14, 17, SpanType.SIZE, "21"),
                FormatSpan(18, 22, SpanType.SIZE, "12"),
            ),
        )

        val after = round(rich)

        assertThat(after.text).isEqualTo(text)
        assertThat(after.spans.toSet()).isEqualTo(rich.spans.toSet())
    }

    @Test
    fun `zapis trzech kolorow jest taki, jaki czyta serwer`() {
        val rich = RichText(
            text = "raz dwa trzy",
            spans = listOf(
                FormatSpan(0, 3, SpanType.COLOR, red),
                FormatSpan(4, 7, SpanType.COLOR, green),
                FormatSpan(8, 12, SpanType.COLOR, blue),
            ),
        )

        assertThat(rich.toMarkdown()).isEqualTo(
            """<span style="color:#c81e1e">raz</span> """ +
                """<span style="color:#1f6b3a">dwa</span> """ +
                """<span style="color:#1b4f8c">trzy</span>""",
        )
    }

    @Test
    fun `rozmiar stoi na zewnatrz barwy, tak samo jak na serwerze`() {
        val rich = RichText(
            text = "duze",
            spans = listOf(
                FormatSpan(0, 4, SpanType.COLOR, red),
                FormatSpan(0, 4, SpanType.SIZE, "21"),
            ),
        )

        assertThat(rich.toMarkdown()).isEqualTo(
            """<span style="font-size:21px"><span style="color:#c81e1e">duze</span></span>""",
        )
        assertThat(round(rich).spans.toSet()).isEqualTo(rich.spans.toSet())
    }

    @Test
    fun `pogrubienie obejmuje barwe, tak samo jak na serwerze`() {
        val rich = RichText(
            text = "gruby zielony",
            spans = listOf(
                FormatSpan(0, 13, SpanType.BOLD),
                FormatSpan(0, 13, SpanType.COLOR, green),
            ),
        )

        // Dokładnie ten zapis sprawdza test serwera w rich-text.test.ts.
        assertThat(rich.toMarkdown())
            .isEqualTo("""**<span style="color:#1f6b3a">gruby zielony</span>**""")
    }

    @Test
    fun `kod w zdaniu jest formatem fragmentu i nie wpuszcza innych znacznikow`() {
        val model = RichText.parse("uzyj `println` tutaj")

        assertThat(model.text).isEqualTo("uzyj println tutaj")
        assertThat(model.spans).containsExactly(FormatSpan(5, 12, SpanType.CODE))
        assertThat(model.toMarkdown()).isEqualTo("uzyj `println` tutaj")

        // W środku kodu gwiazdki nic nie znaczą - treść zostaje znak w znak.
        val code = RichText.parse("`**to jest kod**`")
        assertThat(code.text).isEqualTo("**to jest kod**")
        assertThat(code.spans).containsExactly(FormatSpan(0, 15, SpanType.CODE))
        assertThat(code.toMarkdown()).isEqualTo("`**to jest kod**`")
    }

    @Test
    fun `kod w pogrubieniu ma grawisy przy samej tresci`() {
        val rich = RichText(
            text = "kod",
            spans = listOf(
                FormatSpan(0, 3, SpanType.BOLD),
                FormatSpan(0, 3, SpanType.CODE),
            ),
        )

        assertThat(rich.toMarkdown()).isEqualTo("**`kod`**")
        assertThat(round(rich).spans.toSet()).isEqualTo(rich.spans.toSet())
    }

    @Test
    fun `kursywa dziala takze w srodku slowa i przy spacji`() {
        // „Al*a ma k*ota" - dawniej gwiazdki wychodziły na wierzch, bo przed
        // otwierającą stała litera.
        val glued = RichText.parse("Al*a ma k*ota")
        assertThat(glued.text).isEqualTo("Ala ma kota")
        assertThat(glued.spans).containsExactly(FormatSpan(2, 8, SpanType.ITALIC))
        assertThat(glued.toMarkdown()).isEqualTo("Al*a ma k*ota")
    }

    @Test
    fun `wszystkie rodzaje naraz przechodza bez zmian`() {
        val rich = RichText(
            text = "wszystko",
            spans = SpanType.entries.map { type ->
                FormatSpan(
                    start = 0,
                    end = 8,
                    type = type,
                    value = when (type) {
                        SpanType.COLOR -> red
                        SpanType.SIZE -> "18"
                        else -> ""
                    },
                )
            },
        )

        val after = round(rich)

        assertThat(after.text).isEqualTo("wszystko")
        assertThat(after.spans.toSet()).isEqualTo(rich.spans.toSet())
    }

    @Test
    fun `zapis notatki wraca znak w znak`() {
        val notes = listOf(
            "zwykły tekst",
            "**mocno** i *ukosem* i ~~skreślone~~ i ==ważne==",
            "<u>podkreślone</u>",
            """<span style="color:#c81e1e">czerwone</span>""",
            """<span style="font-size:21px">większe</span>""",
            """**<span style="color:#1f6b3a">gruby zielony</span>**""",
            """<u>pod <span style="color:#665222">kolor</span></u>""",
            "# Nagłówek z **pogrubieniem**",
            "- [ ] zadanie z <u>podkreśleniem</u>",
            "> cytat z **mocnym**",
            "raz\ndwa\n\ntrzy",
        )

        for (note in notes) {
            assertThat(round(note)).isEqualTo(note)
        }
    }

    @Test
    fun `barwa przechodzi w obie strony, takze pisana wielkimi literami`() {
        val model = RichText.parse("""<span style="color:#C81E1E">czerwone</span>""")

        assertThat(model.text).isEqualTo("czerwone")
        assertThat(model.spans)
            .containsExactly(FormatSpan(0, 8, SpanType.COLOR, red))
        // Wraca w postaci kanonicznej - takiej, jaką pisze serwer.
        assertThat(model.toMarkdown())
            .isEqualTo("""<span style="color:#c81e1e">czerwone</span>""")
    }

    @Test
    fun `format nie przechodzi na nastepny wiersz`() {
        val rich = RichText(
            text = "raz\ndwa",
            spans = listOf(FormatSpan(0, 7, SpanType.COLOR, red)),
        )

        assertThat(rich.toMarkdown()).isEqualTo(
            """<span style="color:#c81e1e">raz</span>""" + "\n" +
                """<span style="color:#c81e1e">dwa</span>""",
        )
    }

    // --- Naprawa starych notatek ---

    @Test
    fun `zagniezdzone znaczniki schodza do jednego, wewnetrzny wygrywa`() {
        val broken = """raz <span style="color:#111111"><span style="color:#665222">x</span></span> dwa"""
        val once = RichTextCodec.flatten(broken)

        assertThat(once).isEqualTo("""raz <span style="color:#665222">x</span> dwa""")
        assertThat(RichTextCodec.flatten(once)).isEqualTo(once)
    }

    @Test
    fun `osierocone domkniecie znika`() {
        assertThat(RichTextCodec.flatten("tekst</span> dalej")).isEqualTo("tekst dalej")
    }

    @Test
    fun `pusty znacznik znika`() {
        assertThat(RichTextCodec.flatten("""raz <span style="color:#665222"></span>dwa"""))
            .isEqualTo("raz dwa")
    }

    @Test
    fun `znacznik bez domkniecia dziala do konca wiersza`() {
        assertThat(RichTextCodec.flatten("""<span style="color:#665222">bez konca""" + "\nczysty"))
            .isEqualTo("""<span style="color:#665222">bez konca</span>""" + "\nczysty")
    }

    @Test
    fun `sasiednie znaczniki o tych samych atrybutach schodza sie w jeden`() {
        val text = """<span style="color:#665222">raz</span><span style="color:#665222"> dwa</span>"""
        assertThat(RichTextCodec.flatten(text))
            .isEqualTo("""<span style="color:#665222">raz dwa</span>""")
    }

    @Test
    fun `rozmiar i kolor w obu porzadkach wychodza kanonicznie`() {
        val canonical = """<span style="font-size:21px"><span style="color:#665222">x</span></span>"""
        val swapped = """<span style="color:#665222"><span style="font-size:21px">x</span></span>"""

        assertThat(RichTextCodec.flatten(canonical)).isEqualTo(canonical)
        assertThat(RichTextCodec.flatten(swapped)).isEqualTo(canonical)
    }

    @Test
    fun `nieznany znacznik zostaje w tresci razem z domknieciem`() {
        val text = """<span style="background:#fff">obcy</span>"""
        assertThat(RichTextCodec.flatten(text)).isEqualTo(text)
    }

    @Test
    fun `blok kodu zostaje co do znaku`() {
        val text = "przed\n```\n<span style=\"color:#665222\">to jest kod\n```\npo</span>"
        val repaired = RichTextCodec.flatten(text)

        assertThat(repaired).contains("```\n<span style=\"color:#665222\">to jest kod\n```")
        assertThat(repaired).endsWith("po")
    }

    @Test
    fun `gwiazdka listy i mnozenie nie robia kursywy`() {
        assertThat(RichText.parse("* mleko").text).isEqualTo("* mleko")
        assertThat(RichText.parse("2 * 3 * 4").text).isEqualTo("2 * 3 * 4")
    }

    @Test
    fun `gwiazdki w kodzie nie robia pogrubienia`() {
        val text = "`**to jest kod**`"
        val model = RichText.parse(text)

        // Grawisy schodzą jako znacznik kodu, ale gwiazdki w środku zostają
        // treścią - pogrubienia z nich nie ma i zapis wraca bez zmian.
        assertThat(model.spans.map { it.type }).containsExactly(SpanType.CODE)
        assertThat(model.toMarkdown()).isEqualTo(text)
    }

    @Test
    fun `odnosnik i zdjecie zostaja w tresci`() {
        assertThat(round("[opis](https://kajet.wojtoteka.ovh)"))
            .isEqualTo("[opis](https://kajet.wojtoteka.ovh)")
        assertThat(round("![zdjęcie](assets/z.png)")).isEqualTo("![zdjęcie](assets/z.png)")
    }

    // --- Odczyt formatów pod kursorem ---

    @Test
    fun `pasek widzi format pod kursorem`() {
        val rich = RichText.parse("""zwykły **mocny** i <span style="color:#c81e1e">czerwony</span>""")
        val plain = rich.text // "zwykły mocny i czerwony"

        assertThat(rich.formatsAt(plain.indexOf("mocny") + 2).map { it.type })
            .containsExactly(SpanType.BOLD)
        assertThat(rich.formatsAt(plain.indexOf("czerwony") + 2))
            .containsExactly(FormatSpan(15, 23, SpanType.COLOR, red))
        assertThat(rich.formatsAt(3)).isEmpty()
    }

    @Test
    fun `format zaznaczenia liczy sie tylko wtedy, gdy obejmuje calosc`() {
        val rich = RichText.parse("**raz** dwa")

        assertThat(rich.formatsIn(0, 3).map { it.type }).containsExactly(SpanType.BOLD)
        // "raz dwa" - pogrubiona jest tylko połowa, więc pasek go nie zapala.
        assertThat(rich.formatsIn(0, 7)).isEmpty()
    }

    @Test
    fun `kratki naglowka licza sie takze poskladane`() {
        assertThat(RichTextCodec.isHeadingMarker("# ")).isTrue()
        assertThat(RichTextCodec.isHeadingMarker("### ")).isTrue()
        assertThat(RichTextCodec.isHeadingMarker("- ")).isFalse()

        assertThat(RichTextCodec.headingPrefixLength("# Tytul")).isEqualTo(2)
        assertThat(RichTextCodec.headingPrefixLength("## Tytul")).isEqualTo(3)
        assertThat(RichTextCodec.headingPrefixLength("## # Tytul")).isEqualTo(5)
        assertThat(RichTextCodec.headingPrefixLength("# ### Tytul")).isEqualTo(6)
        assertThat(RichTextCodec.headingPrefixLength("Tytul")).isEqualTo(0)
        assertThat(RichTextCodec.headingPrefixLength("####### nie naglowek")).isEqualTo(0)
    }
}
