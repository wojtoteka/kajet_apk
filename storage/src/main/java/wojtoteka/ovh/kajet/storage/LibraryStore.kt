package wojtoteka.ovh.kajet.storage

import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile
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
            "Nie ma już folderu $path. Ktoś mógł go przenieść poza aplikacją.",
        )

    // Reading the content of a folder

    fun list(path: String): List<LibraryItem> {
        val folder = folder(path) ?: return emptyList()
        return folder.listFiles()
            .mapNotNull { toItem(it, path) }
            .sortedWith(
                compareBy<LibraryItem> { it.type != ItemType.FOLDER }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )
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
                    childCount = file.listFiles().count { it.name?.startsWith('.') == false },
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
    ): LibraryItem {
        val parent = requireFolder(parentPath)
        val nameOnDisk = FileNames.unique(name, DiskFiles.occupiedNames(parent))
        val folder = parent.createDirectory(nameOnDisk)
            ?: throw IOException("Nie udało się utworzyć folderu $name. Sprawdź, czy jest miejsce na dysku.")

        val meta = FolderMeta(
            id = UUID.randomUUID().toString(),
            displayName = name.trim().ifEmpty { nameOnDisk },
            colorId = colorId,
            iconId = iconId,
            createdAt = System.currentTimeMillis(),
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
        writeFolderMeta(folder, previous.copy(colorId = colorId, iconId = iconId))
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
            ?: throw IOException("Nie udało się utworzyć notatki. Sprawdź, czy jest miejsce na dysku.")

        val now = System.currentTimeMillis()
        val document = NoteDocument(
            id = UUID.randomUUID().toString(),
            kind = kind,
            title = title.trim().ifEmpty { "Bez tytułu" },
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
            ?: throw IOException("Nie ma już notatki $path.")
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
            "Nie da się odczytać notatki ${noteFolder.name}. Plik content.json jest uszkodzony.",
        )
    }

    fun writeNote(path: String, document: NoteDocument) {
        val folder = entry(path)?.takeIf { it.isDirectory }
            ?: throw IOException("Nie ma już notatki $path. Zmiany nie zostały zapisane.")
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
            ?: throw IOException("Nie ma już notatki $notePath.")
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
            ?: throw IOException("Nie ma już notatki $notePath.")
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
            ?: throw IOException("Nie ma już notatki $notePath.")
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

    fun readText(path: String): String {
        val file = entry(path)?.takeIf { it.isFile }
            ?: throw IOException("Nie ma już pliku $path.")
        return DiskFiles.readText(resolver, file)
    }

    fun writeText(path: String, content: String) {
        val file = entry(path)?.takeIf { it.isFile }
            ?: throw IOException("Nie ma już pliku $path. Zmiany nie zostały zapisane.")
        DiskFiles.writeText(resolver, file, content)
    }

    // Renaming, moving, copying

    fun rename(path: String, newName: String): String {
        val entry = entry(path) ?: throw IOException("Nie ma już wpisu $path.")
        val parent = folder(path.substringBeforeLast('/', ""))
            ?: throw IOException("Nie ma już folderu nadrzędnego.")
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
        if (!entry.renameTo(target)) {
            throw IOException("Nie udało się zmienić nazwy na $newName.")
        }

        // The real name goes inside as well, because on disk it may have been trimmed.
        if (isNote) {
            val document = runCatching { readDocument(entry) }.getOrNull()
            if (document != null) writeDocument(entry, document.copy(title = newName.trim()))
        } else if (entry.isDirectory) {
            val meta = readFolderMeta(entry)
            if (meta != null) writeFolderMeta(entry, meta.copy(displayName = newName.trim()))
        }

        val parentPath = path.substringBeforeLast('/', "")
        return if (parentPath.isEmpty()) target else "$parentPath/$target"
    }

    fun move(path: String, targetFolder: String): String {
        val source = entry(path) ?: throw IOException("Nie ma już wpisu $path.")
        val target = requireFolder(targetFolder)
        if (path == targetFolder || targetFolder.startsWith("$path/")) {
            throw IOException("Nie można przenieść folderu do jego własnego wnętrza.")
        }
        val name = source.name ?: throw IOException("Wpis nie ma nazwy.")
        val newName = FileNames.unique(
            name = name.substringBeforeLast('.', name),
            occupied = DiskFiles.occupiedNames(target),
            extension = if (name.contains('.')) "." + name.substringAfterLast('.') else "",
        )
        DiskFiles.copyRecursively(resolver, source, target, newName)
        DiskFiles.deleteRecursively(source)
        return if (targetFolder.isEmpty()) newName else "$targetFolder/$newName"
    }

    fun copy(path: String, targetFolder: String): String {
        val source = entry(path) ?: throw IOException("Nie ma już wpisu $path.")
        val target = requireFolder(targetFolder)
        val name = source.name ?: throw IOException("Wpis nie ma nazwy.")
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
            name = "$stem (kopia)",
            occupied = DiskFiles.occupiedNames(target),
            extension = extension,
        )
        DiskFiles.copyRecursively(resolver, source, target, newName)
        return if (targetFolder.isEmpty()) newName else "$targetFolder/$newName"
    }

    // The bin

    private fun trashFolder(): DocumentFile = DiskFiles.folderForWrite(root, TrashEntry.DIRECTORY)

    fun moveToTrash(path: String) {
        val source = entry(path) ?: throw IOException("Nie ma już wpisu $path.")
        val name = source.name ?: throw IOException("Wpis nie ma nazwy.")
        val trash = trashFolder()
        val id = UUID.randomUUID().toString()
        val slot = trash.createDirectory(id)
            ?: throw IOException("Nie udało się otworzyć kosza. Sprawdź, czy jest miejsce na dysku.")

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
        )
        val descriptionFile = DiskFiles.fileForWrite(slot, TrashEntry.DESC_FILE, DiskFiles.MIME_JSON)
        DiskFiles.writeText(resolver, descriptionFile, NoteCodec.json.encodeToString(description))

        DiskFiles.copyRecursively(resolver, source, slot, name)
        DiskFiles.deleteRecursively(source)
    }

    fun listTrash(): List<TrashEntry> {
        val trash = root.findFile(TrashEntry.DIRECTORY)?.takeIf { it.isDirectory } ?: return emptyList()
        return trash.listFiles().mapNotNull { slot ->
            if (!slot.isDirectory) return@mapNotNull null
            val description = slot.findFile(TrashEntry.DESC_FILE) ?: return@mapNotNull null
            runCatching {
                NoteCodec.json.decodeFromString<TrashEntry>(DiskFiles.readText(resolver, description))
            }.getOrNull()
        }.sortedByDescending { it.deletedAt }
    }

    fun restore(id: String): String {
        val trash = root.findFile(TrashEntry.DIRECTORY)?.takeIf { it.isDirectory }
            ?: throw IOException("Kosz jest pusty.")
        val slot = trash.findFile(id)?.takeIf { it.isDirectory }
            ?: throw IOException("Tego wpisu nie ma już w koszu.")
        val descriptionFile = slot.findFile(TrashEntry.DESC_FILE)
            ?: throw IOException("Wpis w koszu nie ma opisu, więc nie wiadomo, gdzie go odłożyć.")
        val description =
            NoteCodec.json.decodeFromString<TrashEntry>(DiskFiles.readText(resolver, descriptionFile))

        val source = slot.findFile(description.fileName)
            ?: throw IOException("W koszu nie ma już pliku ${description.fileName}.")

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

    fun deletePermanently(id: String) {
        val trash = root.findFile(TrashEntry.DIRECTORY)?.takeIf { it.isDirectory } ?: return
        val slot = trash.findFile(id) ?: return
        DiskFiles.deleteRecursively(slot)
    }

    fun emptyTrash() {
        val trash = root.findFile(TrashEntry.DIRECTORY)?.takeIf { it.isDirectory } ?: return
        for (slot in trash.listFiles()) {
            DiskFiles.deleteRecursively(slot)
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

        fun codeTemplate(language: CodeLanguage): String = when (language) {
            CodeLanguage.PYTHON -> "print(\"Cześć\")\n"
            CodeLanguage.C -> "#include <stdio.h>\n\nint main(void) {\n    printf(\"Cześć\\n\");\n    return 0;\n}\n"
            CodeLanguage.CPP -> "#include <iostream>\n\nint main() {\n    std::cout << \"Cześć\" << std::endl;\n    return 0;\n}\n"
            CodeLanguage.JAVA -> "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Cześć\");\n    }\n}\n"
            CodeLanguage.KOTLIN -> "fun main() {\n    println(\"Cześć\")\n}\n"
            CodeLanguage.JAVASCRIPT -> "console.log(\"Cześć\")\n"
            CodeLanguage.TYPESCRIPT -> "const powitanie: string = \"Cześć\"\nconsole.log(powitanie)\n"
            CodeLanguage.CSHARP -> "using System;\n\nclass Program {\n    static void Main() {\n        Console.WriteLine(\"Cześć\");\n    }\n}\n"
            CodeLanguage.GO -> "package main\n\nimport \"fmt\"\n\nfunc main() {\n    fmt.Println(\"Cześć\")\n}\n"
            CodeLanguage.RUST -> "fn main() {\n    println!(\"Cześć\");\n}\n"
            CodeLanguage.PHP -> "<?php\necho \"Cześć\\n\";\n"
            CodeLanguage.RUBY -> "puts \"Cześć\"\n"
            CodeLanguage.BASH -> "echo \"Cześć\"\n"
            CodeLanguage.SQL -> "select 'Cześć';\n"
            CodeLanguage.PLAIN_TEXT -> ""
        }
    }
}
