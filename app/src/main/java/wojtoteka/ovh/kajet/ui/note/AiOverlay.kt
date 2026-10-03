package wojtoteka.ovh.kajet.ui.note

import android.view.Gravity
import android.view.MotionEvent
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.DialogWindowProvider
import kotlin.math.roundToInt
import wojtoteka.ovh.kajet.core.design.Kajet
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import wojtoteka.ovh.kajet.cloud.KajetLinks
import wojtoteka.ovh.kajet.cloud.openLink
import wojtoteka.ovh.kajet.core.ai.AiAssistant
import wojtoteka.ovh.kajet.core.ai.AiConsentDialog
import wojtoteka.ovh.kajet.core.ai.AiHooks
import wojtoteka.ovh.kajet.core.ai.AiNoteKind
import wojtoteka.ovh.kajet.core.ai.AiPanel
import wojtoteka.ovh.kajet.core.text.LocalStrings

/*
  Asystent nad edytorem.

  Nakładka, nie część edytora - tak samo jak okno zapisu. Dzięki temu każdy
  z trzech edytorów dostał tylko jedno pole więcej („onAi"), a cała obsługa
  asystenta stoi w jednym miejscu.

  Bramka jest dwustopniowa. Najpierw available(): konto bez uprawnienia nie
  dostaje nawet przycisku, więc po funkcji nie ma w aplikacji ani śladu.
  Potem consented(): uprawnienie jest, ale zanim cokolwiek wyjdzie do Google,
  człowiek musi wiedzieć, co się z tym stanie, i to potwierdzić.

  Panel stoi we własnym oknie (Dialog), nie w Boxie nad edytorem. Podgląd
  HTML to WebView, a WebView maluje się we własnej warstwie Androida - Compose
  narysowany obok niego, nawet później w drzewie, zostaje pod spodem. Osobne
  okno jest nad tą warstwą, tak samo jak zgoda i KajetDialog.
*/
@Composable
fun AiOverlay(
    assistant: AiAssistant,
    open: Boolean,
    noteId: String?,
    kind: AiNoteKind,
    hooks: AiHooks,
    onClose: () -> Unit,
) {
    if (!open || noteId == null) return

    val context = LocalContext.current
    val words = LocalStrings.current
    val scope = rememberCoroutineScope()
    var consented by remember { mutableStateOf(assistant.consented()) }
    var consentError by remember { mutableStateOf<String?>(null) }
    var agreeing by remember { mutableStateOf(false) }

    if (!consented) {
        AiConsentDialog(
            onAgree = {
                if (!agreeing) {
                    agreeing = true
                    consentError = null
                    scope.launch {
                        val ok = runCatching { assistant.setConsent(true) }.getOrDefault(false)
                        if (ok) consented = true else consentError = words.aiConsentFailed
                        agreeing = false
                    }
                }
            },
            onNo = onClose,
            onOpenPolicy = { openLink(context, KajetLinks.privacy()) },
            error = consentError,
            busy = agreeing,
        )
        return
    }

    // Okienko z boku, jak kalkulator - notatka pod spodem zostaje widoczna,
    // da się ją przewijać i czytać, a sam asystent przesuwa się za uchwyt.
    Dialog(onDismissRequest = onClose, properties = FLOATING) {
        FloatingWindow {
            Box(
                Modifier
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.7f).dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                AiPanel(
                    assistant = assistant,
                    noteId = noteId,
                    kind = kind,
                    hooks = hooks,
                    onClose = onClose,
                )
            }
        }
    }
}

/*
  Okno dialogowe zamienione w pływające okienko.

  Własne okno zostaje (patrz wyżej: WebView), ale:
  - bez przyciemnienia i bez zajmowania całego ekranu - ma tylko swoją wielkość
    i stoi w prawym dolnym rogu, tak jak kalkulator,
  - dotknięcie obok niego idzie do notatki (FLAG_NOT_TOUCH_MODAL), a nie
    zamyka asystenta,
  - po dotknięciu notatki okienko oddaje jej klawiaturę (NOT_FOCUSABLE), a po
    dotknięciu okienka bierze ją z powrotem - inaczej pisałoby się
    w polecenie, patrząc na kursor w notatce.
*/
private val FLOATING = DialogProperties(
    dismissOnClickOutside = false,
    usePlatformDefaultWidth = false,
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FloatingWindow(content: @Composable () -> Unit) {
    val view = LocalView.current
    val window = remember(view) { (view.parent as? DialogWindowProvider)?.window }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    // Przesunięcie od prawego dolnego rogu ekranu, w pikselach.
    var shiftX by rememberSaveable { mutableIntStateOf(0) }
    var shiftY by rememberSaveable { mutableIntStateOf(0) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val margin = with(density) { 16.dp.roundToPx() }
    val screenWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.roundToPx() }

    DisposableEffect(window) {
        if (window == null) return@DisposableEffect onDispose { }
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        )
        window.setGravity(Gravity.BOTTOM or Gravity.END)
        window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val original = window.callback
        window.callback = object : Window.Callback by original {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_OUTSIDE -> {
                        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                        return false
                    }
                    MotionEvent.ACTION_DOWN ->
                        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                }
                return original.dispatchTouchEvent(event)
            }
        }
        onDispose { window.callback = original }
    }

    // Okienko nie ucieka za krawędź ekranu - także po obrocie.
    val maxX = (screenWidth - size.width - 2 * margin).coerceAtLeast(0)
    val maxY = (screenHeight - size.height - 2 * margin).coerceAtLeast(0)
    val x = shiftX.coerceIn(0, maxX)
    val y = shiftY.coerceIn(0, maxY)
    SideEffect {
        if (window != null) {
            val attributes = window.attributes
            if (attributes.x != x + margin || attributes.y != y + margin) {
                attributes.x = x + margin
                attributes.y = y + margin
                window.attributes = attributes
            }
        }
    }

    val words = LocalStrings.current
    val colors = Kajet.colors
    val width = minOf(420.dp, configuration.screenWidthDp.dp - 32.dp)
    Column(
        Modifier
            .width(width)
            .onSizeChanged { size = it }
            .shadow(8.dp, RoundedCornerShape(Kajet.dimens.corner))
            .clip(RoundedCornerShape(Kajet.dimens.corner))
            .background(colors.sheet),
    ) {
        // Uchwyt do przesuwania. Liczony w surowych współrzędnych ekranu, bo
        // okno jedzie razem z palcem - współrzędne wewnątrz okna by stały.
        var lastX by remember { mutableFloatStateOf(0f) }
        var lastY by remember { mutableFloatStateOf(0f) }
        Box(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .background(colors.desk)
                .semantics { contentDescription = words.aiMove }
                .pointerInteropFilter { event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            lastX = event.rawX
                            lastY = event.rawY
                        }
                        MotionEvent.ACTION_MOVE -> {
                            shiftX = (shiftX.coerceIn(0, maxX) - (event.rawX - lastX).roundToInt()).coerceIn(0, maxX)
                            shiftY = (shiftY.coerceIn(0, maxY) - (event.rawY - lastY).roundToInt()).coerceIn(0, maxY)
                            lastX = event.rawX
                            lastY = event.rawY
                        }
                    }
                    true
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.line),
            )
        }
        content()
    }
}

/**
 * To samo dla pliku z kodem.
 *
 * Plik na dysku nie niesie identyfikatora - w przeciwieństwie do notatki, która
 * ma go w treści - więc numer, pod którym żyje na serwerze, trzeba wyciągnąć
 * z rejestru synchronizacji.
 */
@Composable
fun CodeAiOverlay(
    assistant: AiAssistant,
    open: Boolean,
    path: String,
    hooks: AiHooks,
    onClose: () -> Unit,
) {
    val noteId = remember(path) { assistant.codeNoteId(path) }
    AiOverlay(assistant, open, noteId, AiNoteKind.CODE, hooks, onClose)
}
