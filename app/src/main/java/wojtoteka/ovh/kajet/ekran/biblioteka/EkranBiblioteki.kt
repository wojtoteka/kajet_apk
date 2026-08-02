package wojtoteka.ovh.kajet.ekran.biblioteka

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wojtoteka.ovh.kajet.core.design.FolderColor
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.EtykietaSekcji
import wojtoteka.ovh.kajet.core.design.component.IkonaPrzycisk
import wojtoteka.ovh.kajet.core.design.component.Komunikat
import wojtoteka.ovh.kajet.core.design.component.LiniaPozioma
import wojtoteka.ovh.kajet.core.design.component.PasekMarginesu
import wojtoteka.ovh.kajet.core.design.component.PrzyciskGlowny
import wojtoteka.ovh.kajet.core.design.component.PrzyciskWtorny
import wojtoteka.ovh.kajet.core.design.component.PustoTutaj
import wojtoteka.ovh.kajet.core.design.component.ZnakKajetu
import wojtoteka.ovh.kajet.core.design.component.liniaMarginesu
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.ItemType
import wojtoteka.ovh.kajet.core.model.LibraryItem
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode
import wojtoteka.ovh.kajet.storage.RepozytoriumBiblioteki
import wojtoteka.ovh.kajet.storage.WpisKosza

@Composable
fun EkranBiblioteki(
    model: ModelBiblioteki,
    repo: RepozytoriumBiblioteki,
    domyslnyTryb: PageMode,
    domyslneTlo: PageBackground,
    onOtworzWpis: (LibraryItem) -> Unit,
    onUstawienia: () -> Unit,
) {
    val sekcja by model.sekcja.collectAsStateWithLifecycle()
    val sciezka by model.sciezka.collectAsStateWithLifecycle()
    val zawartosc by model.zawartosc.collectAsStateWithLifecycle()
    val drzewo by model.drzewo.collectAsStateWithLifecycle()
    val blad by model.blad.collectAsStateWithLifecycle()
    val przebudowa by model.przebudowa.collectAsStateWithLifecycle()

    var oknoFolderu by remember { mutableStateOf(false) }
    var oknoNotatki by remember { mutableStateOf(false) }
    var oknoPliku by remember { mutableStateOf(false) }
    var menuWpisu by remember { mutableStateOf<LibraryItem?>(null) }
    var zmianaNazwy by remember { mutableStateOf<LibraryItem?>(null) }
    var przeniesienie by remember { mutableStateOf<LibraryItem?>(null) }
    var wygladFolderu by remember { mutableStateOf<LibraryItem?>(null) }

    val szerokoscEkranu = LocalConfiguration.current.screenWidthDp
    val jestMiejsceNaDrzewo = szerokoscEkranu >= 720

    Row(Modifier.fillMaxSize().background(Kajet.colors.desk)) {
        PasekSekcji(
            wybrana = sekcja,
            onWybor = model::ustawSekcje,
            onUstawienia = onUstawienia,
        )

        if (sekcja == Sekcja.BIBLIOTEKA && jestMiejsceNaDrzewo) {
            KolumnaDrzewa(
                drzewo = drzewo,
                biezaca = sciezka,
                onWybor = model::przejdzDo,
                onPrzelacz = model::przelaczRozwiniecie,
            )
        }

        Column(
            Modifier
                .fillMaxHeight()
                .weight(1f)
                .background(Kajet.colors.sheet),
        ) {
            if (przebudowa != null) {
                Komunikat(
                    ikona = KajetIcons.Przywroc,
                    tekst = "Odbudowuję spis notatek. $przebudowa",
                    kolor = Kajet.colors.accent,
                )
            }
            if (blad != null) {
                Komunikat(
                    ikona = KajetIcons.Blad,
                    tekst = blad.orEmpty(),
                    kolor = Kajet.colors.danger,
                ) {
                    PrzyciskWtorny("Rozumiem", model::schowajBlad)
                }
            }

            when (sekcja) {
                Sekcja.BIBLIOTEKA -> WidokFolderu(
                    sciezka = sciezka,
                    wpisy = zawartosc,
                    repo = repo,
                    pokazSciezke = !jestMiejsceNaDrzewo,
                    onWyzej = model::wyzej,
                    onOtworz = { wpis ->
                        if (wpis.type == ItemType.FOLDER) {
                            model.przejdzDo(wpis.path)
                        } else {
                            model.zapamietajOtwarcie(wpis)
                            onOtworzWpis(wpis)
                        }
                    },
                    onMenu = { menuWpisu = it },
                    onNowyFolder = { oknoFolderu = true },
                    onNowaNotatka = { oknoNotatki = true },
                    onNowyPlik = { oknoPliku = true },
                )

                Sekcja.ULUBIONE -> ListaProsta(
                    naglowek = "Ulubione",
                    podtytul = "Notatki oznaczone gwiazdką w edytorze.",
                    zrodlo = model.ulubione,
                    repo = repo,
                    pustyOpis = "Nie masz jeszcze ulubionych notatek. Otwórz notatkę i naciśnij gwiazdkę na pasku u góry.",
                    onOtworz = { model.zapamietajOtwarcie(it); onOtworzWpis(it) },
                    onMenu = { menuWpisu = it },
                )

                Sekcja.OSTATNIE -> ListaProsta(
                    naglowek = "Ostatnio otwarte",
                    podtytul = "Dwadzieścia notatek, przy których byłeś ostatnio.",
                    zrodlo = model.ostatnie,
                    repo = repo,
                    pustyOpis = "Tu pojawią się notatki, które otworzysz.",
                    onOtworz = { model.zapamietajOtwarcie(it); onOtworzWpis(it) },
                    onMenu = { menuWpisu = it },
                )

                Sekcja.SZUKAJ -> WidokSzukania(
                    model = model,
                    repo = repo,
                    onOtworz = { model.zapamietajOtwarcie(it); onOtworzWpis(it) },
                )

                Sekcja.KOSZ -> WidokKosza(model = model)
            }
        }
    }

    if (oknoFolderu) {
        OknoNowegoFolderu(
            onZamknij = { oknoFolderu = false },
            onUtworz = { nazwa, kolor, ikona ->
                model.nowyFolder(nazwa, kolor, ikona)
                oknoFolderu = false
            },
        )
    }

    if (oknoNotatki) {
        OknoNowejNotatki(
            onZamknij = { oknoNotatki = false },
            domyslnyTryb = domyslnyTryb,
            domyslneTlo = domyslneTlo,
            onUtworz = { tytul, rodzaj, tryb, tlo ->
                oknoNotatki = false
                model.nowaNotatka(tytul, rodzaj, tryb, tlo) { wpis -> onOtworzWpis(wpis) }
            },
        )
    }

    if (oknoPliku) {
        OknoNowegoPliku(
            onZamknij = { oknoPliku = false },
            onUtworz = { nazwa, jezyk ->
                oknoPliku = false
                model.nowyPlikKodu(nazwa, jezyk) { wpis -> onOtworzWpis(wpis) }
            },
        )
    }

    menuWpisu?.let { wpis ->
        MenuWpisu(
            wpis = wpis,
            onZamknij = { menuWpisu = null },
            onZmienNazwe = { menuWpisu = null; zmianaNazwy = wpis },
            onPrzenies = { menuWpisu = null; przeniesienie = wpis },
            onKopiuj = { menuWpisu = null; model.kopiuj(wpis) },
            onWyglad = { menuWpisu = null; wygladFolderu = wpis },
            onDoKosza = { menuWpisu = null; model.doKosza(wpis) },
        )
    }

    zmianaNazwy?.let { wpis ->
        OknoZmianyNazwy(
            biezaca = wpis.name,
            onZamknij = { zmianaNazwy = null },
            onZapisz = { nowa ->
                model.zmienNazwe(wpis, nowa)
                zmianaNazwy = null
            },
        )
    }

    przeniesienie?.let { wpis ->
        OknoPrzeniesienia(
            wpis = wpis,
            drzewo = drzewo,
            onZamknij = { przeniesienie = null },
            onPrzenies = { docelowy ->
                model.przenies(wpis, docelowy)
                przeniesienie = null
            },
        )
    }

    wygladFolderu?.let { wpis ->
        OknoWygladuFolderu(
            wpis = wpis,
            onZamknij = { wygladFolderu = null },
            onZapisz = { kolor, ikona ->
                model.zmienWygladFolderu(wpis, kolor, ikona)
                wygladFolderu = null
            },
        )
    }
}

/** Pionowy pasek marginesu. Przełącza widoki i nosi znak aplikacji. */
@Composable
private fun PasekSekcji(
    wybrana: Sekcja,
    onWybor: (Sekcja) -> Unit,
    onUstawienia: () -> Unit,
) {
    PasekMarginesu {
        Box(
            Modifier
                .height(64.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            ZnakKajetu(
                modifier = Modifier.size(26.dp),
                kolor = Kajet.colors.accent,
            )
        }

        IkonaPrzycisk(
            ikona = KajetIcons.Biblioteka,
            opis = "Biblioteka",
            onClick = { onWybor(Sekcja.BIBLIOTEKA) },
            wybrany = wybrana == Sekcja.BIBLIOTEKA,
        )
        IkonaPrzycisk(
            ikona = KajetIcons.Szukaj,
            opis = "Szukaj w notatkach",
            onClick = { onWybor(Sekcja.SZUKAJ) },
            wybrany = wybrana == Sekcja.SZUKAJ,
        )
        IkonaPrzycisk(
            ikona = KajetIcons.Ulubione,
            opis = "Ulubione",
            onClick = { onWybor(Sekcja.ULUBIONE) },
            wybrany = wybrana == Sekcja.ULUBIONE,
        )
        IkonaPrzycisk(
            ikona = KajetIcons.Ostatnie,
            opis = "Ostatnio otwarte",
            onClick = { onWybor(Sekcja.OSTATNIE) },
            wybrany = wybrana == Sekcja.OSTATNIE,
        )

        Spacer(Modifier.weight(1f))

        IkonaPrzycisk(
            ikona = KajetIcons.Kosz,
            opis = "Kosz",
            onClick = { onWybor(Sekcja.KOSZ) },
            wybrany = wybrana == Sekcja.KOSZ,
        )
        IkonaPrzycisk(
            ikona = KajetIcons.Ustawienia,
            opis = "Ustawienia",
            onClick = onUstawienia,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun KolumnaDrzewa(
    drzewo: List<WezelDrzewa>,
    biezaca: String,
    onWybor: (String) -> Unit,
    onPrzelacz: (String) -> Unit,
) {
    Column(
        Modifier
            .width(272.dp)
            .fillMaxHeight()
            .background(Kajet.colors.desk)
            .liniaMarginesu(Kajet.colors.line),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            EtykietaSekcji("Foldery")
        }

        WierszFolderu(
            nazwa = "Wszystkie notatki",
            poziom = 0,
            kolor = Kajet.colors.muted,
            ikonaId = "ksiazki",
            wybrany = biezaca.isEmpty(),
            maStrzalke = false,
            rozwiniety = true,
            onKlik = { onWybor("") },
            onStrzalka = {},
        )

        LazyColumn(Modifier.weight(1f)) {
            items(drzewo, key = { it.wpis.path }) { wezel ->
                WierszFolderu(
                    nazwa = wezel.wpis.name,
                    poziom = wezel.poziom + 1,
                    kolor = FolderColor.fromId(wezel.wpis.colorId).color(Kajet.colors.isDark),
                    ikonaId = wezel.wpis.iconId,
                    wybrany = wezel.wpis.path == biezaca,
                    maStrzalke = wezel.wpis.childCount > 0,
                    rozwiniety = wezel.rozwiniety,
                    onKlik = { onWybor(wezel.wpis.path) },
                    onStrzalka = { onPrzelacz(wezel.wpis.path) },
                )
            }
        }
    }
}

@Composable
private fun WierszFolderu(
    nazwa: String,
    poziom: Int,
    kolor: androidx.compose.ui.graphics.Color,
    ikonaId: String?,
    wybrany: Boolean,
    maStrzalke: Boolean,
    rozwiniety: Boolean,
    onKlik: () -> Unit,
    onStrzalka: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(if (wybrany) Kajet.colors.accentWash else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onKlik)
            .padding(start = (6 + poziom * 14).dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clickable(enabled = maStrzalke, onClick = onStrzalka),
            contentAlignment = Alignment.Center,
        ) {
            if (maStrzalke) {
                Icon(
                    imageVector = if (rozwiniety) KajetIcons.StrzalkaWDol else KajetIcons.StrzalkaWPrawo,
                    contentDescription = if (rozwiniety) "Zwiń $nazwa" else "Rozwiń $nazwa",
                    tint = Kajet.colors.muted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Icon(
            imageVector = KajetIcons.folderIcon(ikonaId),
            contentDescription = null,
            tint = kolor,
            modifier = Modifier
                .padding(end = 8.dp)
                .size(18.dp),
        )
        Text(
            text = nazwa,
            style = Kajet.type.body,
            color = if (wybrany) Kajet.colors.text else Kajet.colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WidokFolderu(
    sciezka: String,
    wpisy: List<LibraryItem>,
    repo: RepozytoriumBiblioteki,
    pokazSciezke: Boolean,
    onWyzej: () -> Unit,
    onOtworz: (LibraryItem) -> Unit,
    onMenu: (LibraryItem) -> Unit,
    onNowyFolder: () -> Unit,
    onNowaNotatka: () -> Unit,
    onNowyPlik: () -> Unit,
) {
    val nazwaMiejsca = if (sciezka.isEmpty()) "Wszystkie notatki" else sciezka.substringAfterLast('/')

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (sciezka.isNotEmpty() && pokazSciezke) {
                IkonaPrzycisk(KajetIcons.Wstecz, "Folder wyżej", onWyzej)
            }
            Column(Modifier.weight(1f)) {
                Text(nazwaMiejsca, style = Kajet.type.display, color = Kajet.colors.text)
                if (sciezka.isNotEmpty()) {
                    Text(
                        text = sciezka.substringBeforeLast('/', "Wszystkie notatki"),
                        style = Kajet.type.meta,
                        color = Kajet.colors.muted,
                    )
                }
            }
            PrzyciskGlowny("Nowa notatka", onNowaNotatka, ikona = KajetIcons.Dodaj)
            PrzyciskWtorny("Folder", onNowyFolder, ikona = KajetIcons.Folder)
            PrzyciskWtorny("Plik z kodem", onNowyPlik, ikona = KajetIcons.PlikKodu)
        }
        LiniaPozioma()

        if (wpisy.isEmpty()) {
            PustoTutaj(
                naglowek = "Ten folder jest pusty",
                opis = "Utwórz notatkę albo folder na przedmiot. Wszystko zapisze się w katalogu, który wskazałeś na tablecie.",
                dzialanie = { PrzyciskGlowny("Nowa notatka", onNowaNotatka, ikona = KajetIcons.Dodaj) },
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(wpisy, key = { it.documentUri }) { wpis ->
                    WierszWpisu(
                        wpis = wpis,
                        repo = repo,
                        onOtworz = { onOtworz(wpis) },
                        onMenu = { onMenu(wpis) },
                        modifier = Modifier.padding(start = 12.dp),
                    )
                    LiniaPozioma(odstepFromStart = 60.dp)
                }
            }
        }
    }
}

@Composable
private fun ListaProsta(
    naglowek: String,
    podtytul: String,
    zrodlo: kotlinx.coroutines.flow.StateFlow<List<LibraryItem>>,
    repo: RepozytoriumBiblioteki,
    pustyOpis: String,
    onOtworz: (LibraryItem) -> Unit,
    onMenu: (LibraryItem) -> Unit,
) {
    val wpisy by zrodlo.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)) {
            Text(naglowek, style = Kajet.type.display, color = Kajet.colors.text)
            Text(podtytul, style = Kajet.type.meta, color = Kajet.colors.muted)
        }
        LiniaPozioma()

        if (wpisy.isEmpty()) {
            PustoTutaj(naglowek = "Pusto", opis = pustyOpis)
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(wpisy, key = { it.documentUri }) { wpis ->
                    WierszWpisu(
                        wpis = wpis,
                        repo = repo,
                        onOtworz = { onOtworz(wpis) },
                        onMenu = { onMenu(wpis) },
                        modifier = Modifier.padding(start = 12.dp),
                    )
                    LiniaPozioma(odstepFromStart = 60.dp)
                }
            }
        }
    }
}

@Composable
private fun WidokSzukania(
    model: ModelBiblioteki,
    repo: RepozytoriumBiblioteki,
    onOtworz: (LibraryItem) -> Unit,
) {
    val zapytanie by model.zapytanie.collectAsStateWithLifecycle()
    val wyniki by model.wynikiSzukania.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
            Text("Szukaj", style = Kajet.type.display, color = Kajet.colors.text)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = KajetIcons.Szukaj,
                    contentDescription = null,
                    tint = Kajet.colors.muted,
                    modifier = Modifier.size(20.dp),
                )
                BasicTextField(
                    value = zapytanie,
                    onValueChange = model::ustawZapytanie,
                    singleLine = true,
                    textStyle = Kajet.type.bodyLarge.copy(color = Kajet.colors.text),
                    cursorBrush = SolidColor(Kajet.colors.accent),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp),
                )
            }
            LiniaPozioma(kolor = Kajet.colors.muted.copy(alpha = 0.5f))
            Text(
                text = "Szukam w tytułach i w treści. Pismo odręczne znajdę wtedy, kiedy zamienisz je na tekst.",
                style = Kajet.type.meta,
                color = Kajet.colors.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        when {
            zapytanie.length < 2 -> PustoTutaj(
                naglowek = "Wpisz, czego szukasz",
                opis = "Wystarczą dwie litery. Szukanie działa bez internetu, bo spis notatek leży na tablecie.",
            )

            wyniki.isEmpty() -> PustoTutaj(
                naglowek = "Nic nie znalazłem",
                opis = "Sprawdź pisownię albo odbuduj spis notatek w ustawieniach, jeśli kopiowałeś pliki spoza aplikacji.",
            )

            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(wyniki, key = { it.documentUri }) { wpis ->
                    WierszWpisu(
                        wpis = wpis,
                        repo = repo,
                        onOtworz = { onOtworz(wpis) },
                        onMenu = {},
                        modifier = Modifier.padding(start = 12.dp),
                    )
                    LiniaPozioma(odstepFromStart = 60.dp)
                }
            }
        }
    }
}

@Composable
private fun WidokKosza(model: ModelBiblioteki) {
    val kosz by model.kosz.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Kosz", style = Kajet.type.display, color = Kajet.colors.text)
                Text(
                    text = "Wyrzucone notatki leżą w katalogu .trash obok biblioteki. Nic nie ginie, dopóki nie opróżnisz kosza.",
                    style = Kajet.type.meta,
                    color = Kajet.colors.muted,
                    modifier = Modifier.widthIn(max = 560.dp),
                )
            }
            if (kosz.isNotEmpty()) {
                PrzyciskWtorny(
                    tekst = "Opróżnij kosz",
                    onClick = model::oproznijKosz,
                    ikona = KajetIcons.Kosz,
                    kolor = Kajet.colors.danger,
                )
            }
        }
        LiniaPozioma()

        if (kosz.isEmpty()) {
            PustoTutaj(
                naglowek = "Kosz jest pusty",
                opis = "Wyrzucone notatki znajdziesz tutaj i będziesz mógł je przywrócić dokładnie tam, skąd zniknęły.",
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(kosz, key = { it.id }) { wpis ->
                    WierszKosza(
                        wpis = wpis,
                        onPrzywroc = { model.przywroc(wpis) },
                        onUsun = { model.usunTrwale(wpis) },
                    )
                    LiniaPozioma(odstepFromStart = 20.dp)
                }
            }
        }
    }
}

@Composable
private fun WierszKosza(wpis: WpisKosza, onPrzywroc: () -> Unit, onUsun: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 20.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(wpis.displayName, style = Kajet.type.body, color = Kajet.colors.text)
            Text(
                text = "Wyrzucone ${kiedy(wpis.deletedAt).lowercase()}, było w: " +
                    wpis.originalParent.ifEmpty { "Wszystkie notatki" },
                style = Kajet.type.meta,
                color = Kajet.colors.muted,
            )
        }
        PrzyciskWtorny("Przywróć", onPrzywroc, ikona = KajetIcons.Przywroc)
        IkonaPrzycisk(KajetIcons.Kosz, "Usuń ${wpis.displayName} na dobre", onUsun)
    }
}

@Composable
private fun MenuWpisu(
    wpis: LibraryItem,
    onZamknij: () -> Unit,
    onZmienNazwe: () -> Unit,
    onPrzenies: () -> Unit,
    onKopiuj: () -> Unit,
    onWyglad: () -> Unit,
    onDoKosza: () -> Unit,
) {
    OknoKajetu(wpis.name, onZamknij, szerokosc = 420) {
        Column {
            DzialanieMenu(KajetIcons.Pioro, "Zmień nazwę", onZmienNazwe)
            DzialanieMenu(KajetIcons.Przenies, "Przenieś do innego folderu", onPrzenies)
            DzialanieMenu(KajetIcons.Kopiuj, "Zrób kopię", onKopiuj)
            if (wpis.type == ItemType.FOLDER) {
                DzialanieMenu(KajetIcons.Kolor, "Zmień kolor i ikonę", onWyglad)
            }
            DzialanieMenu(KajetIcons.Kosz, "Wyrzuć do kosza", onDoKosza, Kajet.colors.danger)
        }
        PrzyciskWtorny("Zamknij", onZamknij)
    }
}

@Composable
private fun DzialanieMenu(
    ikona: androidx.compose.ui.graphics.vector.ImageVector,
    tekst: String,
    onKlik: () -> Unit,
    kolor: androidx.compose.ui.graphics.Color = Kajet.colors.text,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onKlik),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(ikona, contentDescription = null, tint = kolor, modifier = Modifier.size(20.dp))
        Text(tekst, style = Kajet.type.body, color = kolor)
    }
}

@Composable
private fun OknoPrzeniesienia(
    wpis: LibraryItem,
    drzewo: List<WezelDrzewa>,
    onZamknij: () -> Unit,
    onPrzenies: (String) -> Unit,
) {
    OknoKajetu("Przenieś: ${wpis.name}", onZamknij, szerokosc = 460) {
        Text(
            text = "Wybierz folder, do którego ma trafić ten wpis.",
            style = Kajet.type.body,
            color = Kajet.colors.muted,
        )
        Column {
            WierszCelu("Wszystkie notatki", 0) { onPrzenies("") }
            drzewo.filter { it.wpis.path != wpis.path && !it.wpis.path.startsWith(wpis.path + "/") }
                .forEach { wezel ->
                    WierszCelu(wezel.wpis.name, wezel.poziom + 1) { onPrzenies(wezel.wpis.path) }
                }
        }
        PrzyciskWtorny("Anuluj", onZamknij)
    }
}

@Composable
private fun WierszCelu(nazwa: String, poziom: Int, onKlik: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onKlik)
            .padding(start = (poziom * 16).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            KajetIcons.Folder,
            contentDescription = null,
            tint = Kajet.colors.muted,
            modifier = Modifier.size(18.dp),
        )
        Text(nazwa, style = Kajet.type.body, color = Kajet.colors.text)
    }
}

@Composable
private fun OknoWygladuFolderu(
    wpis: LibraryItem,
    onZamknij: () -> Unit,
    onZapisz: (String, String) -> Unit,
) {
    var kolor by remember { mutableStateOf(FolderColor.fromId(wpis.colorId)) }
    var ikona by remember {
        mutableStateOf(wojtoteka.ovh.kajet.core.model.FolderIcon.fromId(wpis.iconId))
    }

    OknoKajetu("Wygląd folderu: ${wpis.name}", onZamknij) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EtykietaSekcji("Kolor")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FolderColor.entries.forEach { wariant ->
                    Box(
                        Modifier
                            .size(36.dp)
                            .clickable { kolor = wariant },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(if (wariant == kolor) 26.dp else 20.dp)
                                .background(
                                    wariant.color(Kajet.colors.isDark),
                                    androidx.compose.foundation.shape.CircleShape,
                                ),
                        )
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EtykietaSekcji("Ikona")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                wojtoteka.ovh.kajet.core.model.FolderIcon.entries.forEach { wariant ->
                    Box(
                        Modifier
                            .size(44.dp)
                            .background(
                                if (wariant == ikona) Kajet.colors.accentWash else Kajet.colors.desk,
                                androidx.compose.foundation.shape.RoundedCornerShape(Kajet.dimens.corner),
                            )
                            .clickable { ikona = wariant },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            KajetIcons.folderIcon(wariant.id),
                            contentDescription = wariant.labelPl,
                            tint = kolor.color(Kajet.colors.isDark),
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrzyciskGlowny("Zapisz", { onZapisz(kolor.id, ikona.id) })
            PrzyciskWtorny("Anuluj", onZamknij)
        }
    }
}
