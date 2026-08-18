package wojtoteka.ovh.kajet.code

import wojtoteka.ovh.kajet.core.model.CodeLanguage

/**
 * Wynik jednego uruchomienia.
 *
 * [exitCode] bywa pusty i to nie jest usterka: program przerwany po limicie
 * czasu numeru nie ma, tak samo jak program, którego wydruk był dłuższy, niż
 * wolno pokazać (wynik jest wtedy ucięty i kończy się zdaniem o ucięciu).
 * Pustego numeru nie zastępujemy zerem ani minus jedynką — zero znaczyłoby
 * „skończył się dobrze", a takiej wiedzy nie mamy.
 *
 * Nie ma tu pola „udało się". Program, który kończy się jedynką i nic nie
 * wypisuje na wyjście błędów, jest programem DZIAŁAJĄCYM — sam numer stoi
 * w pasku nad wynikiem i to on o tym mówi.
 */
data class RunResult(
    val language: CodeLanguage,
    val output: String,
    val errors: String,
    val exitCode: Int?,
    val durationMs: Long,
    val viaNetwork: Boolean,
    /**
     * Program nie skończył się sam — przerwał go limit czasu.
     *
     * Rozróżnia dwa przypadki pustego [exitCode], które inaczej wyglądałyby
     * na ekranie identycznie: przerwanie i wynik ucięty za długością.
     */
    val interrupted: Boolean = false,
)

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
