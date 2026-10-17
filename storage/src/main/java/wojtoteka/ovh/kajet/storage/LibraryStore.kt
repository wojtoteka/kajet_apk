package wojtoteka.ovh.kajet.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import wojtoteka.ovh.kajet.core.text.words
import wojtoteka.ovh.kajet.core.model.CodeLanguage
import wojtoteka.ovh.kajet.core.model.DrawingSource
import wojtoteka.ovh.kajet.core.model.FolderMeta
import wojtoteka.ovh.kajet.core.model.HandwritingContent
import wojtoteka.ovh.kajet.core.model.ItemType
import wojtoteka.ovh.kajet.core.model.LibraryItem
import wojtoteka.ovh.kajet.core.model.MindMapContent
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.model.NotePage
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode
import wojtoteka.ovh.kajet.core.model.TextContent
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import wojtoteka.ovh.kajet.core.text.entryGone
import wojtoteka.ovh.kajet.core.text.fileGone
import wojtoteka.ovh.kajet.core.text.fileGoneFromBin
import wojtoteka.ovh.kajet.core.text.fileGoneUnsaved
import wojtoteka.ovh.kajet.core.text.folderCreateFailed
import wojtoteka.ovh.kajet.core.text.folderGone
import wojtoteka.ovh.kajet.core.text.noteGone
import wojtoteka.ovh.kajet.core.text.noteGoneUnsaved
import wojtoteka.ovh.kajet.core.text.noteUnreadable
import wojtoteka.ovh.kajet.core.text.renameFailed

/** Zapisana notatka: adres jej katalogu i treść z datą, która trafiła na dysk. */
data class SavedNote(
    val documentUri: String,
    val document: NoteDocument,
)

/**
 * Co leży pod ścieżką. „Nie ma" i „nie wiadomo" to dwie różne odpowiedzi:
 * pierwsza pozwala posprzątać po wpisie, druga każe niczego nie ruszać.
 */
internal sealed interface PathLookup {
    class Found(val entry: DiskEntry) : PathLookup
    data object Missing : PathLookup
    data object Unknown : PathLookup
}

/** Zawartość folderu z dysku - z tym samym rozróżnieniem co [PathLookup]. */
internal sealed interface FolderListing {
    /** [complete] fałszywe, gdy któregoś podfolderu nie dało się zajrzeć do środka. */
    class Found(val items: List<LibraryItem>, val complete: Boolean) : FolderListing
    data object Missing : FolderListing
    data object Unknown : FolderListing
}

/** Opisy folderów z jednego przejścia po drzewie, patrz [LibraryStore.allFolderMetas]. */
internal class FolderMetas(
    val byPath: Map<String, FolderMeta>,
    val complete: Boolean,
)

class LibraryStore(
    private val resolver: ContentResolver,
    val root: DocumentFile,
) {

    val rootUri: String get() = root.uri.toString()

    private val rootEntry = DiskEntry(
        uri = root.uri,
        name = "",
        mime = DocumentsContract.Document.MIME_TYPE_DIR,
        lastModified = 0L,
        size = 0L,
    )

    /*
      Opisy folderów (folder.json) z ostatniego odczytu.

      Każde wejście do folderu czytało folder.json każdego podfolderu od nowa,
      choć te pliki zmieniają się tylko przy zmianie nazwy albo wyglądu. Wpis
      jest ważny, dopóki plik ma tę samą datę i ten sam rozmiar, a własny zapis
      wyrzuca go od razu.
    */
    private class CachedMeta(val lastModified: Long, val size: Long, val meta: FolderMeta)

    private val folderMetas = ConcurrentHashMap<String, CachedMeta>()

    /*
      Identyfikatory notatek z każdego wpisu kosza.

      Synchronizacja pyta o zawartość kosza przy każdym przebiegu, a żeby
      poznać identyfikator, trzeba przeczytać plik notatki. Wpis kosza się
      nie zmienia - zmienia się tylko przez kasowanie z niego, które tę
      pamięć czyści - więc wystarczy przeczytać go raz.
    */
    private val slotNoteIds = ConcurrentHashMap<String, List<String>>()

    // Finding places in the tree

    fun folder(path: String): DocumentFile? {
        if (path.isBlank()) return root
        var current: DocumentFile = root
        for (part in path.split('/')) {
            if (part.isEmpty()) continue
            val next = DiskFiles.locate(resolver, current, part) ?: return null
            if (!next.entry.isDirectory) return null
            current = next.file
        }
        return current
    }

    fun entry(path: String): DocumentFile? = located(path)?.file

    /** Wpis pod ścieżką razem z jego opisem - do zmian na dysku. */
    internal fun located(path: String): Located? {
        if (path.isBlank()) return Located(root, rootEntry)
        val parent = folder(path.substringBeforeLast('/', "")) ?: return null
        return DiskFiles.locate(resolver, parent, path.substringAfterLast('/'))
    }

    /**
     * Wpis pod ścieżką, tylko do czytania - jedno zapytanie na poziom drzewa,
     * bez obiektów DocumentFile, które są potrzebne dopiero do zmian.
     */
    internal fun lookup(path: String): PathLookup {
        var current = rootEntry
        for (part in path.split('/')) {
            if (part.isEmpty()) continue
            if (!current.isDirectory) return PathLookup.Missing
            val inside = DiskFiles.children(resolver, current.uri) ?: return PathLookup.Unknown
            current = inside.firstOrNull { it.name == part } ?: return PathLookup.Missing
        }
        return PathLookup.Found(current)
    }

    internal fun describe(path: String): DiskEntry? = (lookup(path) as? PathLookup.Found)?.entry

    private fun noteFolder(path: String): DocumentFile? =
        located(path)?.takeIf { it.entry.isDirectory }?.file

    private fun requireFolder(path: String): DocumentFile =
        folder(path) ?: throw IOException(
            words.folderGone(path),
        )

    // Reading the content of a folder

    fun list(path: String): List<LibraryItem> =
        (listing(path) as? FolderListing.Found)?.items.orEmpty()

    internal fun listing(path: String): FolderListing {
        val folder = when (val found = lookup(path)) {
            is PathLookup.Found -> found.entry.takeIf { it.isDirectory } ?: return FolderListing.Missing
            PathLookup.Missing -> return FolderListing.Missing
            PathLookup.Unknown -> return FolderListing.Unknown
        }
        val inside = DiskFiles.children(resolver, folder.uri) ?: return FolderListing.Unknown
        var complete = true
        val items = inside.mapNotNull { child ->
            if (child.isDirectory && !FileNames.isNote(child.name) && !FileNames.isHidden(child.name)) {
                val nested = DiskFiles.children(resolver, child.uri)
                if (nested == null) complete = false
                folderItem(child, path, nested)
            } else {
                toItem(child, path)
            }
        }
        return FolderListing.Found(items.sortedWith(libraryOrder), complete)
    }

    /** Jeden wpis pod ścieżką w tym samym kształcie co w spisie folderu. */
    internal fun item(path: String): LibraryItem? {
        val entry = describe(path) ?: return null
        return toItem(entry, path.substringBeforeLast('/', ""))
    }

    private fun toItem(file: DiskEntry, parentPath: String): LibraryItem? {
        val name = file.name
        if (FileNames.isHidden(name)) return null
        val path = childPath(parentPath, name)

        return when {
            file.isDirectory && FileNames.isNote(name) -> {
                LibraryItem(
                    id = file.uri.toString(),
                    path = path,
                    name = FileNames.withoutNoteExtension(name),
                    type = ItemType.NOTE,
                    documentUri = file.uri.toString(),
                    updatedAt = file.lastModified,
                    noteKind = null,
                )
            }

            file.isDirectory -> folderItem(file, parentPath, DiskFiles.children(resolver, file.uri))

            else -> {
                val language = CodeLanguage.fromExtension(name)
                if (name == FolderMeta.FILE) return null
                LibraryItem(
                    id = file.uri.toString(),
                    path = path,
                    name = name,
                    type = if (language != null) ItemType.CODE_FILE else ItemType.OTHER_FILE,
                    documentUri = file.uri.toString(),
                    updatedAt = file.lastModified,
                    language = language,
                )
            }
        }
    }

    /** [inside] null - zawartości nie dało się przeczytać; folder bez opisu i licznika. */
    private fun folderItem(file: DiskEntry, parentPath: String, inside: List<DiskEntry>?): LibraryItem {
        val meta = inside?.let(::folderMetaIn)
        return LibraryItem(
            id = meta?.id ?: file.uri.toString(),
            path = childPath(parentPath, file.name),
            name = meta?.displayName ?: file.name,
            type = ItemType.FOLDER,
            documentUri = file.uri.toString(),
            updatedAt = file.lastModified,
            colorId = meta?.colorId,
            iconId = meta?.iconId,
            // Liczymy to, co widać w folderze. Plik z jego barwą i ikoną
            // to nie wpis - przez niego pusty folder meldował „1 wpis".
            childCount = inside?.count { child ->
                !FileNames.isHidden(child.name) && child.name != FolderMeta.FILE
            } ?: 0,
        )
    }

    // Folders

    fun readFolderMeta(folder: DocumentFile): FolderMeta? =
        DiskFiles.children(resolver, folder.uri)?.let(::folderMetaIn)

    private fun folderMetaIn(inside: List<DiskEntry>): FolderMeta? {
        val file = inside.firstOrNull { it.name == FolderMeta.FILE }?.takeIf { it.isFile } ?: return null
        val key = file.uri.toString()
        folderMetas[key]?.let { cached ->
            if (cached.lastModified > 0L && cached.lastModified == file.lastModified && cached.size == file.size) {
                return cached.meta
            }
        }
        // Nieudany odczyt nie trafia do pamięci - chwilowy kłopot z magazynem
        // nie może zostawić folderu bez nazwy i barwy aż do następnej zmiany.
        val meta = runCatching { NoteCodec.decodeFolder(DiskFiles.readText(resolver, file)) }.getOrNull()
            ?: return null
        if (folderMetas.size > MAX_REMEMBERED) folderMetas.clear()
        folderMetas[key] = CachedMeta(file.lastModified, file.size, meta)
        return meta
    }

    fun writeFolderMeta(folder: DocumentFile, meta: FolderMeta) {
        val file = DiskFiles.fileForWrite(resolver, folder, FolderMeta.FILE, DiskFiles.MIME_JSON)
        DiskFiles.writeText(resolver, file, NoteCodec.encodeFolder(meta))
        folderMetas.remove(file.uri.toString())
    }

    fun createFolder(
        parentPath: String,
        name: String,
        colorId: String = "grafit",
        iconId: String = "folder",
        // Folder przysłany z chmury przynosi swój identyfikator - ten sam,
        // pod którym żyje na serwerze i na innych urządzeniach.
        id: String? = null,
    ): LibraryItem {
        val parent = requireFolder(parentPath)
        val nameOnDisk = FileNames.unique(name, DiskFiles.occupiedNames(resolver, parent))
        val folder = parent.createDirectory(nameOnDisk)
            ?: throw IOException(words.folderCreateFailed(name))

        val now = System.currentTimeMillis()
        val meta = FolderMeta(
            id = id ?: UUID.randomUUID().toString(),
            displayName = name.trim().ifEmpty { nameOnDisk },
            colorId = colorId,
            iconId = iconId,
            createdAt = now,
            modifiedAt = now,
        )
        writeFolderMeta(folder, meta)

        val path = childPath(parentPath, nameOnDisk)
        return LibraryItem(
            id = meta.id,
            path = path,
            name = meta.displayName,
            type = ItemType.FOLDER,
            documentUri = folder.uri.toString(),
            updatedAt = folder.lastModified(),
            colorId = colorId,
            iconId = iconId,
        )
    }

    fun updateFolderLook(path: String, colorId: String, iconId: String) {
        val folder = requireFolder(path)
        val previous = readFolderMeta(folder) ?: FolderMeta(
            id = UUID.randomUUID().toString(),
            displayName = folder.name.orEmpty(),
            createdAt = System.currentTimeMillis(),
        )
        writeFolderMeta(
            folder,
            previous.copy(colorId = colorId, iconId = iconId, modifiedAt = System.currentTimeMillis()),
        )
    }

    // Notes

    fun createNote(
        parentPath: String,
        title: String,
        kind: NoteKind,
        pageMode: PageMode = PageMode.A4,
        background: PageBackground = PageBackground.LINED,
    ): LibraryItem {
        val parent = requireFolder(parentPath)
        val nameOnDisk = FileNames.unique(
            name = title,
            occupied = DiskFiles.occupiedNames(resolver, parent),
            extension = NoteDocument.EXTENSION,
        )
        val folder = parent.createDirectory(nameOnDisk)
            ?: throw IOException(words.folderCreateFailed(words.newNote))

        val now = System.currentTimeMillis()
        val document = NoteDocument(
            id = UUID.randomUUID().toString(),
            kind = kind,
            title = title.trim().ifEmpty { words.untitled },
            createdAt = now,
            updatedAt = now,
            handwriting = if (kind == NoteKind.HANDWRITTEN) {
                HandwritingContent(
                    pageMode = pageMode,
                    background = background,
                    pages = listOf(newPage(pageMode)),
                )
            } else {
                null
            },
            text = if (kind == NoteKind.TEXT) TextContent() else null,
            mindMap = if (kind == NoteKind.MINDMAP) MindMapContent() else null,
        )
        writeDocument(folder, document)

        val path = childPath(parentPath, nameOnDisk)
        return LibraryItem(
            id = document.id,
            path = path,
            name = FileNames.withoutNoteExtension(nameOnDisk),
            type = ItemType.NOTE,
            documentUri = folder.uri.toString(),
            updatedAt = now,
            noteKind = kind,
        )
    }

    private fun newPage(mode: PageMode): NotePage = NotePage(
        id = UUID.randomUUID().toString(),
        width = NotePage.A4_WIDTH,
        height = if (mode == PageMode.A4) NotePage.A4_HEIGHT else NotePage.SCROLL_STEP,
    )

    fun readNote(path: String): NoteDocument {
        val folder = describe(path)?.takeIf { it.isDirectory }
            ?: throw IOException(words.noteGone(path))
        return readDocumentIn(folder.uri) { folder.name }
    }

    /** Notatka po adresie katalogu ze spisu - bez schodzenia po ścieżce. */
    internal fun readNoteAt(documentUri: String): NoteDocument {
        val uri = Uri.parse(documentUri)
        return readDocumentIn(uri) { uri.lastPathSegment }
    }

    fun readDocument(noteFolder: DocumentFile): NoteDocument =
        readDocumentIn(noteFolder.uri) { noteFolder.name }

    private fun readDocumentIn(noteFolder: Uri, folderName: () -> String?): NoteDocument {
        val inside = DiskFiles.children(resolver, noteFolder).orEmpty()
        for (name in CONTENT_FILES) {
            val file = inside.firstOrNull { it.name == name }?.takeIf { it.isFile } ?: continue
            val result = runCatching { NoteCodec.decodeNote(DiskFiles.readText(resolver, file)) }
            result.getOrNull()?.let { return it }
        }
        throw FormatException(
            words.noteUnreadable(folderName()),
        )
    }

    /**
     * Sam identyfikator notatki - bez budowania całej treści. Kosz pyta o to
     * przy każdej synchronizacji, a pełny odczyt notatki odręcznej to tysiące
     * punktów kreski, z których nic tu nie jest potrzebne.
     */
    private fun noteIdIn(noteFolder: Uri): String? {
        val inside = DiskFiles.children(resolver, noteFolder).orEmpty()
        for (name in CONTENT_FILES) {
            val file = inside.firstOrNull { it.name == name }?.takeIf { it.isFile } ?: continue
            runCatching { NoteCodec.decodeNoteId(DiskFiles.readText(resolver, file)) }.getOrNull()?.let { return it }
        }
        return null
    }

    fun writeNote(path: String, document: NoteDocument): SavedNote {
        val folder = noteFolder(path)
            ?: throw IOException(words.noteGoneUnsaved(path))
        return SavedNote(folder.uri.toString(), writeDocument(folder, document))
    }

    /** Zwraca treść w takiej postaci, w jakiej trafiła na dysk - z datą zapisu. */
    fun writeDocument(noteFolder: DocumentFile, document: NoteDocument): NoteDocument {
        val stamped = document.copy(updatedAt = System.currentTimeMillis())
        val content = NoteCodec.encodeNote(stamped)
        // Oba pliki leżą w tym samym katalogu, więc spis wystarczy na dwa zapisy.
        val inside = DiskFiles.children(resolver, noteFolder.uri)
        if (inside == null) {
            val backup = DiskFiles.fileForWrite(resolver, noteFolder, BACKUP_FILE, DiskFiles.MIME_JSON)
            DiskFiles.writeText(resolver, backup, content)
            val main = DiskFiles.fileForWrite(resolver, noteFolder, NoteDocument.CONTENT_FILE, DiskFiles.MIME_JSON)
            DiskFiles.writeText(resolver, main, content)
            return stamped
        }
        val backup = DiskFiles.uriForWrite(inside, noteFolder, BACKUP_FILE, DiskFiles.MIME_JSON)
        DiskFiles.writeText(resolver, backup, content, BACKUP_FILE)
        val main = DiskFiles.uriForWrite(inside, noteFolder, NoteDocument.CONTENT_FILE, DiskFiles.MIME_JSON)
        DiskFiles.writeText(resolver, main, content, NoteDocument.CONTENT_FILE)
        return stamped
    }

    // Note attachments

    fun assetsFolder(noteFolder: DocumentFile): DocumentFile =
        DiskFiles.folderForWrite(resolver, noteFolder, NoteDocument.ASSETS_DIRECTORY)

    fun writeAttachment(notePath: String, name: String, data: ByteArray, mime: String): String {
        val folder = noteFolder(notePath)
            ?: throw IOException(words.noteGone(notePath))
        val assets = assetsFolder(folder)
        val nameOnDisk = FileNames.unique(
            name = name.substringBeforeLast('.'),
            occupied = DiskFiles.occupiedNames(resolver, assets),
            extension = "." + name.substringAfterLast('.', "bin"),
        )
        val file = DiskFiles.createFile(assets, nameOnDisk, mime)
        DiskFiles.writeBytes(resolver, file, data)
        return file.name ?: nameOnDisk
    }

    /** Overwrite-or-create under the exact name (sync pull; markdown links stay valid). */
    fun putAttachment(notePath: String, name: String, data: ByteArray, mime: String) {
        val folder = noteFolder(notePath)
            ?: throw IOException(words.noteGone(notePath))
        val assets = assetsFolder(folder)
        val file = DiskFiles.fileForWrite(resolver, assets, name, mime)
        DiskFiles.writeBytes(resolver, file, data)
    }

    private fun attachment(notePath: String, name: String): DiskEntry? {
        // Nazwa z ukośnikiem nie jest nazwą pliku w assets - i nie może
        // sięgnąć do innego katalogu niż ten jeden.
        if (name.isEmpty() || name.contains('/')) return null
        return describe("$notePath/${NoteDocument.ASSETS_DIRECTORY}/$name")?.takeIf { it.isFile }
    }

    fun readAttachment(notePath: String, name: String): ByteArray? {
        val file = attachment(notePath, name) ?: return null
        return runCatching { DiskFiles.readBytes(resolver, file) }.getOrNull()
    }

    /** Nazwy plików w assets notatki. */
    internal fun attachmentNames(notePath: String): List<String> {
        val assets = describe("$notePath/${NoteDocument.ASSETS_DIRECTORY}")?.takeIf { it.isDirectory }
            ?: return emptyList()
        return DiskFiles.children(resolver, assets.uri).orEmpty().filter { it.isFile }.map { it.name }
    }

    /**
     * Kasuje załącznik notatki. Brak pliku to nie błąd - i tak go nie ma.
     * Zwraca, czy coś faktycznie zniknęło z dysku.
     */
    fun deleteAttachment(notePath: String, name: String): Boolean {
        val file = attachment(notePath, name) ?: return false
        return runCatching { DiskFiles.deleteDocument(resolver, file.uri) }.getOrDefault(false)
    }

    fun writeInlineDrawing(notePath: String, name: String, drawing: DrawingSource) {
        val folder = noteFolder(notePath)
            ?: throw IOException(words.noteGone(notePath))
        val assets = assetsFolder(folder)
        val file = DiskFiles.fileForWrite(resolver, assets, name, DiskFiles.MIME_JSON)
        DiskFiles.writeText(resolver, file, NoteCodec.encodeDrawing(drawing))
    }

    fun readInlineDrawing(notePath: String, name: String): DrawingSource? {
        val data = readAttachment(notePath, name) ?: return null
        return runCatching { NoteCodec.decodeDrawing(data.toString(Charsets.UTF_8)) }.getOrNull()
    }

    // Text files and code files

    fun createCodeFile(parentPath: String, name: String, language: CodeLanguage): LibraryItem {
        val parent = requireFolder(parentPath)
        val extension = "." + language.extensions.first()
        val fullName = if (name.endsWith(extension)) name else name + extension
        val nameOnDisk = FileNames.unique(
            name = fullName.substringBeforeLast('.'),
            occupied = DiskFiles.occupiedNames(resolver, parent),
            extension = extension,
        )
        val file = DiskFiles.createFile(parent, nameOnDisk, DiskFiles.MIME_TEXT)
        DiskFiles.writeText(resolver, file, codeTemplate(language))

        val path = childPath(parentPath, nameOnDisk)
        return LibraryItem(
            id = file.uri.toString(),
            path = path,
            name = file.name ?: nameOnDisk,
            type = ItemType.CODE_FILE,
            documentUri = file.uri.toString(),
            updatedAt = file.lastModified(),
            language = language,
        )
    }

    /**
     * Nowy plik tekstowy o zadanej nazwie i treści - dla plików z kodem
     * przyjeżdżających z chmury. Przy zajętej nazwie dokleja licznik.
     */
    fun createTextFile(parentPath: String, fileName: String, content: String): LibraryItem {
        val parent = requireFolder(parentPath)
        val extension = if (fileName.contains('.')) "." + fileName.substringAfterLast('.') else ""
        val nameOnDisk = FileNames.unique(
            name = fileName.removeSuffix(extension),
            occupied = DiskFiles.occupiedNames(resolver, parent),
            extension = extension,
        )
        val file = DiskFiles.createFile(parent, nameOnDisk, DiskFiles.MIME_TEXT)
        DiskFiles.writeText(resolver, file, content)

        val language = CodeLanguage.fromExtension(nameOnDisk)
        val path = childPath(parentPath, nameOnDisk)
        return LibraryItem(
            id = file.uri.toString(),
            path = path,
            name = file.name ?: nameOnDisk,
            type = if (language != null) ItemType.CODE_FILE else ItemType.OTHER_FILE,
            documentUri = file.uri.toString(),
            updatedAt = file.lastModified(),
            language = language,
        )
    }

    /**
     * Kopia pliku z zewnątrz (Udostępnij / Otwórz w) do katalogu notatek.
     * Bajty idą strumieniem - PDF i zdjęcia nie mieszczą się w Stringu.
     */
    fun importFile(parentPath: String, fileName: String, mime: String, source: Uri): LibraryItem {
        val parent = requireFolder(parentPath)
        val named = FileNames.withMimeExtension(fileName, mime)
        val extension = if (named.contains('.')) "." + named.substringAfterLast('.') else ""
        val nameOnDisk = FileNames.unique(
            name = named.removeSuffix(extension),
            occupied = DiskFiles.occupiedNames(resolver, parent),
            extension = extension,
        )
        val type = mime.ifBlank { "application/octet-stream" }
        val file = DiskFiles.createFile(parent, nameOnDisk, type)
        DiskFiles.copyUri(resolver, source, file)

        val language = CodeLanguage.fromExtension(nameOnDisk)
        val path = childPath(parentPath, nameOnDisk)
        return LibraryItem(
            id = file.uri.toString(),
            path = path,
            name = file.name ?: nameOnDisk,
            type = if (language != null) ItemType.CODE_FILE else ItemType.OTHER_FILE,
            documentUri = file.uri.toString(),
            updatedAt = file.lastModified(),
            language = language,
        )
    }

    fun readText(path: String): String {
        val file = describe(path)?.takeIf { it.isFile }
            ?: throw IOException(words.fileGone(path))
        return DiskFiles.readText(resolver, file)
    }

    fun readTextUpTo(path: String, maxBytes: Int): BoundedTextRead {
        val file = describe(path)?.takeIf { it.isFile }
            ?: throw IOException(words.fileGone(path))
        val declared = file.size.coerceAtLeast(0L)
        if (declared > maxBytes) {
            return BoundedTextRead.TooLarge(declared, file.uri.toString())
        }
        val bytes = DiskFiles.readBytesUpTo(resolver, file.uri, file.size, maxBytes, file.name)
            ?: return BoundedTextRead.TooLarge(
                sizeBytes = maxOf(declared, maxBytes.toLong() + 1L),
                documentUri = file.uri.toString(),
            )
        return BoundedTextRead.Content(bytes.toString(Charsets.UTF_8))
    }

    fun writeText(path: String, content: String) {
        val file = describe(path)?.takeIf { it.isFile }
            ?: throw IOException(words.fileGoneUnsaved(path))
        DiskFiles.writeText(resolver, file.uri, content, file.name)
    }

    // Renaming, moving, copying

    fun rename(path: String, newName: String): String {
        val parentPath = path.substringBeforeLast('/', "")
        val parent = folder(parentPath) ?: throw IOException(words.entryGone(path))
        val found = DiskFiles.locate(resolver, parent, path.substringAfterLast('/'))
            ?: throw IOException(words.entryGone(path))
        val entry = found.file
        val oldName = found.entry.name

        val isNote = found.entry.isDirectory && FileNames.isNote(oldName)
        val extension = when {
            isNote -> NoteDocument.EXTENSION
            found.entry.isFile && oldName.contains('.') -> "." + oldName.substringAfterLast('.')
            else -> ""
        }
        val occupied = DiskFiles.occupiedNames(resolver, parent) - oldName
        val target = FileNames.unique(
            name = newName.removeSuffix(extension),
            occupied = occupied,
            extension = extension,
        )

        /*
          Nazwa bez zmiany: nie ruszamy pliku.

          Magazyn Androida nie wie, że plik zmienia nazwę sam na siebie -
          widzi zajętą nazwę i dokłada „ (1)". „Zmień nazwę" i „Zapisz" bez
          poprawki zmieniały więc nazwę pliku, choć nikt o to nie prosił.
        */
        if (target != oldName && !entry.renameTo(target)) {
            throw IOException(words.renameFailed(newName))
        }

        // The real name goes inside as well, because on disk it may have been trimmed.
        if (isNote) {
            val document = runCatching { readDocument(entry) }.getOrNull()
            if (document != null) writeDocument(entry, document.copy(title = newName.trim()))
        } else if (found.entry.isDirectory) {
            val meta = readFolderMeta(entry)
            if (meta != null) {
                writeFolderMeta(
                    entry,
                    meta.copy(displayName = newName.trim(), modifiedAt = System.currentTimeMillis()),
                )
            }
        }

        return childPath(parentPath, target)
    }

    fun move(path: String, targetFolder: String): String {
        val source = describe(path) ?: throw IOException(words.entryGone(path))
        val target = requireFolder(targetFolder)
        if (path == targetFolder || targetFolder.startsWith("$path/")) {
            throw IOException(words.cannotMoveIntoItself)
        }
        val name = source.name
        val newName = FileNames.unique(
            name = name.substringBeforeLast('.', name),
            occupied = DiskFiles.occupiedNames(resolver, target),
            extension = if (name.contains('.')) "." + name.substringAfterLast('.') else "",
        )
        DiskFiles.copyRecursively(resolver, source, target, newName)
        DiskFiles.deleteRecursively(resolver, source)
        val newPath = childPath(targetFolder, newName)
        // Przeniesiony folder zmienił rodzica - synchronizacja folderów ma
        // wiedzieć, że ta zmiana jest świeższa niż stan serwera.
        if (source.isDirectory && !FileNames.isNote(name)) {
            entry(newPath)?.let { moved ->
                readFolderMeta(moved)?.let { meta ->
                    writeFolderMeta(moved, meta.copy(modifiedAt = System.currentTimeMillis()))
                }
            }
        }
        return newPath
    }

    fun copy(path: String, targetFolder: String): String {
        val source = describe(path) ?: throw IOException(words.entryGone(path))
        val target = requireFolder(targetFolder)
        val name = source.name
        val stem = if (FileNames.isNote(name)) {
            FileNames.withoutNoteExtension(name)
        } else {
            name.substringBeforeLast('.', name)
        }
        val extension = when {
            FileNames.isNote(name) -> NoteDocument.EXTENSION
            name.contains('.') -> "." + name.substringAfterLast('.')
            else -> ""
        }
        val newName = FileNames.unique(
            name = "$stem ${words.copySuffix}",
            occupied = DiskFiles.occupiedNames(resolver, target),
            extension = extension,
        )
        DiskFiles.copyRecursively(resolver, source, target, newName)
        val newPath = childPath(targetFolder, newName)
        // Kopia nie może nieść tych samych identyfikatorów co oryginał - dwa
        // wpisy o wspólnym numerze biłyby się o jedną notatkę na serwerze.
        located(newPath)?.takeIf { it.entry.isDirectory }?.let { copied ->
            regenerateIdsAfterCopy(copied, markAsCopy = true)
        }
        return newPath
    }

    /**
     * Świeże identyfikatory dla skopiowanych folderów i notatek, całą gałęzią.
     *
     * [markAsCopy] dokłada „(kopia)" do nazwy widocznej NA LIŚCIE - i tylko
     * wierzchołkowi. Spis pokazuje tytuł z wnętrza pliku, a nie nazwę katalogu,
     * więc bez tego kopia i pierwowzór stały obok siebie wyglądając identycznie
     * i nie dało się rozpoznać, którą z nich się otwiera.
     */
    private fun regenerateIdsAfterCopy(entry: Located, markAsCopy: Boolean = false) {
        val folder = entry.file
        if (FileNames.isNote(entry.entry.name)) {
            runCatching {
                val document = readDocument(folder)
                writeDocument(
                    folder,
                    document.copy(
                        id = UUID.randomUUID().toString(),
                        title = if (markAsCopy) {
                            "${document.title} ${words.copySuffix}"
                        } else {
                            document.title
                        },
                    ),
                )
            }
            return
        }
        readFolderMeta(folder)?.let { meta ->
            writeFolderMeta(
                folder,
                meta.copy(
                    id = UUID.randomUUID().toString(),
                    displayName = if (markAsCopy) {
                        "${meta.displayName} ${words.copySuffix}"
                    } else {
                        meta.displayName
                    },
                    modifiedAt = System.currentTimeMillis(),
                ),
            )
        }
        for (child in DiskFiles.locatedChildren(resolver, folder).orEmpty()) {
            if (child.entry.isDirectory && !FileNames.isHidden(child.entry.name)) {
                regenerateIdsAfterCopy(child)
            }
        }
    }

    // The bin

    private fun trashFolder(): DocumentFile =
        DiskFiles.folderForWrite(resolver, root, TrashEntry.DIRECTORY)

    /** Wpisy kosza. Pusta lista - kosza nie ma; null - magazyn nie odpowiedział. */
    private fun trashSlots(): List<DiskEntry>? {
        val top = DiskFiles.children(resolver, root.uri) ?: return null
        val trash = top.firstOrNull { it.name == TrashEntry.DIRECTORY }?.takeIf { it.isDirectory }
            ?: return emptyList()
        return DiskFiles.children(resolver, trash.uri)
    }

    /**
     * Wyrzuca wpis do kosza. [fromServer] zaznacza, że stoi za tym kasowanie
     * na serwerze, a nie ręka użytkownika - taki wpis dostaje termin, patrz
     * [TrashEntry.fromServer].
     */
    fun moveToTrash(path: String, fromServer: Boolean = false) {
        val source = describe(path) ?: throw IOException(words.entryGone(path))
        val name = source.name
        val trash = trashFolder()
        val id = UUID.randomUUID().toString()
        val slot = trash.createDirectory(id)
            ?: throw IOException(words.folderCreateFailed(words.sectionTrash))

        val type = when {
            source.isDirectory && FileNames.isNote(name) -> ItemType.NOTE
            source.isDirectory -> ItemType.FOLDER
            CodeLanguage.fromExtension(name) != null -> ItemType.CODE_FILE
            else -> ItemType.OTHER_FILE
        }
        val visibleName = when (type) {
            ItemType.NOTE -> FileNames.withoutNoteExtension(name)
            ItemType.FOLDER ->
                DiskFiles.children(resolver, source.uri)?.let(::folderMetaIn)?.displayName ?: name
            else -> name
        }

        val description = TrashEntry(
            id = id,
            originalPath = path,
            fileName = name,
            displayName = visibleName,
            type = type,
            deletedAt = System.currentTimeMillis(),
            fromServer = fromServer,
        )
        val descriptionFile = DiskFiles.fileForWrite(resolver, slot, TrashEntry.DESC_FILE, DiskFiles.MIME_JSON)
        DiskFiles.writeText(resolver, descriptionFile, NoteCodec.json.encodeToString(description))

        DiskFiles.copyRecursively(resolver, source, slot, name)
        DiskFiles.deleteRecursively(resolver, source)
    }

    fun listTrash(): List<TrashEntry> =
        trashSlots().orEmpty()
            .mapNotNull { slot -> if (slot.isDirectory) readTrashDescription(slot) else null }
            .sortedByDescending { it.deletedAt }

    private fun readTrashDescription(slot: DiskEntry): TrashEntry? =
        DiskFiles.children(resolver, slot.uri)?.let(::trashDescriptionIn)

    private fun trashDescriptionIn(inside: List<DiskEntry>): TrashEntry? {
        val description = inside.firstOrNull { it.name == TrashEntry.DESC_FILE } ?: return null
        return runCatching {
            NoteCodec.json.decodeFromString<TrashEntry>(DiskFiles.readText(resolver, description))
        }.getOrNull()
    }

    fun restore(id: String): String {
        val trash = DiskFiles.children(resolver, root.uri).orEmpty()
            .firstOrNull { it.name == TrashEntry.DIRECTORY }
            ?.takeIf { it.isDirectory }
            ?: throw IOException(words.binEmpty)
        val slot = DiskFiles.children(resolver, trash.uri)
            ?.firstOrNull { it.name == id }
            ?.takeIf { it.isDirectory }
            ?: throw IOException(words.notInBinAnyMore)
        val inside = DiskFiles.children(resolver, slot.uri).orEmpty()
        val descriptionFile = inside.firstOrNull { it.name == TrashEntry.DESC_FILE }
            ?: throw IOException(words.binEntryNoDescription)
        val description =
            NoteCodec.json.decodeFromString<TrashEntry>(DiskFiles.readText(resolver, descriptionFile))

        val source = inside.firstOrNull { it.name == description.fileName }
            ?: throw IOException(words.fileGoneFromBin(description.fileName))

        var target = root
        for (part in description.originalParent.split('/')) {
            if (part.isEmpty()) continue
            target = DiskFiles.folderForWrite(resolver, target, part)
        }

        val name = FileNames.unique(
            name = description.fileName.substringBeforeLast('.', description.fileName),
            occupied = DiskFiles.occupiedNames(resolver, target),
            extension = if (description.fileName.contains('.')) {
                "." + description.fileName.substringAfterLast('.')
            } else {
                ""
            },
        )
        DiskFiles.copyRecursively(resolver, source, target, name)
        slotNoteIds.remove(id)
        DiskFiles.deleteRecursively(resolver, slot)

        return childPath(description.originalParent, name)
    }

    /**
     * Kasuje wpis z dysku na stałe, bez kosza - porządki zlecone przez chmurę.
     * Zwraca false, kiedy plik został na dysku mimo próby; wołający odkłada
     * go wtedy do [DeleteRetryQueue] zamiast udawać, że go nie ma.
     */
    fun deleteEntry(path: String): Boolean {
        // Pusta ścieżka to cała biblioteka - tego nikt nie kasuje jednym wywołaniem.
        if (path.isBlank()) return false
        val target = when (val found = lookup(path)) {
            is PathLookup.Found -> found.entry
            PathLookup.Missing -> return true
            // Magazyn nie odpowiedział - nie wiadomo, czy plik jest, więc
            // kasowanie wraca do kolejki zamiast zostać uznane za zrobione.
            PathLookup.Unknown -> return false
        }
        return DiskFiles.deleteRecursively(resolver, target)
    }

    /** Kasuje wpis kosza. Zwraca false, kiedy coś z niego zostało na dysku. */
    fun deletePermanently(id: String): Boolean {
        val slots = trashSlots() ?: return false
        val slot = slots.firstOrNull { it.name == id } ?: return true
        slotNoteIds.remove(id)
        return DiskFiles.deleteRecursively(resolver, slot)
    }

    /** Opróżnia kosz. Zwraca identyfikatory wpisów, których nie dało się skasować. */
    fun emptyTrash(): List<String> {
        val slots = trashSlots() ?: return emptyList()
        val failed = mutableListOf<String>()
        for (slot in slots) {
            slotNoteIds.remove(slot.name)
            if (!DiskFiles.deleteRecursively(resolver, slot)) failed += slot.name
        }
        return failed
    }

    /**
     * Wpis kosza, w którym leży notatka o tym identyfikatorze.
     *
     * Kosz jest poza spisem - wyrzucenie notatki kasuje jej wiersz, a katalog
     * `.trash` jest ukryty, więc nie wciąga go nawet przebudowa spisu. Bez tej
     * drogi kasowanie zlecone przez serwer nie miało jak dosięgnąć notatki,
     * która wcześniej trafiła do lokalnego kosza - i tam zostawała na zawsze.
     */
    fun trashSlotForNote(noteId: String): String? =
        trashedNoteSlots().firstOrNull { (_, id) -> id == noteId }?.first

    /**
     * Pary (identyfikator wpisu kosza, identyfikator notatki) dla całego kosza,
     * razem z notatkami schowanymi w wyrzuconych folderach.
     */
    fun trashedNoteSlots(): List<Pair<String, String>> {
        val found = mutableListOf<Pair<String, String>>()
        for (slot in trashSlots().orEmpty()) {
            if (!slot.isDirectory) continue
            for (noteId in noteIdsInSlot(slot)) found += slot.name to noteId
        }
        return found
    }

    private fun noteIdsInSlot(slot: DiskEntry): List<String> {
        slotNoteIds[slot.name]?.let { return it }
        val ids = mutableListOf<String>()
        // Niepełny odczyt nie trafia do pamięci - następny przebieg spróbuje
        // jeszcze raz, zamiast na zawsze zgubić notatkę z kosza.
        if (collectNoteIdsIn(slot.uri, ids)) {
            if (slotNoteIds.size > MAX_REMEMBERED) slotNoteIds.clear()
            slotNoteIds[slot.name] = ids
        }
        return ids
    }

    /** Jak wyżej, dla plików z kodem: (identyfikator wpisu kosza, pierwotna ścieżka). */
    fun trashedCodeSlots(): List<Pair<String, String>> =
        trashSlots().orEmpty()
            .filter { it.isDirectory }
            .flatMap { slot -> codePathsInSlot(slot).map { path -> slot.name to path } }

    /**
     * Wpisy kosza, z których nic już nie da się przywrócić: bez pliku opisu
     * albo z opisem wskazującym na plik, którego w środku nie ma. Nie widać ich
     * w koszu (listTrash je pomija), więc leżą i zajmują miejsce.
     *
     * Wpis, do którego nie dało się zajrzeć, NIE jest zepsuty. Dawniej każdy
     * chwilowy kłopot z magazynem wyglądał tu jak pusty wpis - a sprzątanie
     * kasuje zepsute wpisy na stałe, razem z notatką w środku.
     */
    fun brokenTrashSlots(): List<String> {
        val slots = trashSlots() ?: return emptyList()
        return slots.mapNotNull { slot ->
            val name = slot.name
            if (!slot.isDirectory) return@mapNotNull name
            val inside = DiskFiles.children(resolver, slot.uri) ?: return@mapNotNull null
            val descriptionFile = inside.firstOrNull { it.name == TrashEntry.DESC_FILE }
                ?: return@mapNotNull name
            val text = runCatching { DiskFiles.readText(resolver, descriptionFile) }.getOrNull()
                ?: return@mapNotNull null
            val description = runCatching { NoteCodec.json.decodeFromString<TrashEntry>(text) }.getOrNull()
                ?: return@mapNotNull name
            if (inside.none { it.name == description.fileName }) name else null
        }
    }

    /**
     * Identyfikatory notatek leżących we wpisie kosza - razem z notatkami
     * schowanymi w wyrzuconych folderach. Trwałe kasowanie zgłasza je potem
     * serwerowi, żeby i tam zniknęły.
     */
    fun trashedNoteIds(id: String): List<String> {
        val slot = trashSlots().orEmpty().firstOrNull { it.name == id }?.takeIf { it.isDirectory }
            ?: return emptyList()
        return noteIdsInSlot(slot)
    }

    /** Zwraca false, gdy do któregoś katalogu albo notatki nie dało się zajrzeć. */
    private fun collectNoteIdsIn(folder: Uri, into: MutableList<String>): Boolean {
        val inside = DiskFiles.children(resolver, folder) ?: return false
        var complete = true
        for (child in inside) {
            if (!child.isDirectory) continue
            if (FileNames.isNote(child.name)) {
                val noteId = noteIdIn(child.uri)
                if (noteId != null) into += noteId else complete = false
            } else if (!collectNoteIdsIn(child.uri, into)) {
                complete = false
            }
        }
        return complete
    }

    /**
     * Kopiuje załączniki notatki leżącej w koszu do notatki pod [targetNotePath].
     *
     * Dla „Zapisz jako nową": treść notatki człowiek ma wciąż na ekranie, ale
     * zdjęcia i rysunki leżą w katalogu, który pojechał do kosza - bez nich
     * nowa kopia miałaby dziury zamiast obrazków. Zwraca true, kiedy było co
     * skopiować.
     */
    fun copyTrashedAssets(noteId: String, targetNotePath: String): Boolean {
        var source: DiskEntry? = null
        for (slot in trashSlots().orEmpty()) {
            if (!slot.isDirectory) continue
            // Wpis, o którym wiadomo, że tej notatki nie ma, nie wymaga czytania.
            if (slotNoteIds[slot.name]?.contains(noteId) == false) continue
            source = findNoteFolderIn(slot.uri, noteId)
            if (source != null) break
        }
        val assets = source
            ?.let { DiskFiles.children(resolver, it.uri) }
            ?.firstOrNull { it.name == NoteDocument.ASSETS_DIRECTORY }
            ?.takeIf { it.isDirectory }
            ?: return false
        val target = noteFolder(targetNotePath) ?: return false
        DiskFiles.copyRecursively(resolver, assets, target, NoteDocument.ASSETS_DIRECTORY)
        return true
    }

    /**
     * Pierwotne ścieżki plików z kodem leżących we wpisie kosza. Rejestr
     * synchronizacji zna pliki po ścieżce sprzed wyrzucenia, więc to po nich
     * zgłasza się serwerowi trwałe kasowanie.
     */
    fun trashedCodePaths(id: String): List<String> {
        val slot = trashSlots().orEmpty().firstOrNull { it.name == id }?.takeIf { it.isDirectory }
            ?: return emptyList()
        return codePathsInSlot(slot)
    }

    private fun codePathsInSlot(slot: DiskEntry): List<String> {
        val inside = DiskFiles.children(resolver, slot.uri) ?: return emptyList()
        val description = trashDescriptionIn(inside) ?: return emptyList()
        val source = inside.firstOrNull { it.name == description.fileName } ?: return emptyList()
        val found = mutableListOf<String>()
        collectCodePathsIn(source, description.originalPath, found)
        return found
    }

    // Kasowanie pojedynczej rzeczy z wpisu kosza
    //
    // Serwer zgłasza kasowanie notatka po notatce, a wpis kosza bywa całym
    // wyrzuconym folderem. Skasowanie całego wpisu zabrałoby wtedy rodzeństwo,
    // o którym nikt nic nie mówił - dlatego wpis znika w całości tylko wtedy,
    // gdy leży w nim dokładnie ta jedna rzecz.

    /** Kasuje z kosza jedną notatkę. Zwraca false, gdy plik został na dysku. */
    fun deleteNoteInsideTrash(slotId: String, noteId: String): Boolean {
        val slots = trashSlots() ?: return false
        val slot = slots.firstOrNull { it.name == slotId }?.takeIf { it.isDirectory } ?: return true
        val target = findNoteFolderIn(slot.uri, noteId) ?: return true
        return deleteFromSlot(slot, target)
    }

    /** Kasuje z kosza jeden plik z kodem, wskazany pierwotną ścieżką. */
    fun deleteCodeInsideTrash(slotId: String, originalPath: String): Boolean {
        val slots = trashSlots() ?: return false
        val slot = slots.firstOrNull { it.name == slotId }?.takeIf { it.isDirectory } ?: return true
        val inside = DiskFiles.children(resolver, slot.uri) ?: return false
        val description = trashDescriptionIn(inside) ?: return true
        val source = inside.firstOrNull { it.name == description.fileName } ?: return true
        val target = findByOriginalPath(source, description.originalPath, originalPath) ?: return true
        return deleteFromSlot(slot, target)
    }

    /**
     * Kasuje [target] leżący w [slot]. Kiedy to jest właśnie ta rzecz, którą
     * wyrzucono do kosza, znika cały wpis razem z opisem - bez pliku i tak nie
     * byłoby czego przywracać, a sam opis zostałby jako wpis nie do ruszenia.
     */
    private fun deleteFromSlot(slot: DiskEntry, target: DiskEntry): Boolean {
        val inside = DiskFiles.children(resolver, slot.uri)
        val description = inside?.let(::trashDescriptionIn)
        val wholeSlot = description != null &&
            inside.firstOrNull { it.name == description.fileName }?.uri == target.uri
        slotNoteIds.remove(slot.name)
        return DiskFiles.deleteRecursively(resolver, if (wholeSlot) slot else target)
    }

    private fun findNoteFolderIn(folder: Uri, noteId: String): DiskEntry? {
        for (child in DiskFiles.children(resolver, folder).orEmpty()) {
            if (!child.isDirectory) continue
            if (FileNames.isNote(child.name)) {
                if (noteIdIn(child.uri) == noteId) return child
            } else {
                findNoteFolderIn(child.uri, noteId)?.let { return it }
            }
        }
        return null
    }

    /** Szuka w koszu wpisu, który przed wyrzuceniem leżał pod [wanted]. */
    private fun findByOriginalPath(
        entry: DiskEntry,
        path: String,
        wanted: String,
    ): DiskEntry? {
        if (path == wanted) return entry
        if (entry.isFile) return null
        if (FileNames.isNote(entry.name)) return null
        for (child in DiskFiles.children(resolver, entry.uri).orEmpty()) {
            findByOriginalPath(child, "$path/${child.name}", wanted)?.let { return it }
        }
        return null
    }

    private fun collectCodePathsIn(entry: DiskEntry, path: String, into: MutableList<String>) {
        val name = entry.name
        if (entry.isFile) {
            if (CodeLanguage.fromExtension(name) != null) into += path
            return
        }
        if (FileNames.isNote(name)) return
        for (child in DiskFiles.children(resolver, entry.uri).orEmpty()) {
            collectCodePathsIn(child, "$path/${child.name}", into)
        }
    }

    // Walking the whole tree, needed when building the index

    fun walkTree(visit: (LibraryItem) -> Unit) {
        walk(visit)
    }

    /**
     * Całe drzewo biblioteki - jedno zapytanie na folder, bez zaglądania do
     * wnętrza notatek. Zwraca false, gdy któryś folder nie dał się przeczytać,
     * czyli spis jest niepełny i nie wolno na jego podstawie niczego kasować.
     */
    internal fun walk(visit: (LibraryItem) -> Unit): Boolean {
        var complete = true
        fun descend(inside: List<DiskEntry>, path: String) {
            for (child in inside) {
                if (FileNames.isHidden(child.name)) continue
                if (child.isDirectory && !FileNames.isNote(child.name)) {
                    val nested = DiskFiles.children(resolver, child.uri)
                    if (nested == null) complete = false
                    val item = folderItem(child, path, nested)
                    visit(item)
                    if (nested != null) descend(nested, item.path)
                } else {
                    visit(toItem(child, path) ?: continue)
                }
            }
        }
        val top = DiskFiles.children(resolver, root.uri) ?: return false
        descend(top, "")
        return complete
    }

    /**
     * Wpis pod [path] i wszystko pod nim, jak w spisie folderu. null, gdy
     * wpisu nie ma albo nie dało się go znaleźć.
     */
    internal fun branch(path: String): List<LibraryItem>? {
        val entry = describe(path) ?: return null
        val parentPath = path.substringBeforeLast('/', "")
        if (!entry.isDirectory || FileNames.isNote(entry.name)) {
            return listOfNotNull(toItem(entry, parentPath))
        }
        val inside = DiskFiles.children(resolver, entry.uri)
        val found = mutableListOf(folderItem(entry, parentPath, inside))
        if (inside != null) {
            fun descend(entries: List<DiskEntry>, at: String) {
                for (child in entries) {
                    if (FileNames.isHidden(child.name)) continue
                    if (child.isDirectory && !FileNames.isNote(child.name)) {
                        val nested = DiskFiles.children(resolver, child.uri)
                        val item = folderItem(child, at, nested)
                        found += item
                        if (nested != null) descend(nested, item.path)
                    } else {
                        found += toItem(child, at) ?: continue
                    }
                }
            }
            descend(inside, path)
        }
        return found
    }

    /**
     * Opisy wszystkich folderów biblioteki po ścieżkach. [FolderMetas.complete]
     * fałszywe, gdy któregoś folderu albo jego opisu nie dało się przeczytać.
     */
    internal fun allFolderMetas(): FolderMetas {
        val found = HashMap<String, FolderMeta>()
        var complete = true
        fun descend(inside: List<DiskEntry>, path: String) {
            for (child in inside) {
                if (!child.isDirectory || FileNames.isHidden(child.name) || FileNames.isNote(child.name)) continue
                val at = childPath(path, child.name)
                val nested = DiskFiles.children(resolver, child.uri)
                if (nested == null) {
                    complete = false
                    continue
                }
                val meta = folderMetaIn(nested)
                if (meta != null) {
                    found[at] = meta
                } else if (nested.any { it.name == FolderMeta.FILE }) {
                    complete = false
                }
                descend(nested, at)
            }
        }
        val top = DiskFiles.children(resolver, root.uri) ?: return FolderMetas(emptyMap(), complete = false)
        descend(top, "")
        return FolderMetas(found, complete)
    }

    /** Opis folderu pod ścieżką (folder.json) albo null. */
    internal fun folderMetaAt(path: String): FolderMeta? {
        val folder = describe(path)?.takeIf { it.isDirectory } ?: return null
        return DiskFiles.children(resolver, folder.uri)?.let(::folderMetaIn)
    }

    private fun childPath(parentPath: String, name: String): String =
        if (parentPath.isEmpty()) name else "$parentPath/$name"

    companion object {
        const val BACKUP_FILE = "content.bak.json"

        /** Gdzie szukać treści notatki: najpierw plik główny, potem jego kopia. */
        private val CONTENT_FILES = listOf(NoteDocument.CONTENT_FILE, BACKUP_FILE)

        /** Ile opisów folderów i wpisów kosza trzymamy w pamięci, zanim zaczniemy od zera. */
        private const val MAX_REMEMBERED = 4096

        /** Foldery alfabetycznie na górze, notatki i pliki pod nimi od najnowszej zmiany. */
        val libraryOrder: Comparator<LibraryItem> = Comparator { a, b ->
            val aFolder = a.type == ItemType.FOLDER
            val bFolder = b.type == ItemType.FOLDER
            when {
                aFolder != bFolder -> if (aFolder) -1 else 1
                aFolder -> String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name)
                else -> {
                    val byDate = b.updatedAt.compareTo(a.updatedAt)
                    if (byDate != 0) byDate else String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name)
                }
            }
        }

        fun codeTemplate(language: CodeLanguage): String = when (language) {
            CodeLanguage.PYTHON -> "print(\"${words.greetingWord}\")\n"
            CodeLanguage.C -> "#include <stdio.h>\n\nint main(void) {\n    printf(\"${words.greetingWord}\\n\");\n    return 0;\n}\n"
            CodeLanguage.CPP -> "#include <iostream>\n\nint main() {\n    std::cout << \"${words.greetingWord}\" << std::endl;\n    return 0;\n}\n"
            CodeLanguage.JAVA -> "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"${words.greetingWord}\");\n    }\n}\n"
            CodeLanguage.KOTLIN -> "fun main() {\n    println(\"${words.greetingWord}\")\n}\n"
            CodeLanguage.JAVASCRIPT -> "console.log(\"${words.greetingWord}\")\n"
            CodeLanguage.TYPESCRIPT ->
                "const ${words.greetingVariable}: string = \"${words.greetingWord}\"\n" +
                    "console.log(${words.greetingVariable})\n"
            CodeLanguage.CSHARP -> "using System;\n\nclass Program {\n    static void Main() {\n        Console.WriteLine(\"${words.greetingWord}\");\n    }\n}\n"
            CodeLanguage.GO -> "package main\n\nimport \"fmt\"\n\nfunc main() {\n    fmt.Println(\"${words.greetingWord}\")\n}\n"
            CodeLanguage.RUST -> "fn main() {\n    println!(\"${words.greetingWord}\");\n}\n"
            CodeLanguage.PHP -> "<?php\necho \"${words.greetingWord}\\n\";\n"
            CodeLanguage.RUBY -> "puts \"${words.greetingWord}\"\n"
            CodeLanguage.BASH -> "echo \"${words.greetingWord}\"\n"
            CodeLanguage.SQL -> "select '${words.greetingWord}';\n"
            /*
              MySQL-a nie da się dziś założyć z okna nowego pliku (rozszerzenie
              sql należy do SQLite), ale gałąź musi tu być - when po języku jest
              zupełny. Zapytanie stoi po MySQL-owemu, żeby przy zmianie tamtej
              decyzji nie trzeba było tu wracać.
            */
            CodeLanguage.MYSQL ->
                "select '${words.greetingWord}' as ${words.greetingVariable};\n"
            CodeLanguage.HTML ->
                "<!doctype html>\n<html lang=\"${if (words.english) "en" else "pl"}\">\n<head>\n  <meta charset=\"utf-8\">\n" +
                    "  <title>${words.myPageTitle}</title>\n</head>\n<body>\n  <h1>${words.greetingWord}</h1>\n</body>\n</html>\n"
            CodeLanguage.PLAIN_TEXT -> ""
        }
    }
}
