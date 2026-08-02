package wojtoteka.ovh.kajet.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Odstępy i wymiary. Skala jest ciasna celowo, bo aplikacja ma być gęsta
 * jak zeszyt, a nie rozstrzelona jak strona reklamowa.
 */
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

    /** Szerokość pionowego paska marginesu, znaku rozpoznawczego aplikacji. */
    val railWidth: Dp = 56.dp,
    /** Odległość linii marginesu od lewej krawędzi kartki w edytorze notatki. */
    val pageMarginInset: Dp = 44.dp,
    /** Grubość włoskowatej linii. */
    val hairline: Dp = 1.dp,
    /** Najmniejszy cel dotykowy. */
    val minTouch: Dp = 48.dp,
    /** Największa szerokość akapitu, żeby wiersz nie robił się za długi na tablecie. */
    val readingWidth: Dp = 720.dp,
    /** Promień zaokrąglenia. Jeden na całą aplikację, mały. */
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
