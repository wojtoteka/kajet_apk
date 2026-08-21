package wojtoteka.ovh.kajet.runner

import wojtoteka.ovh.kajet.cloud.CloudCode
import wojtoteka.ovh.kajet.code.CodeRunner
import wojtoteka.ovh.kajet.code.RunException
import wojtoteka.ovh.kajet.code.RunResult
import wojtoteka.ovh.kajet.core.model.CodeLanguage
import wojtoteka.ovh.kajet.core.text.words

class KajetServerRunner(private val cloud: CloudCode) : CodeRunner {

    override val name: String get() = words.codeOnServer
    override val requiresInternet: Boolean = true

    /*
      Co serwer umie, mówi sam spis języków: serverRuntime jest pusty dokładnie
      przy tych, których serwer nie uruchamia.

      Stał tu wcześniej drugi spis nazw i to on uziemiał języki: C# i Java
      siedziały w CodeLanguage z poprawnym identyfikatorem, ale w tamtej liście
      ich nie było, więc okno zakładania pliku je pokazywało, a przycisk
      uruchomienia przy nich nie działał. Jeden spis, jedno miejsce do
      poprawienia.

      Python ma offline=true (Chaquopy na tablecie), ale na telefonie i tak leci
      tu - RunnerRegistry wybierze lokalny runner, gdy jest zarejestrowany.
    */
    override fun supports(language: CodeLanguage): Boolean = language.runnable

    override suspend fun run(
        language: CodeLanguage,
        code: String,
        input: String,
        fileName: String,
    ): RunResult {
        val start = System.currentTimeMillis()

        return when (val outcome = cloud.run(languageId(language), code, input)) {
            is CloudCode.Outcome.Refused -> throw RunException(outcome.reason)

            is CloudCode.Outcome.Ready -> RunResult(
                language = language,
                output = outcome.result.output,
                errors = outcome.result.errors,
                exitCode = outcome.result.exitCode,
                interrupted = outcome.result.interrupted,
                // Measured locally, so it includes the network trip. The server reports its
                // own timing, but a person cares about how long they actually waited.
                durationMs = System.currentTimeMillis() - start,
                viaNetwork = true,
            )
        }
    }

    /*
      Serwer szuka języka przez LANGUAGES.find(l => l.id === id), więc idzie
      tam identyfikator SERWERA, nie nasz: SQLite to „sqlite3", a C++ to „c++".
      Przy pozostałych językach oba napisy są takie same.
    */
    private fun languageId(language: CodeLanguage): String = language.serverRuntime ?: language.id
}
