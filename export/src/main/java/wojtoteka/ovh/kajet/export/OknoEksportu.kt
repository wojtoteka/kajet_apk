package wojtoteka.ovh.kajet.export

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.EtykietaSekcji
import wojtoteka.ovh.kajet.core.design.component.LiniaPozioma
import wojtoteka.ovh.kajet.core.design.component.PrzyciskGlowny
import wojtoteka.ovh.kajet.core.design.component.PrzyciskWtorny
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.NoteKind
import java.io.File

/**
 * Okno eksportu notatki.
 *
 * Przy każdym formacie piszemy wprost, co z notatki wyjdzie, a co zostanie
 * po drodze. Notatka odręczna zapisana jako dokument Word straci pismo,
 * i lepiej wiedzieć o tym przed zapisaniem niż po otwarciu pliku.
 */
@Composable
fun OknoEksportu(
    dokument: NoteDocument,
    sciezka: String,
    usluga: UslugaEksportu,
    onZamknij: () -> Unit,
) {
    val zakres = rememberCoroutineScope()
    var format by remember { mutableStateOf(FormatEksportu.PDF) }
    var gotowy by remember { mutableStateOf<File?>(null) }
    var pracuje by remember { mutableStateOf(false) }
    var blad by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onZamknij) {
        Column(
            Modifier
                .width(520.dp)
                .background(Kajet.colors.sheet, RoundedCornerShape(Kajet.dimens.corner))
                .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner)),
        ) {
            Text(
                text = "Zapisz notatkę do pliku",
                style = Kajet.type.title,
                color = Kajet.colors.text,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp),
            )
            LiniaPozioma()

            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                EtykietaSekcji("Format")
                FormatEksportu.entries.forEach { wariant ->
                    val ostrzezenie = ostrzezenie(dokument, wariant)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .background(
                                if (format == wariant) Kajet.colors.accentWash else Kajet.colors.sheet,
                                RoundedCornerShape(Kajet.dimens.corner),
                            )
                            .clickable { format = wariant; gotowy = null }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = KajetIcons.Eksport,
                            contentDescription = null,
                            tint = if (format == wariant) Kajet.colors.accent else Kajet.colors.muted,
                            modifier = Modifier.size(18.dp),
                        )
                        Column {
                            Text(wariant.nazwaPl, style = Kajet.type.body, color = Kajet.colors.text)
                            Text(wariant.opisPl, style = Kajet.type.meta, color = Kajet.colors.muted)
                            if (ostrzezenie != null) {
                                Text(ostrzezenie, style = Kajet.type.meta, color = Kajet.colors.danger)
                            }
                        }
                    }
                }

                if (blad != null) {
                    Text(blad.orEmpty(), style = Kajet.type.body, color = Kajet.colors.danger)
                }

                if (gotowy != null) {
                    Text(
                        text = "Plik jest gotowy: ${gotowy?.name}",
                        style = Kajet.type.body,
                        color = Kajet.colors.text,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val plik = gotowy
                    if (plik == null) {
                        PrzyciskGlowny(
                            tekst = if (pracuje) "Zapisuję..." else "Zapisz plik",
                            onClick = {
                                pracuje = true
                                blad = null
                                zakres.launch {
                                    try {
                                        gotowy = usluga.eksportuj(sciezka, dokument, format)
                                    } catch (e: Exception) {
                                        blad = e.message ?: "Zapis się nie udał. Spróbuj innego formatu."
                                    } finally {
                                        pracuje = false
                                    }
                                }
                            },
                            ikona = KajetIcons.Eksport,
                            wlaczony = !pracuje,
                        )
                    } else {
                        PrzyciskGlowny(
                            tekst = "Wyślij",
                            onClick = { usluga.udostepnij(plik, format.mime, dokument.title) },
                            ikona = KajetIcons.Udostepnij,
                        )
                        PrzyciskWtorny(
                            tekst = "Otwórz",
                            onClick = { usluga.otworz(plik, format.mime) },
                        )
                    }
                    PrzyciskWtorny(
                        tekst = "Drukuj",
                        onClick = { usluga.drukuj(dokument, sciezka) },
                        ikona = KajetIcons.Drukuj,
                    )
                    Box(Modifier.weight(1f))
                    PrzyciskWtorny("Zamknij", onZamknij)
                }
            }
        }
    }
}

private fun ostrzezenie(dokument: NoteDocument, format: FormatEksportu): String? = when {
    format == FormatEksportu.DOCX && dokument.kind == NoteKind.ODRECZNA ->
        "Pismo odręczne nie wejdzie do tego pliku."

    format == FormatEksportu.DOCX && dokument.text?.markdown?.contains("![") == true ->
        "Zdjęcia nie wejdą do tego pliku."

    format == FormatEksportu.PNG && dokument.kind != NoteKind.ODRECZNA ->
        "Ten format zapisuje tylko notatki odręczne."

    format == FormatEksportu.MARKDOWN && dokument.kind == NoteKind.ODRECZNA ->
        "Zapisze się tylko tekst, bez pisma."

    else -> null
}
