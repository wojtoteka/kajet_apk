package wojtoteka.ovh.kajet.ink

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

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

    /**
     * Czy punkt leży na pustym biurku: obok kartek, nad pierwszą albo pod
     * ostatnią. Szczelina między stronami się nie liczy, bo to pasek, w który
     * palec przewijający trafia przypadkiem.
     */
    fun isDesk(docX: Float, docY: Float, pageWidth: Float, totalHeight: Float): Boolean =
        docX < 0f || docX > pageWidth || docY < 0f || docY > totalHeight

    /**
     * Czy na ekranie został kawałek kartki, w który da się trafić palcem:
     * co najmniej [reach] w obu kierunkach. Wszystko w jednostkach kartki.
     */
    fun sheetReachable(
        offsetX: Float,
        offsetY: Float,
        visibleWidth: Float,
        visibleHeight: Float,
        pageWidth: Float,
        totalHeight: Float,
        reach: Float,
    ): Boolean {
        val across = min(offsetX + visibleWidth, pageWidth) - max(offsetX, 0f)
        val down = min(offsetY + visibleHeight, totalHeight) - max(offsetY, 0f)
        return across >= reach && down >= reach
    }

    /**
     * Czy dwa dotknięcia zaczęte na biurku to szczypanie, a nie oparta dłoń.
     * Oba muszą się ruszyć o co najmniej [slop], a odległość między nimi
     * zmienić o [reach]: dłoń, która się zsuwa, jedzie obiema naraz, a palec
     * przewijający obok opartej dłoni rusza tylko jednym. Wszystko w pikselach.
     */
    fun isPinch(
        startA: FloatArray,
        startB: FloatArray,
        nowA: FloatArray,
        nowB: FloatArray,
        slop: Float,
        reach: Float,
    ): Boolean {
        val movedA = hypot(nowA[0] - startA[0], nowA[1] - startA[1])
        val movedB = hypot(nowB[0] - startB[0], nowB[1] - startB[1])
        val spanThen = hypot(startA[0] - startB[0], startA[1] - startB[1])
        val spanNow = hypot(nowA[0] - nowB[0], nowA[1] - nowB[1])
        return movedA >= slop && movedB >= slop && abs(spanNow - spanThen) >= reach
    }
}
