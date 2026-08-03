package wojtoteka.ovh.kajet.ink

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.PageBackground
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

class OnScreenPage(
    val index: Int,
    val width: Float,
    val height: Float,
    val background: PageBackground,
    var strokes: List<InkStroke>,
)

interface CanvasListener {
    fun strokeFinished(page: Int, stroke: InkStroke)
    fun eraserPassed(page: Int, x: Float, y: Float, radius: Float, wholeStroke: Boolean)

    fun eraserFinished()
    fun lassoFinished(page: Int, polygon: List<Float>)
    fun selectionMoved(dx: Float, dy: Float, finished: Boolean)
    fun viewChanged(offsetX: Float, offsetY: Float, zoom: Float)
    fun emptyAreaTapped()
}

@SuppressLint("ViewConstructor")
class StrokeCanvas(context: Context) : FrameLayout(context), InProgressStrokesFinishedListener {

    private val inProgressView = InProgressStrokesView(context).also {
        it.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        it.eagerInit()
        it.addFinishedStrokesListener(this)
    }

    private val renderer: CanvasStrokeRenderer = CanvasStrokeRenderer.create()

    var listener: CanvasListener? = null

    var pages: List<OnScreenPage> = emptyList()
        set(value) {
            field = value
            rebuildStrokes()
            // The model returned the page with the freshly written stroke, so our layer
            // already has it and it may leave the authoring layer.
            releaseFinished()
            // Pages arrive from the model after the view has been sized, so fitting the
            // width has to work from here too.
            if (!fitted && value.isNotEmpty() && width > 0) fitWidth()
            invalidate()
        }

    private var fitted = false

    var tool: EditorTool = EditorTool.PEN
    var settings: PenSettings = PenSettings(
        penColor = Color.BLACK,
        highlighterColor = Color.YELLOW,
    )

    var fingerDraws: Boolean = false

    var paperColor: Int = Color.WHITE
    var ruleColor: Int = Color.LTGRAY
    var deskColor: Int = Color.GRAY
    var selectionColor: Int = Color.BLUE

    var selected: List<InkStroke> = emptyList()
        set(value) {
            field = value
            invalidate()
        }
    var selectionPage: Int = -1

    var offsetX: Float = 0f
        private set
    var offsetY: Float = 0f
        private set
    var zoom: Float = 1f
        private set

    private val engineStrokes = HashMap<Int, MutableList<Stroke>>()

    private val fallbackStrokes = HashMap<Int, MutableList<InkStroke>>()

    private val cache = HashMap<String, Stroke>()

    private val toRelease = HashSet<InProgressStrokeId>()

    private val releaseFallback = Runnable { releaseFinished() }

    private val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 10f), 0f)
    }
    private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fallbackPath = Path()

    private val docToView = Matrix()
    private val viewToDoc = Matrix()
    private val pageMatrix = Matrix()
    private val buffer = FloatArray(2)

    // Touch state

    private var drawingPointerId = -1
    private var activeStroke: InProgressStrokeId? = null

    private val strokePage = HashMap<InProgressStrokeId, Int>()

    private var lastStylusTime = 0L
    private var stylusDown = false

    private val lasso = ArrayList<Float>()
    private var movingSelection = false
    private var lastX = 0f
    private var lastY = 0f

    private var eraserX = Float.NaN
    private var eraserY = Float.NaN

    private var fingerIds = intArrayOf(-1, -1)
    private var lastCenterX = 0f
    private var lastCenterY = 0f
    private var lastSpan = 0f

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    init {
        setWillNotDraw(false)
        addView(inProgressView)
    }

    // View placement

    fun setView(x: Float, y: Float, scale: Float) {
        offsetX = x
        offsetY = y
        zoom = scale.coerceIn(MIN_ZOOM, MAX_ZOOM)
        refreshMatrices()
        invalidate()
    }

    fun fitWidth() {
        val page = pages.firstOrNull() ?: return
        if (width == 0) return
        val scale = (width - 2 * DESK_MARGIN) / page.width
        setView(-DESK_MARGIN / scale, 0f, scale)
        fitted = true
        listener?.viewChanged(offsetX, offsetY, zoom)
    }

    private fun refreshMatrices() {
        docToView.reset()
        docToView.postTranslate(-offsetX, -offsetY)
        docToView.postScale(zoom, zoom)

        viewToDoc.reset()
        viewToDoc.postScale(1f / zoom, 1f / zoom)
        viewToDoc.postTranslate(offsetX, offsetY)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!fitted && w > 0) fitWidth()
        refreshMatrices()
    }

    // Page layout in one coordinate system

    private fun topEdge(index: Int): Float {
        var y = 0f
        for (i in 0 until index) {
            y += pages[i].height + PAGE_GAP
        }
        return y
    }

    fun pageAt(docX: Float, docY: Float): Int {
        var y = 0f
        for (page in pages) {
            if (docY >= y && docY <= y + page.height && docX >= 0f && docX <= page.width) {
                return page.index
            }
            y += page.height + PAGE_GAP
        }
        return -1
    }

    private fun nearestPage(docY: Float): Int {
        if (pages.isEmpty()) return -1
        var y = 0f
        for (page in pages) {
            if (docY <= y + page.height + PAGE_GAP / 2f) return page.index
            y += page.height + PAGE_GAP
        }
        return pages.last().index
    }

    private fun toDoc(x: Float, y: Float): FloatArray {
        buffer[0] = x
        buffer[1] = y
        viewToDoc.mapPoints(buffer)
        return buffer
    }

    // Drawing

    private fun rebuildStrokes() {
        val live = HashSet<String>()
        engineStrokes.clear()
        fallbackStrokes.clear()
        for (page in pages) {
            val ready = ArrayList<Stroke>(page.strokes.size)
            val fallback = ArrayList<InkStroke>()
            for (stroke in page.strokes) {
                live += stroke.id
                val mesh = cache[stroke.id] ?: runCatching { Strokes.toEngine(stroke) }
                    .getOrNull()
                    ?.takeIf { it.inputs.size > 0 }
                    ?.also { cache[stroke.id] = it }
                // A stroke saved in the file must never disappear from the screen.
                if (mesh != null) ready += mesh else if (stroke.pointCount > 0) fallback += stroke
            }
            engineStrokes[page.index] = ready
            if (fallback.isNotEmpty()) fallbackStrokes[page.index] = fallback
        }
        if (cache.size > live.size) {
            cache.keys.retainAll(live)
        }
    }

    private fun releaseFinished() {
        if (toRelease.isEmpty()) return
        val copy = HashSet(toRelease)
        toRelease.clear()
        inProgressView.removeFinishedStrokes(copy)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(deskColor)
        if (pages.isEmpty()) return

        paperPaint.color = paperColor
        rulePaint.color = ruleColor

        var top = 0f
        for (page in pages) {
            val viewBottom = (top + page.height - offsetY) * zoom
            val viewTop = (top - offsetY) * zoom
            if (viewBottom >= -32f && viewTop <= height + 32f) {
                drawPage(canvas, page, top)
            }
            top += page.height + PAGE_GAP
        }

        if (lasso.size >= 4) drawLasso(canvas)
        if (selected.isNotEmpty()) drawSelectionFrame(canvas)
        if (!eraserX.isNaN()) drawEraserCircle(canvas)
    }

    private fun drawPage(canvas: Canvas, page: OnScreenPage, top: Float) {
        val left = -offsetX * zoom
        val topY = (top - offsetY) * zoom
        val right = left + page.width * zoom
        val bottom = topY + page.height * zoom

        canvas.drawRect(left, topY, right, bottom, paperPaint)

        canvas.save()
        canvas.clipRect(left, topY, right, bottom)
        drawBackground(canvas, page, left, topY)

        pageMatrix.set(docToView)
        pageMatrix.preTranslate(0f, top)
        for (stroke in engineStrokes[page.index].orEmpty()) {
            renderer.draw(canvas, stroke, pageMatrix)
        }
        fallbackStrokes[page.index]?.let { drawFallback(canvas, it) }
        canvas.restore()

        rulePaint.strokeWidth = 1f
        canvas.drawRect(left, topY, right, bottom, rulePaint)
    }

    private fun drawBackground(canvas: Canvas, page: OnScreenPage, left: Float, top: Float) {
        val s = zoom
        rulePaint.strokeWidth = max(1f, 0.6f * s)
        val pageWidth = page.width * s
        val pageHeight = page.height * s

        when (page.background) {
            PageBackground.PLAIN -> Unit

            PageBackground.LINED -> {
                var y = LINE_SPACING
                while (y < page.height) {
                    canvas.drawLine(left, top + y * s, left + pageWidth, top + y * s, rulePaint)
                    y += LINE_SPACING
                }
            }

            PageBackground.GRID -> {
                var y = GRID_SPACING
                while (y < page.height) {
                    canvas.drawLine(left, top + y * s, left + pageWidth, top + y * s, rulePaint)
                    y += GRID_SPACING
                }
                var x = GRID_SPACING
                while (x < page.width) {
                    canvas.drawLine(left + x * s, top, left + x * s, top + pageHeight, rulePaint)
                    x += GRID_SPACING
                }
            }

            PageBackground.DOTS -> {
                val radius = max(1f, 0.9f * s)
                rulePaint.style = Paint.Style.FILL
                var y = GRID_SPACING
                while (y < page.height) {
                    var x = GRID_SPACING
                    while (x < page.width) {
                        canvas.drawCircle(left + x * s, top + y * s, radius, rulePaint)
                        x += GRID_SPACING
                    }
                    y += GRID_SPACING
                }
                rulePaint.style = Paint.Style.STROKE
            }

            PageBackground.STAVE -> {
                var y = STAVE_SPACING
                while (y + 4 * STAVE_SPACING < page.height) {
                    for (i in 0 until 5) {
                        val line = top + (y + i * STAVE_SPACING) * s
                        canvas.drawLine(
                            left + STAVE_MARGIN * s,
                            line,
                            left + pageWidth - STAVE_MARGIN * s,
                            line,
                            rulePaint,
                        )
                    }
                    y += 5 * STAVE_SPACING + STAVE_GAP
                }
            }
        }

        // The margin rule, the same one that runs through the whole app.
        if (page.background != PageBackground.PLAIN) {
            rulePaint.strokeWidth = max(1f, 0.9f * s)
            canvas.drawLine(
                left + PAGE_MARGIN * s,
                top,
                left + PAGE_MARGIN * s,
                top + pageHeight,
                rulePaint,
            )
        }
    }

    private fun drawFallback(canvas: Canvas, strokes: List<InkStroke>) {
        canvas.save()
        canvas.concat(pageMatrix)
        for (stroke in strokes) {
            val count = stroke.pointCount
            if (count == 0) continue
            fallbackPaint.color = stroke.color
            fallbackPaint.strokeWidth = stroke.size.coerceAtLeast(0.4f)

            if (count == 1) {
                fallbackPaint.style = Paint.Style.FILL
                canvas.drawCircle(stroke.x(0), stroke.y(0), stroke.size / 2f, fallbackPaint)
                fallbackPaint.style = Paint.Style.STROKE
                continue
            }

            fallbackPath.reset()
            fallbackPath.moveTo(stroke.x(0), stroke.y(0))
            for (i in 1 until count) {
                fallbackPath.lineTo(stroke.x(i), stroke.y(i))
            }
            canvas.drawPath(fallbackPath, fallbackPaint)
        }
        canvas.restore()
    }

    private fun drawLasso(canvas: Canvas) {
        val path = Path()
        val point = FloatArray(2)
        for (i in 0 until lasso.size / 2) {
            point[0] = lasso[i * 2]
            point[1] = lasso[i * 2 + 1]
            docToView.mapPoints(point)
            if (i == 0) path.moveTo(point[0], point[1]) else path.lineTo(point[0], point[1])
        }
        selectionPaint.color = selectionColor
        canvas.drawPath(path, selectionPaint)
    }

    private fun drawSelectionFrame(canvas: Canvas) {
        val box = Strokes.bounds(selected) ?: return
        val top = topEdge(selectionPage.coerceAtLeast(0))
        val viewLeft = (box.left - offsetX) * zoom
        val viewTop = (box.top + top - offsetY) * zoom
        val viewRight = (box.right - offsetX) * zoom
        val viewBottom = (box.bottom + top - offsetY) * zoom
        selectionPaint.color = selectionColor
        canvas.drawRect(viewLeft - 8f, viewTop - 8f, viewRight + 8f, viewBottom + 8f, selectionPaint)
    }

    private fun drawEraserCircle(canvas: Canvas) {
        eraserPaint.color = selectionColor
        eraserPaint.strokeWidth = 2f
        val point = floatArrayOf(eraserX, eraserY)
        docToView.mapPoints(point)
        canvas.drawCircle(point[0], point[1], settings.eraserRadius * zoom, eraserPaint)
    }

    // Touch

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val index = event.actionIndex
        val id = event.getPointerId(index)
        val type = event.getToolType(index)

        if (type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER) {
            lastStylusTime = event.eventTime
        }

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> onDown(event, index, id, type)
            MotionEvent.ACTION_MOVE -> onMove(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> onUp(event, index, id)
            MotionEvent.ACTION_CANCEL -> {
                cancel()
                true
            }
            else -> false
        }
    }

    private fun onDown(event: MotionEvent, index: Int, id: Int, type: Int): Boolean {
        val stylus = type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER
        val finger = !stylus && type != MotionEvent.TOOL_TYPE_MOUSE

        // Drugi palec na ekranie zawsze znaczy „przesuwam kartkę", nawet kiedy
        // palec rysuje. Kreska zaczęta pierwszym palcem znika, bo i tak brała się
        // z tego, że ktoś chciał chwycić stronę, a nie pisać.
        if (finger && !stylusDown && drawingPointerId >= 0 && fingerIds[0] < 0) {
            val writing = drawingPointerId
            val writingIndex = event.findPointerIndex(writing)
            abandonStroke()
            if (writingIndex >= 0) {
                rememberFinger(writing, event.getX(writingIndex), event.getY(writingIndex), event)
            }
            rememberFinger(id, event.getX(index), event.getY(index), event)
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }

        if (stylus) {
            stylusDown = true
            // The stylus wins. A palm already resting on the screen stops working.
            dropFingers()
        } else if (rejectPalm(event)) {
            return true
        }

        val docPoint = toDoc(event.getX(index), event.getY(index))
        val docX = docPoint[0]
        val docY = docPoint[1]

        val stylusEraser = type == MotionEvent.TOOL_TYPE_ERASER
        // A mouse draws too, so notes opened on a computer are not scroll-only.
        val drawing = stylus || fingerDraws || type == MotionEvent.TOOL_TYPE_MOUSE

        if (!drawing) {
            rememberFinger(id, event.getX(index), event.getY(index), event)
            return true
        }

        when {
            stylusEraser || tool.isEraser -> {
                drawingPointerId = id
                eraserX = docX
                eraserY = docY
                erase(docX, docY, tool == EditorTool.ERASER_STROKE && !stylusEraser)
                invalidate()
            }

            tool == EditorTool.LASSO -> {
                drawingPointerId = id
                if (selected.isNotEmpty() && inSelection(docX, docY)) {
                    movingSelection = true
                    lastX = docX
                    lastY = docY
                } else {
                    lasso.clear()
                    lasso += docX
                    lasso += docY
                    selected = emptyList()
                }
            }

            tool.writes -> {
                val page = nearestPage(docY)
                if (page < 0) return true
                drawingPointerId = id
                val brush = when (tool) {
                    EditorTool.HIGHLIGHTER -> Brushes.highlighter(
                        settings.highlighterColor,
                        settings.highlighterWidth,
                        settings.highlighterOpacity,
                    )
                    else -> Brushes.forTool(
                        settings.penKind,
                        settings.penColor,
                        settings.penWidth,
                        settings.penOpacity,
                    )
                }

                // The engine takes two matrices. The first maps touch points into document
                // coordinates. The second says which coordinate system to record the stroke
                // in: page coordinates, shifted by the top edge, so the stroke comes back
                // ready to save.
                val toPageSpace = Matrix().apply { setTranslate(0f, topEdge(page)) }
                activeStroke = inProgressView.startStroke(
                    event,
                    id,
                    brush,
                    Matrix(viewToDoc),
                    toPageSpace,
                )
                activeStroke?.let { strokePage[it] = page }
                // Without this the system batches stylus events and the stroke loses points.
                requestUnbufferedDispatch(event)
            }
        }
        parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }

    private fun onMove(event: MotionEvent): Boolean {
        if (drawingPointerId >= 0) {
            val index = event.findPointerIndex(drawingPointerId)
            if (index < 0) return true
            val docPoint = toDoc(event.getX(index), event.getY(index))
            val docX = docPoint[0]
            val docY = docPoint[1]

            when {
                activeStroke != null -> inProgressView.addToStroke(event, drawingPointerId, activeStroke!!, null)

                movingSelection -> {
                    listener?.selectionMoved(docX - lastX, docY - lastY, finished = false)
                    lastX = docX
                    lastY = docY
                }

                tool == EditorTool.LASSO -> {
                    lasso += docX
                    lasso += docY
                    invalidate()
                }

                else -> {
                    // Eraser. Wipe along the way, not only where the finger is now.
                    val steps = max(
                        1,
                        (hypot(docX - eraserX, docY - eraserY) / (settings.eraserRadius / 2f)).toInt(),
                    )
                    for (i in 1..steps) {
                        val t = i / steps.toFloat()
                        erase(
                            eraserX + (docX - eraserX) * t,
                            eraserY + (docY - eraserY) * t,
                            tool == EditorTool.ERASER_STROKE,
                        )
                    }
                    eraserX = docX
                    eraserY = docY
                    invalidate()
                }
            }
            return true
        }

        if (fingerIds[0] >= 0) {
            panAndZoom(event)
            return true
        }
        return true
    }

    private fun onUp(event: MotionEvent, index: Int, id: Int): Boolean {
        if (id == drawingPointerId) {
            val stroke = activeStroke
            if (stroke != null) {
                inProgressView.finishStroke(event, id, stroke)
            } else if (movingSelection) {
                listener?.selectionMoved(0f, 0f, finished = true)
                movingSelection = false
            } else if (tool == EditorTool.LASSO && lasso.size >= 6) {
                val page = nearestPage(lasso[1])
                if (page >= 0) {
                    val top = topEdge(page)
                    val local = ArrayList<Float>(lasso.size)
                    for (i in lasso.indices) {
                        local += if (i % 2 == 0) lasso[i] else lasso[i] - top
                    }
                    listener?.lassoFinished(page, local)
                }
                lasso.clear()
                invalidate()
            } else if (tool.isEraser) {
                eraserX = Float.NaN
                eraserY = Float.NaN
                listener?.eraserFinished()
                invalidate()
            }
            drawingPointerId = -1
            activeStroke = null
        }

        forgetFinger(id)

        if (event.actionMasked == MotionEvent.ACTION_UP) {
            stylusDown = false
            dropFingers()
        }
        return true
    }

    private fun cancel() {
        activeStroke?.let {
            inProgressView.cancelStroke(it, null)
            strokePage.remove(it)
        }
        activeStroke = null
        drawingPointerId = -1
        movingSelection = false
        lasso.clear()
        dropFingers()
        eraserX = Float.NaN
        eraserY = Float.NaN
        invalidate()
    }

    /** Porzuca to, co właśnie rysował palec, i zostawia ekran gotowy na przesuwanie. */
    private fun abandonStroke() {
        activeStroke?.let {
            inProgressView.cancelStroke(it, null)
            strokePage.remove(it)
        }
        activeStroke = null
        drawingPointerId = -1
        if (movingSelection) {
            listener?.selectionMoved(0f, 0f, finished = true)
            movingSelection = false
        }
        if (lasso.isNotEmpty()) lasso.clear()
        if (!eraserX.isNaN()) {
            eraserX = Float.NaN
            eraserY = Float.NaN
            listener?.eraserFinished()
        }
        invalidate()
    }

    private fun rejectPalm(event: MotionEvent): Boolean {
        if (stylusDown) return true
        return event.eventTime - lastStylusTime < PALM_REJECT_MS
    }

    private fun rememberFinger(id: Int, x: Float, y: Float, event: MotionEvent) {
        if (fingerIds[0] < 0) {
            fingerIds[0] = id
            lastCenterX = x
            lastCenterY = y
            lastSpan = 0f
        } else if (fingerIds[1] < 0 && fingerIds[0] != id) {
            fingerIds[1] = id
            refreshFingerCenter(event)
        }
    }

    private fun forgetFinger(id: Int) {
        if (fingerIds[0] == id) fingerIds[0] = fingerIds[1].also { fingerIds[1] = -1 }
        if (fingerIds[1] == id) fingerIds[1] = -1
        lastSpan = 0f
    }

    private fun dropFingers() {
        fingerIds[0] = -1
        fingerIds[1] = -1
        lastSpan = 0f
    }

    private fun refreshFingerCenter(event: MotionEvent) {
        val i1 = event.findPointerIndex(fingerIds[0])
        val i2 = event.findPointerIndex(fingerIds[1])
        if (i1 < 0 || i2 < 0) return
        lastCenterX = (event.getX(i1) + event.getX(i2)) / 2f
        lastCenterY = (event.getY(i1) + event.getY(i2)) / 2f
        lastSpan = hypot(event.getX(i1) - event.getX(i2), event.getY(i1) - event.getY(i2))
    }

    private fun panAndZoom(event: MotionEvent) {
        val i1 = event.findPointerIndex(fingerIds[0])
        if (i1 < 0) return
        val i2 = if (fingerIds[1] >= 0) event.findPointerIndex(fingerIds[1]) else -1

        if (i2 < 0) {
            val dx = event.getX(i1) - lastCenterX
            val dy = event.getY(i1) - lastCenterY
            if (abs(dx) < touchSlop && abs(dy) < touchSlop && lastSpan == 0f) return
            offsetX -= dx / zoom
            offsetY -= dy / zoom
            lastCenterX = event.getX(i1)
            lastCenterY = event.getY(i1)
        } else {
            val centerX = (event.getX(i1) + event.getX(i2)) / 2f
            val centerY = (event.getY(i1) + event.getY(i2)) / 2f
            val span = hypot(event.getX(i1) - event.getX(i2), event.getY(i1) - event.getY(i2))

            if (lastSpan > 0f && span > 0f) {
                val change = span / lastSpan
                val newZoom = (zoom * change).coerceIn(MIN_ZOOM, MAX_ZOOM)
                // Zoom around the point between the fingers so the page does not run away.
                val before = toDoc(centerX, centerY)
                val docX = before[0]
                val docY = before[1]
                zoom = newZoom
                refreshMatrices()
                val after = toDoc(centerX, centerY)
                offsetX += docX - after[0]
                offsetY += docY - after[1]
            }

            offsetX -= (centerX - lastCenterX) / zoom
            offsetY -= (centerY - lastCenterY) / zoom
            lastCenterX = centerX
            lastCenterY = centerY
            lastSpan = span
        }

        clampView()
        refreshMatrices()
        invalidate()
        listener?.viewChanged(offsetX, offsetY, zoom)
    }

    private fun clampView() {
        val last = pages.lastOrNull() ?: return
        val totalHeight = topEdge(last.index) + last.height
        val slackX = last.width * 0.5f
        val slackY = height / zoom * 0.5f
        offsetX = offsetX.coerceIn(-slackX, last.width + slackX)
        offsetY = offsetY.coerceIn(-slackY, totalHeight + slackY)
    }

    private fun inSelection(docX: Float, docY: Float): Boolean {
        val box = Strokes.bounds(selected) ?: return false
        val top = topEdge(selectionPage.coerceAtLeast(0))
        return box.expanded(10f).contains(docX, docY - top)
    }

    private fun erase(docX: Float, docY: Float, wholeStroke: Boolean) {
        val page = nearestPage(docY)
        if (page < 0) return
        val top = topEdge(page)
        listener?.eraserPassed(page, docX, docY - top, settings.eraserRadius, wholeStroke)
    }

    // Finished strokes coming back from the engine

    override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
        for ((strokeId, stroke) in strokes) {
            val page = strokePage.remove(strokeId)
            if (page == null) {
                toRelease += strokeId
                continue
            }
            var model = Strokes.toModel(stroke, settings.toInkTool(tool))
            if (tool == EditorTool.RULER) model = Strokes.straighten(model)
            // The stroke already arrives in page coordinates, per the matrix given to the engine.
            listener?.strokeFinished(page, model)
            toRelease += strokeId
        }
        // Normally the pages setter drops them once the model hands the stroke back.
        // This is the way out in case it never does.
        removeCallbacks(releaseFallback)
        postDelayed(releaseFallback, RELEASE_FALLBACK_MS)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(releaseFallback)
        inProgressView.removeFinishedStrokesListener(this)
        super.onDetachedFromWindow()
    }

    companion object {
        const val PAGE_GAP = 24f
        const val DESK_MARGIN = 24f
        const val LINE_SPACING = 28f
        const val GRID_SPACING = 20f
        const val STAVE_SPACING = 9f
        const val STAVE_GAP = 46f
        const val STAVE_MARGIN = 30f
        const val PAGE_MARGIN = 60f
        const val MIN_ZOOM = 0.25f
        const val MAX_ZOOM = 8f

        const val PALM_REJECT_MS = 400L

        const val RELEASE_FALLBACK_MS = 700L
    }
}
