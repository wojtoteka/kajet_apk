package wojtoteka.ovh.kajet.editor

import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import wojtoteka.ovh.kajet.ink.PenHaptics

/**
 * Oznacza composable jako powierzchnię pisania rysikiem: pole tekstu, po
 * którym tablet zamienia kreski na litery. Rysik drga tylko nad takimi
 * powierzchniami (i nad kartką) - nad paskami narzędzi i menu jest cisza.
 *
 * Zdarzenia idą po torze Initial, żeby dzieci (pola tekstowe, przyciski)
 * nie zdążyły ich zabrać. Wjazd i zjazd liczy się względem CAŁEGO obszaru,
 * więc ruch pomiędzy blokami w środku niczego nie gasi.
 */
fun Modifier.penWritingSurface(context: Context): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            // Tylko rysik: palec nie drga, więc nie ma prawa przestawiać stanu.
            if (event.changes.none { it.type == PointerType.Stylus }) continue
            when (event.type) {
                // Press też znaczy „nad powierzchnią": system wysyła koniec
                // najechania tuż przed dotknięciem i bez tego pisanie po polu
                // zaczynałoby się bez drgania.
                PointerEventType.Enter, PointerEventType.Press ->
                    PenHaptics.surfaceHover(context, true)
                PointerEventType.Exit -> PenHaptics.surfaceHover(context, false)
                else -> Unit
            }
        }
    }
}

/**
 * Odwrotność [penWritingSurface]: miejsce, w którym rysik ma milczeć.
 *
 * Usługa Lenovo wznawia drganie przy każdym wjeździe rysika nad okno wpisanej
 * aplikacji i sama nie odróżnia kartki od paska narzędzi. Aktywność dogasza to
 * w `dispatchGenericMotionEvent`, ale okno dialogowe jest OSOBNYM oknem i te
 * zdarzenia do aktywności nie docierają - rysik drgał więc nad całym oknem
 * rysowania: nad przyciskami, nad podpisem, nad pustym miejscem obok karty.
 *
 * Zdarzenia idą po torze Initial, czyli przed dziećmi. Dzięki temu kartka
 * (`StrokeCanvas`) zdąży zgłosić się jako powierzchnia pisania PO tym, jak
 * rodzic wyciszy rysika, a nie odwrotnie.
 *
 * Liczy się samo najechanie i dotknięcie - ruchu nie ma po co obsługiwać,
 * bo [PenHaptics.surfaceHover] i tak odrzuca powtórkę po jednym porównaniu.
 */
fun Modifier.penQuietSurface(context: Context): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.none { it.type == PointerType.Stylus }) continue
            when (event.type) {
                PointerEventType.Enter, PointerEventType.Press ->
                    PenHaptics.surfaceHover(context, false)
                else -> Unit
            }
        }
    }
}
