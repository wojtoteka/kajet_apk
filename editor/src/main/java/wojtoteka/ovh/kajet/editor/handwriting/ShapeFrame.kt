package wojtoteka.ovh.kajet.editor.handwriting

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.NotePage
import wojtoteka.ovh.kajet.core.model.ShapeElement
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.ink.ShapeGeometry
import wojtoteka.ovh.kajet.ink.StrokeCanvas
import kotlin.math.roundToInt

/**
 * Ramka WZIĘTEGO kształtu: obrys, uchwyty rozmiaru, uchwyt obrotu i kosz.
 *
 * Sam kształt rysuje kartka ([StrokeCanvas]) pod atramentem — tutaj jest tylko
 * to, czym się go rusza, i wyłącznie dla kształtu wskazanego stuknięciem.
 * Po kształtach niewziętych pisze się jak po papierze, tak samo jak przy
 * zdjęciach ([ImageFrameOnPage]).
 */
@Composable
fun ShapeFrameOnPage(
    pages: List<NotePage>,
    offsetX: Float,
    offsetY: Float,
    zoom: Float,
    selected: String?,
    square: Boolean,
    onChange: (page: Int, shape: ShapeElement, toHistory: Boolean) -> Unit,
    onCommit: (page: Int, before: ShapeElement, after: ShapeElement) -> Unit,
    onDelete: (page: Int, id: String) -> Unit,
) {
    if (selected == null) return
    var top = 0f
    pages.forEachIndexed { index, sheet ->
        val pageTop = top
        sheet.shapes.firstOrNull { it.id == selected }?.let { shape ->
            ShapeFrame(
                shape = shape,
                pageTop = pageTop,
                offsetX = offsetX,
                offsetY = offsetY,
                zoom = zoom,
                square = square,
                onChange = { next, toHistory -> onChange(index, next, toHistory) },
                onCommit = { before, after -> onCommit(index, before, after) },
                onDelete = { onDelete(index, shape.id) },
            )
        }
        top += sheet.height + StrokeCanvas.PAGE_GAP
    }
}

@Composable
private fun ShapeFrame(
    shape: ShapeElement,
    pageTop: Float,
    offsetX: Float,
    offsetY: Float,
    zoom: Float,
    square: Boolean,
    onChange: (ShapeElement, Boolean) -> Unit,
    onCommit: (before: ShapeElement, after: ShapeElement) -> Unit,
    onDelete: () -> Unit,
) {
    val words = LocalStrings.current
    val density = LocalDensity.current
    val accent = Kajet.colors.accent

    /*
      Gesty czytają kształt, przybliżenie i blokadę przez rememberUpdatedState.
      Blok `pointerInput` powstaje raz na kształt i pamiętałby wartości sprzed
      pierwszego przesunięcia — drugie chwycenie ramki cofałoby ją wtedy tam,
      gdzie leżała na początku.
    */
    val current by rememberUpdatedState(shape)
    val currentZoom by rememberUpdatedState(zoom)
    val squareNow by rememberUpdatedState(square)

    fun viewX(pageX: Float) = (pageX - offsetX) * zoom
    fun viewY(pageY: Float) = (pageY + pageTop - offsetY) * zoom
    fun pageX(view: Float) = view / zoom + offsetX
    fun pageY(view: Float) = view / zoom - pageTop + offsetY

    val bounds = ShapeGeometry.bounds(shape)
    val frameLeft = viewX(bounds.left) - FRAME_PADDING
    val frameTop = viewY(bounds.top) - FRAME_PADDING
    val frameWidth = bounds.width * zoom + 2 * FRAME_PADDING
    val frameHeight = bounds.height * zoom + 2 * FRAME_PADDING

    val handles = ShapeGeometry.handlePoints(shape)
    val handleCount = handles.size / 2

    // Przesuwanie: całe pole ramki. Zajmuje rysik tylko na czas poprawek —
    // po odłożeniu kształtu kartka znowu przyjmuje pismo w tym miejscu.
    Box(
        Modifier
            .offset { IntOffset(frameLeft.roundToInt(), frameTop.roundToInt()) }
            .size(
                width = with(density) { frameWidth.toDp() },
                height = with(density) { frameHeight.toDp() },
            )
            .pointerInput(shape.id) {
                var base: ShapeElement? = null
                var latest: ShapeElement? = null
                var acc = Offset.Zero
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
                        latest = null
                        acc = Offset.Zero
                    },
                    onDragEnd = { finish() },
                    onDragCancel = { finish() },
                ) { change, drag ->
                    change.consume()
                    acc += drag
                    val from = base ?: return@detectDragGestures
                    val moved = from.movedBy(acc.x / currentZoom, acc.y / currentZoom)
                    latest = moved
                    onChange(moved, false)
                }
            },
    )

    // Obrys ramki. Canvas bez gestu nie zabiera dotknięć, więc obrys nie
    // przeszkadza uchwytom leżącym na nim.
    Canvas(
        Modifier
            .offset { IntOffset(frameLeft.roundToInt(), frameTop.roundToInt()) }
            .size(
                width = with(density) { frameWidth.toDp() },
                height = with(density) { frameHeight.toDp() },
            ),
    ) {
        val path = Path()
        for (i in 0 until handleCount) {
            val x = viewX(handles[i * 2]) - frameLeft
            val y = viewY(handles[i * 2 + 1]) - frameTop
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (handleCount > 2) path.close()
        drawPath(
            path = path,
            color = accent,
            style = Stroke(
                width = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
            ),
        )
    }

    // Uchwyty rozmiaru: rogi prostokąta, a przy linii i strzałce jej końce.
    for (index in 0 until handleCount) {
        ShapeHandle(
            xView = viewX(handles[index * 2]),
            yView = viewY(handles[index * 2 + 1]),
            description = words.shapeResize,
            key = "rozmiar-$index",
            shape = shape,
            zoom = zoom,
            startPage = handles[index * 2] to handles[index * 2 + 1],
            transform = { base, x, y ->
                ShapeGeometry.dragHandle(base, index, x, y, squareNow)
            },
            onChange = { onChange(it, false) },
            onCommit = onCommit,
        )
    }

    // Uchwyt obrotu stoi nad górną krawędzią — jak w każdym programie do
    // rysowania, więc nikt go nie musi szukać.
    val rotateAt = rotationHandlePoint(shape, handles)
    ShapeHandle(
        xView = viewX(rotateAt.first),
        yView = viewY(rotateAt.second) - ROTATE_GAP,
        description = words.shapeRotate,
        key = "obrot",
        shape = shape,
        zoom = zoom,
        startPage = rotateAt.first to (rotateAt.second - ROTATE_GAP / zoom),
        transform = { base, x, y -> ShapeGeometry.rotatedTo(base, x, y) },
        onChange = { onChange(it, false) },
        onCommit = onCommit,
        icon = true,
    )

    Box(
        Modifier
            .offset {
                IntOffset(
                    (frameLeft + frameWidth).roundToInt(),
                    (frameTop - BIN_SIZE_PX).roundToInt(),
                )
            }
            .size(28.dp)
            .background(accent, CircleShape)
            .pointerInput(shape.id) { detectTapGestures(onTap = { onDelete() }) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            KajetIcons.Bin,
            contentDescription = words.shapeRemove,
            tint = Kajet.colors.onAccent,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Jeden uchwyt.
 *
 * Ciągnięcie liczy się od miejsca, w którym uchwyt STAŁ na początku gestu,
 * powiększonego o przebytą drogę. Gdyby liczyło się od bieżącego położenia
 * uchwytu, ten uciekałby spod palca: każda zmiana kształtu przesuwa przecież
 * sam uchwyt.
 */
@Composable
private fun ShapeHandle(
    xView: Float,
    yView: Float,
    description: String,
    key: String,
    shape: ShapeElement,
    zoom: Float,
    startPage: Pair<Float, Float>,
    transform: (base: ShapeElement, pageX: Float, pageY: Float) -> ShapeElement,
    onChange: (ShapeElement) -> Unit,
    onCommit: (before: ShapeElement, after: ShapeElement) -> Unit,
    diameter: Dp = 22.dp,
    icon: Boolean = false,
) {
    val density = LocalDensity.current
    val accent = Kajet.colors.accent
    val current by rememberUpdatedState(shape)
    val anchor by rememberUpdatedState(startPage)
    val currentZoom by rememberUpdatedState(zoom)
    val half = with(density) { diameter.toPx() } / 2f

    Box(
        Modifier
            .offset {
                IntOffset((xView - half).roundToInt(), (yView - half).roundToInt())
            }
            .size(diameter)
            .background(accent, CircleShape)
            .pointerInput(key, shape.id) {
                var base: ShapeElement? = null
                var from: Pair<Float, Float> = 0f to 0f
                var latest: ShapeElement? = null
                var acc = Offset.Zero
                fun finish() {
                    val before = base
                    val after = latest
                    if (before != null && after != null) onCommit(before, after)
                    base = null
                    latest = null
                }
                detectDragGestures(
                    onDragStart = {
                        base = current
                        from = anchor
                        latest = null
                        acc = Offset.Zero
                    },
                    onDragEnd = { finish() },
                    onDragCancel = { finish() },
                ) { change, drag ->
                    change.consume()
                    acc += drag
                    val start = base ?: return@detectDragGestures
                    val next = transform(
                        start,
                        from.first + acc.x / currentZoom,
                        from.second + acc.y / currentZoom,
                    )
                    latest = next
                    onChange(next)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (icon) {
            Icon(
                KajetIcons.ShapeRotate,
                contentDescription = description,
                tint = Kajet.colors.onAccent,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * Miejsce uchwytu obrotu w układzie strony: środek górnej krawędzi ramki,
 * a przy linii — środek między jej końcami.
 */
private fun rotationHandlePoint(shape: ShapeElement, handles: FloatArray): Pair<Float, Float> {
    if (handles.size < 4) return shape.centerX to shape.centerY
    return ((handles[0] + handles[2]) / 2f) to ((handles[1] + handles[3]) / 2f)
}

/** Zapas między obrysem kształtu a ramką, w punktach ekranu. */
private const val FRAME_PADDING = 10f

/** O tyle uchwyt obrotu odsuwa się od górnej krawędzi. */
private const val ROTATE_GAP = 44f

private const val BIN_SIZE_PX = 34f
