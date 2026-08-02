package wojtoteka.ovh.kajet.ekran.biblioteka

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import wojtoteka.ovh.kajet.storage.RepozytoriumBiblioteki
import wojtoteka.ovh.kajet.storage.WpisKosza

/** Co pokazuje kolumna z treścią. Pasek marginesu przełącza między tymi widokami. */
enum class Sekcja {
    BIBLIOTEKA,
    ULUBIONE,
    OSTATNIE,
    SZUKAJ,
    KOSZ,
    ;

    val nazwaPl: String
        get() = when (this) {
            BIBLIOTEKA -> "Biblioteka"
            ULUBIONE -> "Ulubione"
            OSTATNIE -> "Ostatnio otwarte"
            SZUKAJ -> "Szukaj"
            KOSZ -> "Kosz"
        }
}

/** Jeden wiersz drzewa folderów po lewej stronie. */
data class WezelDrzewa(
    val wpis: LibraryItem,
    val poziom: Int,
    val rozwiniety: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ModelBiblioteki(private val repo: RepozytoriumBiblioteki) : ViewModel() {

    private val _sciezka = MutableStateFlow("")
    val sciezka: StateFlow<String> = _sciezka.asStateFlow()

    private val _sekcja = MutableStateFlow(Sekcja.BIBLIOTEKA)
    val sekcja: StateFlow<Sekcja> = _sekcja.asStateFlow()

    private val _rozwiniete = MutableStateFlow(setOf(""))
    private val _drzewo = MutableStateFlow<List<WezelDrzewa>>(emptyList())
    val drzewo: StateFlow<List<WezelDrzewa>> = _drzewo.asStateFlow()

    private val _kosz = MutableStateFlow<List<WpisKosza>>(emptyList())
    val kosz: StateFlow<List<WpisKosza>> = _kosz.asStateFlow()

    private val _zapytanie = MutableStateFlow("")
    val zapytanie: StateFlow<String> = _zapytanie.asStateFlow()

    private val _wynikiSzukania = MutableStateFlow<List<LibraryItem>>(emptyList())
    val wynikiSzukania: StateFlow<List<LibraryItem>> = _wynikiSzukania.asStateFlow()

    private val _blad = MutableStateFlow<String?>(null)
    val blad: StateFlow<String?> = _blad.asStateFlow()

    private val _przebudowa = MutableStateFlow<String?>(null)
    val przebudowa: StateFlow<String?> = _przebudowa.asStateFlow()

    val zawartosc: StateFlow<List<LibraryItem>> = _sciezka
        .flatMapLatest { repo.folder(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ulubione: StateFlow<List<LibraryItem>> = repo.ulubione()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ostatnie: StateFlow<List<LibraryItem>> = repo.ostatnie()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            combine(_rozwiniete, zawartosc) { rozwiniete, _ -> rozwiniete }.collect { rozwiniete ->
                _drzewo.value = zbudujDrzewo(rozwiniete)
            }
        }
    }

    private suspend fun zbudujDrzewo(rozwiniete: Set<String>): List<WezelDrzewa> {
        val wynik = mutableListOf<WezelDrzewa>()
        suspend fun zejdz(sciezka: String, poziom: Int) {
            val foldery = runCatching { repo.folderySynchronicznie(sciezka) }.getOrDefault(emptyList())
            for (folder in foldery) {
                val czyRozwiniety = folder.path in rozwiniete
                wynik += WezelDrzewa(folder, poziom, czyRozwiniety)
                if (czyRozwiniety) zejdz(folder.path, poziom + 1)
            }
        }
        zejdz("", 0)
        return wynik
    }

    fun przejdzDo(sciezka: String) {
        _sekcja.value = Sekcja.BIBLIOTEKA
        _sciezka.value = sciezka
        _rozwiniete.value = _rozwiniete.value + sciezkiNadrzedne(sciezka)
    }

    fun wyzej() {
        val biezaca = _sciezka.value
        if (biezaca.isEmpty()) return
        _sciezka.value = biezaca.substringBeforeLast('/', "")
    }

    fun przelaczRozwiniecie(sciezka: String) {
        _rozwiniete.value = if (sciezka in _rozwiniete.value) {
            _rozwiniete.value - sciezka
        } else {
            _rozwiniete.value + sciezka
        }
    }

    fun ustawSekcje(nowa: Sekcja) {
        _sekcja.value = nowa
        if (nowa == Sekcja.KOSZ) odswiezKosz()
    }

    fun ustawZapytanie(tekst: String) {
        _zapytanie.value = tekst
        viewModelScope.launch {
            _wynikiSzukania.value = runCatching { repo.szukaj(tekst) }.getOrDefault(emptyList())
        }
    }

    fun schowajBlad() {
        _blad.value = null
    }

    // Zmiany

    fun nowyFolder(nazwa: String, colorId: String, iconId: String) = wTle {
        repo.utworzFolder(_sciezka.value, nazwa, colorId, iconId)
        _rozwiniete.value = _rozwiniete.value + _sciezka.value
    }

    fun nowaNotatka(
        tytul: String,
        rodzaj: NoteKind,
        tryb: PageMode,
        tlo: PageBackground,
        poUtworzeniu: (LibraryItem) -> Unit = {},
    ) = wTle {
        val wpis = repo.utworzNotatke(_sciezka.value, tytul, rodzaj, tryb, tlo)
        poUtworzeniu(wpis)
    }

    fun nowyPlikKodu(nazwa: String, jezyk: CodeLanguage, poUtworzeniu: (LibraryItem) -> Unit = {}) = wTle {
        val wpis = repo.utworzPlikKodu(_sciezka.value, nazwa, jezyk)
        poUtworzeniu(wpis)
    }

    fun zmienNazwe(wpis: LibraryItem, nowaNazwa: String) = wTle {
        repo.zmienNazwe(wpis.path, nowaNazwa)
    }

    fun przenies(wpis: LibraryItem, docelowyFolder: String) = wTle {
        repo.przenies(wpis.path, docelowyFolder)
    }

    fun kopiuj(wpis: LibraryItem) = wTle {
        repo.kopiuj(wpis.path, wpis.parentPath)
    }

    fun doKosza(wpis: LibraryItem) = wTle {
        repo.doKosza(wpis.path)
    }

    fun zmienWygladFolderu(wpis: LibraryItem, colorId: String, iconId: String) = wTle {
        repo.zmienWygladFolderu(wpis.path, colorId, iconId)
    }

    fun zapamietajOtwarcie(wpis: LibraryItem) = wTle {
        repo.zapamietajOtwarcie(wpis.documentUri)
    }

    // Kosz

    fun odswiezKosz() = wTle {
        _kosz.value = repo.wypiszKosz()
    }

    fun przywroc(wpis: WpisKosza) = wTle {
        repo.przywrocZKosza(wpis.id)
        _kosz.value = repo.wypiszKosz()
    }

    fun usunTrwale(wpis: WpisKosza) = wTle {
        repo.usunTrwale(wpis.id)
        _kosz.value = repo.wypiszKosz()
    }

    fun oproznijKosz() = wTle {
        repo.oproznijKosz()
        _kosz.value = repo.wypiszKosz()
    }

    // Indeks

    fun przebudujIndeks() = wTle {
        _przebudowa.value = "Przeglądam bibliotekę..."
        repo.przebudujIndeks { zrobione, wszystkich ->
            _przebudowa.value = "Sprawdzam $zrobione z $wszystkich"
        }
        _przebudowa.value = null
    }

    private fun wTle(blok: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                blok()
            } catch (e: Exception) {
                _blad.value = e.message ?: "Nie udało się wykonać tej czynności."
                _przebudowa.value = null
            }
        }
    }

    private fun sciezkiNadrzedne(sciezka: String): Set<String> {
        if (sciezka.isEmpty()) return setOf("")
        val czesci = sciezka.split('/')
        val wynik = mutableSetOf("")
        var biezaca = ""
        for (czesc in czesci) {
            biezaca = if (biezaca.isEmpty()) czesc else "$biezaca/$czesc"
            wynik += biezaca
        }
        return wynik
    }

    class Fabryka(private val repo: RepozytoriumBiblioteki) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ModelBiblioteki(repo) as T
    }
}

/** Pomocniczy filtr: same foldery, potrzebne do drzewa po lewej stronie. */
private suspend fun RepozytoriumBiblioteki.folderySynchronicznie(sciezka: String): List<LibraryItem> =
    magazyn()?.wypisz(sciezka)?.filter { it.type == ItemType.FOLDER } ?: emptyList()
