package dev.forge.agent.tools

import dev.forge.agent.data.Workspace
import kotlinx.serialization.json.*

class ReadFileTool : AgentTool {
    override val name = "read_file"
    override val description =
        "Read the contents of a file. Path is relative to the workspace root. " +
        "Optionally specify start_line and end_line (1-indexed, inclusive). " +
        "Returns the content with line numbers prefixed for easy editing."
    override val readOnly = true
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "path" to mapOf("type" to "string", "description" to "Relative file path"),
            "start_line" to mapOf("type" to "integer", "description" to "Start line (1-indexed)"),
            "end_line" to mapOf("type" to "integer", "description" to "End line (inclusive)")
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

        if (!workspace.exists(path)) {
            return ToolResult.error("File not found: $path")
        }
        if (workspace.isDirectory(path)) {
            return ToolResult.error("Path is a directory, not a file: $path")
        }

        val rawContent = try {
            workspace.readText(path)
        } catch (e: Exception) {
            return ToolResult.error("Failed to read file: ${e.message}")
        }

        val lines = rawContent.lines()
        val startLine = args["start_line"]?.jsonPrimitive?.intOrNull ?: 1
        val endLine = args["end_line"]?.jsonPrimitive?.intOrNull ?: lines.size

        val s = (startLine - 1).coerceIn(0, lines.size)
        val e = endLine.coerceIn(s, lines.size)

        val width = e.toString().length
        val builder = StringBuilder()
        for (i in s until e) {
            builder.append((i + 1).toString().padStart(width))
            builder.append(": ")
            builder.append(lines[i])
            builder.append('\n')
        }
        return ToolResult.success(builder.toString())
    }
}
