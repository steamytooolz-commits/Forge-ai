package dev.forge.agent.llm

import dev.forge.agent.data.ProviderConfig

object ProviderFactory {
    fun create(config: ProviderConfig): LlmProvider? = when (config.kind) {
        ProviderConfig.Kind.OPENAI -> OpenAiProvider(config.apiKey, config.baseUrl ?: "https://api.openai.com/v1")
        ProviderConfig.Kind.ANTHROPIC -> AnthropicProvider(config.apiKey, config.baseUrl ?: "https://api.anthropic.com/v1")
        ProviderConfig.Kind.GEMINI -> GeminiProvider(config.apiKey)
        ProviderConfig.Kind.OLLAMA -> OllamaProvider(config.baseUrl ?: "http://localhost:11434")
        ProviderConfig.Kind.OPENROUTER -> OpenAiProvider(config.apiKey, config.baseUrl ?: "https://openrouter.ai/api/v1")
        ProviderConfig.Kind.DEEPSEEK -> OpenAiProvider(config.apiKey, config.baseUrl ?: "https://api.deepseek.com/v1")
        ProviderConfig.Kind.GROQ -> OpenAiProvider(config.apiKey, config.baseUrl ?: "https://api.groq.com/openai/v1")
        ProviderConfig.Kind.CUSTOM -> if (config.baseUrl != null) OpenAiProvider(config.apiKey, config.baseUrl) else null
    }
}
