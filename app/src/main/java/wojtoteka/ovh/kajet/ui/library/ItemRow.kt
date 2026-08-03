package wojtoteka.ovh.kajet.ui.library

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
import wojtoteka.ovh.kajet.core.design.component.IconAction
import wojtoteka.ovh.kajet.core.design.component.StrokePreview
import wojtoteka.ovh.kajet.core.design.icon.LanguageIcons
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.ItemType
import wojtoteka.ovh.kajet.core.model.LibraryItem
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.storage.LibraryRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ItemRow(
    item: LibraryItem,
    repo: LibraryRepository,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    onFavourite: (() -> Unit)? = null,
) {
    val colors = Kajet.colors
    val height = when {
        item.type == ItemType.FOLDER -> 60.dp
        item.noteKind == NoteKind.HANDWRITTEN -> 108.dp
        item.type == ItemType.NOTE -> 86.dp
        else -> 56.dp
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .combinedClickable(onClick = onOpen, onLongClick = onMenu)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowMark(item)

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = item.name,
                style = if (item.type == ItemType.FOLDER) Kajet.type.titleSmall else Kajet.type.body,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            when {
                item.type == ItemType.FOLDER -> {
                    Text(
                        text = folderSummary(item.childCount),
                        style = Kajet.type.meta,
                        color = colors.muted,
                    )
                }

                item.noteKind == NoteKind.HANDWRITTEN -> {
                    HandwritingThumbnail(item, repo)
                    Text(
                        text = relativeTime(item.updatedAt),
                        style = Kajet.type.meta,
                        color = colors.muted,
                    )
                }

                item.type == ItemType.NOTE -> {
                    Text(
                        text = item.preview ?: "Pusta notatka",
                        style = Kajet.type.meta,
                        color = colors.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = relativeTime(item.updatedAt),
                        style = Kajet.type.meta,
                        color = colors.muted,
                    )
                }

                item.language != null -> {
                    Text(
                        text = item.language!!.labelPl + if (item.language!!.offline) {
                            ", działa bez internetu"
                        } else {
                            ", uruchamiany przez internet"
                        },
                        style = Kajet.type.meta,
                        color = colors.muted,
                    )
                }
            }
        }

        // Gwiazdka jest osobnym przyciskiem, a nie samą ikoną. Wcześniej
        // dotknięcie jej otwierało notatkę, bo cały wiersz był klikalny.
        if (onFavourite != null && item.type == ItemType.NOTE) {
            IconAction(
                icon = KajetIcons.Favourites,
                description = if (item.favorite) {
                    "Usuń ${item.name} z ulubionych"
                } else {
                    "Dodaj ${item.name} do ulubionych"
                },
                onClick = onFavourite,
                selected = item.favorite,
                iconSize = 18.dp,
                touchTarget = 44.dp,
            )
        } else if (item.favorite) {
            Icon(
                imageVector = KajetIcons.Favourites,
                contentDescription = "W ulubionych",
                tint = colors.accent,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(16.dp),
            )
        }

        IconAction(
            icon = KajetIcons.MoreDots,
            description = "Działania dla ${item.name}",
            onClick = onMenu,
            iconSize = 18.dp,
        )
    }
}

@Composable
private fun RowMark(item: LibraryItem) {
    val colors = Kajet.colors
    val folderColor = FolderColor.fromId(item.colorId).color(colors.isDark)

    Box(
        modifier = Modifier
            .width(48.dp)
            .height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (item.type) {
            ItemType.FOLDER -> Icon(
                imageVector = KajetIcons.folderIcon(item.iconId),
                contentDescription = null,
                tint = folderColor,
                modifier = Modifier.size(22.dp),
            )

            ItemType.NOTE -> Icon(
                imageVector = when (item.noteKind) {
                    NoteKind.HANDWRITTEN -> KajetIcons.HandwrittenNote
                    NoteKind.MINDMAP -> KajetIcons.MindMapIcon
                    else -> KajetIcons.TextNote
                },
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(20.dp),
            )

            ItemType.CODE_FILE -> Icon(
                imageVector = LanguageIcons.forLanguage(item.language),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(21.dp),
            )

            ItemType.OTHER_FILE -> Icon(
                imageVector = KajetIcons.TextNote,
                contentDescription = null,
                tint = colors.muted.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun HandwritingThumbnail(item: LibraryItem, repo: LibraryRepository) {
    var strokes by remember(item.documentUri, item.updatedAt) { mutableStateOf<List<InkStroke>>(emptyList()) }

    LaunchedEffect(item.documentUri, item.updatedAt) {
        strokes = runCatching {
            val document = repo.readNote(item.path)
            document.handwriting?.pages?.firstOrNull()?.strokes.orEmpty()
        }.getOrDefault(emptyList())
    }

    if (strokes.isEmpty()) {
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
            StrokePreview(
                strokes = strokes,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                color = Kajet.colors.muted,
            )
        }
    }
}

private fun folderSummary(count: Int): String = when (count) {
    0 -> "Pusty folder"
    1 -> "1 wpis"
    in 2..4 -> "$count wpisy"
    else -> "$count wpisów"
}

private val dateFormat = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("pl", "PL"))

fun relativeTime(millis: Long): String {
    if (millis <= 0L) return "Bez daty"
    val now = System.currentTimeMillis()
    val elapsed = now - millis
    return when {
        elapsed < 60_000 -> "Przed chwilą"
        elapsed < 3_600_000 -> "${elapsed / 60_000} min temu"
        elapsed < 86_400_000 -> "${elapsed / 3_600_000} godz. temu"
        else -> dateFormat.format(Date(millis))
    }
}
