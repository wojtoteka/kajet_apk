package wojtoteka.ovh.kajet.core.model

import kotlinx.serialization.Serializable

@Serializable
data class FolderMeta(
    val format: Int = NoteDocument.FORMAT_CURRENT,
    val id: String,
    val displayName: String,
    val colorId: String = "grafit",
    val iconId: String = "folder",
    val createdAt: Long = 0L,
    val order: Int = 0,
) {
    companion object {
        const val FILE = "folder.json"
    }
}

enum class ItemType {
    FOLDER,
    NOTE,
    CODE_FILE,
    OTHER_FILE,
}

data class LibraryItem(
    val id: String,
    val path: String,
    val name: String,
    val type: ItemType,
    val documentUri: String,
    val updatedAt: Long = 0L,
    val noteKind: NoteKind? = null,
    val language: CodeLanguage? = null,
    val colorId: String? = null,
    val iconId: String? = null,
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val preview: String? = null,
    val childCount: Int = 0,
) {
    val parentPath: String get() = path.substringBeforeLast('/', "")
}

enum class FolderIcon(val id: String, val labelPl: String) {
    FOLDER("folder", "Folder"),
    BOOKS("ksiazki", "Książki"),
    LETTERS("litery", "Litery"),
    OPERATIONS("dzialania", "Działania"),
    NOTE("nuta", "Nuta"),
    FLASK("kolba", "Kolba"),
    GLOBE("globus", "Globus"),
    CODE("kod", "Kod"),
    STAR("gwiazdka", "Gwiazdka"),
    BRUSH("pedzel", "Pędzel"),
    HEART("serce", "Serce"),
    ATOM("atom", "Atom"),
    DNA("dna", "Nić DNA"),
    MAP("mapa", "Mapa"),
    COG("zebatka", "Zębatka"),
    BULB("zarowka", "Żarówka"),
    COMPASS("kompas", "Kompas"),
    ROCKET("rakieta", "Rakieta"),
    CROWN("korona", "Korona"),
    CUP("filizanka", "Filiżanka"),
    TREE("drzewo", "Drzewo"),
    MOUNTAIN("gora", "Góra"),
    CLOUD("cloud", "Chmura"),
    KEY("klucz", "Klucz"),
    CLOCK("zegar", "Zegar"),
    CALENDAR("kalendarz", "Kalendarz"),
    FLAG("flaga", "Flaga"),
    MICROSCOPE("mikroskop", "Mikroskop"),
    BALL("pilka", "Piłka"),
    MASK("maska", "Maska"),
    SCALES("waga", "Waga"),
    SHIELD("tarcza", "Tarcza"),
    HOUSE("dom", "Dom"),
    CAMERA("aparat", "Aparat"),
    PHOTO("zdjecie", "Zdjęcie"),
    DRAWING("rysunek", "Rysunek"),
    NODE("wezel", "Węzeł"),
    TAG("tag", "Etykieta"),
    ;

    companion object {
        fun fromId(id: String?): FolderIcon = entries.firstOrNull { it.id == id } ?: FOLDER
    }
}
