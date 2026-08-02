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
import wojtoteka.ovh.kajet.storage.MagazynUstawien
import wojtoteka.ovh.kajet.storage.RepozytoriumBiblioteki

/** Co się dzieje z zapisem notatki. Pokazujemy to na pasku u góry. */
enum class StanZapisu {
    WCZYTYWANIE,
    ZAPISANE,
    ZMIENIONE,
    ZAPISYWANIE,
    BLAD,
}

/**
 * Wspólny model notatki dla wszystkich trzech rodzajów: odręcznej, tekstowej i mapy myśli.
 *
 * Notatka zapisuje się sama. Nie ma przycisku zapisz. Po każdej zmianie
 * ustawiamy budzik na kilka sekund, a przy wyjściu z edytora zapisujemy od razu.
 */
open class ModelNotatki(
    private val repo: RepozytoriumBiblioteki,
    private val ustawienia: MagazynUstawien,
    val sciezka: String,
) : ViewModel() {

    protected val _dokument = MutableStateFlow<NoteDocument?>(null)
    val dokument: StateFlow<NoteDocument?> = _dokument.asStateFlow()

    private val _stanZapisu = MutableStateFlow(StanZapisu.WCZYTYWANIE)
    val stanZapisu: StateFlow<StanZapisu> = _stanZapisu.asStateFlow()

    private val _blad = MutableStateFlow<String?>(null)
    val blad: StateFlow<String?> = _blad.asStateFlow()

    val historia = HistoriaZmian()

    private val _mozeCofnac = MutableStateFlow(false)
    val mozeCofnac: StateFlow<Boolean> = _mozeCofnac.asStateFlow()

    private val _mozePonowic = MutableStateFlow(false)
    val mozePonowic: StateFlow<Boolean> = _mozePonowic.asStateFlow()

    private var zadanieZapisu: Job? = null
    private var odstepAutozapisu = 5

    init {
        viewModelScope.launch {
            odstepAutozapisu = runCatching { ustawienia.ustawienia.first().odstepAutozapisu }.getOrDefault(5)
            wczytaj()
        }
    }

    private suspend fun wczytaj() {
        try {
            _dokument.value = repo.czytajNotatke(sciezka)
            _stanZapisu.value = StanZapisu.ZAPISANE
        } catch (e: Exception) {
            _blad.value = e.message ?: "Nie udało się otworzyć notatki."
            _stanZapisu.value = StanZapisu.BLAD
        }
    }

    /** Zapisuje zmianę i odkłada ją na stos cofania. */
    fun wykonaj(zmiana: Zmiana) {
        val biezacy = _dokument.value ?: return
        _dokument.value = zmiana.zastosuj(biezacy)
        historia.zapamietaj(zmiana)
        odswiezPrzyciski()
        oznaczZmiane()
    }

    /** Zmiana bez wpisu w historii, na przykład poprawka tytułu albo gwiazdka. */
    fun zmienBezHistorii(przeksztalcenie: (NoteDocument) -> NoteDocument) {
        val biezacy = _dokument.value ?: return
        _dokument.value = przeksztalcenie(biezacy)
        oznaczZmiane()
    }

    fun cofnij() {
        val biezacy = _dokument.value ?: return
        val po = historia.cofnij(biezacy) ?: return
        _dokument.value = po
        odswiezPrzyciski()
        oznaczZmiane()
    }

    fun ponow() {
        val biezacy = _dokument.value ?: return
        val po = historia.ponow(biezacy) ?: return
        _dokument.value = po
        odswiezPrzyciski()
        oznaczZmiane()
    }

    fun przelaczUlubione() {
        zmienBezHistorii { it.copy(favorite = !it.favorite) }
    }

    fun zmienTytul(tytul: String) {
        zmienBezHistorii { it.copy(title = tytul) }
    }

    protected fun odswiezPrzyciski() {
        _mozeCofnac.value = historia.mozeCofnac
        _mozePonowic.value = historia.mozePonowic
    }

    private fun oznaczZmiane() {
        _stanZapisu.value = StanZapisu.ZMIENIONE
        zadanieZapisu?.cancel()
        zadanieZapisu = viewModelScope.launch {
            delay(odstepAutozapisu * 1000L)
            zapisz()
        }
    }

    /** Zapisuje teraz. Wywoływane przy wyjściu z notatki i przy uśpieniu tabletu. */
    fun zapiszTeraz() {
        zadanieZapisu?.cancel()
        viewModelScope.launch { zapisz() }
    }

    private suspend fun zapisz() {
        val dokument = _dokument.value ?: return
        if (_stanZapisu.value == StanZapisu.ZAPISANE) return
        _stanZapisu.value = StanZapisu.ZAPISYWANIE
        try {
            repo.zapiszNotatke(sciezka, dokument)
            _stanZapisu.value = StanZapisu.ZAPISANE
            _blad.value = null
        } catch (e: Exception) {
            _stanZapisu.value = StanZapisu.BLAD
            _blad.value = e.message ?: "Nie udało się zapisać notatki."
        }
    }

    fun schowajBlad() {
        _blad.value = null
    }

    override fun onCleared() {
        // Ostatnia szansa na zapis. Zakres modelu już się kończy,
        // więc zapis idzie przez repozytorium w zakresie aplikacji.
        val dokument = _dokument.value
        if (dokument != null && _stanZapisu.value != StanZapisu.ZAPISANE) {
            repo.zapiszWTle(sciezka, dokument)
        }
        super.onCleared()
    }

    class Fabryka(
        private val repo: RepozytoriumBiblioteki,
        private val ustawienia: MagazynUstawien,
        private val sciezka: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ModelNotatki(repo, ustawienia, sciezka) as T
    }
}
