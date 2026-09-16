package dev.forge.agent.tools

import dev.forge.agent.data.Workspace
import kotlinx.serialization.json.*

class WriteFileTool : AgentTool {
    override val name = "write_file"
    override val description =
        "Create or overwrite a file at the specified relative path with the provided content. " +
        "Creates any intermediate directories automatically."
    override val readOnly = false
    override val defaultPermission = ToolPermission.ALLOW
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "path" to mapOf("type" to "string", "description" to "Relative path to write"),
            "content" to mapOf("type" to "string", "description" to "Full file content to write")
        ),
        "required" to listOf("path", "content")
    )

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult {
        val args = try {
            Json.parseToJsonElement(arguments).jsonObject
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments JSON: ${e.message}")
        }
        val path = args["path"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing required argument: path")
        val content = args["content"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing required argument: content")

        return try {
            workspace.writeText(path, content)
            ToolResult.success("Successfully wrote ${content.length} characters to $path")
        } catch (e: Exception) {
            ToolResult.error("Failed to write file '$path': ${e.message}")
        }
    }
}
