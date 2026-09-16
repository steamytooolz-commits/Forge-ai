package dev.forge.agent.core

import dev.forge.agent.data.FileWorkspace
import dev.forge.agent.data.Workspace
import dev.forge.agent.llm.LlmProvider
import dev.forge.agent.llm.StreamEvent
import dev.forge.agent.tools.ToolPermission
import dev.forge.agent.tools.ToolRegistry
import dev.forge.agent.tools.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.util.UUID

class AgentLoop(
    private val provider: LlmProvider,
    private val model: String,
    private val tools: ToolRegistry,
    private val workspace: Workspace,
    private val sessionManager: SessionManager,
    private val sessionId: String,
    private val maxIterations: Int = 25,
    private val planMode: Boolean = false,
    private val temperature: Double = 0.7,
    private val maxTokens: Int = 8192,
    private val permissionEngine: PermissionEngine = PermissionEngine(),
    private val onEvent: suspend (AgentEvent) -> Unit
) {
    constructor(
        provider: LlmProvider,
        model: String,
        tools: ToolRegistry,
        workspaceFile: File,
        sessionManager: SessionManager,
        sessionId: String,
        maxIterations: Int = 25,
        planMode: Boolean = false,
        temperature: Double = 0.7,
        maxTokens: Int = 8192,
        permissionEngine: PermissionEngine = PermissionEngine(),
        onEvent: suspend (AgentEvent) -> Unit
    ) : this(
        provider = provider,
        model = model,
        tools = tools,
        workspace = FileWorkspace(workspaceFile),
        sessionManager = sessionManager,
        sessionId = sessionId,
        maxIterations = maxIterations,
        planMode = planMode,
        temperature = temperature,
        maxTokens = maxTokens,
        permissionEngine = permissionEngine,
        onEvent = onEvent
    )

    suspend fun run() {
        var iterations = 0
        try {
            while (iterations < maxIterations) {
                currentCoroutineContext().ensureActive()
                iterations++

                val history = sessionManager.load(sessionId)
                val llmMessages = history.map { it.toLlm() }
                val toolDefs = tools.getDefinitions(planMode)

                val textBuffer = StringBuilder()
                val toolCalls = mutableMapOf<Int, Triple<String, String, StringBuilder>>() // idx -> (id, name, args)

                var fatalError: String? = null

                provider.streamChat(llmMessages, toolDefs, model).collect { event ->
                    currentCoroutineContext().ensureActive()
                    when (event) {
                        is StreamEvent.TextDelta -> {
                            textBuffer.append(event.text)
                            onEvent(AgentEvent.AssistantTextDelta(event.text))
                        }
                        is StreamEvent.ReasoningDelta -> {
                            onEvent(AgentEvent.ReasoningDelta(event.text))
                        }
                        is StreamEvent.ToolCallStart -> {
                            toolCalls[event.index] = Triple(event.id, event.name, StringBuilder())
                            onEvent(AgentEvent.AssistantToolCallStart(event.id, event.name, event.index))
                        }
                        is StreamEvent.ToolCallDelta -> {
                            val entry = toolCalls[event.index]
                            entry?.third?.append(event.argumentsDelta)
                            onEvent(AgentEvent.AssistantToolCallArgs(event.id, event.argumentsDelta, event.index))
                        }
                        is StreamEvent.Usage -> {
                            onEvent(AgentEvent.UsageReported(event.promptTokens, event.completionTokens, event.totalTokens))
                        }
                        is StreamEvent.Error -> {
                            fatalError = event.message
                            onEvent(AgentEvent.ErrorRaised(event.message, fatal = true))
                        }
                        is StreamEvent.Done -> {}
                    }
                }

                if (fatalError != null) break

                val serializedCalls = toolCalls.entries
                    .sortedBy { it.key }
                    .map { (_, triple) ->
                        SerializedToolCall(
                            id = triple.first,
                            name = triple.second,
                            arguments = triple.third.toString().ifEmpty { "{}" }
                        )
                    }

                val assistantMsg = ChatMessage.Assistant(
                    id = UUID.randomUUID().toString(),
                    content = textBuffer.toString(),
                    toolCalls = serializedCalls
                )
                sessionManager.append(sessionId, assistantMsg)
                onEvent(AgentEvent.AssistantMessageCompleted(assistantMsg))

                if (serializedCalls.isEmpty()) {
                    // Turn finished; assistant answered with text only
                    break
                }

                // Execute tools in sequence
                for (call in serializedCalls) {
                    currentCoroutineContext().ensureActive()
                    onEvent(AgentEvent.ToolExecutionStarted(call.id, call.name, call.arguments))

                    val tool = tools.get(call.name)
                    val permission = permissionEngine.getPermission(call.name)

                    val result = if (permission == ToolPermission.DENY) {
                        ToolResult.error("Tool '${call.name}' execution was denied by security permissions.")
                    } else if (tool == null) {
                        ToolResult.error("Tool '${call.name}' not found in registry")
                    } else if (planMode && !tool.readOnly) {
                        ToolResult.error("Tool '${call.name}' cannot be executed in Plan Mode (read-only mode active)")
                    } else {
                        try {
                            tool.execute(call.arguments, workspace)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            ToolResult.error("Tool '${call.name}' threw exception: ${e.message}")
                        }
                    }

                    onEvent(AgentEvent.ToolExecutionFinished(call.id, call.name, result.output, result.isError))

                    val toolMsg = ChatMessage.Tool(
                        id = UUID.randomUUID().toString(),
                        toolCallId = call.id,
                        toolName = call.name,
                        content = result.output,
                        isError = result.isError
                    )
                    sessionManager.append(sessionId, toolMsg)
                }
            }

            onEvent(AgentEvent.LoopCompleted(sessionId, iterations))
        } catch (e: CancellationException) {
            onEvent(AgentEvent.Cancelled)
        } catch (e: Exception) {
            onEvent(AgentEvent.ErrorRaised(e.message ?: "Unknown agent loop error", fatal = true))
        }
    }
}
