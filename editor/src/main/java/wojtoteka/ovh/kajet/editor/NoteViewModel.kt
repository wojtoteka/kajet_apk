package wojtoteka.ovh.kajet.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.storage.SettingsStore
import wojtoteka.ovh.kajet.storage.LibraryRepository

enum class SaveState {
    LOADING,
    SAVED,
    CHANGED,
    SAVING,
    ERROR,
}

open class NoteViewModel(
    private val repo: LibraryRepository,
    protected val settings: SettingsStore,
    val path: String,
) : ViewModel() {

    protected val _document = MutableStateFlow<NoteDocument?>(null)
    val document: StateFlow<NoteDocument?> = _document.asStateFlow()

    private val _saveState = MutableStateFlow(SaveState.LOADING)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _lastSave = MutableStateFlow<Long?>(null)
    val lastSave: StateFlow<Long?> = _lastSave.asStateFlow()

    val recentColors: StateFlow<List<Int>> = MutableStateFlow<List<Int>>(emptyList()).also { state ->
        viewModelScope.launch {
            settings.settings.collect { state.value = it.recentColors }
        }
    }.asStateFlow()

    fun rememberColor(argb: Int) {
        viewModelScope.launch { runCatching { settings.rememberColor(argb) } }
    }

    val history = ChangeHistory()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var saveJob: Job? = null
    private var autosaveInterval = 5

    init {
        viewModelScope.launch {
            autosaveInterval = runCatching { settings.settings.first().autosaveInterval }.getOrDefault(5)
            load()
        }
    }

    private suspend fun load() {
        try {
            _document.value = repo.readNote(path)
            _saveState.value = SaveState.SAVED
        } catch (e: Exception) {
            _error.value = e.message ?: "Nie udało się otworzyć notatki."
            _saveState.value = SaveState.ERROR
        }
    }

    fun perform(change: Change) {
        val current = _document.value ?: return
        _document.value = change.applyTo(current)
        history.record(change)
        refreshButtons()
        markChanged()
    }

    fun editWithoutHistory(transform: (NoteDocument) -> NoteDocument) {
        val current = _document.value ?: return
        _document.value = transform(current)
        markChanged()
    }

    fun undo() {
        val current = _document.value ?: return
        val after = history.undo(current) ?: return
        _document.value = after
        refreshButtons()
        markChanged()
    }

    fun redo() {
        val current = _document.value ?: return
        val after = history.redo(current) ?: return
        _document.value = after
        refreshButtons()
        markChanged()
    }

    fun toggleFavorite() {
        editWithoutHistory { it.copy(favorite = !it.favorite) }
    }

    fun setTitle(title: String) {
        editWithoutHistory { it.copy(title = title) }
    }

    protected fun refreshButtons() {
        _canUndo.value = history.canUndo
        _canRedo.value = history.canRedo
    }

    private fun markChanged() {
        _saveState.value = SaveState.CHANGED
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(autosaveInterval * 1000L)
            save()
        }
    }

    fun saveNow() {
        saveJob?.cancel()
        viewModelScope.launch { save() }
    }

    private suspend fun save() {
        val document = _document.value ?: return
        if (_saveState.value == SaveState.SAVED) return
        _saveState.value = SaveState.SAVING
        try {
            repo.writeNote(path, document)
            _saveState.value = SaveState.SAVED
            _lastSave.value = System.currentTimeMillis()
            _error.value = null
        } catch (e: Exception) {
            _saveState.value = SaveState.ERROR
            _error.value = e.message ?: "Nie udało się zapisać notatki."
        }
    }

    fun dismissError() {
        _error.value = null
    }

    protected fun setError(text: String) {
        _error.value = text
    }

    override fun onCleared() {
        // Ostatnia szansa na zapis. Zakres modelu już się kończy,
        // więc zapis idzie przez repozytorium w zakresie aplikacji.
        val document = _document.value
        if (document != null && _saveState.value != SaveState.SAVED) {
            repo.writeInBackground(path, document)
        }
        super.onCleared()
    }

    class Factory(
        private val repo: LibraryRepository,
        private val settings: SettingsStore,
        private val path: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NoteViewModel(repo, settings, path) as T
    }
}
