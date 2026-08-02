package wojtoteka.ovh.kajet.editor

import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.MindMapContent
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.NotePage
import wojtoteka.ovh.kajet.core.model.TextBoxElement

/**
 * Jedna zmiana w notatce, którą da się cofnąć i wykonać ponownie.
 *
 * Trzymamy różnicę, a nie kopię całej notatki. Dzięki temu pięćdziesiąt kroków
 * wstecz zajmuje tyle, co kilka kresek, i cofanie działa tak samo szybko
 * przy pustej stronie i przy stronie zapisanej do końca.
 */
sealed interface Zmiana {
    fun zastosuj(dokument: NoteDocument): NoteDocument
    fun cofnij(dokument: NoteDocument): NoteDocument
}

/**
 * Dodanie, skasowanie albo podmiana kresek na jednej stronie.
 *
 * Skasowane kreski pamiętamy razem z ich miejscem w kolejności,
 * bo kolejność decyduje o tym, co jest narysowane na wierzchu.
 */
data class ZmianaKresek(
    val strona: Int,
    val usuniete: List<Pair<Int, InkStroke>> = emptyList(),
    val dodane: List<InkStroke> = emptyList(),
) : Zmiana {

    override fun zastosuj(dokument: NoteDocument): NoteDocument =
        dokument.zeStrona(strona) { kartka ->
            val doUsuniecia = usuniete.map { it.second.id }.toSet()
            val zostajace = if (doUsuniecia.isEmpty()) {
                kartka.strokes
            } else {
                kartka.strokes.filterNot { it.id in doUsuniecia }
            }
            kartka.copy(strokes = zostajace + dodane)
        }

    override fun cofnij(dokument: NoteDocument): NoteDocument =
        dokument.zeStrona(strona) { kartka ->
            val doUsuniecia = dodane.map { it.id }.toSet()
            val bezDodanych = if (doUsuniecia.isEmpty()) {
                kartka.strokes.toMutableList()
            } else {
                kartka.strokes.filterNot { it.id in doUsuniecia }.toMutableList()
            }
            for ((miejsce, kreska) in usuniete.sortedBy { it.first }) {
                bezDodanych.add(miejsce.coerceIn(0, bezDodanych.size), kreska)
            }
            kartka.copy(strokes = bezDodanych)
        }
}

/** Zmiana pól tekstowych na stronie: dodanie, skasowanie, przesunięcie albo poprawka treści. */
data class ZmianaPol(
    val strona: Int,
    val przed: List<TextBoxElement>,
    val po: List<TextBoxElement>,
) : Zmiana {
    override fun zastosuj(dokument: NoteDocument) =
        dokument.zeStrona(strona) { it.copy(texts = po) }

    override fun cofnij(dokument: NoteDocument) =
        dokument.zeStrona(strona) { it.copy(texts = przed) }
}

/** Zmiana układu stron: dołożenie kartki, skasowanie kartki, zmiana tła. */
data class ZmianaStron(
    val przed: List<NotePage>,
    val po: List<NotePage>,
) : Zmiana {
    override fun zastosuj(dokument: NoteDocument) = dokument.zeStronami(po)
    override fun cofnij(dokument: NoteDocument) = dokument.zeStronami(przed)
}

/** Zmiana w mapie myśli. Mapa jest niewielka, więc pamiętamy ją w całości. */
data class ZmianaMapy(
    val przed: MindMapContent,
    val po: MindMapContent,
) : Zmiana {
    override fun zastosuj(dokument: NoteDocument) = dokument.copy(mindMap = po)
    override fun cofnij(dokument: NoteDocument) = dokument.copy(mindMap = przed)
}

/**
 * Stos cofania. Pamięta co najmniej pięćdziesiąt kroków, tak jak w zleceniu.
 * Nowa zmiana kasuje to, co było do przodu, bo historia rozgałęziona
 * tylko myli człowieka.
 */
class HistoriaZmian(private val ileKrokow: Int = 120) {

    private val wstecz = ArrayDeque<Zmiana>()
    private val wprzod = ArrayDeque<Zmiana>()

    val mozeCofnac: Boolean get() = wstecz.isNotEmpty()
    val mozePonowic: Boolean get() = wprzod.isNotEmpty()

    fun zapamietaj(zmiana: Zmiana) {
        wstecz.addLast(zmiana)
        while (wstecz.size > ileKrokow) wstecz.removeFirst()
        wprzod.clear()
    }

    fun cofnij(dokument: NoteDocument): NoteDocument? {
        val zmiana = wstecz.removeLastOrNull() ?: return null
        wprzod.addLast(zmiana)
        return zmiana.cofnij(dokument)
    }

    fun ponow(dokument: NoteDocument): NoteDocument? {
        val zmiana = wprzod.removeLastOrNull() ?: return null
        wstecz.addLast(zmiana)
        return zmiana.zastosuj(dokument)
    }

    fun wyczysc() {
        wstecz.clear()
        wprzod.clear()
    }
}

// Pomocnicze przekształcenia dokumentu

fun NoteDocument.zeStrona(indeks: Int, zmiana: (NotePage) -> NotePage): NoteDocument {
    val pismo = handwriting ?: return this
    if (indeks !in pismo.pages.indices) return this
    val nowe = pismo.pages.toMutableList()
    nowe[indeks] = zmiana(nowe[indeks])
    return copy(handwriting = pismo.copy(pages = nowe))
}

fun NoteDocument.zeStronami(strony: List<NotePage>): NoteDocument {
    val pismo = handwriting ?: return this
    return copy(handwriting = pismo.copy(pages = strony))
}

fun NoteDocument.strona(indeks: Int): NotePage? = handwriting?.pages?.getOrNull(indeks)
