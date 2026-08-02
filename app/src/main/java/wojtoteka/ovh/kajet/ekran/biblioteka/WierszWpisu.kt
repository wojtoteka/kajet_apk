package wojtoteka.ovh.kajet.ekran.biblioteka

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import wojtoteka.ovh.kajet.core.design.FolderColor
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.IkonaPrzycisk
import wojtoteka.ovh.kajet.core.design.component.PodgladKresek
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.ItemType
import wojtoteka.ovh.kajet.core.model.LibraryItem
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.storage.RepozytoriumBiblioteki
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Wiersz listy. Każdy rodzaj wpisu ma inną wysokość i inną treść,
 * bo folder, notatka odręczna i plik z kodem to trzy różne rzeczy.
 */
@Composable
fun WierszWpisu(
    wpis: LibraryItem,
    repo: RepozytoriumBiblioteki,
    onOtworz: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kolory = Kajet.colors
    val wysokosc = when {
        wpis.type == ItemType.FOLDER -> 60.dp
        wpis.noteKind == NoteKind.ODRECZNA -> 108.dp
        wpis.type == ItemType.NOTATKA -> 86.dp
        else -> 56.dp
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = wysokosc)
            .combinedClickable(onClick = onOtworz, onLongClick = onMenu)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZnakWiersza(wpis)

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = wpis.name,
                style = if (wpis.type == ItemType.FOLDER) Kajet.type.titleSmall else Kajet.type.body,
                color = kolory.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            when {
                wpis.type == ItemType.FOLDER -> {
                    Text(
                        text = opisFolderu(wpis.childCount),
                        style = Kajet.type.meta,
                        color = kolory.muted,
                    )
                }

                wpis.noteKind == NoteKind.ODRECZNA -> {
                    MiniaturaPisma(wpis, repo)
                    Text(
                        text = kiedy(wpis.updatedAt),
                        style = Kajet.type.meta,
                        color = kolory.muted,
                    )
                }

                wpis.type == ItemType.NOTATKA -> {
                    Text(
                        text = wpis.preview ?: "Pusta notatka",
                        style = Kajet.type.meta,
                        color = kolory.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = kiedy(wpis.updatedAt),
                        style = Kajet.type.meta,
                        color = kolory.muted,
                    )
                }

                wpis.language != null -> {
                    Text(
                        text = wpis.language!!.labelPl + if (wpis.language!!.offline) {
                            ", działa bez internetu"
                        } else {
                            ", uruchamiany przez internet"
                        },
                        style = Kajet.type.meta,
                        color = kolory.muted,
                    )
                }
            }
        }

        if (wpis.favorite) {
            Icon(
                imageVector = KajetIcons.Ulubione,
                contentDescription = "W ulubionych",
                tint = kolory.accent,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(16.dp),
            )
        }

        IkonaPrzycisk(
            ikona = KajetIcons.Wiecej,
            opis = "Działania dla ${wpis.name}",
            onClick = onMenu,
            rozmiarIkony = 18.dp,
        )
    }
}

/**
 * Lewa krawędź wiersza. Dla folderu niesie jego kolor, dla notatki rodzaj,
 * dla pliku z kodem język. To ta sama zasada, co linia marginesu.
 */
@Composable
private fun ZnakWiersza(wpis: LibraryItem) {
    val kolory = Kajet.colors
    val kolorFolderu = FolderColor.fromId(wpis.colorId).color(kolory.isDark)

    Box(
        modifier = Modifier
            .width(48.dp)
            .height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (wpis.type) {
            ItemType.FOLDER -> Icon(
                imageVector = KajetIcons.folderIcon(wpis.iconId),
                contentDescription = null,
                tint = kolorFolderu,
                modifier = Modifier.size(22.dp),
            )

            ItemType.NOTATKA -> Icon(
                imageVector = when (wpis.noteKind) {
                    NoteKind.ODRECZNA -> KajetIcons.NotatkaOdreczna
                    NoteKind.MAPA -> KajetIcons.MapaMysli
                    else -> KajetIcons.NotatkaTekstowa
                },
                contentDescription = null,
                tint = kolory.muted,
                modifier = Modifier.size(20.dp),
            )

            ItemType.PLIK_KODU -> Text(
                text = skrotJezyka(wpis),
                style = Kajet.type.eyebrow,
                color = kolory.accent,
            )

            ItemType.INNY_PLIK -> Icon(
                imageVector = KajetIcons.NotatkaTekstowa,
                contentDescription = null,
                tint = kolory.muted.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Pasek z pierwszymi kreskami notatki. Wczytujemy je dopiero wtedy,
 * kiedy wiersz pojawi się na ekranie, żeby lista otwierała się od razu.
 */
@Composable
private fun MiniaturaPisma(wpis: LibraryItem, repo: RepozytoriumBiblioteki) {
    var kreski by remember(wpis.documentUri, wpis.updatedAt) { mutableStateOf<List<InkStroke>>(emptyList()) }

    LaunchedEffect(wpis.documentUri, wpis.updatedAt) {
        kreski = runCatching {
            val dokument = repo.czytajNotatke(wpis.path)
            dokument.handwriting?.pages?.firstOrNull()?.strokes.orEmpty()
        }.getOrDefault(emptyList())
    }

    if (kreski.isEmpty()) {
        Text(
            text = "Pusta strona",
            style = Kajet.type.meta,
            color = Kajet.colors.muted,
        )
        Spacer(Modifier.height(2.dp))
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .background(Kajet.colors.desk),
        ) {
            PodgladKresek(
                kreski = kreski,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                kolor = Kajet.colors.muted,
            )
        }
    }
}

private fun skrotJezyka(wpis: LibraryItem): String =
    wpis.name.substringAfterLast('.', "").uppercase().take(4)

private fun opisFolderu(ile: Int): String = when (ile) {
    0 -> "Pusty folder"
    1 -> "1 wpis"
    in 2..4 -> "$ile wpisy"
    else -> "$ile wpisów"
}

private val formatDaty = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("pl", "PL"))

fun kiedy(czas: Long): String {
    if (czas <= 0L) return "Bez daty"
    val teraz = System.currentTimeMillis()
    val roznica = teraz - czas
    return when {
        roznica < 60_000 -> "Przed chwilą"
        roznica < 3_600_000 -> "${roznica / 60_000} min temu"
        roznica < 86_400_000 -> "${roznica / 3_600_000} godz. temu"
        else -> formatDaty.format(Date(czas))
    }
}
