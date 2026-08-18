package wojtoteka.ovh.kajet.cloud

import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.storage.CloudLibrary
import wojtoteka.ovh.kajet.storage.LibraryRepository
import wojtoteka.ovh.kajet.storage.ServerDeletion
import wojtoteka.ovh.kajet.storage.TrashContents

/**
 * Atrapy biblioteki, serwera i konta — wspólne dla wszystkich testów
 * synchronizacji ([SyncTest], [SyncCodeTest], [SyncTrashTest]).
 *
 * Stoją osobno, bo prawdziwa biblioteka to dysk przez SAF, a prawdziwy serwer
 * to HTTP; ani jednego, ani drugiego nie ma w teście jednostkowym. Prawdziwe
 * zostają kolejka (SharedPreferences przez Robolectric), rejestr plików
 * z kodem i zapamiętane wersje — bo to właśnie o nie w tych testach chodzi.
 */
internal class FakeLibrary : CloudLibrary {

    val notes = LinkedHashMap<String, NoteDocument>()
    val texts = LinkedHashMap<String, String>()
    val trashedNoteIds = mutableListOf<String>()

    /** Ścieżki plików wyrzuconych do lokalnego kosza przez synchronizację. */
    val trashedFilePaths = mutableListOf<String>()

    var failWrites = false

    /** Przenoszenie do kosza rzuca wyjątkiem (błąd SAF, cofnięte uprawnienie). */
    var failTrash = false

    /** Sprzątanie po nagrobku rzuca wyjątkiem. */
    var failServerDeletions = false

    /** Zapis załącznika na dysk rzuca wyjątkiem. */
    var failAttachmentWrites = false

    /** Załączniki na dysku: (ścieżka notatki, nazwa) → bajty. */
    val attachments = LinkedHashMap<Pair<String, String>, ByteArray>()

    /** Co odpowiada biblioteka na nagrobek z serwera — po identyfikatorze. */
    val serverDeletions = mutableMapOf<String, ServerDeletion>()

    /** Co odpowiada biblioteka na nagrobek pliku z kodem — po ścieżce. */
    val serverCodeDeletions = mutableMapOf<String, ServerDeletion>()

    /** Po czym synchronizacja naprawdę kazała posprzątać. */
    val serverDeletionCalls = mutableListOf<String>()
    val serverCodeDeletionCalls = mutableListOf<String>()

    /** Notatki i pliki leżące w lokalnym koszu. */
    var trash = TrashContents()

    override fun refresh() = Unit
    override suspend fun hasStore(): Boolean = true
    override suspend fun rebuildIfEmpty(progress: ((Int, Int) -> Unit)?) = Unit

    override suspend fun readNote(path: String): NoteDocument =
        notes[path] ?: throw IllegalStateException("brak $path")

    override suspend fun readText(path: String): String =
        texts[path] ?: throw IllegalStateException("brak $path")

    override suspend fun allNoteIds(): List<Pair<String, String>> =
        notes.map { (notePath, doc) -> notePath to doc.id }

    override suspend fun allCodeFilePaths(): List<String> = texts.keys.toList()

    /** Ścieżki plików z gwiazdką — synchronizacja i czyta je, i zapisuje. */
    val starredFiles = mutableSetOf<String>()

    override suspend fun fileFavorite(path: String): Boolean = path in starredFiles

    override suspend fun setFileFavoriteFromCloud(path: String, favorite: Boolean) {
        if (favorite) starredFiles += path else starredFiles -= path
    }

    override suspend fun readTrashContents(): TrashContents = trash

    override suspend fun writeNoteFromCloud(document: NoteDocument, targetFolder: String): String? {
        if (failWrites) return null
        val newPath = if (targetFolder.isEmpty()) {
            "${document.title}.note"
        } else {
            "$targetFolder/${document.title}.note"
        }
        notes[newPath] = document
        return newPath
    }

    override suspend fun writeTextFromCloud(path: String, content: String) {
        if (failWrites) throw IllegalStateException("dysk odmawia")
        texts[path] = content
    }

    override suspend fun createTextFileFromCloud(
        parent: String,
        fileName: String,
        content: String,
    ): String {
        if (failWrites) throw IllegalStateException("dysk odmawia")
        // Zajętej nazwy nie nadpisujemy — prawdziwa biblioteka dokłada wtedy
        // licznik przed rozszerzeniem (FileNames.unique). Bez tego atrapa
        // gubiłaby po cichu plik, który miała postawić OBOK.
        val newPath = free(if (parent.isEmpty()) fileName else "$parent/$fileName")
        texts[newPath] = content
        return newPath
    }

    private fun free(path: String): String {
        if (path !in texts) return path
        val stem = path.substringBeforeLast('.', path)
        val extension = path.substringAfterLast('.', "")
        var counter = 2
        while (true) {
            val candidate = if (extension.isEmpty()) {
                "$stem ($counter)"
            } else {
                "$stem ($counter).$extension"
            }
            if (candidate !in texts) return candidate
            counter += 1
        }
    }

    override suspend fun moveNoteFromCloud(noteId: String, targetFolder: String): String? = null

    override suspend fun trashNoteFromCloud(noteId: String): Boolean {
        if (failTrash) throw IllegalStateException("kosz odmawia")
        trashedNoteIds += noteId
        val entry = notes.entries.firstOrNull { it.value.id == noteId } ?: return false
        notes.remove(entry.key)
        return true
    }

    override suspend fun trashFileFromCloud(path: String): Boolean {
        if (failTrash) throw IllegalStateException("kosz odmawia")
        trashedFilePaths += path
        return texts.remove(path) != null
    }

    override suspend fun applyServerDeletion(noteId: String, trash: TrashContents?): ServerDeletion {
        if (failServerDeletions) throw IllegalStateException("sprzątanie odmawia")
        serverDeletionCalls += noteId
        val outcome = serverDeletions[noteId] ?: ServerDeletion.NOTHING
        // Prawdziwa biblioteka po ERASED/TRASHED nie zostawia notatki tam,
        // gdzie była — atrapa też nie, inaczej uzgadnianie zaraz po nagrobku
        // widziałoby ją znowu i wysyłało na serwer jako nowość.
        if (outcome != ServerDeletion.NOTHING) {
            notes.entries.firstOrNull { it.value.id == noteId }?.let { notes.remove(it.key) }
        }
        return outcome
    }

    override suspend fun applyServerCodeDeletion(path: String, trash: TrashContents?): ServerDeletion {
        if (failServerDeletions) throw IllegalStateException("sprzątanie odmawia")
        serverCodeDeletionCalls += path
        val outcome = serverCodeDeletions[path] ?: ServerDeletion.NOTHING
        if (outcome != ServerDeletion.NOTHING) texts.remove(path)
        return outcome
    }

    override suspend fun attachmentNames(notePath: String): List<String> =
        attachments.keys.filter { it.first == notePath }.map { it.second }

    override suspend fun readAttachment(notePath: String, name: String): ByteArray? =
        attachments[notePath to name]

    override suspend fun putAttachment(notePath: String, name: String, data: ByteArray, mime: String) {
        if (failAttachmentWrites) throw IllegalStateException("dysk odmawia")
        attachments[notePath to name] = data
    }

    override suspend fun allCloudFolders(): List<LibraryRepository.CloudFolder> = emptyList()

    override suspend fun createFolderFromCloud(
        parent: String,
        name: String,
        colorId: String,
        iconId: String,
        id: String,
    ): String = if (parent.isEmpty()) name else "$parent/$name"

    override suspend fun renameFolderFromCloud(path: String, newName: String): String = path
    override suspend fun moveFolderFromCloud(path: String, targetFolder: String): String = path
    override suspend fun updateFolderLookFromCloud(path: String, colorId: String, iconId: String) = Unit
}

internal class FakeTransport : CloudTransport {

    /** Notatki, które „leżą na serwerze" — odda je pobieranie zmian. */
    var serverNotes: List<ServerNote> = emptyList()
    var failFetch = false

    /** Nagrobki, które odda pobieranie skasowanych na zawsze. */
    var tombstones: List<String> = emptyList()

    /** Załączniki „na serwerze": numer notatki → spis. */
    var serverAttachments: Map<String, List<AttachmentInfo>> = emptyMap()

    /** Bajty do pobrania: (numer notatki, nazwa) → treść. */
    var attachmentData: Map<Pair<String, String>, ByteArray> = emptyMap()

    /** Wysyłka załącznika odbija się od serwera. */
    var failSendAttachment = false

    /** Każdy wysłany załącznik: (numer notatki, nazwa). */
    val sentAttachments = mutableListOf<Pair<String, String>>()

    /** Każda wysłana notatka, po kolei. */
    val sentNotes = mutableListOf<OutgoingNote>()

    /** Każde trwałe skasowanie zgłoszone serwerowi, po kolei. */
    val deletedIds = mutableListOf<String>()

    var onSendNote: (OutgoingNote) -> CloudClient.Result<SaveResponse> = {
        CloudClient.Result.Ok(SaveResponse(status = "ok", version = 1))
    }

    var onDeleteNote: (String) -> CloudClient.Result<SaveResponse> = {
        CloudClient.Result.Ok(SaveResponse(status = "ok"))
    }

    override fun hasNetwork(): Boolean = true

    override suspend fun fetchChanges(
        since: Long,
        afterId: String?,
        withContent: Boolean,
    ): CloudClient.Result<ChangesResponse> {
        if (failFetch) return CloudClient.Result.Error("zerwana sieć", worthRetrying = true)
        val notes = if (withContent) serverNotes else serverNotes.map { it.copy(content = null) }
        return CloudClient.Result.Ok(
            ChangesResponse(
                notes = notes,
                upTo = notes.maxOfOrNull { it.updatedAt } ?: 0,
                hasMore = false,
            ),
        )
    }

    override suspend fun sendNote(note: OutgoingNote): CloudClient.Result<SaveResponse> {
        sentNotes += note
        return onSendNote(note)
    }

    override suspend fun deleteNote(noteId: String): CloudClient.Result<SaveResponse> {
        deletedIds += noteId
        return onDeleteNote(noteId)
    }

    override suspend fun fetchDeleted(since: Long, afterId: String?): CloudClient.Result<DeletedResponse> =
        CloudClient.Result.Ok(
            DeletedResponse(ids = tombstones, upTo = if (tombstones.isEmpty()) 0 else since + 1),
        )

    override suspend fun fetchFolders(): CloudClient.Result<FoldersResponse> =
        CloudClient.Result.Error("starszy serwer", notFound = true)

    override suspend fun sendFolder(folder: OutgoingFolder): CloudClient.Result<FolderSaveResponse> =
        CloudClient.Result.Error("starszy serwer", notFound = true)

    override suspend fun deleteFolder(folderId: String): CloudClient.Result<FolderSaveResponse> =
        CloudClient.Result.Error("starszy serwer", notFound = true)

    override suspend fun listAttachments(noteId: String): CloudClient.Result<AttachmentsResponse> =
        CloudClient.Result.Ok(AttachmentsResponse(serverAttachments[noteId].orEmpty()))

    override suspend fun sendAttachment(
        noteId: String,
        name: String,
        mime: String,
        data: ByteArray,
    ): CloudClient.Result<AttachmentResponse> {
        sentAttachments += noteId to name
        if (failSendAttachment) {
            return CloudClient.Result.Error("zerwana sieć", worthRetrying = true)
        }
        return CloudClient.Result.Ok(AttachmentResponse(name = name))
    }

    override suspend fun fetchAttachment(noteId: String, name: String): CloudClient.Result<ByteArray> =
        attachmentData[noteId to name]
            ?.let { CloudClient.Result.Ok(it) }
            ?: CloudClient.Result.Error("brak", notFound = true)
}

internal class FakeAccount : SyncAccount {
    private var lastSync = 0L

    /** Znacznik nagrobków — jawny, żeby testy widziały, czy się przesunął. */
    var lastDeleted = 0L

    /** Które z kolei pytanie o zalogowanie ma rzucić wyjątkiem (0 = żadne). */
    var throwOnSignedInCall = 0
    private var signedInCalls = 0

    override fun isSignedIn(): Boolean {
        signedInCalls += 1
        if (signedInCalls == throwOnSignedInCall) throw IllegalStateException("konto odmawia")
        return true
    }

    override fun lastSync(): Long = lastSync
    override fun rememberSync(moment: Long) { lastSync = moment }
    override fun lastDeletedSync(): Long = lastDeleted
    override fun rememberDeletedSync(moment: Long) { lastDeleted = moment }
}
