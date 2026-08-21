package wojtoteka.ovh.kajet.cloud

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class SendQueue(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /*
      Pasek zapisu musi wiedzieć, kiedy wpis wszedł albo zszedł z kolejki.
      Sama zawartość zostaje w SharedPreferences; ten licznik jest tylko
      sygnałem „spójrz jeszcze raz", bez drugiego spisu ścieżek.
    */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    @Synchronized
    fun add(
        path: String,
        noteId: String,
        kind: String = QueueEntry.KIND_NOTE,
        reconciled: Boolean = false,
    ) {
        val current = read().toMutableMap()
        val previous = current[path]
        current[path] = QueueEntry(
            path = path,
            noteId = noteId,
            kind = kind,
            // Swiezy zapis to nowa tresc - dostaje pelna pule prob. Utknieta
            // notatka odwiesza sie wiec sama, gdy tylko czlowiek ja zmieni.
            failedAttempts = 0,
            addedAt = previous?.addedAt ?: System.currentTimeMillis(),
            revision = (previous?.revision ?: 0) + 1,
            // Świeży zapis użytkownika zdejmuje znacznik uzgadniania: taka
            // notatka ma prawo wygrać z serwerowym koszem.
            reconciled = reconciled && previous == null,
        )
        write(current)
    }

    /**
     * Kolejkuje kasowanie po stronie serwera. Wpis nie ma ścieżki (notatki już
     * nie ma w bibliotece), więc kluczem jest sam identyfikator. Trwałe
     * kasowanie wygrywa z koszem: skoro użytkownik skasował na stałe, samo
     * odłożenie do serwerowego kosza już nie wystarczy.
     */
    @Synchronized
    fun addDeletion(noteId: String, purge: Boolean) {
        val key = "${QueueEntry.DELETION_PREFIX}$noteId"
        val current = read().toMutableMap()
        val previous = current[key]
        val kind = if (purge || previous?.kind == QueueEntry.KIND_PURGE) {
            QueueEntry.KIND_PURGE
        } else {
            QueueEntry.KIND_TRASH
        }
        current[key] = QueueEntry(
            path = key,
            noteId = noteId,
            kind = kind,
            failedAttempts = 0,
            addedAt = previous?.addedAt ?: System.currentTimeMillis(),
            revision = (previous?.revision ?: 0) + 1,
        )
        write(current)
    }

    @Synchronized
    fun remove(path: String) {
        val current = read().toMutableMap()
        current.remove(path)
        write(current)
    }

    /**
     * Zdejmuje wpis tylko wtedy, gdy nikt go w międzyczasie nie podmienił.
     * Wysyłka trwa chwilę; jeśli w jej trakcie doszła nowa zmiana (autozapis)
     * albo kosz urósł do trwałego kasowania, wpis ma zostać i pojechać jeszcze
     * raz - inaczej ta świeższa robota przepadałaby po cichu.
     */
    @Synchronized
    fun removeIfUnchanged(sent: QueueEntry) {
        val current = read().toMutableMap()
        val now = current[sent.path] ?: return
        if (now.revision != sent.revision || now.kind != sent.kind) return
        current.remove(sent.path)
        write(current)
    }

    /**
     * Nieudana wysyłka. Po [MAX_ATTEMPTS] porażkach wpis zostaje w kolejce
     * jako utknięty: przestajemy dobijać się nim do serwera, ale nie znika -
     * kiedyś znikał i notatka przestawała się synchronizować NA ZAWSZE, bez
     * żadnego sygnału. Teraz liczbę utkniętych widać w stanie synchronizacji,
     * a [retryStuck] daje im kolejną szansę.
     *
     * Zwraca true, dopóki wpis ma jeszcze próby przed sobą.
     */
    @Synchronized
    fun recordFailure(path: String): Boolean {
        val current = read().toMutableMap()
        val entry = current[path] ?: return false
        val after = entry.copy(failedAttempts = entry.failedAttempts + 1)
        current[path] = after
        write(current)
        return after.failedAttempts < MAX_ATTEMPTS
    }

    /** Ile wpisów wyczerpało próby i czeka na ręczne ponowienie. */
    @Synchronized
    fun stuckCount(): Int = read().values.count { it.stuck }

    /** Utknięte wpisy dostają od nowa pełną pulę prób. */
    @Synchronized
    fun retryStuck() {
        val current = read().toMutableMap()
        var changed = false
        for ((key, entry) in current) {
            if (!entry.stuck) continue
            current[key] = entry.copy(failedAttempts = 0)
            changed = true
        }
        if (changed) write(current)
    }

    @Synchronized
    fun all(): List<QueueEntry> = read().values.sortedBy { it.addedAt }

    @Synchronized
    fun size(): Int = read().size

    @Synchronized
    fun clear() {
        preferences.edit().remove(KEY).apply()
        bump()
    }

    private fun read(): Map<String, QueueEntry> {
        val text = preferences.getString(KEY, null) ?: return emptyMap()
        return runCatching { json.decodeFromString<List<QueueEntry>>(text) }
            .getOrDefault(emptyList())
            .associateBy { it.path }
    }

    private fun write(entries: Map<String, QueueEntry>) {
        preferences.edit().putString(KEY, json.encodeToString(entries.values.toList())).apply()
        bump()
    }

    private fun bump() {
        _revision.value = _revision.value + 1
    }

    private companion object {
        const val FILE_NAME = "kajet-queue"
        const val KEY = "to_send"

        const val MAX_ATTEMPTS = QueueEntry.MAX_ATTEMPTS

        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
data class QueueEntry(
    val path: String,
    val noteId: String,
    // Starsze kolejki nie mają tego pola, a wszystkie ich wpisy to notatki.
    val kind: String = KIND_NOTE,
    val failedAttempts: Int = 0,
    val addedAt: Long = 0,
    // Rośnie przy każdym dopisaniu pod ten sam klucz - po tym poznajemy, że
    // wpis podmieniono w trakcie wysyłki i nie wolno go jeszcze zdjąć.
    val revision: Int = 0,
    // Wpis z uzgadniania biblioteki (po zalogowaniu), a nie z zapisu
    // użytkownika. Kiedy serwer trzyma taką notatkę w koszu, kosz wygrywa -
    // bez tego przelogowanie wskrzeszało wszystko, co skasowano gdzie indziej.
    val reconciled: Boolean = false,
) {
    /** Wyczerpał próby wysyłki i czeka na ręczne ponowienie. */
    val stuck: Boolean get() = failedAttempts >= MAX_ATTEMPTS

    companion object {
        /** Tyle nieudanych wysyłek z rzędu odkłada wpis na bok. */
        const val MAX_ATTEMPTS = 5

        const val KIND_NOTE = "note"
        const val KIND_CODE = "code"
        const val KIND_TRASH = "trash"
        const val KIND_PURGE = "purge"

        /** Kasowanie folderu na serwerze; noteId niesie identyfikator folderu. */
        const val KIND_FOLDER_DELETE = "folder-delete"

        /** Klucz wpisu o kasowaniu; nie zderzy się z żadną prawdziwą ścieżką. */
        const val DELETION_PREFIX = "#kasowanie:"
    }
}
