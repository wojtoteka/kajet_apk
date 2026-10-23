package wojtoteka.ovh.kajet.ink

import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer

/**
 * Jedyne miejsce, z którego bierze się renderer kresek - kartka, obrazki
 * rysunków i PDF rysują tak samo.
 *
 * Od Androida 14 androidx.ink sam wybiera siatkę (`Canvas.drawMesh` z własnym
 * shaderem). Na Androidzie 17 z grafiką na Vulkanie siatka rozjeżdża trójkąty:
 * pętle zalewają się klinami, na zakrętach wyrastają trójkąty, kółka wyglądają
 * na rozmazane. Zapisane punkty są przy tym całe, psuje się tylko obraz -
 * ta sama notatka na stronie w przeglądarce wygląda dobrze.
 *
 * Ścieżka rysuje obrys kreski zwykłym `drawPath`, który działa jednakowo na
 * każdym systemie. Obrys jest liczony raz na kreskę i trzymany przez bibliotekę,
 * więc przesuwanie kartki nie przelicza go od nowa.
 */
object InkRenderer {

    fun create(): CanvasStrokeRenderer = CanvasStrokeRenderer.create(forcePathRendering = true)
}
