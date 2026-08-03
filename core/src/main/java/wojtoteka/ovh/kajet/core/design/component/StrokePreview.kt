package wojtoteka.ovh.kajet.core.design.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.Rect

@Composable
fun StrokePreview(
    strokes: List<InkStroke>,
    modifier: Modifier = Modifier,
    color: Color,
    maxStrokes: Int = 240,
    width: Float = 1.4f,
) {
    if (strokes.isEmpty()) return
    val shown = if (strokes.size > maxStrokes) strokes.take(maxStrokes) else strokes

    Canvas(modifier) {
        val bounds = boundsOf(shown) ?: return@Canvas
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

private fun boundsOf(strokes: List<InkStroke>): Rect? {
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
    if (empty) return null
    return Rect(minX, minY, maxX, maxY)
}
