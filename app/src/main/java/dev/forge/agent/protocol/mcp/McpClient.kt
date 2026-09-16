package dev.forge.agent.protocol.mcp

import dev.forge.agent.data.Workspace
import dev.forge.agent.tools.AgentTool
import dev.forge.agent.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

interface McpTransport {
    suspend fun send(request: McpJsonRpcRequest): McpJsonRpcResponse
    fun close()
}

class HttpSseMcpTransport(
    private val url: String,
    private val headers: Map<String, String> = emptyMap()
) : McpTransport {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun send(request: McpJsonRpcRequest): McpJsonRpcResponse = withContext(Dispatchers.IO) {
        val reqBody = json.encodeToString(request).toRequestBody("application/json".toMediaType())
        val reqBuilder = Request.Builder().url(url).post(reqBody)
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }

        val response = client.newCall(reqBuilder.build()).execute()
        val body = response.body?.string() ?: "{}"
        try {
            json.decodeFromString<McpJsonRpcResponse>(body)
        } catch (_: Exception) {
            McpJsonRpcResponse(id = request.id, error = buildJsonObject { put("message", "Malformed MCP response: $body") })
        }
    }

    override fun close() {}
}

class McpClient(
    val config: McpServerConfig,
    private val transport: McpTransport
) {
    private val idCounter = AtomicLong(1)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun listTools(): List<McpToolDefinition> {
        val req = McpJsonRpcRequest(
            id = idCounter.getAndIncrement(),
            method = "tools/list",
            params = buildJsonObject { }
        )
        val res = transport.send(req)
        val toolsArray = res.result?.get("tools")?.jsonArray ?: return emptyList()

        return toolsArray.mapNotNull {
            try {
                val obj = it.jsonObject
                McpToolDefinition(
                    name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                    description = obj["description"]?.jsonPrimitive?.contentOrNull ?: "",
                    inputSchema = obj["inputSchema"]?.jsonObject
                )
            } catch (_: Exception) { null }
        }
    }

    suspend fun callTool(name: String, arguments: JsonObject): String {
        val req = McpJsonRpcRequest(
            id = idCounter.getAndIncrement(),
            method = "tools/call",
            params = buildJsonObject {
                put("name", name)
                put("arguments", arguments)
            }
        )
        val res = transport.send(req)
        return res.result?.get("content")?.toString() ?: res.error?.get("message")?.jsonPrimitive?.contentOrNull ?: "No output"
    }

    fun toAgentTools(tools: List<McpToolDefinition>): List<AgentTool> {
        return tools.map { tool ->
            object : AgentTool {
                override val name = "${config.id}__${tool.name}"
                override val description = "[MCP: ${config.name}] ${tool.description}"
                override val parameters = tool.inputSchema?.let {
                    try {
                        Json.decodeFromJsonElement<Map<String, Any?>>(it)
                    } catch (_: Exception) {
                        mapOf("type" to "object")
                    }
                } ?: mapOf("type" to "object")

                override suspend fun execute(arguments: String, workspace: Workspace): ToolResult {
                    return try {
                        val parsedArgs = json.parseToJsonElement(arguments).jsonObject
                        val result = callTool(tool.name, parsedArgs)
                        ToolResult.success(result)
                    } catch (e: Exception) {
                        ToolResult.error("MCP tool execution failed: ${e.message}")
                    }
                }
            }
        }
    }
}
