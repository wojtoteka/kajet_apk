package wojtoteka.ovh.kajet.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.SelfOverlap
import androidx.ink.brush.StockBrushes
import wojtoteka.ovh.kajet.core.model.InkTool

/**
 * Pędzle silnika androidx.ink.
 *
 * Pióro reaguje na nacisk rysika, więc kreska grubieje tam, gdzie mocniej naciskasz.
 * Zakreślacz kładzie się pod tekstem i nie ciemnieje w miejscu, gdzie kreska
 * nachodzi sama na siebie, bo inaczej powstałyby brzydkie plamy.
 */
object Pedzle {

    /** Dokładność uproszczenia toru kreski w jednostkach strony. Mniej znaczy wierniej. */
    const val DOKLADNOSC = 0.1f

    fun pioro(kolorArgb: Int, grubosc: Float, dokladnosc: Float = DOKLADNOSC): Brush =
        Brush.createWithColorIntArgb(
            family = StockBrushes.pressurePen(),
            colorIntArgb = kolorArgb,
            size = grubosc,
            epsilon = dokladnosc,
        )

    fun zakreslacz(kolorArgb: Int, grubosc: Float, dokladnosc: Float = DOKLADNOSC): Brush =
        Brush.createWithColorIntArgb(
            family = StockBrushes.highlighter(SelfOverlap.DISCARD),
            colorIntArgb = kolorArgb,
            size = grubosc,
            epsilon = dokladnosc,
        )

    fun dla(narzedzie: InkTool, kolorArgb: Int, grubosc: Float, dokladnosc: Float = DOKLADNOSC): Brush =
        when (narzedzie) {
            InkTool.PIORO -> pioro(kolorArgb, grubosc, dokladnosc)
            InkTool.ZAKRESLACZ -> zakreslacz(kolorArgb, grubosc, dokladnosc)
        }

    /** Grubości do wyboru w pasku narzędzi, w jednostkach strony. */
    val gruboscPiora = listOf(1.2f, 2.0f, 3.2f, 5.0f, 8.0f)
    val gruboscZakreslacza = listOf(10f, 16f, 24f)
}
