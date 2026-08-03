package wojtoteka.ovh.kajet.code

import wojtoteka.ovh.kajet.core.model.CodeLanguage

data class RunResult(
    val language: CodeLanguage,
    val output: String,
    val errors: String,
    val exitCode: Int?,
    val durationMs: Long,
    val viaNetwork: Boolean,
) {
    val succeeded: Boolean get() = exitCode == 0 && errors.isBlank()
}

class RunException(
    val userMessage: String,
    cause: Throwable? = null,
) : Exception(userMessage, cause)

interface CodeRunner {

    val name: String

    fun supports(language: CodeLanguage): Boolean

    val requiresInternet: Boolean

    suspend fun run(
        language: CodeLanguage,
        code: String,
        input: String,
        fileName: String,
    ): RunResult
}

class RunnerRegistry(
    private val runners: List<CodeRunner>,
    /** Gdy true — najpierw runner sieciowy (CloudCode), potem lokalny. */
    private val preferServer: () -> Boolean = { false },
) {

    fun forLanguage(language: CodeLanguage): CodeRunner? {
        val matching = runners.filter { it.supports(language) }
        if (preferServer()) {
            matching.firstOrNull { it.requiresInternet }?.let { return it }
        }
        return matching.firstOrNull { !it.requiresInternet }
            ?: matching.firstOrNull()
    }

    fun runsOffline(language: CodeLanguage): Boolean {
        if (preferServer() && runners.any { it.supports(language) && it.requiresInternet }) {
            return false
        }
        return runners.any { it.supports(language) && !it.requiresInternet }
    }
}
