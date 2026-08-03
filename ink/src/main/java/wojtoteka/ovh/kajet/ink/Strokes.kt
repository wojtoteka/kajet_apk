package wojtoteka.ovh.kajet.ink

import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.InkTool
import wojtoteka.ovh.kajet.core.model.InputKind
import wojtoteka.ovh.kajet.core.model.Rect
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object Strokes {

    private const val COORD_DECIMALS = 2
    private const val PRESSURE_DECIMALS = 3

    private const val MAX_TILT = (Math.PI / 2).toFloat()

    private const val FULL_TURN = (2 * Math.PI).toFloat()

    fun toEngine(stroke: InkStroke): Stroke {
        val type = when (stroke.input) {
            InputKind.STYLUS -> InputToolType.STYLUS
            InputKind.FINGER -> InputToolType.TOUCH
            InputKind.MOUSE -> InputToolType.MOUSE
        }

        val batch = MutableStrokeInputBatch()
        for (point in pointsForEngine(stroke)) {
            // A point the engine still refuses must cost that point, not the whole stroke.
            runCatching {
                batch.add(
                    type = type,
                    x = point.x,
                    y = point.y,
                    elapsedTimeMillis = point.timeMs,
                    pressure = point.pressure,
                    tiltRadians = point.tilt,
                    orientationRadians = point.orientation,
                )
            }
        }

        // Opacity already sits in the alpha channel of the stored colour, so it is passed
        // back unchanged; otherwise the brush would overwrite alpha with full opacity.
        val brush = Brushes.forTool(
            tool = stroke.tool,
            colorArgb = stroke.color,
            width = stroke.size,
            opacity = Brushes.opacityOf(stroke.color),
            epsilon = if (stroke.epsilon.isFinite() && stroke.epsilon > 0f) {
                stroke.epsilon
            } else {
                Brushes.EPSILON
            },
        )
        return Stroke(brush, batch.toImmutable())
    }

    fun toModel(stroke: Stroke, tool: InkTool, id: String = UUID.randomUUID().toString()): InkStroke {
        val inputs = stroke.inputs
        val points = ArrayList<Float>(inputs.size * InkStroke.VALUES_PER_POINT)
        val buffer = StrokeInput()
        var inputType = InputToolType.STYLUS

        var prevX = Float.NaN
        var prevY = Float.NaN
        var prevTime = -1f

        for (i in 0 until inputs.size) {
            inputs.populate(i, buffer)
            if (i == 0) inputType = buffer.toolType

            val x = roundTo(buffer.x, COORD_DECIMALS)
            val y = roundTo(buffer.y, COORD_DECIMALS)
            val time = buffer.elapsedTimeMillis.toFloat()

            // Rounding can collapse neighbouring points onto the same place and time,
            // which the engine refuses, so drop the duplicate while saving.
            if (x == prevX && y == prevY && time == prevTime) continue

            points += x
            points += y
            points += time
            // Rounding can also push a value out of range, so clamp after rounding.
            points += if (buffer.hasPressure) {
                roundTo(buffer.pressure, PRESSURE_DECIMALS).coerceIn(0f, 1f)
            } else {
                InkStroke.MISSING
            }
            points += if (buffer.hasTilt) {
                roundTo(buffer.tiltRadians, PRESSURE_DECIMALS).coerceIn(0f, MAX_TILT)
            } else {
                InkStroke.MISSING
            }
            points += if (buffer.hasOrientation) {
                wrapAngle(roundTo(buffer.orientationRadians, PRESSURE_DECIMALS))
            } else {
                InkStroke.MISSING
            }

            prevX = x
            prevY = y
            prevTime = time
        }

        return InkStroke(
            id = id,
            tool = tool,
            color = stroke.brush.colorIntArgb,
            size = stroke.brush.size,
            epsilon = stroke.brush.epsilon,
            input = when (inputType) {
                InputToolType.STYLUS -> InputKind.STYLUS
                InputToolType.MOUSE -> InputKind.MOUSE
                else -> InputKind.FINGER
            },
            points = points,
        )
    }

    internal fun pointsForEngine(stroke: InkStroke): List<InputPoint> {
        val count = stroke.pointCount
        if (count == 0) return emptyList()

        // Every point must carry the same extras: one missing pressure drops pressure for all.
        var withPressure = true
        var withTilt = true
        var withOrientation = true
        for (i in 0 until count) {
            if (!isPresent(stroke.pressure(i))) withPressure = false
            if (!isPresent(stroke.tilt(i))) withTilt = false
            if (!isPresent(stroke.orientation(i))) withOrientation = false
        }

        val result = ArrayList<InputPoint>(count)
        var prevTime = 0L
        var prevX = Float.NaN
        var prevY = Float.NaN

        for (i in 0 until count) {
            val x = stroke.x(i)
            val y = stroke.y(i)
            if (!x.isFinite() || !y.isFinite()) continue

            // Time may neither go backwards nor repeat together with the same place.
            var time = stroke.timeMs(i).toLong().coerceAtLeast(0L)
            if (time < prevTime) time = prevTime
            if (time == prevTime && x == prevX && y == prevY) time = prevTime + 1

            result += InputPoint(
                x = x,
                y = y,
                timeMs = time,
                pressure = if (withPressure) stroke.pressure(i).coerceIn(0f, 1f) else InkStroke.MISSING,
                tilt = if (withTilt) stroke.tilt(i).coerceIn(0f, MAX_TILT) else InkStroke.MISSING,
                orientation = if (withOrientation) wrapAngle(stroke.orientation(i)) else InkStroke.MISSING,
            )

            prevTime = time
            prevX = x
            prevY = y
        }
        return result
    }

    internal data class InputPoint(
        val x: Float,
        val y: Float,
        val timeMs: Long,
        val pressure: Float,
        val tilt: Float,
        val orientation: Float,
    )

    private fun isPresent(value: Float): Boolean = value != InkStroke.MISSING && value.isFinite()

    internal fun wrapAngle(value: Float): Float {
        if (!value.isFinite()) return 0f
        var angle = value % FULL_TURN
        if (angle < 0f) angle += FULL_TURN
        return if (angle >= FULL_TURN) 0f else angle
    }

    private fun roundTo(value: Float, decimals: Int): Float {
        if (value == InkStroke.MISSING) return value
        var factor = 1f
        repeat(decimals) { factor *= 10f }
        return kotlin.math.round(value * factor) / factor
    }

    // Eraser and hit testing

    fun hitsCircle(stroke: InkStroke, centerX: Float, centerY: Float, radius: Float): Boolean {
        val reach = radius + stroke.size / 2f
        val box = stroke.bounds().expanded(reach)
        if (!box.contains(centerX, centerY)) return false

        val count = stroke.pointCount
        if (count == 0) return false
        if (count == 1) {
            return hypot(stroke.x(0) - centerX, stroke.y(0) - centerY) <= reach
        }
        for (i in 0 until count - 1) {
            val distance = distanceToSegment(
                centerX, centerY,
                stroke.x(i), stroke.y(i),
                stroke.x(i + 1), stroke.y(i + 1),
            )
            if (distance <= reach) return true
        }
        return false
    }

    fun cutFragment(
        stroke: InkStroke,
        centerX: Float,
        centerY: Float,
        radius: Float,
    ): List<InkStroke> {
        val reach = radius + stroke.size / 2f
        val count = stroke.pointCount
        if (count == 0) return emptyList()

        val keep = BooleanArray(count) { i ->
            hypot(stroke.x(i) - centerX, stroke.y(i) - centerY) > reach
        }
        if (keep.all { it }) return listOf(stroke)
        if (keep.none { it }) return emptyList()

        val pieces = mutableListOf<InkStroke>()
        var start = -1
        for (i in 0 until count) {
            if (keep[i]) {
                if (start < 0) start = i
            } else if (start >= 0) {
                addPiece(stroke, start, i - 1, pieces)
                start = -1
            }
        }
        if (start >= 0) addPiece(stroke, start, count - 1, pieces)
        return pieces
    }

    private fun addPiece(source: InkStroke, from: Int, to: Int, out: MutableList<InkStroke>) {
        if (to - from < 1) return
        val points = ArrayList<Float>((to - from + 1) * InkStroke.VALUES_PER_POINT)
        val startTime = source.timeMs(from)
        for (i in from..to) {
            val base = i * InkStroke.VALUES_PER_POINT
            points += source.points[base]
            points += source.points[base + 1]
            points += source.points[base + 2] - startTime
            points += source.points[base + 3]
            points += source.points[base + 4]
            points += source.points[base + 5]
        }
        out += source.copy(id = UUID.randomUUID().toString(), points = points)
    }

    fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val lengthSq = dx * dx + dy * dy
        if (lengthSq < 1e-6f) return hypot(px - ax, py - ay)
        var t = ((px - ax) * dx + (py - ay) * dy) / lengthSq
        t = t.coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    // Lasso

    fun inLasso(stroke: InkStroke, polygon: List<Float>, threshold: Float = 0.6f): Boolean {
        val count = stroke.pointCount
        if (count == 0 || polygon.size < 6) return false
        var inside = 0
        for (i in 0 until count) {
            if (pointInPolygon(stroke.x(i), stroke.y(i), polygon)) inside++
        }
        return inside.toFloat() / count >= threshold
    }

    fun pointInPolygon(px: Float, py: Float, polygon: List<Float>): Boolean {
        var result = false
        val count = polygon.size / 2
        var j = count - 1
        for (i in 0 until count) {
            val xi = polygon[i * 2]
            val yi = polygon[i * 2 + 1]
            val xj = polygon[j * 2]
            val yj = polygon[j * 2 + 1]
            if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) {
                result = !result
            }
            j = i
        }
        return result
    }

    fun bounds(strokes: List<InkStroke>): Rect? {
        if (strokes.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var empty = true
        for (stroke in strokes) {
            for (i in 0 until stroke.pointCount) {
                empty = false
                minX = min(minX, stroke.x(i))
                maxX = max(maxX, stroke.x(i))
                minY = min(minY, stroke.y(i))
                maxY = max(maxY, stroke.y(i))
            }
        }
        return if (empty) null else Rect(minX, minY, maxX, maxY)
    }

    // Ruler

    fun straighten(stroke: InkStroke, toleranceDegrees: Float = 4f): InkStroke {
        val count = stroke.pointCount
        if (count < 2) return stroke

        val x1 = stroke.x(0)
        val y1 = stroke.y(0)
        var x2 = stroke.x(count - 1)
        var y2 = stroke.y(count - 1)

        val dx = x2 - x1
        val dy = y2 - y1
        val length = hypot(dx, dy)
        if (length < 1f) return stroke

        val angle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val snapAngle = kotlin.math.round(angle / 45f) * 45f
        if (abs(angle - snapAngle) <= toleranceDegrees) {
            val radians = Math.toRadians(snapAngle.toDouble())
            x2 = x1 + (length * kotlin.math.cos(radians)).toFloat()
            y2 = y1 + (length * kotlin.math.sin(radians)).toFloat()
        }

        // Pressure comes from the first and last point so the line starts and ends
        // like the one drawn by hand.
        val points = ArrayList<Float>(2 * InkStroke.VALUES_PER_POINT)
        val steps = 24
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            points += x1 + (x2 - x1) * t
            points += y1 + (y2 - y1) * t
            points += stroke.timeMs(0) + (stroke.timeMs(count - 1) - stroke.timeMs(0)) * t
            points += lerp(stroke.pressure(0), stroke.pressure(count - 1), t)
            points += lerp(stroke.tilt(0), stroke.tilt(count - 1), t)
            points += lerp(stroke.orientation(0), stroke.orientation(count - 1), t)
        }
        return stroke.copy(points = points)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float {
        if (a == InkStroke.MISSING || b == InkStroke.MISSING) return InkStroke.MISSING
        return a + (b - a) * t
    }
}
