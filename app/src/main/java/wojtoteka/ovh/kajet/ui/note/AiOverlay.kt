package wojtoteka.ovh.kajet.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import kotlinx.coroutines.launch
import wojtoteka.ovh.kajet.cloud.KajetLinks
import wojtoteka.ovh.kajet.cloud.openLink
import wojtoteka.ovh.kajet.core.ai.AiAssistant
import wojtoteka.ovh.kajet.core.ai.AiConsentDialog
import wojtoteka.ovh.kajet.core.ai.AiHooks
import wojtoteka.ovh.kajet.core.ai.AiNoteKind
import wojtoteka.ovh.kajet.core.ai.AiPanel
import wojtoteka.ovh.kajet.core.design.Kajet

/*
  Asystent nad edytorem.

  Nakładka, nie część edytora - tak samo jak okno zapisu. Dzięki temu każdy
  z trzech edytorów dostał tylko jedno pole więcej („onAi"), a cała obsługa
  asystenta stoi w jednym miejscu.

  Bramka jest dwustopniowa. Najpierw available(): konto bez uprawnienia nie
  dostaje nawet przycisku, więc po funkcji nie ma w aplikacji ani śladu.
  Potem consented(): uprawnienie jest, ale zanim cokolwiek wyjdzie do Google,
  człowiek musi wiedzieć, co się z tym stanie, i to potwierdzić.
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
    val scope = rememberCoroutineScope()
    var consented by remember { mutableStateOf(assistant.consented()) }

    if (!consented) {
        AiConsentDialog(
            onAgree = {
                scope.launch {
                    // Zgoda zapisuje się przy KONCIE. Gdyby wysyłka nie
                    // przeszła, okno zostaje otwarte - nie udajemy, że serwer
                    // o niej wie.
                    if (assistant.setConsent(true)) consented = true
                }
            },
            onNo = onClose,
            onOpenPolicy = { openLink(context, KajetLinks.privacy()) },
        )
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Kajet.colors.desk.copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Kliknięcie w sam panel nie ma go zamykać - stąd druga, pusta łapka.
        Box(
            Modifier
                .widthIn(max = 460.dp)
                .padding(24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
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
