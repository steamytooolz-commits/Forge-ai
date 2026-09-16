package dev.forge.agent.tools

import dev.forge.agent.data.Workspace
import kotlinx.serialization.json.*

class AstGrepSearchTool : AgentTool {
    override val name = "ast_grep_search"
    override val description =
        "Search code patterns structurally across source files in the workspace. " +
        "Supports pattern matching with meta-variables like \$NAME, \$\$\$ARGS for Kotlin, Java, TS, JS, and Python."
    override val readOnly = true
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "pattern" to mapOf("type" to "string", "description" to "AST pattern or structural query (e.g., 'fun \$NAME(\$\$\$ARGS)')"),
            "path" to mapOf("type" to "string", "description" to "Subdirectory or file path to search in (optional)", "default" to "."),
            "language" to mapOf("type" to "string", "description" to "Language hint (e.g. kotlin, java, typescript, python)")
        ),
        "required" to listOf("pattern")
    )

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult {
        val args = try {
            Json.parseToJsonElement(arguments).jsonObject
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments JSON: ${e.message}")
        }

        val pattern = args["pattern"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing required parameter: pattern")
        val subPath = args["path"]?.jsonPrimitive?.contentOrNull ?: "."

        // Convert AST pattern placeholders ($NAME -> [a-zA-Z_][a-zA-Z0-9_]*, $$$ARGS -> .*)
        val regexPattern = pattern
            .replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace("{", "\\{")
            .replace("}", "\\}")
            .replace("$$$", ".*?")
            .replace(Regex("\\$[A-Za-z0-9_]+"), "[a-zA-Z0-9_]+")

        val regex = try {
            Regex(regexPattern, RegexOption.IGNORE_CASE)
        } catch (e: Exception) {
            Regex(Regex.escape(pattern), RegexOption.IGNORE_CASE)
        }

        val results = StringBuilder()
        var matchCount = 0
        val extensions = setOf("kt", "java", "ts", "js", "py", "rs", "go", "json", "xml", "c", "cpp")

        fun searchFile(rel: String) {
            if (matchCount >= 200) return
            val ext = rel.substringAfterLast('.', "")
            if (ext.lowercase() !in extensions) return
            try {
                val content = workspace.readText(rel)
                val lines = content.lines()
                lines.forEachIndexed { index, line ->
                    if (regex.containsMatchIn(line)) {
                        matchCount++
                        results.append("$rel:${index + 1}: ${line.trim()}\n")
                        if (matchCount >= 200) return
                    }
                }
            } catch (_: Exception) {}
        }

        fun walk(rel: String) {
            if (matchCount >= 200) return
            if (workspace.exists(rel) && !workspace.isDirectory(rel)) {
                searchFile(rel)
                return
            }
            val entries = workspace.list(rel)
            for (entry in entries) {
                if (entry.name.startsWith(".") || entry.name == "build" || entry.name == "node_modules") continue
                val child = if (rel == "." || rel.isEmpty()) entry.name else "$rel/${entry.name}"
                if (entry.isDirectory) {
                    walk(child)
                } else {
                    searchFile(child)
                }
            }
        }

        try {
            walk(subPath)
        } catch (e: Exception) {
            return ToolResult.error("AST Grep error: ${e.message}")
        }

        return if (matchCount == 0) {
            ToolResult.success("No matches found for AST pattern: $pattern")
        } else {
            ToolResult.success("Found $matchCount matches:\n$results")
        }
    }
}
