package wojtoteka.ovh.kajet.core.design

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import wojtoteka.ovh.kajet.core.R

/**
 * Dwa kroje pisma o wyraźnie różnym charakterze plus krój o stałej szerokości znaku.
 *
 * Archivo, wąska grotesk, trzyma nagłówki i krótkie etykiety. Jest zbudowana
 * na duże rozmiary i przy 30 sp ma widoczny charakter.
 * IBM Plex Sans niesie treść. Jest humanistyczna, więc czyta się ją długo bez zmęczenia.
 * IBM Plex Mono pojawia się tylko w edytorze kodu i w blokach kodu.
 *
 * Archivo i Plex Sans to kroje zmienne, więc jeden plik obsługuje wszystkie grubości.
 * Compose ustawia oś wagi sam, na podstawie podanego FontWeight, od Androida 8.0.
 */
val Archivo = FontFamily(
    Font(R.font.archivo, FontWeight.Normal),
    Font(R.font.archivo, FontWeight.Medium),
    Font(R.font.archivo, FontWeight.SemiBold),
    Font(R.font.archivo, FontWeight.Bold),
)

val PlexSans = FontFamily(
    Font(R.font.plex_sans, FontWeight.Light),
    Font(R.font.plex_sans, FontWeight.Normal),
    Font(R.font.plex_sans, FontWeight.Medium),
    Font(R.font.plex_sans, FontWeight.SemiBold),
)

val PlexMono = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_bold, FontWeight.Bold),
)

private val noExtraPadding = PlatformTextStyle(includeFontPadding = false)

private val trimmedLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    family: FontFamily,
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: Float = 0f,
) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = letterSpacing.sp,
    platformStyle = noExtraPadding,
    lineHeightStyle = trimmedLineHeight,
)

/**
 * Style nazwane po tym, do czego służą, a nie po wielkości.
 * Interlinia w tekście ciągłym wynosi 1.6 wielkości pisma.
 */
@Immutable
data class KajetTypography(
    /** Nazwa ekranu, jedna na widok. */
    val display: TextStyle = style(Archivo, 30, 36, FontWeight.SemiBold, -0.4f),
    /** Nagłówek sekcji albo tytuł notatki na liście. */
    val title: TextStyle = style(Archivo, 21, 27, FontWeight.SemiBold, -0.2f),
    /** Tytuł mniejszy, na paskach narzędzi i w oknach. */
    val titleSmall: TextStyle = style(Archivo, 16, 21, FontWeight.SemiBold),
    /** Wersalikowa etykieta przy linii marginesu. Używana rzadko. */
    val eyebrow: TextStyle = style(Archivo, 11, 14, FontWeight.SemiBold, 1.4f),
    /** Dłuższy tekst, akapity w notatkach. */
    val bodyLarge: TextStyle = style(PlexSans, 17, 28),
    /** Tekst w interfejsie. */
    val body: TextStyle = style(PlexSans, 15, 24),
    /** Etykiety przycisków i pól. */
    val label: TextStyle = style(PlexSans, 13, 18, FontWeight.Medium),
    /** Daty, liczby stron, podpisy pod treścią. */
    val meta: TextStyle = style(PlexSans, 12, 16),
    /** Kod źródłowy i wynik programu. */
    val code: TextStyle = style(PlexMono, 14, 22),
    /** Numery linii w rynnie edytora kodu. */
    val codeGutter: TextStyle = style(PlexMono, 12, 22),
)

val LocalKajetTypography = staticCompositionLocalOf { KajetTypography() }

/** Odwzorowanie na typografię Material 3, żeby gotowe elementy też trzymały krój. */
internal fun KajetTypography.toMaterial(): Typography = Typography(
    displayLarge = display,
    displayMedium = display,
    displaySmall = title,
    headlineLarge = title,
    headlineMedium = title,
    headlineSmall = titleSmall,
    titleLarge = title,
    titleMedium = titleSmall,
    titleSmall = label,
    bodyLarge = bodyLarge,
    bodyMedium = body,
    bodySmall = meta,
    labelLarge = label,
    labelMedium = label,
    labelSmall = eyebrow,
)
