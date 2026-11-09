package wojtoteka.ovh.kajet.ink

import wojtoteka.ovh.kajet.core.model.InkStroke

/**
 * Gotowe kreski silnika, po jednej na kreskę z dokumentu.
 *
 * Samo id nie wystarcza: kreska przesunięta zaznaczeniem albo cofnięta zachowuje
 * swoje id, a ma inne punkty. Kartka rysowała wtedy starą kreskę w starym
 * miejscu, chociaż w dokumencie leżała już gdzie indziej - ramka odjeżdżała
 * sama, a gumka nie trafiała w to, co widać.
 */
internal class MeshCache<M : Any> {

    private class Entry<M>(val source: InkStroke, val mesh: M)

    private val entries = HashMap<String, Entry<M>>()

    /** Gotowa kreska dla [stroke], zbudowana od nowa przez [build], gdy się zmieniła. */
    fun meshFor(stroke: InkStroke, build: (InkStroke) -> M?): M? {
        val cached = entries[stroke.id]
        // Porównanie tożsamości załatwia prawie wszystko bez przeglądania punktów.
        if (cached != null && (cached.source === stroke || cached.source == stroke)) return cached.mesh
        val built = build(stroke)
        if (built == null) entries.remove(stroke.id) else entries[stroke.id] = Entry(stroke, built)
        return built
    }

    /** Zapomina kreski spoza [live]. */
    fun retain(live: Set<String>) {
        if (entries.size > live.size) entries.keys.retainAll(live)
    }
}
