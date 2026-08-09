package wojtoteka.ovh.kajet.storage

import android.content.ContentResolver
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

class LibraryStore(
    private val resolver: ContentResolver,
    val root: DocumentFile,
) {

    val rootUri: String get() = root.uri.toString()

    // Finding places in the tree

    fun folder(path: String): DocumentFile? {
        if (path.isBlank()) return root
        var current: DocumentFile = root
        for (part in path.split('/')) {
            if (part.isEmpty()) continue
            val next = current.findFile(part) ?: return null
            if (!next.isDirectory) return null
            current = next
        }
        return current
    }

    fun entry(path: String): DocumentFile? {
        if (path.isBlank()) return root
        val parent = folder(path.substringBeforeLast('/', "")) ?: return null
        return parent.findFile(path.substringAfterLast('/'))
    }

    private fun requireFolder(path: String): DocumentFile =
        folder(path) ?: throw IOException(
            words.folderGone(path),
        )

    // Reading the content of a folder

    fun list(path: String): List<LibraryItem> {
        val folder = folder(path) ?: return emptyList()
        return folder.listFiles()
            .mapNotNull { toItem(it, path) }
            .sortedWith(libraryOrder)
    }

    private fun toItem(file: DocumentFile, parentPath: String): LibraryItem? {
        val name = file.name ?: return null
        if (FileNames.isHidden(name)) return null
        val path = if (parentPath.isEmpty()) name else "$parentPath/$name"

        return when {
            file.isDirectory && FileNames.isNote(name) -> {
                LibraryItem(
                    id = file.uri.toString(),
                    path = path,
                    name = FileNames.withoutNoteExtension(name),
                    type = ItemType.NOTE,
                    documentUri = file.uri.toString(),
                    updatedAt = file.lastModified(),
                    noteKind = null,
                )
            }

            file.isDirectory -> {
                val meta = readFolderMeta(file)
                LibraryItem(
                    id = meta?.id ?: file.uri.toString(),
                    path = path,
                    name = meta?.displayName ?: name,
                    type = ItemType.FOLDER,
                    documentUri = file.uri.toString(),
                    updatedAt = file.lastModified(),
                    colorId = meta?.colorId,
                    iconId = meta?.iconId,
                    // Liczymy to, co widać w folderze. Plik z jego barwą i ikoną
                    // to nie wpis — przez niego pusty folder meldował „1 wpis".
                    childCount = file.listFiles().count { child ->
                        val childName = child.name
                        childName != null &&
                            !FileNames.isHidden(childName) &&
                            childName != FolderMeta.FILE
                    },
                )
            }

            else -> {
                val language = CodeLanguage.fromExtension(name)
                if (name == FolderMeta.FILE) return null
                LibraryItem(
                    id = file.uri.toString(),
                    path = path,
                    name = name,
                    type = if (language != null) ItemType.CODE_FILE else ItemType.OTHER_FILE,
                    documentUri = file.uri.toString(),
                    updatedAt = file.lastModified(),
                    language = language,
                )
            }
        }
    }

    // Folders

    fun readFolderMeta(folder: DocumentFile): FolderMeta? {
        val file = folder.findFile(FolderMeta.FILE) ?: return null
        if (!file.isFile) return null
        return runCatching { NoteCodec.decodeFolder(DiskFiles.readText(resolver, file)) }.getOrNull()
    }

    fun writeFolderMeta(folder: DocumentFile, meta: FolderMeta) {
        val file = DiskFiles.fileForWrite(folder, FolderMeta.FILE, DiskFiles.MIME_JSON)
        DiskFiles.writeText(resolver, file, NoteCodec.encodeFolder(meta))
    }

    fun createFolder(
        parentPath: String,
        name: String,
        colorId: String = "grafit",
        iconId: String = "folder",
        // Folder przysłany z chmury przynosi swój identyfikator — ten sam,
        // pod którym żyje na serwerze i na innych urządzeniach.
        id: String? = null,
    ): LibraryItem {
        val parent = requireFolder(parentPath)
        val nameOnDisk = FileNames.unique(name, DiskFiles.occupiedNames(parent))
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

        val path = if (parentPath.isEmpty()) nameOnDisk else "$parentPath/$nameOnDisk"
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
            occupied = DiskFiles.occupiedNames(parent),
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

        val path = if (parentPath.isEmpty()) nameOnDisk else "$parentPath/$nameOnDisk"
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
        val folder = entry(path)?.takeIf { it.isDirectory }
            ?: throw IOException(words.noteGone(path))
        return readDocument(folder)
    }

    fun readDocument(noteFolder: DocumentFile): NoteDocument {
        val main = noteFolder.findFile(NoteDocument.CONTENT_FILE)
        if (main != null && main.isFile) {
            val result = runCatching { NoteCodec.decodeNote(DiskFiles.readText(resolver, main)) }
            result.getOrNull()?.let { return it }
        }
        val backup = noteFolder.findFile(BACKUP_FILE)
        if (backup != null && backup.isFile) {
            val result = runCatching { NoteCodec.decodeNote(DiskFiles.readText(resolver, backup)) }
            result.getOrNull()?.let { return it }
        }
        throw FormatException(
            words.noteUnreadable(noteFolder.name),
        )
    }

    fun writeNote(path: String, document: NoteDocument) {
        val folder = entry(path)?.takeIf { it.isDirectory }
            ?: throw IOException(words.noteGoneUnsaved(path))
        writeDocument(folder, document)
    }

    fun writeDocument(noteFolder: DocumentFile, document: NoteDocument) {
        val content = NoteCodec.encodeNote(document.copy(updatedAt = System.currentTimeMillis()))
        val backup = DiskFiles.fileForWrite(noteFolder, BACKUP_FILE, DiskFiles.MIME_JSON)
        DiskFiles.writeText(resolver, backup, content)
        val main = DiskFiles.fileForWrite(noteFolder, NoteDocument.CONTENT_FILE, DiskFiles.MIME_JSON)
        DiskFiles.writeText(resolver, main, content)
    }

    // Note attachments

    fun assetsFolder(noteFolder: DocumentFile): DocumentFile =
        DiskFiles.folderForWrite(noteFolder, NoteDocument.ASSETS_DIRECTORY)

    fun writeAttachment(notePath: String, name: String, data: ByteArray, mime: String): String {
        val folder = entry(notePath)?.takeIf { it.isDirectory }
            ?: throw IOException(words.noteGone(notePath))
        val assets = assetsFolder(folder)
        val nameOnDisk = FileNames.unique(
            name = name.substringBeforeLast('.'),
            occupied = DiskFiles.occupiedNames(assets),
            extension = "." + name.substringAfterLast('.', "bin"),
        )
        val file = DiskFiles.createFile(assets, nameOnDisk, mime)
        DiskFiles.writeBytes(resolver, file, data)
        return file.name ?: nameOnDisk
    }

    /** Overwrite-or-create under the exact name (sync pull; markdown links stay valid). */
    fun putAttachment(notePath: String, name: String, data: ByteArray, mime: String) {
        val folder = entry(notePath)?.takeIf { it.isDirectory }
            ?: throw IOException(words.noteGone(notePath))
        val assets = assetsFolder(folder)
        val file = DiskFiles.fileForWrite(assets, name, mime)
        DiskFiles.writeBytes(resolver, file, data)
    }

    fun readAttachment(notePath: String, name: String): ByteArray? {
        val folder = entry(notePath)?.takeIf { it.isDirectory } ?: return null
        val assets = folder.findFile(NoteDocument.ASSETS_DIRECTORY) ?: return null
        val file = assets.findFile(name)?.takeIf { it.isFile } ?: return null
        return runCatching { DiskFiles.readBytes(resolver, file) }.getOrNull()
    }

    fun writeInlineDrawing(notePath: String, name: String, drawing: DrawingSource) {
        val folder = entry(notePath)?.takeIf { it.isDirectory }
            ?: throw IOException(words.noteGone(notePath))
        val assets = assetsFolder(folder)
        val file = DiskFiles.fileForWrite(assets, name, DiskFiles.MIME_JSON)
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
            occupied = DiskFiles.occupiedNames(parent),
            extension = extension,
        )
        val file = DiskFiles.createFile(parent, nameOnDisk, DiskFiles.MIME_TEXT)
        DiskFiles.writeText(resolver, file, codeTemplate(language))

        val path = if (parentPath.isEmpty()) nameOnDisk else "$parentPath/$nameOnDisk"
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
     * Nowy plik tekstowy o zadanej nazwie i treści — dla plików z kodem
     * przyjeżdżających z chmury. Przy zajętej nazwie dokleja licznik.
     */
    fun createTextFile(parentPath: String, fileName: String, content: String): LibraryItem {
        val parent = requireFolder(parentPath)
        val extension = if (fileName.contains('.')) "." + fileName.substringAfterLast('.') else ""
        val nameOnDisk = FileNames.unique(
            name = fileName.removeSuffix(extension),
            occupied = DiskFiles.occupiedNames(parent),
            extension = extension,
        )
        val file = DiskFiles.createFile(parent, nameOnDisk, DiskFiles.MIME_TEXT)
        DiskFiles.writeText(resolver, file, content)

        val language = CodeLanguage.fromExtension(nameOnDisk)
        val path = if (parentPath.isEmpty()) nameOnDisk else "$parentPath/$nameOnDisk"
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
        val file = entry(path)?.takeIf { it.isFile }
            ?: throw IOException(words.fileGone(path))
        return DiskFiles.readText(resolver, file)
    }

    fun writeText(path: String, content: String) {
        val file = entry(path)?.takeIf { it.isFile }
            ?: throw IOException(words.fileGoneUnsaved(path))
        DiskFiles.writeText(resolver, file, content)
    }

    // Renaming, moving, copying

    fun rename(path: String, newName: String): String {
        val entry = entry(path) ?: throw IOException(words.entryGone(path))
        val parent = folder(path.substringBeforeLast('/', ""))
            ?: throw IOException(words.noParentFolder)
        val oldName = entry.name.orEmpty()

        val isNote = entry.isDirectory && FileNames.isNote(oldName)
        val extension = when {
            isNote -> NoteDocument.EXTENSION
            entry.isFile && oldName.contains('.') -> "." + oldName.substringAfterLast('.')
            else -> ""
        }
        val occupied = DiskFiles.occupiedNames(parent) - oldName
        val target = FileNames.unique(
            name = newName.removeSuffix(extension),
            occupied = occupied,
            extension = extension,
        )

        /*
          Nazwa bez zmiany: nie ruszamy pliku.

          Magazyn Androida nie wie, że plik zmienia nazwę sam na siebie —
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
        } else if (entry.isDirectory) {
            val meta = readFolderMeta(entry)
            if (meta != null) {
                writeFolderMeta(
                    entry,
                    meta.copy(displayName = newName.trim(), modifiedAt = System.currentTimeMillis()),
                )
            }
        }

        val parentPath = path.substringBeforeLast('/', "")
        return if (parentPath.isEmpty()) target else "$parentPath/$target"
    }

    fun move(path: String, targetFolder: String): String {
        val source = entry(path) ?: throw IOException(words.entryGone(path))
        val target = requireFolder(targetFolder)
        if (path == targetFolder || targetFolder.startsWith("$path/")) {
            throw IOException(words.cannotMoveIntoItself)
        }
        val name = source.name ?: throw IOException(words.entryHasNoName)
        val newName = FileNames.unique(
            name = name.substringBeforeLast('.', name),
            occupied = DiskFiles.occupiedNames(target),
            extension = if (name.contains('.')) "." + name.substringAfterLast('.') else "",
        )
        DiskFiles.copyRecursively(resolver, source, target, newName)
        DiskFiles.deleteRecursively(source)
        val newPath = if (targetFolder.isEmpty()) newName else "$targetFolder/$newName"
        // Przeniesiony folder zmienił rodzica — synchronizacja folderów ma
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
        val source = entry(path) ?: throw IOException(words.entryGone(path))
        val target = requireFolder(targetFolder)
        val name = source.name ?: throw IOException(words.entryHasNoName)
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
            occupied = DiskFiles.occupiedNames(target),
            extension = extension,
        )
        DiskFiles.copyRecursively(resolver, source, target, newName)
        val newPath = if (targetFolder.isEmpty()) newName else "$targetFolder/$newName"
        // Kopia nie może nieść tych samych identyfikatorów co oryginał — dwa
        // wpisy o wspólnym numerze biłyby się o jedną notatkę na serwerze.
        entry(newPath)?.takeIf { it.isDirectory }?.let { copied ->
            regenerateIdsAfterCopy(copied, markAsCopy = true)
        }
        return newPath
    }

    /**
     * Świeże identyfikatory dla skopiowanych folderów i notatek, całą gałęzią.
     *
     * [markAsCopy] dokłada „(kopia)" do nazwy widocznej NA LIŚCIE — i tylko
     * wierzchołkowi. Spis pokazuje tytuł z wnętrza pliku, a nie nazwę katalogu,
     * więc bez tego kopia i pierwowzór stały obok siebie wyglądając identycznie
     * i nie dało się rozpoznać, którą z nich się otwiera.
     */
    private fun regenerateIdsAfterCopy(entry: DocumentFile, markAsCopy: Boolean = false) {
        val name = entry.name.orEmpty()
        if (FileNames.isNote(name)) {
            runCatching {
                val document = readDocument(entry)
                writeDocument(
                    entry,
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
        readFolderMeta(entry)?.let { meta ->
            writeFolderMeta(
                entry,
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
        for (child in entry.listFiles()) {
            val childName = child.name ?: continue
            if (child.isDirectory && !FileNames.isHidden(childName)) {
                regenerateIdsAfterCopy(child)
            }
        }
    }

    // The bin

    private fun trashFolder(): DocumentFile = DiskFiles.folderForWrite(root, TrashEntry.DIRECTORY)

    /**
     * Wyrzuca wpis do kosza. [fromServer] zaznacza, że stoi za tym kasowanie
     * na serwerze, a nie ręka użytkownika — taki wpis dostaje termin, patrz
     * [TrashEntry.fromServer].
     */
    fun moveToTrash(path: String, fromServer: Boolean = false) {
        val source = entry(path) ?: throw IOException(words.entryGone(path))
        val name = source.name ?: throw IOException(words.entryHasNoName)
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
            ItemType.FOLDER -> readFolderMeta(source)?.displayName ?: name
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
        val descriptionFile = DiskFiles.fileForWrite(slot, TrashEntry.DESC_FILE, DiskFiles.MIME_JSON)
        DiskFiles.writeText(resolver, descriptionFile, NoteCodec.json.encodeToString(description))

        DiskFiles.copyRecursively(resolver, source, slot, name)
        DiskFiles.deleteRecursively(source)
    }

    fun listTrash(): List<TrashEntry> {
        val trash = root.findFile(TrashEntry.DIRECTORY)?.takeIf { it.isDirectory } ?: return emptyList()
        return trash.listFiles()
            .mapNotNull { slot -> if (slot.isDirectory) readTrashDescription(slot) else null }
            .sortedByDescending { it.deletedAt }
    }

    private fun readTrashDescription(slot: DocumentFile): TrashEntry? {
        val description = slot.findFile(TrashEntry.DESC_FILE) ?: return null
        return runCatching {
            NoteCodec.json.decodeFromString<TrashEntry>(DiskFiles.readText(resolver, description))
        }.getOrNull()
    }

    fun restore(id: String): String {
        val trash = root.findFile(TrashEntry.DIRECTORY)?.takeIf { it.isDirectory }
            ?: throw IOException(words.binEmpty)
        val slot = trash.findFile(id)?.takeIf { it.isDirectory }
            ?: throw IOException(words.notInBinAnyMore)
        val descriptionFile = slot.findFile(TrashEntry.DESC_FILE)
            ?: throw IOException(words.binEntryNoDescription)
        val description =
            NoteCodec.json.decodeFromString<TrashEntry>(DiskFiles.readText(resolver, descriptionFile))

        val source = slot.findFile(description.fileName)
            ?: throw IOException(words.fileGoneFromBin(description.fileName))

        var target = root
        for (part in description.originalParent.split('/')) {
            if (part.isEmpty()) continue
            target = DiskFiles.folderForWrite(target, part)
        }

        val name = FileNames.unique(
            name = description.fileName.substringBeforeLast('.', description.fileName),
            occupied = DiskFiles.occupiedNames(target),
            extension = if (description.fileName.contains('.')) {
                "." + description.fileName.substringAfterLast('.')
            } else {
                ""
            },
        )
        DiskFiles.copyRecursively(resolver, source, target, name)
        DiskFiles.deleteRecursively(slot)

        return if (description.originalParent.isEmpty()) name else "${description.originalParent}/$name"
    }

    /**
     * Kasuje wpis z dysku na stałe, bez kosza — porządki zlecone przez chmurę.
     * Zwraca false, kiedy plik został na dysku mimo próby; wołający odkłada
     * go wtedy do [DeleteRetryQueue] zamiast udawać, że go nie ma.
     */
    fun deleteEntry(path: String): Boolean {
        val target = entry(path) ?: return true
        return DiskFiles.deleteRecursively(target)
    }

    /** Kasuje wpis kosza. Zwraca false, kiedy coś z niego zostało na dysku. */
    fun deletePermanently(id: String): Boolean {
        val trash = root.findFile(TrashEntry.DIRECTORY)?.takeIf { it.isDirectory } ?: return true
        val slot = trash.findFile(id) ?: return true
        return DiskFiles.deleteRecursively(slot)
    }

    /** Opróżnia kosz. Zwraca identyfikatory wpisów, których nie dało się skasować. */
    fun emptyTrash(): List<String> {
        val trash = root.findFile(TrashEntry.DIRECTORY)?.takeIf { it.isDirectory }
            ?: return emptyList()
        val failed = mutableListOf<String>()
        for (slot in trash.listFiles()) {
            val name = slot.name ?: continue
            if (!DiskFiles.deleteRecursively(slot)) failed += name
        }
        return failed
    }

    /**
     * Wpis kosza, w którym leży notatka o tym identyfikatorze.
     *
     * Kosz jest poza spisem — wyrzucenie notatki kasuje jej wiersz, a katalog
     * `.trash` jest ukryty, więc nie wciąga go nawet przebudowa spisu. Bez tej
     * drogi kasowanie zlecone przez serwer nie miało jak dosięgnąć notatki,
     * która wcześniej trafiła do lokalnego kosza — i tam zostawała na zawsze.
     */
    fun trashSlotForNote(noteId: String): String? =
        trashedNoteSlots().firstOrNull { (_, id) -> id == noteId }?.first

    /**
     * Pary (identyfikator wpisu kosza, identyfikator notatki) dla całego kosza,
     * razem z notatkami schowanymi w wyrzuconych folderach.
     */
    fun trashedNoteSlots(): List<Pair<String, String>> {
        val trash = root.findFile(TrashEntry.DIRECTORY)?.takeIf { it.isDirectory } ?: return emptyList()
        val found = mutableListOf<Pair<String, String>>()
        for (slot in trash.listFiles()) {
            if (!slot.isDirectory) continue
            val slotId = slot.name ?: continue
            val ids = mutableListOf<String>()
            collectNoteIdsIn(slot, ids)
            for (noteId in ids) found += slotId to noteId
        }
        return found
    }

    /** Jak wyżej, dla plików z kodem: (identyfikator wpisu kosza, pierwotna ścieżka). */
    fun trashedCodeSlots(): List<Pair<String, String>> {
        val trash = root.findFile(TrashEntry.DIRECTORY)?.takeIf { it.isDirectory } ?: return emptyList()
        return trash.listFiles()
            .filter { it.isDirectory }
            .flatMap { slot ->
                val slotId = slot.name ?: return@flatMap emptyList()
                codePathsInSlot(slot).map { path -> slotId to path }
            }
    }

    /**
     * Wpisy kosza, z których nic już nie da się przywrócić: bez pliku opisu
     * albo z opisem wskazującym na plik, którego w środku nie ma. Nie widać ich
     * w koszu (listTrash je pomija), więc leżą i zajmują miejsce.
     */
    fun brokenTrashSlots(): List<String> {
        val trash = root.findFile(TrashEntry.DIRECTORY)?.takeIf { it.isDirectory } ?: return emptyList()
        return trash.listFiles().mapNotNull { slot ->
            val name = slot.name ?: return@mapNotNull null
            if (!slot.isDirectory) return@mapNotNull name
            val descriptionFile = slot.findFile(TrashEntry.DESC_FILE) ?: return@mapNotNull name
            val description = runCatching {
                NoteCodec.json.decodeFromString<TrashEntry>(DiskFiles.readText(resolver, descriptionFile))
            }.getOrNull() ?: return@mapNotNull name
            if (slot.findFile(description.fileName) == null) name else null
        }
    }

    /**
     * Identyfikatory notatek leżących we wpisie kosza — razem z notatkami
     * schowanymi w wyrzuconych folderach. Trwałe kasowanie zgłasza je potem
     * serwerowi, żeby i tam zniknęły.
     */
    fun trashedNoteIds(id: String): List<String> {
        val trash = root.findFile(TrashEntry.DIRECTORY)?.takeIf { it.isDirectory } ?: return emptyList()
        val slot = trash.findFile(id)?.takeIf { it.isDirectory } ?: return emptyList()
        val found = mutableListOf<String>()
        collectNoteIdsIn(slot, found)
        return found
    }

    private fun collectNoteIdsIn(folder: DocumentFile, into: MutableList<String>) {
        for (child in folder.listFiles()) {
            if (!child.isDirectory) continue
            val name = child.name ?: continue
            if (FileNames.isNote(name)) {
                runCatching { readDocument(child) }.getOrNull()?.let { into += it.id }
            } else {
                collectNoteIdsIn(child, into)
            }
        }
    }

    /**
     * Kopiuje załączniki notatki leżącej w koszu do notatki pod [targetNotePath].
     *
     * Dla „Zapisz jako nową": treść notatki człowiek ma wciąż na ekranie, ale
     * zdjęcia i rysunki leżą w katalogu, który pojechał do kosza — bez nich
     * nowa kopia miałaby dziury zamiast obrazków. Zwraca true, kiedy było co
     * skopiować.
     */
    fun copyTrashedAssets(noteId: String, targetNotePath: String): Boolean {
        val trash = root.findFile(TrashEntry.DIRECTORY)?.takeIf { it.isDirectory } ?: return false
        var source: DocumentFile? = null
        for (slot in trash.listFiles()) {
            if (!slot.isDirectory) continue
            source = findNoteFolderIn(slot, noteId)
            if (source != null) break
        }
        val assets = source?.findFile(NoteDocument.ASSETS_DIRECTORY)?.takeIf { it.isDirectory }
            ?: return false
        val target = entry(targetNotePath)?.takeIf { it.isDirectory } ?: return false
        DiskFiles.copyRecursively(resolver, assets, target, NoteDocument.ASSETS_DIRECTORY)
        return true
    }

    /**
     * Pierwotne ścieżki plików z kodem leżących we wpisie kosza. Rejestr
     * synchronizacji zna pliki po ścieżce sprzed wyrzucenia, więc to po nich
     * zgłasza się serwerowi trwałe kasowanie.
     */
    fun trashedCodePaths(id: String): List<String> {
        val trash = root.findFile(TrashEntry.DIRECTORY)?.takeIf { it.isDirectory } ?: return emptyList()
        val slot = trash.findFile(id)?.takeIf { it.isDirectory } ?: return emptyList()
        return codePathsInSlot(slot)
    }

    private fun codePathsInSlot(slot: DocumentFile): List<String> {
        val description = readTrashDescription(slot) ?: return emptyList()
        val source = slot.findFile(description.fileName) ?: return emptyList()
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
        val slot = trashSlot(slotId) ?: return true
        val target = findNoteFolderIn(slot, noteId) ?: return true
        return deleteFromSlot(slot, target)
    }

    /** Kasuje z kosza jeden plik z kodem, wskazany pierwotną ścieżką. */
    fun deleteCodeInsideTrash(slotId: String, originalPath: String): Boolean {
        val slot = trashSlot(slotId) ?: return true
        val description = readTrashDescription(slot) ?: return true
        val source = slot.findFile(description.fileName) ?: return true
        val target = findByOriginalPath(source, description.originalPath, originalPath) ?: return true
        return deleteFromSlot(slot, target)
    }

    private fun trashSlot(slotId: String): DocumentFile? =
        root.findFile(TrashEntry.DIRECTORY)
            ?.takeIf { it.isDirectory }
            ?.findFile(slotId)
            ?.takeIf { it.isDirectory }

    /**
     * Kasuje [target] leżący w [slot]. Kiedy to jest właśnie ta rzecz, którą
     * wyrzucono do kosza, znika cały wpis razem z opisem — bez pliku i tak nie
     * byłoby czego przywracać, a sam opis zostałby jako wpis nie do ruszenia.
     */
    private fun deleteFromSlot(slot: DocumentFile, target: DocumentFile): Boolean {
        val description = readTrashDescription(slot)
        val wholeSlot = description != null && slot.findFile(description.fileName)?.uri == target.uri
        return DiskFiles.deleteRecursively(if (wholeSlot) slot else target)
    }

    private fun findNoteFolderIn(folder: DocumentFile, noteId: String): DocumentFile? {
        for (child in folder.listFiles()) {
            if (!child.isDirectory) continue
            val name = child.name ?: continue
            if (FileNames.isNote(name)) {
                val document = runCatching { readDocument(child) }.getOrNull()
                if (document?.id == noteId) return child
            } else {
                findNoteFolderIn(child, noteId)?.let { return it }
            }
        }
        return null
    }

    /** Szuka w koszu wpisu, który przed wyrzuceniem leżał pod [wanted]. */
    private fun findByOriginalPath(
        entry: DocumentFile,
        path: String,
        wanted: String,
    ): DocumentFile? {
        if (path == wanted) return entry
        if (entry.isFile) return null
        val name = entry.name ?: return null
        if (FileNames.isNote(name)) return null
        for (child in entry.listFiles()) {
            val childName = child.name ?: continue
            findByOriginalPath(child, "$path/$childName", wanted)?.let { return it }
        }
        return null
    }

    private fun collectCodePathsIn(entry: DocumentFile, path: String, into: MutableList<String>) {
        val name = entry.name ?: return
        if (entry.isFile) {
            if (CodeLanguage.fromExtension(name) != null) into += path
            return
        }
        if (FileNames.isNote(name)) return
        for (child in entry.listFiles()) {
            val childName = child.name ?: continue
            collectCodePathsIn(child, "$path/$childName", into)
        }
    }

    // Walking the whole tree, needed when building the index

    fun walkTree(visit: (LibraryItem) -> Unit) {
        fun descend(folder: DocumentFile, path: String) {
            for (child in folder.listFiles()) {
                val name = child.name ?: continue
                if (FileNames.isHidden(name)) continue
                val item = toItem(child, path) ?: continue
                visit(item)
                if (item.type == ItemType.FOLDER) {
                    descend(child, item.path)
                }
            }
        }
        descend(root, "")
    }

    companion object {
        const val BACKUP_FILE = "content.bak.json"

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
            CodeLanguage.HTML ->
                "<!doctype html>\n<html lang=\"${if (words.english) "en" else "pl"}\">\n<head>\n  <meta charset=\"utf-8\">\n" +
                    "  <title>${words.myPageTitle}</title>\n</head>\n<body>\n  <h1>${words.greetingWord}</h1>\n</body>\n</html>\n"
            CodeLanguage.PLAIN_TEXT -> ""
        }
    }
}
