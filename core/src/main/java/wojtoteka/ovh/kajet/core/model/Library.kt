package wojtoteka.ovh.kajet.core.model

import kotlinx.serialization.Serializable
import wojtoteka.ovh.kajet.core.text.Strings

@Serializable
data class FolderMeta(
    val format: Int = NoteDocument.FORMAT_CURRENT,
    val id: String,
    val displayName: String,
    val colorId: String = "grafit",
    val iconId: String = "folder",
    val createdAt: Long = 0L,
    // Ostatnia zmiana nazwy albo wyglądu — synchronizacja folderów porównuje
    // to ze znacznikiem serwera, żeby wiedzieć, która strona jest świeższa.
    // Stare pliki nie mają tego pola (zero = serwer wygrywa).
    val modifiedAt: Long = 0L,
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

/*
  Ikony folderów.

  Nazwy stoją tu, przy ikonach, a nie w słowniku napisów — trzydzieści osiem
  pozycji rozdętoby go bez żadnego zysku, a nazwa ikony nigdy nie zmieni się
  bez zmiany samej ikony. Który język wybrać, mówi `Strings.english`.
*/
enum class FolderIcon(val id: String, val labelPl: String, val labelEn: String) {
    FOLDER("folder", "Folder", "Folder"),
    BOOKS("ksiazki", "Książki", "Books"),
    LETTERS("litery", "Litery", "Letters"),
    OPERATIONS("dzialania", "Działania", "Arithmetic"),
    NOTE("nuta", "Nuta", "Music note"),
    FLASK("kolba", "Kolba", "Flask"),
    GLOBE("globus", "Globus", "Globe"),
    CODE("kod", "Kod", "Code"),
    STAR("gwiazdka", "Gwiazdka", "Star"),
    BRUSH("pedzel", "Pędzel", "Brush"),
    HEART("serce", "Serce", "Heart"),
    ATOM("atom", "Atom", "Atom"),
    DNA("dna", "Nić DNA", "DNA strand"),
    MAP("mapa", "Mapa", "Map"),
    COG("zebatka", "Zębatka", "Cog"),
    BULB("zarowka", "Żarówka", "Light bulb"),
    COMPASS("kompas", "Kompas", "Compass"),
    ROCKET("rakieta", "Rakieta", "Rocket"),
    CROWN("korona", "Korona", "Crown"),
    CUP("filizanka", "Filiżanka", "Cup"),
    TREE("drzewo", "Drzewo", "Tree"),
    MOUNTAIN("gora", "Góra", "Mountain"),
    CLOUD("cloud", "Chmura", "Cloud"),
    KEY("klucz", "Klucz", "Key"),
    CLOCK("zegar", "Zegar", "Clock"),
    CALENDAR("kalendarz", "Kalendarz", "Calendar"),
    FLAG("flaga", "Flaga", "Flag"),
    MICROSCOPE("mikroskop", "Mikroskop", "Microscope"),
    BALL("pilka", "Piłka", "Ball"),
    MASK("maska", "Maska", "Mask"),
    SCALES("waga", "Waga", "Scales"),
    SHIELD("tarcza", "Tarcza", "Shield"),
    HOUSE("dom", "Dom", "House"),
    CAMERA("aparat", "Aparat", "Camera"),
    PHOTO("zdjecie", "Zdjęcie", "Photo"),
    DRAWING("rysunek", "Rysunek", "Drawing"),
    NODE("wezel", "Węzeł", "Node"),
    TAG("tag", "Etykieta", "Tag"),
    ;

    fun label(words: Strings): String = if (words.english) labelEn else labelPl

    companion object {
        fun fromId(id: String?): FolderIcon = entries.firstOrNull { it.id == id } ?: FOLDER
    }
}
