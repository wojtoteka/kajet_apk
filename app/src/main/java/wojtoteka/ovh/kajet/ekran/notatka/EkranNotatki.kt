package wojtoteka.ovh.kajet.ekran.notatka

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import wojtoteka.ovh.kajet.core.design.InkPalette
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.PrzyciskWtorny
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.editor.odreczny.EdytorOdreczny
import wojtoteka.ovh.kajet.editor.odreczny.ModelOdrecznego
import wojtoteka.ovh.kajet.storage.MagazynUstawien
import wojtoteka.ovh.kajet.storage.RepozytoriumBiblioteki

/**
 * Otwiera notatkę we właściwym edytorze.
 *
 * Rodzaj notatki bierzemy ze spisu, więc wybór edytora nie wymaga
 * czytania całego pliku dwa razy.
 */
@Composable
fun EkranNotatki(
    sciezka: String,
    repo: RepozytoriumBiblioteki,
    ustawienia: MagazynUstawien,
    palecRysuje: Boolean,
    onWstecz: () -> Unit,
) {
    var rodzaj by remember(sciezka) { mutableStateOf<NoteKind?>(null) }
    var problem by remember(sciezka) { mutableStateOf<String?>(null) }

    LaunchedEffect(sciezka) {
        val znaleziony = runCatching { repo.rodzajNotatki(sciezka) }.getOrNull()
        if (znaleziony == null) {
            problem = "Nie udało się otworzyć notatki. Sprawdź, czy plik nadal jest w folderze."
        }
        rodzaj = znaleziony
    }

    val kolory = Kajet.colors

    when {
        problem != null -> Column(
            Modifier
                .fillMaxSize()
                .background(kolory.sheet)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Notatka się nie otworzyła", style = Kajet.type.title, color = kolory.text)
            Text(problem.orEmpty(), style = Kajet.type.body, color = kolory.muted)
            PrzyciskWtorny("Wróć do biblioteki", onWstecz, ikona = KajetIcons.Wstecz)
        }

        rodzaj == NoteKind.ODRECZNA -> {
            val model: ModelOdrecznego = viewModel(
                key = "odreczna-$sciezka",
                factory = ModelOdrecznego.Fabryka(
                    repo = repo,
                    ustawienia = ustawienia,
                    sciezka = sciezka,
                    kolorAtramentu = kolory.defaultInk.toArgb(),
                    kolorZakreslacza = InkPalette.ZakreslaczZolty.toArgb(),
                ),
            )
            EdytorOdreczny(
                model = model,
                palecRysuje = palecRysuje,
                onWstecz = onWstecz,
                onEksport = { },
                onRozpoznajPismo = { _, _ -> },
            )
        }

        rodzaj != null -> Column(
            Modifier
                .fillMaxSize()
                .background(kolory.sheet)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Ten rodzaj notatki nie ma jeszcze edytora", style = Kajet.type.title, color = kolory.text)
            Text(
                text = "Notatka jest bezpieczna na dysku. Edytor tego rodzaju powstaje w kolejnym etapie pracy.",
                style = Kajet.type.body,
                color = kolory.muted,
            )
            PrzyciskWtorny("Wróć do biblioteki", onWstecz, ikona = KajetIcons.Wstecz)
        }

        else -> Column(
            Modifier
                .fillMaxSize()
                .background(kolory.sheet)
                .padding(32.dp),
        ) {
            Text("Otwieram notatkę", style = Kajet.type.body, color = kolory.muted)
        }
    }
}
