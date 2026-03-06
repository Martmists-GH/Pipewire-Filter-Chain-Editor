package com.martmists.pipewire.editor.compose

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.martmists.pipewire.editor.graph.GraphState
import com.martmists.pipewire.editor.graph.NodeDefinition
import java.awt.Cursor
import kotlin.collections.iterator


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ResizableSplitter(onDelta: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .width(5.dp)
            .fillMaxHeight()
            .background(AppColors.border)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onDelta(drag.x)
                }
            }
    )
}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NodePalette(
    state: GraphState,
    canvasBoundsInWindow: () -> Rect,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    var dragType by remember { mutableStateOf<String?>(null) }
    var dragScreenPos by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }

    val grouped = remember(NodeDefinition.NODE_DEFS.keys.size) {
        NodeDefinition.NODE_DEFS.entries
            .filter { it.key != "LADSPA Plugin" }
            .groupBy { it.value.category }
    }

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().background(AppColors.panel)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.panel2)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    "Nodes",
                    color = AppColors.textDim,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            HorizontalDivider(color = AppColors.border, thickness = 1.dp)

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                for ((category, entries) in grouped) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AppColors.border)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                category.uppercase(),
                                color = AppColors.textDim,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    items(entries.sortedBy { it.key }) { (name, def) ->
                        PaletteItem(
                            name = name,
                            def = def,
                            onAddAtCenter = {
                                val node = state.addNode(
                                    name,
                                    200f + (state.nodes.size * 20f) % 200f,
                                    150f + (state.nodes.size * 15f) % 200f
                                )
                                state.selectOnly(node.id)
                            },
                            onDragStart = { sp ->
                                dragType = name
                                dragScreenPos = sp
                                dragging = true
                            },
                            onDragUpdate = { sp -> dragScreenPos = sp },
                            onDragEnd = { sp ->
                                val bounds = canvasBoundsInWindow()
                                if (bounds.contains(sp)) {
                                    val wx = (sp.x - bounds.left - state.offsetX) / (state.scale * density)
                                    val wy = (sp.y - bounds.top - state.offsetY) / (state.scale * density)
                                    state.addNode(name, wx, wy).id.also { state.selectOnly(it) }
                                } else {
                                    state.addNode(name, 200f, 200f).id.also { state.selectOnly(it) }
                                }
                                dragging = false
                                dragType = null
                            }
                        )
                    }
                }
            }
        }

        if (dragging && dragType != null) {
            val def = NodeDefinition.NODE_DEFS[dragType!!]
            if (def != null) {
                Popup(
                    popupPositionProvider = object : PopupPositionProvider {
                        override fun calculatePosition(
                            anchorBounds: IntRect,
                            windowSize: IntSize,
                            layoutDirection: LayoutDirection,
                            popupContentSize: IntSize
                        ) = IntOffset((dragScreenPos.x + 14).toInt(), (dragScreenPos.y + 4).toInt())
                    },
                    onDismissRequest = {}
                ) {
                    Box(
                        modifier = Modifier
                            .background(def.headerColor.copy(alpha = 0.85f))
                            .border(1.dp, AppColors.accent)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            dragType!!,
                            color = AppColors.textHi,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PaletteItem(
    name: String,
    def: NodeDefinition,
    onAddAtCenter: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDragUpdate: (Offset) -> Unit,
    onDragEnd: (Offset) -> Unit
) {
    var itemPosInWindow by remember { mutableStateOf(Offset.Zero) }
    var hasDragged by remember { mutableStateOf(false) }
    var pressPos by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .background(def.color)
            .onGloballyPositioned { itemPosInWindow = it.positionInWindow() }
            .pointerInput(name) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        if (event.type == PointerEventType.Scroll) continue

                        val localPos = change.position
                        when (event.type) {
                            PointerEventType.Press -> {
                                pressPos = localPos
                                hasDragged = false
                                change.consume()
                            }
                            PointerEventType.Move -> {
                                if (event.buttons.isPrimaryPressed) {
                                    val screenPos = itemPosInWindow + localPos
                                    if (!hasDragged && (localPos - pressPos).getDistance() > 6f) {
                                        hasDragged = true
                                        onDragStart(screenPos)
                                    } else if (hasDragged) {
                                        onDragUpdate(screenPos)
                                    }
                                    change.consume()
                                }
                            }
                            PointerEventType.Release -> {
                                val screenPos = itemPosInWindow + localPos
                                if (hasDragged) onDragEnd(screenPos) else onAddAtCenter()
                                hasDragged = false
                                change.consume()
                            }
                            else -> {}
                        }
                    }
                }
            }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(name, color = AppColors.text, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
    }
}


@Composable
fun PropertiesPanel(state: GraphState, modifier: Modifier = Modifier) {
    val node = state.selectedId?.let { state.nodes[it] }

    Column(modifier = modifier.fillMaxSize().background(AppColors.panel)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    node?.definition?.headerColor ?: node?.let { NodeDefinition.FALLBACK.headerColor } ?: AppColors.panel2
                )
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                node?.type ?: "PROPERTIES",
                color = AppColors.textHi,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        HorizontalDivider(color = AppColors.border, thickness = 1.dp)

        if (node == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No selection",
                    color = AppColors.textDim,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                PropertyField("Name", node.name, false) { state.updateNode(node.id) { copy(name = it) } }

                if (node.portsIn.isNotEmpty() || node.portsOut.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    PropertySectionHeader("Ports")
                    if (node.portsIn.isNotEmpty()) {
                        Text(
                            "IN: ${node.portsIn.joinToString(", ")}",
                            color = AppColors.portIn, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                    if (node.portsOut.isNotEmpty()) {
                        Text(
                            "OUT: ${node.portsOut.joinToString(", ")}",
                            color = AppColors.portOut, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                }

                if (node.params.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    PropertySectionHeader("Parameters")
                    val labels = (node.definition ?: NodeDefinition.FALLBACK).parameterLabels
                    for ((key, value) in node.params) {
                        PropertyField(labels[key] ?: key, value, frozen = node.definition?.frozenParameters?.contains(key) ?: false) { newValue ->
                            state.updateNode(node.id) { copy(params = params + (key to newValue)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PropertySectionHeader(text: String) {
    Text(
        text.uppercase(),
        color = AppColors.border2,
        fontSize = 8.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
    )
}

@Composable
private fun PropertyField(label: String, value: String, frozen: Boolean, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = AppColors.textDim, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(2.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = AppColors.textHi,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            ),
            cursorBrush = SolidColor(AppColors.accent),
            readOnly = frozen,
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.panel2)
                .border(1.dp, AppColors.border)
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}
