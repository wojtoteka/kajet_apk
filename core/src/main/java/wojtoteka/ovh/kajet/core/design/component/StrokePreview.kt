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
import androidx.compose.ui.graphics.drawscope.withTransform
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.Rect

/**
 * Miniatura strony gotowa do rysowania: łamane złożone już w ścieżki i
 * prostokąt, który obejmują - we współrzędnych strony.
 *
 * Składa się ją poza wątkiem rysowania ([of] można wołać z dowolnego wątku).
 * Wiersz listy, który wjeżdża na ekran, tylko rysuje gotowe ścieżki. Dawniej
 * w chwili pokazania wiersza składał setki łamanych i przewijanie listy
 * z notatkami odręcznymi się zacinało.
 */
class PreviewDrawing private constructor(
    val paths: List<Path>,
    val bounds: Rect,
) {
    companion object {
        /** [lines] to łamane x, y, x, y… we współrzędnych strony. null - nie ma czego rysować. */
        fun of(lines: List<FloatArray>): PreviewDrawing? {
            val bounds = boundsOf(lines) ?: return null
            val paths = lines.filter { it.size >= 4 }.map { line ->
                Path().apply {
                    moveTo(line[0], line[1])
                    var i = 2
                    while (i + 1 < line.size) {
                        lineTo(line[i], line[i + 1])
                        i += 2
                    }
                }
            }
            return PreviewDrawing(paths, bounds)
        }
    }
}

/**
 * Miniatura strony na liście.
 *
 * Podgląd pokazuje górę strony: skaluje do szerokości i ucina to, co nie
 * mieści się na wysokości. Przycięcie do własnych granic, bo bez niego
 * rysunek wchodziłby na datę i na tytuł następnej notatki.
 */
@Composable
fun StrokePreview(
    drawing: PreviewDrawing,
    modifier: Modifier = Modifier,
    color: Color,
    width: Float = 1.4f,
) {
    Canvas(modifier.clipToBounds()) {
        val bounds = drawing.bounds
        val contentWidth = bounds.width.coerceAtLeast(1f)
        val contentHeight = bounds.height.coerceAtLeast(1f)
        val scale = size.width / contentWidth
        val verticalScale = size.height / contentHeight
        val used = minOf(scale, verticalScale * 3f).coerceAtMost(scale)
        // Ścieżki są we współrzędnych strony - skala idzie na całe płótno,
        // a grubość kreski dzieli się przez nią, żeby na ekranie była stała.
        val stroke = Stroke(width = width / used, cap = StrokeCap.Round, join = StrokeJoin.Round)
        withTransform({
            scale(used, used, pivot = Offset.Zero)
            translate(-bounds.left, -bounds.top)
        }) {
            for (path in drawing.paths) drawPath(path = path, color = color, style = stroke)
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

private fun boundsOf(lines: List<FloatArray>): Rect? {
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    var empty = true
    for (line in lines) {
        var i = 0
        while (i + 1 < line.size) {
            empty = false
            val x = line[i]
            val y = line[i + 1]
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
