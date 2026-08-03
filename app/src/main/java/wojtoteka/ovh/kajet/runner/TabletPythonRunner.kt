package wojtoteka.ovh.kajet.runner

import android.content.Context
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wojtoteka.ovh.kajet.code.CodeRunner
import wojtoteka.ovh.kajet.code.RunException
import wojtoteka.ovh.kajet.code.RunResult
import wojtoteka.ovh.kajet.core.model.CodeLanguage

class TabletPythonRunner(private val context: Context) : CodeRunner {

    override val name: String = "na tablecie"
    override val requiresInternet: Boolean = false

    override fun supports(language: CodeLanguage): Boolean = language == CodeLanguage.PYTHON

    override suspend fun run(
        language: CodeLanguage,
        code: String,
        input: String,
        fileName: String,
    ): RunResult = withContext(Dispatchers.Default) {
        if (!Python.isStarted()) {
            runCatching { Python.start(AndroidPlatform(context)) }.onFailure {
                throw RunException(
                    "Nie udało się uruchomić Pythona na tablecie. Zamknij aplikację i otwórz ją ponownie.",
                    it,
                )
            }
        }

        val python = Python.getInstance()
        val start = System.currentTimeMillis()

        val outcome = try {
            val module = python.getModule("kajet_runner")
            module.callAttr("run", code, input)
        } catch (e: PyException) {
            throw RunException("Wewnętrzny błąd Pythona: ${e.message}", e)
        }
        val elapsed = System.currentTimeMillis() - start

        RunResult(
            language = CodeLanguage.PYTHON,
            output = outcome.callAttr("get", "stdout").toString(),
            errors = outcome.callAttr("get", "stderr").toString(),
            exitCode = outcome.callAttr("get", "code").toInt(),
            durationMs = elapsed,
            viaNetwork = false,
        )
    }
}
