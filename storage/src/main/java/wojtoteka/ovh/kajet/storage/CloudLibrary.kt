package wojtoteka.ovh.kajet.storage

import wojtoteka.ovh.kajet.core.model.NoteDocument

/**
 * Wszystko, czego synchronizacja potrzebuje od biblioteki - i nic więcej.
 *
 * [LibraryRepository] umie znacznie więcej, ale chmura ma dotykać plików
 * wyłącznie przez tę wąską furtkę. Dzięki temu po samym interfejsie widać,
 * co synchronizacja może zrobić z dyskiem, a testy synchronizacji podstawiają
 * bibliotekę w pamięci zamiast dźwigać SAF i Rooma.
 */
interface CloudLibrary {

    fun refresh()

    /** Czy biblioteka jest w ogole dostepna (wybrany katalog da sie otworzyc). */
    suspend fun hasStore(): Boolean

    suspend fun rebuildIfEmpty(progress: ((done: Int, total: Int) -> Unit)? = null)

    // --- Czytanie ---

    suspend fun readNote(path: String): NoteDocument

    suspend fun readText(path: String): String

    suspend fun allNoteIds(): List<Pair<String, String>>

    suspend fun allCodeFilePaths(): List<String>

    /**
     * Czy plik ma gwiazdkę. Notatka niesie ją w treści, plik nie ma w czym -
     * a serwer trzyma ją przy notatce CODE tak samo jak przy każdej innej.
     */
    suspend fun fileFavorite(path: String): Boolean

    suspend fun readTrashContents(): TrashContents

    // --- Zapis z chmury ---

    suspend fun writeNoteFromCloud(document: NoteDocument, targetFolder: String = ""): String?

    suspend fun writeTextFromCloud(path: String, content: String)

    suspend fun createTextFileFromCloud(parent: String, fileName: String, content: String): String

    /** Gwiazdka pliku przysłana z serwera - bez odsyłania jej z powrotem. */
    suspend fun setFileFavoriteFromCloud(path: String, favorite: Boolean)

    suspend fun moveNoteFromCloud(noteId: String, targetFolder: String): String?

    // --- Kosz i kasowanie z chmury ---

    suspend fun trashNoteFromCloud(noteId: String): Boolean

    suspend fun trashFileFromCloud(path: String): Boolean

    suspend fun applyServerDeletion(noteId: String, trash: TrashContents? = null): ServerDeletion

    suspend fun applyServerCodeDeletion(path: String, trash: TrashContents? = null): ServerDeletion

    // --- Załączniki ---

    suspend fun attachmentNames(notePath: String): List<String>

    suspend fun readAttachment(notePath: String, name: String): ByteArray?

    suspend fun putAttachment(notePath: String, name: String, data: ByteArray, mime: String)

    // --- Foldery ---

    suspend fun allCloudFolders(): List<LibraryRepository.CloudFolder>

    suspend fun createFolderFromCloud(
        parent: String,
        name: String,
        colorId: String,
        iconId: String,
        id: String,
    ): String

    suspend fun renameFolderFromCloud(path: String, newName: String): String

    suspend fun moveFolderFromCloud(path: String, targetFolder: String): String

    suspend fun updateFolderLookFromCloud(path: String, colorId: String, iconId: String)
}
