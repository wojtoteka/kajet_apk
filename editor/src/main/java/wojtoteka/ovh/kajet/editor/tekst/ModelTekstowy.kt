package wojtoteka.ovh.kajet.editor.tekst

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import wojtoteka.ovh.kajet.core.model.DrawingSource
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.InlineDrawing
import wojtoteka.ovh.kajet.core.model.TextContent
import wojtoteka.ovh.kajet.editor.ModelNotatki
import wojtoteka.ovh.kajet.ink.RysunekDoObrazu
import wojtoteka.ovh.kajet.storage.MagazynUstawien
import wojtoteka.ovh.kajet.storage.RepozytoriumBiblioteki

/**
 * Model notatki tekstowej.
 *
 * Pod spodem leży zwykły Markdown, dokładnie taki, jaki wyjdzie przy eksporcie.
 * Zdjęcia i rysunki są w nim zapisane jako obrazki wskazujące na katalog assets
 * wewnątrz notatki, więc plik da się otworzyć w dowolnym innym programie.
 */
class ModelTekstowy(
    private val repo: RepozytoriumBiblioteki,
    ustawienia: MagazynUstawien,
    sciezka: String,
) : ModelNotatki(repo, ustawienia, sciezka) {

    private val _podglad = MutableStateFlow(true)
    val podglad: StateFlow<Boolean> = _podglad.asStateFlow()

    private val _rysowanie = MutableStateFlow(false)
    val rysowanie: StateFlow<Boolean> = _rysowanie.asStateFlow()

    private val _zajety = MutableStateFlow<String?>(null)
    val zajety: StateFlow<String?> = _zajety.asStateFlow()

    val markdown: String get() = dokument.value?.text?.markdown.orEmpty()

    fun przelaczPodglad() {
        _podglad.value = !_podglad.value
    }

    fun otworzRysowanie() {
        _rysowanie.value = true
    }

    fun zamknijRysowanie() {
        _rysowanie.value = false
    }

    fun zmienTresc(nowa: String) {
        zmienBezHistorii { dokument ->
            val tresc = dokument.text ?: TextContent()
            dokument.copy(text = tresc.copy(markdown = nowa))
        }
    }

    fun przelaczZadanie(numerWiersza: Int) {
        zmienTresc(Markdown.przelaczZadanie(markdown, numerWiersza))
    }

    /** Wstawia tekst w miejscu kursora i zwraca nowe położenie kursora. */
    fun wstaw(fragment: String, poczatek: Int, koniec: Int): Int {
        val tresc = markdown
        val od = poczatek.coerceIn(0, tresc.length)
        val doIndeksu = koniec.coerceIn(od, tresc.length)
        zmienTresc(tresc.substring(0, od) + fragment + tresc.substring(doIndeksu))
        return od + fragment.length
    }

    /** Otacza zaznaczony tekst znacznikiem, na przykład gwiazdkami pogrubienia. */
    fun otocz(znacznik: String, poczatek: Int, koniec: Int): IntRange {
        val tresc = markdown
        val od = poczatek.coerceIn(0, tresc.length)
        val doIndeksu = koniec.coerceIn(od, tresc.length)
        val srodek = tresc.substring(od, doIndeksu).ifEmpty { "tekst" }
        val nowy = tresc.substring(0, od) + znacznik + srodek + znacznik + tresc.substring(doIndeksu)
        zmienTresc(nowy)
        return (od + znacznik.length)..(od + znacznik.length + srodek.length)
    }

    /** Dokleja znacznik na początku wiersza, w którym stoi kursor. */
    fun naPoczatkuWiersza(znacznik: String, pozycja: Int): Int {
        val tresc = markdown
        val kursor = pozycja.coerceIn(0, tresc.length)
        val poczatekWiersza = tresc.lastIndexOf('\n', (kursor - 1).coerceAtLeast(0)).let {
            if (it < 0) 0 else it + 1
        }
        val wiersz = tresc.substring(poczatekWiersza, tresc.indexOf('\n', poczatekWiersza).let {
            if (it < 0) tresc.length else it
        })

        // Drugie naciśnięcie tego samego przycisku zdejmuje znacznik.
        return if (wiersz.startsWith(znacznik)) {
            zmienTresc(tresc.removeRange(poczatekWiersza, poczatekWiersza + znacznik.length))
            (kursor - znacznik.length).coerceAtLeast(poczatekWiersza)
        } else {
            zmienTresc(tresc.substring(0, poczatekWiersza) + znacznik + tresc.substring(poczatekWiersza))
            kursor + znacznik.length
        }
    }

    // Zdjęcia i rysunki

    fun wstawZdjecie(dane: ByteArray, rozszerzenie: String, pozycja: Int) {
        viewModelScope.launch {
            _zajety.value = "Zapisuję zdjęcie"
            try {
                val nazwa = repo.zapiszZalacznik(
                    sciezkaNotatki = sciezka,
                    nazwa = "zdjecie.$rozszerzenie",
                    dane = dane,
                    mime = if (rozszerzenie == "png") "image/png" else "image/jpeg",
                )
                wstaw("\n![zdjęcie](assets/$nazwa)\n", pozycja, pozycja)
            } catch (e: Exception) {
                ustawBlad(e.message ?: "Nie udało się zapisać zdjęcia w notatce.")
            } finally {
                _zajety.value = null
            }
        }
    }

    /**
     * Zapisuje rysunek dwa razy: jako obrazek do podglądu i jako kreski,
     * żeby dało się go później poprawić.
     */
    fun wstawRysunek(kreski: List<InkStroke>, szerokosc: Float, wysokosc: Float, pozycja: Int) {
        if (kreski.isEmpty()) {
            _rysowanie.value = false
            return
        }
        viewModelScope.launch {
            _zajety.value = "Zapisuję rysunek"
            try {
                val png = RysunekDoObrazu.png(kreski, szerokosc, wysokosc)
                val nazwaObrazka = repo.zapiszZalacznik(sciezka, "rysunek.png", png, "image/png")
                val nazwaZrodla = nazwaObrazka.removeSuffix(".png") + ".strokes.json"

                val magazyn = repo.magazyn()
                magazyn?.zapiszRysunekWTekscie(
                    sciezkaNotatki = sciezka,
                    nazwa = nazwaZrodla,
                    rysunek = DrawingSource(width = szerokosc, height = wysokosc, strokes = kreski),
                )

                zmienBezHistorii { dokument ->
                    val tresc = dokument.text ?: TextContent()
                    dokument.copy(
                        text = tresc.copy(
                            drawings = tresc.drawings + InlineDrawing(
                                asset = nazwaObrazka,
                                source = nazwaZrodla,
                                width = szerokosc,
                                height = wysokosc,
                            ),
                        ),
                    )
                }
                wstaw("\n![rysunek](assets/$nazwaObrazka)\n", pozycja, pozycja)
            } catch (e: Exception) {
                ustawBlad(e.message ?: "Nie udało się zapisać rysunku w notatce.")
            } finally {
                _zajety.value = null
                _rysowanie.value = false
            }
        }
    }

    suspend fun zalacznik(nazwa: String): ByteArray? = repo.czytajZalacznik(sciezka, nazwa)

    class Fabryka(
        private val repo: RepozytoriumBiblioteki,
        private val ustawienia: MagazynUstawien,
        private val sciezka: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ModelTekstowy(repo, ustawienia, sciezka) as T
    }
}
