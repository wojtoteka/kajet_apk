package wojtoteka.ovh.kajet.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextAttachmentsTest {

    private val drawing = InlineDrawing(
        asset = "rysunek-1.png",
        source = "rysunek-1.strokes.json",
        width = 560f,
        height = 300f,
    )

    @Test
    fun `rysunek w tresci i jego kreski sa w uzyciu`() {
        val text = TextContent(markdown = "Ala\n\n![rysunek](assets/rysunek-1.png)", drawings = listOf(drawing))

        assertThat(TextAttachments.inUse(text, "rysunek-1.png")).isTrue()
        assertThat(TextAttachments.inUse(text, "rysunek-1.strokes.json")).isTrue()
        assertThat(TextAttachments.removedDrawings(text)).isEmpty()
    }

    @Test
    fun `usuniety rysunek nie jest w uzyciu razem ze swoimi kreskami`() {
        val text = TextContent(markdown = "Ala", drawings = listOf(drawing))

        assertThat(TextAttachments.inUse(text, "rysunek-1.png")).isFalse()
        assertThat(TextAttachments.inUse(text, "rysunek-1.strokes.json")).isFalse()
        assertThat(TextAttachments.removedDrawings(text)).containsExactly(drawing)
        assertThat(TextAttachments.withoutRemovedDrawings(text).drawings).isEmpty()
    }

    @Test
    fun `zdjecie obok innego w jednym wierszu i z ulozeniem tez sie liczy`() {
        val text = TextContent(
            markdown = """![a|25%](assets/a.png "srodek") ![b|25%](assets/zdjecie%20(2).png "srodek")""",
        )

        assertThat(TextAttachments.inUse(text, "a.png")).isTrue()
        assertThat(TextAttachments.inUse(text, "zdjecie (2).png")).isTrue()
        assertThat(TextAttachments.inUse(text, "c.png")).isFalse()
    }

    @Test
    fun `w razie watpliwosci plik zostaje`() {
        // Nazwa, która jest początkiem innej - lepiej zostawić niż skasować.
        val text = TextContent(markdown = "![x](assets/kot.png.kopia)")
        assertThat(TextAttachments.inUse(text, "kot.png")).isTrue()
    }
}
