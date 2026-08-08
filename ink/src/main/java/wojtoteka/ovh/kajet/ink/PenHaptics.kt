package wojtoteka.ovh.kajet.ink

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import java.util.concurrent.Executors
import wojtoteka.ovh.kajet.core.model.InkTool

/**
 * Czym rysik ma drgać i jak mocno.
 *
 * [brush] to numer przyboru, który naśladuje usługa Lenovo. [strength] jest
 * mnożnikiem względem siły podstawowej urządzenia — jedynka znaczy „tyle, ile
 * tablet ma fabrycznie".
 */
data class PenProfile(val brush: Int, val strength: Float)

/**
 * Wibracje rysika Lenovo — wyłącznie przy pisaniu.
 *
 * Drga nie tablet — drga silniczek w samym rysiku, prowadzony po Bluetooth
 * przez systemową usługę `ZuiPenHapticService`. To ten sam silniczek, który
 * daje odgłos pisania: drganie i dźwięk to jedno, nie dwie rzeczy. Usługa sama
 * pilnuje, nad którą aplikacją unosi się rysik, i sama wysyła rozkaz;
 * aplikacja nie ma jak zagrać niczego bezpośrednio. Może natomiast powiedzieć
 * TRZY rzeczy:
 *
 * - [PACKAGES] — spis aplikacji, którym rysik w ogóle odpowiada. Kajet dopisuje
 *   się do niego przy wejściu do notatnika odręcznego i wypisuje przy wyjściu:
 *   drganie ma być tylko tam, gdzie rysik naprawdę pisze. (Siły zero usługa
 *   nie przyjmuje — sprawdzone na tablecie — więc spis to jedyna pewna cisza.)
 * - [BRUSH] — który przybór ma naśladować.
 * - [LEVEL] — jak mocno. Tego ustawienia NIE zapisujemy: to gałka całego
 *   urządzenia, ustawiona przez człowieka w ustawieniach Lenovo. Czytamy ją
 *   jako punkt odniesienia i przekazujemy własną wartość wprost do usługi,
 *   w rozkazie, który gaśnie razem z nim.
 *
 * Spis i przybór leżą w [Settings.Global] i zwykła aplikacja nie ma prawa ich
 * zapisać. Uprawnienie `WRITE_SECURE_SETTINGS` nadaje się raz, przez USB:
 *
 * ```
 * adb shell pm grant wojtoteka.ovh.kajet android.permission.WRITE_SECURE_SETTINGS
 * ```
 *
 * Bez tego uprawnienia wszystko tu po cichu nie robi nic — pisanie działa jak
 * zawsze, tylko rysik nie drga. Na urządzeniach innych niż Lenovo tych
 * ustawień po prostu nie ma i też się nic nie dzieje.
 *
 * ## Ulotność rozkazu
 *
 * Rozkaz wysłany wprost do usługi gaśnie, gdy Kajet schodzi w tło, gdy
 * przykryje go zasłona powiadomień (a ta NIE przechodzi przez cykl życia
 * aktywności!) i gdy rysik odjedzie od ekranu. Dlatego [MainActivity]
 * przypomina profil przy każdym zbliżeniu i dotknięciu rysika oraz przy
 * powrocie ostrości okna, a [StrokeCanvas] przed każdą kreską — wszystko to
 * kończy się natychmiast, gdy notatnik nie jest otwarty.
 *
 * ## Wątki
 *
 * Każdy odczyt i zapis to rozmowa z innym procesem. Wątek rysowania nie ma
 * prawa na nią czekać, więc wszystko idzie na własny wątek, a [enter], [use],
 * [leave] i [refresh] wracają natychmiast. To, co liczy się na wątku
 * wołającego, to same odczyty pól — bez zapytań do systemu.
 */
object PenHaptics {

    private const val TAG = "Kajet"

    private const val PACKAGES = "pen_haptic_packages"
    private const val BRUSH = "pen_haptic_brush"
    private const val LEVEL = "pen_haptic_level"

    /** Siła podstawowa, gdy urządzenie nie mówi swojej. Tyle ma tablet fabrycznie. */
    private const val DEFAULT_LEVEL = 3

    /*
      Zakres siły. Usługa Lenovo nie mówi, ile ma stopni — trójka to wartość
      fabryczna, a piątka jest górną granicą suwaka w ustawieniach Lenovo.
      Gdyby tablet miał inną skalę, przycięcie i tak nie wypuści wartości poza
      bezpieczny zakres.
    */
    private const val LEVEL_MIN = 1
    private const val LEVEL_MAX = 5

    /** Nazwa systemowej usługi rysika (`ZUI_PEN_HAPTIC_SERVICE` we framework.jar). */
    private const val SERVICE = "zui_pen_haptic"

    /** Najkrótszy odstęp między przypomnieniami wysyłanymi do usługi. */
    private const val REFRESH_GAP_MS = 400L

    /** Ile zostaje z siły przy włączonym oszczędzaniu baterii. */
    private const val POWER_SAVE_STRENGTH = 0.5f

    /*
      Numery przyborów wzięte wprost z zasobów systemowej aplikacji Lenovo
      (PenService.apk, `attr/brush`):
          ball_pen 0x20, pencil 0x21, chisel_marker 0x22, lenovo_pen 0x24.

      Gumki w tych zasobach nie ma, bo w ustawieniach Lenovo się jej nie
      wybiera. Jej numer — 0x23, czyli luka między markerem a piórem —
      podpatrzony w dzienniku usługi przy mazaniu w notatniku Lenovo:
          ZuiPenHapticService: setHapticContinuousParams called, id = 35
    */
    const val BALL_PEN = 32
    const val PENCIL = 33
    const val CHISEL_MARKER = 34
    const val ERASER = 35
    const val LENOVO_PEN = 36

    /** Profil w notatniku, zanim ekran zdąży powiedzieć, czym się rysuje. */
    val WRITING = PenProfile(BALL_PEN, 1f)

    private val worker = Executors.newSingleThreadExecutor { work ->
        Thread(work, "kajet-rysik").apply { isDaemon = true }
    }

    /** Ile ekranów notatnika odręcznego stoi jedno na drugim. */
    @Volatile
    private var depth = 0

    /** Czym rysuje otwarty notatnik. Poza nim bez znaczenia. */
    @Volatile
    private var notebook: PenProfile? = null

    /** Brzmienie zastane przy wejściu do notatnika — do oddania przy wyjściu. */
    @Volatile
    private var borrowed: Int? = null

    /** Siła ustawiona na urządzeniu przez człowieka. Nasz punkt odniesienia. */
    @Volatile
    private var baseLevel = 0

    @Volatile
    private var powerSaving = false

    /** Co usługa ostatnio dostała. Powtórka nie kosztuje już nic. */
    @Volatile
    private var sentBrush = -1

    @Volatile
    private var sentLevel = -1

    /** Dojście wprost do usługi rysika — sprawdzane raz, na wątku w tle. */
    @Volatile
    private var direct: Direct? = null

    @Volatile
    private var directChecked = false

    @Volatile
    private var lastRefresh = 0L

    /** Czy Kajet stoi w tej chwili w spisie usługi. */
    @Volatile
    private var listedNow = false

    /**
     * Czy rysik unosi się nad kartką albo nad polem pisania. Usługa Lenovo
     * drga nad CAŁĄ wpisaną aplikacją — paskami narzędzi i menu też — więc
     * powierzchnie pisania zgłaszają się same ([surfaceHover]), a wszędzie
     * indziej rozkaz jest dogaszany.
     */
    @Volatile
    private var overSurface = false

    /**
     * Przybór i siła przy danym narzędziu Kajetu.
     *
     * Lenovo daje pięć gotowych charakterów i jedną gałkę siły — „szorstkie"
     * albo „miękkie" nie da się tu zaprogramować, można tylko wybrać najbliższy
     * gotowy przybór i dobrać moc.
     *
     * Gumka jest głośniejsza od pisania i celowo: ma dać się poznać po samym
     * dotyku, że się ściera, a nie pisze. Zakreślacz odwrotnie — szeroki,
     * tępy, prawie płaski. Narzędzia, które nie zostawiają kreski (zaznaczanie,
     * kształty), dostają najcichszy sygnał: to praca techniczna, nie pisanie.
     */
    fun profileFor(tool: EditorTool, penKind: InkTool): PenProfile = when {
        tool.isEraser -> PenProfile(ERASER, 1.2f)
        tool == EditorTool.HIGHLIGHTER -> PenProfile(CHISEL_MARKER, 0.7f)
        !tool.writes -> PenProfile(BALL_PEN, 0.6f)
        penKind == InkTool.PENCIL -> PenProfile(PENCIL, 1f)
        penKind == InkTool.HIGHLIGHTER -> PenProfile(CHISEL_MARKER, 0.7f)
        else -> PenProfile(BALL_PEN, 1f)
    }

    /** Czy to urządzenie w ogóle ma usługę rysika Lenovo. */
    fun serviceAvailable(context: Context): Boolean =
        runCatching { context.getSystemService(SERVICE) != null }.getOrDefault(false)

    /**
     * Czy Kajet dostał uprawnienie do sterowania drganiem. Bez niego cała
     * reszta po cichu nie robi nic — ekran ustawień ma o tym powiedzieć
     * głośno, z komendą do nadania, zamiast zostawić zgadywanie.
     */
    fun permitted(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Wejście do notatki odręcznej (także okna rysowania w notatce tekstowej).
     * Zagnieżdżenia są liczone. Dopiero tutaj Kajet dopisuje się do spisu
     * usługi — wcześniej rysik ma go nie zauważać.
     */
    fun enter(context: Context) {
        depth += 1
        lastRefresh = SystemClock.uptimeMillis()
        val app = context.applicationContext
        worker.execute {
            if (baseLevel == 0) baseLevel = readLevel(app)
            if (borrowed == null) borrowed = readBrush(app)
            powerSaving = runCatching {
                app.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
            }.getOrDefault(false)
            listedNow = false
            deliver(app, force = true, writeSetting = true)
        }
    }

    /**
     * Wyjście z notatki. Przy ostatnim: Kajet wypisuje się ze spisu usługi
     * i oddaje brzmienie zastane przy wejściu — to ustawienia całego
     * urządzenia, więc nie zostają po nim przestawione.
     */
    fun leave(context: Context) {
        depth = (depth - 1).coerceAtLeast(0)
        if (depth > 0) return
        notebook = null
        overSurface = false
        sentBrush = -1
        sentLevel = -1
        val back = borrowed
        borrowed = null
        val app = context.applicationContext
        worker.execute {
            unlist(app)
            if (back != null) writeBrush(app, back)
            hushService(app)
        }
    }

    /** Zmiana narzędzia. Wraca od razu; reszta dzieje się w tle. */
    fun use(context: Context, profile: PenProfile) {
        if (notebook == profile) return
        notebook = profile
        if (depth == 0) return
        val app = context.applicationContext
        worker.execute { deliver(app, force = false, writeSetting = true) }
    }

    /**
     * Przypomina usłudze obowiązujący profil. Poza notatnikiem kończy się na
     * jednym porównaniu, a powtórki w krótkim odstępie są odrzucane — wołanie
     * tego przy każdym zbliżeniu rysika nic nie kosztuje.
     */
    fun refresh(context: Context) {
        if (depth == 0) return
        val now = SystemClock.uptimeMillis()
        if (now - lastRefresh < REFRESH_GAP_MS) return
        lastRefresh = now
        val app = context.applicationContext
        worker.execute { deliver(app, force = true, writeSetting = false) }
    }

    /**
     * Okno odzyskało ostrość — rozkaz mógł właśnie zgasnąć pod zasłoną
     * powiadomień albo oknem dialogowym. Przypomnienie idzie od razu,
     * bez czekania na odstęp.
     */
    fun wake(context: Context) {
        lastRefresh = 0L
        refresh(context)
    }

    /**
     * Powrót do aplikacji. Ma znaczenie tylko z otwartym notatnikiem: rozkaz
     * zgasł przy zejściu w tło, a spis mógł się zmienić, gdy Kajet leżał
     * odłożony (ustawienia Lenovo, przywrócenie kopii) — sprawdzamy od nowa.
     */
    fun register(context: Context) {
        val app = context.applicationContext
        if (depth == 0) {
            /*
              Sprzątanie po nieczystym wyjściu. Gdy system ubije proces
              z otwartym notatnikiem, [leave] nie zdąży się wykonać i Kajet
              zostaje w spisie usługi NA STAŁE — rysik drga wtedy nad każdym
              ekranem aplikacji. Powrót do aplikacji bez otwartego notatnika
              to pewny moment, w którym wpisu ma nie być.
            */
            worker.execute {
                unlist(app)
                hushService(app)
            }
            return
        }
        lastRefresh = SystemClock.uptimeMillis()
        worker.execute {
            powerSaving = runCatching {
                app.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
            }.getOrDefault(false)
            listedNow = false
            deliver(app, force = true, writeSetting = true)
        }
    }

    /**
     * Kajet schodzi w tło: oddaj brzmienie, jakie zastaliśmy. Usługa i tak
     * gasi wtedy haptykę sama, a znaczniki wysłanego czyścimy, żeby powrót
     * naprawdę wysłał rozkaz od nowa. [borrowed] zostaje — notatnik wciąż
     * jest otwarty i przy wyjściu z niego odda dokładnie tę wartość.
     */
    fun quiet(context: Context) {
        sentBrush = -1
        sentLevel = -1
        lastRefresh = 0L
        val back = borrowed
        val app = context.applicationContext
        worker.execute {
            if (back != null) writeBrush(app, back)
            hushService(app)
        }
    }

    /**
     * Kartka albo pole pisania mówi, czy rysik jest nad nią. Wjazd nad
     * powierzchnię zamawia drganie, zjazd na paski i menu je gasi.
     * Wołane przy każdym ruchu najechania, więc powtórki wracają po jednym
     * porównaniu.
     */
    fun surfaceHover(context: Context, over: Boolean) {
        if (overSurface == over) return
        overSurface = over
        if (depth == 0) return
        val app = context.applicationContext
        worker.execute {
            if (over) deliver(app, force = true, writeSetting = false) else hushService(app)
        }
    }

    /**
     * Rysik zjawił się w oknie poza powierzchnią pisania. Usługa Lenovo
     * właśnie sama wznowiła drganie (robi to przy każdym najechaniu nad
     * wpisaną aplikację), więc trzeba je od razu dogasić.
     */
    fun settle(context: Context) {
        if (depth == 0 || overSurface) return
        val app = context.applicationContext
        worker.execute { hushService(app) }
    }

    /** Profil obowiązujący w tej chwili. Null poza notatnikiem — czyli cisza. */
    private fun wanted(): PenProfile? = if (depth > 0) notebook ?: WRITING else null

    private fun levelFor(profile: PenProfile): Int {
        val base = if (baseLevel > 0) baseLevel else DEFAULT_LEVEL
        var value = base * profile.strength
        if (powerSaving) value *= POWER_SAVE_STRENGTH
        return Math.round(value).coerceIn(LEVEL_MIN, LEVEL_MAX)
    }

    /** Wysyła obowiązujący profil. Tylko z wątku [worker]. */
    private fun deliver(context: Context, force: Boolean, writeSetting: Boolean) {
        val profile = wanted() ?: return
        if (!listedNow) ensureListed(context)
        val level = levelFor(profile)

        // Ustawienie przyboru zapisuje się niezależnie od położenia rysika:
        // z niego usługa bierze brzmienie, gdy sama startuje przy zbliżeniu.
        // Zmiana samej siły nie ma po co ruszać dysku.
        if (writeSetting && profile.brush != sentBrush) writeBrush(context, profile.brush)

        // Rozkaz drgania idzie tylko nad kartką albo polem pisania — nad
        // paskami i menu ma być cisza. Wjazd nad kartkę dośle go od razu.
        if (!overSurface) return
        if (!force && profile.brush == sentBrush && level == sentLevel) return

        sentBrush = profile.brush
        sentLevel = level

        // Wprost do usługi, bo to działa od razu.
        tellService(context, profile.brush, level)
    }

    private fun readBrush(context: Context): Int? = runCatching {
        Settings.Global.getInt(context.contentResolver, BRUSH)
    }.getOrNull()

    private fun readLevel(context: Context): Int = runCatching {
        Settings.Global.getInt(context.contentResolver, LEVEL)
    }.getOrDefault(DEFAULT_LEVEL)

    /**
     * Droga na skróty: prosto do systemowej usługi rysika, tą samą metodą,
     * którą woła notatnik Lenovo.
     *
     * Po co, skoro ustawienie wystarcza: bo usługa zagląda do ustawienia
     * dopiero wtedy, gdy rysik zbliża się do ekranu, więc po zmianie
     * narzędzia stara barwa trzymała się jeszcze kilka sekund. Wywołana
     * wprost, usługa wysyła rozkaz do rysika od razu. To także jedyna droga
     * do siły, bo ustawienia siły nie ruszamy.
     *
     * To nieudokumentowane API producenta i Android potrafi zamknąć do niego
     * dostęp aplikacjom z zewnątrz. Dlatego wszystko jest w [runCatching],
     * sprawdzamy raz, a przy odmowie zostaje droga przez ustawienia — czyli
     * sam wybór przyboru, bez sterowania siłą.
     */
    private fun tellService(context: Context, brush: Int, level: Int): Boolean {
        val direct = direct(context) ?: return false
        return runCatching {
            // Trzeci parametr to jedynka — tyle wysyła usługa do rysika przy
            // pisaniu w notatniku Lenovo (rozkaz `[brzmienie, siła, 1, 0]`).
            direct.setParams.invoke(direct.manager, brush, level, 1) as? Boolean == true
        }.onFailure { failure ->
            Log.i(TAG, "Rysik: usługa nie przyjęła rozkazu (${failure.javaClass.simpleName})")
        }.getOrDefault(false)
    }

    /**
     * Gasi trwający rozkaz drgania.
     *
     * Usługa zatrzymuje haptykę sama, gdy rysik przejeżdża nad cudzą
     * aplikację — ale przejście z notatki do biblioteki to wciąż Kajet,
     * więc rozkaz ciągły zostawał w mocy i rysik drgał w menu głównym.
     * Zero jako parametr podpatrzone w SDK Lenovo: `PenHaptic.stopHaptic()`
     * woła `ZuiPenHapticManager.stopHaptic(0)`.
     */
    private fun hushService(context: Context) {
        val direct = direct(context) ?: return
        val stop = direct.stop ?: return
        runCatching { stop.invoke(direct.manager, 0) }
            .onFailure { failure ->
                Log.i(TAG, "Rysik: nie udało się zgasić drgania (${failure.javaClass.simpleName})")
            }
    }

    private fun direct(context: Context): Direct? {
        if (directChecked) return direct

        direct = runCatching {
            val manager = context.getSystemService(SERVICE) ?: return@runCatching null
            val setParams = manager.javaClass.getMethod(
                "setHapticContinuousParams",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            // Gaszenie jest osobno i nieobowiązkowo: bez niego zostaje
            // przynajmniej sterowanie brzmieniem, jak dotychczas.
            val stop = runCatching {
                manager.javaClass.getMethod("stopHaptic", Int::class.javaPrimitiveType)
            }.getOrNull()
            Direct(manager, setParams, stop)
        }.onFailure { failure ->
            Log.i(TAG, "Rysik: brak dojścia wprost do usługi (${failure.javaClass.simpleName})")
        }.getOrNull()

        directChecked = true

        Log.i(
            TAG,
            if (direct != null) {
                "Rysik: rozkazy idą wprost do usługi, siła sterowalna"
            } else {
                "Rysik: rozkazy idą przez ustawienia systemowe, bez sterowania siłą"
            },
        )
        return direct
    }

    private class Direct(
        val manager: Any,
        val setParams: java.lang.reflect.Method,
        val stop: java.lang.reflect.Method?,
    )

    private fun writeBrush(context: Context, brush: Int) {
        write(context, BRUSH, brush)
    }

    /**
     * Dopisuje Kajet do spisu aplikacji, którym rysik odpowiada. Wpisy innych
     * aplikacji zostają nietknięte.
     */
    private fun ensureListed(context: Context) {
        val listed = listed(context) ?: return
        if (context.packageName in listed) {
            listedNow = true
            return
        }

        val updated = (listed + context.packageName).joinToString(";")
        listedNow = write(context, PACKAGES, updated)
        if (listedNow) {
            Log.i(TAG, "Rysik: Kajet dopisany do spisu aplikacji z haptyką")
        }
    }

    /** Odwrotność [ensureListed]: notatnik zamknięty, rysik ma Kajet omijać. */
    private fun unlist(context: Context) {
        listedNow = false
        val listed = listed(context) ?: return
        if (context.packageName !in listed) return

        val updated = listed.filterNot { it == context.packageName }.joinToString(";")
        if (write(context, PACKAGES, updated)) {
            Log.i(TAG, "Rysik: Kajet wypisany ze spisu aplikacji z haptyką")
        }
    }

    private fun listed(context: Context): List<String>? {
        val current = runCatching {
            Settings.Global.getString(context.contentResolver, PACKAGES)
        }.getOrNull() ?: return null
        return current.split(';').map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun write(context: Context, key: String, value: Any): Boolean = runCatching {
        when (value) {
            is Int -> Settings.Global.putInt(context.contentResolver, key, value)
            else -> Settings.Global.putString(context.contentResolver, key, value.toString())
        }
    }.onFailure { failure ->
        // Najczęściej brak WRITE_SECURE_SETTINGS. Mówimy o tym w dzienniku
        // i idziemy dalej — drganie rysika to wygoda, nie warunek pisania.
        Log.i(TAG, "Rysik: nie udało się zapisać $key (${failure.javaClass.simpleName})")
    }.getOrDefault(false)
}
