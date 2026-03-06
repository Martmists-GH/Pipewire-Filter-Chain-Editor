package com.martmists.pipewire.editor.graph

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import java.util.UUID


class GraphState {
    val nodes: SnapshotStateMap<String, Node> = mutableStateMapOf()
    val connections: SnapshotStateList<Connection> = mutableStateListOf()
    var chainName by mutableStateOf("My Filter Chain")
    var filePath by mutableStateOf<String?>(null)

    var scale by mutableStateOf(1f)
    var offsetX by mutableStateOf(40f)
    var offsetY by mutableStateOf(40f)

    var selectedId by mutableStateOf<String?>(null)
    var renamingId by mutableStateOf<String?>(null)

    fun addNode(type: String, x: Float, y: Float): Node {
        val def = NodeDefinition.NODE_DEFS[type] ?: return Node(
            type = type,
            name = type,
            x = x,
            y = y,
            portsIn = emptyList(),
            portsOut = emptyList(),
            params = emptyMap()
        )
        val id = UUID.randomUUID().toString().take(8)
        val node = Node(
            id = id,
            type = type,
            name = type.lowercase().replace(Regex("[^a-z0-9]+"), "_").trimEnd('_') + "_" + id.take(4),
            x = x,
            y = y,
            portsIn = def.portsIn.toList(),
            portsOut = def.portsOut.toList(),
            params = def.parameters.toMap()
        )
        nodes[node.id] = node
        return node
    }

    fun updateNode(id: String, transform: Node.() -> Node) {
        nodes[id] = nodes[id]?.transform() ?: return
    }

    fun deleteNode(id: String) {
        nodes.remove(id)
        connections.removeAll { it.fromNode == id || it.toNode == id }
        if (selectedId == id) selectedId = null
    }

    fun deleteSelected() {
        val dead = nodes.values.filter { it.selected }.map { it.id }.toSet()
        dead.forEach { deleteNode(it) }
    }

    fun disconnectAll(id: String) {
        connections.removeAll { it.fromNode == id || it.toNode == id }
    }

    fun deleteConnection(id: String) {
        connections.removeAll { it.id == id }
    }

    fun addConnection(fromNode: String, fromPort: String, toNode: String, toPort: String) {
        val isDuplicate = connections.any {
            it.fromNode == fromNode && it.fromPort == fromPort &&
                    it.toNode == toNode && it.toPort == toPort
        }
        if (!isDuplicate) {
            connections.removeAll { it.toNode == toNode && it.toPort == toPort }

            connections.add(
                Connection(
                    fromNode = fromNode,
                    fromPort = fromPort,
                    toNode = toNode,
                    toPort = toPort
                )
            )
        }
    }

    fun selectOnly(id: String) {
        nodes.keys.forEach { nid ->
            nodes[nid] = nodes[nid]!!.copy(selected = nid == id)
        }
        selectedId = id
    }

    fun toggleSelect(id: String) {
        val sel = !(nodes[id]?.selected ?: false)
        nodes[id] = nodes[id]!!.copy(selected = sel)
        if (sel) selectedId = id else if (selectedId == id) selectedId = null
    }

    fun clearSelection() {
        nodes.keys.forEach { nid ->
            if (nodes[nid]?.selected == true) nodes[nid] = nodes[nid]!!.copy(selected = false)
        }
        selectedId = null
    }

    fun selectInRect(x0: Float, y0: Float, x1: Float, y1: Float) {
        nodes.values.forEach { node ->
            val nx = node.x
            val ny = node.y
            val inside = nx + NODE_WIDTH >= x0 && nx <= x1 && ny + node.height() >= y0 && ny <= y1
            if (inside) {
                nodes[node.id] = node.copy(selected = true)
            }
        }
        selectedId = nodes.values.firstOrNull { it.selected }?.id
    }

    fun clearAll() {
        nodes.clear(); connections.clear(); selectedId = null
    }

    fun load(newNodes: Map<String, Node>, newConnections: List<Connection>, chain: String) {
        nodes.clear(); connections.clear()
        nodes.putAll(newNodes); connections.addAll(newConnections)
        chainName = chain; selectedId = null
    }

    fun fitView(canvasW: Float, canvasH: Float, density: Float = 1f) {
        if (nodes.isEmpty()) return
        val xs = nodes.values.map { it.x }
        val ys = nodes.values.map { it.y }
        val xs2 = nodes.values.map { it.x + NODE_WIDTH }
        val ys2 = nodes.values.map { it.y + it.height() }
        val wx0 = xs.min() - 40f
        val wy0 = ys.min() - 40f
        val wx1 = xs2.max() + 40f
        val wy1 = ys2.max() + 40f
        scale = minOf(canvasW / ((wx1 - wx0) * density), canvasH / ((wy1 - wy0) * density), 2.0f)
        offsetX = canvasW / 2f - ((wx0 + wx1) / 2f) * scale * density
        offsetY = canvasH / 2f - ((wy0 + wy1) / 2f) * scale * density
    }

    fun defaultGraph() {
        clearAll()
        val src = addNode("Source",  80f,  160f)
        val snk = addNode("Sink",    600f, 160f)
        addConnection(src.id,  "FL",  snk.id,  "FL")
        addConnection(src.id,  "FR",  snk.id,  "FR")
    }
}
