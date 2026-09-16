package dev.forge.agent.tools

import dev.forge.agent.core.SessionManager
import dev.forge.agent.data.FileWorkspace
import dev.forge.agent.data.Workspace
import kotlinx.serialization.json.*
import java.io.File

class SessionListTool(private val sessionManager: SessionManager? = null) : AgentTool {
    override val name = "session_list"
    override val description = "List all persistent agent sessions and conversation histories."
    override val readOnly = true
    override val parameters = mapOf("type" to "object", "properties" to emptyMap<String, Any>())

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult {
        val sessionsDir = if (workspace is FileWorkspace) {
            File(workspace.root.parentFile ?: workspace.root, "sessions")
        } else {
            File("/data/data/dev.forge.agent/files/sessions")
        }
        val manager = sessionManager ?: SessionManager(sessionsDir)
        val sessions = manager.listSessions()
        if (sessions.isEmpty()) {
            return ToolResult.success("No active or archived sessions found.")
        }
        val text = sessions.joinToString("\n") { s ->
            "- ID: ${s.id} | Title: '${s.title}' | Messages: ${s.messageCount} | Created: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(s.createdAt))}"
        }
        return ToolResult.success("Forge Sessions:\n$text")
    }
}

class SessionReadTool(private val sessionManager: SessionManager? = null) : AgentTool {
    override val name = "session_read"
    override val description = "Read the messages and tool history from a specific session."
    override val readOnly = true
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "session_id" to mapOf("type" to "string", "description" to "ID of the session to read")
        ),
        "required" to listOf("session_id")
    )

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult {
        val args = try { Json.parseToJsonElement(arguments).jsonObject } catch (e: Exception) {
            return ToolResult.error("Invalid JSON: ${e.message}")
        }
        val id = args["session_id"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.error("Missing session_id")

        val sessionsDir = if (workspace is FileWorkspace) {
            File(workspace.root.parentFile ?: workspace.root, "sessions")
        } else {
            File("/data/data/dev.forge.agent/files/sessions")
        }
        val manager = sessionManager ?: SessionManager(sessionsDir)
        val messages = manager.load(id)
        if (messages.isEmpty()) {
            return ToolResult.success("Session $id has no messages or does not exist.")
        }
        val formatted = messages.take(30).joinToString("\n\n") { m ->
            when (m) {
                is dev.forge.agent.core.ChatMessage.User -> "USER: ${m.content}"
                is dev.forge.agent.core.ChatMessage.Assistant -> "ASSISTANT: ${m.content} (tools: ${m.toolCalls.size})"
                is dev.forge.agent.core.ChatMessage.Tool -> "TOOL [${m.toolName}]: ${m.content.take(200)}"
                is dev.forge.agent.core.ChatMessage.System -> "SYSTEM: ${m.content}"
            }
        }
        return ToolResult.success("Session $id (${messages.size} messages):\n\n$formatted")
    }
}

class SessionSearchTool(private val sessionManager: SessionManager? = null) : AgentTool {
    override val name = "session_search"
    override val description = "Perform full-text search across all session message histories."
    override val readOnly = true
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf("type" to "string", "description" to "Search query term")
        ),
        "required" to listOf("query")
    )

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult {
        val args = try { Json.parseToJsonElement(arguments).jsonObject } catch (e: Exception) {
            return ToolResult.error("Invalid JSON: ${e.message}")
        }
        val query = args["query"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.error("Missing query")

        val sessionsDir = if (workspace is FileWorkspace) {
            File(workspace.root.parentFile ?: workspace.root, "sessions")
        } else {
            File("/data/data/dev.forge.agent/files/sessions")
        }
        val manager = sessionManager ?: SessionManager(sessionsDir)
        val sessions = manager.listSessions()
        val results = mutableListOf<String>()

        sessions.forEach { s ->
            val msgs = manager.load(s.id)
            msgs.forEachIndexed { idx, m ->
                val content = when (m) {
                    is dev.forge.agent.core.ChatMessage.User -> m.content
                    is dev.forge.agent.core.ChatMessage.Assistant -> m.content
                    is dev.forge.agent.core.ChatMessage.Tool -> m.content
                    is dev.forge.agent.core.ChatMessage.System -> m.content
                }
                if (content.contains(query, ignoreCase = true)) {
                    results.add("Session ${s.id} [#$idx]: ${content.take(150)}")
                }
            }
        }

        return if (results.isEmpty()) {
            ToolResult.success("No session matches found for query: $query")
        } else {
            ToolResult.success("Found ${results.size} matches for '$query':\n" + results.take(20).joinToString("\n"))
        }
    }
}

class SessionInfoTool(private val sessionManager: SessionManager? = null) : AgentTool {
    override val name = "session_info"
    override val description = "Retrieve metadata, total token usage estimates, and statistics for a session."
    override val readOnly = true
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "session_id" to mapOf("type" to "string", "description" to "Session ID")
        ),
        "required" to listOf("session_id")
    )

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult {
        val args = try { Json.parseToJsonElement(arguments).jsonObject } catch (e: Exception) {
            return ToolResult.error("Invalid JSON: ${e.message}")
        }
        val id = args["session_id"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.error("Missing session_id")

        val sessionsDir = if (workspace is FileWorkspace) {
            File(workspace.root.parentFile ?: workspace.root, "sessions")
        } else {
            File("/data/data/dev.forge.agent/files/sessions")
        }
        val manager = sessionManager ?: SessionManager(sessionsDir)
        val msgs = manager.load(id)
        val charCount = msgs.sumOf {
            when (it) {
                is dev.forge.agent.core.ChatMessage.User -> it.content.length
                is dev.forge.agent.core.ChatMessage.Assistant -> it.content.length
                is dev.forge.agent.core.ChatMessage.Tool -> it.content.length
                is dev.forge.agent.core.ChatMessage.System -> it.content.length
            }
        }
        val estTokens = charCount / 4

        return ToolResult.success("Session $id statistics:\n- Total Messages: ${msgs.size}\n- Estimated Characters: $charCount\n- Estimated Tokens: $estTokens")
    }
}
