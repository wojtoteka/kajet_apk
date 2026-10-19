package wojtoteka.ovh.kajet.ink

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeskBoundsTest {

    // Kartka A4 w jednostkach kartki i ekran tabletu w pikselach.
    private val page = 595f
    private val screen = 1600f

    /** Lewa krawędź kartki na ekranie, w pikselach. */
    private fun leftEdge(offsetX: Float, zoom: Float) = -offsetX * zoom

    /** Prawa krawędź kartki na ekranie, w pikselach. */
    private fun rightEdge(offsetX: Float, zoom: Float) = (page - offsetX) * zoom

    @Test
    fun `kartka w srodku zakresu zostaje tam, gdzie ja polozono`() {
        val zoom = 2f
        assertThat(DeskBounds.clampX(100f, page, screen, zoom)).isEqualTo(100f)
        assertThat(DeskBounds.clampX(-200f, page, screen, zoom)).isEqualTo(-200f)
    }

    @Test
    fun `w lewo kartka znika za ekranem jak dotad`() {
        val zoom = 2f
        val x = DeskBounds.clampX(10_000f, page, screen, zoom)
        assertThat(x).isEqualTo(page * 1.5f)
        assertThat(rightEdge(x, zoom)).isLessThan(0f)
    }

    @Test
    fun `w prawo kartka tez znika za ekranem`() {
        val zoom = 2f
        val x = DeskBounds.clampX(-10_000f, page, screen, zoom)
        assertThat(leftEdge(x, zoom)).isGreaterThan(screen)
    }

    @Test
    fun `zapas za lewa i za prawa krawedzia jest taki sam`() {
        for (zoom in listOf(StrokeCanvas.MIN_ZOOM, 0.5f, 1f, screen / page, 3f, StrokeCanvas.MAX_ZOOM)) {
            val left = DeskBounds.clampX(Float.MAX_VALUE, page, screen, zoom)
            val right = DeskBounds.clampX(-Float.MAX_VALUE, page, screen, zoom)
            val pastLeft = -rightEdge(left, zoom)
            val pastRight = leftEdge(right, zoom) - screen
            assertThat(pastRight).isWithin(0.01f).of(pastLeft)
        }
    }

    @Test
    fun `pomniejszona kartka przejezdza przez caly ekran w prawo`() {
        val zoom = StrokeCanvas.MIN_ZOOM
        val x = DeskBounds.clampX(-10_000f, page, screen, zoom)
        assertThat(leftEdge(x, zoom)).isGreaterThan(screen)
    }

    @Test
    fun `bez rozmiaru widoku granica prawa wraca do zapasu kartki`() {
        assertThat(DeskBounds.clampX(-10_000f, page, 0f, 1f)).isEqualTo(-page * DeskBounds.SLACK)
    }
}
