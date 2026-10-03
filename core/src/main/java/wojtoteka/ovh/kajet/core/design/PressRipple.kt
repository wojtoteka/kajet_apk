package wojtoteka.ovh.kajet.core.design

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.ripple
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.DelegatableNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter

/*
  Fala pod palcem - i nic poza nią.

  Zwykła fala z Material maluje też stan „najechany" i „ze skupieniem": tę samą
  zieloną plamę, którą Kajet pokazuje przy włączonym narzędziu. Na tablecie
  z rysikiem najechanie zdarza się co chwilę - rysik wisi nad paskiem, zanim
  dotknie - a koniec najechania potrafi nie dojść, kiedy rysik odjedzie
  w bok albo za daleko od ekranu. Przycisk zostawał wtedy podświetlony, jakby
  był wybrany, choć nie był (najczęściej w kalkulatorze, bo tam klawisze stoją
  gęsto pod ręką). Ta fala widzi tylko naciśnięcie.
*/
class PressRipple(private val inner: IndicationNodeFactory) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        inner.create(pressOnly(interactionSource))

    override fun equals(other: Any?): Boolean = other is PressRipple && other.inner == inner

    override fun hashCode(): Int = inner.hashCode()
}

/** Te same zdarzenia co [source], bez najechania i skupienia. */
internal fun pressOnly(source: InteractionSource): InteractionSource = object : InteractionSource {
    override val interactions: Flow<Interaction> =
        source.interactions.filter { it !is HoverInteraction && it !is FocusInteraction }
}

/** Fala w barwie [color] (domyślnie z motywu), bez stanów najechania i skupienia. */
fun pressRipple(color: Color = Color.Unspecified): IndicationNodeFactory =
    PressRipple(ripple(color = color))
