package wojtoteka.ovh.kajet.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode
import wojtoteka.ovh.kajet.core.text.Strings

enum class FingerBehavior {
    SCROLL,
    DRAW,
    ;

    val labelPl: String
        get() = if (this == SCROLL) "Palec przewija stronę" else "Palec też rysuje"

    fun label(words: Strings): String = when {
        !words.english -> labelPl
        this == SCROLL -> "Finger scrolls the page"
        else -> "Finger draws too"
    }
}

/**
 * Strona, po której stoi pasek narzędzi w edytorach. Domyślnie lewa, ale
 * leworęczni trzymają dłoń właśnie nad lewą krawędzią i klikają pasek
 * łokciem zamiast palcem — dla nich jest prawa.
 */
enum class ToolbarSide {
    LEFT,
    RIGHT,
}

enum class ThemeChoice {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    val labelPl: String
        get() = when (this) {
            SYSTEM -> "Taki jak w systemie"
            LIGHT -> "Jasny"
            DARK -> "Ciemny"
        }

    fun label(words: Strings): String = when (this) {
        SYSTEM -> words.themeSystem
        LIGHT -> words.themeLight
        DARK -> words.themeDark
    }
}

data class RememberedPen(
    val tool: String? = null,
    val color: Int? = null,
    val width: Float? = null,
    val opacity: Float? = null,
    val highlighterColor: Int? = null,
    val highlighterWidth: Float? = null,
    val highlighterOpacity: Float? = null,
    val eraserRadius: Float? = null,
)

/** Czym rysowało się kształty ostatnim razem. Wraca przy następnej notatce. */
data class RememberedShape(
    val kind: String? = null,
    val color: Int? = null,
    val strokeWidth: Float? = null,
    val fill: Int? = null,
    val opacity: Float? = null,
    val square: Boolean? = null,
)

data class KajetSettings(
    val libraryFolder: String? = null,
    // Rysik pisze, palec przesuwa kartkę — jak w zwykłym zeszycie. Ale na
    // urządzeniu bez rysika ta zasada oznaczałaby, że nie da się pisać wcale,
    // więc domyślna wartość zależy od sprzętu (patrz SettingsStore).
    val fingerBehavior: FingerBehavior = FingerBehavior.SCROLL,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val defaultPageMode: PageMode = PageMode.A4,
    val defaultBackground: PageBackground = PageBackground.LINED,
    /*
      Pomoc przy pisaniu kodu: domykanie nawiasów i znaczników HTML. Domyślnie
      włączona, bo tego się dziś po edytorze spodziewa - ale komu przeszkadza,
      ten ją gasi w ustawieniach.
    */
    val codeAssist: Boolean = true,
    /*
      Wybrany język. „system" znaczy: tak, jak ustawiony jest telefon albo
      tablet - po polsku, gdy system jest po polsku, po angielsku w każdym
      innym przypadku.
    */
    val language: String = "system",
    val toolbarSide: ToolbarSide = ToolbarSide.LEFT,
    val recentColors: List<Int> = emptyList(),
    val pens: RememberedPen = RememberedPen(),
    val shapes: RememberedShape = RememberedShape(),
) {
    companion object {
        const val RECENT_COLOR_LIMIT = 16
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kajet")

class SettingsStore(private val context: Context) {

    private object Keys {
        val folder = stringPreferencesKey("katalog_biblioteki")
        val finger = stringPreferencesKey("zachowanie_palca")
        val theme = stringPreferencesKey("motyw")
        val pageMode = stringPreferencesKey("domyslny_tryb_strony")
        val background = stringPreferencesKey("domyslne_tlo")
        val codeAssist = booleanPreferencesKey("pomoc_przy_kodzie")
        val language = stringPreferencesKey("jezyk")
        val toolbarSide = stringPreferencesKey("strona_paska")
        val recentColors = stringPreferencesKey("ostatnie_kolory")

        val penTool = stringPreferencesKey("pisak_rodzaj")
        val penColor = intPreferencesKey("pisak_kolor")
        val penWidth = floatPreferencesKey("pisak_grubosc")
        val penOpacity = floatPreferencesKey("pisak_krycie")
        val highlighterColor = intPreferencesKey("zakreslacz_kolor")
        val highlighterWidth = floatPreferencesKey("zakreslacz_grubosc")
        val highlighterOpacity = floatPreferencesKey("zakreslacz_krycie")
        val eraserRadius = floatPreferencesKey("gumka_promien")

        val shapeKind = stringPreferencesKey("ksztalt_rodzaj")
        val shapeColor = intPreferencesKey("ksztalt_kolor")
        val shapeWidth = floatPreferencesKey("ksztalt_grubosc")
        val shapeFill = intPreferencesKey("ksztalt_wypelnienie")
        val shapeOpacity = floatPreferencesKey("ksztalt_krycie")
        val shapeSquare = booleanPreferencesKey("ksztalt_proporcje")
    }

    // Telefon bez rysika: gdyby palec tylko przewijał, w notatce odręcznej
    // nie dałoby się postawić ani jednej kreski. Dlatego bez zapisanego wyboru
    // palec rysuje wszędzie tam, gdzie system nie zgłasza żadnego rysika.
    private val defaultFinger: FingerBehavior by lazy {
        val stylusPresent = android.view.InputDevice.getDeviceIds().any { id ->
            android.view.InputDevice.getDevice(id)?.supportsSource(android.view.InputDevice.SOURCE_STYLUS) == true
        }
        if (stylusPresent) FingerBehavior.SCROLL else FingerBehavior.DRAW
    }

    /*
      Ustawienia z dysku.

      `.catch` nie jest ozdobą. Cały ekran aplikacji czeka na PIERWSZĄ wartość
      z tego strumienia — dopóki jej nie ma, rysuje się samo tło biurka, a to
      w ciemnym motywie wygląda dokładnie jak czarny ekran, z którego nie ma
      wyjścia. Gdyby odczyt pliku poszedł źle (uszkodzony plik, brak miejsca,
      zabrany dostęp), strumień przewróciłby się bez jednej emisji i aplikacja
      zostałaby tak na zawsze. Zamiast tego wchodzimy na ustawieniach
      domyślnych i mówimy o tym w logu.
    */
    val settings: Flow<KajetSettings> = context.dataStore.data.catch { failure ->
        android.util.Log.w("Kajet", "Nie udało się odczytać ustawień", failure)
        emit(emptyPreferences())
    }.map { data ->
        KajetSettings(
            libraryFolder = data[Keys.folder],
            fingerBehavior = data[Keys.finger]?.let(::fingerBehaviorFrom) ?: defaultFinger,
            theme = data[Keys.theme]?.let(::themeChoiceFrom) ?: ThemeChoice.SYSTEM,
            defaultPageMode = data[Keys.pageMode]?.let { name ->
                runCatching { PageMode.valueOf(name) }.getOrNull()
            } ?: PageMode.A4,
            defaultBackground = data[Keys.background]?.let { name ->
                runCatching { PageBackground.valueOf(name) }.getOrNull()
            } ?: PageBackground.LINED,
            codeAssist = data[Keys.codeAssist] ?: true,
            language = data[Keys.language] ?: "system",
            toolbarSide = data[Keys.toolbarSide]?.let { name ->
                runCatching { ToolbarSide.valueOf(name) }.getOrNull()
            } ?: ToolbarSide.LEFT,
            recentColors = data[Keys.recentColors]
                ?.split(',')
                ?.mapNotNull { it.trim().toLongOrNull()?.toInt() }
                .orEmpty(),
            pens = RememberedPen(
                tool = data[Keys.penTool],
                color = data[Keys.penColor],
                width = data[Keys.penWidth],
                opacity = data[Keys.penOpacity],
                highlighterColor = data[Keys.highlighterColor],
                highlighterWidth = data[Keys.highlighterWidth],
                highlighterOpacity = data[Keys.highlighterOpacity],
                eraserRadius = data[Keys.eraserRadius],
            ),
            shapes = RememberedShape(
                kind = data[Keys.shapeKind],
                color = data[Keys.shapeColor],
                strokeWidth = data[Keys.shapeWidth],
                fill = data[Keys.shapeFill],
                opacity = data[Keys.shapeOpacity],
                square = data[Keys.shapeSquare],
            ),
        )
    }

    suspend fun setShape(shape: RememberedShape) {
        context.dataStore.edit { data ->
            shape.kind?.let { data[Keys.shapeKind] = it }
            shape.color?.let { data[Keys.shapeColor] = it }
            shape.strokeWidth?.let { data[Keys.shapeWidth] = it }
            // Wypełnienie zapisuje się także wtedy, gdy wynosi zero: „bez
            // wypełnienia" to wybór, nie brak wyboru.
            shape.fill?.let { data[Keys.shapeFill] = it }
            shape.opacity?.let { data[Keys.shapeOpacity] = it }
            shape.square?.let { data[Keys.shapeSquare] = it }
        }
    }

    suspend fun setPen(pens: RememberedPen) {
        context.dataStore.edit { data ->
            pens.tool?.let { data[Keys.penTool] = it }
            pens.color?.let { data[Keys.penColor] = it }
            pens.width?.let { data[Keys.penWidth] = it }
            pens.opacity?.let { data[Keys.penOpacity] = it }
            pens.highlighterColor?.let { data[Keys.highlighterColor] = it }
            pens.highlighterWidth?.let { data[Keys.highlighterWidth] = it }
            pens.highlighterOpacity?.let { data[Keys.highlighterOpacity] = it }
            pens.eraserRadius?.let { data[Keys.eraserRadius] = it }
        }
    }

    suspend fun rememberColor(argb: Int) {
        context.dataStore.edit { data ->
            val previous = data[Keys.recentColors]
                ?.split(',')
                ?.mapNotNull { it.trim().toLongOrNull()?.toInt() }
                .orEmpty()
            val updated = (listOf(argb) + previous.filterNot { it == argb })
                .take(KajetSettings.RECENT_COLOR_LIMIT)
            data[Keys.recentColors] = updated.joinToString(",")
        }
    }

    suspend fun setLibraryFolder(uri: String) {
        context.dataStore.edit { it[Keys.folder] = uri }
    }

    suspend fun setFingerBehavior(value: FingerBehavior) {
        context.dataStore.edit { it[Keys.finger] = value.name }
    }

    suspend fun setTheme(value: ThemeChoice) {
        context.dataStore.edit { it[Keys.theme] = value.name }
    }

    suspend fun setDefaultPageMode(mode: PageMode) {
        context.dataStore.edit { it[Keys.pageMode] = mode.name }
    }

    suspend fun setDefaultBackground(background: PageBackground) {
        context.dataStore.edit { it[Keys.background] = background.name }
    }

    suspend fun setCodeAssist(enabled: Boolean) {
        context.dataStore.edit { it[Keys.codeAssist] = enabled }
    }

    suspend fun setLanguage(id: String) {
        context.dataStore.edit { it[Keys.language] = id }
    }

    suspend fun setToolbarSide(side: ToolbarSide) {
        context.dataStore.edit { it[Keys.toolbarSide] = side.name }
    }
}

private fun fingerBehaviorFrom(stored: String): FingerBehavior? = when (stored) {
    "SCROLL", "PRZEWIJA" -> FingerBehavior.SCROLL
    "DRAW", "RYSUJE" -> FingerBehavior.DRAW
    else -> null
}

private fun themeChoiceFrom(stored: String): ThemeChoice? = when (stored) {
    "SYSTEM" -> ThemeChoice.SYSTEM
    "LIGHT", "JASNY" -> ThemeChoice.LIGHT
    "DARK", "CIEMNY" -> ThemeChoice.DARK
    else -> null
}
