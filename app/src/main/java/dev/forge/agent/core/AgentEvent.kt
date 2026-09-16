package dev.forge.agent.core

import dev.forge.agent.tools.ToolPermission

sealed class AgentEvent {
    data class UserMessageAdded(val message: ChatMessage.User) : AgentEvent()
    data class AssistantTextDelta(val delta: String) : AgentEvent()
    data class ReasoningDelta(val text: String) : AgentEvent()
    data class AssistantToolCallStart(val id: String, val name: String, val index: Int) : AgentEvent()
    data class AssistantToolCallArgs(val id: String, val argsDelta: String, val index: Int) : AgentEvent()
    data class AssistantMessageCompleted(val message: ChatMessage.Assistant) : AgentEvent()
    data class ToolExecutionStarted(val callId: String, val toolName: String, val arguments: String) : AgentEvent()
    data class ToolExecutionFinished(val callId: String, val toolName: String, val result: String, val isError: Boolean) : AgentEvent()
    data class UsageReported(val promptTokens: Int, val completionTokens: Int, val totalTokens: Int) : AgentEvent()
    data class ErrorRaised(val message: String, val fatal: Boolean = false) : AgentEvent()
    data class LoopCompleted(val sessionId: String, val iterations: Int) : AgentEvent()
    data class QuestionAsked(val question: String) : AgentEvent()
    data class PermissionRequested(val toolName: String, val arguments: String, val permission: ToolPermission) : AgentEvent()
    object Cancelled : AgentEvent()
}
