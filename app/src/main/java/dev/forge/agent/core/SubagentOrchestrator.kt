package dev.forge.agent.core

import dev.forge.agent.data.Workspace
import dev.forge.agent.llm.LlmProvider
import dev.forge.agent.tools.ToolRegistry
import dev.forge.agent.tools.ToolResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class SubagentOrchestrator(
    private val provider: LlmProvider,
    private val model: String,
    private val baseTools: ToolRegistry,
    private val sessionManager: SessionManager,
    private val scope: CoroutineScope
) {
    private val backgroundTasks = ConcurrentHashMap<String, String>()

    suspend fun spawnSubagent(
        description: String,
        category: String,
        runInBackground: Boolean,
        workspace: Workspace
    ): ToolResult {
        val taskId = "subagent_${System.currentTimeMillis()}"
        val subagentSessionId = sessionManager.createSession(
            title = "Subagent: $description",
            model = model
        )

        val subagentTools = when (category.lowercase()) {
            "explore", "plan" -> baseTools.readOnly()
            else -> baseTools
        }

        if (runInBackground) {
            backgroundTasks[taskId] = "Running task: $description"
            scope.launch(Dispatchers.IO) {
                try {
                    val loop = AgentLoop(
                        provider = provider,
                        model = model,
                        tools = subagentTools,
                        workspace = workspace,
                        sessionManager = sessionManager,
                        sessionId = subagentSessionId,
                        maxIterations = 10,
                        planMode = (category == "explore" || category == "plan"),
                        onEvent = {}
                    )
                    sessionManager.append(
                        subagentSessionId,
                        ChatMessage.User(
                            id = java.util.UUID.randomUUID().toString(),
                            content = description
                        )
                    )
                    loop.run()
                    backgroundTasks[taskId] = "Completed: $description"
                } catch (e: Exception) {
                    backgroundTasks[taskId] = "Failed: ${e.message}"
                }
            }
            return ToolResult.success("Background subagent task spawned with ID: $taskId. Use 'background_output' with task ID to retrieve results.")
        } else {
            sessionManager.append(
                subagentSessionId,
                ChatMessage.User(
                    id = java.util.UUID.randomUUID().toString(),
                    content = description
                )
            )
            val loop = AgentLoop(
                provider = provider,
                model = model,
                tools = subagentTools,
                workspace = workspace,
                sessionManager = sessionManager,
                sessionId = subagentSessionId,
                maxIterations = 8,
                planMode = (category == "explore" || category == "plan"),
                onEvent = {}
            )
            loop.run()
            val history = sessionManager.load(subagentSessionId)
            val lastAssistant = history.filterIsInstance<ChatMessage.Assistant>().lastOrNull()?.content
                ?: "Subagent completed task without text output."
            return ToolResult.success("Subagent result:\n$lastAssistant")
        }
    }

    fun getBackgroundOutput(taskId: String): String {
        return backgroundTasks[taskId] ?: "Task $taskId not found."
    }

    fun cancelBackgroundTask(taskId: String): Boolean {
        return if (backgroundTasks.containsKey(taskId)) {
            backgroundTasks[taskId] = "Cancelled by user"
            true
        } else false
    }
}
