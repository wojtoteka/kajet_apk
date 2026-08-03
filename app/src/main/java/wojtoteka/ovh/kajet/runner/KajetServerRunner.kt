package wojtoteka.ovh.kajet.runner

import wojtoteka.ovh.kajet.cloud.CloudCode
import wojtoteka.ovh.kajet.code.CodeRunner
import wojtoteka.ovh.kajet.code.RunException
import wojtoteka.ovh.kajet.code.RunResult
import wojtoteka.ovh.kajet.core.model.CodeLanguage

class KajetServerRunner(private val cloud: CloudCode) : CodeRunner {

    override val name: String = "na serwerze Kajetu"
    override val requiresInternet: Boolean = true

    // Python ma offline=true (Chaquopy na tablecie), ale na telefonie i tak
    // leci tu — RunnerRegistry wybierze lokalny runner, gdy jest zarejestrowany.
    override fun supports(language: CodeLanguage): Boolean =
        language.serverRuntime in ON_SERVER

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
                // Measured locally, so it includes the network trip. The server reports its
                // own timing, but a person cares about how long they actually waited.
                durationMs = System.currentTimeMillis() - start,
                viaNetwork = true,
            )
        }
    }

    private fun languageId(language: CodeLanguage): String = language.serverRuntime ?: language.id

    private companion object {
        val ON_SERVER = setOf(
            "python",
            "javascript",
            "typescript",
            "bash",
            "c",
            "c++",
            "php",
            "ruby",
            "sqlite3",
        )
    }
}
