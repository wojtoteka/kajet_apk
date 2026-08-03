package wojtoteka.ovh.kajet.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class InkTool {
    @SerialName("pen")
    PEN,

    @SerialName("highlighter")
    HIGHLIGHTER,

    @SerialName("fineliner")
    FINELINER,

    @SerialName("pencil")
    PENCIL,

    @SerialName("dashed")
    DASHED,
    ;

    val labelPl: String
        get() = when (this) {
            PEN -> "Pióro"
            HIGHLIGHTER -> "Zakreślacz"
            FINELINER -> "Cienkopis"
            PENCIL -> "Ołówek"
            DASHED -> "Linia przerywana"
        }
}

@Serializable
data class InkStroke(
    val id: String,
    val tool: InkTool = InkTool.PEN,
    val color: Int,
    val size: Float,
    val epsilon: Float = 0.1f,
    val input: InputKind = InputKind.STYLUS,
    val points: List<Float> = emptyList(),
) {
    val pointCount: Int get() = points.size / VALUES_PER_POINT

    fun x(i: Int): Float = points[i * VALUES_PER_POINT]
    fun y(i: Int): Float = points[i * VALUES_PER_POINT + 1]
    fun timeMs(i: Int): Float = points[i * VALUES_PER_POINT + 2]
    fun pressure(i: Int): Float = points[i * VALUES_PER_POINT + 3]
    fun tilt(i: Int): Float = points[i * VALUES_PER_POINT + 4]
    fun orientation(i: Int): Float = points[i * VALUES_PER_POINT + 5]

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

    fun translated(dx: Float, dy: Float): InkStroke {
        val moved = ArrayList<Float>(points.size)
        for (i in points.indices) {
            moved += when (i % VALUES_PER_POINT) {
                0 -> points[i] + dx
                1 -> points[i] + dy
                else -> points[i]
            }
        }
        return copy(points = moved)
    }

    companion object {
        const val VALUES_PER_POINT = 6
        const val MISSING = -1f
    }
}

@Serializable
enum class InputKind {
    @SerialName("stylus")
    STYLUS,

    @SerialName("finger")
    FINGER,

    @SerialName("mouse")
    MOUSE,
}

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
