package wojtoteka.ovh.kajet.storage

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.FileNotFoundException

/**
 * Magazyn dokumentów nad zwykłym katalogiem - ta sama droga przez SAF, którą
 * aplikacja idzie na urządzeniu, tylko bez urządzenia.
 *
 * Zwykły ContentProvider, który sam rozkłada adresy SAF (drzewo, dokument,
 * dzieci) - Robolectric woła starą postać `query`, a systemowy
 * DocumentsProvider jej celowo nie obsługuje.
 *
 * Identyfikatory są ścieżkami, jak w magazynie Androida. Licznik [queries]
 * mówi, ile razy ktoś zapytał o spis albo o pojedynczy wpis - po nim widać,
 * czy wejście do folderu nie zaczęło znowu pytać o każdy plik osobno.
 */
class FakeDocumentsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val documentId = DocumentsContract.getDocumentId(uri)
        return if (uri.lastPathSegment == "children") {
            queryChildDocuments(documentId, projection, sortOrder)
        } else {
            queryDocument(documentId, projection)
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        queryArgs: Bundle?,
        cancellationSignal: CancellationSignal?,
    ): Cursor = query(uri, projection, null, null, null)

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        openDocument(DocumentsContract.getDocumentId(uri), mode, null)

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val target = extras?.getParcelable<Uri>(EXTRA_URI) ?: return null
        val documentId = DocumentsContract.getDocumentId(target)
        val out = Bundle()
        when (method) {
            "android:createDocument" -> {
                val created = createDocument(
                    documentId,
                    extras.getString(Document.COLUMN_MIME_TYPE).orEmpty(),
                    extras.getString(Document.COLUMN_DISPLAY_NAME).orEmpty(),
                )
                out.putParcelable(EXTRA_URI, DocumentsContract.buildDocumentUriUsingTree(target, created))
            }
            "android:renameDocument" -> {
                val renamed = renameDocument(documentId, extras.getString(Document.COLUMN_DISPLAY_NAME).orEmpty())
                out.putParcelable(EXTRA_URI, DocumentsContract.buildDocumentUriUsingTree(target, renamed))
            }
            "android:deleteDocument" -> deleteDocument(documentId)
            else -> return null
        }
        return out
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun fileOf(documentId: String): File =
        if (documentId == ROOT_ID) base else File(base, documentId.removePrefix("$ROOT_ID/"))

    private fun idOf(file: File): String =
        if (file == base) ROOT_ID else ROOT_ID + "/" + file.relativeTo(base).invariantSeparatorsPath

    private fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        queries += 1
        val file = fileOf(documentId)
        val cursor = MatrixCursor(projection ?: COLUMNS)
        if (file.exists()) addRow(cursor, file)
        return cursor
    }

    private fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        queries += 1
        val cursor = MatrixCursor(projection ?: COLUMNS)
        val folder = fileOf(parentDocumentId)
        if (!folder.isDirectory) throw FileNotFoundException(parentDocumentId)
        if (failListing?.invoke(folder) == true) throw IllegalStateException("magazyn nie odpowiada")
        folder.listFiles()?.sortedBy { it.name }?.forEach { addRow(cursor, it) }
        return cursor
    }

    private fun addRow(cursor: MatrixCursor, file: File) {
        cursor.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, idOf(file))
            .add(Document.COLUMN_DISPLAY_NAME, file.name)
            .add(Document.COLUMN_MIME_TYPE, mimeOf(file))
            .add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
            .add(Document.COLUMN_SIZE, if (file.isDirectory) 0L else file.length())
            .add(
                Document.COLUMN_FLAGS,
                Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE or
                    Document.FLAG_SUPPORTS_RENAME or Document.FLAG_DIR_SUPPORTS_CREATE,
            )
    }

    private fun mimeOf(file: File): String {
        if (file.isDirectory) return Document.MIME_TYPE_DIR
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    private fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val file = fileOf(documentId)
        if (!file.isFile) throw FileNotFoundException(documentId)
        if (openDelayMs > 0) Thread.sleep(openDelayMs)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    // Jak magazyn Androida: zajęta nazwa dostaje licznik, a nie błąd.
    private fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = fileOf(parentDocumentId)
        var target = File(parent, displayName)
        var number = 1
        while (target.exists()) {
            target = File(parent, "$displayName ($number)")
            number += 1
        }
        if (mimeType == Document.MIME_TYPE_DIR) target.mkdir() else target.createNewFile()
        return idOf(target)
    }

    private fun deleteDocument(documentId: String) {
        val file = fileOf(documentId)
        if (!file.exists() || !file.deleteRecursively()) throw FileNotFoundException(documentId)
    }

    private fun renameDocument(documentId: String, displayName: String): String {
        val file = fileOf(documentId)
        val target = File(file.parentFile, displayName)
        if (target.exists() || !file.renameTo(target)) throw IllegalStateException("rename $documentId")
        return idOf(target)
    }

    companion object {
        const val AUTHORITY = "wojtoteka.ovh.kajet.test.dokumenty"
        const val ROOT_ID = "korzen"

        private const val EXTRA_URI = "uri"

        private val COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_SIZE,
            Document.COLUMN_FLAGS,
        )

        lateinit var base: File

        @Volatile
        var queries = 0

        /** Tyle trwa otwarcie każdego pliku - jak wolny magazyn na telefonie. */
        @Volatile
        var openDelayMs = 0L

        /** Katalogi, których spisu magazyn „nie odda" - jak przy chwilowej awarii. */
        @Volatile
        var failListing: ((File) -> Boolean)? = null

        /** Rejestruje magazyn nad [directory] i oddaje drzewo, jak po wyborze folderu w SAF. */
        fun install(directory: File): DocumentFile {
            base = directory
            queries = 0
            failListing = null
            openDelayMs = 0L
            val info = ProviderInfo().apply {
                authority = AUTHORITY
                exported = true
                grantUriPermissions = true
            }
            Robolectric.buildContentProvider(FakeDocumentsProvider::class.java).create(info)
            val tree: Uri = DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT_ID)
            return DocumentFile.fromTreeUri(RuntimeEnvironment.getApplication(), tree)
                ?: error("brak drzewa")
        }
    }
}
