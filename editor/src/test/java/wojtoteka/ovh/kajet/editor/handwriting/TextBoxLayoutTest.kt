package wojtoteka.ovh.kajet.editor.handwriting

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextBoxLayoutTest {

    @Test
    fun `wysokosc wiersza maleje razem z przyblizeniem`() {
        val font = 14f
        fun linePx(zoom: Float) = TextBoxLayout.linePx(font, zoom)

        assertThat(TextBoxLayout.LINE_RATIO).isEqualTo(1.3f)
        assertThat(linePx(1f)).isEqualTo(font * 1.3f)
        assertThat(linePx(0.77f)).isWithin(0.001f).of(linePx(1f) * 0.77f)
        // Stale 24.sp z kroju body nie maleje z zoomem — przy 77% byloby
        // wyzsze niz sam glif i scinalo litery od gory, tak jak wezly mapy.
        assertThat(linePx(0.77f)).isLessThan(24f)
        assertThat(linePx(0.25f)).isLessThan(linePx(0.77f))
        // Pole CODE dzieli ten sam kroj ukladu: maszynowy font nie dostaje
        // osobnej, sztywnej ramki 22.sp z kroju code.
        assertThat(TextBoxLayout.linePx(14f, 0.5f)).isEqualTo(14f * 0.5f * 1.3f)
    }

    @Test
    fun `wysciolka maleje razem z ramka`() {
        val density = 2.75f
        fun pad(zoom: Float) = TextBoxLayout.padPx(density, zoom)

        assertThat(pad(1f)).isEqualTo(4f * density)
        assertThat(pad(0.77f)).isWithin(0.001f).of(pad(1f) * 0.77f)
        assertThat(pad(0.25f)).isLessThan(pad(1f))
    }

    @Test
    fun `przy oddaleniu wysciolka nie zjada calego pola`() {
        val density = 3f
        val zoom = 0.25f
        val boxHeight = 44f
        val screenHeight = boxHeight * zoom
        val pad = TextBoxLayout.padPx(density, zoom)

        // Dawne stale 4.dp przy gestosci 3 to 12 px z kazdej strony — wiecej
        // niz 11 px ramki przy 25%. Skalowana wyściółka zostawia miejsce na glif.
        assertThat(4f * density).isGreaterThan(screenHeight)
        assertThat(pad * 2f).isLessThan(screenHeight)
        assertThat(TextBoxLayout.linePx(14f, zoom)).isLessThan(screenHeight)
    }

    @Test
    fun `ramka na ekranie trzyma sie wspolrzednych kartki przy kazdym zoomie`() {
        val boxX = 80f
        val boxY = 40f
        val boxW = 220f
        val boxH = 44f
        val pageTop = 100f
        val offsetX = 10f
        val offsetY = 20f

        for (zoom in listOf(0.25f, 0.77f, 1f, 1.5f, 2f)) {
            val screen = TextBoxLayout.screenRect(
                x = boxX,
                y = boxY,
                width = boxW,
                height = boxH,
                pageTop = pageTop,
                offsetX = offsetX,
                offsetY = offsetY,
                zoom = zoom,
            )
            // Hit-box na kartce (x, y, width, height) i ramka na ekranie
            // to te same krawedzie — tylko przeskalowane. Inaczej obrys,
            // tekst i stukniecie rozjezdzalyby sie przy przyblizaniu.
            assertThat(screen.left / zoom + offsetX).isWithin(0.001f).of(boxX)
            assertThat(screen.top / zoom + offsetY - pageTop).isWithin(0.001f).of(boxY)
            assertThat(screen.width / zoom).isWithin(0.001f).of(boxW)
            assertThat(screen.height / zoom).isWithin(0.001f).of(boxH)
        }
    }
}
