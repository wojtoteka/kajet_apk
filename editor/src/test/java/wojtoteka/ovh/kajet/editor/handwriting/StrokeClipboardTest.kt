package wojtoteka.ovh.kajet.editor.handwriting

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import wojtoteka.ovh.kajet.core.model.HandwritingContent
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.model.NotePage
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode
import wojtoteka.ovh.kajet.editor.ChangeHistory
import wojtoteka.ovh.kajet.editor.CompoundChange
import wojtoteka.ovh.kajet.editor.StrokeChange
import wojtoteka.ovh.kajet.ink.Strokes

class StrokeClipboardTest {

    @After
    fun emptyClipboard() = StrokeClipboard.clear()

    /** Kreska z punktów (x, y); reszta wartości punktu jest bez znaczenia. */
    private fun stroke(id: String, vararg xy: Float, size: Float = 0f): InkStroke {
        val points = ArrayList<Float>()
        for (i in xy.indices step 2) {
            points += listOf(xy[i], xy[i + 1], 0f, 1f, 0f, 0f)
        }
        return InkStroke(id = id, color = 0, size = size, points = points)
    }

    private val a4 = Sheet(NotePage.A4_WIDTH, NotePage.A4_HEIGHT)

    @Test
    fun `schowek trzyma kopie i pusta lista go nie czysci`() {
        StrokeClipboard.put(listOf(stroke("a", 10f, 10f, 20f, 20f)))
        StrokeClipboard.put(emptyList())
        assertThat(StrokeClipboard.strokes.value.map { it.id }).containsExactly("a")

        // Kreska bez punktów nie ma czego wkleić.
        StrokeClipboard.put(listOf(stroke("pusta")))
        assertThat(StrokeClipboard.strokes.value.map { it.id }).containsExactly("a")

        StrokeClipboard.clear()
        assertThat(StrokeClipboard.strokes.value).isEmpty()
    }

    @Test
    fun `wklejone staje srodkiem w podanym punkcie i dostaje nowe id`() {
        val copied = listOf(stroke("a", 10f, 10f, 30f, 10f), stroke("b", 10f, 50f, 30f, 50f))
        var next = 0
        val placed = StrokePlacement.pasted(copied, 300f, 400f, a4) { "nowa-${next++}" }

        assertThat(placed.map { it.id }).containsExactly("nowa-0", "nowa-1").inOrder()
        val box = Strokes.bounds(placed)!!
        assertThat((box.left + box.right) / 2f).isWithin(0.01f).of(300f)
        assertThat((box.top + box.bottom) / 2f).isWithin(0.01f).of(400f)
        // Kształt rysunku zostaje ten sam - przesunięty, nie rozciągnięty.
        assertThat(box.width).isWithin(0.01f).of(20f)
        assertThat(box.height).isWithin(0.01f).of(40f)
        // Oryginał w schowku zostaje nietknięty.
        assertThat(copied[0].x(0)).isEqualTo(10f)
    }

    @Test
    fun `wklejenie przy brzegu zostaje w calosci na kartce`() {
        val copied = listOf(stroke("a", 0f, 0f, 100f, 60f, size = 4f))
        val placed = StrokePlacement.pasted(copied, a4.width, a4.height + 300f, a4)

        val box = Strokes.bounds(placed)!!
        // Grubość kreski też musi się zmieścić: pół kreski od brzegu.
        assertThat(box.right).isWithin(0.01f).of(a4.width - 2f)
        assertThat(box.bottom).isWithin(0.01f).of(a4.height - 2f)
    }

    @Test
    fun `rysunek wiekszy od kartki przylega do jej poczatku`() {
        val copied = listOf(stroke("a", 0f, 0f, 900f, 100f))
        val placed = StrokePlacement.pasted(copied, 50f, 50f, Sheet(560f, 300f))

        assertThat(Strokes.bounds(placed)!!.left).isWithin(0.01f).of(0f)
    }

    @Test
    fun `kolejne wklejenia w to samo miejsce schodza schodkami`() {
        val spots = PasteSpots()
        assertThat(spots.next(0, 200f, 300f)).isEqualTo(0f)
        assertThat(spots.next(0, 200f, 300f)).isEqualTo(StrokePlacement.PASTE_STEP)
        assertThat(spots.next(0, 200f, 300f)).isEqualTo(2 * StrokePlacement.PASTE_STEP)
        // Kartka przesunięta albo inna strona - znowu od środka.
        assertThat(spots.next(0, 200f, 500f)).isEqualTo(0f)
        assertThat(spots.next(1, 200f, 500f)).isEqualTo(0f)
        spots.next(1, 200f, 500f)
        spots.reset()
        assertThat(spots.next(1, 200f, 500f)).isEqualTo(0f)
    }

    @Test
    fun `zaznaczenie na kartce zostaje tam, gdzie je puszczono`() {
        val sheets = listOf(a4, a4)
        val landing = StrokePlacement.landing(listOf(stroke("a", 100f, 100f, 200f, 150f)), 0, sheets, 24f)

        assertThat(landing.page).isEqualTo(0)
        assertThat(landing.stays).isTrue()
    }

    @Test
    fun `zaznaczenie przeciagniete pod kartke przechodzi na nastepna strone`() {
        val sheets = listOf(a4, a4, a4)
        val gap = 24f
        // Środek na wysokości 900 - już pod pierwszą kartką (842 + pół odstępu).
        val below = listOf(stroke("a", 100f, 880f, 200f, 920f))
        val landing = StrokePlacement.landing(below, 0, sheets, gap)

        assertThat(landing.page).isEqualTo(1)
        assertThat(landing.dx).isEqualTo(0f)
        // Ten sam punkt ekranu, tylko w układzie drugiej strony.
        assertThat(landing.dy).isWithin(0.01f).of(-(a4.height + gap))
    }

    @Test
    fun `zaznaczenie przeciagniete nad kartke wraca na poprzednia strone`() {
        val sheets = listOf(a4, a4, a4)
        val gap = 24f
        val above = listOf(stroke("a", 100f, -120f, 200f, -80f))
        val landing = StrokePlacement.landing(above, 2, sheets, gap)

        assertThat(landing.page).isEqualTo(1)
        assertThat(landing.dy).isWithin(0.01f).of(a4.height + gap)
    }

    @Test
    fun `zaznaczenie wyciagniete na biurko wraca na kartke`() {
        val sheets = listOf(a4)
        // Na prawo od kartki i pod ostatnią stroną - dalej nie ma dokąd iść.
        val outside = listOf(stroke("a", 650f, 900f, 700f, 950f))
        val landing = StrokePlacement.landing(outside, 0, sheets, 24f)

        assertThat(landing.page).isEqualTo(0)
        val moved = outside.map { it.translated(landing.dx, landing.dy) }
        val box = Strokes.bounds(moved)!!
        assertThat(box.right).isWithin(0.01f).of(a4.width)
        assertThat(box.bottom).isWithin(0.01f).of(a4.height)
    }

    @Test
    fun `przeprowadzka na inna strone cofa sie jednym krokiem`() {
        val moving = stroke("a", 100f, 100f, 200f, 100f)
        val staying = stroke("b", 300f, 300f, 400f, 300f)
        val document = NoteDocument(
            id = "n",
            kind = NoteKind.HANDWRITTEN,
            title = "",
            createdAt = 0L,
            updatedAt = 0L,
            handwriting = HandwritingContent(
                pageMode = PageMode.A4,
                background = PageBackground.PLAIN,
                pages = listOf(
                    NotePage(id = "1", strokes = listOf(moving, staying)),
                    NotePage(id = "2"),
                ),
            ),
        )
        val arrived = moving.translated(0f, -500f)
        val change = CompoundChange(
            listOf(
                StrokeChange(0, removed = listOf(0 to moving)),
                StrokeChange(1, added = listOf(arrived)),
            ),
        )
        val history = ChangeHistory()

        val after = change.applyTo(document)
        history.record(change)
        assertThat(after.handwriting!!.pages[0].strokes).containsExactly(staying)
        assertThat(after.handwriting!!.pages[1].strokes).containsExactly(arrived)

        val undone = history.undo(after)!!
        assertThat(undone).isEqualTo(document)
        assertThat(history.canUndo).isFalse()

        assertThat(history.redo(undone)).isEqualTo(after)
    }
}
