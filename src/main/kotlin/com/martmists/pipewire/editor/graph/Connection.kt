package com.martmists.pipewire.editor.graph

import java.util.UUID

data class Connection(
    val id: String = UUID.randomUUID().toString().take(8),
    val fromNode: String,
    val fromPort: String,
    val toNode: String,
    val toPort: String
)
