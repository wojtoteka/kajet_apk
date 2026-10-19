package wojtoteka.ovh.kajet.ink

/**
 * Jak daleko da się odsunąć kartkę palcem po biurku.
 *
 * W poziomie granice są lustrzane: kartkę da się zepchnąć za prawą krawędź
 * ekranu dokładnie tak daleko jak za lewą - aż zniknie i jeszcze o pół swojej
 * szerokości dalej. Prawa granica musi przy tym liczyć szerokość ekranu.
 * Bez niej kartka stawała lewą krawędzią pół kartki od lewego brzegu ekranu,
 * więc osoba leworęczna nie mogła odsunąć jej w prawo, spod dłoni.
 *
 * Rachunek jest zwykłym Kotlinem, żeby dało się go sprawdzić testem
 * jednostkowym. Wszystko w jednostkach kartki, poza [viewWidth] w pikselach.
 */
object DeskBounds {

    /** Zapas za krawędzią kartki, w ułamku jej szerokości. */
    const val SLACK = 0.5f

    /**
     * [offsetX] przycięty tak, żeby kartka szerokości [pageWidth] nie odjechała
     * dalej niż [SLACK] swojej szerokości za żadną z bocznych krawędzi ekranu.
     */
    fun clampX(offsetX: Float, pageWidth: Float, viewWidth: Float, zoom: Float): Float {
        val slack = pageWidth * SLACK
        val visible = viewWidth / zoom
        return offsetX.coerceIn(-visible - slack, pageWidth + slack)
    }
}
