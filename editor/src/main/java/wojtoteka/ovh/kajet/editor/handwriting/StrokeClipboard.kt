package wojtoteka.ovh.kajet.editor.handwriting

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.Rect
import wojtoteka.ovh.kajet.ink.Strokes
import java.util.UUID
import kotlin.math.abs

/*
  Schowek na kreski.

  Jeden na całą aplikację, jak w Paincie: wycięte w jednej notatce wkleja się
  w drugiej albo w rysunku w notatce tekstowej. Trzyma kopie kresek w układzie
  kartki, z której przyszły - miejsce wklejenia i tak liczy się od nowa.
  Żyje tyle, co proces. Z systemowym schowkiem nie ma nic wspólnego: kreska
  to nie tekst i żadna inna aplikacja nie umiałaby jej wkleić.
*/
object StrokeClipboard {

    private val _strokes = MutableStateFlow<List<InkStroke>>(emptyList())
    val strokes: StateFlow<List<InkStroke>> = _strokes.asStateFlow()

    /** Kładzie [strokes] do schowka w miejsce tego, co w nim było. Pusta lista nie czyści. */
    fun put(strokes: List<InkStroke>) {
        val kept = strokes.filter { it.pointCount > 0 }
        if (kept.isNotEmpty()) _strokes.value = kept
    }

    fun clear() {
        _strokes.value = emptyList()
    }
}

/** Rozmiar kartki - tyle, ile trzeba do ułożenia na niej kresek. */
internal data class Sheet(val width: Float, val height: Float)

/**
 * Gdzie stają kreski wklejone i przeciągnięte. Czysta geometria, bez widoku -
 * ta sama dla notatki odręcznej i dla okna rysunku.
 */
internal object StrokePlacement {

    /** O tyle odsuwa się każde kolejne wklejenie w to samo miejsce. */
    const val PASTE_STEP = 24f

    /**
     * Kopie [strokes] ze świeżymi id, środkiem w punkcie ([centerX], [centerY])
     * kartki [sheet]. Jeśli mieszczą się na kartce, są na nią dosunięte w całości -
     * wklejenie przy brzegu nie może zostawić połowy rysunku na biurku, gdzie
     * kartka go nie pokaże.
     */
    fun pasted(
        strokes: List<InkStroke>,
        centerX: Float,
        centerY: Float,
        sheet: Sheet,
        newId: () -> String = { UUID.randomUUID().toString() },
    ): List<InkStroke> {
        val drawn = strokes.filter { it.pointCount > 0 }
        val box = outline(drawn) ?: return emptyList()
        val dx = onSheet(box.left, box.right, centerX - box.width / 2f, sheet.width)
        val dy = onSheet(box.top, box.bottom, centerY - box.height / 2f, sheet.height)
        return drawn.map { it.translated(dx, dy).copy(id = newId()) }
    }

    /**
     * Gdzie ląduje zaznaczenie puszczone po przeciągnięciu: strona i przesunięcie
     * do jej układu.
     *
     * Stronę wybiera środek zaznaczenia. Kartka kończy się w połowie odstępu
     * [gap] do następnej - tak samo jak przy szukaniu strony pod rysikiem.
     * Na wybranej stronie zaznaczenie zostaje dosunięte do kartki, jeśli się
     * na niej mieści: kreska wyciągnięta na biurko znika z ekranu (kartka ją
     * przycina), a w pliku i na wydruku dalej by była.
     */
    fun landing(strokes: List<InkStroke>, page: Int, sheets: List<Sheet>, gap: Float): Landing {
        val box = outline(strokes)
        if (box == null || page !in sheets.indices) return Landing(page, 0f, 0f)

        var target = page
        var shiftY = 0f
        var middle = (box.top + box.bottom) / 2f
        while (target > 0 && middle < -gap / 2f) {
            val step = sheets[target - 1].height + gap
            middle += step
            shiftY += step
            target--
        }
        while (target < sheets.lastIndex && middle > sheets[target].height + gap / 2f) {
            val step = sheets[target].height + gap
            middle -= step
            shiftY -= step
            target++
        }

        val sheet = sheets[target]
        val dx = onSheet(box.left, box.right, box.left, sheet.width)
        val dy = onSheet(box.top + shiftY, box.bottom + shiftY, box.top + shiftY, sheet.height)
        return Landing(target, dx, shiftY + dy)
    }

    /** Wynik [landing]: strona i przesunięcie kresek do jej układu. */
    data class Landing(val page: Int, val dx: Float, val dy: Float) {
        val stays: Boolean get() = dx == 0f && dy == 0f
    }

    /**
     * Obrys kresek razem z ich grubością. Sam środek kreski przy brzegu kartki
     * to pół kreski za brzegiem.
     */
    private fun outline(strokes: List<InkStroke>): Rect? {
        val box = Strokes.bounds(strokes) ?: return null
        val pad = (strokes.maxOfOrNull { it.size } ?: 0f) / 2f
        return box.expanded(pad)
    }

    /**
     * Przesunięcie odcinka [from]..[to] tak, żeby zaczynał się w [start],
     * a gdy się mieści - leżał w całości w 0..[size]. Za długi przylega do
     * początku kartki.
     */
    private fun onSheet(from: Float, to: Float, start: Float, size: Float): Float {
        val length = to - from
        val placed = if (length >= size) 0f else start.coerceIn(0f, size - length)
        return placed - from
    }
}

/**
 * Pilnuje, żeby kilka wklejeń z rzędu w to samo miejsce nie leżało jedno na
 * drugim: każde następne odsuwa się o [StrokePlacement.PASTE_STEP].
 */
internal class PasteSpots {

    private var lastPage = -1
    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private var repeats = 0

    /** Odsunięcie dla wklejenia w punkcie ([x], [y]) strony [page]. */
    fun next(page: Int, x: Float, y: Float): Float {
        val same = page == lastPage && abs(x - lastX) < 1f && abs(y - lastY) < 1f
        repeats = if (same) repeats + 1 else 0
        lastPage = page
        lastX = x
        lastY = y
        return repeats * StrokePlacement.PASTE_STEP
    }

    /** Nowa zawartość schowka zaczyna wklejanie od środka. */
    fun reset() {
        lastPage = -1
        lastX = Float.NaN
        lastY = Float.NaN
        repeats = 0
    }
}
