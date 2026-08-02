package wojtoteka.ovh.kajet.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import wojtoteka.ovh.kajet.core.model.InkStroke
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Zamiana kresek na obrazek PNG.
 *
 * Potrzebne w dwóch miejscach: rysunek wstawiony w środek notatki tekstowej
 * zapisuje się jako obrazek, żeby dało się go pokazać w podglądzie i wyeksportować,
 * a eksport strony do PNG korzysta z tej samej drogi.
 *
 * Obok obrazka zapisujemy też same kreski, więc rysunek zostaje edytowalny.
 */
object RysunekDoObrazu {

    /** Ile pikseli przypada na jednostkę strony przy zapisie obrazka. */
    const val GESTOSC = 3f

    fun bitmapa(
        kreski: List<InkStroke>,
        szerokosc: Float,
        wysokosc: Float,
        gestosc: Float = GESTOSC,
        kolorTla: Int? = null,
    ): Bitmap {
        val szerokoscPx = max(1, (szerokosc * gestosc).roundToInt())
        val wysokoscPx = max(1, (wysokosc * gestosc).roundToInt())
        val bitmapa = Bitmap.createBitmap(szerokoscPx, wysokoscPx, Bitmap.Config.ARGB_8888)
        val plotno = Canvas(bitmapa)
        if (kolorTla != null) plotno.drawColor(kolorTla)

        val rysownik = CanvasStrokeRenderer.create()
        val macierz = Matrix().apply { setScale(gestosc, gestosc) }
        for (kreska in kreski) {
            val gotowa = runCatching { Kresy.doSilnika(kreska) }.getOrNull() ?: continue
            rysownik.draw(plotno, gotowa, macierz)
        }
        return bitmapa
    }

    fun png(
        kreski: List<InkStroke>,
        szerokosc: Float,
        wysokosc: Float,
        gestosc: Float = GESTOSC,
        kolorTla: Int? = null,
    ): ByteArray {
        val bitmapa = bitmapa(kreski, szerokosc, wysokosc, gestosc, kolorTla)
        val strumien = ByteArrayOutputStream()
        bitmapa.compress(Bitmap.CompressFormat.PNG, 100, strumien)
        bitmapa.recycle()
        return strumien.toByteArray()
    }
}
