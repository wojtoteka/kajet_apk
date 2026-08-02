package wojtoteka.ovh.kajet.code

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.EtykietaSekcji
import wojtoteka.ovh.kajet.core.design.component.IkonaPrzycisk
import wojtoteka.ovh.kajet.core.design.component.LiniaPozioma
import wojtoteka.ovh.kajet.core.design.component.PrzyciskWtorny
import wojtoteka.ovh.kajet.core.design.component.liniaMarginesu
import wojtoteka.ovh.kajet.core.design.icon.IkonyJezykow
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons

/**
 * Edytor kodu.
 *
 * Ekran dzieli się poziomo: u góry kod, na dole panel z wynikiem.
 * Rynna z numerami linii to ta sama pionowa linia, która na innych ekranach
 * jest marginesem, tylko tutaj niesie numery.
 */
@Composable
fun EdytorKodu(
    model: ModelKodu,
    onWstecz: () -> Unit,
) {
    val kod by model.kod.collectAsStateWithLifecycle()
    val wejscie by model.wejscie.collectAsStateWithLifecycle()
    val wynik by model.wynik.collectAsStateWithLifecycle()
    val zakladka by model.zakladka.collectAsStateWithLifecycle()
    val uruchamianie by model.uruchamianie.collectAsStateWithLifecycle()
    val blad by model.blad.collectAsStateWithLifecycle()
    val zapisane by model.zapisane.collectAsStateWithLifecycle()
    val zawijanie by model.zawijanie.collectAsStateWithLifecycle()
    val szukanie by model.szukanie.collectAsStateWithLifecycle()
    val trafienia by model.trafienia.collectAsStateWithLifecycle()

    val kolory = Kajet.colors
    var szukanieWidoczne by remember { mutableStateOf(false) }

    val barwy = remember(kolory.isDark) {
        BarwyKodu(
            zwykly = kolory.text,
            slowoKluczowe = kolory.accent,
            napis = if (kolory.isDark) androidx.compose.ui.graphics.Color(0xFFD6A648) else androidx.compose.ui.graphics.Color(0xFF8A6212),
            liczba = if (kolory.isDark) androidx.compose.ui.graphics.Color(0xFF5AA8C4) else androidx.compose.ui.graphics.Color(0xFF1C5C74),
            komentarz = kolory.muted,
            nazwaWlasna = if (kolory.isDark) androidx.compose.ui.graphics.Color(0xFF9EB367) else androidx.compose.ui.graphics.Color(0xFF56662A),
        )
    }

    val wlasciciel = LocalLifecycleOwner.current
    DisposableEffect(wlasciciel) {
        val obserwator = LifecycleEventObserver { _, zdarzenie ->
            if (zdarzenie == Lifecycle.Event.ON_STOP) model.zapiszTeraz()
        }
        wlasciciel.lifecycle.addObserver(obserwator)
        onDispose { wlasciciel.lifecycle.removeObserver(obserwator) }
    }

    Row(Modifier.fillMaxSize().background(kolory.desk)) {

        Column(
            Modifier
                .width(Kajet.dimens.railWidth)
                .fillMaxSize()
                .background(kolory.desk)
                .liniaMarginesu(kolory.line)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IkonaPrzycisk(KajetIcons.Wstecz, "Wróć do biblioteki", { model.zapiszTeraz(); onWstecz() })
            LiniaPozioma(Modifier.padding(horizontal = 12.dp))

            if (uruchamianie) {
                IkonaPrzycisk(KajetIcons.Zatrzymaj, "Zatrzymaj", model::zatrzymaj)
            } else {
                IkonaPrzycisk(
                    ikona = KajetIcons.Uruchom,
                    opis = "Uruchom program",
                    onClick = model::uruchom,
                    wlaczony = model.sposob != null,
                    wybrany = model.sposob != null,
                )
            }
            IkonaPrzycisk(KajetIcons.Szukaj, "Szukaj w pliku", { szukanieWidoczne = !szukanieWidoczne }, wybrany = szukanieWidoczne)
            IkonaPrzycisk(KajetIcons.Zawijanie, "Zawijanie wierszy", model::przelaczZawijanie, wybrany = zawijanie)
            Spacer(Modifier.height(12.dp))
        }

        Column(Modifier.fillMaxSize()) {

            NaglowekPliku(model = model, zapisane = zapisane)
            LiniaPozioma()

            if (szukanieWidoczne) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(kolory.desk)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(KajetIcons.Szukaj, null, tint = kolory.muted, modifier = Modifier.size(18.dp))
                    BasicTextField(
                        value = szukanie,
                        onValueChange = model::szukaj,
                        singleLine = true,
                        textStyle = Kajet.type.code.copy(color = kolory.text),
                        cursorBrush = SolidColor(kolory.accent),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = when {
                            szukanie.length < 2 -> "Wpisz co najmniej dwie litery"
                            trafienia.isEmpty() -> "Nic nie znalazłem"
                            else -> "Znalazłem ${trafienia.size}"
                        },
                        style = Kajet.type.meta,
                        color = kolory.muted,
                    )
                }
                LiniaPozioma()
            }

            if (blad != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(kolory.desk)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(KajetIcons.BezSieci, null, tint = kolory.danger, modifier = Modifier.size(18.dp))
                    Text(blad.orEmpty(), style = Kajet.type.body, color = kolory.text, modifier = Modifier.weight(1f))
                    PrzyciskWtorny("Rozumiem", model::schowajBlad)
                }
                LiniaPozioma()
            }

            // Kod z rynną numerów linii
            Box(
                Modifier
                    .weight(0.62f)
                    .fillMaxWidth()
                    .background(kolory.sheet)
                    .imePadding(),
            ) {
                val przewijaniePionowe = rememberScrollState()
                Row(Modifier.fillMaxSize().verticalScroll(przewijaniePionowe)) {
                    RynnaNumerow(kod)
                    Box(
                        Modifier
                            .weight(1f)
                            .then(
                                if (zawijanie) Modifier else Modifier.horizontalScroll(rememberScrollState()),
                            ),
                    ) {
                        BasicTextField(
                            value = kod,
                            onValueChange = model::zmienKod,
                            textStyle = Kajet.type.code.copy(color = kolory.text),
                            cursorBrush = SolidColor(kolory.accent),
                            visualTransformation = { tekst ->
                                androidx.compose.ui.text.input.TransformedText(
                                    Kolorowanie.pokoloruj(tekst.text, model.jezyk, barwy),
                                    androidx.compose.ui.text.input.OffsetMapping.Identity,
                                )
                            },
                            modifier = Modifier
                                .padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 40.dp)
                                .fillMaxWidth(),
                        )
                    }
                }
            }

            LiniaPozioma()
            PanelWyniku(
                zakladka = zakladka,
                wynik = wynik,
                wejscie = wejscie,
                uruchamianie = uruchamianie,
                offline = model.offline,
                modifier = Modifier.weight(0.38f),
                onZakladka = model::ustawZakladke,
                onWejscie = model::zmienWejscie,
            )
        }
    }
}

@Composable
private fun NaglowekPliku(model: ModelKodu, zapisane: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Kajet.colors.sheet)
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = IkonyJezykow.dla(model.jezyk),
            contentDescription = model.jezyk.labelPl,
            tint = Kajet.colors.accent,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(model.nazwaPliku, style = Kajet.type.title, color = Kajet.colors.text)
            Text(
                text = when {
                    model.sposob == null -> "${model.jezyk.labelPl}. Tego języka Kajet nie uruchomi."
                    model.offline -> "${model.jezyk.labelPl}. Uruchamia się na tablecie, bez internetu."
                    else -> "${model.jezyk.labelPl}. Uruchamia się na serwerze, potrzebny internet."
                },
                style = Kajet.type.meta,
                color = Kajet.colors.muted,
            )
        }
        Text(
            text = if (zapisane) "Zapisane" else "Zmiany czekają",
            style = Kajet.type.meta,
            color = Kajet.colors.muted,
        )
    }
}

/**
 * Numery linii. Rynna jest zamknięta pionową linią, tą samą,
 * która na innych ekranach jest marginesem.
 */
@Composable
private fun RynnaNumerow(kod: String) {
    val liczbaLinii = kod.count { it == '\n' } + 1
    Column(
        Modifier
            .width(52.dp)
            .background(Kajet.colors.desk)
            .liniaMarginesu(Kajet.colors.line)
            .padding(top = 8.dp, end = 8.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.End,
    ) {
        for (numer in 1..liczbaLinii) {
            Text(
                text = numer.toString(),
                style = Kajet.type.codeGutter,
                color = Kajet.colors.muted,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun PanelWyniku(
    zakladka: ZakladkaPanelu,
    wynik: WynikUruchomienia?,
    wejscie: String,
    uruchamianie: Boolean,
    offline: Boolean,
    modifier: Modifier,
    onZakladka: (ZakladkaPanelu) -> Unit,
    onWejscie: (String) -> Unit,
) {
    val kolory = Kajet.colors

    Column(modifier.fillMaxWidth().background(kolory.desk)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZakladkaPanelu.entries.forEach { wariant ->
                val liczba = when (wariant) {
                    ZakladkaPanelu.BLEDY -> if (!wynik?.bledy.isNullOrBlank()) " !" else ""
                    else -> ""
                }
                Box(
                    Modifier
                        .height(44.dp)
                        .clickable(onClickLabel = wariant.nazwaPl) { onZakladka(wariant) }
                        .background(if (zakladka == wariant) kolory.sheet else kolory.desk)
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = when (wariant) {
                                ZakladkaPanelu.WYNIK -> KajetIcons.Wynik
                                ZakladkaPanelu.BLEDY -> KajetIcons.Blad
                                ZakladkaPanelu.WEJSCIE -> KajetIcons.Wejscie
                            },
                            contentDescription = null,
                            tint = if (zakladka == wariant) kolory.accent else kolory.muted,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = wariant.nazwaPl + liczba,
                            style = Kajet.type.label,
                            color = if (zakladka == wariant) kolory.text else kolory.muted,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (uruchamianie) {
                Text(
                    text = if (offline) "Liczę na tablecie..." else "Wysyłam na serwer...",
                    style = Kajet.type.meta,
                    color = kolory.muted,
                    modifier = Modifier.padding(end = 16.dp),
                )
            } else if (wynik != null) {
                Text(
                    text = buildString {
                        append(if (wynik.przezSiec) "Serwer" else "Tablet")
                        append(", ")
                        append(wynik.czasMs)
                        append(" ms")
                        if (wynik.kodWyjscia != null) {
                            append(", kod wyjścia ")
                            append(wynik.kodWyjscia)
                        }
                    },
                    style = Kajet.type.meta,
                    color = kolory.muted,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(kolory.sheet)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            when (zakladka) {
                ZakladkaPanelu.WYNIK -> TekstPanelu(
                    tekst = wynik?.wyjscie.orEmpty(),
                    pusty = "Naciśnij przycisk uruchomienia po lewej stronie. Tu pojawi się to, co program wypisze.",
                )

                ZakladkaPanelu.BLEDY -> TekstPanelu(
                    tekst = wynik?.bledy.orEmpty(),
                    pusty = "Nie ma błędów.",
                    kolor = kolory.danger,
                )

                ZakladkaPanelu.WEJSCIE -> Column {
                    EtykietaSekcji("Dane, które program przeczyta")
                    Spacer(Modifier.height(8.dp))
                    BasicTextField(
                        value = wejscie,
                        onValueChange = onWejscie,
                        textStyle = Kajet.type.code.copy(color = kolory.text),
                        cursorBrush = SolidColor(kolory.accent),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}

@Composable
private fun TekstPanelu(
    tekst: String,
    pusty: String,
    kolor: androidx.compose.ui.graphics.Color = Kajet.colors.text,
) {
    if (tekst.isBlank()) {
        Text(pusty, style = Kajet.type.body, color = Kajet.colors.muted)
    } else {
        Text(
            text = tekst,
            style = Kajet.type.code,
            color = kolor,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState()),
        )
    }
}
