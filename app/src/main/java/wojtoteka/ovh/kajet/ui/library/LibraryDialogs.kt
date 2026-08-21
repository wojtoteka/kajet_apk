package wojtoteka.ovh.kajet.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import wojtoteka.ovh.kajet.core.design.FolderColor
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.SectionLabel
import wojtoteka.ovh.kajet.core.design.component.HorizontalRule
import wojtoteka.ovh.kajet.core.design.component.PrimaryButton
import wojtoteka.ovh.kajet.core.design.component.SecondaryButton
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.CodeLanguage
import wojtoteka.ovh.kajet.core.model.FolderIcon
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.core.text.nameOnDiskAbout
import wojtoteka.ovh.kajet.storage.FileNames

@Composable
fun KajetDialog(
    title: String,
    onClose: () -> Unit,
    width: Int = 480,
    content: @Composable () -> Unit,
) {
    /*
      Okno dialogu bierze cały ekran i samo układa w nim swoją zawartość.

      decorFitsSystemWindows=false oddaje mu wgląd w klawiaturę - dzięki temu
      imePadding kurczy dialog nad nią i przyciski nie giną pod spodem. Samo
      to jednak nie wystarczy: bez usePlatformDefaultWidth=false okno zostaje
      przy swojej domyślnej wielkości, a zawartość liczy odstępy od krawędzi
      CAŁEGO ekranu. Rysunek i dotyk rozjeżdżały się wtedy o pasek stanu, czyli
      mniej więcej o jeden wiersz menu - palec trafiał w „Zrób kopię",
      a uruchamiało się „Przenieś do folderu".
    */
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            decorFitsSystemWindows = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Stuknięcie obok okna zamyka je, tak jak zawsze. Pełnoekranowa
                // zawartość łapie ten dotyk sama, więc musi go oddać dalej.
                .pointerInput(Unit) { detectTapGestures { onClose() } }
                .systemBarsPadding()
                .imePadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    // Kolejność jest tu istotna i łatwo ją odwrócić. `fillMaxWidth`
                    // daje temu, co pod spodem, szerokość sztywną - równą całemu
                    // ekranowi - a `widthIn` postawione PO nim może już tylko
                    // zmieścić się w tym, co dostało, więc górna granica przepadała
                    // bez śladu. Na tablecie okna szły przez to od krawędzi do
                    // krawędzi. Najpierw granica, dopiero potem wypełnienie.
                    .widthIn(max = width.dp)
                    .fillMaxWidth()
                    // Dotyk w samo okno nie ma go zamykać.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .background(Kajet.colors.sheet, RoundedCornerShape(Kajet.dimens.corner))
                    .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner)),
            ) {
                Text(
                    text = title,
                    style = Kajet.type.title,
                    color = Kajet.colors.text,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
                )
                HorizontalRule()
                Column(
                    modifier = Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun KajetTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    singleLine: Boolean = true,
) {
    val focus = remember { FocusRequester() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(label)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            textStyle = Kajet.type.body.copy(color = Kajet.colors.text),
            cursorBrush = SolidColor(Kajet.colors.accent),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .padding(vertical = 8.dp),
        )
        HorizontalRule(color = Kajet.colors.muted.copy(alpha = 0.5f))
    }
    if (autoFocus) {
        androidx.compose.runtime.LaunchedEffect(Unit) { focus.requestFocus() }
    }
}

@Composable
fun NewFolderDialog(
    onClose: () -> Unit,
    onCreate: (name: String, colorId: String, iconId: String) -> Unit,
) {
    val words = LocalStrings.current
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(FolderColor.Graphite) }
    var icon by remember { mutableStateOf(FolderIcon.FOLDER) }

    KajetDialog(words.newFolder, onClose) {
        KajetTextField(name, { name = it }, words.folderName, autoFocus = true)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(words.colour)
            // Kolorów jest więcej, niż mieści wąski ekran - pasek jeździ w bok.
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FolderColor.entries.forEach { option ->
                    FolderColourDot(option, option == color) { color = option }
                }
            }
        }

        FolderIconGrid(
            selected = icon,
            color = color,
            onSelect = { icon = it },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                text = words.createFolder,
                onClick = { onCreate(name, color.id, icon.id) },
                enabled = name.isNotBlank(),
            )
            SecondaryButton(words.cancel, onClose)
        }
    }
}

@Composable
fun FolderIconGrid(
    selected: FolderIcon,
    color: FolderColor,
    onSelect: (FolderIcon) -> Unit,
) {
    val words = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(words.icon, modifier = Modifier.weight(1f))
            Text(selected.label(words), style = Kajet.type.meta, color = Kajet.colors.muted)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 52.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(184.dp)
                .background(Kajet.colors.desk, RoundedCornerShape(Kajet.dimens.corner))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(FolderIcon.entries, key = { it.id }) { option ->
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (option == selected) Kajet.colors.accentWash else Kajet.colors.sheet,
                            RoundedCornerShape(Kajet.dimens.corner),
                        )
                        .clickable(onClickLabel = option.label(words)) { onSelect(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = KajetIcons.folderIcon(option.id),
                        contentDescription = option.label(words),
                        tint = color.color(Kajet.colors.isDark),
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderColourDot(option: FolderColor, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (selected) 26.dp else 20.dp)
                .background(option.color(Kajet.colors.isDark), CircleShape),
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .border(1.dp, Kajet.colors.text, CircleShape),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewNoteDialog(
    onClose: () -> Unit,
    onCreate: (title: String, kind: NoteKind, mode: PageMode, background: PageBackground) -> Unit,
    defaultMode: PageMode,
    defaultBackground: PageBackground,
) {
    val words = LocalStrings.current
    var title by remember { mutableStateOf("") }
    val narrowPhone = LocalConfiguration.current.screenWidthDp < 600
    var kind by remember {
        mutableStateOf(if (narrowPhone) NoteKind.TEXT else NoteKind.HANDWRITTEN)
    }
    var mode by remember { mutableStateOf(defaultMode) }
    var background by remember { mutableStateOf(defaultBackground) }

    KajetDialog(words.newNote, onClose, width = 520) {
        KajetTextField(title, { title = it }, words.titleLabel, autoFocus = true)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(words.noteKindLabel)
            NoteKind.entries.forEach { option ->
                ChoiceRow(
                    text = option.label(words),
                    description = when (option) {
                        NoteKind.HANDWRITTEN -> words.kindHandwrittenAbout
                        NoteKind.TEXT -> words.kindTextAbout
                        NoteKind.MINDMAP -> words.kindMindMapAbout
                    },
                    selected = option == kind,
                    onClick = { kind = option },
                )
            }
        }

        if (kind == NoteKind.HANDWRITTEN) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(words.pageLabel)
                PageMode.entries.forEach { option ->
                    ChoiceRow(
                        text = option.label(words),
                        description = if (option == PageMode.A4) {
                            words.pageA4About
                        } else {
                            words.pageScrollAbout
                        },
                        selected = option == mode,
                        onClick = { mode = option },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(words.pageBackgroundLabel)
                /*
                  Każdy kafelek jest szeroki tyle, ile jego napis, a kiedy
                  zabraknie miejsca, reszta schodzi do następnego wiersza.
                  Po równym podziale szerokości (weight) na telefonie
                  „W kratkę" i „Pięciolinia" łamały się w środku słowa, a przy
                  powiększonym piśmie nie mieściły się wcale.
                */
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PageBackground.entries.forEach { option ->
                        val picked = option == background
                        Box(
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .background(
                                    if (picked) Kajet.colors.accentWash else Kajet.colors.desk,
                                    RoundedCornerShape(Kajet.dimens.corner),
                                )
                                .clickable { background = option }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = option.label(words),
                                style = Kajet.type.label,
                                color = if (picked) Kajet.colors.accent else Kajet.colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                text = words.createNote,
                onClick = { onCreate(title.ifBlank { words.untitled }, kind, mode, background) },
            )
            SecondaryButton(words.cancel, onClose)
        }
    }
}

@Composable
fun NewFileDialog(
    onClose: () -> Unit,
    onCreate: (name: String, language: CodeLanguage) -> Unit,
) {
    val words = LocalStrings.current
    var name by remember { mutableStateOf("") }
    var language by remember { mutableStateOf(CodeLanguage.PYTHON) }

    KajetDialog(words.newCodeFile, onClose, width = 520) {
        KajetTextField(name, { name = it }, words.fileName, autoFocus = true)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(words.settingsLanguage)
            /*
              Zwykłego tekstu tu nie ma: od pisania tekstu jest notatka, a plik
              .txt założony w „Nowy plik z kodem" tylko mylił. Istniejące pliki
              .txt dalej się otwierają - PLAIN_TEXT zostaje w spisie języków.

              MySQL wypada z tej listy z innego powodu. Aplikacja czyta język
              wyłącznie z rozszerzenia pliku, a .sql należy do SQLite (tak samo
              jak na serwerze), więc plik założony jako MySQL i tak otworzyłby
              się jako SQLite. Lepiej nie stawiać wyboru, którego nie ma jak
              dotrzymać - MySQL-a wybiera się na stronie, gdzie język zapisuje
              się w samej notatce.
            */
            CodeLanguage.entries
                .filter { (it.runnable || it == CodeLanguage.HTML) && it != CodeLanguage.MYSQL }
                .forEach { option ->
                    val phone = LocalConfiguration.current.smallestScreenWidthDp < 600
                    ChoiceRow(
                        text = option.label(words),
                        description = when {
                            option == CodeLanguage.HTML -> words.langHtmlAbout
                            option == CodeLanguage.PYTHON && phone -> words.langNeedsAccountAbout
                            option.offline -> words.codeRunsOnTablet
                            else -> words.codeRunsOnServer
                        },
                        selected = option == language,
                        onClick = { language = option },
                    )
                }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                text = words.createFile,
                onClick = { onCreate(name.ifBlank { words.defaultFileName }, language) },
            )
            SecondaryButton(words.cancel, onClose)
        }
    }
}

@Composable
fun RenameDialog(
    current: String,
    onClose: () -> Unit,
    onSave: (String) -> Unit,
) {
    val words = LocalStrings.current
    var name by remember { mutableStateOf(current) }
    KajetDialog(words.rename, onClose) {
        KajetTextField(name, { name = it }, words.newName, autoFocus = true)

        // Dwukropka ani ukośnika nie da się wpisać w nazwę pliku - magazyn
        // zamienia je na podkreślenie. Nazwa na liście zostaje wtedy taka, jak
        // wpisana, a katalog na dysku nazywa się inaczej. Lepiej powiedzieć to
        // wprost, niż zostawić dwie różne nazwy bez wyjaśnienia.
        val onDisk = FileNames.safe(name)
        if (name.isNotBlank() && onDisk != name.trim()) {
            Text(
                text = words.nameOnDiskAbout(onDisk),
                style = Kajet.type.meta,
                color = Kajet.colors.muted,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(words.save, { onSave(name) }, enabled = name.isNotBlank())
            SecondaryButton(words.cancel, onClose)
        }
    }
}

@Composable
fun ChoiceRow(
    text: String,
    description: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(
                if (selected) Kajet.colors.accentWash else Kajet.colors.sheet,
                RoundedCornerShape(Kajet.dimens.corner),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = KajetIcons.Confirm,
                    contentDescription = null,
                    tint = Kajet.colors.accent,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .border(1.dp, Kajet.colors.line, CircleShape),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text, style = Kajet.type.body, color = Kajet.colors.text)
            if (description != null) {
                Text(description, style = Kajet.type.meta, color = Kajet.colors.muted)
            }
        }
    }
}
