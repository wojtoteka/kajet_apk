package wojtoteka.ovh.kajet.editor.tekst

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import wojtoteka.ovh.kajet.core.design.InkPalette
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.EtykietaSekcji
import wojtoteka.ovh.kajet.core.design.component.IkonaPrzycisk
import wojtoteka.ovh.kajet.core.design.component.LiniaPozioma
import wojtoteka.ovh.kajet.core.design.component.PrzyciskGlowny
import wojtoteka.ovh.kajet.core.design.component.PrzyciskWtorny
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.ink.Narzedzie
import wojtoteka.ovh.kajet.ink.PlotnoKresek
import wojtoteka.ovh.kajet.ink.SluchaczPlotna
import wojtoteka.ovh.kajet.ink.StronaNaEkranie
import wojtoteka.ovh.kajet.ink.UstawieniaPisaka

/** Wielkość wstawki rysunkowej w jednostkach strony. */
private const val SZEROKOSC_RYSUNKU = 560f
private const val WYSOKOSC_RYSUNKU = 300f

/**
 * Mały rysunek wstawiany w środek notatki tekstowej.
 *
 * Rysujesz palcem albo rysikiem na kawałku gładkiej kartki.
 * Po zapisaniu rysunek trafia do notatki jako obrazek, a kreski
 * zostają obok, więc da się go później poprawić.
 */
@Composable
fun OknoRysunku(
    onZamknij: () -> Unit,
    onGotowe: (kreski: List<InkStroke>, szerokosc: Float, wysokosc: Float) -> Unit,
) {
    val kolory = Kajet.colors
    var kreski by remember { mutableStateOf<List<InkStroke>>(emptyList()) }
    var narzedzie by remember { mutableStateOf(Narzedzie.PIORO) }
    var kolor by remember { mutableStateOf(kolory.defaultInk.toArgb()) }
    var grubosc by remember { mutableStateOf(2.4f) }

    Dialog(onDismissRequest = onZamknij) {
        Column(
            Modifier
                .width(640.dp)
                .background(kolory.sheet, RoundedCornerShape(Kajet.dimens.corner))
                .border(1.dp, kolory.line, RoundedCornerShape(Kajet.dimens.corner)),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Rysunek w notatce", style = Kajet.type.title, color = kolory.text, modifier = Modifier.weight(1f))
                IkonaPrzycisk(KajetIcons.Zamknij, "Zamknij bez zapisywania", onZamknij)
            }
            LiniaPozioma()

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(kolory.desk)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IkonaPrzycisk(
                    KajetIcons.Pioro,
                    "Pióro",
                    { narzedzie = Narzedzie.PIORO },
                    wybrany = narzedzie == Narzedzie.PIORO,
                )
                IkonaPrzycisk(
                    KajetIcons.GumkaKreska,
                    "Gumka do całej kreski",
                    { narzedzie = Narzedzie.GUMKA_KRESKA },
                    wybrany = narzedzie == Narzedzie.GUMKA_KRESKA,
                )
                Box(Modifier.width(12.dp))
                InkPalette.pisak.take(4).forEach { (nazwa, wariant) ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .clickable(onClickLabel = "Kolor $nazwa") { kolor = wariant.toArgb() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(if (kolor == wariant.toArgb()) 24.dp else 18.dp)
                                .background(wariant, CircleShape)
                                .border(1.dp, kolory.line, CircleShape),
                        )
                    }
                }
                Box(Modifier.width(12.dp))
                listOf(1.6f, 2.4f, 4f, 7f).forEach { wariant ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(
                                if (grubosc == wariant) kolory.accentWash else Color.Transparent,
                                RoundedCornerShape(Kajet.dimens.corner),
                            )
                            .clickable(onClickLabel = "Grubość kreski") { grubosc = wariant },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size((wariant * 2.6f).dp)
                                .background(Color(kolor), CircleShape),
                        )
                    }
                }
            }
            LiniaPozioma()

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(kolory.sheet),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    factory = { kontekst ->
                        PlotnoKresek(kontekst).also { widok ->
                            widok.sluchacz = object : SluchaczPlotna {
                                override fun kreskaSkonczona(strona: Int, kreska: InkStroke) {
                                    kreski = kreski + kreska
                                }

                                override fun gumkaPrzeszla(
                                    strona: Int,
                                    x: Float,
                                    y: Float,
                                    promien: Float,
                                    calaKreska: Boolean,
                                ) {
                                    kreski = kreski.filterNot {
                                        wojtoteka.ovh.kajet.ink.Kresy.dotykaKola(it, x, y, promien)
                                    }
                                }

                                override fun lassoSkonczone(strona: Int, wielokat: List<Float>) = Unit
                                override fun zaznaczeniePrzesuniete(dx: Float, dy: Float, koniec: Boolean) = Unit
                                override fun widokZmieniony(x: Float, y: Float, skala: Float) = Unit
                                override fun dotknietoPustego() = Unit
                            }
                        }
                    },
                    update = { widok ->
                        widok.strony = listOf(
                            StronaNaEkranie(
                                indeks = 0,
                                szerokosc = SZEROKOSC_RYSUNKU,
                                wysokosc = WYSOKOSC_RYSUNKU,
                                tlo = PageBackground.GLADKIE,
                                kreski = kreski,
                            ),
                        )
                        widok.narzedzie = narzedzie
                        widok.ustawienia = UstawieniaPisaka(
                            kolorPiora = kolor,
                            gruboscPiora = grubosc,
                            kolorZakreslacza = InkPalette.ZakreslaczZolty.toArgb(),
                        )
                        widok.palecRysuje = true
                        widok.kolorPapieru = kolory.sheet.toArgb()
                        widok.kolorLinii = kolory.pageRule.toArgb()
                        widok.kolorBiurka = kolory.sheet.toArgb()
                        widok.kolorZaznaczenia = kolory.accent.toArgb()
                    },
                )
            }
            LiniaPozioma()

            Row(
                Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PrzyciskGlowny(
                    tekst = "Wstaw rysunek",
                    onClick = { onGotowe(kreski, SZEROKOSC_RYSUNKU, WYSOKOSC_RYSUNKU) },
                    ikona = KajetIcons.Zatwierdz,
                    wlaczony = kreski.isNotEmpty(),
                )
                PrzyciskWtorny("Wyczyść", { kreski = emptyList() })
                Box(Modifier.weight(1f))
                EtykietaSekcji("Rysuj palcem albo rysikiem")
            }
        }
    }
}
