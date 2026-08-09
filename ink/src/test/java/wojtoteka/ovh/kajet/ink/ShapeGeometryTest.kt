package wojtoteka.ovh.kajet.ink

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.core.model.ShapeElement
import wojtoteka.ovh.kajet.core.model.ShapeKind
import kotlin.math.abs
import kotlin.math.hypot

class ShapeGeometryTest {

    private fun shape(
        kind: ShapeKind = ShapeKind.RECTANGLE,
        x: Float = 100f,
        y: Float = 100f,
        width: Float = 200f,
        height: Float = 100f,
        rotation: Float = 0f,
        fill: Int = 0,
    ) = ShapeElement(
        id = "k",
        kind = kind,
        x = x,
        y = y,
        width = width,
        height = height,
        rotation = rotation,
        color = -0x1000000,
        strokeWidth = 2f,
        fill = fill,
    )

    @Test
    fun `przeciagniecie od punktu do punktu daje dodatnie boki`() {
        // Ciągnięcie w lewo do góry — prostokąt i tak ma boki dodatnie.
        val drawn = ShapeGeometry.fitTo(shape(), 300f, 300f, 100f, 150f, square = false)

        assertThat(drawn.x).isEqualTo(100f)
        assertThat(drawn.y).isEqualTo(150f)
        assertThat(drawn.width).isEqualTo(200f)
        assertThat(drawn.height).isEqualTo(150f)
    }

    @Test
    fun `blokada proporcji robi z prostokata kwadrat`() {
        val drawn = ShapeGeometry.fitTo(shape(), 100f, 100f, 300f, 160f, square = true)

        assertThat(drawn.width).isEqualTo(200f)
        assertThat(drawn.height).isEqualTo(200f)
    }

    @Test
    fun `linia zachowuje kierunek, bo grot strzalki siedzi na drugim koncu`() {
        val drawn = ShapeGeometry.fitTo(
            shape = shape(kind = ShapeKind.ARROW),
            startX = 300f,
            startY = 300f,
            endX = 100f,
            endY = 200f,
            square = false,
        )

        assertThat(drawn.x).isEqualTo(300f)
        assertThat(drawn.y).isEqualTo(300f)
        assertThat(drawn.width).isEqualTo(-200f)
        assertThat(drawn.height).isEqualTo(-100f)
        // Prostokąt obejmujący zostaje mimo to dodatni.
        assertThat(drawn.box().width).isEqualTo(200f)
    }

    @Test
    fun `blokada proporcji dosnapowuje linie do 45 stopni`() {
        val drawn = ShapeGeometry.fitTo(
            shape = shape(kind = ShapeKind.LINE),
            startX = 0f,
            startY = 0f,
            endX = 100f,
            endY = 10f,
            square = true,
        )

        // Najbliższy kąt to zero stopni: linia kładzie się w poziomie.
        assertThat(abs(drawn.height)).isLessThan(0.01f)
        assertThat(drawn.width).isWithin(0.01f).of(hypot(100f, 10f))
    }

    @Test
    fun `pusty prostokat lapie obrys, a nie srodek`() {
        val empty = shape()

        assertThat(ShapeGeometry.hits(empty, 100f, 100f, 4f)).isTrue()
        assertThat(ShapeGeometry.hits(empty, 200f, 150f, 4f)).isFalse()
    }

    @Test
    fun `wypelniony prostokat lapie calym polem`() {
        val filled = shape(fill = -0x10000)

        assertThat(ShapeGeometry.hits(filled, 200f, 150f, 4f)).isTrue()
        assertThat(ShapeGeometry.hits(filled, 400f, 150f, 4f)).isFalse()
    }

    @Test
    fun `obrocony ksztalt lapie tam, gdzie naprawde lezy`() {
        // Prostokąt 200 na 100 obrócony o ćwiartkę: staje się 100 na 200.
        val turned = shape(rotation = 90f, fill = -0x10000)

        assertThat(ShapeGeometry.hits(turned, 200f, 60f, 2f)).isTrue()
        assertThat(ShapeGeometry.hits(turned, 120f, 150f, 2f)).isFalse()
    }

    @Test
    fun `uchwyt przeciwlegly zostaje w miejscu`() {
        val start = shape()
        val corners = ShapeGeometry.handlePoints(start)
        // Ciągniemy lewy górny róg; prawy dolny ma zostać tam, gdzie był.
        val anchorX = corners[4]
        val anchorY = corners[5]

        val stretched = ShapeGeometry.dragHandle(start, 0, 60f, 40f, square = false)
        val after = ShapeGeometry.handlePoints(stretched)

        assertThat(after[4]).isWithin(0.01f).of(anchorX)
        assertThat(after[5]).isWithin(0.01f).of(anchorY)
        assertThat(stretched.width).isWithin(0.01f).of(240f)
        assertThat(stretched.height).isWithin(0.01f).of(160f)
    }

    @Test
    fun `uchwyt przeciwlegly zostaje w miejscu takze po obrocie`() {
        val start = shape(rotation = 30f)
        val corners = ShapeGeometry.handlePoints(start)
        val anchorX = corners[4]
        val anchorY = corners[5]

        val stretched = ShapeGeometry.dragHandle(start, 0, 40f, 20f, square = false)
        val after = ShapeGeometry.handlePoints(stretched)

        assertThat(after[4]).isWithin(0.05f).of(anchorX)
        assertThat(after[5]).isWithin(0.05f).of(anchorY)
        assertThat(stretched.rotation).isEqualTo(30f)
    }

    @Test
    fun `obrot dosnapowuje sie do cwiartki`() {
        val start = shape()
        // Punkt prawie na wprost w prawo od środka: 90 stopni z małym odchyleniem.
        val turned = ShapeGeometry.rotatedTo(start, start.centerX + 200f, start.centerY + 4f)

        assertThat(turned.rotation).isEqualTo(90f)
    }

    @Test
    fun `stukniecie zamiast przeciagniecia nie jest ksztaltem`() {
        val tap = ShapeGeometry.fitTo(shape(), 100f, 100f, 102f, 101f, square = false)

        assertThat(ShapeGeometry.bigEnough(tap)).isFalse()
    }

    @Test
    fun `przezroczystosc wchodzi w kanal alfa`() {
        val half = ShapeGeometry.withOpacity(-0x1000000, 0.5f)

        assertThat(half ushr 24).isEqualTo(128)
        assertThat(half and 0xFFFFFF).isEqualTo(0)
    }
}
