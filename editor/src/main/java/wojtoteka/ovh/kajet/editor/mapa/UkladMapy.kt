package wojtoteka.ovh.kajet.editor.mapa

import wojtoteka.ovh.kajet.core.model.MindMapContent
import wojtoteka.ovh.kajet.core.model.MindNode

/**
 * Automatyczne rozłożenie gałęzi mapy myśli.
 *
 * Układ jest drzewiasty i idzie w prawo: korzeń po lewej, dzieci obok niego,
 * każde poddrzewo zajmuje tyle wysokości, ile potrzebuje. To jest opcja,
 * a nie przymus, bo ręcznie poustawiane węzły też coś znaczą.
 *
 * Węzły, do których nie prowadzi żadna linia, traktujemy jak osobne korzenie
 * i układamy je jeden pod drugim. Dzięki temu mapa z kilkoma niezależnymi
 * gałęziami nie robi się bałaganem.
 */
object UkladMapy {

    const val ODSTEP_POZIOMY = 90f
    const val ODSTEP_PIONOWY = 26f
    const val ODSTEP_KORZENI = 60f

    fun rozloz(mapa: MindMapContent): MindMapContent {
        if (mapa.nodes.isEmpty()) return mapa

        val poId = mapa.nodes.associateBy { it.id }
        val dzieci = HashMap<String, MutableList<String>>()
        val maRodzica = HashSet<String>()

        for (linia in mapa.edges) {
            if (linia.fromId !in poId || linia.toId !in poId) continue
            if (linia.toId in maRodzica) continue
            dzieci.getOrPut(linia.fromId) { mutableListOf() } += linia.toId
            maRodzica += linia.toId
        }

        val korzenie = mapa.nodes.filter { it.id !in maRodzica }.map { it.id }
        val nowePolozenia = HashMap<String, Pair<Float, Float>>()
        val odwiedzone = HashSet<String>()

        var gora = 0f
        for (korzen in korzenie) {
            val wysokosc = ulozGalaz(
                id = korzen,
                lewo = 0f,
                gora = gora,
                poId = poId,
                dzieci = dzieci,
                odwiedzone = odwiedzone,
                wynik = nowePolozenia,
            )
            gora += wysokosc + ODSTEP_KORZENI
        }

        return mapa.copy(
            nodes = mapa.nodes.map { wezel ->
                val polozenie = nowePolozenia[wezel.id] ?: return@map wezel
                wezel.copy(x = polozenie.first, y = polozenie.second)
            },
        )
    }

    /** Układa poddrzewo i zwraca jego wysokość. */
    private fun ulozGalaz(
        id: String,
        lewo: Float,
        gora: Float,
        poId: Map<String, MindNode>,
        dzieci: Map<String, List<String>>,
        odwiedzone: MutableSet<String>,
        wynik: MutableMap<String, Pair<Float, Float>>,
    ): Float {
        val wezel = poId[id] ?: return 0f
        if (!odwiedzone.add(id)) return 0f

        val potomkowie = if (wezel.collapsed) emptyList() else dzieci[id].orEmpty()
        if (potomkowie.isEmpty()) {
            wynik[id] = lewo to gora
            return wezel.height
        }

        var wysokoscPoddrzewa = 0f
        val lewoDzieci = lewo + wezel.width + ODSTEP_POZIOMY
        for (dziecko in potomkowie) {
            val wysokosc = ulozGalaz(
                id = dziecko,
                lewo = lewoDzieci,
                gora = gora + wysokoscPoddrzewa,
                poId = poId,
                dzieci = dzieci,
                odwiedzone = odwiedzone,
                wynik = wynik,
            )
            if (wysokosc > 0f) wysokoscPoddrzewa += wysokosc + ODSTEP_PIONOWY
        }
        if (wysokoscPoddrzewa > 0f) wysokoscPoddrzewa -= ODSTEP_PIONOWY

        // Rodzic staje na wysokości środka swoich dzieci.
        val srodek = gora + (wysokoscPoddrzewa - wezel.height) / 2f
        wynik[id] = lewo to srodek
        return maxOf(wysokoscPoddrzewa, wezel.height)
    }

    /** Węzły widoczne przy zwiniętych gałęziach. */
    fun widoczne(mapa: MindMapContent): Set<String> {
        val poId = mapa.nodes.associateBy { it.id }
        val dzieci = HashMap<String, MutableList<String>>()
        val maRodzica = HashSet<String>()
        for (linia in mapa.edges) {
            if (linia.fromId !in poId || linia.toId !in poId) continue
            if (linia.toId in maRodzica) continue
            dzieci.getOrPut(linia.fromId) { mutableListOf() } += linia.toId
            maRodzica += linia.toId
        }

        val widoczne = HashSet<String>()
        fun zejdz(id: String) {
            if (!widoczne.add(id)) return
            val wezel = poId[id] ?: return
            if (wezel.collapsed) return
            dzieci[id].orEmpty().forEach { zejdz(it) }
        }
        mapa.nodes.filter { it.id !in maRodzica }.forEach { zejdz(it.id) }

        // Węzły w pętli albo osierocone też pokazujemy, żeby nie znikły z oczu.
        mapa.nodes.forEach { if (it.id !in widoczne && !schowanyPodZwinietym(it.id, poId, dzieci, maRodzica)) widoczne += it.id }
        return widoczne
    }

    private fun schowanyPodZwinietym(
        id: String,
        poId: Map<String, MindNode>,
        dzieci: Map<String, List<String>>,
        maRodzica: Set<String>,
    ): Boolean {
        if (id !in maRodzica) return false
        val rodzic = dzieci.entries.firstOrNull { id in it.value }?.key ?: return false
        val wezelRodzica = poId[rodzic] ?: return false
        return wezelRodzica.collapsed || schowanyPodZwinietym(rodzic, poId, dzieci, maRodzica)
    }

    /** Czy węzeł ma dzieci, czyli czy warto pokazywać przy nim strzałkę zwijania. */
    fun maDzieci(mapa: MindMapContent, id: String): Boolean = mapa.edges.any { it.fromId == id }
}
