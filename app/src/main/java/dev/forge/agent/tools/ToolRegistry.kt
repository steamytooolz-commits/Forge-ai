package dev.forge.agent.tools

import dev.forge.agent.llm.LlmToolDefinition

class ToolRegistry(initialTools: List<AgentTool> = emptyList()) {

    private val tools = mutableMapOf<String, AgentTool>()

    init {
        initialTools.forEach { register(it) }
    }

    fun register(tool: AgentTool): ToolRegistry {
        tools[tool.name] = tool
        return this
    }

    fun get(name: String): AgentTool? {
        val canonical = when (name) {
            "read" -> "read_file"
            "write" -> "write_file"
            "edit" -> "edit_file"
            "delete" -> "delete_file"
            "list_dir" -> "list_directory"
            "web_fetch" -> "webfetch"
            "web_search" -> "websearch"
            "todo_write" -> "todowrite"
            "todo_read" -> "todoread"
            else -> name
        }
        return tools[canonical] ?: tools[name]
    }

    fun getAll(): List<AgentTool> = tools.values.toList()
    fun all(): List<AgentTool> = getAll()

    fun definitions(): List<LlmToolDefinition> = getDefinitions(false)

    fun readOnly(): ToolRegistry = ToolRegistry().apply {
        tools.values.filter { it.readOnly }.forEach { register(it) }
    }

    fun filtered(enabledNames: Set<String>): ToolRegistry = ToolRegistry().apply {
        tools.values.filter { it.name in enabledNames }.forEach { register(it) }
    }

    fun getDefinitions(planMode: Boolean = false): List<LlmToolDefinition> =
        tools.values
            .filter { if (planMode) it.readOnly else true }
            .map { tool ->
                LlmToolDefinition(
                    name = tool.name,
                    description = tool.description,
                    parameters = tool.parameters
                )
            }

    companion object {
        fun default(): ToolRegistry = defaultRegistry()

        fun defaultRegistry(): ToolRegistry = ToolRegistry(
            listOf(
                // File tools
                ReadFileTool(),
                WriteFileTool(),
                EditFileTool(),
                DeleteFileTool(),
                PatchTool(),
                // Search tools
                ListDirectoryTool(),
                GlobTool(),
                GrepTool(),
                AstGrepSearchTool(),
                // Execution tools
                BashTool(),
                // LSP tools
                LspDiagnosticsTool(),
                LspGotoDefinitionTool(),
                LspFindReferencesTool(),
                LspHoverTool(),
                LspDocumentSymbolTool(),
                LspWorkspaceSymbolTool(),
                LspRenameTool(),
                // Web tools
                WebFetchTool(),
                WebSearchTool(),
                // Delegation tools
                TaskTool(),
                // Session tools
                SessionListTool(),
                SessionReadTool(),
                SessionSearchTool(),
                SessionInfoTool(),
                // Task management tools
                TodoWriteTool(),
                TodoReadTool(),
                // Interaction tools
                QuestionTool()
            )
        )
    }
}
