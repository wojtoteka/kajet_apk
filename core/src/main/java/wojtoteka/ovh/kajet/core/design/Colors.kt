package wojtoteka.ovh.kajet.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class KajetColors(
    val desk: Color,
    val sheet: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val line: Color,
    val danger: Color,
    val isDark: Boolean,
) {
    val onAccent: Color get() = if (isDark) Color(0xFF10221E) else Color(0xFFF4F1EA)

    val accentWash: Color get() = accent.copy(alpha = if (isDark) 0.18f else 0.10f)

    val defaultInk: Color get() = if (isDark) Color(0xFFE8E4DA) else Color(0xFF23211D)

    val pageRule: Color get() = if (isDark) Color(0xFF3A382F) else Color(0xFFD3CCBC)
}

val KajetLightColors = KajetColors(
    desk = Color(0xFFE7E2D6),
    sheet = Color(0xFFF4F1EA),
    text = Color(0xFF23211D),
    muted = Color(0xFF67635A),
    accent = Color(0xFF0F6B5C),
    line = Color(0xFFD3CCBC),
    danger = Color(0xFFA6392E),
    isDark = false,
)

val KajetDarkColors = KajetColors(
    desk = Color(0xFF171614),
    sheet = Color(0xFF24231F),
    text = Color(0xFFE8E4DA),
    muted = Color(0xFF9A948A),
    accent = Color(0xFF4FB39C),
    line = Color(0xFF35332C),
    danger = Color(0xFFE2857A),
    isDark = true,
)

val LocalKajetColors = staticCompositionLocalOf { KajetLightColors }

enum class FolderColor(
    val id: String,
    val labelPl: String,
    private val light: Color,
    private val dark: Color,
) {
    Graphite("grafit", "Grafit", Color(0xFF5B584F), Color(0xFF8E8A7E)),
    GreenInk("zielen", "Zieleń", Color(0xFF0F6B5C), Color(0xFF4FB39C)),
    Teal("morski", "Morski", Color(0xFF1C5C74), Color(0xFF5AA8C4)),
    Brick("ceglany", "Ceglany", Color(0xFFA6392E), Color(0xFFE2857A)),
    Mustard("musztarda", "Musztardowy", Color(0xFF8A6212), Color(0xFFD6A648)),
    Olive("oliwka", "Oliwkowy", Color(0xFF56662A), Color(0xFF9EB367)),
    Rust("rdza", "Rdzawy", Color(0xFF8F4A1C), Color(0xFFD08A55)),
    Neutral("bez", "Bezbarwny", Color(0xFF8C877C), Color(0xFF6E6A61));

    fun color(isDark: Boolean): Color = if (isDark) dark else light

    companion object {
        fun fromId(id: String?): FolderColor = entries.firstOrNull { it.id == id } ?: Graphite
    }
}

object InkPalette {
    val Black = Color(0xFF23211D)
    val White = Color(0xFFF0EDE4)
    val Blue = Color(0xFF1B4F8C)
    val Red = Color(0xFFB0322A)
    val Green = Color(0xFF1F6B3A)
    val Brown = Color(0xFF6B4A22)

    val HighlighterYellow = Color(0xFFF2D24B)
    val HighlighterGreen = Color(0xFF8FD07A)
    val HighlighterPink = Color(0xFFF29BAE)
    val HighlighterBlue = Color(0xFF87BEE8)

    val pens = listOf(
        "Czarny" to Black,
        "Biały" to White,
        "Niebieski" to Blue,
        "Czerwony" to Red,
        "Zielony" to Green,
        "Brązowy" to Brown,
    )

    val highlighters = listOf(
        "Żółty" to HighlighterYellow,
        "Zielony" to HighlighterGreen,
        "Różowy" to HighlighterPink,
        "Niebieski" to HighlighterBlue,
    )
}
