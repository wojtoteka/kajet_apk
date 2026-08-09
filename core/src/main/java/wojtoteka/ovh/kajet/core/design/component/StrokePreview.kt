package wojtoteka.ovh.kajet.core.design.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.Rect

/**
 * Miniatura strony.
 *
 * [outlines] to gotowe łamane kształtów — ciąg x, y, x, y… we współrzędnych
 * strony. Kształty przychodzą policzone z zewnątrz, bo ich geometria siedzi w
 * module atramentu, a ten leży wyżej niż wygląd aplikacji.
 */
@Composable
fun StrokePreview(
    strokes: List<InkStroke>,
    modifier: Modifier = Modifier,
    color: Color,
    maxStrokes: Int = 240,
    width: Float = 1.4f,
    outlines: List<FloatArray> = emptyList(),
) {
    if (strokes.isEmpty() && outlines.isEmpty()) return
    val shown = if (strokes.size > maxStrokes) strokes.take(maxStrokes) else strokes

    // Przycięcie do własnych granic. Bez niego rysunek wychodzi poza pasek
    // podglądu i wchodzi na datę oraz na tytuł następnej notatki — kreski
    // ratowało do tej pory pomijanie punktów spod dolnej krawędzi, ale kształt
    // to jedna łamana, której w środku nie da się w ten sposób uciąć.
    Canvas(modifier.clipToBounds()) {
        val bounds = boundsOf(shown, outlines) ?: return@Canvas
        val contentWidth = (bounds.width).coerceAtLeast(1f)
        val contentHeight = (bounds.height).coerceAtLeast(1f)

        // Podgląd pokazuje górę strony, więc skalujemy do szerokości
        // i ucinamy to, co nie mieści się na wysokości.
        val scale = size.width / contentWidth
        val verticalScale = size.height / contentHeight
        val used = minOf(scale, verticalScale * 3f).coerceAtMost(scale)

        for (stroke in shown) {
            val count = stroke.pointCount
            if (count < 2) continue
            val path = Path()
            var started = false
            for (i in 0 until count) {
                val x = (stroke.x(i) - bounds.left) * used
                val y = (stroke.y(i) - bounds.top) * used
                if (y > size.height + 8f) continue
                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
            }
            if (!started) continue
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        for (outline in outlines) {
            if (outline.size < 4) continue
            val path = Path()
            var i = 0
            while (i < outline.size) {
                val x = (outline[i] - bounds.left) * used
                val y = (outline[i + 1] - bounds.top) * used
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                i += 2
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrokes(
    strokes: List<InkStroke>,
    color: Color,
    offset: Offset = Offset.Zero,
    scale: Float = 1f,
    width: Float = 2f,
) {
    for (stroke in strokes) {
        val count = stroke.pointCount
        if (count < 2) continue
        val path = Path()
        path.moveTo(stroke.x(0) * scale + offset.x, stroke.y(0) * scale + offset.y)
        for (i in 1 until count) {
            path.lineTo(stroke.x(i) * scale + offset.x, stroke.y(i) * scale + offset.y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

private fun boundsOf(strokes: List<InkStroke>, outlines: List<FloatArray>): Rect? {
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    var empty = true
    for (stroke in strokes) {
        for (i in 0 until stroke.pointCount) {
            empty = false
            val x = stroke.x(i)
            val y = stroke.y(i)
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
    }
    for (outline in outlines) {
        var i = 0
        while (i < outline.size) {
            empty = false
            val x = outline[i]
            val y = outline[i + 1]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
            i += 2
        }
    }
    if (empty) return null
    return Rect(minX, minY, maxX, maxY)
}
