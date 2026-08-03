package wojtoteka.ovh.kajet.editor.mindmap

import wojtoteka.ovh.kajet.core.model.MindMapContent
import wojtoteka.ovh.kajet.core.model.MindNode

object MindMapLayout {

    const val GAP_X = 90f
    const val GAP_Y = 26f
    const val GAP_ROOTS = 60f

    fun arrange(map: MindMapContent): MindMapContent {
        if (map.nodes.isEmpty()) return map

        val byId = map.nodes.associateBy { it.id }
        val children = HashMap<String, MutableList<String>>()
        val hasParent = HashSet<String>()

        for (edge in map.edges) {
            if (edge.fromId !in byId || edge.toId !in byId) continue
            if (edge.toId in hasParent) continue
            children.getOrPut(edge.fromId) { mutableListOf() } += edge.toId
            hasParent += edge.toId
        }

        val roots = map.nodes.filter { it.id !in hasParent }.map { it.id }
        val positions = HashMap<String, Pair<Float, Float>>()
        val visited = HashSet<String>()

        var top = 0f
        for (root in roots) {
            val height = arrangeBranch(
                id = root,
                left = 0f,
                top = top,
                byId = byId,
                children = children,
                visited = visited,
                result = positions,
            )
            top += height + GAP_ROOTS
        }

        return map.copy(
            nodes = map.nodes.map { node ->
                val position = positions[node.id] ?: return@map node
                node.copy(x = position.first, y = position.second)
            },
        )
    }

    private fun arrangeBranch(
        id: String,
        left: Float,
        top: Float,
        byId: Map<String, MindNode>,
        children: Map<String, List<String>>,
        visited: MutableSet<String>,
        result: MutableMap<String, Pair<Float, Float>>,
    ): Float {
        val node = byId[id] ?: return 0f
        if (!visited.add(id)) return 0f

        val descendants = if (node.collapsed) emptyList() else children[id].orEmpty()
        if (descendants.isEmpty()) {
            result[id] = left to top
            return node.height
        }

        var subtreeHeight = 0f
        val childrenLeft = left + node.width + GAP_X
        for (child in descendants) {
            val height = arrangeBranch(
                id = child,
                left = childrenLeft,
                top = top + subtreeHeight,
                byId = byId,
                children = children,
                visited = visited,
                result = result,
            )
            if (height > 0f) subtreeHeight += height + GAP_Y
        }
        if (subtreeHeight > 0f) subtreeHeight -= GAP_Y

        // Rodzic staje na wysokości środka swoich dzieci.
        val middle = top + (subtreeHeight - node.height) / 2f
        result[id] = left to middle
        return maxOf(subtreeHeight, node.height)
    }

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
