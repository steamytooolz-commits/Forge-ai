package dev.forge.agent.tools

import dev.forge.agent.data.Workspace
import kotlinx.serialization.json.*

class EditFileTool : AgentTool {
    override val name = "edit_file"
    override val description =
        "Replace an exact string in a file. old_string must appear exactly once unless replace_all is true. " +
        "Use this for surgical edits. Provide enough context to make the match unique."
    override val readOnly = false
    override val defaultPermission = ToolPermission.ALLOW
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "path" to mapOf("type" to "string", "description" to "Relative path to the file"),
            "old_string" to mapOf("type" to "string", "description" to "Exact string to be replaced"),
            "new_string" to mapOf("type" to "string", "description" to "Replacement string"),
            "replace_all" to mapOf("type" to "boolean", "description" to "Whether to replace all occurrences", "default" to false)
        ),
        "required" to listOf("path", "old_string", "new_string")
    )

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult {
        val args = try {
            Json.parseToJsonElement(arguments).jsonObject
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments JSON: ${e.message}")
        }
        val path = args["path"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing required argument: path")
        val oldStr = args["old_string"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing required argument: old_string")
        val newStr = args["new_string"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing required argument: new_string")
        val replaceAll = args["replace_all"]?.jsonPrimitive?.booleanOrNull ?: false

        if (!workspace.exists(path)) return ToolResult.error("File not found: $path")

        val content = try {
            workspace.readText(path)
        } catch (e: Exception) {
            return ToolResult.error("Failed to read file '$path': ${e.message}")
        }

        val occurrences = content.split(oldStr).size - 1
        if (occurrences == 0) return ToolResult.error("old_string not found in $path")
        if (occurrences > 1 && !replaceAll) {
            return ToolResult.error(
                "old_string appears $occurrences times in $path. " +
                "Provide more context to make it unique, or set replace_all=true."
            )
        }

        val updated = if (replaceAll) content.replace(oldStr, newStr)
                      else content.replaceFirst(oldStr, newStr)
        return try {
            workspace.writeText(path, updated)
            ToolResult.success("Replaced ${if (replaceAll) occurrences else 1} occurrence(s) in $path")
        } catch (e: Exception) {
            ToolResult.error("Failed to write updated content to '$path': ${e.message}")
        }
    }
}
