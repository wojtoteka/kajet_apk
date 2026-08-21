package wojtoteka.ovh.kajet.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import wojtoteka.ovh.kajet.cloud.KajetLinks
import wojtoteka.ovh.kajet.cloud.LinkOutcome
import wojtoteka.ovh.kajet.cloud.UpdateCheck
import wojtoteka.ovh.kajet.cloud.humanSize
import wojtoteka.ovh.kajet.cloud.openLink
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.PrimaryButton
import wojtoteka.ovh.kajet.core.design.component.SecondaryButton
import wojtoteka.ovh.kajet.core.design.component.SectionLabel
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.core.text.Strings
import wojtoteka.ovh.kajet.core.text.releaseFacts
import wojtoteka.ovh.kajet.core.text.updateOnThisDevice
import wojtoteka.ovh.kajet.ui.library.KajetDialog

/**
 * Komunikat o nowej wersji Kajetu.
 *
 * Wisi nad całą nawigacją, więc pokazuje się niezależnie od tego, na którym
 * ekranie zaczyna się praca - także przy pierwszym uruchomieniu, gdy stoi się
 * jeszcze na wyborze katalogu.
 *
 * Sprawdzenie idzie w tle i nic nie wstrzymuje: dopóki nie ma odpowiedzi,
 * ekran jest zwyczajny. Gdy odpowiedzi nie będzie w ogóle (brak sieci, martwy
 * serwer), nie dzieje się nic i nikt się o tym nie dowiaduje - to funkcja
 * poboczna, nie powód do straszenia człowieka błędem.
 *
 * Zamknięcie na „Później", przyciskiem wstecz i dotknięciem obok znaczy to samo:
 * komunikat schodzi i wraca dopiero przy następnym uruchomieniu, dopóki wersja
 * się nie zmieni.
 *
 * Ułożenie jest to samo co na stronie /download: nadpis, wielki numer wersji,
 * a pod nim jednym wierszem system, wielkość pliku i data. Numer wersji jest
 * tym, po co człowiek tu patrzy, więc nie ma prawa siedzieć w środku zdania.
 */
@Composable
fun UpdateNotice() {
    val context = LocalContext.current
    val words = LocalStrings.current

    var release by remember { mutableStateOf<UpdateCheck.Release?>(null) }
    var linkProblem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Wewnątrz siedzi withContext(Dispatchers.IO) i runCatching na wszystkim,
        // więc ani to nie blokuje rysowania, ani nie ma jak stąd wylecieć wyjątek.
        release = UpdateCheck.newReleaseToAnnounce(context)
    }

    val newest = release ?: return

    // Zamknięcie na „Później", wstecz i dotknięciem obok to jedno i to samo.
    fun close() {
        UpdateCheck.stopAnnouncing()
        release = null
    }

    KajetDialog(words.updateTitle, onClose = ::close, width = 520) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Kajet.colors.accentWash, RoundedCornerShape(Kajet.dimens.corner))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SectionLabel(words.updateToDownload, color = Kajet.colors.accent)
            Text(
                text = newest.version,
                style = Kajet.type.display,
                color = Kajet.colors.accent,
            )
            Text(
                text = words.releaseFacts(
                    size = if (newest.sizeBytes > 0) humanSize(newest.sizeBytes) else "",
                    date = readableDate(newest.releaseDate, words),
                ),
                style = Kajet.type.meta,
                color = Kajet.colors.muted,
            )
        }

        Text(
            text = words.updateOnThisDevice(
                UpdateCheck.installedVersion(context).ifBlank { "?" },
            ),
            style = Kajet.type.body,
            color = Kajet.colors.text,
        )

        // Opis zmian tylko wtedy, gdy serwer go podał. Pusty nagłówek nad
        // niczym wygląda jak usterka.
        newest.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel(words.updateWhatChanged)
                Text(
                    text = notes.trim(),
                    style = Kajet.type.body,
                    color = Kajet.colors.muted,
                )
            }
        }

        linkProblem?.let { problem ->
            Text(text = problem, style = Kajet.type.meta, color = Kajet.colors.danger)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                text = words.updateInstall,
                onClick = {
                    // Adres z odpowiedzi serwera. Stały adres w kodzie zostaje
                    // wyłącznie na wypadek starszego serwera, który go nie poda.
                    val page = newest.pageUrl.ifBlank { KajetLinks.download() }
                    linkProblem = when (openLink(context, page)) {
                        LinkOutcome.OPENED -> {
                            close()
                            null
                        }

                        // Strona się nie otworzyła, więc komunikat ZOSTAJE -
                        // inaczej człowiek zostałby z niczym i bez wyjaśnienia.
                        LinkOutcome.NO_NETWORK -> words.documentNoNetwork
                        LinkOutcome.NO_BROWSER -> words.documentNoBrowser
                    }
                },
            )
            SecondaryButton(words.updateLater, ::close)
        }
    }
}

/**
 * Data wystawienia po ludzku: „2 sierpnia 2026" zamiast „2026-08-02".
 *
 * Starszy serwer daty nie podaje, a nieczytelnej nie ma po co pokazywać -
 * w obu przypadkach wypada z wiersza razem ze swoim oddzielaczem.
 */
private fun readableDate(iso: String?, words: Strings): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        val locale = Locale.forLanguageTag(if (words.english) "en" else "pl")
        LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
    }.getOrDefault("")
}
