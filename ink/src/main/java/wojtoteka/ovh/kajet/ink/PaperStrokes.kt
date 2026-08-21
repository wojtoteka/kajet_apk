package wojtoteka.ovh.kajet.ink

import androidx.compose.ui.graphics.toArgb
import wojtoteka.ovh.kajet.core.design.FolderColor
import wojtoteka.ovh.kajet.core.design.InkPalette
import wojtoteka.ovh.kajet.core.design.KajetDarkColors
import wojtoteka.ovh.kajet.core.design.KajetLightColors
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.InkTool
import kotlin.math.pow

/**
 * Kreski na kartkę, na której ląduje PNG wstawiony do notatki tekstowej.
 *
 * Na ekranie Kajet trzyma jasny atrament na ciemnej kartce. PNG siada na
 * jasnym papierze markdowna - bez tej zamiany kremowy `#E8E4DA` znika.
 * To ta sama umowa co biały podgląd HTML i PDF: płótno papieru, nie zrzut
 * ciemnego edytora.
 */
object PaperStrokes {

    val PAGE: Int = 0xFFFFFFFF.toInt()

    private val INK: Int = InkPalette.DEFAULT_INK_LIGHT_ARGB

    private const val LIGHT_LUMINANCE = 0.45f

    fun of(strokes: List<InkStroke>): List<InkStroke> = strokes.map(::of)

    fun of(stroke: InkStroke): InkStroke =
        stroke.copy(color = ink(stroke.color, highlighter = stroke.tool == InkTool.HIGHLIGHTER))

    fun ink(argb: Int, highlighter: Boolean = false): Int {
        val rgb = rgbOf(argb)
        if (InkPalette.isDefaultInk(opaque(rgb))) return withRgb(argb, rgbOf(INK))
        inkByRgb[rgb]?.let { return withRgb(argb, it) }
        if (highlighter || rgb in highlighterRgb) return argb
        if (luminance(rgb) >= LIGHT_LUMINANCE) return withRgb(argb, rgbOf(INK))
        return argb
    }

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
