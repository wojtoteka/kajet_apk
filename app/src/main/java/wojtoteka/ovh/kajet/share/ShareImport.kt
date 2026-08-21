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
 *
 * Przy wielu URI pierwsza udana kopia idzie do otwarcia, a [ShareImportOutcome.failed]
 * mówi, ile plików nie weszło - żeby dało się pokazać częściową porażkę.
 */
data class ShareImportOutcome(
    val item: LibraryItem,
    val failed: Int = 0,
)

object ShareImport {

    suspend fun intoLibrary(
        context: Context,
        repo: LibraryRepository,
        share: IncomingShare,
        parent: String,
    ): ShareImportOutcome {
        if (share.uris.isNotEmpty()) {
            return importEach(share.uris) { uri ->
                val mime = mimeFor(context, uri, share.mime)
                val name = fileNameFor(context, uri, mime)
                repo.importFile(parent, name, mime, uri)
            }
        }

        val body = share.text.orEmpty()
        if (body.isBlank()) throw IOException(words.couldNotImportShare)
        val title = sharedNoteTitle(share.subject, body, words.untitled)
        val item = repo.createNote(parent, title, NoteKind.TEXT)
        val document = repo.readNote(item.path)
        val text = (document.text ?: TextContent()).copy(markdown = body)
        repo.writeNote(item.path, document.copy(text = text))
        return ShareImportOutcome(item)
    }
}

/**
 * Kopiuje po kolei. Jedna porażka nie zatrzymuje reszty. Same porażki
 * wychodzą jako [IOException]; mieszanka zwraca pierwszy sukces i liczbę
 * nieudanych.
 */
internal suspend fun <T> importEach(
    sources: List<T>,
    copy: suspend (T) -> LibraryItem,
): ShareImportOutcome {
    val imported = mutableListOf<LibraryItem>()
    var lastProblem: String? = null
    for (source in sources) {
        runCatching { copy(source) }
            .onSuccess { imported += it }
            .onFailure { lastProblem = it.message }
    }
    val item = imported.firstOrNull()
        ?: throw IOException(lastProblem ?: words.couldNotImportShare)
    return ShareImportOutcome(item, failed = sources.size - imported.size)
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
