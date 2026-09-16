package dev.forge.agent.tools

import dev.forge.agent.data.Workspace
import kotlinx.serialization.json.*

class PatchTool : AgentTool {
    override val name = "patch"
    override val description =
        "Apply a unified diff patch to a file in the workspace. " +
        "Specify the target relative file path and the patch content (standard unified diff format)."
    override val readOnly = false
    override val defaultPermission = ToolPermission.ASK
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "path" to mapOf("type" to "string", "description" to "Relative path to the file to patch"),
            "patch" to mapOf("type" to "string", "description" to "Unified diff patch content")
        ),
        "required" to listOf("path", "patch")
    )

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult {
        val args = try {
            Json.parseToJsonElement(arguments).jsonObject
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments JSON: ${e.message}")
        }

        val path = args["path"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing required parameter: path")
        val patchText = args["patch"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing required parameter: patch")

        if (!workspace.exists(path)) {
            return ToolResult.error("File does not exist: $path")
        }

        val originalContent = try {
            workspace.readText(path)
        } catch (e: Exception) {
            return ToolResult.error("Failed to read file '$path': ${e.message}")
        }

        val originalLines = originalContent.lines().toMutableList()
        val patchLines = patchText.lines()

        var currentLineIndex = 0
        var patchApplied = false

        val newLines = mutableListOf<String>()
        var patchIdx = 0

        while (patchIdx < patchLines.size) {
            val line = patchLines[patchIdx]
            if (line.startsWith("@@")) {
                patchIdx++
                continue
            }
            if (line.startsWith("---") || line.startsWith("+++")) {
                patchIdx++
                continue
            }

            if (line.startsWith("-")) {
                val toRemove = line.substring(1)
                if (currentLineIndex < originalLines.size && originalLines[currentLineIndex] == toRemove) {
                    currentLineIndex++
                    patchApplied = true
                } else {
                    val matchIdx = originalLines.indexOfFirst { it == toRemove }
                    if (matchIdx != -1) {
                        originalLines.removeAt(matchIdx)
                        patchApplied = true
                    }
                }
            } else if (line.startsWith("+")) {
                val toAdd = line.substring(1)
                newLines.add(toAdd)
                patchApplied = true
            } else if (line.startsWith(" ")) {
                val context = line.substring(1)
                if (currentLineIndex < originalLines.size && originalLines[currentLineIndex] == context) {
                    newLines.add(originalLines[currentLineIndex])
                    currentLineIndex++
                } else {
                    newLines.add(context)
                }
            }
            patchIdx++
        }

        while (currentLineIndex < originalLines.size) {
            newLines.add(originalLines[currentLineIndex])
            currentLineIndex++
        }

        val updatedContent = newLines.joinToString("\n")
        return try {
            workspace.writeText(path, updatedContent)
            ToolResult.success("Patch applied successfully to $path", diff = patchText)
        } catch (e: Exception) {
            ToolResult.error("Failed to save patched content to '$path': ${e.message}")
        }
    }
}
