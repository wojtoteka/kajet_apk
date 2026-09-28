package wojtoteka.ovh.kajet.ui.file

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.CalculatorAction
import wojtoteka.ovh.kajet.core.design.component.EmptyState
import wojtoteka.ovh.kajet.core.design.component.HorizontalRule
import wojtoteka.ovh.kajet.core.design.component.IconAction
import wojtoteka.ovh.kajet.core.design.component.SecondaryButton
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.image.Bitmaps
import wojtoteka.ovh.kajet.core.model.OtherFileKind
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.core.text.nothingOpensFile
import wojtoteka.ovh.kajet.core.text.pdfPage
import wojtoteka.ovh.kajet.storage.LibraryRepository
import java.io.File

/**
 * Podgląd pliku, którego Kajet nie edytuje: zdjęcie, PDF albo komunikat
 * przy binarce. Świadomie bez pola tekstu - surowe bajty w edytorze kodu
 * psuły plik przy pierwszym klawiszu.
 */
@Composable
fun FileViewer(
    path: String,
    repo: LibraryRepository,
    onBack: () -> Unit,
) {
    val words = LocalStrings.current
    val fileName = path.substringAfterLast('/')
    val kind = OtherFileKind.of(fileName)

    Column(
        Modifier
            .fillMaxSize()
            .background(Kajet.colors.sheet),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 20.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconAction(KajetIcons.BackArrow, words.backToLibrary, onBack)
            Text(
                text = fileName.ifBlank { words.unnamed },
                style = Kajet.type.titleSmall,
                color = Kajet.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            CalculatorAction()
        }
        HorizontalRule()
        when (kind) {
            OtherFileKind.IMAGE -> ImageBody(path, repo, onBack)
            OtherFileKind.PDF -> PdfBody(path, repo, onBack)
            OtherFileKind.BINARY, OtherFileKind.TEXT -> BinaryNotice(onBack)
        }
    }
}

@Composable
private fun ImageBody(path: String, repo: LibraryRepository, onBack: () -> Unit) {
    val words = LocalStrings.current
    val context = LocalContext.current
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path) {
        val data = withContext(Dispatchers.IO) {
            val uri = repo.store()?.entry(path)?.takeIf { it.isFile }?.uri
                ?: return@withContext null
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
        if (data == null) {
            failed = true
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.IO) { Bitmaps.decode(data)?.asImageBitmap() }
        failed = bitmap == null
    }

    when {
        failed -> EmptyState(
            title = words.fileDidNotOpen,
            description = words.fileDidNotOpenAbout,
            modifier = Modifier.fillMaxSize(),
            action = { SecondaryButton(words.backToLibrary, onBack, icon = KajetIcons.BackArrow) },
        )

        bitmap != null -> Box(
            Modifier
                .fillMaxSize()
                .background(Kajet.colors.desk)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap!!,
                contentDescription = path.substringAfterLast('/'),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }

        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(words.loading, style = Kajet.type.body, color = Kajet.colors.muted)
        }
    }
}

@Composable
private fun PdfBody(path: String, repo: LibraryRepository, onBack: () -> Unit) {
    val words = LocalStrings.current
    val context = LocalContext.current
    val widthPx = with(LocalDensity.current) {
        (LocalConfiguration.current.screenWidthDp.dp - 32.dp).toPx().toInt().coerceAtLeast(1)
    }

    var source by remember(path) { mutableStateOf<Uri?>(null) }
    var session by remember(path) { mutableStateOf<PdfSession?>(null) }
    var problem by remember(path) { mutableStateOf<String?>(null) }
    var pageIndex by remember(path) { mutableIntStateOf(0) }
    var page by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    var outsideProblem by remember(path) { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        val uri = withContext(Dispatchers.IO) {
            repo.store()?.entry(path)?.takeIf { it.isFile }?.uri
        }
        if (uri == null) {
            problem = words.fileDidNotOpenAbout
            return@LaunchedEffect
        }
        source = uri
        val opened = withContext(Dispatchers.IO) { PdfSession.open(context, uri) }
        if (opened == null) {
            problem = words.fileDidNotOpenAbout
            return@LaunchedEffect
        }
        session = opened
        try {
            awaitCancellation()
        } finally {
            opened.close()
            if (session === opened) session = null
        }
    }

    LaunchedEffect(session, pageIndex, widthPx) {
        val current = session ?: return@LaunchedEffect
        page = current.render(pageIndex, widthPx)
    }

    val current = session
    when {
        problem != null -> EmptyState(
            title = words.fileDidNotOpen,
            description = problem.orEmpty(),
            modifier = Modifier.fillMaxSize(),
            action = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        words.openInOtherApp,
                        onClick = {
                            val uri = source
                            outsideProblem = if (uri == null) {
                                words.fileDidNotOpenAbout
                            } else {
                                openPdfOutside(context, uri, path.substringAfterLast('/'))
                            }
                        },
                    )
                    if (outsideProblem != null) {
                        Text(outsideProblem.orEmpty(), style = Kajet.type.meta, color = Kajet.colors.danger)
                    }
                    SecondaryButton(words.backToLibrary, onBack, icon = KajetIcons.BackArrow)
                }
            },
        )

        current != null -> Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Kajet.colors.desk)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (page != null) {
                    Image(
                        bitmap = page!!,
                        contentDescription = words.pdfPage(pageIndex + 1, current.pageCount),
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                } else {
                    Text(
                        words.loading,
                        style = Kajet.type.body,
                        color = Kajet.colors.muted,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            }
            if (current.pageCount > 1) {
                HorizontalRule()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SecondaryButton(
                        words.previousPage,
                        onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                        enabled = pageIndex > 0,
                    )
                    Text(
                        text = words.pdfPage(pageIndex + 1, current.pageCount),
                        style = Kajet.type.meta,
                        color = Kajet.colors.muted,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        words.nextPage,
                        onClick = { pageIndex = (pageIndex + 1).coerceAtMost(current.pageCount - 1) },
                        enabled = pageIndex < current.pageCount - 1,
                    )
                }
            }
        }

        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(words.loading, style = Kajet.type.body, color = Kajet.colors.muted)
        }
    }
}

@Composable
private fun BinaryNotice(onBack: () -> Unit) {
    val words = LocalStrings.current
    EmptyState(
        title = words.cannotEditAsText,
        description = words.cannotEditAsTextAbout,
        modifier = Modifier.fillMaxSize(),
        action = { SecondaryButton(words.backToLibrary, onBack, icon = KajetIcons.BackArrow) },
    )
}

private class PdfSession(
    private val renderer: PdfRenderer,
    private val descriptor: ParcelFileDescriptor,
) {
    private val lock = Mutex()
    val pageCount: Int = renderer.pageCount

    suspend fun render(index: Int, widthPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (index !in 0 until renderer.pageCount) return@withLock null
            runCatching {
                renderer.openPage(index).use { pdfPage ->
                    val width = widthPx.coerceAtLeast(1)
                    val height = (pdfPage.height * (width.toFloat() / pdfPage.width))
                        .toInt()
                        .coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        lock.withLock {
            runCatching { renderer.close() }
            runCatching { descriptor.close() }
            Unit
        }
    }

    companion object {
        fun open(context: Context, uri: Uri): PdfSession? {
            val descriptor = fileDescriptor(context, uri) ?: return null
            val renderer = runCatching { PdfRenderer(descriptor) }.getOrNull()
            if (renderer == null) {
                runCatching { descriptor.close() }
                return null
            }
            if (renderer.pageCount <= 0) {
                runCatching { renderer.close() }
                runCatching { descriptor.close() }
                return null
            }
            return PdfSession(renderer, descriptor)
        }

        private fun fileDescriptor(context: Context, uri: Uri): ParcelFileDescriptor? {
            context.contentResolver.openFileDescriptor(uri, "r")?.let { return it }
            val dir = File(context.cacheDir, "eksport").apply { mkdirs() }
            val dest = File(dir, "podglad.pdf")
            val copied = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                } != null
            }.getOrDefault(false)
            if (!copied) return null
            return runCatching {
                ParcelFileDescriptor.open(dest, ParcelFileDescriptor.MODE_READ_ONLY)
            }.getOrNull()
        }
    }
}

/**
 * Otwiera PDF poza Kajetem. Najpierw sam URI z biblioteki, a gdy system
 * tego nie weźmie - kopia w pamięci podręcznej przez FileProvider.
 * Bajty nie idą do edytora kodu.
 */
private fun openPdfOutside(context: Context, source: Uri, fileName: String): String? {
    val words = wojtoteka.ovh.kajet.core.text.words
    val name = fileName.ifBlank { "dokument.pdf" }

    val direct = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(source, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
    }
    if (runCatching { context.startActivity(direct) }.isSuccess) return null

    val dir = File(context.cacheDir, "eksport").apply { mkdirs() }
    val dest = File(dir, "podglad.pdf")
    val copied = runCatching {
        context.contentResolver.openInputStream(source)?.use { input ->
            dest.outputStream().use { input.copyTo(it) }
        } != null
    }.getOrDefault(false)
    if (!copied) return words.fileDidNotOpenAbout

    val share = FileProvider.getUriForFile(context, "${context.packageName}.pliki", dest)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(share, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val outcome = runCatching { context.startActivity(intent) }
    return outcome.exceptionOrNull()?.let {
        Log.w("Kajet", "Otwieranie PDF poza Kajetem nie wyszło", it)
        words.nothingOpensFile(name)
    }
}
