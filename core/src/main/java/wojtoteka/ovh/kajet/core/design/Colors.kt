package wojtoteka.ovh.kajet.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import wojtoteka.ovh.kajet.core.text.Strings

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
    val labelEn: String,
    private val light: Color,
    private val dark: Color,
) {
    Graphite("grafit", "Grafit", "Graphite", Color(0xFF5B584F), Color(0xFF8E8A7E)),
    GreenInk("zielen", "Zieleń", "Green ink", Color(0xFF0F6B5C), Color(0xFF4FB39C)),
    Teal("morski", "Morski", "Teal", Color(0xFF1C5C74), Color(0xFF5AA8C4)),
    Brick("ceglany", "Ceglany", "Brick", Color(0xFFA6392E), Color(0xFFE2857A)),
    Mustard("musztarda", "Musztardowy", "Mustard", Color(0xFF8A6212), Color(0xFFD6A648)),
    Olive("oliwka", "Oliwkowy", "Olive", Color(0xFF56662A), Color(0xFF9EB367)),
    Rust("rdza", "Rdzawy", "Rust", Color(0xFF8F4A1C), Color(0xFFD08A55)),
    Navy("granat", "Granatowy", "Navy", Color(0xFF2C3E6B), Color(0xFF7C90C0)),
    Violet("fiolet", "Fioletowy", "Violet", Color(0xFF5B3E8C), Color(0xFF9E86C9)),
    Rose("roz", "Różowy", "Rose", Color(0xFF9C3A63), Color(0xFFD887A8)),
    Chocolate("braz", "Brązowy", "Chocolate", Color(0xFF5F4630), Color(0xFFA98A66)),
    Neutral("bez", "Bezbarwny", "Plain", Color(0xFF8C877C), Color(0xFF6E6A61));

    fun color(isDark: Boolean): Color = if (isDark) dark else light

    fun label(words: Strings): String = if (words.english) labelEn else labelPl

    companion object {
        fun fromId(id: String?): FolderColor = entries.firstOrNull { it.id == id } ?: Graphite
    }
}

object InkPalette {
    val Black = Color(0xFF23211D)

    // Dawny „Biały" (0xFFF0EDE4) zlewał się z papierem: jedno przypadkowe
    // dotknięcie kropki w pasku i pisanie „znikało". Zamiast niego jest szary.
    val Gray = Color(0xFF6F6A5E)
    val Blue = Color(0xFF1B4F8C)
    val Red = Color(0xFFB0322A)
    val Green = Color(0xFF1F6B3A)
    val Brown = Color(0xFF6B4A22)

    val HighlighterYellow = Color(0xFFF2D24B)
    val HighlighterGreen = Color(0xFF8FD07A)
    val HighlighterPink = Color(0xFFF29BAE)
    val HighlighterBlue = Color(0xFF87BEE8)

    fun pens(words: Strings): List<Pair<String, Color>> = listOf(
        words.penBlack to Black,
        words.penGrey to Gray,
        words.penBlue to Blue,
        words.penRed to Red,
        words.penGreen to Green,
        words.penBrown to Brown,
    )

    /*
      Atrament domyślny zależy od kartki: ciemny na jasnej, jasny na ciemnej -
      to ta sama para co KajetColors.defaultInk.

      Bez tego rozróżnienia pisak zapamiętany na jasnej kartce wracał na ciemną
      jako czarny i pisało się czernią po czerni: kreska była, tylko nie było
      jej widać.
    */
    val DEFAULT_INK_LIGHT_ARGB: Int = 0xFF23211D.toInt()
    val DEFAULT_INK_DARK_ARGB: Int = 0xFFE8E4DA.toInt()

    /** Czy to „zwykły atrament" - którejkolwiek kartki. */
    fun isDefaultInk(argb: Int): Boolean =
        argb == DEFAULT_INK_LIGHT_ARGB || argb == DEFAULT_INK_DARK_ARGB

    /**
     * Pisaki do paska. Pierwszy to atrament tej kartki, na której się właśnie
     * pisze - żeby na ciemnej stronie pierwsza kropka nie była czarna.
     */
    fun pens(isDark: Boolean, words: Strings): List<Pair<String, Color>> = listOf(
        words.penInk to Color(if (isDark) DEFAULT_INK_DARK_ARGB else DEFAULT_INK_LIGHT_ARGB),
    ) + pens(words).drop(1)

    /** Kolor dawnego „Białego" pisaka. Zapamiętany w ustawieniach ma wrócić do domyślnego. */
    val LEGACY_WHITE_ARGB: Int = 0xFFF0EDE4.toInt()

    fun highlighters(words: Strings): List<Pair<String, Color>> = listOf(
        words.penYellow to HighlighterYellow,
        words.penGreen to HighlighterGreen,
        words.penPink to HighlighterPink,
        words.penBlue to HighlighterBlue,
    )
}
