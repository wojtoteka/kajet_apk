package wojtoteka.ovh.kajet.cloud

import wojtoteka.ovh.kajet.core.text.words

/*
  Asystent od strony chmury. Ten sam układ, co CloudCode przy uruchamianiu
  kodu: cienka warstwa nad CloudClient, która zna reguły, a nie protokół.

  Najciekawsza jest tu kolejność, bo od niej zależy, czy model pracuje na tym,
  co człowiek widzi na ekranie:

    1. zwykła synchronizacja W GÓRĘ - to, co leży na tablecie, ma trafić na
       serwer, ZANIM model to przeczyta. Bez tego asystent poprawiałby wersję
       sprzed ostatniego zdania,
    2. prośba o zmianę - sam identyfikator notatki i polecenie,
    3. zwykła synchronizacja W DÓŁ - zmiana wraca jak z trzeciego urządzenia.

  Żaden z tych kroków nie ma własnej drogi do plików notatek: chodzi
  istniejącym Sync, po jego publicznych wejściach. Synchronizacji nikt tu
  nie przerabia.
*/
class CloudAi(
    private val account: AccountStore,
    private val client: CloudClient,
    private val sync: Sync,
    private val codeIds: CodeFileIds,
) {

    /**
     * Identyfikator, pod którym ten plik z kodem żyje na serwerze.
     *
     * Zakłada nowy, gdy pliku jeszcze nie znamy - i tak trzeba go mieć, zanim
     * ruszy wysyłka, a synchronizacja użyje dokładnie tego samego wpisu.
     */
    fun codeNoteId(path: String): String = codeIds.idFor(path)

    fun available(): Boolean = account.isSignedIn() && account.aiAvailable()

    fun consented(): Boolean = account.aiConsented()

    suspend fun setConsent(consented: Boolean): Boolean {
        val response = runCatching { client.aiSetConsent(consented) }.getOrNull()
        return if (response is CloudClient.Result.Ok) {
            account.rememberAiConsent(consented)
            true
        } else {
            false
        }
    }

    suspend fun ask(noteId: String, instruction: String): Outcome {
        if (!available()) return Outcome.Refused(words.notSignedIn)
        if (!client.hasNetwork()) return Outcome.Refused(words.aiOffline)

        // 1. Świeża treść na serwer. Gdyby wysyłka padła, nie ma sensu pytać
        //    modelu - poprawiałby coś, czego człowiek już nie ma na ekranie.
        if (!runCatching { sync.synchronise() }.isSuccess) {
            return Outcome.Refused(words.aiSaveFirst)
        }

        return when (val response = client.aiEdit(noteId, instruction, baseVersion = 0)) {
            is CloudClient.Result.Error -> Outcome.Refused(response.message)

            is CloudClient.Result.Ok -> when (response.data.status) {
                "zmieniono" -> {
                    // 3. Wynik z powrotem na urządzenie. Gdyby ściągnięcie
                    //    padło po cichu, ekran pokazałby sukces przy starej
                    //    treści - stąd osobna odmowa, nie przełknięcie błędu.
                    if (!runCatching { sync.synchronise() }.isSuccess) {
                        return Outcome.Refused(words.aiPullFailed)
                    }
                    Outcome.Changed(response.data.opis, response.data.version)
                }
                "pytanie" -> Outcome.Question(response.data.pytanie)
                "konflikt" -> Outcome.Refused(words.aiNoteChangedElsewhere)
                else -> Outcome.Refused(words.serverGibberish)
            }
        }
    }

    suspend fun history(noteId: String): List<AiTurn> =
        when (val response = client.aiHistory(noteId)) {
            is CloudClient.Result.Ok -> response.data.turns
            is CloudClient.Result.Error -> emptyList()
        }

    suspend fun forgetHistory(noteId: String): Boolean =
        client.aiForgetHistory(noteId) is CloudClient.Result.Ok

    sealed interface Outcome {
        data class Changed(val summary: String, val version: Int) : Outcome
        data class Question(val question: String) : Outcome
        data class Refused(val reason: String) : Outcome
    }
}
