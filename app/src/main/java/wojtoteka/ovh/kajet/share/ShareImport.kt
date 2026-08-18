package wojtoteka.ovh.kajet.share

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import wojtoteka.ovh.kajet.core.model.LibraryItem
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.model.TextContent
import wojtoteka.ovh.kajet.core.text.words
import wojtoteka.ovh.kajet.storage.FileNames
import wojtoteka.ovh.kajet.storage.LibraryRepository
import java.io.IOException

/**
 * Kopia udostępnionego pliku (albo treść z EXTRA_TEXT) do katalogu notatek.
 *
 * Potem nawigacja idzie przez istniejące `open()`: notatka, edytor kodu,
 * podgląd zdjęcia / PDF albo komunikat przy binarce.
 */
object ShareImport {

    suspend fun intoLibrary(
        context: Context,
        repo: LibraryRepository,
        share: IncomingShare,
        parent: String,
    ): LibraryItem {
        if (share.uris.isNotEmpty()) {
            val imported = mutableListOf<LibraryItem>()
            var lastProblem: String? = null
            for (uri in share.uris) {
                val mime = mimeFor(context, uri, share.mime)
                val name = fileNameFor(context, uri, mime)
                runCatching { repo.importFile(parent, name, mime, uri) }
                    .onSuccess { imported += it }
                    .onFailure { lastProblem = it.message }
            }
            return imported.firstOrNull()
                ?: throw IOException(lastProblem ?: words.couldNotImportShare)
        }

        val body = share.text.orEmpty()
        if (body.isBlank()) throw IOException(words.couldNotImportShare)
        val title = sharedNoteTitle(share.subject, body, words.untitled)
        val item = repo.createNote(parent, title, NoteKind.TEXT)
        val document = repo.readNote(item.path)
        val text = (document.text ?: TextContent()).copy(markdown = body)
        repo.writeNote(item.path, document.copy(text = text))
        return item
    }
}

fun sharedNoteTitle(subject: String?, text: String, untitled: String): String {
    val heading = subject?.trim().orEmpty()
    if (heading.isNotEmpty()) return heading.take(FileNames.MAX_LENGTH)
    val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
    if (line.isNotEmpty()) return line.take(FileNames.MAX_LENGTH)
    return untitled
}

internal fun fileNameFor(context: Context, uri: Uri, mime: String?): String {
    val raw = queryDisplayName(context, uri)
        ?: uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() && ':' !in it }
        ?: words.unnamed
    return FileNames.withMimeExtension(raw, mime)
}

internal fun mimeFor(context: Context, uri: Uri, intentMime: String?): String {
    val fromUri = context.contentResolver.getType(uri)
        ?.takeIf { it.isNotBlank() && it != "*/*" && '*' !in it }
    if (fromUri != null) return fromUri
    val fromName = mimeFromFileName(
        queryDisplayName(context, uri) ?: uri.lastPathSegment.orEmpty(),
    )
    if (fromName != null) return fromName
    val fromIntent = intentMime?.substringBefore(';')?.trim()
        ?.takeIf { it.isNotBlank() && it != "*/*" && '*' !in it }
    return fromIntent ?: "application/octet-stream"
}

private fun mimeFromFileName(name: String): String? {
    val extension = name.substringAfterLast('.', "").lowercase()
    if (extension.isEmpty() || extension == name.lowercase()) return null
    return when (extension) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        "txt", "log" -> "text/plain"
        "html", "htm" -> "text/html"
        "csv" -> "text/csv"
        "json" -> "application/json"
        else -> null
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index < 0) null else cursor.getString(index)?.takeIf { it.isNotBlank() }
        }
    }.getOrNull()
