package wojtoteka.ovh.kajet.export

import androidx.compose.ui.graphics.toArgb
import wojtoteka.ovh.kajet.core.design.FolderColor
import wojtoteka.ovh.kajet.core.design.InkPalette
import wojtoteka.ovh.kajet.core.design.KajetDarkColors
import wojtoteka.ovh.kajet.core.design.KajetLightColors
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.InkTool
import wojtoteka.ovh.kajet.core.model.ShapeElement
import wojtoteka.ovh.kajet.core.model.TextBoxElement
import kotlin.math.pow

/**
 * Kolory notatki na prawdziwy papier: biała kartka, ciemny tusz, czytelne linie.
 *
 * Na ekranie Kajet trzyma jasny atrament na ciemnej kartce. PDF, druk i PNG
 * malują biel — bez tej zamiany kremowy `#E8E4DA` znika. To ta sama umowa
 * co biały podgląd HTML: płótno papieru, a nie zrzut ciemnego edytora.
 *
 * Kolory z palety (niebieski, czerwony, zakreślacze) zostają, o ile już
 * są dość ciemne, żeby było je widać na bieli.
 */
internal object PaperInk {

    val PAPER: Int = 0xFFFFFFFF.toInt()
    val INK: Int = InkPalette.DEFAULT_INK_LIGHT_ARGB
    val RULE: Int = 0xFFD3CCBC.toInt()

    private const val LIGHT_LUMINANCE = 0.45f

    fun ink(argb: Int, highlighter: Boolean = false): Int {
        val rgb = rgbOf(argb)
        if (InkPalette.isDefaultInk(opaque(rgb))) return withRgb(argb, rgbOf(INK))
        inkByRgb[rgb]?.let { return withRgb(argb, it) }
        if (highlighter || rgb in highlighterRgb) return argb
        if (luminance(rgb) >= LIGHT_LUMINANCE) return withRgb(argb, rgbOf(INK))
        return argb
    }

    fun plate(argb: Int): Int {
        if (argb == 0) return 0
        plateByRgb[rgbOf(argb)]?.let { return withRgb(argb, it) }
        return argb
    }

    fun forStroke(stroke: InkStroke): InkStroke =
        stroke.copy(color = ink(stroke.color, highlighter = stroke.tool == InkTool.HIGHLIGHTER))

    fun forShape(shape: ShapeElement): ShapeElement = shape.copy(
        color = ink(shape.color),
        fill = plate(shape.fill),
    )

    fun forField(field: TextBoxElement): TextBoxElement = field.copy(
        color = ink(field.color),
        background = plate(field.background),
    )

    private val highlighterRgb: Set<Int> = setOf(
        rgbOf(InkPalette.HighlighterYellow.toArgb()),
        rgbOf(InkPalette.HighlighterGreen.toArgb()),
        rgbOf(InkPalette.HighlighterPink.toArgb()),
        rgbOf(InkPalette.HighlighterBlue.toArgb()),
    )

    private val inkByRgb: Map<Int, Int> = buildMap {
        fun pair(from: Int, to: Int) {
            put(rgbOf(from), rgbOf(to))
        }
        pair(KajetDarkColors.text.toArgb(), KajetLightColors.text.toArgb())
        pair(KajetDarkColors.muted.toArgb(), KajetLightColors.muted.toArgb())
        pair(KajetDarkColors.accent.toArgb(), KajetLightColors.accent.toArgb())
        pair(KajetDarkColors.danger.toArgb(), KajetLightColors.danger.toArgb())
        FolderColor.entries.forEach { color ->
            pair(color.color(true).toArgb(), color.color(false).toArgb())
        }
    }

    private val plateByRgb: Map<Int, Int> = buildMap {
        fun pair(from: Int, to: Int) {
            put(rgbOf(from), rgbOf(to))
        }
        pair(KajetDarkColors.desk.toArgb(), KajetLightColors.desk.toArgb())
        pair(KajetDarkColors.sheet.toArgb(), KajetLightColors.sheet.toArgb())
        pair(KajetDarkColors.line.toArgb(), KajetLightColors.line.toArgb())
        FolderColor.entries.forEach { color ->
            pair(color.color(true).toArgb(), color.color(false).toArgb())
        }
    }

    private fun rgbOf(argb: Int): Int = argb and 0x00FFFFFF

    private fun opaque(rgb: Int): Int = rgb or 0xFF000000.toInt()

    private fun withRgb(argb: Int, rgb: Int): Int =
        (argb and 0xFF000000.toInt()) or (rgb and 0x00FFFFFF)

    internal fun luminance(rgb: Int): Float {
        fun channel(value: Int): Float {
            val c = value / 255f
            return if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        }
        return 0.2126f * channel((rgb shr 16) and 0xFF) +
            0.7152f * channel((rgb shr 8) and 0xFF) +
            0.0722f * channel(rgb and 0xFF)
    }
}
