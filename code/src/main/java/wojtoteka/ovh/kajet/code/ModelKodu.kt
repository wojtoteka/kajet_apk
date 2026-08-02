package wojtoteka.ovh.kajet.code

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
import wojtoteka.ovh.kajet.core.model.CodeLanguage
import wojtoteka.ovh.kajet.storage.MagazynUstawien
import wojtoteka.ovh.kajet.storage.RepozytoriumBiblioteki

/** Zakładka panelu pod edytorem. */
enum class ZakladkaPanelu {
    WYNIK,
    BLEDY,
    WEJSCIE,
    ;

    val nazwaPl: String
        get() = when (this) {
            WYNIK -> "Wynik"
            BLEDY -> "Błędy"
            WEJSCIE -> "Wejście"
        }
}

/**
 * Model edytora kodu.
 *
 * Plik z kodem to zwykły plik na dysku, więc zapisujemy go tak samo jak notatkę:
 * sam, co kilka sekund i przy wyjściu. Dane wpisane do zakładki Wejście
 * nie są częścią pliku, bo to notatnik na próbę, a nie treść programu.
 */
class ModelKodu(
    private val repo: RepozytoriumBiblioteki,
    private val ustawienia: MagazynUstawien,
    private val rejestr: RejestrUruchamiania,
    val sciezka: String,
) : ViewModel() {

    val jezyk: CodeLanguage = CodeLanguage.fromExtension(sciezka.substringAfterLast('/'))
        ?: CodeLanguage.TEKST

    val nazwaPliku: String = sciezka.substringAfterLast('/')

    private val _kod = MutableStateFlow("")
    val kod: StateFlow<String> = _kod.asStateFlow()

    private val _wejscie = MutableStateFlow("")
    val wejscie: StateFlow<String> = _wejscie.asStateFlow()

    private val _wynik = MutableStateFlow<WynikUruchomienia?>(null)
    val wynik: StateFlow<WynikUruchomienia?> = _wynik.asStateFlow()

    private val _zakladka = MutableStateFlow(ZakladkaPanelu.WYNIK)
    val zakladka: StateFlow<ZakladkaPanelu> = _zakladka.asStateFlow()

    private val _uruchamianie = MutableStateFlow(false)
    val uruchamianie: StateFlow<Boolean> = _uruchamianie.asStateFlow()

    private val _blad = MutableStateFlow<String?>(null)
    val blad: StateFlow<String?> = _blad.asStateFlow()

    private val _zapisane = MutableStateFlow(true)
    val zapisane: StateFlow<Boolean> = _zapisane.asStateFlow()

    private val _szukanie = MutableStateFlow("")
    val szukanie: StateFlow<String> = _szukanie.asStateFlow()

    private val _trafienia = MutableStateFlow<List<IntRange>>(emptyList())
    val trafienia: StateFlow<List<IntRange>> = _trafienia.asStateFlow()

    private val _zawijanie = MutableStateFlow(false)
    val zawijanie: StateFlow<Boolean> = _zawijanie.asStateFlow()

    /** Czy ten język policzy się na tablecie, czy pójdzie przez internet. */
    val offline: Boolean = rejestr.offline(jezyk)
    val sposob: CodeRunner? = rejestr.dla(jezyk)

    private var zadanieZapisu: Job? = null
    private var zadanieUruchomienia: Job? = null
    private var odstepAutozapisu = 5

    init {
        viewModelScope.launch {
            odstepAutozapisu = runCatching { ustawienia.ustawienia.first().odstepAutozapisu }.getOrDefault(5)
            try {
                _kod.value = repo.czytajTekst(sciezka)
            } catch (e: Exception) {
                _blad.value = e.message ?: "Nie udało się otworzyć pliku."
            }
        }
    }

    fun zmienKod(nowy: String) {
        _kod.value = nowy
        _zapisane.value = false
        odswiezTrafienia()
        zadanieZapisu?.cancel()
        zadanieZapisu = viewModelScope.launch {
            delay(odstepAutozapisu * 1000L)
            zapisz()
        }
    }

    fun zmienWejscie(nowe: String) {
        _wejscie.value = nowe
    }

    fun ustawZakladke(nowa: ZakladkaPanelu) {
        _zakladka.value = nowa
    }

    fun przelaczZawijanie() {
        _zawijanie.value = !_zawijanie.value
    }

    fun szukaj(fragment: String) {
        _szukanie.value = fragment
        odswiezTrafienia()
    }

    private fun odswiezTrafienia() {
        val fragment = _szukanie.value
        if (fragment.length < 2) {
            _trafienia.value = emptyList()
            return
        }
        val tekst = _kod.value
        val wynik = mutableListOf<IntRange>()
        var od = tekst.indexOf(fragment, ignoreCase = true)
        while (od >= 0 && wynik.size < 500) {
            wynik += od until (od + fragment.length)
            od = tekst.indexOf(fragment, od + 1, ignoreCase = true)
        }
        _trafienia.value = wynik
    }

    fun zapiszTeraz() {
        zadanieZapisu?.cancel()
        viewModelScope.launch { zapisz() }
    }

    private suspend fun zapisz() {
        if (_zapisane.value) return
        try {
            repo.zapiszTekst(sciezka, _kod.value)
            _zapisane.value = true
        } catch (e: Exception) {
            _blad.value = e.message ?: "Nie udało się zapisać pliku."
        }
    }

    fun uruchom() {
        val sposobUruchomienia = sposob
        if (sposobUruchomienia == null) {
            _blad.value = "Kajet nie umie uruchomić języka ${jezyk.labelPl}. Plik możesz nadal pisać i zapisywać."
            return
        }
        zadanieUruchomienia?.cancel()
        zadanieUruchomienia = viewModelScope.launch {
            _uruchamianie.value = true
            _blad.value = null
            zapisz()
            try {
                val wynikUruchomienia = sposobUruchomienia.uruchom(
                    jezyk = jezyk,
                    kod = _kod.value,
                    wejscie = _wejscie.value,
                    nazwaPliku = nazwaPliku,
                )
                _wynik.value = wynikUruchomienia
                _zakladka.value = if (wynikUruchomienia.bledy.isNotBlank()) {
                    ZakladkaPanelu.BLEDY
                } else {
                    ZakladkaPanelu.WYNIK
                }
            } catch (e: BladUruchomienia) {
                _blad.value = e.komunikatDlaUzytkownika
            } catch (e: Exception) {
                _blad.value = e.message ?: "Uruchomienie się nie udało."
            } finally {
                _uruchamianie.value = false
            }
        }
    }

    fun zatrzymaj() {
        zadanieUruchomienia?.cancel()
        _uruchamianie.value = false
    }

    fun schowajBlad() {
        _blad.value = null
    }

    override fun onCleared() {
        if (!_zapisane.value) repo.zapiszTekstWTle(sciezka, _kod.value)
        super.onCleared()
    }

    class Fabryka(
        private val repo: RepozytoriumBiblioteki,
        private val ustawienia: MagazynUstawien,
        private val rejestr: RejestrUruchamiania,
        private val sciezka: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ModelKodu(repo, ustawienia, rejestr, sciezka) as T
    }
}
