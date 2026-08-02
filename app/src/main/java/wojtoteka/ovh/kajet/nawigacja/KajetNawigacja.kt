package wojtoteka.ovh.kajet.nawigacja

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import wojtoteka.ovh.kajet.Kontener
import wojtoteka.ovh.kajet.core.model.ItemType
import wojtoteka.ovh.kajet.core.model.LibraryItem
import wojtoteka.ovh.kajet.ekran.biblioteka.EkranBiblioteki
import wojtoteka.ovh.kajet.ekran.biblioteka.ModelBiblioteki
import wojtoteka.ovh.kajet.ekran.notatka.EkranNotatki
import wojtoteka.ovh.kajet.ekran.start.EkranWyboruKatalogu
import wojtoteka.ovh.kajet.ekran.ustawienia.EkranUstawien
import wojtoteka.ovh.kajet.storage.UstawieniaKajetu
import wojtoteka.ovh.kajet.storage.ZachowaniePalca

/** Adresy ekranów. Ścieżki plików wędrują w adresie zakodowane, bo mają ukośniki. */
object Trasy {
    const val START = "start"
    const val BIBLIOTEKA = "biblioteka"
    const val USTAWIENIA = "ustawienia"

    const val NOTATKA = "notatka/{sciezka}"
    const val KOD = "kod/{sciezka}"

    fun notatka(sciezka: String) = "notatka/" + Uri.encode(sciezka)
    fun kod(sciezka: String) = "kod/" + Uri.encode(sciezka)
}

@Composable
fun KajetNawigacja(kontener: Kontener, ustawienia: UstawieniaKajetu) {
    val nawigacja = rememberNavController()
    val zakres = rememberCoroutineScope()

    val model: ModelBiblioteki = viewModel(
        factory = ModelBiblioteki.Fabryka(kontener.biblioteka),
    )

    val start = if (ustawienia.katalogBiblioteki.isNullOrBlank()) Trasy.START else Trasy.BIBLIOTEKA

    NavHost(navController = nawigacja, startDestination = start) {

        composable(Trasy.START) {
            EkranWyboruKatalogu(
                onWybrano = { uri ->
                    zakres.launch {
                        kontener.biblioteka.ustawKatalogBiblioteki(uri)
                        model.przebudujIndeks()
                        nawigacja.navigate(Trasy.BIBLIOTEKA) {
                            popUpTo(Trasy.START) { inclusive = true }
                        }
                    }
                },
            )
        }

        composable(Trasy.BIBLIOTEKA) {
            EkranBiblioteki(
                model = model,
                repo = kontener.biblioteka,
                domyslnyTryb = ustawienia.domyslnyTrybStrony,
                domyslneTlo = ustawienia.domyslneTlo,
                onOtworzWpis = { wpis -> otworz(nawigacja, wpis) },
                onUstawienia = { nawigacja.navigate(Trasy.USTAWIENIA) },
            )
        }

        composable(
            route = Trasy.NOTATKA,
            arguments = listOf(navArgument("sciezka") { type = NavType.StringType }),
        ) { wpis ->
            val sciezka = Uri.decode(wpis.arguments?.getString("sciezka").orEmpty())
            EkranNotatki(
                sciezka = sciezka,
                repo = kontener.biblioteka,
                ustawienia = kontener.ustawienia,
                palecRysuje = ustawienia.zachowaniePalca == ZachowaniePalca.RYSUJE,
                onWstecz = {
                    model.odswiezPoZmianie()
                    nawigacja.popBackStack()
                },
            )
        }

        composable(Trasy.USTAWIENIA) {
            EkranUstawien(
                magazynUstawien = kontener.ustawienia,
                repo = kontener.biblioteka,
                onWstecz = { nawigacja.popBackStack() },
                onPrzebudujIndeks = { model.przebudujIndeks() },
            )
        }
    }
}

private fun otworz(nawigacja: NavHostController, wpis: LibraryItem) {
    when (wpis.type) {
        ItemType.NOTATKA -> nawigacja.navigate(Trasy.notatka(wpis.path))
        ItemType.PLIK_KODU, ItemType.INNY_PLIK -> nawigacja.navigate(Trasy.kod(wpis.path))
        ItemType.FOLDER -> Unit
    }
}
