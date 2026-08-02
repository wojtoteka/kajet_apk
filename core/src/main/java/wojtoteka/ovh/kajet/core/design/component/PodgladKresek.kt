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

/**
 * Mały podgląd pisma odręcznego, rysowany prosto z zapisanych punktów.
 *
 * Na liście notatek pokazuje pierwsze kreski, więc widać własne pismo,
 * a nie kolejną taką samą ikonkę. To nie jest pełny silnik kreski,
 * tylko łamana o stałej grubości, bo przy tej wielkości nacisku i tak nie widać.
 */
@Composable
fun PodgladKresek(
    kreski: List<InkStroke>,
    modifier: Modifier = Modifier,
    kolor: Color,
    maksKresek: Int = 240,
    grubosc: Float = 1.4f,
) {
    if (kreski.isEmpty()) return
    val wybrane = if (kreski.size > maksKresek) kreski.take(maksKresek) else kreski

    Canvas(modifier) {
        val granice = obszar(wybrane) ?: return@Canvas
        val szerokoscTresci = (granice.width).coerceAtLeast(1f)
        val wysokoscTresci = (granice.height).coerceAtLeast(1f)

        // Podgląd pokazuje górę strony, więc skalujemy do szerokości
        // i ucinamy to, co nie mieści się na wysokości.
        val skala = size.width / szerokoscTresci
        val skalaPionowa = size.height / wysokoscTresci
        val uzyta = minOf(skala, skalaPionowa * 3f).coerceAtMost(skala)

        for (kreska in wybrane) {
            val liczba = kreska.pointCount
            if (liczba < 2) continue
            val sciezka = Path()
            var zaczete = false
            for (i in 0 until liczba) {
                val x = (kreska.x(i) - granice.left) * uzyta
                val y = (kreska.y(i) - granice.top) * uzyta
                if (y > size.height + 8f) continue
                if (!zaczete) {
                    sciezka.moveTo(x, y)
                    zaczete = true
                } else {
                    sciezka.lineTo(x, y)
                }
            }
            if (!zaczete) continue
            drawPath(
                path = sciezka,
                color = kolor,
                style = Stroke(width = grubosc, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** Prosty rysunek kilku kresek w zadanym prostokącie, użyty w podglądzie węzła mapy myśli. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.rysujKreski(
    kreski: List<InkStroke>,
    kolor: Color,
    przesuniecie: Offset = Offset.Zero,
    skala: Float = 1f,
    grubosc: Float = 2f,
) {
    for (kreska in kreski) {
        val liczba = kreska.pointCount
        if (liczba < 2) continue
        val sciezka = Path()
        sciezka.moveTo(kreska.x(0) * skala + przesuniecie.x, kreska.y(0) * skala + przesuniecie.y)
        for (i in 1 until liczba) {
            sciezka.lineTo(kreska.x(i) * skala + przesuniecie.x, kreska.y(i) * skala + przesuniecie.y)
        }
        drawPath(
            path = sciezka,
            color = kolor,
            style = Stroke(width = grubosc, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

private fun obszar(kreski: List<InkStroke>): Rect? {
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    var puste = true
    for (kreska in kreski) {
        for (i in 0 until kreska.pointCount) {
            puste = false
            val x = kreska.x(i)
            val y = kreska.y(i)
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
    }
    if (puste) return null
    return Rect(minX, minY, maxX, maxY)
}
