package wojtoteka.ovh.kajet.core.design

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.geometry.Offset
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PressRippleTest {

    @Test
    fun hoverAndFocusNeverReachTheRipple() = runTest(UnconfinedTestDispatcher()) {
        val source = MutableInteractionSource()
        val seen = mutableListOf<Interaction>()
        val job = launch { pressOnly(source).interactions.collect { seen += it } }

        val hover = HoverInteraction.Enter()
        val press = PressInteraction.Press(Offset.Zero)
        source.emit(hover)
        source.emit(FocusInteraction.Focus())
        source.emit(press)
        source.emit(PressInteraction.Release(press))
        // Rysik odjechał bez „końca najechania" - nic nie może zostać zapalone.

        assertThat(seen.map { it::class }).containsExactly(
            PressInteraction.Press::class,
            PressInteraction.Release::class,
        ).inOrder()
        job.cancel()
    }
}
