package wojtoteka.ovh.kajet.export

import wojtoteka.ovh.kajet.core.model.MindMapContent
import wojtoteka.ovh.kajet.core.model.NoteDocument

/**
 * Eksport notatki do zwykłego pliku Markdown.
 *
 * Notatka tekstowa wychodzi jeden do jednego, bo Markdown jest jej formatem
 * od początku. Mapa myśli zamienia się w listę wypunktowaną z wcięciami,
 * bo tak wygląda drzewo zapisane tekstem. Z notatki odręcznej wychodzą
 * pola tekstowe i pismo zamienione wcześniej na tekst.
 */
object EksportMarkdown {

    fun zamien(dokument: NoteDocument): String = buildString {
        append("# ").append(dokument.title).append("\n\n")

        if (dokument.tags.isNotEmpty()) {
            append(dokument.tags.joinToString(" ") { "#$it" })
            append("\n\n")
        }

        when {
            dokument.text != null -> append(dokument.text!!.markdown)
            dokument.mindMap != null -> append(zMapy(dokument.mindMap!!))
            dokument.handwriting != null -> append(zPisma(dokument))
        }
        if (!endsWith("\n")) append('\n')
    }

    private fun zMapy(mapa: MindMapContent): String = buildString {
        val poId = mapa.nodes.associateBy { it.id }
        val maRodzica = mapa.edges.map { it.toId }.toSet()
        val dzieci = mapa.edges.groupBy({ it.fromId }, { it.toId })
        val odwiedzone = mutableSetOf<String>()

        fun zejdz(id: String, poziom: Int) {
            if (!odwiedzone.add(id)) return
            val wezel = poId[id] ?: return
            append("  ".repeat(poziom))
            append("- ")
            append(wezel.text.ifBlank { "Bez podpisu" })
            append('\n')
            dzieci[id].orEmpty().forEach { zejdz(it, poziom + 1) }
        }

        mapa.nodes.filter { it.id !in maRodzica }.forEach { zejdz(it.id, 0) }
        mapa.nodes.forEach { zejdz(it.id, 0) }
    }

    private fun zPisma(dokument: NoteDocument): String = buildString {
        val strony = dokument.handwriting?.pages.orEmpty()
        var cokolwiek = false
        strony.forEachIndexed { numer, kartka ->
            val fragmenty = (kartka.recognized.map { it.text } + kartka.texts.map { it.text })
                .filter { it.isNotBlank() }
            if (fragmenty.isEmpty()) return@forEachIndexed
            cokolwiek = true
            append("## Strona ").append(numer + 1).append("\n\n")
            fragmenty.forEach { append(it).append("\n\n") }
        }
        if (!cokolwiek) {
            append(
                "Ta notatka jest pisana odręcznie i nie ma w niej jeszcze tekstu. " +
                    "Zaznacz pismo lassem i wybierz zamianę na tekst, a potem wyeksportuj ponownie.\n",
            )
        }
    }
}
