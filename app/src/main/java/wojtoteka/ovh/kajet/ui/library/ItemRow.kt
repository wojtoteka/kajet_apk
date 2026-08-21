package wojtoteka.ovh.kajet.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
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
import wojtoteka.ovh.kajet.ink.ShapeGeometry
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.core.text.Strings
import wojtoteka.ovh.kajet.core.text.folderSummary
import wojtoteka.ovh.kajet.core.text.hoursAgo
import wojtoteka.ovh.kajet.core.text.minutesAgo
import wojtoteka.ovh.kajet.core.text.actionsFor
import wojtoteka.ovh.kajet.core.text.starNote
import wojtoteka.ovh.kajet.core.text.unstarNote
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
    /** Długie przytrzymanie. Bez tego robi to samo, co trzy kropki. */
    onLongPress: (() -> Unit)? = null,
    onFavourite: (() -> Unit)? = null,
    /** Wysyłka tej notatki wyczerpała próby - patrz [StuckNotes]. */
    stuck: Boolean = false,
    /*
      Czy ten wiersz jest zaznaczony - albo null, kiedy nikt niczego nie
      zaznacza.

      W trybie zaznaczania gwiazdka i trzy kropki schodzą z wiersza. Gdyby
      zostały, dotknięcie skrajem palca robiłoby coś zupełnie innego, niż
      wygląda - a wiersz w tym trybie ma znaczyć jedno: zaznacz albo odznacz.
    */
    selected: Boolean? = null,
) {
    val words = LocalStrings.current
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
            .background(if (selected == true) colors.accentWash else Color.Transparent)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress ?: onMenu)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected == null) RowMark(item) else SelectionMark(selected)

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
                        text = words.folderSummary(item.childCount),
                        style = Kajet.type.meta,
                        color = colors.muted,
                    )
                }

                item.noteKind == NoteKind.HANDWRITTEN -> {
                    HandwritingThumbnail(item, repo)
                    Text(
                        text = relativeTime(item.updatedAt, words),
                        style = Kajet.type.meta,
                        color = colors.muted,
                    )
                }

                item.type == ItemType.NOTE -> {
                    Text(
                        text = item.preview ?: words.emptyNote,
                        style = Kajet.type.meta,
                        color = colors.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = relativeTime(item.updatedAt, words),
                        style = Kajet.type.meta,
                        color = colors.muted,
                    )
                }

                /*
                  Plik z kodem: język i data ostatniej zmiany w jednym wierszu.
                  Data stoi przy każdej notatce, a przy plikach jej brakowało.
                  Było tam za to zdanie o tym, że HTML ma podgląd strony albo
                  że program działa bez internetu - spis notatek nie jest od
                  takich objaśnień. To samo, i dokładniej, mówi nagłówek
                  otwartego pliku.
                */
                item.language != null -> {
                    Text(
                        text = "${item.language!!.label(words)} · " +
                            relativeTime(item.updatedAt, words),
                        style = Kajet.type.meta,
                        color = colors.muted,
                    )
                }

                // Zdjęcie, PDF, cokolwiek innego wrzuconego do biblioteki.
                // Nazwa mówi wszystko, więc zostaje sama data.
                else -> {
                    Text(
                        text = relativeTime(item.updatedAt, words),
                        style = Kajet.type.meta,
                        color = colors.muted,
                    )
                }
            }

            // Drobna linijka, nie ostrzeżenie na czerwono: notatka działa
            // dalej, tyle że jej zmiany nie doszły na serwer. Po zbiorczy
            // sygnał i przycisk ponowienia - pasek nad spisem.
            if (stuck) NotUploadedTag()
        }

        // Gwiazdka jest osobnym przyciskiem, a nie samą ikoną. Wcześniej
        // dotknięcie jej otwierało notatkę, bo cały wiersz był klikalny.
        //
        // Plik z kodem dostaje ją tak samo jak notatka - na stronie zawsze ją
        // miał, a w aplikacji nie było czym oznaczyć ani pliku HTML, ani
        // Pythona. Folder zostaje bez gwiazdki: ulubione są o treści.
        if (selected == null) {
            if (onFavourite != null && item.type != ItemType.FOLDER) {
                IconAction(
                    icon = KajetIcons.Favourites,
                    description = if (item.favorite) {
                        words.unstarNote(item.name)
                    } else {
                        words.starNote(item.name)
                    },
                    onClick = onFavourite,
                    selected = item.favorite,
                    iconSize = 18.dp,
                    touchTarget = 44.dp,
                )
            } else if (item.favorite) {
                Icon(
                    imageVector = KajetIcons.Favourites,
                    contentDescription = words.inFavorites,
                    tint = colors.accent,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(16.dp),
                )
            }

            IconAction(
                icon = KajetIcons.MoreDots,
                description = words.actionsFor(item.name),
                onClick = onMenu,
                iconSize = 18.dp,
            )
        }
    }
}

/**
 * Znacznik zaznaczenia w miejscu ikony rodzaju wpisu.
 *
 * Stoi tam, gdzie ikona, a nie obok niej: wiersz na telefonie nie ma
 * szerokości do oddania, a przez chwilę zaznaczania i tak liczy się tylko to,
 * co jest zaznaczone.
 */
@Composable
private fun SelectionMark(selected: Boolean) {
    val colors = Kajet.colors
    val words = LocalStrings.current
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(
                    color = if (selected) colors.accent else Color.Transparent,
                    shape = CircleShape,
                )
                .border(
                    width = if (selected) 0.dp else 2.dp,
                    color = colors.muted,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = KajetIcons.Confirm,
                    contentDescription = words.selectMany,
                    tint = colors.sheet,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun NotUploadedTag() {
    val words = LocalStrings.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = KajetIcons.Offline,
            // Opis dla czytnika ekranu niesie całe zdanie - samo „nie wysłano"
            // wyrwane z wiersza nie mówi, o co chodzi.
            contentDescription = words.notUploadedAbout,
            tint = Kajet.colors.muted,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = words.notUploadedTag,
            style = Kajet.type.meta,
            color = Kajet.colors.muted,
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
    val words = LocalStrings.current
    var strokes by remember(item.documentUri, item.updatedAt) { mutableStateOf<List<InkStroke>>(emptyList()) }
    // Kształty idą do podglądu jako gotowe łamane - miniatura nie zna ich geometrii.
    var outlines by remember(item.documentUri, item.updatedAt) { mutableStateOf<List<FloatArray>>(emptyList()) }

    LaunchedEffect(item.documentUri, item.updatedAt) {
        val page = runCatching {
            repo.readNote(item.path).handwriting?.pages?.firstOrNull()
        }.getOrNull()
        strokes = page?.strokes.orEmpty()
        outlines = page?.shapes.orEmpty().map { shape ->
            val points = ShapeGeometry.points(shape)
            // Figura zamknięta wraca do pierwszego punktu, żeby obrys się domykał.
            if (shape.kind.open) points else points + floatArrayOf(points[0], points[1])
        }
    }

    if (strokes.isEmpty() && outlines.isEmpty()) {
        Text(
            text = words.emptyPage,
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
                outlines = outlines,
            )
        }
    }
}

/*
  Data po ludzku.

  Nazwy miesięcy bierze SimpleDateFormat z ustawień języka, więc format też
  musi znać wybór z Kajetu - inaczej po przełączeniu na angielski wychodziło
  "5 sierpnia 2026" pośród angielskich zdań.
*/
private val polishDate = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("pl", "PL"))
private val englishDate = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.UK)

fun relativeTime(millis: Long, words: Strings): String {
    if (millis <= 0L) return words.noDate
    val elapsed = System.currentTimeMillis() - millis
    return when {
        elapsed < 60_000 -> words.justNow
        elapsed < 3_600_000 -> words.minutesAgo(elapsed / 60_000)
        elapsed < 86_400_000 -> words.hoursAgo(elapsed / 3_600_000)
        else -> (if (words.english) englishDate else polishDate).format(Date(millis))
    }
}
