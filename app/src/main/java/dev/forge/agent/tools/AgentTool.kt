package dev.forge.agent.tools

import dev.forge.agent.data.FileWorkspace
import dev.forge.agent.data.Workspace
import java.io.File

/**
 * The result of executing a tool.
 */
data class ToolResult(
    val output: String,
    val isError: Boolean = false,
    val exitCode: Int? = null,
    val diff: String? = null
) {
    companion object {
        fun success(output: String, diff: String? = null) = ToolResult(output, false, 0, diff)
        fun error(message: String) = ToolResult(message, true, 1)
    }
}

/**
 * A tool that the agent can invoke.
 */
interface AgentTool {
    val name: String
    val description: String
    val parameters: Map<String, Any?>
    /** True if this tool is safe to run in plan mode. */
    val readOnly: Boolean get() = false
    /** Default security permission for this tool. */
    val defaultPermission: ToolPermission get() = ToolPermission.ALLOW

    suspend fun execute(arguments: String, workspace: Workspace): ToolResult

    suspend fun execute(arguments: String, workspace: File): ToolResult {
        return execute(arguments, FileWorkspace(workspace))
    }
}
