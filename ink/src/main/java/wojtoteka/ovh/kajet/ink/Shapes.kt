package wojtoteka.ovh.kajet.ink

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import wojtoteka.ovh.kajet.core.model.InkStroke

/**
 * Rozpoznawanie kształtów dla narzędzia „Linijka i kształty".
 *
 * Kreska OTWARTA prostuje się jak dotąd ([Strokes.straighten]), a z
 * wyraźnymi rogami - jak L czy Z - staje się łamaną z prostych odcinków.
 * Łamana, która prawie wraca do początku, to trójkąt albo prostokąt
 * narysowany bez domknięcia - ostatni bok dorysowuje się sam. Kreska
 * ZAMKNIĘTA zamienia się w równą figurę: koło, owal, trójkąt albo
 * prostokąt. Za zamkniętą uchodzi też kreska, która okrąża środek prawie
 * cały raz, choć końce się nie zeszły, i kreska przeciągnięta za swój
 * początek - tak rysuje się koła ręką. Zamknięty bazgroł, który nie
 * przypomina żadnej figury, zostaje odręczny - lepszy własny rysunek niż
 * zgadnięty kształt.
 *
 * Wynik jest zwykłą łamaną w tym samym zapisie punktów co każda kreska,
 * więc gumka, lasso i eksport działają na nim bez żadnych zmian.
 */
object Shapes {

    /** Odstęp końców względem długości kreski, poniżej którego jest „zamknięta". */
    private const val CLOSED_GAP_RATIO = 0.2f

    /** Obrót kreski (stopnie), od którego jest zamknięta mimo przerwy między końcami. */
    private const val CLOSED_TURN_DEG = 280f

    /** Rozrzut odległości od środka względem promienia, poniżej którego to koło. */
    private const val CIRCLE_ROUNDNESS = 0.15f

    /** To samo dla owalu, liczone po sprowadzeniu owalu do koła. */
    private const val OVAL_ROUNDNESS = 0.12f

    /** Stosunek osi, do którego owal jest po prostu kołem. */
    private const val CIRCLE_MAX_RATIO = 1.2f

    /** Dłuższy i węższy zamknięty kształt to już nie owal, tylko pętla. */
    private const val OVAL_MAX_RATIO = 4f

    /** Epsilon upraszczania łamanej względem przekątnej obrysu. */
    private const val SIMPLIFY_RATIO = 0.04f

    /** O ile kąt w rogu może odbiegać od prostego, żeby czworokąt był prostokątem. */
    private const val RIGHT_ANGLE_TOLERANCE_DEG = 15f

    /** Odchylenie od poziomu/pionu, przy którym prostokąt i owal dosnapowują się do osi. */
    private const val AXIS_SNAP_DEG = 10f

    /** Zgięcie mniejsze niż tyle to nie róg, tylko punkt w środku boku. */
    private const val STRAIGHT_DEG = 25f

    /**
     * Zgięcie, od którego róg jest naprawdę rogiem. Uproszczone koło też ma
     * „wierzchołki", ale łagodne (~50 stopni) - trójkąt i prostokąt mają
     * wyraźne (od 60 wzwyż). Bez tego progu kwadrat wygrywałby test koła:
     * jego odległości od środka wahają się tylko o ~11%.
     */
    private const val CRISP_TURN_DEG = 60f

    /**
     * Zgięcie, od którego róg otwartej kreski jest rogiem łamanej. Wyżej niż
     * [CRISP_TURN_DEG], bo łuk po uproszczeniu też się łamie (~50 stopni),
     * a nie powinien zamienić się w łamaną.
     */
    private const val OPEN_CORNER_DEG = 70f

    /** Najwięcej rogów otwartej łamanej - gęsty zygzak to bazgroł, nie figura. */
    private const val MAX_OPEN_CORNERS = 4

    /** Najkrótszy bok łamanej względem długości kreski; krótszy na końcu to haczyk rysika. */
    private const val MIN_SIDE_RATIO = 0.08f

    /** Obrót łamanej (stopnie), od którego to niedomknięta figura, a nie litera U. */
    private const val OPEN_SHAPE_TURN_DEG = 200f

    /** Przerwa między końcami niedomkniętej figury względem długości kreski. */
    private const val OPEN_SHAPE_GAP_RATIO = 0.4f

    /** Odchylenie boku łamanej od wielokrotności 45 stopni, przy którym dosnapowuje się do niej. */
    private const val SIDE_SNAP_DEG = 6f

    private const val CIRCLE_POINTS = 64

    /** Ile punktów w równych odstępach bierze się z kreski do rozpoznawania. */
    private const val SAMPLE_POINTS = 48

    fun snap(stroke: InkStroke): InkStroke {
        val count = stroke.pointCount
        if (count < 8) return Strokes.straighten(stroke)

        val rawX = FloatArray(count) { stroke.x(it) }
        val rawY = FloatArray(count) { stroke.y(it) }
        val length = pathLength(rawX, rawY)
        if (length < 24f) return Strokes.straighten(stroke)

        // Punkty w równych odstępach: ręka zwalnia na zakrętach i przy końcach,
        // a zagęszczone punkty w jednym miejscu przesuwałyby środek koła.
        var (xs, ys) = resample(rawX, rawY, length, SAMPLE_POINTS)
        // Kreska przeciągnięta za swój początek kończy się tam, gdzie zatoczyła
        // pełny obrót - reszta to tylko zakładka.
        val fullTurn = fullTurnIndex(xs, ys)
        if (fullTurn != null) {
            xs = xs.copyOf(fullTurn + 1)
            ys = ys.copyOf(fullTurn + 1)
        }

        val closed = fullTurn != null ||
            isClosed(xs, ys, pathLength(xs, ys)) ||
            abs(totalTurn(xs, ys)) >= CLOSED_TURN_DEG
        if (!closed) return snapOpen(stroke, xs, ys)

        // Najpierw wyraźne rogi, potem koło - w tej kolejności, bo kwadrat
        // jest „okrąglejszy", niż się wydaje, i wygrywałby test koła.
        val corners = corners(xs, ys)
        val crisp = corners.size in 3..4 && turnAngles(corners).all { it >= CRISP_TURN_DEG }
        if (crisp && corners.size == 3) return polygon(stroke, corners)
        if (crisp) return rectangleOrQuad(stroke, corners)
        // Zamknięta gwiazdka czy chmurka to nie figura - zostaje odręczna.
        val oval = fitOval(xs, ys) ?: return stroke
        return ovalStroke(stroke, oval, xs, ys)
    }

    // --- Miary ---

    internal fun pathLength(xs: FloatArray, ys: FloatArray): Float {
        var length = 0f
        for (i in 1 until xs.size) {
            length += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        }
        return length
    }

    internal fun isClosed(xs: FloatArray, ys: FloatArray, length: Float): Boolean {
        val gap = hypot(xs.last() - xs.first(), ys.last() - ys.first())
        return gap <= CLOSED_GAP_RATIO * length
    }

    /** [n] punktów w równych odstępach wzdłuż łamanej o długości [length]. */
    internal fun resample(xs: FloatArray, ys: FloatArray, length: Float, n: Int): Pair<FloatArray, FloatArray> {
        val outX = FloatArray(n)
        val outY = FloatArray(n)
        val step = length / (n - 1)
        var segment = 1
        var segmentStart = 0f
        var segmentLength = hypot(xs[1] - xs[0], ys[1] - ys[0])
        for (k in 0 until n) {
            val target = step * k
            while (segment < xs.size - 1 && segmentStart + segmentLength < target) {
                segmentStart += segmentLength
                segment++
                segmentLength = hypot(xs[segment] - xs[segment - 1], ys[segment] - ys[segment - 1])
            }
            val t = if (segmentLength > 1e-6f) ((target - segmentStart) / segmentLength).coerceIn(0f, 1f) else 0f
            outX[k] = xs[segment - 1] + (xs[segment] - xs[segment - 1]) * t
            outY[k] = ys[segment - 1] + (ys[segment] - ys[segment - 1]) * t
        }
        return outX to outY
    }

    /** Zmiana kierunku między kolejnymi odcinkami, ze znakiem (stopnie). */
    private fun turns(xs: FloatArray, ys: FloatArray): FloatArray {
        if (xs.size < 3) return FloatArray(0)
        return FloatArray(xs.size - 2) { i ->
            val inAngle = atan2(ys[i + 1] - ys[i], xs[i + 1] - xs[i])
            val outAngle = atan2(ys[i + 2] - ys[i + 1], xs[i + 2] - xs[i + 1])
            var turn = Math.toDegrees((outAngle - inAngle).toDouble()).toFloat()
            while (turn > 180f) turn -= 360f
            while (turn < -180f) turn += 360f
            turn
        }
    }

    /** O ile stopni obróciła się kreska od początku do końca - pełne koło to ~360. */
    internal fun totalTurn(xs: FloatArray, ys: FloatArray): Float = turns(xs, ys).sum()

    /** Indeks punktu, w którym kreska zatoczyła pełny obrót, albo null. */
    internal fun fullTurnIndex(xs: FloatArray, ys: FloatArray): Int? {
        var turned = 0f
        for ((i, turn) in turns(xs, ys).withIndex()) {
            turned += turn
            if (abs(turned) >= 360f) return i + 1
        }
        return null
    }

    // --- Koło i owal ---

    /** Owal o środku ([cx], [cy]), półosiach [a] i [b], obrócony o [angle] (radiany). */
    internal data class Oval(val cx: Float, val cy: Float, val a: Float, val b: Float, val angle: Float)

    /**
     * Koło albo owal najlepiej pasujący do kreski, albo null, gdy kreska
     * nie przypomina żadnego z nich. Osie owalu wychodzą z rozrzutu punktów
     * wokół środka; prawie równe osie dają koło dopasowane wprost do punktów,
     * więc i niedomknięty łuk trafia w swój środek.
     */
    internal fun fitOval(xs: FloatArray, ys: FloatArray): Oval? {
        val n = xs.size
        val mx = xs.average().toFloat()
        val my = ys.average().toFloat()
        var sxx = 0f
        var syy = 0f
        var sxy = 0f
        for (i in 0 until n) {
            val dx = xs[i] - mx
            val dy = ys[i] - my
            sxx += dx * dx
            syy += dy * dy
            sxy += dx * dy
        }
        sxx /= n
        syy /= n
        sxy /= n
        val half = (sxx + syy) / 2f
        val spread = hypot((sxx - syy) / 2f, sxy)
        val major = half + spread
        val minor = half - spread
        if (minor < 1e-3f) return null
        val ratio = sqrt(major / minor)

        if (ratio <= CIRCLE_MAX_RATIO) {
            val circle = fitCircle(xs, ys)
            if (circle != null) return circle
        }
        if (ratio > OVAL_MAX_RATIO) return null

        var angle = 0.5f * atan2(2f * sxy, sxx - syy)
        // Dla punktów rozłożonych równo po obwodzie wariancja wzdłuż osi to połowa jej kwadratu.
        val a0 = sqrt(2f * major)
        val b0 = sqrt(2f * minor)
        val c = cos(angle)
        val s = sin(angle)
        val normalized = FloatArray(n) { i ->
            val dx = xs[i] - mx
            val dy = ys[i] - my
            hypot((dx * c + dy * s) / a0, (-dx * s + dy * c) / b0)
        }
        val mean = normalized.average().toFloat()
        if (spreadOf(normalized, mean) / mean >= OVAL_ROUNDNESS) return null

        // Owal prawie wzdłuż osi staje wzdłuż osi - jak prostokąt.
        val degrees = Math.toDegrees(angle.toDouble()).toFloat()
        val snapped = round(degrees / 90f) * 90f
        if (abs(degrees - snapped) <= AXIS_SNAP_DEG) {
            angle = Math.toRadians(snapped.toDouble()).toFloat()
        }
        return Oval(mx, my, a0 * mean, b0 * mean, angle)
    }

    /** Koło metodą najmniejszych kwadratów (Kåsa) - trafia w środek także łuku. */
    private fun fitCircle(xs: FloatArray, ys: FloatArray): Oval? {
        val n = xs.size
        val mx = xs.average()
        val my = ys.average()
        var suu = 0.0
        var svv = 0.0
        var suv = 0.0
        var suuu = 0.0
        var svvv = 0.0
        var suvv = 0.0
        var svuu = 0.0
        for (i in 0 until n) {
            val u = xs[i] - mx
            val v = ys[i] - my
            suu += u * u
            svv += v * v
            suv += u * v
            suuu += u * u * u
            svvv += v * v * v
            suvv += u * v * v
            svuu += v * u * u
        }
        val det = suu * svv - suv * suv
        if (abs(det) < 1e-9) return null
        val r1 = (suuu + suvv) / 2.0
        val r2 = (svvv + svuu) / 2.0
        val cx = (mx + (r1 * svv - r2 * suv) / det).toFloat()
        val cy = (my + (r2 * suu - r1 * suv) / det).toFloat()

        val distances = FloatArray(n) { hypot(xs[it] - cx, ys[it] - cy) }
        val radius = distances.average().toFloat()
        if (radius < 1e-3f) return null
        if (spreadOf(distances, radius) / radius >= CIRCLE_ROUNDNESS) return null
        return Oval(cx, cy, radius, radius, 0f)
    }

    private fun spreadOf(values: FloatArray, mean: Float): Float {
        var variance = 0f
        for (value in values) variance += (value - mean) * (value - mean)
        return sqrt(variance / values.size)
    }

    // --- Rogi ---

    /** Douglas-Peucker: indeksy punktów, które wyznaczają kształt łamanej. */
    internal fun douglasPeucker(xs: FloatArray, ys: FloatArray, epsilon: Float): List<Int> {
        val keep = BooleanArray(xs.size)
        keep[0] = true
        keep[xs.size - 1] = true

        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to xs.size - 1)
        while (stack.isNotEmpty()) {
            val (from, to) = stack.removeLast()
            var farthest = -1
            var record = epsilon
            for (i in from + 1 until to) {
                val d = Strokes.distanceToSegment(xs[i], ys[i], xs[from], ys[from], xs[to], ys[to])
                if (d > record) {
                    record = d
                    farthest = i
                }
            }
            if (farthest >= 0) {
                keep[farthest] = true
                stack.addLast(from to farthest)
                stack.addLast(farthest to to)
            }
        }
        return keep.indices.filter { keep[it] }
    }

    /**
     * Rogi zamkniętej kreski: uproszczenie łamanej, sklejenie końców w jeden
     * róg i odrzucenie punktów, w których kreska prawie się nie zgina -
     * kreska zaczęta w środku boku nie ma tam przecież rogu.
     */
    internal fun corners(xs: FloatArray, ys: FloatArray): List<Pair<Float, Float>> {
        val kept = douglasPeucker(xs, ys, simplifyEpsilon(xs, ys)).map { xs[it] to ys[it] }
        // Początek i koniec to ten sam róg - kreska jest zamknięta. Przy
        // przerwie między końcami róg leży na przecięciu pierwszego i
        // ostatniego boku, nie w połowie przerwy.
        val cyclic = if (kept.size >= 4) {
            closeUp(kept, pathLength(xs, ys))
        } else {
            listOf(
                ((kept.first().first + kept.last().first) / 2f) to ((kept.first().second + kept.last().second) / 2f),
            ) + kept.subList(1, kept.size - 1)
        }

        // Punkt bez zgięcia to nie róg. Powtarzamy, bo zdjęcie jednego punktu
        // potrafi wyprostować sąsiedni.
        var vertices = cyclic
        while (vertices.size > 2) {
            val bends = turnAngles(vertices)
            val straightest = bends.indices.minByOrNull { bends[it] } ?: break
            if (bends[straightest] >= STRAIGHT_DEG) break
            vertices = ArrayList(vertices).apply { removeAt(straightest) }
        }
        return vertices
    }

    private fun simplifyEpsilon(xs: FloatArray, ys: FloatArray): Float {
        var minX = xs[0]
        var maxX = xs[0]
        var minY = ys[0]
        var maxY = ys[0]
        for (i in xs.indices) {
            if (xs[i] < minX) minX = xs[i]
            if (xs[i] > maxX) maxX = xs[i]
            if (ys[i] < minY) minY = ys[i]
            if (ys[i] > maxY) maxY = ys[i]
        }
        return SIMPLIFY_RATIO * hypot(maxX - minX, maxY - minY)
    }

    /**
     * Wierzchołki otwartej kreski: oba końce i punkty, w których kreska
     * wyraźnie się zgina. Jak w [corners], punkt prawie bez zgięcia odpada.
     */
    internal fun openVertices(xs: FloatArray, ys: FloatArray): List<Pair<Float, Float>> {
        var vertices: List<Pair<Float, Float>> =
            douglasPeucker(xs, ys, simplifyEpsilon(xs, ys)).map { xs[it] to ys[it] }
        while (vertices.size > 2) {
            val bends = signedTurns(vertices)
            val straightest = bends.indices.minByOrNull { abs(bends[it]) } ?: break
            if (abs(bends[straightest]) >= STRAIGHT_DEG) break
            vertices = ArrayList(vertices).apply { removeAt(straightest + 1) }
        }
        return vertices
    }

    /** Zgięcie ze znakiem w każdym wewnętrznym wierzchołku otwartej łamanej (stopnie). */
    private fun signedTurns(vertices: List<Pair<Float, Float>>): List<Float> =
        List((vertices.size - 2).coerceAtLeast(0)) { i ->
            turnBetween(vertices[i], vertices[i + 1], vertices[i + 1], vertices[i + 2])
        }

    /** O ile stopni skręca kierunek z odcinka [a0]→[a1] na odcinek [b0]→[b1], ze znakiem. */
    private fun turnBetween(
        a0: Pair<Float, Float>,
        a1: Pair<Float, Float>,
        b0: Pair<Float, Float>,
        b1: Pair<Float, Float>,
    ): Float {
        val inAngle = atan2(a1.second - a0.second, a1.first - a0.first)
        val outAngle = atan2(b1.second - b0.second, b1.first - b0.first)
        var turn = Math.toDegrees((outAngle - inAngle).toDouble()).toFloat()
        while (turn > 180f) turn -= 360f
        while (turn < -180f) turn += 360f
        return turn
    }

    private fun distance(a: Pair<Float, Float>, b: Pair<Float, Float>): Float =
        hypot(b.first - a.first, b.second - a.second)

    // --- Otwarta kreska ---

    /**
     * Otwarta kreska: bez rogów prostuje się w linię, z kilkoma wyraźnymi
     * rogami staje się łamaną (L, Z, U), a łamana, która zawraca prawie do
     * początku, to niedomknięty trójkąt albo prostokąt.
     */
    private fun snapOpen(stroke: InkStroke, xs: FloatArray, ys: FloatArray): InkStroke {
        val length = pathLength(xs, ys)
        val vertices = ArrayList(openVertices(xs, ys))
        // Haczyk przy przyłożeniu albo oderwaniu rysika to nie bok.
        while (vertices.size > 2 && distance(vertices[0], vertices[1]) < MIN_SIDE_RATIO * length) {
            vertices.removeAt(0)
        }
        while (vertices.size > 2 &&
            distance(vertices[vertices.size - 2], vertices.last()) < MIN_SIDE_RATIO * length
        ) {
            vertices.removeAt(vertices.size - 1)
        }

        val turns = signedTurns(vertices)
        val polyline = turns.size in 1..MAX_OPEN_CORNERS &&
            turns.all { abs(it) >= OPEN_CORNER_DEG } &&
            (1 until vertices.size).all { distance(vertices[it - 1], vertices[it]) >= MIN_SIDE_RATIO * length }
        if (!polyline) return Strokes.straighten(stroke)

        val oneWay = turns.all { it > 0f } || turns.all { it < 0f }
        val gap = distance(vertices.first(), vertices.last())
        if (turns.size >= 2 && oneWay &&
            abs(turns.sum()) >= OPEN_SHAPE_TURN_DEG &&
            gap <= OPEN_SHAPE_GAP_RATIO * length
        ) {
            val shape = closeUp(vertices, length)
            if (shape.size in 3..4 && turnAngles(shape).all { it >= CRISP_TURN_DEG }) {
                return if (shape.size == 3) polygon(stroke, shape) else rectangleOrQuad(stroke, shape)
            }
        }
        return polygon(stroke, straightSides(vertices), closed = false)
    }

    /**
     * Rogi figury z niedomkniętej łamanej. Brakujący róg leży tam, gdzie
     * przecinają się pierwszy i ostatni bok. Gdy te boki biegną w jedną
     * stronę, kreska zaczęła się i skończyła na tym samym boku - wtedy
     * brakującego rogu nie ma.
     */
    private fun closeUp(vertices: List<Pair<Float, Float>>, length: Float): List<Pair<Float, Float>> {
        val inner = vertices.subList(1, vertices.size - 1)
        val a0 = vertices[0]
        val a1 = vertices[1]
        val b0 = vertices[vertices.size - 2]
        val b1 = vertices.last()
        if (abs(turnBetween(b0, b1, a0, a1)) < STRAIGHT_DEG) return inner

        val d1x = a1.first - a0.first
        val d1y = a1.second - a0.second
        val d2x = b1.first - b0.first
        val d2y = b1.second - b0.second
        val denominator = d1x * d2y - d1y * d2x
        val middle = ((a0.first + b1.first) / 2f) to ((a0.second + b1.second) / 2f)
        if (abs(denominator) < 1e-6f) return listOf(middle) + inner
        val t = ((b0.first - a0.first) * d2y - (b0.second - a0.second) * d2x) / denominator
        val corner = (a0.first + d1x * t) to (a0.second + d1y * t)
        // Przecięcie daleko od końców kreski to nie róg, który ręka pominęła.
        val reach = OPEN_SHAPE_GAP_RATIO * length
        return if (distance(corner, a0) <= reach && distance(corner, b1) <= reach) {
            listOf(corner) + inner
        } else {
            listOf(middle) + inner
        }
    }

    /**
     * Boki łamanej po kolei, z zachowaną długością; bok prawie poziomy,
     * pionowy albo pod 45 stopni dosnapowuje się do tego kierunku - jak
     * pojedyncza linia z linijki.
     */
    private fun straightSides(vertices: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        val out = ArrayList<Pair<Float, Float>>(vertices.size)
        out += vertices.first()
        for (i in 1 until vertices.size) {
            val (ax, ay) = vertices[i - 1]
            val (bx, by) = vertices[i]
            val side = hypot(bx - ax, by - ay)
            var degrees = Math.toDegrees(atan2(by - ay, bx - ax).toDouble()).toFloat()
            val snapped = round(degrees / 45f) * 45f
            if (abs(degrees - snapped) <= SIDE_SNAP_DEG) degrees = snapped
            val radians = Math.toRadians(degrees.toDouble()).toFloat()
            val (px, py) = out.last()
            out += (px + side * cos(radians)) to (py + side * sin(radians))
        }
        return out
    }

    /** Zgięcie w każdym rogu (stopnie, 0 = prosto, 90 = kąt prosty), cyklicznie. */
    internal fun turnAngles(vertices: List<Pair<Float, Float>>): List<Float> {
        val n = vertices.size
        return List(n) { i ->
            val before = vertices[(i - 1 + n) % n]
            val here = vertices[i]
            val after = vertices[(i + 1) % n]
            val inAngle = atan2(here.second - before.second, here.first - before.first)
            val outAngle = atan2(after.second - here.second, after.first - here.first)
            var turn = Math.toDegrees((outAngle - inAngle).toDouble()).toFloat()
            while (turn > 180f) turn -= 360f
            while (turn < -180f) turn += 360f
            abs(turn)
        }
    }

    // --- Figury ---

    private fun ovalStroke(stroke: InkStroke, oval: Oval, xs: FloatArray, ys: FloatArray): InkStroke {
        val c = cos(oval.angle)
        val s = sin(oval.angle)

        // Owal zaczyna się tam, gdzie zaczęła się kreska, i biegnie w tę samą
        // stronę - pole ze znakiem mówi, czy rysowano zgodnie z zegarem.
        val dx = xs[0] - oval.cx
        val dy = ys[0] - oval.cy
        val start = atan2((-dx * s + dy * c) / oval.b, (dx * c + dy * s) / oval.a)
        var area = 0f
        for (i in xs.indices) {
            val j = (i + 1) % xs.size
            area += xs[i] * ys[j] - xs[j] * ys[i]
        }
        val direction = if (area >= 0f) 1f else -1f

        val outX = FloatArray(CIRCLE_POINTS + 1)
        val outY = FloatArray(CIRCLE_POINTS + 1)
        for (i in 0..CIRCLE_POINTS) {
            val t = start + direction * 2f * Math.PI.toFloat() * (i / CIRCLE_POINTS.toFloat())
            val u = oval.a * cos(t)
            val v = oval.b * sin(t)
            outX[i] = oval.cx + u * c - v * s
            outY[i] = oval.cy + u * s + v * c
        }
        return polylineStroke(stroke, outX, outY)
    }

    private fun polygon(
        stroke: InkStroke,
        vertices: List<Pair<Float, Float>>,
        closed: Boolean = true,
    ): InkStroke {
        // Łamana przez rogi, przy figurze z powrotem do pierwszego. Każdy bok
        // dostaje punkty proporcjonalnie do długości, rogi zostają ostre.
        val path = if (closed) vertices + vertices.first() else vertices
        var perimeter = 0f
        for (i in 1 until path.size) {
            perimeter += hypot(
                path[i].first - path[i - 1].first,
                path[i].second - path[i - 1].second,
            )
        }
        if (perimeter < 1e-3f) return stroke

        val outX = ArrayList<Float>()
        val outY = ArrayList<Float>()
        for (i in 1 until path.size) {
            val (ax, ay) = path[i - 1]
            val (bx, by) = path[i]
            val edge = hypot(bx - ax, by - ay)
            val steps = (48 * edge / perimeter).toInt().coerceAtLeast(2)
            val fromStep = if (i == 1) 0 else 1 // róg wspólny z poprzednim bokiem już jest
            for (step in fromStep..steps) {
                val t = step / steps.toFloat()
                outX += ax + (bx - ax) * t
                outY += ay + (by - ay) * t
            }
        }
        return polylineStroke(stroke, outX.toFloatArray(), outY.toFloatArray())
    }

    private fun rectangleOrQuad(stroke: InkStroke, corners: List<Pair<Float, Float>>): InkStroke {
        val bends = turnAngles(corners)
        val rightAngles = bends.all { abs(it - 90f) <= RIGHT_ANGLE_TOLERANCE_DEG }
        if (!rightAngles) {
            // Romb albo trapez narysowany naprawdę: proste boki, własne kąty.
            return polygon(stroke, corners)
        }

        // Prostokąt: oś z dłuższej pary boków, wymiary ze średnich boków
        // naprzeciwległych, środek ze środka rogów.
        val cx = corners.sumOf { it.first.toDouble() }.toFloat() / 4f
        val cy = corners.sumOf { it.second.toDouble() }.toFloat() / 4f

        fun edge(i: Int): Pair<Float, Float> {
            val (ax, ay) = corners[i]
            val (bx, by) = corners[(i + 1) % 4]
            return (bx - ax) to (by - ay)
        }

        val sideA = (edgeLength(edge(0)) + edgeLength(edge(2))) / 2f
        val sideB = (edgeLength(edge(1)) + edgeLength(edge(3))) / 2f

        // Kierunek pierwszego boku, ujednolicony z przeciwległym (tamten
        // biegnie z powrotem, więc wchodzi z odwróconym znakiem).
        val (e0x, e0y) = edge(0)
        val (e2x, e2y) = edge(2)
        var theta = atan2(e0y - e2y, e0x - e2x)

        // Snap do poziomu/pionu - w duchu snapu 45 stopni z linijki.
        val degrees = Math.toDegrees(theta.toDouble()).toFloat()
        val snapped = round(degrees / 90f) * 90f
        if (abs(degrees - snapped) <= AXIS_SNAP_DEG) {
            theta = Math.toRadians(snapped.toDouble()).toFloat()
        }

        val ux = cos(theta)
        val uy = sin(theta)
        // Prostopadła w stronę drugiego boku, żeby obieg zgadzał się z ręką.
        val cross = e0x * edge(1).second - e0y * edge(1).first
        val sign = if (cross >= 0f) 1f else -1f
        val vx = -uy * sign
        val vy = ux * sign

        val halfA = sideA / 2f
        val halfB = sideB / 2f
        val rect = listOf(
            (cx - ux * halfA - vx * halfB) to (cy - uy * halfA - vy * halfB),
            (cx + ux * halfA - vx * halfB) to (cy + uy * halfA - vy * halfB),
            (cx + ux * halfA + vx * halfB) to (cy + uy * halfA + vy * halfB),
            (cx - ux * halfA + vx * halfB) to (cy - uy * halfA + vy * halfB),
        )

        // Początek figury przy początku kreski, żeby nic nie „przeskoczyło".
        val (sx, sy) = corners.first()
        val startAt = rect.indices.minByOrNull { hypot(rect[it].first - sx, rect[it].second - sy) } ?: 0
        val ordered = List(4) { rect[(startAt + it) % 4] }

        return polygon(stroke, ordered)
    }

    private fun edgeLength(edge: Pair<Float, Float>): Float = hypot(edge.first, edge.second)

    /**
     * Łamana w zapisie kreski: czas biegnie wzdłuż drogi, nacisk i pochylenie
     * przechodzą płynnie od pierwszego do ostatniego punktu ręki - jak przy
     * prostowaniu linii.
     */
    private fun polylineStroke(source: InkStroke, xs: FloatArray, ys: FloatArray): InkStroke {
        val count = source.pointCount
        val startTime = source.timeMs(0)
        val endTime = source.timeMs(count - 1)
        val total = pathLength(xs, ys)

        val points = ArrayList<Float>(xs.size * InkStroke.VALUES_PER_POINT)
        var travelled = 0f
        for (i in xs.indices) {
            if (i > 0) travelled += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
            val t = if (total > 1e-3f) travelled / total else i / (xs.size - 1).coerceAtLeast(1).toFloat()
            points += xs[i]
            points += ys[i]
            points += startTime + (endTime - startTime) * t
            points += lerp(source.pressure(0), source.pressure(count - 1), t)
            points += lerp(source.tilt(0), source.tilt(count - 1), t)
            points += lerp(source.orientation(0), source.orientation(count - 1), t)
        }
        return source.copy(points = points)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float {
        if (a == InkStroke.MISSING || b == InkStroke.MISSING) return InkStroke.MISSING
        return a + (b - a) * t
    }
}
