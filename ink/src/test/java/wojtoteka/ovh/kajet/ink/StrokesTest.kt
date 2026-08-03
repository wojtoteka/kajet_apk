package wojtoteka.ovh.kajet.ink

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.core.model.InkStroke

class StrokesTest {

    private fun horizontal(step: Float = 10f, size: Float = 2f) = InkStroke(
        id = "k",
        color = 0xFF000000.toInt(),
        size = size,
        points = buildList {
            var x = 0f
            var time = 0f
            while (x <= 100f) {
                add(x); add(50f); add(time); add(0.5f); add(0.4f); add(1f)
                x += step
                time += 8f
            }
        },
    )

    @Test
    fun `gumka trafia w kreske, po ktorej przejedzie`() {
        assertThat(Strokes.hitsCircle(horizontal(), 50f, 50f, 5f)).isTrue()
    }

    @Test
    fun `gumka nie trafia w kreske obok`() {
        assertThat(Strokes.hitsCircle(horizontal(), 50f, 90f, 5f)).isFalse()
    }

    @Test
    fun `gumka trafia miedzy punktami, a nie tylko w punkty`() {
        // Punkty leza co 40, gumka jest dokladnie miedzy nimi.
        val sparse = horizontal(step = 40f)
        assertThat(Strokes.hitsCircle(sparse, 20f, 50f, 3f)).isTrue()
    }

    @Test
    fun `wytarcie srodka dzieli kreske na dwa kawalki`() {
        val pieces = Strokes.cutFragment(horizontal(), 50f, 50f, 12f)
        assertThat(pieces).hasSize(2)
        assertThat(pieces[0].x(0)).isEqualTo(0f)
        assertThat(pieces[0].bounds().right).isLessThan(50f)
        assertThat(pieces[1].bounds().left).isGreaterThan(50f)
    }

    @Test
    fun `kawalki po wytarciu maja wlasne identyfikatory`() {
        val pieces = Strokes.cutFragment(horizontal(), 50f, 50f, 12f)
        assertThat(pieces[0].id).isNotEqualTo(pieces[1].id)
        assertThat(pieces[0].id).isNotEqualTo("k")
    }

    @Test
    fun `wytarcie poza kreska nie zmienia niczego`() {
        val pieces = Strokes.cutFragment(horizontal(), 300f, 300f, 12f)
        assertThat(pieces).hasSize(1)
        assertThat(pieces[0].id).isEqualTo("k")
    }

    @Test
    fun `wytarcie calej kreski zostawia pusto`() {
        val pieces = Strokes.cutFragment(horizontal(), 50f, 50f, 300f)
        assertThat(pieces).isEmpty()
    }

    @Test
    fun `kawalek krotszy niz dwa punkty jest odrzucany`() {
        // Gumka zjada wszystko poza ostatnim punktem.
        val pieces = Strokes.cutFragment(horizontal(), 40f, 50f, 85f)
        assertThat(pieces.all { it.pointCount >= 2 }).isTrue()
    }

    @Test
    fun `czas w kawalku liczy sie od jego poczatku`() {
        val pieces = Strokes.cutFragment(horizontal(), 50f, 50f, 12f)
        assertThat(pieces[1].timeMs(0)).isEqualTo(0f)
    }

    @Test
    fun `lasso lapie kreske w srodku`() {
        val square = listOf(-10f, 0f, 200f, 0f, 200f, 100f, -10f, 100f)
        assertThat(Strokes.inLasso(horizontal(), square)).isTrue()
    }

    @Test
    fun `lasso nie lapie kreski obok`() {
        val square = listOf(200f, 0f, 300f, 0f, 300f, 100f, 200f, 100f)
        assertThat(Strokes.inLasso(horizontal(), square)).isFalse()
    }

    @Test
    fun `lasso lapie kreske, ktorej wiekszosc wpadla do srodka`() {
        // Kwadrat obejmuje x od -10 do 80, czyli okolo 80 procent kreski.
        val square = listOf(-10f, 0f, 80f, 0f, 80f, 100f, -10f, 100f)
        assertThat(Strokes.inLasso(horizontal(), square)).isTrue()
    }

    @Test
    fun `punkt w wielokacie liczony jest poprawnie dla ksztaltu wkleslego`() {
        // Litera C: wielokat wklesly.
        val c = listOf(
            0f, 0f, 100f, 0f, 100f, 20f, 20f, 20f,
            20f, 80f, 100f, 80f, 100f, 100f, 0f, 100f,
        )
        assertThat(Strokes.pointInPolygon(10f, 50f, c)).isTrue()
        assertThat(Strokes.pointInPolygon(60f, 50f, c)).isFalse()
    }

    @Test
    fun `linijka prostuje krzywa do odcinka`() {
        val curve = InkStroke(
            id = "k",
            color = 0xFF000000.toInt(),
            size = 2f,
            points = listOf(
                0f, 0f, 0f, 0.5f, 0.4f, 1f,
                20f, 30f, 8f, 0.5f, 0.4f, 1f,
                40f, 5f, 16f, 0.5f, 0.4f, 1f,
                60f, 20f, 24f, 0.5f, 0.4f, 1f,
            ),
        )
        val straight = Strokes.straighten(curve)
        // Wszystkie punkty leza na odcinku miedzy pierwszym a ostatnim.
        for (i in 0 until straight.pointCount) {
            val distance = Strokes.distanceToSegment(
                straight.x(i), straight.y(i),
                straight.x(0), straight.y(0),
                straight.x(straight.pointCount - 1), straight.y(straight.pointCount - 1),
            )
            assertThat(distance).isLessThan(0.01f)
        }
    }

    @Test
    fun `linijka dociaga prawie pozioma kreske do rownej`() {
        val almostHorizontal = InkStroke(
            id = "k",
            color = 0xFF000000.toInt(),
            size = 2f,
            points = listOf(
                0f, 0f, 0f, 0.5f, 0.4f, 1f,
                100f, 3f, 16f, 0.5f, 0.4f, 1f,
            ),
        )
        val straight = Strokes.straighten(almostHorizontal)
        val last = straight.pointCount - 1
        assertThat(straight.y(last)).isWithin(0.01f).of(straight.y(0))
    }

    @Test
    fun `linijka nie rusza kreski pod katem trzydziestu stopni`() {
        val diagonal = InkStroke(
            id = "k",
            color = 0xFF000000.toInt(),
            size = 2f,
            points = listOf(
                0f, 0f, 0f, 0.5f, 0.4f, 1f,
                100f, 58f, 16f, 0.5f, 0.4f, 1f,
            ),
        )
        val straight = Strokes.straighten(diagonal)
        val last = straight.pointCount - 1
        assertThat(straight.x(last)).isWithin(0.5f).of(100f)
        assertThat(straight.y(last)).isWithin(0.5f).of(58f)
    }

    @Test
    fun `obszar kilku kresek obejmuje je wszystkie`() {
        val a = horizontal()
        val b = horizontal().translated(0f, 100f)
        val bounds = Strokes.bounds(listOf(a, b))!!
        assertThat(bounds.top).isEqualTo(50f)
        assertThat(bounds.bottom).isEqualTo(150f)
        assertThat(bounds.right).isEqualTo(100f)
    }

    // Repairing saved points into something the engine accepts. One bad point used to
    // wipe the whole stroke off the screen.

    private val halfTurn = (Math.PI / 2).toFloat()
    private val fullTurn = (2 * Math.PI).toFloat()

    private fun strokeOf(vararg values: Float) = InkStroke(
        id = "k",
        color = 0xFF000000.toInt(),
        size = 2f,
        points = values.toList(),
    )

    @Test
    fun `pochylenie wieksze od polowy kata prostego wraca do zakresu`() {
        val stroke = strokeOf(
            0f, 0f, 0f, 0.5f, 1.571f, 1f,
            10f, 10f, 8f, 0.5f, 1.571f, 1f,
        )
        val points = Strokes.pointsForEngine(stroke)
        assertThat(points).hasSize(2)
        points.forEach { assertThat(it.tilt).isAtMost(halfTurn) }
    }

    @Test
    fun `obrot rowny pelnemu kolu schodzi ponizej pelnego kola`() {
        assertThat(Strokes.wrapAngle(fullTurn)).isLessThan(fullTurn)
        assertThat(Strokes.wrapAngle(6.284f)).isLessThan(fullTurn)
        assertThat(Strokes.wrapAngle(-0.5f)).isAtLeast(0f)
        assertThat(Strokes.wrapAngle(1.2f)).isWithin(0.0001f).of(1.2f)
    }

    @Test
    fun `powtorzony punkt dostaje wlasny czas zamiast wypasc`() {
        val stroke = strokeOf(
            5f, 5f, 12f, 0.5f, 0.4f, 1f,
            5f, 5f, 12f, 0.5f, 0.4f, 1f,
        )
        val points = Strokes.pointsForEngine(stroke)
        assertThat(points).hasSize(2)
        assertThat(points[1].timeMs).isGreaterThan(points[0].timeMs)
    }

    @Test
    fun `czas nigdy sie nie cofa`() {
        val stroke = strokeOf(
            0f, 0f, 30f, 0.5f, 0.4f, 1f,
            10f, 10f, 5f, 0.5f, 0.4f, 1f,
        )
        val points = Strokes.pointsForEngine(stroke)
        assertThat(points[1].timeMs).isAtLeast(points[0].timeMs)
    }

    @Test
    fun `nacisk podany tylko czesci punktow znika ze wszystkich`() {
        val stroke = strokeOf(
            0f, 0f, 0f, 0.5f, 0.4f, 1f,
            10f, 10f, 8f, InkStroke.MISSING, 0.4f, 1f,
        )
        val points = Strokes.pointsForEngine(stroke)
        points.forEach { assertThat(it.pressure).isEqualTo(InkStroke.MISSING) }
        // Pochylenie mają oba punkty, więc zostaje.
        points.forEach { assertThat(it.tilt).isWithin(0.001f).of(0.4f) }
    }

    @Test
    fun `kreska z rysika zapisana przez starsza wersje wraca w dozwolonym zakresie`() {
        val fromStylus = InkStroke(
            id = "k",
            color = 0xFF000000.toInt(),
            size = 2f,
            input = wojtoteka.ovh.kajet.core.model.InputKind.STYLUS,
            points = listOf(
                // x, y, czas, nacisk, pochylenie, obrót
                10f, 10f, 0f, 0.42f, 1.571f, 6.284f,
                10f, 10f, 0f, 0.42f, 1.571f, 6.284f,
                10.5f, 11f, 4f, 1.001f, 1.5708f, 0.5f,
                11f, 12f, 8f, 0.5f, 0.9f, 3.14f,
            ),
        )

        val points = Strokes.pointsForEngine(fromStylus)

        assertThat(points).hasSize(4)
        points.forEach { point ->
            assertThat(point.pressure).isIn(com.google.common.collect.Range.closed(0f, 1f))
            assertThat(point.tilt).isIn(com.google.common.collect.Range.closed(0f, halfTurn))
            assertThat(point.orientation).isAtLeast(0f)
            assertThat(point.orientation).isLessThan(fullTurn)
        }
        assertThat(points[1].timeMs).isGreaterThan(points[0].timeMs)
        points.zipWithNext { earlier, later ->
            assertThat(later.timeMs).isAtLeast(earlier.timeMs)
        }
    }

    @Test
    fun `punkt bez wspolrzednych wypada, reszta kreski zostaje`() {
        val stroke = strokeOf(
            0f, 0f, 0f, 0.5f, 0.4f, 1f,
            Float.NaN, 10f, 8f, 0.5f, 0.4f, 1f,
            20f, 20f, 16f, 0.5f, 0.4f, 1f,
        )
        assertThat(Strokes.pointsForEngine(stroke)).hasSize(2)
    }
}
