package dev.forge.agent.protocol.lsp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class LspServerConfig(
    val language: String,
    val fileExtensions: List<String>,
    val command: String,
    val args: List<String> = emptyList(),
    val enabled: Boolean = true
)

@Serializable
data class LspPosition(
    val line: Int,
    val character: Int
)

@Serializable
data class LspRange(
    val start: LspPosition,
    val end: LspPosition
)

@Serializable
data class LspDiagnostic(
    val range: LspRange,
    val severity: Int? = null,
    val message: String,
    val source: String? = null
)
