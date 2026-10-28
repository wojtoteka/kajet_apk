package wojtoteka.ovh.kajet.ink

import kotlin.math.min

/**
 * Brzeg kartki dla pisaka: atrament istnieje tylko na kartce.
 *
 * Kartka przycina rysowanie do swoich krawędzi, ale kreska brała punkty także
 * z biurka obok niej. Pisało się więc i poza kartką - tylko tego nie było
 * widać, a niewidoczny atrament i tak lądował w pliku. Kreska kończy się teraz
 * na brzegu, a gdy rysik wraca na kartkę, zaczyna się nowa - jak przy pisaniu
 * po prawdziwej kartce leżącej na stole.
 *
 * Rachunek jest zwykłym Kotlinem, żeby dało się go sprawdzić testem
 * jednostkowym. Wszystko w jednostkach kartki, od jej lewego górnego rogu.
 */
object PageEdge {

    /** Punkt leży na kartce. Sam brzeg też się liczy. */
    fun inside(x: Float, y: Float, width: Float, height: Float): Boolean =
        x >= 0f && x <= width && y >= 0f && y <= height

    /**
     * Ułamek drogi od punktu na kartce ([fromX], [fromY]) do punktu poza nią
     * ([toX], [toY]), na którym odcinek przecina brzeg: 0 to punkt na kartce,
     * 1 - ten poza nią. Kartka jest prostokątem, więc odcinek wychodzi z niej
     * dokładnie raz.
     *
     * Gdy drugi punkt też leży na kartce (urosła pod rysikiem), wynik to 1:
     * po drodze nie ma brzegu.
     */
    fun exit(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        width: Float,
        height: Float,
    ): Float = min(axisExit(fromX, toX, width), axisExit(fromY, toY, height))

    private fun axisExit(from: Float, to: Float, size: Float): Float = when {
        to > size && from < size -> (size - from) / (to - from)
        to < 0f && from > 0f -> from / (from - to)
        // Początek leży już na samym brzegu.
        to > size || to < 0f -> 0f
        else -> 1f
    }
}
