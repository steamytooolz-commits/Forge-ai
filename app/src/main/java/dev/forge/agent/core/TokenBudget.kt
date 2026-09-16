package dev.forge.agent.core

class TokenBudget(
    private val contextWindow: Int = 128_000,
    private val thresholdRatio: Double = 0.80
) {
    /**
     * Roughly estimate token count for a list of messages.
     * Rule of thumb: ~4 characters per token for English text and code.
     */
    fun estimate(messages: List<ChatMessage>): Int {
        val totalChars = messages.sumOf { msg ->
            when (msg) {
                is ChatMessage.System -> msg.content.length
                is ChatMessage.User -> msg.content.length
                is ChatMessage.Assistant -> msg.content.length + msg.toolCalls.sumOf { it.arguments.length + it.name.length }
                is ChatMessage.Tool -> msg.content.length
            }
        }
        return totalChars / 4
    }

    fun usageRatio(messages: List<ChatMessage>): Double =
        estimate(messages).toDouble() / contextWindow

    fun shouldCompress(messages: List<ChatMessage>): Boolean =
        usageRatio(messages) >= thresholdRatio

    fun remaining(messages: List<ChatMessage>): Int =
        (contextWindow - estimate(messages)).coerceAtLeast(0)
}
