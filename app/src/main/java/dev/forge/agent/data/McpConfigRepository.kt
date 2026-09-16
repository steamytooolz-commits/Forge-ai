package dev.forge.agent.data

import android.content.Context
import dev.forge.agent.protocol.mcp.McpServerConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class McpConfigRepository(context: Context) {
    private val file = File(context.filesDir, "mcp_servers.json")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    fun loadServers(): List<McpServerConfig> {
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString<List<McpServerConfig>>(file.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveServer(server: McpServerConfig) {
        val existing = loadServers().filterNot { it.id == server.id }.toMutableList()
        existing.add(server)
        file.writeText(json.encodeToString(existing))
    }

    fun deleteServer(id: String) {
        val existing = loadServers().filterNot { it.id == id }
        file.writeText(json.encodeToString(existing))
    }
}
