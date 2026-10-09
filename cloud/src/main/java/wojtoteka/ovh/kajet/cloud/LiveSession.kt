package wojtoteka.ovh.kajet.cloud

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import wojtoteka.ovh.kajet.core.live.LiveMerge
import wojtoteka.ovh.kajet.storage.LivePerson
import wojtoteka.ovh.kajet.storage.LiveStatus
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicReference

/**
 * Edycja na żywo jednej otwartej notatki.
 *
 * Trzyma trzy rzeczy: bazę (treść notatki tak, jak zna ją serwer w wersji
 * [version]), to, co jest na ekranie (pyta o to edytor) i strumień zmian
 * z serwera. Zasady są dwie:
 *
 * 1. Własna zmiana jedzie jako delta od bazy (setki bajtów zamiast całej
 *    notatki). Serwer przyjmuje ją tylko wtedy, gdy baza jest aktualna;
 *    inaczej odpowiada „stale", a sesja najpierw dociąga cudzą zmianę.
 * 2. Cudza zmiana wchodzi na ekran przez [LiveMerge.merge3] - bazę, ekran
 *    i nową treść z serwera składa się w jedno, więc nic, co ktoś tu właśnie
 *    pisze, nie przepada.
 *
 * Bez sieci sesja czeka i próbuje dalej; zmiany zostają na dysku i jadą
 * zwykłą synchronizacją albo po powrocie łącza, scalone tak samo.
 */
class LiveSession(
    private val api: LiveApi,
    private val noteId: String,
    private val shareToken: String?,
    private val document: LiveDocument,
    private val start: Start,
    /** Serwer potwierdził treść [base] w wersji [version] - do zapamiętania na później. */
    private val onSynced: (version: Int, base: JsonObject) -> Unit,
    private val hasNetwork: () -> Boolean = { true },
    dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) {

    /** Z czym sesja startuje. */
    data class Start(
        /** Treść z serwera w wersji [version], jeśli ją znamy (baza scalania). */
        val base: JsonObject?,
        val version: Int,
        /** Wersja, którą synchronizacja ostatnio widziała na serwerze. */
        val knownVersion: Int,
        /** Czy na dysku czekają zmiany, których serwer jeszcze nie ma. */
        val pendingLocal: Boolean,
        /** Wiadomo z góry, że to udostępnienie tylko do czytania. */
        val readOnly: Boolean = false,
    )

    /** Edytor po stronie JSON - tłumaczenie z NoteDocument robi CloudLive. */
    interface LiveDocument {
        /** Treść edytora (znormalizowana) i znacznik tej migawki. */
        suspend fun snapshot(): Snapshot?

        /** Podmienia treść, o ile edytor wciąż pokazuje migawkę [token]. */
        suspend fun replace(token: Any, merged: JsonObject, author: String): Boolean

        /** Treść z serwera w postaci, w jakiej zapisałaby ją aplikacja. */
        fun normalize(content: JsonObject): JsonObject

        fun gone()
    }

    data class Snapshot(val token: Any, val content: JsonObject)

    private val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + dispatcher)
    private val mutex = Mutex()
    private val events = Channel<LiveApi.Event>(Channel.UNLIMITED)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val connection = AtomicReference<HttpURLConnection?>(null)

    @Volatile
    private var active = true

    private var base: JsonObject? = start.base
    private var version: Int = if (start.base != null) start.version else 0
    private var inflight = false
    private val held = ArrayList<LiveApi.Event.Change>()

    private val _status = MutableStateFlow(LiveStatus.CONNECTING)
    val status: StateFlow<LiveStatus> = _status.asStateFlow()

    private val _people = MutableStateFlow<List<LivePerson>>(emptyList())
    val people: StateFlow<List<LivePerson>> = _people.asStateFlow()

    private val _lastAuthor = MutableStateFlow<String?>(null)
    val lastAuthor: StateFlow<String?> = _lastAuthor.asStateFlow()

    @Volatile
    var readOnly: Boolean = start.readOnly
        private set

    private var jobs: List<Job> = emptyList()

    fun start() {
        jobs = listOf(
            scope.launch { readLoop() },
            scope.launch { for (event in events) handle(event) },
            scope.launch { pushLoop() },
        )
    }

    /** Edytor zapisał zmianę - wyślemy ją po krótkiej chwili (sklejając serię). */
    fun localChanged() {
        if (active) wake.trySend(Unit)
    }

    /**
     * Ostatnia próba wysyłki przed zamknięciem notatki - żeby to, co napisano
     * w ostatniej chwili, nie czekało na następną synchronizację.
     */
    suspend fun flush(timeoutMs: Long = 4_000) {
        if (readOnly || _status.value != LiveStatus.LIVE) return
        kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            var rounds = 0
            while (rounds < MAX_ROUNDS && pushOnce()) rounds++
        }
    }

    /** Czy na ekranie zostało coś, czego serwer nie ma - do decyzji przy zamknięciu. */
    suspend fun hasUnsent(): Boolean = mutex.withLock {
        val known = base ?: return@withLock true
        val now = document.snapshot() ?: return@withLock false
        LiveMerge.diff(known, now.content) != null
    }

    fun close() {
        active = false
        _status.value = LiveStatus.OFF
        runCatching { connection.get()?.disconnect() }
        events.close()
        wake.close()
        scope.cancel()
    }

    // --- Strumień ---

    private suspend fun readLoop() {
        var failures = 0
        while (active && scope.isActive) {
            if (!hasNetwork()) {
                _status.value = LiveStatus.OFFLINE
                delay(RETRY_MS[minOf(failures, RETRY_MS.lastIndex)])
                failures++
                continue
            }
            if (_status.value != LiveStatus.LIVE) _status.value = LiveStatus.CONNECTING
            val since = mutex.withLock { if (base != null) version else 0 }
            try {
                withContext(Dispatchers.IO) {
                    api.listen(
                        noteId = noteId,
                        since = since,
                        shareToken = shareToken,
                        stillWanted = { active },
                        onOpen = { connection.set(it) },
                    ) { event ->
                        failures = 0
                        events.trySend(event)
                    }
                }
            } catch (e: LiveApi.LiveException) {
                if (e.status == 404 || e.status == 403 || e.status == 410) {
                    // Notatki nie ma albo dostęp odebrany - nie ma po co pukać dalej.
                    gone()
                    return
                }
            } catch (_: Exception) {
                // Zerwane łącze - spróbujemy za chwilę.
            }
            if (!active) return
            _status.value = LiveStatus.OFFLINE
            delay(RETRY_MS[minOf(failures, RETRY_MS.lastIndex)])
            failures++
        }
    }

    private suspend fun handle(event: LiveApi.Event) {
        when (event) {
            is LiveApi.Event.Hello -> {
                mutex.withLock {
                    readOnly = !event.canEdit
                    if (base != null && event.version > version) version = event.version
                }
                _people.value = people(event.people)
                _status.value = LiveStatus.LIVE
                // Po powrocie łącza mogło zostać coś do wysłania.
                wake.trySend(Unit)
            }
            is LiveApi.Event.People -> _people.value = people(event.people)
            is LiveApi.Event.Change -> mutex.withLock {
                if (inflight) held += event else applyChange(event)
            }
            is LiveApi.Event.Reset -> mutex.withLock { applyReset(event.version, event.content) }
            LiveApi.Event.Gone -> gone()
        }
    }

    private fun people(list: List<LiveApi.Person>): List<LivePerson> = list
        .filter { it.client != api.clientId }
        .map { LivePerson(client = it.client, name = it.name, app = it.app, color = it.color) }

    private fun gone() {
        if (!active) return
        document.gone()
        close()
    }

    /** Wywoływać pod [mutex]. */
    private suspend fun applyChange(event: LiveApi.Event.Change) {
        if (event.version <= version) return
        val known = base
        if (known == null) {
            // Bez bazy delta nie ma do czego przylgnąć - prosimy o całość.
            runCatching { connection.get()?.disconnect() }
            return
        }
        val next = runCatching {
            document.normalize(LiveMerge.applyDelta(known, event.delta).jsonObject)
        }.getOrElse {
            runCatching { connection.get()?.disconnect() }
            return
        }
        if (event.by == api.clientId) {
            // Echo własnej zmiany, której odpowiedź się zgubiła - po prostu nowa baza.
            base = next
            version = event.version
            onSynced(version, next)
            return
        }
        mergeRemote(next, event.version, event.name)
    }

    /** Wywoływać pod [mutex]. */
    private suspend fun applyReset(serverVersion: Int, content: String) {
        val server = runCatching {
            document.normalize(LiveApi.json.parseToJsonElement(content).jsonObject)
        }.getOrNull() ?: return

        if (base == null) {
            when {
                // Serwer jest tam, gdzie ostatnia synchronizacja - to on jest bazą,
                // a niewysłane zmiany z dysku pojadą jako delta od niego.
                serverVersion == start.knownVersion -> {
                    base = server
                    version = serverVersion
                    onSynced(serverVersion, server)
                    wake.trySend(Unit)
                    return
                }
                // Na dysku nic nie czeka - przyjmujemy serwer w całości.
                !start.pendingLocal -> base = document.snapshot()?.content ?: server
                // Zmiany tu i tam, a bazy brak: bez niej scalanie zgadywałoby.
                // Rozstrzygnie to zwykła synchronizacja (kopia obok).
                else -> {
                    _status.value = LiveStatus.OFF
                    close()
                    return
                }
            }
        }
        mergeRemote(server, serverVersion, "")
    }

    /** Wywoływać pod [mutex]. */
    private suspend fun mergeRemote(next: JsonObject, nextVersion: Int, author: String) {
        val known = base ?: next
        for (attempt in 0 until MERGE_ATTEMPTS) {
            val snapshot = document.snapshot() ?: break
            val merged = LiveMerge.merge3(known, snapshot.content, next) as? JsonObject ?: next
            if (LiveMerge.jsonEqual(merged, snapshot.content)) break
            if (document.replace(snapshot.token, merged, author)) break
        }
        base = next
        version = nextVersion
        onSynced(nextVersion, next)
        if (author.isNotBlank()) _lastAuthor.value = author
        // Na ekranie mogło zostać coś własnego na wierzchu cudzej zmiany.
        wake.trySend(Unit)
    }

    // --- Wysyłka ---

    private suspend fun pushLoop() {
        for (unit in wake) {
            delay(PUSH_DELAY_MS)
            var again = true
            var rounds = 0
            while (again && active && rounds < MAX_ROUNDS) {
                again = pushOnce()
                rounds++
            }
        }
    }

    /** Wysyła, co jest do wysłania. true - warto od razu spróbować jeszcze raz. */
    private suspend fun pushOnce(): Boolean {
        if (readOnly) return false
        val sending: JsonObject
        val delta: JsonObject
        val from: Int
        mutex.withLock {
            if (inflight || _status.value != LiveStatus.LIVE) return false
            val known = base ?: return false
            val now = document.snapshot() ?: return false
            delta = LiveMerge.diff(known, now.content) ?: return false
            sending = now.content
            from = version
            inflight = true
        }

        val result = withContext(Dispatchers.IO) { api.push(noteId, from, delta, shareToken) }

        return mutex.withLock {
            inflight = false
            when (result) {
                is LiveApi.PushResult.Ok -> {
                    base = sending
                    version = maxOf(version, result.version)
                    onSynced(version, sending)
                    drainHeld()
                    true
                }
                is LiveApi.PushResult.Stale -> {
                    val hadNews = held.isNotEmpty()
                    drainHeld()
                    if (!hadNews) {
                        // Wersja urosła bez zmiany w strumieniu (gwiazdka,
                        // przeniesienie) albo strumień się spóźnia - bierzemy
                        // pełną treść i scalamy z nią.
                        val state = runCatching {
                            withContext(Dispatchers.IO) { api.state(noteId, shareToken) }
                        }.getOrNull()
                        if (state != null) applyReset(state.version, state.content)
                    }
                    true
                }
                LiveApi.PushResult.ReadOnly -> {
                    readOnly = true
                    false
                }
                LiveApi.PushResult.Gone -> {
                    scope.launch { gone() }
                    false
                }
                is LiveApi.PushResult.Failed -> {
                    // Sieć albo serwer - za chwilę jeszcze raz.
                    scope.launch {
                        delay(RETRY_PUSH_MS)
                        wake.trySend(Unit)
                    }
                    false
                }
            }
        }
    }

    /** Wywoływać pod [mutex]. */
    private suspend fun drainHeld() {
        val waiting = held.toList()
        held.clear()
        for (event in waiting) applyChange(event)
    }

    companion object {
        private val RETRY_MS = longArrayOf(1_000, 2_000, 5_000, 10_000, 20_000)
        private const val PUSH_DELAY_MS = 120L
        private const val RETRY_PUSH_MS = 3_000L
        private const val MERGE_ATTEMPTS = 5
        private const val MAX_ROUNDS = 6
    }
}
