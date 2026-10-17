package wojtoteka.ovh.kajet.storage

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wojtoteka.ovh.kajet.core.model.HandwritingContent
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.model.NotePage
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode
import wojtoteka.ovh.kajet.core.model.ShapeElement
import wojtoteka.ovh.kajet.core.model.ShapeKind

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteThumbnailsTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /** Kreska z punktów co [step] jednostek - w sześciu wartościach na punkt, jak w pliku. */
    private fun stroke(points: Int, step: Float = 0.3f): InkStroke = InkStroke(
        id = "k$points",
        color = 0,
        size = 2f,
        points = (0 until points).flatMap { i -> listOf(10f + i * step, 20f, i.toFloat(), 0.5f, 0f, 0f) },
    )

    private fun note(pages: List<NotePage>) = NoteDocument(
        id = "n",
        kind = NoteKind.HANDWRITTEN,
        title = "Odręczna",
        createdAt = 1L,
        updatedAt = 2L,
        handwriting = HandwritingContent(PageMode.A4, PageBackground.LINED, pages),
    )

    @Test
    fun `a thumbnail keeps the shape of the first page and drops what the strip cannot show`() {
        val first = NotePage(id = "1", strokes = listOf(stroke(500)))
        val second = NotePage(id = "2", strokes = listOf(stroke(10, step = 5f)))

        val thumbnail = PageThumbnail.of(note(listOf(first, second)), updatedAt = 7L)

        val line = thumbnail.lines.single()
        // Pierwszy i ostatni punkt zostają, gęste punkty pomiędzy - nie.
        assertThat(line.take(2)).containsExactly(10, 20).inOrder()
        assertThat(line.takeLast(2)).containsExactly(160, 20).inOrder()
        assertThat(line.size).isLessThan(500 * 2 / 4)
        assertThat(thumbnail.updatedAt).isEqualTo(7L)
    }

    @Test
    fun `only as many strokes as the strip ever showed`() {
        val page = NotePage(id = "1", strokes = List(400) { stroke(3, step = 4f) })

        assertThat(PageThumbnail.of(note(listOf(page)), 1L).lines).hasSize(PageThumbnail.MAX_STROKES)
    }

    @Test
    fun `shapes travel whole and an empty page gives an empty thumbnail`() {
        val shape = ShapeElement(id = "s", kind = ShapeKind.values().first(), x = 1f, y = 2f, width = 30f, height = 40f, color = 0)
        val withShape = PageThumbnail.of(note(listOf(NotePage(id = "1", shapes = listOf(shape)))), 1L)
        val empty = PageThumbnail.of(note(listOf(NotePage(id = "1"))), 1L)

        assertThat(withShape.shapes).containsExactly(shape)
        assertThat(empty.isEmpty).isTrue()
    }

    @Test
    fun `a stored thumbnail survives a fresh start and an older one is not handed out`() {
        val directory = temporaryFolder.newFolder("miniatury")
        val page = NotePage(id = "1", strokes = listOf(stroke(50, step = 3f)))
        NoteThumbnails(directory).put("uri:notatka", PageThumbnail.of(note(listOf(page)), updatedAt = 5L))

        // Nowa pamięć, jak po ponownym uruchomieniu aplikacji - zostaje plik.
        val fresh = NoteThumbnails(directory)

        assertThat(fresh.get("uri:notatka", 5L)?.lines?.single()?.take(2)).containsExactly(10, 20).inOrder()
        assertThat(fresh.get("uri:notatka", 6L)).isNull()
        assertThat(fresh.get("uri:inna", 5L)).isNull()
    }
}
