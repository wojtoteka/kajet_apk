package wojtoteka.ovh.kajet.editor.odreczny

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.PlexSans
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.NotePage
import wojtoteka.ovh.kajet.core.model.TextBoxElement
import wojtoteka.ovh.kajet.ink.PlotnoKresek
import kotlin.math.roundToInt

/**
 * Pola tekstowe leżące na kartce.
 *
 * Rysujemy je nad płótnem, a nie w nim, bo tekst ma się wpisywać
 * prawdziwą klawiaturą, z podpowiedziami i poprawianiem polskich znaków.
 * Pole przesuwasz za pasek u góry, a rozciągasz za róg.
 */
@Composable
fun PolaTekstoweNaStronie(
    strony: List<NotePage>,
    przesuniecieX: Float,
    przesuniecieY: Float,
    powiekszenie: Float,
    edytowane: String?,
    onEdytuj: (String?) -> Unit,
    onZmiana: (strona: Int, pole: TextBoxElement, doHistorii: Boolean) -> Unit,
    onUsun: (strona: Int, id: String) -> Unit,
) {
    var gora = 0f
    strony.forEachIndexed { indeks, kartka ->
        val gornaKrawedz = gora
        kartka.texts.forEach { pole ->
            PoleNaKartce(
                pole = pole,
                gornaKrawedzStrony = gornaKrawedz,
                przesuniecieX = przesuniecieX,
                przesuniecieY = przesuniecieY,
                powiekszenie = powiekszenie,
                edytowane = edytowane == pole.id,
                onEdytuj = { onEdytuj(if (edytowane == pole.id) null else pole.id) },
                onZmiana = { nowe, doHistorii -> onZmiana(indeks, nowe, doHistorii) },
                onUsun = { onUsun(indeks, pole.id) },
            )
        }
        gora += kartka.height + PlotnoKresek.ODSTEP_STRON
    }
}

@Composable
private fun PoleNaKartce(
    pole: TextBoxElement,
    gornaKrawedzStrony: Float,
    przesuniecieX: Float,
    przesuniecieY: Float,
    powiekszenie: Float,
    edytowane: Boolean,
    onEdytuj: () -> Unit,
    onZmiana: (TextBoxElement, Boolean) -> Unit,
    onUsun: () -> Unit,
) {
    val gestosc = LocalDensity.current
    val lewo = (pole.x - przesuniecieX) * powiekszenie
    val gora = (pole.y + gornaKrawedzStrony - przesuniecieY) * powiekszenie
    val szerokosc = pole.width * powiekszenie
    val wysokosc = pole.height * powiekszenie
    val fokus = remember { FocusRequester() }

    Box(
        Modifier
            .offset { IntOffset(lewo.roundToInt(), gora.roundToInt()) }
            .size(
                width = with(gestosc) { szerokosc.toDp() },
                height = with(gestosc) { wysokosc.toDp() },
            )
            .then(if (edytowane) Modifier.border(1.dp, Kajet.colors.accent) else Modifier)
            .pointerInput(pole.id) {
                detectTapGestures(onTap = { onEdytuj() })
            },
    ) {
            val styl = TextStyle(
                fontFamily = PlexSans,
                fontSize = with(gestosc) { (pole.fontSize * powiekszenie).toSp() },
                color = Color(pole.color),
                fontWeight = if (pole.bold) FontWeight.SemiBold else FontWeight.Normal,
                fontStyle = if (pole.italic) FontStyle.Italic else FontStyle.Normal,
            )

            if (edytowane) {
                BasicTextField(
                    value = pole.text,
                    onValueChange = { onZmiana(pole.copy(text = it), false) },
                    textStyle = styl,
                    cursorBrush = SolidColor(Kajet.colors.accent),
                    modifier = Modifier
                        .padding(4.dp)
                        .focusRequester(fokus),
                )
                LaunchedEffect(pole.id) { runCatching { fokus.requestFocus() } }
            } else if (pole.text.isNotEmpty()) {
                Text(text = pole.text, style = styl, modifier = Modifier.padding(4.dp))
            } else {
                Text(
                    text = "Pole tekstowe",
                    style = styl.copy(color = Kajet.colors.muted),
                    modifier = Modifier.padding(4.dp),
                )
            }

            if (edytowane) {
                // Pasek do przesuwania i przycisk kasowania nad polem.
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .background(Kajet.colors.accent),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .pointerInput(pole.id) {
                                detectDragGestures(
                                    onDragEnd = { onZmiana(pole, true) },
                                ) { zmiana, przesuniecie ->
                                    zmiana.consume()
                                    onZmiana(
                                        pole.copy(
                                            x = pole.x + przesuniecie.x / powiekszenie,
                                            y = pole.y + przesuniecie.y / powiekszenie,
                                        ),
                                        false,
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            KajetIcons.Przenies,
                            contentDescription = "Przesuń pole tekstowe",
                            tint = Kajet.colors.onAccent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Box(
                        Modifier
                            .size(28.dp)
                            .pointerInput(pole.id) {
                                detectTapGestures(onTap = { onUsun() })
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            KajetIcons.Kosz,
                            contentDescription = "Skasuj pole tekstowe",
                            tint = Kajet.colors.onAccent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                // Uchwyt do rozciągania w prawym dolnym rogu.
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                        .background(Kajet.colors.accent)
                        .pointerInput(pole.id) {
                            detectDragGestures(
                                onDragEnd = { onZmiana(pole, true) },
                            ) { zmiana, przesuniecie ->
                                zmiana.consume()
                                onZmiana(
                                    pole.copy(
                                        width = (pole.width + przesuniecie.x / powiekszenie).coerceAtLeast(60f),
                                        height = (pole.height + przesuniecie.y / powiekszenie).coerceAtLeast(28f),
                                    ),
                                    false,
                                )
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        KajetIcons.Dopasuj,
                        contentDescription = "Zmień wielkość pola",
                        tint = Kajet.colors.onAccent,
                        modifier = Modifier.size(14.dp),
                    )
                }
        }
    }
}
