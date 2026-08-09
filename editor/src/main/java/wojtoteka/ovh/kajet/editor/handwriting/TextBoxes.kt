package wojtoteka.ovh.kajet.editor.handwriting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.fontFamilyFor
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.NotePage
import wojtoteka.ovh.kajet.core.model.TextBoxElement
import wojtoteka.ovh.kajet.core.model.NoteAlign
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.editor.penWritingSurface
import wojtoteka.ovh.kajet.ink.StrokeCanvas
import kotlin.math.roundToInt

@Composable
fun TextBoxesOnPage(
    pages: List<NotePage>,
    offsetX: Float,
    offsetY: Float,
    zoom: Float,
    edited: String?,
    onEdit: (String?) -> Unit,
    onChange: (page: Int, box: TextBoxElement, toHistory: Boolean) -> Unit,
    onCommit: (page: Int, before: TextBoxElement, after: TextBoxElement) -> Unit,
    onDelete: (page: Int, id: String) -> Unit,
) {
    var top = 0f
    pages.forEachIndexed { index, sheet ->
        val pageTop = top
        sheet.texts.forEach { box ->
            TextBoxOnSheet(
                box = box,
                pageTop = pageTop,
                offsetX = offsetX,
                offsetY = offsetY,
                zoom = zoom,
                edited = edited == box.id,
                onEdit = { onEdit(if (edited == box.id) null else box.id) },
                onChange = { next, toHistory -> onChange(index, next, toHistory) },
                onCommit = { before, after -> onCommit(index, before, after) },
                onDelete = { onDelete(index, box.id) },
            )
        }
        top += sheet.height + StrokeCanvas.PAGE_GAP
    }
}

@Composable
private fun TextBoxOnSheet(
    box: TextBoxElement,
    pageTop: Float,
    offsetX: Float,
    offsetY: Float,
    zoom: Float,
    edited: Boolean,
    onEdit: () -> Unit,
    onChange: (TextBoxElement, Boolean) -> Unit,
    onCommit: (before: TextBoxElement, after: TextBoxElement) -> Unit,
    onDelete: () -> Unit,
) {
    val words = LocalStrings.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val left = (box.x - offsetX) * zoom
    val top = (box.y + pageTop - offsetY) * zoom
    val width = box.width * zoom
    val height = box.height * zoom
    val focus = remember { FocusRequester() }

    /*
      Gesty czytają pole przez rememberUpdatedState. Lambda w pointerInput
      rusza raz i żyje dalej ze starym `box` w garści — liczenie od niego
      dawało pole drgające o jeden krok, a zapis do historii na końcu gestu
      wpisywał położenie SPRZED przeciągnięcia i cofał cały ruch.
    */
    val current by rememberUpdatedState(box)
    val currentZoom by rememberUpdatedState(zoom)

    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(
                width = with(density) { width.toDp() },
                height = with(density) { height.toDp() },
            )
            .then(
                if (box.background != 0) Modifier.background(Color(box.background)) else Modifier,
            )
            .then(if (edited) Modifier.border(1.dp, Kajet.colors.accent) else Modifier)
            .then(
                /*
                  Nieedytowane pole nie może łapać dotyku: composable nad kanwą
                  zabiera cały gest i nie dało się zacząć kreski na tekście ani
                  na bloku CODE. Rysowanie po polu idzie do kartki, a stuknięcie
                  w pole zgłasza kartka przez textTapped.
                */
                if (edited) {
                    // Edytowane pole zabiera najechanie kartce, a pisze się
                    // w nim rysikiem tak samo — więc też jest powierzchnią.
                    Modifier
                        .penWritingSurface(context)
                        .pointerInput(box.id) {
                            detectTapGestures(onTap = { onEdit() })
                        }
                } else {
                    Modifier
                },
            ),
    ) {
            val style = TextStyle(
                fontFamily = fontFamilyFor(box.font),
                fontSize = with(density) { (box.fontSize * zoom).toSp() },
                color = Color(box.color),
                fontWeight = if (box.bold) FontWeight.SemiBold else FontWeight.Normal,
                fontStyle = if (box.italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = if (box.underline) TextDecoration.Underline else null,
                textAlign = when (box.align) {
                    NoteAlign.LEFT -> TextAlign.Start
                    NoteAlign.CENTER -> TextAlign.Center
                    NoteAlign.RIGHT -> TextAlign.End
                },
            )

            if (edited) {
                BasicTextField(
                    value = box.text,
                    onValueChange = { onChange(box.copy(text = it), false) },
                    textStyle = style,
                    cursorBrush = SolidColor(Kajet.colors.accent),
                    modifier = Modifier
                        .padding(4.dp)
                        .focusRequester(focus),
                )
                LaunchedEffect(box.id) { runCatching { focus.requestFocus() } }
            } else if (box.text.isNotEmpty()) {
                Text(text = box.text, style = style, modifier = Modifier.padding(4.dp))
            } else {
                Text(
                    text = words.textBox,
                    style = style.copy(color = Kajet.colors.muted),
                    modifier = Modifier.padding(4.dp),
                )
            }

            if (edited) {
                // Pasek do przesuwania i przycisk kasowania nad polem.
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .background(Kajet.colors.accent),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .pointerInput(box.id) {
                                var base: TextBoxElement? = null
                                var acc = Offset.Zero
                                var latest: TextBoxElement? = null
                                fun finish() {
                                    val from = base
                                    val to = latest
                                    if (from != null && to != null) onCommit(from, to)
                                    base = null
                                    latest = null
                                }
                                detectDragGestures(
                                    onDragStart = {
                                        base = current
                                        acc = Offset.Zero
                                        latest = null
                                    },
                                    onDragEnd = { finish() },
                                    onDragCancel = { finish() },
                                ) { change, drag ->
                                    change.consume()
                                    acc += drag
                                    val from = base ?: return@detectDragGestures
                                    val moved = from.copy(
                                        x = from.x + acc.x / currentZoom,
                                        y = from.y + acc.y / currentZoom,
                                    )
                                    latest = moved
                                    onChange(moved, false)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            KajetIcons.Move,
                            contentDescription = words.moveTextBox,
                            tint = Kajet.colors.onAccent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Box(
                        Modifier
                            .size(28.dp)
                            .pointerInput(box.id) {
                                detectTapGestures(onTap = { onDelete() })
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            KajetIcons.Bin,
                            contentDescription = words.deleteTextBox,
                            tint = Kajet.colors.onAccent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                // Handle do rozciągania w prawym dolnym rogu.
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                        .background(Kajet.colors.accent)
                        .pointerInput(box.id) {
                            var base: TextBoxElement? = null
                            var acc = Offset.Zero
                            var latest: TextBoxElement? = null
                            fun finish() {
                                val from = base
                                val to = latest
                                if (from != null && to != null) onCommit(from, to)
                                base = null
                                latest = null
                            }
                            detectDragGestures(
                                onDragStart = {
                                    base = current
                                    acc = Offset.Zero
                                    latest = null
                                },
                                onDragEnd = { finish() },
                                onDragCancel = { finish() },
                            ) { change, drag ->
                                change.consume()
                                acc += drag
                                val from = base ?: return@detectDragGestures
                                val resized = from.copy(
                                    width = (from.width + acc.x / currentZoom).coerceAtLeast(60f),
                                    height = (from.height + acc.y / currentZoom).coerceAtLeast(28f),
                                )
                                latest = resized
                                onChange(resized, false)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        KajetIcons.FitToView,
                        contentDescription = words.resizeTextBox,
                        tint = Kajet.colors.onAccent,
                        modifier = Modifier.size(14.dp),
                    )
                }
        }
    }
}
