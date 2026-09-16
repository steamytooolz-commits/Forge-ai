package dev.forge.agent.data

import android.content.Context
import dev.forge.agent.core.AgentDefinition
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class AgentConfigRepository(context: Context) {
    private val file = File(context.filesDir, "custom_agents.json")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    fun loadCustomAgents(): List<AgentDefinition> {
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString<List<AgentDefinition>>(file.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCustomAgent(agent: AgentDefinition) {
        val existing = loadCustomAgents().filterNot { it.name.equals(agent.name, ignoreCase = true) }.toMutableList()
        existing.add(agent.copy(isBuiltIn = false))
        file.writeText(json.encodeToString(existing))
    }

    fun deleteCustomAgent(name: String) {
        val existing = loadCustomAgents().filterNot { it.name.equals(name, ignoreCase = true) }
        file.writeText(json.encodeToString(existing))
    }
}
