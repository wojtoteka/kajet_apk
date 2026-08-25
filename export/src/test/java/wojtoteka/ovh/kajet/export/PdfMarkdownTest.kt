package wojtoteka.ovh.kajet.export

import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.core.design.FolderColor
import wojtoteka.ovh.kajet.core.design.InkPalette
import wojtoteka.ovh.kajet.core.model.MindEdge
import wojtoteka.ovh.kajet.core.model.MindMapContent
import wojtoteka.ovh.kajet.core.model.MindNode
import wojtoteka.ovh.kajet.core.model.NodeShape
import wojtoteka.ovh.kajet.core.model.NoteAlign
import wojtoteka.ovh.kajet.core.model.TextMarkers
import wojtoteka.ovh.kajet.core.text.EnglishStrings
import wojtoteka.ovh.kajet.core.text.PolishStrings

class PdfMarkdownTest {

    @Test
    fun `pogrubienie, kursywa, kod i podkreslenie zostaja w przebiegach`() {
        val runs = PdfMarkdown.runs("**grube** i *pochyle* oraz `kod` i <u>kreska</u>")
        assertThat(runs.joinToString("") { it.text }).isEqualTo("grube i pochyle oraz kod i kreska")
        assertThat(runs.single { it.text == "grube" }.bold).isTrue()
        assertThat(runs.single { it.text == "pochyle" }.italic).isTrue()
        assertThat(runs.single { it.text == "kod" }.code).isTrue()
        assertThat(runs.single { it.text == "kreska" }.underline).isTrue()
        assertThat(runs.any { "*" in it.text || "`" in it.text }).isFalse()
    }

    @Test
    fun `wyroznienie, skreslenie, barwa i rozmiar tez`() {
        val red = "#c81e1e"
        val runs = PdfMarkdown.runs(
            "==wazne== ~~stare~~ <span style=\"color:$red\">czerwone</span> " +
                "<span style=\"font-size:21px\">duze</span>",
        )
        assertThat(runs.single { it.text == "wazne" }.highlight).isTrue()
        assertThat(runs.single { it.text == "stare" }.strike).isTrue()
        assertThat(runs.single { it.text == "czerwone" }.color).isEqualTo(TextMarkers.colorFromHex(red))
        assertThat(runs.single { it.text == "duze" }.sizePx).isEqualTo(21f)
    }

    @Test
    fun `blok kodu otwiera sie na grawisach i wzorze`() {
        assertThat(PdfMarkdown.opensFence("```kotlin")).isEqualTo("```")
        assertThat(PdfMarkdown.opensFence("$$")).isEqualTo("$$")
        assertThat(PdfMarkdown.opensFence("zwykly wiersz")).isNull()
        assertThat(PdfMarkdown.closesFence("```", "```")).isTrue()
        assertThat(PdfMarkdown.closesFence("$$", "$$")).isTrue()
    }

    @Test
    fun `zdjecie z assets oddaje nazwe zalacznika i skale`() {
        assertThat(PdfMarkdown.images("![mapa](assets/mapa.png)"))
            .containsExactly(PdfMarkdown.ImageRef("mapa.png", 1f))
        assertThat(PdfMarkdown.images("![z](assets/z.png \"50%\")"))
            .containsExactly(PdfMarkdown.ImageRef("z.png", 0.5f))
        assertThat(PdfMarkdown.images("![z|25%](assets/z.png)"))
            .containsExactly(PdfMarkdown.ImageRef("z.png", 0.25f))
        assertThat(PdfMarkdown.images("zwykly tekst")).isEmpty()
        assertThat(PdfMarkdown.images("![siec](https://przyklad.pl/a.png)")).isEmpty()

        // Dwa zdjecia w jednym wierszu stoja obok siebie takze na wydruku,
        // a ulozenie wiersza czytamy z tytulu.
        val obok = PdfMarkdown.images(
            "![a|25%](assets/a.png \"srodek\") ![b|25%](assets/b.png)",
        )
        assertThat(obok).containsExactly(
            PdfMarkdown.ImageRef("a.png", 0.25f, NoteAlign.CENTER),
            PdfMarkdown.ImageRef("b.png", 0.25f, NoteAlign.CENTER),
        ).inOrder()

        assertThat(
            PdfMarkdown.attachmentBytes(
                { name -> if (name == "mapa.png") byteArrayOf(1) else null },
                "mapa.png",
            ),
        ).isEqualTo(byteArrayOf(1))
        assertThat(
            PdfMarkdown.attachmentBytes(
                { name -> if (name == "assets/mapa.png") byteArrayOf(2) else null },
                "mapa.png",
            ),
        ).isEqualTo(byteArrayOf(2))
    }

    @Test
    fun `owal to NodeShape OVAL, a nie zla nazwa OWAL`() {
        assertThat(NodeShape.OVAL.name).isEqualTo("OVAL")
        assertThat(NodeShape.OVAL.name).isNotEqualTo("OWAL")
        val oval = MindNode(id = "n", x = 0f, y = 0f, width = 80f, height = 40f, shape = NodeShape.OVAL)
        assertThat(PdfMarkdown.cornerRadius(oval)).isEqualTo(20f)
        assertThat(PdfMarkdown.cornerRadius(oval.copy(shape = NodeShape.RECTANGLE))).isEqualTo(3f)
    }

    @Test
    fun `zwinieta galaz nie idzie na kartke`() {
        val map = MindMapContent(
            nodes = listOf(
                MindNode(id = "korzen", x = 0f, y = 0f, text = "Ruch"),
                MindNode(id = "a", x = 10f, y = 0f, text = "Predkosc", collapsed = true),
                MindNode(id = "a1", x = 20f, y = 0f, text = "Srednia"),
                MindNode(id = "b", x = 0f, y = 10f, text = "Przyspieszenie"),
            ),
            edges = listOf(
                MindEdge("e1", "korzen", "a"),
                MindEdge("e2", "korzen", "b"),
                MindEdge("e3", "a", "a1"),
            ),
        )
        val visible = PdfMarkdown.visibleNodes(map).map { it.id }
        assertThat(visible).containsExactly("korzen", "a", "b")
        assertThat(PdfMarkdown.visibleIds(map)).doesNotContain("a1")
    }

    @Test
    fun `pismo wezla na papierze to grafit, nie krem z ciemnego motywu`() {
        val cream = MindNode(id = "n", x = 0f, y = 0f, textColor = InkPalette.DEFAULT_INK_DARK_ARGB)
        assertThat(PdfMarkdown.nodeTextInk(cream)).isEqualTo(PaperInk.INK)
        val graphite = MindNode(id = "n", x = 0f, y = 0f, colorId = FolderColor.Graphite.id)
        assertThat(PdfMarkdown.nodeInk(graphite))
            .isEqualTo(PaperInk.ink(FolderColor.Graphite.color(isDark = false).toArgb()))
    }

    @Test
    fun `opis PDF nie obiecuje ze wszystko wychodzi piksel w piksel`() {
        assertThat(ExportFormat.PDF.description(PolishStrings)).doesNotContain("dokładnie")
        assertThat(ExportFormat.PDF.description(EnglishStrings).lowercase()).doesNotContain("exactly")
    }
}
