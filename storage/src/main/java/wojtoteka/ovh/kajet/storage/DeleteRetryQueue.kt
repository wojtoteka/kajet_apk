package wojtoteka.ovh.kajet.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * Pliki, których nie udało się skasować z dysku.
 *
 * Kasowanie idzie przez SAF i potrafi się nie udać z powodów, na które nikt tu
 * nie ma wpływu: karta pamięci akurat odpięta, plik otwarty przez inny program,
 * odebrane prawo do katalogu. Wcześniej taki wynik przepadał — [DiskFiles]
 * oddaje wprawdzie `Boolean`, ale żadne z trzech miejsc kasujących go nie
 * czytało, więc po nieudanej próbie zostawał plik, o którym nikt już nie
 * wiedział: bez wiersza w spisie i bez wpisu w koszu.
 *
 * Ten rejestr trzyma takie zaległości między uruchomieniami. Sprzątanie
 * ([Housekeeping]) próbuje je skasować jeszcze raz.
 */
class DeleteRetryQueue(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** Nieudane kasowanie wpisu z biblioteki, wskazanego ścieżką. */
    fun addPath(path: String) = add(PREFIX_PATH + path)

    /** Nieudane kasowanie wpisu kosza, wskazanego jego identyfikatorem. */
    fun addTrashSlot(id: String) = add(PREFIX_TRASH + id)

    @Synchronized
    private fun add(target: String) {
        if (target.isBlank()) return
        val current = read().toMutableSet()
        if (!current.add(target)) return
        write(current)
    }

    @Synchronized
    fun remove(target: String) {
        val current = read().toMutableSet()
        if (!current.remove(target)) return
        write(current)
    }

    @Synchronized
    fun all(): List<String> = read().sorted()

    @Synchronized
    fun isEmpty(): Boolean = read().isEmpty()

    @Synchronized
    fun clear() {
        preferences.edit().remove(KEY).apply()
    }

    private fun read(): Set<String> = preferences.getStringSet(KEY, emptySet()).orEmpty()

    private fun write(entries: Set<String>) {
        // Kopia zbioru: SharedPreferences nie kopiuje przekazanego zbioru
        // i zmiana tego samego egzemplarza po zapisie potrafi go rozjechać.
        preferences.edit().putStringSet(KEY, HashSet(entries)).apply()
    }

    companion object {
        private const val FILE_NAME = "kajet-kasowanie"
        private const val KEY = "zalegle"

        const val PREFIX_PATH = "sciezka:"
        const val PREFIX_TRASH = "kosz:"
    }
}
