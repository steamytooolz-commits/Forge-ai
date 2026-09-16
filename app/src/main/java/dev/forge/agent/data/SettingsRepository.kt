package dev.forge.agent.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.forge.agent.core.DefaultPrompts
import java.io.File

class SettingsRepository(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "forge_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            // Fallback to standard prefs if key generation fails on older/unsupported emulators
            context.getSharedPreferences("forge_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    fun getApiKey(kind: ProviderConfig.Kind): String {
        return prefs.getString("key_${kind.name}", "") ?: ""
    }

    fun setApiKey(kind: ProviderConfig.Kind, key: String) {
        prefs.edit().putString("key_${kind.name}", key).apply()
    }

    fun getBaseUrl(kind: ProviderConfig.Kind): String {
        val default = when (kind) {
            ProviderConfig.Kind.OPENAI -> "https://api.openai.com/v1"
            ProviderConfig.Kind.ANTHROPIC -> "https://api.anthropic.com/v1"
            ProviderConfig.Kind.GEMINI -> ""
            ProviderConfig.Kind.OLLAMA -> "http://localhost:11434"
            ProviderConfig.Kind.OPENROUTER -> "https://openrouter.ai/api/v1"
            ProviderConfig.Kind.DEEPSEEK -> "https://api.deepseek.com/v1"
            ProviderConfig.Kind.GROQ -> "https://api.groq.com/openai/v1"
            ProviderConfig.Kind.CUSTOM -> ""
        }
        return prefs.getString("url_${kind.name}", default) ?: default
    }

    fun setBaseUrl(kind: ProviderConfig.Kind, url: String) {
        prefs.edit().putString("url_${kind.name}", url).apply()
    }

    fun getSelectedModel(): String {
        return prefs.getString("selected_model", "gemini-2.5-flash") ?: "gemini-2.5-flash"
    }

    fun setSelectedModel(modelId: String) {
        prefs.edit().putString("selected_model", modelId).apply()
    }

    fun getPlanMode(): Boolean {
        return prefs.getBoolean("plan_mode", false)
    }

    fun setPlanMode(enabled: Boolean) {
        prefs.edit().putBoolean("plan_mode", enabled).apply()
    }

    fun getSystemPrompt(): String {
        return prefs.getString("system_prompt", DefaultPrompts.DEFAULT_SYSTEM_PROMPT)
            ?: DefaultPrompts.DEFAULT_SYSTEM_PROMPT
    }

    fun setSystemPrompt(prompt: String) {
        prefs.edit().putString("system_prompt", prompt).apply()
    }

    fun getWorkspaceDir(): File {
        val path = prefs.getString("workspace_dir", null)
        val dir = if (path != null) File(path) else File(context.filesDir, "workspace")
        dir.mkdirs()
        return dir
    }

    fun setWorkspaceDir(dir: File) {
        prefs.edit().putString("workspace_dir", dir.absolutePath).apply()
    }

    fun getWorkspaceUri(): String? {
        return prefs.getString("workspace_uri", null)
    }

    fun setWorkspaceUri(uriString: String?) {
        if (uriString == null) {
            prefs.edit().remove("workspace_uri").apply()
        } else {
            prefs.edit().putString("workspace_uri", uriString).apply()
        }
    }

    fun getTemperature(): Float {
        return prefs.getFloat("temperature", 0.7f)
    }

    fun setTemperature(value: Float) {
        prefs.edit().putFloat("temperature", value).apply()
    }

    fun getMaxTokens(): Int {
        return prefs.getInt("max_tokens", 8192)
    }

    fun setMaxTokens(value: Int) {
        prefs.edit().putInt("max_tokens", value).apply()
    }

    fun getProviderConfig(kind: ProviderConfig.Kind): ProviderConfig {
        return ProviderConfig(
            kind = kind,
            apiKey = getApiKey(kind),
            baseUrl = getBaseUrl(kind).ifBlank { null }
        )
    }
}
