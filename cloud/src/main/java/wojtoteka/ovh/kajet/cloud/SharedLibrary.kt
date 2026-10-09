package wojtoteka.ovh.kajet.cloud

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.text.words
import wojtoteka.ovh.kajet.storage.LibraryRepository
import wojtoteka.ovh.kajet.storage.NoteCodec
import wojtoteka.ovh.kajet.storage.SharedNotes
import java.util.UUID

/**
 * Cudze notatki i foldery w aplikacji - „Udostępnione mi" i odnośniki.
 *
 * Odnośnik /n/<token> stuknięty w poczcie czy komunikatorze otwiera się tutaj
 * (MainActivity). Serwer mówi, co to jest:
 *
 * - udostępnienie imienne otwarte kontem z adresem z zaproszenia zostaje
 *   przyjęte - od teraz stoi w bibliotece z oznaczeniem „udostępnione", także
 *   na stronie, i da się w nim pisać bez sieci (zmiany dojadą później),
 * - zwykły odnośnik otwiera się na czas oglądania i po wyjściu znika.
 *
 * Treść cudzej notatki leży w [SharedNotes]; edytor otwiera ją zwykłą ścieżką,
 * a zmiany jadą na żywo ([CloudLive]) - z prawami z udostępnienia.
 */
class SharedLibrary(
    private val repository: LibraryRepository,
    private val account: AccountStore,
    private val client: CloudClient,
    private val live: CloudLive,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notes: SharedNotes = repository.shared

    private val _items = MutableStateFlow<List<SharedItem>>(emptyList())

    /** Przyjęte udostępnienia - lista w bibliotece. */
    val items: StateFlow<List<SharedItem>> = _items.asStateFlow()

    init {
        // Zdjęcie dołożone do cudzej notatki jedzie od razu do właściciela.
        repository.onSharedAttachment = { noteId, name, data, mime ->
            val token = notes.meta(noteId)?.token
            if (token != null) {
                scope.launch {
                    val sent = client.sendSharedAttachment(noteId, name, mime, data, token)
                    if (sent is CloudClient.Result.Error) Log.w("Kajet", "Zdjęcie do $noteId: ${sent.message}")
                }
            }
        }
        repository.onNoteClosed = ::closed
        // Notatki otwarte samym odnośnikiem nie zostają po zamknięciu aplikacji.
        scope.launch { runCatching { notes.forgetTemporary() } }
        _items.value = notes.accepted().map { meta ->
            SharedItem(
                token = meta.token,
                kind = "note",
                permission = if (meta.canEdit) "edit" else "read",
                owner = meta.owner,
                note = SharedNoteEntry(id = meta.noteId, title = meta.title, kind = meta.kind),
                accepted = true,
            )
        }
    }

    /** Odświeża listę z serwera. Bez konta albo sieci zostaje to, co było. */
    suspend fun refresh(): Boolean {
        if (!account.isSignedIn() || !client.hasNetwork()) return false
        return when (val answer = client.sharedItems()) {
            is CloudClient.Result.Ok -> {
                _items.value = answer.data.items
                // Notatka zdjęta z listy (na stronie albo cofnięta przez
                // właściciela) nie ma czego szukać na urządzeniu.
                val still = answer.data.items.mapNotNull { it.note?.id }.toSet()
                notes.accepted().filter { it.noteId !in still }.forEach { notes.forget(it.noteId) }
                true
            }
            is CloudClient.Result.Error -> false
        }
    }

    sealed interface Opened {
        /** Notatka gotowa w edytorze pod tą ścieżką. */
        data class Note(val path: String, val item: SharedItem) : Opened

        /** Folder - do przeglądania na liście. */
        data class Folder(val item: SharedItem) : Opened

        /** Tego aplikacja nie pokaże (np. plik z kodem) - otworzy się na stronie. */
        data class Web(val url: String) : Opened

        data class Failed(val message: String, val mustSignIn: Boolean = false) : Opened
    }

    /** Odnośnik z zewnątrz. */
    suspend fun open(token: String): Opened {
        if (!client.hasNetwork()) {
            // Przyjęta wcześniej notatka otwiera się i bez sieci.
            notes.accepted().firstOrNull { it.token == token }?.let { meta ->
                return Opened.Note(
                    notes.pathFor(meta.noteId),
                    SharedItem(token = token, permission = if (meta.canEdit) "edit" else "read", accepted = true),
                )
            }
            return Opened.Failed(words.noInternet)
        }
        val item = when (val answer = client.openShared(token)) {
            is CloudClient.Result.Ok -> answer.data
            is CloudClient.Result.Error -> return Opened.Failed(
                answer.message,
                mustSignIn = answer.code == "sign-in" || answer.code == "someone-else",
            )
        }
        if (item.accepted) refreshSoon()
        val note = item.note
        return when {
            item.kind == "folder" && item.folder != null -> Opened.Folder(item)
            note != null -> openNote(item.token, note, item)
            else -> Opened.Failed(words.sharedOpenFailed)
        }
    }

    private fun refreshSoon() {
        scope.launch { runCatching { refresh() } }
    }

    /** Notatka z udostępnienia (albo z udostępnionego folderu) do edytora. */
    suspend fun openNote(token: String, entry: SharedNoteEntry, item: SharedItem): Opened =
        withContext(Dispatchers.IO) {
            if (entry.kind == "CODE") {
                return@withContext Opened.Web("${account.serverUrl()}/n/$token?note=${entry.id}")
            }
            val state = try {
                live.api.state(entry.id, token)
            } catch (e: LiveApi.LiveException) {
                return@withContext Opened.Failed(
                    if (e.status == 404) words.sharedOpenFailed else words.serverUnreachable,
                )
            } catch (e: Exception) {
                return@withContext Opened.Failed(words.serverUnreachable)
            }
            val document = runCatching { NoteCodec.decodeNote(state.content) }.getOrElse {
                return@withContext Opened.Failed(words.sharedOpenFailed)
            }
            val previous = notes.meta(entry.id)
            val keepLocal = previous != null && previous.base.isNotBlank() && notes.exists(entry.id) &&
                runCatching {
                    val local = SyncBase.normalize(notes.readNote(entry.id))
                    val base = SyncBase.normalize(previous.base)
                    base != null && !wojtoteka.ovh.kajet.core.live.LiveMerge.jsonEqual(local, base)
                }.getOrDefault(false)
            val meta = SharedNotes.Meta(
                noteId = entry.id,
                token = token,
                canEdit = state.canEdit,
                accepted = item.accepted || (previous?.accepted ?: false),
                title = state.title,
                kind = state.kind,
                owner = item.owner,
                // Niewysłane zmiany z pracy bez sieci zostają - sesja na żywo
                // scali je z serwerem od starej bazy.
                version = if (keepLocal) previous!!.version else state.version,
                base = if (keepLocal) previous!!.base else NoteCodec.encodeNote(document),
            )
            try {
                if (keepLocal) notes.writeMeta(meta) else notes.store(meta, document)
                downloadAttachments(entry.id, token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Brak miejsca, uszkodzony plik - lepiej powiedzieć niż wyłożyć aplikację.
                return@withContext Opened.Failed(words.sharedOpenFailed)
            }
            Opened.Note(notes.pathFor(entry.id), item.copy(permission = if (state.canEdit) "edit" else "read"))
        }

    private suspend fun downloadAttachments(noteId: String, token: String) {
        val listing = client.sharedAttachments(noteId, token) as? CloudClient.Result.Ok ?: return
        val have = notes.attachmentNames(noteId).toSet()
        for (attachment in listing.data.attachments) {
            if (attachment.name in have) continue
            val data = client.sharedAttachment(noteId, attachment.name, token) as? CloudClient.Result.Ok
                ?: continue
            notes.writeAttachment(noteId, attachment.name, data.data)
        }
    }

    suspend fun folder(token: String, folderId: String?): CloudClient.Result<SharedFolderListing> =
        client.sharedFolder(token, folderId)

    /** Nowa pusta notatka w udostępnionym folderze - od razu do edytora. */
    suspend fun createNote(item: SharedItem, folderId: String, kind: NoteKind): Opened {
        val id = UUID.randomUUID().toString()
        val document = SharedNotes.emptyDocument(id = id, kind = kind, title = words.untitled)
        val created = client.createSharedNote(
            token = item.token,
            folderId = folderId,
            noteId = id,
            kind = kind.name,
            title = document.title,
            content = NoteCodec.encodeNote(document),
        )
        return when (created) {
            is CloudClient.Result.Ok -> openNote(
                item.token,
                SharedNoteEntry(id = id, title = document.title, kind = kind.name, version = created.data.version),
                item,
            )
            is CloudClient.Result.Error -> Opened.Failed(created.message)
        }
    }

    suspend fun deleteNote(item: SharedItem, noteId: String): String? =
        when (val answer = client.deleteSharedNote(item.token, noteId)) {
            is CloudClient.Result.Ok -> {
                notes.forget(noteId)
                null
            }
            is CloudClient.Result.Error -> answer.message
        }

    suspend fun createFolder(item: SharedItem, parentId: String, name: String): String? =
        when (val answer = client.createSharedFolder(item.token, parentId, name)) {
            is CloudClient.Result.Ok -> null
            is CloudClient.Result.Error -> answer.message
        }

    /** „Usuń z moich udostępnionych". */
    suspend fun leave(item: SharedItem): String? {
        val answer = client.leaveShared(item.shareId)
        if (answer is CloudClient.Result.Error) return answer.message
        item.note?.id?.let { notes.forget(it) }
        _items.value = _items.value.filterNot { it.shareId == item.shareId && it.token == item.token }
        return null
    }

    /** Notatka zamknięta w edytorze: ta z samego odnośnika znika z urządzenia. */
    fun closed(path: String) {
        if (!notes.isShared(path)) return
        val noteId = notes.noteIdOf(path)
        val meta = notes.meta(noteId) ?: return
        if (!meta.accepted) scope.launch {
            // Chwila na ostatnią wysyłkę na żywo, zanim katalog zniknie.
            kotlinx.coroutines.delay(5_000)
            if (!live.isLive(noteId)) notes.forget(noteId)
        }
    }
}
