package wojtoteka.ovh.kajet.editor.handwriting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.ImageElement
import wojtoteka.ovh.kajet.core.model.NotePage
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.ink.StrokeCanvas
import kotlin.math.roundToInt

/**
 * Ramka ZAZNACZONEGO zdjęcia. Samo zdjęcie rysuje kartka ([StrokeCanvas])
 * pod atramentem — tu jest tylko obrys, uchwyt rozmiaru i kosz, i to
 * wyłącznie dla zdjęcia wskazanego stuknięciem. Dzięki temu nakładka nie
 * zabiera rysika reszcie kartki: po niezaznaczonym zdjęciu pisze się
 * jak po papierze.
 */
@Composable
fun ImageFrameOnPage(
    pages: List<NotePage>,
    offsetX: Float,
    offsetY: Float,
    zoom: Float,
    edited: String?,
    onChange: (page: Int, image: ImageElement, toHistory: Boolean) -> Unit,
    onCommit: (page: Int, before: ImageElement, after: ImageElement) -> Unit,
    onDelete: (page: Int, id: String) -> Unit,
) {
    if (edited == null) return
    var top = 0f
    pages.forEachIndexed { index, sheet ->
        val pageTop = top
        sheet.images.firstOrNull { it.id == edited }?.let { image ->
            ImageFrame(
                image = image,
                pageTop = pageTop,
                offsetX = offsetX,
                offsetY = offsetY,
                zoom = zoom,
                onChange = { next, toHistory -> onChange(index, next, toHistory) },
                onCommit = { before, after -> onCommit(index, before, after) },
                onDelete = { onDelete(index, image.id) },
            )
        }
        top += sheet.height + StrokeCanvas.PAGE_GAP
    }
}

@Composable
private fun ImageFrame(
    image: ImageElement,
    pageTop: Float,
    offsetX: Float,
    offsetY: Float,
    zoom: Float,
    onChange: (ImageElement, Boolean) -> Unit,
    onCommit: (before: ImageElement, after: ImageElement) -> Unit,
    onDelete: () -> Unit,
) {
    val words = LocalStrings.current
    val density = LocalDensity.current
    val left = (image.x - offsetX) * zoom
    val top = (image.y + pageTop - offsetY) * zoom

    // Gest czyta zdjęcie przez rememberUpdatedState — patrz uwaga w TextBoxes.
    val current by rememberUpdatedState(image)
    val currentZoom by rememberUpdatedState(zoom)

    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(
                width = with(density) { (image.width * zoom).toDp() },
                height = with(density) { (image.height * zoom).toDp() },
            )
            .border(2.dp, Kajet.colors.accent)
            .pointerInput(image.id) {
                var base: ImageElement? = null
                var acc = Offset.Zero
                var latest: ImageElement? = null
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
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
                .background(Kajet.colors.accent)
                .pointerInput(image.id) {
                    detectTapGestures(onTap = { onDelete() })
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                KajetIcons.Bin,
                contentDescription = words.photoRemove,
                tint = Kajet.colors.onAccent,
                modifier = Modifier.size(16.dp),
            )
        }

        // Rozmiar w rogu. Proporcje zostają — zdjęcie się skaluje, nie rozjeżdża.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(28.dp)
                .background(Kajet.colors.accent)
                .pointerInput(image.id) {
                    var base: ImageElement? = null
                    var acc = Offset.Zero
                    var latest: ImageElement? = null
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
                        val width = (from.width + acc.x / currentZoom).coerceAtLeast(40f)
                        val height = if (from.width > 0f) width * from.height / from.width else width
                        val resized = from.copy(width = width, height = height)
                        latest = resized
                        onChange(resized, false)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                KajetIcons.FitToView,
                contentDescription = words.photoSize,
                tint = Kajet.colors.onAccent,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
