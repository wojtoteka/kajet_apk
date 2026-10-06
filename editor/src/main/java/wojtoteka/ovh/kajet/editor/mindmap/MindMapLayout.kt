package wojtoteka.ovh.kajet.editor.mindmap

import wojtoteka.ovh.kajet.core.model.MindMapContent
import wojtoteka.ovh.kajet.core.model.MindNode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Rozkładanie mapy myśli: temat główny w środku, gałęzie dookoła niego.
 *
 * Do tej pory mapa szła w prawo kolumnami, poziom po poziomie. Wygląda to
 * porządnie, ale czyta się źle: przy czterech gałęziach po kilkoro dzieci
 * kartka rośnie w dół na kilka ekranów, a na telefonie zostaje z tego wąski,
 * bardzo długi pasek. Mapa myśli ma się rozchodzić na boki - po to właśnie
 * jest mapą, a nie spisem.
 *
 * Rachunek jest przepisany JEDEN DO JEDNEGO z serwera (src/lib/mindmap-layout.ts).
 * Musi taki zostać: tę samą mapę układa i przycisk „Rozłóż gałęzie" tutaj,
 * i KajetAI po swoich zmianach. Gdyby liczyły inaczej, mapa przeskakiwałaby
 * przy każdej synchronizacji.
 *
 * 1. Każde poddrzewo dostaje WYCINEK KOŁA, tym szerszy, im więcej miejsca na
 *    obwodzie potrzebują jego liście. Wycinki są rozłączne, więc gałęzie nie
 *    mają się gdzie na siebie nałożyć.
 * 2. Poziom to pierścień. Promień musi zmieścić się między węzłem rodzica
 *    a węzłem dziecka (w głąb) i dać każdemu węzłowi tyle łuku, ile zajmuje
 *    jego bok (w poprzek). Bierzemy większy z tych dwóch warunków.
 */
object MindMapLayout {

    const val GAP_X = 90f
    const val GAP_Y = 26f
    const val GAP_ROOTS = 60f

    /** Najmniejszy prześwit między węzłami - i w głąb, i w poprzek pierścienia. */
    const val GAP = 44f
    /** Przerwa między osobnymi mapami, gdy na jednej kartce jest ich kilka. */
    const val GAP_CLUSTERS = 96f

    private const val START_X = 40f
    private const val START_Y = 40f

    private data class Slot(val id: String, val depth: Int, val angle: Double, val span: Double)

    fun arrange(map: MindMapContent): MindMapContent {
        if (map.nodes.isEmpty()) return map

        val byId = map.nodes.associateBy { it.id }
        fun width(id: String) = byId[id]?.width ?: MindMapSizes.DEFAULT_WIDTH
        fun height(id: String) = byId[id]?.height ?: MindMapSizes.DEFAULT_HEIGHT

        // --- drzewo do narysowania ---
        // Węzeł podwieszony w dwóch miejscach należy do pierwszego rodzica, do
        // którego uda się dojść; druga krawędź zostaje w mapie, ale układu nie
        // rusza. To samo zabezpieczenie zatrzymuje obchodzenie na pierścieniu.
        val rawChildren = HashMap<String, MutableList<String>>()
        val hasParent = HashSet<String>()
        for (edge in map.edges) {
            if (edge.fromId !in byId || edge.toId !in byId) continue
            if (edge.fromId == edge.toId) continue
            rawChildren.getOrPut(edge.fromId) { mutableListOf() } += edge.toId
            hasParent += edge.toId
        }

        val kids = HashMap<String, List<String>>()
        val taken = HashSet<String>()

        fun grow(id: String) {
            val mine = mutableListOf<String>()
            for (child in rawChildren[id].orEmpty()) {
                if (!taken.add(child)) continue
                mine += child
            }
            kids[id] = mine
            for (child in mine) grow(child)
        }

        val roots = mutableListOf<String>()
        fun openRoot(id: String) {
            if (!taken.add(id)) return
            roots += id
            grow(id)
        }

        for (node in map.nodes) if (node.id !in hasParent) openRoot(node.id)
        // Zostały węzły zamknięte w pierścieniu - każdy zaczyna własną mapę,
        // żeby nie przepadł.
        for (node in map.nodes) openRoot(node.id)

        /*
         * Ile obwodu należy się każdej gałęzi. Nie liczba liści, tylko ich
         * SZEROKOŚĆ: skoro promień musi pomieścić najszerszy węzeł pierścienia,
         * to bez tego jeden długi napis rozpychał całe koło.
         */
        val rim = HashMap<String, Float>()
        fun weigh(id: String): Float {
            val mine = kids[id].orEmpty()
            val total = if (mine.isEmpty()) {
                width(id) + GAP
            } else {
                mine.fold(0f) { sum, child -> sum + weigh(child) }
            }
            rim[id] = total
            return total
        }
        for (root in roots) weigh(root)

        val placed = HashMap<String, Pair<Float, Float>>()
        var clusterTop = START_Y

        for (root in roots) {
            val slots = mutableListOf<Slot>()

            fun cut(id: String, depth: Int, from: Double, to: Double) {
                slots += Slot(id, depth, (from + to) / 2.0, to - from)
                val mine = kids[id].orEmpty()
                if (mine.isEmpty()) return
                val total = mine.fold(0f) { sum, child -> sum + (rim[child] ?: 1f) }
                    .takeIf { it > 0f } ?: 1f

                var at = from
                for (child in mine) {
                    val share = ((rim[child] ?: 1f) / total).toDouble() * (to - from)
                    cut(child, depth + 1, at, at + share)
                    at += share
                }
            }

            /*
             * Pełne koło przesunięte tak, żeby PIERWSZE dziecko wypadło
             * dokładnie po prawej. Przy jednej gałęzi wychodzi z tego węzeł
             * obok korzenia, przy dwóch - lewo i prawo, przy czterech - krzyż.
             */
            val rootKids = kids[root].orEmpty()
            val wholeRim = rootKids.fold(0f) { sum, child -> sum + (rim[child] ?: 1f) }
                .takeIf { it > 0f } ?: 1f
            val firstShare = if (rootKids.isEmpty()) {
                0.0
            } else {
                ((rim[rootKids.first()] ?: 1f) / wholeRim).toDouble() * 2 * PI
            }
            cut(root, 0, -firstShare / 2, -firstShare / 2 + 2 * PI)

            // --- promienie pierścieni ---
            val byDepth = slots.groupBy { it.depth }
            val parentOf = HashMap<String, String>()
            for ((id, mine) in kids) for (child in mine) parentOf[child] = id

            val deepest = slots.maxOf { it.depth }
            val radius = HashMap<Int, Double>()
            radius[0] = 0.0

            for (depth in 1..deepest) {
                val here = byDepth[depth].orEmpty()
                val previous = radius[depth - 1] ?: 0.0

                // W głąb: od boku rodzica do boku dziecka. Oba boki mierzymy
                // w kierunku DZIECKA, bo tamtędy biegnie odcinek między nimi.
                var inward = 0.0
                for (slot in here) {
                    val parent = parentOf[slot.id]
                    val mine = reach(width(slot.id), height(slot.id), slot.angle)
                    val theirs = if (parent != null) {
                        reach(width(parent), height(parent), slot.angle)
                    } else {
                        0.0
                    }
                    inward = max(inward, mine + theirs)
                }

                // W poprzek: łuk przypadający na węzeł musi pomieścić jego bok.
                var sideways = 0.0
                for (slot in here) {
                    if (slot.span <= 0.0) continue
                    val across = span(width(slot.id), height(slot.id), slot.angle)
                    sideways = max(sideways, (across + GAP) / slot.span)
                }

                radius[depth] = max(previous + inward + GAP, sideways)
            }

            // --- z biegunowych na zwykłe ---
            val local = HashMap<String, Pair<Float, Float>>()
            var left = Float.MAX_VALUE
            var top = Float.MAX_VALUE
            var right = -Float.MAX_VALUE
            var bottom = -Float.MAX_VALUE

            for (slot in slots) {
                val distance = radius[slot.depth] ?: 0.0
                val x = (distance * cos(slot.angle) - width(slot.id) / 2.0).roundToInt().toFloat()
                val y = (distance * sin(slot.angle) - height(slot.id) / 2.0).roundToInt().toFloat()
                local[slot.id] = x to y
                left = min(left, x)
                top = min(top, y)
                right = max(right, x + width(slot.id))
                bottom = max(bottom, y + height(slot.id))
            }

            for ((id, at) in local) {
                placed[id] = (at.first - left + START_X) to (at.second - top + clusterTop)
            }

            clusterTop += bottom - top + GAP_CLUSTERS
        }

        return map.copy(
            nodes = map.nodes.map { node ->
                val position = placed[node.id] ?: return@map node
                node.copy(x = position.first, y = position.second)
            },
        )
    }

    /*
     * Miejsce dla węzła, który urósł w miejscu.
     *
     * Węzeł rośnie pod dłuższe hasło - gdy KajetAI zmieni mu napis albo gdy
     * ktoś w nim pisze - ale układu wtedy nie liczymy od nowa: przesuwanie
     * całej mapy przy poprawianiu literówki byłoby wścibskie. Tyle że rosnący
     * węzeł wchodził wtedy na sąsiadów i zasłaniał im hasła, a rozdzielać
     * trzeba było ręcznie.
     *
     * Teraz odsuwamy TYLKO to, na co urośnięty węzeł najechał, i tylko tyle,
     * ile trzeba: w poziomie albo w pionie, zależnie od tego, co wymaga
     * krótszego ruchu, zawsze w stronę od niego. Odsunięty węzeł może z kolei
     * najechać na kolejny - ten też się odsuwa, jak kostki domina. Węzeł raz
     * ustawiony już się nie rusza, więc nic nie kręci się w kółko.
     *
     * Rachunek jest przepisany jeden do jednego z serwera
     * (makeRoom w src/lib/mindmap-layout.ts).
     */

    /** Prześwit, który zostaje między odsuniętymi węzłami. */
    const val ROOM_GAP = 24f

    private class Box(var x: Float, var y: Float, val w: Float, val h: Float)

    private fun collide(a: Box, b: Box, gap: Float): Boolean =
        a.x < b.x + b.w + gap && b.x < a.x + a.w + gap &&
            a.y < b.y + b.h + gap && b.y < a.y + a.h + gap

    /**
     * Odsuwa węzły, na które najechały węzły z [grown]. Węzły z [grown] stoją
     * w miejscu. Gdy nic na nic nie najechało, oddaje tę samą mapę.
     */
    fun makeRoom(map: MindMapContent, grown: Collection<String>): MindMapContent {
        val boxes = LinkedHashMap<String, Box>()
        for (node in map.nodes) boxes[node.id] = Box(node.x, node.y, node.width, node.height)

        val queue = grown.filter { it in boxes }.distinct().toMutableList()
        val settled = queue.toHashSet()
        val moved = HashSet<String>()

        var at = 0
        while (at < queue.size) {
            val pusher = boxes.getValue(queue[at])
            at += 1

            for (node in map.nodes) {
                if (node.id in settled) continue
                val box = boxes.getValue(node.id)
                if (!collide(pusher, box, ROOM_GAP)) continue

                // Kierunek od środka tego, co pcha. Ruch po osi, na której
                // wystarczy mniej - przy równych w bok.
                val right = box.x + box.w / 2 >= pusher.x + pusher.w / 2
                val down = box.y + box.h / 2 >= pusher.y + pusher.h / 2
                val alongX = if (right) pusher.x + pusher.w + ROOM_GAP - box.x else box.x + box.w + ROOM_GAP - pusher.x
                val alongY = if (down) pusher.y + pusher.h + ROOM_GAP - box.y else box.y + box.h + ROOM_GAP - pusher.y
                val horizontal = alongX <= alongY
                val forward = if (horizontal) right else down

                if (horizontal) box.x += if (forward) alongX else -alongX
                else box.y += if (forward) alongY else -alongY

                // Dalej w tę samą stronę, póki stoi na którymś z już
                // ustawionych. Ruch jest w jedną stronę, więc to się kończy.
                for (guard in queue.indices) {
                    val blocker = queue.firstOrNull { collide(boxes.getValue(it), box, ROOM_GAP) } ?: break
                    val other = boxes.getValue(blocker)
                    if (horizontal) {
                        box.x = if (forward) other.x + other.w + ROOM_GAP else other.x - ROOM_GAP - box.w
                    } else {
                        box.y = if (forward) other.y + other.h + ROOM_GAP else other.y - ROOM_GAP - box.h
                    }
                }

                settled += node.id
                queue += node.id
                moved += node.id
            }
        }

        if (moved.isEmpty()) return map
        return map.copy(
            nodes = map.nodes.map { node ->
                if (node.id !in moved) return@map node
                val box = boxes.getValue(node.id)
                node.copy(x = box.x.roundToInt().toFloat(), y = box.y.roundToInt().toFloat())
            },
        )
    }

    /** Odległość od środka prostokąta do jego brzegu w podanym kierunku. */
    private fun reach(w: Float, h: Float, angle: Double): Double =
        abs(cos(angle)) * (w / 2.0) + abs(sin(angle)) * (h / 2.0)

    /** Szerokość prostokąta mierzona W POPRZEK podanego kierunku. */
    private fun span(w: Float, h: Float, angle: Double): Double =
        abs(sin(angle)) * w + abs(cos(angle)) * h

    fun visible(map: MindMapContent): Set<String> {
        val byId = map.nodes.associateBy { it.id }
        val children = HashMap<String, MutableList<String>>()
        val hasParent = HashSet<String>()
        for (edge in map.edges) {
            if (edge.fromId !in byId || edge.toId !in byId) continue
            if (edge.toId in hasParent) continue
            children.getOrPut(edge.fromId) { mutableListOf() } += edge.toId
            hasParent += edge.toId
        }

        val visible = HashSet<String>()
        fun descend(id: String) {
            if (!visible.add(id)) return
            val node = byId[id] ?: return
            if (node.collapsed) return
            children[id].orEmpty().forEach { descend(it) }
        }
        map.nodes.filter { it.id !in hasParent }.forEach { descend(it.id) }

        // Węzły w pętli albo osierocone też pokazujemy, żeby nie znikły z oczu.
        map.nodes.forEach { if (it.id !in visible && !hiddenUnderCollapsed(it.id, byId, children, hasParent)) visible += it.id }
        return visible
    }

    private fun hiddenUnderCollapsed(
        id: String,
        byId: Map<String, MindNode>,
        children: Map<String, List<String>>,
        hasParent: Set<String>,
    ): Boolean {
        if (id !in hasParent) return false
        val parent = children.entries.firstOrNull { id in it.value }?.key ?: return false
        val parentNode = byId[parent] ?: return false
        return parentNode.collapsed || hiddenUnderCollapsed(parent, byId, children, hasParent)
    }

    fun hasChildren(map: MindMapContent, id: String): Boolean = map.edges.any { it.fromId == id }

    /** Ilu potomków chowa zwinięcie tego węzła. Odporny na pętle w krawędziach. */
    fun hiddenDescendants(map: MindMapContent, id: String): Int {
        val seen = HashSet<String>()
        fun walk(current: String) {
            for (edge in map.edges) {
                if (edge.fromId == current && edge.toId != id && seen.add(edge.toId)) walk(edge.toId)
            }
        }
        walk(id)
        return seen.size
    }
}

/*
 * Rozmiar węzła pod długość hasła - przepisany z serwera
 * (src/lib/mindmap-layout.ts, fitNodeSize) i musi liczyć to samo.
 *
 * Węzeł ma stały rozmiar i przycina to, co się nie zmieściło, więc dłuższe
 * hasło po prostu ZNIKA - bez śladu, że coś tam jeszcze było. Miar pisma tu
 * nie ma, więc szerokość znaku jest oszacowana, ale nie jedną liczbą na
 * wszystkie znaki: „WWW" i „ili" są w tym samym kroju szerokie zupełnie
 * inaczej. Oszacowanie ma wychodzić raczej za duże niż za małe - węzeł
 * odrobinę za wysoki nikomu nie przeszkadza, a ucięte hasło bardzo.
 *
 * Tam, gdzie miary pisma są (ekran mapy po odpowiedzi KajetAI i „Rozłóż
 * gałęzie"), rozmiar liczy [measured] z prawdziwego pomiaru, tym samym
 * sposobem: szerokość do [MAX_WIDTH], a hasło łamane i tak - wyrównane.
 */
object MindMapSizes {

    const val DEFAULT_WIDTH = 160f
    const val DEFAULT_HEIGHT = 64f
    /**
     * Granica szerokości. 400, nie 280: KajetAI pisze krótko, ale pełnymi
     * zdaniami, a przy 280 zwykłe zdanie łamało się na kilka ciasnych wierszy
     * i węzeł trzeba było rozciągać ręcznie.
     */
    const val MAX_WIDTH = 400f
    /** Krok szerokości - węzły nie stoją na przypadkowych ułamkach piksela. */
    private const val WIDTH_STEP = 20f
    const val DEFAULT_FONT_SIZE = 15f

    /**
     * `line-height: 1.3` z edytora WWW.
     *
     * Widok węzła musi użyć tej samej proporcji przy każdym przybliżeniu:
     * stałe `24.sp` z kroju `body` nie maleje razem z `fontSize`, więc hasło
     * zjeżdżało na dół ramki i znikało przy oddalaniu.
     */
    const val LINE_RATIO = 1.3f
    /**
     * Wyściółka węzła, obustronnie - w jednostkach mapy.
     *
     * To jest `padding: 6px 10px` z węzła w edytorze WWW (measureNodeText.ts).
     * Widok węzła MUSI odkładać dokładnie tyle miejsca, bo inaczej rachunek
     * wysokości nie zgadza się z tym, co naprawdę widać, i ostatni wiersz
     * hasła znika bez śladu.
     */
    const val PAD_X = 20f
    const val PAD_Y = 12f

    private const val NARROW = "iljI.,;:'!|[]()ft"
    private const val WIDE = "mwMW@%"
    private const val DIGITS = "0123456789"

    private fun charRatio(sign: Char): Float = when {
        sign == ' ' || sign == '\t' -> 0.28f
        NARROW.contains(sign) -> 0.33f
        WIDE.contains(sign) -> 0.92f
        DIGITS.contains(sign) -> 0.57f
        // Wielka litera - także polska. Porównanie z wersją małą odróżnia
        // litery od znaków przestankowych, które są sobie równe w obu wersjach.
        sign != sign.lowercaseChar() && sign == sign.uppercaseChar() -> 0.68f
        else -> 0.55f
    }

    private fun inkWidth(text: String, fontSize: Float): Float {
        var total = 0f
        for (sign in text) total += charRatio(sign) * fontSize
        return total
    }

    /** Ile wierszy zajmie hasło w węźle o takiej szerokości wnętrza. */
    private fun linesNeeded(text: String, usable: Float, fontSize: Float): Int {
        if (usable <= 0f) return 1
        val spaceWidth = inkWidth(" ", fontSize)
        var lines = 0

        for (paragraph in text.split("\n")) {
            var taken = 0f
            var count = 1

            for (word in paragraph.split(" ")) {
                val wordWidth = inkWidth(word, fontSize)

                if (wordWidth > usable) {
                    // Wyraz dłuższy niż cały wiersz łamie się w środku.
                    if (taken > 0f) {
                        count += 1
                        taken = 0f
                    }
                    val rows = ceil(wordWidth / usable).toInt()
                    count += rows - 1
                    taken = wordWidth - (rows - 1) * usable
                    continue
                }

                val withSpace = if (taken > 0f) taken + spaceWidth + wordWidth else wordWidth
                if (withSpace > usable) {
                    count += 1
                    taken = wordWidth
                } else {
                    taken = withSpace
                }
            }

            lines += count
        }

        return max(1, lines)
    }

    /** Szerokość i wysokość węzła, w których hasło się zmieści. */
    fun fit(text: String, fontSize: Float = DEFAULT_FONT_SIZE): Pair<Float, Float> {
        val clean = text.trim()
        if (clean.isEmpty()) return DEFAULT_WIDTH to DEFAULT_HEIGHT

        val longest = clean.split("\n").maxOf { inkWidth(it, fontSize) }
        var width = min(
            MAX_WIDTH,
            max(DEFAULT_WIDTH, ceil((longest + PAD_X) / WIDTH_STEP) * WIDTH_STEP),
        )

        var lines = linesNeeded(clean, width - PAD_X, fontSize)

        // Hasło i tak się łamie: zwężamy węzeł, dopóki wierszy nie przybywa.
        if (lines > clean.split("\n").size) {
            var narrower = DEFAULT_WIDTH
            while (narrower < width) {
                val there = linesNeeded(clean, narrower - PAD_X, fontSize)
                if (there <= lines) {
                    width = narrower
                    lines = there
                    break
                }
                narrower += WIDTH_STEP
            }
        }

        val height = max(DEFAULT_HEIGHT, ceil(lines * fontSize * LINE_RATIO + PAD_Y))

        return width to height
    }

    /**
     * Rozmiar węzła z PRAWDZIWEGO pomiaru pisma - ten sam rachunek co [fit],
     * tylko bez zgadywania szerokości znaków.
     *
     * [natural] to szerokość najdłuższego wiersza bez łamania, [paragraphs] -
     * ile wierszy wpisano ręcznie, a [layout] mierzy napis złamany do podanej
     * szerokości wnętrza i oddaje liczbę wierszy i wysokość pisma. Wszystko
     * w jednostkach mapy.
     */
    fun measured(
        natural: Float,
        paragraphs: Int,
        layout: (usable: Float) -> Pair<Int, Float>,
    ): Pair<Float, Float> {
        var width = min(
            MAX_WIDTH,
            max(DEFAULT_WIDTH, ceil((natural + PAD_X + SLACK) / WIDTH_STEP) * WIDTH_STEP),
        )
        var (lines, textHeight) = layout(width - PAD_X - SLACK)

        if (lines > paragraphs) {
            var narrower = DEFAULT_WIDTH
            while (narrower < width) {
                val there = layout(narrower - PAD_X - SLACK)
                if (there.first <= lines) {
                    width = narrower
                    lines = there.first
                    textHeight = there.second
                    break
                }
                narrower += WIDTH_STEP
            }
        }

        return width to max(DEFAULT_HEIGHT, ceil(textHeight + PAD_Y + SLACK))
    }

    /**
     * Zapas na zaokrąglenia pomiaru i wystające końce pochyłych liter -
     * bez niego napis równo na styk potrafił przy innym przybliżeniu zejść
     * do kolejnego wiersza.
     */
    private const val SLACK = 4f

    /**
     * Rozmiar, w którym hasło się zmieści - ale nigdy mniejszy niż teraz.
     *
     * Węzeł tylko ROŚNIE. Skurczenie go po skasowaniu połowy hasła cofałoby
     * ręczne rozciągnięcie przy poprawianiu literówki; od zmniejszania jest
     * uchwyt.
     */
    fun grown(node: MindNode): Pair<Float, Float> {
        val fontSize = if (node.fontSize > 0f) node.fontSize else DEFAULT_FONT_SIZE
        val (width, height) = fit(node.text, fontSize)
        return max(node.width, width) to max(node.height, height)
    }
}
