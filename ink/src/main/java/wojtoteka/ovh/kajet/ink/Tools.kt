package wojtoteka.ovh.kajet.ink

import wojtoteka.ovh.kajet.core.model.InkTool

enum class EditorTool {
    PEN,
    HIGHLIGHTER,
    ERASER_PARTIAL,
    ERASER_STROKE,
    LASSO,
    RULER,
    ;

    val labelPl: String
        get() = when (this) {
            PEN -> "Pisak"
            HIGHLIGHTER -> "Zakreślacz"
            ERASER_PARTIAL -> "Gumka"
            ERASER_STROKE -> "Gumka do całej kreski"
            LASSO -> "Zaznaczanie"
            RULER -> "Linijka"
        }

    val descriptionPl: String
        get() = when (this) {
            PEN -> "Kreska idzie tak, jak prowadzisz rysik."
            HIGHLIGHTER -> "Szeroka jasna kreska, kładzie się pod tekstem."
            ERASER_PARTIAL -> "Wyciera tylko to, po czym przejedziesz."
            ERASER_STROKE -> "Kasuje całą kreskę, której dotkniesz."
            LASSO -> "Obrysuj fragment, żeby go przesunąć albo skasować."
            RULER -> "Prostuje kreskę do linii. Blisko poziomu dociąga do równej."
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
