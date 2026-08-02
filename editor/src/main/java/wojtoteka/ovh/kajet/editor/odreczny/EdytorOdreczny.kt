package wojtoteka.ovh.kajet.editor.odreczny

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wojtoteka.ovh.kajet.core.design.InkPalette
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.EtykietaSekcji
import wojtoteka.ovh.kajet.core.design.component.IkonaPrzycisk
import wojtoteka.ovh.kajet.core.design.component.LiniaPozioma
import wojtoteka.ovh.kajet.core.design.component.PrzyciskWtorny
import wojtoteka.ovh.kajet.core.design.component.liniaMarginesu
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.editor.StanZapisu
import wojtoteka.ovh.kajet.ink.Narzedzie
import wojtoteka.ovh.kajet.ink.Pedzle
import wojtoteka.ovh.kajet.ink.PlotnoKresek
import wojtoteka.ovh.kajet.ink.SluchaczPlotna
import wojtoteka.ovh.kajet.ink.StronaNaEkranie

/**
 * Edytor notatki odręcznej.
 *
 * Kartka zajmuje cały ekran. Nie ma paska u góry, bo zabierałby miejsce
 * na pisanie. Wszystko, co potrzebne, leży w wąskim pasku przy lewej krawędzi,
 * na wysokości kciuka trzymającego tablet.
 */
@Composable
fun EdytorOdreczny(
    model: ModelOdrecznego,
    palecRysuje: Boolean,
    onWstecz: () -> Unit,
    onEksport: () -> Unit,
    onRozpoznajPismo: (strona: Int, kreski: List<InkStroke>) -> Unit,
) {
    val dokument by model.dokument.collectAsStateWithLifecycle()
    val narzedzie by model.narzedzie.collectAsStateWithLifecycle()
    val pisak by model.pisak.collectAsStateWithLifecycle()
    val zaznaczone by model.zaznaczone.collectAsStateWithLifecycle()
    val stronaZaznaczenia by model.stronaZaznaczenia.collectAsStateWithLifecycle()
    val stanZapisu by model.stanZapisu.collectAsStateWithLifecycle()
    val mozeCofnac by model.mozeCofnac.collectAsStateWithLifecycle()
    val mozePonowic by model.mozePonowic.collectAsStateWithLifecycle()
    val blad by model.blad.collectAsStateWithLifecycle()
    val edytowanePole by model.edytowanePole.collectAsStateWithLifecycle()

    val kolory = Kajet.colors
    var panelKoloru by remember { mutableStateOf(false) }
    var panelTla by remember { mutableStateOf(false) }

    var przesuniecieX by remember { mutableFloatStateOf(0f) }
    var przesuniecieY by remember { mutableFloatStateOf(0f) }
    var powiekszenie by remember { mutableFloatStateOf(1f) }

    val plotno = remember { mutableStateOf<PlotnoKresek?>(null) }

    // Zapis przy wyjściu z aplikacji, zanim system uśpi tablet.
    val wlasciciel = LocalLifecycleOwner.current
    DisposableEffect(wlasciciel) {
        val obserwator = LifecycleEventObserver { _, zdarzenie ->
            if (zdarzenie == Lifecycle.Event.ON_STOP) model.zapiszTeraz()
        }
        wlasciciel.lifecycle.addObserver(obserwator)
        onDispose { wlasciciel.lifecycle.removeObserver(obserwator) }
    }

    Row(Modifier.fillMaxSize().background(kolory.desk)) {

        PasekRysowania(
            narzedzie = narzedzie,
            mozeCofnac = mozeCofnac,
            mozePonowic = mozePonowic,
            ulubiona = dokument?.favorite == true,
            kolorPisaka = Color(if (narzedzie == Narzedzie.ZAKRESLACZ) pisak.kolorZakreslacza else pisak.kolorPiora),
            onNarzedzie = model::wybierzNarzedzie,
            onCofnij = model::cofnij,
            onPonow = model::ponow,
            onKolor = { panelKoloru = !panelKoloru; panelTla = false },
            onTlo = { panelTla = !panelTla; panelKoloru = false },
            onDodajStrone = model::dolozStrone,
            onPoleTekstowe = {
                val strona = 0
                model.dodajPole(
                    strona = strona,
                    x = przesuniecieX + 80f,
                    y = przesuniecieY + 80f,
                    kolor = kolory.text.toArgb(),
                )
            },
            onUlubione = model::przelaczUlubione,
            onEksport = onEksport,
            onWstecz = {
                model.zapiszTeraz()
                onWstecz()
            },
        )

        Box(Modifier.fillMaxSize()) {
            val pismo = dokument?.handwriting
            if (pismo != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { kontekst ->
                        PlotnoKresek(kontekst).also { widok ->
                            plotno.value = widok
                            widok.sluchacz = object : SluchaczPlotna {
                                override fun kreskaSkonczona(strona: Int, kreska: InkStroke) {
                                    model.dopiszKreske(strona, kreska)
                                }

                                override fun gumkaPrzeszla(
                                    strona: Int,
                                    x: Float,
                                    y: Float,
                                    promien: Float,
                                    calaKreska: Boolean,
                                ) {
                                    model.gumka(strona, x, y, promien, calaKreska)
                                }

                                override fun lassoSkonczone(strona: Int, wielokat: List<Float>) {
                                    model.zaznaczLassem(strona, wielokat)
                                }

                                override fun zaznaczeniePrzesuniete(dx: Float, dy: Float, koniec: Boolean) {
                                    model.przesunZaznaczenie(dx, dy, koniec)
                                }

                                override fun widokZmieniony(x: Float, y: Float, skala: Float) {
                                    przesuniecieX = x
                                    przesuniecieY = y
                                    powiekszenie = skala
                                }

                                override fun dotknietoPustego() {
                                    model.edytujPole(null)
                                }
                            }
                        }
                    },
                    update = { widok ->
                        widok.strony = pismo.pages.mapIndexed { indeks, kartka ->
                            StronaNaEkranie(
                                indeks = indeks,
                                szerokosc = kartka.width,
                                wysokosc = kartka.height,
                                tlo = kartka.background ?: pismo.background,
                                kreski = kartka.strokes,
                            )
                        }
                        widok.narzedzie = narzedzie
                        widok.ustawienia = pisak
                        widok.palecRysuje = palecRysuje
                        widok.zaznaczone = zaznaczone
                        widok.stronaZaznaczenia = stronaZaznaczenia
                        widok.kolorPapieru = kolory.sheet.toArgb()
                        widok.kolorLinii = kolory.pageRule.toArgb()
                        widok.kolorBiurka = kolory.desk.toArgb()
                        widok.kolorZaznaczenia = kolory.accent.toArgb()
                    },
                )

                PolaTekstoweNaStronie(
                    strony = pismo.pages,
                    przesuniecieX = przesuniecieX,
                    przesuniecieY = przesuniecieY,
                    powiekszenie = powiekszenie,
                    edytowane = edytowanePole,
                    onEdytuj = model::edytujPole,
                    onZmiana = { strona, pole, doHistorii -> model.zmienPole(strona, pole, doHistorii) },
                    onUsun = { strona, id -> model.usunPole(strona, id) },
                )
            }

            PasekTytulu(
                tytul = dokument?.title.orEmpty(),
                stan = stanZapisu,
                stron = pismo?.pages?.size ?: 0,
                modifier = Modifier.align(Alignment.TopStart),
            )

            if (blad != null) {
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .background(kolory.sheet, RoundedCornerShape(Kajet.dimens.corner))
                        .border(1.dp, kolory.danger, RoundedCornerShape(Kajet.dimens.corner))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(KajetIcons.Blad, null, tint = kolory.danger, modifier = Modifier.size(18.dp))
                    Text(blad.orEmpty(), style = Kajet.type.body, color = kolory.text)
                    PrzyciskWtorny("Rozumiem", model::schowajBlad)
                }
            }

            if (zaznaczone.isNotEmpty()) {
                PanelZaznaczenia(
                    ile = zaznaczone.size,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    onSkasuj = model::skasujZaznaczenie,
                    onRozpoznaj = { onRozpoznajPismo(stronaZaznaczenia, zaznaczone) },
                    onOdznacz = model::odznacz,
                )
            }

            if (panelKoloru) {
                PanelPisaka(
                    narzedzie = narzedzie,
                    kolorPiora = pisak.kolorPiora,
                    gruboscPiora = pisak.gruboscPiora,
                    kolorZakreslacza = pisak.kolorZakreslacza,
                    gruboscZakreslacza = pisak.gruboscZakreslacza,
                    promienGumki = pisak.promienGumki,
                    onKolorPiora = model::ustawKolorPiora,
                    onGruboscPiora = model::ustawGruboscPiora,
                    onKolorZakreslacza = model::ustawKolorZakreslacza,
                    onGruboscZakreslacza = model::ustawGruboscZakreslacza,
                    onPromienGumki = model::ustawPromienGumki,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp),
                )
            }

            if (panelTla) {
                PanelTla(
                    biezace = pismo?.background ?: PageBackground.LINIE,
                    onWybor = { model.zmienTlo(it); panelTla = false },
                    onDopasuj = { plotno.value?.dopasujSzerokosc() },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PasekRysowania(
    narzedzie: Narzedzie,
    mozeCofnac: Boolean,
    mozePonowic: Boolean,
    ulubiona: Boolean,
    kolorPisaka: Color,
    onNarzedzie: (Narzedzie) -> Unit,
    onCofnij: () -> Unit,
    onPonow: () -> Unit,
    onKolor: () -> Unit,
    onTlo: () -> Unit,
    onDodajStrone: () -> Unit,
    onPoleTekstowe: () -> Unit,
    onUlubione: () -> Unit,
    onEksport: () -> Unit,
    onWstecz: () -> Unit,
) {
    Column(
        Modifier
            .width(Kajet.dimens.railWidth)
            .fillMaxSize()
            .background(Kajet.colors.desk)
            .liniaMarginesu(Kajet.colors.line)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IkonaPrzycisk(KajetIcons.Wstecz, "Wróć do biblioteki", onWstecz)
        LiniaPozioma(Modifier.padding(horizontal = 12.dp))

        IkonaPrzycisk(KajetIcons.Pioro, Narzedzie.PIORO.nazwaPl, { onNarzedzie(Narzedzie.PIORO) }, wybrany = narzedzie == Narzedzie.PIORO)
        IkonaPrzycisk(KajetIcons.Zakreslacz, Narzedzie.ZAKRESLACZ.nazwaPl, { onNarzedzie(Narzedzie.ZAKRESLACZ) }, wybrany = narzedzie == Narzedzie.ZAKRESLACZ)
        IkonaPrzycisk(KajetIcons.Gumka, Narzedzie.GUMKA_FRAGMENT.nazwaPl, { onNarzedzie(Narzedzie.GUMKA_FRAGMENT) }, wybrany = narzedzie == Narzedzie.GUMKA_FRAGMENT)
        IkonaPrzycisk(KajetIcons.GumkaKreska, Narzedzie.GUMKA_KRESKA.nazwaPl, { onNarzedzie(Narzedzie.GUMKA_KRESKA) }, wybrany = narzedzie == Narzedzie.GUMKA_KRESKA)
        IkonaPrzycisk(KajetIcons.Lasso, Narzedzie.LASSO.nazwaPl, { onNarzedzie(Narzedzie.LASSO) }, wybrany = narzedzie == Narzedzie.LASSO)
        IkonaPrzycisk(KajetIcons.Linijka, Narzedzie.LINIJKA.nazwaPl, { onNarzedzie(Narzedzie.LINIJKA) }, wybrany = narzedzie == Narzedzie.LINIJKA)

        LiniaPozioma(Modifier.padding(horizontal = 12.dp))

        Box(Modifier.size(48.dp).clickable(onClick = onKolor), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(22.dp)
                    .background(kolorPisaka, CircleShape)
                    .border(1.dp, Kajet.colors.line, CircleShape),
            )
        }
        IkonaPrzycisk(KajetIcons.PoleTekstowe, "Wstaw pole tekstowe", onPoleTekstowe)
        IkonaPrzycisk(KajetIcons.TloStrony, "Tło strony", onTlo)
        IkonaPrzycisk(KajetIcons.DodajStrone, "Dodaj stronę", onDodajStrone)

        LiniaPozioma(Modifier.padding(horizontal = 12.dp))

        IkonaPrzycisk(KajetIcons.Cofnij, "Cofnij", onCofnij, wlaczony = mozeCofnac)
        IkonaPrzycisk(KajetIcons.Ponow, "Ponów", onPonow, wlaczony = mozePonowic)

        LiniaPozioma(Modifier.padding(horizontal = 12.dp))

        IkonaPrzycisk(KajetIcons.Ulubione, if (ulubiona) "Usuń z ulubionych" else "Dodaj do ulubionych", onUlubione, wybrany = ulubiona)
        IkonaPrzycisk(KajetIcons.Eksport, "Eksportuj notatkę", onEksport)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PasekTytulu(tytul: String, stan: StanZapisu, stron: Int, modifier: Modifier = Modifier) {
    val opisStanu = when (stan) {
        StanZapisu.WCZYTYWANIE -> "Wczytuję"
        StanZapisu.ZAPISANE -> "Zapisane"
        StanZapisu.ZMIENIONE -> "Zmiany czekają na zapis"
        StanZapisu.ZAPISYWANIE -> "Zapisuję"
        StanZapisu.BLAD -> "Zapis się nie udał"
    }
    Row(
        modifier
            .padding(12.dp)
            .background(Kajet.colors.sheet.copy(alpha = 0.94f), RoundedCornerShape(Kajet.dimens.corner))
            .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(tytul, style = Kajet.type.titleSmall, color = Kajet.colors.text)
        Box(
            Modifier
                .size(6.dp)
                .background(
                    if (stan == StanZapisu.BLAD) Kajet.colors.danger else Kajet.colors.muted,
                    CircleShape,
                ),
        )
        Text(opisStanu, style = Kajet.type.meta, color = Kajet.colors.muted)
        if (stron > 1) {
            Text("$stron stron", style = Kajet.type.meta, color = Kajet.colors.muted)
        }
    }
}

@Composable
private fun PanelZaznaczenia(
    ile: Int,
    modifier: Modifier,
    onSkasuj: () -> Unit,
    onRozpoznaj: () -> Unit,
    onOdznacz: () -> Unit,
) {
    Row(
        modifier
            .background(Kajet.colors.sheet, RoundedCornerShape(Kajet.dimens.corner))
            .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Zaznaczono $ile kresek", style = Kajet.type.label, color = Kajet.colors.text)
        PrzyciskWtorny("Zamień na tekst", onRozpoznaj, ikona = KajetIcons.RozpoznajPismo)
        PrzyciskWtorny("Skasuj", onSkasuj, ikona = KajetIcons.Kosz, kolor = Kajet.colors.danger)
        IkonaPrzycisk(KajetIcons.Zamknij, "Odznacz", onOdznacz)
    }
}

@Composable
private fun PanelPisaka(
    narzedzie: Narzedzie,
    kolorPiora: Int,
    gruboscPiora: Float,
    kolorZakreslacza: Int,
    gruboscZakreslacza: Float,
    promienGumki: Float,
    onKolorPiora: (Int) -> Unit,
    onGruboscPiora: (Float) -> Unit,
    onKolorZakreslacza: (Int) -> Unit,
    onGruboscZakreslacza: (Float) -> Unit,
    onPromienGumki: (Float) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier
            .width(260.dp)
            .background(Kajet.colors.sheet, RoundedCornerShape(Kajet.dimens.corner))
            .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when {
            narzedzie.gumka -> {
                EtykietaSekcji("Wielkość gumki")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(6f, 12f, 24f, 40f).forEach { promien ->
                        KolkoGrubosci(
                            wielkosc = promien / 3f,
                            wybrane = promienGumki == promien,
                            kolor = Kajet.colors.text,
                        ) { onPromienGumki(promien) }
                    }
                }
                Text(
                    text = narzedzie.opisPl,
                    style = Kajet.type.meta,
                    color = Kajet.colors.muted,
                )
            }

            narzedzie == Narzedzie.ZAKRESLACZ -> {
                EtykietaSekcji("Kolor zakreślacza")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InkPalette.zakreslacz.forEach { (nazwa, kolor) ->
                        KropkaAtramentu(nazwa, kolor, kolorZakreslacza == kolor.toArgb()) {
                            onKolorZakreslacza(kolor.toArgb())
                        }
                    }
                }
                EtykietaSekcji("Szerokość")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pedzle.gruboscZakreslacza.forEach { grubosc ->
                        KolkoGrubosci(
                            wielkosc = grubosc / 2.4f,
                            wybrane = gruboscZakreslacza == grubosc,
                            kolor = Color(kolorZakreslacza),
                        ) { onGruboscZakreslacza(grubosc) }
                    }
                }
            }

            else -> {
                EtykietaSekcji("Kolor atramentu")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InkPalette.pisak.forEach { (nazwa, kolor) ->
                        KropkaAtramentu(nazwa, kolor, kolorPiora == kolor.toArgb()) {
                            onKolorPiora(kolor.toArgb())
                        }
                    }
                }
                EtykietaSekcji("Grubość")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pedzle.gruboscPiora.forEach { grubosc ->
                        KolkoGrubosci(
                            wielkosc = grubosc * 1.6f,
                            wybrane = gruboscPiora == grubosc,
                            kolor = Color(kolorPiora),
                        ) { onGruboscPiora(grubosc) }
                    }
                }
                Text(
                    text = "Kreska grubieje tam, gdzie mocniej naciskasz rysikiem.",
                    style = Kajet.type.meta,
                    color = Kajet.colors.muted,
                )
            }
        }
    }
}

@Composable
private fun KropkaAtramentu(nazwa: String, kolor: Color, wybrany: Boolean, onKlik: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clickable(onClick = onKlik),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (wybrany) 26.dp else 20.dp)
                .background(kolor, CircleShape)
                .border(1.dp, Kajet.colors.line, CircleShape),
        )
    }
}

@Composable
private fun KolkoGrubosci(wielkosc: Float, wybrane: Boolean, kolor: Color, onKlik: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .background(
                if (wybrane) Kajet.colors.accentWash else Color.Transparent,
                RoundedCornerShape(Kajet.dimens.corner),
            )
            .clickable(onClick = onKlik),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(wielkosc.dp.coerceAtLeast(3.dp).coerceAtMost(26.dp))
                .background(kolor, CircleShape),
        )
    }
}

@Composable
private fun PanelTla(
    biezace: PageBackground,
    onWybor: (PageBackground) -> Unit,
    onDopasuj: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier
            .width(240.dp)
            .background(Kajet.colors.sheet, RoundedCornerShape(Kajet.dimens.corner))
            .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EtykietaSekcji("Tło strony")
        PageBackground.entries.forEach { wariant ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(
                        if (wariant == biezace) Kajet.colors.accentWash else Color.Transparent,
                        RoundedCornerShape(Kajet.dimens.corner),
                    )
                    .clickable { onWybor(wariant) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = wariant.nazwaPl,
                    style = Kajet.type.body,
                    color = if (wariant == biezace) Kajet.colors.accent else Kajet.colors.text,
                )
            }
        }
        LiniaPozioma()
        PrzyciskWtorny("Dopasuj szerokość", onDopasuj, ikona = KajetIcons.Dopasuj)
    }
}
