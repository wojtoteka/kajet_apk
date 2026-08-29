package wojtoteka.ovh.kajet.cloud

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import wojtoteka.ovh.kajet.core.text.andMoreNotes
import wojtoteka.ovh.kajet.core.text.cloudCopyOf
import wojtoteka.ovh.kajet.core.text.codeNoteUnknownShape
import wojtoteka.ovh.kajet.core.text.noteSaveOnDeviceFailed
import wojtoteka.ovh.kajet.core.text.words
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.concurrent.atomic.AtomicBoolean
import wojtoteka.ovh.kajet.core.model.CodeLanguage
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.storage.CloudLibrary
import wojtoteka.ovh.kajet.storage.FormatException
import wojtoteka.ovh.kajet.storage.NoteCodec
import wojtoteka.ovh.kajet.storage.LibraryRepository
import wojtoteka.ovh.kajet.storage.ServerDeletion
import wojtoteka.ovh.kajet.storage.TrashContents

class Sync(
    private val context: Context,
    private val repository: CloudLibrary,
    private val account: SyncAccount,
    private val client: CloudTransport,
    private val queue: SendQueue,
    private val codeIds: CodeFileIds,
    private val uploads: FileUploader? = null,
) {

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /*
      Notatki, które wyczerpały próby wysyłki. Nie znikają z kolejki - czekają
      na ręczne ponowienie, a ekran konta pokazuje, że coś utknęło. Kiedyś
      taki wpis po prostu wypadał i notatka przestawała się synchronizować
      na zawsze, bez żadnego sygnału.
    */
    private val _stuck = MutableStateFlow(0)
    val stuck: StateFlow<Int> = _stuck.asStateFlow()

    // Synchronizacja chodzi w tle i nikt na nią nie czeka. Bez tego uchwytu
    // każdy jej błąd - zerwane połączenie, dziwna odpowiedź serwera - leci do
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

    // Autozapis potrafi zgłaszać zmianę co niecałą pół sekundy. Każde takie
    // zgłoszenie odpalało kiedyś osobną, pełną synchronizację; wszystkie
    // ustawiały się w kolejce do zamka i przy dłuższym pisaniu (albo przy
    // spamowaniu przycisku) rosła góra zaległych przebiegów, aż aplikacja
    // stawała. Teraz czeka co najwyżej jeden zapasowy przebieg: skoro i tak
    // wyśle wszystko, co uzbierało się w kolejce, więcej nie trzeba.
    //
    // Flaga gaśnie dopiero POD zamkiem (w synchronise), nie przy starcie
    // korutyny - inaczej okno konflacji trwałoby mikrosekundy i przez cały
    // czas trwającej synchronizacji dalej przybywałoby czekających przebiegów.
    private val syncScheduled = AtomicBoolean(false)

    // Stan folderów z ostatniego przebiegu syncFolders (żyje pod zamkiem
    // synchronizacji): czy serwer zna foldery i jak ścieżki mapują się na
    // identyfikatory. Bez wsparcia serwera notatki jadą bez pola folderu.
    private var folderSupport = false
    private var folderIdByPath: Map<String, String> = emptyMap()
    private var folderPathById: Map<String, String> = emptyMap()

    private fun scheduleSync() {
        if (!client.hasNetwork()) return
        if (!syncScheduled.compareAndSet(false, true)) return
        scope.launch {
            // Flaga gaśnie normalnie POD zamkiem w synchronise. Wyjątek przed
            // tym miejscem zostawiał ją podniesioną na zawsze: scheduleSync
            // nie umiał już nic zaplanować i synchronizacja stawała do
            // restartu aplikacji. Sam wyjątek idzie dalej, do [brokenSync] -
            // wcześniej znikał tu bez śladu.
            var reachedSync = false
            try {
                synchronise()
                reachedSync = true
            } finally {
                if (!reachedSync) syncScheduled.set(false)
            }
        }
    }

    /**
     * Ikona chmury przy „Zapisane": czy serwer ma tę notatkę, czy tylko dysk.
     *
     * true - nie czeka w kolejce i znamy serwerową wersję.
     * false - jest konto, ale wpis w kolejce albo brak potwierdzenia.
     * null - wylogowany; nie twierdzimy niczego o chmurze.
     */
    fun cloudSave(path: String, noteId: String?): Boolean? {
        val signedIn = runCatching { account.isSignedIn() }.getOrDefault(false)
        val queued = runCatching { queue.all().any { it.path == path } }.getOrDefault(false)
        val id = noteId?.takeIf { it.isNotBlank() }
            ?: runCatching { codeIds.existingIdFor(path) }.getOrNull()
        val known = !id.isNullOrBlank() && knownVersion(id) > 0
        return cloudSaveState(signedIn = signedIn, inQueue = queued, knownOnServer = known)
    }

    fun reportChange(path: String, noteId: String) {
        if (!account.isSignedIn()) return
        runCatching { queue.add(path, noteId) }
        scheduleSync()
    }

    /** Zapisany plik z kodem jedzie na serwer jako notatka CODE. */
    fun reportCodeChange(path: String) {
        if (!account.isSignedIn()) return
        // Tylko pliki o znanych rozszerzeniach; innych plików nie umiemy
        // przedstawić serwerowi jako notatki z kodem.
        if (CodeLanguage.fromExtension(path.substringAfterLast('/')) == null) return
        runCatching { queue.add(path, codeIds.idFor(path), QueueEntry.KIND_CODE) }
        scheduleSync()
    }

    /**
     * Kasowanie notatek na urządzeniu jedzie też na serwer: kosz jako nagrobek
     * (notatka ląduje w serwerowym koszu), trwałe kasowanie jako pełne
     * usunięcie razem z załącznikami.
     */
    fun reportDeleted(noteIds: List<String>, purge: Boolean) {
        if (!account.isSignedIn()) return
        for (noteId in noteIds) {
            if (noteId.isBlank()) continue
            runCatching { queue.addDeletion(noteId, purge) }
        }
        scheduleSync()
    }

    /** Zmiana nazwy albo przeniesienie - rejestr plików z kodem idzie w ślad. */
    fun reportPathMoved(oldPath: String, newPath: String) {
        // Kolejka może pamiętać zmianę sprzed przeniesienia. Przepinamy ją
        // zanim biblioteka zgłosi świeży zapis pod nową ścieżką.
        runCatching { queue.rebindPaths(oldPath, newPath) }
        runCatching { codeIds.rebind(oldPath, newPath) }
    }

    /** Pliki z kodem wyrzucone do kosza znikają też z biblioteki na serwerze. */
    fun reportCodeDeleted(paths: List<String>) {
        if (!account.isSignedIn()) return
        for (path in paths) {
            val id = codeIds.existingIdFor(path) ?: continue
            runCatching { queue.addDeletion(id, purge = false) }
        }
        scheduleSync()
    }

    /** Pliki z kodem skasowane na stałe - serwer też kasuje je z kosza. */
    fun reportCodePurged(paths: List<String>) {
        if (!account.isSignedIn()) return
        for (path in paths) {
            val id = codeIds.existingIdFor(path) ?: continue
            runCatching { queue.addDeletion(id, purge = true) }
            // Numer przestaje istnieć - nowy plik pod tą samą nazwą nie może
            // go odziedziczyć i wskrzesić skasowanego wpisu na serwerze.
            codeIds.remove(path)
        }
        scheduleSync()
    }

    /** Świeżo założony plik dostaje świeży numer, nawet gdy nazwa się powtarza. */
    fun reportCodeCreated(path: String) {
        codeIds.remove(path)
        reportCodeChange(path)
    }

    /**
     * Synchronizuje w tle i nie każe na siebie czekać - do zawołania przy
     * wejściu do aplikacji. Bez tego notatki dopisane na stronie pojawiały się
     * na urządzeniu dopiero po okresowym zadaniu (co pół godziny) albo po
     * ręcznym kliknięciu „Synchronizuj teraz".
     */
    fun syncSoon() {
        if (!account.isSignedIn()) return
        scheduleSync()
    }

    /**
     * Pełna synchronizacja we własnym zakresie Sync, nie w zakresie ekranu.
     * Wyjście z ekranu konta w trakcie nie przerywa wysyłki - przerwana w pół
     * kroku potrafiła zgubić zapamiętaną wersję i mnożyć kopie „(wersja z
     * serwera)".
     */
    fun synchroniseInBackground(): Deferred<SyncResult> = scope.async { synchronise() }

    /**
     * Ręczne „Synchronizuj teraz” jest również drogą naprawczą: odnawia pulę
     * prób wpisom, które wcześniej utknęły, a potem wykonuje pełny przebieg
     * góra/dół wraz z folderami i położeniem plików.
     */
    fun synchroniseManuallyInBackground(): Deferred<SyncResult> = scope.async {
        queue.retryStuck()
        _stuck.value = queue.stuckCount()
        synchronise()
    }

    suspend fun synchronise(): SyncResult = lock.withLock {
        // Dopiero teraz kolejne scheduleSync ma prawo zaplanować nowy przebieg:
        // ten wyśle wszystko, co zdążyło wpaść do kolejki przed tym miejscem.
        syncScheduled.set(false)

        // Proces mógł zginąć po PENDING -> UPLOADING. Cofamy taki stan także
        // wtedy, gdy urządzenie nadal jest offline; UI od razu pokazuje
        // oczekiwanie, a kolejny Worker będzie mógł podjąć wpis.
        uploads?.recoverInterrupted()

        if (!account.isSignedIn()) {
            return@withLock SyncResult(reason = words.notSignedIn)
        }
        if (!client.hasNetwork()) {
            _state.value = SyncState.NoNetwork(queue.size())
            return@withLock SyncResult(
                reason = words.offlineNoteQueued,
                worthRetrying = true,
            )
        }

        _state.value = SyncState.InProgress

        // Odkąd pliki z kodem też się synchronizują, raz trzeba przejrzeć całe
        // konto od początku - te utworzone na stronie przed tą zmianą mają
        // daty sprzed zakładki i zwykłe pobranie by ich nie zobaczyło.
        if (!versions.getBoolean(KEY_FULL_FETCH_FOR_CODE, false)) {
            account.rememberSync(0)
            versions.edit().putBoolean(KEY_FULL_FETCH_FOR_CODE, true).apply()
        }

        // Spis w Room bywa pusty (np. po zmianie wersji bazy). Bez odbudowy
        // uzgadnianie nic by nie widziało, a nagrobki z serwera trafiałyby
        // w próżnię i przepadały, choć pliki wciąż leżą na dysku.
        runCatching { repository.rebuildIfEmpty() }

        // Foldery idą przed notatkami: pobrane notatki muszą mieć już dokąd
        // trafić, a wysyłane - znać identyfikator swojego folderu.
        val folders = syncFolders()

        // Importowane pliki idą po folderach (folderId musi już istnieć), ale
        // przed pobraniem zmian, żeby odpowiedź serwera wróciła do lokalnej
        // biblioteki jeszcze w tym samym przebiegu.
        val uploaded = if (folders.reason == null) {
            uploads?.processPending() ?: UploadBatchResult()
        } else {
            // Nie próbujemy wysłać pliku do folderu, którego zapis właśnie
            // się nie udał. Serwer odpowiedziałby 404 i tymczasowa awaria
            // zostałaby błędnie utrwalona jako permanentny błąd uploadu.
            UploadBatchResult(reason = folders.reason, worthRetrying = folders.worthRetrying)
        }

        val sent = sendPending()
        val fetched = fetchChanges()

        // Notatki skasowane na serwerze na zawsze (np. „opróżnij kosz" na
        // stronie). Wiersz znika bez śladu, więc przyrostowe pobieranie nigdy
        // się o nim nie dowie - po to serwer zostawia nagrobki.
        val removals = fetchServerDeletions()

        // Droga zapasowa dla tego, po czym nagrobka nie ma: skasowań sprzed
        // tej zmiany i starszego serwera, który punktu z nagrobkami nie zna.
        // Porównanie z pełnym spisem identyfikatorów jest kosztowne (ciągnie
        // cały spis konta), więc idzie tylko wtedy, gdy naprawdę trzeba.
        if (!removals.tombstonesWork || !versions.getBoolean(KEY_SWEEP_AFTER_TOMBSTONES, false)) {
            val swept = reconcileServerDeletions()
            // Znacznik stawiamy DOPIERO po przebiegu, który naprawdę doszedł do
            // skutku. Postawiony po nieudanym (zerwana sieć w połowie spisu)
            // zamknąłby tę drogę na zawsze, a zaległości sprzed nagrobków nikt
            // by już nie posprzątał.
            if (swept && removals.tombstonesWork) {
                versions.edit().putBoolean(KEY_SWEEP_AFTER_TOMBSTONES, true).apply()
            }
        }

        // Uzgodnienie całej biblioteki, jak na dysku w chmurze: notatki, których
        // serwer nie zna - także te sprzed zalogowania - dopisują się do kolejki
        // i jadą od razu. Kolejność ma znaczenie: najpierw pobranie zapamiętuje
        // wersje wszystkiego, co serwer już ma, więc nic mu się nie dubluje.
        /*
          Tylko po PEŁNYM pobraniu. Uzgadnianie wysyła z baseVersion = 0
          wszystko, czego wersji nie zna - a serwer przy zerze przyjmuje
          bezwarunkowo. Po niedokończonym pobraniu (zerwana sieć, błąd
          w połowie spisu) taka wysyłka nadpisywałaby na serwerze notatki,
          których wersji po prostu nie zdążyliśmy zapamiętać.
        */
        // Pełne pobranie doszło do końca - od teraz brak zapamiętanej wersji
        // naprawdę znaczy „serwer tej notatki nie zna" i wysyłka z zerową
        // podstawą przestaje być strzałem w ciemno (patrz sendPending).
        if (fetched.completed && !versions.getBoolean(KEY_BASELINE_FETCH, false)) {
            versions.edit().putBoolean(KEY_BASELINE_FETCH, true).apply()
        }

        val settled = if (fetched.completed) reconcileLibrary() else 0

        // Drugi przebieg: po konflikcie z serwerowym koszem (zapamiętana
        // wersja nagrobka pozwala od razu przywrócić), po nowościach
        // z uzgadniania i dla wpisów wstrzymanych przed pierwszym pełnym
        // pobraniem - te jadą dopiero teraz, ze znaną podstawą.
        val sentAfter = if (settled > 0 || sent.retryNeeded || fetched.retryNeeded) {
            sendPending()
        } else {
            StepResult()
        }

        // Wpis wstrzymany bramką mógł dopiero w drugim przebiegu odbić się
        // od serwerowego kosza - wersja nagrobka już zapamiętana, trzeci
        // przebieg przywraca notatkę zamiast kazać czekać do następnego razu.
        val sentLast = if (sentAfter.retryNeeded) sendPending() else StepResult()

        val stuckNow = queue.stuckCount()
        _stuck.value = stuckNow
        val stillWaiting = queue.size() - stuckNow
        _state.value = if (stillWaiting > 0) {
            SyncState.Waiting(stillWaiting)
        } else {
            SyncState.Done(System.currentTimeMillis())
        }

        SyncResult(
            sent = sent.count + sentAfter.count + sentLast.count + uploaded.synced,
            fetched = fetched.count,
            conflicts = sent.conflicts + fetched.conflicts + sentAfter.conflicts +
                sentLast.conflicts,
            reason = folders.reason ?: uploaded.reason ?: sent.reason ?: fetched.reason
                ?: removals.reason ?: sentAfter.reason ?: sentLast.reason,
            worthRetrying = folders.worthRetrying || uploaded.worthRetrying || sent.worthRetrying ||
                fetched.worthRetrying || removals.worthRetrying ||
                sentAfter.worthRetrying || sentLast.worthRetrying,
        )
    }

    /**
     * Dopisuje do kolejki każdą notatkę i każdy plik z kodem z biblioteki,
     * o których serwer nic nie wie (nie mamy zapamiętanej żadnej jego wersji).
     * Zwraca liczbę dopisanych.
     */
    private suspend fun reconcileLibrary(): Int = withContext(Dispatchers.IO) {
        val queued = queue.all().map { it.path }.toSet()
        var added = 0
        val notes = runCatching { repository.allNoteIds() }.getOrDefault(emptyList())
        for ((path, noteId) in notes) {
            if (noteId.isBlank()) continue
            if (knownVersion(noteId) > 0) continue
            if (path in queued) continue
            runCatching { queue.add(path, noteId, reconciled = true) }
            added += 1
        }
        val codeFiles = runCatching { repository.allCodeFilePaths() }.getOrDefault(emptyList())
        for (path in codeFiles) {
            if (path in queued) continue
            if (CodeLanguage.fromExtension(path.substringAfterLast('/')) == null) continue
            val id = codeIds.idFor(path)
            if (knownVersion(id) > 0) continue
            runCatching { queue.add(path, id, QueueEntry.KIND_CODE, reconciled = true) }
            added += 1
        }
        added
    }

    // --- Folders ---

    /**
     * Uzgadnia strukturę folderów z serwerem: lokalne foldery, których serwer
     * nie zna, jadą do niego; serwerowe, których nie ma tu - powstają lokalnie.
     * Rozjazd nazwy/wyglądu/rodzica rozstrzyga świeższy znacznik czasu.
     * Starszy serwer (404) wyłącza całość na ten przebieg.
     */
    private suspend fun syncFolders(): StepResult = withContext(Dispatchers.IO) {
        folderSupport = false
        folderIdByPath = emptyMap()
        folderPathById = emptyMap()

        if (!repository.hasStore()) return@withContext StepResult()

        val listing = when (val response = client.fetchFolders()) {
            is CloudClient.Result.Error -> {
                if (response.notFound) return@withContext StepResult()
                // Sesję gasi CloudClient - tutaj zostaje sam stan paska.
                if (response.mustSignIn) _state.value = SyncState.MustSignIn
                return@withContext StepResult(
                    reason = response.message,
                    worthRetrying = response.worthRetrying,
                )
            }
            is CloudClient.Result.Ok -> response.data.folders
        }
        folderSupport = true

        var reason: String? = null
        var worthRetrying = false

        val locals = runCatching { repository.allCloudFolders() }.getOrDefault(emptyList())
            .sortedBy { it.path.count { piece -> piece == '/' } }
        val localById = locals.associateBy { it.id }
        val serverById = listing.associateBy { it.id }
        val localIdByPath = locals.associate { it.path to it.id }.toMutableMap()
        val localPathById = locals.associate { it.id to it.path }.toMutableMap()

        suspend fun push(folder: LibraryRepository.CloudFolder) {
            val parentId = if (folder.parentPath.isEmpty()) null else localIdByPath[folder.parentPath]
            val outcome = client.sendFolder(
                OutgoingFolder(
                    id = folder.id,
                    parentId = parentId,
                    name = folder.name,
                    colorId = folder.colorId,
                    iconId = folder.iconId,
                ),
            )
            when (outcome) {
                is CloudClient.Result.Ok -> rememberFolderSynced(folder.id)
                is CloudClient.Result.Error -> {
                    reason = reason ?: outcome.message
                    worthRetrying = worthRetrying || outcome.worthRetrying
                }
            }
        }

        // Foldery skasowane na urządzeniu - kolejka trzyma ich identyfikatory.
        for (entry in queue.all().filter { it.kind == QueueEntry.KIND_FOLDER_DELETE }) {
            when (val outcome = client.deleteFolder(entry.noteId)) {
                is CloudClient.Result.Ok -> {
                    forgetFolderSynced(entry.noteId)
                    queue.removeIfUnchanged(entry)
                }
                is CloudClient.Result.Error -> {
                    if (outcome.notFound) {
                        forgetFolderSynced(entry.noteId)
                        queue.removeIfUnchanged(entry)
                    } else {
                        reason = reason ?: outcome.message
                        worthRetrying = worthRetrying || outcome.worthRetrying
                    }
                }
            }
        }

        // Lokalny stan kontra serwer, od korzenia w głąb.
        for (folder in locals) {
            val onServer = serverById[folder.id]
            if (onServer == null) {
                // Serwer znał ten folder i już go nie zna - skasowany gdzie
                // indziej. Nieznany nigdy - świeży, jedzie na serwer.
                if (wasFolderSynced(folder.id)) {
                    runCatching { repository.trashFileFromCloud(folder.path) }
                        .onSuccess {
                            forgetFolderSynced(folder.id)
                            localIdByPath.remove(folder.path)
                            localPathById.remove(folder.id)
                        }
                } else {
                    push(folder)
                }
                continue
            }

            rememberFolderSynced(folder.id)
            val serverParentPath = onServer.parentId?.let { localPathById[it] } ?: ""
            val sameName = onServer.name == folder.name
            val sameLook = onServer.colorId == folder.colorId && onServer.iconId == folder.iconId
            val sameParent = serverParentPath == folder.parentPath

            if (sameName && sameLook && sameParent) continue

            if (folder.modifiedAt > onServer.updatedAt) {
                push(folder)
            } else {
                // Serwer jest świeższy - jego wersja wchodzi na urządzenie.
                var path = folder.path
                runCatching {
                    if (!sameName) path = repository.renameFolderFromCloud(path, onServer.name)
                    if (!sameLook) {
                        repository.updateFolderLookFromCloud(path, onServer.colorId, onServer.iconId)
                    }
                    if (!sameParent) {
                        val parent = onServer.parentId
                        val targetPath = if (parent == null) "" else localPathById[parent]
                        if (targetPath != null) {
                            path = repository.moveFolderFromCloud(path, targetPath)
                        }
                    }
                }
                localIdByPath.remove(folder.path)
                localIdByPath[path] = folder.id
                localPathById[folder.id] = path
            }
        }

        // Foldery z serwera, których nie ma lokalnie - powstają tutaj,
        // rodzice przed dziećmi.
        val missing = listing.filter { it.id !in localById }
        val pending = missing.toMutableList()
        var progress = true
        while (pending.isNotEmpty() && progress) {
            progress = false
            val iterator = pending.iterator()
            while (iterator.hasNext()) {
                val fromServer = iterator.next()
                val parentPath = when (val parent = fromServer.parentId) {
                    null -> ""
                    else -> localPathById[parent] ?: continue
                }
                val created = runCatching {
                    repository.createFolderFromCloud(
                        parent = parentPath,
                        name = fromServer.name.ifBlank { words.libKindFolder },
                        colorId = fromServer.colorId,
                        iconId = fromServer.iconId,
                        id = fromServer.id,
                    )
                }.getOrNull()
                if (created != null) {
                    localIdByPath[created] = fromServer.id
                    localPathById[fromServer.id] = created
                    rememberFolderSynced(fromServer.id)
                }
                iterator.remove()
                progress = true
            }
        }

        folderIdByPath = localIdByPath
        folderPathById = localPathById
        StepResult(reason = reason, worthRetrying = worthRetrying)
    }

    /**
     * Pole folderu dla wysyłanej notatki. Pusty tekst znaczy „korzeń wprost"
     * (serwer zamienia go na null); null znaczy „nie ruszaj" - tak jedzie,
     * gdy serwer nie zna folderów albo rodzic nie ma jeszcze tożsamości.
     */
    private fun outgoingFolderId(path: String): String? {
        if (!folderSupport) return null
        val parent = path.substringBeforeLast('/', "")
        if (parent.isEmpty()) return ""
        return folderIdByPath[parent]
    }

    /** Folder z serwerowym identyfikatorem - dokąd zapisać pobraną notatkę. */
    private fun folderTargetFor(folderId: String?): String? = when {
        !folderSupport -> null
        // Null na serwerze nie odróżnia „przeniesiono do korzenia" od „nigdy
        // nie ustawiono" (stare aplikacje nie wysyłały folderu), więc istniejącej
        // notatki nie ruszamy; nowa i tak trafi do korzenia.
        folderId == null -> null
        else -> folderPathById[folderId]
    }

    private fun wasFolderSynced(folderId: String): Boolean =
        versions.getBoolean("$KEY_FOLDER_PREFIX$folderId", false)

    private fun rememberFolderSynced(folderId: String) {
        if (!wasFolderSynced(folderId)) {
            versions.edit().putBoolean("$KEY_FOLDER_PREFIX$folderId", true).apply()
        }
    }

    private fun forgetFolderSynced(folderId: String) {
        versions.edit().remove("$KEY_FOLDER_PREFIX$folderId").apply()
    }

    /** Foldery wyrzucone do kosza na urządzeniu - serwer kasuje odpowiedniki. */
    fun reportFoldersDeleted(folderIds: List<String>) {
        if (!account.isSignedIn()) return
        for (folderId in folderIds) {
            if (folderId.isBlank()) continue
            runCatching {
                queue.add(
                    path = "${QueueEntry.DELETION_PREFIX}folder:$folderId",
                    noteId = folderId,
                    kind = QueueEntry.KIND_FOLDER_DELETE,
                )
            }
        }
        scheduleSync()
    }

    // --- Sending ---

    private data class StepResult(
        val count: Int = 0,
        val conflicts: Int = 0,
        val reason: String? = null,
        val worthRetrying: Boolean = false,
        // Konflikt z notatką leżącą w serwerowym koszu: wpis został w kolejce
        // i druga wysyłka w tym samym przebiegu przywróci notatkę.
        val retryNeeded: Boolean = false,
        // Czy krok obszedł wszystko, co miał obejść. Uzgadnianie biblioteki
        // rusza tylko po pełnym pobraniu - patrz [reconcileLibrary]. Pole na
        // końcu, bo wysyłka buduje ten wynik pozycyjnie.
        val completed: Boolean = true,
    )

    private class StopSending(val partial: StepResult) : Exception()

    private suspend fun sendPending(): StepResult = withContext(Dispatchers.IO) {
        var sent = 0
        var conflicts = 0
        var reason: String? = null
        var worthRetrying = false
        var retryNeeded = false
        var heldBack = false

        // Kiedy katalog notatek jest chwilowo nie do odczytania, każdy odczyt
        // poniżej rzuca wyjątkiem. Bez tej zapory cała kolejka szła wtedy do
        // kasacji jako „notatki, których już nie ma", i zmiany nigdy nie
        // docierały na serwer.
        if (!repository.hasStore()) {
            return@withContext StepResult(
                reason = words.noNotesDirToSend,
            )
        }

        // Wspólna obsługa odpowiedzi serwera dla wszystkich rodzajów wpisów.
        // Zwraca dane przy powodzeniu; błąd notuje i mówi, czy iść dalej.
        fun onError(entry: QueueEntry, failure: CloudClient.Result.Error): Boolean {
            reason = reason ?: failure.message
            if (failure.mustSignIn) {
                // The token stopped working. There is no point walking
                // the rest of the queue: every note would bounce alike.
                // Sesję zgasił już CloudClient; kolejka zostaje nietknięta,
                // żeby po ponownym zalogowaniu zaległe zmiany dojechały.
                _state.value = SyncState.MustSignIn
                throw StopSending(StepResult(sent, conflicts, reason))
            }
            if (failure.worthRetrying) {
                worthRetrying = true
                return false
            }
            queue.recordFailure(entry.path)
            return true
        }

        try {
            for (entry in queue.all()) {
                // Utknięte wpisy nie dobijają się do serwera i nie blokują
                // reszty - czekają na ręczne ponowienie z ekranu konta.
                if (entry.stuck) continue
                when (entry.kind) {
                    // Kasowania folderów obsługuje syncFolders - tu tylko nie
                    // wolno ich pomylić ze zwykłą notatką.
                    QueueEntry.KIND_FOLDER_DELETE -> continue

                    QueueEntry.KIND_TRASH, QueueEntry.KIND_PURGE -> {
                        val response = if (entry.kind == QueueEntry.KIND_PURGE) {
                            client.deleteNote(entry.noteId)
                        } else {
                            client.sendNote(
                                OutgoingNote(
                                    id = entry.noteId,
                                    title = "",
                                    kind = "TEXT",
                                    content = "",
                                    baseVersion = knownVersion(entry.noteId),
                                    deleted = true,
                                ),
                            )
                        }
                        when (response) {
                            is CloudClient.Result.Ok -> {
                                // Skasowana notatka nie ma już lokalnej wersji do
                                // pamiętania; przy koszu zapamiętujemy wersję
                                // nagrobka, żeby pobieranie go nie odtwarzało.
                                val version = response.data.version
                                if (entry.kind == QueueEntry.KIND_TRASH && version > 0) {
                                    rememberVersion(entry.noteId, version)
                                } else {
                                    forgetVersion(entry.noteId)
                                }
                                if (entry.kind == QueueEntry.KIND_PURGE) {
                                    // Po trwałym skasowaniu numer jest wolny -
                                    // nowy plik pod tą samą ścieżką ma dostać
                                    // świeży, a nie wskrzeszać stary wpis.
                                    codeIds.pathFor(entry.noteId)?.let { codeIds.remove(it) }
                                }
                                queue.removeIfUnchanged(entry)
                                sent += 1
                            }
                            is CloudClient.Result.Error -> if (!onError(entry, response)) break
                        }
                    }

                    QueueEntry.KIND_CODE -> {
                        val text = runCatching { repository.readText(entry.path) }.getOrNull()
                        if (text == null) {
                            // Plik zniknął z biblioteki (np. do kosza) - kasowanie
                            // zgłasza się osobnym wpisem, tu nie ma czego wysłać.
                            queue.remove(entry.path)
                            continue
                        }
                        if (knownVersion(entry.noteId) == 0 && !entry.reconciled &&
                            !baselineFetched()
                        ) {
                            // Kolejka czeka, nie wysyła w ciemno: wpis pojedzie
                            // w drugim przebiegu, już po pełnym pobraniu.
                            heldBack = true
                            continue
                        }
                        val fileName = entry.path.substringAfterLast('/')
                        val response = client.sendNote(
                            OutgoingNote(
                                id = entry.noteId,
                                title = fileName,
                                kind = "CODE",
                                // Gwiazdka jedzie razem z plikiem. Bez tego pola
                                // serwer czytał brak jako „nie ulubiona" i każda
                                // zmiana pliku na urządzeniu zdejmowała gwiazdkę
                                // postawioną na stronie.
                                favorite = runCatching { repository.fileFavorite(entry.path) }
                                    .getOrDefault(false),
                                folderId = outgoingFolderId(entry.path),
                                content = codeNoteContent(entry.noteId, entry.path, text),
                                baseVersion = knownVersion(entry.noteId),
                            ),
                        )
                        when (response) {
                            is CloudClient.Result.Ok -> {
                                when (response.data.status) {
                                    "conflict" -> {
                                        val onServer = response.data.onServer
                                        if (onServer?.deletedAt != null && entry.reconciled) {
                                            // Uzgadnianie trafiło na plik skasowany gdzie
                                            // indziej - serwerowy kosz wygrywa, plik idzie
                                            // do lokalnego kosza zamiast się wskrzeszać.
                                            // Rozliczenie dopiero PO udanym przeniesieniu:
                                            // porażka zostawiała wersję zapamiętaną, wpis
                                            // znikał, a plik wisiał osierocony bez śladu.
                                            val moved = runCatching {
                                                repository.trashFileFromCloud(entry.path)
                                            }
                                            if (moved.isSuccess) {
                                                rememberVersion(entry.noteId, onServer.version)
                                                queue.removeIfUnchanged(entry)
                                            } else {
                                                reason = reason ?: words.syncTrashMoveFailed
                                                queue.recordFailure(entry.path)
                                            }
                                        } else if (onServer?.deletedAt != null) {
                                            // Plik leży w serwerowym koszu, a tu ktoś
                                            // go właśnie zapisał. Wersja z serwera już
                                            // zapamiętana - ponowna wysyłka przywróci.
                                            rememberVersion(entry.noteId, onServer.version)
                                            retryNeeded = true
                                        } else if (onServer != null &&
                                            saveCodeVersionAlongside(entry.path, onServer)
                                        ) {
                                            rememberVersion(entry.noteId, onServer.version)
                                            conflicts += 1
                                            queue.removeIfUnchanged(entry)
                                        } else {
                                            // Kopia NIE powstała (albo serwer nie dał treści) -
                                            // wpis zostaje, wersja niezapamiętana; po wyczerpaniu
                                            // prób wpis widać jako utknięty.
                                            reason = reason ?: words.conflictCopyFailed
                                            queue.recordFailure(entry.path)
                                        }
                                    }
                                    "gone" -> {
                                        // Notatka trwale skasowana gdzie indziej.
                                        // Plik idzie do lokalnego kosza zamiast
                                        // wskrzeszać skasowane. Numer i wersja
                                        // schodzą dopiero PO udanym przeniesieniu -
                                        // bez tego plik zostawał w bibliotece jako
                                        // bezpański i wracał na serwer jako duplikat.
                                        val moved = runCatching {
                                            repository.trashFileFromCloud(entry.path)
                                        }
                                        if (moved.isSuccess) {
                                            codeIds.remove(entry.path)
                                            forgetVersion(entry.noteId)
                                            queue.removeIfUnchanged(entry)
                                        } else {
                                            reason = reason ?: words.syncTrashMoveFailed
                                            queue.recordFailure(entry.path)
                                        }
                                    }
                                    else -> {
                                        rememberVersion(entry.noteId, response.data.version)
                                        queue.removeIfUnchanged(entry)
                                        sent += 1
                                    }
                                }
                            }
                            is CloudClient.Result.Error -> if (!onError(entry, response)) break
                        }
                    }

                    else -> {
                        val document = runCatching { repository.readNote(entry.path) }.getOrNull()
                        if (document == null) {
                            // The note vanished from the tablet, for example into the bin.
                            // There is nothing to send, so we simply drop it from the queue.
                            queue.remove(entry.path)
                            continue
                        }

                        if (knownVersion(document.id) == 0 && !entry.reconciled &&
                            !baselineFetched()
                        ) {
                            // Jak wyżej: zero przed pierwszym pełnym pobraniem
                            // nie znaczy „nowa notatka", tylko „nie wiemy".
                            heldBack = true
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
                                folderId = outgoingFolderId(entry.path),
                                content = content,
                                baseVersion = knownVersion(document.id),
                            ),
                        )

                        when (response) {
                            is CloudClient.Result.Ok -> {
                                when (response.data.status) {
                                    "conflict" -> {
                                        val onServer = response.data.onServer
                                        if (onServer?.deletedAt != null && entry.reconciled) {
                                            // Wpis wziął się z uzgadniania biblioteki (np.
                                            // po przelogowaniu), a serwer trzyma notatkę
                                            // w koszu - czyli skasowano ją gdzie indziej.
                                            // Kosz wygrywa; wcześniej taka wysyłka
                                            // wskrzeszała wszystko po każdym logowaniu.
                                            // Rozliczenie dopiero PO udanym przeniesieniu.
                                            val moved = runCatching {
                                                repository.trashNoteFromCloud(document.id)
                                            }
                                            if (moved.isSuccess) {
                                                rememberVersion(document.id, onServer.version)
                                                queue.removeIfUnchanged(entry)
                                            } else {
                                                reason = reason ?: words.syncTrashMoveFailed
                                                queue.recordFailure(entry.path)
                                            }
                                        } else if (onServer?.deletedAt != null) {
                                            // Notatka leży w serwerowym koszu, a lokalnie
                                            // ktoś ją dalej pisze. Nie robimy kopii kosza -
                                            // zapamiętana wersja sprawia, że ponowna
                                            // wysyłka przywraca notatkę na serwerze.
                                            rememberVersion(document.id, onServer.version)
                                            retryNeeded = true
                                        } else if (saveVersionAlongside(entry.path, response.data)) {
                                            // The server copy just landed next to the local note.
                                            // Remember the server version, otherwise the next fetch
                                            // would treat it as new and overwrite the local edits
                                            // this conflict was meant to protect.
                                            onServer?.let {
                                                rememberVersion(document.id, it.version)
                                            }
                                            conflicts += 1
                                            queue.removeIfUnchanged(entry)
                                        } else {
                                            // Kopia NIE powstała - niczego nie udajemy: wpis
                                            // zostaje w kolejce, wersja niezapamiętana, konflikt
                                            // niepoliczony. Po wyczerpaniu prób wpis widać jako
                                            // utknięty na ekranie konta.
                                            reason = reason ?: words.conflictCopyFailed
                                            queue.recordFailure(entry.path)
                                        }
                                    }
                                    "gone" -> {
                                        // Notatka trwale skasowana gdzie indziej.
                                        // Lokalna kopia idzie do kosza - stamtąd
                                        // zawsze można ją wyjąć. Wersja i wpis
                                        // schodzą dopiero PO udanym przeniesieniu.
                                        val moved = runCatching {
                                            repository.trashNoteFromCloud(document.id)
                                        }
                                        if (moved.isSuccess) {
                                            forgetVersion(document.id)
                                            queue.removeIfUnchanged(entry)
                                        } else {
                                            reason = reason ?: words.syncTrashMoveFailed
                                            queue.recordFailure(entry.path)
                                        }
                                    }
                                    else -> {
                                        rememberVersion(document.id, response.data.version)
                                        if (sendAttachments(document.id, entry.path)) {
                                            queue.removeIfUnchanged(entry)
                                            sent += 1
                                        } else {
                                            // Treść doszła (wersja słusznie
                                            // zapamiętana), ale załącznik nie -
                                            // wpis zostaje i ponowna wysyłka
                                            // dośle brakujące po skrócie treści.
                                            reason = reason ?: words.syncAttachmentFailed
                                            queue.recordFailure(entry.path)
                                        }
                                    }
                                }
                            }
                            is CloudClient.Result.Error -> if (!onError(entry, response)) break
                        }
                    }
                }
            }
        } catch (stop: StopSending) {
            return@withContext stop.partial
        }

        StepResult(sent, conflicts, reason, worthRetrying, retryNeeded || heldBack)
    }

    /**
     * Zwraca, czy każdy załącznik z dysku naprawdę dojechał na serwer.
     * Kiedyś porażka przechodziła bez śladu i brakujący załącznik nie miał
     * już żadnej okazji, żeby pojechać - aż do następnej zmiany notatki.
     */
    private suspend fun sendAttachments(noteId: String, path: String): Boolean {
        val onDisk = runCatching { repository.attachmentNames(path) }.getOrDefault(emptyList())
        if (onDisk.isEmpty()) return true

        val onServer = when (val listing = client.listAttachments(noteId)) {
            is CloudClient.Result.Ok -> listing.data.attachments.associateBy { it.name }
            is CloudClient.Result.Error -> return false
        }

        var allSent = true
        for (name in onDisk) {
            val data = runCatching { repository.readAttachment(path, name) }.getOrNull()
            if (data == null) {
                // Jest na spisie, a nie daje się odczytać - nie udajemy,
                // że dojechał.
                allSent = false
                continue
            }
            val localHash = hash(data)
            if (onServer[name]?.hash == localHash) continue

            val outcome = client.sendAttachment(noteId, name, mimeFromName(name), data)
            if (outcome is CloudClient.Result.Error) allSent = false
        }
        return allSent
    }

    // --- Fetching ---

    private suspend fun fetchChanges(): StepResult = withContext(Dispatchers.IO) {
        // Bez katalogu na notatki nie ma dokąd ich zapisać. Wcześniej pobieranie
        // szło mimo to: każda notatka po cichu przepadała, a zakładka przesuwała
        // się na koniec - po wskazaniu katalogu nie pobierało się już nic.
        if (!repository.hasStore()) {
            return@withContext StepResult(
                reason = words.noNotesDirToSave,
                completed = false,
            )
        }

        var since = account.lastSync()
        var afterId: String? = null
        var fetched = 0
        var conflicts = 0
        var pages = 0
        var failures = 0
        var firstFailure: String? = null
        var sawEnd = false
        // Straż nieznanej podstawy rozbroiła wpis czekający w kolejce -
        // wersja już zapamiętana, więc warto od razu ponowić wysyłkę.
        var resolvedPending = false

        /*
          Ścieżki notatek po identyfikatorze - do straży przed nadpisaniem
          plików o nieznanej podstawie (patrz niżej). Spis idzie z Rooma,
          więc jest tani. Bez spisu nie ma straży - wtedy nie pobieramy
          wcale, zamiast nadpisywać w ciemno.
        */
        val localPathById = runCatching { repository.allNoteIds() }.getOrNull()
            ?.associate { (notePath, noteId) -> noteId to notePath }
            ?: return@withContext StepResult(
                reason = words.syncFailedSafe,
                worthRetrying = true,
                completed = false,
            )

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
                } ?: words.noteSaveOnDeviceFailed(note.title)
            }
            bookmark = minOf(bookmark, (note.updatedAt - 1).coerceAtLeast(0))
            blocked = true
        }

        // Notatka z lokalną zmianą czekającą na wysłanie nie może zostać
        // nadpisana treścią z serwera - konflikt ma rozstrzygnąć wysyłka
        // (kopią „kopia z chmury" obok), nie ciche pobranie. Zakładka staje
        // przed taką notatką, żeby następny przebieg po niej wrócił.
        fun deferred(note: ServerNote) {
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
                        conflicts = conflicts,
                        reason = response.message,
                        worthRetrying = response.worthRetrying,
                        completed = false,
                    )
                }

                is CloudClient.Result.Ok -> {
                    pages += 1
                    val page = response.data

                    // Świeży zrzut na każdą stronę: wysyłka mogła właśnie
                    // opróżnić część kolejki, a autozapis dołożyć nowe wpisy.
                    val pendingByNote = runCatching { queue.all() }.getOrDefault(emptyList())
                        .groupBy { it.noteId }

                    for (fromServer in page.notes) {
                        /*
                          Notatkę z czekającą wysyłką i ZNANĄ wersją zostawiamy
                          wysyłce - konflikt rozstrzygnie kopią obok, nie ciche
                          pobranie. Przy NIEZNANEJ wersji odroczenie nie miało
                          końca: wpis blokował zapamiętanie wersji, a wysyłka
                          szła z zerową podstawą, którą serwer przyjmuje
                          bezwarunkowo - nadpisując nowszą wersję. Taka notatka
                          przechodzi niżej, do straży nieznanej podstawy, która
                          zapamiętuje wersję i odblokowuje obie strony.
                          Czekające kasowania i nagrobki rozstrzyga wysyłka.
                        */
                        val pending = pendingByNote[fromServer.id].orEmpty()
                        val pendingDeletion = pending.any {
                            it.kind == QueueEntry.KIND_TRASH ||
                                it.kind == QueueEntry.KIND_PURGE ||
                                it.kind == QueueEntry.KIND_FOLDER_DELETE
                        }
                        if (pending.isNotEmpty() &&
                            (pendingDeletion || fromServer.deletedAt != null ||
                                knownVersion(fromServer.id) > 0)
                        ) {
                            deferred(fromServer)
                            continue
                        }

                        // Skasowana gdzie indziej (na stronie albo na drugim
                        // urządzeniu) - tu też idzie do kosza. Z kosza zawsze
                        // można ją wyjąć, więc niczego nie tracimy. Nagrobek
                        // idzie PRZED strażą wersji: rozjazd zapamiętanych
                        // wersji nie ma prawa zostawić przy życiu notatki,
                        // której serwer już nie ma.
                        if (fromServer.deletedAt != null) {
                            val moved = runCatching {
                                if (fromServer.kind == KIND_CODE) {
                                    trashCodeFileFromCloud(fromServer.id)
                                } else {
                                    repository.trashNoteFromCloud(fromServer.id)
                                }
                            }
                            if (moved.isSuccess) {
                                rememberVersion(fromServer.id, fromServer.version)
                                settled(fromServer.updatedAt)
                            } else {
                                // Zapamiętana wersja udawałaby, że nagrobek
                                // zadziałał - notatka zostałaby przy życiu
                                // na zawsze. Zakładka staje, następny przebieg
                                // spróbuje jeszcze raz.
                                Log.w(
                                    "Kajet",
                                    "Nie udało się wyrzucić do kosza ${fromServer.id}",
                                    moved.exceptionOrNull(),
                                )
                                failed(fromServer, moved.exceptionOrNull())
                            }
                            continue
                        }

                        // A note we sent ourselves a moment ago need not be read
                        // back. We recognise it by the remembered version.
                        if (knownVersion(fromServer.id) >= fromServer.version) {
                            settled(fromServer.updatedAt)
                            continue
                        }

                        val content = fromServer.content
                        if (content == null) {
                            settled(fromServer.updatedAt)
                            continue
                        }

                        /*
                          Plik jest, a wersji nie znamy - podstawa nieznana
                          (reinstalacja, wyczyszczenie danych, wylogowanie).
                          Serwerowa kopia nie ma prawa po cichu nadpisać
                          takiego pliku: zgodną treść przyjmujemy za swoją,
                          a rozjazd rozstrzygamy jak zwykły konflikt - kopia
                          serwera obok, lokalny plik nietknięty.
                        */
                        val unknownBase = if (knownVersion(fromServer.id) == 0) {
                            localPathById[fromServer.id]
                        } else {
                            null
                        }
                        if (unknownBase != null && fromServer.kind != KIND_CODE) {
                            val local = runCatching { repository.readNote(unknownBase) }.getOrNull()
                            val serverDoc = runCatching { NoteCodec.decodeNote(content) }.getOrNull()
                            when {
                                local == null || serverDoc == null ->
                                    failed(fromServer, null)
                                samePayload(local, serverDoc) -> {
                                    // Zgodna treść to jeszcze nie komplet:
                                    // załączniki też muszą być na miejscu,
                                    // inaczej zapamiętana wersja zamknęłaby
                                    // im drogę na zawsze.
                                    if (fetchAttachments(
                                            fromServer.id,
                                            unknownBase,
                                            fromServer.attachments,
                                        )
                                    ) {
                                        rememberVersion(fromServer.id, fromServer.version)
                                        if (pending.isNotEmpty()) resolvedPending = true
                                        settled(fromServer.updatedAt)
                                    } else {
                                        failed(
                                            fromServer,
                                            IllegalStateException(words.syncAttachmentFailed),
                                        )
                                    }
                                }
                                saveServerCopyAlongside(unknownBase, fromServer, serverDoc) -> {
                                    rememberVersion(fromServer.id, fromServer.version)
                                    if (pending.isNotEmpty()) resolvedPending = true
                                    conflicts += 1
                                    fetched += 1
                                    settled(fromServer.updatedAt)
                                }
                                else -> failed(fromServer, null)
                            }
                            continue
                        }

                        if (fromServer.kind == KIND_CODE) {
                            val outcome = runCatching { writeCodeFileFromCloud(fromServer, content) }
                            val write = outcome.getOrNull()
                            if (write != null) {
                                // Gwiazdka z metadanych serwera, tak samo jak
                                // przy zwykłej notatce - strona przełącza ją
                                // bez ruszania treści pliku.
                                runCatching {
                                    repository.setFileFavoriteFromCloud(
                                        write.path,
                                        fromServer.favorite,
                                    )
                                }
                                rememberVersion(fromServer.id, fromServer.version)
                                if (pending.isNotEmpty()) resolvedPending = true
                                if (write.conflictCopy) conflicts += 1
                                fetched += 1
                                settled(fromServer.updatedAt)
                            } else {
                                Log.w(
                                    "Kajet",
                                    "Nie udało się zapisać pliku z kodem ${fromServer.id} z serwera",
                                    outcome.exceptionOrNull(),
                                )
                                failed(fromServer, outcome.exceptionOrNull())
                            }
                            continue
                        }

                        val target = folderTargetFor(fromServer.folderId)
                        val outcome = runCatching {
                            repository.writeNoteFromCloud(
                                // Gwiazdka z metadanych serwera, nie z treści:
                                // strona przełącza ją bez przepisywania treści,
                                // więc tylko metadana jest zawsze aktualna.
                                NoteCodec.decodeNote(content).copy(favorite = fromServer.favorite),
                                targetFolder = target ?: "",
                            )
                        }
                        val saved = outcome.getOrNull()

                        if (saved != null) {
                            // Notatka przeniesiona między folderami na innym
                            // urządzeniu przenosi się też tutaj.
                            if (target != null && saved.substringBeforeLast('/', "") != target) {
                                runCatching { repository.moveNoteFromCloud(fromServer.id, target) }
                            }
                            if (fetchAttachments(fromServer.id, saved, fromServer.attachments)) {
                                rememberVersion(fromServer.id, fromServer.version)
                                fetched += 1
                                settled(fromServer.updatedAt)
                            } else {
                                // Załącznik nie dojechał - notatka nie liczy
                                // się za pobraną. Zapis po identyfikatorze jest
                                // idempotentny, więc ponowienie nie dubluje.
                                failed(
                                    fromServer,
                                    IllegalStateException(words.syncAttachmentFailed),
                                )
                            }
                        } else {
                            Log.w(
                                "Kajet",
                                "Nie udało się zapisać notatki ${fromServer.id} z serwera",
                                outcome.exceptionOrNull(),
                            )
                            failed(fromServer, outcome.exceptionOrNull())
                        }
                    }

                    // Brak postępu kursora przy hasMore=true kręciłby tą pętlą
                    // bez końca, trzymając zamek synchronizacji (wisząca
                    // „Synchronizuję…" i zamrożony ekran). Wychodzimy i następny
                    // przebieg spróbuje od zapamiętanej zakładki.
                    val stalled = page.upTo <= 0 ||
                        (page.upTo == since && page.upToId == afterId)

                    if (page.upTo > 0) {
                        since = page.upTo
                        afterId = page.upToId
                        account.rememberSync(bookmark)
                    }

                    if (!page.hasMore) {
                        sawEnd = true
                        break
                    }
                    if (stalled || pages >= MAX_FETCH_PAGES) break
                }
            }
        }

        repository.refresh()
        StepResult(
            count = fetched,
            conflicts = conflicts,
            reason = firstFailure?.let { first ->
                if (failures > 1) words.andMoreNotes(first, failures) else first
            },
            retryNeeded = resolvedPending,
            completed = sawEnd && failures == 0,
        )
    }

    // --- Notatki skasowane na serwerze na zawsze ---

    private data class DeletionStep(
        /** Czy serwer w ogóle zna punkt z nagrobkami. Starszy odpowiada 404. */
        val tombstonesWork: Boolean = false,
        val erased: Int = 0,
        val trashed: Int = 0,
        val reason: String? = null,
        val worthRetrying: Boolean = false,
    )

    /**
     * Nagrobki z serwera: notatki skasowane na zawsze od ostatniego udanego
     * pobrania.
     *
     * Reguła kasowania jest jedna i pilnuje jej [LibraryRepository]: co leży w
     * lokalnym koszu, znika doszczętnie; co jest widoczne w bibliotece, idzie
     * najwyżej do kosza. Sama synchronizacja nie kasuje z dysku niczego, czego
     * człowiek do kosza nie wyrzucił.
     *
     * Znacznik czasu przesuwa się DOPIERO po przejściu całego spisu. Przebieg
     * urwany w połowie zaczyna następnym razem od tego samego miejsca -
     * powtórne zastosowanie nagrobka niczego nie psuje, a przeskoczenie
     * choćby jednego zostawiłoby notatkę w koszu na zawsze.
     */
    private suspend fun fetchServerDeletions(): DeletionStep = withContext(Dispatchers.IO) {
        if (!repository.hasStore()) return@withContext DeletionStep()

        val startedAt = account.lastDeletedSync()
        var since = startedAt
        var afterId: String? = null
        var pages = 0
        var erased = 0
        var trashed = 0
        var failures = 0

        // Rejestr plików z kodem i zawartość kosza czytamy raz na przebieg, nie
        // raz na nagrobek: jedno i drugie oznacza przejście po wszystkim, co
        // leży w koszu, razem z odczytem każdego pliku notatki przez SAF.
        val codePathById = runCatching { codeIds.pathsById() }.getOrDefault(emptyMap())
        val trash = runCatching { repository.readTrashContents() }.getOrDefault(TrashContents())

        while (true) {
            when (val response = client.fetchDeleted(since, afterId)) {
                is CloudClient.Result.Error -> {
                    // Starszy serwer nie zna tego adresu. To nie jest awaria -
                    // po prostu zostaje droga zapasowa.
                    if (response.notFound) return@withContext DeletionStep(tombstonesWork = false)
                    if (response.mustSignIn) _state.value = SyncState.MustSignIn
                    return@withContext DeletionStep(
                        tombstonesWork = true,
                        erased = erased,
                        trashed = trashed,
                        reason = response.message,
                        worthRetrying = response.worthRetrying,
                    )
                }

                is CloudClient.Result.Ok -> {
                    pages += 1
                    val page = response.data

                    // Świeży zrzut kolejki na każdą stronę: notatka z czekającą
                    // wysyłką sama się rozstrzygnie przy wysyłce (serwer odpowie
                    // „gone"), więc nie ruszamy jej tutaj.
                    val queued = runCatching { queue.all().map { it.noteId }.toSet() }
                        .getOrDefault(emptySet())

                    for (id in page.ids) {
                        if (id.isBlank() || id in queued) continue
                        val codePath = codePathById[id]
                        val outcome = runCatching {
                            if (codePath != null) {
                                repository.applyServerCodeDeletion(codePath, trash)
                            } else {
                                repository.applyServerDeletion(id, trash)
                            }
                        }.onFailure {
                            failures += 1
                            Log.w("Kajet", "Nie udało się posprzątać po skasowanej $id", it)
                        }.getOrNull()

                        when (outcome) {
                            ServerDeletion.ERASED -> {
                                erased += 1
                                forgetVersion(id)
                            }
                            ServerDeletion.TRASHED -> {
                                trashed += 1
                                forgetVersion(id)
                            }
                            // Nie ma jej tutaj albo krok się nie udał. Wersję
                            // zapominamy tylko wtedy, gdy naprawdę coś zaszło -
                            // inaczej nagrobek po nieudanej próbie wyglądałby
                            // na załatwiony.
                            else -> Unit
                        }
                    }

                    val stalled = page.upTo <= 0 ||
                        (page.upTo == since && page.upToId == afterId)

                    if (!page.hasMore) {
                        if (page.upTo > since) since = page.upTo
                        break
                    }
                    if (stalled || pages >= MAX_FETCH_PAGES) {
                        // Kursor stoi w miejscu - dalsze pytanie kręciłoby pętlą
                        // bez końca. Znacznika nie przesuwamy: spis nie został
                        // przejrzany do końca.
                        return@withContext DeletionStep(
                            tombstonesWork = true,
                            erased = erased,
                            trashed = trashed,
                            worthRetrying = true,
                        )
                    }
                    since = page.upTo
                    afterId = page.upToId
                }
            }
        }

        if (erased > 0 || trashed > 0) {
            Log.i(
                "Kajet",
                "Serwer skasował na zawsze: $erased zniknęło z urządzenia, " +
                    "$trashed poszło do kosza",
            )
            repository.refresh()
        }

        // Nieudane sprzątnięcie nie może przesunąć znacznika - nagrobek
        // zniknąłby z oczu na zawsze, a notatka zostałaby przy życiu.
        // Powtórka niczego nie psuje: następny przebieg zaczyna od tego
        // samego miejsca.
        if (failures > 0) {
            return@withContext DeletionStep(
                tombstonesWork = true,
                erased = erased,
                trashed = trashed,
                reason = words.syncTrashMoveFailed,
                worthRetrying = true,
            )
        }

        // Dopiero tutaj: cały spis przeszedł od początku do końca.
        if (since > startedAt) account.rememberDeletedSync(since)

        DeletionStep(tombstonesWork = true, erased = erased, trashed = trashed)
    }

    /**
     * Droga zapasowa: uzgodnienie z pełnym spisem identyfikatorów konta.
     *
     * Potrzebna dla tego, po czym nagrobka nie ma - notatek skasowanych zanim
     * serwer nauczył się je zostawiać, i serwera, który punktu z nagrobkami
     * jeszcze nie zna. Kosztuje pobranie całego spisu konta, więc chodzi
     * rzadko: raz po aktualizacji i przy starszym serwerze.
     *
     * Ta sama reguła co przy nagrobkach: notatka z lokalnego kosza znika
     * doszczętnie, notatka widoczna w bibliotece idzie najwyżej do kosza.
     * Kosz przeglądamy osobno, bo jest poza spisem - i to właśnie tego tu
     * brakowało: notatka wyrzucona do kosza była dla uzgadniania niewidoczna
     * i zostawała w nim na zawsze, choć na serwerze dawno jej nie było.
     *
     * Nietknięte zostają notatki, których serwer nigdy nie znał (brak
     * zapamiętanej wersji), i te z czekającą wysyłką.
     *
     * Zwraca false, kiedy przebieg nie doszedł do skutku - bez pełnego spisu
     * z serwera nie wolno na jego podstawie niczego kasować ani uznać, że
     * zaległości są już posprzątane.
     */
    private suspend fun reconcileServerDeletions(): Boolean = withContext(Dispatchers.IO) {
        if (!repository.hasStore()) return@withContext false
        val serverIds = fetchAllServerIds() ?: return@withContext false

        val queued = runCatching { queue.all().map { it.noteId }.toSet() }
            .getOrDefault(emptySet())
        val trash = runCatching { repository.readTrashContents() }.getOrDefault(TrashContents())
        var erased = 0
        var trashed = 0
        var failures = 0

        suspend fun settle(id: String, codePath: String?) {
            if (id.isBlank()) return
            if (knownVersion(id) <= 0) return
            if (id in serverIds) return
            if (id in queued) return
            val outcome = runCatching {
                if (codePath != null) {
                    repository.applyServerCodeDeletion(codePath, trash)
                } else {
                    repository.applyServerDeletion(id, trash)
                }
            }.onFailure {
                failures += 1
                Log.w("Kajet", "Nie udało się posprzątać po skasowanej $id", it)
            }.getOrNull()

            when (outcome) {
                ServerDeletion.ERASED -> { erased += 1; forgetVersion(id) }
                ServerDeletion.TRASHED -> { trashed += 1; forgetVersion(id) }
                else -> Unit
            }
        }

        // Notatki widoczne w bibliotece.
        for ((_, noteId) in runCatching { repository.allNoteIds() }.getOrDefault(emptyList())) {
            settle(noteId, codePath = null)
        }

        // Notatki leżące w koszu - poza spisem, więc osobnym przejściem.
        for (noteId in trash.noteSlots.keys) {
            settle(noteId, codePath = null)
        }

        // Pliki z kodem: widoczne i te z kosza.
        val codePaths = runCatching { repository.allCodeFilePaths() }.getOrDefault(emptyList()) +
            trash.codeSlots.keys
        for (path in codePaths.distinct()) {
            val id = codeIds.existingIdFor(path) ?: continue
            settle(id, codePath = path)
        }

        if (erased > 0 || trashed > 0) {
            Log.i(
                "Kajet",
                "Uzgodnienie ze spisem serwera: $erased zniknęło z urządzenia, " +
                    "$trashed poszło do kosza",
            )
            repository.refresh()
        }
        // Porażka choć jednego sprzątnięcia = przebieg niedokończony. Nie
        // wolno na jego podstawie postawić znacznika zamiatania - zamknąłby
        // drogę zapasową z niezałatwioną zaległością.
        failures == 0
    }

    /**
     * Pełny spis id wszystkiego, co konto ma na serwerze - razem z nagrobkami.
     * Null, gdy spisu nie udało się dostać W CAŁOŚCI: na niepełnym spisie
     * nie wolno opierać żadnego kasowania.
     */
    private suspend fun fetchAllServerIds(): Set<String>? {
        val ids = mutableSetOf<String>()
        var since = 0L
        var afterId: String? = null
        var pages = 0
        while (true) {
            when (val response = client.fetchChanges(since, afterId, withContent = false)) {
                is CloudClient.Result.Error -> return null
                is CloudClient.Result.Ok -> {
                    pages += 1
                    val page = response.data
                    for (note in page.notes) ids += note.id
                    if (!page.hasMore) return ids
                    val stalled = page.upTo <= 0 ||
                        (page.upTo == since && page.upToId == afterId)
                    if (stalled || pages >= MAX_FETCH_PAGES) return null
                    since = page.upTo
                    afterId = page.upToId
                }
            }
        }
    }

    /**
     * Zwraca, czy KAŻDY załącznik z serwera naprawdę wylądował na dysku.
     * Kiedyś porażka przechodziła bez śladu: notatka liczyła się za pobraną,
     * wersja zostawała zapamiętana i brakującego załącznika nikt już nie
     * dociągał.
     */
    private suspend fun fetchAttachments(
        noteId: String,
        path: String,
        listed: List<AttachmentInfo> = emptyList(),
    ): Boolean {
        val onServer = listed.ifEmpty {
            when (val listing = client.listAttachments(noteId)) {
                is CloudClient.Result.Ok -> listing.data.attachments
                is CloudClient.Result.Error -> return false
            }
        }
        if (onServer.isEmpty()) return true

        var allSaved = true
        for (info in onServer) {
            val local = runCatching { repository.readAttachment(path, info.name) }.getOrNull()
            if (local != null && hash(local) == info.hash) continue

            val bytes = when (val downloaded = client.fetchAttachment(noteId, info.name)) {
                is CloudClient.Result.Ok -> downloaded.data
                is CloudClient.Result.Error -> {
                    allSaved = false
                    continue
                }
            }
            val saved = runCatching {
                repository.putAttachment(path, info.name, bytes, info.mime.ifBlank { mimeFromName(info.name) })
            }
            if (saved.isFailure) allSaved = false
        }
        return allSaved
    }

    private suspend fun saveVersionAlongside(path: String, response: SaveResponse): Boolean {
        val fromServer = response.onServer ?: return false
        val content = fromServer.content ?: return false
        val document = runCatching { NoteCodec.decodeNote(content) }.getOrNull() ?: return false
        return saveServerCopyAlongside(path, fromServer, document)
    }

    /**
     * Kopia serwerowej wersji obok lokalnego pliku. Zwraca, czy naprawdę
     * powstała - rozliczyć konflikt (zapamiętać wersję, zdjąć wpis z kolejki)
     * wolno dopiero wtedy. Kiedyś porażka przechodziła tędy bez śladu:
     * konflikt liczył się jako obsłużony, a edycja z drugiego urządzenia
     * znikała bez komunikatu.
     */
    private suspend fun saveServerCopyAlongside(
        path: String,
        fromServer: ServerNote,
        document: NoteDocument,
    ): Boolean {
        val whenChanged = serverVersionStamp(fromServer.updatedAt)

        val saved = runCatching {
            repository.writeNoteFromCloud(
                document.copy(
                    // A new identifier, because this is meant to be a separate
                    // note rather than a swap of the one somebody is sitting at.
                    id = java.util.UUID.randomUUID().toString(),
                    title = words.cloudCopyOf(document.title, whenChanged),
                ),
                targetFolder = path.substringBeforeLast('/', ""),
            )
        }.getOrNull() ?: return false

        // Attachments live under the original server note id; the conflict copy
        // is a new local note that still points at assets/... in its content.
        if (!fetchAttachments(fromServer.id, saved, fromServer.attachments)) {
            // Kopia już stoi i cofnąć jej nie wolno - ponowienie mnożyłoby
            // kopie „(kopia z chmury)". Brak załącznika zostaje w dzienniku.
            Log.w("Kajet", "Kopia konfliktu ${fromServer.id} bez części załączników")
        }
        return true
    }

    /**
     * Kiedy serwer ostatnio widział tę wersję - do tytułu kopii konfliktu,
     * żeby dwie kopie z różnych dni dało się odróżnić bez otwierania.
     */
    private fun serverVersionStamp(updatedAt: Long): String =
        java.text.SimpleDateFormat(
            "d MMMM, HH:mm",
            // Nazwa miesiąca musi iść za wyborem języka w Kajecie - inaczej
            // w angielskim tytule kopii siedziało „5 sierpnia”.
            if (words.english) java.util.Locale.UK else java.util.Locale.forLanguageTag("pl-PL"),
        ).format(java.util.Date(updatedAt))

    /**
     * Ta sama treść mimo nieznanej podstawy. Gwiazdka nie liczy się do
     * rozjazdu - strona przełącza ją bez przepisywania treści notatki.
     */
    private fun samePayload(local: NoteDocument, fromServer: NoteDocument): Boolean =
        local.copy(favorite = false) == fromServer.copy(favorite = false)

    // --- Code files as CODE notes ---

    private data class ServerCode(val language: String?, val source: String)

    /**
     * Treść notatki CODE w kształcie, który rozumie strona (code-note.ts).
     * Celowo bez znaczników czasu: ta sama zawartość pliku daje zawsze ten sam
     * zapis, więc serwer rozpoznaje ponowną wysyłkę jako „bez zmian".
     */
    private fun codeNoteContent(noteId: String, path: String, source: String): String {
        val fileName = path.substringAfterLast('/')
        return buildJsonObject {
            put("format", 1)
            put("id", noteId)
            put("kind", "code")
            put("title", fileName)
            putJsonArray("tags") {}
            put("favorite", false)
            putJsonObject("code") {
                put("language", outgoingLanguageId(path, fileName))
                put("source", source)
            }
        }.toString()
    }

    /**
     * Jakim językiem nazwać plik jadący na serwer.
     *
     * Zwykle wystarczy rozszerzenie. Jest jednak jedna para, której rozszerzenie
     * nie rozstrzyga: .sql to i SQLite, i MySQL. Wygrywa wtedy język, którym
     * serwer nazwał tę notatkę wcześniej - inaczej poprawka literówki zrobiona
     * na tablecie przestawiałaby notatkę MySQL na SQLite.
     *
     * Pamięć obowiązuje tylko dopóki pasuje do nazwy pliku. Kto przemianuje
     * zadanie.sql na zadanie.py, dostaje Pythona i tak ma być - zmiana
     * rozszerzenia jest świadomą zmianą języka.
     */
    private fun outgoingLanguageId(path: String, fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val remembered = CodeLanguage.fromServerId(codeIds.languageFor(path))
            ?.takeIf { extension in it.extensions }
        val language = remembered ?: CodeLanguage.fromExtension(fileName)
        return language?.serverRuntime ?: language?.id ?: "text"
    }

    private fun parseCodeContent(content: String): ServerCode? = runCatching {
        val root = CloudClient.json.parseToJsonElement(content).jsonObject
        val code = root["code"]?.jsonObject ?: return@runCatching null
        ServerCode(
            language = code["language"]?.jsonPrimitive?.content,
            source = code["source"]?.jsonPrimitive?.content.orEmpty(),
        )
    }.getOrNull()

    /** Nazwa pliku dla notatki CODE z serwera: tytuł, a rozszerzenie z języka. */
    private fun codeFileName(title: String, languageId: String?): String {
        val cleaned = title.trim()
            .replace(Regex("[/\\\\:*?\"<>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { words.codeWord }
        if (CodeLanguage.fromExtension(cleaned) != null) return cleaned
        val language = CodeLanguage.fromServerId(languageId)
        return "$cleaned.${language?.extensions?.first() ?: "txt"}"
    }

    /**
     * Wynik zapisu pliku z serwera: pod jaką ścieżką plik ostatecznie leży
     * i czy rozjazd trzeba było rozstrzygnąć kopią konfliktu obok - pętla
     * pobierania liczy po tym konflikty, a ścieżką trafia gwiazdka.
     */
    private data class CodeWrite(val path: String, val conflictCopy: Boolean = false)

    private suspend fun writeCodeFileFromCloud(fromServer: ServerNote, content: String): CodeWrite {
        val write = storeCodeFileFromCloud(fromServer, content)
        // Język zapisujemy PO udanym zapisie pliku i pod ścieżką, która
        // naprawdę powstała - przy kopii konfliktu bywa inna niż zgadywana.
        codeIds.rememberLanguage(write.path, parseCodeContent(content)?.language)
        return write
    }

    private suspend fun storeCodeFileFromCloud(fromServer: ServerNote, content: String): CodeWrite {
        val code = parseCodeContent(content)
            ?: throw FormatException(words.codeNoteUnknownShape(fromServer.title))

        val existingPath = codeIds.pathFor(fromServer.id)
        if (existingPath != null) {
            /*
              Plik jest, a wersji nie znamy - podstawa nieznana. Ta sama
              reguła co przy notatkach (straż w fetchChanges): serwer nie ma
              prawa po cichu nadpisać takiego pliku. Zgodną treść przyjmujemy
              za swoją, rozjazd dostaje kopię serwera obok, lokalny plik
              zostaje nietknięty. Nieczytelny plik zatrzymuje notatkę
              w pobieraniu - w ciemno nie nadpisujemy.
            */
            if (knownVersion(fromServer.id) == 0) {
                if (repository.readText(existingPath) == code.source) return CodeWrite(existingPath)
                if (!saveCodeVersionAlongside(existingPath, fromServer)) {
                    throw IllegalStateException(words.conflictCopyFailed)
                }
                return CodeWrite(existingPath, conflictCopy = true)
            }
            val written = runCatching { repository.writeTextFromCloud(existingPath, code.source) }
            if (written.isSuccess) return CodeWrite(existingPath)
            // Pliku już nie ma pod zapamiętaną ścieżką - zakładamy go od nowa.
            codeIds.remove(existingPath)
        }

        /*
          Mapowanie przepadło (reinstalacja, wyczyszczenie danych), ale plik
          o tej nazwie i tej samej treści już leży w bibliotece - to ta sama
          notatka. Wiążemy je z powrotem, zamiast tworzyć obok duplikat pod
          nowym numerem.
        */
        val parent = folderTargetFor(fromServer.folderId) ?: ""
        val fileName = codeFileName(fromServer.title, code.language)
        val candidate = if (parent.isEmpty()) fileName else "$parent/$fileName"
        val existingText = runCatching { repository.readText(candidate) }.getOrNull()
        if (existingText == code.source) {
            codeIds.bind(candidate, fromServer.id)
            return CodeWrite(candidate)
        }

        /*
          Plik o tej nazwie jest, treść inna, numeru nie nosi, a wersji
          notatki nie znamy - to ten sam plik po utracie rejestru. Jak przy
          notatkach: lokalny plik przejmuje tożsamość, treść serwera ląduje
          obok jako kopia konfliktu. Kiedyś treść serwera dostawała bezimienne
          „(2)" wraz z numerem, a lokalny plik jechał potem na serwer jako
          duplikat.
        */
        if (existingText != null &&
            knownVersion(fromServer.id) == 0 &&
            codeIds.existingIdFor(candidate) == null
        ) {
            if (!saveCodeVersionAlongside(candidate, fromServer)) {
                throw IllegalStateException(words.conflictCopyFailed)
            }
            codeIds.bind(candidate, fromServer.id)
            return CodeWrite(candidate, conflictCopy = true)
        }

        val path = repository.createTextFileFromCloud(
            parent = parent,
            fileName = fileName,
            content = code.source,
        )
        codeIds.bind(path, fromServer.id)
        return CodeWrite(path)
    }

    private suspend fun trashCodeFileFromCloud(noteId: String) {
        val path = codeIds.pathFor(noteId) ?: return
        // Wyjątek idzie wyżej - połknięty tutaj sprawiał, że nagrobek liczył
        // się za obsłużony, choć plik dalej leżał w bibliotece.
        repository.trashFileFromCloud(path)
        // Mapowanie zostaje: przywrócenie z lokalnego kosza ma wskrzesić tę
        // samą notatkę na serwerze, a nie założyć duplikat pod nowym numerem.
    }

    /** Odpowiednik [saveServerCopyAlongside] dla pliku z kodem: kopia obok. */
    private suspend fun saveCodeVersionAlongside(path: String, onServer: ServerNote): Boolean {
        val content = onServer.content ?: return false
        val code = parseCodeContent(content) ?: return false
        val fileName = path.substringAfterLast('/')
        val stem = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "")
        // Dwukropek z godziny nie przejdzie w nazwie pliku - stąd podkreślnik.
        val whenChanged = serverVersionStamp(onServer.updatedAt).replace(':', '_')
        val copyName = if (extension.isEmpty()) {
            words.cloudCopyOf(stem, whenChanged)
        } else {
            words.cloudCopyOf(stem, whenChanged) + ".$extension"
        }
        return runCatching {
            repository.createTextFileFromCloud(
                parent = path.substringBeforeLast('/', ""),
                fileName = copyName,
                content = code.source,
            )
        }.isSuccess
    }

    // --- Remembered versions ---

    private fun knownVersion(noteId: String): Int = versions.getInt(noteId, 0)

    /**
     * Czy od zalogowania doszło do końca choć jedno pełne pobranie. Przed nim
     * wysyłka z baseVersion = 0 to strzał w ciemno: zero może znaczyć „nowa
     * notatka", ale równie dobrze „nie znamy stanu serwera" - a serwer przy
     * zerze przyjmuje bezwarunkowo i wysyłka nadpisałaby nowszą wersję.
     * Znacznik żyje w spisie wersji, więc wylogowanie czyści go razem z nim.
     */
    private fun baselineFetched(): Boolean = versions.getBoolean(KEY_BASELINE_FETCH, false)

    private fun rememberVersion(noteId: String, version: Int) {
        versions.edit().putInt(noteId, version).apply()
    }

    private fun forgetVersion(noteId: String) {
        versions.edit().remove(noteId).apply()
    }

    /**
     * Po notatce nie ma już śladu na dysku - niech zniknie też z pamięci
     * chmury. Woła to biblioteka przez `onNoteErased`, jednym wejściem dla
     * każdej drogi kasowania.
     *
     * Zgłoszenia kasowania (kosz i trwałe) zostają w kolejce nietknięte: to
     * właśnie one mają dojechać na serwer. Znikają wpisy z treścią do
     * wysłania - notatki już nie ma, więc nie ma czego wysyłać, a taki wpis
     * przy najbliższym przebiegu próbowałby ją odtworzyć.
     */
    fun forgetNote(noteId: String) {
        if (noteId.isBlank()) return
        forgetVersion(noteId)
        runCatching {
            queue.all()
                .filter {
                    it.noteId == noteId &&
                        it.kind != QueueEntry.KIND_PURGE &&
                        it.kind != QueueEntry.KIND_TRASH
                }
                .forEach { queue.remove(it.path) }
        }
    }

    /** To samo dla pliku z kodem, którego chmura pilnuje po ścieżce. */
    fun forgetCodePath(path: String) {
        if (path.isBlank()) return
        codeIds.existingIdFor(path)?.let { forgetNote(it) }
        // Numer przestaje obowiązywać: nowy plik pod tą samą nazwą ma dostać
        // świeży, a nie odziedziczyć numer po skasowanej notatce i ją wskrzesić.
        codeIds.remove(path)
        runCatching { queue.remove(path) }
    }

    /** Utknięte wpisy dostają nową pulę prób i od razu rusza synchronizacja. */
    fun retryStuck() {
        queue.retryStuck()
        _stuck.value = queue.stuckCount()
        syncSoon()
    }

    fun forgetAllVersions() {
        versions.edit().clear().apply()
        // Po wylogowaniu tożsamości plików z kodem też przestają obowiązywać:
        // na innym koncie te same ścieżki nie mogą nieść starych numerów.
        codeIds.clear()
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

    private companion object {
        /** Rodzaj notatki, pod którym pliki z kodem żyją na serwerze. */
        const val KIND_CODE = "CODE"

        /**
         * Znacznik w spisie wersji: jednorazowe pełne pobranie po tym, jak
         * aplikacja nauczyła się synchronizować pliki z kodem. Klucz nie
         * zderzy się z żadnym identyfikatorem notatki.
         */
        const val KEY_FULL_FETCH_FOR_CODE = "#pelne-pobranie-dla-kodu"

        /**
         * Znacznik w spisie wersji: od zalogowania doszło do końca choć jedno
         * pełne pobranie - patrz [baselineFetched].
         */
        const val KEY_BASELINE_FETCH = "#pierwsze-pobranie-za-nami"

        /** Znacznik w spisie wersji: folder był już kiedyś uzgodniony z serwerem. */
        const val KEY_FOLDER_PREFIX = "#folder:"

        /**
         * Znacznik w spisie wersji: pełne porównanie ze spisem serwera poszło
         * już raz, odkąd serwer zostawia nagrobki. Notatki skasowane wcześniej
         * nie mają nagrobka i tylko to jedno zamiatanie potrafi je wyłapać;
         * potem nagrobki wystarczają i nie ma po co ciągnąć całego spisu przy
         * każdej synchronizacji.
         */
        const val KEY_SWEEP_AFTER_TOMBSTONES = "#zamiatanie-po-nagrobkach"

        /** Bezpiecznik: więcej stron w jednym przebiegu nie ma prawa być. */
        const val MAX_FETCH_PAGES = 500
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
