package wojtoteka.ovh.kajet.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import wojtoteka.ovh.kajet.core.text.Strings

@Serializable
data class NoteDocument(
    val format: Int = FORMAT_CURRENT,
    val id: String,
    val kind: NoteKind,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val tags: List<String> = emptyList(),
    val favorite: Boolean = false,
    val handwriting: HandwritingContent? = null,
    val text: TextContent? = null,
    val mindMap: MindMapContent? = null,
) {
    companion object {
        const val FORMAT_CURRENT = 1
        const val CONTENT_FILE = "content.json"
        const val ASSETS_DIRECTORY = "assets"
        const val EXTENSION = ".note"
    }
}

@Serializable
enum class NoteKind {
    @SerialName("handwritten")
    HANDWRITTEN,

    @SerialName("text")
    TEXT,

    @SerialName("mindmap")
    MINDMAP,
    ;

    val labelPl: String
        get() = when (this) {
            HANDWRITTEN -> "Notatka odręczna"
            TEXT -> "Notatka tekstowa"
            MINDMAP -> "Mapa myśli"
        }

    fun label(words: Strings): String = if (!words.english) labelPl else when (this) {
        HANDWRITTEN -> "Handwritten note"
        TEXT -> "Text note"
        MINDMAP -> "Mind map"
    }
}

// Handwritten note

@Serializable
data class HandwritingContent(
    val pageMode: PageMode,
    val background: PageBackground,
    val pages: List<NotePage>,
)

@Serializable
enum class PageMode {
    @SerialName("a4")
    A4,

    @SerialName("scroll")
    SCROLL,
    ;

    val labelPl: String
        get() = when (this) {
            A4 -> "Strony A4"
            SCROLL -> "Jedna długa strona"
        }

    fun label(words: Strings): String = if (!words.english) labelPl else when (this) {
        A4 -> "A4 pages"
        SCROLL -> "One long page"
    }
}

@Serializable
enum class PageBackground {
    @SerialName("plain")
    PLAIN,

    @SerialName("lined")
    LINED,

    @SerialName("grid")
    GRID,

    @SerialName("dots")
    DOTS,

    @SerialName("stave")
    STAVE,
    ;

    val labelPl: String
        get() = when (this) {
            PLAIN -> "Gładkie"
            LINED -> "W linie"
            GRID -> "W kratkę"
            DOTS -> "W kropki"
            STAVE -> "Pięciolinia"
        }

    fun label(words: Strings): String = if (!words.english) labelPl else when (this) {
        PLAIN -> "Blank"
        LINED -> "Ruled"
        GRID -> "Squared"
        DOTS -> "Dotted"
        STAVE -> "Music staves"
    }
}

@Serializable
data class NotePage(
    val id: String,
    val width: Float = A4_WIDTH,
    val height: Float = A4_HEIGHT,
    val background: PageBackground? = null,
    val strokes: List<InkStroke> = emptyList(),
    val shapes: List<ShapeElement> = emptyList(),
    val texts: List<TextBoxElement> = emptyList(),
    val images: List<ImageElement> = emptyList(),
    val recognized: List<RecognizedText> = emptyList(),
) {
    companion object {
        const val A4_WIDTH = 595f
        const val A4_HEIGHT = 842f

        const val SCROLL_STEP = 842f
    }
}

@Serializable
enum class NoteFont {
    @SerialName("heading")
    HEADING,

    @SerialName("body")
    BODY,

    @SerialName("mono")
    MONO,
    ;

    val labelPl: String
        get() = when (this) {
            HEADING -> "Nagłówkowy"
            BODY -> "Tekstowy"
            MONO -> "Maszynowy"
        }

    fun label(words: Strings): String = if (!words.english) labelPl else when (this) {
        HEADING -> "Display"
        BODY -> "Body"
        MONO -> "Typewriter"
    }
}

@Serializable
enum class NoteAlign {
    @SerialName("left")
    LEFT,

    @SerialName("center")
    CENTER,

    @SerialName("right")
    RIGHT,
    ;

    val labelPl: String
        get() = when (this) {
            LEFT -> "Do lewej"
            CENTER -> "Do środka"
            RIGHT -> "Do prawej"
        }

    fun label(words: Strings): String = if (!words.english) labelPl else when (this) {
        LEFT -> "Align left"
        CENTER -> "Align centre"
        RIGHT -> "Align right"
    }
}

@Serializable
data class TextBoxElement(
    val id: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val text: String = "",
    val fontSize: Float = 14f,
    val color: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val font: NoteFont = NoteFont.BODY,
    val align: NoteAlign = NoteAlign.LEFT,
    val background: Int = 0,
)

@Serializable
data class ImageElement(
    val id: String,
    val asset: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotation: Float = 0f,
)

@Serializable
enum class ShapeKind {
    @SerialName("line")
    LINE,

    @SerialName("arrow")
    ARROW,

    @SerialName("rect")
    RECTANGLE,

    @SerialName("round_rect")
    ROUNDED_RECTANGLE,

    @SerialName("ellipse")
    ELLIPSE,

    @SerialName("triangle")
    TRIANGLE,

    @SerialName("diamond")
    DIAMOND,

    @SerialName("star")
    STAR,
    ;

    /** Linia i strzałka mają dwa końce zamiast pola — stąd inne uchwyty i brak wypełnienia. */
    val open: Boolean get() = this == LINE || this == ARROW

    val labelPl: String
        get() = when (this) {
            LINE -> "Linia"
            ARROW -> "Strzałka"
            RECTANGLE -> "Prostokąt"
            ROUNDED_RECTANGLE -> "Prostokąt zaokrąglony"
            ELLIPSE -> "Elipsa"
            TRIANGLE -> "Trójkąt"
            DIAMOND -> "Romb"
            STAR -> "Gwiazda"
        }

    fun label(words: Strings): String = if (!words.english) labelPl else when (this) {
        LINE -> "Line"
        ARROW -> "Arrow"
        RECTANGLE -> "Rectangle"
        ROUNDED_RECTANGLE -> "Rounded rectangle"
        ELLIPSE -> "Ellipse"
        TRIANGLE -> "Triangle"
        DIAMOND -> "Diamond"
        STAR -> "Star"
    }
}

/**
 * Kształt wstawiony ręcznie: obiekt, nie wypalona kreska. Da się go zaznaczyć,
 * przesunąć, skalować, obrócić i skasować, a plik notatki trzyma go obok
 * pociągnięć rysika, w tych samych współrzędnych strony.
 *
 * Kształt siedzi w prostokącie odniesienia [x], [y], [width], [height]
 * i dopiero potem obraca się o [rotation] wokół swojego środka. Dzięki temu
 * skalowanie za uchwyty i obrót to zmiana czterech liczb, a nie przeliczanie
 * całej geometrii.
 *
 * Kształt zamknięty ma boki dodatnie. Linia i strzałka mogą mieć [width] albo
 * [height] ujemne: ich końce to (x, y) oraz (x + width, y + height), a grot
 * strzałki siedzi na tym drugim — bez znaku nie dałoby się narysować strzałki
 * w lewo inaczej niż obrotem o 180 stopni.
 */
@Serializable
data class ShapeElement(
    val id: String,
    val kind: ShapeKind,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    /** Obrót w stopniach, wokół środka prostokąta odniesienia. */
    val rotation: Float = 0f,
    /** Barwa obrysu w ARGB. */
    val color: Int,
    val strokeWidth: Float = 2f,
    /** Wypełnienie w ARGB; 0 znaczy „bez wypełnienia" — tak samo jak tło pola tekstowego. */
    val fill: Int = 0,
    val opacity: Float = 1f,
    /** Zaokrąglenie rogu prostokąta zaokrąglonego, jako ułamek krótszego boku. */
    val corner: Float = 0.18f,
) {
    val centerX: Float get() = x + width / 2f
    val centerY: Float get() = y + height / 2f

    /**
     * Prostokąt odniesienia BEZ obrotu, z bokami ustawionymi rosnąco — linia
     * w lewo ma ujemną szerokość, a prostokąt obejmujący musi zostać dodatni.
     * Obrys po obrocie liczy ShapeGeometry.
     */
    fun box(): Rect = Rect(
        left = minOf(x, x + width),
        top = minOf(y, y + height),
        right = maxOf(x, x + width),
        bottom = maxOf(y, y + height),
    )

    fun movedBy(dx: Float, dy: Float): ShapeElement = copy(x = x + dx, y = y + dy)
}

@Serializable
data class RecognizedText(
    val id: String,
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val strokeIds: List<String> = emptyList(),
)

// Text note

@Serializable
data class TextContent(
    val markdown: String = "",
    val drawings: List<InlineDrawing> = emptyList(),
    val font: NoteFont = NoteFont.BODY,
    val fontSize: Float = 0f,
    val textColor: Int = 0,
    val align: NoteAlign = NoteAlign.LEFT,
) {
    companion object {
        const val DEFAULT_SIZE = 17f
        const val SMALLEST_SIZE = 10f
        const val LARGEST_SIZE = 48f
    }
}

@Serializable
data class InlineDrawing(
    val asset: String,
    val source: String,
    val width: Float,
    val height: Float,
)

@Serializable
data class DrawingSource(
    val format: Int = NoteDocument.FORMAT_CURRENT,
    val width: Float,
    val height: Float,
    val strokes: List<InkStroke> = emptyList(),
)

// Mind map

@Serializable
data class MindMapContent(
    val nodes: List<MindNode> = emptyList(),
    val edges: List<MindEdge> = emptyList(),
    val viewX: Float = 0f,
    val viewY: Float = 0f,
    val zoom: Float = 1f,
)

@Serializable
data class MindNode(
    val id: String,
    val x: Float,
    val y: Float,
    val width: Float = 160f,
    val height: Float = 64f,
    val shape: NodeShape = NodeShape.RECTANGLE,
    val text: String = "",
    val ink: List<InkStroke> = emptyList(),
    val colorId: String = "grafit",
    val customColor: Int = 0,
    val fontSize: Float = 15f,
    val font: NoteFont = NoteFont.BODY,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val align: NoteAlign = NoteAlign.CENTER,
    val textColor: Int = 0,
    val collapsed: Boolean = false,
)

@Serializable
enum class NodeShape {
    @SerialName("rectangle")
    RECTANGLE,

    @SerialName("oval")
    OVAL,
    ;

    val labelPl: String get() = if (this == RECTANGLE) "Prostokąt" else "Owal"

    fun label(words: Strings): String = when {
        !words.english -> labelPl
        this == RECTANGLE -> "Rectangle"
        else -> "Oval"
    }
}

@Serializable
data class MindEdge(
    val id: String,
    val fromId: String,
    val toId: String,
)
