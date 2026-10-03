package wojtoteka.ovh.kajet.editor

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
import wojtoteka.ovh.kajet.core.ai.AiHooks
import wojtoteka.ovh.kajet.core.awaria.failureHandler
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.NoteTitles
import wojtoteka.ovh.kajet.core.text.words
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
) : ViewModel(), AiHooks {

    protected val _document = MutableStateFlow<NoteDocument?>(null)
    val document: StateFlow<NoteDocument?> = _document.asStateFlow()

    private val _saveState = MutableStateFlow(SaveState.LOADING)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _lastSave = MutableStateFlow<Long?>(null)
    val lastSave: StateFlow<Long?> = _lastSave.asStateFlow()

    /*
      Czy serwer ma tę notatkę. „Zapisane" znaczy dysk; ta flaga to osobny
      sygnał z kolejki i zapamiętanej wersji. null - nie ma konta, ikony
      chmury nie ma.
    */
    private val _inCloud = MutableStateFlow<Boolean?>(null)
    val inCloud: StateFlow<Boolean?> = _inCloud.asStateFlow()

    /*
      Notatka zniknęła z dysku na polecenie serwera - ktoś skasował ją na
      innym urządzeniu, kiedy tu była otwarta. Treść na ekranie wciąż jest
      w pamięci; ekran notatki pyta wtedy człowieka, czy zapisać ją jako nową,
      czy odrzucić. Bez pytania nie wolno ani jej po cichu wskrzesić (kasujący
      wiedział, co robi), ani po cichu wyrzucić (piszący też).
    */
    private val _remotelyDeleted = MutableStateFlow(false)
    val remotelyDeleted: StateFlow<Boolean> = _remotelyDeleted.asStateFlow()

    /** Człowiek wybrał „Odrzuć zmiany" - zamknięcie modelu nie zapisuje w tle. */
    private var discarded = false

    /*
      Czy ktoś tknął pole tytułu ręcznie.

      Podpowiedź z treści włącza się przy PUSTYM tytule - a skasowanie
      podpowiedzianego tytułu to właśnie pusty tytuł. Autozapis rusza ułamek
      sekundy później i wpisywał podpowiedź z powrotem, więc własnego tytułu
      nie dało się wpisać: pole samo wracało do starej nazwy.

      Ręczna zmiana tytułu kończy podpowiadanie na resztę pracy z notatką,
      i to niezależnie od tego, co w polu zostało. Puste pole po skasowaniu
      też jest wyborem człowieka, a nie brakiem tytułu do wymyślenia.
    */
    private var titleTouched = false

    /*
      Nasłuchy ustawień mają własne handlery. Bez nich wyjątek z odczytu
      ustawień szedł prosto do domyślnego handlera wątku, czyli ubijał całą
      aplikację przy otwartej notatce - a chodzi tu tylko o spis kolorów i
      stronę paska narzędzi.
    */
    val recentColors: StateFlow<List<Int>> = MutableStateFlow<List<Int>>(emptyList()).also { state ->
        viewModelScope.launch(failureHandler("spis ostatnich kolorów")) {
            settings.settings.collect { state.value = it.recentColors }
        }
    }.asStateFlow()

    /** Pasek narzędzi po prawej stronie - ustawienie dla leworęcznych. */
    val toolbarOnRight: StateFlow<Boolean> = MutableStateFlow(false).also { state ->
        viewModelScope.launch(failureHandler("strona paska narzędzi")) {
            settings.settings.collect {
                state.value = it.toolbarSide == wojtoteka.ovh.kajet.storage.ToolbarSide.RIGHT
            }
        }
    }.asStateFlow()

    /**
     * Dokłada barwę do spisu „twoich kolorów". Wołać po zamknięciu okna z
     * tęczą, nie przy każdej zmianie - tęcza zgłasza każdy odcień mijany pod
     * palcem, a spis mieści tylko kilka pozycji.
     *
     * Zero znaczy „barwy nie ustawiono" (tak zapisuje się węzeł mapy bez
     * własnego koloru), więc do spisu nie trafia.
     */
    fun rememberColor(argb: Int) {
        if (argb == 0) return
        viewModelScope.launch { runCatching { settings.rememberColor(argb) } }
    }

    val history = ChangeHistory()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var saveJob: Job? = null
    private var pendingSince = 0L

    /**
     * Ostatnia siatka pod odczytem i zapisem notatki.
     *
     * [load] i [save] łapią `Exception`, ale nie `Error` - brak pamięci przy
     * wielkiej notatce przechodził obok nich i ubijał aplikację. Tu zatrzymuje
     * się wszystko i zamienia w napis na pasku zapisu.
     */
    private val brokenNote = failureHandler("notatka $path") { failure ->
        _saveState.value = SaveState.ERROR
        _error.value = failure.message ?: words.noteSaveFailed
    }

    init {
        viewModelScope.launch(brokenNote) { load() }

        // Kasowanie przysłane z serwera dzieje się w tle, pod otwartym
        // edytorem. Dopasowanie po ścieżce, z przedrostkiem - notatka potrafi
        // zniknąć także razem z całym folderem skasowanym gdzie indziej.
        viewModelScope.launch(failureHandler("nasłuch kasowania notatki $path")) {
            repo.remotelyRemovedPaths.collect { removed ->
                if (removed == path || path.startsWith("$removed/")) {
                    _remotelyDeleted.value = true
                }
            }
        }

        viewModelScope.launch(failureHandler("stan chmury $path")) {
            val lookup = repo.cloudSave ?: return@launch
            merge(lookup.changes(), document.map { }).collect { refreshCloudSave() }
        }
    }

    private suspend fun load() {
        try {
            _document.value = repo.readNote(path)
            _saveState.value = SaveState.SAVED
            refreshCloudSave()
        } catch (e: Exception) {
            _error.value = e.message ?: words.noteOpenFailed
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
        titleTouched = true
        editWithoutHistory { it.copy(title = title) }
    }

    protected fun refreshButtons() {
        _canUndo.value = history.canUndo
        _canRedo.value = history.canRedo
    }

    private fun markChanged() {
        _saveState.value = SaveState.CHANGED

        // Zapis rusza zaraz po zmianie. Krótka zwłoka skleja serię szybkich
        // zmian (pisane słowo, ciągnięta kreska) w jeden zapis, a górna granica
        // pilnuje, żeby przy pisaniu bez przerwy treść i tak szła na dysk.
        val now = System.currentTimeMillis()
        if (pendingSince == 0L) pendingSince = now
        val wait = if (now - pendingSince >= MAX_DEFER_MS) 0L else COALESCE_MS

        saveJob?.cancel()
        saveJob = viewModelScope.launch(brokenNote) {
            delay(wait)
            save()
        }
    }

    fun saveNow() {
        saveJob?.cancel()
        viewModelScope.launch(brokenNote) { save() }
    }

    private suspend fun save() {
        val stored = _document.value ?: return
        if (_saveState.value == SaveState.SAVED) return
        // Pliku już nie ma - kolejne zapisy tylko mieliłyby ten sam błąd.
        // Los treści rozstrzyga okno wyboru na ekranie notatki, nie autozapis.
        if (_remotelyDeleted.value) return
        val document = withSuggestedTitle(stored)
        // Podpowiedziany tytuł ma być widoczny w polu tytułu od razu -
        // wprost do stanu, nie przez markChanged, bo to by kręciło zapis
        // w kółko.
        if (document !== stored) _document.value = document
        pendingSince = 0L
        _saveState.value = SaveState.SAVING
        try {
            // Zapis chodzi teraz przy każdej zmianie, więc kolejna zmiana może
            // skasować zadanie w połowie pisania pliku. NonCancellable pilnuje,
            // żeby raz zaczęty zapis zawsze doszedł do końca.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                repo.writeNote(path, document)
            }
            _saveState.value = SaveState.SAVED
            _lastSave.value = System.currentTimeMillis()
            _error.value = null
            refreshCloudSave()
        } catch (e: Exception) {
            _saveState.value = SaveState.ERROR
            _error.value = e.message ?: words.noteSaveFailed
            // Droga zapasowa obok nasłuchu: zapis mógł paść właśnie dlatego,
            // że notatkę przed chwilą skasowano gdzie indziej - także wtedy,
            // gdy emisja umknęła (np. model wstał już po kasowaniu).
            if (runCatching { repo.entryVanished(path) }.getOrDefault(false)) {
                _remotelyDeleted.value = true
            }
        }
    }

    /**
     * Treść z ekranu zapisuje się jako świeża notatka - wybór „Zapisz jako
     * nową" po kasowaniu na innym urządzeniu.
     *
     * Świeży identyfikator jest nieprzypadkowy: po starym został na serwerze
     * nagrobek (albo wpis w koszu) i notatka pod starym numerem zniknęłaby
     * przy najbliższej synchronizacji. Załączniki wracają z lokalnego kosza,
     * o ile jeszcze tam leżą.
     *
     * Zwraca true po udanym zapisie; false zostawia treść na ekranie i zapala
     * komunikat - człowiek może spróbować jeszcze raz.
     */
    suspend fun saveAsNew(): Boolean {
        val current = _document.value ?: return false
        val fresh = withSuggestedTitle(current).copy(
            id = java.util.UUID.randomUUID().toString(),
        )
        val parent = path.substringBeforeLast('/', "")
        val saved = runCatching {
            repo.saveAsNewNote(parent, fresh, assetsFromNoteId = current.id)
        }.getOrNull()
        if (saved == null) {
            _error.value = words.saveAsNewFailed
            return false
        }
        // Nowa notatka jest na dysku - stara wersja z pamięci nie ma już
        // czego pilnować i nie może się zapisać w tle pod martwą ścieżką.
        discarded = true
        _saveState.value = SaveState.SAVED
        refreshCloudSave()
        return true
    }

    /** Wybór „Odrzuć zmiany": treść z pamięci przepada świadomie, nie po cichu. */
    fun discardChanges() {
        discarded = true
    }

    /**
     * Tytuł podpowiedziany z treści - jak na stronie: tylko dopóki tytuł jest
     * wciąż podstawionym „Bez tytułu" (albo pusty). Pierwsza podpowiedź go
     * nadpisuje i mechanizm sam się kończy; ręcznie wpisanej nazwy nie rusza
     * nigdy. Notatka odręczna nie ma tekstu, więc naturalnie nic się nie dzieje.
     *
     * [titleTouched] wyłącza podpowiadanie od chwili, gdy ktoś sięgnie do pola
     * tytułu - inaczej skasowanie podpowiedzi wracało przy najbliższym zapisie.
     */
    private fun withSuggestedTitle(document: NoteDocument): NoteDocument {
        if (titleTouched) return document
        if (!NoteTitles.isPlaceholder(document.title)) return document
        val suggested = document.text?.let { NoteTitles.fromMarkdown(it.markdown) }
            ?: document.mindMap?.let { NoteTitles.fromMindMap(it.nodes) }
        return if (suggested.isNullOrBlank()) document else document.copy(title = suggested)
    }

    // --- Asystent KajetAI ---
    //
    // Trzy rzeczy, których potrzebuje panel asystenta. Siedzą tutaj, a nie
    // w panelu, bo tylko model wie, co jest na ekranie i którędy idzie zapis.

    /** Wersja sprzed zmiany asystenta - jedyne, na czym stoi cofanie. */
    private var beforeAi: NoteDocument? = null

    /**
     * Zapisuje to, co na ekranie, i zapamiętuje tę wersję.
     *
     * Asystent pracuje na tym, co ma serwer, więc niezapisane zdanie musi
     * trafić na dysk (a stamtąd synchronizacją w górę) ZANIM model je
     * przeczyta. Inaczej poprawiałby wersję sprzed ostatniej minuty.
     */
    override suspend fun prepareForAi() {
        beforeAi = _document.value
        saveJob?.cancel()
        save()
    }

    /** Notatkę zmienił asystent i synchronizacja ją już ściągnęła. */
    override suspend fun reloadAfterAi() {
        load()
    }

    /**
     * Powrót do wersji sprzed zmiany asystenta.
     *
     * To zwykły zapis, nie żadna osobna droga: stara treść wraca jako kolejna
     * wersja notatki i jedzie na serwer tak samo jak każda inna poprawka.
     * Dzięki temu cofnięcie dociera też na pozostałe urządzenia.
     */
    override suspend fun undoAi(): Boolean {
        val previous = beforeAi ?: return false
        _document.value = previous
        _saveState.value = SaveState.CHANGED
        save()
        beforeAi = null
        return _saveState.value == SaveState.SAVED
    }

    fun dismissError() {
        _error.value = null
    }

    private fun refreshCloudSave() {
        _inCloud.value = repo.cloudSave?.inCloud(path, _document.value?.id)
    }

    protected fun setError(text: String) {
        _error.value = text
    }

    override fun onCleared() {
        // Ostatnia szansa na zapis. Zakres modelu już się kończy,
        // więc zapis idzie przez repozytorium w zakresie aplikacji.
        // Tytuł podpowiada się i tutaj - dla notatki zamkniętej tuż po
        // napisaniu pierwszego zdania.
        //
        // Notatka skasowana zdalnie i treść odrzucona świadomie zostają
        // w spokoju: zapis pod martwą ścieżką i tak by padł, a po „Odrzuć
        // zmiany" nie wolno niczego dopisywać za plecami człowieka.
        val document = _document.value
        if (!discarded && !_remotelyDeleted.value && document != null) {
            val tidy = tidyOnClose(withSuggestedTitle(document))
            val changed = _saveState.value != SaveState.SAVED || tidy.document != document
            if (changed || tidy.unused.isNotEmpty()) {
                repo.writeAndDropAttachmentsInBackground(
                    path = path,
                    document = if (changed) tidy.document else null,
                    unused = tidy.unused,
                )
            }
        }
        super.onCleared()
    }

    /** Notatka do ostatniego zapisu i załączniki, których już nie używa. */
    protected class Tidy(val document: NoteDocument, val unused: Collection<String> = emptyList())

    /**
     * Porządki przy zamknięciu notatki: rodzaj notatki może zdjąć z niej to,
     * czego już nie używa, i wskazać pliki do skasowania. Pliki znikają
     * dopiero PO zapisie treści.
     */
    protected open fun tidyOnClose(document: NoteDocument): Tidy = Tidy(document)

    class Factory(
        private val repo: LibraryRepository,
        private val settings: SettingsStore,
        private val path: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NoteViewModel(repo, settings, path) as T
    }

    private companion object {
        const val COALESCE_MS = 400L
        const val MAX_DEFER_MS = 2_000L
    }
}
