package wojtoteka.ovh.kajet.core.design.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun KajetMark(
    modifier: Modifier = Modifier,
    color: Color,
    withSheet: Boolean = true,
) {
    Canvas(
        modifier.semantics { contentDescription = "Kajet" },
    ) {
        val side = minOf(size.width, size.height)
        val s = side / 24f
        val width = 1.9f * s

        if (withSheet) {
            val sheet = Path().apply {
                moveTo(4f * s, 2.5f * s)
                lineTo(16.5f * s, 2.5f * s)
                lineTo(20f * s, 6f * s)
                lineTo(20f * s, 21.5f * s)
                lineTo(4f * s, 21.5f * s)
                close()
            }
            drawPath(sheet, color, style = Stroke(width = width, cap = StrokeCap.Round))
        }

        // Linia marginesu, znak rozpoznawczy aplikacji.
        drawLine(
            color = color,
            start = Offset(9f * s, if (withSheet) 2.5f * s else 1.5f * s),
            end = Offset(9f * s, if (withSheet) 21.5f * s else 22.5f * s),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )

        // Kreska przechodząca przez margines, od cienkiej do grubej.
        val steps = 12
        val startX = 5.5f * s
        val endX = 18.5f * s
        val startY = 15.5f * s
        val endY = 9.5f * s
        for (i in 0 until steps) {
            val t1 = i / steps.toFloat()
            val t2 = (i + 1) / steps.toFloat()
            val x1 = startX + (endX - startX) * t1
            val x2 = startX + (endX - startX) * t2
            val y1 = startY + (endY - startY) * t1 + kotlin.math.sin(t1 * 3.1f) * 1.6f * s
            val y2 = startY + (endY - startY) * t2 + kotlin.math.sin(t2 * 3.1f) * 1.6f * s
            drawLine(
                color = color,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = (0.7f + 2.1f * t2) * s,
                cap = StrokeCap.Round,
            )
        }
    }
}
