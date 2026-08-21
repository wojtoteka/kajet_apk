package wojtoteka.ovh.kajet.cloud

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pytanie do serwera, czy nie ma nowszej wersji Kajetu.
 *
 * Bez konta i bez tokenu - punkt `GET /api/v1/app/latest` odpowiada każdemu.
 * Sprawdzenie ma działać także wtedy, gdy nikt się jeszcze nie zalogował, bo
 * plik ze stroną do pobrania i tak stoi otworem.
 *
 * Celowo nie dotyka [CloudClient]: tamten wymaga zalogowania przy większości
 * zapytań i czeka na odpowiedź do minuty, a to jest funkcja poboczna, która nie
 * ma prawa niczego opóźnić. Stąd goły [HttpURLConnection] i pięć sekund - tak
 * samo jak przy wysyłce raportów o awariach ([CrashReporter]).
 *
 * ŻADNA droga stąd nie prowadzi do wyjątku ani do komunikatu o błędzie. Brak
 * sieci, martwy serwer, odpowiedź nie do odczytania - wszystko to znaczy tyle
 * samo: [Outcome.Unknown], czyli nic się nie dzieje i człowiek pracuje dalej.
 * Jedynym miejscem, które mówi o niepowodzeniu wprost, są ustawienia, bo tam
 * człowiek sam o to poprosił.
 */
object UpdateCheck {

    private const val PATH = "/api/v1/app/latest"
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 5_000

    /** Jak długo wynik sprawdzenia jest jeszcze świeży. */
    private const val REMEMBER_MS = 60 * 60 * 1000L

    /** Co wiadomo po zapytaniu serwera. */
    sealed interface Outcome {
        /** Jest nowsza wersja niż ta zainstalowana. */
        data class Newer(val release: Release) : Outcome

        /** Serwer odpowiedział i nic nowszego nie ma. */
        data object UpToDate : Outcome

        /** Nie udało się zapytać: brak sieci, serwer milczy, odpowiedź nieczytelna. */
        data object Unknown : Outcome
    }

    /**
     * Wydanie wystawione na serwerze.
     *
     * Odpowiedź serwera ma więcej pól (skrót pliku, jego rozmiar); tu zostają
     * te, które są do czegoś potrzebne. `ignoreUnknownKeys` sprawia, że reszta
     * nie przeszkadza, a starszy serwer bez `minSupportedRelease` też się
     * odczyta - pole ma wartość domyślną.
     */
    @Serializable
    data class Release(
        val version: String = "",
        val versionCode: Int = 0,
        val notes: String? = null,
        /** Strona z plikiem do pobrania. Adres bierzemy stąd, nie z kodu aplikacji. */
        val pageUrl: String = "",
        /** Wielkość pliku. Warto wiedzieć, zanim się go ściągnie przez telefon. */
        val sizeBytes: Long = 0,
        val releaseDate: String? = null,
        /*
          Najstarsze wydanie obsługiwane przez serwer. Odczytujemy je i na razie
          NIC z nim nie robimy - to zapas na aktualizację obowiązkową, gdyby
          kiedyś była potrzebna. Wymuszanie czegokolwiek wymaga osobnej decyzji,
          nie samego odczytania liczby.
        */
        val minSupportedRelease: Int = 0,
    )

    @Serializable
    private data class Answer(val release: Release? = null)

    /** Ostatni wynik razem z chwilą, w której powstał. */
    private data class Remembered(val outcome: Outcome, val at: Long)

    @Volatile
    private var remembered: Remembered? = null

    /**
     * Wydanie ogłoszone w tym uruchomieniu. Zapytanie idzie raz i zostaje.
     *
     * Znacznik żyje tyle, co proces aplikacji, więc powrót z tła go nie zeruje -
     * a o to właśnie chodzi: komunikat ma wyskakiwać raz na zimny start, nie za
     * każdym razem, gdy ktoś przełączy się na Kajet z innej aplikacji.
     */
    @Volatile
    private var announced: Release? = null

    /**
     * Czy człowiek zamknął już komunikat.
     *
     * Osobno od [announced], i to nie jest drobiazg. Gdyby samo pokazanie
     * zamykało sprawę, komunikat przepadałby przy każdym odtworzeniu ekranu -
     * a system potrafi odtworzyć aktywność choćby po zmianie wielkości pisma
     * czy przy włączonym „nie zachowuj aktywności". Wtedy człowiek widziałby
     * mignięcie i nic więcej. Teraz komunikat wraca dopóty, dopóki nie zostanie
     * naprawdę zamknięty.
     */
    @Volatile
    private var dismissed = false

    /** Numer wydania zainstalowanej aplikacji. Zero, gdy nie da się go odczytać. */
    fun installedRelease(context: Context): Int = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.longVersionCode.toInt()
    }.getOrDefault(0)

    /** Nazwa wersji zainstalowanej aplikacji, na przykład „26.08.01". */
    fun installedVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    /**
     * Wynik sprawdzenia. Zapamiętany wynik oddaje bez ruszania sieci, chyba że
     * [force] - tak robi przycisk w ustawieniach, bo tam człowiek prosi
     * o sprawdzenie TERAZ.
     */
    suspend fun check(context: Context, force: Boolean = false): Outcome =
        withContext(Dispatchers.IO) {
            if (!force) {
                remembered
                    ?.takeIf { System.currentTimeMillis() - it.at < REMEMBER_MS }
                    ?.let { return@withContext it.outcome }
            }

            val outcome = ask(context)

            // Nieudanego sprawdzenia nie zapamiętujemy jako odpowiedzi serwera:
            // sieć wraca, a wtedy przy najbliższej okazji ma być zapytanie,
            // nie odgrzany brak wyniku.
            if (outcome != Outcome.Unknown) {
                remembered = Remembered(outcome, System.currentTimeMillis())
            }
            outcome
        }

    /**
     * Wydanie do ogłoszenia albo nic. Serwer pytany jest o to raz na
     * uruchomienie, a odpowiedź zostaje aż do zamknięcia komunikatu.
     */
    suspend fun newReleaseToAnnounce(context: Context): Release? {
        if (dismissed) return null
        announced?.let { return it }

        val outcome = check(context)
        if (outcome !is Outcome.Newer) return null

        announced = outcome.release
        return outcome.release
    }

    /**
     * Komunikat zamknięty - „Później", wstecz, dotknięcie obok albo przejście
     * do pobierania. Wraca dopiero przy następnym uruchomieniu Kajetu.
     */
    fun stopAnnouncing() {
        dismissed = true
    }

    /** Do testów i do ręcznego sprawdzania: zapomina wynik i to, że coś pokazano. */
    fun forget() {
        remembered = null
        announced = null
        dismissed = false
    }

    private fun ask(context: Context): Outcome {
        val installed = installedRelease(context)
        // Bez znajomości własnego numeru nie ma czego z czym porównywać.
        if (installed <= 0) return Outcome.Unknown

        val body = fetch() ?: return Outcome.Unknown

        val release = runCatching {
            CloudClient.json.decodeFromString<Answer>(body).release
        }.getOrNull() ?: return Outcome.UpToDate

        // Ostro większe. Równe znaczy „ta sama wersja", a nie „jest nowsza".
        return if (release.versionCode > installed) Outcome.Newer(release) else Outcome.UpToDate
    }

    private fun fetch(): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(AccountStore.SERVER_URL + PATH).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")

            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (failure: Throwable) {
            // Brak sieci to zwykły stan, nie usterka.
            null
        } finally {
            runCatching { connection?.disconnect() }
        }
    }
}
