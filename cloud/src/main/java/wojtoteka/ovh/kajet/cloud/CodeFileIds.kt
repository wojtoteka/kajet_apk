package wojtoteka.ovh.kajet.cloud

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * Plik z kodem to na dysku zwykły plik tekstowy - nie niesie w sobie żadnego
 * identyfikatora, a serwer rozpoznaje notatki wyłącznie po identyfikatorze.
 * Ten rejestr skleja jedno z drugim: każdej ścieżce pliku przypisuje stały
 * identyfikator, pod którym plik żyje na serwerze jako notatka CODE.
 *
 * Zmiana nazwy albo przeniesienie pliku poza aplikacją zrywa powiązanie -
 * plik dostaje wtedy nowy identyfikator i na serwerze pojawia się jako nowy.
 */
class CodeFileIds(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences("kajet-code-ids", Context.MODE_PRIVATE)

    /*
      Drugi rejestr, w osobnym pliku, żeby nie mieszał się do przeglądania
      numerów: jakim językiem serwer nazwał plik leżący pod tą ścieżką.

      Po co: aplikacja czyta język z rozszerzenia, a .sql to dwa różne języki -
      SQLite i MySQL. Bez tej pamięci notatka założona na stronie jako MySQL
      wracałaby na serwer jako SQLite przy pierwszej poprawce zrobionej na
      tablecie, czyli tablet po cichu przestawiałby jej język. Serwer trzyma
      język w samej notatce (content.json → code.language) i tylko zgaduje go
      z rozszerzenia; tu jest to samo rozróżnienie.
    */
    private val languages: SharedPreferences =
        context.getSharedPreferences("kajet-code-languages", Context.MODE_PRIVATE)

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
     * Do przejścia po spisie nagrobków - [pathFor] przegląda przy każdym
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

    /**
     * Zapamiętuje język, którym serwer nazwał ten plik. Pusty identyfikator
     * kasuje wpis - notatka bez języka nie ma czego trzymać.
     */
    @Synchronized
    fun rememberLanguage(path: String, serverLanguageId: String?) {
        val edit = languages.edit()
        if (serverLanguageId.isNullOrBlank()) edit.remove(path) else edit.putString(path, serverLanguageId)
        edit.apply()
    }

    /** Język zapamiętany przy tej ścieżce albo `null`, gdy nic nie wiemy. */
    @Synchronized
    fun languageFor(path: String): String? = languages.getString(path, null)

    @Synchronized
    fun remove(path: String) {
        preferences.edit().remove(path).apply()
        languages.edit().remove(path).apply()
    }

    /**
     * Przepina powiązania po zmianie nazwy albo przeniesieniu - także dla
     * wszystkiego, co leżało w przenoszonym folderze.
     */
    @Synchronized
    fun rebind(oldPath: String, newPath: String) {
        movePaths(preferences, oldPath, newPath)
        // Pamięć języka jedzie za plikiem tak samo jak jego numer. Inaczej
        // zmiana nazwy notatki MySQL gubiłaby jej język przy najbliższym
        // zapisie.
        movePaths(languages, oldPath, newPath)
    }

    private fun movePaths(store: SharedPreferences, oldPath: String, newPath: String) {
        val edit = store.edit()
        var changed = false
        for ((key, value) in store.all) {
            val kept = value as? String ?: continue
            val moved = when {
                key == oldPath -> newPath
                key.startsWith("$oldPath/") -> newPath + key.removePrefix(oldPath)
                else -> continue
            }
            edit.remove(key).putString(moved, kept)
            changed = true
        }
        if (changed) edit.apply()
    }

    @Synchronized
    fun clear() {
        preferences.edit().clear().apply()
        languages.edit().clear().apply()
    }
}
