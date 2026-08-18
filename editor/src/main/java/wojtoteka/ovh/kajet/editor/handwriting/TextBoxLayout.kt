package wojtoteka.ovh.kajet.editor.handwriting

/**
 * Układ pola TEXT/CODE na kartce przy przybliżeniu.
 *
 * Ramka, glify i wyściółka muszą iść z zoomem w tej samej proporcji.
 * Samo `fontSize * zoom` bez wysokości wiersza w em zostawia stałe 24.sp
 * z kroju body (Material scala nieustawione pola), a sztywne 4.dp
 * wyściółki zjada litery przy oddalaniu — ten sam błąd co węzły mapy myśli.
 */
object TextBoxLayout {

    /** `line-height: 1.3` — jak węzły mapy myśli, niezależnie od zooma. */
    const val LINE_RATIO = 1.3f

    /** Wyściółka pola przy 100%, w dp. Przy zoomie mnoży się razem z ramką. */
    const val PAD_DP = 4f

    data class ScreenRect(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
    ) {
        val right: Float get() = left + width
        val bottom: Float get() = top + height
    }

    fun screenRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        pageTop: Float,
        offsetX: Float,
        offsetY: Float,
        zoom: Float,
    ) = ScreenRect(
        left = (x - offsetX) * zoom,
        top = (y + pageTop - offsetY) * zoom,
        width = width * zoom,
        height = height * zoom,
    )

    /** Wysokość wiersza w pikselach ekranu: maleje razem z glifem. */
    fun linePx(fontSize: Float, zoom: Float): Float = fontSize * zoom * LINE_RATIO

    /**
     * Wyściółka w pikselach ekranu.
     *
     * [density] to `DisplayMetrics.density` (px na 1.dp). Przy zoomie 1
     * wychodzi tyle, ile dawało dawne `padding(4.dp)`.
     */
    fun padPx(density: Float, zoom: Float): Float = PAD_DP * density * zoom
}
