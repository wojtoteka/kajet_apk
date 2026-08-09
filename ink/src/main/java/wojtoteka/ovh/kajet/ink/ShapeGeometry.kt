package wojtoteka.ovh.kajet.ink

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import wojtoteka.ovh.kajet.core.model.Rect
import wojtoteka.ovh.kajet.core.model.ShapeElement
import wojtoteka.ovh.kajet.core.model.ShapeKind
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Geometria kształtów wstawianych ręcznie.
 *
 * Jedno miejsce dla wszystkich, którzy kształt rysują albo w niego trafiają:
 * kartka na ekranie, eksport do PDF i miniatura w bibliotece. Dzięki temu
 * elipsa w pliku PDF ma ten sam obrys co elipsa pod rysikiem.
 *
 * Rachunki na punktach są zwykłym Kotlinem — bez [Path] i bez [Canvas] — więc
 * dają się sprawdzić testem jednostkowym. Rysowanie siedzi niżej, w
 * [ShapePainter].
 */
object ShapeGeometry {

    /** Poniżej tego boku przeciągnięcie było stuknięciem, nie rysowaniem. */
    const val MIN_SIDE = 6f

    /** Na tyle odcinków dzieli się elipsę przy liczeniu trafienia. */
    private const val ELLIPSE_STEPS = 48

    private const val STAR_ARMS = 5

    /** Klasyczna pięcioramienna gwiazda: wcięcie na 0,382 promienia. */
    private const val STAR_INNER = 0.382f

    private const val ARROW_HEAD_DEGREES = 26f

    /** Obrót dosnapowuje się do ćwiartek, kiedy ręka jest już blisko. */
    private const val ANGLE_SNAP_DEG = 45f
    private const val ANGLE_SNAP_TOLERANCE_DEG = 4f

    // --- Punkty ---

    /**
     * Wierzchołki kształtu PRZED obrotem, w układzie strony: x, y, x, y…
     * Elipsa i prostokąt zaokrąglony dostają wielokąt przybliżający — do
     * trafiania w kształt to wystarcza, a rysunek i tak idzie przez [Path].
     */
    fun localPoints(shape: ShapeElement): FloatArray {
        val left = min(shape.x, shape.x + shape.width)
        val top = min(shape.y, shape.y + shape.height)
        val right = max(shape.x, shape.x + shape.width)
        val bottom = max(shape.y, shape.y + shape.height)
        val width = right - left
        val height = bottom - top

        return when (shape.kind) {
            ShapeKind.LINE, ShapeKind.ARROW -> floatArrayOf(
                shape.x,
                shape.y,
                shape.x + shape.width,
                shape.y + shape.height,
            )

            ShapeKind.RECTANGLE, ShapeKind.ROUNDED_RECTANGLE -> floatArrayOf(
                left, top,
                right, top,
                right, bottom,
                left, bottom,
            )

            ShapeKind.TRIANGLE -> floatArrayOf(
                left + width / 2f, top,
                right, bottom,
                left, bottom,
            )

            ShapeKind.DIAMOND -> floatArrayOf(
                left + width / 2f, top,
                right, top + height / 2f,
                left + width / 2f, bottom,
                left, top + height / 2f,
            )

            ShapeKind.STAR -> star(left, top, width, height)
            ShapeKind.ELLIPSE -> ellipse(left, top, width, height)
        }
    }

    private fun star(left: Float, top: Float, width: Float, height: Float): FloatArray {
        val cx = left + width / 2f
        val cy = top + height / 2f
        val points = FloatArray(STAR_ARMS * 4)
        for (i in 0 until STAR_ARMS * 2) {
            val angle = (-90f + i * 180f / STAR_ARMS) * DEG
            val reach = if (i % 2 == 0) 0.5f else 0.5f * STAR_INNER
            points[i * 2] = cx + width * reach * cos(angle)
            points[i * 2 + 1] = cy + height * reach * sin(angle)
        }
        return points
    }

    private fun ellipse(left: Float, top: Float, width: Float, height: Float): FloatArray {
        val cx = left + width / 2f
        val cy = top + height / 2f
        val points = FloatArray(ELLIPSE_STEPS * 2)
        for (i in 0 until ELLIPSE_STEPS) {
            val angle = 2f * Math.PI.toFloat() * i / ELLIPSE_STEPS
            points[i * 2] = cx + width / 2f * cos(angle)
            points[i * 2 + 1] = cy + height / 2f * sin(angle)
        }
        return points
    }

    /** Wierzchołki po obrocie — tak, jak kształt leży na stronie. */
    fun points(shape: ShapeElement): FloatArray {
        val points = localPoints(shape)
        rotateInPlace(points, shape.centerX, shape.centerY, shape.rotation)
        return points
    }

    /**
     * Punkty uchwytów: cztery rogi prostokąta odniesienia, a przy linii i
     * strzałce dwa końce. Kolejność rogów: lewy górny, prawy górny, prawy
     * dolny, lewy dolny — uchwyt naprzeciwko to `(i + 2) % 4`.
     */
    fun handlePoints(shape: ShapeElement): FloatArray {
        val points = if (shape.kind.open) {
            floatArrayOf(shape.x, shape.y, shape.x + shape.width, shape.y + shape.height)
        } else {
            val box = shape.box()
            floatArrayOf(
                box.left, box.top,
                box.right, box.top,
                box.right, box.bottom,
                box.left, box.bottom,
            )
        }
        rotateInPlace(points, shape.centerX, shape.centerY, shape.rotation)
        return points
    }

    /** Prostokąt obejmujący kształt PO obrocie — do rysowania i do wzrostu strony. */
    fun bounds(shape: ShapeElement): Rect {
        val points = points(shape)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var i = 0
        while (i < points.size) {
            minX = min(minX, points[i])
            maxX = max(maxX, points[i])
            minY = min(minY, points[i + 1])
            maxY = max(maxY, points[i + 1])
            i += 2
        }
        val reach = shape.strokeWidth / 2f
        return Rect(minX - reach, minY - reach, maxX + reach, maxY + reach)
    }

    // --- Trafianie ---

    /**
     * Czy punkt trafia w kształt.
     *
     * Kształt wypełniony bierze się całym polem, pusty tylko obrysem — inaczej
     * duży prostokąt narysowany wokół notatek łapałby każde stuknięcie
     * wymierzone w pismo pod nim.
     */
    fun hits(shape: ShapeElement, px: Float, py: Float, tolerance: Float): Boolean {
        val local = rotatePoint(px, py, shape.centerX, shape.centerY, -shape.rotation)
        val points = localPoints(shape)
        val reach = tolerance + shape.strokeWidth / 2f

        if (shape.kind.open) {
            return Strokes.distanceToSegment(
                local[0], local[1],
                points[0], points[1],
                points[2], points[3],
            ) <= reach
        }

        val filled = shape.fill != 0 && (shape.fill ushr 24) != 0
        if (filled && inPolygon(local[0], local[1], points)) return true
        return edgeDistance(local[0], local[1], points) <= reach
    }

    internal fun inPolygon(px: Float, py: Float, polygon: FloatArray): Boolean {
        var inside = false
        val count = polygon.size / 2
        var j = count - 1
        for (i in 0 until count) {
            val xi = polygon[i * 2]
            val yi = polygon[i * 2 + 1]
            val xj = polygon[j * 2]
            val yj = polygon[j * 2 + 1]
            if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    private fun edgeDistance(px: Float, py: Float, polygon: FloatArray): Float {
        val count = polygon.size / 2
        var best = Float.MAX_VALUE
        var j = count - 1
        for (i in 0 until count) {
            best = min(
                best,
                Strokes.distanceToSegment(
                    px, py,
                    polygon[j * 2], polygon[j * 2 + 1],
                    polygon[i * 2], polygon[i * 2 + 1],
                ),
            )
            j = i
        }
        return best
    }

    // --- Rysowanie przeciągnięciem i uchwytami ---

    /**
     * Kształt rozciągnięty od punktu do punktu — tak powstaje przy rysowaniu.
     *
     * [square] to proporcje 1:1: kształt zamknięty dostaje równe boki, a linia
     * i strzałka kąt dosnapowany do wielokrotności 45 stopni.
     */
    fun fitTo(
        shape: ShapeElement,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        square: Boolean,
    ): ShapeElement {
        var dx = endX - startX
        var dy = endY - startY

        if (square) {
            if (shape.kind.open) {
                val length = hypot(dx, dy)
                val angle = atan2(dy, dx) / DEG
                val snapped = (Math.round(angle / 45f) * 45f) * DEG
                dx = length * cos(snapped)
                dy = length * sin(snapped)
            } else {
                val side = max(abs(dx), abs(dy))
                dx = side * signOf(dx)
                dy = side * signOf(dy)
            }
        }

        return if (shape.kind.open) {
            shape.copy(x = startX, y = startY, width = dx, height = dy)
        } else {
            shape.copy(
                x = min(startX, startX + dx),
                y = min(startY, startY + dy),
                width = abs(dx),
                height = abs(dy),
            )
        }
    }

    /**
     * Kształt po przeciągnięciu uchwytu [index] do punktu na stronie.
     *
     * Uchwyt naprzeciwko stoi w miejscu — także przy obróconym kształcie.
     * Dlatego rachunek idzie w układzie kształtu (bez obrotu), a na końcu
     * środek wraca na stronę obrócony o ten sam kąt: bez tego obrócony
     * prostokąt uciekałby spod palca przy każdym rozciągnięciu.
     */
    fun dragHandle(
        shape: ShapeElement,
        index: Int,
        px: Float,
        py: Float,
        square: Boolean,
    ): ShapeElement {
        val cx = shape.centerX
        val cy = shape.centerY
        val moved = rotatePoint(px, py, cx, cy, -shape.rotation)

        val handles = if (shape.kind.open) {
            floatArrayOf(shape.x, shape.y, shape.x + shape.width, shape.y + shape.height)
        } else {
            val box = shape.box()
            floatArrayOf(
                box.left, box.top,
                box.right, box.top,
                box.right, box.bottom,
                box.left, box.bottom,
            )
        }
        val count = handles.size / 2
        if (index !in 0 until count) return shape

        val opposite = if (shape.kind.open) (index + 1) % 2 else (index + 2) % 4
        val anchorX = handles[opposite * 2]
        val anchorY = handles[opposite * 2 + 1]

        // Nowy kształt w układzie bez obrotu: kotwica plus punkt pod palcem.
        val stretched = if (shape.kind.open && index == 0) {
            fitTo(shape, moved[0], moved[1], anchorX, anchorY, square)
        } else {
            fitTo(shape, anchorX, anchorY, moved[0], moved[1], square)
        }

        // Środek przesunął się w układzie kształtu; na stronie musi wylądować
        // obrócony wokół dawnego środka, żeby kotwica została na swoim miejscu.
        val center = rotatePoint(stretched.centerX, stretched.centerY, cx, cy, shape.rotation)
        return stretched.copy(
            x = center[0] - stretched.width / 2f,
            y = center[1] - stretched.height / 2f,
        )
    }

    /**
     * Kąt kształtu obróconego uchwytem stojącym nad górną krawędzią.
     * Blisko ćwiartki kąt się do niej dosnapowuje — inaczej „prosto" nigdy nie
     * wychodzi prosto.
     */
    fun rotatedTo(shape: ShapeElement, px: Float, py: Float): ShapeElement {
        val angle = atan2(py - shape.centerY, px - shape.centerX) / DEG + 90f
        var degrees = ((angle % 360f) + 360f) % 360f
        val snapped = Math.round(degrees / ANGLE_SNAP_DEG) * ANGLE_SNAP_DEG
        if (abs(degrees - snapped) <= ANGLE_SNAP_TOLERANCE_DEG) degrees = snapped % 360f
        return shape.copy(rotation = degrees)
    }

    /** Czy kształt jest już czymś więcej niż stuknięciem. */
    fun bigEnough(shape: ShapeElement): Boolean =
        max(abs(shape.width), abs(shape.height)) >= MIN_SIDE

    // --- Ścieżka do rysowania ---

    fun path(shape: ShapeElement): Path = Path().also { buildPath(shape, it) }

    fun buildPath(shape: ShapeElement, into: Path) {
        into.reset()
        val box = shape.box()
        val rect = RectF(box.left, box.top, box.right, box.bottom)

        when (shape.kind) {
            ShapeKind.LINE, ShapeKind.ARROW -> {
                val x1 = shape.x
                val y1 = shape.y
                val x2 = shape.x + shape.width
                val y2 = shape.y + shape.height
                into.moveTo(x1, y1)
                into.lineTo(x2, y2)
                if (shape.kind == ShapeKind.ARROW) arrowHead(into, x1, y1, x2, y2, shape.strokeWidth)
            }

            ShapeKind.RECTANGLE -> into.addRect(rect, Path.Direction.CW)

            ShapeKind.ROUNDED_RECTANGLE -> {
                val radius = shape.corner.coerceIn(0f, 0.5f) * min(box.width, box.height)
                into.addRoundRect(rect, radius, radius, Path.Direction.CW)
            }

            ShapeKind.ELLIPSE -> into.addOval(rect, Path.Direction.CW)

            else -> {
                val points = localPoints(shape)
                into.moveTo(points[0], points[1])
                var i = 2
                while (i < points.size) {
                    into.lineTo(points[i], points[i + 1])
                    i += 2
                }
                into.close()
            }
        }

        if (shape.rotation != 0f) {
            val matrix = Matrix()
            matrix.setRotate(shape.rotation, shape.centerX, shape.centerY)
            into.transform(matrix)
        }
    }

    private fun arrowHead(
        into: Path,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        strokeWidth: Float,
    ) {
        val length = hypot(x2 - x1, y2 - y1)
        if (length < 1f) return
        // Grot rośnie z grubością kreski, ale nigdy nie zjada całej strzałki.
        val head = min(max(10f, strokeWidth * 5f), length * 0.4f)
        val direction = atan2(y2 - y1, x2 - x1)
        val spread = ARROW_HEAD_DEGREES * DEG
        into.moveTo(x2, y2)
        into.lineTo(
            x2 - head * cos(direction - spread),
            y2 - head * sin(direction - spread),
        )
        into.moveTo(x2, y2)
        into.lineTo(
            x2 - head * cos(direction + spread),
            y2 - head * sin(direction + spread),
        )
    }

    // --- Drobiazgi ---

    /** Barwa z przezroczystością kształtu wmieszaną w kanał alfa. */
    fun withOpacity(argb: Int, opacity: Float): Int {
        val alpha = ((argb ushr 24) * opacity.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
        return (alpha shl 24) or (argb and 0x00FFFFFF)
    }

    private const val DEG = (Math.PI / 180.0).toFloat()

    private fun signOf(value: Float): Float = if (value < 0f) -1f else 1f

    internal fun rotatePoint(
        px: Float,
        py: Float,
        cx: Float,
        cy: Float,
        degrees: Float,
    ): FloatArray {
        if (degrees == 0f) return floatArrayOf(px, py)
        val angle = degrees * DEG
        val cosine = cos(angle)
        val sine = sin(angle)
        val dx = px - cx
        val dy = py - cy
        return floatArrayOf(cx + dx * cosine - dy * sine, cy + dx * sine + dy * cosine)
    }

    private fun rotateInPlace(points: FloatArray, cx: Float, cy: Float, degrees: Float) {
        if (degrees == 0f) return
        val angle = degrees * DEG
        val cosine = cos(angle)
        val sine = sin(angle)
        var i = 0
        while (i < points.size) {
            val dx = points[i] - cx
            val dy = points[i + 1] - cy
            points[i] = cx + dx * cosine - dy * sine
            points[i + 1] = cy + dx * sine + dy * cosine
            i += 2
        }
    }
}

/**
 * Rysuje kształty na canvasie. Osobny obiekt zamiast wspólnych pędzli w
 * [ShapeGeometry], bo kartka rysuje na wątku głównym, a eksport do PDF na
 * roboczym — jeden pędzel dzielony przez oba potrafiłby zmienić kolor w
 * połowie rysunku.
 */
class ShapePainter {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    fun draw(canvas: Canvas, shape: ShapeElement) {
        ShapeGeometry.buildPath(shape, path)

        if (!shape.kind.open && shape.fill != 0) {
            fillPaint.color = ShapeGeometry.withOpacity(shape.fill, shape.opacity)
            canvas.drawPath(path, fillPaint)
        }

        strokePaint.color = ShapeGeometry.withOpacity(shape.color, shape.opacity)
        strokePaint.strokeWidth = shape.strokeWidth.coerceAtLeast(0.2f)
        canvas.drawPath(path, strokePaint)
    }

    fun draw(canvas: Canvas, shapes: List<ShapeElement>) {
        for (shape in shapes) draw(canvas, shape)
    }
}
