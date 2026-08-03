package wojtoteka.ovh.kajet.editor.text

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.PlexMono
import wojtoteka.ovh.kajet.core.design.component.SectionLabel
import wojtoteka.ovh.kajet.core.design.component.IconAction
import wojtoteka.ovh.kajet.core.design.component.SecondaryButton
import wojtoteka.ovh.kajet.core.design.component.SettingSlider
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.design.fontFamilyFor
import wojtoteka.ovh.kajet.core.model.TextContent
import wojtoteka.ovh.kajet.core.model.NoteAlign

@Composable
fun BlockEditor(
    blocks: List<Block>,
    attachment: suspend (String) -> ByteArray?,
    onBlocksChange: (List<Block>) -> Unit,
    keyToFocus: String?,
    onFocusTaken: () -> Unit,
    onBlockFocused: (key: String, setField: (TextFieldValue) -> Unit) -> Unit,
    onSelection: (TextFieldValue) -> Unit,
    appearance: TextContent,
    modifier: Modifier = Modifier,
) {
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

    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 28.dp,
            end = 24.dp,
            top = 20.dp,
            bottom = 160.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(blocks, key = { it.key }) { block ->
            when (block) {
                is Block.Text -> TextBlock(
                    key = block.key,
                    content = block.content,
                    style = style,
                    inlineStyle = inlineStyle,
                    hint = if (blocks.firstOrNull()?.key == block.key) {
                        "Zacznij pisać. Formatowanie widać od razu w treści."
                    } else {
                        null
                    },
                    focused = keyToFocus == block.key,
                    onFocusTaken = onFocusTaken,
                    onContent = { onBlocksChange(Blocks.setText(blocks, block.key, it)) },
                    onBlockFocused = onBlockFocused,
                    onSelection = onSelection,
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
                        onBlocksChange(
                            if (content.contains('\n')) {
                                Blocks.splitTask(blocks, block.key, content)
                            } else {
                                Blocks.setText(blocks, block.key, content)
                            },
                        )
                    },
                    onBlockFocused = onBlockFocused,
                    onSelection = onSelection,
                )

                is Block.Image -> ImageBlock(
                    block = block,
                    attachment = attachment,
                    canMoveUp = blocks.firstOrNull()?.key != block.key,
                    canMoveDown = blocks.lastOrNull()?.key != block.key,
                    onAlt = { onBlocksChange(Blocks.setAlt(blocks, block.key, it)) },
                    onWidth = { onBlocksChange(Blocks.setImageWidth(blocks, block.key, it)) },
                    onMoveUp = { onBlocksChange(Blocks.move(blocks, block.key, up = true)) },
                    onMoveDown = { onBlocksChange(Blocks.move(blocks, block.key, up = false)) },
                    onDelete = { onBlocksChange(Blocks.remove(blocks, block.key)) },
                )
            }
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
            onValueChange = { next ->
                field = next
                onSelection(next)
                if (next.text != content) onContent(next.text)
            },
            textStyle = style,
            // To jest cała rzecz, dzięki której nie ma osobnego podglądu:
            // pogrubienie widać pogrubione tam, gdzie się je pisze.
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
) {
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
                    onClickLabel = if (block.done) "Odznacz zadanie" else "Odhacz zadanie",
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

        TextBlock(
            key = block.key,
            content = block.content,
            style = if (block.done) {
                style.copy(
                    color = Kajet.colors.muted,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                )
            } else {
                style
            },
            inlineStyle = inlineStyle,
            hint = null,
            focused = focused,
            onFocusTaken = onFocusTaken,
            onContent = onContent,
            onBlockFocused = onBlockFocused,
            onSelection = onSelection,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ImageBlock(
    block: Block.Image,
    attachment: suspend (String) -> ByteArray?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onAlt: (String) -> Unit,
    onWidth: (Float) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = Kajet.colors
    var image by remember(block.url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var failed by remember(block.url) { mutableStateOf(false) }
    var showAlt by remember { mutableStateOf(false) }
    var showSize by remember { mutableStateOf(false) }

    LaunchedEffect(block.url) {
        val name = block.attachmentName
        if (name == null) {
            failed = true
            return@LaunchedEffect
        }
        val data = runCatching { attachment(name) }.getOrNull()
        if (data == null) {
            failed = true
        } else {
            image = runCatching {
                BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
            }.getOrNull()
            failed = image == null
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = Kajet.dimens.readingWidth)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth(block.width.coerceIn(Block.SMALLEST_WIDTH, Block.FULL_WIDTH))
                .background(colors.desk, RoundedCornerShape(Kajet.dimens.corner))
                .border(1.dp, colors.line, RoundedCornerShape(Kajet.dimens.corner)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                image != null -> Image(
                    bitmap = image!!,
                    contentDescription = block.alt.ifBlank { "Zdjęcie w notatce" },
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )

                failed -> Row(
                    Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    androidx.compose.material3.Icon(
                        KajetIcons.ErrorMark,
                        contentDescription = null,
                        tint = colors.danger,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Nie znalazłem pliku ${block.url}. Zdjęcie mogło zostać skasowane z katalogu notatki.",
                        style = Kajet.type.meta,
                        color = colors.muted,
                    )
                }

                else -> Box(Modifier.fillMaxWidth().height(120.dp))
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = block.alt.ifBlank { "Bez podpisu" },
                style = Kajet.type.meta,
                color = colors.muted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(block.width * 100).roundToInt()} %",
                style = Kajet.type.meta,
                color = colors.muted,
            )
            IconAction(
                icon = KajetIcons.FitToView,
                description = "Zmień wielkość zdjęcia",
                onClick = { showSize = !showSize },
                selected = showSize,
                iconSize = 16.dp,
                touchTarget = 40.dp,
            )
            IconAction(
                icon = KajetIcons.Letters,
                description = "Zmień podpis zdjęcia",
                onClick = { showAlt = !showAlt },
                selected = showAlt,
                iconSize = 16.dp,
                touchTarget = 40.dp,
            )
            IconAction(
                icon = KajetIcons.ArrowDown,
                description = "Przesuń zdjęcie wyżej",
                onClick = onMoveUp,
                enabled = canMoveUp,
                iconSize = 16.dp,
                touchTarget = 40.dp,
            )
            IconAction(
                icon = KajetIcons.ArrowDown,
                description = "Przesuń zdjęcie niżej",
                onClick = onMoveDown,
                enabled = canMoveDown,
                iconSize = 16.dp,
                touchTarget = 40.dp,
            )
            IconAction(
                icon = KajetIcons.Bin,
                description = "Usuń zdjęcie z notatki",
                onClick = onDelete,
                iconSize = 16.dp,
                touchTarget = 40.dp,
            )
        }

        if (showSize) {
            Column {
                SettingSlider(
                    name = "Wielkość zdjęcia",
                    value = block.width.coerceIn(Block.SMALLEST_WIDTH, Block.FULL_WIDTH),
                    range = Block.SMALLEST_WIDTH..Block.FULL_WIDTH,
                    onChange = onWidth,
                    readout = { "${(it * 100).roundToInt()} %" },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.25f, 0.5f, 0.75f, 1f).forEach { part ->
                        SecondaryButton(
                            text = "${(part * 100).roundToInt()} %",
                            onClick = { onWidth(part) },
                        )
                    }
                }
            }
        }

        if (showAlt) {
            Column {
                SectionLabel("Podpis zdjęcia")
                BasicTextField(
                    value = block.alt,
                    onValueChange = onAlt,
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
                    text = "Podpis czyta czytnik ekranu i trafia do wydruku.",
                    style = Kajet.type.meta,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
