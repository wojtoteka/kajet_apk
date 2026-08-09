package wojtoteka.ovh.kajet.cloud

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Wysyłka raportów o awariach na serwer Kajetu.
 *
 * Bez konta i bez tokenu — punkt `POST /api/v1/crash` przyjmuje raporty od
 * każdego. Aplikacja potrafi wywrócić się zanim ktokolwiek się zaloguje, a
 * wtedy raport jest najbardziej potrzebny.
 *
 * Celowo nie dotyka [AccountStore] ani [CloudClient]: raporty wysyła się także
 * po awarii w budowie kontenera aplikacji, kiedy tamtych obiektów może po
 * prostu nie być. Stąd goły [HttpURLConnection] i jedyna zależność w postaci
 * stałej z adresem — a ta jest wstawiana w kod przy kompilacji.
 *
 * Wołać poza wątkiem głównym.
 */
object CrashReporter {

    /** Co zrobić z raportem po próbie wysłania. */
    enum class Outcome {
        /** Serwer przyjął. Raport można odhaczyć. */
        SENT,

        /** Serwer odmówił na stałe (za duży, nieczytelny). Powtarzanie nic nie da. */
        REJECTED,

        /** Nie udało się teraz — brak sieci, zapora, awaria serwera. Zostaje na potem. */
        RETRY,
    }

    private const val PATH = "/api/v1/crash"
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000
    private const val TAG = "Kajet"

    /**
     * Treść zapytania. Puste pola wypadają z JSON-a (`explicitNulls = false`
     * w [CloudClient.json]), a po stronie serwera wszystkie poza `report` są
     * nieobowiązkowe — starszy raport bez numeru wydania też ma dojść.
     */
    @Serializable
    data class Body(
        val report: String,
        val appVersion: String? = null,
        val versionCode: Int? = null,
        val device: String? = null,
        val android: String? = null,
        val thread: String? = null,
    )

    /** Osobno od [send], żeby dało się to sprawdzić testem kontraktu. */
    fun bodyOf(
        report: String,
        appVersion: String? = null,
        versionCode: Int? = null,
        device: String? = null,
        android: String? = null,
        thread: String? = null,
    ): String = CloudClient.json.encodeToString(
        Body(report, appVersion, versionCode, device, android, thread),
    )

    fun send(
        report: String,
        appVersion: String? = null,
        versionCode: Int? = null,
        device: String? = null,
        android: String? = null,
        thread: String? = null,
    ): Outcome {
        if (report.isBlank()) return Outcome.REJECTED

        val body = bodyOf(report, appVersion, versionCode, device, android, thread)
            .toByteArray(Charsets.UTF_8)

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(AccountStore.SERVER_URL + PATH).openConnection() as HttpURLConnection)
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setFixedLengthStreamingMode(body.size)

            BufferedOutputStream(connection.outputStream).use { it.write(body) }

            when (val code = connection.responseCode) {
                in 200..299 -> {
                    Log.i(TAG, "Raport o awarii przyjęty przez serwer")
                    Outcome.SENT
                }

                // Serwer powiedział, że tego raportu nie przyjmie nigdy.
                // Trzymanie go i dosyłanie w kółko zapchałoby tylko urządzenie.
                HttpURLConnection.HTTP_BAD_REQUEST,
                HttpURLConnection.HTTP_ENTITY_TOO_LARGE,
                -> {
                    Log.w(TAG, "Serwer odrzucił raport o awarii ($code)")
                    Outcome.REJECTED
                }

                /*
                  Także 404: serwer bez nowej trasy jeszcze nie umie przyjąć
                  raportu (wdrożenie idzie serwer-najpierw, ale kolejność da
                  się odwrócić przez pomyłkę). Raport ma wtedy poczekać na
                  dysku, a nie przepaść — stąd RETRY, nie REJECTED.
                */
                else -> {
                    Log.i(TAG, "Serwer nie przyjął teraz raportu o awarii ($code) — spróbuję później")
                    Outcome.RETRY
                }
            }
        } catch (failure: Throwable) {
            // Brak sieci to normalny stan, nie usterka — stąd tylko notatka
            // w dzienniku i powrót do tego raportu przy następnym uruchomieniu.
            Log.i(TAG, "Raport o awarii poczeka na sieć: ${failure.message}")
            Outcome.RETRY
        } finally {
            runCatching { connection?.disconnect() }
        }
    }
}
