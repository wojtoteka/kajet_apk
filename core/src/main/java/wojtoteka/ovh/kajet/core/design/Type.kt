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

@Immutable
data class KajetTypography(
    val display: TextStyle = style(Archivo, 30, 36, FontWeight.SemiBold, -0.4f),
    val title: TextStyle = style(Archivo, 21, 27, FontWeight.SemiBold, -0.2f),
    val titleSmall: TextStyle = style(Archivo, 16, 21, FontWeight.SemiBold),
    val eyebrow: TextStyle = style(Archivo, 11, 14, FontWeight.SemiBold, 1.4f),
    val bodyLarge: TextStyle = style(PlexSans, 17, 28),
    val body: TextStyle = style(PlexSans, 15, 24),
    val label: TextStyle = style(PlexSans, 13, 18, FontWeight.Medium),
    val meta: TextStyle = style(PlexSans, 12, 16),
    val code: TextStyle = style(PlexMono, 14, 22),
    val codeGutter: TextStyle = style(PlexMono, 12, 22),
)

val LocalKajetTypography = staticCompositionLocalOf { KajetTypography() }

fun fontFamilyFor(typeface: wojtoteka.ovh.kajet.core.model.NoteFont): FontFamily =
    when (typeface) {
        wojtoteka.ovh.kajet.core.model.NoteFont.HEADING -> Archivo
        wojtoteka.ovh.kajet.core.model.NoteFont.BODY -> PlexSans
        wojtoteka.ovh.kajet.core.model.NoteFont.MONO -> PlexMono
    }

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
