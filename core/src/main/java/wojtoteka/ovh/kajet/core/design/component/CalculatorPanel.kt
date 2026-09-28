package wojtoteka.ovh.kajet.core.design.component

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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

/*
  Wielkość kalkulatora.

  Domyślna to ta, która była od początku (skala 1). Człowiek zmienia ją
  minusem i plusem na pasku albo ciągnąc uchwyt w lewym górnym rogu - okienko
  stoi przy prawym dolnym rogu, więc rośnie w górę i w lewo, tam gdzie się
  ciągnie. Pasek z tytułem ma stałą wielkość, żeby przyciski dało się trafić
  także w najmniejszym kalkulatorze; skaluje się wyświetlacz i klawisze.

  Wybrana wielkość zostaje zapamiętana na urządzeniu, więc następne otwarcie
  kalkulatora - w dowolnej notatce - ma już tę samą.
*/
private const val SCALE_MIN = 0.85f
private const val SCALE_MAX = 1.8f
private const val SCALE_STEP = 0.1f
private const val PREFS = "kajet_kalkulator"
private const val PREF_SCALE = "skala"

/** Wysokość okienka w skali 1, rozbita na część stałą i skalowaną. */
private val FIXED_HEIGHT = 126.dp
private val SCALED_HEIGHT = 294.dp

private val KEYPAD = listOf(
    listOf(CalcKey.CLEAR, CalcKey.ROOT, CalcKey.PARENS, CalcKey.DIVIDE),
    listOf(CalcKey.D7, CalcKey.D8, CalcKey.D9, CalcKey.TIMES),
    listOf(CalcKey.D4, CalcKey.D5, CalcKey.D6, CalcKey.MINUS),
    listOf(CalcKey.D1, CalcKey.D2, CalcKey.D3, CalcKey.PLUS),
    listOf(CalcKey.PERCENT, CalcKey.D0, CalcKey.DOT, CalcKey.EQUALS),
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
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    var state by remember { mutableStateOf(CalculatorState()) }
    // Przesunięcie od prawego dolnego rogu, w pikselach. Przeżywa obrót ekranu.
    var dragX by rememberSaveable { mutableStateOf(0f) }
    var dragY by rememberSaveable { mutableStateOf(0f) }
    var scale by remember {
        mutableFloatStateOf(prefs.getFloat(PREF_SCALE, 1f).coerceIn(SCALE_MIN, SCALE_MAX))
    }
    fun keepScale(next: Float) {
        scale = next.coerceIn(SCALE_MIN, SCALE_MAX)
        prefs.edit().putFloat(PREF_SCALE, scale).apply()
    }

    val decimal = if (words.english) '.' else ','
    fun shown(text: String) = text.replace('.', decimal)

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        // Zapamiętana wielkość może nie zmieścić się na mniejszym ekranie albo
        // po obrocie - wtedy okienko jest tak duże, jak się da, a zapamiętana
        // wartość zostaje na później.
        val fitsWidth = maxWidth / PANEL_WIDTH
        val fitsHeight = (maxHeight - FIXED_HEIGHT) / SCALED_HEIGHT
        val shownScale = minOf(scale, fitsWidth, fitsHeight).coerceAtLeast(0.6f)
        val panelWidth = minOf(PANEL_WIDTH * shownScale, maxWidth)
        val maxShiftX = with(density) { (maxWidth - panelWidth).toPx() }.coerceAtLeast(0f)
        // Wysokość okienka nie jest znana przed pomiarem; tyle wystarczy, by
        // pasek z tytułem nigdy nie uciekł nad górną krawędź.
        val maxShiftY = with(density) { (maxHeight - 120.dp).toPx() }.coerceAtLeast(0f)
        val x = dragX.coerceIn(-maxShiftX, 0f)
        val y = dragY.coerceIn(-maxShiftY, 0f)
        val basePx = with(density) { PANEL_WIDTH.toPx() }

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
                    .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Uchwyt wielkości: ciągnięty w górę i w lewo powiększa.
                ResizeGrip(
                    description = words.calculatorResize,
                    onDrag = { dx, dy -> scale = (scale + (-dx - dy) / 2f / basePx).coerceIn(SCALE_MIN, SCALE_MAX) },
                    onDragEnd = { keepScale(scale) },
                )
                // W małym kalkulatorze napis nie ma gdzie stanąć obok pięciu
                // przycisków, więc zostaje samo puste miejsce do chwytania.
                Box(Modifier.weight(1f)) {
                    if (panelWidth >= 280.dp) {
                        Text(
                            words.calculator,
                            style = Kajet.type.titleSmall,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconAction(
                    icon = KajetIcons.Minus,
                    description = words.calculatorSmaller,
                    onClick = { keepScale(shownScale - SCALE_STEP) },
                    enabled = shownScale > SCALE_MIN + 0.001f,
                    iconSize = 18.dp,
                    touchTarget = 40.dp,
                )
                IconAction(
                    icon = KajetIcons.Plus,
                    description = words.calculatorBigger,
                    onClick = { keepScale(shownScale + SCALE_STEP) },
                    enabled = scale < SCALE_MAX - 0.001f && shownScale >= scale - 0.001f,
                    iconSize = 18.dp,
                    touchTarget = 40.dp,
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

            // Wyświetlacz: działanie u góry, podgląd wyniku pod nim, a obok
            // działania kasowanie ostatniego znaku - jak w kalkulatorze telefonu.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = shown(state.expression).ifEmpty { "0" },
                        style = Kajet.type.title.copy(
                            fontSize = (28 * shownScale).sp,
                            lineHeight = (34 * shownScale).sp,
                        ),
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
                        style = Kajet.type.body.copy(
                            fontSize = (15 * shownScale).sp,
                            lineHeight = (24 * shownScale).sp,
                        ),
                        color = if (state.error) colors.danger else colors.muted,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.StartEllipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp * shownScale),
                    )
                }
                Box(
                    Modifier
                        .padding(start = 4.dp)
                        .size(44.dp * shownScale.coerceAtLeast(1f))
                        .clip(RoundedCornerShape(Kajet.dimens.corner))
                        .focusProperties { canFocus = false }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = colors.accent),
                            role = Role.Button,
                            onClickLabel = words.calculatorBackspace,
                            onClick = { state = state.press(CalcKey.BACKSPACE) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        CalcKey.BACKSPACE.symbol,
                        style = Kajet.type.title.copy(fontSize = (22 * shownScale).sp),
                        color = colors.muted,
                    )
                }
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
                                    CalcKey.CLEAR -> words.calculatorClear
                                    CalcKey.PARENS -> words.calculatorParens
                                    CalcKey.ROOT -> words.calculatorRoot
                                    else -> null
                                },
                                scale = shownScale,
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

/** Trzy ukośne kreski w rogu - znany z okien znak „tu się ciągnie". */
@Composable
private fun ResizeGrip(
    description: String,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val color = Kajet.colors.muted
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    Box(
        Modifier
            .size(40.dp)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = { end() }, onDragCancel = { end() }) { change, amount ->
                    change.consume()
                    drag(amount.x, amount.y)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(14.dp)) {
            val w = size.width
            val stroke = 1.5.dp.toPx()
            for (i in 1..3) {
                val t = w * i / 3f
                drawLine(color, Offset(0f, t), Offset(t, 0f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun CalculatorKey(
    key: CalcKey,
    label: String,
    description: String?,
    scale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Kajet.colors
    val operator = key in setOf(
        CalcKey.PLUS, CalcKey.MINUS, CalcKey.TIMES, CalcKey.DIVIDE,
        CalcKey.PERCENT, CalcKey.PARENS, CalcKey.ROOT,
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
            .height(KEY_HEIGHT * scale)
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
            style = Kajet.type.title.copy(fontSize = (22 * scale).sp),
            color = textColor,
        )
    }
}
