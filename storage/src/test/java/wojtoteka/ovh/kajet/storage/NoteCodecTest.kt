package wojtoteka.ovh.kajet.storage

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import wojtoteka.ovh.kajet.core.model.HandwritingContent
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.InkTool
import wojtoteka.ovh.kajet.core.model.InputKind
import wojtoteka.ovh.kajet.core.model.MindEdge
import wojtoteka.ovh.kajet.core.model.MindMapContent
import wojtoteka.ovh.kajet.core.model.MindNode
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.model.NotePage
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode
import wojtoteka.ovh.kajet.core.model.RecognizedText
import wojtoteka.ovh.kajet.core.model.TextBoxElement
import wojtoteka.ovh.kajet.core.model.TextContent

class NoteCodecTest {

    private fun sampleStroke(id: String) = InkStroke(
        id = id,
        tool = InkTool.PEN,
        color = 0xFF23211D.toInt(),
        size = 2.4f,
        input = InputKind.STYLUS,
        points = listOf(
            10f, 20f, 0f, 0.35f, 0.9f, 1.2f,
            12.5f, 22.25f, 8f, 0.51f, 0.88f, 1.19f,
            15f, 25f, 16f, 0.62f, 0.85f, 1.18f,
        ),
    )

    private fun handwrittenNote() = NoteDocument(
        id = "n-1",
        kind = NoteKind.HANDWRITTEN,
        title = "Całki oznaczone",
        createdAt = 1_700_000_000_000,
        updatedAt = 1_700_000_100_000,
        tags = listOf("matematyka", "kolokwium"),
        favorite = true,
        handwriting = HandwritingContent(
            pageMode = PageMode.A4,
            background = PageBackground.GRID,
            pages = listOf(
                NotePage(
                    id = "s-1",
                    strokes = listOf(sampleStroke("k-1"), sampleStroke("k-2")),
                    texts = listOf(
                        TextBoxElement(
                            id = "t-1",
                            x = 100f, y = 120f, width = 200f, height = 40f,
                            text = "Wzór Newtona i Leibniza",
                            color = 0xFF23211D.toInt(),
                        ),
                    ),
                    recognized = listOf(
                        RecognizedText(
                            id = "r-1",
                            text = "całka oznaczona",
                            x = 50f, y = 60f, width = 180f, height = 30f,
                            strokeIds = listOf("k-1", "k-2"),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `notatka odreczna przezywa zapis i odczyt bez zmian`() {
        val before = handwrittenNote()
        val after = NoteCodec.decodeNote(NoteCodec.encodeNote(before))
        assertThat(after).isEqualTo(before)
    }

    @Test
    fun `punkty kreski nie traca dokladnosci`() {
        val before = handwrittenNote()
        val after = NoteCodec.decodeNote(NoteCodec.encodeNote(before))
        val stroke = after.handwriting!!.pages.first().strokes.first()
        assertThat(stroke.pointCount).isEqualTo(3)
        assertThat(stroke.x(1)).isEqualTo(12.5f)
        assertThat(stroke.y(1)).isEqualTo(22.25f)
        assertThat(stroke.pressure(2)).isEqualTo(0.62f)
        assertThat(stroke.tilt(0)).isEqualTo(0.9f)
    }

    @Test
    fun `notatka tekstowa przezywa zapis i odczyt`() {
        val before = NoteDocument(
            id = "n-2",
            kind = NoteKind.TEXT,
            title = "Lista zadań",
            createdAt = 1L,
            updatedAt = 2L,
            text = TextContent(
                markdown = "# Zadania\n\n- [ ] Przeczytać rozdział 3\n- [x] Zrobić zadanie 12\n\n\$\$a^2 + b^2 = c^2\$\$\n",
            ),
        )
        val after = NoteCodec.decodeNote(NoteCodec.encodeNote(before))
        assertThat(after).isEqualTo(before)
        assertThat(after.text!!.markdown).contains("- [x] Zrobić zadanie 12")
    }

    @Test
    fun `mapa mysli przezywa zapis i odczyt`() {
        val before = NoteDocument(
            id = "n-3",
            kind = NoteKind.MINDMAP,
            title = "Powtórka z fizyki",
            createdAt = 1L,
            updatedAt = 2L,
            mindMap = MindMapContent(
                nodes = listOf(
                    MindNode(id = "w-1", x = 0f, y = 0f, text = "Ruch"),
                    MindNode(id = "w-2", x = 200f, y = -60f, text = "Prędkość", collapsed = true),
                ),
                edges = listOf(MindEdge(id = "e-1", fromId = "w-1", toId = "w-2")),
                zoom = 1.25f,
            ),
        )
        val after = NoteCodec.decodeNote(NoteCodec.encodeNote(before))
        assertThat(after).isEqualTo(before)
    }

    @Test
    fun `nieznane pola z nowszej wersji nie psuja odczytu`() {
        val text = """
            {"format":1,"id":"n-9","kind":"text","title":"Test","createdAt":1,"updatedAt":2,
             "cosNowego":{"a":1},"text":{"markdown":"tresc","drawings":[]}}
        """.trimIndent()
        val after = NoteCodec.decodeNote(text)
        assertThat(after.title).isEqualTo("Test")
        assertThat(after.text!!.markdown).isEqualTo("tresc")
    }

    @Test
    fun `notatka z nowszej wersji formatu daje zrozumialy komunikat`() {
        val text = """{"format":99,"id":"n-9","kind":"text","title":"Test","createdAt":1,"updatedAt":2}"""
        val error = assertThrows(FormatException::class.java) { NoteCodec.decodeNote(text) }
        assertThat(error.userMessage).contains("nowszej wersji")
        assertThat(error.userMessage).contains("Zaktualizuj")
    }

    @Test
    fun `uszkodzony plik daje zrozumialy komunikat`() {
        val error = assertThrows(FormatException::class.java) {
            NoteCodec.decodeNote("{to nie jest json")
        }
        assertThat(error.userMessage).contains("uszkodzony")
    }

    @Test
    fun `pusty plik daje zrozumialy komunikat`() {
        val error = assertThrows(FormatException::class.java) { NoteCodec.decodeNote("   ") }
        assertThat(error.userMessage).contains("pusty")
    }

    @Test
    fun `nazwy pol w pliku sa stale i czytelne`() {
        // Format ma byc czytelny dla czlowieka, gdyby kiedys trzeba bylo ratowac notatke recznie.
        val text = NoteCodec.encodeNote(handwrittenNote())
        assertThat(text).contains("\"kind\":\"handwritten\"")
        assertThat(text).contains("\"pageMode\":\"a4\"")
        assertThat(text).contains("\"background\":\"grid\"")
        assertThat(text).contains("\"tool\":\"pen\"")
        assertThat(text).contains("\"input\":\"stylus\"")
    }

    @Test
    fun `przesuniecie kreski zmienia tylko wspolrzedne`() {
        val stroke = sampleStroke("k-1")
        val shifted = stroke.translated(5f, -3f)
        assertThat(shifted.x(0)).isEqualTo(15f)
        assertThat(shifted.y(0)).isEqualTo(17f)
        assertThat(shifted.timeMs(0)).isEqualTo(stroke.timeMs(0))
        assertThat(shifted.pressure(0)).isEqualTo(stroke.pressure(0))
        assertThat(shifted.pointCount).isEqualTo(stroke.pointCount)
    }

    @Test
    fun `prostokat otaczajacy kreske obejmuje wszystkie punkty`() {
        val bounds = sampleStroke("k-1").bounds()
        assertThat(bounds.left).isEqualTo(10f)
        assertThat(bounds.top).isEqualTo(20f)
        assertThat(bounds.right).isEqualTo(15f)
        assertThat(bounds.bottom).isEqualTo(25f)
    }
}
