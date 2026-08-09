package wojtoteka.ovh.kajet.cloud

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * Plik z kodem to na dysku zwykły plik tekstowy — nie niesie w sobie żadnego
 * identyfikatora, a serwer rozpoznaje notatki wyłącznie po identyfikatorze.
 * Ten rejestr skleja jedno z drugim: każdej ścieżce pliku przypisuje stały
 * identyfikator, pod którym plik żyje na serwerze jako notatka CODE.
 *
 * Zmiana nazwy albo przeniesienie pliku poza aplikacją zrywa powiązanie —
 * plik dostaje wtedy nowy identyfikator i na serwerze pojawia się jako nowy.
 */
class CodeFileIds(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences("kajet-code-ids", Context.MODE_PRIVATE)

    /** Identyfikator dla ścieżki; zakłada nowy, kiedy pliku jeszcze nie znamy. */
    @Synchronized
    fun idFor(path: String): String {
        preferences.getString(path, null)?.let { return it }
        val created = UUID.randomUUID().toString()
        preferences.edit().putString(path, created).apply()
        return created
    }

    /** Identyfikator dla ścieżki bez zakładania nowego. */
    @Synchronized
    fun existingIdFor(path: String): String? = preferences.getString(path, null)

    @Synchronized
    fun pathFor(id: String): String? =
        preferences.all.entries.firstOrNull { it.value == id }?.key

    /**
     * Cały rejestr odwrócony: numer notatki na serwerze wskazuje ścieżkę pliku.
     * Do przejścia po spisie nagrobków — [pathFor] przegląda przy każdym
     * pytaniu wszystkie wpisy, więc przy pięciuset nagrobkach robi z tego
     * pięćset przebiegów po tym samym.
     */
    @Synchronized
    fun pathsById(): Map<String, String> =
        preferences.all.entries.mapNotNull { (path, value) ->
            (value as? String)?.let { id -> id to path }
        }.toMap()

    /** Wiąże ścieżkę z identyfikatorem z serwera (plik utworzony przy pobraniu). */
    @Synchronized
    fun bind(path: String, id: String) {
        preferences.edit().putString(path, id).apply()
    }

    @Synchronized
    fun remove(path: String) {
        preferences.edit().remove(path).apply()
    }

    /**
     * Przepina powiązania po zmianie nazwy albo przeniesieniu — także dla
     * wszystkiego, co leżało w przenoszonym folderze.
     */
    @Synchronized
    fun rebind(oldPath: String, newPath: String) {
        val edit = preferences.edit()
        var changed = false
        for ((key, value) in preferences.all) {
            val id = value as? String ?: continue
            val moved = when {
                key == oldPath -> newPath
                key.startsWith("$oldPath/") -> newPath + key.removePrefix(oldPath)
                else -> continue
            }
            edit.remove(key).putString(moved, id)
            changed = true
        }
        if (changed) edit.apply()
    }

    @Synchronized
    fun clear() {
        preferences.edit().clear().apply()
    }
}
