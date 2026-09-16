package dev.forge.agent.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.forge.agent.core.AgentEvent
import dev.forge.agent.core.ChatMessage
import dev.forge.agent.core.CustomCommand
import dev.forge.agent.core.SerializedToolCall
import dev.forge.agent.core.SessionInfo
import dev.forge.agent.core.SessionManager
import dev.forge.agent.data.CommandRepository
import dev.forge.agent.data.SettingsRepository
import dev.forge.agent.service.AgentBus
import dev.forge.agent.service.AgentService
import dev.forge.agent.tools.QuestionBridge
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

object TerminalLogBus {
    val logs = MutableStateFlow<List<String>>(listOf("[Forge Terminal Initialized]"))
    fun append(log: String) {
        logs.value = (logs.value + log).takeLast(1000)
    }
    fun clear() {
        logs.value = listOf("[Terminal Cleared]")
    }
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsRepository(application)
    private val sessionsDir = File(application.filesDir, "sessions")
    private val sessionManager = SessionManager(sessionsDir)
    private val workspace = File(application.filesDir, "workspace").apply { mkdirs() }
    private val commandRepo = CommandRepository(workspace)

    private val _currentSessionId = MutableStateFlow("")
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _streamingReasoning = MutableStateFlow("")
    val streamingReasoning: StateFlow<String> = _streamingReasoning.asStateFlow()

    private val _streamingToolCall = MutableStateFlow<SerializedToolCall?>(null)
    val streamingToolCall: StateFlow<SerializedToolCall?> = _streamingToolCall.asStateFlow()

    private val _availableCommands = MutableStateFlow<List<CustomCommand>>(emptyList())
    val availableCommands: StateFlow<List<CustomCommand>> = _availableCommands.asStateFlow()

    private val _pendingQuestion = MutableStateFlow<String?>(null)
    val pendingQuestion: StateFlow<String?> = _pendingQuestion.asStateFlow()

    private val _pendingPermission = MutableStateFlow<AgentEvent.PermissionRequested?>(null)
    val pendingPermission: StateFlow<AgentEvent.PermissionRequested?> = _pendingPermission.asStateFlow()

    val isRunning: StateFlow<Boolean> = AgentBus.isRunning
    val activeToolName: StateFlow<String?> = AgentBus.activeToolName

    private val _tokenUsage = MutableStateFlow<String?>(null)
    val tokenUsage: StateFlow<String?> = _tokenUsage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val selectedModel: String
        get() = settings.getSelectedModel()

    val planMode: Boolean
        get() = settings.getPlanMode()

    init {
        // Load commands
        _availableCommands.value = commandRepo.loadCommands()

        // Initialize or restore latest session
        val existing = sessionManager.listSessions()
        if (existing.isNotEmpty()) {
            loadSession(existing.first().id)
        } else {
            newSession()
        }

        // Collect events from AgentBus
        viewModelScope.launch {
            AgentBus.events.collect { event ->
                handleAgentEvent(event)
            }
        }
    }

    fun newSession() {
        val id = sessionManager.createSession(
            title = "Session " + (sessionManager.listSessions().size + 1),
            model = settings.getSelectedModel()
        )
        sessionManager.ensureSystemPrompt(id, settings.getSystemPrompt())
        loadSession(id)
    }

    fun loadSession(sessionId: String) {
        _currentSessionId.value = sessionId
        _streamingText.value = ""
        _streamingReasoning.value = ""
        _streamingToolCall.value = null
        _errorMessage.value = null
        _tokenUsage.value = null
        refreshMessages()
    }

    fun refreshMessages() {
        val id = _currentSessionId.value
        if (id.isNotBlank()) {
            _messages.value = sessionManager.load(id).filter { it !is ChatMessage.System }
        }
    }

    fun listSessions(): List<SessionInfo> {
        return sessionManager.listSessions()
    }

    fun deleteSession(id: String) {
        sessionManager.deleteSession(id)
        if (_currentSessionId.value == id) {
            val remaining = sessionManager.listSessions()
            if (remaining.isNotEmpty()) {
                loadSession(remaining.first().id)
            } else {
                newSession()
            }
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val sessionId = _currentSessionId.value
        if (sessionId.isBlank()) return

        // Check for local slash commands
        if (trimmed == "/clear") {
            newSession()
            return
        }

        val userMsg = ChatMessage.User(
            id = UUID.randomUUID().toString(),
            content = trimmed
        )
        sessionManager.append(sessionId, userMsg)
        refreshMessages()

        _streamingText.value = ""
        _streamingReasoning.value = ""
        _streamingToolCall.value = null
        _errorMessage.value = null

        AgentService.start(getApplication(), sessionId)
    }

    fun answerQuestion(answer: String) {
        QuestionBridge.answerQuestion(answer)
        _pendingQuestion.value = null
    }

    fun cancelQuestion() {
        QuestionBridge.cancel()
        _pendingQuestion.value = null
    }

    fun allowPermission() {
        _pendingPermission.value = null
    }

    fun denyPermission() {
        _pendingPermission.value = null
    }

    fun stop() {
        AgentService.stop(getApplication())
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun handleAgentEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.AssistantTextDelta -> {
                _streamingText.value += event.delta
            }
            is AgentEvent.ReasoningDelta -> {
                _streamingReasoning.value += event.text
            }
            is AgentEvent.AssistantToolCallStart -> {
                _streamingToolCall.value = SerializedToolCall(event.id, event.name, "")
            }
            is AgentEvent.AssistantToolCallArgs -> {
                val current = _streamingToolCall.value
                if (current != null && current.id == event.id) {
                    _streamingToolCall.value = current.copy(arguments = current.arguments + event.argsDelta)
                }
            }
            is AgentEvent.AssistantMessageCompleted -> {
                _streamingText.value = ""
                _streamingReasoning.value = ""
                _streamingToolCall.value = null
                refreshMessages()
            }
            is AgentEvent.ToolExecutionStarted -> {
                TerminalLogBus.append("$ ${event.toolName} ${event.arguments.take(120)}")
            }
            is AgentEvent.ToolExecutionFinished -> {
                TerminalLogBus.append("-> ${if (event.isError) "ERROR: " else ""}${event.result.take(200)}")
                refreshMessages()
            }
            is AgentEvent.UsageReported -> {
                _tokenUsage.value = "Tokens: ${event.totalTokens} (prompt ${event.promptTokens}, completion ${event.completionTokens})"
            }
            is AgentEvent.QuestionAsked -> {
                _pendingQuestion.value = event.question
            }
            is AgentEvent.PermissionRequested -> {
                _pendingPermission.value = event
            }
            is AgentEvent.ErrorRaised -> {
                _errorMessage.value = event.message
            }
            is AgentEvent.LoopCompleted, is AgentEvent.Cancelled -> {
                _streamingText.value = ""
                _streamingReasoning.value = ""
                _streamingToolCall.value = null
                refreshMessages()
            }
            else -> {}
        }
    }
}
