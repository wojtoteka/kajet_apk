package wojtoteka.ovh.kajet.ink

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.ink.brush.InputToolType
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.ImmutableStrokeInputBatch
import androidx.ink.strokes.InProgressStroke
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import android.graphics.Bitmap
import android.graphics.RectF
import wojtoteka.ovh.kajet.core.model.ImageElement
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.ShapeElement
import wojtoteka.ovh.kajet.core.model.TextBoxElement
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

class OnScreenPage(
    val index: Int,
    val width: Float,
    val height: Float,
    val background: PageBackground,
    var strokes: List<InkStroke>,
    var images: List<ImageElement> = emptyList(),
    var shapes: List<ShapeElement> = emptyList(),
    var texts: List<TextBoxElement> = emptyList(),
)

interface CanvasListener {
    fun strokeFinished(page: Int, stroke: InkStroke)
    fun eraserPassed(page: Int, x: Float, y: Float, radius: Float, wholeStroke: Boolean)

    fun eraserFinished()
    fun lassoFinished(page: Int, polygon: List<Float>)
    fun selectionMoved(dx: Float, dy: Float, finished: Boolean)
    fun viewChanged(offsetX: Float, offsetY: Float, zoom: Float)

    /** Palec stuknął w zdjęcie — bez ruchu i bez rysowania. */
    fun imageTapped(page: Int, id: String)

    /** Stuknięcie w pole tekstowe (także CODE). Domyślnie puste — okno rysunku pól nie ma. */
    fun textTapped(page: Int, id: String) = Unit
    fun emptyAreaTapped()

    /*
      Kształty. Domyślne ciała są puste, bo z kartki korzysta też okno rysunku
      w notatce tekstowej, a tam kształtów nie ma.
    */

    /** Przeciągnięcie skończone: gotowy kształt bez nazwy — nazwę nadaje model. */
    fun shapeDrawn(page: Int, shape: ShapeElement) = Unit

    /** Stuknięcie w kształt albo w puste miejsce ([id] równe null). */
    fun shapeTapped(page: Int, id: String?) = Unit
}

@SuppressLint("ViewConstructor")
class StrokeCanvas(context: Context) : FrameLayout(context) {

    private val renderer: CanvasStrokeRenderer = CanvasStrokeRenderer.create()

    var listener: CanvasListener? = null

    var pages: List<OnScreenPage> = emptyList()
        set(value) {
            field = value
            rebuildStrokes()
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

    var shapeSettings: ShapeSettings = ShapeSettings(color = Color.BLACK)

    /** Blokada proporcji 1:1 włączona na stałe w panelu kształtu. */
    var shapeSquareLocked: Boolean = false

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

    /** Bitmapy załączników. Kartka rysuje zdjęcia POD atramentem. */
    var imageBitmaps: Map<String, Bitmap> = emptyMap()
        set(value) {
            field = value
            invalidate()
        }

    var offsetX: Float = 0f
        private set
    var offsetY: Float = 0f
        private set
    var zoom: Float = 1f
        private set

    private val engineStrokes = HashMap<Int, MutableList<Stroke>>()

    private val fallbackStrokes = HashMap<Int, MutableList<InkStroke>>()

    private val cache = HashMap<String, Stroke>()

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

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val imageDst = RectF()

    private val shapePainter = ShapePainter()

    // Kształt rysowany właśnie rysikiem: żyje tylko tutaj, do dokumentu wchodzi
    // dopiero po oderwaniu ręki.
    private var draft: ShapeElement? = null
    private var draftTemplate: ShapeElement? = null
    private var draftPage = -1
    private var shapeDragging = false
    private var shapeStartX = 0f
    private var shapeStartY = 0f
    private var shapeLastX = 0f
    private var shapeLastY = 0f
    private var squareFinger = -1

    // Stuknięcie palcem: wybiera zdjęcie albo odznacza, co było wybrane.
    private var tapPointerId = -1
    private var tapDownX = 0f
    private var tapDownY = 0f
    private var tapDownTime = 0L
    private var tapCandidate = false

    // Touch state

    private var drawingPointerId = -1

    // Mokra kreska żyje na tym samym canvasie co suche. InProgressStrokesView
    // z warstwą frontowego bufora zakrywał na części urządzeń pasek edytora
    // i świeżo dopisane kreski, więc rysujemy ją sami.
    private var wet: InProgressStroke? = null
    private var wetPage = -1
    private var wetTool: EditorTool = EditorTool.PEN
    private var wetStartTime = 0L
    private val wetBuffer = MutableStrokeInputBatch()

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

    /**
     * Przywraca położenie i przybliżenie kartki zapamiętane przez ekran —
     * po obrocie tabletu albo po tym, jak system zabił proces w tle.
     *
     * Ustawia [fitted], żeby dopasowanie do szerokości nie nadpisało tego przy
     * pierwszym nadaniu rozmiaru.
     */
    fun restoreView(x: Float, y: Float, scale: Float) {
        fitted = true
        setView(x, y, scale)
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

    /*
      Górna krawędź strony [index] w układzie dokumentu.

      Indeks jest przycinany do tego, co naprawdę jest na ekranie. Zaznaczenie
      lassem zapamiętuje numer strony, a strony potrafią zniknąć spod niego —
      przy kasowaniu strony albo przy scaleniu wszystkich w jedną (tryb
      przewijania). Gołe `pages[i]` leciało wtedy poza zakres WEWNĄTRZ onDraw,
      czyli przy każdym przerysowaniu kartki.
    */
    private fun topEdge(index: Int): Float {
        var y = 0f
        val last = index.coerceAtMost(pages.size)
        for (i in 0 until last) {
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

    /*
      Wyjątek z rysowania leci prosto na wątek główny i ubija aplikację —
      z tego nie ma ekranu błędu, bo to samo rysowanie jest zepsute. Kartka bez
      jednej klatki jest lepsza niż zamknięty Kajet, więc awaria rysowania
      zostaje w dzienniku, a canvas wraca do stanu sprzed próby: `restoreToCount`
      domyka wszystkie `save` porzucone w połowie.
    */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val checkpoint = canvas.save()
        try {
            drawEverything(canvas)
        } catch (failure: Throwable) {
            Log.e("Kajet", "Kartka się nie narysowała", failure)
        } finally {
            canvas.restoreToCount(checkpoint)
        }
    }

    private fun drawEverything(canvas: Canvas) {
        // Compose trzyma widoki z clipChildren=false, więc canvas nie jest
        // przycięty do naszych granic. Bez własnego przycięcia drawColor
        // zalewa całe okno i zamalowuje pasek edytora nad kartką.
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
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

        // Zdjęcia leżą pod atramentem: po wklejonym obrazku da się pisać.
        for (image in page.images) {
            val bitmap = imageBitmaps[image.asset] ?: continue
            imageDst.set(
                left + image.x * zoom,
                topY + image.y * zoom,
                left + (image.x + image.width) * zoom,
                topY + (image.y + image.height) * zoom,
            )
            canvas.drawBitmap(bitmap, null, imageDst, imagePaint)
        }

        pageMatrix.set(docToView)
        pageMatrix.preTranslate(0f, top)

        // Kształty leżą nad zdjęciami, ale pod atramentem: po wstawionym
        // kształcie da się pisać, a wypełnienie nie zakrywa notatek.
        val drafted = if (draftPage == page.index) draft else null
        if (page.shapes.isNotEmpty() || drafted != null) {
            canvas.save()
            canvas.concat(pageMatrix)
            shapePainter.draw(canvas, page.shapes)
            drafted?.let { shapePainter.draw(canvas, it) }
            canvas.restore()
        }

        // CanvasStrokeRenderer nie nakłada podanej macierzy na canvas — dostaje
        // ją tylko do jakości teselacji. Transformację trzeba nałożyć samemu,
        // inaczej kreski lądują w surowych współrzędnych strony: obok miejsca
        // pisania, w innej skali i znikają, gdy kartka odjedzie spod nich.
        canvas.save()
        canvas.concat(pageMatrix)
        for (stroke in engineStrokes[page.index].orEmpty()) {
            renderer.draw(canvas, stroke, pageMatrix)
        }
        if (wetPage == page.index) {
            wet?.let { renderer.draw(canvas, it, pageMatrix) }
        }
        canvas.restore()
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
        // Zaznaczenie ze strony, której już nie ma, nie ma czego obrysować.
        if (selectionPage !in pages.indices) return
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

    /*
      Kartka to powierzchnia pisania: nad nią rysik ma drgać, nad paskami
      narzędzi i menu — nie. Najechanie i zjazd zgłaszają się same; PenHaptics
      odrzuca powtórki po jednym porównaniu.
    */
    override fun onHoverEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE ->
                PenHaptics.surfaceHover(context, true)
            MotionEvent.ACTION_HOVER_EXIT ->
                PenHaptics.surfaceHover(context, false)
        }
        return super.onHoverEvent(event)
    }

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

        // Rysik Lenovo gubi zamówiony profil, gdy aplikacja schodzi w tło
        // albo rysik zaśnie. Przypomnienie tuż przed kreską jest tanie (idzie
        // wątkiem w tle i odrzuca powtórki), a pilnuje, żeby drganie było.
        if (stylus) {
            // Dotknięcie kartki bez wcześniejszego najechania (nie każdy
            // rysik je zgłasza) też znaczy „jestem nad powierzchnią pisania".
            PenHaptics.surfaceHover(context, true)
            PenHaptics.refresh(context)
        }

        /*
          Drugi palec w trakcie rysowania kształtu prosi o proporcje 1:1, a nie
          o przesuwanie kartki.

          Dłoń oparta obok rysika też przychodzi jako palec, więc liczy się
          tylko dotknięcie wielkości opuszka. Kto woli pewność zamiast gestu,
          włącza blokadę w panelu kształtu albo trzyma Shift.
        */
        if (shapeDragging && finger && id != drawingPointerId && squareFinger < 0 &&
            event.getTouchMajor(index) <= fingertipReach()
        ) {
            squareFinger = id
            updateDraft(event)
            invalidate()
            return true
        }

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
            if (fingerIds[0] < 0) {
                tapPointerId = id
                tapDownX = event.getX(index)
                tapDownY = event.getY(index)
                tapDownTime = event.eventTime
                tapCandidate = true
            } else {
                // Drugi palec to już chwyt kartki, nie stuknięcie.
                tapCandidate = false
            }
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

            tool == EditorTool.SHAPES -> {
                val page = nearestPage(docY)
                if (page < 0) return true
                drawingPointerId = id
                draftPage = page
                shapeDragging = true
                shapeStartX = docX
                shapeStartY = docY - topEdge(page)
                shapeLastX = shapeStartX
                shapeLastY = shapeStartY
                draftTemplate = ShapeElement(
                    id = "",
                    kind = shapeSettings.kind,
                    x = shapeStartX,
                    y = shapeStartY,
                    width = 0f,
                    height = 0f,
                    color = shapeSettings.color,
                    strokeWidth = shapeSettings.strokeWidth,
                    fill = shapeSettings.fill,
                    opacity = shapeSettings.opacity,
                )
                draft = null
                // Bez tego system pakuje zdarzenia rysika i podgląd skacze.
                requestUnbufferedDispatch(event)
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

                val stroke = InProgressStroke()
                stroke.start(brush)
                wet = stroke
                wetPage = page
                wetTool = tool
                wetStartTime = event.eventTime
                enqueueWetPoints(event, index, page)
                // Without this the system batches stylus events and the stroke loses points.
                requestUnbufferedDispatch(event)
                invalidate()
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
                wet != null -> {
                    enqueueWetPoints(event, index, wetPage)
                    invalidate()
                }

                shapeDragging -> {
                    shapeLastX = docX
                    shapeLastY = docY - topEdge(draftPage)
                    updateDraft(event)
                    invalidate()
                }

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
        // Palec od proporcji 1:1 puszczony przed rysikiem: kształt wraca do
        // swobodnych boków, ale rysowanie trwa dalej.
        if (id == squareFinger) {
            squareFinger = -1
            if (shapeDragging) {
                updateDraft(event)
                invalidate()
            }
        }

        if (id == drawingPointerId) {
            if (wet != null) {
                enqueueWetPoints(event, index, wetPage)
                finishWetStroke()
            } else if (shapeDragging) {
                finishShape()
            } else if (movingSelection) {
                listener?.selectionMoved(0f, 0f, finished = true)
                movingSelection = false
            } else if (tool == EditorTool.LASSO && lasso.size >= 6 && !lassoIsTap()) {
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
            } else if (tool == EditorTool.LASSO) {
                // Stuknięcie zamiast obrysu: bierze kształt spod rysika.
                if (lasso.size >= 2) selectShapeAt(lasso[0], lasso[1])
                lasso.clear()
                invalidate()
            } else if (tool.isEraser) {
                eraserX = Float.NaN
                eraserY = Float.NaN
                listener?.eraserFinished()
                invalidate()
            }
            drawingPointerId = -1
        }

        if (tapPointerId == id) {
            val upIndex = event.findPointerIndex(id).let { if (it < 0) index else it }
            val moved = hypot(event.getX(upIndex) - tapDownX, event.getY(upIndex) - tapDownY)
            if (tapCandidate && moved <= touchSlop &&
                event.eventTime - tapDownTime < TAP_TIMEOUT_MS
            ) {
                val doc = toDoc(event.getX(upIndex), event.getY(upIndex))
                dispatchTap(doc[0], doc[1])
            }
            tapCandidate = false
            tapPointerId = -1
        }

        forgetFinger(id)

        if (event.actionMasked == MotionEvent.ACTION_UP) {
            stylusDown = false
            dropFingers()
        }
        return true
    }

    // Kształty

    private fun updateDraft(event: MotionEvent) {
        val template = draftTemplate ?: return
        draft = ShapeGeometry.fitTo(
            shape = template,
            startX = shapeStartX,
            startY = shapeStartY,
            endX = shapeLastX,
            endY = shapeLastY,
            square = squareWanted(event),
        )
    }

    private fun squareWanted(event: MotionEvent): Boolean =
        shapeSquareLocked ||
            squareFinger >= 0 ||
            (event.metaState and KeyEvent.META_SHIFT_ON) != 0

    /** Największe dotknięcie, które jeszcze uchodzi za opuszek, a nie za dłoń. */
    private fun fingertipReach(): Float = resources.displayMetrics.density * FINGERTIP_DP

    private fun finishShape() {
        val page = draftPage
        val shape = draft
        val startX = shapeStartX
        val startY = shapeStartY
        dropDraft()
        invalidate()
        if (page < 0) return

        if (shape != null && ShapeGeometry.bigEnough(shape)) {
            listener?.shapeDrawn(page, shape)
            return
        }
        // Przeciągnięcie krótsze niż stuknięcie: zamiast stawiać kropkę,
        // bierzemy kształt leżący pod rysikiem.
        listener?.shapeTapped(page, shapeAt(page, startX, startY)?.id)
    }

    private fun dropDraft() {
        draft = null
        draftTemplate = null
        draftPage = -1
        shapeDragging = false
        squareFinger = -1
    }

    /** Stuknięcie w kartkę: bierze kształt pod ręką albo odkłada zaznaczony. */
    private fun selectShapeAt(docX: Float, docY: Float) {
        val page = nearestPage(docY)
        if (page < 0) return
        val localY = docY - topEdge(page)
        // Pole tekstowe leży nad kształtami, więc łapie stuknięcie pierwsze.
        // To także jedyna droga do edycji pola, gdy palec rysuje.
        val text = textAt(page, docX, localY)
        if (text != null) {
            listener?.textTapped(page, text.id)
            return
        }
        listener?.shapeTapped(page, shapeAt(page, docX, localY)?.id)
    }

    private fun textAt(page: Int, localX: Float, localY: Float): TextBoxElement? {
        val onScreen = pages.firstOrNull { it.index == page } ?: return null
        // Ostatnie na spisie leży na wierzchu — tak jak przy rysowaniu.
        return onScreen.texts.lastOrNull {
            localX >= it.x && localX <= it.x + it.width &&
                localY >= it.y && localY <= it.y + it.height
        }
    }

    private fun shapeAt(page: Int, localX: Float, localY: Float): ShapeElement? {
        val onScreen = pages.firstOrNull { it.index == page } ?: return null
        // Ostatni na spisie leży na wierzchu — tak jak przy rysowaniu.
        return onScreen.shapes.lastOrNull {
            ShapeGeometry.hits(it, localX, localY, TAP_REACH / zoom)
        }
    }

    /** Obrys lassa krótszy niż drgnięcie ręki to stuknięcie, nie zaznaczanie. */
    private fun lassoIsTap(): Boolean {
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var i = 0
        while (i < lasso.size) {
            minX = kotlin.math.min(minX, lasso[i])
            maxX = max(maxX, lasso[i])
            minY = kotlin.math.min(minY, lasso[i + 1])
            maxY = max(maxY, lasso[i + 1])
            i += 2
        }
        return hypot(maxX - minX, maxY - minY) * zoom <= touchSlop
    }

    private fun dispatchTap(docX: Float, docY: Float) {
        val page = pageAt(docX, docY)
        if (page >= 0) {
            val localY = docY - topEdge(page)
            // Pole tekstowe rysuje się nad kartką, więc łapie stuknięcie pierwsze.
            val text = textAt(page, docX, localY)
            if (text != null) {
                listener?.textTapped(page, text.id)
                return
            }
            // Kształt leży nad zdjęciem, więc pierwszy łapie stuknięcie.
            val shape = shapeAt(page, docX, localY)
            if (shape != null) {
                listener?.shapeTapped(page, shape.id)
                return
            }
            val onScreen = pages.firstOrNull { it.index == page }
            // Ostatnie na spisie leży na wierzchu — tak jak przy rysowaniu.
            val image = onScreen?.images?.lastOrNull { image ->
                docX >= image.x && docX <= image.x + image.width &&
                    localY >= image.y && localY <= image.y + image.height
            }
            if (image != null) {
                listener?.imageTapped(page, image.id)
                return
            }
        }
        listener?.emptyAreaTapped()
    }

    private fun cancel() {
        dropWetStroke()
        dropDraft()
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
        dropWetStroke()
        dropDraft()
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

    // Wet stroke: points go straight into the engine in page coordinates

    private fun enqueueWetPoints(event: MotionEvent, pointerIndex: Int, page: Int) {
        val stroke = wet ?: return
        if (page < 0) return
        val top = topEdge(page)
        val toolType = event.getToolType(pointerIndex)
        val type = when (toolType) {
            MotionEvent.TOOL_TYPE_MOUSE -> InputToolType.MOUSE
            MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER -> InputToolType.STYLUS
            else -> InputToolType.TOUCH
        }
        val stylus = toolType == MotionEvent.TOOL_TYPE_STYLUS

        wetBuffer.clear()
        // Zdarzenia przychodzą spakowane: najpierw punkty historyczne, na końcu bieżący.
        for (i in 0..event.historySize) {
            val current = i == event.historySize
            val rawX = if (current) event.getX(pointerIndex) else event.getHistoricalX(pointerIndex, i)
            val rawY = if (current) event.getY(pointerIndex) else event.getHistoricalY(pointerIndex, i)
            val time = if (current) event.eventTime else event.getHistoricalEventTime(i)
            val point = toDoc(rawX, rawY)
            // Punkt, którego silnik nie przyjmie (czas stoi, duplikat), kosztuje
            // tylko ten punkt, nie całą kreskę.
            runCatching {
                wetBuffer.add(
                    type = type,
                    x = point[0],
                    y = point[1] - top,
                    elapsedTimeMillis = (time - wetStartTime).coerceAtLeast(0L),
                    pressure = if (stylus) {
                        val raw = if (current) {
                            event.getPressure(pointerIndex)
                        } else {
                            event.getHistoricalPressure(pointerIndex, i)
                        }
                        raw.coerceIn(0f, 1f)
                    } else {
                        StrokeInput.NO_PRESSURE
                    },
                    tiltRadians = if (stylus) {
                        val raw = if (current) {
                            event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex)
                        } else {
                            event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, pointerIndex, i)
                        }
                        raw.coerceIn(0f, (Math.PI / 2).toFloat())
                    } else {
                        StrokeInput.NO_TILT
                    },
                    orientationRadians = if (stylus) {
                        val raw = if (current) {
                            event.getAxisValue(MotionEvent.AXIS_ORIENTATION, pointerIndex)
                        } else {
                            event.getHistoricalAxisValue(MotionEvent.AXIS_ORIENTATION, pointerIndex, i)
                        }
                        // MotionEvent liczy od „góry" w [-PI, PI], silnik od „lewej" w [0, 2PI).
                        (raw + 2.5f * Math.PI.toFloat()).mod(2f * Math.PI.toFloat())
                    } else {
                        StrokeInput.NO_ORIENTATION
                    },
                )
            }
        }
        runCatching {
            stroke.enqueueInputs(wetBuffer, ImmutableStrokeInputBatch.EMPTY)
            stroke.updateShape((event.eventTime - wetStartTime).coerceAtLeast(0L))
        }
    }

    private fun finishWetStroke() {
        val stroke = wet ?: return
        wet = null
        val page = wetPage
        wetPage = -1
        invalidate()

        val engineStroke = runCatching {
            stroke.finishInput()
            stroke.updateShape()
            stroke.toImmutable()
        }.getOrNull() ?: return
        if (engineStroke.inputs.size == 0) return

        var model = Strokes.toModel(engineStroke, settings.toInkTool(wetTool))
        // Linijka i kształty: otwarta kreska prostuje się, zamknięta staje się
        // kołem, trójkątem albo prostokątem.
        if (wetTool == EditorTool.RULER) model = Shapes.snap(model)
        listener?.strokeFinished(page, model)
    }

    private fun dropWetStroke() {
        wet = null
        wetPage = -1
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }

    companion object {
        const val TAP_TIMEOUT_MS = 400L

        /** Zapas wokół kształtu przy stukaniu, w punktach ekranu. */
        const val TAP_REACH = 12f

        /** Dotknięcie szersze niż tyle to dłoń, nie opuszek. */
        const val FINGERTIP_DP = 45f

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
    }
}
