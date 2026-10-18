package wojtoteka.ovh.kajet.core.text

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Ile spacji zajmuje na ekranie jeden tabulator. */
const val TAB_WIDTH = 4

/**
 * Tabulator w polu do pisania.
 *
 * Klawisz Tab wpisuje do pola prawdziwy znak tabulacji, tylko że Android
 * rysuje go co 20 pikseli - na tablecie to tyle, co zwykła spacja. W treści
 * zostaje znak tabulacji (w pliku, w chmurze, po skopiowaniu), a na ekranie
 * zajmuje miejsce [TAB_WIDTH] spacji.
 *
 * [columns] - tabulator dociąga do najbliższej wielokrotności [TAB_WIDTH]
 * znaków w wierszu, jak w edytorach kodu. Ma to sens tylko przy czcionce
 * o stałej szerokości; w zwykłym tekście każdy tabulator ma tę samą szerokość.
 *
 * Kursor przeskakuje tabulator w całości, a stuknięcie w jego środek stawia
 * kursor po bliższej stronie.
 */
data class TabStops(
    private val inner: VisualTransformation = VisualTransformation.None,
    private val columns: Boolean = false,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val before = inner.filter(text)
        val shown = before.text
        if ('\t' !in shown.text) return before

        // starts[i] - gdzie na ekranie zaczyna się i-ty znak; ostatni wpis to długość całości.
        val starts = IntArray(shown.length + 1)
        val out = StringBuilder(shown.length + 16)
        var column = 0
        for ((index, sign) in shown.text.withIndex()) {
            starts[index] = out.length
            when (sign) {
                '\t' -> {
                    val width = if (columns) TAB_WIDTH - column % TAB_WIDTH else TAB_WIDTH
                    repeat(width) { out.append(' ') }
                    column += width
                }
                '\n' -> {
                    out.append(sign)
                    column = 0
                }
                else -> {
                    out.append(sign)
                    column++
                }
            }
        }
        starts[shown.length] = out.length

        val builder = AnnotatedString.Builder(out.toString())
        for (range in shown.spanStyles) {
            builder.addStyle(range.item, starts[range.start], starts[range.end])
        }
        for (range in shown.paragraphStyles) {
            builder.addStyle(range.item, starts[range.start], starts[range.end])
        }
        for (range in shown.getStringAnnotations(0, shown.length)) {
            builder.addStringAnnotation(range.tag, range.item, starts[range.start], starts[range.end])
        }

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                starts[before.offsetMapping.originalToTransformed(offset).coerceIn(0, shown.length)]

            override fun transformedToOriginal(offset: Int): Int =
                before.offsetMapping.transformedToOriginal(signAt(starts, offset))
        }
        return TransformedText(builder.toAnnotatedString(), mapping)
    }
}

/** Który znak stoi pod tym miejscem na ekranie; w środku tabulatora - bliższa krawędź. */
private fun signAt(starts: IntArray, offset: Int): Int {
    val found = starts.binarySearch(offset)
    if (found >= 0) return found
    val after = -found - 1
    if (after >= starts.size) return starts.lastIndex
    val before = after - 1
    return if (offset - starts[before] <= starts[after] - offset) before else after
}
