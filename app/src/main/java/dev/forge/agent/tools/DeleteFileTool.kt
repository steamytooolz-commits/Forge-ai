package dev.forge.agent.tools

import dev.forge.agent.data.Workspace
import kotlinx.serialization.json.*

class DeleteFileTool : AgentTool {
    override val name = "delete_file"
    override val description =
        "Delete a file or directory at the specified relative path."
    override val readOnly = false
    override val defaultPermission = ToolPermission.ASK
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "path" to mapOf("type" to "string", "description" to "Relative path to delete")
        ),
        "required" to listOf("path")
    )

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult {
        val args = try {
            Json.parseToJsonElement(arguments).jsonObject
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments JSON: ${e.message}")
        }
        val path = args["path"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing required argument: path")

        if (!workspace.exists(path)) return ToolResult.error("File not found: $path")

        val deleted = workspace.delete(path)
        return if (deleted) {
            ToolResult.success("Deleted: $path")
        } else {
            ToolResult.error("Failed to delete: $path")
        }
    }
}
