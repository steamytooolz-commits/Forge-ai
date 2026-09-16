package dev.forge.agent.core

import dev.forge.agent.llm.LlmMessage
import dev.forge.agent.llm.LlmToolCall
import kotlinx.serialization.Serializable

@Serializable
data class SerializedToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

@Serializable
sealed class ChatMessage {
    abstract val id: String
    abstract val timestamp: Long

    @Serializable
    data class User(
        override val id: String,
        override val timestamp: Long = java.lang.System.currentTimeMillis(),
        val content: String,
        val images: List<String> = emptyList()
    ) : ChatMessage()

    @Serializable
    data class Assistant(
        override val id: String,
        override val timestamp: Long = java.lang.System.currentTimeMillis(),
        val content: String,
        val toolCalls: List<SerializedToolCall> = emptyList(),
        val finishReason: String? = null
    ) : ChatMessage()

    @Serializable
    data class Tool(
        override val id: String,
        override val timestamp: Long = java.lang.System.currentTimeMillis(),
        val toolCallId: String,
        val toolName: String,
        val content: String,
        val isError: Boolean = false
    ) : ChatMessage()

    @Serializable
    data class System(
        override val id: String,
        override val timestamp: Long = java.lang.System.currentTimeMillis(),
        val content: String
    ) : ChatMessage()

    fun toLlm(): LlmMessage = when (this) {
        is System -> LlmMessage(LlmMessage.Role.SYSTEM, content)
        is User -> LlmMessage(LlmMessage.Role.USER, content, images = images.ifEmpty { null })
        is Assistant -> LlmMessage(
            LlmMessage.Role.ASSISTANT,
            content,
            toolCalls = toolCalls.map { LlmToolCall(it.id, it.name, it.arguments) }.ifEmpty { null }
        )
        is Tool -> LlmMessage(
            LlmMessage.Role.TOOL,
            content,
            toolCallId = toolCallId
        )
    }
}
