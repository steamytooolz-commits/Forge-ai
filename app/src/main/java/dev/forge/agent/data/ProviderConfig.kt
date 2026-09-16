package dev.forge.agent.data

import kotlinx.serialization.Serializable

@Serializable
data class ProviderConfig(
    val kind: Kind,
    val apiKey: String = "",
    val baseUrl: String? = null
) {
    @Serializable
    enum class Kind(val displayName: String) {
        OPENAI("OpenAI"),
        ANTHROPIC("Anthropic"),
        GEMINI("Google Gemini"),
        OLLAMA("Ollama (Local)"),
        OPENROUTER("OpenRouter"),
        DEEPSEEK("DeepSeek"),
        GROQ("Groq"),
        CUSTOM("Custom Endpoint")
    }
}

@Serializable
data class ModelInfo(
    val id: String,
    val displayName: String,
    val providerKind: ProviderConfig.Kind,
    val contextWindow: Int = 128_000,
    val maxOutputTokens: Int = 8192,
    val supportsReasoning: Boolean = false
)

object CuratedModels {
    val all = listOf(
        ModelInfo("gemini-2.5-flash", "Gemini 2.5 Flash", ProviderConfig.Kind.GEMINI, 1_000_000, 8192, true),
        ModelInfo("gemini-2.5-pro", "Gemini 2.5 Pro", ProviderConfig.Kind.GEMINI, 2_000_000, 8192, true),
        ModelInfo("gpt-4o", "GPT-4o", ProviderConfig.Kind.OPENAI, 128_000, 4096),
        ModelInfo("gpt-4o-mini", "GPT-4o Mini", ProviderConfig.Kind.OPENAI, 128_000, 4096),
        ModelInfo("o1", "o1 (Reasoning)", ProviderConfig.Kind.OPENAI, 200_000, 32768, true),
        ModelInfo("o3-mini", "o3 Mini", ProviderConfig.Kind.OPENAI, 200_000, 65536, true),
        ModelInfo("claude-3-7-sonnet-20250219", "Claude 3.7 Sonnet", ProviderConfig.Kind.ANTHROPIC, 200_000, 8192, true),
        ModelInfo("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet", ProviderConfig.Kind.ANTHROPIC, 200_000, 8192),
        ModelInfo("claude-3-5-haiku-20241022", "Claude 3.5 Haiku", ProviderConfig.Kind.ANTHROPIC, 200_000, 8192),
        ModelInfo("deepseek-chat", "DeepSeek V3", ProviderConfig.Kind.DEEPSEEK, 64_000, 4096),
        ModelInfo("deepseek-reasoner", "DeepSeek R1", ProviderConfig.Kind.DEEPSEEK, 64_000, 8192, true),
        ModelInfo("llama-3.3-70b-versatile", "Groq: Llama 3.3 70B", ProviderConfig.Kind.GROQ, 128_000, 8192),
        ModelInfo("anthropic/claude-3.5-sonnet", "OpenRouter: Claude 3.5 Sonnet", ProviderConfig.Kind.OPENROUTER, 200_000, 8192),
        ModelInfo("llama3.1:8b", "Ollama: Llama 3.1 8B", ProviderConfig.Kind.OLLAMA, 128_000, 4096),
        ModelInfo("qwen2.5-coder:7b", "Ollama: Qwen 2.5 Coder 7B", ProviderConfig.Kind.OLLAMA, 32_000, 4096)
    )
}
