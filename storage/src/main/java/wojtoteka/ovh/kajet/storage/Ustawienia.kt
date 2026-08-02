package wojtoteka.ovh.kajet.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode

/** Co ma się dziać, kiedy ekranu dotknie palec. */
enum class ZachowaniePalca {
    PRZEWIJA,
    RYSUJE,
    ;

    val nazwaPl: String
        get() = if (this == PRZEWIJA) "Palec przewija stronę" else "Palec też rysuje"
}

enum class WyborMotywu {
    SYSTEM,
    JASNY,
    CIEMNY,
    ;

    val nazwaPl: String
        get() = when (this) {
            SYSTEM -> "Taki jak w systemie"
            JASNY -> "Jasny"
            CIEMNY -> "Ciemny"
        }
}

data class UstawieniaKajetu(
    /** Adres katalogu biblioteki wybranego przez użytkownika. Pusty, kiedy jeszcze nie wybrał. */
    val katalogBiblioteki: String? = null,
    val zachowaniePalca: ZachowaniePalca = ZachowaniePalca.PRZEWIJA,
    val motyw: WyborMotywu = WyborMotywu.SYSTEM,
    /** Adres serwera, który uruchamia kod w językach innych niż Python. */
    val adresSerweraKodu: String = DOMYSLNY_SERWER_KODU,
    /** Co ile sekund notatka zapisuje się sama. */
    val odstepAutozapisu: Int = 5,
    val domyslnyTrybStrony: PageMode = PageMode.A4,
    val domyslneTlo: PageBackground = PageBackground.LINIE,
    /** Czy model rozpoznawania pisma po polsku jest już pobrany. */
    val modelPismaPobrany: Boolean = false,
) {
    companion object {
        const val DOMYSLNY_SERWER_KODU = "https://emkc.org/api/v2/piston"
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kajet")

/**
 * Ustawienia aplikacji. Leżą w katalogu aplikacji, bo to nie są notatki,
 * tylko sposób, w jaki użytkownik lubi pracować.
 */
class MagazynUstawien(private val context: Context) {

    private object Klucze {
        val katalog = stringPreferencesKey("katalog_biblioteki")
        val palec = stringPreferencesKey("zachowanie_palca")
        val motyw = stringPreferencesKey("motyw")
        val serwer = stringPreferencesKey("adres_serwera_kodu")
        val autozapis = intPreferencesKey("odstep_autozapisu")
        val trybStrony = stringPreferencesKey("domyslny_tryb_strony")
        val tlo = stringPreferencesKey("domyslne_tlo")
        val modelPisma = booleanPreferencesKey("model_pisma_pobrany")
    }

    val ustawienia: Flow<UstawieniaKajetu> = context.dataStore.data.map { dane ->
        UstawieniaKajetu(
            katalogBiblioteki = dane[Klucze.katalog],
            zachowaniePalca = dane[Klucze.palec]?.let { nazwa ->
                runCatching { ZachowaniePalca.valueOf(nazwa) }.getOrNull()
            } ?: ZachowaniePalca.PRZEWIJA,
            motyw = dane[Klucze.motyw]?.let { nazwa ->
                runCatching { WyborMotywu.valueOf(nazwa) }.getOrNull()
            } ?: WyborMotywu.SYSTEM,
            adresSerweraKodu = dane[Klucze.serwer] ?: UstawieniaKajetu.DOMYSLNY_SERWER_KODU,
            odstepAutozapisu = dane[Klucze.autozapis] ?: 5,
            domyslnyTrybStrony = dane[Klucze.trybStrony]?.let { nazwa ->
                runCatching { PageMode.valueOf(nazwa) }.getOrNull()
            } ?: PageMode.A4,
            domyslneTlo = dane[Klucze.tlo]?.let { nazwa ->
                runCatching { PageBackground.valueOf(nazwa) }.getOrNull()
            } ?: PageBackground.LINIE,
            modelPismaPobrany = dane[Klucze.modelPisma] ?: false,
        )
    }

    suspend fun ustawKatalogBiblioteki(uri: String) {
        context.dataStore.edit { it[Klucze.katalog] = uri }
    }

    suspend fun ustawZachowaniePalca(wartosc: ZachowaniePalca) {
        context.dataStore.edit { it[Klucze.palec] = wartosc.name }
    }

    suspend fun ustawMotyw(wartosc: WyborMotywu) {
        context.dataStore.edit { it[Klucze.motyw] = wartosc.name }
    }

    suspend fun ustawAdresSerweraKodu(adres: String) {
        context.dataStore.edit { it[Klucze.serwer] = adres.trim().trimEnd('/') }
    }

    suspend fun ustawOdstepAutozapisu(sekundy: Int) {
        context.dataStore.edit { it[Klucze.autozapis] = sekundy.coerceIn(2, 60) }
    }

    suspend fun ustawDomyslnyTrybStrony(tryb: PageMode) {
        context.dataStore.edit { it[Klucze.trybStrony] = tryb.name }
    }

    suspend fun ustawDomyslneTlo(tlo: PageBackground) {
        context.dataStore.edit { it[Klucze.tlo] = tlo.name }
    }

    suspend fun ustawModelPismaPobrany(pobrany: Boolean) {
        context.dataStore.edit { it[Klucze.modelPisma] = pobrany }
    }
}
