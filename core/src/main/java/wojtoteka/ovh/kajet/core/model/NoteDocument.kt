package wojtoteka.ovh.kajet.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
            SCROLL -> "Nieskończona strona w dół"
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
}

@Serializable
data class NotePage(
    val id: String,
    val width: Float = A4_WIDTH,
    val height: Float = A4_HEIGHT,
    val background: PageBackground? = null,
    val strokes: List<InkStroke> = emptyList(),
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
}

@Serializable
data class MindEdge(
    val id: String,
    val fromId: String,
    val toId: String,
)
