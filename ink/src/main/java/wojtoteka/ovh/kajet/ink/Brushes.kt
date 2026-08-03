package wojtoteka.ovh.kajet.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.SelfOverlap
import androidx.ink.brush.StockBrushes
import wojtoteka.ovh.kajet.core.model.InkTool

object Brushes {

    const val EPSILON = 0.1f

    private fun family(tool: InkTool): BrushFamily = when (tool) {
        InkTool.PEN -> StockBrushes.pressurePen()
        InkTool.FINELINER -> StockBrushes.marker()
        InkTool.DASHED -> StockBrushes.dashedLine()
        InkTool.PENCIL -> runCatching { StockBrushes.pencilUnstable }
            .getOrElse { StockBrushes.pressurePen() }
        InkTool.HIGHLIGHTER -> StockBrushes.highlighter(SelfOverlap.DISCARD)
    }

    fun forTool(
        tool: InkTool,
        colorArgb: Int,
        width: Float,
        opacity: Float = 1f,
        epsilon: Float = EPSILON,
    ): Brush = Brush.createWithColorIntArgb(
        family = family(tool),
        colorIntArgb = withOpacity(colorArgb, opacity),
        size = width.coerceIn(MIN_WIDTH, MAX_WIDTH),
        epsilon = epsilon,
    )

    fun pen(colorArgb: Int, width: Float, opacity: Float = 1f, epsilon: Float = EPSILON): Brush =
        forTool(InkTool.PEN, colorArgb, width, opacity, epsilon)

    fun highlighter(
        colorArgb: Int,
        width: Float,
        opacity: Float = HIGHLIGHTER_OPACITY,
        epsilon: Float = EPSILON,
    ): Brush = forTool(InkTool.HIGHLIGHTER, colorArgb, width, opacity, epsilon)

    fun withOpacity(colorArgb: Int, opacity: Float): Int {
        val alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
        return (colorArgb and 0x00FFFFFF) or (alpha shl 24)
    }

    fun opacityOf(colorArgb: Int): Float = ((colorArgb ushr 24) and 0xFF) / 255f

    val penKinds = listOf(InkTool.PEN, InkTool.FINELINER, InkTool.PENCIL, InkTool.DASHED)

    val penWidths = listOf(0.6f, 1.2f, 2.0f, 3.2f, 5.0f, 8.0f, 12.0f)
    val highlighterWidth = listOf(8f, 12f, 16f, 24f, 32f)

    const val MIN_WIDTH = 0.2f
    const val MAX_WIDTH = 48f

    const val HIGHLIGHTER_OPACITY = 0.45f
}
