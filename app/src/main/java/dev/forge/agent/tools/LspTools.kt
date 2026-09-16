package dev.forge.agent.tools

import dev.forge.agent.data.Workspace
import kotlinx.serialization.json.*

class LspDiagnosticsTool : AgentTool {
    override val name = "lsp_diagnostics"
    override val description =
        "Retrieve static analysis diagnostics, syntax errors, and compiler warnings for a workspace file."
    override val readOnly = true
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "path" to mapOf("type" to "string", "description" to "Relative path of file to check")
        ),
        "required" to listOf("path")
    )

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult {
        val args = try { Json.parseToJsonElement(arguments).jsonObject } catch (e: Exception) {
            return ToolResult.error("Invalid JSON: ${e.message}")
        }
        val path = args["path"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing path parameter")
        if (!workspace.exists(path)) return ToolResult.error("File not found: $path")

        val errors = mutableListOf<String>()
        val content = try { workspace.readText(path) } catch (e: Exception) {
            return ToolResult.error("Failed to read file: ${e.message}")
        }
        val lines = content.lines()
        val ext = path.substringAfterLast('.', "").lowercase()

        if (ext == "json") {
            try {
                Json.parseToJsonElement(content)
            } catch (e: Exception) {
                errors.add("JSON Syntax Error: ${e.message}")
            }
        } else if (ext in listOf("kt", "java", "ts", "js")) {
            var braceDepth = 0
            var parenDepth = 0
            lines.forEachIndexed { idx, line ->
                val trimmed = line.trim()
                if (!trimmed.startsWith("//") && !trimmed.startsWith("/*")) {
                    braceDepth += line.count { it == '{' } - line.count { it == '}' }
                    parenDepth += line.count { it == '(' } - line.count { it == ')' }
                }
            }
            if (braceDepth != 0) errors.add("Line warning: Unbalanced curly braces (depth $braceDepth)")
            if (parenDepth != 0) errors.add("Line warning: Unbalanced parentheses (depth $parenDepth)")
        }

        return if (errors.isEmpty()) {
            ToolResult.success("No diagnostics errors or warnings found in $path")
        } else {
            ToolResult.success("Diagnostics for $path:\n" + errors.joinToString("\n"))
        }
    }
}

class LspGotoDefinitionTool : AgentTool {
    override val name = "lsp_goto_definition"
    override val description = "Jump to the definition of a symbol at the given file and position."
    override val readOnly = true
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "path" to mapOf("type" to "string", "description" to "Relative path to file"),
            "symbol" to mapOf("type" to "string", "description" to "Symbol name to locate")
        ),
        "required" to listOf("symbol")
    )

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult {
        val args = try { Json.parseToJsonElement(arguments).jsonObject } catch (e: Exception) {
            return ToolResult.error("Invalid JSON: ${e.message}")
        }
        val symbol = args["symbol"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.error("Missing symbol")

        val results = mutableListOf<String>()
        val searchPatterns = listOf("class $symbol", "interface $symbol", "fun $symbol", "val $symbol", "var $symbol", "def $symbol", "function $symbol")

        fun scan(rel: String) {
            if (results.size >= 50) return
            if (workspace.exists(rel) && !workspace.isDirectory(rel)) {
                try {
                    val lines = workspace.readText(rel).lines()
                    lines.forEachIndexed { index, line ->
                        if (searchPatterns.any { line.contains(it) }) {
                            results.add("$rel:${index + 1}: ${line.trim()}")
                        }
                    }
                } catch (_: Exception) {}
                return
            }
            val entries = workspace.list(rel)
            for (entry in entries) {
                if (entry.name.startsWith(".") || entry.name == "build" || entry.name == "node_modules") continue
                val child = if (rel == "." || rel.isEmpty()) entry.name else "$rel/${entry.name}"
                if (entry.isDirectory) scan(child)
                else {
                    try {
                        val lines = workspace.readText(child).lines()
                        lines.forEachIndexed { index, line ->
                            if (searchPatterns.any { line.contains(it) }) {
                                results.add("$child:${index + 1}: ${line.trim()}")
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        scan(".")

        return if (results.isEmpty()) {
            ToolResult.success("Definition not found for symbol: $symbol")
        } else {
            ToolResult.success("Definitions found for $symbol:\n" + results.joinToString("\n"))
        }
    }
}

class LspFindReferencesTool : AgentTool {
    override val name = "lsp_find_references"
    override val description = "Find all references and usages of a symbol across the workspace."
    override val readOnly = true
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "symbol" to mapOf("type" to "string", "description" to "Symbol name to find usages for"),
            "path" to mapOf("type" to "string", "description" to "Optional scope path")
        ),
        "required" to listOf("symbol")
    )

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult {
        val args = try { Json.parseToJsonElement(arguments).jsonObject } catch (e: Exception) {
            return ToolResult.error("Invalid JSON: ${e.message}")
        }
        val symbol = args["symbol"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.error("Missing symbol")

        val results = mutableListOf<String>()

        fun scan(rel: String) {
            if (results.size >= 100) return
            val entries = workspace.list(rel)
            for (entry in entries) {
                if (entry.name.startsWith(".") || entry.name == "build" || entry.name == "node_modules") continue
                val child = if (rel == "." || rel.isEmpty()) entry.name else "$rel/${entry.name}"
                if (entry.isDirectory) scan(child)
                else {
                    try {
                        val lines = workspace.readText(child).lines()
                        lines.forEachIndexed { index, line ->
                            if (line.contains(symbol)) {
                                results.add("$child:${index + 1}: ${line.trim()}")
                                if (results.size >= 100) return
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        scan(".")

        return if (results.isEmpty()) {
            ToolResult.success("No references found for symbol: $symbol")
        } else {
            ToolResult.success("Found ${results.size} references to $symbol:\n" + results.joinToString("\n"))
        }
    }
}

class LspHoverTool : AgentTool {
    override val name = "lsp_hover"
    override val description = "Get hover information, type signatures, and documentation for a symbol."
    override val readOnly = true
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "path" to mapOf("type" to "string", "description" to "Relative path to file"),
            "symbol" to mapOf("type" to "string", "description" to "Symbol name")
        ),
        "required" to listOf("path", "symbol")
    )

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult {
        val args = try { Json.parseToJsonElement(arguments).jsonObject } catch (e: Exception) {
            return ToolResult.error("Invalid JSON: ${e.message}")
        }
        val path = args["path"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.error("Missing path")
        val symbol = args["symbol"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.error("Missing symbol")

        if (!workspace.exists(path)) return ToolResult.error("File not found: $path")

        val matchedLine = try {
            workspace.readText(path).lines().firstOrNull { it.contains(symbol) }
        } catch (e: Exception) { null }

        return if (matchedLine != null) {
            ToolResult.success("Symbol: `$symbol`\nDeclaration context: `${matchedLine.trim()}`\nLocation: `$path`")
        } else {
            ToolResult.success("No hover information available for `$symbol` in `$path`")
        }
    }
}

class LspDocumentSymbolTool : AgentTool {
    override val name = "lsp_document_symbol"
    override val description = "Get an outline of symbols (classes, functions, variables) declared in a file."
    override val readOnly = true
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "path" to mapOf("type" to "string", "description" to "Relative path to file")
        ),
        "required" to listOf("path")
    )

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult {
        val args = try { Json.parseToJsonElement(arguments).jsonObject } catch (e: Exception) {
            return ToolResult.error("Invalid JSON: ${e.message}")
        }
        val path = args["path"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.error("Missing path")
        if (!workspace.exists(path)) return ToolResult.error("File not found: $path")

        val symbols = mutableListOf<String>()
        val declPatterns = listOf("class ", "interface ", "enum class ", "fun ", "object ", "struct ", "def ", "type ")

        try {
            workspace.readText(path).lines().forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (declPatterns.any { trimmed.startsWith(it) }) {
                    symbols.add("Line ${index + 1}: $trimmed")
                }
            }
        } catch (_: Exception) {}

        return ToolResult.success("Symbols in $path:\n" + symbols.ifEmpty { listOf("No top-level declarations found") }.joinToString("\n"))
    }
}

class LspWorkspaceSymbolTool : AgentTool {
    override val name = "lsp_workspace_symbol"
    override val description = "Search symbols across the entire workspace."
    override val readOnly = true
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf("type" to "string", "description" to "Symbol query string")
        ),
        "required" to listOf("query")
    )

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult {
        val args = try { Json.parseToJsonElement(arguments).jsonObject } catch (e: Exception) {
            return ToolResult.error("Invalid JSON: ${e.message}")
        }
        val query = args["query"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.error("Missing query")

        val symbols = mutableListOf<String>()

        fun scan(rel: String) {
            if (symbols.size >= 100) return
            val entries = workspace.list(rel)
            for (entry in entries) {
                if (entry.name.startsWith(".") || entry.name == "build" || entry.name == "node_modules") continue
                val child = if (rel == "." || rel.isEmpty()) entry.name else "$rel/${entry.name}"
                if (entry.isDirectory) scan(child)
                else {
                    try {
                        workspace.readText(child).lines().forEachIndexed { index, line ->
                            val trimmed = line.trim()
                            if ((trimmed.startsWith("class ") || trimmed.startsWith("fun ") || trimmed.startsWith("interface ")) && trimmed.contains(query, ignoreCase = true)) {
                                symbols.add("$child:${index + 1}: $trimmed")
                                if (symbols.size >= 100) return
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        scan(".")

        return ToolResult.success("Workspace symbols matching '$query':\n" + symbols.ifEmpty { listOf("No symbols found") }.joinToString("\n"))
    }
}

class LspRenameTool : AgentTool {
    override val name = "lsp_rename"
    override val description = "Rename a symbol across all workspace files."
    override val readOnly = false
    override val defaultPermission = ToolPermission.ASK
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "old_name" to mapOf("type" to "string", "description" to "Current symbol name"),
            "new_name" to mapOf("type" to "string", "description" to "New replacement symbol name")
        ),
        "required" to listOf("old_name", "new_name")
    )

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult {
        val args = try { Json.parseToJsonElement(arguments).jsonObject } catch (e: Exception) {
            return ToolResult.error("Invalid JSON: ${e.message}")
        }
        val oldName = args["old_name"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.error("Missing old_name")
        val newName = args["new_name"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.error("Missing new_name")

        var modifiedFiles = 0

        fun process(rel: String) {
            val entries = workspace.list(rel)
            for (entry in entries) {
                if (entry.name.startsWith(".") || entry.name == "build" || entry.name == "node_modules") continue
                val child = if (rel == "." || rel.isEmpty()) entry.name else "$rel/${entry.name}"
                if (entry.isDirectory) process(child)
                else {
                    try {
                        val text = workspace.readText(child)
                        if (text.contains(oldName)) {
                            val replaced = text.replace(oldName, newName)
                            workspace.writeText(child, replaced)
                            modifiedFiles++
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        process(".")

        return ToolResult.success("Renamed '$oldName' to '$newName' across $modifiedFiles files.")
    }
}
