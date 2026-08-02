package wojtoteka.ovh.kajet.ekran.biblioteka

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import wojtoteka.ovh.kajet.core.design.FolderColor
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.EtykietaSekcji
import wojtoteka.ovh.kajet.core.design.component.LiniaPozioma
import wojtoteka.ovh.kajet.core.design.component.PrzyciskGlowny
import wojtoteka.ovh.kajet.core.design.component.PrzyciskWtorny
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.CodeLanguage
import wojtoteka.ovh.kajet.core.model.FolderIcon
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode

/** Wspólna rama okna. Prostokąt na kartce, bez cienia, z linią pod nagłówkiem. */
@Composable
fun OknoKajetu(
    tytul: String,
    onZamknij: () -> Unit,
    szerokosc: Int = 480,
    zawartosc: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onZamknij) {
        Column(
            modifier = Modifier
                .width(szerokosc.dp)
                .background(Kajet.colors.sheet, RoundedCornerShape(Kajet.dimens.corner))
                .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner)),
        ) {
            Text(
                text = tytul,
                style = Kajet.type.title,
                color = Kajet.colors.text,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
            )
            LiniaPozioma()
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                zawartosc()
            }
        }
    }
}

/** Pole tekstowe w stylu Kajetu: linia pod spodem, bez ramki dookoła. */
@Composable
fun PoleTekstu(
    wartosc: String,
    onZmiana: (String) -> Unit,
    etykieta: String,
    modifier: Modifier = Modifier,
    autofokus: Boolean = false,
    jednolinijkowe: Boolean = true,
) {
    val fokus = remember { FocusRequester() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EtykietaSekcji(etykieta)
        BasicTextField(
            value = wartosc,
            onValueChange = onZmiana,
            singleLine = jednolinijkowe,
            textStyle = Kajet.type.body.copy(color = Kajet.colors.text),
            cursorBrush = SolidColor(Kajet.colors.accent),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(fokus)
                .padding(vertical = 8.dp),
        )
        LiniaPozioma(kolor = Kajet.colors.muted.copy(alpha = 0.5f))
    }
    if (autofokus) {
        androidx.compose.runtime.LaunchedEffect(Unit) { fokus.requestFocus() }
    }
}

@Composable
fun OknoNowegoFolderu(
    onZamknij: () -> Unit,
    onUtworz: (nazwa: String, colorId: String, iconId: String) -> Unit,
) {
    var nazwa by remember { mutableStateOf("") }
    var kolor by remember { mutableStateOf(FolderColor.Grafit) }
    var ikona by remember { mutableStateOf(FolderIcon.FOLDER) }

    OknoKajetu("Nowy folder", onZamknij) {
        PoleTekstu(nazwa, { nazwa = it }, "Nazwa folderu", autofokus = true)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EtykietaSekcji("Kolor")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FolderColor.entries.forEach { wariant ->
                    KropkaKoloru(wariant, wariant == kolor) { kolor = wariant }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EtykietaSekcji("Ikona")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FolderIcon.entries.forEach { wariant ->
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (wariant == ikona) Kajet.colors.accentWash else Kajet.colors.desk,
                                RoundedCornerShape(Kajet.dimens.corner),
                            )
                            .clickable { ikona = wariant },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = KajetIcons.folderIcon(wariant.id),
                            contentDescription = wariant.labelPl,
                            tint = kolor.color(Kajet.colors.isDark),
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrzyciskGlowny(
                tekst = "Utwórz folder",
                onClick = { onUtworz(nazwa, kolor.id, ikona.id) },
                wlaczony = nazwa.isNotBlank(),
            )
            PrzyciskWtorny("Anuluj", onZamknij)
        }
    }
}

@Composable
private fun KropkaKoloru(wariant: FolderColor, wybrany: Boolean, onKlik: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clickable(onClick = onKlik),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (wybrany) 26.dp else 20.dp)
                .background(wariant.color(Kajet.colors.isDark), CircleShape),
        )
        if (wybrany) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .border(1.dp, Kajet.colors.text, CircleShape),
            )
        }
    }
}

@Composable
fun OknoNowejNotatki(
    onZamknij: () -> Unit,
    onUtworz: (tytul: String, rodzaj: NoteKind, tryb: PageMode, tlo: PageBackground) -> Unit,
    domyslnyTryb: PageMode,
    domyslneTlo: PageBackground,
) {
    var tytul by remember { mutableStateOf("") }
    var rodzaj by remember { mutableStateOf(NoteKind.ODRECZNA) }
    var tryb by remember { mutableStateOf(domyslnyTryb) }
    var tlo by remember { mutableStateOf(domyslneTlo) }

    OknoKajetu("Nowa notatka", onZamknij, szerokosc = 520) {
        PoleTekstu(tytul, { tytul = it }, "Tytuł", autofokus = true)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EtykietaSekcji("Rodzaj")
            NoteKind.entries.forEach { wariant ->
                WyborWiersz(
                    tekst = wariant.nazwaPl,
                    opis = when (wariant) {
                        NoteKind.ODRECZNA -> "Piszesz rysikiem, możesz też wstawić pole z tekstem."
                        NoteKind.TEKSTOWA -> "Piszesz z klawiatury, możesz wstawić zdjęcie i mały rysunek."
                        NoteKind.MAPA -> "Węzły połączone liniami, podpisy z klawiatury albo rysikiem."
                    },
                    wybrany = wariant == rodzaj,
                    onKlik = { rodzaj = wariant },
                )
            }
        }

        if (rodzaj == NoteKind.ODRECZNA) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EtykietaSekcji("Strona")
                PageMode.entries.forEach { wariant ->
                    WyborWiersz(
                        tekst = wariant.nazwaPl,
                        opis = if (wariant == PageMode.A4) {
                            "Tak jak w zeszycie. Wydruk wychodzi bez przycinania."
                        } else {
                            "Strona rośnie w dół, kiedy piszesz przy dolnej krawędzi."
                        },
                        wybrany = wariant == tryb,
                        onKlik = { tryb = wariant },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EtykietaSekcji("Tło strony")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageBackground.entries.forEach { wariant ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .background(
                                    if (wariant == tlo) Kajet.colors.accentWash else Kajet.colors.desk,
                                    RoundedCornerShape(Kajet.dimens.corner),
                                )
                                .clickable { tlo = wariant }
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = wariant.nazwaPl,
                                style = Kajet.type.meta,
                                color = if (wariant == tlo) Kajet.colors.accent else Kajet.colors.muted,
                            )
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrzyciskGlowny(
                tekst = "Utwórz notatkę",
                onClick = { onUtworz(tytul.ifBlank { "Bez tytułu" }, rodzaj, tryb, tlo) },
            )
            PrzyciskWtorny("Anuluj", onZamknij)
        }
    }
}

@Composable
fun OknoNowegoPliku(
    onZamknij: () -> Unit,
    onUtworz: (nazwa: String, jezyk: CodeLanguage) -> Unit,
) {
    var nazwa by remember { mutableStateOf("") }
    var jezyk by remember { mutableStateOf(CodeLanguage.PYTHON) }

    OknoKajetu("Nowy plik z kodem", onZamknij, szerokosc = 520) {
        PoleTekstu(nazwa, { nazwa = it }, "Nazwa pliku", autofokus = true)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EtykietaSekcji("Język")
            CodeLanguage.entries.filter { it.uruchamialny }.forEach { wariant ->
                WyborWiersz(
                    tekst = wariant.labelPl,
                    opis = if (wariant.offline) {
                        "Uruchamia się na tablecie, bez internetu."
                    } else {
                        "Uruchamia się na serwerze, potrzebny internet."
                    },
                    wybrany = wariant == jezyk,
                    onKlik = { jezyk = wariant },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrzyciskGlowny(
                tekst = "Utwórz plik",
                onClick = { onUtworz(nazwa.ifBlank { "program" }, jezyk) },
            )
            PrzyciskWtorny("Anuluj", onZamknij)
        }
    }
}

@Composable
fun OknoZmianyNazwy(
    biezaca: String,
    onZamknij: () -> Unit,
    onZapisz: (String) -> Unit,
) {
    var nazwa by remember { mutableStateOf(biezaca) }
    OknoKajetu("Zmień nazwę", onZamknij) {
        PoleTekstu(nazwa, { nazwa = it }, "Nowa nazwa", autofokus = true)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrzyciskGlowny("Zapisz", { onZapisz(nazwa) }, wlaczony = nazwa.isNotBlank())
            PrzyciskWtorny("Anuluj", onZamknij)
        }
    }
}

@Composable
fun WyborWiersz(
    tekst: String,
    opis: String?,
    wybrany: Boolean,
    onKlik: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(
                if (wybrany) Kajet.colors.accentWash else Kajet.colors.sheet,
                RoundedCornerShape(Kajet.dimens.corner),
            )
            .clickable(onClick = onKlik)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (wybrany) {
                Icon(
                    imageVector = KajetIcons.Zatwierdz,
                    contentDescription = null,
                    tint = Kajet.colors.accent,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .border(1.dp, Kajet.colors.line, CircleShape),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tekst, style = Kajet.type.body, color = Kajet.colors.text)
            if (opis != null) {
                Text(opis, style = Kajet.type.meta, color = Kajet.colors.muted)
            }
        }
    }
}
