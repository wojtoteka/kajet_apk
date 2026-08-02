package wojtoteka.ovh.kajet.code

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import wojtoteka.ovh.kajet.core.model.CodeLanguage
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Uruchamianie kodu na serwerze Piston.
 *
 * Android nie skompiluje C++ ani nie uruchomi Javy bez zewnętrznych narzędzi,
 * więc te języki liczy serwer. Adres serwera jest w ustawieniach, żeby dało się
 * podstawić własny. Bez internetu nic tu nie zadziała i aplikacja mówi to wprost.
 *
 * Zapytanie idzie zwykłym połączeniem HTTP z biblioteki standardowej,
 * bo to jedno zapytanie na uruchomienie i nie warto za to płacić kolejną zależnością.
 */
class SerwerKodu(
    private val context: Context,
    private val adresSerwera: () -> String,
) : CodeRunner {

    override val nazwa: String = "na serwerze"
    override val wymagaInternetu: Boolean = true

    override fun obsluguje(jezyk: CodeLanguage): Boolean = jezyk.pistonRuntime != null && !jezyk.offline

    override suspend fun uruchom(
        jezyk: CodeLanguage,
        kod: String,
        wejscie: String,
        nazwaPliku: String,
    ): WynikUruchomienia = withContext(Dispatchers.IO) {
        val srodowisko = jezyk.pistonRuntime
            ?: throw BladUruchomienia("Kajet nie umie uruchomić języka ${jezyk.labelPl}.")

        if (!jestInternet()) {
            throw BladUruchomienia(
                "Nie ma internetu, a ${jezyk.labelPl} liczy się na serwerze. " +
                    "Kod jest zapisany. Spróbuj ponownie po połączeniu z siecią.",
            )
        }

        val adres = adresSerwera().trimEnd('/')
        val zapytanie = json.encodeToString(
            ZapytaniePiston(
                language = srodowisko,
                version = "*",
                files = listOf(PlikPiston(name = jezyk.remoteFileName, content = kod)),
                stdin = wejscie,
            ),
        )

        val poczatek = System.currentTimeMillis()
        val odpowiedz = wyslij("$adres/execute", zapytanie)
        val czas = System.currentTimeMillis() - poczatek

        val wynik = runCatching { json.decodeFromString<OdpowiedzPiston>(odpowiedz) }.getOrElse {
            throw BladUruchomienia(
                "Serwer odpowiedział czymś, czego nie rozumiem. Sprawdź adres serwera w ustawieniach.",
                it,
            )
        }

        if (wynik.message != null) {
            throw BladUruchomienia("Serwer odmówił uruchomienia: ${wynik.message}")
        }

        val kompilacja = wynik.compile
        val uruchomienie = wynik.run
            ?: throw BladUruchomienia("Serwer nie odesłał wyniku uruchomienia.")

        val bledy = buildString {
            if (!kompilacja?.stderr.isNullOrBlank()) {
                append("Błędy kompilacji:\n")
                append(kompilacja?.stderr)
                append('\n')
            }
            if (uruchomienie.stderr.isNotBlank()) append(uruchomienie.stderr)
        }.trim()

        WynikUruchomienia(
            jezyk = jezyk,
            wyjscie = uruchomienie.stdout,
            bledy = bledy,
            kodWyjscia = uruchomienie.code ?: kompilacja?.code,
            czasMs = czas,
            przezSiec = true,
        )
    }

    private fun jestInternet(): Boolean {
        val menedzer = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val siec = menedzer.activeNetwork ?: return false
        val mozliwosci = menedzer.getNetworkCapabilities(siec) ?: return false
        return mozliwosci.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun wyslij(adres: String, tresc: String): String {
        val polaczenie = try {
            URL(adres).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw BladUruchomienia("Adres serwera jest nieprawidłowy: $adres", e)
        }

        return try {
            polaczenie.requestMethod = "POST"
            polaczenie.doOutput = true
            polaczenie.connectTimeout = 15_000
            polaczenie.readTimeout = 45_000
            polaczenie.setRequestProperty("Content-Type", "application/json")
            polaczenie.setRequestProperty("Accept", "application/json")
            polaczenie.outputStream.use { it.write(tresc.toByteArray(Charsets.UTF_8)) }

            val kod = polaczenie.responseCode
            val strumien = if (kod in 200..299) polaczenie.inputStream else polaczenie.errorStream
            val odpowiedz = strumien?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            when {
                kod == 429 -> throw BladUruchomienia(
                    "Serwer prosi o przerwę, bo w krótkim czasie poszło za dużo zapytań. " +
                        "Odczekaj kilkanaście sekund i uruchom ponownie.",
                )
                kod !in 200..299 -> throw BladUruchomienia(
                    "Serwer odpowiedział błędem $kod. Sprawdź adres serwera w ustawieniach.",
                )
                else -> odpowiedz
            }
        } catch (e: UnknownHostException) {
            throw BladUruchomienia(
                "Nie mogę połączyć się z serwerem. Sprawdź internet albo adres serwera w ustawieniach.",
                e,
            )
        } catch (e: SocketTimeoutException) {
            throw BladUruchomienia(
                "Serwer nie odpowiedział na czas. Spróbuj jeszcze raz albo wpisz adres własnego serwera.",
                e,
            )
        } catch (e: IOException) {
            throw BladUruchomienia("Połączenie z serwerem się urwało. Spróbuj jeszcze raz.", e)
        } finally {
            polaczenie.disconnect()
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}

@Serializable
private data class ZapytaniePiston(
    val language: String,
    val version: String,
    val files: List<PlikPiston>,
    val stdin: String = "",
    @SerialName("run_timeout") val runTimeout: Int = 10_000,
    @SerialName("compile_timeout") val compileTimeout: Int = 15_000,
)

@Serializable
private data class PlikPiston(val name: String, val content: String)

@Serializable
private data class OdpowiedzPiston(
    val language: String? = null,
    val version: String? = null,
    val run: EtapPiston? = null,
    val compile: EtapPiston? = null,
    val message: String? = null,
)

@Serializable
private data class EtapPiston(
    val stdout: String = "",
    val stderr: String = "",
    val code: Int? = null,
    val signal: String? = null,
)
