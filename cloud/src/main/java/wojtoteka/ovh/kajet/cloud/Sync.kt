package wojtoteka.ovh.kajet.cloud

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.storage.NoteCodec
import wojtoteka.ovh.kajet.storage.LibraryRepository

class Sync(
    private val context: Context,
    private val repository: LibraryRepository,
    private val account: AccountStore,
    private val client: CloudClient,
    private val queue: SendQueue,
) {

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    // Synchronizacja chodzi w tle i nikt na nią nie czeka. Bez tego uchwytu
    // każdy jej błąd — zerwane połączenie, dziwna odpowiedź serwera — leci do
    // systemu i zamyka całą aplikację. Notatki mają być ważniejsze od chmury,
    // więc awaria wysyłki może najwyżej zapalić komunikat.
    private val brokenSync = CoroutineExceptionHandler { _, failure ->
        _state.value = SyncState.Waiting(queue.size())
        Log.w("Kajet", "Synchronizacja w tle nie doszła do skutku", failure)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + brokenSync)

    private val lock = Mutex()

    private val versions: SharedPreferences =
        context.getSharedPreferences("kajet-versions", Context.MODE_PRIVATE)

    fun reportChange(path: String, noteId: String) {
        if (!account.isSignedIn()) return
        runCatching { queue.add(path, noteId) }
        if (client.hasNetwork()) scope.launch { runCatching { synchronise() } }
    }

    suspend fun synchronise(): SyncResult = lock.withLock {
        if (!account.isSignedIn()) {
            return@withLock SyncResult(reason = "Nie jesteś zalogowany.")
        }
        if (!client.hasNetwork()) {
            _state.value = SyncState.NoNetwork(queue.size())
            return@withLock SyncResult(
                reason = "Nie ma internetu. Notatki czekają na urządzeniu i wyślemy je, gdy sieć wróci.",
                worthRetrying = true,
            )
        }

        _state.value = SyncState.InProgress
        val sent = sendPending()
        val fetched = fetchChanges()

        val stillWaiting = queue.size()
        _state.value = if (stillWaiting > 0) {
            SyncState.Waiting(stillWaiting)
        } else {
            SyncState.Done(System.currentTimeMillis())
        }

        SyncResult(
            sent = sent.count,
            fetched = fetched.count,
            conflicts = sent.conflicts,
            reason = sent.reason ?: fetched.reason,
            worthRetrying = sent.worthRetrying || fetched.worthRetrying,
        )
    }

    // --- Sending ---

    private data class StepResult(
        val count: Int = 0,
        val conflicts: Int = 0,
        val reason: String? = null,
        val worthRetrying: Boolean = false,
    )

    private suspend fun sendPending(): StepResult = withContext(Dispatchers.IO) {
        var sent = 0
        var conflicts = 0
        var reason: String? = null
        var worthRetrying = false

        for (entry in queue.all()) {
            val document = runCatching { repository.readNote(entry.path) }.getOrNull()
            if (document == null) {
                // The note vanished from the tablet, for example into the bin.
                // There is nothing to send, so we simply drop it from the queue.
                queue.remove(entry.path)
                continue
            }

            val content = NoteCodec.encodeNote(document)
            val response = client.sendNote(
                OutgoingNote(
                    id = document.id,
                    title = document.title,
                    kind = toServerKind(document.kind),
                    favorite = document.favorite,
                    tags = document.tags,
                    content = content,
                    baseVersion = knownVersion(document.id),
                ),
            )

            when (response) {
                is CloudClient.Result.Ok -> {
                    when (response.data.status) {
                        "conflict" -> {
                            saveVersionAlongside(entry.path, response.data)
                            conflicts += 1
                            queue.remove(entry.path)
                        }
                        else -> {
                            rememberVersion(document.id, response.data.version)
                            sendAttachments(document.id, entry.path)
                            queue.remove(entry.path)
                            sent += 1
                        }
                    }
                }

                is CloudClient.Result.Error -> {
                    reason = reason ?: response.message
                    if (response.mustSignIn) {
                        // The token stopped working. There is no point walking
                        // the rest of the queue: every note would bounce alike.
                        _state.value = SyncState.MustSignIn
                        return@withContext StepResult(sent, conflicts, reason)
                    }
                    if (response.worthRetrying) {
                        worthRetrying = true
                        break
                    }
                    queue.recordFailure(entry.path)
                }
            }
        }

        StepResult(sent, conflicts, reason, worthRetrying)
    }

    private suspend fun sendAttachments(noteId: String, path: String) {
        val onDisk = runCatching { repository.attachmentNames(path) }.getOrDefault(emptyList())
        if (onDisk.isEmpty()) return

        val onServer = when (val listing = client.listAttachments(noteId)) {
            is CloudClient.Result.Ok -> listing.data.attachments.associateBy { it.name }
            is CloudClient.Result.Error -> return
        }

        for (name in onDisk) {
            val data = runCatching { repository.readAttachment(path, name) }.getOrNull() ?: continue
            val localHash = hash(data)
            if (onServer[name]?.hash == localHash) continue

            client.sendAttachment(noteId, name, mimeFromName(name), data)
        }
    }

    // --- Fetching ---

    private suspend fun fetchChanges(): StepResult = withContext(Dispatchers.IO) {
        var since = account.lastSync()
        var afterId: String? = null
        var fetched = 0
        var pages = 0

        // Server pages at 200 notes. Loop until hasMore is false, advancing the
        // (upTo, upToId) cursor so notes that share the same updatedAt are not lost.
        while (true) {
            when (val response = client.fetchChanges(since, afterId)) {
                is CloudClient.Result.Error -> {
                    if (pages > 0) {
                        account.rememberSync(since)
                        repository.refresh()
                    }
                    return@withContext StepResult(
                        count = fetched,
                        reason = response.message,
                        worthRetrying = response.worthRetrying,
                    )
                }

                is CloudClient.Result.Ok -> {
                    pages += 1
                    val page = response.data

                    for (fromServer in page.notes) {
                        val content = fromServer.content ?: continue

                        // A note we sent ourselves a moment ago need not be read
                        // back. We recognise it by the remembered version.
                        if (knownVersion(fromServer.id) >= fromServer.version) continue

                        // Notes deleted on the server are not deleted here. The bin
                        // is on the tablet and it decides what disappears.
                        if (fromServer.deletedAt != null) continue

                        val saved = runCatching {
                            repository.writeNoteFromCloud(NoteCodec.decodeNote(content))
                        }.getOrNull()

                        if (saved != null) {
                            fetchAttachments(fromServer.id, saved, fromServer.attachments)
                            rememberVersion(fromServer.id, fromServer.version)
                            fetched += 1
                        }
                    }

                    if (page.upTo > 0) {
                        since = page.upTo
                        afterId = page.upToId
                        account.rememberSync(since)
                    }

                    if (!page.hasMore) break
                }
            }
        }

        repository.refresh()
        StepResult(count = fetched)
    }

    private suspend fun fetchAttachments(
        noteId: String,
        path: String,
        listed: List<AttachmentInfo> = emptyList(),
    ) {
        val onServer = listed.ifEmpty {
            when (val listing = client.listAttachments(noteId)) {
                is CloudClient.Result.Ok -> listing.data.attachments
                is CloudClient.Result.Error -> return
            }
        }
        if (onServer.isEmpty()) return

        for (info in onServer) {
            val local = runCatching { repository.readAttachment(path, info.name) }.getOrNull()
            if (local != null && hash(local) == info.hash) continue

            val bytes = when (val downloaded = client.fetchAttachment(noteId, info.name)) {
                is CloudClient.Result.Ok -> downloaded.data
                is CloudClient.Result.Error -> continue
            }
            runCatching {
                repository.putAttachment(path, info.name, bytes, info.mime.ifBlank { mimeFromName(info.name) })
            }
        }
    }

    private suspend fun saveVersionAlongside(path: String, response: SaveResponse) {
        val fromServer = response.onServer ?: return
        val content = fromServer.content ?: return
        val document = runCatching { NoteCodec.decodeNote(content) }.getOrNull() ?: return

        val whenChanged = java.text.SimpleDateFormat(
            "d MMMM, HH:mm",
            java.util.Locale.forLanguageTag("pl-PL"),
        ).format(java.util.Date(fromServer.updatedAt))

        val saved = runCatching {
            repository.writeNoteFromCloud(
                document.copy(
                    // A new identifier, because this is meant to be a separate
                    // note rather than a swap of the one somebody is sitting at.
                    id = java.util.UUID.randomUUID().toString(),
                    title = "${document.title} (wersja z serwera, $whenChanged)",
                ),
                targetFolder = path.substringBeforeLast('/', ""),
            )
        }.getOrNull()

        // Attachments live under the original server note id; the conflict copy
        // is a new local note that still points at assets/... in its content.
        if (saved != null) {
            fetchAttachments(fromServer.id, saved, fromServer.attachments)
        }
    }

    // --- Remembered versions ---

    private fun knownVersion(noteId: String): Int = versions.getInt(noteId, 0)

    private fun rememberVersion(noteId: String, version: Int) {
        versions.edit().putInt(noteId, version).apply()
    }

    fun forgetAllVersions() {
        versions.edit().clear().apply()
    }

    private fun hash(data: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(data)
            .joinToString("") { "%02x".format(it) }

    private fun mimeFromName(name: String): String =
        when (name.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "json" -> "application/json"
            else -> "application/octet-stream"
        }

    private fun toServerKind(kind: NoteKind): String = when (kind) {
        NoteKind.HANDWRITTEN -> "HANDWRITTEN"
        NoteKind.TEXT -> "TEXT"
        NoteKind.MINDMAP -> "MINDMAP"
    }
}

sealed interface SyncState {
    data object Idle : SyncState
    data object InProgress : SyncState
    data class Done(val at: Long) : SyncState
    data class Waiting(val count: Int) : SyncState
    data class NoNetwork(val waiting: Int) : SyncState
    data object MustSignIn : SyncState
}

data class SyncResult(
    val sent: Int = 0,
    val fetched: Int = 0,
    val conflicts: Int = 0,
    val reason: String? = null,
    val worthRetrying: Boolean = false,
) {
    val fullySucceeded: Boolean get() = reason == null
}
