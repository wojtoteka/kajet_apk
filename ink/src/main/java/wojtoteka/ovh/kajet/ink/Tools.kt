package wojtoteka.ovh.kajet.ink

import wojtoteka.ovh.kajet.core.model.InkTool
import wojtoteka.ovh.kajet.core.model.ShapeKind
import wojtoteka.ovh.kajet.core.text.Strings

enum class EditorTool {
    PEN,
    HIGHLIGHTER,
    ERASER_PARTIAL,
    ERASER_STROKE,
    LASSO,
    RULER,
    SHAPES,
    ;

    val labelPl: String
        get() = when (this) {
            PEN -> "Pisak"
            HIGHLIGHTER -> "Zakreślacz"
            ERASER_PARTIAL -> "Gumka"
            ERASER_STROKE -> "Gumka do całej kreski"
            LASSO -> "Zaznaczanie"
            RULER -> "Linijka i kształty"
            SHAPES -> "Kształty"
        }

    val descriptionPl: String
        get() = when (this) {
            PEN -> "Kreska idzie tak, jak prowadzisz rysik."
            HIGHLIGHTER -> "Szeroka, jasna kreska. Rysuje się pod tekstem, więc go nie zasłania."
            ERASER_PARTIAL -> "Wyciera tylko to, po czym przejedziesz."
            ERASER_STROKE -> "Kasuje całą kreskę, której dotkniesz."
            LASSO -> "Obrysuj fragment, żeby go przesunąć albo skasować."
            RULER -> "Zamienia kreskę w prostą linię, a zamkniętą - w koło, owal, trójkąt albo prostokąt."
            SHAPES -> "Przeciągnij rysik, żeby wstawić kształt. Stuknięcie bierze kształt do poprawek."
        }

    fun label(words: Strings): String = if (!words.english) labelPl else when (this) {
        PEN -> "Pen"
        HIGHLIGHTER -> "Highlighter"
        ERASER_PARTIAL -> "Eraser"
        ERASER_STROKE -> "Whole-stroke eraser"
        LASSO -> "Select"
        RULER -> "Ruler and shapes"
        SHAPES -> "Shapes"
    }

    fun description(words: Strings): String = if (!words.english) descriptionPl else when (this) {
        PEN -> "The line follows the stylus exactly."
        HIGHLIGHTER -> "A wide, pale stroke that sits under the text."
        ERASER_PARTIAL -> "Rubs out only what you run over."
        ERASER_STROKE -> "Removes the whole stroke you touch."
        LASSO -> "Draw around a piece to move it or delete it."
        RULER -> "Turns a stroke into a straight line, and a closed one into a circle, oval, triangle, or rectangle."
        SHAPES -> "Drag the stylus to place a shape. A tap picks a shape up for changes."
    }

    val writes: Boolean get() = this == PEN || this == HIGHLIGHTER || this == RULER

    val isEraser: Boolean get() = this == ERASER_PARTIAL || this == ERASER_STROKE
}

data class PenSettings(
    val penKind: InkTool = InkTool.PEN,
    val penColor: Int,
    val penWidth: Float = 2.0f,
    val penOpacity: Float = 1f,
    val highlighterColor: Int,
    val highlighterWidth: Float = 16f,
    val highlighterOpacity: Float = Brushes.HIGHLIGHTER_OPACITY,
    val eraserRadius: Float = 12f,
) {
    fun toInkTool(tool: EditorTool): InkTool =
        if (tool == EditorTool.HIGHLIGHTER) InkTool.HIGHLIGHTER else penKind
}

/**
 * Czym rysuje się kształt: rodzaj, obrys, wypełnienie.
 *
 * [fill] równe zero znaczy „bez wypełnienia" - ta sama umowa co przy tle pola
 * tekstowego, więc nie przybywa nowej zasady do zapamiętania.
 */
data class ShapeSettings(
    val kind: ShapeKind = ShapeKind.RECTANGLE,
    val color: Int,
    val strokeWidth: Float = 2f,
    val fill: Int = 0,
    val opacity: Float = 1f,
)
