package wojtoteka.ovh.kajet.code

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import wojtoteka.ovh.kajet.core.awaria.failureHandler
import wojtoteka.ovh.kajet.core.model.CodeLanguage
import wojtoteka.ovh.kajet.core.text.Strings
import wojtoteka.ovh.kajet.core.text.cannotRunLanguage
import wojtoteka.ovh.kajet.core.ai.AiHooks
import wojtoteka.ovh.kajet.core.text.words
import wojtoteka.ovh.kajet.storage.SettingsStore
import wojtoteka.ovh.kajet.storage.LibraryRepository

enum class PanelTab {
    OUTPUT,
    ERRORS,
    INPUT,
    ;

    fun label(words: Strings): String = when (this) {
        OUTPUT -> words.codeOutput
        ERRORS -> words.codeErrors
        INPUT -> words.codeInput
    }
}

class CodeViewModel(
    private val repo: LibraryRepository,
    private val settings: SettingsStore,
    private val registry: RunnerRegistry,
    val path: String,
    /**
     * Czy zawijanie wierszy ma być włączone od początku.
     *
     * Na telefonie tak. Wiersz kodu nie mieści się tam w szerokości ekranu,
     * a przewijanie w bok nie daje po sobie żadnego znaku - było widać
     * `std::cout << "Cześć" << std:` i nic więcej, bez śladu, że dalej coś
     * jeszcze jest. Na tablecie wiersz się mieści, więc zostaje jak było.
     */
    wrapByDefault: Boolean = false,
    /**
     * Pomoc przy pisaniu ze snapshotu, który Kajet już ma (ekran startowy
     * czeka na DataStore). Gdyby tu wstawić `true` i dograć ustawienie w tle,
     * pierwsze znaki poszłyby z domykaniem nawiasów, choć ktoś to zgasił.
     */
    assistEnabled: Boolean = true,
) : ViewModel(), AiHooks {

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

    /*
      Czy serwer ma ten plik. „Zapisane" znaczy dysk; ta flaga to osobny
      sygnał z kolejki i zapamiętanej wersji. null - nie ma konta.
    */
    private val _inCloud = MutableStateFlow<Boolean?>(null)
    val inCloud: StateFlow<Boolean?> = _inCloud.asStateFlow()

    /*
      Plik zniknął z dysku na polecenie serwera - skasowany na innym
      urządzeniu, kiedy tu był otwarty. Kod na ekranie wciąż jest w pamięci;
      edytor pyta wtedy człowieka, czy zapisać go jako nowy plik, czy odrzucić.
    */
    private val _remotelyDeleted = MutableStateFlow(false)
    val remotelyDeleted: StateFlow<Boolean> = _remotelyDeleted.asStateFlow()

    /** Człowiek wybrał „Odrzuć zmiany" - zamknięcie modelu nie zapisuje w tle. */
    private var discarded = false

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _matches = MutableStateFlow<List<IntRange>>(emptyList())
    val matches: StateFlow<List<IntRange>> = _matches.asStateFlow()

    private val _wordWrap = MutableStateFlow(wrapByDefault)
    val wordWrap: StateFlow<Boolean> = _wordWrap.asStateFlow()

    /**
     * Pomoc przy pisaniu (domykanie nawiasów i znaczników). Ustawienie konta na
     * urządzeniu - kto woli pisać wszystko sam, gasi ją w ustawieniach.
     */
    private val _assist = MutableStateFlow(assistEnabled)
    val assist: StateFlow<Boolean> = _assist.asStateFlow()

    /** Pasek narzędzi po prawej stronie - ustawienie dla leworęcznych. */
    private val _toolbarOnRight = MutableStateFlow(false)
    val toolbarOnRight: StateFlow<Boolean> = _toolbarOnRight.asStateFlow()

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
                _error.value = e.message ?: words.codeOpenFailed
            }
        }
        // Własny handler: nieudany odczyt ustawień ma zgasić podpowiadanie
        // składni, a nie zamknąć edytor kodu razem z niezapisanym plikiem.
        viewModelScope.launch(failureHandler("ustawienia edytora kodu")) {
            settings.settings.collect {
                _assist.value = it.codeAssist
                _toolbarOnRight.value =
                    it.toolbarSide == wojtoteka.ovh.kajet.storage.ToolbarSide.RIGHT
            }
        }
        // Kasowanie przysłane z serwera dzieje się w tle, pod otwartym
        // edytorem. Przedrostek, bo plik potrafi zniknąć razem z folderem.
        viewModelScope.launch(failureHandler("nasłuch kasowania pliku $path")) {
            repo.remotelyRemovedPaths.collect { removed ->
                if (removed == path || path.startsWith("$removed/")) {
                    _remotelyDeleted.value = true
                }
            }
        }
        viewModelScope.launch(failureHandler("stan chmury $path")) {
            val lookup = repo.cloudSave ?: return@launch
            merge(lookup.changes(), _saved.map { }).collect { refreshCloudSave() }
        }
    }

    /**
     * Zmiana w polu z kodem, przepuszczona przez pomocnika. Oddaje stan, który
     * ma naprawdę wejść do pola - z domkniętym nawiasem albo znacznikiem.
     */
    fun onTyping(
        before: androidx.compose.ui.text.input.TextFieldValue,
        after: androidx.compose.ui.text.input.TextFieldValue,
    ): androidx.compose.ui.text.input.TextFieldValue {
        val helped = if (_assist.value) {
            CodeAssist.keepIndent(before, CodeAssist.assist(before, after, language))
        } else {
            after
        }
        if (helped.text != _code.value) onCodeChange(helped.text)
        return helped
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

    // --- Asystent KajetAI ---

    /** Kod sprzed zmiany asystenta - jedyne, na czym stoi cofanie. */
    private var beforeAi: String? = null

    override suspend fun prepareForAi() {
        beforeAi = _code.value
        saveJob?.cancel()
        save()
    }

    override suspend fun reloadAfterAi() {
        runCatching { repo.readText(path) }.onSuccess {
            _code.value = it
            _saved.value = true
            refreshCloudSave()
        }
    }

    /**
     * Powrot do kodu sprzed zmiany. Zwykly zapis, nie osobna droga: stara
     * tresc wraca jako kolejna wersja pliku i jedzie na serwer tak samo jak
     * kazda inna poprawka, wiec cofniecie dociera tez na reszte urzadzen.
     */
    override suspend fun undoAi(): Boolean {
        val previous = beforeAi ?: return false
        _code.value = previous
        _saved.value = false
        save()
        beforeAi = null
        return _saved.value
    }

    private suspend fun save() {
        if (_saved.value) return
        // Pliku już nie ma - los kodu rozstrzyga okno wyboru, nie autozapis.
        if (_remotelyDeleted.value) return
        pendingSince = 0L
        try {
            // Raz zaczęty zapis ma dojść do końca, nawet gdy kolejna zmiana
            // właśnie kasuje to zadanie.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                repo.writeText(path, _code.value)
            }
            _saved.value = true
            refreshCloudSave()
        } catch (e: Exception) {
            _error.value = e.message ?: words.codeSaveFailed
            // Droga zapasowa obok nasłuchu: zapis mógł paść dlatego, że plik
            // przed chwilą skasowano gdzie indziej.
            if (runCatching { repo.entryVanished(path) }.getOrDefault(false)) {
                _remotelyDeleted.value = true
            }
        }
    }

    /**
     * Kod z ekranu zapisuje się jako świeży plik - wybór „Zapisz jako nowy
     * plik" po kasowaniu na innym urządzeniu. Świeża tożsamość w chmurze
     * pilnuje, żeby nie wskrzesić skasowanego wpisu na serwerze.
     */
    suspend fun saveAsNewFile(): Boolean {
        val parent = path.substringBeforeLast('/', "")
        val saved = runCatching {
            repo.saveAsNewCodeFile(parent, fileName, _code.value)
        }.getOrNull()
        if (saved == null) {
            _error.value = words.saveAsNewFailed
            return false
        }
        discarded = true
        _saved.value = true
        refreshCloudSave()
        return true
    }

    /** Wybór „Odrzuć zmiany": kod z pamięci przepada świadomie, nie po cichu. */
    fun discardChanges() {
        discarded = true
    }

    fun run() {
        val codeRunner = runner
        if (codeRunner == null) {
            _error.value = words.cannotRunLanguage(language.label(words))
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
                _error.value = e.message ?: words.codeRunFailed
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

    private fun refreshCloudSave() {
        _inCloud.value = repo.cloudSave?.inCloud(path, null)
    }

    override fun onCleared() {
        // Plik skasowany zdalnie i treść odrzucona świadomie zostają w spokoju
        // - zapis pod martwą ścieżką i tak by padł.
        if (!discarded && !_remotelyDeleted.value && !_saved.value) {
            repo.writeTextInBackground(path, _code.value)
        }
        super.onCleared()
    }

    class Factory(
        private val repo: LibraryRepository,
        private val settings: SettingsStore,
        private val registry: RunnerRegistry,
        private val path: String,
        private val wrapByDefault: Boolean = false,
        private val assistEnabled: Boolean = true,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CodeViewModel(repo, settings, registry, path, wrapByDefault, assistEnabled) as T
    }
}
