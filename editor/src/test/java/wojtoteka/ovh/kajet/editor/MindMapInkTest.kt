package wojtoteka.ovh.kajet.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.MindNode
import wojtoteka.ovh.kajet.editor.mindmap.MindMapInk

class MindMapInkTest {

    /** Kreska z punktów (x, y). Czas, nacisk, pochylenie i obrót są zerowe. */
    private fun stroke(vararg points: Pair<Float, Float>): InkStroke = InkStroke(
        id = "k",
        color = 0,
        size = 2f,
        points = points.flatMap { (x, y) -> listOf(x, y, 0f, 0f, 0f, 0f) },
    )

    private fun node() = MindNode(id = "w", x = 300f, y = 200f)

    private fun bounds(node: MindNode): List<Float> {
        val xs = node.ink.flatMap { s -> (0 until s.pointCount).map { s.x(it) } }
        val ys = node.ink.flatMap { s -> (0 until s.pointCount).map { s.y(it) } }
        return listOf(xs.min(), ys.min(), xs.max(), ys.max())
    }

    @Test
    fun `hasło pisane na środku kartki siada w ramce węzła`() {
        // Tak leżą kreski w oknie rysunku: daleko od jego lewego górnego rogu.
        val fitted = MindMapInk.fitted(
            node(),
            listOf(stroke(220f to 130f, 480f to 130f, 480f to 190f)),
        )

        val (left, top, right, bottom) = bounds(fitted)
        assertThat(left).isAtLeast(0f)
        assertThat(top).isAtLeast(0f)
        assertThat(right).isAtMost(fitted.width)
        assertThat(bottom).isAtMost(fitted.height)
    }

    @Test
    fun `pismo leży pośrodku węzła`() {
        val fitted = MindMapInk.fitted(
            node(),
            listOf(stroke(220f to 130f, 480f to 130f, 480f to 190f)),
        )

        val (left, top, right, bottom) = bounds(fitted)
        assertThat(left).isWithin(0.01f).of(fitted.width - right)
        assertThat(top).isWithin(0.01f).of(fitted.height - bottom)
    }

    @Test
    fun `wysokie pismo podnosi węzeł, zamiast kurczyć się do paska`() {
        val fitted = MindMapInk.fitted(
            node(),
            listOf(stroke(0f to 0f, 140f to 400f)),
        )

        assertThat(fitted.height).isGreaterThan(node().height)
        assertThat(bounds(fitted)[3]).isAtMost(fitted.height)
    }

    @Test
    fun `drobny podpis zostaje w swojej wielkości`() {
        val fitted = MindMapInk.fitted(node(), listOf(stroke(10f to 10f, 50f to 30f)))

        val (left, top, right, bottom) = bounds(fitted)
        assertThat(right - left).isWithin(0.01f).of(40f)
        assertThat(bottom - top).isWithin(0.01f).of(20f)
        assertThat(fitted.height).isEqualTo(node().height)
    }

    @Test
    fun `węzeł raz podniesiony już nie opada`() {
        val tall = node().copy(height = 200f)
        val fitted = MindMapInk.fitted(tall, listOf(stroke(10f to 10f, 50f to 30f)))

        assertThat(fitted.height).isEqualTo(200f)
    }

    @Test
    fun `puste kreski kasują podpis`() {
        val withInk = MindMapInk.fitted(node(), listOf(stroke(10f to 10f, 50f to 30f)))

        assertThat(MindMapInk.fitted(withInk, emptyList()).ink).isEmpty()
    }
}
