package wojtoteka.ovh.kajet.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wojtoteka.ovh.kajet.core.design.FolderColor
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.SectionLabel
import wojtoteka.ovh.kajet.core.design.component.IconAction
import wojtoteka.ovh.kajet.core.design.component.NoticeBar
import wojtoteka.ovh.kajet.core.design.component.HorizontalRule
import wojtoteka.ovh.kajet.core.design.component.MarginRail
import wojtoteka.ovh.kajet.core.design.component.PrimaryButton
import wojtoteka.ovh.kajet.core.design.component.SecondaryButton
import wojtoteka.ovh.kajet.core.design.component.EmptyState
import wojtoteka.ovh.kajet.core.design.component.KajetMark
import wojtoteka.ovh.kajet.core.design.component.marginRule
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.ItemType
import wojtoteka.ovh.kajet.core.model.LibraryItem
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode
import wojtoteka.ovh.kajet.export.ExportFormat
import wojtoteka.ovh.kajet.storage.LibraryRepository
import wojtoteka.ovh.kajet.storage.TrashEntry

@Composable
fun LibraryScreen(
    model: LibraryViewModel,
    repo: LibraryRepository,
    defaultMode: PageMode,
    defaultBackground: PageBackground,
    onOpenItem: (LibraryItem) -> Unit,
    onSettings: () -> Unit,
) {
    val section by model.section.collectAsStateWithLifecycle()
    val path by model.path.collectAsStateWithLifecycle()
    val content by model.content.collectAsStateWithLifecycle()
    val tree by model.tree.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val progress by model.progress.collectAsStateWithLifecycle()

    var folderDialog by remember { mutableStateOf(false) }
    var noteDialog by remember { mutableStateOf(false) }
    var fileDialog by remember { mutableStateOf(false) }
    var itemMenu by remember { mutableStateOf<LibraryItem?>(null) }
    var renaming by remember { mutableStateOf<LibraryItem?>(null) }
    var moving by remember { mutableStateOf<LibraryItem?>(null) }
    var folderLook by remember { mutableStateOf<LibraryItem?>(null) }

    val screenWidth = LocalConfiguration.current.screenWidthDp
    // Phones and narrow windows: hide the folder tree, keep the main list usable.
    val roomForTree = screenWidth >= 600
    val narrow = screenWidth < 600
    val railWidth = if (narrow) 48.dp else Kajet.dimens.railWidth

    Row(Modifier.fillMaxSize().background(Kajet.colors.desk)) {
        SectionRail(
            selected = section,
            onSelect = model::setSection,
            onSettings = onSettings,
            railWidth = railWidth,
        )

        if (section == LibrarySection.LIBRARY && roomForTree) {
            TreeColumn(
                tree = tree,
                current = path,
                onSelect = model::goTo,
                onToggle = model::toggleExpanded,
            )
        }

        Column(
            Modifier
                .fillMaxHeight()
                .weight(1f)
                .background(Kajet.colors.sheet),
        ) {
            if (progress != null) {
                NoticeBar(
                    icon = KajetIcons.Restore,
                    text = "Odbudowuję spis notatek. $progress",
                    color = Kajet.colors.accent,
                )
            }
            if (error != null) {
                NoticeBar(
                    icon = KajetIcons.ErrorMark,
                    text = error.orEmpty(),
                    color = Kajet.colors.danger,
                ) {
                    SecondaryButton("Rozumiem", model::dismissError)
                }
            }

            when (section) {
                LibrarySection.LIBRARY -> FolderView(
                    path = path,
                    items = content,
                    repo = repo,
                    showPath = !roomForTree,
                    onUp = model::goUp,
                    onOpen = { item ->
                        if (item.type == ItemType.FOLDER) {
                            model.goTo(item.path)
                        } else {
                            model.rememberOpened(item)
                            onOpenItem(item)
                        }
                    },
                    onMenu = { itemMenu = it },
                    onFavourite = model::toggleFavorite,
                    onNewFolder = { folderDialog = true },
                    onNewNote = { noteDialog = true },
                    onNewFile = { fileDialog = true },
                )

                LibrarySection.FAVORITES -> SimpleList(
                    title = "Ulubione",
                    subtitle = "Notatki oznaczone gwiazdką w edytorze.",
                    source = model.favorites,
                    repo = repo,
                    emptyDescription = "Nie masz jeszcze ulubionych notatek. Otwórz notatkę i naciśnij gwiazdkę na pasku u góry.",
                    onOpen = { model.rememberOpened(it); onOpenItem(it) },
                    onMenu = { itemMenu = it },
                    onFavourite = model::toggleFavorite,
                )

                LibrarySection.RECENT -> SimpleList(
                    title = "Ostatnio otwarte",
                    subtitle = "Dwadzieścia notatek, przy których byłeś ostatnio.",
                    source = model.recent,
                    repo = repo,
                    emptyDescription = "Tu pojawią się notatki, które otworzysz.",
                    onOpen = { model.rememberOpened(it); onOpenItem(it) },
                    onMenu = { itemMenu = it },
                    onFavourite = model::toggleFavorite,
                )

                LibrarySection.SEARCH -> SearchView(
                    model = model,
                    repo = repo,
                    onOpen = { model.rememberOpened(it); onOpenItem(it) },
                )

                LibrarySection.TRASH -> TrashView(model = model)
            }
        }
    }

    if (folderDialog) {
        NewFolderDialog(
            onClose = { folderDialog = false },
            onCreate = { name, color, icon ->
                model.newFolder(name, color, icon)
                folderDialog = false
            },
        )
    }

    if (noteDialog) {
        NewNoteDialog(
            onClose = { noteDialog = false },
            defaultMode = defaultMode,
            defaultBackground = defaultBackground,
            onCreate = { title, kind, mode, background ->
                noteDialog = false
                model.newNote(title, kind, mode, background) { item -> onOpenItem(item) }
            },
        )
    }

    if (fileDialog) {
        NewFileDialog(
            onClose = { fileDialog = false },
            onCreate = { name, language ->
                fileDialog = false
                model.newCodeFile(name, language) { item -> onOpenItem(item) }
            },
        )
    }

    itemMenu?.let { item ->
        ItemMenu(
            item = item,
            onClose = { itemMenu = null },
            onRename = { itemMenu = null; renaming = item },
            onMove = { itemMenu = null; moving = item },
            onCopy = { itemMenu = null; model.copy(item) },
            onLook = { itemMenu = null; folderLook = item },
            onExportFolder = { format ->
                itemMenu = null
                model.exportFolder(item, format)
            },
            onTrash = { itemMenu = null; model.moveToTrash(item) },
        )
    }

    renaming?.let { item ->
        RenameDialog(
            current = item.name,
            onClose = { renaming = null },
            onSave = { updated ->
                model.rename(item, updated)
                renaming = null
            },
        )
    }

    moving?.let { item ->
        MoveDialog(
            item = item,
            tree = tree,
            onClose = { moving = null },
            onMove = { target ->
                model.move(item, target)
                moving = null
            },
        )
    }

    folderLook?.let { item ->
        FolderLookDialog(
            item = item,
            onClose = { folderLook = null },
            onSave = { color, icon ->
                model.updateFolderLook(item, color, icon)
                folderLook = null
            },
        )
    }
}

@Composable
private fun SectionRail(
    selected: LibrarySection,
    onSelect: (LibrarySection) -> Unit,
    onSettings: () -> Unit,
    railWidth: androidx.compose.ui.unit.Dp = Kajet.dimens.railWidth,
) {
    MarginRail(width = railWidth) {
        Box(
            Modifier
                .height(64.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            KajetMark(
                modifier = Modifier.size(26.dp),
                color = Kajet.colors.accent,
            )
        }

        IconAction(
            icon = KajetIcons.Library,
            description = "Biblioteka",
            onClick = { onSelect(LibrarySection.LIBRARY) },
            selected = selected == LibrarySection.LIBRARY,
        )
        IconAction(
            icon = KajetIcons.Search,
            description = "Szukaj w notatkach",
            onClick = { onSelect(LibrarySection.SEARCH) },
            selected = selected == LibrarySection.SEARCH,
        )
        IconAction(
            icon = KajetIcons.Favourites,
            description = "Ulubione",
            onClick = { onSelect(LibrarySection.FAVORITES) },
            selected = selected == LibrarySection.FAVORITES,
        )
        IconAction(
            icon = KajetIcons.Recent,
            description = "Ostatnio otwarte",
            onClick = { onSelect(LibrarySection.RECENT) },
            selected = selected == LibrarySection.RECENT,
        )

        Spacer(Modifier.weight(1f))

        IconAction(
            icon = KajetIcons.Bin,
            description = "Kosz",
            onClick = { onSelect(LibrarySection.TRASH) },
            selected = selected == LibrarySection.TRASH,
        )
        IconAction(
            icon = KajetIcons.SettingsCog,
            description = "Ustawienia",
            onClick = onSettings,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TreeColumn(
    tree: List<TreeNode>,
    current: String,
    onSelect: (String) -> Unit,
    onToggle: (String) -> Unit,
) {
    Column(
        Modifier
            .width(272.dp)
            .fillMaxHeight()
            .background(Kajet.colors.desk)
            .marginRule(Kajet.colors.line),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            SectionLabel("Foldery")
        }

        FolderRow(
            name = "Wszystkie notatki",
            level = 0,
            color = Kajet.colors.muted,
            iconId = "ksiazki",
            selected = current.isEmpty(),
            hasArrow = false,
            expanded = true,
            onClick = { onSelect("") },
            onArrow = {},
        )

        LazyColumn(Modifier.weight(1f)) {
            items(tree, key = { it.item.path }) { node ->
                FolderRow(
                    name = node.item.name,
                    level = node.level + 1,
                    color = FolderColor.fromId(node.item.colorId).color(Kajet.colors.isDark),
                    iconId = node.item.iconId,
                    selected = node.item.path == current,
                    hasArrow = node.item.childCount > 0,
                    expanded = node.expanded,
                    onClick = { onSelect(node.item.path) },
                    onArrow = { onToggle(node.item.path) },
                )
            }
        }
    }
}

@Composable
private fun FolderRow(
    name: String,
    level: Int,
    color: androidx.compose.ui.graphics.Color,
    iconId: String?,
    selected: Boolean,
    hasArrow: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    onArrow: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(if (selected) Kajet.colors.accentWash else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = (6 + level * 14).dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clickable(enabled = hasArrow, onClick = onArrow),
            contentAlignment = Alignment.Center,
        ) {
            if (hasArrow) {
                Icon(
                    imageVector = if (expanded) KajetIcons.ArrowDown else KajetIcons.ArrowRight,
                    contentDescription = if (expanded) "Zwiń $name" else "Rozwiń $name",
                    tint = Kajet.colors.muted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Icon(
            imageVector = KajetIcons.folderIcon(iconId),
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .padding(end = 8.dp)
                .size(18.dp),
        )
        Text(
            text = name,
            style = Kajet.type.body,
            color = if (selected) Kajet.colors.text else Kajet.colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FolderView(
    path: String,
    items: List<LibraryItem>,
    repo: LibraryRepository,
    showPath: Boolean,
    onUp: () -> Unit,
    onOpen: (LibraryItem) -> Unit,
    onMenu: (LibraryItem) -> Unit,
    onFavourite: (LibraryItem) -> Unit,
    onNewFolder: () -> Unit,
    onNewNote: () -> Unit,
    onNewFile: () -> Unit,
) {
    val placeName = if (path.isEmpty()) "Wszystkie notatki" else path.substringAfterLast('/')
    val narrow = LocalConfiguration.current.screenWidthDp < 600

    Column(Modifier.fillMaxSize()) {
        if (narrow) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (path.isNotEmpty() && showPath) {
                        IconAction(KajetIcons.BackArrow, "Folder wyżej", onUp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(placeName, style = Kajet.type.title, color = Kajet.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (path.isNotEmpty()) {
                            Text(
                                text = path.substringBeforeLast('/', "Wszystkie notatki"),
                                style = Kajet.type.meta,
                                color = Kajet.colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    PrimaryButton("Nowa notatka", onNewNote, icon = KajetIcons.Plus)
                    SecondaryButton("Folder", onNewFolder, icon = KajetIcons.Folder)
                    SecondaryButton("Kod", onNewFile, icon = KajetIcons.CodeFile)
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (path.isNotEmpty() && showPath) {
                    IconAction(KajetIcons.BackArrow, "Folder wyżej", onUp)
                }
                Column(Modifier.weight(1f)) {
                    Text(placeName, style = Kajet.type.display, color = Kajet.colors.text)
                    if (path.isNotEmpty()) {
                        Text(
                            text = path.substringBeforeLast('/', "Wszystkie notatki"),
                            style = Kajet.type.meta,
                            color = Kajet.colors.muted,
                        )
                    }
                }
                PrimaryButton("Nowa notatka", onNewNote, icon = KajetIcons.Plus)
                SecondaryButton("Folder", onNewFolder, icon = KajetIcons.Folder)
                SecondaryButton("Plik z kodem", onNewFile, icon = KajetIcons.CodeFile)
            }
        }
        HorizontalRule()

        if (items.isEmpty()) {
            EmptyState(
                title = "Ten folder jest pusty",
                description = "Utwórz notatkę albo folder na przedmiot. Wszystko zapisze się w katalogu, który wskazałeś na urządzeniu.",
                action = { PrimaryButton("Nowa notatka", onNewNote, icon = KajetIcons.Plus) },
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.documentUri }) { item ->
                    ItemRow(
                        item = item,
                        repo = repo,
                        onOpen = { onOpen(item) },
                        onMenu = { onMenu(item) },
                        onFavourite = { onFavourite(item) },
                        modifier = Modifier.padding(start = 12.dp),
                    )
                    HorizontalRule(insetFromStart = 60.dp)
                }
            }
        }
    }
}

@Composable
private fun SimpleList(
    title: String,
    subtitle: String,
    source: kotlinx.coroutines.flow.StateFlow<List<LibraryItem>>,
    repo: LibraryRepository,
    emptyDescription: String,
    onOpen: (LibraryItem) -> Unit,
    onMenu: (LibraryItem) -> Unit,
    onFavourite: (LibraryItem) -> Unit,
) {
    val items by source.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)) {
            Text(title, style = Kajet.type.display, color = Kajet.colors.text)
            Text(subtitle, style = Kajet.type.meta, color = Kajet.colors.muted)
        }
        HorizontalRule()

        if (items.isEmpty()) {
            EmptyState(title = "Pusto", description = emptyDescription)
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.documentUri }) { item ->
                    ItemRow(
                        item = item,
                        repo = repo,
                        onOpen = { onOpen(item) },
                        onMenu = { onMenu(item) },
                        onFavourite = { onFavourite(item) },
                        modifier = Modifier.padding(start = 12.dp),
                    )
                    HorizontalRule(insetFromStart = 60.dp)
                }
            }
        }
    }
}

@Composable
private fun SearchView(
    model: LibraryViewModel,
    repo: LibraryRepository,
    onOpen: (LibraryItem) -> Unit,
) {
    val query by model.query.collectAsStateWithLifecycle()
    val results by model.searchResults.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
            Text("Szukaj", style = Kajet.type.display, color = Kajet.colors.text)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = KajetIcons.Search,
                    contentDescription = null,
                    tint = Kajet.colors.muted,
                    modifier = Modifier.size(20.dp),
                )
                BasicTextField(
                    value = query,
                    onValueChange = model::setQuery,
                    singleLine = true,
                    textStyle = Kajet.type.bodyLarge.copy(color = Kajet.colors.text),
                    cursorBrush = SolidColor(Kajet.colors.accent),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp),
                )
            }
            HorizontalRule(color = Kajet.colors.muted.copy(alpha = 0.5f))
            Text(
                text = "Szukam w tytułach i w treści. Pismo odręczne znajdę wtedy, kiedy zamienisz je na tekst.",
                style = Kajet.type.meta,
                color = Kajet.colors.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        when {
            query.length < 2 -> EmptyState(
                title = "Wpisz, czego szukasz",
                description = "Wystarczą dwie litery. Szukanie działa bez internetu, bo spis notatek leży na urządzeniu.",
            )

            results.isEmpty() -> EmptyState(
                title = "Nic nie znalazłem",
                description = "Sprawdź pisownię albo odbuduj spis notatek w ustawieniach, jeśli kopiowałeś pliki spoza aplikacji.",
            )

            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(results, key = { it.documentUri }) { item ->
                    ItemRow(
                        item = item,
                        repo = repo,
                        onOpen = { onOpen(item) },
                        onMenu = {},
                        modifier = Modifier.padding(start = 12.dp),
                    )
                    HorizontalRule(insetFromStart = 60.dp)
                }
            }
        }
    }
}

@Composable
private fun TrashView(model: LibraryViewModel) {
    val trash by model.trash.collectAsStateWithLifecycle()
    val narrow = LocalConfiguration.current.screenWidthDp < 600

    Column(Modifier.fillMaxSize()) {
        if (narrow) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column {
                    Text("Kosz", style = Kajet.type.title, color = Kajet.colors.text)
                    Text(
                        text = "Wyrzucone notatki leżą w katalogu .trash obok biblioteki. Nic nie ginie, dopóki nie opróżnisz kosza.",
                        style = Kajet.type.meta,
                        color = Kajet.colors.muted,
                    )
                }
                if (trash.isNotEmpty()) {
                    SecondaryButton(
                        text = "Opróżnij kosz",
                        onClick = model::emptyTrash,
                        icon = KajetIcons.Bin,
                        color = Kajet.colors.danger,
                    )
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Kosz", style = Kajet.type.display, color = Kajet.colors.text)
                    Text(
                        text = "Wyrzucone notatki leżą w katalogu .trash obok biblioteki. Nic nie ginie, dopóki nie opróżnisz kosza.",
                        style = Kajet.type.meta,
                        color = Kajet.colors.muted,
                        modifier = Modifier.widthIn(max = 560.dp),
                    )
                }
                if (trash.isNotEmpty()) {
                    SecondaryButton(
                        text = "Opróżnij kosz",
                        onClick = model::emptyTrash,
                        icon = KajetIcons.Bin,
                        color = Kajet.colors.danger,
                    )
                }
            }
        }
        HorizontalRule()

        if (trash.isEmpty()) {
            EmptyState(
                title = "Kosz jest pusty",
                description = "Wyrzucone notatki znajdziesz tutaj i będziesz mógł je przywrócić dokładnie tam, skąd zniknęły.",
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(trash, key = { it.id }) { item ->
                    TrashRow(
                        item = item,
                        onRestore = { model.restore(item) },
                        onDelete = { model.deletePermanently(item) },
                    )
                    HorizontalRule(insetFromStart = 20.dp)
                }
            }
        }
    }
}

@Composable
private fun TrashRow(item: TrashEntry, onRestore: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 20.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.displayName, style = Kajet.type.body, color = Kajet.colors.text)
            Text(
                text = "Wyrzucone ${relativeTime(item.deletedAt).lowercase()}, było w: " +
                    item.originalParent.ifEmpty { "Wszystkie notatki" },
                style = Kajet.type.meta,
                color = Kajet.colors.muted,
            )
        }
        SecondaryButton("Przywróć", onRestore, icon = KajetIcons.Restore)
        IconAction(KajetIcons.Bin, "Usuń ${item.displayName} na dobre", onDelete)
    }
}

@Composable
private fun ItemMenu(
    item: LibraryItem,
    onClose: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onLook: () -> Unit,
    onExportFolder: (ExportFormat) -> Unit,
    onTrash: () -> Unit,
) {
    KajetDialog(item.name, onClose, width = 420) {
        Column {
            MenuAction(KajetIcons.Pen, "Zmień nazwę", onRename)
            MenuAction(KajetIcons.Move, "Przenieś do innego folderu", onMove)
            MenuAction(KajetIcons.Copy, "Zrób kopię", onCopy)
            if (item.type == ItemType.FOLDER) {
                MenuAction(KajetIcons.ColorSwatch, "Zmień kolor i ikonę", onLook)
                MenuAction(
                    icon = KajetIcons.Export,
                    text = "Zapisz cały folder jako PDF",
                    onClick = { onExportFolder(ExportFormat.PDF) },
                )
                MenuAction(
                    icon = KajetIcons.Export,
                    text = "Zapisz cały folder jako Markdown",
                    onClick = { onExportFolder(ExportFormat.MARKDOWN) },
                )
            }
            MenuAction(KajetIcons.Bin, "Wyrzuć do kosza", onTrash, Kajet.colors.danger)
        }
        SecondaryButton("Zamknij", onClose)
    }
}

@Composable
private fun MenuAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    color: androidx.compose.ui.graphics.Color = Kajet.colors.text,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, style = Kajet.type.body, color = color)
    }
}

@Composable
private fun MoveDialog(
    item: LibraryItem,
    tree: List<TreeNode>,
    onClose: () -> Unit,
    onMove: (String) -> Unit,
) {
    KajetDialog("Przenieś: ${item.name}", onClose, width = 460) {
        Text(
            text = "Wybierz folder, do którego ma trafić ten wpis.",
            style = Kajet.type.body,
            color = Kajet.colors.muted,
        )
        Column {
            TargetRow("Wszystkie notatki", 0) { onMove("") }
            tree.filter { it.item.path != item.path && !it.item.path.startsWith(item.path + "/") }
                .forEach { node ->
                    TargetRow(node.item.name, node.level + 1) { onMove(node.item.path) }
                }
        }
        SecondaryButton("Anuluj", onClose)
    }
}

@Composable
private fun TargetRow(name: String, level: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(start = (level * 16).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            KajetIcons.Folder,
            contentDescription = null,
            tint = Kajet.colors.muted,
            modifier = Modifier.size(18.dp),
        )
        Text(name, style = Kajet.type.body, color = Kajet.colors.text)
    }
}

@Composable
private fun FolderLookDialog(
    item: LibraryItem,
    onClose: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var color by remember { mutableStateOf(FolderColor.fromId(item.colorId)) }
    var icon by remember {
        mutableStateOf(wojtoteka.ovh.kajet.core.model.FolderIcon.fromId(item.iconId))
    }

    KajetDialog("Wygląd folderu: ${item.name}", onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Kolor")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FolderColor.entries.forEach { option ->
                    Box(
                        Modifier
                            .size(36.dp)
                            .clickable { color = option },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(if (option == color) 26.dp else 20.dp)
                                .background(
                                    option.color(Kajet.colors.isDark),
                                    androidx.compose.foundation.shape.CircleShape,
                                ),
                        )
                    }
                }
            }
        }
        FolderIconGrid(selected = icon, color = color, onSelect = { icon = it })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Zapisz", { onSave(color.id, icon.id) })
            SecondaryButton("Anuluj", onClose)
        }
    }
}
