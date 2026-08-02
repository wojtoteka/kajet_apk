package wojtoteka.ovh.kajet.kod

import android.content.Context
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wojtoteka.ovh.kajet.code.BladUruchomienia
import wojtoteka.ovh.kajet.code.CodeRunner
import wojtoteka.ovh.kajet.code.WynikUruchomienia
import wojtoteka.ovh.kajet.core.model.CodeLanguage

/**
 * Python uruchamiany na tablecie, bez internetu.
 *
 * Tłumacz Pythona jest wbudowany w aplikację, więc program liczy się lokalnie
 * i działa też wtedy, gdy nie ma zasięgu. To pokrywa większość zadań szkolnych.
 *
 * Wyjście i błędy przechwytujemy po stronie Pythona, przestawiając
 * standardowe strumienie na bufory w pamięci. Standardowe wejście podstawiamy
 * z tego, co użytkownik wpisał w zakładce Wejście, dzięki czemu funkcja input
 * działa tak samo jak na komputerze.
 */
class PythonNaTablecie(private val context: Context) : CodeRunner {

    override val nazwa: String = "na tablecie"
    override val wymagaInternetu: Boolean = false

    override fun obsluguje(jezyk: CodeLanguage): Boolean = jezyk == CodeLanguage.PYTHON

    override suspend fun uruchom(
        jezyk: CodeLanguage,
        kod: String,
        wejscie: String,
        nazwaPliku: String,
    ): WynikUruchomienia = withContext(Dispatchers.Default) {
        if (!Python.isStarted()) {
            runCatching { Python.start(AndroidPlatform(context)) }.onFailure {
                throw BladUruchomienia(
                    "Nie udało się uruchomić Pythona na tablecie. Zamknij aplikację i otwórz ją ponownie.",
                    it,
                )
            }
        }

        val python = Python.getInstance()
        val poczatek = System.currentTimeMillis()

        val wynik = try {
            val modul = python.getModule("kajet_runner")
            modul.callAttr("uruchom", kod, wejscie)
        } catch (e: PyException) {
            throw BladUruchomienia("Wewnętrzny błąd Pythona: ${e.message}", e)
        }
        val czas = System.currentTimeMillis() - poczatek

        val wyjscie = wynik.callAttr("get", "stdout").toString()
        val bledy = wynik.callAttr("get", "stderr").toString()
        val kodWyjscia = wynik.callAttr("get", "code").toInt()

        WynikUruchomienia(
            jezyk = CodeLanguage.PYTHON,
            wyjscie = wyjscie,
            bledy = bledy,
            kodWyjscia = kodWyjscia,
            czasMs = czas,
            przezSiec = false,
        )
    }
}
