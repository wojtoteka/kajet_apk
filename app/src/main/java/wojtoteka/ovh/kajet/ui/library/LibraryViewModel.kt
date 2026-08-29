package wojtoteka.ovh.kajet.ui.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import wojtoteka.ovh.kajet.core.awaria.failureHandler
import wojtoteka.ovh.kajet.core.model.CodeLanguage
import wojtoteka.ovh.kajet.core.model.ItemType
import wojtoteka.ovh.kajet.core.model.LibraryItem
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode
import wojtoteka.ovh.kajet.core.text.bulkPartlyFailed
import wojtoteka.ovh.kajet.core.text.checkingProgress
import wojtoteka.ovh.kajet.core.text.deletingOne
import wojtoteka.ovh.kajet.core.text.deletingProgress
import wojtoteka.ovh.kajet.core.text.movingProgress
import wojtoteka.ovh.kajet.core.text.refreshingIndex
import wojtoteka.ovh.kajet.core.text.trashingProgress
import wojtoteka.ovh.kajet.core.text.uploadNotTextAbout
import wojtoteka.ovh.kajet.core.text.savingFolder
import wojtoteka.ovh.kajet.core.text.savingNote
import wojtoteka.ovh.kajet.core.text.words
import wojtoteka.ovh.kajet.core.text.Strings
import wojtoteka.ovh.kajet.export.ExportFormat
import wojtoteka.ovh.kajet.export.ExportService
import wojtoteka.ovh.kajet.share.IncomingShare
import wojtoteka.ovh.kajet.share.ShareImport
import wojtoteka.ovh.kajet.storage.LibraryRepository
import wojtoteka.ovh.kajet.storage.TrashEntry
import wojtoteka.ovh.kajet.cloud.FileUploader
import wojtoteka.ovh.kajet.storage.index.FileUploadEntry

enum class LibrarySection {
    LIBRARY,
    FAVORITES,
    RECENT,
    SEARCH,
    TRASH,
    ;

    fun label(words: Strings): String = when (this) {
        LIBRARY -> words.sectionLibrary
        FAVORITES -> words.sectionFavorites
        RECENT -> words.sectionRecent
        SEARCH -> words.sectionSearch
        TRASH -> words.sectionTrash
    }
}

data class TreeNode(
    val item: LibraryItem,
    val level: Int,
    val expanded: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
/*
  [saved] przeżywa śmierć procesu.

  System potrafi ubić Kajet zwinięty do tła - pamięć na telefonie jest
  potrzebna komuś innemu. Po powrocie ekran budował się od zera: biblioteka
  wracała do korzenia, otwarte foldery się zwijały, a wpisane szukanie znikało.
  Wyglądało to na zgubioną robotę, choć na dysku nic nie ubyło. Te cztery
  rzeczy jadą teraz w zapisanym stanie i wracają takie, jakie były.
*/
class LibraryViewModel(
    private val repo: LibraryRepository,
    private val export: ExportService,
    private val saved: SavedStateHandle = SavedStateHandle(),
    // Furtką, a nie gotowym obiektem: budowa chmury to magazyn kluczy i I/O
    // na dysku, a biblioteka rysuje się jako pierwsza. Sięgamy po nią dopiero
    // w korutynie poniżej, czyli poza wątkiem rysowania.
    private val stuck: () -> StuckNotes? = { null },
    private val uploader: () -> FileUploader? = { null },
) : ViewModel() {

    val uploads: Flow<List<FileUploadEntry>> =
        uploader()?.uploads ?: flowOf(emptyList<FileUploadEntry>())

    private val _path = MutableStateFlow(saved.get<String>(KEY_PATH).orEmpty())
    val path: StateFlow<String> = _path.asStateFlow()

    private val _section = MutableStateFlow(
        runCatching { LibrarySection.valueOf(saved.get<String>(KEY_SECTION).orEmpty()) }
            .getOrDefault(LibrarySection.LIBRARY),
    )
    val section: StateFlow<LibrarySection> = _section.asStateFlow()

    private val _expanded = MutableStateFlow(
        saved.get<ArrayList<String>>(KEY_EXPANDED)?.toSet() ?: setOf(""),
    )
    private val _tree = MutableStateFlow<List<TreeNode>>(emptyList())
    val tree: StateFlow<List<TreeNode>> = _tree.asStateFlow()

    private val _trash = MutableStateFlow<List<TrashEntry>>(emptyList())
    val trash: StateFlow<List<TrashEntry>> = _trash.asStateFlow()

    private val _query = MutableStateFlow(saved.get<String>(KEY_QUERY).orEmpty())
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searchResults = MutableStateFlow<List<LibraryItem>>(emptyList())
    val searchResults: StateFlow<List<LibraryItem>> = _searchResults.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _progress = MutableStateFlow<String?>(null)
    val progress: StateFlow<String?> = _progress.asStateFlow()

    // Każdy z tych spisów ma własne .catch. Bez niego jeden nieczytelny plik
    // w katalogu przewraca korutynę, a człowiek widzi czarny ekran zamiast
    // biblioteki i nie wie, co się stało.
    val content: StateFlow<List<LibraryItem>> = _path
        .flatMapLatest { repo.folder(it) }
        .catch { failure -> reportAndEmpty(failure, words.readingFolderFailed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites: StateFlow<List<LibraryItem>> = repo.favorites()
        .catch { failure -> reportAndEmpty(failure, words.readingFavoritesFailed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recent: StateFlow<List<LibraryItem>> = repo.recent()
        .catch { failure -> reportAndEmpty(failure, words.readingRecentFailed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private suspend fun FlowCollector<List<LibraryItem>>.reportAndEmpty(
        failure: Throwable,
        fallback: String,
    ) {
        _error.value = failure.message?.takeIf { it.isNotBlank() } ?: fallback
        emit(emptyList())
    }

    /*
      Notatki, których nie udało się wysłać.

      Licznik z synchronizacji mówi tylko, KIEDY coś się zmieniło; ścieżki
      idą z kolejki, bo tylko ona wie, KTÓRE to notatki. Odczyt kolejki to
      wejście na dysk, więc chodzi poza wątkiem rysowania.
    */
    val stuckPaths: StateFlow<Set<String>> = flow {
        val source = stuck()
        if (source == null) {
            emit(emptySet())
            return@flow
        }
        emitAll(source.count.map { source.paths() })
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    // Zamknięty pasek zbiorczy zostaje zamknięty, dopóki utknięte notatki są
    // te same. Kiedy dojdzie następna, pasek wraca - inaczej jedno zamknięcie
    // zagłuszałoby wszystko, co utknie później.
    private val _stuckNoticeHidden = MutableStateFlow<Set<String>>(emptySet())

    val stuckNotice: StateFlow<Set<String>> = combine(
        stuckPaths,
        _stuckNoticeHidden,
    ) { paths, hidden ->
        if (paths.isNotEmpty() && paths == hidden) emptySet() else paths
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun hideStuckNotice() {
        _stuckNoticeHidden.value = stuckPaths.value
    }

    fun retryStuck() {
        viewModelScope.launch(Dispatchers.IO + failureHandler("ponowienie wysyłki")) {
            stuck()?.retry()
        }
    }

    init {
        // Drzewo folderów po lewej. Bez handlera jeden nieczytelny folder
        // przewracał tę korutynę, a wyjątek szedł dalej i zabierał ze sobą całą
        // aplikację - zamiast po prostu zostawić drzewo takim, jakie było.
        viewModelScope.launch(failureHandler("drzewo folderów") { _error.value = it.message }) {
            combine(_expanded, content) { expanded, _ -> expanded }.collect { expanded ->
                _tree.value = buildTree(expanded)
            }
        }

        // Po aktualizacji, która zmieniła budowę spisu, jest on pusty.
        // Odbudowujemy go sami, żeby favorites i ostatnio otwarte nie zniknęły
        // człowiekowi z oczu bez żadnego wyjaśnienia.
        viewModelScope.launch(failureHandler("odbudowa spisu")) {
            runCatching {
                repo.rebuildIfEmpty { done, total ->
                    _progress.value = words.refreshingIndex(done, total)
                }
            }
            _progress.value = null
        }
    }

    private suspend fun buildTree(expanded: Set<String>): List<TreeNode> {
        val result = mutableListOf<TreeNode>()
        suspend fun descend(path: String, level: Int) {
            val folders = runCatching { repo.foldersIn(path) }.getOrDefault(emptyList())
            for (folder in folders) {
                val expanded = folder.path in expanded
                result += TreeNode(folder, level, expanded)
                if (expanded) descend(folder.path, level + 1)
            }
        }
        descend("", 0)
        return result
    }

    // Zmiana miejsca kończy zaznaczanie. Zaznaczone ścieżki zostają w innym
    // folderze, więc pasek z licznikiem mówiłby o wpisach, których na ekranie
    // już nie widać - a to jest gotowy przepis na skasowanie nie tego.

    fun goTo(path: String) {
        stopSelecting()
        setSectionValue(LibrarySection.LIBRARY)
        setPathValue(path)
        setExpandedValue(_expanded.value + parentPaths(path))
    }

    fun goUp() {
        val current = _path.value
        if (current.isEmpty()) return
        stopSelecting()
        setPathValue(current.substringBeforeLast('/', ""))
    }

    fun toggleExpanded(path: String) {
        setExpandedValue(
            if (path in _expanded.value) _expanded.value - path else _expanded.value + path,
        )
    }

    fun setSection(updated: LibrarySection) {
        stopSelecting()
        setSectionValue(updated)
        if (updated == LibrarySection.TRASH) refreshTrash()
    }

    fun setQuery(text: String) {
        _query.value = text
        saved[KEY_QUERY] = text
        viewModelScope.launch(failureHandler("szukanie w notatkach")) {
            _searchResults.value = runCatching { repo.search(text) }.getOrDefault(emptyList())
        }
    }

    // Każda zmiana idzie równolegle do stanu na ekranie i do stanu zapisanego,
    // żeby po śmierci procesu było co przywrócić.

    private fun setPathValue(value: String) {
        _path.value = value
        saved[KEY_PATH] = value
    }

    private fun setSectionValue(value: LibrarySection) {
        _section.value = value
        saved[KEY_SECTION] = value.name
    }

    private fun setExpandedValue(value: Set<String>) {
        _expanded.value = value
        saved[KEY_EXPANDED] = ArrayList(value)
    }

    /*
      Zaznaczanie wielu wpisów naraz.

      Tryb stoi osobno od zbioru zaznaczonych ścieżek, bo „włączony, a jeszcze
      nic nie wskazano" to zwyczajny stan: tak wygląda ekran zaraz po
      naciśnięciu „Zaznacz". Ścieżkami, a nie wpisami, bo tylko one przeżywają
      odświeżenie spisu.
    */
    private val _selecting = MutableStateFlow(false)
    val selecting: StateFlow<Boolean> = _selecting.asStateFlow()

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    /** Wejście w tryb; [item] to wpis wskazany długim przytrzymaniem. */
    fun startSelecting(item: LibraryItem? = null) {
        _selecting.value = true
        if (item != null) _selected.value = setOf(item.path)
    }

    fun stopSelecting() {
        _selecting.value = false
        _selected.value = emptySet()
    }

    fun toggleSelected(item: LibraryItem) {
        val paths = _selected.value
        val next = if (item.path in paths) paths - item.path else paths + item.path
        _selected.value = next
        // Ostatni odznaczony wpis kończy tryb: pasek działań ma zniknąć
        // razem z pustym zaznaczeniem, a nie zostać z licznikiem zero.
        if (next.isEmpty()) stopSelecting()
    }

    fun selectAll(items: List<LibraryItem>) {
        _selected.value = items.mapTo(mutableSetOf()) { it.path }
    }

    /**
     * Wyrzuca zaznaczone do kosza, jeden po drugim.
     *
     * Nieudany wpis nie zatrzymuje reszty - przy dwudziestu notatkach jeden
     * plik zajęty przez inny program nie ma prawa unieważnić całej roboty.
     * Ile ich było, mówi zdanie na końcu.
     */
    fun trashSelected() = inBackground {
        val targets = topmost(_selected.value)
        stopSelecting()
        var failed = 0
        targets.forEachIndexed { number, path ->
            _progress.value = words.trashingProgress(number + 1, targets.size)
            if (runCatching { repo.moveToTrash(path) }.isFailure) failed += 1
        }
        _progress.value = null
        if (failed > 0) _error.value = words.bulkPartlyFailed(failed)
    }

    fun moveSelected(targetFolder: String) = inBackground {
        val targets = topmost(_selected.value)
        stopSelecting()
        var failed = 0
        targets.forEachIndexed { number, path ->
            _progress.value = words.movingProgress(number + 1, targets.size)
            // Wpis już leżący na miejscu oraz folder przenoszony do siebie
            // albo do własnego wnętrza: nie ma co robić, a nie jest to błąd.
            val alreadyThere = path.substringBeforeLast('/', "") == targetFolder
            val intoItself = targetFolder == path || targetFolder.startsWith("$path/")
            if (alreadyThere || intoItself) return@forEachIndexed
            if (runCatching { repo.move(path, targetFolder) }.isFailure) failed += 1
        }
        _progress.value = null
        if (failed > 0) _error.value = words.bulkPartlyFailed(failed)
    }

    /**
     * Zostawia same wierzchołki zaznaczenia.
     *
     * Notatka zaznaczona razem z folderem, w którym leży, zniknie i tak razem
     * z nim. Ruszanie jej osobno oznaczałoby robotę pod ścieżką, której już
     * nie ma - czyli błąd tam, gdzie wszystko poszło dobrze.
     */
    private fun topmost(paths: Set<String>): List<String> = paths
        .filterNot { path -> paths.any { it != path && path.startsWith("$it/") } }
        .sorted()

    fun refreshAfterChange() {
        repo.refresh()
    }

    fun dismissError() {
        _error.value = null
    }

    // Zmiany

    fun newFolder(name: String, colorId: String, iconId: String) = inBackground {
        repo.createFolder(_path.value, name, colorId, iconId)
        _expanded.value = _expanded.value + _path.value
    }

    fun newNote(
        title: String,
        kind: NoteKind,
        mode: PageMode,
        background: PageBackground,
        onCreated: (LibraryItem) -> Unit = {},
    ) = inBackground {
        val item = repo.createNote(_path.value, title, kind, mode, background)
        onCreated(item)
    }

    fun newCodeFile(name: String, language: CodeLanguage, onCreated: (LibraryItem) -> Unit = {}) = inBackground {
        val item = repo.createCodeFile(_path.value, name, language)
        onCreated(item)
    }

    fun queueUpload(uri: Uri, name: String, mime: String, size: Long?) = inBackground {
        val service = uploader() ?: throw IllegalStateException(words.notSignedIn)
        service.enqueue(uri, name, mime, size, _path.value)
    }

    /**
     * Plik nie do wgrania. Komunikat idzie zwykłym paskiem błędu, więc znika
     * razem z „Zrozumiałem” i nie zostaje po nim wpis w kolejce wysyłania.
     */
    fun refuseUpload(name: String) {
        _error.value = words.uploadNotTextAbout(name)
    }

    fun retryUpload(id: String) = inBackground {
        uploader()?.retry(id)
    }

    /*
      Zamknięcie paska idzie do bazy, nie do pamięci widoku. Zapamiętane
      w modelu ginie razem z procesem, więc napis wracał po każdym powrocie
      do aplikacji i trzeba było zamykać go od nowa.
    */
    fun hideUpload(id: String) = inBackground {
        uploader()?.hide(id)
    }

    fun importIncoming(
        share: IncomingShare,
        context: Context,
        onImported: (LibraryItem) -> Unit,
        onFailed: () -> Unit = {},
    ) = inBackground {
        try {
            val outcome = ShareImport.intoLibrary(context, repo, share, _path.value)
            if (outcome.failed > 0) _error.value = words.bulkPartlyFailed(outcome.failed)
            repo.rememberOpened(outcome.item)
            onImported(outcome.item)
        } catch (e: Exception) {
            _error.value = e.message ?: words.couldNotImportShare
            onFailed()
        }
    }

    fun rename(item: LibraryItem, newName: String) = inBackground {
        repo.rename(item.path, newName)
    }

    fun move(item: LibraryItem, targetFolder: String) = inBackground {
        repo.move(item.path, targetFolder)
    }

    fun copy(item: LibraryItem) = inBackground {
        repo.copy(item.path, item.parentPath)
    }

    fun moveToTrash(item: LibraryItem) = inBackground {
        repo.moveToTrash(item.path)
    }

    fun updateFolderLook(item: LibraryItem, colorId: String, iconId: String) = inBackground {
        repo.updateFolderLook(item.path, colorId, iconId)
    }

    fun rememberOpened(item: LibraryItem) = inBackground {
        repo.rememberOpened(item)
    }

    fun toggleFavorite(item: LibraryItem) = inBackground {
        // Folder gwiazdki nie nosi; notatka i plik z kodem tak samo.
        if (item.type == ItemType.FOLDER) return@inBackground
        repo.toggleFavorite(item.path)
    }

    // Kosz

    fun refreshTrash() = inBackground {
        _trash.value = repo.listTrash()
    }

    fun restore(item: TrashEntry) = inBackground {
        repo.restoreFromTrash(item.id)
        _trash.value = repo.listTrash()
    }

    /*
      Kasowanie z kosza mówi, co się dzieje - tak samo jak wyrzucanie do kosza
      w bibliotece. Wpis kosza bywa całym folderem, a pełny kosz to i kilkaset
      plików: bez paska ekran stał nieruchomo, a po chwili wszystko znikało
      naraz i wyglądało to jak zacięcie.
    */
    fun deletePermanently(item: TrashEntry) = inBackground {
        _progress.value = words.deletingOne(item.displayName)
        repo.deletePermanently(item.id) { done, total ->
            if (total > 1) _progress.value = words.deletingProgress(done, total)
        }
        _trash.value = repo.listTrash()
        _progress.value = null
    }

    fun emptyTrash() = inBackground {
        _progress.value = words.emptyingTrash
        repo.emptyTrash { done, total ->
            _progress.value = words.deletingProgress(done, total)
        }
        _trash.value = repo.listTrash()
        _progress.value = null
    }

    fun exportFolder(
        activityContext: android.content.Context,
        item: LibraryItem,
        format: ExportFormat,
    ) = inBackground {
        _progress.value = words.savingFolder(item.name)
        val file = export.exportFolder(item.path, format) { done, total ->
            _progress.value = words.savingNote(done, total)
        }
        _progress.value = null
        export.share(activityContext, file, "application/zip", item.name)?.let { _error.value = it }
    }

    // Indeks

    /*
      Czy odbudowa właśnie się udała.

      Postęp znika w tej samej chwili, w której robota się kończy, a przy małej
      bibliotece trwa ona ułamek sekundy. Bez osobnego znaku ekran ustawień nie
      miał czego pokazać: człowiek naciskał przycisk i nie dostawał żadnej
      odpowiedzi, ani że trwa, ani że gotowe.
    */
    private val _indexRebuilt = MutableStateFlow(false)
    val indexRebuilt: StateFlow<Boolean> = _indexRebuilt.asStateFlow()

    fun dismissIndexRebuilt() {
        _indexRebuilt.value = false
    }

    fun rebuildIndex() = inBackground {
        _indexRebuilt.value = false
        // Całe zdanie, nie sam ogon: pasek postępu pokazuje to, co dostanie,
        // a chodzi nim także zapis folderu i robota na wielu wpisach naraz.
        _progress.value = "${words.libRebuilding} ${words.walkingLibrary}"
        repo.rebuildIndex { done, total ->
            _progress.value = "${words.libRebuilding} ${words.checkingProgress(done, total)}"
        }
        _progress.value = null
        _indexRebuilt.value = true
    }

    private fun inBackground(block: suspend () -> Unit) {
        // `try` łapie Exception, handler dokłada resztę - z brakiem pamięci
        // przy eksporcie wielkiego folderu włącznie.
        viewModelScope.launch(
            failureHandler("działanie w bibliotece") { failure ->
                _error.value = failure.message ?: words.actionFailed
                _progress.value = null
            },
        ) {
            try {
                block()
            } catch (e: Exception) {
                _error.value = e.message ?: words.actionFailed
                _progress.value = null
            }
        }
    }

    private fun parentPaths(path: String): Set<String> {
        if (path.isEmpty()) return setOf("")
        val parts = path.split('/')
        val result = mutableSetOf("")
        var current = ""
        for (part in parts) {
            current = if (current.isEmpty()) part else "$current/$part"
            result += current
        }
        return result
    }

    class Factory(
        private val repo: LibraryRepository,
        private val export: ExportService,
        private val stuck: () -> StuckNotes? = { null },
        private val uploader: () -> FileUploader? = { null },
    ) : ViewModelProvider.Factory {
        // Wariant z CreationExtras, bo tylko stamtąd da się wziąć
        // SavedStateHandle - czyli stan, który przeżywa śmierć procesu.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            LibraryViewModel(repo, export, extras.createSavedStateHandle(), stuck, uploader) as T
    }

    private companion object {
        const val KEY_PATH = "sciezka"
        const val KEY_SECTION = "dzial"
        const val KEY_EXPANDED = "rozwiniete"
        const val KEY_QUERY = "szukane"
    }
}

private suspend fun LibraryRepository.foldersIn(path: String): List<LibraryItem> =
    store()?.list(path)?.filter { it.type == ItemType.FOLDER } ?: emptyList()
