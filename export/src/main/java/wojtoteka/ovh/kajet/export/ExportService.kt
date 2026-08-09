package wojtoteka.ovh.kajet.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wojtoteka.ovh.kajet.core.model.ItemType
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.text.Strings
import wojtoteka.ovh.kajet.core.text.nothingOpensFile
import wojtoteka.ovh.kajet.core.text.words
import wojtoteka.ovh.kajet.storage.FileNames
import wojtoteka.ovh.kajet.storage.LibraryRepository
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportFormat(
    val labelPl: String,
    val descriptionPl: String,
    val extension: String,
    val mime: String,
) {
    PDF(
        labelPl = "PDF",
        descriptionPl = "Wygląda dokładnie tak jak na ekranie. Nadaje się do druku i do wysłania.",
        extension = "pdf",
        mime = "application/pdf",
    ),
    DOCX(
        labelPl = "Dokument Word",
        descriptionPl = "Tekst do dalszej pracy. Bez zdjęć i bez pisma odręcznego.",
        extension = "docx",
        mime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    ),
    MARKDOWN(
        labelPl = "Markdown",
        descriptionPl = "Czysty tekst ze znacznikami. Otworzysz go w każdym edytorze.",
        extension = "md",
        mime = "text/markdown",
    ),
    PNG(
        labelPl = "Obrazek PNG",
        descriptionPl = "Pierwsza strona jako obrazek. Dobre do wklejenia w wiadomość.",
        extension = "png",
        mime = "image/png",
    ),
    ;

    fun label(words: Strings): String = if (!words.english) labelPl else when (this) {
        PDF -> "PDF"
        DOCX -> "Word document"
        MARKDOWN -> "Markdown"
        PNG -> "PNG image"
    }

    fun description(words: Strings): String = if (!words.english) descriptionPl else when (this) {
        PDF -> "Looks exactly as it does on screen. Good for printing and sending."
        DOCX -> "Text to keep working on. No pictures, no handwriting."
        MARKDOWN -> "Plain text with markers. Opens in any editor."
        PNG -> "The first page as a picture. Good for pasting into a message."
    }
}

class ExportService(
    private val context: Context,
    private val repo: LibraryRepository,
) {

    // Katalog musi nazywać się tak samo jak w res/xml/sciezki_plikow.xml.
    // Rozjazd tych dwóch nazw wywala aplikację przy „Udostępnij".
    private fun exportDir(): File = File(context.cacheDir, "eksport").apply { mkdirs() }

    suspend fun export(
        notePath: String,
        document: NoteDocument,
        format: ExportFormat,
    ): File = withContext(Dispatchers.IO) {
        val name = FileNames.safe(document.title) + "." + format.extension
        val file = File(exportDir(), name)

        FileOutputStream(file).use { output ->
            when (format) {
                ExportFormat.PDF -> PdfExport.write(
                    document = document,
                    output = output,
                    attachment = { assetName -> readAttachmentBlocking(notePath, assetName) },
                )

                ExportFormat.DOCX -> DocxExport.write(document, output)

                ExportFormat.MARKDOWN -> output.write(
                    MarkdownExport.convert(document).toByteArray(Charsets.UTF_8),
                )

                ExportFormat.PNG -> {
                    val page = document.handwriting?.pages?.firstOrNull()
                    val bitmap = if (page != null) {
                        PdfExport.pageAsPng(page)
                    } else {
                        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                    }
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                    bitmap.recycle()
                }
            }
        }
        file
    }

    suspend fun exportFolder(
        folderPath: String,
        format: ExportFormat,
        progress: ((done: Int, total: Int) -> Unit)? = null,
    ): File = withContext(Dispatchers.IO) {
        val store = repo.store()
            ?: throw java.io.IOException(words.noNotesFolderPicked)

        val notes = mutableListOf<String>()
        fun walk(path: String) {
            for (item in store.list(path)) {
                when (item.type) {
                    ItemType.FOLDER -> walk(item.path)
                    ItemType.NOTE -> notes += item.path
                    else -> Unit
                }
            }
        }
        walk(folderPath)

        val folderName = folderPath.substringAfterLast('/').ifEmpty { words.libraryFolderName }
        val file = File(exportDir(), FileNames.safe(folderName) + ".zip")

        ZipOutputStream(file.outputStream()).use { zip ->
            notes.forEachIndexed { index, notePath ->
                val document = runCatching { store.readNote(notePath) }.getOrNull()
                if (document != null) {
                    val relative = notePath.removePrefix(folderPath).trimStart('/')
                    val entry = relative.removeSuffix(".note") + "." + format.extension
                    zip.putNextEntry(ZipEntry(entry))
                    when (format) {
                        ExportFormat.PDF -> PdfExport.write(
                            document = document,
                            output = zip,
                            attachment = { assetName -> readAttachmentBlocking(notePath, assetName) },
                        )
                        ExportFormat.DOCX -> DocxExport.write(document, zip)
                        ExportFormat.MARKDOWN -> zip.write(
                            MarkdownExport.convert(document).toByteArray(Charsets.UTF_8),
                        )
                        ExportFormat.PNG -> {
                            val page = document.handwriting?.pages?.firstOrNull()
                            if (page != null) {
                                val bitmap = PdfExport.pageAsPng(page)
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)
                                bitmap.recycle()
                            }
                        }
                    }
                    zip.closeEntry()
                }
                progress?.invoke(index + 1, notes.size)
            }
        }
        file
    }

    private fun readAttachmentBlocking(notePath: String, name: String): ByteArray? =
        kotlinx.coroutines.runBlocking { repo.readAttachment(notePath, name) }

    fun fileUri(file: File) = FileProvider.getUriForFile(context, "${context.packageName}.pliki", file)

    /*
     * Udostępnianie i otwieranie ruszają z kontekstu EKRANU, nie aplikacji.
     * Start z kontekstu aplikacji wymaga osobnego zadania, a nowsze Androidy
     * potrafią taki start po cichu zdusić — okno „Udostępnij" po prostu się
     * nie pokazuje, bez żadnego wyjątku. Druk przeszedł tę samą drogę.
     */

    /**
     * Podaje dalej sam tekst — u nas odnośnik do notatki w chmurze. Ta sama
     * droga co przy pliku (systemowe „Udostępnij"), tylko bez załącznika:
     * odnośnik wkleja się wprost w wiadomość.
     * Zwraca null, gdy się udało, albo zdanie dla człowieka, gdy nie.
     */
    fun shareText(activityContext: Context, text: String, title: String): String? {
        val outcome = runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, title)
            }
            activityContext.startActivity(Intent.createChooser(intent, "${words.sendLink}: $title"))
        }
        return outcome.exceptionOrNull()?.let {
            Log.w("Kajet", "Udostępnianie odnośnika nie wyszło", it)
            "${words.shareWindowFailed} $text"
        }
    }

    /** Zwraca null, gdy się udało, albo zdanie dla człowieka, gdy nie. */
    fun share(activityContext: Context, file: File, mime: String, title: String): String? {
        val outcome = runCatching {
            val uri = fileUri(file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // Chooser nie przepisuje grantu z intencji wewnętrznej, a panel
            // udostępniania czyta plik we własnym procesie, żeby pokazać
            // podgląd. ClipData niesie grant także dla niego.
            val chooser = Intent.createChooser(intent, "${words.sendFile}: $title").apply {
                clipData = ClipData.newRawUri(title, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activityContext.startActivity(chooser)
        }
        return outcome.exceptionOrNull()?.let {
            Log.w("Kajet", "Udostępnianie pliku nie wyszło", it)
            words.shareWindowFailedFile
        }
    }

    /** Zwraca null, gdy się udało, albo zdanie dla człowieka, gdy nie. */
    fun open(activityContext: Context, file: File, mime: String): String? {
        val outcome = runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri(file), mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activityContext.startActivity(intent)
        }
        return outcome.exceptionOrNull()?.let {
            Log.w("Kajet", "Otwieranie pliku nie wyszło", it)
            words.nothingOpensFile(file.name)
        }
    }

    /**
     * [activityContext] musi pochodzić z ekranu (LocalContext), nie z aplikacji:
     * systemowy druk odmawia pracy bez Activity.
     */
    fun print(activityContext: Context, document: NoteDocument, notePath: String): String? {
        val manager = activityContext.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: return words.noSystemPrinting
        val name = FileNames.safe(document.title)
        val outcome = runCatching {
            manager.print(
                name,
                PrintAdapter(name) { output ->
                    PdfExport.write(
                        document = document,
                        output = output,
                        attachment = { assetName -> readAttachmentBlocking(notePath, assetName) },
                    )
                },
                PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build(),
            )
        }
        return outcome.exceptionOrNull()?.let {
            // Treść wyjątku zostaje w dzienniku: na ekranie było z niej
            // angielskie zdanie o wskaźniku na null.
            Log.w("Kajet", "Drukowanie nie ruszyło", it)
            words.printFailed
        }
    }
}

private class PrintAdapter(
    private val name: String,
    private val write: (java.io.OutputStream) -> Unit,
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder("$name.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        if (destination == null) {
            callback.onWriteFailed(words.printOpenFailed)
            return
        }
        try {
            java.io.FileOutputStream(destination.fileDescriptor).use { output -> write(output) }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message ?: words.printGaveUp)
        }
    }
}
