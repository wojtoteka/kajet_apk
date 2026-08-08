package wojtoteka.ovh.kajet.cloud

import android.content.Context
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.model.TextContent
import wojtoteka.ovh.kajet.storage.CloudLibrary
import wojtoteka.ovh.kajet.storage.LibraryRepository
import wojtoteka.ovh.kajet.storage.NoteCodec
import wojtoteka.ovh.kajet.storage.ServerDeletion
import wojtoteka.ovh.kajet.storage.TrashContents

/**
 * Co synchronizacja ROBI z odpowiedzią serwera — dalszy ciąg
 * [ServerContractTest], który sprawdza tylko, czy umie ją odczytać.
 *
 * Odpowiedź konfliktu rozgałęzia się na pięć ścieżek różniących się jedną
 * flagą albo jednym polem, a pomyłka między nimi to „skasowałem notatkę
 * i wróciła" albo „napisałem notatkę i zniknęła". Te testy przypinają każdą
 * ścieżkę z osobna, na atrapach biblioteki i serwera — prawdziwe są kolejka
 * (SharedPreferences przez Robolectric) i zapamiętane wersje.
 */
@RunWith(RobolectricTestRunner::class)
class SyncTest {

    private lateinit var context: Context
    private lateinit var library: FakeLibrary
    private lateinit var transport: FakeTransport
    private lateinit var account: FakeAccount
    private lateinit var queue: SendQueue
    private lateinit var sync: Sync

    private val noteId = "nota-1"
    private val path = "szkola/fizyka.note"

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("kajet-versions", Context.MODE_PRIVATE)
            .edit().clear().commit()
        library = FakeLibrary()
        transport = FakeTransport()
        account = FakeAccount()
        queue = SendQueue(context)
        queue.clear()
        sync = Sync(context, library, account, transport, queue, CodeFileIds(context))
    }

    private fun document(id: String = noteId, body: String = "treść") = NoteDocument(
        id = id,
        kind = NoteKind.TEXT,
        title = "Fizyka",
        createdAt = 1_000,
        updatedAt = 2_000,
        text = TextContent(markdown = body),
    )

    private fun serverCopy(body: String = "z serwera", version: Int = 7) = ServerNote(
        id = noteId,
        title = "Fizyka",
        kind = "TEXT",
        version = version,
        updatedAt = 5_000,
        content = NoteCodec.encodeNote(document(body = body)),
    )

    private fun knownVersion(id: String): Int =
        context.getSharedPreferences("kajet-versions", Context.MODE_PRIVATE).getInt(id, 0)

    private fun sync(): SyncResult = runBlocking { sync.synchronise() }

    // --- Konflikt: kopia obok ---

    @Test
    fun `konflikt tworzy kopie obok a lokalny plik zostaje nietkniety`() {
        library.notes[path] = document(body = "lokalna wersja")
        queue.add(path, noteId)
        transport.onSendNote = { note ->
            // Konfliktem odpowiada tylko sporna notatka; kopia konfliktu,
            // którą uzgadnianie wyśle jako nowość, przechodzi normalnie.
            if (note.id == noteId) {
                CloudClient.Result.Ok(SaveResponse(status = "conflict", onServer = serverCopy()))
            } else {
                CloudClient.Result.Ok(SaveResponse(status = "ok", version = 1))
            }
        }

        val result = sync()

        assertThat(result.conflicts).isEqualTo(1)
        // Lokalny plik dokładnie ten sam, a obok stanęła kopia z serwera.
        assertThat(library.notes[path]!!.text!!.markdown).isEqualTo("lokalna wersja")
        val copy = library.notes.values.single { it.id != noteId }
        assertThat(copy.title).contains("wersja z serwera")
        assertThat(copy.text!!.markdown).isEqualTo("z serwera")
        // Konflikt rozliczony: wersja zapamiętana, wpis zdjęty z kolejki.
        assertThat(knownVersion(noteId)).isEqualTo(7)
        assertThat(queue.size()).isEqualTo(0)
    }

    @Test
    fun `nieudana kopia konfliktu nie zdejmuje wpisu i nie zapamietuje wersji`() {
        library.notes[path] = document(body = "lokalna wersja")
        library.failWrites = true
        queue.add(path, noteId)
        transport.onSendNote = {
            CloudClient.Result.Ok(SaveResponse(status = "conflict", onServer = serverCopy()))
        }

        val result = sync()

        // Nic nie udajemy: bez kopii nie ma rozliczenia konfliktu.
        assertThat(result.conflicts).isEqualTo(0)
        assertThat(knownVersion(noteId)).isEqualTo(0)
        assertThat(queue.size()).isEqualTo(1)
        assertThat(queue.all().single().failedAttempts).isEqualTo(1)
    }

    // --- Konflikt z serwerowym koszem ---

    @Test
    fun `kosz serwera wygrywa przy wpisie z uzgadniania`() {
        library.notes[path] = document()
        queue.add(path, noteId, reconciled = true)
        transport.onSendNote = {
            CloudClient.Result.Ok(
                SaveResponse(
                    status = "conflict",
                    onServer = serverCopy(version = 9).copy(deletedAt = 4_000),
                ),
            )
        }

        sync()

        assertThat(library.trashedNoteIds).containsExactly(noteId)
        assertThat(knownVersion(noteId)).isEqualTo(9)
        assertThat(queue.size()).isEqualTo(0)
        // Kopia konfliktu NIE powstaje — kosz to nie rozjazd treści.
        assertThat(library.notes.values.none { it.title.contains("wersja z serwera") }).isTrue()
    }

    @Test
    fun `zapis czlowieka wskrzesza notatke z serwerowego kosza`() {
        library.notes[path] = document(body = "pisane dalej")
        queue.add(path, noteId, reconciled = false)
        var calls = 0
        transport.onSendNote = { note ->
            calls += 1
            if (calls == 1) {
                CloudClient.Result.Ok(
                    SaveResponse(
                        status = "conflict",
                        onServer = serverCopy(version = 9).copy(deletedAt = 4_000),
                    ),
                )
            } else {
                // Druga wysyłka idzie już z zapamiętaną wersją nagrobka.
                assertThat(note.baseVersion).isEqualTo(9)
                CloudClient.Result.Ok(SaveResponse(status = "ok", version = 10))
            }
        }

        sync()

        assertThat(calls).isEqualTo(2)
        assertThat(library.trashedNoteIds).isEmpty()
        assertThat(library.notes[path]!!.text!!.markdown).isEqualTo("pisane dalej")
        assertThat(knownVersion(noteId)).isEqualTo(10)
        assertThat(queue.size()).isEqualTo(0)
    }

    @Test
    fun `gone wyrzuca lokalna kopie do kosza i zapomina wersje`() {
        library.notes[path] = document()
        queue.add(path, noteId)
        transport.onSendNote = { CloudClient.Result.Ok(SaveResponse(status = "gone")) }

        sync()

        assertThat(library.trashedNoteIds).containsExactly(noteId)
        assertThat(knownVersion(noteId)).isEqualTo(0)
        assertThat(queue.size()).isEqualTo(0)
    }

    // --- Nieznana podstawa (reinstalacja, wyczyszczenie danych) ---

    @Test
    fun `pobranie nie nadpisuje pliku o nieznanej podstawie tylko robi konflikt`() {
        library.notes[path] = document(body = "lokalna, może nowsza")
        transport.serverNotes = listOf(serverCopy(body = "serwerowa", version = 5))

        val result = sync()

        // Lokalny plik nietknięty, kopia serwera obok, wersja zapamiętana.
        assertThat(library.notes[path]!!.text!!.markdown).isEqualTo("lokalna, może nowsza")
        val copy = library.notes.values.single { it.id != noteId }
        assertThat(copy.text!!.markdown).isEqualTo("serwerowa")
        assertThat(knownVersion(noteId)).isEqualTo(5)
        assertThat(result.conflicts).isEqualTo(1)
        // Uzgadnianie nie wysłało niczego z baseVersion = 0.
        assertThat(transport.sentNotes.none { it.id == noteId && it.baseVersion == 0 }).isTrue()
    }

    @Test
    fun `zgodna tresc przy nieznanej podstawie zostaje przyjeta bez kopii`() {
        library.notes[path] = document(body = "ta sama treść")
        transport.serverNotes = listOf(serverCopy(body = "ta sama treść", version = 5))

        sync()

        assertThat(library.notes).hasSize(1)
        assertThat(knownVersion(noteId)).isEqualTo(5)
        assertThat(transport.sentNotes).isEmpty()
    }

    @Test
    fun `niedokonczone pobranie wstrzymuje uzgadnianie biblioteki`() {
        library.notes[path] = document()
        transport.failFetch = true

        sync()

        // Bez pełnego pobrania nie wolno wysyłać w ciemno z baseVersion = 0.
        assertThat(transport.sentNotes).isEmpty()
        assertThat(queue.size()).isEqualTo(0)
    }

    @Test
    fun `pierwsza synchronizacja pustego serwera wysyla nieznane notatki`() {
        library.notes[path] = document()

        sync()

        // Serwer pusty, pobranie pełne — uzgadnianie ma prawo wysłać nowość.
        assertThat(transport.sentNotes.single().baseVersion).isEqualTo(0)
        assertThat(knownVersion(noteId)).isEqualTo(1)
    }

    // --- Utknięte wpisy ---

    @Test
    fun `po wyczerpaniu prob wpis zostaje jako utkniety i widac go w stanie`() {
        library.notes[path] = document()
        queue.add(path, noteId)
        transport.onSendNote = {
            CloudClient.Result.Error("serwer odmawia", worthRetrying = false)
        }

        repeat(QueueEntry.MAX_ATTEMPTS) { sync() }

        assertThat(queue.size()).isEqualTo(1)
        assertThat(queue.stuckCount()).isEqualTo(1)
        assertThat(sync.stuck.value).isEqualTo(1)

        // Utknięty wpis nie dobija się już do serwera.
        transport.sentNotes.clear()
        sync()
        assertThat(transport.sentNotes).isEmpty()

        // Ponowienie daje nową pulę prób.
        queue.retryStuck()
        assertThat(queue.stuckCount()).isEqualTo(0)
        sync()
        assertThat(transport.sentNotes).hasSize(1)
    }

    @Test
    fun `swiezy zapis czlowieka odwiesza utknieta notatke`() {
        library.notes[path] = document()
        queue.add(path, noteId)
        transport.onSendNote = {
            CloudClient.Result.Error("serwer odmawia", worthRetrying = false)
        }
        repeat(QueueEntry.MAX_ATTEMPTS) { sync() }
        assertThat(queue.stuckCount()).isEqualTo(1)

        // Człowiek zapisał notatkę jeszcze raz — wpis dostaje pełną pulę prób.
        queue.add(path, noteId)
        assertThat(queue.stuckCount()).isEqualTo(0)
    }
}

// --- Atrapy ---

private class FakeLibrary : CloudLibrary {

    val notes = LinkedHashMap<String, NoteDocument>()
    val texts = LinkedHashMap<String, String>()
    val trashedNoteIds = mutableListOf<String>()
    var failWrites = false

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

    override suspend fun readTrashContents(): TrashContents = TrashContents()

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
        val newPath = if (parent.isEmpty()) fileName else "$parent/$fileName"
        texts[newPath] = content
        return newPath
    }

    override suspend fun moveNoteFromCloud(noteId: String, targetFolder: String): String? = null

    override suspend fun trashNoteFromCloud(noteId: String): Boolean {
        trashedNoteIds += noteId
        val entry = notes.entries.firstOrNull { it.value.id == noteId } ?: return false
        notes.remove(entry.key)
        return true
    }

    override suspend fun trashFileFromCloud(path: String): Boolean = texts.remove(path) != null

    override suspend fun applyServerDeletion(noteId: String, trash: TrashContents?) =
        ServerDeletion.NOTHING

    override suspend fun applyServerCodeDeletion(path: String, trash: TrashContents?) =
        ServerDeletion.NOTHING

    override suspend fun attachmentNames(notePath: String): List<String> = emptyList()
    override suspend fun readAttachment(notePath: String, name: String): ByteArray? = null
    override suspend fun putAttachment(notePath: String, name: String, data: ByteArray, mime: String) = Unit

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

private class FakeTransport : CloudTransport {

    /** Notatki, które „leżą na serwerze" — odda je pobieranie zmian. */
    var serverNotes: List<ServerNote> = emptyList()
    var failFetch = false

    /** Każda wysłana notatka, po kolei. */
    val sentNotes = mutableListOf<OutgoingNote>()

    var onSendNote: (OutgoingNote) -> CloudClient.Result<SaveResponse> = {
        CloudClient.Result.Ok(SaveResponse(status = "ok", version = 1))
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

    override suspend fun deleteNote(noteId: String): CloudClient.Result<SaveResponse> =
        CloudClient.Result.Ok(SaveResponse(status = "ok"))

    override suspend fun fetchDeleted(since: Long, afterId: String?): CloudClient.Result<DeletedResponse> =
        CloudClient.Result.Ok(DeletedResponse())

    override suspend fun fetchFolders(): CloudClient.Result<FoldersResponse> =
        CloudClient.Result.Error("starszy serwer", notFound = true)

    override suspend fun sendFolder(folder: OutgoingFolder): CloudClient.Result<FolderSaveResponse> =
        CloudClient.Result.Error("starszy serwer", notFound = true)

    override suspend fun deleteFolder(folderId: String): CloudClient.Result<FolderSaveResponse> =
        CloudClient.Result.Error("starszy serwer", notFound = true)

    override suspend fun listAttachments(noteId: String): CloudClient.Result<AttachmentsResponse> =
        CloudClient.Result.Ok(AttachmentsResponse())

    override suspend fun sendAttachment(
        noteId: String,
        name: String,
        mime: String,
        data: ByteArray,
    ): CloudClient.Result<AttachmentResponse> =
        CloudClient.Result.Ok(AttachmentResponse(name = name))

    override suspend fun fetchAttachment(noteId: String, name: String): CloudClient.Result<ByteArray> =
        CloudClient.Result.Error("brak", notFound = true)
}

private class FakeAccount : SyncAccount {
    private var lastSync = 0L
    private var lastDeleted = 0L
    override fun isSignedIn(): Boolean = true
    override fun lastSync(): Long = lastSync
    override fun rememberSync(moment: Long) { lastSync = moment }
    override fun lastDeletedSync(): Long = lastDeleted
    override fun rememberDeletedSync(moment: Long) { lastDeleted = moment }
}
