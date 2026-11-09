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

    private val tall = 1700f

    @Test
    fun `punkt na kartce nie jest biurkiem`() {
        assertThat(DeskBounds.isDesk(300f, 400f, page, tall)).isFalse()
    }

    @Test
    fun `szczelina miedzy stronami nie jest biurkiem`() {
        // Punkt w odstępie między stronami, ale w szerokości kartki.
        assertThat(DeskBounds.isDesk(300f, 850f, page, tall)).isFalse()
    }

    @Test
    fun `obok kartki nad nia i pod nia jest biurko`() {
        assertThat(DeskBounds.isDesk(-1f, 400f, page, tall)).isTrue()
        assertThat(DeskBounds.isDesk(page + 1f, 400f, page, tall)).isTrue()
        assertThat(DeskBounds.isDesk(300f, -1f, page, tall)).isTrue()
        assertThat(DeskBounds.isDesk(300f, tall + 1f, page, tall)).isTrue()
    }

    @Test
    fun `kartka widoczna w ekranie da sie chwycic`() {
        assertThat(DeskBounds.sheetReachable(-100f, 0f, 800f, 500f, page, tall, 20f)).isTrue()
    }

    @Test
    fun `kartka zepchnieta z ekranu nie da sie chwycic`() {
        assertThat(DeskBounds.sheetReachable(page + 50f, 0f, 800f, 500f, page, tall, 20f)).isFalse()
        assertThat(DeskBounds.sheetReachable(-900f, 0f, 800f, 500f, page, tall, 20f)).isFalse()
    }

    @Test
    fun `sam skrawek kartki przy brzegu nie wystarcza`() {
        // Widać 5 jednostek kartki przy zapasie 20.
        assertThat(DeskBounds.sheetReachable(page - 5f, 0f, 800f, 500f, page, tall, 20f)).isFalse()
    }

    private fun point(x: Float, y: Float) = floatArrayOf(x, y)

    private fun pinch(a: FloatArray, b: FloatArray) =
        DeskBounds.isPinch(point(100f, 500f), point(300f, 500f), a, b, slop = 16f, reach = 96f)

    @Test
    fun `palce rozjezdzajace sie w przeciwne strony to szczypanie`() {
        assertThat(pinch(point(40f, 500f), point(360f, 500f))).isTrue()
    }

    @Test
    fun `dlon zsuwajaca sie obiema czesciami naraz nie jest szczypaniem`() {
        assertThat(pinch(point(100f, 800f), point(300f, 800f))).isFalse()
    }

    @Test
    fun `palec jadacy obok opartej dloni nie jest szczypaniem`() {
        assertThat(pinch(point(100f, 500f), point(900f, 500f))).isFalse()
    }

    @Test
    fun `male drgania obu czesci dloni nie sa szczypaniem`() {
        assertThat(pinch(point(80f, 500f), point(320f, 500f))).isFalse()
    }
}
