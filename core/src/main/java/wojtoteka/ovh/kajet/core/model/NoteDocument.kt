package wojtoteka.ovh.kajet.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Zawartość jednej notatki. To jest dokładnie to, co leży w pliku content.json
 * wewnątrz katalogu notatki. Opis formatu znajdziesz w pliku FORMAT.md.
 */
@Serializable
data class NoteDocument(
    /** Numer wersji formatu. Rośnie, kiedy zmiana psuje zgodność wstecz. */
    val format: Int = FORMAT_BIEZACY,
    val id: String,
    val kind: NoteKind,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val tags: List<String> = emptyList(),
    val favorite: Boolean = false,
    /** Wypełnione tylko dla notatek odręcznych. */
    val handwriting: HandwritingContent? = null,
    /** Wypełnione tylko dla notatek tekstowych. */
    val text: TextContent? = null,
    /** Wypełnione tylko dla map myśli. */
    val mindMap: MindMapContent? = null,
) {
    companion object {
        const val FORMAT_BIEZACY = 1
        const val PLIK_TRESCI = "content.json"
        const val KATALOG_ZALACZNIKOW = "assets"
        const val ROZSZERZENIE = ".note"
    }
}

@Serializable
enum class NoteKind {
    @SerialName("odreczna")
    ODRECZNA,

    @SerialName("tekstowa")
    TEKSTOWA,

    @SerialName("mapa")
    MAPA,
    ;

    val nazwaPl: String
        get() = when (this) {
            ODRECZNA -> "Notatka odręczna"
            TEKSTOWA -> "Notatka tekstowa"
            MAPA -> "Mapa myśli"
        }
}

// Notatka odręczna

@Serializable
data class HandwritingContent(
    val pageMode: PageMode,
    val background: PageBackground,
    val pages: List<NotePage>,
)

@Serializable
enum class PageMode {
    /** Osobne strony A4. Tak wychodzi na drukarce. */
    @SerialName("a4")
    A4,

    /** Jedna strona, która rośnie w dół bez końca. */
    @SerialName("wstega")
    WSTEGA,
    ;

    val nazwaPl: String
        get() = when (this) {
            A4 -> "Strony A4"
            WSTEGA -> "Nieskończona strona w dół"
        }
}

@Serializable
enum class PageBackground {
    @SerialName("gladkie")
    GLADKIE,

    @SerialName("linie")
    LINIE,

    @SerialName("kratka")
    KRATKA,

    @SerialName("kropki")
    KROPKI,

    @SerialName("pieciolinia")
    PIECIOLINIA,
    ;

    val nazwaPl: String
        get() = when (this) {
            GLADKIE -> "Gładkie"
            LINIE -> "W linie"
            KRATKA -> "W kratkę"
            KROPKI -> "W kropki"
            PIECIOLINIA -> "Pięciolinia"
        }
}

/**
 * Jedna strona notatki odręcznej. Współrzędne są w punktach typograficznych,
 * czyli 1/72 cala, tak jak w pliku PDF. Strona A4 to 595 na 842 punkty.
 * Dzięki temu wydruk i eksport nie wymagają przeliczania jednostek.
 */
@Serializable
data class NotePage(
    val id: String,
    val width: Float = SZEROKOSC_A4,
    val height: Float = WYSOKOSC_A4,
    /** Tło tylko tej strony. Puste oznacza tło ustawione dla całej notatki. */
    val background: PageBackground? = null,
    val strokes: List<InkStroke> = emptyList(),
    val texts: List<TextBoxElement> = emptyList(),
    val images: List<ImageElement> = emptyList(),
    /** Pismo zamienione na tekst. Trzymane obok kresek, nie zamiast nich. */
    val recognized: List<RecognizedText> = emptyList(),
) {
    companion object {
        const val SZEROKOSC_A4 = 595f
        const val WYSOKOSC_A4 = 842f

        /** Wysokość jednego przewinięcia wstęgi. Strona rośnie o tyle, gdy dopiszesz na dole. */
        const val PRZYROST_WSTEGI = 842f
    }
}

/** Pole tekstowe położone na stronie notatki odręcznej. */
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
)

/** Zdjęcie wstawione na stronę. Plik leży w katalogu assets wewnątrz notatki. */
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

/** Wynik rozpoznawania pisma. Służy do wyszukiwania i do eksportu tekstu. */
@Serializable
data class RecognizedText(
    val id: String,
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    /** Kreski, z których powstał ten tekst. */
    val strokeIds: List<String> = emptyList(),
)

// Notatka tekstowa

/**
 * Treść notatki tekstowej to zwykły Markdown. Rysunki wstawione w środek tekstu
 * są w nim zapisane jako obrazki, a obok leży ich wersja wektorowa,
 * żeby dało się je później poprawić.
 */
@Serializable
data class TextContent(
    val markdown: String = "",
    val drawings: List<InlineDrawing> = emptyList(),
)

@Serializable
data class InlineDrawing(
    /** Nazwa obrazka w katalogu assets, na przykład rysunek-1.png. */
    val asset: String,
    /** Nazwa pliku z kreskami w katalogu assets, na przykład rysunek-1.strokes.json. */
    val source: String,
    val width: Float,
    val height: Float,
)

/** Kreski rysunku wstawionego w tekst. Osobny plik, żeby content.json nie puchł. */
@Serializable
data class DrawingSource(
    val format: Int = NoteDocument.FORMAT_BIEZACY,
    val width: Float,
    val height: Float,
    val strokes: List<InkStroke> = emptyList(),
)

// Mapa myśli

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
    val shape: NodeShape = NodeShape.PROSTOKAT,
    val text: String = "",
    /** Podpis pisany rysikiem. Współrzędne liczone od lewego górnego rogu węzła. */
    val ink: List<InkStroke> = emptyList(),
    val colorId: String = "grafit",
    /** Zwinięty węzeł chowa swoje dzieci, ale ich nie kasuje. */
    val collapsed: Boolean = false,
)

@Serializable
enum class NodeShape {
    @SerialName("prostokat")
    PROSTOKAT,

    @SerialName("owal")
    OWAL,
    ;

    val nazwaPl: String get() = if (this == PROSTOKAT) "Prostokąt" else "Owal"
}

@Serializable
data class MindEdge(
    val id: String,
    val fromId: String,
    val toId: String,
)
