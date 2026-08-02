package wojtoteka.ovh.kajet.editor.mapa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.MindEdge
import wojtoteka.ovh.kajet.core.model.MindMapContent
import wojtoteka.ovh.kajet.core.model.MindNode
import wojtoteka.ovh.kajet.core.model.NodeShape
import wojtoteka.ovh.kajet.editor.ModelNotatki
import wojtoteka.ovh.kajet.editor.ZmianaMapy
import wojtoteka.ovh.kajet.storage.MagazynUstawien
import wojtoteka.ovh.kajet.storage.RepozytoriumBiblioteki
import java.util.UUID

/**
 * Model mapy myśli.
 *
 * Mapa to zbiór węzłów i linii między nimi. Rodzic i dziecko wynikają
 * z kierunku linii, więc nie trzymamy tego dwa razy i nie da się
 * doprowadzić do sprzeczności między jednym a drugim.
 */
class ModelMapy(
    repo: RepozytoriumBiblioteki,
    ustawienia: MagazynUstawien,
    sciezka: String,
) : ModelNotatki(repo, ustawienia, sciezka) {

    private val _wybrany = MutableStateFlow<String?>(null)
    val wybrany: StateFlow<String?> = _wybrany.asStateFlow()

    private val _edytowany = MutableStateFlow<String?>(null)
    val edytowany: StateFlow<String?> = _edytowany.asStateFlow()

    private val _laczenie = MutableStateFlow(false)
    val laczenie: StateFlow<Boolean> = _laczenie.asStateFlow()

    private val _podpisRysikiem = MutableStateFlow<String?>(null)
    val podpisRysikiem: StateFlow<String?> = _podpisRysikiem.asStateFlow()

    private var przedPrzesunieciem: MindMapContent? = null

    val mapa: MindMapContent get() = dokument.value?.mindMap ?: MindMapContent()

    fun wybierz(id: String?) {
        if (_laczenie.value && id != null) {
            val od = _wybrany.value
            if (od != null && od != id) {
                polacz(od, id)
                _laczenie.value = false
                return
            }
        }
        _wybrany.value = id
        if (id == null) _edytowany.value = null
    }

    fun edytuj(id: String?) {
        _edytowany.value = id
        if (id != null) _wybrany.value = id
    }

    fun przelaczLaczenie() {
        _laczenie.value = !_laczenie.value
    }

    fun otworzPodpisRysikiem(id: String) {
        _podpisRysikiem.value = id
    }

    fun zamknijPodpisRysikiem() {
        _podpisRysikiem.value = null
    }

    // Węzły

    fun dodajWezel(x: Float, y: Float, tekst: String = ""): String {
        val id = UUID.randomUUID().toString()
        val nowy = MindNode(id = id, x = x, y = y, text = tekst)
        zmien(mapa.copy(nodes = mapa.nodes + nowy))
        _wybrany.value = id
        _edytowany.value = id
        return id
    }

    /** Nowy węzeł podczepiony pod wybrany, ustawiony po jego prawej stronie. */
    fun dodajDziecko(rodzicId: String): String? {
        val rodzic = mapa.nodes.firstOrNull { it.id == rodzicId } ?: return null
        val rodzenstwo = mapa.edges.count { it.fromId == rodzicId }
        val id = UUID.randomUUID().toString()
        val dziecko = MindNode(
            id = id,
            x = rodzic.x + rodzic.width + ODSTEP_POZIOMY,
            y = rodzic.y + rodzenstwo * (rodzic.height + ODSTEP_PIONOWY),
            colorId = rodzic.colorId,
        )
        zmien(
            mapa.copy(
                nodes = mapa.nodes + dziecko,
                edges = mapa.edges + MindEdge(UUID.randomUUID().toString(), rodzicId, id),
            ),
        )
        _wybrany.value = id
        _edytowany.value = id
        return id
    }

    fun zmienTekst(id: String, tekst: String) {
        zmienBezZapisuHistorii { stara ->
            stara.copy(nodes = stara.nodes.map { if (it.id == id) it.copy(text = tekst) else it })
        }
    }

    fun zmienKsztalt(id: String, ksztalt: NodeShape) {
        zmien(mapa.copy(nodes = mapa.nodes.map { if (it.id == id) it.copy(shape = ksztalt) else it }))
    }

    fun zmienKolor(id: String, colorId: String) {
        zmien(mapa.copy(nodes = mapa.nodes.map { if (it.id == id) it.copy(colorId = colorId) else it }))
    }

    fun zmienPodpisRysikiem(id: String, kreski: List<InkStroke>) {
        zmien(mapa.copy(nodes = mapa.nodes.map { if (it.id == id) it.copy(ink = kreski) else it }))
        _podpisRysikiem.value = null
    }

    fun przelaczZwiniecie(id: String) {
        zmien(
            mapa.copy(
                nodes = mapa.nodes.map { if (it.id == id) it.copy(collapsed = !it.collapsed) else it },
            ),
        )
    }

    /** Kasuje węzeł razem z liniami, które do niego prowadzą. Dzieci zostają. */
    fun usunWezel(id: String) {
        zmien(
            mapa.copy(
                nodes = mapa.nodes.filterNot { it.id == id },
                edges = mapa.edges.filterNot { it.fromId == id || it.toId == id },
            ),
        )
        if (_wybrany.value == id) _wybrany.value = null
        if (_edytowany.value == id) _edytowany.value = null
    }

    fun rozpocznijPrzesuwanie() {
        przedPrzesunieciem = mapa
    }

    fun przesunWezel(id: String, dx: Float, dy: Float) {
        zmienBezZapisuHistorii { stara ->
            stara.copy(
                nodes = stara.nodes.map {
                    if (it.id == id) it.copy(x = it.x + dx, y = it.y + dy) else it
                },
            )
        }
    }

    fun zakonczPrzesuwanie() {
        val przed = przedPrzesunieciem ?: return
        przedPrzesunieciem = null
        if (przed != mapa) historia.zapamietaj(ZmianaMapy(przed, mapa))
        odswiezPrzyciski()
    }

    fun zmienWielkosc(id: String, szerokosc: Float, wysokosc: Float, koniec: Boolean) {
        if (koniec) {
            zakonczPrzesuwanie()
            return
        }
        zmienBezZapisuHistorii { stara ->
            stara.copy(
                nodes = stara.nodes.map {
                    if (it.id == id) {
                        it.copy(
                            width = szerokosc.coerceIn(80f, 600f),
                            height = wysokosc.coerceIn(40f, 400f),
                        )
                    } else {
                        it
                    }
                },
            )
        }
    }

    // Linie

    fun polacz(odId: String, doId: String) {
        val juzJest = mapa.edges.any {
            (it.fromId == odId && it.toId == doId) || (it.fromId == doId && it.toId == odId)
        }
        if (juzJest) return
        zmien(mapa.copy(edges = mapa.edges + MindEdge(UUID.randomUUID().toString(), odId, doId)))
    }

    fun rozlacz(edgeId: String) {
        zmien(mapa.copy(edges = mapa.edges.filterNot { it.id == edgeId }))
    }

    // Widok

    fun zapamietajWidok(x: Float, y: Float, zoom: Float) {
        zmienBezZapisuHistorii { it.copy(viewX = x, viewY = y, zoom = zoom) }
    }

    /**
     * Automatyczne rozłożenie gałęzi. Uruchamiane przyciskiem, nigdy samo,
     * bo ręczne ustawienie węzłów też jest informacją.
     */
    fun rozlozGalezie() {
        val ulozona = UkladMapy.rozloz(mapa)
        if (ulozona != mapa) zmien(ulozona)
    }

    private fun zmien(nowa: MindMapContent) {
        val stara = mapa
        wykonaj(ZmianaMapy(stara, nowa))
    }

    private fun zmienBezZapisuHistorii(przeksztalcenie: (MindMapContent) -> MindMapContent) {
        zmienBezHistorii { dokument ->
            dokument.copy(mindMap = przeksztalcenie(dokument.mindMap ?: MindMapContent()))
        }
    }

    companion object {
        const val ODSTEP_POZIOMY = 70f
        const val ODSTEP_PIONOWY = 24f
    }

    class Fabryka(
        private val repo: RepozytoriumBiblioteki,
        private val ustawienia: MagazynUstawien,
        private val sciezka: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ModelMapy(repo, ustawienia, sciezka) as T
    }
}
