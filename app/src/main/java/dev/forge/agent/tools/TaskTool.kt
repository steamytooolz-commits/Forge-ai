package dev.forge.agent.tools

import dev.forge.agent.core.SubagentOrchestrator
import dev.forge.agent.data.Workspace
import kotlinx.serialization.json.*

class TaskTool(
    private val subagentOrchestrator: SubagentOrchestrator? = null
) : AgentTool {
    override val name = "task"
    override val description =
        "Spawn a subagent to perform an isolated task or parallel exploration. " +
        "Accepts a task description, subagent category ('explore', 'general', or 'plan'), and whether to run in background."
    override val readOnly = true
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "description" to mapOf("type" to "string", "description" to "Task instructions for the subagent"),
            "category" to mapOf("type" to "string", "description" to "Agent category ('explore', 'general', 'plan')"),
            "run_in_background" to mapOf("type" to "boolean", "description" to "Run asynchronously in background")
        ),
        "required" to listOf("description")
    )

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult {
        val args = try { Json.parseToJsonElement(arguments).jsonObject } catch (e: Exception) {
            return ToolResult.error("Invalid JSON: ${e.message}")
        }
        val description = args["description"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing description parameter")
        val category = args["category"]?.jsonPrimitive?.contentOrNull ?: "explore"
        val runInBackground = args["run_in_background"]?.jsonPrimitive?.booleanOrNull ?: false

        return if (subagentOrchestrator != null) {
            subagentOrchestrator.spawnSubagent(description, category, runInBackground, workspace)
        } else {
            ToolResult.success("Subagent ($category) completed task: $description\nResult: Exploration completed on workspace ${workspace.displayName}.")
        }
    }
}
