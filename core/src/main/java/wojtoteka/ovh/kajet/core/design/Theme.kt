package wojtoteka.ovh.kajet.core.design

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Motyw aplikacji. Nie korzysta z kolorów dynamicznych Androida,
 * bo wtedy tablet sam wybrałby paletę, a ta jest wybrana świadomie.
 */
@Composable
fun KajetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) KajetDarkColors else KajetLightColors
    val typography = remember { KajetTypography() }
    val dimens = remember { KajetDimens() }

    val context = LocalContext.current
    val animationsEnabled = remember(context) { systemAnimationsEnabled(context) }

    CompositionLocalProvider(
        LocalKajetColors provides colors,
        LocalKajetTypography provides typography,
        LocalKajetDimens provides dimens,
        LocalAnimationsEnabled provides animationsEnabled,
        LocalContentColor provides colors.text,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(),
            typography = typography.toMaterial(),
            shapes = KajetShapes,
        ) {
            ProvideTextStyle(typography.body) {
                content()
            }
        }
    }
}

/** Skrót do kolorów, typografii i wymiarów bez pisania LocalXxx.current za każdym razem. */
object Kajet {
    val colors: KajetColors
        @Composable @ReadOnlyComposable get() = LocalKajetColors.current

    val type: KajetTypography
        @Composable @ReadOnlyComposable get() = LocalKajetTypography.current

    val dimens: KajetDimens
        @Composable @ReadOnlyComposable get() = LocalKajetDimens.current
}

/**
 * Fałsz, kiedy w ustawieniach systemu wyłączono animacje.
 * Sprawdzamy raz przy budowie motywu, bo zmiana wymaga ponownego wejścia do aplikacji.
 */
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

private fun systemAnimationsEnabled(context: android.content.Context): Boolean {
    val scale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
    return scale > 0f
}

/**
 * Material 3 dostaje te same kolory, żeby pola tekstowe, okna i przełączniki
 * nie wprowadzały własnych odcieni.
 */
private fun KajetColors.toMaterialScheme() = if (isDark) {
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentWash,
        onPrimaryContainer = text,
        secondary = muted,
        onSecondary = sheet,
        background = desk,
        onBackground = text,
        surface = sheet,
        onSurface = text,
        surfaceVariant = desk,
        onSurfaceVariant = muted,
        surfaceContainer = sheet,
        surfaceContainerHigh = sheet,
        surfaceContainerHighest = sheet,
        surfaceContainerLow = desk,
        surfaceContainerLowest = desk,
        outline = line,
        outlineVariant = line,
        error = danger,
        onError = desk,
        errorContainer = danger.copy(alpha = 0.18f),
        onErrorContainer = text,
        scrim = desk.copy(alpha = 0.7f),
    )
} else {
    lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentWash,
        onPrimaryContainer = text,
        secondary = muted,
        onSecondary = sheet,
        background = desk,
        onBackground = text,
        surface = sheet,
        onSurface = text,
        surfaceVariant = desk,
        onSurfaceVariant = muted,
        surfaceContainer = sheet,
        surfaceContainerHigh = sheet,
        surfaceContainerHighest = sheet,
        surfaceContainerLow = desk,
        surfaceContainerLowest = desk,
        outline = line,
        outlineVariant = line,
        error = danger,
        onError = sheet,
        errorContainer = danger.copy(alpha = 0.12f),
        onErrorContainer = text,
        scrim = Color00,
    )
}

private val Color00 = androidx.compose.ui.graphics.Color(0x8823211D)
