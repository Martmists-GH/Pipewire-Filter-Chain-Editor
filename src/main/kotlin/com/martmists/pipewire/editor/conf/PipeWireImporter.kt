package com.martmists.pipewire.editor.conf

import com.martmists.pipewire.editor.graph.Connection
import com.martmists.pipewire.editor.graph.Node
import com.martmists.pipewire.editor.graph.NodeDefinition
import java.util.UUID
import kotlin.collections.iterator
import kotlin.collections.plusAssign


@Suppress("UNCHECKED_CAST")
class PipeWireImporter {

    private val TOKEN_PATTERN = Regex(
        """"(?:[^"\\]|\\.)*"""" +
                """|'(?:[^'\\]|\\.)*'""" +
                """|\{|\}|\[|]|=""" +
                """|[^\s{}\[\]='"#]+"""
    )

    private fun tokenize(text: String): List<String> {
        val stripped = text.replace(Regex("#[^\n]*"), "")
        return TOKEN_PATTERN.findAll(stripped).map { it.value }.toList()
    }

    private class Parser(private val tokens: List<String>) {
        private var index = 0

        private fun peek(): String? = tokens.getOrNull(index)
        private fun eat(expected: String? = null): String {
            val token = tokens[index]
            if (expected != null && token != expected) {
                error("Expected $expected but got $token at index $index")
            }
            index++
            return token
        }

        private fun value(): Any = when (peek()) {
            "{" -> parseObject()
            "[" -> parseArray()
            else -> unquote(eat())
        }

        private fun parseObject(): Map<String, Any?> {
            eat("{")
            val result = mutableMapOf<String, Any?>()
            val unnamedItems = mutableListOf<Any?>()
            while (peek() != "}" && peek() != null) {
                when (peek()) {
                    "{", "[" -> unnamedItems += value()
                    else -> {
                        val key = unquote(eat())
                        if (peek() == "=") {
                            eat("=")
                            result[key] = value()
                        } else {
                            unnamedItems += key
                        }
                    }
                }
            }
            eat("}")
            if (unnamedItems.isNotEmpty()) {
                result["__items__"] = unnamedItems
            }
            return result
        }

        private fun parseArray(): List<Any?> {
            eat("[")
            val items = mutableListOf<Any?>()
            while (peek() != "]" && peek() != null) {
                items += value()
            }
            eat("]")
            return items
        }

        private fun unquote(s: String): String =
            if (s.length >= 2 && (s.startsWith('"') || s.startsWith('\''))) s.substring(1, s.length - 1) else s


        fun parse(): Map<String, Any?> {
            val result = mutableMapOf<String, Any?>()
            while (peek() != null) {
                val token = peek()!!
                if (token == "{" || token == "[") {
                    value()
                    continue
                }
                val key = unquote(eat())
                if (peek() == "=") {
                    eat("=")
                    result[key] = value()
                }
            }
            return result
        }
    }

    fun parseConf(text: String): Map<String, Any?> = Parser(tokenize(text)).parse()

    fun load(text: String): Triple<String, Map<String, Node>, List<Connection>> {
        val conf = parseConf(text)
        val modules = conf["context.modules"] as? List<*> ?: emptyList<Any>()
        var fcArgs = emptyMap<String, Any?>()

        for (m in modules) {
            val map = m as? Map<String, Any?> ?: continue
            if (map["name"] == "libpipewire-module-filter-chain") {
                fcArgs = map["args"] as? Map<String, Any?> ?: emptyMap()
                break
            }
        }

        val chain = fcArgs["node.description"] as? String
            ?: fcArgs["media.name"] as? String ?: "Filter Chain"
        val graph = fcArgs["filter.graph"] as? Map<String, Any?> ?: emptyMap()
        val captureProps = fcArgs["capture.props"] as? Map<String, Any?> ?: emptyMap()
        val playbackProps = fcArgs["playback.props"] as? Map<String, Any?> ?: emptyMap()
        val rawNodes = graph["nodes"] as? List<*> ?: emptyList<Any>()
        val rawLinks = graph["links"] as? List<*> ?: emptyList<Any>()
        val rawInputs = graph["inputs"] as? List<*> ?: emptyList<Any>()
        val rawOutputs = graph["outputs"] as? List<*> ?: emptyList<Any>()

        val nodeMap = mutableMapOf<String, Node>()

        for (rn in rawNodes) {
            val rnode = rn as? Map<String, Any?> ?: continue
            val pwType = rnode["type"] as? String ?: "builtin"
            val pwLabel = rnode["label"] as? String ?: ""
            val rawName = rnode["name"] as? String ?: "node_${nodeMap.size}"
            val typeName = if (pwType == "ladspa") {
                NodeDefinition.LABEL_TO_TYPE[pwLabel] ?: "LADSPA Plugin"
            } else {
                NodeDefinition.LABEL_TO_TYPE[pwLabel] ?: "EQ Peak"
            }
            val definition = NodeDefinition.NODE_DEFS[typeName]

            val id = UUID.randomUUID().toString().take(8)
            var portsIn = (definition?.portsIn ?: listOf("Input")).toMutableList()
            var portsOut = (definition?.portsOut ?: listOf("Output")).toMutableList()

            if (pwType == "ladspa") {
                NodeDefinition.LADSPA_LABEL_PORT[pwLabel]?.let { (pin, pout) ->
                    portsIn = pin.toMutableList()
                    portsOut = pout.toMutableList()
                }
            }

            val controlMap = rnode["control"] as? Map<String, Any?> ?: emptyMap()
            val params = (definition?.parameters ?: emptyMap()).toMutableMap()
            for ((k, v) in controlMap) {
                if (k != "__items__") {
                    params[k] = v.toString()
                }
            }
            if (pwType == "ladspa") {
                params["plugin"] = rnode["plugin"]?.toString() ?: ""
                params["label"] = pwLabel
            }

            nodeMap[rawName] = Node(
                id = id,
                type = typeName,
                name = rawName,
                x = 0f,
                y = 0f,
                portsIn = portsIn,
                portsOut = portsOut,
                params = params
            )
        }

        val sourceId = UUID.randomUUID().toString().take(8)
        val sourceNode = Node(
            id = sourceId,
            type = "Source",
            name = "source",
            x = 0f,
            y = 0f,
            portsIn = emptyList(),
            portsOut = NodeDefinition.NODE_DEFS["Source"]!!.portsOut.toList(),
            params = captureProps.filter { it.key != "__items__" }.mapValues { it.value.toString() }
        )

        val sinkId = UUID.randomUUID().toString().take(8)
        val sinkNode = Node(
            id = sinkId,
            type = "Sink",
            name = "sink",
            x = 0f,
            y = 0f,
            portsIn = NodeDefinition.NODE_DEFS["Sink"]!!.portsIn.toList(),
            portsOut = emptyList(),
            params = playbackProps.filter { it.key != "__items__" }.mapValues { it.value.toString() }
        )

        val connections = mutableListOf<Connection>()
        val finalNodeMap = nodeMap.toMutableMap()

        fun splitPort(s: String): Pair<String, String> {
            val trimmed = s.trim()
            val colonIndex = trimmed.indexOf(':')
            return if (colonIndex < 0) {
                trimmed to ""
            } else {
                trimmed.substring(0, colonIndex).trim() to trimmed.substring(colonIndex + 1).trim()
            }
        }

        fun addOutputPort(node: Node, port: String): Node =
            if (port.isNotEmpty() && port !in node.portsOut) node.copy(portsOut = node.portsOut + port) else node

        fun addInputPort(node: Node, port: String): Node =
            if (port.isNotEmpty() && port !in node.portsIn) node.copy(portsIn = node.portsIn + port) else node

        for (rl in rawLinks) {
            val link = rl as? Map<String, Any?> ?: continue
            val (outNodeName, outPortName) = splitPort(link["output"]?.toString() ?: "")
            val (inNodeName, inPortName) = splitPort(link["input"]?.toString() ?: "")
            val fromNode = finalNodeMap[outNodeName] ?: continue
            val toNode = finalNodeMap[inNodeName] ?: continue
            finalNodeMap[outNodeName] = addOutputPort(fromNode, outPortName)
            finalNodeMap[inNodeName] = addInputPort(toNode, inPortName)
            connections += Connection(
                fromNode = fromNode.id,
                fromPort = outPortName,
                toNode = toNode.id,
                toPort = inPortName
            )
        }

        val sourceOutputs = NodeDefinition.NODE_DEFS["Source"]!!.portsOut
        for ((index, entry) in rawInputs.withIndex()) {
            val (targetName, targetPort) = splitPort(entry.toString())
            val targetNode = finalNodeMap[targetName] ?: continue
            val sourcePort = sourceOutputs.getOrElse(index) { "out${index + 1}" }
            finalNodeMap[targetName] = addInputPort(targetNode, targetPort)
            connections += Connection(
                fromNode = sourceId,
                fromPort = sourcePort,
                toNode = targetNode.id,
                toPort = targetPort
            )
        }

        val sinkInputs = NodeDefinition.NODE_DEFS["Sink"]!!.portsIn
        for ((index, entry) in rawOutputs.withIndex()) {
            val (sourceName, sourcePort) = splitPort(entry.toString())
            val fromNode = finalNodeMap[sourceName] ?: continue
            val destPort = sinkInputs.getOrElse(index) { "in${index + 1}" }
            finalNodeMap[sourceName] = addOutputPort(fromNode, sourcePort)
            connections += Connection(
                fromNode = fromNode.id,
                fromPort = sourcePort,
                toNode = sinkId,
                toPort = destPort
            )
        }

        for ((key, node) in finalNodeMap) {
            finalNodeMap[key] = node.cleanPorts()
        }

        val allNodesMap = finalNodeMap.toMutableMap()
        val filterNodeIds = allNodesMap.values.map { it.id }.toSet()

        val successors = mutableMapOf<String, MutableList<String>>()
        val predecessors = mutableMapOf<String, MutableList<String>>()
        for (nodeId in (filterNodeIds + sourceId + sinkId)) {
            successors[nodeId] = mutableListOf()
            predecessors[nodeId] = mutableListOf()
        }
1
        for (connection in connections) {
            successors[connection.fromNode]?.add(connection.toNode)
            predecessors[connection.toNode]?.add(connection.fromNode)
        }

        val roots = filterNodeIds.filter { (predecessors[it]?.size ?: 0) == 0 }
        val queue = (if (roots.isEmpty() && filterNodeIds.isNotEmpty()) filterNodeIds.take(1) else roots).toMutableList()
        val layers = mutableMapOf<String, Int>()
        queue.forEach { layers[it] = 0 }

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val currentLayer = layers[current] ?: 0
            for (next in successors[current] ?: emptyList()) {
                val nextLayer = currentLayer + 1
                if ((layers[next] ?: -1) < nextLayer) {
                    layers[next] = nextLayer
                    queue.addLast(next)
                }
            }
        }
        filterNodeIds.forEach { if (it !in layers) layers[it] = 0 }
        filterNodeIds.forEach { layers[it] = (layers[it] ?: 0) + 1 }
        layers[sourceId] = 0
        layers[sinkId] = (filterNodeIds.maxOfOrNull { layers[it] ?: 0 } ?: 0) + 1

        val buckets = mutableMapOf<Int, MutableList<String>>()
        for ((nodeId, l) in layers) {
            buckets.getOrPut(l) { mutableListOf() }.add(nodeId)
        }

        val finalNodes = mutableMapOf<String, Node>()
        for (node in allNodesMap.values) finalNodes[node.id] = node
        finalNodes[sourceId] = sourceNode
        finalNodes[sinkId] = sinkNode

        val startX = 80f
        val deltaX = 260f
        val startY = 80f
        val deltaY = 150f
        for ((col, nodeIds) in buckets.entries.sortedBy { it.key }) {
            for ((row, nodeId) in nodeIds.withIndex()) {
                finalNodes[nodeId] = finalNodes[nodeId]!!.copy(x = startX + col * deltaX, y = startY + row * deltaY)
            }
        }

        return Triple(chain, finalNodes, connections)
    }
}
