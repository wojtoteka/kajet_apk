package wojtoteka.ovh.kajet.ekran.ustawienia

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.EtykietaSekcji
import wojtoteka.ovh.kajet.core.design.component.IkonaPrzycisk
import wojtoteka.ovh.kajet.core.design.component.LiniaPozioma
import wojtoteka.ovh.kajet.core.design.component.PrzyciskWtorny
import wojtoteka.ovh.kajet.core.design.component.ZnakKajetu
import wojtoteka.ovh.kajet.core.design.component.liniaMarginesu
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode
import wojtoteka.ovh.kajet.ekran.biblioteka.WyborWiersz
import wojtoteka.ovh.kajet.storage.MagazynUstawien
import wojtoteka.ovh.kajet.storage.RepozytoriumBiblioteki
import wojtoteka.ovh.kajet.storage.UstawieniaKajetu
import wojtoteka.ovh.kajet.storage.WyborMotywu
import wojtoteka.ovh.kajet.storage.ZachowaniePalca

/**
 * Ustawienia. Jedna kolumna z sekcjami rozdzielonymi liniami,
 * bez kart i bez siatki, bo to jest lista decyzji, a nie galeria.
 */
@Composable
fun EkranUstawien(
    magazynUstawien: MagazynUstawien,
    repo: RepozytoriumBiblioteki,
    onWstecz: () -> Unit,
    onPrzebudujIndeks: () -> Unit,
) {
    val ustawienia by magazynUstawien.ustawienia.collectAsStateWithLifecycle(UstawieniaKajetu())
    val zakres = rememberCoroutineScope()

    val wyborKatalogu = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            zakres.launch {
                repo.ustawKatalogBiblioteki(uri)
                onPrzebudujIndeks()
            }
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(Kajet.colors.desk),
    ) {
        Column(
            Modifier
                .width(Kajet.dimens.railWidth)
                .fillMaxHeight()
                .liniaMarginesu(Kajet.colors.line),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.height(64.dp), contentAlignment = Alignment.Center) {
                ZnakKajetu(modifier = Modifier.size(26.dp), kolor = Kajet.colors.accent)
            }
            IkonaPrzycisk(KajetIcons.Wstecz, "Wróć do biblioteki", onWstecz)
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(Kajet.colors.sheet)
                .verticalScroll(rememberScrollState())
                .padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Text("Ustawienia", style = Kajet.type.display, color = Kajet.colors.text)

            Sekcja(
                tytul = "Katalog na notatki",
                opis = "Tu leżą wszystkie Twoje pliki. Po zmianie katalogu Kajet przeczyta go od nowa.",
            ) {
                Text(
                    text = ustawienia.katalogBiblioteki?.let { czytelnyAdres(it) }
                        ?: "Jeszcze nie wybrano folderu.",
                    style = Kajet.type.body,
                    color = Kajet.colors.muted,
                    modifier = Modifier.widthIn(max = Kajet.dimens.readingWidth),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrzyciskWtorny(
                        tekst = "Zmień folder",
                        onClick = { wyborKatalogu.launch(null) },
                        ikona = KajetIcons.Folder,
                    )
                    PrzyciskWtorny(
                        tekst = "Odbuduj spis notatek",
                        onClick = onPrzebudujIndeks,
                        ikona = KajetIcons.Przywroc,
                    )
                }
                Text(
                    text = "Odbuduj spis wtedy, gdy skopiowałeś notatki z komputera albo " +
                        "wyszukiwanie nie znajduje czegoś, co na pewno masz.",
                    style = Kajet.type.meta,
                    color = Kajet.colors.muted,
                    modifier = Modifier.widthIn(max = Kajet.dimens.readingWidth),
                )
            }

            Sekcja(
                tytul = "Rysik i palec",
                opis = "Kiedy rysik dotyka ekranu, dłoń nigdy nie rysuje. To działa zawsze.",
            ) {
                ZachowaniePalca.entries.forEach { wariant ->
                    WyborWiersz(
                        tekst = wariant.nazwaPl,
                        opis = if (wariant == ZachowaniePalca.PRZEWIJA) {
                            "Palcem przesuwasz stronę, rysikiem piszesz. Tak jest najwygodniej."
                        } else {
                            "Palcem też rysujesz. Przydaje się, kiedy nie masz przy sobie rysika."
                        },
                        wybrany = ustawienia.zachowaniePalca == wariant,
                        onKlik = { zakres.launch { magazynUstawien.ustawZachowaniePalca(wariant) } },
                    )
                }
            }

            Sekcja(
                tytul = "Wygląd",
                opis = "Motyw jasny i ciemny są rysowane osobno, a nie odwracane kolorami.",
            ) {
                WyborMotywu.entries.forEach { wariant ->
                    WyborWiersz(
                        tekst = wariant.nazwaPl,
                        opis = null,
                        wybrany = ustawienia.motyw == wariant,
                        onKlik = { zakres.launch { magazynUstawien.ustawMotyw(wariant) } },
                    )
                }
            }

            Sekcja(
                tytul = "Nowa notatka odręczna",
                opis = "Te ustawienia podpowiadają się przy tworzeniu notatki. Zawsze możesz je zmienić.",
            ) {
                EtykietaSekcji("Rodzaj strony")
                PageMode.entries.forEach { wariant ->
                    WyborWiersz(
                        tekst = wariant.nazwaPl,
                        opis = null,
                        wybrany = ustawienia.domyslnyTrybStrony == wariant,
                        onKlik = { zakres.launch { magazynUstawien.ustawDomyslnyTrybStrony(wariant) } },
                    )
                }
                EtykietaSekcji("Tło strony")
                PageBackground.entries.forEach { wariant ->
                    WyborWiersz(
                        tekst = wariant.nazwaPl,
                        opis = null,
                        wybrany = ustawienia.domyslneTlo == wariant,
                        onKlik = { zakres.launch { magazynUstawien.ustawDomyslneTlo(wariant) } },
                    )
                }
            }

            Sekcja(
                tytul = "Uruchamianie kodu",
                opis = "Python działa na tablecie bez internetu. Pozostałe języki liczy serwer, " +
                    "więc bez internetu ich nie uruchomisz.",
            ) {
                PoleAdresu(
                    wartosc = ustawienia.adresSerweraKodu,
                    onZapisz = { zakres.launch { magazynUstawien.ustawAdresSerweraKodu(it) } },
                )
                Text(
                    text = "Domyślnie jest to publiczny serwer Piston. Jeśli masz własny, wpisz jego adres.",
                    style = Kajet.type.meta,
                    color = Kajet.colors.muted,
                    modifier = Modifier.widthIn(max = Kajet.dimens.readingWidth),
                )
            }

            Sekcja(
                tytul = "Zapis automatyczny",
                opis = "Notatka zapisuje się sama. Nie ma przycisku zapisz i nie musisz o tym myśleć.",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 5, 10, 20).forEach { sekundy ->
                        WyborLiczby(
                            liczba = sekundy,
                            wybrana = ustawienia.odstepAutozapisu == sekundy,
                            onKlik = { zakres.launch { magazynUstawien.ustawOdstepAutozapisu(sekundy) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Sekcja(tytul: String, opis: String, zawartosc: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tytul, style = Kajet.type.title, color = Kajet.colors.text)
        Text(
            text = opis,
            style = Kajet.type.body,
            color = Kajet.colors.muted,
            modifier = Modifier.widthIn(max = Kajet.dimens.readingWidth),
        )
        zawartosc()
        LiniaPozioma()
    }
}

@Composable
private fun PoleAdresu(wartosc: String, onZapisz: (String) -> Unit) {
    var tekst by remember(wartosc) { mutableStateOf(wartosc) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EtykietaSekcji("Adres serwera")
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = tekst,
                onValueChange = { tekst = it },
                singleLine = true,
                textStyle = Kajet.type.code.copy(color = Kajet.colors.text),
                cursorBrush = SolidColor(Kajet.colors.accent),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp),
            )
            PrzyciskWtorny("Zapisz adres", { onZapisz(tekst) })
        }
        LiniaPozioma(kolor = Kajet.colors.muted.copy(alpha = 0.5f))
    }
}

@Composable
private fun WyborLiczby(liczba: Int, wybrana: Boolean, onKlik: () -> Unit) {
    Box(
        Modifier
            .size(64.dp, 48.dp)
            .background(
                if (wybrana) Kajet.colors.accentWash else Kajet.colors.desk,
                androidx.compose.foundation.shape.RoundedCornerShape(Kajet.dimens.corner),
            )
            .clickable(onClick = onKlik),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$liczba s",
            style = Kajet.type.label,
            color = if (wybrana) Kajet.colors.accent else Kajet.colors.muted,
        )
    }
}

/** Zamienia długi adres dostawcy plików na coś, co da się przeczytać. */
fun czytelnyAdres(uri: String): String {
    val odkodowany = Uri.decode(uri)
    val ogon = odkodowany.substringAfterLast("/tree/")
    return ogon.replace("primary:", "Pamięć tabletu / ").ifBlank { odkodowany }
}
