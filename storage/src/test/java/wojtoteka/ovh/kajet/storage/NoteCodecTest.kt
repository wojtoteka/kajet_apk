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

/**
 * Format notatki to miejsce, w którym błąd oznacza utratę pracy użytkownika.
 * Te testy sprawdzają, czy zapis i odczyt dają dokładnie to samo.
 */
class NoteCodecTest {

    private fun przykladowaKreska(id: String) = InkStroke(
        id = id,
        tool = InkTool.PIORO,
        color = 0xFF23211D.toInt(),
        size = 2.4f,
        input = InputKind.RYSIK,
        points = listOf(
            10f, 20f, 0f, 0.35f, 0.9f, 1.2f,
            12.5f, 22.25f, 8f, 0.51f, 0.88f, 1.19f,
            15f, 25f, 16f, 0.62f, 0.85f, 1.18f,
        ),
    )

    private fun notatkaOdreczna() = NoteDocument(
        id = "n-1",
        kind = NoteKind.ODRECZNA,
        title = "Całki oznaczone",
        createdAt = 1_700_000_000_000,
        updatedAt = 1_700_000_100_000,
        tags = listOf("matematyka", "kolokwium"),
        favorite = true,
        handwriting = HandwritingContent(
            pageMode = PageMode.A4,
            background = PageBackground.KRATKA,
            pages = listOf(
                NotePage(
                    id = "s-1",
                    strokes = listOf(przykladowaKreska("k-1"), przykladowaKreska("k-2")),
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
        val przed = notatkaOdreczna()
        val po = NoteCodec.czytajNotatke(NoteCodec.zapiszNotatke(przed))
        assertThat(po).isEqualTo(przed)
    }

    @Test
    fun `punkty kreski nie traca dokladnosci`() {
        val przed = notatkaOdreczna()
        val po = NoteCodec.czytajNotatke(NoteCodec.zapiszNotatke(przed))
        val kreska = po.handwriting!!.pages.first().strokes.first()
        assertThat(kreska.pointCount).isEqualTo(3)
        assertThat(kreska.x(1)).isEqualTo(12.5f)
        assertThat(kreska.y(1)).isEqualTo(22.25f)
        assertThat(kreska.pressure(2)).isEqualTo(0.62f)
        assertThat(kreska.tilt(0)).isEqualTo(0.9f)
    }

    @Test
    fun `notatka tekstowa przezywa zapis i odczyt`() {
        val przed = NoteDocument(
            id = "n-2",
            kind = NoteKind.TEKSTOWA,
            title = "Lista zadań",
            createdAt = 1L,
            updatedAt = 2L,
            text = TextContent(
                markdown = "# Zadania\n\n- [ ] Przeczytać rozdział 3\n- [x] Zrobić zadanie 12\n\n\$\$a^2 + b^2 = c^2\$\$\n",
            ),
        )
        val po = NoteCodec.czytajNotatke(NoteCodec.zapiszNotatke(przed))
        assertThat(po).isEqualTo(przed)
        assertThat(po.text!!.markdown).contains("- [x] Zrobić zadanie 12")
    }

    @Test
    fun `mapa mysli przezywa zapis i odczyt`() {
        val przed = NoteDocument(
            id = "n-3",
            kind = NoteKind.MAPA,
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
        val po = NoteCodec.czytajNotatke(NoteCodec.zapiszNotatke(przed))
        assertThat(po).isEqualTo(przed)
    }

    @Test
    fun `nieznane pola z nowszej wersji nie psuja odczytu`() {
        val tekst = """
            {"format":1,"id":"n-9","kind":"tekstowa","title":"Test","createdAt":1,"updatedAt":2,
             "cosNowego":{"a":1},"text":{"markdown":"tresc","drawings":[]}}
        """.trimIndent()
        val po = NoteCodec.czytajNotatke(tekst)
        assertThat(po.title).isEqualTo("Test")
        assertThat(po.text!!.markdown).isEqualTo("tresc")
    }

    @Test
    fun `notatka z nowszej wersji formatu daje zrozumialy komunikat`() {
        val tekst = """{"format":99,"id":"n-9","kind":"tekstowa","title":"Test","createdAt":1,"updatedAt":2}"""
        val blad = assertThrows(BladFormatuException::class.java) { NoteCodec.czytajNotatke(tekst) }
        assertThat(blad.komunikatDlaUzytkownika).contains("nowszej wersji")
        assertThat(blad.komunikatDlaUzytkownika).contains("Zaktualizuj")
    }

    @Test
    fun `uszkodzony plik daje zrozumialy komunikat`() {
        val blad = assertThrows(BladFormatuException::class.java) {
            NoteCodec.czytajNotatke("{to nie jest json")
        }
        assertThat(blad.komunikatDlaUzytkownika).contains("uszkodzony")
    }

    @Test
    fun `pusty plik daje zrozumialy komunikat`() {
        val blad = assertThrows(BladFormatuException::class.java) { NoteCodec.czytajNotatke("   ") }
        assertThat(blad.komunikatDlaUzytkownika).contains("pusty")
    }

    @Test
    fun `nazwy pol w pliku sa stale i czytelne`() {
        // Format ma byc czytelny dla czlowieka, gdyby kiedys trzeba bylo ratowac notatke recznie.
        val tekst = NoteCodec.zapiszNotatke(notatkaOdreczna())
        assertThat(tekst).contains("\"kind\":\"odreczna\"")
        assertThat(tekst).contains("\"pageMode\":\"a4\"")
        assertThat(tekst).contains("\"background\":\"kratka\"")
        assertThat(tekst).contains("\"tool\":\"pioro\"")
        assertThat(tekst).contains("\"input\":\"rysik\"")
    }

    @Test
    fun `przesuniecie kreski zmienia tylko wspolrzedne`() {
        val kreska = przykladowaKreska("k-1")
        val przesunieta = kreska.translated(5f, -3f)
        assertThat(przesunieta.x(0)).isEqualTo(15f)
        assertThat(przesunieta.y(0)).isEqualTo(17f)
        assertThat(przesunieta.timeMs(0)).isEqualTo(kreska.timeMs(0))
        assertThat(przesunieta.pressure(0)).isEqualTo(kreska.pressure(0))
        assertThat(przesunieta.pointCount).isEqualTo(kreska.pointCount)
    }

    @Test
    fun `prostokat otaczajacy kreske obejmuje wszystkie punkty`() {
        val granice = przykladowaKreska("k-1").bounds()
        assertThat(granice.left).isEqualTo(10f)
        assertThat(granice.top).isEqualTo(20f)
        assertThat(granice.right).isEqualTo(15f)
        assertThat(granice.bottom).isEqualTo(25f)
    }
}
