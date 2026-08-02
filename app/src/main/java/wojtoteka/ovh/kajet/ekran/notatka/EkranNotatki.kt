package wojtoteka.ovh.kajet.ekran.notatka

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import wojtoteka.ovh.kajet.core.design.InkPalette
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.PrzyciskWtorny
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.editor.mapa.EdytorMapy
import wojtoteka.ovh.kajet.editor.mapa.ModelMapy
import wojtoteka.ovh.kajet.editor.odreczny.EdytorOdreczny
import wojtoteka.ovh.kajet.editor.odreczny.ModelOdrecznego
import wojtoteka.ovh.kajet.editor.tekst.EdytorTekstowy
import wojtoteka.ovh.kajet.editor.tekst.ModelTekstowy
import wojtoteka.ovh.kajet.export.OknoEksportu
import wojtoteka.ovh.kajet.export.UslugaEksportu
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
    eksport: UslugaEksportu,
    palecRysuje: Boolean,
    onWstecz: () -> Unit,
) {
    var oknoEksportu by remember(sciezka) { mutableStateOf(false) }
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
                onEksport = { oknoEksportu = true },
                onRozpoznajPismo = { _, _ -> },
            )
            OknoEksportuNotatki(oknoEksportu, model.dokument, sciezka, eksport) { oknoEksportu = false }
        }

        rodzaj == NoteKind.TEKSTOWA -> {
            val model: ModelTekstowy = viewModel(
                key = "tekstowa-$sciezka",
                factory = ModelTekstowy.Fabryka(repo, ustawienia, sciezka),
            )
            val kontekst = LocalContext.current
            var plikAparatu by remember { mutableStateOf<java.io.File?>(null) }

            val zGalerii = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent(),
            ) { uri: Uri? ->
                if (uri != null) {
                    val dane = Zdjecia.wczytaj(kontekst, uri)
                    if (dane != null) model.wstawZdjecie(dane, Zdjecia.rozszerzenie(kontekst, uri), model.markdown.length)
                }
            }

            val zAparatu = rememberLauncherForActivityResult(
                ActivityResultContracts.TakePicture(),
            ) { udane: Boolean ->
                val plik = plikAparatu
                if (udane && plik != null && plik.exists()) {
                    model.wstawZdjecie(plik.readBytes(), "jpg", model.markdown.length)
                    plik.delete()
                }
                plikAparatu = null
            }

            EdytorTekstowy(
                model = model,
                onWstecz = onWstecz,
                onEksport = { oknoEksportu = true },
                onZdjecieZGalerii = { zGalerii.launch("image/*") },
                onZdjecieZAparatu = {
                    val (plik, uri) = Zdjecia.plikNaZdjecie(kontekst)
                    plikAparatu = plik
                    zAparatu.launch(uri)
                },
            )
            OknoEksportuNotatki(oknoEksportu, model.dokument, sciezka, eksport) { oknoEksportu = false }
        }

        rodzaj == NoteKind.MAPA -> {
            val model: ModelMapy = viewModel(
                key = "mapa-$sciezka",
                factory = ModelMapy.Fabryka(repo, ustawienia, sciezka),
            )
            EdytorMapy(model = model, onWstecz = onWstecz, onEksport = { oknoEksportu = true })
            OknoEksportuNotatki(oknoEksportu, model.dokument, sciezka, eksport) { oknoEksportu = false }
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

/** Okno eksportu pokazywane nad edytorem. Czeka, aż notatka będzie wczytana. */
@Composable
private fun OknoEksportuNotatki(
    widoczne: Boolean,
    dokument: kotlinx.coroutines.flow.StateFlow<wojtoteka.ovh.kajet.core.model.NoteDocument?>,
    sciezka: String,
    eksport: UslugaEksportu,
    onZamknij: () -> Unit,
) {
    if (!widoczne) return
    val tresc by dokument.collectAsStateWithLifecycle()
    val gotowa = tresc ?: return
    OknoEksportu(dokument = gotowa, sciezka = sciezka, usluga = eksport, onZamknij = onZamknij)
}
