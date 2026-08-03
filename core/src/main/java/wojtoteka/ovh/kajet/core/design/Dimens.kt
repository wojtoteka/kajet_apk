package wojtoteka.ovh.kajet.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class KajetDimens(
    val space1: Dp = 2.dp,
    val space2: Dp = 4.dp,
    val space3: Dp = 8.dp,
    val space4: Dp = 12.dp,
    val space5: Dp = 16.dp,
    val space6: Dp = 24.dp,
    val space7: Dp = 32.dp,
    val space8: Dp = 48.dp,

    val railWidth: Dp = 56.dp,
    val pageMarginInset: Dp = 44.dp,
    val hairline: Dp = 1.dp,
    val minTouch: Dp = 48.dp,
    val readingWidth: Dp = 720.dp,
    val corner: Dp = 3.dp,
)

val LocalKajetDimens = staticCompositionLocalOf { KajetDimens() }

internal val KajetShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(3.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(4.dp),
)
