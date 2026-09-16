package dev.forge.agent.tools

import dev.forge.agent.data.Workspace
import kotlinx.serialization.json.*

object TodoStore {
    private val todos = mutableListOf<String>()

    fun getTodos(): List<String> = synchronized(this) { todos.toList() }
    fun setTodos(newTodos: List<String>) = synchronized(this) {
        todos.clear()
        todos.addAll(newTodos)
    }
}

class TodoWriteTool : AgentTool {
    override val name = "todowrite"
    override val description =
        "Update the agent's internal todo list for multi-step tasks. Provide an array of task items."
    override val readOnly = false
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "todos" to mapOf(
                "type" to "array",
                "items" to mapOf("type" to "string"),
                "description" to "List of todo tasks"
            )
        ),
        "required" to listOf("todos")
    )

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult {
        val args = try { Json.parseToJsonElement(arguments).jsonObject } catch (e: Exception) {
            return ToolResult.error("Invalid JSON: ${e.message}")
        }
        val items = args["todos"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: return ToolResult.error("Missing todos array")

        TodoStore.setTodos(items)
        return ToolResult.success("Updated todo list with ${items.size} tasks:\n" + items.mapIndexed { i, t -> "${i + 1}. $t" }.joinToString("\n"))
    }
}

class TodoReadTool : AgentTool {
    override val name = "todoread"
    override val description = "Read the current agent's active todo list."
    override val readOnly = true
    override val parameters = mapOf(
        "type" to "object",
        "properties" to emptyMap<String, Any>()
    )

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult {
        val todos = TodoStore.getTodos()
        return if (todos.isEmpty()) {
            ToolResult.success("Todo list is currently empty.")
        } else {
            ToolResult.success("Current Todo List:\n" + todos.mapIndexed { i, t -> "${i + 1}. $t" }.joinToString("\n"))
        }
    }
}
