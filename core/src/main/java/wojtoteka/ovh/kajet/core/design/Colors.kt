package wojtoteka.ovh.kajet.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Paleta Kajetu. Sześć wartości na motyw plus jeden kolor ostrzegawczy.
 *
 * Motyw jasny to ciemniejsze biurko i jaśniejsza kartka na nim.
 * Motyw ciemny nie jest odwróconym jasnym: kartka nadal jest jaśniejsza od tła,
 * ale obie są ciepłym grafitem, a nie czernią, i kreska pisana rysikiem
 * domyślnie zmienia kolor na jasny, jak kreda na tablicy.
 */
@Immutable
data class KajetColors(
    /** Tło aplikacji, paski narzędzi, obszar wokół kartki. */
    val desk: Color,
    /** Kartka, panele, wiersze list. */
    val sheet: Color,
    /** Tekst główny. Kontrast co najmniej 12:1 na kartce. */
    val text: Color,
    /** Tekst drugorzędny, daty, podpisy. Kontrast co najmniej 4.6:1 na obu tłach. */
    val muted: Color,
    /** Akcent. Używany oszczędnie: stan wybrany, aktywne narzędzie, jedno działanie główne. */
    val accent: Color,
    /** Włoskowate linie, ramki, linie marginesu. */
    val line: Color,
    /** Błędy i usuwanie. Poza paletą, bo niesie znaczenie, nie ozdobę. */
    val danger: Color,
    /** Czy to motyw ciemny. Potrzebne przy doborze koloru kreski i tła stron. */
    val isDark: Boolean,
) {
    /** Kolor tekstu na tle akcentu. */
    val onAccent: Color get() = if (isDark) Color(0xFF10221E) else Color(0xFFF4F1EA)

    /** Delikatne wypełnienie w kolorze akcentu, na zaznaczenie i podświetlenie. */
    val accentWash: Color get() = accent.copy(alpha = if (isDark) 0.18f else 0.10f)

    /** Domyślny kolor kreski pisanej rysikiem na nowej stronie. */
    val defaultInk: Color get() = if (isDark) Color(0xFFE8E4DA) else Color(0xFF23211D)

    /** Linie i kratka na tle strony. */
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

/**
 * Kolory folderów. Użytkownik wybiera jeden z nich przy tworzeniu folderu.
 * Każdy ma osobny odcień na motyw jasny i ciemny, żeby zachować czytelność.
 * Nie ma tu fioletu.
 */
enum class FolderColor(
    val id: String,
    val labelPl: String,
    private val light: Color,
    private val dark: Color,
) {
    Grafit("grafit", "Grafit", Color(0xFF5B584F), Color(0xFF8E8A7E)),
    Zielen("zielen", "Zieleń", Color(0xFF0F6B5C), Color(0xFF4FB39C)),
    Morski("morski", "Morski", Color(0xFF1C5C74), Color(0xFF5AA8C4)),
    Ceglany("ceglany", "Ceglany", Color(0xFFA6392E), Color(0xFFE2857A)),
    Musztarda("musztarda", "Musztardowy", Color(0xFF8A6212), Color(0xFFD6A648)),
    Oliwka("oliwka", "Oliwkowy", Color(0xFF56662A), Color(0xFF9EB367)),
    Rdza("rdza", "Rdzawy", Color(0xFF8F4A1C), Color(0xFFD08A55)),
    Bez("bez", "Bezbarwny", Color(0xFF8C877C), Color(0xFF6E6A61));

    fun color(isDark: Boolean): Color = if (isDark) dark else light

    companion object {
        fun fromId(id: String?): FolderColor = entries.firstOrNull { it.id == id } ?: Grafit
    }
}

/**
 * Kolory atramentu dostępne w pisaku. Osobne od palety interfejsu,
 * bo to jest treść notatki, a nie ozdoba ekranu.
 */
object InkPalette {
    val Czarny = Color(0xFF23211D)
    val Bialy = Color(0xFFF0EDE4)
    val Niebieski = Color(0xFF1B4F8C)
    val Czerwony = Color(0xFFB0322A)
    val Zielony = Color(0xFF1F6B3A)
    val Braz = Color(0xFF6B4A22)

    /** Kolory zakreślacza. Kładzione pod tekstem, więc bardzo jasne. */
    val ZakreslaczZolty = Color(0xFFF2D24B)
    val ZakreslaczZielony = Color(0xFF8FD07A)
    val ZakreslaczRozowy = Color(0xFFF29BAE)
    val ZakreslaczNiebieski = Color(0xFF87BEE8)

    val pisak = listOf(
        "Czarny" to Czarny,
        "Biały" to Bialy,
        "Niebieski" to Niebieski,
        "Czerwony" to Czerwony,
        "Zielony" to Zielony,
        "Brązowy" to Braz,
    )

    val zakreslacz = listOf(
        "Żółty" to ZakreslaczZolty,
        "Zielony" to ZakreslaczZielony,
        "Różowy" to ZakreslaczRozowy,
        "Niebieski" to ZakreslaczNiebieski,
    )
}
