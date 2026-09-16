package dev.forge.agent.ui.agents

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.forge.agent.core.AgentDefinition
import dev.forge.agent.core.AgentRegistry
import dev.forge.agent.data.AgentConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AgentViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AgentConfigRepository(application)
    private val registry = AgentRegistry()

    private val _agents = MutableStateFlow<List<AgentDefinition>>(emptyList())
    val agents: StateFlow<List<AgentDefinition>> = _agents.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val builtIns = registry.all()
        val customs = repo.loadCustomAgents()
        _agents.value = builtIns + customs
    }

    fun addCustomAgent(name: String, description: String, prompt: String, model: String? = null) {
        val agent = AgentDefinition(
            name = name,
            description = description,
            systemPrompt = prompt,
            modelOverride = model?.ifBlank { null },
            isBuiltIn = false
        )
        repo.saveCustomAgent(agent)
        refresh()
    }

    fun deleteCustomAgent(name: String) {
        repo.deleteCustomAgent(name)
        refresh()
    }
}
