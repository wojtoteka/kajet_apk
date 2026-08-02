package wojtoteka.ovh.kajet.storage.indeks

import wojtoteka.ovh.kajet.core.model.NoteDocument

/**
 * Wyciąga z notatki tekst, po którym da się szukać.
 *
 * Z notatki odręcznej bierze pola tekstowe i pismo zamienione na tekst.
 * Dopóki nie uruchomisz rozpoznawania pisma, notatka odręczna znajduje się
 * tylko po tytule i po podpisach. Aplikacja mówi o tym wprost przy wyszukiwaniu.
 */
object TekstDoIndeksu {

    const val DLUGOSC_PODGLADU = 160

    fun tresc(dokument: NoteDocument): String = buildString {
        dokument.text?.let { append(it.markdown) }

        dokument.handwriting?.pages?.forEach { strona ->
            strona.texts.forEach { pole ->
                if (pole.text.isNotBlank()) {
                    append(pole.text)
                    append('\n')
                }
            }
            strona.recognized.forEach { rozpoznane ->
                if (rozpoznane.text.isNotBlank()) {
                    append(rozpoznane.text)
                    append('\n')
                }
            }
        }

        dokument.mindMap?.nodes?.forEach { wezel ->
            if (wezel.text.isNotBlank()) {
                append(wezel.text)
                append('\n')
            }
        }
    }.trim()

    /** Początek treści, pokazywany na liście notatek i w wynikach wyszukiwania. */
    fun podglad(dokument: NoteDocument): String {
        val surowy = tresc(dokument)
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("![") }
            .map { it.trimStart('#', '>', '-', '*', ' ') }
            .filter { it.isNotEmpty() }
            .joinToString(" ")

        if (surowy.isEmpty()) {
            val mapa = dokument.mindMap
            return when {
                dokument.handwriting != null -> pustaOdreczna(dokument)
                mapa != null -> "${mapa.nodes.size} węzłów"
                else -> ""
            }
        }
        return if (surowy.length <= DLUGOSC_PODGLADU) {
            surowy
        } else {
            surowy.take(DLUGOSC_PODGLADU).substringBeforeLast(' ') + "..."
        }
    }

    private fun pustaOdreczna(dokument: NoteDocument): String {
        val strony = dokument.handwriting?.pages ?: return ""
        val kreski = strony.sumOf { it.strokes.size }
        return when {
            kreski == 0 -> "Pusta notatka"
            strony.size == 1 -> "Pismo odręczne, $kreski kresek"
            else -> "Pismo odręczne, ${strony.size} stron"
        }
    }
}
