package wojtoteka.ovh.kajet.ui.note

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.input.pointer.pointerInput
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

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            decorFitsSystemWindows = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onClose() } }
                .systemBarsPadding()
                .imePadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Kliknięcie w sam panel nie ma go zamykać - stąd druga, pusta łapka.
            Box(
                Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .pointerInput(Unit) { detectTapGestures { } },
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
