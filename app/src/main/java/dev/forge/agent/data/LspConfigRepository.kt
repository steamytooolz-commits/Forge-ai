package dev.forge.agent.data

import android.content.Context
import dev.forge.agent.protocol.lsp.LspServerConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class LspConfigRepository(context: Context) {
    private val file = File(context.filesDir, "lsp_servers.json")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    fun loadServers(): List<LspServerConfig> {
        if (!file.exists()) {
            return listOf(
                LspServerConfig("Kotlin", listOf("kt", "kts"), "kotlin-language-server"),
                LspServerConfig("Java", listOf("java"), "jdtls"),
                LspServerConfig("TypeScript / JavaScript", listOf("ts", "js", "json"), "typescript-language-server"),
                LspServerConfig("Python", listOf("py"), "pylsp")
            )
        }
        return try {
            json.decodeFromString<List<LspServerConfig>>(file.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveServer(server: LspServerConfig) {
        val existing = loadServers().filterNot { it.language.equals(server.language, ignoreCase = true) }.toMutableList()
        existing.add(server)
        file.writeText(json.encodeToString(existing))
    }
}
