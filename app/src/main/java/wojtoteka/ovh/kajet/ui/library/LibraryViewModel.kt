package wojtoteka.ovh.kajet.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import wojtoteka.ovh.kajet.core.model.CodeLanguage
import wojtoteka.ovh.kajet.core.model.ItemType
import wojtoteka.ovh.kajet.core.model.LibraryItem
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode
import wojtoteka.ovh.kajet.export.ExportFormat
import wojtoteka.ovh.kajet.export.ExportService
import wojtoteka.ovh.kajet.storage.LibraryRepository
import wojtoteka.ovh.kajet.storage.TrashEntry

enum class LibrarySection {
    LIBRARY,
    FAVORITES,
    RECENT,
    SEARCH,
    TRASH,
    ;

    val labelPl: String
        get() = when (this) {
            LIBRARY -> "Biblioteka"
            FAVORITES -> "Ulubione"
            RECENT -> "Ostatnio otwarte"
            SEARCH -> "Szukaj"
            TRASH -> "Kosz"
        }
}

data class TreeNode(
    val item: LibraryItem,
    val level: Int,
    val expanded: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val repo: LibraryRepository,
    private val export: ExportService,
) : ViewModel() {

    private val _path = MutableStateFlow("")
    val path: StateFlow<String> = _path.asStateFlow()

    private val _section = MutableStateFlow(LibrarySection.LIBRARY)
    val section: StateFlow<LibrarySection> = _section.asStateFlow()

    private val _expanded = MutableStateFlow(setOf(""))
    private val _tree = MutableStateFlow<List<TreeNode>>(emptyList())
    val tree: StateFlow<List<TreeNode>> = _tree.asStateFlow()

    private val _trash = MutableStateFlow<List<TrashEntry>>(emptyList())
    val trash: StateFlow<List<TrashEntry>> = _trash.asStateFlow()

    private val _query = MutableStateFlow("")
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
        .catch { failure -> reportAndEmpty(failure, "Nie udało się odczytać folderu.") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites: StateFlow<List<LibraryItem>> = repo.favorites()
        .catch { failure -> reportAndEmpty(failure, "Nie udało się odczytać ulubionych.") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recent: StateFlow<List<LibraryItem>> = repo.recent()
        .catch { failure -> reportAndEmpty(failure, "Nie udało się odczytać ostatnio otwartych.") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private suspend fun FlowCollector<List<LibraryItem>>.reportAndEmpty(
        failure: Throwable,
        fallback: String,
    ) {
        _error.value = failure.message?.takeIf { it.isNotBlank() } ?: fallback
        emit(emptyList())
    }

    init {
        viewModelScope.launch {
            combine(_expanded, content) { expanded, _ -> expanded }.collect { expanded ->
                _tree.value = buildTree(expanded)
            }
        }

        // Po aktualizacji, która zmieniła budowę spisu, jest on pusty.
        // Odbudowujemy go sami, żeby favorites i ostatnio otwarte nie zniknęły
        // człowiekowi z oczu bez żadnego wyjaśnienia.
        viewModelScope.launch {
            runCatching {
                repo.rebuildIfEmpty { done, total ->
                    _progress.value = "Odświeżam spis notatek, $done z $total"
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

    fun goTo(path: String) {
        _section.value = LibrarySection.LIBRARY
        _path.value = path
        _expanded.value = _expanded.value + parentPaths(path)
    }

    fun goUp() {
        val current = _path.value
        if (current.isEmpty()) return
        _path.value = current.substringBeforeLast('/', "")
    }

    fun toggleExpanded(path: String) {
        _expanded.value = if (path in _expanded.value) {
            _expanded.value - path
        } else {
            _expanded.value + path
        }
    }

    fun setSection(updated: LibrarySection) {
        _section.value = updated
        if (updated == LibrarySection.TRASH) refreshTrash()
    }

    fun setQuery(text: String) {
        _query.value = text
        viewModelScope.launch {
            _searchResults.value = runCatching { repo.search(text) }.getOrDefault(emptyList())
        }
    }

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
        if (item.type != ItemType.NOTE) return@inBackground
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

    fun deletePermanently(item: TrashEntry) = inBackground {
        repo.deletePermanently(item.id)
        _trash.value = repo.listTrash()
    }

    fun emptyTrash() = inBackground {
        repo.emptyTrash()
        _trash.value = repo.listTrash()
    }

    fun exportFolder(item: LibraryItem, format: ExportFormat) = inBackground {
        _progress.value = "Zapisuję folder ${item.name}"
        val file = export.exportFolder(item.path, format) { done, total ->
            _progress.value = "Zapisuję notatkę $done z $total"
        }
        _progress.value = null
        export.share(file, "application/zip", item.name)
    }

    // Indeks

    fun rebuildIndex() = inBackground {
        _progress.value = "Przeglądam bibliotekę..."
        repo.rebuildIndex { done, total ->
            _progress.value = "Sprawdzam $done z $total"
        }
        _progress.value = null
    }

    private fun inBackground(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _error.value = e.message ?: "Nie udało się wykonać tej czynności."
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LibraryViewModel(repo, export) as T
    }
}

private suspend fun LibraryRepository.foldersIn(path: String): List<LibraryItem> =
    store()?.list(path)?.filter { it.type == ItemType.FOLDER } ?: emptyList()
