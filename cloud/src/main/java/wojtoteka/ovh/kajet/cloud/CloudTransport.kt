package wojtoteka.ovh.kajet.cloud

/**
 * Rozmowy synchronizacji z serwerem - i nic więcej.
 *
 * [CloudClient] umie też logować i uruchamiać kod; chmura notatek dotyka
 * serwera wyłącznie przez tę furtkę. Testy synchronizacji podstawiają tu
 * skryptowane odpowiedzi zamiast prawdziwego HTTP.
 */
interface CloudTransport {

    fun hasNetwork(): Boolean

    suspend fun fetchChanges(
        since: Long,
        afterId: String? = null,
        withContent: Boolean = true,
    ): CloudClient.Result<ChangesResponse>

    suspend fun sendNote(note: OutgoingNote): CloudClient.Result<SaveResponse>

    suspend fun deleteNote(noteId: String): CloudClient.Result<SaveResponse>

    suspend fun fetchDeleted(since: Long, afterId: String? = null): CloudClient.Result<DeletedResponse>

    suspend fun fetchFolders(): CloudClient.Result<FoldersResponse>

    suspend fun sendFolder(folder: OutgoingFolder): CloudClient.Result<FolderSaveResponse>

    suspend fun deleteFolder(folderId: String): CloudClient.Result<FolderSaveResponse>

    suspend fun listAttachments(noteId: String): CloudClient.Result<AttachmentsResponse>

    suspend fun sendAttachment(
        noteId: String,
        name: String,
        mime: String,
        data: ByteArray,
    ): CloudClient.Result<AttachmentResponse>

    suspend fun fetchAttachment(noteId: String, name: String): CloudClient.Result<ByteArray>
}

/**
 * To, czego synchronizacja potrzebuje od konta: czy jest zalogowane i dokąd
 * doszło ostatnie pobieranie.
 */
interface SyncAccount {

    fun isSignedIn(): Boolean

    fun lastSync(): Long

    fun rememberSync(moment: Long)

    fun lastDeletedSync(): Long

    fun rememberDeletedSync(moment: Long)
}
