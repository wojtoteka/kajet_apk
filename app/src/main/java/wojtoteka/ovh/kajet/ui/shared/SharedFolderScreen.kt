package wojtoteka.ovh.kajet.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import wojtoteka.ovh.kajet.cloud.CloudClient
import wojtoteka.ovh.kajet.cloud.SharedFolderListing
import wojtoteka.ovh.kajet.cloud.SharedItem
import wojtoteka.ovh.kajet.cloud.SharedLibrary
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.HorizontalRule
import wojtoteka.ovh.kajet.core.design.component.IconAction
import wojtoteka.ovh.kajet.core.design.component.NoticeBar
import wojtoteka.ovh.kajet.core.design.component.PrimaryButton
import wojtoteka.ovh.kajet.core.design.component.SecondaryButton
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.core.text.sharedByOwner
import wojtoteka.ovh.kajet.ui.library.KajetDialog

/**
 * Udostępniony folder: podfoldery i notatki właściciela, wprost z serwera.
 *
 * Z prawem do zmian da się tu dopisać notatkę (należy do właściciela
 * folderu, jak na Dysku Google), założyć podfolder i usunąć notatkę - ta
 * trafia do kosza właściciela, więc nic nie przepada na dobre.
 */
@Composable
fun SharedFolderScreen(
    shared: SharedLibrary,
    token: String,
    folderId: String?,
    onOpenNote: (path: String) -> Unit,
    onOpenFolder: (folderId: String) -> Unit,
    onOpenWeb: (url: String) -> Unit,
    onBack: () -> Unit,
) {
    val words = LocalStrings.current
    val colors = Kajet.colors
    val scope = rememberCoroutineScope()
    var listing by remember(token, folderId) { mutableStateOf<SharedFolderListing?>(null) }
    var problem by remember(token, folderId) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var newNote by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(token, folderId, reload) {
        when (val answer = shared.folder(token, folderId)) {
            is CloudClient.Result.Ok -> {
                listing = answer.data
                problem = null
            }
            is CloudClient.Result.Error -> problem = answer.message
        }
    }

    val current = listing
    val item = SharedItem(
        token = token,
        kind = "folder",
        permission = current?.permission ?: "read",
        owner = current?.owner.orEmpty(),
        accepted = shared.items.value.any { it.token == token },
    )
    val canEdit = current?.permission == "edit"

    fun opened(result: SharedLibrary.Opened) {
        when (result) {
            is SharedLibrary.Opened.Note -> onOpenNote(result.path)
            is SharedLibrary.Opened.Web -> onOpenWeb(result.url)
            is SharedLibrary.Opened.Folder -> Unit
            is SharedLibrary.Opened.Failed -> problem = result.message
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.sheet),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(icon = KajetIcons.BackArrow, description = words.back, onClick = onBack)
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    current?.path?.joinToString(" / ") { it.name } ?: words.sharedSection,
                    style = Kajet.type.titleSmall,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (current != null) {
                    Text(
                        "${words.sharedByOwner(current.owner)} · ${if (canEdit) words.shareRightEdit else words.liveReadOnly}",
                        style = Kajet.type.meta,
                        color = colors.muted,
                        maxLines = 1,
                    )
                }
            }
            if (canEdit) {
                IconAction(icon = KajetIcons.Plus, description = words.newNote, onClick = { newNote = true })
                IconAction(icon = KajetIcons.Folder, description = words.sharedNewFolder, onClick = { newFolder = true })
            }
        }
        HorizontalRule()

        problem?.let {
            NoticeBar(icon = KajetIcons.ErrorMark, text = it, color = colors.danger) {
                SecondaryButton(words.retryStuckButton, { reload++ })
            }
        }

        when {
            current == null && problem == null -> Text(
                words.sharedOpening,
                style = Kajet.type.body,
                color = colors.muted,
                modifier = Modifier.padding(24.dp),
            )
            current != null && current.folders.isEmpty() && current.notes.isEmpty() -> Text(
                words.sharedFolderEmpty,
                style = Kajet.type.body,
                color = colors.muted,
                modifier = Modifier.padding(24.dp),
            )
            current != null -> LazyColumn(Modifier.fillMaxSize()) {
                items(current.folders, key = { "f" + it.id }) { folder ->
                    Entry(
                        icon = KajetIcons.Folder,
                        title = folder.name,
                        onClick = { onOpenFolder(folder.id) },
                    )
                }
                items(current.notes, key = { "n" + it.id }) { note ->
                    Entry(
                        icon = when (note.kind) {
                            "HANDWRITTEN" -> KajetIcons.HandwrittenNote
                            "MINDMAP" -> KajetIcons.MindMapIcon
                            "CODE" -> KajetIcons.CodeFile
                            else -> KajetIcons.TextNote
                        },
                        title = note.title.ifBlank { words.untitled },
                        onClick = {
                            if (!busy) {
                                busy = true
                                scope.launch {
                                    opened(shared.openNote(token, note, item))
                                    busy = false
                                }
                            }
                        },
                        onDelete = if (canEdit) ({ deleting = note.id }) else null,
                    )
                }
            }
        }
    }

    if (newNote) {
        KajetDialog(words.newNote, onClose = { newNote = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (kind in NoteKind.entries) {
                    SecondaryButton(kind.label(words), {
                        newNote = false
                        val parent = current?.folder?.id ?: return@SecondaryButton
                        scope.launch { opened(shared.createNote(item, parent, kind)) }
                    }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (newFolder) {
        var name by remember { mutableStateOf("") }
        KajetDialog(words.sharedNewFolder, onClose = { newFolder = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(120) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton(words.create, {
                    val parent = current?.folder?.id
                    if (parent != null && name.isNotBlank()) {
                        newFolder = false
                        scope.launch {
                            problem = shared.createFolder(item, parent, name.trim())
                            reload++
                        }
                    }
                }, enabled = name.isNotBlank())
            }
        }
    }

    deleting?.let { noteId ->
        KajetDialog(words.sharedDeleteNote, onClose = { deleting = null }) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(words.cancel, { deleting = null })
                PrimaryButton(words.delete, {
                    deleting = null
                    scope.launch {
                        problem = shared.deleteNote(item, noteId)
                        reload++
                    }
                })
            }
        }
    }
}

@Composable
private fun Entry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val colors = Kajet.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
        Text(
            title,
            style = Kajet.type.body,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onDelete != null) {
            IconAction(icon = KajetIcons.Bin, description = LocalStrings.current.delete, onClick = onDelete)
        }
    }
    HorizontalRule()
}
