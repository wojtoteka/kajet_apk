package wojtoteka.ovh.kajet.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import wojtoteka.ovh.kajet.core.calc.CalcKey
import wojtoteka.ovh.kajet.core.calc.Calculator
import wojtoteka.ovh.kajet.core.calc.CalculatorState
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.text.LocalStrings

/**
 * Przycisk kalkulatora dla pasków narzędzi edytorów. Kalkulator żyje nad
 * całym stosem ekranów, więc edytor nie musi go trzymać ani przekazywać -
 * wystarczy, że postawi ten przycisk. Poza zasięgiem [LocalCalculator]
 * (na przykład w podglądzie) przycisku po prostu nie ma.
 */
@Immutable
class CalculatorControl(val open: Boolean, val toggle: () -> Unit)

val LocalCalculator = compositionLocalOf<CalculatorControl?> { null }

@Composable
fun CalculatorAction(enabled: Boolean = true) {
    val control = LocalCalculator.current ?: return
    IconAction(
        icon = KajetIcons.Operations,
        description = LocalStrings.current.calculator,
        onClick = control.toggle,
        selected = control.open,
        enabled = enabled,
    )
}

private val PANEL_WIDTH = 288.dp
private val KEY_HEIGHT = 52.dp

private val KEYPAD = listOf(
    listOf(CalcKey.CLEAR, CalcKey.PARENS, CalcKey.PERCENT, CalcKey.DIVIDE),
    listOf(CalcKey.D7, CalcKey.D8, CalcKey.D9, CalcKey.TIMES),
    listOf(CalcKey.D4, CalcKey.D5, CalcKey.D6, CalcKey.MINUS),
    listOf(CalcKey.D1, CalcKey.D2, CalcKey.D3, CalcKey.PLUS),
    listOf(CalcKey.BACKSPACE, CalcKey.D0, CalcKey.DOT, CalcKey.EQUALS),
)

/*
  Pływający kalkulator.

  Nie jest oknem dialogowym: notatka pod spodem zostaje widoczna i dalej
  działa, więc liczby można przepisywać z kartki i na kartkę bez zamykania
  kalkulatora. Okienko przesuwa się za pasek z tytułem, a zamyka tylko
  krzyżykiem albo tym samym przyciskiem na pasku narzędzi - dotknięcie obok
  niego należy do notatki.

  Klawisze nie biorą skupienia. Inaczej każde stuknięcie w kalkulator
  zwijało klawiaturę i kursor w polu tekstu notatki.
*/
@Composable
fun CalculatorPanel(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val words = LocalStrings.current
    val colors = Kajet.colors
    val density = LocalDensity.current
    val clipboard = LocalClipboardManager.current

    var state by remember { mutableStateOf(CalculatorState()) }
    // Przesunięcie od prawego dolnego rogu, w pikselach. Przeżywa obrót ekranu.
    var dragX by rememberSaveable { mutableStateOf(0f) }
    var dragY by rememberSaveable { mutableStateOf(0f) }

    val decimal = if (words.english) '.' else ','
    fun shown(text: String) = text.replace('.', decimal)

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        val panelWidth = if (maxWidth < PANEL_WIDTH) maxWidth else PANEL_WIDTH
        val maxShiftX = with(density) { (maxWidth - panelWidth).toPx() }.coerceAtLeast(0f)
        // Wysokość okienka nie jest znana przed pomiarem; tyle wystarczy, by
        // pasek z tytułem nigdy nie uciekł nad górną krawędź.
        val maxShiftY = with(density) { (maxHeight - 120.dp).toPx() }.coerceAtLeast(0f)
        val x = dragX.coerceIn(-maxShiftX, 0f)
        val y = dragY.coerceIn(-maxShiftY, 0f)

        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .width(panelWidth)
                .shadow(8.dp, RoundedCornerShape(Kajet.dimens.corner))
                .clip(RoundedCornerShape(Kajet.dimens.corner))
                .background(colors.sheet)
                .border(1.dp, colors.line, RoundedCornerShape(Kajet.dimens.corner))
                // Stuknięcie między klawiszami nie może przejść do notatki pod spodem.
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.desk)
                    .pointerInput(maxShiftX, maxShiftY) {
                        detectDragGestures { change, amount ->
                            change.consume()
                            dragX = (dragX.coerceIn(-maxShiftX, 0f) + amount.x).coerceIn(-maxShiftX, 0f)
                            dragY = (dragY.coerceIn(-maxShiftY, 0f) + amount.y).coerceIn(-maxShiftY, 0f)
                        }
                    }
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = KajetIcons.Operations,
                    contentDescription = words.calculatorMove,
                    tint = colors.muted,
                    modifier = Modifier.width(18.dp),
                )
                Text(
                    words.calculator,
                    style = Kajet.type.titleSmall,
                    color = colors.text,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                val result = if (state.showsResult) state.expression else null
                IconAction(
                    icon = KajetIcons.Copy,
                    description = words.calculatorCopyResult,
                    onClick = {
                        val value = result ?: state.preview?.let(Calculator::format)
                        if (value != null) clipboard.setText(AnnotatedString(shown(value)))
                    },
                    enabled = result != null || state.preview != null,
                    iconSize = 18.dp,
                    touchTarget = 40.dp,
                )
                IconAction(
                    icon = KajetIcons.Close,
                    description = words.calculatorClose,
                    onClick = onClose,
                    iconSize = 18.dp,
                    touchTarget = 40.dp,
                )
            }
            HorizontalRule()

            // Wyświetlacz: działanie u góry, podgląd wyniku pod nim.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = shown(state.expression).ifEmpty { "0" },
                    style = Kajet.type.title.copy(fontSize = 28.sp, lineHeight = 34.sp),
                    color = colors.text,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                val preview = state.preview
                val hint = when {
                    state.error -> words.calculatorError
                    state.showsResult -> ""
                    preview != null && Calculator.format(preview) != state.expression ->
                        "= " + shown(Calculator.format(preview))
                    else -> ""
                }
                Text(
                    text = hint,
                    style = Kajet.type.body,
                    color = if (state.error) colors.danger else colors.muted,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                )
            }
            HorizontalRule()

            Column(
                Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KEYPAD.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { key ->
                            CalculatorKey(
                                key = key,
                                label = if (key == CalcKey.DOT) decimal.toString() else key.symbol,
                                description = when (key) {
                                    CalcKey.BACKSPACE -> words.calculatorBackspace
                                    CalcKey.CLEAR -> words.calculatorClear
                                    CalcKey.PARENS -> words.calculatorParens
                                    else -> null
                                },
                                onClick = { state = state.press(key) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalculatorKey(
    key: CalcKey,
    label: String,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Kajet.colors
    val operator = key in setOf(
        CalcKey.PLUS, CalcKey.MINUS, CalcKey.TIMES, CalcKey.DIVIDE,
        CalcKey.PERCENT, CalcKey.PARENS,
    )
    val background = when {
        key == CalcKey.EQUALS -> colors.accent
        operator -> colors.accentWash
        else -> colors.desk
    }
    val textColor = when {
        key == CalcKey.EQUALS -> colors.onAccent
        key == CalcKey.CLEAR -> colors.danger
        operator -> colors.accent
        else -> colors.text
    }
    Box(
        modifier
            .height(KEY_HEIGHT)
            .clip(RoundedCornerShape(Kajet.dimens.corner))
            .background(background)
            .focusProperties { canFocus = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = colors.accent),
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = Kajet.type.title.copy(fontSize = 22.sp),
            color = textColor,
        )
    }
}
