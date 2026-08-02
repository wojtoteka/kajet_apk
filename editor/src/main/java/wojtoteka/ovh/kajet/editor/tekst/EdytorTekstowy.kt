package wojtoteka.ovh.kajet.editor.tekst

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.IkonaPrzycisk
import wojtoteka.ovh.kajet.core.design.component.LiniaPozioma
import wojtoteka.ovh.kajet.core.design.component.PrzyciskWtorny
import wojtoteka.ovh.kajet.core.design.component.liniaMarginesu
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.editor.StanZapisu

/**
 * Edytor notatki tekstowej.
 *
 * Po lewej piszesz, po prawej od razu widzisz wynik. Na wąskim ekranie
 * podgląd chowa się i przełączasz go przyciskiem. Pod spodem cały czas
 * leży zwykły Markdown, ten sam, który wychodzi przy eksporcie.
 */
@Composable
fun EdytorTekstowy(
    model: ModelTekstowy,
    onWstecz: () -> Unit,
    onEksport: () -> Unit,
    onZdjecieZGalerii: () -> Unit,
    onZdjecieZAparatu: () -> Unit,
) {
    val dokument by model.dokument.collectAsStateWithLifecycle()
    val podglad by model.podglad.collectAsStateWithLifecycle()
    val rysowanie by model.rysowanie.collectAsStateWithLifecycle()
    val zajety by model.zajety.collectAsStateWithLifecycle()
    val stanZapisu by model.stanZapisu.collectAsStateWithLifecycle()
    val blad by model.blad.collectAsStateWithLifecycle()

    val kolory = Kajet.colors
    val szeroko = LocalConfiguration.current.screenWidthDp >= 840

    var pole by remember(dokument?.id) {
        mutableStateOf(TextFieldValue(dokument?.text?.markdown.orEmpty()))
    }

    // Treść mogła zmienić się poza polem, na przykład przez odhaczenie zadania
    // w podglądzie albo przez wstawienie rysunku.
    val trescModelu = dokument?.text?.markdown.orEmpty()
    if (trescModelu != pole.text) {
        pole = pole.copy(
            text = trescModelu,
            selection = TextRange(pole.selection.start.coerceAtMost(trescModelu.length)),
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

    fun ustawKursor(pozycja: Int) {
        pole = pole.copy(selection = TextRange(pozycja.coerceIn(0, model.markdown.length)))
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

            IkonaPrzycisk(KajetIcons.Zdjecie, "Wstaw zdjęcie z galerii", onZdjecieZGalerii)
            IkonaPrzycisk(KajetIcons.Aparat, "Zrób zdjęcie", onZdjecieZAparatu)
            IkonaPrzycisk(KajetIcons.Rysunek, "Wstaw rysunek", model::otworzRysowanie)

            LiniaPozioma(Modifier.padding(horizontal = 12.dp))

            IkonaPrzycisk(
                ikona = KajetIcons.NotatkaTekstowa,
                opis = if (podglad) "Schowaj podgląd" else "Pokaż podgląd",
                onClick = model::przelaczPodglad,
                wybrany = podglad,
            )
            IkonaPrzycisk(
                ikona = KajetIcons.Ulubione,
                opis = if (dokument?.favorite == true) "Usuń z ulubionych" else "Dodaj do ulubionych",
                onClick = model::przelaczUlubione,
                wybrany = dokument?.favorite == true,
            )
            IkonaPrzycisk(KajetIcons.Eksport, "Eksportuj notatkę", onEksport)
            Spacer(Modifier.height(12.dp))
        }

        Column(Modifier.fillMaxSize()) {
            NaglowekNotatki(
                tytul = dokument?.title.orEmpty(),
                stan = stanZapisu,
                zajety = zajety,
            )
            LiniaPozioma()

            if (blad != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(kolory.desk)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(KajetIcons.Blad, null, tint = kolory.danger, modifier = Modifier.size(18.dp))
                    Text(blad.orEmpty(), style = Kajet.type.body, color = kolory.text, modifier = Modifier.weight(1f))
                    PrzyciskWtorny("Rozumiem", model::schowajBlad)
                }
                LiniaPozioma()
            }

            PasekFormatowania(
                onPogrubienie = {
                    val zakres = model.otocz("**", pole.selection.start, pole.selection.end)
                    pole = pole.copy(selection = TextRange(zakres.first, zakres.last))
                },
                onKursywa = {
                    val zakres = model.otocz("*", pole.selection.start, pole.selection.end)
                    pole = pole.copy(selection = TextRange(zakres.first, zakres.last))
                },
                onNaglowek = { ustawKursor(model.naPoczatkuWiersza("## ", pole.selection.start)) },
                onLista = { ustawKursor(model.naPoczatkuWiersza("- ", pole.selection.start)) },
                onZadanie = { ustawKursor(model.naPoczatkuWiersza("- [ ] ", pole.selection.start)) },
                onCytat = { ustawKursor(model.naPoczatkuWiersza("> ", pole.selection.start)) },
                onKod = {
                    ustawKursor(model.wstaw("\n```\n\n```\n", pole.selection.start, pole.selection.end) - 5)
                },
                onWzor = {
                    ustawKursor(model.wstaw("\n$$\n\n$$\n", pole.selection.start, pole.selection.end) - 4)
                },
            )
            LiniaPozioma()

            Row(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .weight(if (podglad && szeroko) 0.5f else 1f)
                        .fillMaxSize()
                        .background(kolory.sheet)
                        .imePadding(),
                ) {
                    BasicTextField(
                        value = pole,
                        onValueChange = { nowe ->
                            pole = nowe
                            model.zmienTresc(nowe.text)
                        },
                        textStyle = Kajet.type.bodyLarge.copy(color = kolory.text),
                        cursorBrush = SolidColor(kolory.accent),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 28.dp, end = 24.dp, top = 20.dp, bottom = 120.dp)
                            .widthIn(max = Kajet.dimens.readingWidth),
                    )
                    if (pole.text.isEmpty()) {
                        Text(
                            text = "Zacznij pisać. Znaki formatowania działają jak w Markdown.",
                            style = Kajet.type.bodyLarge,
                            color = kolory.muted,
                            modifier = Modifier.padding(start = 28.dp, top = 20.dp),
                        )
                    }
                }

                if (podglad && szeroko) {
                    Box(
                        Modifier
                            .width(1.dp)
                            .fillMaxSize()
                            .background(kolory.line),
                    )
                    PodgladMarkdown(
                        markdown = pole.text,
                        kolory = kolory,
                        zalacznik = model::zalacznik,
                        onZadanie = model::przelaczZadanie,
                        modifier = Modifier
                            .weight(0.5f)
                            .fillMaxSize()
                            .background(kolory.sheet),
                    )
                } else if (podglad) {
                    PodgladMarkdown(
                        markdown = pole.text,
                        kolory = kolory,
                        zalacznik = model::zalacznik,
                        onZadanie = model::przelaczZadanie,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(kolory.sheet),
                    )
                }
            }
        }
    }

    if (rysowanie) {
        OknoRysunku(
            onZamknij = model::zamknijRysowanie,
            onGotowe = { kreski, szerokosc, wysokosc ->
                model.wstawRysunek(kreski, szerokosc, wysokosc, pole.selection.start)
            },
        )
    }
}

@Composable
private fun NaglowekNotatki(tytul: String, stan: StanZapisu, zajety: String?) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Kajet.colors.sheet)
            .padding(start = 28.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(tytul, style = Kajet.type.display, color = Kajet.colors.text, modifier = Modifier.weight(1f))
        if (zajety != null) {
            Text(zajety, style = Kajet.type.meta, color = Kajet.colors.muted)
        }
        Box(
            Modifier
                .size(6.dp)
                .background(
                    if (stan == StanZapisu.BLAD) Kajet.colors.danger else Kajet.colors.muted,
                    CircleShape,
                ),
        )
        Text(
            text = when (stan) {
                StanZapisu.WCZYTYWANIE -> "Wczytuję"
                StanZapisu.ZAPISANE -> "Zapisane"
                StanZapisu.ZMIENIONE -> "Zmiany czekają"
                StanZapisu.ZAPISYWANIE -> "Zapisuję"
                StanZapisu.BLAD -> "Zapis się nie udał"
            },
            style = Kajet.type.meta,
            color = Kajet.colors.muted,
        )
    }
}

@Composable
private fun PasekFormatowania(
    onPogrubienie: () -> Unit,
    onKursywa: () -> Unit,
    onNaglowek: () -> Unit,
    onLista: () -> Unit,
    onZadanie: () -> Unit,
    onCytat: () -> Unit,
    onKod: () -> Unit,
    onWzor: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Kajet.colors.desk)
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ZnakFormatu("B", "Pogrubienie", onPogrubienie, pogrubione = true)
        ZnakFormatu("I", "Kursywa", onKursywa, kursywa = true)
        ZnakFormatu("H", "Nagłówek", onNaglowek)
        ZnakFormatu("•", "Lista", onLista)
        IkonaPrzycisk(KajetIcons.Zatwierdz, "Lista zadań", onZadanie, rozmiarIkony = 18.dp)
        ZnakFormatu("„", "Cytat", onCytat)
        IkonaPrzycisk(KajetIcons.PlikKodu, "Blok kodu", onKod, rozmiarIkony = 18.dp)
        ZnakFormatu("Σ", "Wzór matematyczny", onWzor)
    }
}

@Composable
private fun ZnakFormatu(
    znak: String,
    opis: String,
    onKlik: () -> Unit,
    pogrubione: Boolean = false,
    kursywa: Boolean = false,
) {
    Box(
        Modifier
            .size(48.dp)
            .semantics {
                contentDescription = opis
                role = Role.Button
            }
            .clickable(onClick = onKlik),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = znak,
            style = Kajet.type.titleSmall.copy(
                fontWeight = if (pogrubione) androidx.compose.ui.text.font.FontWeight.Bold else null,
                fontStyle = if (kursywa) androidx.compose.ui.text.font.FontStyle.Italic else null,
            ),
            color = Kajet.colors.text,
        )
    }
}
