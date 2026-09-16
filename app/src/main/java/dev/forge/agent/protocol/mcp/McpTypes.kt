package dev.forge.agent.protocol.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class McpServerConfig(
    val id: String,
    val name: String,
    val transportType: String = "stdio", // "stdio" or "sse"
    val command: String? = null,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val url: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true
)

@Serializable
data class McpToolDefinition(
    val name: String,
    val description: String = "",
    val inputSchema: JsonObject? = null
)

@Serializable
data class McpJsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Long,
    val method: String,
    val params: JsonObject? = null
)

@Serializable
data class McpJsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: Long? = null,
    val result: JsonObject? = null,
    val error: JsonObject? = null
)
