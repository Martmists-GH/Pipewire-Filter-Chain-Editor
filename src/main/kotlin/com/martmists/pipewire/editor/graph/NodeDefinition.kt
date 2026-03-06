package com.martmists.pipewire.editor.graph

import androidx.compose.ui.graphics.Color
import kotlin.collections.iterator

data class NodeDefinition(
    val category: String,
    val color: Color,
    val headerColor: Color,
    val portsIn: List<String>,
    val portsOut: List<String>,
    val pipewireType: String,
    val pipewireLabel: String,
    val parameters: Map<String, String>,
    val parameterLabels: Map<String, String>,
    val frozenParameters: List<String>,
) {
    companion object {
        private val STATIC = mapOf(
            "Source" to NodeDefinition(
                category = "I/O",
                color = Color(0xFF0D2E1A),
                headerColor = Color(0xFF1A4D2E),
                portsIn = emptyList(),
                portsOut = listOf("FL", "FR"),
                pipewireType = "builtin",
                pipewireLabel = "source",
                parameters = mapOf(
                    "node.name" to "filter.capture",
                    "media.class" to "Audio/Sink",
                    "target.object" to ""
                ),
                parameterLabels = mapOf(
                    "node.name" to "Node Name",
                    "media.class" to "Media Class",
                    "target.object" to "Target Input"
                ),
                frozenParameters = emptyList(),
            ),
            "Sink" to NodeDefinition(
                category = "I/O",
                color = Color(0xFF2E0D0D),
                headerColor = Color(0xFF4D1A1A),
                portsIn = listOf("FL", "FR"),
                portsOut = emptyList(),
                pipewireType = "builtin",
                pipewireLabel = "sink",
                parameters = mapOf(
                    "node.name" to "filter.playback",
                    "media.class" to "Audio/Source"
                ),
                parameterLabels = mapOf(
                    "node.name" to "Node Name",
                    "media.class" to "Media Class"
                ),
                frozenParameters = emptyList(),
            ),
            "EQ Lowpass" to createEqDefinition(
                "bq_lowpass",
                mapOf("Freq" to "1000", "Q" to "0.707"),
                mapOf("Freq" to "Frequency (Hz)", "Q" to "Q Factor")
            ),
            "EQ Highpass" to createEqDefinition(
                "bq_highpass",
                mapOf("Freq" to "200", "Q" to "0.707"),
                mapOf("Freq" to "Frequency (Hz)", "Q" to "Q Factor")
            ),
            "EQ Bandpass" to createEqDefinition(
                "bq_bandpass",
                mapOf("Freq" to "200", "Q" to "0.707"),
                mapOf("Freq" to "Frequency (Hz)", "Q" to "Q Factor")
            ),
            "EQ Peak" to createEqDefinition(
                "bq_peaking",
                mapOf("Freq" to "1000", "Q" to "1.0", "Gain" to "0.0"),
                mapOf("Freq" to "Frequency (Hz)", "Q" to "Q Factor", "Gain" to "Gain (dB)")
            ),
            "EQ Lowshelf" to createEqDefinition(
                "bq_lowshelf",
                mapOf("Freq" to "200", "Gain" to "0.0"),
                mapOf("Freq" to "Frequency (Hz)", "Gain" to "Gain (dB)")
            ),
            "EQ Highshelf" to createEqDefinition(
                "bq_highshelf",
                mapOf("Freq" to "8000", "Gain" to "0.0"),
                mapOf("Freq" to "Frequency (Hz)", "Gain" to "Gain (dB)")
            ),
            "EQ Notch" to createEqDefinition(
                "bq_notch",
                mapOf("Freq" to "60", "Q" to "30"),
                mapOf("Freq" to "Frequency (Hz)", "Q" to "Q Factor")
            ),
            "EQ Allpass" to createEqDefinition(
                "bq_allpass",
                mapOf("Freq" to "1000", "Q" to "0.707"),
                mapOf("Freq" to "Frequency (Hz)", "Q" to "Q Factor")
            ),
            "Mixer" to NodeDefinition(
                category = "Utility",
                color = Color(0xFF1A1A1A),
                headerColor = Color(0xFF2E2E2E),
                portsIn = (1..8).map { "In $it" },
                portsOut = listOf("Out"),
                pipewireType = "builtin",
                pipewireLabel = "null",
                parameters = (1..8).associate { "Gain $it" to "0.0" },
                parameterLabels = (1..8).associate { "Gain $it" to "Gain $it (dB)" },
                frozenParameters = emptyList(),
            ),
            "Delay" to NodeDefinition(
                category = "Time",
                color = Color(0xFF2E2E0D),
                headerColor = Color(0xFF4D4D1A),
                portsIn = listOf("In"),
                portsOut = listOf("Out"),
                pipewireType = "builtin",
                pipewireLabel = "delay",
                parameters = mapOf("Delay (s)" to "0.1"),
                parameterLabels = mapOf("Delay (s)" to "Delay (sec)"),
                frozenParameters = emptyList(),
            ),
            "Convolver" to NodeDefinition(
                category = "Time",
                color = Color(0xFF1A0D0D),
                headerColor = Color(0xFF301A1A),
                portsIn = listOf("In"),
                portsOut = listOf("Out"),
                pipewireType = "builtin",
                pipewireLabel = "convolver",
                parameters = mapOf(
                    "gain" to "1.0",
                    "delay" to "0.1",
                    "filename" to "",
                ),
                parameterLabels = mapOf(
                    "gain" to "Gain (dB)",
                    "delay" to "Delay (float: sec, int: samples)",
                    "filename" to "IR File",
                ),
                frozenParameters = emptyList(),
            ),
        )

        val FALLBACK = NodeDefinition(
            category = "LADSPA",
            color = Color(0xFF1A0D1A),
            headerColor = Color(0xFF2E1A2E),
            portsIn = listOf("Input"),
            portsOut = listOf("Output"),
            pipewireType = "ladspa",
            pipewireLabel = "",
            parameters = emptyMap(),
            parameterLabels = emptyMap(),
            frozenParameters = emptyList(),
        )

        val NODE_DEFS: Map<String, NodeDefinition>
            field = STATIC.toMutableMap()

        val LABEL_TO_TYPE: MutableMap<String, String> = NODE_DEFS
            .filter { it.value.pipewireLabel.isNotEmpty() }
            .map { (key, value) -> value.pipewireLabel to key }
            .toMap()
            .toMutableMap()

        val LADSPA_LABEL_PORT: Map<String, Pair<List<String>, List<String>>>
            field = mutableMapOf<String, Pair<List<String>, List<String>>>()

        val numLadspaPlugins: Int
            get() = LADSPA_LABEL_PORT.size

        private fun createEqDefinition(
            label: String,
            parameters: Map<String, String>,
            parameterLabels: Map<String, String>
        ) = NodeDefinition(
            category = "EQ",
            color = Color(0xFF0D1A2E),
            headerColor = Color(0xFF1A2E4D),
            portsIn = listOf("In"),
            portsOut = listOf("Out"),
            pipewireType = "builtin",
            pipewireLabel = label,
            parameters = parameters,
            parameterLabels = parameterLabels,
            frozenParameters = emptyList(),
        )

        fun registerLadspaPlugin(
            display: String,
            label: String,
            plugin: String,
            portsIn: List<String>,
            portsOut: List<String>,
            controlDefaults: Map<String, String> = emptyMap()
        ) {
            val key = "LADSPA: $display"
            val parameters = LinkedHashMap<String, String>()
            val parameterLabels = LinkedHashMap<String, String>()
            parameters["plugin"] = plugin
            parameters["label"] = label
            parameterLabels["plugin"] = "Plugin Path"
            parameterLabels["label"] = "Label"
            for ((name, defaultValue) in controlDefaults) {
                parameters[name] = defaultValue
                parameterLabels[name] = name
            }

            val definition = NodeDefinition(
                category = "LADSPA",
                color = Color(0xFF1A0D1A),
                headerColor = Color(0xFF2E1A2E),
                portsIn = portsIn,
                portsOut = portsOut,
                pipewireType = "ladspa",
                pipewireLabel = label,
                parameters = parameters,
                parameterLabels = parameterLabels,
                frozenParameters = listOf("plugin", "label"),
            )
            NODE_DEFS[key] = definition
            LABEL_TO_TYPE[label] = key
            LADSPA_LABEL_PORT[label] = portsIn to portsOut
        }

        fun ensureFallbackLadspa() {
            if ("LADSPA Plugin" !in NODE_DEFS) {
                NODE_DEFS["LADSPA Plugin"] = FALLBACK
            }
        }
    }
}



