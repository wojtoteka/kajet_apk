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
            val from = _selected.value
            if (from != null && from != id) {
                connect(from, id)
                _connecting.value = false
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
        _connecting.value = !_connecting.value
    }

    // Łączenie przeciąganiem

    fun startConnecting(fromId: String, x: Float, y: Float) {
        _draggedLine.value = DraggedLine(fromId = fromId, x = x, y = y, targetId = null)
    }

    fun dragConnection(x: Float, y: Float) {
        val current = _draggedLine.value ?: return
        val target = map.nodes.lastOrNull { node ->
            node.id != current.fromId &&
                x >= node.x && x <= node.x + node.width &&
                y >= node.y && y <= node.y + node.height
        }
        _draggedLine.value = current.copy(x = x, y = y, targetId = target?.id)
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
                    shape = parent.shape,
                    font = parent.font,
                    fontSize = parent.fontSize,
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

    fun setText(id: String, text: String) {
        changeWithoutHistory { old ->
            old.copy(nodes = old.nodes.map { if (it.id == id) it.copy(text = text) else it })
        }
    }

    fun setShape(id: String, shape: NodeShape) {
        change(map.copy(nodes = map.nodes.map { if (it.id == id) it.copy(shape = shape) else it }))
    }

    fun setColor(id: String, colorId: String) {
        // A palette choice clears the custom colour, otherwise nothing would show.
        updateNode(id) { it.copy(colorId = colorId, customColor = 0) }
    }

    fun setCustomColor(id: String, argb: Int) {
        updateNode(id) { it.copy(customColor = argb) }
        rememberColor(argb)
    }

    fun setTextColor(id: String, argb: Int) {
        updateNode(id) { it.copy(textColor = argb) }
        rememberColor(argb)
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
        change(map.copy(nodes = map.nodes.map { if (it.id == id) it.copy(ink = strokes) else it }))
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

    fun arrangeBranches() {
        val arranged = MindMapLayout.arrange(map)
        if (arranged != map) change(arranged)
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
