package wojtoteka.ovh.kajet.editor.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.core.model.InkStroke

class DrawingFrameTest {

    private fun stroke(vararg xy: Float, size: Float = 2f): InkStroke {
        val points = ArrayList<Float>()
        for (i in xy.indices step 2) points += listOf(xy[i], xy[i + 1], 0f, 1f, 0f, 0f)
        return InkStroke(id = "k", color = 0xFF000000.toInt(), size = size, points = points)
    }

    @Test
    fun `maly rysunek po lewej to nadal cala kartka`() {
        val frame = DrawingFrame.of(listOf(stroke(20f, 20f, 60f, 50f)), pageWidth = 560f, pageHeight = 300f)

        assertThat(frame.width).isEqualTo(560f)
        assertThat(frame.height).isEqualTo(300f)
        // Kreski zostają tam, gdzie je narysowano.
        assertThat(frame.strokes.single().x(0)).isEqualTo(20f)
    }

    @Test
    fun `kreska za lewa i prawa krawedzia kartki nie jest ucinana`() {
        val frame = DrawingFrame.of(
            listOf(stroke(-15f, 40f, 590f, 60f, size = 4f)),
            pageWidth = 560f,
            pageHeight = 300f,
        )

        assertThat(frame.width).isGreaterThan(560f + 15f + 30f)
        // Ramka zaczyna się przed kreską, więc kreska siedzi w środku obrazka.
        val moved = frame.strokes.single()
        assertThat(moved.x(0)).isGreaterThan(0f)
        assertThat(moved.x(1)).isLessThan(frame.width)
    }

    @Test
    fun `kreska nad gorna krawedzia tez sie miesci`() {
        val frame = DrawingFrame.of(listOf(stroke(100f, -20f, 120f, 30f)), pageWidth = 560f, pageHeight = 300f)

        assertThat(frame.strokes.single().y(0)).isGreaterThan(0f)
        assertThat(frame.height).isGreaterThan(300f)
    }

    @Test
    fun `dolozona i pusta kartka na dole jest przycinana do wysokosci startowej`() {
        // Kartka urosła do 540, ale kreska stoi na górze.
        val frame = DrawingFrame.of(listOf(stroke(10f, 10f, 50f, 40f)), pageWidth = 560f, pageHeight = 540f)

        assertThat(frame.height).isEqualTo(300f)
    }

    @Test
    fun `dolozona kartka z kreska na dole zostaje do kreski`() {
        val frame = DrawingFrame.of(listOf(stroke(10f, 10f, 50f, 450f)), pageWidth = 560f, pageHeight = 540f)

        assertThat(frame.height).isEqualTo(490f)
    }
}
