package wojtoteka.ovh.kajet.core.design.component

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import wojtoteka.ovh.kajet.core.calc.CalcKey
import wojtoteka.ovh.kajet.core.calc.Calculator
import wojtoteka.ovh.kajet.core.calc.CalculatorDisplay
import wojtoteka.ovh.kajet.core.calc.CalculatorState
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.design.pressRipple
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

private val PANEL_WIDTH = 320.dp
private val KEY_HEIGHT = 56.dp
private val SCIENTIFIC_KEY_HEIGHT = 42.dp
private val KEY_GAP = 6.dp

/*
  Wielkość kalkulatora.

  Człowiek zmienia ją minusem i plusem na pasku albo ciągnąc uchwyt w lewym
  górnym rogu - okienko stoi przy prawym dolnym rogu, więc rośnie w górę
  i w lewo, tam gdzie się ciągnie. Pasek z tytułem i pasek narzędzi pod
  wyświetlaczem mają stałą wielkość, żeby przyciski dało się trafić także
  w najmniejszym kalkulatorze; skaluje się wyświetlacz i klawisze.

  Wybrana wielkość, funkcje naukowe, stopnie albo radiany i historia działań
  zostają zapamiętane na urządzeniu - następne otwarcie kalkulatora,
  w dowolnej notatce, wygląda tak samo jak ostatnie.
*/
private const val SCALE_MIN = 0.75f
private const val SCALE_MAX = 1.8f
private const val SCALE_STEP = 0.1f
private const val PREFS = "kajet_kalkulator"
private const val PREF_SCALE = "skala"
private const val PREF_SCIENTIFIC = "naukowy"
private const val PREF_DEGREES = "stopnie"
private const val PREF_HISTORY = "historia"
private const val HISTORY_SIZE = 40

/** Wysokość okienka w skali 1, rozbita na część stałą i skalowaną. */
private val FIXED_HEIGHT = 112.dp
private val SCALED_HEIGHT = 414.dp
private val SCIENTIFIC_HEIGHT = SCIENTIFIC_KEY_HEIGHT * 4 + KEY_GAP * 4

/*
  Klawiatura jak w kalkulatorze telefonu: C, nawias, procent i dzielenie
  u góry, cyfry na środku, zmiana znaku obok zera i „=" w prawym dolnym rogu.
  Kasowanie ostatniego znaku stoi nad klawiaturą, przy wyświetlaczu.
*/
private val KEYPAD = listOf(
    listOf(CalcKey.CLEAR, CalcKey.PARENS, CalcKey.PERCENT, CalcKey.DIVIDE),
    listOf(CalcKey.D7, CalcKey.D8, CalcKey.D9, CalcKey.TIMES),
    listOf(CalcKey.D4, CalcKey.D5, CalcKey.D6, CalcKey.MINUS),
    listOf(CalcKey.D1, CalcKey.D2, CalcKey.D3, CalcKey.PLUS),
    listOf(CalcKey.SIGN, CalcKey.D0, CalcKey.DOT, CalcKey.EQUALS),
)

/** Klawisz funkcji naukowych: zwykły i ten, który stoi na nim po „INV". */
private sealed interface ScienceButton

private data class ScienceKey(val key: CalcKey, val inverse: CalcKey = key) : ScienceButton

private data object InverseToggle : ScienceButton

private data object AngleToggle : ScienceButton

/*
  Funkcje naukowe - wysuwane nad zwykłą klawiaturą, jak w telefonie po
  przekręceniu go bokiem. „INV" zamienia sinus na arcus sinus, ln na eˣ,
  log na 10ˣ, pierwiastek na sześcienny, a kwadrat na sześcian.
*/
private val SCIENCE_PAD = listOf(
    listOf(InverseToggle, AngleToggle, ScienceKey(CalcKey.OPEN), ScienceKey(CalcKey.CLOSE)),
    listOf(
        ScienceKey(CalcKey.SIN, CalcKey.ASIN),
        ScienceKey(CalcKey.COS, CalcKey.ACOS),
        ScienceKey(CalcKey.TAN, CalcKey.ATAN),
        ScienceKey(CalcKey.PI),
    ),
    listOf(
        ScienceKey(CalcKey.LN, CalcKey.EXP),
        ScienceKey(CalcKey.LOG, CalcKey.TEN_POWER),
        ScienceKey(CalcKey.POWER),
        ScienceKey(CalcKey.E),
    ),
    listOf(
        ScienceKey(CalcKey.ROOT, CalcKey.CUBE_ROOT),
        ScienceKey(CalcKey.SQUARE, CalcKey.CUBE),
        ScienceKey(CalcKey.FACTORIAL),
        ScienceKey(CalcKey.INVERSE),
    ),
)

/** Jedno policzone działanie w historii. */
private data class HistoryEntry(val expression: String, val result: String)

private fun readHistory(prefs: SharedPreferences): List<HistoryEntry> =
    prefs.getString(PREF_HISTORY, "").orEmpty()
        .split('\n')
        .mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                HistoryEntry(parts[0], parts[1])
            } else {
                null
            }
        }

private fun writeHistory(prefs: SharedPreferences, history: List<HistoryEntry>) {
    prefs.edit()
        .putString(PREF_HISTORY, history.joinToString("\n") { "${it.expression}\t${it.result}" })
        .apply()
}

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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalculatorPanel(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val words = LocalStrings.current
    val colors = Kajet.colors
    val density = LocalDensity.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    var state by remember { mutableStateOf(CalculatorState(degrees = prefs.getBoolean(PREF_DEGREES, true))) }
    var scientific by remember { mutableStateOf(prefs.getBoolean(PREF_SCIENTIFIC, false)) }
    var inverse by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(readHistory(prefs)) }
    var historyOpen by remember { mutableStateOf(false) }
    // Przesunięcie od prawego dolnego rogu, w pikselach. Przeżywa obrót ekranu.
    var dragX by rememberSaveable { mutableStateOf(0f) }
    var dragY by rememberSaveable { mutableStateOf(0f) }
    // Zmierzona wysokość okienka; 0, dopóki nie zostało pierwszy raz ułożone.
    var panelHeightPx by remember { mutableIntStateOf(0) }
    var scale by remember {
        mutableFloatStateOf(prefs.getFloat(PREF_SCALE, 1f).coerceIn(SCALE_MIN, SCALE_MAX))
    }
    fun keepScale(next: Float) {
        scale = next.coerceIn(SCALE_MIN, SCALE_MAX)
        prefs.edit().putFloat(PREF_SCALE, scale).apply()
    }

    /** Klawisz kalkulatora; „=" z wynikiem trafia do historii. */
    fun press(key: CalcKey) {
        val next = state.press(key)
        val counted = key == CalcKey.EQUALS && next.showsResult && !state.showsResult
        if (counted && next.previous != next.expression) {
            history = (history + HistoryEntry(next.previous, next.expression)).takeLast(HISTORY_SIZE)
            writeHistory(prefs, history)
        }
        state = next
    }

    val decimal = if (words.english) '.' else ','
    // Odstęp tysięcy nierozdzielający, żeby liczba nie łamała się w pół.
    val group = if (words.english) ',' else ' '
    fun shown(text: String) = CalculatorDisplay.shown(text, decimal, group)
    fun copied(text: String) = text.replace('.', decimal)

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        // Zapamiętana wielkość może nie zmieścić się na mniejszym ekranie albo
        // po obrocie - wtedy okienko jest tak duże, jak się da, a zapamiętana
        // wartość zostaje na później.
        val scaledHeight = SCALED_HEIGHT + if (scientific) SCIENTIFIC_HEIGHT else 0.dp
        val fitsWidth = maxWidth / PANEL_WIDTH
        val fitsHeight = (maxHeight - FIXED_HEIGHT) / scaledHeight
        val shownScale = minOf(scale, fitsWidth, fitsHeight).coerceAtLeast(0.5f)
        val panelWidth = minOf(PANEL_WIDTH * shownScale, maxWidth)
        val maxShiftX = with(density) { (maxWidth - panelWidth).toPx() }.coerceAtLeast(0f)
        // Okienko stoi dołem przy dolnej krawędzi, więc w górę może pojechać
        // najwyżej o tyle, ile zostaje nad nim - inaczej pasek z tytułem,
        // a z nim uchwyt i krzyżyk, uciekał nad ekran i nie dało się już
        // okienka ani przesunąć, ani zamknąć. Przed pierwszym pomiarem
        // wysokość jest liczona z tych samych stałych, z których rośnie.
        val panelHeight = with(density) {
            if (panelHeightPx > 0) panelHeightPx.toFloat()
            else (FIXED_HEIGHT + scaledHeight * shownScale).toPx()
        }
        val maxShiftY = (with(density) { maxHeight.toPx() } - panelHeight).coerceAtLeast(0f)
        val x = dragX.coerceIn(-maxShiftX, 0f)
        val y = dragY.coerceIn(-maxShiftY, 0f)
        val basePx = with(density) { PANEL_WIDTH.toPx() }

        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .width(panelWidth)
                .onSizeChanged { panelHeightPx = it.height }
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
                    if (panelWidth >= 300.dp) {
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
                        if (value != null) clipboard.setText(AnnotatedString(copied(value)))
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

            Display(
                state = state,
                scale = shownScale,
                shown = ::shown,
                // Stuknięcie w działanie nad wynikiem wraca do niego - można
                // poprawić jedną liczbę zamiast wpisywać wszystko od nowa.
                onPrevious = { state = state.withExpression(state.previous) },
            )

            // Pasek narzędzi jak w telefonie: historia, funkcje naukowe
            // i kasowanie ostatniego znaku (przytrzymane czyści wszystko).
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(
                    icon = KajetIcons.Clock,
                    description = words.calculatorHistory,
                    onClick = { historyOpen = !historyOpen },
                    selected = historyOpen,
                    iconSize = 20.dp,
                    touchTarget = 44.dp,
                )
                ToolChip(
                    label = "f(x)",
                    description = words.calculatorScientific,
                    selected = scientific,
                    onClick = {
                        scientific = !scientific
                        prefs.edit().putBoolean(PREF_SCIENTIFIC, scientific).apply()
                    },
                )
                Box(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(width = 56.dp, height = 44.dp)
                        .clip(RoundedCornerShape(Kajet.dimens.corner))
                        .focusProperties { canFocus = false }
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = pressRipple(colors.accent),
                            role = Role.Button,
                            onClickLabel = words.calculatorBackspace,
                            onLongClickLabel = words.calculatorClearAll,
                            onLongClick = { press(CalcKey.CLEAR) },
                            onClick = { press(CalcKey.BACKSPACE) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        CalcKey.BACKSPACE.symbol,
                        style = Kajet.type.title.copy(fontSize = 22.sp),
                        color = colors.accent,
                    )
                }
            }
            HorizontalRule()

            // Historia wchodzi w miejsce klawiatury i ma jej wielkość, żeby
            // okienko nie skakało przy przełączaniu.
            Box {
                Column(
                    Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(KEY_GAP),
                ) {
                    if (scientific) {
                        SCIENCE_PAD.forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                                row.forEach { button ->
                                    ScienceKeyButton(
                                        button = button,
                                        inverse = inverse,
                                        degrees = state.degrees,
                                        scale = shownScale,
                                        onKey = ::press,
                                        onInverse = { inverse = !inverse },
                                        onAngle = {
                                            state = state.toggleDegrees()
                                            prefs.edit().putBoolean(PREF_DEGREES, state.degrees).apply()
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                    KEYPAD.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                            row.forEach { key ->
                                CalculatorKey(
                                    key = key,
                                    label = when (key) {
                                        CalcKey.DOT -> decimal.toString()
                                        CalcKey.SIGN -> "+/−"
                                        else -> key.symbol
                                    },
                                    description = when (key) {
                                        CalcKey.CLEAR -> words.calculatorClear
                                        CalcKey.PARENS -> words.calculatorParens
                                        CalcKey.SIGN -> words.calculatorSign
                                        else -> null
                                    },
                                    scale = shownScale,
                                    onClick = { press(key) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                if (historyOpen) {
                    HistoryList(
                        history = history,
                        shown = ::shown,
                        onResult = { entry ->
                            state = state.withNumber(entry.result)
                            historyOpen = false
                        },
                        onExpression = { entry ->
                            state = state.withExpression(entry.expression)
                            historyOpen = false
                        },
                        onClear = {
                            history = emptyList()
                            writeHistory(prefs, history)
                        },
                        modifier = Modifier
                            .matchParentSize()
                            .background(colors.sheet),
                    )
                }
            }
        }
    }
}

/**
 * Wyświetlacz: nad wynikiem działanie, z którego wyszedł, pod spodem duże
 * działanie albo wynik, a niżej podgląd wyniku w trakcie pisania - jak
 * w kalkulatorze telefonu. Długie działanie zmniejsza pismo, zanim zacznie
 * uciekać za lewą krawędź.
 */
@Composable
private fun Display(
    state: CalculatorState,
    scale: Float,
    shown: (String) -> String,
    onPrevious: () -> Unit,
) {
    val colors = Kajet.colors
    val words = LocalStrings.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.End,
    ) {
        val previous = if (state.showsResult && state.previous.isNotEmpty()) shown(state.previous) + " =" else ""
        Text(
            text = previous,
            style = Kajet.type.body.copy(fontSize = (15 * scale).sp, lineHeight = (20 * scale).sp),
            color = colors.muted,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.StartEllipsis,
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp * scale)
                .then(
                    if (previous.isNotEmpty()) {
                        Modifier
                            .focusProperties { canFocus = false }
                            .clickable(role = Role.Button, onClick = onPrevious)
                    } else {
                        Modifier
                    },
                ),
        )
        val expression = shown(state.expression).ifEmpty { "0" }
        // Do jedenastu znaków pełna wielkość, dalej pismo maleje - najwyżej
        // do połowy, potem działanie ucieka za lewą krawędź.
        val fit = if (expression.length <= 11) 1f else (11f / expression.length).coerceAtLeast(0.5f)
        Text(
            text = expression,
            style = Kajet.type.display.copy(
                fontSize = (34 * scale * fit).sp,
                lineHeight = (42 * scale).sp,
            ),
            color = if (state.error) colors.danger else colors.text,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.StartEllipsis,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp * scale),
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
                fontSize = (17 * scale).sp,
                lineHeight = (24 * scale).sp,
            ),
            color = if (state.error) colors.danger else colors.muted,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.StartEllipsis,
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp * scale),
        )
    }
}

/** Lista policzonych działań - najnowsze na dole, tuż nad klawiaturą. */
@Composable
private fun HistoryList(
    history: List<HistoryEntry>,
    shown: (String) -> String,
    onResult: (HistoryEntry) -> Unit,
    onExpression: (HistoryEntry) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Kajet.colors
    val words = LocalStrings.current
    Column(modifier) {
        if (history.isEmpty()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    words.calculatorHistoryEmpty,
                    style = Kajet.type.body,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                reverseLayout = true,
            ) {
                items(history.asReversed()) { entry ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        // Działanie wraca do poprawiania, wynik - do liczenia dalej.
                        Text(
                            shown(entry.expression),
                            style = Kajet.type.body,
                            color = colors.muted,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .focusProperties { canFocus = false }
                                .clickable(role = Role.Button) { onExpression(entry) },
                        )
                        Text(
                            "= " + shown(entry.result),
                            style = Kajet.type.title,
                            color = colors.text,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .focusProperties { canFocus = false }
                                .clickable(role = Role.Button) { onResult(entry) },
                        )
                    }
                    HorizontalRule(insetFromStart = 16.dp)
                }
            }
        }
        HorizontalRule()
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .focusProperties { canFocus = false }
                .clickable(enabled = history.isNotEmpty(), role = Role.Button, onClick = onClear),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                words.calculatorHistoryClear,
                style = Kajet.type.label,
                color = if (history.isNotEmpty()) colors.danger else colors.muted,
            )
        }
    }
}

/** Mały przełącznik z napisem na pasku pod wyświetlaczem. */
@Composable
private fun ToolChip(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = Kajet.colors
    Box(
        Modifier
            .padding(start = 4.dp)
            .size(width = 52.dp, height = 36.dp)
            .clip(RoundedCornerShape(Kajet.dimens.corner))
            .background(if (selected) colors.accentWash else Color.Transparent)
            .focusProperties { canFocus = false }
            .semantics { contentDescription = description }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = pressRipple(colors.accent),
                role = Role.Switch,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = Kajet.type.label.copy(fontSize = 15.sp),
            color = if (selected) colors.accent else colors.text,
        )
    }
}

@Composable
private fun ScienceKeyButton(
    button: ScienceButton,
    inverse: Boolean,
    degrees: Boolean,
    scale: Float,
    onKey: (CalcKey) -> Unit,
    onInverse: () -> Unit,
    onAngle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Kajet.colors
    val words = LocalStrings.current
    val label: String
    val description: String?
    val highlighted: Boolean
    val action: () -> Unit
    when (button) {
        InverseToggle -> {
            label = "INV"
            description = words.calculatorInverse
            highlighted = inverse
            action = onInverse
        }
        AngleToggle -> {
            label = if (degrees) "DEG" else "RAD"
            description = words.calculatorAngle
            highlighted = false
            action = onAngle
        }
        is ScienceKey -> {
            val key = if (inverse) button.inverse else button.key
            label = key.symbol
            description = if (key == CalcKey.ROOT) words.calculatorRoot else null
            highlighted = inverse && button.inverse != button.key
            action = { onKey(key) }
        }
    }
    Box(
        modifier
            .height(SCIENTIFIC_KEY_HEIGHT * scale)
            .clip(RoundedCornerShape(Kajet.dimens.corner))
            .background(if (highlighted) colors.accentWash else Color.Transparent)
            .border(1.dp, colors.line, RoundedCornerShape(Kajet.dimens.corner))
            .focusProperties { canFocus = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = pressRipple(colors.accent),
                role = Role.Button,
                onClickLabel = description,
                onClick = action,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = Kajet.type.label.copy(fontSize = (17 * scale).sp, lineHeight = (22 * scale).sp),
            color = if (highlighted) colors.accent else colors.text,
            maxLines = 1,
        )
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
            .height(KEY_HEIGHT * scale)
            .clip(RoundedCornerShape(Kajet.dimens.corner))
            .background(background)
            .focusProperties { canFocus = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = pressRipple(colors.accent),
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = Kajet.type.title.copy(fontSize = (24 * scale).sp),
            color = textColor,
        )
    }
}
