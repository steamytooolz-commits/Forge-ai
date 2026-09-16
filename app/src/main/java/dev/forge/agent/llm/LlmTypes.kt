package dev.forge.agent.llm

import kotlinx.coroutines.flow.Flow

/**
 * A message in the conversation, normalized across providers.
 */
data class LlmMessage(
    val role: Role,
    val content: String,
    val toolCallId: String? = null,
    val toolCalls: List<LlmToolCall>? = null,
    val images: List<String>? = null // base64 data URLs, optional
) {
    enum class Role { SYSTEM, USER, ASSISTANT, TOOL }
}

/**
 * A tool call emitted by an assistant message.
 */
data class LlmToolCall(
    val id: String,
    val name: String,
    val arguments: String // JSON string
)

/**
 * A tool definition sent to the model so it knows what it can call.
 */
data class LlmToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, Any?>
)

/**
 * A provider-agnostic interface for streaming chat completions.
 */
interface LlmProvider {
    /**
     * Stream a chat completion.
     *
     * @param messages The full conversation history.
     * @param tools Tools the model is allowed to call.
     * @param model The model identifier for this provider.
     * @param temperature Sampling temperature.
     * @param maxTokens Maximum tokens to generate.
     */
    fun streamChat(
        messages: List<LlmMessage>,
        tools: List<LlmToolDefinition>,
        model: String,
        temperature: Double = 0.7,
        maxTokens: Int = 8192
    ): Flow<StreamEvent>
}
