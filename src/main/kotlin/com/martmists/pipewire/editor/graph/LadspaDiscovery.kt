package com.martmists.pipewire.editor.graph

import java.io.File

private val LADSPA_DIRS by lazy {
    val paths = mutableListOf(
        "/usr/lib/ladspa", "/usr/lib64/ladspa",
        "/usr/local/lib/ladspa",
        System.getProperty("user.home") + "/.ladspa"
    )
    val env = System.getenv("LADSPA_PATH")
    if (!env.isNullOrBlank()) paths += env.split(":").filter { it.isNotBlank() }
    paths.map { File(it) }.filter { it.isDirectory }
}

private fun parseAudioPorts(section: String): Pair<List<String>, List<String>> {
    val portPattern = Regex(""""([^"]+)"\s+(input|output),\s*audio""")
    val inputs = mutableListOf<String>()
    val outputs = mutableListOf<String>()
    portPattern.findAll(section).forEach { match ->
        if (match.groupValues[2] == "output") {
            outputs += match.groupValues[1]
        } else {
            inputs += match.groupValues[1]
        }
    }
    return (inputs.ifEmpty { listOf("Input") }) to (outputs.ifEmpty { listOf("Output") })
}

private fun parseControlPorts(section: String): Map<String, String> {
    val pattern = Regex(""""([^"]+)"\s+input,\s*control([^\n]*)""")
    val result = LinkedHashMap<String, String>()
    pattern.findAll(section).forEach { match ->
        val name = match.groupValues[1]
        if (name.equals("Placeholder", ignoreCase = true)) return@forEach
        val defaultMatch = Regex("""default\s+([-\d.]+)""").find(match.groupValues[2])
        result[name] = defaultMatch?.groupValues?.get(1) ?: "0"
    }
    return result
}

fun discoverLadspa() {
    val seenPlugins = mutableSetOf<Pair<String, String>>()

    fun tryAddPlugin(
        displayName: String,
        label: String,
        pluginPath: String,
        portsIn: List<String>,
        portsOut: List<String>,
        controlDefaults: Map<String, String> = emptyMap()
    ) {
        val key = label to pluginPath
        if (key !in seenPlugins) {
            seenPlugins += key
            NodeDefinition.registerLadspaPlugin(displayName, label, pluginPath, portsIn, portsOut, controlDefaults)
        }
    }

    for (directory in LADSPA_DIRS) {
        val sharedObjects = directory.listFiles { file -> file.extension == "so" }?.sorted() ?: continue
        for (soFile in sharedObjects) {
            val baseName = soFile.nameWithoutExtension
            var found = false

            try {
                val process = ProcessBuilder("analyseplugin", soFile.absolutePath)
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()

                val sections = output.split(Regex("(?=Plugin Name:)"))
                for (section in sections) {
                    val nameMatch = Regex("Plugin Name:\\s+\"([^\"]+)\"").find(section) ?: continue
                    val labelMatch = Regex("""Plugin Label:\s+(\S+)""").find(section) ?: continue
                    val (inputs, outputs) = parseAudioPorts(section)
                    val controls = parseControlPorts(section)
                    tryAddPlugin(
                        nameMatch.groupValues[1],
                        labelMatch.groupValues[1],
                        soFile.absolutePath,
                        inputs,
                        outputs,
                        controls
                    )
                    found = true
                }
            } catch (e: Exception) {

            }

            if (!found) {
                try {
                    val process = ProcessBuilder("listplugins", soFile.absolutePath)
                        .redirectErrorStream(true)
                        .start()
                    val output = process.inputStream.bufferedReader().readText()
                    process.waitFor()
                    Regex("""(.+?)\s+\(\d+/(\w+)\)""").findAll(output).forEach { match ->
                        tryAddPlugin(
                            match.groupValues[1].trim(),
                            match.groupValues[2],
                            soFile.absolutePath,
                            listOf("Input"),
                            listOf("Output")
                        )
                        found = true
                    }
                } catch (e: Exception) {

                }
            }

            if (!found) {
                tryAddPlugin(baseName, baseName, soFile.absolutePath, listOf("Input"), listOf("Output"))
            }
        }
    }
    NodeDefinition.ensureFallbackLadspa()
}
