package wojtoteka.ovh.kajet.storage

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Sprzątanie po kasowaniu - raz na dobę, nie przy każdym uruchomieniu.
 *
 * Trzy rzeczy, wszystkie tanie i wszystkie odkładane na później, żeby nie
 * opóźniać wejścia do aplikacji:
 *
 *  1. ponowienie kasowań, które kiedyś się nie udały,
 *  2. przeterminowane wpisy kosza, które trafiły tam za serwerem,
 *  3. sieroty: wiersze spisu po nieistniejących plikach i wpisy kosza nie do
 *     przywrócenia,
 *  4. stary podręczny materiał: zdjęcia z aparatu i pliki eksportu. Te nie są
 *     przypisane do żadnej notatki - nazwa nie niesie jej identyfikatora -
 *     więc jedyne, co da się o nich powiedzieć, to jak długo leżą.
 *
 * Nic tutaj nie jest pilne i nic nie może przerwać pracy. Awaria jednego kroku
 * nie zatrzymuje pozostałych, a nieudany przebieg powtórzy się nazajutrz.
 */
class Housekeeping(
    context: Context,
    private val repository: LibraryRepository,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    private val app = context.applicationContext

    private val marks = app.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** Czy od ostatniego przebiegu minęła doba. */
    fun isDue(now: Long = System.currentTimeMillis()): Boolean {
        val last = marks.getLong(KEY_LAST_RUN, 0L)
        // Zegar cofnięty ręcznie albo po zmianie strefy: przebieg należy się
        // od razu, zamiast czekać, aż czas znowu dogoni zapisany znacznik.
        if (last > now) return true
        return now - last >= DAY
    }

    /** Robi przebieg, jeśli się należy. Zwraca true, kiedy naprawdę sprzątał. */
    suspend fun runIfDue(now: Long = System.currentTimeMillis()): Boolean = withContext(io) {
        if (!isDue(now)) return@withContext false

        // Znacznik idzie PRZED robotą, nie po niej: gdy sprzątanie przewróci
        // się w połowie, następna próba ma być za dobę, a nie przy najbliższym
        // uruchomieniu i tak w kółko.
        marks.edit().putLong(KEY_LAST_RUN, now).apply()

        val retried = runCatching { repository.retryPendingDeletions() }.getOrDefault(0)
        val expired = runCatching { repository.sweepExpiredServerTrash(SERVER_TRASH_DAYS, now) }
            .getOrDefault(0)
        val orphans = runCatching { repository.sweepOrphans() }.getOrDefault(0)
        val cached = runCatching { sweepCache(now) }.getOrDefault(0)

        if (retried > 0 || expired > 0 || orphans > 0 || cached > 0) {
            Log.i(
                "Kajet",
                "Sprzątanie: ponowionych kasowań $retried, przeterminowanych wpisów " +
                    "kosza $expired, sierot $orphans, starych plików podręcznych $cached",
            )
        }
        true
    }

    /** Zdjęcia z aparatu i pliki eksportu starsze niż [CACHE_DAYS] dni. */
    private fun sweepCache(now: Long): Int {
        var removed = 0
        for (name in CACHE_DIRECTORIES) {
            val directory = File(app.cacheDir, name)
            if (!directory.isDirectory) continue
            val files = directory.listFiles() ?: continue
            for (file in files) {
                if (!file.isFile) continue
                val age = now - file.lastModified()
                if (age < CACHE_DAYS * DAY) continue
                if (file.delete()) removed += 1
            }
        }
        return removed
    }

    companion object {
        /**
         * Ile dni wpis, który trafił do kosza za serwerem, czeka, zanim zniknie
         * z dysku. Tyle samo, ile kosz na serwerze (TRASH_DAYS=30) - żeby ten
         * sam termin obowiązywał wszędzie i żeby dało się go zapamiętać.
         *
         * Liczone od chwili wejścia do kosza NA TYM urządzeniu, patrz
         * [TrashEntry.deletedAt]. Przywrócenie z kosza kasuje cały wpis razem
         * z opisem, więc odliczanie znika razem z nim.
         */
        const val SERVER_TRASH_DAYS = 30

        /**
         * Ile dni zostało wpisowi kosza, zanim zniknie z dysku. Null, kiedy
         * termin go nie dotyczy - czyli gdy ktoś wyrzucił go tutaj sam.
         *
         * Liczy się tu, obok samego sprzątania, żeby napis w koszu i chwila
         * skasowania nie mogły się rozjechać. Zaokrąglenie w górę: dopóki
         * został choćby kawałek dnia, jest to jeszcze „jeden dzień".
         */
        fun daysLeft(entry: TrashEntry, now: Long = System.currentTimeMillis()): Int? {
            if (!entry.fromServer) return null
            val left = entry.deletedAt + SERVER_TRASH_DAYS * DAY - now
            if (left <= 0) return 0
            return ((left + DAY - 1) / DAY).toInt()
        }

        private const val FILE_NAME = "kajet-sprzatanie"
        private const val KEY_LAST_RUN = "ostatni_przebieg"

        private const val DAY = 24 * 60 * 60 * 1000L

        /**
         * Po tylu dniach plik podręczny znika. Tydzień, bo zdjęcie zrobione
         * aparatem trafia do notatki od razu, a wyeksportowany plik człowiek
         * odbiera z powiadomienia tego samego dnia - dłuższe trzymanie to już
         * tylko zajęte miejsce.
         */
        private const val CACHE_DAYS = 7

        private val CACHE_DIRECTORIES = listOf("aparat", "eksport")
    }
}
