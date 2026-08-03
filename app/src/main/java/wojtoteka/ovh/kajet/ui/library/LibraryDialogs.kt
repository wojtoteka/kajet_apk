package wojtoteka.ovh.kajet.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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

@Composable
fun KajetDialog(
    title: String,
    onClose: () -> Unit,
    width: Int = 480,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = width.dp)
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
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(FolderColor.Graphite) }
    var icon by remember { mutableStateOf(FolderIcon.FOLDER) }

    KajetDialog("Nowy folder", onClose) {
        KajetTextField(name, { name = it }, "Nazwa folderu", autoFocus = true)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Kolor")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                text = "Utwórz folder",
                onClick = { onCreate(name, color.id, icon.id) },
                enabled = name.isNotBlank(),
            )
            SecondaryButton("Anuluj", onClose)
        }
    }
}

@Composable
fun FolderIconGrid(
    selected: FolderIcon,
    color: FolderColor,
    onSelect: (FolderIcon) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Ikona", modifier = Modifier.weight(1f))
            Text(selected.labelPl, style = Kajet.type.meta, color = Kajet.colors.muted)
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
                        .clickable(onClickLabel = option.labelPl) { onSelect(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = KajetIcons.folderIcon(option.id),
                        contentDescription = option.labelPl,
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

@Composable
fun NewNoteDialog(
    onClose: () -> Unit,
    onCreate: (title: String, kind: NoteKind, mode: PageMode, background: PageBackground) -> Unit,
    defaultMode: PageMode,
    defaultBackground: PageBackground,
) {
    var title by remember { mutableStateOf("") }
    val narrowPhone = LocalConfiguration.current.screenWidthDp < 600
    var kind by remember {
        mutableStateOf(if (narrowPhone) NoteKind.TEXT else NoteKind.HANDWRITTEN)
    }
    var mode by remember { mutableStateOf(defaultMode) }
    var background by remember { mutableStateOf(defaultBackground) }

    KajetDialog("Nowa notatka", onClose, width = 520) {
        KajetTextField(title, { title = it }, "Tytuł", autoFocus = true)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Rodzaj")
            NoteKind.entries.forEach { option ->
                ChoiceRow(
                    text = option.labelPl,
                    description = when (option) {
                        NoteKind.HANDWRITTEN -> "Piszesz rysikiem, możesz też wstawić pole z tekstem."
                        NoteKind.TEXT -> "Piszesz z klawiatury, możesz wstawić zdjęcie i mały rysunek."
                        NoteKind.MINDMAP -> "Węzły połączone liniami, podpisy z klawiatury albo rysikiem."
                    },
                    selected = option == kind,
                    onClick = { kind = option },
                )
            }
        }

        if (kind == NoteKind.HANDWRITTEN) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Strona")
                PageMode.entries.forEach { option ->
                    ChoiceRow(
                        text = option.labelPl,
                        description = if (option == PageMode.A4) {
                            "Tak jak w zeszycie. Wydruk wychodzi bez przycinania."
                        } else {
                            "Strona rośnie w dół, kiedy piszesz przy dolnej krawędzi."
                        },
                        selected = option == mode,
                        onClick = { mode = option },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Tło strony")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageBackground.entries.forEach { option ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .background(
                                    if (option == background) Kajet.colors.accentWash else Kajet.colors.desk,
                                    RoundedCornerShape(Kajet.dimens.corner),
                                )
                                .clickable { background = option }
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = option.labelPl,
                                style = Kajet.type.meta,
                                color = if (option == background) Kajet.colors.accent else Kajet.colors.muted,
                            )
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                text = "Utwórz notatkę",
                onClick = { onCreate(title.ifBlank { "Bez tytułu" }, kind, mode, background) },
            )
            SecondaryButton("Anuluj", onClose)
        }
    }
}

@Composable
fun NewFileDialog(
    onClose: () -> Unit,
    onCreate: (name: String, language: CodeLanguage) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var language by remember { mutableStateOf(CodeLanguage.PYTHON) }

    KajetDialog("Nowy plik z kodem", onClose, width = 520) {
        KajetTextField(name, { name = it }, "Nazwa pliku", autoFocus = true)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Język")
            CodeLanguage.entries.filter { it.runnable }.forEach { option ->
                val phone = LocalConfiguration.current.smallestScreenWidthDp < 600
                ChoiceRow(
                    text = option.labelPl,
                    description = when {
                        option == CodeLanguage.PYTHON && phone ->
                            "Uruchamia się na serwerze, potrzebne konto i internet."
                        option.offline ->
                            "Uruchamia się na tablecie, bez internetu."
                        else ->
                            "Uruchamia się na serwerze, potrzebny internet."
                    },
                    selected = option == language,
                    onClick = { language = option },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                text = "Utwórz plik",
                onClick = { onCreate(name.ifBlank { "program" }, language) },
            )
            SecondaryButton("Anuluj", onClose)
        }
    }
}

@Composable
fun RenameDialog(
    current: String,
    onClose: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(current) }
    KajetDialog("Zmień nazwę", onClose) {
        KajetTextField(name, { name = it }, "Nowa nazwa", autoFocus = true)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Zapisz", { onSave(name) }, enabled = name.isNotBlank())
            SecondaryButton("Anuluj", onClose)
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
