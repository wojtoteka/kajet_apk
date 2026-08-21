package wojtoteka.ovh.kajet.ui.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.PrimaryButton
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.editor.NoteViewModel
import wojtoteka.ovh.kajet.editor.SaveState
import wojtoteka.ovh.kajet.ui.library.KajetDialog

/*
 * Osłona edytora na rzecz skasowaną w trakcie pracy na innym urządzeniu.
 *
 * Kiedy synchronizacja zabiera otwartą notatkę albo plik (kasowanie przyszło
 * z serwera), treść na ekranie wciąż jest w pamięci i nie może przepaść bez
 * pytania. Osłona rysuje okno wyboru - „Zapisz jako nową" / „Odrzuć zmiany" -
 * i oddaje powrót do podania edytorowi: zwykły, dopóki nie ma o co pytać;
 * przy niezapisanej treści skasowanej rzeczy najpierw okno.
 *
 * Okno da się odłożyć stuknięciem obok (można dalej pisać), ale wraca przy
 * każdej próbie wyjścia - wybór nie może zniknąć razem z treścią.
 */
@Composable
fun remoteDeletionGuard(
    deleted: Boolean,
    unsaved: Boolean,
    message: String,
    saveLabel: String,
    onSaveAsNew: suspend () -> Boolean,
    onDiscard: () -> Unit,
    onBack: () -> Unit,
): () -> Unit {
    val words = LocalStrings.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var postponed by remember { mutableStateOf(false) }

    if (deleted && unsaved && !postponed) {
        KajetDialog(words.noteDeletedElsewhere, onClose = { postponed = true }) {
            Text(message, style = Kajet.type.body, color = Kajet.colors.text)
            // Dwa SecondaryButton 48 dp w jednym rzędzie nie mieszczą się
            // na telefonie. Zapis zostaje PrimaryButton; odrzucenie jest
            // akcją tekstową jak „Wyczyść" w konsoli i schodzi pod spód -
            // treść nie znika bez tego wyboru.
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PrimaryButton(saveLabel, {
                    if (!busy) {
                        busy = true
                        scope.launch {
                            val saved = runCatching { onSaveAsNew() }.getOrDefault(false)
                            busy = false
                            // Nieudany zapis zostawia treść na ekranie razem
                            // z komunikatem - okno zostaje, można ponowić.
                            if (saved) onBack()
                        }
                    }
                })
                DiscardAction(words.discardChanges) {
                    onDiscard()
                    onBack()
                }
            }
        }
    }

    return back@{
        if (deleted && unsaved) {
            postponed = false
            return@back
        }
        onBack()
    }
}

/** To samo dla trzech edytorów notatek - stan czyta wprost z modelu. */
@Composable
fun remoteDeletionGuard(model: NoteViewModel, onBack: () -> Unit): () -> Unit {
    val words = LocalStrings.current
    val deleted by model.remotelyDeleted.collectAsStateWithLifecycle()
    val saveState by model.saveState.collectAsStateWithLifecycle()
    val document by model.document.collectAsStateWithLifecycle()
    return remoteDeletionGuard(
        deleted = deleted,
        unsaved = document != null &&
            saveState != SaveState.SAVED && saveState != SaveState.LOADING,
        message = words.noteDeletedElsewhereAbout,
        saveLabel = words.saveAsNewNote,
        onSaveAsNew = model::saveAsNew,
        onDiscard = model::discardChanges,
        onBack = onBack,
    )
}

@Composable
private fun DiscardAction(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .height(44.dp)
            .clickable(onClick = onClick, onClickLabel = label, role = Role.Button)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Kajet.type.label, color = Kajet.colors.muted)
    }
}
