package wojtoteka.ovh.kajet.ui.note

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import wojtoteka.ovh.kajet.core.design.InkPalette
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.SecondaryButton
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.editor.handwriting.HandwritingEditor
import wojtoteka.ovh.kajet.editor.handwriting.HandwritingViewModel
import wojtoteka.ovh.kajet.editor.mindmap.MindMapEditor
import wojtoteka.ovh.kajet.editor.mindmap.MindMapViewModel
import wojtoteka.ovh.kajet.editor.text.TextEditor
import wojtoteka.ovh.kajet.editor.text.TextNoteViewModel
import wojtoteka.ovh.kajet.export.ExportDialog
import wojtoteka.ovh.kajet.export.ExportService
import wojtoteka.ovh.kajet.storage.SettingsStore
import wojtoteka.ovh.kajet.storage.LibraryRepository

@Composable
fun NoteScreen(
    path: String,
    repo: LibraryRepository,
    settings: SettingsStore,
    export: ExportService,
    onBack: () -> Unit,
) {
    var exportDialog by remember(path) { mutableStateOf(false) }
    var kind by remember(path) { mutableStateOf<NoteKind?>(null) }
    var problem by remember(path) { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        val found = runCatching { repo.noteKind(path) }.getOrNull()
        if (found == null) {
            problem = "Nie udało się otworzyć notatki. Sprawdź, czy plik nadal jest w folderze."
        }
        kind = found
    }

    val colors = Kajet.colors

    when {
        problem != null -> Column(
            Modifier
                .fillMaxSize()
                .background(colors.sheet)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Notatka się nie otworzyła", style = Kajet.type.title, color = colors.text)
            Text(problem.orEmpty(), style = Kajet.type.body, color = colors.muted)
            SecondaryButton("Wróć do biblioteki", onBack, icon = KajetIcons.BackArrow)
        }

        kind == NoteKind.HANDWRITTEN -> {
            val model: HandwritingViewModel = viewModel(
                key = "handwriting-$path",
                factory = HandwritingViewModel.Factory(
                    repo = repo,
                    settings = settings,
                    path = path,
                    inkColor = colors.defaultInk.toArgb(),
                    highlighterColor = InkPalette.HighlighterYellow.toArgb(),
                ),
            )
            HandwritingEditor(
                model = model,
                onBack = onBack,
                onExport = { exportDialog = true },
            )
            NoteExportOverlay(exportDialog, model.document, path, export) { exportDialog = false }
        }

        kind == NoteKind.TEXT -> {
            val model: TextNoteViewModel = viewModel(
                key = "text-$path",
                factory = TextNoteViewModel.Factory(repo, settings, path),
            )
            val context = LocalContext.current
            var cameraFile by remember { mutableStateOf<java.io.File?>(null) }

            val fromGallery = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent(),
            ) { uri: Uri? ->
                if (uri != null) {
                    val data = Photos.read(context, uri)
                    if (data != null) {
                        model.insertPhoto(data, Photos.extension(context, uri), model.markdown.length)
                    }
                }
            }

            val fromCamera = rememberLauncherForActivityResult(
                ActivityResultContracts.TakePicture(),
            ) { ok: Boolean ->
                val file = cameraFile
                if (ok && file != null && file.exists()) {
                    model.insertPhoto(file.readBytes(), "jpg", model.markdown.length)
                    file.delete()
                }
                cameraFile = null
            }

            TextEditor(
                model = model,
                onBack = onBack,
                onExport = { exportDialog = true },
                onPhotoFromGallery = { fromGallery.launch("image/*") },
                onPhotoFromCamera = {
                    val (file, uri) = Photos.fileForPhoto(context)
                    cameraFile = file
                    fromCamera.launch(uri)
                },
            )
            NoteExportOverlay(exportDialog, model.document, path, export) { exportDialog = false }
        }

        kind == NoteKind.MINDMAP -> {
            val model: MindMapViewModel = viewModel(
                key = "mindmap-$path",
                factory = MindMapViewModel.Factory(repo, settings, path),
            )
            MindMapEditor(model = model, onBack = onBack, onExport = { exportDialog = true })
            NoteExportOverlay(exportDialog, model.document, path, export) { exportDialog = false }
        }

        kind != null -> Column(
            Modifier
                .fillMaxSize()
                .background(colors.sheet)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Ten rodzaj notatki nie ma jeszcze edytora", style = Kajet.type.title, color = colors.text)
            Text(
                text = "Notatka jest bezpieczna na dysku. Edytor tego rodzaju powstaje w kolejnym etapie pracy.",
                style = Kajet.type.body,
                color = colors.muted,
            )
            SecondaryButton("Wróć do biblioteki", onBack, icon = KajetIcons.BackArrow)
        }

        else -> Column(
            Modifier
                .fillMaxSize()
                .background(colors.sheet)
                .padding(32.dp),
        ) {
            Text("Otwieram notatkę", style = Kajet.type.body, color = colors.muted)
        }
    }
}

@Composable
private fun NoteExportOverlay(
    visible: Boolean,
    document: kotlinx.coroutines.flow.StateFlow<wojtoteka.ovh.kajet.core.model.NoteDocument?>,
    path: String,
    service: ExportService,
    onClose: () -> Unit,
) {
    if (!visible) return
    val content by document.collectAsStateWithLifecycle()
    val ready = content ?: return
    ExportDialog(document = ready, path = path, service = service, onClose = onClose)
}
