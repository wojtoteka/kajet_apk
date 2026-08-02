package wojtoteka.ovh.kajet.editor.odreczny

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.NotePage
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode
import wojtoteka.ovh.kajet.core.model.TextBoxElement
import wojtoteka.ovh.kajet.editor.ModelNotatki
import wojtoteka.ovh.kajet.editor.ZmianaKresek
import wojtoteka.ovh.kajet.editor.ZmianaPol
import wojtoteka.ovh.kajet.editor.ZmianaStron
import wojtoteka.ovh.kajet.editor.strona
import wojtoteka.ovh.kajet.editor.zeStrona
import wojtoteka.ovh.kajet.ink.Kresy
import wojtoteka.ovh.kajet.ink.Narzedzie
import wojtoteka.ovh.kajet.ink.RozpoznawaniePisma
import wojtoteka.ovh.kajet.ink.StanRozpoznawania
import wojtoteka.ovh.kajet.ink.UstawieniaPisaka
import wojtoteka.ovh.kajet.storage.MagazynUstawien
import wojtoteka.ovh.kajet.storage.RepozytoriumBiblioteki
import java.util.UUID

/**
 * Model edytora pisma odręcznego.
 *
 * Zbiera to, co dzieje się na płótnie, i zamienia na zmiany w dokumencie.
 * Gumka, lasso i przesuwanie zaznaczenia dopisują po jednej pozycji do historii
 * na każde pociągnięcie, a nie na każdy punkt, żeby cofanie działało po ludzku.
 */
class ModelOdrecznego(
    repo: RepozytoriumBiblioteki,
    ustawienia: MagazynUstawien,
    sciezka: String,
    kolorAtramentu: Int,
    kolorZakreslacza: Int,
) : ModelNotatki(repo, ustawienia, sciezka) {

    private val _narzedzie = MutableStateFlow(Narzedzie.PIORO)
    val narzedzie: StateFlow<Narzedzie> = _narzedzie.asStateFlow()

    private val _pisak = MutableStateFlow(
        UstawieniaPisaka(kolorPiora = kolorAtramentu, kolorZakreslacza = kolorZakreslacza),
    )
    val pisak: StateFlow<UstawieniaPisaka> = _pisak.asStateFlow()

    private val _zaznaczone = MutableStateFlow<List<InkStroke>>(emptyList())
    val zaznaczone: StateFlow<List<InkStroke>> = _zaznaczone.asStateFlow()

    private val _stronaZaznaczenia = MutableStateFlow(-1)
    val stronaZaznaczenia: StateFlow<Int> = _stronaZaznaczenia.asStateFlow()

    /** Pole tekstowe, które właśnie poprawiamy. */
    private val _edytowanePole = MutableStateFlow<String?>(null)
    val edytowanePole: StateFlow<String?> = _edytowanePole.asStateFlow()

    // Zbieranie jednego pociągnięcia gumki albo jednego przesunięcia zaznaczenia
    private val rozpoznawanie = RozpoznawaniePisma()

    private val _stanRozpoznawania = MutableStateFlow<StanRozpoznawania>(StanRozpoznawania.Gotowy)
    val stanRozpoznawania: StateFlow<StanRozpoznawania> = _stanRozpoznawania.asStateFlow()

    private val _propozycjeTekstu = MutableStateFlow<List<String>>(emptyList())
    val propozycjeTekstu: StateFlow<List<String>> = _propozycjeTekstu.asStateFlow()

    private var zbieraneUsuniete = mutableListOf<Pair<Int, InkStroke>>()
    private var zbieraneDodane = mutableListOf<InkStroke>()
    private var zbieranaStrona = -1
    private var przesuniecieZbiorczeX = 0f
    private var przesuniecieZbiorczeY = 0f

    fun wybierzNarzedzie(nowe: Narzedzie) {
        _narzedzie.value = nowe
        if (nowe != Narzedzie.LASSO) odznacz()
    }

    fun ustawKolorPiora(kolor: Int) {
        _pisak.value = _pisak.value.copy(kolorPiora = kolor)
    }

    fun ustawGruboscPiora(grubosc: Float) {
        _pisak.value = _pisak.value.copy(gruboscPiora = grubosc)
    }

    fun ustawKolorZakreslacza(kolor: Int) {
        _pisak.value = _pisak.value.copy(kolorZakreslacza = kolor)
    }

    fun ustawGruboscZakreslacza(grubosc: Float) {
        _pisak.value = _pisak.value.copy(gruboscZakreslacza = grubosc)
    }

    fun ustawPromienGumki(promien: Float) {
        _pisak.value = _pisak.value.copy(promienGumki = promien)
    }

    // Kreski

    fun dopiszKreske(strona: Int, kreska: InkStroke) {
        wykonaj(ZmianaKresek(strona = strona, dodane = listOf(kreska)))
        dolozStroneJesliTrzeba(strona)
    }

    /**
     * Kiedy piszesz przy dolnej krawędzi ostatniej strony, dokładamy następną.
     * Przy nieskończonej stronie rośnie ona w dół, przy A4 pojawia się nowa kartka.
     */
    private fun dolozStroneJesliTrzeba(strona: Int) {
        val dokument = dokument.value ?: return
        val pismo = dokument.handwriting ?: return
        if (strona != pismo.pages.lastIndex) return
        val kartka = pismo.pages[strona]
        val najnizszy = kartka.strokes.maxOfOrNull { it.bounds().bottom } ?: return
        if (najnizszy < kartka.height - PROG_DOLOZENIA) return

        val nowe = pismo.pages.toMutableList()
        if (pismo.pageMode == PageMode.WSTEGA) {
            nowe[strona] = kartka.copy(height = kartka.height + NotePage.PRZYROST_WSTEGI)
        } else {
            nowe += NotePage(
                id = UUID.randomUUID().toString(),
                width = kartka.width,
                height = kartka.height,
            )
        }
        wykonaj(ZmianaStron(przed = pismo.pages, po = nowe))
    }

    fun dolozStrone() {
        val pismo = dokument.value?.handwriting ?: return
        val wzor = pismo.pages.lastOrNull()
        val nowe = pismo.pages + NotePage(
            id = UUID.randomUUID().toString(),
            width = wzor?.width ?: NotePage.SZEROKOSC_A4,
            height = wzor?.height ?: NotePage.WYSOKOSC_A4,
        )
        wykonaj(ZmianaStron(przed = pismo.pages, po = nowe))
    }

    fun usunStrone(indeks: Int) {
        val pismo = dokument.value?.handwriting ?: return
        if (pismo.pages.size <= 1) return
        val nowe = pismo.pages.toMutableList().also { it.removeAt(indeks) }
        wykonaj(ZmianaStron(przed = pismo.pages, po = nowe))
    }

    fun zmienTlo(tlo: PageBackground) {
        val dokument = dokument.value ?: return
        val pismo = dokument.handwriting ?: return
        zmienBezHistorii { it.copy(handwriting = pismo.copy(background = tlo)) }
    }

    // Gumka

    fun gumka(strona: Int, x: Float, y: Float, promien: Float, calaKreska: Boolean) {
        val kartka = dokument.value?.strona(strona) ?: return
        if (zbieranaStrona != strona) {
            zakonczZbieranie()
            zbieranaStrona = strona
        }

        val usuniete = mutableListOf<Pair<Int, InkStroke>>()
        val dodane = mutableListOf<InkStroke>()

        kartka.strokes.forEachIndexed { indeks, kreska ->
            if (!Kresy.dotykaKola(kreska, x, y, promien)) return@forEachIndexed
            usuniete += indeks to kreska
            if (!calaKreska) {
                dodane += Kresy.wytnijFragment(kreska, x, y, promien)
            }
        }
        if (usuniete.isEmpty()) return

        // Zmieniamy dokument od razu, żeby gumka reagowała pod palcem,
        // ale do historii wpisujemy dopiero całe pociągnięcie.
        zmienBezHistorii { ZmianaKresek(strona, usuniete, dodane).zastosuj(it) }
        zbieraneUsuniete += usuniete
        zbieraneDodane.removeAll { kreska -> usuniete.any { it.second.id == kreska.id } }
        zbieraneDodane += dodane
    }

    fun koniecGumki() = zakonczZbieranie()

    private fun zakonczZbieranie() {
        if (zbieranaStrona >= 0 && zbieraneUsuniete.isNotEmpty()) {
            historia.zapamietaj(
                ZmianaKresek(zbieranaStrona, zbieraneUsuniete.toList(), zbieraneDodane.toList()),
            )
            odswiezStanPrzyciskow()
        }
        zbieraneUsuniete = mutableListOf()
        zbieraneDodane = mutableListOf()
        zbieranaStrona = -1
    }

    // Lasso

    fun zaznaczLassem(strona: Int, wielokat: List<Float>) {
        val kartka = dokument.value?.strona(strona) ?: return
        val trafione = kartka.strokes.filter { Kresy.wLassie(it, wielokat) }
        _zaznaczone.value = trafione
        _stronaZaznaczenia.value = if (trafione.isEmpty()) -1 else strona
    }

    fun odznacz() {
        _zaznaczone.value = emptyList()
        _stronaZaznaczenia.value = -1
    }

    fun przesunZaznaczenie(dx: Float, dy: Float, koniec: Boolean) {
        val strona = _stronaZaznaczenia.value
        if (strona < 0) return

        if (!koniec) {
            przesuniecieZbiorczeX += dx
            przesuniecieZbiorczeY += dy
            val przesuwane = _zaznaczone.value.map { it.translated(dx, dy) }
            val stareId = _zaznaczone.value.map { it.id }.toSet()
            zmienBezHistorii { dokument ->
                val kartka = dokument.strona(strona) ?: return@zmienBezHistorii dokument
                val nowe = kartka.strokes.map { kreska ->
                    if (kreska.id in stareId) kreska.translated(dx, dy) else kreska
                }
                dokument.zeStrona(strona) { it.copy(strokes = nowe) }
            }
            _zaznaczone.value = przesuwane
            return
        }

        if (przesuniecieZbiorczeX != 0f || przesuniecieZbiorczeY != 0f) {
            val kartka = dokument.value?.strona(strona)
            if (kartka != null) {
                val idZaznaczone = _zaznaczone.value.map { it.id }.toSet()
                val poZmianie = kartka.strokes.filter { it.id in idZaznaczone }
                val przed = poZmianie.map { it.translated(-przesuniecieZbiorczeX, -przesuniecieZbiorczeY) }
                val miejsca = przed.map { kreska ->
                    kartka.strokes.indexOfFirst { it.id == kreska.id } to kreska
                }
                historia.zapamietaj(ZmianaKresek(strona, miejsca, poZmianie))
                odswiezStanPrzyciskow()
            }
        }
        przesuniecieZbiorczeX = 0f
        przesuniecieZbiorczeY = 0f
    }

    fun skasujZaznaczenie() {
        val strona = _stronaZaznaczenia.value
        if (strona < 0) return
        val kartka = dokument.value?.strona(strona) ?: return
        val idZaznaczone = _zaznaczone.value.map { it.id }.toSet()
        val usuniete = kartka.strokes.withIndex()
            .filter { it.value.id in idZaznaczone }
            .map { it.index to it.value }
        if (usuniete.isEmpty()) return
        wykonaj(ZmianaKresek(strona, usuniete = usuniete))
        odznacz()
    }

    // Pola tekstowe

    fun dodajPole(strona: Int, x: Float, y: Float, kolor: Int) {
        val kartka = dokument.value?.strona(strona) ?: return
        val pole = TextBoxElement(
            id = UUID.randomUUID().toString(),
            x = x,
            y = y,
            width = 220f,
            height = 44f,
            text = "",
            color = kolor,
        )
        wykonaj(ZmianaPol(strona, kartka.texts, kartka.texts + pole))
        _edytowanePole.value = pole.id
    }

    fun zmienPole(strona: Int, pole: TextBoxElement, doHistorii: Boolean) {
        val kartka = dokument.value?.strona(strona) ?: return
        val nowe = kartka.texts.map { if (it.id == pole.id) pole else it }
        if (doHistorii) {
            wykonaj(ZmianaPol(strona, kartka.texts, nowe))
        } else {
            zmienBezHistorii { dokument -> dokument.zeStrona(strona) { k -> k.copy(texts = nowe) } }
        }
    }

    fun usunPole(strona: Int, id: String) {
        val kartka = dokument.value?.strona(strona) ?: return
        wykonaj(ZmianaPol(strona, kartka.texts, kartka.texts.filterNot { it.id == id }))
        if (_edytowanePole.value == id) _edytowanePole.value = null
    }

    fun edytujPole(id: String?) {
        _edytowanePole.value = id
    }

    // Zamiana pisma na tekst

    /**
     * Rozpoznaje zaznaczone pismo. Przy pierwszym użyciu pobiera model polski,
     * potem działa bez internetu.
     */
    fun rozpoznajZaznaczone() {
        val kreski = _zaznaczone.value
        if (kreski.isEmpty()) return
        viewModelScope.launch {
            try {
                if (!rozpoznawanie.modelPobrany()) {
                    _stanRozpoznawania.value = StanRozpoznawania.Pobieranie(
                        "Pobieram model pisma po polsku. Robi się to raz, potem działa bez internetu.",
                    )
                    rozpoznawanie.pobierzModel()
                }
                _stanRozpoznawania.value = StanRozpoznawania.Pobieranie("Odczytuję pismo...")

                val kartka = dokument.value?.strona(_stronaZaznaczenia.value)
                val propozycje = rozpoznawanie.rozpoznaj(
                    kreski = kreski,
                    szerokoscObszaru = kartka?.width ?: 595f,
                    wysokoscObszaru = kartka?.height ?: 842f,
                )
                _stanRozpoznawania.value = StanRozpoznawania.Gotowy
                if (propozycje.isEmpty()) {
                    _stanRozpoznawania.value = StanRozpoznawania.Blad(
                        "Nie odczytałem tego pisma. Zaznacz mniejszy fragment i spróbuj jeszcze raz.",
                    )
                } else {
                    _propozycjeTekstu.value = propozycje.take(5)
                }
            } catch (e: Exception) {
                _stanRozpoznawania.value = StanRozpoznawania.Blad(
                    e.message ?: "Nie udało się odczytać pisma.",
                )
            }
        }
    }

    /**
     * Zapisuje odczytany tekst obok pisma. Kresek nie kasujemy,
     * bo notatka odręczna ma zostać odręczna, a tekst służy do szukania.
     */
    fun zatwierdzRozpoznanie(tekst: String) {
        val strona = _stronaZaznaczenia.value
        val kreski = _zaznaczone.value
        _propozycjeTekstu.value = emptyList()
        if (strona < 0 || kreski.isEmpty()) return

        val obszar = wojtoteka.ovh.kajet.ink.Kresy.obszar(kreski) ?: return
        zmienBezHistorii { dokument ->
            dokument.zeStrona(strona) { kartka ->
                kartka.copy(
                    recognized = kartka.recognized + wojtoteka.ovh.kajet.core.model.RecognizedText(
                        id = UUID.randomUUID().toString(),
                        text = tekst,
                        x = obszar.left,
                        y = obszar.top,
                        width = obszar.width,
                        height = obszar.height,
                        strokeIds = kreski.map { it.id },
                    ),
                )
            }
        }
        odznacz()
    }

    fun odrzucPropozycje() {
        _propozycjeTekstu.value = emptyList()
    }

    fun schowajStanRozpoznawania() {
        _stanRozpoznawania.value = StanRozpoznawania.Gotowy
    }

    private fun odswiezStanPrzyciskow() {
        // Historia zmieniła się poza metodą wykonaj, więc odświeżamy przyciski ręcznie.
        odswiezPrzyciski()
    }

    companion object {
        /** Ile punktów przed dolną krawędzią dokładamy następną stronę. */
        const val PROG_DOLOZENIA = 90f
    }

    class Fabryka(
        private val repo: RepozytoriumBiblioteki,
        private val ustawienia: MagazynUstawien,
        private val sciezka: String,
        private val kolorAtramentu: Int,
        private val kolorZakreslacza: Int,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ModelOdrecznego(repo, ustawienia, sciezka, kolorAtramentu, kolorZakreslacza) as T
    }
}
