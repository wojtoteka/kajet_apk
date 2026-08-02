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

/**
 * Znak Kajetu: kartka ze ściętym rogiem, linia marginesu i jedna kreska
 * o zmiennej grubości, taka jak spod rysika.
 *
 * Rysowany kodem, nie plikiem, bo ma być ostry w każdym rozmiarze
 * i przyjmować kolor z motywu. Ten sam kształt leży w pliku logo.svg
 * i w ikonie aplikacji.
 */
@Composable
fun ZnakKajetu(
    modifier: Modifier = Modifier,
    kolor: Color,
    zKartka: Boolean = true,
) {
    Canvas(
        modifier.semantics { contentDescription = "Kajet" },
    ) {
        val bok = minOf(size.width, size.height)
        val s = bok / 24f
        val grubosc = 1.9f * s

        if (zKartka) {
            val kartka = Path().apply {
                moveTo(4f * s, 2.5f * s)
                lineTo(16.5f * s, 2.5f * s)
                lineTo(20f * s, 6f * s)
                lineTo(20f * s, 21.5f * s)
                lineTo(4f * s, 21.5f * s)
                close()
            }
            drawPath(kartka, kolor, style = Stroke(width = grubosc, cap = StrokeCap.Round))
        }

        // Linia marginesu, znak rozpoznawczy aplikacji.
        drawLine(
            color = kolor,
            start = Offset(9f * s, if (zKartka) 2.5f * s else 1.5f * s),
            end = Offset(9f * s, if (zKartka) 21.5f * s else 22.5f * s),
            strokeWidth = grubosc,
            cap = StrokeCap.Round,
        )

        // Kreska przechodząca przez margines, od cienkiej do grubej.
        val kroki = 12
        val poczatekX = 5.5f * s
        val koniecX = 18.5f * s
        val poczatekY = 15.5f * s
        val koniecY = 9.5f * s
        for (i in 0 until kroki) {
            val t1 = i / kroki.toFloat()
            val t2 = (i + 1) / kroki.toFloat()
            val x1 = poczatekX + (koniecX - poczatekX) * t1
            val x2 = poczatekX + (koniecX - poczatekX) * t2
            val y1 = poczatekY + (koniecY - poczatekY) * t1 + kotlin.math.sin(t1 * 3.1f) * 1.6f * s
            val y2 = poczatekY + (koniecY - poczatekY) * t2 + kotlin.math.sin(t2 * 3.1f) * 1.6f * s
            drawLine(
                color = kolor,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = (0.7f + 2.1f * t2) * s,
                cap = StrokeCap.Round,
            )
        }
    }
}
