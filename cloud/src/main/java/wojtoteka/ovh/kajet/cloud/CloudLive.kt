package wojtoteka.ovh.kajet.cloud

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.storage.LibraryRepository
import wojtoteka.ovh.kajet.storage.LiveEditor
import wojtoteka.ovh.kajet.storage.LiveHandle
import wojtoteka.ovh.kajet.storage.LiveNotes
import wojtoteka.ovh.kajet.storage.LivePerson
import wojtoteka.ovh.kajet.storage.LiveStatus
import wojtoteka.ovh.kajet.storage.NoteCodec
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Edycja na żywo dla edytorów - otwiera [LiveSession] dla każdej otwartej
 * notatki, która jest na serwerze (własnej albo udostępnionej).
 *
 * Gdy notatka jest otwarta na żywo, to sesja wysyła jej zmiany (deltami)
 * i przyjmuje cudze; zwykła synchronizacja tę notatkę omija, żeby nie
 * wysyłać jej drugi raz w całości. Po zamknięciu notatki wszystko wraca
 * do synchronizacji - razem z bazą scalania, którą sesja zostawia
 * w [SyncBase].
 */
class CloudLive(
    context: Context,
    private val repository: LibraryRepository,
    private val account: AccountStore,
    private val client: CloudClient,
    private val sync: Sync,
    private val bases: SyncBase,
) : LiveNotes {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessions = ConcurrentHashMap<String, LiveSession>()

    /** To urządzenie dla serwera - stałe, żeby echo własnych zmian było rozpoznawalne. */
    val clientId: String = context.getSharedPreferences("kajet-live", Context.MODE_PRIVATE).let { prefs ->
        prefs.getString(KEY_CLIENT, null) ?: ("app-" + UUID.randomUUID().toString().replace("-", "").take(20))
            .also { prefs.edit().putString(KEY_CLIENT, it).apply() }
    }

    val api = LiveApi(baseUrl = { account.serverUrl() }, token = { account.token() }, clientId = clientId)

    /** Czy tę notatkę prowadzi teraz sesja na żywo - synchronizacja ją wtedy omija. */
    fun isLive(noteId: String): Boolean =
        sessions[noteId]?.status?.value.let { it == LiveStatus.LIVE || it == LiveStatus.CONNECTING }

    override fun open(path: String, document: NoteDocument, editor: LiveEditor): LiveHandle? {
        val shared = repository.shared
        val session: LiveSession
        val noteId: String
        if (shared.isShared(path)) {
            noteId = shared.noteIdOf(path)
            val meta = shared.meta(noteId) ?: return null
            val base = meta.base.takeIf { it.isNotBlank() }?.let(SyncBase::normalize)
            session = LiveSession(
                api = api,
                noteId = noteId,
                shareToken = meta.token,
                document = EditorDocument(editor),
                start = LiveSession.Start(
                    base = base,
                    version = meta.version,
                    knownVersion = meta.version,
                    pendingLocal = false,
                    readOnly = !meta.canEdit,
                ),
                onSynced = { version, content ->
                    shared.rememberBase(noteId, version, content.toString())
                },
                hasNetwork = client::hasNetwork,
            )
        } else {
            if (!account.isSignedIn()) return null
            noteId = document.id
            if (noteId.isBlank()) return null
            val known = sync.serverVersion(noteId)
            // Notatki nie ma jeszcze na serwerze - pojedzie zwykłą synchronizacją,
            // a na żywo otworzy się przy następnym wejściu.
            if (known <= 0) return null
            val stored = bases.read(noteId)?.takeIf { it.first == known }
            session = LiveSession(
                api = api,
                noteId = noteId,
                shareToken = null,
                document = EditorDocument(editor),
                start = LiveSession.Start(
                    base = stored?.second,
                    version = known,
                    knownVersion = known,
                    pendingLocal = sync.isQueued(path),
                ),
                onSynced = { version, content -> sync.liveSynced(noteId, version, content) },
                hasNetwork = client::hasNetwork,
            )
        }
        sessions.put(noteId, session)?.close()
        session.start()
        return Handle(session, path, noteId)
    }

    private inner class Handle(
        private val session: LiveSession,
        private val path: String,
        private val noteId: String,
    ) : LiveHandle {
        override val status: StateFlow<LiveStatus> = session.status
        override val people: StateFlow<List<LivePerson>> = session.people
        override val readOnly: Boolean get() = session.readOnly
        val lastAuthor: StateFlow<String?> = session.lastAuthor

        override fun saved(document: NoteDocument) = session.localChanged()

        override fun close() {
            scope.launch {
                runCatching { session.flush() }
                val unsent = runCatching { session.hasUnsent() }.getOrDefault(true)
                session.close()
                sessions.remove(noteId, session)
                // Co nie zdążyło pojechać na żywo, jedzie zwykłą synchronizacją -
                // scaloną z bazą, którą sesja właśnie zostawiła.
                if (unsent && !repository.shared.isShared(path)) {
                    sync.reportChange(path, noteId)
                } else if (!unsent) {
                    sync.syncSoon()
                }
            }
        }
    }

    /** Edytor po stronie JSON: migawki ekranu i podmiana treści na scaloną. */
    private class EditorDocument(private val editor: LiveEditor) : LiveSession.LiveDocument {

        override suspend fun snapshot(): LiveSession.Snapshot? {
            val document = withContext(Dispatchers.Main.immediate) { editor.current() } ?: return null
            return LiveSession.Snapshot(document, withContext(Dispatchers.Default) { SyncBase.normalize(document) })
        }

        override suspend fun replace(token: Any, merged: JsonObject, author: String): Boolean {
            val expected = token as? NoteDocument ?: return false
            val document = withContext(Dispatchers.Default) {
                runCatching { NoteCodec.json.decodeFromJsonElement(NoteDocument.serializer(), merged) }.getOrNull()
            } ?: return true
            return withContext(Dispatchers.Main.immediate) { editor.replaceIf(expected, document, author) }
        }

        override fun normalize(content: JsonObject): JsonObject = SyncBase.normalize(content)

        override fun gone() {
            CoroutineScope(Dispatchers.Main).launch { editor.gone() }
        }
    }

    private companion object {
        const val KEY_CLIENT = "client"
    }
}
