package wojtoteka.ovh.kajet.editor.text

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wojtoteka.ovh.kajet.core.design.InkPalette
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.IconAction
import wojtoteka.ovh.kajet.core.design.component.ColourDot
import wojtoteka.ovh.kajet.core.design.component.HorizontalRule
import wojtoteka.ovh.kajet.core.design.component.ColourPickerDialog
import wojtoteka.ovh.kajet.core.design.component.SecondaryButton
import wojtoteka.ovh.kajet.core.design.component.SegmentedChoice
import wojtoteka.ovh.kajet.core.design.component.marginRule
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.NoteFont
import wojtoteka.ovh.kajet.core.model.TextContent
import wojtoteka.ovh.kajet.core.model.NoteAlign
import wojtoteka.ovh.kajet.core.model.TextMarkers
import wojtoteka.ovh.kajet.editor.SaveState
import wojtoteka.ovh.kajet.editor.SaveIndicator
import kotlin.math.roundToInt

@Composable
fun TextEditor(
    model: TextNoteViewModel,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onPhotoFromGallery: () -> Unit,
    onPhotoFromCamera: () -> Unit,
) {
    val document by model.document.collectAsStateWithLifecycle()
    val drawing by model.drawing.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val saveState by model.saveState.collectAsStateWithLifecycle()
    val lastSave by model.lastSave.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val appearance by model.appearance.collectAsStateWithLifecycle()
    val recentColors by model.recentColors.collectAsStateWithLifecycle()

    val colors = Kajet.colors
    val narrow = LocalConfiguration.current.screenWidthDp < 600
    val railWidth = if (narrow) 48.dp else Kajet.dimens.railWidth

    var field by remember(document?.id) {
        mutableStateOf(TextFieldValue(document?.text?.markdown.orEmpty()))
    }

    // Treść mogła zmienić się poza polem, na przykład przez odhaczenie zadania
    // albo przez wstawienie rysunku.
    val modelContent = document?.text?.markdown.orEmpty()
    if (modelContent != field.text) {
        field = field.copy(
            text = modelContent,
            selection = TextRange(field.selection.start.coerceAtMost(modelContent.length)),
        )
    }

    /*
     * Widok blokowy: zdjęcia widać jako zdjęcia, a nie jako `![...](...)`.
     * Włączony domyślnie, bo tak notatka wygląda tak, jak człowiek ją napisał.
     * Widok Markdown zostaje pod przyciskiem, bo przy tabelach i wzorach
     * czasem trzeba zobaczyć surowy zapis.
     */
    var blockMode by remember { mutableStateOf(true) }
    var blocks by remember(document?.id) { mutableStateOf(Blocks.split(modelContent)) }

    // Blok, w którym stoi kursor. Pasek formatowania działa właśnie na nim.
    var focusedKey by remember { mutableStateOf<String?>(null) }
    var focusedField by remember { mutableStateOf(TextFieldValue()) }
    var setFocusedField by remember { mutableStateOf<((TextFieldValue) -> Unit)?>(null) }
    var keyToFocus by remember { mutableStateOf<String?>(null) }

    // Treść przyszła spoza edytora, na przykład wstawiono zdjęcie albo cofnięto
    // zmianę. Bloki układamy od nowa, ale tylko wtedy, gdy naprawdę się różnią,
    // żeby nie przerywać pisania przy każdym naciśnięciu klawisza.
    if (blockMode && Blocks.join(blocks) != modelContent) {
        blocks = Blocks.split(modelContent)
    }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) model.saveNow()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // Miejsce, w które ma trafić wstawiana treść: za blokiem z kursorem,
    // a nie na końcu całej notatki.
    fun insertPosition(): Int = if (blockMode) {
        focusedKey?.let { Blocks.endPosition(blocks, it) } ?: model.markdown.length
    } else {
        field.selection.start
    }

    fun format(transform: (TextFieldValue) -> TextFieldValue) {
        if (blockMode) {
            val key = focusedKey ?: return
            val set = setFocusedField ?: return
            val next = transform(focusedField)

            focusedField = next
            set(next)

            val changed = Blocks.setText(blocks, key, next.text)
            blocks = changed
            model.setContent(Blocks.join(changed))
        } else {
            val next = transform(field)
            field = next
            model.setContent(next.text)
        }
    }

    Row(Modifier.fillMaxSize().background(colors.desk)) {

        Column(
            Modifier
                .width(railWidth)
                .fillMaxSize()
                .background(colors.desk)
                .marginRule(colors.line)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconAction(KajetIcons.BackArrow, "Wróć do biblioteki", { model.saveNow(); onBack() })
            HorizontalRule(Modifier.padding(horizontal = 12.dp))

            IconAction(KajetIcons.PhotoFrame, "Wstaw zdjęcie z galerii", {
                model.rememberPhotoPosition(insertPosition())
                onPhotoFromGallery()
            })
            IconAction(KajetIcons.CameraBody, "Zrób zdjęcie", {
                model.rememberPhotoPosition(insertPosition())
                onPhotoFromCamera()
            })
            IconAction(KajetIcons.DrawingPad, "Wstaw rysunek", model::openDrawing)

            HorizontalRule(Modifier.padding(horizontal = 12.dp))

            IconAction(
                icon = KajetIcons.Favourites,
                description = if (document?.favorite == true) "Usuń z ulubionych" else "Dodaj do ulubionych",
                onClick = model::toggleFavorite,
                selected = document?.favorite == true,
            )
            IconAction(KajetIcons.Export, "Eksportuj notatkę", onExport)
            Spacer(Modifier.height(12.dp))
        }

        Column(Modifier.fillMaxSize()) {
            NoteHeader(
                title = document?.title.orEmpty(),
                state = saveState,
                lastSave = lastSave,
                busy = busy,
                onTitle = model::setTitle,
            )
            HorizontalRule()

            if (error != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.desk)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(KajetIcons.ErrorMark, null, tint = colors.danger, modifier = Modifier.size(18.dp))
                    Text(error.orEmpty(), style = Kajet.type.body, color = colors.text, modifier = Modifier.weight(1f))
                    SecondaryButton("Rozumiem", model::dismissError)
                }
                HorizontalRule()
            }

            FormatBar(
                appearance = appearance,
                blockMode = blockMode,
                recentColors = recentColors,
                onBlockMode = { blockMode = it },
                onFont = model::setFont,
                onFontSize = model::setFontSize,
                onTextColor = model::setTextColor,
                onAlign = model::setAlign,
                onWrap = { marker -> format { TextFormat.wrap(it, marker) } },
                onWrapPair = { opening, closing -> format { TextFormat.wrapPair(it, opening, closing) } },
                onBeforeLine = { marker -> format { TextFormat.beforeLine(it, marker) } },
                onInsert = { fragment, stepBack -> format { TextFormat.insert(it, fragment, stepBack) } },
            )
            HorizontalRule()

            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.sheet)
                    .imePadding(),
            ) {
                if (blockMode) {
                    BlockEditor(
                        blocks = blocks,
                        attachment = model::attachment,
                        onBlocksChange = { next ->
                            blocks = next
                            model.setContent(Blocks.join(next))
                        },
                        keyToFocus = keyToFocus,
                        onFocusTaken = { keyToFocus = null },
                        onBlockFocused = { key, set ->
                            focusedKey = key
                            setFocusedField = set
                        },
                        onSelection = { focusedField = it },
                        appearance = appearance,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    BasicTextField(
                        value = field,
                        onValueChange = { next ->
                            field = next
                            model.setContent(next.text)
                        },
                        textStyle = Kajet.type.code.copy(color = colors.text),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 28.dp, end = 24.dp, top = 20.dp, bottom = 120.dp)
                            .widthIn(max = Kajet.dimens.readingWidth),
                    )
                    if (field.text.isEmpty()) {
                        Text(
                            text = "Surowy zapis notatki. Wróć do widoku treści, żeby zobaczyć formatowanie.",
                            style = Kajet.type.code,
                            color = colors.muted,
                            modifier = Modifier.padding(start = 28.dp, top = 20.dp),
                        )
                    }
                }
            }
        }
    }

    if (drawing) {
        DrawingDialog(
            onClose = model::closeDrawing,
            onDone = { strokes, width, height ->
                // Rysunek ma trafić za blok, w którym stoi kursor, a nie
                // na koniec całej notatki.
                val position = if (blockMode) {
                    focusedKey?.let { Blocks.endPosition(blocks, it) } ?: model.markdown.length
                } else {
                    field.selection.start
                }
                model.insertDrawing(strokes, width, height, position)
            },
        )
    }
}

@Composable
private fun NoteHeader(
    title: String,
    state: SaveState,
    lastSave: Long?,
    busy: String?,
    onTitle: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Kajet.colors.sheet)
            .padding(start = 28.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicTextField(
            value = title,
            onValueChange = onTitle,
            singleLine = true,
            textStyle = Kajet.type.display.copy(color = Kajet.colors.text),
            cursorBrush = SolidColor(Kajet.colors.accent),
            modifier = Modifier.weight(1f),
            decorationBox = { field ->
                if (title.isEmpty()) {
                    Text("Bez nazwy", style = Kajet.type.display, color = Kajet.colors.muted)
                }
                field()
            },
        )
        if (busy != null) {
            Text(busy, style = Kajet.type.meta, color = Kajet.colors.muted)
        }
        SaveIndicator(state = state, lastSave = lastSave)
    }
}

@Composable
private fun FormatBar(
    appearance: TextContent,
    blockMode: Boolean,
    recentColors: List<Int>,
    onBlockMode: (Boolean) -> Unit,
    onFont: (NoteFont) -> Unit,
    onFontSize: (Float) -> Unit,
    onTextColor: (Int) -> Unit,
    onAlign: (NoteAlign) -> Unit,
    onWrap: (String) -> Unit,
    onWrapPair: (String, String) -> Unit,
    onBeforeLine: (String) -> Unit,
    onInsert: (fragment: String, stepBack: Int) -> Unit,
) {
    var fontPicker by remember { mutableStateOf(false) }
    var wholeNoteColour by remember { mutableStateOf(false) }
    var selectionColour by remember { mutableStateOf(false) }

    val size = if (appearance.fontSize > 0f) appearance.fontSize else TextContent.DEFAULT_SIZE

    Column(
        Modifier
            .fillMaxWidth()
            .background(Kajet.colors.desk),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Krój pisma całej notatki.
            IconAction(
                icon = KajetIcons.Letters,
                description = "Krój pisma: ${appearance.font.labelPl}",
                onClick = { fontPicker = !fontPicker },
                selected = fontPicker,
                iconSize = 18.dp,
            )
            Text(
                text = appearance.font.labelPl,
                style = Kajet.type.label,
                color = Kajet.colors.muted,
                modifier = Modifier.padding(end = 4.dp),
            )

            Divider()

            // Wielkość pisma.
            IconAction(
                icon = KajetIcons.TextSize,
                description = "Wielkość pisma",
                onClick = { onFontSize(TextContent.DEFAULT_SIZE) },
                iconSize = 18.dp,
            )
            FormatGlyph("−", "Mniejsze pismo", { onFontSize(size - 1f) })
            Text(
                text = "${size.roundToInt()}",
                style = Kajet.type.label,
                color = Kajet.colors.text,
                modifier = Modifier.width(24.dp),
            )
            FormatGlyph("+", "Większe pismo", { onFontSize(size + 1f) })

            Divider()

            // Kolor: całej notatki i samego zaznaczenia.
            ColourDot(
                color = if (appearance.textColor != 0) appearance.textColor else Kajet.colors.text.toArgb(),
                description = "Kolor pisma całej notatki",
                onClick = { wholeNoteColour = true },
            )
            IconAction(
                icon = KajetIcons.TextColour,
                description = "Kolor zaznaczonego fragmentu",
                onClick = { selectionColour = true },
                iconSize = 18.dp,
            )
            IconAction(
                icon = KajetIcons.Highlight,
                description = "Wyróżnij zaznaczony fragment",
                onClick = { onWrap("==") },
                iconSize = 18.dp,
            )

            Divider()

            IconAction(
                icon = KajetIcons.Bold,
                description = "Pogrubienie",
                onClick = { onWrap("**") },
                iconSize = 18.dp,
            )
            IconAction(
                icon = KajetIcons.Italic,
                description = "Kursywa",
                onClick = { onWrap("*") },
                iconSize = 18.dp,
            )
            IconAction(
                icon = KajetIcons.Underline,
                description = "Podkreślenie",
                onClick = { onWrapPair("<u>", "</u>") },
                iconSize = 18.dp,
            )
            IconAction(
                icon = KajetIcons.Strikethrough,
                description = "Przekreślenie",
                onClick = { onWrap("~~") },
                iconSize = 18.dp,
            )

            Divider()

            NoteAlign.entries.forEach { variant ->
                IconAction(
                    icon = when (variant) {
                        NoteAlign.LEFT -> KajetIcons.AlignLeft
                        NoteAlign.CENTER -> KajetIcons.AlignCentre
                        NoteAlign.RIGHT -> KajetIcons.AlignRight
                    },
                    description = variant.labelPl,
                    onClick = { onAlign(variant) },
                    selected = appearance.align == variant,
                    iconSize = 18.dp,
                )
            }
        }

        if (fontPicker) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SegmentedChoice(
                    options = NoteFont.entries,
                    selected = appearance.font,
                    name = { it.labelPl },
                    onSelect = { font ->
                        onFont(font)
                        fontPicker = false
                    },
                    modifier = Modifier.width(360.dp),
                )
            }
        }

        HorizontalRule()

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            FormatGlyph("H1", "Nagłówek największy", { onBeforeLine("# ") }, bold = true)
            FormatGlyph("H2", "Nagłówek średni", { onBeforeLine("## ") }, bold = true)
            FormatGlyph("H3", "Nagłówek mały", { onBeforeLine("### ") }, bold = true)

            Divider()

            IconAction(
                icon = KajetIcons.BulletList,
                description = "Lista",
                onClick = { onBeforeLine("- ") },
                iconSize = 18.dp,
            )
            IconAction(
                icon = KajetIcons.NumberedList,
                description = "Lista numerowana",
                onClick = { onBeforeLine("1. ") },
                iconSize = 18.dp,
            )
            IconAction(
                icon = KajetIcons.TaskList,
                description = "Lista zadań, na przykład lista zakupów",
                onClick = { onBeforeLine("- [ ] ") },
                iconSize = 18.dp,
            )
            IconAction(
                icon = KajetIcons.Quote,
                description = "Cytat",
                onClick = { onBeforeLine("> ") },
                iconSize = 18.dp,
            )

            Divider()

            FormatGlyph("`", "Kod w tekście", { onWrap("`") })
            IconAction(
                icon = KajetIcons.CodeFile,
                description = "Blok kodu",
                // Kursor ma stanąć w środku, między znacznikami, bo tam pisze się kod.
                onClick = { onInsert("\n```\n\n```\n", 5) },
                iconSize = 18.dp,
            )
            IconAction(
                icon = KajetIcons.TableGrid,
                description = "Tabela",
                onClick = { onInsert(TABLE_TEMPLATE, TABLE_TEMPLATE.length - 12) },
                iconSize = 18.dp,
            )
            FormatGlyph("Σ", "Wzór matematyczny", { onInsert("\n$$\n\n$$\n", 4) })
            IconAction(
                icon = KajetIcons.LinkChain,
                description = "Odnośnik",
                onClick = { onInsert("[opis](https://)", 1) },
                iconSize = 18.dp,
            )
            IconAction(
                icon = KajetIcons.DividerLine,
                description = "Linia oddzielająca",
                onClick = { onInsert("\n---\n", 0) },
                iconSize = 18.dp,
            )

            Divider()

            IconAction(
                icon = KajetIcons.CodeFile,
                description = if (blockMode) "Pokaż surowy zapis Markdown" else "Wróć do widoku treści",
                onClick = { onBlockMode(!blockMode) },
                selected = !blockMode,
                iconSize = 18.dp,
            )
        }
    }

    if (wholeNoteColour) {
        ColourPickerDialog(
            title = "Kolor pisma w całej notatce",
            color = if (appearance.textColor != 0) appearance.textColor else Kajet.colors.text.toArgb(),
            onChange = onTextColor,
            onClose = { wholeNoteColour = false },
            withAlpha = false,
            presetColors = InkPalette.pens,
            recentColors = recentColors,
        )
    }

    if (selectionColour) {
        // Kolor zaznaczenia bierzemy dopiero przy zamknięciu okna. Okno oddaje
        // barwę przy każdym ruchu palca po kwadracie, a otaczanie fragmentu
        // znacznikiem przy każdym ruchu obłożyłoby go nimi kilkadziesiąt razy.
        var picked by remember {
            mutableStateOf(if (appearance.textColor != 0) appearance.textColor else 0)
        }
        ColourPickerDialog(
            title = "Kolor zaznaczonego fragmentu",
            color = if (picked != 0) picked else Kajet.colors.text.toArgb(),
            onChange = { picked = it },
            onClose = {
                selectionColour = false
                if (picked != 0) {
                    onWrapPair(
                        "<span style=\"color:${TextMarkers.colorHex(picked)}\">",
                        "</span>",
                    )
                }
            },
            withAlpha = false,
            presetColors = InkPalette.pens,
            recentColors = recentColors,
        )
    }
}

private const val TABLE_TEMPLATE = "\n| Kolumna | Kolumna |\n| --- | --- |\n|  |  |\n"

@Composable
private fun Divider() {
    Box(
        Modifier
            .width(1.dp)
            .height(24.dp)
            .background(Kajet.colors.line),
    )
}

@Composable
private fun FormatGlyph(
    glyph: String,
    description: String,
    onClick: () -> Unit,
    bold: Boolean = false,
    italic: Boolean = false,
) {
    Box(
        Modifier
            .size(48.dp)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = Kajet.type.titleSmall.copy(
                fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else null,
                fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else null,
            ),
            color = Kajet.colors.text,
        )
    }
}
