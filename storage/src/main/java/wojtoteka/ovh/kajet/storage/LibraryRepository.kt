package wojtoteka.ovh.kajet.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wojtoteka.ovh.kajet.core.awaria.failureHandler
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
import wojtoteka.ovh.kajet.core.text.words

/**
 * Zdjęcie zawartości kosza: pod którym wpisem co leży.
 *
 * Kosz jest poza spisem, więc jedyny sposób, żeby się dowiedzieć, co w nim
 * jest, to przeczytać z dysku content.json każdej leżącej tam notatki. Przy
 * przechodzeniu długiego spisu nagrobków robienie tego raz na nagrobek
 * kosztowałoby setki odczytów przez SAF za każdą synchronizację - i to
 * głównie dla notatek, których na urządzeniu w ogóle nie ma. Dlatego zdjęcie
 * robi się raz na przebieg i wędruje dalej.
 *
 * Zdjęcie nie starzeje się w trakcie kasowania: skasowanie jednej rzeczy z
 * wpisu kosza nie rusza pozostałych, a wpis znika w całości tylko wtedy, gdy
 * i tak nic więcej w nim nie było.
 */
data class TrashContents(
    /** Identyfikator notatki → identyfikator wpisu kosza, w którym leży. */
    val noteSlots: Map<String, String> = emptyMap(),
    /** Pierwotna ścieżka pliku z kodem → identyfikator wpisu kosza. */
    val codeSlots: Map<String, String> = emptyMap(),
)

/** Co synchronizacja zrobiła z notatką, którą serwer zgłosił jako skasowaną. */
enum class ServerDeletion {
    /** Leżała w koszu - zniknęła doszczętnie. */
    ERASED,

    /** Była widoczna w bibliotece - poszła do kosza, żeby dało się ją wyjąć. */
    TRASHED,

    /** Nie było jej tutaj ani w bibliotece, ani w koszu. */
    NOTHING,
}

class LibraryRepository(
    private val context: Context,
    private val settings: SettingsStore,
    private val dao: IndexDao,
    private val retries: DeleteRetryQueue,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : CloudLibrary {

    private val refreshTick = MutableStateFlow(0)

    /*
      Ścieżki rzeczy, które właśnie zniknęły z biblioteki na polecenie serwera
      (do kosza albo doszczętnie). Otwarty edytor nasłuchuje tego i zamiast
      męczyć nieistniejący plik kolejnymi zapisami, pyta człowieka, co zrobić
      z treścią, którą wciąż ma na ekranie. Zapas w buforze, bo kasowań potrafi
      przyjść naraz kilka, a nikt na emisji nie wisi.
    */
    private val _remotelyRemovedPaths = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val remotelyRemovedPaths: SharedFlow<String> = _remotelyRemovedPaths.asSharedFlow()

    private fun reportRemotelyRemoved(path: String) {
        if (path.isNotBlank()) _remotelyRemovedPaths.tryEmit(path)
    }

    var onNoteSaved: ((path: String, noteId: String) -> Unit)? = null

    /**
     * Czy otwarta notatka jest na serwerze, czy tylko na dysku. Edytor czyta
     * to przy pasku zapisu; chmura podpina tu kolejkę i zapamiętane wersje.
     */
    var cloudSave: CloudSaveLookup? = null

    /** Notatki wyrzucone do kosza na urządzeniu - do zgłoszenia serwerowi. */
    var onNotesTrashed: ((noteIds: List<String>) -> Unit)? = null

    /** Notatki skasowane na stałe (kosz opróżniony) - serwer też ma je usunąć. */
    var onNotesPurged: ((noteIds: List<String>) -> Unit)? = null

    /**
     * Po notatce nie ma już śladu na dysku - niech zniknie też z pamięci
     * chmury: zapamiętana wersja, powiązanie pliku z kodem, wpis w kolejce
     * wysyłki. Inaczej te wpisy zostawały na zawsze i potrafiły wskrzesić
     * skasowaną notatkę przy najbliższym uzgadnianiu biblioteki.
     */
    var onNoteErased: ((noteId: String) -> Unit)? = null

    /** To samo dla pliku z kodem, który chmura zna po ścieżce. */
    var onCodeErased: ((path: String) -> Unit)? = null

    /** Zapisany plik z kodem - do wysłania na serwer jako notatka CODE. */
    var onCodeSaved: ((path: String) -> Unit)? = null

    /** Pliki z kodem wyrzucone do kosza - do zgłoszenia serwerowi. */
    var onCodeTrashed: ((paths: List<String>) -> Unit)? = null

    /** Foldery wyrzucone do kosza - serwer kasuje swoje odpowiedniki. */
    var onFoldersTrashed: ((folderIds: List<String>) -> Unit)? = null

    /**
     * Założony, przemianowany albo przemalowany folder - jedzie do chmury od
     * razu.
     *
     * Wcześniej pusty folder czekał na serwer aż do najbliższego pełnego
     * przebiegu synchronizacji (w praktyce: do chwili, gdy wpadła w niego
     * pierwsza notatka), więc na stronie po prostu go nie było i wyglądało to
     * na zgubioną zmianę.
     */
    var onFolderChanged: (() -> Unit)? = null

    /** Pliki z kodem skasowane na stałe - do zgłoszenia serwerowi. */
    var onCodePurged: ((paths: List<String>) -> Unit)? = null

    /** Świeżo założony plik z kodem - dostaje w chmurze świeżą tożsamość. */
    var onCodeCreated: ((path: String) -> Unit)? = null

    /** Zmiana nazwy albo przeniesienie - chmura przepina swoje powiązania. */
    var onPathMoved: ((oldPath: String, newPath: String) -> Unit)? = null

    /*
      SupervisorJob pilnuje tylko, żeby awaria jednego zadania nie zabrała
      pozostałych. Sam wyjątek i tak szedł do domyślnego handlera wątku, czyli
      ubijał aplikację. Handler zatrzymuje go tutaj - a zapis notatki w tle
      dzieje się właśnie wtedy, gdy ekran notatki już się zamyka.
    */
    private val backgroundScope = CoroutineScope(
        SupervisorJob() + io + failureHandler("zapis w tle"),
    )

    private var cache: Pair<String, LibraryStore>? = null

    val folderSelected: Flow<Boolean> = settings.settings.map { current ->
        hasPersistedAccess(current.libraryFolder)
    }

    init {
        backgroundScope.launch {
            settings.migrateLibraryFolderOutOfBackup()
        }
    }

    suspend fun setLibraryFolder(uri: Uri) {
        SafAccess.takePersistable(context.contentResolver, uri)
        cache = null
        settings.setLibraryFolder(uri.toString())
        refresh()
    }

    /**
     * Czy system wciąż honoruje trwałe uprawnienie do zapisanego drzewa.
     * Sam niepusty adres w ustawieniach tego nie gwarantuje - po restore
     * kopii zapasowej adres wraca, a [ContentResolver.getPersistedUriPermissions]
     * jest puste.
     */
    fun hasPersistedAccess(folderUri: String?): Boolean =
        SafAccess.hasPersistedGrant(context.contentResolver, folderUri)

    suspend fun hasAccess(): Boolean {
        val folder = settings.settings.first().libraryFolder
        if (!hasPersistedAccess(folder)) return false
        return store()?.root?.canWrite() == true
    }

    override suspend fun hasStore(): Boolean = store() != null

    suspend fun store(): LibraryStore? = withContext(io) {
        val uri = settings.settings.first().libraryFolder ?: return@withContext null
        if (!hasPersistedAccess(uri)) {
            cache = null
            return@withContext null
        }
        cache?.let { (remembered, store) -> if (remembered == uri) return@withContext store }

        val root = DocumentFile.fromTreeUri(context, Uri.parse(uri)) ?: return@withContext null
        if (!root.isDirectory) return@withContext null
        val created = LibraryStore(context.contentResolver, root)
        cache = uri to created
        created
    }

    private suspend fun requireStore(): LibraryStore = store()
        ?: throw java.io.IOException(
            words.noNotesFolderChosen,
        )

    override fun refresh() {
        refreshTick.value = refreshTick.value + 1
    }

    // Reading lists

    fun folder(path: String): Flow<List<LibraryItem>> =
        combine(refreshTick, settings.settings) { _, current -> current.favoriteFiles }
            .map { starredFiles ->
                withContext(io) {
                    val store = store() ?: return@withContext emptyList()
                    val fromDisk = store.list(path)
                    // Sortujemy jeszcze raz, bo indeks ma pewniejsze daty niż SAF,
                    // który potrafi oddać zero w lastModified.
                    fromDisk.map { item -> enrichFromIndex(item, starredFiles) }
                        .sortedWith(LibraryStore.libraryOrder)
                }
            }

    private suspend fun enrichFromIndex(
        item: LibraryItem,
        starredFiles: Set<String>,
    ): LibraryItem {
        // Plik gwiazdki w sobie nie niesie - ta stoi w ustawieniach, patrz
        // [KajetSettings.favoriteFiles]. Spis jest tu tylko odbiciem.
        if (item.type != ItemType.NOTE) {
            return item.copy(favorite = item.path in starredFiles)
        }
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
    override suspend fun allNoteIds(): List<Pair<String, String>> = withContext(io) {
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
            // Pusty folder też jest zmianą - chmura ma go zobaczyć od razu,
            // a nie dopiero razem z pierwszą notatką w środku.
            onFolderChanged?.invoke()
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
            // Świeży plik też jedzie do chmury, tak samo jak świeża notatka.
            onCodeCreated?.invoke(item.path)
            item
        }

    /**
     * Plik z Udostępnij / Otwórz w - kopia w katalogu notatek, potem ten sam
     * `open()` co w bibliotece (notatka, edytor, podgląd zdjęcia/PDF, binarka).
     */
    suspend fun importFile(
        parent: String,
        fileName: String,
        mime: String,
        source: Uri,
    ): LibraryItem = withContext(io) {
        val item = requireStore().importFile(parent, fileName, mime, source)
        dao.upsertKeepingOpened(item.toIndexEntry())
        refresh()
        if (item.type == ItemType.CODE_FILE) onCodeCreated?.invoke(item.path)
        item
    }

    override suspend fun readNote(path: String): NoteDocument = withContext(io) {
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

    /**
     * Gwiazdka na notatce albo na pliku. Zwraca stan PO przełączeniu.
     *
     * Notatka trzyma gwiazdkę we własnej treści i stamtąd jedzie ona na serwer
     * razem z zapisem. Plik z kodem nie ma w czym jej zapisać (na dysku to
     * zwykły tekst), więc jego gwiazdka stoi w ustawieniach, a na serwer
     * dociera jako zwykłe zgłoszenie pliku do wysyłki - synchronizacja dokłada
     * ją do notatki CODE. Do tej pory ta droga w ogóle nie istniała i gwiazdki
     * na plikach HTML czy Pythona nie dało się w aplikacji postawić.
     */
    suspend fun toggleFavorite(path: String): Boolean = withContext(io) {
        if (!FileNames.isNote(path.substringAfterLast('/'))) {
            return@withContext toggleFileFavorite(path)
        }
        val document = requireStore().readNote(path)
        val after = document.copy(favorite = !document.favorite)
        writeNote(path, after)
        refresh()
        after.favorite
    }

    private suspend fun toggleFileFavorite(path: String): Boolean {
        val after = path !in starredFiles()
        settings.setFileFavorite(path, after)
        // Spis idzie za ustawieniami - z niego czytają „Ulubione".
        dao.findByPath(path)?.let { dao.upsert(it.copy(favorite = after)) }
        refresh()
        onCodeSaved?.invoke(path)
        return after
    }

    private suspend fun starredFiles(): Set<String> = settings.settings.first().favoriteFiles

    /** Czy plik ma gwiazdkę - synchronizacja dokłada ją do notatki CODE. */
    override suspend fun fileFavorite(path: String): Boolean = withContext(io) {
        path in starredFiles()
    }

    /** Gwiazdka pliku przysłana z serwera - bez odsyłania jej z powrotem. */
    override suspend fun setFileFavoriteFromCloud(path: String, favorite: Boolean) {
        withContext(io) {
            if ((path in starredFiles()) == favorite) return@withContext
            settings.setFileFavorite(path, favorite)
            dao.findByPath(path)?.let { dao.upsert(it.copy(favorite = favorite)) }
            refresh()
        }
    }

    fun writeInBackground(path: String, document: NoteDocument) {
        backgroundScope.launch {
            runCatching { writeNote(path, document) }
        }
    }

    /**
     * Czy wpisu nie ma już na dysku, choć sama biblioteka jest dostępna.
     *
     * Rozróżnienie ma znaczenie: odpięta karta albo cofnięte prawo do katalogu
     * to kłopot z magazynem, a nie skasowana notatka - wtedy odpowiedź brzmi
     * „nie wiadomo", czyli false, i nikt nie pyta człowieka o los treści.
     */
    suspend fun entryVanished(path: String): Boolean = withContext(io) {
        if (path.isBlank()) return@withContext false
        val store = store() ?: return@withContext false
        runCatching { store.entry(path) == null }.getOrDefault(false)
    }

    /**
     * Zapisuje dokument jako świeżą notatkę - dla treści, której pierwowzór
     * właśnie skasowano na innym urządzeniu, a człowiek wybrał „Zapisz jako
     * nową". Dokument przychodzi już z NOWYM identyfikatorem, żeby nie
     * wskrzeszać starego: po tamtym został na serwerze nagrobek i notatka
     * pod starym numerem zniknęłaby przy najbliższej synchronizacji.
     *
     * [assetsFromNoteId] to numer PIERWOTNEJ notatki - jej katalog zdążył
     * trafić do lokalnego kosza razem ze zdjęciami, więc załączniki wracają
     * stamtąd do nowej kopii. Gdy kosz już ich nie ma (kasowanie doszczętne),
     * nowa notatka powstaje bez nich; treść jest ważniejsza.
     *
     * Zwraca ścieżkę nowej notatki albo null, gdy biblioteka jest niedostępna.
     */
    suspend fun saveAsNewNote(
        targetFolder: String,
        document: NoteDocument,
        assetsFromNoteId: String? = null,
    ): String? = withContext(io) {
        val store = store() ?: return@withContext null
        // Folder mógł zniknąć razem z notatką - wtedy korzeń biblioteki.
        val parent = if (store.folder(targetFolder) != null) targetFolder else ""
        val item = store.createNote(parent, document.title, document.kind)
        store.writeNote(item.path, document)
        if (assetsFromNoteId != null) {
            runCatching { store.copyTrashedAssets(assetsFromNoteId, item.path) }
        }
        writeToIndex(item.copy(name = document.title, noteKind = document.kind), document)
        refresh()
        onNoteSaved?.invoke(item.path, document.id)
        item.path
    }

    /**
     * To samo dla pliku z kodem: świeży plik z tą samą nazwą i treścią.
     * Świeża tożsamość w chmurze idzie przez [onCodeCreated], więc nowy plik
     * nie odziedziczy numeru po skasowanym i go nie wskrzesi.
     */
    suspend fun saveAsNewCodeFile(
        targetFolder: String,
        fileName: String,
        content: String,
    ): String? = withContext(io) {
        val store = store() ?: return@withContext null
        val parent = if (store.folder(targetFolder) != null) targetFolder else ""
        val item = store.createTextFile(parent, fileName, content)
        dao.upsertKeepingOpened(item.toIndexEntry())
        refresh()
        onCodeCreated?.invoke(item.path)
        item.path
    }

    override suspend fun readText(path: String): String = withContext(io) {
        requireStore().readText(path)
    }

    /** Odczyt dla UI: nigdy nie ładuje więcej niż [maxBytes] do pamięci. */
    suspend fun readTextUpTo(path: String, maxBytes: Int): BoundedTextRead = withContext(io) {
        requireStore().readTextUpTo(path, maxBytes)
    }

    suspend fun writeText(path: String, content: String) = withContext(io) {
        requireStore().writeText(path, content)
        onCodeSaved?.invoke(path)
    }

    /** Zapis pliku przysłanego z chmury - bez odsyłania go z powrotem. */
    override suspend fun writeTextFromCloud(path: String, content: String) = withContext(io) {
        requireStore().writeText(path, content)
        refresh()
    }

    /**
     * Nowy plik z kodem przysłany z chmury. Zwraca ścieżkę, pod którą wylądował
     * (przy zajętej nazwie dostaje licznik).
     */
    override suspend fun createTextFileFromCloud(parent: String, fileName: String, content: String): String =
        withContext(io) {
            val item = requireStore().createTextFile(parent, fileName, content)
            // Plik pod tą samą ścieżką mógł już kiedyś mieć gwiazdkę - wraca
            // razem z nim, bo zbiór gwiazdek przeżył jego zniknięcie.
            dao.upsertKeepingOpened(
                item.toIndexEntry().copy(favorite = item.path in starredFiles()),
            )
            refresh()
            item.path
        }

    fun writeTextInBackground(path: String, content: String) {
        backgroundScope.launch { runCatching { writeText(path, content) } }
    }

    suspend fun writeAttachment(notePath: String, name: String, data: ByteArray, mime: String): String =
        withContext(io) { requireStore().writeAttachment(notePath, name, data, mime) }

    override suspend fun putAttachment(notePath: String, name: String, data: ByteArray, mime: String) =
        withContext(io) { requireStore().putAttachment(notePath, name, data, mime) }

    override suspend fun readAttachment(notePath: String, name: String): ByteArray? =
        withContext(io) { store()?.readAttachment(notePath, name) }

    override suspend fun attachmentNames(notePath: String): List<String> = withContext(io) {
        val folder = store()?.entry(notePath)?.takeIf { it.isDirectory }
            ?: return@withContext emptyList()
        folder.findFile(NoteDocument.ASSETS_DIRECTORY)
            ?.listFiles()
            ?.filter { it.isFile }
            ?.mapNotNull { it.name }
            .orEmpty()
    }

    override suspend fun writeNoteFromCloud(
        document: NoteDocument,
        targetFolder: String,
    ): String? = withContext(io) {
        val store = store() ?: return@withContext null

        // Zwietrzały wiersz spisu - plik zniknął z dysku, a wiersz został -
        // robił przy każdym pobraniu nową kopię notatki, zostawiając siebie
        // na miejscu, i tak w kółko. Sprzątamy, zanim cokolwiek utworzymy.
        var existing = dao.findById(document.id)
        var guard = 0
        while (existing != null && store.entry(existing.path) == null && guard < 10) {
            dao.deleteBranch(existing.path)
            existing = dao.findById(document.id)
            guard += 1
        }
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
        // Gwiazdki na plikach są zapisane po ścieżce, więc idą razem z nią.
        settings.moveFavoriteFiles(path, newPath)
        reindexBranch(newPath)
        refresh()
        onPathMoved?.invoke(path, newPath)
        // Nowa nazwa to też zmiana treści (tytuł siedzi w notatce), więc jedzie
        // na serwer. Wcześniej zmiana nazwy nigdy tam nie docierała.
        reportBranchSaved(newPath)
        // Folder nie ma treści do wysłania - jego nazwa jedzie osobną drogą.
        onFolderChanged?.invoke()
        newPath
    }

    suspend fun move(path: String, targetFolder: String): String = withContext(io) {
        val newPath = requireStore().move(path, targetFolder)
        dao.deleteBranch(path)
        settings.moveFavoriteFiles(path, newPath)
        reindexBranch(newPath)
        refresh()
        onPathMoved?.invoke(path, newPath)
        reportBranchSaved(newPath)
        // Przeniesiony folder zmienia rodzica także na serwerze.
        onFolderChanged?.invoke()
        newPath
    }

    /** Po zmianie ścieżki wszystko z gałęzi zgłasza się do wysyłki od nowa. */
    private suspend fun reportBranchSaved(path: String) {
        val notes = runCatching { dao.notesUnder(path) }.getOrDefault(emptyList())
        for (note in notes) {
            onNoteSaved?.invoke(note.path, note.documentId)
        }
        val codePaths = runCatching { dao.codeFilePathsUnder(path) }.getOrDefault(emptyList())
        for (codePath in codePaths) {
            onCodeSaved?.invoke(codePath)
        }
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
        // Nowa barwa albo ikona ma dojechać na stronę od razu.
        onFolderChanged?.invoke()
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
        // Spis czytamy przed kasowaniem, bo za chwilę gałąź z niego zniknie.
        // Z tych identyfikatorów robią się nagrobki wysyłane na serwer.
        val noteIds = runCatching { dao.notesUnder(path).map { it.documentId } }
            .getOrDefault(emptyList())
        val codePaths = runCatching { dao.codeFilePathsUnder(path) }.getOrDefault(emptyList())
        // Identyfikatory folderów czytamy z folder.json PRZED wyrzuceniem -
        // z kosza już się do nich nie dostaniemy.
        val store = requireStore()
        val folderIds = runCatching {
            dao.folderPathsUnder(path).mapNotNull { folderPath ->
                store.entry(folderPath)?.let { store.readFolderMeta(it)?.id }
            }
        }.getOrDefault(emptyList())
        store.moveToTrash(path)
        dao.deleteBranchWithContent(path)
        refresh()
        if (noteIds.isNotEmpty()) onNotesTrashed?.invoke(noteIds)
        if (codePaths.isNotEmpty()) onCodeTrashed?.invoke(codePaths)
        if (folderIds.isNotEmpty()) onFoldersTrashed?.invoke(folderIds)
    }

    suspend fun listTrash(): List<TrashEntry> = withContext(io) {
        store()?.listTrash() ?: emptyList()
    }

    suspend fun restoreFromTrash(id: String): String = withContext(io) {
        val path = requireStore().restore(id)
        reindexBranch(path)
        refresh()
        // Przywrócone wraca też na serwer - i wychodzi z jego kosza.
        val notes = runCatching { dao.notesUnder(path) }.getOrDefault(emptyList())
        for (note in notes) {
            onNoteSaved?.invoke(note.path, note.documentId)
        }
        val codePaths = runCatching { dao.codeFilePathsUnder(path) }.getOrDefault(emptyList())
        for (codePath in codePaths) {
            onCodeSaved?.invoke(codePath)
        }
        path
    }

    /**
     * [onProgress] mówi, przy którym pliku stoi kasowanie. Wpis kosza bywa
     * całym folderem, a kasowanie idzie notatka po notatce - bez tego ekran
     * stoi w miejscu i wygląda, jakby przycisk nie zadziałał.
     */
    suspend fun deletePermanently(
        id: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ) = withContext(io) {
        eraseTrashSlot(id, alsoOnServer = true, onProgress = onProgress)
        refresh()
    }

    /**
     * Kasuje wpis kosza razem z całą zawartością.
     *
     * Każda notatka i każdy plik z niego idą tą samą drogą co kasowanie
     * zlecone przez serwer - jedna implementacja kasowania, nie dwie różne.
     * Zwraca true, kiedy po wpisie nic nie zostało na dysku.
     */
    private suspend fun eraseTrashSlot(
        id: String,
        alsoOnServer: Boolean,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Boolean {
        val store = requireStore()
        val noteIds = runCatching { store.trashedNoteIds(id) }.getOrDefault(emptyList())
        val codePaths = runCatching { store.trashedCodePaths(id) }.getOrDefault(emptyList())
        val here = TrashContents(
            noteSlots = noteIds.associateWith { id },
            codeSlots = codePaths.associateWith { id },
        )
        val total = noteIds.size + codePaths.size
        var done = 0
        for (noteId in noteIds) {
            deleteNoteCompletely(noteId, alsoOnServer, trash = here)
            done += 1
            onProgress(done, total)
        }
        for (codePath in codePaths) {
            deleteCodeFileCompletely(codePath, alsoOnServer, trash = here)
            done += 1
            onProgress(done, total)
        }

        // Reszta wpisu: opis kosza i to, co nie jest ani notatką, ani plikiem
        // z kodem. Notatki skasowane wyżej mogły już zabrać cały wpis.
        if (store.deletePermanently(id)) return true
        retries.addTrashSlot(id)
        return false
    }

    /** [onProgress] jak w [deletePermanently] - pełny kosz kasuje się długo. */
    suspend fun emptyTrash(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }) = withContext(io) {
        val store = requireStore()
        val here = readTrashContents()
        val total = here.noteSlots.size + here.codeSlots.size
        var done = 0
        for (noteId in here.noteSlots.keys) {
            deleteNoteCompletely(noteId, alsoOnServer = true, trash = here)
            done += 1
            onProgress(done, total)
        }
        for (codePath in here.codeSlots.keys) {
            deleteCodeFileCompletely(codePath, alsoOnServer = true, trash = here)
            done += 1
            onProgress(done, total)
        }
        for (leftover in store.emptyTrash()) retries.addTrashSlot(leftover)
        refresh()
    }

    // Kasowanie doszczętne - jedno wejście dla wszystkich dróg

    /**
     * Kasuje notatkę tak, żeby nic po niej nie zostało.
     *
     * Szuka jej i w bibliotece, i w koszu, po czym usuwa katalog notatki z
     * dysku (a w nim content.json, kopię zapasową i cały `assets`), wiersz w
     * spisie, treść z wyszukiwarki oraz wszystko, co chmura o niej pamiętała.
     * Nie ma znaczenia, czy to notatka tekstowa, odręczna czy mapa myśli -
     * wszystkie trzy trzymają całą treść w tym jednym katalogu.
     *
     * Nieudane kasowanie pliku nie ginie po cichu: trafia do [DeleteRetryQueue]
     * i sprzątanie sięgnie po nie jeszcze raz.
     *
     * [alsoOnServer] mówi, czy zgłosić kasowanie serwerowi. Fałsz przy
     * kasowaniu, o którym to serwer nas powiadomił - nie ma mu czego odsyłać.
     *
     * Odświeżenie widoku robi wołający: takich kasowań potrafi przyjść naraz
     * bardzo dużo i odświeżanie po każdym z osobna tylko szarpałoby ekranem.
     */
    suspend fun deleteNoteCompletely(
        noteId: String,
        alsoOnServer: Boolean = true,
        trash: TrashContents? = null,
    ): Boolean = withContext(io) {
        if (noteId.isBlank()) return@withContext false
        val store = store() ?: return@withContext false
        var erased = false

        // Kopia widoczna w bibliotece.
        val indexed = runCatching { dao.findById(noteId) }.getOrNull()
        if (indexed != null) {
            if (store.entry(indexed.path) != null) {
                if (store.deleteEntry(indexed.path)) erased = true else retries.addPath(indexed.path)
            }
            runCatching { dao.deleteBranchWithContent(indexed.path) }
            // Kasowanie zlecone przez serwer mogło właśnie zabrać notatkę
            // spod otwartego edytora - edytor ma się o tym dowiedzieć.
            if (!alsoOnServer) reportRemotelyRemoved(indexed.path)
        }

        // Kopia w koszu. Kosz jest poza spisem, więc trzeba go przejrzeć osobno
        // - i to jest ta droga, której brakowało: notatka wyrzucona do kosza
        // była dla kasowania z serwera niewidoczna i zostawała tam na zawsze.
        val slot = if (trash != null) {
            trash.noteSlots[noteId]
        } else {
            runCatching { store.trashSlotForNote(noteId) }.getOrNull()
        }
        if (slot != null) {
            if (store.deleteNoteInsideTrash(slot, noteId)) erased = true else retries.addTrashSlot(slot)
        }

        // Kolejność: najpierw zgłoszenie serwerowi, dopiero potem czyszczenie
        // pamięci chmury. Odwrotnie sprzątanie zdejmowałoby świeżo dopisane
        // zgłoszenie kasowania i serwer nigdy by się o nim nie dowiedział.
        if (alsoOnServer) onNotesPurged?.invoke(listOf(noteId))
        onNoteErased?.invoke(noteId)
        erased
    }

    /** Odpowiednik [deleteNoteCompletely] dla pliku z kodem, znanego po ścieżce. */
    suspend fun deleteCodeFileCompletely(
        path: String,
        alsoOnServer: Boolean = true,
        trash: TrashContents? = null,
    ): Boolean = withContext(io) {
        if (path.isBlank()) return@withContext false
        val store = store() ?: return@withContext false
        var erased = false

        if (store.entry(path) != null) {
            if (store.deleteEntry(path)) erased = true else retries.addPath(path)
        }
        runCatching { dao.deleteBranchWithContent(path) }
        // Po pliku nie ma już śladu, więc gwiazdka nie ma czego oznaczać.
        // Przy wyrzuceniu do kosza zostaje - przywrócenie ma ją oddać.
        runCatching { settings.forgetFavoriteFiles(path) }
        // Jak przy notatce: otwarty edytor kodu ma się dowiedzieć od razu.
        if (!alsoOnServer) reportRemotelyRemoved(path)

        val slot = if (trash != null) {
            trash.codeSlots[path]
        } else {
            runCatching { store.trashedCodeSlots().firstOrNull { it.second == path }?.first }
                .getOrNull()
        }
        if (slot != null) {
            if (store.deleteCodeInsideTrash(slot, path)) erased = true else retries.addTrashSlot(slot)
        }

        // Tak samo jak przy notatce: zgłoszenie przed sprzątaniem. Tutaj waży
        // to podwójnie - zgłoszenie potrzebuje powiązania ścieżki z numerem
        // notatki na serwerze, a sprzątanie właśnie to powiązanie kasuje.
        if (alsoOnServer) onCodePurged?.invoke(listOf(path))
        onCodeErased?.invoke(path)
        erased
    }

    // Deletions arriving from the cloud

    /**
     * Notatka skasowana gdzie indziej idzie tu do kosza. Celowo bez zgłaszania
     * przez [onNotesTrashed] - to serwer o tym powiedział, nie ma mu czego
     * odsyłać. Zwraca true, kiedy było co wyrzucić.
     */
    override suspend fun trashNoteFromCloud(noteId: String): Boolean = withContext(io) {
        val entry = dao.findById(noteId) ?: return@withContext false
        val store = store() ?: return@withContext false
        if (store.entry(entry.path) == null) {
            dao.deleteBranchWithContent(entry.path)
            reportRemotelyRemoved(entry.path)
            return@withContext false
        }
        store.moveToTrash(entry.path, fromServer = true)
        dao.deleteBranchWithContent(entry.path)
        refresh()
        reportRemotelyRemoved(entry.path)
        true
    }

    /** Jak wyżej, ale dla pliku z kodem wskazanego ścieżką. */
    override suspend fun trashFileFromCloud(path: String): Boolean = withContext(io) {
        val store = store() ?: return@withContext false
        if (store.entry(path) == null) {
            dao.deleteBranchWithContent(path)
            reportRemotelyRemoved(path)
            return@withContext false
        }
        store.moveToTrash(path, fromServer = true)
        dao.deleteBranchWithContent(path)
        refresh()
        reportRemotelyRemoved(path)
        true
    }

    /**
     * Notatka skasowana na zawsze po stronie serwera.
     *
     * Reguła jest jedna i obowiązuje w obie strony: synchronizacja kasuje z
     * urządzenia wyłącznie to, co już leży w koszu. Notatka widoczna w
     * bibliotece nigdy nie znika sama z dysku - najwyżej trafia do kosza,
     * skąd zawsze da się ją wyjąć. Bez tego cicha pomyłka po stronie serwera
     * albo niepełna odpowiedź kasowałaby pracę bez pytania.
     *
     * Odświeżenie widoku robi wołający - takich zgłoszeń potrafi przyjść
     * naraz bardzo dużo.
     */
    override suspend fun applyServerDeletion(
        noteId: String,
        trash: TrashContents?,
    ): ServerDeletion = withContext(io) {
        if (noteId.isBlank()) return@withContext ServerDeletion.NOTHING
        val store = store() ?: return@withContext ServerDeletion.NOTHING

        val slot = if (trash != null) {
            trash.noteSlots[noteId]
        } else {
            runCatching { store.trashSlotForNote(noteId) }.getOrNull()
        }

        // W koszu - znika doszczętnie. To jest naprawa objawu: dotąd notatka
        // wyrzucona do kosza (także ta, która trafiła tam za serwerem) była
        // dla kasowania niewidoczna i zostawała w koszu na zawsze.
        if (slot != null) {
            deleteNoteCompletely(noteId, alsoOnServer = false, trash = TrashContents(mapOf(noteId to slot)))
            return@withContext ServerDeletion.ERASED
        }

        // Widoczna w bibliotece - idzie do kosza, nie z dysku.
        if (trashNoteFromCloud(noteId)) ServerDeletion.TRASHED else ServerDeletion.NOTHING
    }

    /** Jak wyżej, dla pliku z kodem, którego chmura pilnuje po ścieżce. */
    override suspend fun applyServerCodeDeletion(
        path: String,
        trash: TrashContents?,
    ): ServerDeletion = withContext(io) {
        if (path.isBlank()) return@withContext ServerDeletion.NOTHING
        val store = store() ?: return@withContext ServerDeletion.NOTHING

        val slot = if (trash != null) {
            trash.codeSlots[path]
        } else {
            runCatching { store.trashedCodeSlots().firstOrNull { it.second == path }?.first }
                .getOrNull()
        }

        if (slot != null) {
            deleteCodeFileCompletely(
                path,
                alsoOnServer = false,
                trash = TrashContents(codeSlots = mapOf(path to slot)),
            )
            return@withContext ServerDeletion.ERASED
        }

        if (trashFileFromCloud(path)) ServerDeletion.TRASHED else ServerDeletion.NOTHING
    }

    /**
     * Robi zdjęcie kosza. Raz na przebieg synchronizacji, patrz [TrashContents].
     */
    override suspend fun readTrashContents(): TrashContents = withContext(io) {
        val store = store() ?: return@withContext TrashContents()
        TrashContents(
            noteSlots = runCatching {
                store.trashedNoteSlots().associate { (slot, noteId) -> noteId to slot }
            }.getOrDefault(emptyMap()),
            codeSlots = runCatching {
                store.trashedCodeSlots().associate { (slot, path) -> path to slot }
            }.getOrDefault(emptyMap()),
        )
    }

    /** Ścieżki wszystkich plików z kodem - do uzgadniania biblioteki z chmurą. */
    override suspend fun allCodeFilePaths(): List<String> = withContext(io) {
        dao.allCodeFilePaths()
    }

    // Sprzątanie

    /**
     * Ponawia kasowania, które kiedyś się nie udały (odpięta karta, plik zajęty
     * przez inny program, cofnięte prawo do katalogu). Zwraca, ile załatwiono.
     */
    suspend fun retryPendingDeletions(): Int = withContext(io) {
        if (retries.isEmpty()) return@withContext 0
        val store = store() ?: return@withContext 0
        var done = 0
        for (target in retries.all()) {
            val cleared = when {
                target.startsWith(DeleteRetryQueue.PREFIX_PATH) ->
                    runCatching { store.deleteEntry(target.removePrefix(DeleteRetryQueue.PREFIX_PATH)) }
                        .getOrDefault(false)

                target.startsWith(DeleteRetryQueue.PREFIX_TRASH) ->
                    runCatching { store.deletePermanently(target.removePrefix(DeleteRetryQueue.PREFIX_TRASH)) }
                        .getOrDefault(false)

                // Wpis w nieznanym kształcie (starszy zapis) - nie ma czego
                // ponawiać, a trzymanie go w nieskończoność nic nie daje.
                else -> true
            }
            if (cleared) {
                retries.remove(target)
                done += 1
            }
        }
        if (done > 0) refresh()
        done
    }

    /**
     * Kasuje wpisy kosza, które trafiły tam za serwerem i leżą dłużej niż
     * [days] dni.
     *
     * Notatka skasowana na serwerze, a widoczna tutaj w bibliotece, idzie do
     * kosza zamiast wprost z dysku - po to, żeby dało się ją uratować. Bez
     * terminu zostawałaby w nim na zawsze: serwer nie ma już po niej ani
     * wiersza, ani nagrobka, więc nic o niej więcej nie powie.
     *
     * Czas liczy się od chwili, gdy wpis trafił do kosza NA TYM urządzeniu
     * (patrz [TrashEntry.deletedAt]) - to jest czas na reakcję człowieka, a nie
     * czas od zdarzenia na serwerze.
     *
     * Serwerowi nic nie zgłaszamy: on tę notatkę skasował i to on zaczął całą
     * sprawę. Wpisy wyrzucone ręcznie zostają nietknięte.
     */
    suspend fun sweepExpiredServerTrash(
        days: Int,
        now: Long = System.currentTimeMillis(),
    ): Int = withContext(io) {
        if (days <= 0) return@withContext 0
        if (store() == null) return@withContext 0

        val cutoff = now - days * DAY
        var removed = 0
        for (entry in runCatching { listTrash() }.getOrDefault(emptyList())) {
            if (!entry.fromServer) continue
            // Znacznik z przyszłości (przestawiony zegar) nie jest powodem do
            // kasowania - ten warunek go przepuszcza nietkniętego.
            if (entry.deletedAt > cutoff) continue
            if (runCatching { eraseTrashSlot(entry.id, alsoOnServer = false) }.getOrDefault(false)) {
                removed += 1
            }
        }
        if (removed > 0) refresh()
        removed
    }

    /**
     * Sprząta to, co zostało po kasowaniu i do niczego już nie służy:
     *
     * - wiersze spisu wskazujące pliki, których na dysku nie ma,
     * - wpisy kosza bez opisu albo bez pliku, czyli nie do przywrócenia
     *   i niewidoczne w koszu.
     *
     * Świadomie NIE kasuje plików z biblioteki dlatego, że nie ma ich w spisie.
     * Spis jest podręczny i przy zmianie wersji bazy zaczyna od zera
     * (`fallbackToDestructiveMigration`), więc taka reguła skasowałaby przy
     * pierwszym uruchomieniu po aktualizacji całą bibliotekę. Pliki są tu
     * ważniejsze od spisu - to spis nadąża za nimi, nie odwrotnie.
     *
     * Zwraca liczbę posprzątanych rzeczy.
     */
    suspend fun sweepOrphans(): Int = withContext(io) {
        val store = store() ?: return@withContext 0
        var cleaned = 0

        // Sprawdzenie wiersza to zejście po katalogach przez SAF, a ono przy
        // dużej bibliotece nie jest darmowe. Bierzemy więc porcję na przebieg;
        // reszta doczeka jutra. Ile zostało - widać w dzienniku, żeby nie
        // wyglądało to na „przejrzano wszystko".
        val entries = runCatching { dao.allEntries() }.getOrDefault(emptyList())
        val portion = entries.take(MAX_ORPHAN_CHECKS)
        for (entry in portion) {
            if (runCatching { store.entry(entry.path) }.getOrNull() != null) continue
            runCatching { dao.deleteBranchWithContent(entry.path) }.onSuccess { cleaned += 1 }
        }
        if (entries.size > portion.size) {
            Log.i(
                "Kajet",
                "Sprzątanie spisu: sprawdzono ${portion.size} z ${entries.size} wpisów, " +
                    "reszta przy następnym przebiegu",
            )
        }

        for (slotId in runCatching { store.brokenTrashSlots() }.getOrDefault(emptyList())) {
            if (runCatching { store.deletePermanently(slotId) }.getOrDefault(false)) {
                cleaned += 1
            } else {
                retries.addTrashSlot(slotId)
            }
        }

        if (cleaned > 0) refresh()
        cleaned
    }

    private companion object {
        /** Ile wierszy spisu sprawdzamy w jednym przebiegu sprzątania. */
        const val MAX_ORPHAN_CHECKS = 500

        const val DAY = 24 * 60 * 60 * 1000L
    }

    // Folders and the cloud

    /** Folder z biblioteki w kształcie, którym rozmawia się z serwerem. */
    data class CloudFolder(
        val id: String,
        val path: String,
        val name: String,
        val colorId: String,
        val iconId: String,
        val modifiedAt: Long,
    ) {
        val parentPath: String get() = path.substringBeforeLast('/', "")
    }

    /**
     * Wszystkie foldery biblioteki razem z ich identyfikatorami z folder.json.
     * Folder bez pliku metadanych (założony poza aplikacją) jest pomijany -
     * dostanie tożsamość, gdy użytkownik pierwszy raz go tknie w aplikacji.
     */
    override suspend fun allCloudFolders(): List<CloudFolder> = withContext(io) {
        val store = store() ?: return@withContext emptyList()
        dao.allFolderPaths().mapNotNull { path ->
            val entry = store.entry(path) ?: return@mapNotNull null
            val meta = store.readFolderMeta(entry) ?: return@mapNotNull null
            CloudFolder(
                id = meta.id,
                path = path,
                name = meta.displayName.ifBlank { path.substringAfterLast('/') },
                colorId = meta.colorId,
                iconId = meta.iconId,
                modifiedAt = meta.modifiedAt,
            )
        }
    }

    /** Nowy folder przysłany z chmury - z jej identyfikatorem, bez odsyłania. */
    override suspend fun createFolderFromCloud(
        parent: String,
        name: String,
        colorId: String,
        iconId: String,
        id: String,
    ): String = withContext(io) {
        val item = requireStore().createFolder(parent, name, colorId, iconId, id)
        dao.upsertKeepingOpened(item.toIndexEntry())
        refresh()
        item.path
    }

    /** Zmiana nazwy folderu przysłana z chmury - bez odsyłania jej z powrotem. */
    override suspend fun renameFolderFromCloud(path: String, newName: String): String = withContext(io) {
        val newPath = requireStore().rename(path, newName)
        dao.deleteBranch(path)
        reindexBranch(newPath)
        refresh()
        onPathMoved?.invoke(path, newPath)
        newPath
    }

    /** Przeniesienie folderu przysłane z chmury - bez odsyłania. */
    override suspend fun moveFolderFromCloud(path: String, targetFolder: String): String = withContext(io) {
        val newPath = requireStore().move(path, targetFolder)
        dao.deleteBranch(path)
        reindexBranch(newPath)
        refresh()
        onPathMoved?.invoke(path, newPath)
        newPath
    }

    /** Wygląd folderu przysłany z chmury. */
    override suspend fun updateFolderLookFromCloud(path: String, colorId: String, iconId: String) {
        updateFolderLook(path, colorId, iconId)
    }

    /**
     * Notatka zmieniła folder na innym urządzeniu - tu przenosi się tak samo.
     * Zwraca nową ścieżkę albo null, kiedy nie było czego przenosić.
     */
    override suspend fun moveNoteFromCloud(noteId: String, targetFolder: String): String? = withContext(io) {
        val entry = dao.findById(noteId) ?: return@withContext null
        val currentParent = entry.path.substringBeforeLast('/', "")
        if (currentParent == targetFolder) return@withContext entry.path
        val store = store() ?: return@withContext null
        if (store.entry(entry.path) == null) return@withContext null
        val newPath = store.move(entry.path, targetFolder)
        dao.deleteBranch(entry.path)
        reindexBranch(newPath)
        refresh()
        onPathMoved?.invoke(entry.path, newPath)
        newPath
    }

    // The index

    override suspend fun rebuildIfEmpty(progress: ((done: Int, total: Int) -> Unit)?) =
        withContext(io) {
            if (dao.count() > 0) return@withContext
            val store = store() ?: return@withContext
            if (store.list("").isEmpty()) return@withContext
            rebuildIndex(progress)
        }

    // requireStore, not store(): this one is asked for by hand, from a button in
    // settings. A silent return there looks exactly like a dead button - the
    // person taps it and nothing happens, not even a word about the folder
    // being gone. The automatic rebuild above stays silent on purpose.
    suspend fun rebuildIndex(progress: ((done: Int, total: Int) -> Unit)? = null) = withContext(io) {
        val store = requireStore()
        dao.clear()

        // Gwiazdki na plikach przeżywają odbudowę, bo leżą w ustawieniach,
        // a nie w spisie. Wracają do świeżych wierszy tutaj.
        val starred = starredFiles()

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
                dao.upsertKeepingOpened(
                    item.toIndexEntry().copy(favorite = item.path in starred),
                )
            }
            progress?.invoke(number + 1, items.size)
        }
        refresh()
    }

    private suspend fun reindexBranch(path: String, starred: Set<String>? = null) {
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
        // Zbiór czytamy raz na całą gałąź, a nie raz na plik w środku.
        val starredFiles = starred ?: starredFiles()
        dao.upsertKeepingOpened(
            onDisk.toIndexEntry().copy(favorite = onDisk.path in starredFiles),
        )
        if (onDisk.type == ItemType.FOLDER) {
            for (child in store.list(path)) {
                reindexBranch(child.path, starredFiles)
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
