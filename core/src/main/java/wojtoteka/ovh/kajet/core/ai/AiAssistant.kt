package wojtoteka.ovh.kajet.core.ai

/*
  Asystent KajetAI widziany od strony edytora.

  Ten sam układ, co przy uruchamianiu kodu: interfejs stoi nisko, tam gdzie
  sięgają po niego edytory, a złożenie go z chmury robi moduł aplikacji
  (asystent/KajetAi.kt). Dzięki temu editor i code nie muszą wiedzieć o
  istnieniu modułu cloud ani o tym, że gdzieś w tle jest HTTP.

  Poza tym asystenta da się wtedy podstawić w podglądzie i w testach, nie
  ruszając sieci.

  Do serwera jedzie WYŁĄCZNIE identyfikator notatki i polecenie. Treści nie -
  serwer ją ma. Zapis robi on sam, tą samą drogą co zwykła synchronizacja.
*/

/** Typ notatki, przy której asystent w ogóle się pokazuje. */
enum class AiNoteKind { TEXT, MINDMAP, CODE }

/** Jedna wymiana: co człowiek kazał i co asystent na to odpowiedział. */
data class AiTurn(val request: String, val reply: String)

sealed interface AiOutcome {
    /** Notatka zmieniona i zapisana na serwerze. */
    data class Changed(val summary: String, val version: Int) : AiOutcome

    /** Asystent nie zrozumiał polecenia. Notatka nietknięta. */
    data class Question(val question: String) : AiOutcome

    /**
     * Nie udało się. Notatka nietknięta - to obietnica serwera, nie zgadywanie:
     * każda droga, którą tam coś nie wychodzi, kończy się przed zapisem.
     */
    data class Refused(val reason: String) : AiOutcome
}

interface AiAssistant {
    /**
     * Czy asystent ma prawo się pokazać przy tym koncie.
     *
     * Fałsz znaczy jedno z dwóch: konto nie ma uprawnienia albo serwer nie ma
     * klucza do modelu. Z punktu widzenia interfejsu to bez różnicy - w obu
     * wypadkach po asystencie nie ma zostać ani śladu.
     */
    fun available(): Boolean

    /** Czy właściciel konta zgodził się na wysyłanie treści notatek do Google. */
    fun consented(): Boolean

    /** Zapisuje albo wycofuje zgodę. Zwraca true, gdy się udało. */
    suspend fun setConsent(consented: Boolean): Boolean

    /**
     * Prosi o zmianę notatki.
     *
     * Wywołanie samo dba o to, żeby serwer pracował na świeżej treści:
     * najpierw wypycha to, co leży na urządzeniu, a po udanej zmianie ściąga
     * wynik z powrotem. Zwykłą synchronizacją - asystent nie ma własnej drogi
     * do plików notatek.
     */
    suspend fun ask(noteId: String, kind: AiNoteKind, instruction: String): AiOutcome

    /**
     * Identyfikator notatki dla pliku z kodem. Plik na dysku nie niesie go
     * w sobie - w przeciwieństwie do notatki, która ma go w treści.
     */
    fun codeNoteId(path: String): String

    suspend fun history(noteId: String): List<AiTurn>

    suspend fun forgetHistory(noteId: String): Boolean
}
