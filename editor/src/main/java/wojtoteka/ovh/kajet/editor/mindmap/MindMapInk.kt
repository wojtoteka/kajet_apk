package wojtoteka.ovh.kajet.editor.mindmap

import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.MindNode
import wojtoteka.ovh.kajet.ink.Strokes
import kotlin.math.max
import kotlin.math.min

/*
  Podpis rysikiem wpisany w węzeł.

  Okno rysunku oddaje kreski w mierze swojej kartki - pięćset sześćdziesiąt
  punktów szerokości, początek w jej lewym górnym rogu. Węzeł ma sto
  sześćdziesiąt punktów i własny róg, więc kreski wzięte wprost lądują tam,
  gdzie się je napisało NA KARTCE: hasło pisane na środku okna wychodziło
  daleko obok węzła, na tło mapy.

  Dlatego przy zapisie kreski przenosimy do miary węzła: obrys pisma idzie do
  rogu, skala dociąga go do szerokości węzła, a reszta miejsca rozkłada się po
  równo z obu stron. Wtedy nie ma znaczenia, gdzie w oknie się pisało.

  Węzeł tylko rośnie - tak samo jak przy haśle z klawiatury ([MindMapSizes.grown]).
  Pismo wyższe niż węzeł dostaje wyższy węzeł, zamiast kurczyć się do
  nieczytelnego paska. Pismo nigdy się nie powiększa: rysunek z rogu kartki ma
  zostać taki, jaki się go postawiło.
*/
object MindMapInk {

    /** Węzeł z podpisem wpisanym w jego ramkę. */
    fun fitted(node: MindNode, strokes: List<InkStroke>): MindNode {
        val bounds = Strokes.bounds(strokes) ?: return node.copy(ink = emptyList())

        // Zerowa szerokość to kreska pionowa - dzielenie przez nią dałoby
        // nieskończoność, a pionowa kreska i tak mieści się w każdym węźle.
        val usable = node.width - MindMapSizes.PAD_X
        val scale = min(1f, usable / max(1f, bounds.width))
        val inkWidth = bounds.width * scale
        val inkHeight = bounds.height * scale
        val height = max(node.height, inkHeight + MindMapSizes.PAD_Y)

        val dx = (node.width - inkWidth) / 2f - bounds.left * scale
        val dy = (height - inkHeight) / 2f - bounds.top * scale
        return node.copy(
            ink = strokes.map { it.movedInto(scale, dx, dy) },
            height = height,
        )
    }

    /** Kreska przeliczona na miarę węzła. Czas, nacisk i pochylenie zostają. */
    private fun InkStroke.movedInto(scale: Float, dx: Float, dy: Float): InkStroke {
        val fitted = ArrayList<Float>(points.size)
        for (i in points.indices) {
            fitted += when (i % InkStroke.VALUES_PER_POINT) {
                0 -> points[i] * scale + dx
                1 -> points[i] * scale + dy
                else -> points[i]
            }
        }
        return copy(points = fitted, size = size * scale)
    }
}
