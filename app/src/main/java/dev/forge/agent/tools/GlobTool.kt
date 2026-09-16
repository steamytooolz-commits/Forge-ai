package dev.forge.agent.tools

import dev.forge.agent.data.Workspace
import kotlinx.serialization.json.*

class GlobTool : AgentTool {
    override val name = "glob"
    override val description =
        "Find files matching a glob pattern relative to a root directory in the workspace. " +
        "Examples: '**/*.kt', 'src/**/*.java', '*.md'."
    override val readOnly = true
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "pattern" to mapOf("type" to "string", "description" to "Glob pattern to match"),
            "path" to mapOf("type" to "string", "description" to "Base relative directory to start search", "default" to ".")
        ),
        "required" to listOf("pattern")
    )

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult {
        val args = try {
            Json.parseToJsonElement(arguments).jsonObject
        } catch (e: Exception) {
            return ToolResult.error("Invalid JSON: ${e.message}")
        }

        val pattern = args["pattern"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing required argument: pattern")
        val rootRel = args["path"]?.jsonPrimitive?.contentOrNull ?: "."

        val regex = try {
            globToRegex(pattern)
        } catch (e: Exception) {
            return ToolResult.error("Invalid glob pattern: ${e.message}")
        }

        val results = mutableListOf<String>()
        val limit = 500

        fun walk(currentRel: String) {
            if (results.size >= limit) return
            val entries = workspace.list(currentRel)
            for (entry in entries) {
                if (entry.name.startsWith(".") || entry.name == "build" || entry.name == "node_modules") {
                    continue
                }
                val path = if (currentRel == "." || currentRel.isEmpty()) entry.name else "$currentRel/${entry.name}"
                if (entry.isDirectory) {
                    walk(path)
                } else {
                    if (regex.matches(path) || regex.matches(entry.name)) {
                        results.add(path)
                        if (results.size >= limit) return
                    }
                }
            }
        }

        try {
            walk(rootRel)
        } catch (e: Exception) {
            return ToolResult.error("Error during glob traversal: ${e.message}")
        }

        if (results.isEmpty()) return ToolResult.success("No matches for $pattern")
        val truncated = if (results.size >= limit) "\n(truncated at $limit matches)" else ""
        return ToolResult.success(results.joinToString("\n") + truncated)
    }

    private fun globToRegex(glob: String): Regex {
        val sb = StringBuilder("^")
        var i = 0
        while (i < glob.length) {
            val c = glob[i]
            when (c) {
                '*' -> {
                    if (i + 1 < glob.length && glob[i + 1] == '*') {
                        if (i + 2 < glob.length && glob[i + 2] == '/') {
                            sb.append("(?:.+/)?")
                            i += 2
                        } else {
                            sb.append(".*")
                            i += 1
                        }
                    } else {
                        sb.append("[^/]*")
                    }
                }
                '?' -> sb.append("[^/]")
                '.' -> sb.append("\\.")
                '/' -> sb.append("/")
                '(', ')', '[', ']', '{', '}', '+', '^', '$', '|', '\\' -> {
                    sb.append('\\').append(c)
                }
                else -> sb.append(c)
            }
            i++
        }
        sb.append("$")
        return Regex(sb.toString())
    }
}
