package wojtoteka.ovh.kajet.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wojtoteka.ovh.kajet.core.model.CodeLanguage
import wojtoteka.ovh.kajet.core.model.ItemType
import wojtoteka.ovh.kajet.core.model.LibraryItem
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode
import wojtoteka.ovh.kajet.storage.index.IndexDao
import wojtoteka.ovh.kajet.storage.index.IndexEntry
import wojtoteka.ovh.kajet.storage.index.IndexText

class LibraryRepository(
    private val context: Context,
    private val settings: SettingsStore,
    private val dao: IndexDao,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    private val refreshTick = MutableStateFlow(0)

    var onNoteSaved: ((path: String, noteId: String) -> Unit)? = null

    private val backgroundScope = CoroutineScope(SupervisorJob() + io)

    private var cache: Pair<String, LibraryStore>? = null

    val folderSelected: Flow<Boolean> = settings.settings.map { !it.libraryFolder.isNullOrBlank() }

    suspend fun setLibraryFolder(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        cache = null
        settings.setLibraryFolder(uri.toString())
        refresh()
    }

    suspend fun hasAccess(): Boolean = store()?.root?.canWrite() == true

    suspend fun store(): LibraryStore? = withContext(io) {
        val uri = settings.settings.first().libraryFolder ?: return@withContext null
        cache?.let { (remembered, store) -> if (remembered == uri) return@withContext store }

        val root = DocumentFile.fromTreeUri(context, Uri.parse(uri)) ?: return@withContext null
        if (!root.isDirectory) return@withContext null
        val created = LibraryStore(context.contentResolver, root)
        cache = uri to created
        created
    }

    private suspend fun requireStore(): LibraryStore = store()
        ?: throw java.io.IOException(
            "Nie wybrano katalogu na notatki. Otwórz ustawienia i wskaż folder na urządzeniu.",
        )

    fun refresh() {
        refreshTick.value = refreshTick.value + 1
    }

    // Reading lists

    fun folder(path: String): Flow<List<LibraryItem>> =
        combine(refreshTick, settings.settings) { tick, _ -> tick }
            .map {
                withContext(io) {
                    val store = store() ?: return@withContext emptyList()
                    val fromDisk = store.list(path)
                    // Sortujemy jeszcze raz, bo indeks ma pewniejsze daty niż SAF,
                    // który potrafi oddać zero w lastModified.
                    fromDisk.map { item -> enrichFromIndex(item) }
                        .sortedWith(LibraryStore.libraryOrder)
                }
            }

    private suspend fun enrichFromIndex(item: LibraryItem): LibraryItem {
        if (item.type != ItemType.NOTE) return item
        val indexed = dao.find(item.documentUri) ?: return item
        return item.copy(
            name = indexed.name.ifBlank { item.name },
            noteKind = runCatching { indexed.noteKind?.let { NoteKind.valueOf(it) } }.getOrNull(),
            favorite = indexed.favorite,
            tags = indexed.tags.split('|').filter { it.isNotBlank() },
            preview = indexed.preview.ifBlank { null },
            updatedAt = if (indexed.updatedAt > 0) indexed.updatedAt else item.updatedAt,
        )
    }

    fun favorites(): Flow<List<LibraryItem>> =
        dao.favorites().map { list -> list.map { it.toLibraryItem() } }

    fun recent(count: Int = 20): Flow<List<LibraryItem>> =
        dao.recent(count).map { list -> list.map { it.toLibraryItem() } }

    /**
     * Ścieżka i identyfikator każdej notatki ze spisu. Synchronizacja używa
     * tego do uzgodnienia całej biblioteki, a nie tylko notatek zapisanych
     * po zalogowaniu.
     */
    suspend fun allNoteIds(): List<Pair<String, String>> = withContext(io) {
        dao.allNotes().map { it.path to it.documentId }
    }

    suspend fun search(query: String): List<LibraryItem> = withContext(io) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val byName = dao.searchNames(trimmed)
        val byContent = runCatching { dao.searchContent(ftsQuery(trimmed)) }.getOrDefault(emptyList())

        (byName + byContent)
            .distinctBy { it.documentUri }
            .map { it.toLibraryItem() }
    }

    private fun ftsQuery(text: String): String = text
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> "\"" + word.replace("\"", "") + "\"*" }

    // Changes in the library

    suspend fun createFolder(parent: String, name: String, colorId: String, iconId: String): LibraryItem =
        withContext(io) {
            val item = requireStore().createFolder(parent, name, colorId, iconId)
            dao.upsertKeepingOpened(item.toIndexEntry())
            refresh()
            item
        }

    suspend fun createNote(
        parent: String,
        title: String,
        kind: NoteKind,
        mode: PageMode = PageMode.A4,
        background: PageBackground = PageBackground.LINED,
    ): LibraryItem = withContext(io) {
        val store = requireStore()
        val item = store.createNote(parent, title, kind, mode, background)
        val document = store.readNote(item.path)
        writeToIndex(item, document)
        refresh()
        // Świeża notatka też musi pojechać do chmury. Bez tego trafiała tam
        // dopiero po pierwszej poprawce, a notatka założona i zostawiona pusta
        // nie pojawiała się na stronie w ogóle.
        onNoteSaved?.invoke(item.path, document.id)
        item
    }

    suspend fun createCodeFile(parent: String, name: String, language: CodeLanguage): LibraryItem =
        withContext(io) {
            val item = requireStore().createCodeFile(parent, name, language)
            dao.upsertKeepingOpened(item.toIndexEntry())
            refresh()
            item
        }

    suspend fun readNote(path: String): NoteDocument = withContext(io) {
        requireStore().readNote(path)
    }

    suspend fun noteKind(path: String): NoteKind? = withContext(io) {
        dao.findByPath(path)?.noteKind?.let { name ->
            runCatching { NoteKind.valueOf(name) }.getOrNull()
        } ?: runCatching { readNote(path).kind }.getOrNull()
    }

    suspend fun writeNote(path: String, document: NoteDocument) = withContext(io) {
        val store = requireStore()
        store.writeNote(path, document)
        val entry = store.entry(path)
        if (entry != null) {
            writeToIndex(
                LibraryItem(
                    id = document.id,
                    path = path,
                    name = document.title,
                    type = ItemType.NOTE,
                    documentUri = entry.uri.toString(),
                    updatedAt = document.updatedAt,
                    noteKind = document.kind,
                    favorite = document.favorite,
                    tags = document.tags,
                ),
                document,
            )
        }
        onNoteSaved?.invoke(path, document.id)
    }

    suspend fun toggleFavorite(path: String): Boolean = withContext(io) {
        val document = requireStore().readNote(path)
        val after = document.copy(favorite = !document.favorite)
        writeNote(path, after)
        refresh()
        after.favorite
    }

    fun writeInBackground(path: String, document: NoteDocument) {
        backgroundScope.launch {
            runCatching { writeNote(path, document) }
        }
    }

    suspend fun readText(path: String): String = withContext(io) {
        requireStore().readText(path)
    }

    suspend fun writeText(path: String, content: String) = withContext(io) {
        requireStore().writeText(path, content)
    }

    fun writeTextInBackground(path: String, content: String) {
        backgroundScope.launch { runCatching { writeText(path, content) } }
    }

    suspend fun writeAttachment(notePath: String, name: String, data: ByteArray, mime: String): String =
        withContext(io) { requireStore().writeAttachment(notePath, name, data, mime) }

    suspend fun putAttachment(notePath: String, name: String, data: ByteArray, mime: String) =
        withContext(io) { requireStore().putAttachment(notePath, name, data, mime) }

    suspend fun readAttachment(notePath: String, name: String): ByteArray? =
        withContext(io) { store()?.readAttachment(notePath, name) }

    suspend fun attachmentNames(notePath: String): List<String> = withContext(io) {
        val folder = store()?.entry(notePath)?.takeIf { it.isDirectory }
            ?: return@withContext emptyList()
        folder.findFile(NoteDocument.ASSETS_DIRECTORY)
            ?.listFiles()
            ?.filter { it.isFile }
            ?.mapNotNull { it.name }
            .orEmpty()
    }

    suspend fun writeNoteFromCloud(
        document: NoteDocument,
        targetFolder: String = "",
    ): String? = withContext(io) {
        val store = store() ?: return@withContext null

        val existing = dao.findById(document.id)
        if (existing != null && store.entry(existing.path) != null) {
            store.writeNote(existing.path, document)
            writeToIndex(
                LibraryItem(
                    id = document.id,
                    path = existing.path,
                    name = document.title,
                    type = ItemType.NOTE,
                    documentUri = existing.documentUri,
                    updatedAt = document.updatedAt,
                    noteKind = document.kind,
                    favorite = document.favorite,
                    tags = document.tags,
                ),
                document,
            )
            refresh()
            return@withContext existing.path
        }

        // A new note from the server. We start an empty one and write the content
        // into it at once, so the directory name comes out the same as for a note
        // created by hand.
        val item = store.createNote(targetFolder, document.title, document.kind)
        store.writeNote(item.path, document)
        writeToIndex(item.copy(name = document.title, noteKind = document.kind), document)
        refresh()
        item.path
    }

    suspend fun rename(path: String, newName: String): String = withContext(io) {
        val newPath = requireStore().rename(path, newName)
        dao.deleteBranch(path)
        reindexBranch(newPath)
        refresh()
        newPath
    }

    suspend fun move(path: String, targetFolder: String): String = withContext(io) {
        val newPath = requireStore().move(path, targetFolder)
        dao.deleteBranch(path)
        reindexBranch(newPath)
        refresh()
        newPath
    }

    suspend fun copy(path: String, targetFolder: String): String = withContext(io) {
        val newPath = requireStore().copy(path, targetFolder)
        reindexBranch(newPath)
        refresh()
        newPath
    }

    suspend fun updateFolderLook(path: String, colorId: String, iconId: String) = withContext(io) {
        requireStore().updateFolderLook(path, colorId, iconId)
        reindexBranch(path)
        refresh()
    }

    /**
     * Zapisuje, że notatka była otwarta. Kiedy wpisu nie ma jeszcze w spisie
     * albo leży pod innym adresem pliku, dokładamy go tu zamiast po cichu nic
     * nie zrobić. Inaczej „ostatnio otwarte" zostaje puste mimo otwierania.
     */
    suspend fun rememberOpened(item: LibraryItem) = withContext(io) {
        val now = System.currentTimeMillis()
        if (dao.rememberOpened(item.documentUri, now) > 0) return@withContext
        if (dao.rememberOpenedByPath(item.path, now) > 0) return@withContext
        dao.upsert(item.toIndexEntry().copy(openedAt = now))
    }

    // The bin

    suspend fun moveToTrash(path: String) = withContext(io) {
        requireStore().moveToTrash(path)
        dao.deleteBranch(path)
        refresh()
    }

    suspend fun listTrash(): List<TrashEntry> = withContext(io) {
        store()?.listTrash() ?: emptyList()
    }

    suspend fun restoreFromTrash(id: String): String = withContext(io) {
        val path = requireStore().restore(id)
        reindexBranch(path)
        refresh()
        path
    }

    suspend fun deletePermanently(id: String) = withContext(io) {
        requireStore().deletePermanently(id)
        refresh()
    }

    suspend fun emptyTrash() = withContext(io) {
        requireStore().emptyTrash()
        refresh()
    }

    // The index

    suspend fun rebuildIfEmpty(progress: ((done: Int, total: Int) -> Unit)? = null) =
        withContext(io) {
            if (dao.count() > 0) return@withContext
            val store = store() ?: return@withContext
            if (store.list("").isEmpty()) return@withContext
            rebuildIndex(progress)
        }

    suspend fun rebuildIndex(progress: ((done: Int, total: Int) -> Unit)? = null) = withContext(io) {
        val store = store() ?: return@withContext
        dao.clear()

        // A listing of everything first, so we can show how much is still left.
        val items = ArrayList<LibraryItem>(128)
        store.walkTree { items += it }

        items.forEachIndexed { number, item ->
            if (item.type == ItemType.NOTE) {
                val document = runCatching { store.readNote(item.path) }.getOrNull()
                if (document != null) {
                    writeToIndex(item.copy(name = document.title, noteKind = document.kind), document)
                } else {
                    dao.upsertKeepingOpened(item.toIndexEntry())
                }
            } else {
                dao.upsertKeepingOpened(item.toIndexEntry())
            }
            progress?.invoke(number + 1, items.size)
        }
        refresh()
    }

    private suspend fun reindexBranch(path: String) {
        val store = store() ?: return
        val entry = store.entry(path) ?: return
        if (entry.isDirectory && FileNames.isNote(entry.name.orEmpty())) {
            val document = runCatching { store.readDocument(entry) }.getOrNull()
            val item = LibraryItem(
                id = document?.id ?: entry.uri.toString(),
                path = path,
                name = document?.title ?: FileNames.withoutNoteExtension(entry.name.orEmpty()),
                type = ItemType.NOTE,
                documentUri = entry.uri.toString(),
                updatedAt = entry.lastModified(),
                noteKind = document?.kind,
                favorite = document?.favorite ?: false,
                tags = document?.tags.orEmpty(),
            )
            if (document != null) {
                writeToIndex(item, document)
            } else {
                dao.upsertKeepingOpened(item.toIndexEntry())
            }
            return
        }
        // A folder or a plain file: we walk the branch again.
        val parentPath = path.substringBeforeLast('/', "")
        val onDisk = store.list(parentPath).firstOrNull { it.path == path } ?: return
        dao.upsertKeepingOpened(onDisk.toIndexEntry())
        if (onDisk.type == ItemType.FOLDER) {
            for (child in store.list(path)) {
                reindexBranch(child.path)
            }
        }
    }

    private suspend fun writeToIndex(item: LibraryItem, document: NoteDocument) {
        dao.save(
            entry = item.toIndexEntry().copy(
                // The identifier comes from the content rather than the listing
                // entry: when read from disk the entry carries the file address,
                // not the note's number.
                documentId = document.id,
                name = document.title,
                noteKind = document.kind.name,
                favorite = document.favorite,
                tags = document.tags.joinToString("|"),
                updatedAt = document.updatedAt,
                preview = IndexText.preview(document),
            ),
            title = document.title,
            content = IndexText.content(document),
        )
    }
}

private fun LibraryItem.toIndexEntry() = IndexEntry(
    documentUri = documentUri,
    // For notes the identifier comes from content.json and is shared with the
    // server. For folders and files it stays empty, because those do not go to
    // the cloud.
    documentId = if (type == ItemType.NOTE) id else "",
    path = path,
    name = name,
    type = type.name,
    noteKind = noteKind?.name,
    language = language?.id,
    colorId = colorId,
    iconId = iconId,
    favorite = favorite,
    tags = tags.joinToString("|"),
    updatedAt = updatedAt,
    preview = preview.orEmpty(),
)

private fun IndexEntry.toLibraryItem() = LibraryItem(
    id = documentUri,
    path = path,
    name = name,
    type = runCatching { ItemType.valueOf(type) }.getOrDefault(ItemType.OTHER_FILE),
    documentUri = documentUri,
    updatedAt = updatedAt,
    noteKind = runCatching { noteKind?.let { NoteKind.valueOf(it) } }.getOrNull(),
    language = CodeLanguage.fromId(language),
    colorId = colorId,
    iconId = iconId,
    favorite = favorite,
    tags = tags.split('|').filter { it.isNotBlank() },
    preview = preview.ifBlank { null },
)
