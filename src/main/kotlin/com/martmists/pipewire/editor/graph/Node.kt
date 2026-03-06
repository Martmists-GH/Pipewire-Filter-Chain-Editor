package com.martmists.pipewire.editor.graph

import androidx.compose.ui.geometry.Offset
import java.util.UUID

data class Node(
    val id: String = UUID.randomUUID().toString().take(8),
    val type: String,
    val name: String,
    val x: Float,
    val y: Float,
    val portsIn: List<String>,
    val portsOut: List<String>,
    val params: Map<String, String>,
    val selected: Boolean = false
) {
    val definition: NodeDefinition? get() = NodeDefinition.NODE_DEFS[type]

    fun height(): Float =
        PORT_TOP_OFFSET + maxOf(portsIn.size, portsOut.size, 1) * PORT_SPACING + PORT_PADDING

    fun portPosition(port: String, isInput: Boolean, scale: Float, offsetX: Float, offsetY: Float, density: Float = 1f): Offset? {
        val list = if (isInput) portsIn else portsOut
        val index = list.indexOf(port)
        if (index < 0) return null
        val nodeX = x * scale * density + offsetX
        val nodeY = y * scale * density + offsetY
        return Offset(
            if (isInput) nodeX else nodeX + NODE_WIDTH * scale * density,
            nodeY + (PORT_TOP_OFFSET + index * PORT_SPACING + PORT_SPACING / 2f) * scale * density
        )
    }

    fun hitTest(pos: Offset, scale: Float, offsetX: Float, offsetY: Float, density: Float = 1f): Boolean {
        val nodeX = x * scale * density + offsetX
        val nodeY = y * scale * density + offsetY
        return pos.x in nodeX..(nodeX + NODE_WIDTH * scale * density) && pos.y in nodeY..(nodeY + height() * scale * density)
    }

    fun portHitTest(pos: Offset, scale: Float, offsetX: Float, offsetY: Float, density: Float = 1f): Pair<String, Boolean>? {
        val radius = (PORT_RADIUS * density) * scale + 6f
        for (port in portsIn) {
            val portPos = portPosition(port, true, scale, offsetX, offsetY, density) ?: continue
            if ((pos - portPos).getDistance() <= radius) return port to true
        }
        for (port in portsOut) {
            val portPos = portPosition(port, false, scale, offsetX, offsetY, density) ?: continue
            if ((pos - portPos).getDistance() <= radius) return port to false
        }
        return null
    }

    fun cleanPorts(): Node {
        fun drop(lst: List<String>, generic: String): List<String> {
            val specific = lst.filter { it != generic }
            return specific.ifEmpty { lst }
        }
        return copy(portsIn = drop(portsIn, "Input"), portsOut = drop(portsOut, "Output"))
    }
}