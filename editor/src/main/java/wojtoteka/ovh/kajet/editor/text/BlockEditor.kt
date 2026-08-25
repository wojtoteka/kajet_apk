package wojtoteka.ovh.kajet.editor.text

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.PlexMono
import wojtoteka.ovh.kajet.core.image.Bitmaps
import androidx.compose.ui.text.font.FontWeight
import wojtoteka.ovh.kajet.core.design.component.HorizontalRule
import wojtoteka.ovh.kajet.core.design.component.SectionLabel
import wojtoteka.ovh.kajet.core.design.component.IconAction
import wojtoteka.ovh.kajet.core.design.component.SettingSlider
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.design.fontFamilyFor
import wojtoteka.ovh.kajet.core.model.TextContent
import wojtoteka.ovh.kajet.core.model.ImageLines
import wojtoteka.ovh.kajet.core.model.NoteAlign
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.core.text.percentOf
import wojtoteka.ovh.kajet.core.text.photoOfMany
import wojtoteka.ovh.kajet.core.text.photoNotFound

@Composable
fun BlockEditor(
    blocks: List<Block>,
    attachment: suspend (String) -> ByteArray?,
    onBlocksChange: (List<Block>) -> Unit,
    /**
     * Stuknięcie w pustą kartkę pod tekstem. Notatka ma wtedy wejść w pisanie
     * w ostatnim akapicie, a nie zostawić klawiaturę przy tytule.
     */
    onTapBelow: () -> Unit,
    /**
     * Zdjęcie wskazane stuknięciem. Dopiero ono ma obwódkę, uchwyt rozmiaru
     * i przyciski - niezaznaczone zdjęcie jest samym zdjęciem, jak w notatce
     * odręcznej.
     */
    selectedPhoto: String?,
    onSelectPhoto: (String?) -> Unit,
    keyToFocus: String?,
    onFocusTaken: () -> Unit,
    onBlockFocused: (key: String, setField: (TextFieldValue) -> Unit) -> Unit,
    /** Kursor ma stanąć w tym bloku - na przykład w świeżej pozycji listy. */
    onFocusBlock: (key: String) -> Unit,
    onSelection: (TextFieldValue) -> Unit,
    /**
     * Dopisany tekst przechodzi tędy, zanim trafi do pola. Dzięki temu format
     * zapamiętany na pasku narzędzi obejmuje właśnie to, co człowiek napisał.
     * Null znaczy „zostaw tak, jak przyszło z klawiatury".
     */
    onTyped: (previous: String, typed: TextFieldValue) -> TextFieldValue?,
    appearance: TextContent,
    modifier: Modifier = Modifier,
) {
    val words = LocalStrings.current
    val colors = Kajet.colors
    val style = noteStyle(appearance)
    val inlineStyle = remember(colors, appearance.font) {
        InlineStyle(
            textColor = colors.text,
            markerColor = colors.muted,
            highlightColor = colors.accentWash,
            codeColor = colors.accent,
            monoFont = PlexMono,
        )
    }

    // Zdjęcia stojące obok siebie muszą trafić do JEDNEGO pola listy - inaczej
    // nie ma ich jak postawić w rzędzie. Reszta bloków idzie sama, tak jak szła.
    val rows = remember(blocks) { Blocks.rows(blocks) }

    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 28.dp,
            end = 24.dp,
            top = 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(rows, key = { it.first().key }) { group ->
            val block = group.first()
            when (block) {
                is Block.Text -> TextBlock(
                    key = block.key,
                    content = block.content,
                    style = style,
                    inlineStyle = inlineStyle,
                    hint = if (blocks.firstOrNull()?.key == block.key) {
                        words.writeHere
                    } else {
                        null
                    },
                    focused = keyToFocus == block.key,
                    onFocusTaken = onFocusTaken,
                    onContent = { onBlocksChange(Blocks.setText(blocks, block.key, it)) },
                    // Kursor w tekście kończy pracę przy zdjęciu - obwódka
                    // i przyciski nie mają po co zostawać na ekranie.
                    onBlockFocused = { key, set ->
                        onSelectPhoto(null)
                        onBlockFocused(key, set)
                    },
                    onSelection = onSelection,
                    onTyped = onTyped,
                )

                is Block.Task -> TaskBlock(
                    block = block,
                    style = style,
                    inlineStyle = inlineStyle,
                    focused = keyToFocus == block.key,
                    onFocusTaken = onFocusTaken,
                    onToggle = { onBlocksChange(Blocks.toggleTask(blocks, block.key)) },
                    onContent = { content ->
                        // Klawisz nowej linii w zadaniu zaczyna następne zadanie,
                        // a nie zwykły akapit. Tak działa każda lista zakupów.
                        // Kursor musi przy tym przejść do NOWEJ pozycji - inaczej
                        // dalsze pisanie doklejało się do poprzedniej.
                        if (content.contains('\n')) {
                            val split = Blocks.splitTask(blocks, block.key, content)
                            onBlocksChange(split.blocks)
                            onFocusBlock(split.focusKey)
                        } else {
                            onBlocksChange(Blocks.setText(blocks, block.key, content))
                        }
                    },
                    onBlockFocused = { key, set ->
                        onSelectPhoto(null)
                        onBlockFocused(key, set)
                    },
                    onSelection = onSelection,
                    onTyped = onTyped,
                )

                is Block.Table -> TableBlock(
                    block = block,
                    style = style,
                    inlineStyle = inlineStyle,
                    onCell = { row, column, text ->
                        onBlocksChange(Blocks.setCell(blocks, block.key, row, column, text))
                    },
                    onAddRow = { onBlocksChange(Blocks.addRow(blocks, block.key, block.rows.size - 1)) },
                    onAddColumn = {
                        onBlocksChange(Blocks.addColumn(blocks, block.key, block.columns - 1))
                    },
                    onRemoveRow = { row -> onBlocksChange(Blocks.removeRow(blocks, block.key, row)) },
                    onRemoveColumn = { column ->
                        onBlocksChange(Blocks.removeColumn(blocks, block.key, column))
                    },
                    onDelete = { onBlocksChange(Blocks.remove(blocks, block.key)) },
                )

                is Block.Image -> ImageRowBlock(
                    photos = group.filterIsInstance<Block.Image>(),
                    attachment = attachment,
                    selectedKey = selectedPhoto,
                    onSelect = onSelectPhoto,
                    canMoveUp = { key -> blocks.firstOrNull()?.key != key },
                    canMoveDown = { key -> blocks.lastOrNull()?.key != key },
                    canStandBeside = { key -> Blocks.canStandBeside(blocks, key) },
                    standsInRow = { key -> Blocks.standsInRow(blocks, key) },
                    onNudge = { key, nudge ->
                        onBlocksChange(Blocks.nudgePhoto(blocks, key, nudge))
                    },
                    onAlt = { key, alt -> onBlocksChange(Blocks.setAlt(blocks, key, alt)) },
                    onWidth = { key, width -> onBlocksChange(Blocks.setImageWidth(blocks, key, width)) },
                    onAlign = { key, align -> onBlocksChange(Blocks.setImageAlign(blocks, key, align)) },
                    onSideBySide = { key, beside ->
                        onBlocksChange(Blocks.setSideBySide(blocks, key, beside))
                    },
                    onMoveUp = { key -> onBlocksChange(Blocks.move(blocks, key, up = true)) },
                    onMoveDown = { key -> onBlocksChange(Blocks.move(blocks, key, up = false)) },
                    onDelete = { key ->
                        onSelectPhoto(null)
                        onBlocksChange(Blocks.remove(blocks, key))
                    },
                )
            }
        }

        /*
          Reszta kartki pod tekstem. Dawniej był to sam odstęp, więc stuknięcie
          w puste miejsce pod ostatnim akapitem nie robiło nic - a w świeżej
          notatce skupienie zostawało przy tytule i pisany tekst szedł do
          tytułu. Teraz puste miejsce jest częścią kartki: dotknięcie stawia
          kursor w ostatnim akapicie, tak jak w każdym zeszycie.
        */
        item(key = "kartka-ponizej") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clickable(
                        // Bez podświetlenia i bez skupienia: to kartka, a nie
                        // przycisk. Skupienie ma wziąć pole tekstu, nie tło.
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = words.writeHere,
                        onClick = {
                            onSelectPhoto(null)
                            onTapBelow()
                        },
                    ),
            )
        }
    }
}

@Composable
fun noteStyle(appearance: TextContent): TextStyle = Kajet.type.bodyLarge.copy(
    fontFamily = fontFamilyFor(appearance.font),
    fontSize = if (appearance.fontSize > 0f) appearance.fontSize.sp else Kajet.type.bodyLarge.fontSize,
    lineHeight = if (appearance.fontSize > 0f) (appearance.fontSize * 1.6f).sp else Kajet.type.bodyLarge.lineHeight,
    color = if (appearance.textColor != 0) Color(appearance.textColor) else Kajet.colors.text,
    textAlign = when (appearance.align) {
        NoteAlign.LEFT -> TextAlign.Start
        NoteAlign.CENTER -> TextAlign.Center
        NoteAlign.RIGHT -> TextAlign.End
    },
)

@Composable
private fun TextBlock(
    key: String,
    content: String,
    style: TextStyle,
    inlineStyle: InlineStyle,
    hint: String?,
    focused: Boolean,
    onFocusTaken: () -> Unit,
    onContent: (String) -> Unit,
    onBlockFocused: (String, (TextFieldValue) -> Unit) -> Unit,
    onSelection: (TextFieldValue) -> Unit,
    onTyped: (previous: String, typed: TextFieldValue) -> TextFieldValue?,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }

    // Own field state, so the cursor does not jump to the start on every recount
    // of the blocks. Only the text goes to the model.
    var field by remember(key) {
        mutableStateOf(TextFieldValue(content, TextRange(content.length)))
    }

    // Treść mogła zmienić się poza polem, na przykład przez cofnięcie.
    if (field.text != content) {
        field = field.copy(
            text = content,
            selection = TextRange(field.selection.start.coerceAtMost(content.length)),
        )
    }

    Box(modifier.fillMaxWidth()) {
        BasicTextField(
            value = field,
            onValueChange = { typed ->
                // Zapamiętany format nakłada się TU, na dopiero co wpisany
                // kawałek. Pole zostaje to samo - nie ma mowy o ustawianiu
                // treści od nowa, bo wtedy kursor skakałby na początek.
                val next = onTyped(field.text, typed) ?: typed
                field = next
                onSelection(next)
                if (next.text != content) onContent(next.text)
            },
            textStyle = style,
            // To jest cała rzecz, dzięki której nie ma osobnego podglądu:
            // pogrubienie widać pogrubione tam, gdzie się je pisze, a surowych
            // znaczników nie widać nigdy - także pod kursorem.
            visualTransformation = inlineStyle,
            cursorBrush = SolidColor(Kajet.colors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = Kajet.dimens.readingWidth)
                .heightIn(min = 32.dp)
                .focusRequester(focus)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        // Oddajemy w górę sposób ustawienia tego pola, żeby pasek
                        // formatowania mógł wstawić znacznik i wrócić z kursorem
                        // dokładnie tam, gdzie człowiek go zostawił.
                        onBlockFocused(key) { next -> field = next }
                        onSelection(field)
                    }
                },
        )

        if (content.isEmpty() && hint != null) {
            Text(text = hint, style = style, color = Kajet.colors.muted)
        }
    }

    if (focused) {
        LaunchedEffect(key) {
            runCatching { focus.requestFocus() }
            onFocusTaken()
        }
    }
}

@Composable
private fun TaskBlock(
    block: Block.Task,
    style: TextStyle,
    inlineStyle: InlineStyle,
    focused: Boolean,
    onFocusTaken: () -> Unit,
    onToggle: () -> Unit,
    onContent: (String) -> Unit,
    onBlockFocused: (String, (TextFieldValue) -> Unit) -> Unit,
    onSelection: (TextFieldValue) -> Unit,
    onTyped: (previous: String, typed: TextFieldValue) -> TextFieldValue?,
) {
    val words = LocalStrings.current
    Row(
        Modifier
            .fillMaxWidth()
            .widthIn(max = Kajet.dimens.readingWidth),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clickable(
                    role = Role.Checkbox,
                    onClickLabel = if (block.done) words.untickTask else words.tickTask,
                    onClick = onToggle,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .background(
                        if (block.done) Kajet.colors.accent else Color.Transparent,
                        RoundedCornerShape(5.dp),
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (block.done) Kajet.colors.accent else Kajet.colors.muted,
                        shape = RoundedCornerShape(5.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (block.done) {
                    androidx.compose.material3.Icon(
                        imageVector = KajetIcons.Confirm,
                        contentDescription = null,
                        tint = Kajet.colors.sheet,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        /*
          Zadanie czyta się zawsze od lewej, obok swojego kwadracika.
          Wyśrodkowanie notatki dotyczy akapitów, nie listy: inaczej kwadracik
          zostawał przy krawędzi, a jego treść uciekała na środek i wyglądały
          jak dwie niezwiązane rzeczy.
        */
        val taskStyle = style.copy(textAlign = TextAlign.Start)

        TextBlock(
            key = block.key,
            content = block.content,
            style = if (block.done) {
                taskStyle.copy(
                    color = Kajet.colors.muted,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                )
            } else {
                taskStyle
            },
            inlineStyle = inlineStyle,
            hint = null,
            focused = focused,
            onFocusTaken = onFocusTaken,
            onContent = onContent,
            onBlockFocused = onBlockFocused,
            onSelection = onSelection,
            onTyped = onTyped,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * Tabelka jako tabelka, a nie jako wiersze pełne kresek.
 *
 * W pliku notatki nadal leży zwykły markdown (`| Kolumna | Kolumna |`), więc
 * ani serwer, ani eksport nic o tej zmianie nie muszą wiedzieć. Zmienia się
 * tylko to, co widać: siatka z komórkami do pisania.
 *
 * Pasek formatowania działa na akapity, nie na komórki - pogrubienie
 * w komórce wpisuje się na razie znacznikami, tak jak w surowym Markdownie.
 */
@Composable
private fun TableBlock(
    block: Block.Table,
    style: TextStyle,
    inlineStyle: InlineStyle,
    onCell: (row: Int, column: Int, text: String) -> Unit,
    onAddRow: () -> Unit,
    onAddColumn: () -> Unit,
    onRemoveRow: (Int) -> Unit,
    onRemoveColumn: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val words = LocalStrings.current
    val colors = Kajet.colors
    val columns = block.columns.coerceAtLeast(1)
    var chosenRow by remember(block.key) { mutableStateOf(0) }
    var chosenColumn by remember(block.key) { mutableStateOf(0) }

    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = Kajet.dimens.readingWidth)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, colors.line, RoundedCornerShape(Kajet.dimens.corner)),
        ) {
            block.rows.indices.forEach { row ->
                if (row > 0) HorizontalRule()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    for (column in 0 until columns) {
                        if (column > 0) {
                            Box(
                                Modifier
                                    .width(1.dp)
                                    .height(44.dp)
                                    .background(colors.line),
                            )
                        }
                        TableCell(
                            text = block.cell(row, column),
                            style = if (row == 0) {
                                style.copy(fontWeight = FontWeight.SemiBold)
                            } else {
                                style
                            },
                            inlineStyle = inlineStyle,
                            onText = { onCell(row, column, it) },
                            onFocused = {
                                chosenRow = row
                                chosenColumn = column
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Na wąskim ekranie rozmiar, dodawanie i kosze nie mieszczą się obok
        // siebie. Przewijanie w bok jak w FormatBar; akcje tekstowe jak
        // „Wyczyść" w konsoli - SecondaryButton 48 dp z obwódką łamał etykiety.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = words.tableSize(block.rows.size, columns),
                style = Kajet.type.meta,
                color = colors.muted,
                maxLines = 1,
            )
            BarTextAction(words.tableAddRow, onAddRow)
            BarTextAction(words.tableAddColumn, onAddColumn)
            IconAction(
                icon = KajetIcons.Bin,
                description = words.tableRemoveRow,
                onClick = { onRemoveRow(chosenRow) },
                enabled = block.rows.size > 1,
                iconSize = 16.dp,
                touchTarget = 40.dp,
            )
            IconAction(
                icon = KajetIcons.Bin,
                description = words.tableRemoveColumn,
                onClick = { onRemoveColumn(chosenColumn) },
                enabled = columns > 1,
                iconSize = 16.dp,
                touchTarget = 40.dp,
            )
            IconAction(
                icon = KajetIcons.Bin,
                description = words.tableRemove,
                onClick = onDelete,
                iconSize = 16.dp,
                touchTarget = 40.dp,
            )
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    style: TextStyle,
    inlineStyle: InlineStyle,
    onText: (String) -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var field by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    if (field.text != text) {
        field = field.copy(
            text = text,
            selection = TextRange(field.selection.start.coerceAtMost(text.length)),
        )
    }

    BasicTextField(
        value = field,
        onValueChange = { next ->
            field = next
            if (next.text != text) onText(next.text)
        },
        textStyle = style,
        visualTransformation = inlineStyle,
        cursorBrush = SolidColor(Kajet.colors.accent),
        modifier = modifier
            .heightIn(min = 44.dp)
            .padding(horizontal = 10.dp, vertical = 12.dp)
            .onFocusChanged { if (it.isFocused) onFocused() },
    )
}

/**
 * Wiersz ze zdjęciami: jedno albo kilka obok siebie. W pliku to jeden wiersz,
 * więc i tutaj jest to jeden blok.
 *
 * Niezaznaczone zdjęcie jest samym zdjęciem - bez obwódki, uchwytów
 * i przycisków. Dopiero stuknięcie je wybiera: wtedy dostaje obwódkę, uchwyt
 * rozmiaru w rogu, a pod wierszem staje pasek z resztą działań. Tak samo
 * działa zdjęcie na kartce odręcznej (ImageFrame.kt), więc obie notatki
 * obsługuje się tak samo.
 *
 * Przyciski stoją POD wierszem, a nie pod zdjęciem: pod zdjęciem o szerokości
 * 25% i tak by się nie zmieściły, a osobny rząd dla każdego zdjęcia zajmowałby
 * więcej miejsca niż same zdjęcia.
 */
@Composable
private fun ImageRowBlock(
    photos: List<Block.Image>,
    attachment: suspend (String) -> ByteArray?,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    canMoveUp: (String) -> Boolean,
    canMoveDown: (String) -> Boolean,
    canStandBeside: (String) -> Boolean,
    standsInRow: (String) -> Boolean,
    onNudge: (String, Blocks.PhotoNudge) -> Unit,
    onAlt: (String, String) -> Unit,
    onWidth: (String, Float) -> Unit,
    onAlign: (String, NoteAlign) -> Unit,
    onSideBySide: (String, Boolean) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val words = LocalStrings.current
    val colors = Kajet.colors
    val density = LocalDensity.current

    // Zaznaczone zdjęcie TEGO wiersza albo nic, gdy wybrane jest w innym
    // miejscu notatki. Podpis i rozmiar otwierają się przy zdjęciu, więc
    // przy zmianie wyboru mają się zamknąć - stąd klucz w remember.
    val block = photos.firstOrNull { it.key == selectedKey }
    var showAlt by remember(block?.key) { mutableStateOf(false) }
    var showSize by remember(block?.key) { mutableStateOf(false) }

    val many = photos.size > 1
    val align = photos.first().align

    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = Kajet.dimens.readingWidth)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val room = maxWidth
            val roomPx = with(density) { room.toPx() }
            // Szerokość zdjęcia to ułamek szerokości notatki, więc liczymy ją
            // sami. Sam Row podzieliłby miejsce po równo albo mierzył ułamek
            // od tego, co zostało po sąsiedzie - i 25% obok 25% nie byłoby
            // dwoma równymi zdjęciami.
            val gapShare = if (many) (PHOTO_GAP * (photos.size - 1)) / room else 0f
            val parts = ImageLines.sideBySide(photos.map { it.width }, gapShare)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PHOTO_GAP, sideOf(align)),
                verticalAlignment = Alignment.Top,
            ) {
                photos.forEachIndexed { index, photo ->
                    PhotoBox(
                        photo = photo,
                        attachment = attachment,
                        width = room * parts[index],
                        roomPx = roomPx,
                        chosen = photo.key == block?.key,
                        onChoose = { onSelect(if (photo.key == block?.key) null else photo.key) },
                        onWidth = { width -> onWidth(photo.key, width) },
                        onNudge = { nudge -> onNudge(photo.key, nudge) },
                    )
                }
            }
        }

        // Przyciski dopiero po wybraniu zdjęcia - inaczej notatka złożona ze
        // zdjęć składa się w połowie z rzędów przycisków.
        if (block == null) return@Column

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = if (many) {
                    words.photoOfMany(photos.indexOfFirst { it.key == block.key } + 1, photos.size)
                } else {
                    block.alt.ifBlank { words.noCaption }
                },
                style = Kajet.type.meta,
                color = colors.muted,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = words.percentOf((block.width * 100).roundToInt()),
                style = Kajet.type.meta,
                color = colors.muted,
            )
            IconAction(
                icon = KajetIcons.Minus,
                description = words.photoSmaller,
                onClick = { onWidth(block.key, block.width - WIDTH_STEP) },
                enabled = block.width > Block.SMALLEST_WIDTH,
                iconSize = 16.dp,
                touchTarget = 40.dp,
            )
            IconAction(
                icon = KajetIcons.Plus,
                description = words.photoBigger,
                onClick = { onWidth(block.key, block.width + WIDTH_STEP) },
                enabled = block.width < Block.FULL_WIDTH,
                iconSize = 16.dp,
                touchTarget = 40.dp,
            )
            IconAction(
                icon = KajetIcons.FitToView,
                description = words.photoSize,
                onClick = { showSize = !showSize },
                selected = showSize,
                iconSize = 16.dp,
                touchTarget = 40.dp,
            )
            IconAction(
                icon = KajetIcons.Letters,
                description = words.photoCaption,
                onClick = { showAlt = !showAlt },
                selected = showAlt,
                iconSize = 16.dp,
                touchTarget = 40.dp,
            )
            IconAction(
                icon = KajetIcons.Bin,
                description = words.photoRemove,
                onClick = { onDelete(block.key) },
                iconSize = 16.dp,
                touchTarget = 40.dp,
            )
        }

        // Ułożenie wiersza, sąsiedztwo i przesuwanie po notatce. Przewijanie
        // w bok jak w tabeli - na telefonie ten rząd się nie mieści.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            NoteAlign.entries.forEach { side ->
                IconAction(
                    icon = when (side) {
                        NoteAlign.LEFT -> KajetIcons.AlignLeft
                        NoteAlign.CENTER -> KajetIcons.AlignCentre
                        NoteAlign.RIGHT -> KajetIcons.AlignRight
                    },
                    description = side.label(words),
                    onClick = { onAlign(block.key, side) },
                    selected = align == side,
                    iconSize = 16.dp,
                    touchTarget = 40.dp,
                )
            }
            // Zdjęcie idzie obok tego, które stoi nad nim - i wraca do swojego
            // wiersza tym samym przyciskiem. Kiedy nad zdjęciem nie ma zdjęcia,
            // nie ma też obok czego stanąć.
            when {
                standsInRow(block.key) -> BarTextAction(words.photoOwnLine) {
                    onSideBySide(block.key, false)
                }

                canStandBeside(block.key) -> BarTextAction(words.photoBeside) {
                    onSideBySide(block.key, true)
                }
            }
            IconAction(
                icon = KajetIcons.ArrowDown,
                description = words.photoUp,
                onClick = { onMoveUp(block.key) },
                enabled = canMoveUp(block.key),
                iconSize = 16.dp,
                touchTarget = 40.dp,
                modifier = Modifier.rotate(180f),
            )
            IconAction(
                icon = KajetIcons.ArrowDown,
                description = words.photoDown,
                onClick = { onMoveDown(block.key) },
                enabled = canMoveDown(block.key),
                iconSize = 16.dp,
                touchTarget = 40.dp,
            )
        }

        if (showSize) {
            Column {
                SettingSlider(
                    name = words.photoSize,
                    value = block.width.coerceIn(Block.SMALLEST_WIDTH, Block.FULL_WIDTH),
                    range = Block.SMALLEST_WIDTH..Block.FULL_WIDTH,
                    onChange = { onWidth(block.key, it) },
                    readout = { words.percentOf((it * 100).roundToInt()) },
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    listOf(0.25f, 0.5f, 0.75f, 1f).forEach { part ->
                        BarTextAction(words.percentOf((part * 100).roundToInt())) {
                            onWidth(block.key, part)
                        }
                    }
                }
            }
        }

        if (showAlt) {
            Column {
                SectionLabel(words.photoCaption)
                BasicTextField(
                    value = block.alt,
                    onValueChange = { onAlt(block.key, it) },
                    singleLine = true,
                    textStyle = Kajet.type.body.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .border(1.dp, colors.line, RoundedCornerShape(Kajet.dimens.corner))
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                )
                Text(
                    text = words.photoCaptionAbout,
                    style = Kajet.type.meta,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Odstęp między zdjęciami stojącymi obok siebie. */
private val PHOTO_GAP = 8.dp

/** O tyle zmienia się szerokość zdjęcia od jednego naciśnięcia plusa i minusa. */
private const val WIDTH_STEP = 0.1f

/** Uchwyt w rogu zaznaczonego zdjęcia. */
private val HANDLE = 30.dp

/**
 * Ile palec musi przejechać, żeby zdjęcie poszło o jedno miejsce dalej.
 * Mniej znaczyłoby, że zdjęcie ucieka przy samym dotknięciu.
 */
private val NUDGE_STEP = 44.dp

private fun sideOf(align: NoteAlign): Alignment.Horizontal = when (align) {
    NoteAlign.LEFT -> Alignment.Start
    NoteAlign.CENTER -> Alignment.CenterHorizontally
    NoteAlign.RIGHT -> Alignment.End
}

/**
 * Samo zdjęcie w wierszu. Przyciski stoją pod całym wierszem, a tutaj -
 * przy zaznaczonym zdjęciu - jest obwódka i dwa uchwyty: przesuwanie palcem
 * po samym zdjęciu i rozmiar w prawym dolnym rogu.
 */
@Composable
private fun PhotoBox(
    photo: Block.Image,
    attachment: suspend (String) -> ByteArray?,
    width: Dp,
    roomPx: Float,
    chosen: Boolean,
    onChoose: () -> Unit,
    onWidth: (Float) -> Unit,
    onNudge: (Blocks.PhotoNudge) -> Unit,
) {
    val words = LocalStrings.current
    val colors = Kajet.colors
    val density = LocalDensity.current
    var image by remember(photo.url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var failed by remember(photo.url) { mutableStateOf(false) }

    /*
      Gest czyta zdjęcie i działania przez rememberUpdatedState, a liczy od
      stanu z POCZĄTKU gestu - tak samo jak ramka na kartce odręcznej. Lambda
      pointerInput żyje ze starym otoczeniem: bez tego drugie przesunięcie
      w jednym ruchu liczyłoby się od układu notatki sprzed pierwszego.
    */
    val current by rememberUpdatedState(photo)
    val resize by rememberUpdatedState(onWidth)
    val nudge by rememberUpdatedState(onNudge)
    var shift by remember(photo.key) { mutableStateOf(Offset.Zero) }
    val step = with(density) { NUDGE_STEP.toPx() }

    LaunchedEffect(photo.url) {
        val name = photo.attachmentName
        if (name == null) {
            failed = true
            return@LaunchedEffect
        }
        val data = runCatching { attachment(name) }.getOrNull()
        if (data == null) {
            failed = true
        } else {
            // Poza wątkiem głównym i w rozmiarze na ekran, nie w pełnej
            // rozdzielczości aparatu - inaczej wstawione zdjęcie zamrażało
            // przewijanie notatki, a przy kilku kończyło się brakiem pamięci.
            image = withContext(Dispatchers.IO) { Bitmaps.decode(data)?.asImageBitmap() }
            failed = image == null
        }
    }

    Box(
        Modifier
            .width(width)
            .offset { IntOffset(shift.x.roundToInt(), shift.y.roundToInt()) }
            .background(colors.desk, RoundedCornerShape(Kajet.dimens.corner))
            .border(
                width = if (chosen) 2.dp else 1.dp,
                color = if (chosen) colors.accent else colors.line,
                shape = RoundedCornerShape(Kajet.dimens.corner),
            )
            // Bez podświetlenia i bez skupienia: stuknięcie wybiera zdjęcie
            // do pracy, a nie otwiera nic.
            .focusProperties { canFocus = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = if (chosen) words.photoUnchoose else words.photoChoose,
                onClick = onChoose,
            )
            .then(
                if (!chosen) {
                    Modifier
                } else {
                    /*
                      Przesuwanie wybranego zdjęcia. Zdjęcie idzie za palcem,
                      a po minięciu progu przeskakuje o jedno miejsce i wraca
                      pod palec. Wiersz ze zdjęciami leży w poprzek, notatka
                      w pionie - dlatego kierunek liczy się osobno w bok
                      i osobno w pionie.
                    */
                    Modifier.pointerInput(photo.key) {
                        detectDragGestures(
                            onDragEnd = { shift = Offset.Zero },
                            onDragCancel = { shift = Offset.Zero },
                        ) { change, drag ->
                            change.consume()
                            val moved = shift + drag
                            when {
                                abs(moved.x) >= step && abs(moved.x) >= abs(moved.y) -> {
                                    nudge(
                                        if (moved.x > 0) {
                                            Blocks.PhotoNudge.RIGHT
                                        } else {
                                            Blocks.PhotoNudge.LEFT
                                        },
                                    )
                                    shift = Offset.Zero
                                }

                                abs(moved.y) >= step -> {
                                    nudge(
                                        if (moved.y > 0) {
                                            Blocks.PhotoNudge.DOWN
                                        } else {
                                            Blocks.PhotoNudge.UP
                                        },
                                    )
                                    shift = Offset.Zero
                                }

                                else -> shift = moved
                            }
                        }
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            image != null -> Image(
                bitmap = image!!,
                contentDescription = photo.alt.ifBlank { words.photoInNote },
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )

            failed -> Row(
                Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    KajetIcons.ErrorMark,
                    contentDescription = null,
                    tint = colors.danger,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = words.photoNotFound(photo.url),
                    style = Kajet.type.meta,
                    color = colors.muted,
                )
            }

            else -> Box(Modifier.fillMaxWidth().height(120.dp))
        }

        if (chosen) {
            // Znak, że zdjęcie da się teraz wziąć palcem. Przesuwa się je
            // po całym zdjęciu, więc róg tylko na to wskazuje.
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .size(HANDLE)
                    .background(colors.accent)
                    // Uchwyt nie jest przyciskiem: stuknięcie w niego ma nie
                    // zdejmować zaznaczenia ze zdjęcia.
                    .pointerInput(photo.key) { detectTapGestures { } },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    KajetIcons.Move,
                    contentDescription = words.photoMove,
                    tint = colors.onAccent,
                    modifier = Modifier.size(16.dp),
                )
            }

            // Rozmiar ciągnięty za róg. Proporcje zostają - zdjęcie się skaluje,
            // nie rozjeżdża, bo szerokość jest jedyną zapisaną liczbą.
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(HANDLE)
                    .background(colors.accent)
                    // Stuknięcie w uchwyt ma zostać przy uchwycie, a nie zdjąć
                    // zaznaczenia ze zdjęcia.
                    .pointerInput(photo.key) { detectTapGestures { } }
                    .pointerInput(photo.key) {
                        var base = 0f
                        var acc = 0f
                        detectDragGestures(
                            onDragStart = {
                                base = current.width
                                acc = 0f
                            },
                        ) { change, drag ->
                            change.consume()
                            if (roomPx <= 0f) return@detectDragGestures
                            acc += drag.x
                            resize(base + acc / roomPx)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    KajetIcons.FitToView,
                    contentDescription = words.photoSize,
                    tint = colors.onAccent,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/*
  Akcja paska, nie SecondaryButton. Ten ma 48 dp i obwódkę - w ciasnym
  rzędzie tabeli odcinał się od tła i na telefonie łamał etykiety
  („Dodaj wiersz", „Dodaj kolumnę"). Tu ten sam krój co rozmiar tabeli,
  bez ramki.
*/
@Composable
private fun BarTextAction(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .height(48.dp)
            .focusProperties { canFocus = false }
            .clickable(
                onClick = onClick,
                onClickLabel = text,
                role = Role.Button,
            )
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = Kajet.type.label,
            color = Kajet.colors.muted,
            maxLines = 1,
        )
    }
}
