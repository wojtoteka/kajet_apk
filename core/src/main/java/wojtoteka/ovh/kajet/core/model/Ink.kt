package wojtoteka.ovh.kajet.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Narzędzie, którym powstała kreska. */
@Serializable
enum class InkTool {
    @SerialName("pioro")
    PIORO,

    @SerialName("zakreslacz")
    ZAKRESLACZ,
}

/**
 * Kreska zapisana jako surowe punkty wejściowe z rysika, a nie jako gotowy kształt.
 *
 * Dzięki temu przy wczytaniu można ją narysować ponownie w pełnej jakości,
 * przeliczyć na inny rozmiar strony albo wysłać do rozpoznawania pisma.
 *
 * Punkty są spakowane w jedną listę liczb, po [WARTOSCI_NA_PUNKT] na punkt,
 * w kolejności: x, y, czas w milisekundach od początku kreski, nacisk,
 * pochylenie w radianach, obrót w radianach.
 * Wartość -1 oznacza, że rysik nie podał tej danej.
 * Taki zapis jest kilka razy mniejszy od listy obiektów i wczytuje się szybciej.
 */
@Serializable
data class InkStroke(
    val id: String,
    val tool: InkTool = InkTool.PIORO,
    /** Kolor w formacie ARGB. */
    val color: Int,
    /** Grubość pisaka w punktach strony. */
    val size: Float,
    /** Dokładność uproszczenia toru kreski w punktach strony. */
    val epsilon: Float = 0.1f,
    /** Czy kreska powstała rysikiem, palcem czy myszą. */
    val input: InputKind = InputKind.RYSIK,
    val points: List<Float> = emptyList(),
) {
    val pointCount: Int get() = points.size / WARTOSCI_NA_PUNKT

    fun x(i: Int): Float = points[i * WARTOSCI_NA_PUNKT]
    fun y(i: Int): Float = points[i * WARTOSCI_NA_PUNKT + 1]
    fun timeMs(i: Int): Float = points[i * WARTOSCI_NA_PUNKT + 2]
    fun pressure(i: Int): Float = points[i * WARTOSCI_NA_PUNKT + 3]
    fun tilt(i: Int): Float = points[i * WARTOSCI_NA_PUNKT + 4]
    fun orientation(i: Int): Float = points[i * WARTOSCI_NA_PUNKT + 5]

    /** Prostokąt otaczający kreskę, bez uwzględnienia grubości. */
    fun bounds(): Rect {
        if (pointCount == 0) return Rect(0f, 0f, 0f, 0f)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in 0 until pointCount) {
            val px = x(i)
            val py = y(i)
            if (px < minX) minX = px
            if (px > maxX) maxX = px
            if (py < minY) minY = py
            if (py > maxY) maxY = py
        }
        return Rect(minX, minY, maxX, maxY)
    }

    /** Przesuwa kreskę o zadany wektor. Używane przy przenoszeniu zaznaczenia. */
    fun translated(dx: Float, dy: Float): InkStroke {
        val moved = ArrayList<Float>(points.size)
        for (i in points.indices) {
            moved += when (i % WARTOSCI_NA_PUNKT) {
                0 -> points[i] + dx
                1 -> points[i] + dy
                else -> points[i]
            }
        }
        return copy(points = moved)
    }

    companion object {
        const val WARTOSCI_NA_PUNKT = 6
        const val BRAK = -1f
    }
}

/** Czym narysowano kreskę. Potrzebne przy odrzucaniu dłoni i przy rozpoznawaniu pisma. */
@Serializable
enum class InputKind {
    @SerialName("rysik")
    RYSIK,

    @SerialName("palec")
    PALEC,

    @SerialName("mysz")
    MYSZ,
}

/** Prostokąt w układzie strony. Osobny od typu z Compose, bo model nie zależy od interfejsu. */
@Serializable
data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(px: Float, py: Float): Boolean =
        px >= left && px <= right && py >= top && py <= bottom

    fun overlaps(other: Rect): Boolean =
        left <= other.right && right >= other.left && top <= other.bottom && bottom >= other.top

    fun expanded(by: Float): Rect = Rect(left - by, top - by, right + by, bottom + by)

    companion object {
        fun of(points: List<Float>, stride: Int): Rect {
            if (points.isEmpty()) return Rect(0f, 0f, 0f, 0f)
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            var i = 0
            while (i < points.size) {
                val px = points[i]
                val py = points[i + 1]
                if (px < minX) minX = px
                if (px > maxX) maxX = px
                if (py < minY) minY = py
                if (py > maxY) maxY = py
                i += stride
            }
            return Rect(minX, minY, maxX, maxY)
        }
    }
}
