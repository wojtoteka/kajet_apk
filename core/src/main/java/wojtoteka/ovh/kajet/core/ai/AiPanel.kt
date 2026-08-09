package wojtoteka.ovh.kajet.core.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.PrimaryButton
import wojtoteka.ovh.kajet.core.design.component.SecondaryButton
import wojtoteka.ovh.kajet.core.design.component.SectionLabel
import wojtoteka.ovh.kajet.core.design.component.ToolPanel
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.text.LocalStrings

/*
  Panel asystenta - jeden dla notatki tekstowej, mapy myśli i kodu.

  Pokazuje się WYŁĄCZNIE wtedy, gdy konto ma uprawnienie; sprawdza to ten, kto
  panel osadza. Tutaj nie ma na to zapasowej bramki, bo prawdziwa i tak stoi na
  serwerze - ukrycie przycisku nigdy nie było zabezpieczeniem.

  Najważniejszy jest tu przycisk cofania. Asystent zmienia CZYJĄŚ pracę, więc
  możliwość wycofania się jednym dotknięciem nie jest udogodnieniem, tylko
  warunkiem, żeby funkcja w ogóle miała prawo istnieć.
*/

/** Co edytor musi umieć, żeby dało się przy nim postawić asystenta. */
interface AiHooks {
    /**
     * Zapisz to, co jest na ekranie, i zapamiętaj tę wersję. Asystent pracuje
     * na tym, co ma serwer, więc niezapisane zdanie musi tam trafić pierwsze.
     */
    suspend fun prepareForAi()

    /** Wczytaj notatkę z dysku na nowo - asystent ją zmienił. */
    suspend fun reloadAfterAi()

    /** Wróć do wersji zapamiętanej przez [prepareForAi]. */
    suspend fun undoAi(): Boolean
}

private sealed interface Stage {
    data object Idle : Stage
    data object Working : Stage
    data class Done(val summary: String) : Stage
    data class Asked(val question: String) : Stage
    data class Failed(val reason: String) : Stage
    data object Undone : Stage
}

@Composable
fun AiPanel(
    assistant: AiAssistant,
    noteId: String,
    kind: AiNoteKind,
    hooks: AiHooks,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val words = LocalStrings.current
    val scope = rememberCoroutineScope()

    var instruction by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf<Stage>(Stage.Idle) }
    var turns by remember { mutableStateOf<List<AiTurn>>(emptyList()) }
    var historyShown by remember { mutableStateOf(false) }

    ToolPanel(title = words.aiTitle, onClose = onClose, modifier = modifier) {
        val working = stage is Stage.Working

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel(words.aiHint)
            BasicTextField(
                value = instruction,
                onValueChange = { instruction = it },
                enabled = !working,
                textStyle = Kajet.type.body.copy(color = Kajet.colors.text),
                cursorBrush = SolidColor(Kajet.colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                text = words.aiAsk,
                icon = KajetIcons.ArrowRight,
                enabled = !working && instruction.isNotBlank(),
                onClick = {
                    val asked = instruction.trim()
                    stage = Stage.Working
                    scope.launch {
                        hooks.prepareForAi()
                        stage = when (val outcome = assistant.ask(noteId, kind, asked)) {
                            is AiOutcome.Changed -> {
                                hooks.reloadAfterAi()
                                // Pole czyści się dopiero po UDANEJ zmianie -
                                // po błędzie polecenie ma zostać, żeby dało
                                // się poprawić jedno słowo i spróbować znowu.
                                instruction = ""
                                Stage.Done(outcome.summary)
                            }
                            is AiOutcome.Question -> Stage.Asked(outcome.question)
                            is AiOutcome.Refused -> Stage.Failed(outcome.reason)
                        }
                        if (historyShown) turns = assistant.history(noteId)
                    }
                },
            )

            if (stage is Stage.Done) {
                SecondaryButton(
                    text = words.aiUndo,
                    icon = KajetIcons.Undo,
                    onClick = {
                        scope.launch {
                            stage = if (hooks.undoAi()) {
                                Stage.Undone
                            } else {
                                Stage.Failed(words.aiUndoFailed)
                            }
                        }
                    },
                )
            }
        }

        when (val current = stage) {
            is Stage.Working -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    color = Kajet.colors.accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
                // Wywołanie potrafi trwać kilkadziesiąt sekund - bez tego
                // zdania ekran wygląda na zawieszony.
                Text(words.aiWorking, style = Kajet.type.body, color = Kajet.colors.muted)
            }

            is Stage.Done -> Message(current.summary, Kajet.colors.accent)
            is Stage.Undone -> Message(words.aiUndone, Kajet.colors.muted)
            is Stage.Failed -> Message(current.reason, Kajet.colors.muted)

            is Stage.Asked -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel(words.aiQuestionLabel)
                Message(current.question, Kajet.colors.text)
            }

            Stage.Idle -> Unit
        }

        // --- Rozmowa ---

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    historyShown = !historyShown
                    if (historyShown) scope.launch { turns = assistant.history(noteId) }
                },
        ) {
            SectionLabel(words.aiHistoryTitle, modifier = Modifier.weight(1f))
            Icon(
                if (historyShown) KajetIcons.ArrowDown else KajetIcons.ArrowRight,
                contentDescription = null,
                tint = Kajet.colors.muted,
                modifier = Modifier.size(16.dp),
            )
        }

        if (historyShown) {
            if (turns.isEmpty()) {
                Text(words.aiHistoryEmpty, style = Kajet.type.body, color = Kajet.colors.muted)
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    for (turn in turns) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(turn.request, style = Kajet.type.label, color = Kajet.colors.text)
                            Text(turn.reply, style = Kajet.type.body, color = Kajet.colors.muted)
                        }
                    }
                }
                SecondaryButton(
                    text = words.aiForgetHistory,
                    icon = KajetIcons.Bin,
                    onClick = {
                        scope.launch {
                            assistant.forgetHistory(noteId)
                            turns = emptyList()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun Message(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = Kajet.type.body,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .background(Kajet.colors.desk, RoundedCornerShape(Kajet.dimens.corner))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}
