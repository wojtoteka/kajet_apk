package wojtoteka.ovh.kajet.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
}

class ExportService(
    private val context: Context,
    private val repo: LibraryRepository,
) {

    private fun exportDir(): File = File(context.cacheDir, "export").apply { mkdirs() }

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
            ?: throw java.io.IOException("Nie wybrano katalogu na notatki.")

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

        val folderName = folderPath.substringAfterLast('/').ifEmpty { "Biblioteka" }
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

    fun share(file: File, mime: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, fileUri(file))
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Wyślij: $title").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun open(file: File, mime: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri(file), mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun print(document: NoteDocument, notePath: String) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val name = FileNames.safe(document.title)
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
            callback.onWriteFailed("Nie udało się otworzyć pliku do wydruku.")
            return
        }
        try {
            java.io.FileOutputStream(destination.fileDescriptor).use { output -> write(output) }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message ?: "Wydruk się nie udał.")
        }
    }
}
