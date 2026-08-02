package wojtoteka.ovh.kajet.core.model

import kotlinx.serialization.Serializable

/**
 * Opis folderu, zapisany w pliku folder.json wewnątrz katalogu.
 *
 * Nazwa katalogu na dysku bywa okrojona, bo nie każdy system plików przyjmie
 * dwukropek albo znak zapytania. Prawdziwa nazwa, ta którą wpisał użytkownik,
 * leży tutaj i to ją pokazuje aplikacja.
 */
@Serializable
data class FolderMeta(
    val format: Int = NoteDocument.FORMAT_BIEZACY,
    val id: String,
    val displayName: String,
    val colorId: String = "grafit",
    val iconId: String = "folder",
    val createdAt: Long = 0L,
    /** Kolejność ustawiona przez użytkownika. Mniejsza liczba jest wyżej. */
    val order: Int = 0,
) {
    companion object {
        const val PLIK = "folder.json"
    }
}

/** Rodzaj wpisu w drzewie biblioteki. */
enum class ItemType {
    FOLDER,
    NOTATKA,
    PLIK_KODU,
    INNY_PLIK,
}

/**
 * Jeden wiersz w bibliotece. Powstaje z indeksu albo z odczytu katalogu.
 * Trzyma adres dokumentu w systemie plików, bo po nim otwieramy zawartość.
 */
data class LibraryItem(
    val id: String,
    /** Ścieżka względem katalogu głównego biblioteki, na przykład Matematyka/Całki. */
    val path: String,
    val name: String,
    val type: ItemType,
    /** Adres dokumentu w Storage Access Framework, w postaci tekstu. */
    val documentUri: String,
    val updatedAt: Long = 0L,
    val noteKind: NoteKind? = null,
    val language: CodeLanguage? = null,
    val colorId: String? = null,
    val iconId: String? = null,
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
    /** Kilka pierwszych słów treści, pokazywane na liście i w wynikach wyszukiwania. */
    val preview: String? = null,
    /** Liczba wpisów wewnątrz, tylko dla folderów. */
    val childCount: Int = 0,
) {
    val parentPath: String get() = path.substringBeforeLast('/', "")
}

/** Ikony do wyboru przy tworzeniu folderu. */
enum class FolderIcon(val id: String, val labelPl: String) {
    FOLDER("folder", "Folder"),
    KSIAZKI("ksiazki", "Książki"),
    LITERY("litery", "Litery"),
    DZIALANIA("dzialania", "Działania"),
    NUTA("nuta", "Nuta"),
    KOLBA("kolba", "Kolba"),
    GLOBUS("globus", "Globus"),
    KOD("kod", "Kod"),
    GWIAZDKA("gwiazdka", "Gwiazdka"),
    ;

    companion object {
        fun fromId(id: String?): FolderIcon = entries.firstOrNull { it.id == id } ?: FOLDER
    }
}
