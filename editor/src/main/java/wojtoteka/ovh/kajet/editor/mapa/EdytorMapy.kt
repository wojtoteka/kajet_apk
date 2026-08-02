package wojtoteka.ovh.kajet.editor.mapa

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wojtoteka.ovh.kajet.core.design.FolderColor
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.EtykietaSekcji
import wojtoteka.ovh.kajet.core.design.component.IkonaPrzycisk
import wojtoteka.ovh.kajet.core.design.component.LiniaPozioma
import wojtoteka.ovh.kajet.core.design.component.PrzyciskWtorny
import wojtoteka.ovh.kajet.core.design.component.liniaMarginesu
import wojtoteka.ovh.kajet.core.design.component.rysujKreski
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.MindNode
import wojtoteka.ovh.kajet.core.model.NodeShape
import wojtoteka.ovh.kajet.editor.StanZapisu
import kotlin.math.roundToInt

/**
 * Edytor mapy myśli.
 *
 * Planszę przesuwasz i skalujesz dwoma palcami. Węzeł przeciągasz palcem
 * albo rysikiem. Linia trzyma się węzła, bo rysujemy ją zawsze od krawędzi
 * do krawędzi, a nie od zapamiętanego punktu.
 */
@Composable
fun EdytorMapy(
    model: ModelMapy,
    onWstecz: () -> Unit,
    onEksport: () -> Unit,
) {
    val dokument by model.dokument.collectAsStateWithLifecycle()
    val wybrany by model.wybrany.collectAsStateWithLifecycle()
    val edytowany by model.edytowany.collectAsStateWithLifecycle()
    val laczenie by model.laczenie.collectAsStateWithLifecycle()
    val podpis by model.podpisRysikiem.collectAsStateWithLifecycle()
    val stanZapisu by model.stanZapisu.collectAsStateWithLifecycle()
    val mozeCofnac by model.mozeCofnac.collectAsStateWithLifecycle()
    val mozePonowic by model.mozePonowic.collectAsStateWithLifecycle()

    val kolory = Kajet.colors
    val gestosc = LocalDensity.current
    val mapa = dokument?.mindMap

    var przesuniecieX by remember { mutableFloatStateOf(mapa?.viewX ?: 0f) }
    var przesuniecieY by remember { mutableFloatStateOf(mapa?.viewY ?: 0f) }
    var zoom by remember { mutableFloatStateOf(mapa?.zoom ?: 1f) }

    val wlasciciel = LocalLifecycleOwner.current
    DisposableEffect(wlasciciel) {
        val obserwator = LifecycleEventObserver { _, zdarzenie ->
            if (zdarzenie == Lifecycle.Event.ON_STOP) {
                model.zapamietajWidok(przesuniecieX, przesuniecieY, zoom)
                model.zapiszTeraz()
            }
        }
        wlasciciel.lifecycle.addObserver(obserwator)
        onDispose { wlasciciel.lifecycle.removeObserver(obserwator) }
    }

    val widoczne = remember(mapa) { mapa?.let { UkladMapy.widoczne(it) } ?: emptySet() }

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

            IkonaPrzycisk(
                ikona = KajetIcons.Wezel,
                opis = "Nowy węzeł",
                onClick = { model.dodajWezel(przesuniecieX + 120f, przesuniecieY + 120f) },
            )
            IkonaPrzycisk(
                ikona = KajetIcons.Dodaj,
                opis = "Dodaj gałąź do wybranego węzła",
                onClick = { wybrany?.let { model.dodajDziecko(it) } },
                wlaczony = wybrany != null,
            )
            IkonaPrzycisk(
                ikona = KajetIcons.Udostepnij,
                opis = if (laczenie) "Przerwij łączenie" else "Połącz dwa węzły",
                onClick = model::przelaczLaczenie,
                wybrany = laczenie,
                wlaczony = wybrany != null || laczenie,
            )
            IkonaPrzycisk(
                ikona = KajetIcons.Pioro,
                opis = "Podpis rysikiem",
                onClick = { wybrany?.let { model.otworzPodpisRysikiem(it) } },
                wlaczony = wybrany != null,
            )

            LiniaPozioma(Modifier.padding(horizontal = 12.dp))

            IkonaPrzycisk(KajetIcons.MapaMysli, "Rozłóż gałęzie automatycznie", model::rozlozGalezie)
            IkonaPrzycisk(
                ikona = KajetIcons.Dopasuj,
                opis = "Wróć do środka",
                onClick = {
                    przesuniecieX = 0f
                    przesuniecieY = 0f
                    zoom = 1f
                },
            )

            LiniaPozioma(Modifier.padding(horizontal = 12.dp))

            IkonaPrzycisk(KajetIcons.Cofnij, "Cofnij", model::cofnij, wlaczony = mozeCofnac)
            IkonaPrzycisk(KajetIcons.Ponow, "Ponów", model::ponow, wlaczony = mozePonowic)

            LiniaPozioma(Modifier.padding(horizontal = 12.dp))

            IkonaPrzycisk(
                ikona = KajetIcons.Ulubione,
                opis = if (dokument?.favorite == true) "Usuń z ulubionych" else "Dodaj do ulubionych",
                onClick = model::przelaczUlubione,
                wybrany = dokument?.favorite == true,
            )
            IkonaPrzycisk(KajetIcons.Eksport, "Eksportuj mapę", onEksport)
            Spacer(Modifier.height(12.dp))
        }

        Box(Modifier.fillMaxSize()) {
            if (mapa != null) {
                // Plansza. Dwa palce przesuwają i skalują, jeden palec w pustym
                // miejscu odznacza węzeł.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(kolory.desk)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, przesuniecie, zmianaSkali, _ ->
                                zoom = (zoom * zmianaSkali).coerceIn(0.25f, 4f)
                                przesuniecieX -= przesuniecie.x / zoom
                                przesuniecieY -= przesuniecie.y / zoom
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { model.wybierz(null) })
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        // Linie między węzłami
                        val poId = mapa.nodes.associateBy { it.id }
                        for (linia in mapa.edges) {
                            val od = poId[linia.fromId] ?: continue
                            val doWezla = poId[linia.toId] ?: continue
                            if (od.id !in widoczne || doWezla.id !in widoczne) continue
                            rysujLinie(od, doWezla, przesuniecieX, przesuniecieY, zoom, kolory.line)
                        }
                        // Podpisy pisane rysikiem
                        for (wezel in mapa.nodes) {
                            if (wezel.id !in widoczne || wezel.ink.isEmpty()) continue
                            rysujKreski(
                                kreski = wezel.ink,
                                kolor = FolderColor.fromId(wezel.colorId).color(kolory.isDark),
                                przesuniecie = Offset(
                                    (wezel.x - przesuniecieX) * zoom,
                                    (wezel.y - przesuniecieY) * zoom,
                                ),
                                skala = zoom,
                                grubosc = 2f * zoom,
                            )
                        }
                    }

                    mapa.nodes.filter { it.id in widoczne }.forEach { wezel ->
                        WezelNaPlanszy(
                            wezel = wezel,
                            przesuniecieX = przesuniecieX,
                            przesuniecieY = przesuniecieY,
                            zoom = zoom,
                            wybrany = wybrany == wezel.id,
                            edytowany = edytowany == wezel.id,
                            maDzieci = UkladMapy.maDzieci(mapa, wezel.id),
                            onWybierz = { model.wybierz(wezel.id) },
                            onEdytuj = { model.edytuj(wezel.id) },
                            onTekst = { model.zmienTekst(wezel.id, it) },
                            onPoczatekPrzesuwania = model::rozpocznijPrzesuwanie,
                            onPrzesun = { dx, dy -> model.przesunWezel(wezel.id, dx / zoom, dy / zoom) },
                            onKoniecPrzesuwania = model::zakonczPrzesuwanie,
                            onZwin = { model.przelaczZwiniecie(wezel.id) },
                        )
                    }
                }

                if (laczenie) {
                    Text(
                        text = "Dotknij drugiego węzła, żeby połączyć go linią.",
                        style = Kajet.type.label,
                        color = kolory.onAccent,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                            .background(kolory.accent, RoundedCornerShape(Kajet.dimens.corner))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }

                wybrany?.let { id ->
                    val wezel = mapa.nodes.firstOrNull { it.id == id }
                    if (wezel != null) {
                        PanelWezla(
                            wezel = wezel,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp),
                            onKsztalt = { model.zmienKsztalt(id, it) },
                            onKolor = { model.zmienKolor(id, it) },
                            onUsun = { model.usunWezel(id) },
                        )
                    }
                }

                if (mapa.nodes.isEmpty()) {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Pusta mapa", style = Kajet.type.title, color = kolory.text)
                        Text(
                            text = "Dodaj pierwszy węzeł przyciskiem po lewej stronie, a potem doczepiaj do niego gałęzie.",
                            style = Kajet.type.body,
                            color = kolory.muted,
                            modifier = Modifier.width(360.dp),
                        )
                    }
                }
            }

            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(kolory.sheet.copy(alpha = 0.94f), RoundedCornerShape(Kajet.dimens.corner))
                    .border(1.dp, kolory.line, RoundedCornerShape(Kajet.dimens.corner))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(dokument?.title.orEmpty(), style = Kajet.type.titleSmall, color = kolory.text)
                Text(
                    text = when (stanZapisu) {
                        StanZapisu.ZAPISANE -> "Zapisane"
                        StanZapisu.ZAPISYWANIE -> "Zapisuję"
                        StanZapisu.ZMIENIONE -> "Zmiany czekają"
                        StanZapisu.WCZYTYWANIE -> "Wczytuję"
                        StanZapisu.BLAD -> "Zapis się nie udał"
                    },
                    style = Kajet.type.meta,
                    color = kolory.muted,
                )
            }
        }
    }

    podpis?.let { id ->
        val wezel = mapa?.nodes?.firstOrNull { it.id == id }
        if (wezel != null) {
            wojtoteka.ovh.kajet.editor.tekst.OknoRysunku(
                onZamknij = model::zamknijPodpisRysikiem,
                onGotowe = { kreski, _, _ -> model.zmienPodpisRysikiem(id, kreski) },
            )
        }
    }
}

/** Linia od krawędzi jednego węzła do krawędzi drugiego. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.rysujLinie(
    od: MindNode,
    doWezla: MindNode,
    przesuniecieX: Float,
    przesuniecieY: Float,
    zoom: Float,
    kolor: Color,
) {
    val odX = (od.x + od.width / 2f - przesuniecieX) * zoom
    val odY = (od.y + od.height / 2f - przesuniecieY) * zoom
    val doX = (doWezla.x + doWezla.width / 2f - przesuniecieX) * zoom
    val doY = (doWezla.y + doWezla.height / 2f - przesuniecieY) * zoom

    val sciezka = Path().apply {
        moveTo(odX, odY)
        val srodekX = (odX + doX) / 2f
        cubicTo(srodekX, odY, srodekX, doY, doX, doY)
    }
    drawPath(sciezka, kolor, style = Stroke(width = 2f * zoom, cap = StrokeCap.Round))
}

@Composable
private fun WezelNaPlanszy(
    wezel: MindNode,
    przesuniecieX: Float,
    przesuniecieY: Float,
    zoom: Float,
    wybrany: Boolean,
    edytowany: Boolean,
    maDzieci: Boolean,
    onWybierz: () -> Unit,
    onEdytuj: () -> Unit,
    onTekst: (String) -> Unit,
    onPoczatekPrzesuwania: () -> Unit,
    onPrzesun: (Float, Float) -> Unit,
    onKoniecPrzesuwania: () -> Unit,
    onZwin: () -> Unit,
) {
    val kolory = Kajet.colors
    val gestosc = LocalDensity.current
    val kolor = FolderColor.fromId(wezel.colorId).color(kolory.isDark)

    val lewo = (wezel.x - przesuniecieX) * zoom
    val gora = (wezel.y - przesuniecieY) * zoom
    val szerokosc = wezel.width * zoom
    val wysokosc = wezel.height * zoom

    Box(
        Modifier
            .offset { IntOffset(lewo.roundToInt(), gora.roundToInt()) }
            .size(
                width = with(gestosc) { szerokosc.toDp() },
                height = with(gestosc) { wysokosc.toDp() },
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    color = kolory.sheet,
                    shape = if (wezel.shape == NodeShape.OWAL) {
                        RoundedCornerShape(percent = 50)
                    } else {
                        RoundedCornerShape(Kajet.dimens.corner)
                    },
                )
                .border(
                    width = if (wybrany) 2.dp else 1.5.dp,
                    color = if (wybrany) kolory.accent else kolor,
                    shape = if (wezel.shape == NodeShape.OWAL) {
                        RoundedCornerShape(percent = 50)
                    } else {
                        RoundedCornerShape(Kajet.dimens.corner)
                    },
                )
                .pointerInput(wezel.id) {
                    detectTapGestures(
                        onTap = { onWybierz() },
                        onDoubleTap = { onEdytuj() },
                    )
                }
                .pointerInput(wezel.id) {
                    detectDragGestures(
                        onDragStart = { onPoczatekPrzesuwania() },
                        onDragEnd = { onKoniecPrzesuwania() },
                    ) { zmiana, przesuniecie ->
                        zmiana.consume()
                        onPrzesun(przesuniecie.x, przesuniecie.y)
                    }
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            val styl = Kajet.type.body.copy(
                fontSize = with(gestosc) { (14f * zoom).toSp() },
                color = kolory.text,
                textAlign = TextAlign.Center,
            )
            if (edytowany) {
                BasicTextField(
                    value = wezel.text,
                    onValueChange = onTekst,
                    textStyle = styl,
                    cursorBrush = SolidColor(kolory.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = wezel.text.ifEmpty { if (wezel.ink.isEmpty()) "Dotknij dwa razy, aby wpisać" else "" },
                    style = if (wezel.text.isEmpty()) styl.copy(color = kolory.muted) else styl,
                )
            }
        }

        if (maDzieci) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset { IntOffset((szerokosc / 2f).roundToInt() + 4, 0) }
                    .size(with(gestosc) { (22f * zoom).coerceIn(18f, 34f).toDp() })
                    .background(kolory.sheet, RoundedCornerShape(percent = 50))
                    .border(1.dp, kolor, RoundedCornerShape(percent = 50))
                    .clickable(onClickLabel = if (wezel.collapsed) "Rozwiń gałąź" else "Zwiń gałąź", onClick = onZwin),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (wezel.collapsed) KajetIcons.StrzalkaWPrawo else KajetIcons.StrzalkaWDol,
                    contentDescription = null,
                    tint = kolor,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun PanelWezla(
    wezel: MindNode,
    modifier: Modifier,
    onKsztalt: (NodeShape) -> Unit,
    onKolor: (String) -> Unit,
    onUsun: () -> Unit,
) {
    Column(
        modifier
            .background(Kajet.colors.sheet, RoundedCornerShape(Kajet.dimens.corner))
            .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EtykietaSekcji("Wybrany węzeł")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NodeShape.entries.forEach { ksztalt ->
                PrzyciskWtorny(
                    tekst = ksztalt.nazwaPl,
                    onClick = { onKsztalt(ksztalt) },
                    kolor = if (wezel.shape == ksztalt) Kajet.colors.accent else Kajet.colors.text,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FolderColor.entries.take(6).forEach { wariant ->
                Box(
                    Modifier
                        .size(32.dp)
                        .clickable(onClickLabel = "Kolor ${wariant.labelPl}") { onKolor(wariant.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(if (wezel.colorId == wariant.id) 22.dp else 16.dp)
                            .background(
                                wariant.color(Kajet.colors.isDark),
                                RoundedCornerShape(percent = 50),
                            ),
                    )
                }
            }
        }
        PrzyciskWtorny("Skasuj węzeł", onUsun, ikona = KajetIcons.Kosz, kolor = Kajet.colors.danger)
    }
}
