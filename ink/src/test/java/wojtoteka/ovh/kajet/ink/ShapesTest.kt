package wojtoteka.ovh.kajet.ink

import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Test
import wojtoteka.ovh.kajet.core.model.InkStroke

class ShapesTest {

    /** Kreska z listy punktów (x, y); czas i nacisk rosną wzdłuż drogi. */
    private fun stroke(coordinates: List<Pair<Float, Float>>): InkStroke {
        val points = ArrayList<Float>(coordinates.size * InkStroke.VALUES_PER_POINT)
        coordinates.forEachIndexed { index, (x, y) ->
            points += x
            points += y
            points += index * 8f // timeMs
            points += 0.5f // pressure
            points += 0.4f // tilt
            points += 1f // orientation
        }
        return InkStroke(id = "k", color = 0xFF000000.toInt(), size = 2f, points = points)
    }

    /** Zaszumione koło z małą przerwą między początkiem a końcem. */
    private fun roughCircle(radius: Float = 100f, jitter: Float = 4f): InkStroke {
        val coordinates = (0..60).map { i ->
            val angle = i / 62f * 2f * Math.PI.toFloat() // niedomknięte o ~2 kroki
            val wobble = jitter * sin(i * 1.7f)
            (200f + (radius + wobble) * cos(angle)) to (200f + (radius + wobble) * sin(angle))
        }
        return stroke(coordinates)
    }

    /** Punkty wzdłuż łamanej przez [vertices] (zamkniętej), z drżeniem ręki. */
    private fun roughPolygon(vertices: List<Pair<Float, Float>>, jitter: Float = 2f): InkStroke {
        val path = vertices + vertices.first()
        val coordinates = mutableListOf<Pair<Float, Float>>()
        for (i in 1 until path.size) {
            val (ax, ay) = path[i - 1]
            val (bx, by) = path[i]
            val steps = 12
            val until = if (i == path.size - 1) steps - 1 else steps // mała przerwa na końcu
            for (step in 0..until) {
                val t = step / steps.toFloat()
                val wobble = jitter * sin((coordinates.size).toFloat())
                coordinates += (ax + (bx - ax) * t + wobble) to (ay + (by - ay) * t + wobble)
            }
        }
        return stroke(coordinates)
    }

    private fun checkInvariants(result: InkStroke) {
        assertThat(result.points.size % InkStroke.VALUES_PER_POINT).isEqualTo(0)
        for (i in 1 until result.pointCount) {
            assertThat(result.timeMs(i)).isAtLeast(result.timeMs(i - 1))
        }
    }

    @Test
    fun `zaszumione kolo staje sie rownym kolem`() {
        val result = Shapes.snap(roughCircle())

        checkInvariants(result)
        // Wszystkie punkty w równej odległości od wspólnego środka.
        val cx = (0 until result.pointCount).map { result.x(it) }.average().toFloat()
        val cy = (0 until result.pointCount).map { result.y(it) }.average().toFloat()
        val radii = (0 until result.pointCount).map { hypot(result.x(it) - cx, result.y(it) - cy) }
        val mean = radii.average().toFloat()
        for (radius in radii) {
            assertThat(abs(radius - mean) / mean).isLessThan(0.02f)
        }
        assertThat(mean).isWithin(10f).of(100f)
    }

    @Test
    fun `zaszumiony kwadrat staje sie prostokatem wzdluz osi`() {
        val result = Shapes.snap(
            roughPolygon(listOf(0f to 0f, 200f to 2f, 202f to 198f, 2f to 200f)),
        )

        checkInvariants(result)
        // Prostokąt przy osi: każdy punkt leży na jednej z czterech prostych.
        val xs = (0 until result.pointCount).map { result.x(it) }
        val ys = (0 until result.pointCount).map { result.y(it) }
        val minX = xs.min()
        val maxX = xs.max()
        val minY = ys.min()
        val maxY = ys.max()
        assertThat(maxX - minX).isWithin(20f).of(200f)
        assertThat(maxY - minY).isWithin(20f).of(200f)
        for (i in 0 until result.pointCount) {
            val onEdge = minOf(
                abs(xs[i] - minX),
                abs(xs[i] - maxX),
                abs(ys[i] - minY),
                abs(ys[i] - maxY),
            )
            assertThat(onEdge).isLessThan(1f)
        }
    }

    @Test
    fun `zaszumiony trojkat staje sie trojkatem o prostych bokach`() {
        val vertices = listOf(100f to 0f, 200f to 180f, 0f to 180f)
        val result = Shapes.snap(roughPolygon(vertices))

        checkInvariants(result)
        // Każdy punkt leży blisko któregoś z trzech boków.
        for (i in 0 until result.pointCount) {
            val distances = (0 until 3).map { v ->
                val (ax, ay) = vertices[v]
                val (bx, by) = vertices[(v + 1) % 3]
                Strokes.distanceToSegment(result.x(i), result.y(i), ax, ay, bx, by)
            }
            assertThat(distances.min()).isLessThan(8f)
        }
    }

    @Test
    fun `otwarta falista kreska prostuje sie jak dotad`() {
        val open = stroke(
            listOf(0f to 0f, 20f to 30f, 40f to 5f, 60f to 20f, 80f to 8f, 100f to 12f, 120f to 3f, 140f to 2f),
        )
        val result = Shapes.snap(open)

        checkInvariants(result)
        assertThat(result.points).isEqualTo(Strokes.straighten(open).points)
    }

    @Test
    fun `ksztalt litery C jest otwarty, nie zamkniety`() {
        // Pół okręgu: duża przerwa między końcami.
        val coordinates = (0..30).map { i ->
            val angle = Math.PI.toFloat() * i / 30f
            (200f + 100f * cos(angle)) to (200f + 100f * sin(angle))
        }
        val c = stroke(coordinates)
        val result = Shapes.snap(c)

        assertThat(result.points).isEqualTo(Strokes.straighten(c).points)
    }

    @Test
    fun `zamknieta gwiazdka zostaje odreczna`() {
        // Pięcioramienna gwiazda: zamknięta, ale to nie koło ani wielokąt 3-4 rogi.
        val coordinates = (0..40).map { i ->
            val angle = i / 40f * 2f * Math.PI.toFloat()
            val radius = if (i % 4 < 2) 100f else 45f
            (200f + radius * cos(angle)) to (200f + radius * sin(angle))
        }
        val star = stroke(coordinates)
        val result = Shapes.snap(star)

        assertThat(result).isSameInstanceAs(star)
    }

    @Test
    fun `krotka kreska idzie do prostowania, nie do ksztaltow`() {
        val tiny = stroke(listOf(0f to 0f, 3f to 1f, 5f to 0f))
        val result = Shapes.snap(tiny)
        assertThat(result.points).isEqualTo(Strokes.straighten(tiny).points)
    }

    @Test
    fun `nacisk na koncach jest ten sam co w kresce reki`() {
        val result = Shapes.snap(roughCircle())
        assertThat(result.pressure(0)).isWithin(0.001f).of(0.5f)
        assertThat(result.pressure(result.pointCount - 1)).isWithin(0.001f).of(0.5f)
    }
}
