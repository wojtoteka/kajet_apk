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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wojtoteka.ovh.kajet.core.ai.AiNoteKind
import wojtoteka.ovh.kajet.core.design.InkPalette
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.SecondaryButton
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.editor.handwriting.HandwritingEditor
import wojtoteka.ovh.kajet.editor.handwriting.HandwritingViewModel
import wojtoteka.ovh.kajet.editor.mindmap.MindMapEditor
import wojtoteka.ovh.kajet.editor.mindmap.MindMapViewModel
import wojtoteka.ovh.kajet.editor.text.TextEditor
import wojtoteka.ovh.kajet.editor.text.TextNoteViewModel
import wojtoteka.ovh.kajet.container
import wojtoteka.ovh.kajet.export.ExportDialog
import wojtoteka.ovh.kajet.export.ExportService
import wojtoteka.ovh.kajet.storage.SettingsStore
import wojtoteka.ovh.kajet.storage.KajetSettings
import wojtoteka.ovh.kajet.storage.LibraryRepository
import wojtoteka.ovh.kajet.storage.SharedNotes

@Composable
fun NoteScreen(
    path: String,
    repo: LibraryRepository,
    settings: SettingsStore,
    loaded: KajetSettings,
    export: ExportService,
    onBack: () -> Unit,
) {
    val words = LocalStrings.current
    var exportDialog by remember(path) { mutableStateOf(false) }
    var kind by remember(path) { mutableStateOf<NoteKind?>(null) }
    var problem by remember(path) { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        val found = runCatching { repo.noteKind(path) }.getOrNull()
        if (found == null) {
            problem = words.noteNotOpenedAbout
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
            Text(words.noteNotOpened, style = Kajet.type.title, color = colors.text)
            Text(problem.orEmpty(), style = Kajet.type.body, color = colors.muted)
            SecondaryButton(words.backToLibrary, onBack, icon = KajetIcons.BackArrow)
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
                    pens = loaded.pens,
                    shapes = loaded.shapes,
                    fingerBehavior = loaded.fingerBehavior,
                ),
            )
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var cameraFile by remember { mutableStateOf<java.io.File?>(null) }

            val fromGallery = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent(),
            ) { uri: Uri? ->
                if (uri != null) {
                    scope.launch {
                        val photo = readPhoto(context, uri)
                        if (photo != null) model.insertPhoto(photo.first, photo.second)
                    }
                }
            }

            val fromCamera = rememberLauncherForActivityResult(
                ActivityResultContracts.TakePicture(),
            ) { ok: Boolean ->
                val file = cameraFile
                cameraFile = null
                if (ok && file != null) {
                    scope.launch {
                        val data = readCameraFile(file)
                        if (data != null) model.insertPhoto(data, "jpg")
                    }
                }
            }

            // Powrót przez osłonę: notatka skasowana w trakcie pisania na
            // innym urządzeniu najpierw pyta o los niezapisanej treści.
            val guardedBack = remoteDeletionGuard(model, onBack)

            HandwritingEditor(
                model = model,
                onBack = guardedBack,
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

        kind == NoteKind.TEXT -> {
            val model: TextNoteViewModel = viewModel(
                key = "text-$path",
                factory = TextNoteViewModel.Factory(repo, settings, path),
            )
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var cameraFile by remember { mutableStateOf<java.io.File?>(null) }

            val fromGallery = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent(),
            ) { uri: Uri? ->
                if (uri != null) {
                    scope.launch {
                        val photo = readPhoto(context, uri)
                        if (photo != null) {
                            model.insertPhoto(photo.first, photo.second, model.takePhotoSpot())
                        }
                    }
                }
            }

            val fromCamera = rememberLauncherForActivityResult(
                ActivityResultContracts.TakePicture(),
            ) { ok: Boolean ->
                val file = cameraFile
                cameraFile = null
                if (ok && file != null) {
                    scope.launch {
                        val data = readCameraFile(file)
                        if (data != null) model.insertPhoto(data, "jpg", model.takePhotoSpot())
                    }
                }
            }

            val guardedBack = remoteDeletionGuard(model, onBack)
            val assistant = context.container.ai.takeIf { it.available() }
            var aiOpen by remember(path) { mutableStateOf(false) }
            val note by model.document.collectAsStateWithLifecycle()

            TextEditor(
                model = model,
                onBack = guardedBack,
                onExport = { exportDialog = true },
                onPhotoFromGallery = { fromGallery.launch("image/*") },
                onPhotoFromCamera = {
                    val (file, uri) = Photos.fileForPhoto(context)
                    cameraFile = file
                    fromCamera.launch(uri)
                },
                onAi = if (assistant != null) ({ aiOpen = true }) else null,
            )
            NoteExportOverlay(exportDialog, model.document, path, export) { exportDialog = false }
            if (assistant != null) {
                AiOverlay(assistant, aiOpen, note?.id, AiNoteKind.TEXT, model) { aiOpen = false }
            }
        }

        kind == NoteKind.MINDMAP -> {
            val model: MindMapViewModel = viewModel(
                key = "mindmap-$path",
                factory = MindMapViewModel.Factory(repo, settings, path),
            )
            val guardedBack = remoteDeletionGuard(model, onBack)
            val context = LocalContext.current
            val assistant = context.container.ai.takeIf { it.available() }
            var aiOpen by remember(path) { mutableStateOf(false) }
            val note by model.document.collectAsStateWithLifecycle()

            MindMapEditor(
                model = model,
                onBack = guardedBack,
                onExport = { exportDialog = true },
                onAi = if (assistant != null) ({ aiOpen = true }) else null,
            )
            NoteExportOverlay(exportDialog, model.document, path, export) { exportDialog = false }
            if (assistant != null) {
                AiOverlay(assistant, aiOpen, note?.id, AiNoteKind.MINDMAP, model) { aiOpen = false }
            }
        }

        kind != null -> Column(
            Modifier
                .fillMaxSize()
                .background(colors.sheet)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(words.noEditorYet, style = Kajet.type.title, color = colors.text)
            Text(
                text = words.noEditorYetAbout,
                style = Kajet.type.body,
                color = colors.muted,
            )
            SecondaryButton(words.backToLibrary, onBack, icon = KajetIcons.BackArrow)
        }

        else -> Column(
            Modifier
                .fillMaxSize()
                .background(colors.sheet)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(words.noteOpening, style = Kajet.type.body, color = colors.muted)
            // Wyjście musi być zawsze, nawet gdyby otwieranie się zawiesiło.
            SecondaryButton(words.backToLibrary, onBack, icon = KajetIcons.BackArrow)
        }
    }
}

/*
 * Zdjęcie potrafi ważyć kilkanaście megabajtów. Odczyt z galerii idzie przez
 * dostawcę treści (czasem po sieci, gdy to dysk w chmurze), a odpowiedź z
 * aparatu wraca na wątek głównym - czytane stamtąd wprost, zamrażało ekran na
 * czas odczytu. Stąd te dwie funkcje: całe wejście-wyjście na wątku roboczym.
 */

private suspend fun readPhoto(
    context: android.content.Context,
    uri: Uri,
): Pair<ByteArray, String>? = withContext(Dispatchers.IO) {
    val data = Photos.read(context, uri) ?: return@withContext null
    data to Photos.extension(context, uri)
}

private suspend fun readCameraFile(file: java.io.File): ByteArray? = withContext(Dispatchers.IO) {
    val data = runCatching { file.takeIf { it.exists() }?.readBytes() }.getOrNull()
    runCatching { file.delete() }
    data
}

@Composable
private fun NoteExportOverlay(
    visible: Boolean,
    document: kotlinx.coroutines.flow.StateFlow<wojtoteka.ovh.kajet.core.model.NoteDocument?>,
    path: String,
    service: ExportService,
    onClose: () -> Unit,
) {
    // Panel udostępniania żyje obok okna zapisu, nie w nim: przycisk
    // w oknie zamyka okno i otwiera panel, więc stan panelu musi przetrwać
    // zniknięcie okna.
    var shareDialog by remember { mutableStateOf(false) }
    val content by document.collectAsStateWithLifecycle()
    val ready = content ?: return
    val cloud = LocalContext.current.container.cloud

    if (visible) {
        ExportDialog(
            document = ready,
            path = path,
            service = service,
            onClose = onClose,
            // Bez konta notatka nie istnieje na serwerze - nie ma czym
            // zarządzać i przycisk się nie pokazuje.
            // Cudzej notatki nie udostępnia się dalej - to robi jej właściciel.
            onShareLink = if (cloud.account.isSignedIn() && !path.startsWith(SharedNotes.PREFIX)) {
                {
                    onClose()
                    shareDialog = true
                }
            } else {
                null
            },
        )
    }

    if (shareDialog) {
        ShareDialog(
            document = ready,
            cloud = cloud,
            service = service,
            onClose = { shareDialog = false },
        )
    }
}
