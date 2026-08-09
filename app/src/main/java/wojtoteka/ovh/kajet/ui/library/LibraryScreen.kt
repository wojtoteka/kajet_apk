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
import androidx.compose.ui.graphics.vector.ImageVector
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
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.core.text.deleteForeverOf
import wojtoteka.ovh.kajet.core.text.disappearsIn
import wojtoteka.ovh.kajet.core.text.emptyTrashWarning
import wojtoteka.ovh.kajet.core.text.folderLookTitle
import wojtoteka.ovh.kajet.core.text.moveDialogTitle
import wojtoteka.ovh.kajet.core.text.notesStuck
import wojtoteka.ovh.kajet.core.text.trashedAt
import wojtoteka.ovh.kajet.core.model.ItemType
import wojtoteka.ovh.kajet.core.model.LibraryItem
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode
import wojtoteka.ovh.kajet.export.ExportFormat
import wojtoteka.ovh.kajet.storage.Housekeeping
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
    val stuckPaths by model.stuckPaths.collectAsStateWithLifecycle()
    val stuckNotice by model.stuckNotice.collectAsStateWithLifecycle()

    var folderDialog by remember { mutableStateOf(false) }
    var noteDialog by remember { mutableStateOf(false) }
    var fileDialog by remember { mutableStateOf(false) }
    var itemMenu by remember { mutableStateOf<LibraryItem?>(null) }
    var renaming by remember { mutableStateOf<LibraryItem?>(null) }
    var moving by remember { mutableStateOf<LibraryItem?>(null) }
    var folderLook by remember { mutableStateOf<LibraryItem?>(null) }

    val words = LocalStrings.current
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

        // Ulubione trzymają drzewo folderów na ekranie: są tam osobnym wierszem,
        // więc mają wyglądać na miejsce w bibliotece, a nie na oddzielny ekran.
        if ((section == LibrarySection.LIBRARY || section == LibrarySection.FAVORITES) && roomForTree) {
            TreeColumn(
                tree = tree,
                current = path,
                favorites = section == LibrarySection.FAVORITES,
                onSelect = model::goTo,
                onFavorites = { model.setSection(LibrarySection.FAVORITES) },
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
                    text = "${words.libRebuilding} $progress",
                    color = Kajet.colors.accent,
                )
            }
            if (error != null) {
                NoticeBar(
                    icon = KajetIcons.ErrorMark,
                    text = error.orEmpty(),
                    color = Kajet.colors.danger,
                ) {
                    SecondaryButton(words.understood, model::dismissError)
                }
            }
            /*
              Notatki, które nie doszły na serwer. Sygnał stał do tej pory sam
              na ekranie konta — trzeba było tam z własnej woli zajrzeć, więc
              w praktyce nikt się o tym nie dowiadywał.

              Barwa spokojna, nie czerwona: notatki działają dalej i nic nie
              ginie. Pasek da się zamknąć, a wraca dopiero wtedy, gdy utknie
              coś jeszcze.
            */
            if (stuckNotice.isNotEmpty()) {
                NoticeBar(
                    icon = KajetIcons.Offline,
                    text = "${words.notesStuck(stuckNotice.size)} ${words.stuckNothingLost}",
                    color = Kajet.colors.muted,
                ) {
                    SecondaryButton(words.retryStuckButton, model::retryStuck)
                    SecondaryButton(words.understood, model::hideStuckNotice)
                }
            }

            when (section) {
                LibrarySection.LIBRARY -> FolderView(
                    path = path,
                    items = content,
                    repo = repo,
                    stuckPaths = stuckPaths,
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
                    title = words.sectionFavorites,
                    subtitle = words.libFavoritesAbout,
                    source = model.favorites,
                    repo = repo,
                    stuckPaths = stuckPaths,
                    emptyDescription = words.libFavoritesEmpty,
                    onOpen = { model.rememberOpened(it); onOpenItem(it) },
                    onMenu = { itemMenu = it },
                    onFavourite = model::toggleFavorite,
                )

                LibrarySection.RECENT -> SimpleList(
                    title = words.sectionRecent,
                    subtitle = words.libRecentAbout,
                    source = model.recent,
                    repo = repo,
                    stuckPaths = stuckPaths,
                    emptyDescription = words.libRecentEmpty,
                    onOpen = { model.rememberOpened(it); onOpenItem(it) },
                    onMenu = { itemMenu = it },
                    onFavourite = model::toggleFavorite,
                )

                LibrarySection.SEARCH -> SearchView(
                    model = model,
                    repo = repo,
                    stuckPaths = stuckPaths,
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
        // Kontekst ekranu — okno „Udostępnij" po eksporcie musi wystartować
        // z Activity, inaczej system potrafi je po cichu zdusić.
        val context = androidx.compose.ui.platform.LocalContext.current
        ItemMenu(
            item = item,
            onClose = { itemMenu = null },
            onRename = { itemMenu = null; renaming = item },
            onMove = { itemMenu = null; moving = item },
            onCopy = { itemMenu = null; model.copy(item) },
            onLook = { itemMenu = null; folderLook = item },
            onExportFolder = { format ->
                itemMenu = null
                model.exportFolder(context, item, format)
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
    val words = LocalStrings.current
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
            description = words.sectionLibrary,
            onClick = { onSelect(LibrarySection.LIBRARY) },
            selected = selected == LibrarySection.LIBRARY,
        )
        IconAction(
            icon = KajetIcons.Search,
            description = words.libSearchInNotes,
            onClick = { onSelect(LibrarySection.SEARCH) },
            selected = selected == LibrarySection.SEARCH,
        )
        IconAction(
            icon = KajetIcons.Favourites,
            description = words.sectionFavorites,
            onClick = { onSelect(LibrarySection.FAVORITES) },
            selected = selected == LibrarySection.FAVORITES,
        )
        IconAction(
            icon = KajetIcons.Recent,
            description = words.sectionRecent,
            onClick = { onSelect(LibrarySection.RECENT) },
            selected = selected == LibrarySection.RECENT,
        )

        Spacer(Modifier.weight(1f))

        IconAction(
            icon = KajetIcons.Bin,
            description = words.sectionTrash,
            onClick = { onSelect(LibrarySection.TRASH) },
            selected = selected == LibrarySection.TRASH,
        )
        IconAction(
            icon = KajetIcons.SettingsCog,
            description = words.settings,
            onClick = onSettings,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TreeColumn(
    tree: List<TreeNode>,
    current: String,
    favorites: Boolean,
    onSelect: (String) -> Unit,
    onFavorites: () -> Unit,
    onToggle: (String) -> Unit,
) {
    val words = LocalStrings.current
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
            SectionLabel(words.folders)
        }

        FolderRow(
            name = words.libAllNotes,
            level = 0,
            color = Kajet.colors.muted,
            iconId = "ksiazki",
            selected = current.isEmpty() && !favorites,
            hasArrow = false,
            expanded = true,
            onClick = { onSelect("") },
            onArrow = {},
        )

        FolderRow(
            name = words.sectionFavorites,
            level = 0,
            color = Kajet.colors.accent,
            iconId = null,
            icon = KajetIcons.Favourites,
            selected = favorites,
            hasArrow = false,
            expanded = true,
            onClick = onFavorites,
            onArrow = {},
        )

        LazyColumn(Modifier.weight(1f)) {
            items(tree, key = { it.item.path }) { node ->
                FolderRow(
                    name = node.item.name,
                    level = node.level + 1,
                    color = FolderColor.fromId(node.item.colorId).color(Kajet.colors.isDark),
                    iconId = node.item.iconId,
                    selected = node.item.path == current && !favorites,
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
    /** Znaczek spoza spisu ikon folderów - na razie tylko gwiazdka Ulubionych. */
    icon: ImageVector? = null,
) {
    val words = LocalStrings.current
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
                    contentDescription = if (expanded) "${words.libCollapse} $name" else "${words.libExpand} $name",
                    tint = Kajet.colors.muted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Icon(
            imageVector = icon ?: KajetIcons.folderIcon(iconId),
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
    stuckPaths: Set<String>,
    showPath: Boolean,
    onUp: () -> Unit,
    onOpen: (LibraryItem) -> Unit,
    onMenu: (LibraryItem) -> Unit,
    onFavourite: (LibraryItem) -> Unit,
    onNewFolder: () -> Unit,
    onNewNote: () -> Unit,
    onNewFile: () -> Unit,
) {
    val words = LocalStrings.current
    val placeName = if (path.isEmpty()) words.libAllNotes else path.substringAfterLast('/')
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
                        IconAction(KajetIcons.BackArrow, words.libFolderUp, onUp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(placeName, style = Kajet.type.title, color = Kajet.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (path.isNotEmpty()) {
                            Text(
                                text = path.substringBeforeLast('/', words.libAllNotes),
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
                    PrimaryButton(words.newNote, onNewNote, icon = KajetIcons.Plus)
                    SecondaryButton(words.libKindFolder, onNewFolder, icon = KajetIcons.Folder)
                    SecondaryButton(words.codeShort, onNewFile, icon = KajetIcons.CodeFile)
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
                    IconAction(KajetIcons.BackArrow, words.libFolderUp, onUp)
                }
                Column(Modifier.weight(1f)) {
                    Text(placeName, style = Kajet.type.display, color = Kajet.colors.text)
                    if (path.isNotEmpty()) {
                        Text(
                            text = path.substringBeforeLast('/', words.libAllNotes),
                            style = Kajet.type.meta,
                            color = Kajet.colors.muted,
                        )
                    }
                }
                PrimaryButton(words.newNote, onNewNote, icon = KajetIcons.Plus)
                SecondaryButton(words.libKindFolder, onNewFolder, icon = KajetIcons.Folder)
                SecondaryButton(words.libKindCode, onNewFile, icon = KajetIcons.CodeFile)
            }
        }
        HorizontalRule()

        if (items.isEmpty()) {
            EmptyState(
                title = words.libFolderEmpty,
                description = words.libFolderEmptyHint,
                action = { PrimaryButton(words.newNote, onNewNote, icon = KajetIcons.Plus) },
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
                        stuck = item.path in stuckPaths,
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
    stuckPaths: Set<String>,
    emptyDescription: String,
    onOpen: (LibraryItem) -> Unit,
    onMenu: (LibraryItem) -> Unit,
    onFavourite: (LibraryItem) -> Unit,
) {
    val words = LocalStrings.current
    val items by source.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)) {
            Text(title, style = Kajet.type.display, color = Kajet.colors.text)
            Text(subtitle, style = Kajet.type.meta, color = Kajet.colors.muted)
        }
        HorizontalRule()

        if (items.isEmpty()) {
            EmptyState(title = words.libNothingHere, description = emptyDescription)
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.documentUri }) { item ->
                    ItemRow(
                        item = item,
                        repo = repo,
                        onOpen = { onOpen(item) },
                        onMenu = { onMenu(item) },
                        onFavourite = { onFavourite(item) },
                        stuck = item.path in stuckPaths,
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
    stuckPaths: Set<String>,
    onOpen: (LibraryItem) -> Unit,
) {
    val words = LocalStrings.current
    val query by model.query.collectAsStateWithLifecycle()
    val results by model.searchResults.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
            Text(words.search, style = Kajet.type.display, color = Kajet.colors.text)
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
                text = words.libSearchAbout,
                style = Kajet.type.meta,
                color = Kajet.colors.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        when {
            query.length < 2 -> EmptyState(
                title = words.libSearchPrompt,
                description = words.libSearchPromptAbout,
            )

            results.isEmpty() -> EmptyState(
                title = words.libSearchNothing,
                description = words.libSearchNothingAbout,
            )

            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(results, key = { it.documentUri }) { item ->
                    ItemRow(
                        item = item,
                        repo = repo,
                        onOpen = { onOpen(item) },
                        onMenu = {},
                        stuck = item.path in stuckPaths,
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
    val words = LocalStrings.current
    val trash by model.trash.collectAsStateWithLifecycle()
    val narrow = LocalConfiguration.current.screenWidthDp < 600
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<TrashEntry?>(null) }

    // Kasowanie jednego wpisu jest tak samo nieodwracalne jak opróżnienie
    // całego kosza, więc pyta o to samo. Wcześniej sam kosz na ikonie
    // wystarczał, żeby notatka przepadła bez słowa.
    confirmDelete?.let { entry ->
        KajetDialog(words.deleteForeverQuestion, onClose = { confirmDelete = null }) {
            Text(
                text = entry.displayName,
                style = Kajet.type.title,
                color = Kajet.colors.text,
            )
            Text(
                text = words.deleteForeverWarning,
                style = Kajet.type.body,
                color = Kajet.colors.text,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    text = words.deleteForever,
                    onClick = { confirmDelete = null; model.deletePermanently(entry) },
                    icon = KajetIcons.Bin,
                    color = Kajet.colors.danger,
                )
                SecondaryButton(words.cancel, { confirmDelete = null })
            }
        }
    }

    if (confirmEmpty) {
        KajetDialog(words.emptyTrashQuestion, onClose = { confirmEmpty = false }) {
            Text(
                text = words.emptyTrashWarning(trash.size),
                style = Kajet.type.body,
                color = Kajet.colors.text,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    text = words.emptyTrash,
                    onClick = { confirmEmpty = false; model.emptyTrash() },
                    icon = KajetIcons.Bin,
                    color = Kajet.colors.danger,
                )
                SecondaryButton(words.cancel, { confirmEmpty = false })
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (narrow) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column {
                    Text(words.sectionTrash, style = Kajet.type.title, color = Kajet.colors.text)
                    Text(
                        text = words.trashAbout,
                        style = Kajet.type.meta,
                        color = Kajet.colors.muted,
                    )
                }
                if (trash.isNotEmpty()) {
                    SecondaryButton(
                        text = words.emptyTrash,
                        onClick = { confirmEmpty = true },
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
                    Text(words.sectionTrash, style = Kajet.type.display, color = Kajet.colors.text)
                    Text(
                        text = words.trashAbout,
                        style = Kajet.type.meta,
                        color = Kajet.colors.muted,
                        modifier = Modifier.widthIn(max = 560.dp),
                    )
                }
                if (trash.isNotEmpty()) {
                    SecondaryButton(
                        text = words.emptyTrash,
                        onClick = { confirmEmpty = true },
                        icon = KajetIcons.Bin,
                        color = Kajet.colors.danger,
                    )
                }
            }
        }
        HorizontalRule()

        if (trash.isEmpty()) {
            EmptyState(
                title = words.trashEmptyTitle,
                description = words.trashEmptyAbout,
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(trash, key = { it.id }) { item ->
                    TrashRow(
                        item = item,
                        onRestore = { model.restore(item) },
                        onDelete = { confirmDelete = item },
                    )
                    HorizontalRule(insetFromStart = 20.dp)
                }
            }
        }
    }
}

@Composable
private fun TrashRow(item: TrashEntry, onRestore: () -> Unit, onDelete: () -> Unit) {
    val words = LocalStrings.current
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
                text = words.trashedAt(
                    relativeTime(item.deletedAt, words).lowercase(),
                    item.originalParent.ifEmpty { words.libAllNotes },
                ),
                style = Kajet.type.meta,
                color = Kajet.colors.muted,
            )
            // Wpis, który trafił do kosza dlatego, że notatka zniknęła na
            // serwerze, ma termin. Bez tego napisu przepadłby kiedyś sam i nikt
            // by nie wiedział, że w ogóle miał na coś czekać. Ostatnie dni na
            // czerwono — wtedy warto się pospieszyć z przywróceniem.
            Housekeeping.daysLeft(item)?.let { days ->
                Text(
                    text = words.disappearsIn(days),
                    style = Kajet.type.meta,
                    color = if (days <= 3) Kajet.colors.danger else Kajet.colors.muted,
                )
            }
        }
        SecondaryButton(words.restore, onRestore, icon = KajetIcons.Restore)
        IconAction(KajetIcons.Bin, words.deleteForeverOf(item.displayName), onDelete)
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
    val words = LocalStrings.current
    KajetDialog(item.name, onClose, width = 420) {
        Column {
            MenuAction(KajetIcons.Pen, words.rename, onRename)
            MenuAction(KajetIcons.Move, words.menuMoveToFolder, onMove)
            MenuAction(KajetIcons.Copy, words.menuCopy, onCopy)
            if (item.type == ItemType.FOLDER) {
                MenuAction(KajetIcons.ColorSwatch, words.menuLook, onLook)
                MenuAction(
                    icon = KajetIcons.Export,
                    text = words.exportFolderPdf,
                    onClick = { onExportFolder(ExportFormat.PDF) },
                )
                MenuAction(
                    icon = KajetIcons.Export,
                    text = words.exportFolderMarkdown,
                    onClick = { onExportFolder(ExportFormat.MARKDOWN) },
                )
            }
            MenuAction(KajetIcons.Bin, words.moveToTrash, onTrash, Kajet.colors.danger)
        }
        SecondaryButton(words.close, onClose)
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
    val words = LocalStrings.current
    KajetDialog(words.moveDialogTitle(item.name), onClose, width = 460) {
        Text(
            text = words.movePrompt,
            style = Kajet.type.body,
            color = Kajet.colors.muted,
        )
        Column {
            TargetRow(words.libAllNotes, 0) { onMove("") }
            tree.filter { it.item.path != item.path && !it.item.path.startsWith(item.path + "/") }
                .forEach { node ->
                    TargetRow(node.item.name, node.level + 1) { onMove(node.item.path) }
                }
        }
        SecondaryButton(words.cancel, onClose)
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
    val words = LocalStrings.current
    var color by remember { mutableStateOf(FolderColor.fromId(item.colorId)) }
    var icon by remember {
        mutableStateOf(wojtoteka.ovh.kajet.core.model.FolderIcon.fromId(item.iconId))
    }

    KajetDialog(words.folderLookTitle(item.name), onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(words.colour)
            // Kolorów jest więcej, niż mieści wąski ekran — pasek jeździ w bok.
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
            PrimaryButton(words.save, { onSave(color.id, icon.id) })
            SecondaryButton(words.cancel, onClose)
        }
    }
}
