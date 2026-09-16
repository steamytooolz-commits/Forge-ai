package dev.forge.agent.tools

import dev.forge.agent.data.Workspace
import kotlinx.serialization.json.*

class GrepTool : AgentTool {
    override val name = "grep"
    override val description =
        "Search file contents by regular expression. " +
        "Returns matching lines with file path and line number. " +
        "Optionally filter files with an include glob."
    override val readOnly = true
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "pattern" to mapOf("type" to "string", "description" to "Regex pattern to search"),
            "path" to mapOf("type" to "string", "description" to "Starting path", "default" to "."),
            "include" to mapOf("type" to "string", "description" to "Glob filter, e.g. *.kt"),
            "case_sensitive" to mapOf("type" to "boolean", "default" to true),
            "max_results" to mapOf("type" to "integer", "default" to 200)
        ),
        "required" to listOf("pattern")
    )

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult {
        val args = try {
            Json.parseToJsonElement(arguments).jsonObject
        } catch (e: Exception) {
            return ToolResult.error("Invalid JSON: ${e.message}")
        }

        val patternStr = args["pattern"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing required argument: pattern")
        val rootRel = args["path"]?.jsonPrimitive?.contentOrNull ?: "."
        val includeGlob = args["include"]?.jsonPrimitive?.contentOrNull
        val caseSensitive = args["case_sensitive"]?.jsonPrimitive?.booleanOrNull ?: true
        val maxResults = args["max_results"]?.jsonPrimitive?.intOrNull ?: 200

        val regex = try {
            Regex(patternStr, if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
        } catch (e: Exception) {
            return ToolResult.error("Invalid regex: ${e.message}")
        }

        val results = mutableListOf<String>()

        fun searchInFile(filePath: String) {
            if (results.size >= maxResults) return
            try {
                val content = workspace.readText(filePath)
                val lines = content.lines()
                lines.forEachIndexed { idx, line ->
                    if (regex.containsMatchIn(line)) {
                        results.add("$filePath:${idx + 1}: $line")
                        if (results.size >= maxResults) return
                    }
                }
            } catch (_: Exception) {
                // Ignore binary/unreadable files
            }
        }

        fun walk(currentRel: String) {
            if (results.size >= maxResults) return
            if (workspace.exists(currentRel) && !workspace.isDirectory(currentRel)) {
                searchInFile(currentRel)
                return
            }
            val entries = workspace.list(currentRel)
            for (entry in entries) {
                if (entry.name.startsWith(".") || entry.name == "build" || entry.name == "node_modules") continue
                val path = if (currentRel == "." || currentRel.isEmpty()) entry.name else "$currentRel/${entry.name}"
                if (entry.isDirectory) {
                    walk(path)
                } else {
                    if (includeGlob != null && !entry.name.endsWith(includeGlob.removePrefix("*"))) {
                        continue
                    }
                    searchInFile(path)
                }
            }
        }

        try {
            walk(rootRel)
        } catch (e: Exception) {
            return ToolResult.error("Grep error: ${e.message}")
        }

        if (results.isEmpty()) return ToolResult.success("No matches for pattern: $patternStr")
        val truncated = if (results.size >= maxResults) "\n(truncated at $maxResults matches)" else ""
        return ToolResult.success(results.joinToString("\n") + truncated)
    }
}
