package dev.forge.agent.core

import kotlinx.serialization.Serializable

@Serializable
data class AgentDefinition(
    val name: String,
    val description: String,
    val systemPrompt: String,
    val modelOverride: String? = null,
    val toolNames: Set<String> = emptySet(), // empty means all default tools
    val readOnly: Boolean = false,
    val isBuiltIn: Boolean = true
)

class AgentRegistry {
    private val agents = mutableMapOf<String, AgentDefinition>()

    init {
        registerBuiltIns()
    }

    private fun registerBuiltIns() {
        register(
            AgentDefinition(
                name = "build",
                description = "Full software development and coding agent with full file/tool write capabilities.",
                systemPrompt = DefaultPrompts.BUILD_AGENT_PROMPT,
                readOnly = false,
                isBuiltIn = true
            )
        )
        register(
            AgentDefinition(
                name = "plan",
                description = "Analysis and planning agent without file modification capabilities. Drafts architectural plans.",
                systemPrompt = DefaultPrompts.PLAN_AGENT_PROMPT,
                readOnly = true,
                isBuiltIn = true
            )
        )
        register(
            AgentDefinition(
                name = "general",
                description = "Multi-step research and codebase investigation agent.",
                systemPrompt = "You are a specialized research agent. Perform deep scanning, information retrieval, and synthesis.",
                readOnly = true,
                isBuiltIn = true
            )
        )
        register(
            AgentDefinition(
                name = "explore",
                description = "Fast, read-only codebase exploration subagent.",
                systemPrompt = "You are an exploratory subagent. Quickly inspect directory trees, files, and symbol references.",
                readOnly = true,
                isBuiltIn = true
            )
        )
        register(
            AgentDefinition(
                name = "compaction",
                description = "Context compression and conversation summarizer.",
                systemPrompt = DefaultPrompts.COMPACTION_AGENT_PROMPT,
                readOnly = true,
                isBuiltIn = true
            )
        )
        register(
            AgentDefinition(
                name = "title",
                description = "Concise session title generator.",
                systemPrompt = "Generate a concise 3-5 word title summarizing the user request. Output ONLY the title.",
                readOnly = true,
                isBuiltIn = true
            )
        )
        register(
            AgentDefinition(
                name = "summary",
                description = "Produces session summary exports.",
                systemPrompt = "Produce a structured markdown summary of the work done in this session.",
                readOnly = true,
                isBuiltIn = true
            )
        )
    }

    fun register(agent: AgentDefinition) {
        agents[agent.name.lowercase()] = agent
    }

    fun get(name: String): AgentDefinition? = agents[name.lowercase()] ?: agents["build"]

    fun all(): List<AgentDefinition> = agents.values.toList()

    fun removeCustom(name: String): Boolean {
        val agent = agents[name.lowercase()] ?: return false
        if (agent.isBuiltIn) return false
        agents.remove(name.lowercase())
        return true
    }
}
