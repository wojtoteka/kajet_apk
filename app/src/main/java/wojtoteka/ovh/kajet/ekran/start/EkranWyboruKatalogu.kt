package wojtoteka.ovh.kajet.ekran.start

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.EtykietaSekcji
import wojtoteka.ovh.kajet.core.design.component.PrzyciskGlowny
import wojtoteka.ovh.kajet.core.design.component.ZnakKajetu
import wojtoteka.ovh.kajet.core.design.component.liniaMarginesu
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons

/**
 * Pierwsze uruchomienie. Użytkownik wskazuje katalog, w którym mają leżeć notatki.
 *
 * To jest jedyny moment, w którym aplikacja o coś prosi, więc tłumaczy, po co.
 * Ekran jest zbudowany inaczej niż reszta: szeroki margines po lewej i jedna
 * kolumna tekstu, tak jak pierwsza strona zeszytu.
 */
@Composable
fun EkranWyboruKatalogu(
    onWybrano: (Uri) -> Unit,
) {
    val wybor = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> if (uri != null) onWybrano(uri) }

    Row(
        Modifier
            .fillMaxSize()
            .background(Kajet.colors.desk),
    ) {
        Box(
            Modifier
                .width(Kajet.dimens.railWidth)
                .fillMaxHeight()
                .liniaMarginesu(Kajet.colors.line),
            contentAlignment = Alignment.TopCenter,
        ) {
            ZnakKajetu(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .size(28.dp),
                kolor = Kajet.colors.accent,
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(Kajet.colors.sheet)
                .verticalScroll(rememberScrollState())
                .padding(start = 40.dp, end = 32.dp, top = 56.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            EtykietaSekcji("Pierwsze uruchomienie")

            Text(
                text = "Gdzie mam trzymać Twoje notatki?",
                style = Kajet.type.display,
                color = Kajet.colors.text,
                modifier = Modifier.widthIn(max = 560.dp),
            )

            Text(
                text = "Wskaż folder na tablecie. Kajet będzie w nim zapisywał wszystko, " +
                    "co napiszesz. Każdy folder z aplikacji to zwykły katalog na dysku, " +
                    "a każda notatka to katalog z plikiem w środku.",
                style = Kajet.type.bodyLarge,
                color = Kajet.colors.text,
                modifier = Modifier.widthIn(max = Kajet.dimens.readingWidth),
            )

            Text(
                text = "Dzięki temu notatki zostaną na tablecie nawet wtedy, gdy odinstalujesz " +
                    "Kajet. Możesz je też skopiować na komputer albo otworzyć w innej aplikacji. " +
                    "Najlepszym miejscem jest folder Dokumenty.",
                style = Kajet.type.body,
                color = Kajet.colors.muted,
                modifier = Modifier.widthIn(max = Kajet.dimens.readingWidth),
            )

            Box(Modifier.height(8.dp))

            PrzyciskGlowny(
                tekst = "Wskaż folder na notatki",
                onClick = { wybor.launch(podpowiedzDokumenty()) },
                ikona = KajetIcons.Folder,
            )

            Text(
                text = "Otworzy się okno systemu Android. Wybierz folder i naciśnij " +
                    "przycisk potwierdzenia. Możesz zmienić to później w ustawieniach.",
                style = Kajet.type.meta,
                color = Kajet.colors.muted,
                modifier = Modifier.widthIn(max = Kajet.dimens.readingWidth),
            )
        }
    }
}

/**
 * Podpowiedź dla systemowego okna wyboru: zacznij od katalogu Dokumenty.
 * Na starszych wersjach Androida podpowiedzi nie da się przekazać i okno
 * otworzy się tam, gdzie ostatnio.
 */
fun podpowiedzDokumenty(): Uri? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    val identyfikator = "primary:" + Environment.DIRECTORY_DOCUMENTS
    return DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", identyfikator)
}

/** Prośba o wskazanie katalogu, wywoływana też z ustawień. */
fun zamiarWyboruKatalogu(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
    addFlags(
        Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
    )
}
