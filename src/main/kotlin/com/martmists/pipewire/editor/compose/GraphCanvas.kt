package com.martmists.pipewire.editor.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.martmists.pipewire.editor.graph.Connection
import com.martmists.pipewire.editor.graph.GraphState
import com.martmists.pipewire.editor.graph.NODE_HEADER_HEIGHT
import com.martmists.pipewire.editor.graph.NODE_WIDTH
import com.martmists.pipewire.editor.graph.Node
import com.martmists.pipewire.editor.graph.NodeDefinition
import com.martmists.pipewire.editor.graph.PORT_RADIUS
import java.awt.Cursor
import kotlin.math.*


private enum class Mode { IDLE, DRAGGING_NODE, WIRING, PANNING, RUBBER_BAND }

private data class WireStart(val nodeId: String, val port: String, val isInput: Boolean)

private sealed class CtxMenu {
    data class NodeCtx(val nodeId: String, val screenPos: Offset) : CtxMenu()
    data class WireCtx(val connId: String, val screenPos: Offset) : CtxMenu()
    data class CanvasCtx(val screenPos: Offset) : CtxMenu()
}

private fun bezierCtrl(p0: Offset, p1: Offset): Pair<Offset, Offset> {
    val dx = max(abs(p1.x - p0.x) * 0.5f, 50f)
    return Offset(p0.x + dx, p0.y) to Offset(p1.x - dx, p1.y)
}

private fun DrawScope.drawBezierWire(p0: Offset, p1: Offset, color: Color, strokeWidth: Float = 2f) {
    val (c0, c1) = bezierCtrl(p0, p1)
    val path = Path().apply { moveTo(p0.x, p0.y); cubicTo(c0.x, c0.y, c1.x, c1.y, p1.x, p1.y) }
    drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GraphCanvas(
    state: GraphState,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    var mode by remember { mutableStateOf(Mode.IDLE) }
    var dragNodeIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dragOrigins by remember { mutableStateOf<Map<String, Offset>>(emptyMap()) }
    var dragWorldStart by remember { mutableStateOf(Offset.Zero) }
    var wireFrom by remember { mutableStateOf<WireStart?>(null) }
    var wireTip by remember { mutableStateOf(Offset.Zero) }
    var panStart by remember { mutableStateOf(Offset.Zero) }
    var panOxStart by remember { mutableStateOf(0f) }
    var panOyStart by remember { mutableStateOf(0f) }
    var rbStart by remember { mutableStateOf(Offset.Zero) }
    var rbEnd by remember { mutableStateOf(Offset.Zero) }
    var hoverPort by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    var ctxMenu by remember { mutableStateOf<CtxMenu?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    var renameText by remember { mutableStateOf("") }

    LaunchedEffect(state.renamingId) {
        state.renamingId?.let { id ->
            renameText = state.nodes[id]?.name ?: ""
        }
    }

    fun w2c(wx: Float, wy: Float) = Offset(wx * state.scale * density.density + state.offsetX, wy * state.scale * density.density + state.offsetY)
    fun c2w(cx: Float, cy: Float) = Offset((cx - state.offsetX) / (state.scale * density.density), (cy - state.offsetY) / (state.scale * density.density))
    fun c2w(o: Offset) = c2w(o.x, o.y)

    fun DrawScope.drawGrid() {
        val width = size.width
        val height = size.height
        val smallStep = 24f * state.scale * density.density
        val largeStep = 120f * state.scale * density.density

        if (smallStep >= 6f) {
            var x = state.offsetX % smallStep
            while (x < width) {
                drawLine(AppColors.gridMinor, Offset(x, 0f), Offset(x, height))
                x += smallStep
            }
            var y = state.offsetY % smallStep
            while (y < height) {
                drawLine(AppColors.gridMinor, Offset(0f, y), Offset(width, y))
                y += smallStep
            }
        }

        var x = state.offsetX % largeStep
        while (x < width) {
            drawLine(AppColors.gridMajor, Offset(x, 0f), Offset(x, height))
            x += largeStep
        }
        var y = state.offsetY % largeStep
        while (y < height) {
            drawLine(AppColors.gridMajor, Offset(0f, y), Offset(width, y))
            y += largeStep
        }
    }

    fun DrawScope.drawNode(node: Node) {
        val scale = state.scale
        val d = density.density
        val pos = w2c(node.x, node.y)
        val nodeWidth = NODE_WIDTH * scale * d
        val nodeHeight = node.height() * scale * d
        val headerHeight = NODE_HEADER_HEIGHT * scale * d
        val def = node.definition ?: NodeDefinition.FALLBACK
        val cornerRadius = CornerRadius(4f * scale * d)

        drawRoundRect(
            AppColors.shadow,
            topLeft = Offset(pos.x + 3, pos.y + 4),
            size = Size(nodeWidth, nodeHeight),
            cornerRadius = cornerRadius
        )
        drawRoundRect(
            if (node.selected) AppColors.nodeSel else def.color,
            topLeft = pos,
            size = Size(nodeWidth, nodeHeight),
            cornerRadius = cornerRadius
        )
        drawRoundRect(
            if (node.selected) AppColors.accent else AppColors.border2,
            topLeft = pos,
            size = Size(nodeWidth, nodeHeight),
            cornerRadius = cornerRadius,
            style = Stroke(width = if (node.selected) 2f else 1f)
        )
        drawRoundRect(def.headerColor, topLeft = pos, size = Size(nodeWidth, headerHeight), cornerRadius = cornerRadius)
        drawRect(
            def.headerColor,
            topLeft = Offset(pos.x, pos.y + headerHeight / 2),
            size = Size(nodeWidth, headerHeight / 2)
        )
        drawRect(
            if (node.selected) AppColors.accent else AppColors.border,
            topLeft = pos,
            size = Size(nodeWidth, headerHeight),
            style = Stroke(width = 1f)
        )

        if (scale > 0.3f) {
            val fontSize = max(8f, 10f * scale)
            val nameLayout = textMeasurer.measure(
                node.name.take(22),
                TextStyle(
                    color = AppColors.textHi, fontSize = fontSize.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = Constraints(maxWidth = (nodeWidth - 8).toInt().coerceAtLeast(10))
            )
            drawText(
                nameLayout, topLeft = Offset(
                    pos.x + nodeWidth / 2f - nameLayout.size.width / 2f,
                    pos.y + headerHeight / 2f - nameLayout.size.height / 2f
                )
            )

            if (scale > 0.6f) {
                val typeFontSize = max(6f, 7f * scale)
                val typeLayout = textMeasurer.measure(
                    node.type,
                    TextStyle(color = AppColors.textDim, fontSize = typeFontSize.sp, fontFamily = FontFamily.Monospace),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = (nodeWidth / 2).toInt().coerceAtLeast(10))
                )
                drawText(
                    typeLayout, topLeft = Offset(
                        pos.x + nodeWidth - typeLayout.size.width - 4,
                        pos.y + headerHeight / 2f - typeLayout.size.height / 2f
                    )
                )
            }
        }

        val portRadius = PORT_RADIUS * scale * d
        fun drawPort(portName: String, isInput: Boolean) {
            val portPos = node.portPosition(portName, isInput, state.scale, state.offsetX, state.offsetY, d) ?: return
            val isHovered = hoverPort?.let { it.first == node.id && it.second == portName && it.third == isInput } == true
            val color = if (isHovered) AppColors.portHi else if (isInput) AppColors.portIn else AppColors.portOut
            drawCircle(color, radius = portRadius, center = portPos)
            drawCircle(
                AppColors.bg,
                radius = portRadius,
                center = portPos,
                style = Stroke(width = max(1f, 1.5f * scale * d))
            )
            if (scale > 0.45f) {
                val labelFontSize = max(6f, 8f * scale)
                val labelLayout = textMeasurer.measure(
                    portName,
                    TextStyle(color = AppColors.textDim, fontSize = labelFontSize.sp, fontFamily = FontFamily.Monospace),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = (nodeWidth * 0.45f).toInt().coerceAtLeast(10))
                )
                val labelX = if (isInput) portPos.x + portRadius + 4 else portPos.x - portRadius - 4 - labelLayout.size.width
                drawText(labelLayout, topLeft = Offset(labelX, portPos.y - labelLayout.size.height / 2f))
            }
        }
        node.portsIn.forEach { drawPort(it, true) }
        node.portsOut.forEach { drawPort(it, false) }
    }

    fun DrawScope.drawWire(connection: Connection) {
        val fromNode = state.nodes[connection.fromNode] ?: return
        val toNode = state.nodes[connection.toNode] ?: return
        val p0 = fromNode.portPosition(connection.fromPort, false, state.scale, state.offsetX, state.offsetY, density.density) ?: return
        val p1 = toNode.portPosition(connection.toPort, true, state.scale, state.offsetX, state.offsetY, density.density) ?: return
        drawBezierWire(p0, p1, AppColors.wire, strokeWidth = max(1.5f, 2f * state.scale * density.density))
    }

    fun findPortHit(pos: Offset): Pair<Node, Pair<String, Boolean>>? {
        for (node in state.nodes.values.toList().asReversed()) {
            val portHit = node.portHitTest(pos, state.scale, state.offsetX, state.offsetY, density.density)
            if (portHit != null) return node to portHit
        }
        return null
    }

    fun findNodeHit(pos: Offset): Node? =
        state.nodes.values.toList().asReversed()
            .firstOrNull { it.hitTest(pos, state.scale, state.offsetX, state.offsetY, density.density) }

    fun findWireHit(pos: Offset): Connection? =
        state.connections.firstOrNull { connection ->
            val fromNode = state.nodes[connection.fromNode] ?: return@firstOrNull false
            val toNode = state.nodes[connection.toNode] ?: return@firstOrNull false
            val p0 = fromNode.portPosition(connection.fromPort, false, state.scale, state.offsetX, state.offsetY, density.density)
                ?: return@firstOrNull false
            val p1 = toNode.portPosition(connection.toPort, true, state.scale, state.offsetX, state.offsetY, density.density)
                ?: return@firstOrNull false

            var found = false
            val (c0, c1) = bezierCtrl(p0, p1)
            for (i in 0..24) {
                val t = i / 24f; val t1 = 1f - t
                val bx = t1*t1*t1*p0.x + 3*t1*t1*t*c0.x + 3*t1*t*t*c1.x + t*t*t*p1.x
                val by = t1*t1*t1*p0.y + 3*t1*t1*t*c0.y + 3*t1*t*t*c1.y + t*t*t*p1.y
                if (hypot(pos.x - bx, pos.y - by) < 9f) found = true
            }
            found
        }

    Box(modifier = modifier.onSizeChanged { canvasSize = it }) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.bg)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            val pos = change.position

                            when (event.type) {
                                PointerEventType.Scroll -> {
                                    val delta = change.scrollDelta.y
                                    val factor = if (delta < 0) 1.12f else 1f / 1.12f
                                    val ns = (state.scale * factor).coerceIn(0.15f, 3f)
                                    state.offsetX = pos.x - (pos.x - state.offsetX) * (ns / state.scale)
                                    state.offsetY = pos.y - (pos.y - state.offsetY) * (ns / state.scale)
                                    state.scale = ns
                                }
                                PointerEventType.Move -> {
                                    hoverPort = findPortHit(pos)?.let { (n, ph) -> Triple(n.id, ph.first, ph.second) }

                                    when (mode) {
                                        Mode.DRAGGING_NODE -> {
                                            val worldPos = c2w(pos)
                                            val dx = worldPos.x - dragWorldStart.x
                                            val dy = worldPos.y - dragWorldStart.y
                                            for (nodeId in dragNodeIds) {
                                                val origin = dragOrigins[nodeId] ?: continue
                                                state.updateNode(nodeId) { copy(x = origin.x + dx, y = origin.y + dy) }
                                            }
                                        }
                                        Mode.WIRING -> wireTip = pos
                                        Mode.PANNING -> {
                                            state.offsetX = panOxStart + pos.x - panStart.x
                                            state.offsetY = panOyStart + pos.y - panStart.y
                                        }
                                        Mode.RUBBER_BAND -> rbEnd = pos
                                        else -> {}
                                    }
                                }
                                PointerEventType.Press -> when {
                                    event.buttons.isSecondaryPressed -> {
                                        val portHit = findPortHit(pos)
                                        if (portHit == null) {
                                            val nodeHit = findNodeHit(pos)
                                            ctxMenu = if (nodeHit != null) {
                                                if (!nodeHit.selected) state.selectOnly(nodeHit.id)
                                                CtxMenu.NodeCtx(nodeHit.id, pos)
                                            } else {
                                                val wireHit = findWireHit(pos)
                                                if (wireHit != null) {
                                                    CtxMenu.WireCtx(wireHit.id, pos)
                                                } else {
                                                    CtxMenu.CanvasCtx(pos)
                                                }
                                            }
                                        }
                                    }
                                    else -> {
                                        val shift = event.keyboardModifiers.isShiftPressed || event.keyboardModifiers.isCtrlPressed
                                        val portHit = findPortHit(pos)
                                        if (portHit != null) {
                                            val (n, ph) = portHit
                                            wireFrom = WireStart(n.id, ph.first, ph.second)
                                            wireTip = pos
                                            mode = Mode.WIRING
                                        } else {
                                            val nodeHit = findNodeHit(pos)
                                            if (nodeHit != null) {
                                                if (!shift) {
                                                    if (!nodeHit.selected) state.clearSelection()
                                                    state.updateNode(nodeHit.id) { copy(selected = true) }
                                                    state.selectedId = nodeHit.id
                                                } else {
                                                    state.toggleSelect(nodeHit.id)
                                                }
                                                val wpos = c2w(pos)
                                                dragWorldStart = wpos
                                                dragNodeIds =
                                                    state.nodes.values.filter { it.selected }.map { it.id }.toSet()
                                                dragOrigins = dragNodeIds.associateWith { nid ->
                                                    state.nodes[nid]!!.let { Offset(it.x, it.y) }
                                                }
                                                mode = Mode.DRAGGING_NODE
                                            } else {
                                                if (!shift) state.clearSelection()
                                                panStart = pos
                                                panOxStart = state.offsetX
                                                panOyStart = state.offsetY
                                                mode = Mode.PANNING
                                            }
                                        }
                                    }
                                }
                                PointerEventType.Release -> {
                                    if (!event.buttons.isPrimaryPressed) {
                                        when (mode) {
                                            Mode.WIRING -> {
                                                val from = wireFrom
                                                if (from != null) {
                                                    val target = findPortHit(pos)
                                                    if (target != null) {
                                                        val (tn, tp) = target
                                                        if (tn.id != from.nodeId && tp.second != from.isInput) {
                                                            val (outN, outP, inN, inP) = if (from.isInput)
                                                                arrayOf(tn.id, tp.first, from.nodeId, from.port)
                                                            else
                                                                arrayOf(from.nodeId, from.port, tn.id, tp.first)
                                                            state.addConnection(outN, outP, inN, inP)
                                                        }
                                                    }
                                                }
                                                wireFrom = null; mode = Mode.IDLE
                                            }
                                            Mode.RUBBER_BAND -> {
                                                val (wx0, wy0) = c2w(min(rbStart.x, rbEnd.x), min(rbStart.y, rbEnd.y))
                                                val (wx1, wy1) = c2w(max(rbStart.x, rbEnd.x), max(rbStart.y, rbEnd.y))
                                                state.selectInRect(wx0, wy0, wx1, wy1)
                                                mode = Mode.IDLE
                                            }
                                            Mode.DRAGGING_NODE -> mode = Mode.IDLE
                                            else -> mode = Mode.IDLE
                                        }
                                    }
                                }
                                else -> {}
                            }
                            change.consume()
                        }
                    }
                }
        ) {
            drawGrid()

            val currentWireFrom = wireFrom
            if (currentWireFrom != null && mode == Mode.WIRING) {
                val startNode = state.nodes[currentWireFrom.nodeId]
                val p0 = startNode?.portPosition(
                    currentWireFrom.port,
                    currentWireFrom.isInput,
                    state.scale,
                    state.offsetX,
                    state.offsetY,
                    density.density
                )
                if (p0 != null) {
                    val (tip0, tip1) = if (currentWireFrom.isInput) wireTip to p0 else p0 to wireTip
                    drawBezierWire(tip0, tip1, AppColors.wireHi, strokeWidth = max(1.5f, 2f * state.scale * density.density))
                }
            }

            state.nodes.values.filter { !it.selected }.forEach { drawNode(it) }
            state.nodes.values.filter {  it.selected }.forEach { drawNode(it) }

            state.connections.forEach { drawWire(it) }

            if (mode == Mode.RUBBER_BAND) {
                val rx = min(rbStart.x, rbEnd.x); val ry = min(rbStart.y, rbEnd.y)
                val rw = abs(rbEnd.x - rbStart.x); val rh = abs(rbEnd.y - rbStart.y)
                drawRect(AppColors.rubberBand, topLeft = Offset(rx, ry), size = Size(rw, rh))
                drawRect(
                    AppColors.rubberBandBorder, topLeft = Offset(rx, ry), size = Size(rw, rh),
                         style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))))
            }
        }

        val ctx = ctxMenu
        if (ctx != null) {
            val menuPos: Offset = when (ctx) {
                is CtxMenu.NodeCtx   -> ctx.screenPos
                is CtxMenu.WireCtx   -> ctx.screenPos
                is CtxMenu.CanvasCtx -> ctx.screenPos
            }
            Popup(
                popupPositionProvider = object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect, windowSize: IntSize,
                        layoutDirection: LayoutDirection, popupContentSize: IntSize
                    ): IntOffset {
                        val px = (anchorBounds.left + menuPos.x).roundToInt()
                        val py = (anchorBounds.top + menuPos.y).roundToInt()
                        val clampedX = px.coerceIn(0, (windowSize.width  - popupContentSize.width).coerceAtLeast(0))
                        val clampedY = py.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
                        return IntOffset(clampedX, clampedY)
                    }
                },
                onDismissRequest = { ctxMenu = null }
            ) {
                Surface(
                    color = AppColors.panel2,
                    shape = MaterialTheme.shapes.small,
                    shadowElevation = 8.dp,
                    tonalElevation = 0.dp
                ) {
                    Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                        when (ctx) {
                            is CtxMenu.NodeCtx -> {
                                val node = state.nodes[ctx.nodeId]
//                                CtxItem("Rename \"${node?.name?.take(20)}\"") {
//                                    ctxMenu = null
//                                    state.renamingId = ctx.nodeId
//                                }
                                CtxDivider()
                                CtxItem("[D] Disconnect all") {
                                    state.disconnectAll(ctx.nodeId); ctxMenu = null
                                }
                                CtxItem("[X] Delete node", color = AppColors.red) {
                                    state.deleteNode(ctx.nodeId); ctxMenu = null
                                }
                            }
                            is CtxMenu.WireCtx -> {
                                val c = state.connections.firstOrNull { it.id == ctx.connId }
                                val label = c?.let {
                                    val fn = state.nodes[it.fromNode]
                                    val tn = state.nodes[it.toNode]
                                    "[X] ${fn?.name}:${it.fromPort} -> ${tn?.name}:${it.toPort}"
                                } ?: "[X] Delete wire"
                                CtxItem(label, color = AppColors.red) {
                                    state.deleteConnection(ctx.connId); ctxMenu = null
                                }
                            }
                            is CtxMenu.CanvasCtx -> {
                                CtxItem("[F] Fit view") {
                                    ctxMenu = null
                                    state.fitView(canvasSize.width.toFloat(), canvasSize.height.toFloat(), density.density)
                                }
                                CtxDivider()
                                CtxItem("Clear all", color = AppColors.red) {
                                    state.clearAll(); ctxMenu = null
                                }
                                CtxItem("[F1] About") {
                                    state.renamingId = null
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.renamingId != null) {
            AlertDialog(
                onDismissRequest = { state.renamingId = null },
                title = { Text("Rename Node", color = AppColors.textHi) },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppColors.textHi,
                            unfocusedTextColor = AppColors.text,
                            focusedBorderColor = AppColors.accent,
                            unfocusedBorderColor = AppColors.border2,
                            cursorColor = AppColors.accent
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        state.renamingId?.let { state.updateNode(it) { copy(name = renameText) } }
                        state.renamingId = null
                    }) { Text("OK", color = AppColors.accent) }
                },
                dismissButton = {
                    TextButton(onClick = { state.renamingId = null }) { Text("Cancel", color = AppColors.textDim) }
                },
                containerColor = AppColors.panel2
            )
        }
    }
}

@Composable
private fun CtxItem(label: String, color: Color = AppColors.text, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().clipToBounds().padding(0.dp),
        shape = RectangleShape,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(label, color = color, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
             modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CtxDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        thickness = 1.dp,
        color = AppColors.border
    )
}
