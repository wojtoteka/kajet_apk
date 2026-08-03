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
import wojtoteka.ovh.kajet.storage.FormatException
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

    /**
     * Synchronizuje w tle i nie każe na siebie czekać — do zawołania przy
     * wejściu do aplikacji. Bez tego notatki dopisane na stronie pojawiały się
     * na urządzeniu dopiero po okresowym zadaniu (co pół godziny) albo po
     * ręcznym kliknięciu „Synchronizuj teraz".
     */
    fun syncSoon() {
        if (!account.isSignedIn()) return
        if (!client.hasNetwork()) return
        scope.launch { runCatching { synchronise() } }
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

        // Uzgodnienie całej biblioteki, jak na dysku w chmurze: notatki, których
        // serwer nie zna — także te sprzed zalogowania — dopisują się do kolejki
        // i jadą od razu. Kolejność ma znaczenie: najpierw pobranie zapamiętuje
        // wersje wszystkiego, co serwer już ma, więc nic mu się nie dubluje.
        val settled = reconcileLibrary()
        val sentAfter = if (settled > 0) sendPending() else StepResult()

        val stillWaiting = queue.size()
        _state.value = if (stillWaiting > 0) {
            SyncState.Waiting(stillWaiting)
        } else {
            SyncState.Done(System.currentTimeMillis())
        }

        SyncResult(
            sent = sent.count + sentAfter.count,
            fetched = fetched.count,
            conflicts = sent.conflicts + sentAfter.conflicts,
            reason = sent.reason ?: fetched.reason ?: sentAfter.reason,
            worthRetrying = sent.worthRetrying || fetched.worthRetrying || sentAfter.worthRetrying,
        )
    }

    /**
     * Dopisuje do kolejki każdą notatkę z biblioteki, o której serwer nic nie
     * wie (nie mamy zapamiętanej żadnej jego wersji). Zwraca liczbę dopisanych.
     */
    private suspend fun reconcileLibrary(): Int = withContext(Dispatchers.IO) {
        val queued = queue.all().map { it.path }.toSet()
        var added = 0
        val notes = runCatching { repository.allNoteIds() }.getOrDefault(emptyList())
        for ((path, noteId) in notes) {
            if (noteId.isBlank()) continue
            if (knownVersion(noteId) > 0) continue
            if (path in queued) continue
            runCatching { queue.add(path, noteId) }
            added += 1
        }
        added
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

        // Kiedy katalog notatek jest chwilowo nie do odczytania, każdy odczyt
        // poniżej rzuca wyjątkiem. Bez tej zapory cała kolejka szła wtedy do
        // kasacji jako „notatki, których już nie ma", i zmiany nigdy nie
        // docierały na serwer.
        if (repository.store() == null) {
            return@withContext StepResult(
                reason = "Nie widzę katalogu z notatkami, więc nie mam czego wysłać. " +
                    "Otwórz ustawienia i wskaż folder na urządzeniu.",
            )
        }

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
                            // The server copy just landed next to the local note.
                            // Remember the server version, otherwise the next fetch
                            // would treat it as new and overwrite the local edits
                            // this conflict was meant to protect.
                            response.data.onServer?.let {
                                rememberVersion(document.id, it.version)
                            }
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
        // Bez katalogu na notatki nie ma dokąd ich zapisać. Wcześniej pobieranie
        // szło mimo to: każda notatka po cichu przepadała, a zakładka przesuwała
        // się na koniec — po wskazaniu katalogu nie pobierało się już nic.
        if (repository.store() == null) {
            return@withContext StepResult(
                reason = "Nie wskazano katalogu na notatki, więc nie ma dokąd ich zapisać. " +
                    "Otwórz ustawienia i wybierz folder na urządzeniu.",
            )
        }

        var since = account.lastSync()
        var afterId: String? = null
        var fetched = 0
        var pages = 0
        var failures = 0
        var firstFailure: String? = null

        // Zakładka, którą wolno zapamiętać. Przesuwa się tylko przez notatki
        // załatwione do końca; pierwsza nieudana ją zatrzymuje, żeby następna
        // synchronizacja sięgnęła po tę notatkę jeszcze raz.
        var bookmark = since
        var blocked = false

        fun settled(updatedAt: Long) {
            if (!blocked && updatedAt > bookmark) bookmark = updatedAt
        }

        fun failed(note: ServerNote, problem: Throwable?) {
            failures += 1
            if (firstFailure == null) {
                firstFailure = when (problem) {
                    is FormatException -> problem.userMessage
                    else -> problem?.message?.takeIf { it.isNotBlank() }
                } ?: "Nie udało się zapisać notatki „${note.title}” na urządzeniu."
            }
            bookmark = minOf(bookmark, (note.updatedAt - 1).coerceAtLeast(0))
            blocked = true
        }

        // Server pages at 200 notes. Loop until hasMore is false, advancing the
        // (upTo, upToId) cursor so notes that share the same updatedAt are not lost.
        while (true) {
            when (val response = client.fetchChanges(since, afterId)) {
                is CloudClient.Result.Error -> {
                    if (pages > 0) {
                        account.rememberSync(bookmark)
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
                        val content = fromServer.content
                        if (content == null) {
                            settled(fromServer.updatedAt)
                            continue
                        }

                        // A note we sent ourselves a moment ago need not be read
                        // back. We recognise it by the remembered version.
                        if (knownVersion(fromServer.id) >= fromServer.version) {
                            settled(fromServer.updatedAt)
                            continue
                        }

                        // Notes deleted on the server are not deleted here. The bin
                        // is on the tablet and it decides what disappears.
                        if (fromServer.deletedAt != null) {
                            settled(fromServer.updatedAt)
                            continue
                        }

                        val outcome = runCatching {
                            repository.writeNoteFromCloud(NoteCodec.decodeNote(content))
                        }
                        val saved = outcome.getOrNull()

                        if (saved != null) {
                            fetchAttachments(fromServer.id, saved, fromServer.attachments)
                            rememberVersion(fromServer.id, fromServer.version)
                            fetched += 1
                            settled(fromServer.updatedAt)
                        } else {
                            Log.w(
                                "Kajet",
                                "Nie udało się zapisać notatki ${fromServer.id} z serwera",
                                outcome.exceptionOrNull(),
                            )
                            failed(fromServer, outcome.exceptionOrNull())
                        }
                    }

                    if (page.upTo > 0) {
                        since = page.upTo
                        afterId = page.upToId
                        account.rememberSync(bookmark)
                    }

                    if (!page.hasMore) break
                }
            }
        }

        repository.refresh()
        StepResult(
            count = fetched,
            reason = firstFailure?.let { first ->
                if (failures > 1) "$first (takich notatek jest $failures)" else first
            },
        )
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
