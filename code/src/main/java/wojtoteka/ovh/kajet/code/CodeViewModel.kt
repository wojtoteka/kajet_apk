package wojtoteka.ovh.kajet.code

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import wojtoteka.ovh.kajet.core.model.CodeLanguage
import wojtoteka.ovh.kajet.storage.SettingsStore
import wojtoteka.ovh.kajet.storage.LibraryRepository

enum class PanelTab {
    OUTPUT,
    ERRORS,
    INPUT,
    ;

    val labelPl: String
        get() = when (this) {
            OUTPUT -> "Wynik"
            ERRORS -> "Błędy"
            INPUT -> "Wejście"
        }
}

class CodeViewModel(
    private val repo: LibraryRepository,
    private val settings: SettingsStore,
    private val registry: RunnerRegistry,
    val path: String,
) : ViewModel() {

    val language: CodeLanguage = CodeLanguage.fromExtension(path.substringAfterLast('/'))
        ?: CodeLanguage.PLAIN_TEXT

    val fileName: String = path.substringAfterLast('/')

    private val _code = MutableStateFlow("")
    val code: StateFlow<String> = _code.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _result = MutableStateFlow<RunResult?>(null)
    val result: StateFlow<RunResult?> = _result.asStateFlow()

    private val _tab = MutableStateFlow(PanelTab.OUTPUT)
    val tab: StateFlow<PanelTab> = _tab.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _saved = MutableStateFlow(true)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _matches = MutableStateFlow<List<IntRange>>(emptyList())
    val matches: StateFlow<List<IntRange>> = _matches.asStateFlow()

    private val _wordWrap = MutableStateFlow(false)
    val wordWrap: StateFlow<Boolean> = _wordWrap.asStateFlow()

    val offline: Boolean = registry.runsOffline(language)
    val runner: CodeRunner? = registry.forLanguage(language)

    private var saveJob: Job? = null
    private var runJob: Job? = null
    private var pendingSince = 0L

    init {
        viewModelScope.launch {
            try {
                _code.value = repo.readText(path)
            } catch (e: Exception) {
                _error.value = e.message ?: "Nie udało się otworzyć pliku."
            }
        }
    }

    fun onCodeChange(text: String) {
        _code.value = text
        _saved.value = false
        refreshMatches()

        // Zapis rusza zaraz po zmianie: krótka zwłoka skleja szybkie pisanie
        // w jeden zapis, a górna granica pilnuje, żeby przy pisaniu bez przerwy
        // kod i tak szedł na dysk. Ta sama zasada co w NoteViewModel.
        val now = System.currentTimeMillis()
        if (pendingSince == 0L) pendingSince = now
        val wait = if (now - pendingSince >= 2_000L) 0L else 400L

        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(wait)
            save()
        }
    }

    fun onInputChange(text: String) {
        _input.value = text
    }

    fun selectTab(tab: PanelTab) {
        _tab.value = tab
    }

    fun toggleWordWrap() {
        _wordWrap.value = !_wordWrap.value
    }

    fun search(needle: String) {
        _query.value = needle
        refreshMatches()
    }

    private fun refreshMatches() {
        val needle = _query.value
        if (needle.length < 2) {
            _matches.value = emptyList()
            return
        }
        val text = _code.value
        val found = mutableListOf<IntRange>()
        var at = text.indexOf(needle, ignoreCase = true)
        while (at >= 0 && found.size < 500) {
            found += at until (at + needle.length)
            at = text.indexOf(needle, at + 1, ignoreCase = true)
        }
        _matches.value = found
    }

    fun saveNow() {
        saveJob?.cancel()
        viewModelScope.launch { save() }
    }

    private suspend fun save() {
        if (_saved.value) return
        pendingSince = 0L
        try {
            // Raz zaczęty zapis ma dojść do końca, nawet gdy kolejna zmiana
            // właśnie kasuje to zadanie.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                repo.writeText(path, _code.value)
            }
            _saved.value = true
        } catch (e: Exception) {
            _error.value = e.message ?: "Nie udało się zapisać pliku."
        }
    }

    fun run() {
        val codeRunner = runner
        if (codeRunner == null) {
            _error.value = "Kajet nie umie uruchomić języka ${language.labelPl}. Plik możesz nadal pisać i zapisywać."
            return
        }
        runJob?.cancel()
        runJob = viewModelScope.launch {
            _running.value = true
            _error.value = null
            save()
            try {
                val runResult = codeRunner.run(
                    language = language,
                    code = _code.value,
                    input = _input.value,
                    fileName = fileName,
                )
                _result.value = runResult
                _tab.value = if (runResult.errors.isNotBlank()) PanelTab.ERRORS else PanelTab.OUTPUT
            } catch (e: RunException) {
                _error.value = e.userMessage
            } catch (e: Exception) {
                _error.value = e.message ?: "Uruchomienie się nie udało."
            } finally {
                _running.value = false
            }
        }
    }

    fun stop() {
        runJob?.cancel()
        _running.value = false
    }

    fun dismissError() {
        _error.value = null
    }

    override fun onCleared() {
        if (!_saved.value) repo.writeTextInBackground(path, _code.value)
        super.onCleared()
    }

    class Factory(
        private val repo: LibraryRepository,
        private val settings: SettingsStore,
        private val registry: RunnerRegistry,
        private val path: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CodeViewModel(repo, settings, registry, path) as T
    }
}
