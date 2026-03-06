package com.martmists.pipewire.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.*
import androidx.compose.ui.window.*
import com.martmists.pipewire.editor.compose.AppColors
import com.martmists.pipewire.editor.compose.GraphCanvas
import com.martmists.pipewire.editor.compose.NodePalette
import com.martmists.pipewire.editor.compose.PropertiesPanel
import com.martmists.pipewire.editor.compose.ResizableSplitter
import com.martmists.pipewire.editor.conf.PipeWireExporter
import com.martmists.pipewire.editor.conf.PipeWireImporter
import com.martmists.pipewire.editor.graph.GraphState
import com.martmists.pipewire.editor.graph.NodeDefinition
import com.martmists.pipewire.editor.graph.discoverLadspa
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import kotlinx.coroutines.launch
import java.awt.datatransfer.StringSelection
import java.io.File
import java.util.Properties

fun main() {
    val prefsFile = File("${System.getProperty("user.home")}/.config/pipewire-filter-editor.properties")
    val props = Properties()
    if (prefsFile.exists()) {
        try {
            prefsFile.inputStream().use { props.load(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val configDir = File("${System.getProperty("user.home")}/.config/pipewire/pipewire.conf.d")
    if (!configDir.exists()) configDir.mkdirs()

    discoverLadspa()

    application {
        val state = remember { GraphState().also { it.defaultGraph() } }
        val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
        var uiScale by remember { mutableStateOf(props.getProperty("uiScale", "1.0").toFloatOrNull() ?: 1.0f) }

        Window(
            onCloseRequest = ::exitApplication,
            title = "PipeWire Filter Chain Editor",
            state = windowState,
        ) {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = baseDensity.density * uiScale,
                    fontScale = baseDensity.fontScale
                )
            ) {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        background = AppColors.bg,
                        surface = AppColors.panel,
                        primary = AppColors.accent,
                        onBackground = AppColors.text,
                        onSurface = AppColors.text
                    )
                ) {
                    AppContent(
                        state = state,
                        uiScale = uiScale,
                        onScaleChange = {
                            uiScale = it.coerceIn(0.5f, 3.0f)
                            props.setProperty("uiScale", uiScale.toString())
                            try {
                                prefsFile.outputStream().use { out -> props.store(out, null) }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    )
                }
            }
        }
    }
}



@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppContent(
    state: GraphState,
    uiScale: Float,
    onScaleChange: (Float) -> Unit
) {
    val defaultDirectory = remember { PlatformFile("${System.getProperty("user.home")}/.config/pipewire/pipewire.conf.d") }
    val exporter = remember { PipeWireExporter() }
    val importer = remember { PipeWireImporter() }
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Ready") }

    var paletteWidthDp by remember { mutableStateOf(185f) }
    var propsWidthDp by remember { mutableStateOf(240f) }
    var canvasBounds by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current

    fun doSave(path: String) {
        try {
            File(path).writeText(exporter.export(state))
            state.filePath = path
            status = "Saved: $path"
        } catch (e: Exception) {
            status = "Save failed: ${e.message}"
        }
    }

    fun doOpen(path: String) {
        try {
            val (chain, nodes, conns) = importer.load(File(path).readText())
            state.load(nodes, conns, chain)
            state.filePath = path
            status = "Opened: $path  (${nodes.size} nodes, ${conns.size} connections)"
        } catch (e: Exception) {
            status = "Open failed: ${e.message}"
        }
    }

    val openLauncher = rememberFilePickerLauncher(
        type = FileKitType.File(listOf("conf", "conf.disabled")),
        mode = FileKitMode.Single,
        directory = defaultDirectory,
    ) { file: PlatformFile? ->
        file?.let { doOpen(it.file.absolutePath) }
    }

    val saveLauncher = rememberFileSaverLauncher(
        FileKitDialogSettings()
    ) { file: PlatformFile? ->
        file?.let { doSave(it.file.absolutePath) }
    }

    var showAbout by remember { mutableStateOf(false) }
    if (showAbout) {
        val numPlugin = NodeDefinition.numLadspaPlugins
        val numPluginString = if (numPlugin >= 0) {
            "$numPlugin LADSPA plugins found"
        } else {
            "No LADSPA plugins found"
        }
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("About") },
            text = { Text("PipeWire Filter Chain Editor\nBuilt with Compose for Desktop\n\n$numPluginString.") },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text("OK")
                }
            }
        )
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(state.renamingId, showAbout) {
        if (state.renamingId == null && !showAbout) {
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.bg)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown) {
                    val ctrl = it.isCtrlPressed
                    val shift = it.isShiftPressed
                    when (it.key) {
                        Key.N -> if (ctrl) {
                            state.clearAll()
                            state.defaultGraph()
                            state.filePath = null
                            status = "New graph."
                            true
                        } else false
                        Key.O -> if (ctrl) {
                            openLauncher.launch()
                            true
                        } else false
                        Key.S -> if (ctrl) {
                            if (shift) {
                                saveLauncher.launch(
                                    state.filePath?.split("/")?.last() ?: "50-${state.chainName.replace(' ', '-').lowercase()}",
                                    "conf",
                                    defaultDirectory,
                                )
                            } else {
                                state.filePath?.let { doSave(it) } ?: saveLauncher.launch(
                                    state.filePath?.split("/")?.last() ?: "50-${state.chainName.replace(' ', '-').lowercase()}",
                                    "conf",
                                    defaultDirectory,
                                )
                            }
                            true
                        } else false
                        Key.C -> if (ctrl) {
                            scope.launch {
                                clipboardManager.setClipEntry(
                                    ClipEntry(StringSelection(exporter.export(state)))
                                )
                            }
                            status = "Config copied to clipboard"
                            true
                        } else false
                        Key.Plus, Key.NumPadAdd, Key.Equals -> if (ctrl) {
                            onScaleChange(uiScale + 0.1f)
                            true
                        } else false
                        Key.Minus, Key.NumPadSubtract -> if (ctrl) {
                            onScaleChange(uiScale - 0.1f)
                            true
                        } else false
                        Key.F -> {
                            state.fitView(canvasBounds.width, canvasBounds.height, density.density)
                            true
                        }
                        Key.Delete, Key.X, Key.Backspace -> {
                            if (shift && (it.key == Key.Delete || it.key == Key.X)) {
                                state.clearAll()
                                true
                            } else {
                                state.deleteSelected()
                                true
                            }
                        }
//                        Key.R -> {
//                            state.selectedId?.let {
//                                state.renamingId = it
//                                true
//                            } ?: false
//                        }
                        Key.D -> {
                            state.selectedId?.let {
                                state.disconnectAll(it)
                                true
                            } ?: false
                        }
                        Key.F1 -> {
                            showAbout = true
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(AppColors.panel)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarButton("New") {
                state.clearAll()
                state.defaultGraph()
                state.filePath = null
                status = "New graph."
            }
            ToolbarButton("Open") {
                openLauncher.launch()
            }
            ToolbarButton("Save") {
                state.filePath?.let { doSave(it) } ?: saveLauncher.launch(
                    state.filePath?.split("/")?.last() ?: "50-${state.chainName.replace(' ', '-').lowercase()}",
                    "conf",
                    defaultDirectory,
                )
            }
            ToolbarButton("Save As") {
                saveLauncher.launch(
                    state.filePath?.split("/")?.last() ?: "50-${state.chainName.replace(' ', '-').lowercase()}",
                    "conf",
                    defaultDirectory,
                )
            }
            ToolbarButton("Copy config") {
                scope.launch {
                    clipboardManager.setClipEntry(
                        ClipEntry(StringSelection(exporter.export(state)))
                    )
                }
                status = "Config copied to clipboard"
            }
            ToolbarButton("Restart Pipewire") {
                Runtime.getRuntime().exec(arrayOf("systemctl", "--user", "restart", "pipewire"))
            }
//            ToolbarSeparator()
//            ToolbarButton("Fit view [F]") {
//                state.fitView(canvasBounds.width, canvasBounds.height)
//            }
//            ToolbarButton("Delete selected [Del/X]") { state.deleteSelected() }
            ToolbarSeparator()

            Text(
                "Chain:",
                color = AppColors.textDim,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp)
            )

            BasicTextField(
                value = state.chainName,
                onValueChange = { state.chainName = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = AppColors.textHi,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier
                    .width(180.dp)
                    .background(AppColors.panel2)
                    .border(1.dp, AppColors.border)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )

            ToolbarSeparator()

            Text(
                "UI:",
                color = AppColors.textDim,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 4.dp, end = 2.dp)
            )
            ToolbarButton("-") { onScaleChange(uiScale - 0.1f) }
            Text(
                "${(uiScale * 100).toInt()}%",
                color = AppColors.text,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(36.dp),
            )
            ToolbarButton("+") { onScaleChange(uiScale + 0.1f) }
            ToolbarSeparator()
            ToolbarButton("About") {
                showAbout = true
            }
        }

        HorizontalDivider(color = AppColors.border, thickness = 1.dp)

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            NodePalette(
                state = state,
                canvasBoundsInWindow = { canvasBounds },
                modifier = Modifier
                    .width(paletteWidthDp.dp)
                    .fillMaxHeight()
            )

            ResizableSplitter { delta ->
                val deltaDp = with(density) { delta.toDp().value }
                paletteWidthDp = (paletteWidthDp + deltaDp).coerceIn(80f, 500f)
            }

            GraphCanvas(
                state = state,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds()
                    .onGloballyPositioned { canvasBounds = it.boundsInWindow() }
            )

            ResizableSplitter { delta ->
                val deltaDp = with(density) { delta.toDp().value }
                propsWidthDp = (propsWidthDp - deltaDp).coerceIn(120f, 600f)
            }

            PropertiesPanel(
                state = state,
                modifier = Modifier
                    .width(propsWidthDp.dp)
                    .fillMaxHeight()
            )
        }

        HorizontalDivider(color = AppColors.border, thickness = 1.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.panel)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(status, color = AppColors.textDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun ToolbarButton(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 1.dp),
        shape = RectangleShape,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    ) {
        Text(label, color = AppColors.text, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ToolbarSeparator() = Box(
    modifier = Modifier
        .padding(horizontal = 3.dp)
        .width(1.dp)
        .height(26.dp)
        .background(AppColors.border)
)
