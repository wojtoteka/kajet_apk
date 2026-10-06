package wojtoteka.ovh.kajet.editor.mindmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.NoteFont
import wojtoteka.ovh.kajet.core.model.MindEdge
import wojtoteka.ovh.kajet.core.model.MindMapContent
import wojtoteka.ovh.kajet.core.model.MindNode
import wojtoteka.ovh.kajet.core.model.NodeShape
import wojtoteka.ovh.kajet.core.model.NoteAlign
import wojtoteka.ovh.kajet.editor.NoteViewModel
import wojtoteka.ovh.kajet.editor.MapChange
import wojtoteka.ovh.kajet.storage.SettingsStore
import wojtoteka.ovh.kajet.storage.LibraryRepository
import java.util.UUID
import kotlin.math.max

class MindMapViewModel(
    repo: LibraryRepository,
    settings: SettingsStore,
    path: String,
) : NoteViewModel(repo, settings, path) {

    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()

    private val _edited = MutableStateFlow<String?>(null)
    val edited: StateFlow<String?> = _edited.asStateFlow()

    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting.asStateFlow()

    /*
      Węzeł, od którego łączymy. Trzymamy go osobno od zaznaczenia, bo tryb
      łączenia ma zostać włączony po pierwszej linii: jedna myśl łączy się
      zwykle z kilkoma naraz, a przedtem trzeba było po każdej linii na nowo
      wybierać węzeł i klikać w przycisk. Teraz stukasz kolejno we wszystko, co
      ma się z nim połączyć, i dopiero potem wyłączasz tryb.
    */
    private val _connectFrom = MutableStateFlow<String?>(null)
    val connectFrom: StateFlow<String?> = _connectFrom.asStateFlow()

    private val _draggedLine = MutableStateFlow<DraggedLine?>(null)
    val draggedLine: StateFlow<DraggedLine?> = _draggedLine.asStateFlow()

    private val _inkLabel = MutableStateFlow<String?>(null)
    val inkLabel: StateFlow<String?> = _inkLabel.asStateFlow()

    private var beforeDrag: MindMapContent? = null

    val map: MindMapContent get() = document.value?.mindMap ?: MindMapContent()

    // Dotknięta linia między węzłami. Po dotknięciu pokazuje się przy niej
    // przycisk rozłączenia, bo tak człowiek kasuje linię: dotyka jej, nie szuka
    // listy w ustawieniach węzła.
    private val _selectedEdge = MutableStateFlow<String?>(null)
    val selectedEdge: StateFlow<String?> = _selectedEdge.asStateFlow()

    fun selectEdge(id: String?) {
        _selectedEdge.value = id
        if (id != null) {
            _selected.value = null
            _edited.value = null
        }
    }

    /**
     * Szuka linii, w którą trafił palec. Punkty przychodzą w układzie mapy,
     * a [reach] jest promieniem trafienia w tym samym układzie.
     */
    fun edgeAt(x: Float, y: Float, reach: Float): String? {
        val byId = map.nodes.associateBy { it.id }
        var best: String? = null
        var bestDistance = reach
        for (edge in map.edges) {
            val from = byId[edge.fromId] ?: continue
            val to = byId[edge.toId] ?: continue
            val distance = distanceToSegment(
                x, y,
                from.x + from.width / 2f, from.y + from.height / 2f,
                to.x + to.width / 2f, to.y + to.height / 2f,
            )
            if (distance <= bestDistance) {
                bestDistance = distance
                best = edge.id
            }
        }
        return best
    }

    private fun distanceToSegment(
        x: Float,
        y: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0f) return kotlin.math.hypot(x - x1, y - y1)
        val t = (((x - x1) * dx + (y - y1) * dy) / lengthSquared).coerceIn(0f, 1f)
        return kotlin.math.hypot(x - (x1 + t * dx), y - (y1 + t * dy))
    }

    fun select(id: String?) {
        _selectedEdge.value = null
        if (_connecting.value && id != null) {
            val from = _connectFrom.value ?: _selected.value
            if (from != null && from != id) {
                connect(from, id)
                // Tryb zostaje włączony - można od razu stuknąć w następny
                // węzeł i połączyć ten sam z kilkoma naraz.
                return
            }
        }
        _selected.value = id
        if (id == null) _edited.value = null
    }

    fun edit(id: String?) {
        _edited.value = id
        if (id != null) _selected.value = id
    }

    fun toggleConnecting() {
        val next = !_connecting.value
        _connecting.value = next
        // Źródło zapamiętujemy w chwili włączenia trybu; bez tego stuknięcie
        // w kolejny węzeł przestawiałoby je i linie szłyby nie stamtąd, skąd
        // człowiek chciał.
        _connectFrom.value = if (next) _selected.value else null
    }

    // Łączenie przeciąganiem

    fun startConnecting(fromId: String, x: Float, y: Float) {
        _draggedLine.value = DraggedLine(fromId = fromId, x = x, y = y, targetId = null)
    }

    fun dragConnection(x: Float, y: Float) {
        val current = _draggedLine.value ?: return
        _draggedLine.value = current.copy(x = x, y = y, targetId = nodeNear(x, y, current.fromId))
    }

    /**
     * Węzeł pod palcem - a jeśli palec minął się o włos, to najbliższy w zasięgu
     * [GRAB_MARGIN].
     *
     * Wcześniej liczyło się samo trafienie w prostokąt węzła. Przy mapie z wielu
     * małych myśli oznaczało to, że połowa prób łączenia kończyła się nowym,
     * pustym węzłem obok - i wyglądało to tak, jakby niektórych węzłów nie dało
     * się ze sobą połączyć w ogóle.
     */
    private fun nodeNear(x: Float, y: Float, exceptId: String): String? {
        val candidates = map.nodes.filter { it.id != exceptId }
        candidates.lastOrNull { node ->
            x >= node.x && x <= node.x + node.width &&
                y >= node.y && y <= node.y + node.height
        }?.let { return it.id }

        return candidates
            .map { node -> node to distanceToBox(x, y, node.x, node.y, node.width, node.height) }
            .filter { (_, distance) -> distance <= GRAB_MARGIN }
            .minByOrNull { (_, distance) -> distance }
            ?.first
            ?.id
    }

    /** Odległość punktu od prostokąta; zero, gdy punkt leży w środku. */
    private fun distanceToBox(
        x: Float,
        y: Float,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
    ): Float {
        val dx = maxOf(left - x, 0f, x - (left + width))
        val dy = maxOf(top - y, 0f, y - (top + height))
        return kotlin.math.hypot(dx, dy)
    }

    fun finishConnecting(createInEmpty: Boolean = true) {
        val current = _draggedLine.value ?: return
        _draggedLine.value = null

        val target = current.targetId
        when {
            target != null -> connect(current.fromId, target)

            createInEmpty && draggedFarEnough(current) -> {
                val parent = map.nodes.firstOrNull { it.id == current.fromId } ?: return
                val id = UUID.randomUUID().toString()
                val node = MindNode(
                    id = id,
                    // Punkt puszczenia palca to ma być środek nowego węzła,
                    // a nie jego lewy górny róg.
                    x = current.x - parent.width / 2f,
                    y = current.y - parent.height / 2f,
                    width = parent.width,
                    height = parent.height,
                    colorId = parent.colorId,
                    customColor = parent.customColor,
                    shape = parent.shape,
                    font = parent.font,
                    fontSize = parent.fontSize,
                    align = parent.align,
                )
                change(
                    map.copy(
                        nodes = map.nodes + node,
                        edges = map.edges + MindEdge(UUID.randomUUID().toString(), current.fromId, id),
                    ),
                )
                _selected.value = id
                _edited.value = id
            }
        }
    }

    fun cancelConnecting() {
        _draggedLine.value = null
    }

    private fun draggedFarEnough(line: DraggedLine): Boolean {
        val parent = map.nodes.firstOrNull { it.id == line.fromId } ?: return false
        val centreX = parent.x + parent.width / 2f
        val centreY = parent.y + parent.height / 2f
        return kotlin.math.hypot(line.x - centreX, line.y - centreY) > CONNECT_THRESHOLD
    }

    fun openInkLabel(id: String) {
        _inkLabel.value = id
    }

    fun closeInkLabel() {
        _inkLabel.value = null
    }

    // Węzły

    fun addNode(x: Float, y: Float, text: String = ""): String {
        val id = UUID.randomUUID().toString()
        val node = MindNode(id = id, x = x, y = y, text = text)
        change(map.copy(nodes = map.nodes + node))
        _selected.value = id
        _edited.value = id
        return id
    }

    fun addChild(parentId: String): String? {
        val parent = map.nodes.firstOrNull { it.id == parentId } ?: return null
        val siblings = map.edges.count { it.fromId == parentId }
        val id = UUID.randomUUID().toString()
        val child = MindNode(
            id = id,
            x = parent.x + parent.width + GAP_X,
            y = parent.y + siblings * (parent.height + GAP_Y),
            colorId = parent.colorId,
            customColor = parent.customColor,
            shape = parent.shape,
            font = parent.font,
            fontSize = parent.fontSize,
            align = parent.align,
        )
        change(
            map.copy(
                nodes = map.nodes + child,
                edges = map.edges + MindEdge(UUID.randomUUID().toString(), parentId, id),
            ),
        )
        _selected.value = id
        _edited.value = id
        return id
    }

    /** Nowy węzeł tuż pod wskazanym, podpięty do tego samego rodzica. */
    fun addSibling(id: String): String? {
        val node = map.nodes.firstOrNull { it.id == id } ?: return null
        val parentId = map.edges.firstOrNull { it.toId == id }?.fromId
        val twinId = UUID.randomUUID().toString()
        val twin = MindNode(
            id = twinId,
            x = node.x,
            y = node.y + node.height + GAP_Y,
            colorId = node.colorId,
            customColor = node.customColor,
            shape = node.shape,
            font = node.font,
            fontSize = node.fontSize,
            align = node.align,
        )
        change(
            map.copy(
                nodes = map.nodes + twin,
                // Korzeń nie ma rodzica: wtedy powstaje sam węzeł, bez linii.
                edges = if (parentId != null) {
                    map.edges + MindEdge(UUID.randomUUID().toString(), parentId, twinId)
                } else {
                    map.edges
                },
            ),
        )
        _selected.value = twinId
        _edited.value = twinId
        return twinId
    }

    fun setText(id: String, text: String) {
        // Edytor WWW ucina przy 500 znakach - tu tak samo, żeby pliki się zgadzały.
        val capped = text.take(500)
        changeWithoutHistory { old ->
            val updated = old.copy(
                nodes = old.nodes.map { node ->
                    if (node.id != id) return@map node
                    /*
                     * Węzeł przycina to, co się w nim nie zmieściło - dłuższe
                     * hasło znikało bez śladu, że cokolwiek tam jeszcze jest.
                     * Dlatego po zmianie napisu pudełko ROŚNIE do rozmiaru,
                     * w którym całość się mieści. Nigdy nie maleje: od
                     * zmniejszania jest uchwyt w rogu.
                     */
                    val withText = node.copy(text = capped)
                    val (width, height) = MindMapSizes.grown(withText)
                    withText.copy(width = width, height = height)
                },
            )
            // Węzeł, który urósł, odsuwa sąsiadów, na których najechał -
            // zamiast zasłaniać im hasła.
            val before = old.nodes.firstOrNull { it.id == id }
            val after = updated.nodes.firstOrNull { it.id == id }
            val grew = before != null && after != null &&
                (after.width > before.width || after.height > before.height)
            if (grew) MindMapLayout.makeRoom(updated, listOf(id)) else updated
        }
    }

    fun setShape(id: String, shape: NodeShape) {
        change(map.copy(nodes = map.nodes.map { if (it.id == id) it.copy(shape = shape) else it }))
    }

    fun setColor(id: String, colorId: String) {
        // A palette choice clears the custom colour, otherwise nothing would show.
        updateNode(id) { it.copy(colorId = colorId, customColor = 0) }
    }

    // Do spisu „twoich kolorów" barwa trafia dopiero po zamknięciu okna z
    // tęczą (rememberColor woła panel węzła). Tęcza zgłasza każdy odcień
    // mijany pod palcem, więc zapisywanie po drodze zapychało cały spis.
    fun setCustomColor(id: String, argb: Int) {
        updateNode(id) { it.copy(customColor = argb) }
    }

    fun setTextColor(id: String, argb: Int) {
        updateNode(id) { it.copy(textColor = argb) }
    }

    fun setFont(id: String, font: NoteFont) = updateNode(id) { it.copy(font = font) }

    fun setFontSize(id: String, size: Float) = updateNode(id) {
        it.copy(fontSize = size.coerceIn(8f, 48f))
    }

    fun setBold(id: String, on: Boolean) = updateNode(id) { it.copy(bold = on) }

    fun setItalic(id: String, on: Boolean) = updateNode(id) { it.copy(italic = on) }

    fun setAlign(id: String, align: NoteAlign) = updateNode(id) {
        it.copy(align = align)
    }

    private fun updateNode(id: String, transform: (MindNode) -> MindNode) {
        change(map.copy(nodes = map.nodes.map { if (it.id == id) transform(it) else it }))
    }

    fun nodeConnections(id: String): List<Pair<MindEdge, MindNode>> {
        val byId = map.nodes.associateBy { it.id }
        return map.edges.mapNotNull { edge ->
            when (id) {
                edge.fromId -> byId[edge.toId]?.let { edge to it }
                edge.toId -> byId[edge.fromId]?.let { edge to it }
                else -> null
            }
        }
    }

    fun setInkLabel(id: String, strokes: List<InkStroke>) {
        // Kreski przychodzą w mierze kartki z okna rysunku, więc przed zapisem
        // trafiają w ramkę węzła - inaczej podpis leży obok niego.
        updateNode(id) { MindMapInk.fitted(it, strokes) }
        _inkLabel.value = null
    }

    fun toggleCollapsed(id: String) {
        change(
            map.copy(
                nodes = map.nodes.map { if (it.id == id) it.copy(collapsed = !it.collapsed) else it },
            ),
        )
    }

    fun removeNode(id: String) {
        change(
            map.copy(
                nodes = map.nodes.filterNot { it.id == id },
                edges = map.edges.filterNot { it.fromId == id || it.toId == id },
            ),
        )
        if (_selected.value == id) _selected.value = null
        if (_edited.value == id) _edited.value = null
    }

    fun startDragging() {
        beforeDrag = map
    }

    fun moveNode(id: String, dx: Float, dy: Float) {
        changeWithoutHistory { old ->
            old.copy(
                nodes = old.nodes.map {
                    if (it.id == id) it.copy(x = it.x + dx, y = it.y + dy) else it
                },
            )
        }
    }

    fun finishDragging() {
        val before = beforeDrag ?: return
        beforeDrag = null
        if (before != map) history.record(MapChange(before, map))
        refreshButtons()
    }

    fun resizeNode(id: String, width: Float, height: Float, finished: Boolean) {
        if (finished) {
            finishDragging()
            return
        }
        changeWithoutHistory { old ->
            old.copy(
                nodes = old.nodes.map {
                    if (it.id == id) {
                        it.copy(
                            width = width.coerceIn(80f, 600f),
                            height = height.coerceIn(40f, 400f),
                        )
                    } else {
                        it
                    }
                },
            )
        }
    }

    // Linie

    fun connect(fromId: String, toId: String) {
        val exists = map.edges.any {
            (it.fromId == fromId && it.toId == toId) || (it.fromId == toId && it.toId == fromId)
        }
        if (exists) return
        change(map.copy(edges = map.edges + MindEdge(UUID.randomUUID().toString(), fromId, toId)))
    }

    fun disconnect(edgeId: String) {
        change(map.copy(edges = map.edges.filterNot { it.id == edgeId }))
        if (_selectedEdge.value == edgeId) _selectedEdge.value = null
    }

    // Widok

    fun rememberView(x: Float, y: Float, zoom: Float) {
        changeWithoutHistory { it.copy(viewX = x, viewY = y, zoom = zoom) }
    }

    /**
     * „Rozłóż gałęzie". [measure] to pomiar prawdziwego pisma z ekranu: przy
     * okazji każdy węzeł, w którym hasło się nie mieściło, ROŚNIE - tak samo
     * jak „Rozłóż" na stronie. To naturalny moment na naprawienie pudełek
     * ze starych map.
     */
    fun arrangeBranches(measure: ((MindNode) -> Pair<Float, Float>?)? = null) {
        val grown = if (measure == null) {
            map
        } else {
            map.copy(
                nodes = map.nodes.map { node ->
                    val (width, height) = measure(node) ?: return@map node
                    node.copy(width = max(node.width, width), height = max(node.height, height))
                },
            )
        }
        val arranged = MindMapLayout.arrange(grown)
        if (arranged != map) change(arranged)
    }

    // Po KajetAI

    /*
      Węzły, którym po odpowiedzi KajetAI trzeba dobrać rozmiar.

      Serwer zna hasło, ale nie zna pisma: rozmiar węzła tylko OSZACOWUJE.
      Ekran mapy ma prawdziwe miary, więc tu każdy węzeł, który KajetAI dodał
      albo któremu zmienił napis, jest mierzony jeszcze raz - i dostaje
      najmniejszy rozmiar, w którym całe hasło się mieści. Bez tego zdarzało
      się, że ostatnie słowa znikały pod krawędzią i węzeł trzeba było
      rozciągać ręcznie.
    */
    private val _fitAfterAi = MutableStateFlow<AiFit?>(null)
    val fitAfterAi: StateFlow<AiFit?> = _fitAfterAi.asStateFlow()

    override suspend fun reloadAfterAi() {
        val before = map
        super.reloadAfterAi()
        _fitAfterAi.value = AiFit.between(before, map)
    }

    /**
     * Rozmiary zmierzone na ekranie dla węzłów z [fitAfterAi].
     *
     * Węzeł dodany przez KajetAI dostaje dokładnie zmierzony rozmiar - serwer
     * dał mu tylko oszacowanie. Węzeł, któremu KajetAI zmienił napis, tylko
     * ROŚNIE: mógł być rozciągnięty ręcznie i tego nie cofamy. Gdy KajetAI
     * zmienił budowę mapy, układ liczy się od nowa z nowymi rozmiarami - tym
     * samym rachunkiem co na serwerze, więc mapa nie przeskakuje.
     */
    fun fitMeasured(fit: AiFit, sizes: Map<String, Pair<Float, Float>>) {
        if (_fitAfterAi.value != fit) return
        _fitAfterAi.value = null

        val resized = map.copy(
            nodes = map.nodes.map { node ->
                val (width, height) = sizes[node.id] ?: return@map node
                when (node.id) {
                    in fit.added -> node.copy(width = width, height = height)
                    in fit.retexted -> node.copy(
                        width = max(node.width, width),
                        height = max(node.height, height),
                    )
                    else -> node
                }
            },
        )
        /*
          Bez zmiany budowy układ zostaje, ale węzeł, który po pomiarze urósł,
          nie może wejść na sąsiadów - odsuwamy tylko te, na które najechał.
        */
        val next = if (fit.rearrange) {
            MindMapLayout.arrange(resized)
        } else {
            val old = map.nodes.associateBy { it.id }
            val grew = resized.nodes.filter { node ->
                val was = old[node.id] ?: return@filter false
                node.width > was.width || node.height > was.height
            }.map { it.id }
            MindMapLayout.makeRoom(resized, grew)
        }
        if (next != map) changeWithoutHistory { next }
    }

    private fun change(next: MindMapContent) {
        perform(MapChange(map, next))
    }

    private fun changeWithoutHistory(transform: (MindMapContent) -> MindMapContent) {
        editWithoutHistory { document ->
            document.copy(mindMap = transform(document.mindMap ?: MindMapContent()))
        }
    }

    companion object {
        const val GAP_X = 70f
        const val GAP_Y = 24f

        const val CONNECT_THRESHOLD = 60f

        /**
         * O ile palec może minąć się z węzłem, a i tak trafić. Mniej więcej
         * szerokość opuszka na mapie w zwykłym powiększeniu.
         */
        const val GRAB_MARGIN = 36f
    }

    class Factory(
        private val repo: LibraryRepository,
        private val settings: SettingsStore,
        private val path: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MindMapViewModel(repo, settings, path) as T
    }
}

data class DraggedLine(
    val fromId: String,
    val x: Float,
    val y: Float,
    val targetId: String?,
)

/**
 * Co KajetAI zmienił w mapie: węzły nowe, węzły z nowym napisem i czy ruszył
 * budowę (wtedy serwer rozłożył mapę od nowa).
 */
data class AiFit(
    val added: Set<String>,
    val retexted: Set<String>,
    val rearrange: Boolean,
) {
    val ids: Set<String> get() = added + retexted

    companion object {
        fun between(before: MindMapContent, after: MindMapContent): AiFit? {
            val old = before.nodes.associateBy { it.id }
            val added = after.nodes.filter { it.id !in old }.map { it.id }.toSet()
            val retexted = after.nodes
                .filter { node -> old[node.id]?.let { it.text != node.text } == true }
                .map { it.id }
                .toSet()
            if (added.isEmpty() && retexted.isEmpty()) return null

            val edges = { map: MindMapContent -> map.edges.map { it.fromId to it.toId }.toSet() }
            val rearrange = added.isNotEmpty() ||
                old.size != after.nodes.size ||
                edges(before) != edges(after)
            return AiFit(added, retexted, rearrange)
        }
    }
}
