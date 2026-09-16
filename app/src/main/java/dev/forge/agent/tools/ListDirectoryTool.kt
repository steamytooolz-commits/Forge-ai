package dev.forge.agent.tools

import dev.forge.agent.data.Workspace
import kotlinx.serialization.json.*

class ListDirectoryTool : AgentTool {
    override val name = "list_directory"
    override val description =
        "List files and directories in a directory relative to the workspace root. " +
        "Returns names with a trailing slash for directories and byte sizes for files."
    override val readOnly = true
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "path" to mapOf("type" to "string", "description" to "Relative path to list", "default" to ".")
        )
    )

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult {
        val path = try {
            Json.parseToJsonElement(arguments).jsonObject["path"]?.jsonPrimitive?.contentOrNull ?: "."
        } catch (_: Exception) { "." }

        val entries = try {
            workspace.list(path)
        } catch (e: Exception) {
            return ToolResult.error("Could not read directory '$path': ${e.message}")
        }

        val lines = entries.map { entry ->
            if (entry.isDirectory) "${entry.name}/"
            else "${entry.name} (${entry.sizeBytes} bytes)"
        }

        return ToolResult.success(lines.joinToString("\n").ifEmpty { "(empty directory)" })
    }
}
