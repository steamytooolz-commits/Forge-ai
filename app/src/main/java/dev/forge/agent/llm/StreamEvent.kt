package dev.forge.agent.llm

/**
 * A single event emitted during a streaming chat completion.
 *
 * Providers translate their wire format into these normalized events,
 * so the agent loop never needs to know which backend is being used.
 */
sealed class StreamEvent {

    /** A chunk of visible text from the assistant. */
    data class TextDelta(val text: String) : StreamEvent()

    /** A chunk of internal reasoning/thinking from reasoning models. */
    data class ReasoningDelta(val text: String) : StreamEvent()

    /** The assistant has started a tool call with a known name and ID. */
    data class ToolCallStart(
        val id: String,
        val name: String,
        val index: Int
    ) : StreamEvent()

    /** A chunk of JSON arguments for an in-progress tool call. */
    data class ToolCallDelta(
        val id: String,
        val argumentsDelta: String,
        val index: Int
    ) : StreamEvent()

    /** The assistant has finished streaming. */
    data class Done(val finishReason: String) : StreamEvent()

    /** The provider returned an error mid-stream. */
    data class Error(val message: String, val cause: Throwable? = null) : StreamEvent()

    /** Token usage stats, emitted at the end when the provider supports it. */
    data class Usage(
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int
    ) : StreamEvent()
}
