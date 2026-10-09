package wojtoteka.ovh.kajet.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import wojtoteka.ovh.kajet.cloud.SharedItem
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.SectionLabel
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.text.LocalStrings

/**
 * „Udostępnione mi" na górze biblioteki - cudze notatki i foldery przyjęte
 * przez to konto. Stoją obok własnych, z oznaczeniem „udostępnione"
 * i imieniem właściciela; przytrzymanie pozwala zdjąć je z listy.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SharedStrip(
    items: List<SharedItem>,
    onOpen: (SharedItem) -> Unit,
    onLeave: (SharedItem) -> Unit,
) {
    if (items.isEmpty()) return
    val words = LocalStrings.current
    val colors = Kajet.colors
    var asking by remember { mutableStateOf<SharedItem?>(null) }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            SectionLabel(words.sharedSection)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.shareId + it.token }) { item ->
                val title = item.folder?.name ?: item.note?.title?.ifBlank { words.untitled } ?: words.untitled
                val icon = when {
                    item.folder != null -> KajetIcons.Folder
                    item.note?.kind == "HANDWRITTEN" -> KajetIcons.HandwrittenNote
                    item.note?.kind == "MINDMAP" -> KajetIcons.MindMapIcon
                    item.note?.kind == "CODE" -> KajetIcons.CodeFile
                    else -> KajetIcons.TextNote
                }
                Row(
                    modifier = Modifier
                        .width(240.dp)
                        .border(1.dp, colors.line, RoundedCornerShape(Kajet.dimens.corner))
                        .background(colors.sheet, RoundedCornerShape(Kajet.dimens.corner))
                        .combinedClickable(
                            onClick = { onOpen(item) },
                            onLongClick = { asking = item },
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            style = Kajet.type.label,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${words.sharedBadge} · ${item.owner}",
                            style = Kajet.type.meta,
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    asking?.let { item ->
        KajetDialog(title = item.folder?.name ?: item.note?.title ?: words.sharedSection, onClose = { asking = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${words.sharedBadge} · ${item.owner}",
                    style = Kajet.type.body,
                    color = colors.muted,
                )
                wojtoteka.ovh.kajet.core.design.component.SecondaryButton(
                    words.sharedRemove,
                    {
                        onLeave(item)
                        asking = null
                    },
                    icon = KajetIcons.Close,
                    color = colors.danger,
                )
            }
        }
    }
}
