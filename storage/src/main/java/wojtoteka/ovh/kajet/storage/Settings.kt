package wojtoteka.ovh.kajet.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode

enum class FingerBehavior {
    SCROLL,
    DRAW,
    ;

    val labelPl: String
        get() = if (this == SCROLL) "Palec przewija stronę" else "Palec też rysuje"
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

data class KajetSettings(
    val libraryFolder: String? = null,
    // Rysik pisze, palec przesuwa kartkę. Tak trzyma się tablet w ręku i tak
    // działa zwykły zeszyt. Kto woli rysować palcem, przestawia to w ustawieniach.
    val fingerBehavior: FingerBehavior = FingerBehavior.SCROLL,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val autosaveInterval: Int = 5,
    val defaultPageMode: PageMode = PageMode.A4,
    val defaultBackground: PageBackground = PageBackground.LINED,
    val handwritingModelDownloaded: Boolean = false,
    val recentColors: List<Int> = emptyList(),
    val pens: RememberedPen = RememberedPen(),
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
        val autosave = intPreferencesKey("odstep_autozapisu")
        val pageMode = stringPreferencesKey("domyslny_tryb_strony")
        val background = stringPreferencesKey("domyslne_tlo")
        val handwritingModel = booleanPreferencesKey("model_pisma_pobrany")
        val recentColors = stringPreferencesKey("ostatnie_kolory")

        val penTool = stringPreferencesKey("pisak_rodzaj")
        val penColor = intPreferencesKey("pisak_kolor")
        val penWidth = floatPreferencesKey("pisak_grubosc")
        val penOpacity = floatPreferencesKey("pisak_krycie")
        val highlighterColor = intPreferencesKey("zakreslacz_kolor")
        val highlighterWidth = floatPreferencesKey("zakreslacz_grubosc")
        val highlighterOpacity = floatPreferencesKey("zakreslacz_krycie")
        val eraserRadius = floatPreferencesKey("gumka_promien")
    }

    val settings: Flow<KajetSettings> = context.dataStore.data.map { data ->
        KajetSettings(
            libraryFolder = data[Keys.folder],
            fingerBehavior = data[Keys.finger]?.let(::fingerBehaviorFrom) ?: FingerBehavior.SCROLL,
            theme = data[Keys.theme]?.let(::themeChoiceFrom) ?: ThemeChoice.SYSTEM,
            autosaveInterval = data[Keys.autosave] ?: 5,
            defaultPageMode = data[Keys.pageMode]?.let { name ->
                runCatching { PageMode.valueOf(name) }.getOrNull()
            } ?: PageMode.A4,
            defaultBackground = data[Keys.background]?.let { name ->
                runCatching { PageBackground.valueOf(name) }.getOrNull()
            } ?: PageBackground.LINED,
            handwritingModelDownloaded = data[Keys.handwritingModel] ?: false,
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
        )
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

    suspend fun setAutosaveInterval(seconds: Int) {
        context.dataStore.edit { it[Keys.autosave] = seconds.coerceIn(2, 60) }
    }

    suspend fun setDefaultPageMode(mode: PageMode) {
        context.dataStore.edit { it[Keys.pageMode] = mode.name }
    }

    suspend fun setDefaultBackground(background: PageBackground) {
        context.dataStore.edit { it[Keys.background] = background.name }
    }

    suspend fun setHandwritingModelDownloaded(downloaded: Boolean) {
        context.dataStore.edit { it[Keys.handwritingModel] = downloaded }
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
