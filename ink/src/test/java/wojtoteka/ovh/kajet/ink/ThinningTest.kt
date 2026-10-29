package wojtoteka.ovh.kajet.ink

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.core.model.InkStroke
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class ThinningTest {

    /** Pozioma kreska z punktami co [step], tak gesto, jak daje je rysik. */
    private fun line(
        step: Float = 0.5f,
        length: Float = 100f,
        pressure: (Float) -> Float = { 0.5f },
        tilt: Float = 0.4f,
        orientation: Float = 1f,
    ) = InkStroke(
        id = "k",
        color = 0xFF000000.toInt(),
        size = 2f,
        points = buildList {
            var x = 0f
            var time = 0f
            while (x <= length) {
                add(x); add(50f); add(time); add(pressure(x)); add(tilt); add(orientation)
                x += step
                time += 4f
            }
        },
    )

    /** Fala pisana reka: 240 probek na sekunde, jak z rysika. */
    private fun wave(): InkStroke = InkStroke(
        id = "k",
        color = 0xFF000000.toInt(),
        size = 2f,
        points = buildList {
            for (i in 0 until 400) {
                val t = i / 240f
                add(100f + 240f * t + 3f * cos(t * 30f))
                add(300f + 5f * sin(t * 30f))
                add(Math.round(t * 1000f).toFloat())
                add(0.5f); add(0.4f); add(1f)
            }
        },
    )

    private fun InkStroke.gaps(): List<Float> =
        (1 until pointCount).map { hypot(x(it) - x(it - 1), y(it) - y(it - 1)) }

    @Test
    fun `prosta kreska traci wiekszosc punktow`() {
        val dense = line()
        val thin = Strokes.thinned(dense)

        assertThat(dense.pointCount).isEqualTo(201)
        assertThat(thin.pointCount).isLessThan(dense.pointCount / 2)
    }

    @Test
    fun `poczatek i koniec zostaja na swoim miejscu i w swoim czasie`() {
        val dense = line()
        val thin = Strokes.thinned(dense)

        assertThat(thin.x(0)).isEqualTo(0f)
        assertThat(thin.timeMs(0)).isEqualTo(0f)
        assertThat(thin.x(thin.pointCount - 1)).isEqualTo(100f)
        assertThat(thin.timeMs(thin.pointCount - 1)).isEqualTo(dense.timeMs(dense.pointCount - 1))
    }

    @Test
    fun `miedzy punktami nie ma dziury, ktorej gumka by nie przetarla`() {
        val thin = Strokes.thinned(line())
        thin.gaps().forEach { assertThat(it).isAtMost(Strokes.MAX_GAP) }
    }

    @Test
    fun `krzywa zostaje tam, gdzie byla`() {
        val dense = wave()
        val thin = Strokes.thinned(dense)

        assertThat(thin.pointCount).isLessThan(dense.pointCount)
        // Kazdy punkt z rysika lezy przy kresce po przerzedzeniu blizej niz pol epsilona.
        for (i in 0 until dense.pointCount) {
            val nearest = (1 until thin.pointCount).minOf { k ->
                Strokes.distanceToSegment(
                    dense.x(i), dense.y(i),
                    thin.x(k - 1), thin.y(k - 1),
                    thin.x(k), thin.y(k),
                )
            }
            assertThat(nearest).isAtMost(dense.epsilon / 2f)
        }
    }

    @Test
    fun `zostaja tylko punkty z rysika, z wlasnym czasem`() {
        val dense = wave()
        val thin = Strokes.thinned(dense)
        val original = (0 until dense.pointCount).map { listOf(dense.x(it), dense.y(it), dense.timeMs(it)) }.toSet()

        for (k in 0 until thin.pointCount) {
            assertThat(original).contains(listOf(thin.x(k), thin.y(k), thin.timeMs(k)))
        }
    }

    @Test
    fun `zmiana nacisku zostaje w kresce`() {
        val pressed = line(length = 20f, pressure = { x -> if (x < 10f) 0.2f else 0.9f })
        val thin = Strokes.thinned(pressed)

        val xs = (0 until thin.pointCount).map { thin.x(it) }
        assertThat(xs).contains(9.5f)
        assertThat(xs).contains(10f)
    }

    @Test
    fun `kreska palcem bez nacisku i pochylenia tez sie przerzedza`() {
        val finger = line(
            length = 10f,
            pressure = { InkStroke.MISSING },
            tilt = InkStroke.MISSING,
            orientation = InkStroke.MISSING,
        )
        assertThat(Strokes.thinned(finger).pointCount).isLessThan(finger.pointCount)
    }

    @Test
    fun `kreska z dwoch punktow zostaje ta sama`() {
        val short = line(step = 5f, length = 5f)
        assertThat(short.pointCount).isEqualTo(2)
        assertThat(Strokes.thinned(short)).isSameInstanceAs(short)
    }
}
