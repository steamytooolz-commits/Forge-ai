package dev.forge.agent.tools

import dev.forge.agent.data.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class WebFetchTool : AgentTool {
    override val name = "webfetch"
    override val description =
        "Fetch the text content of a web page (HTML converted to clean text) or JSON API. " +
        "Useful for reading documentation, checking APIs, or downloading references. Up to max_chars returned."
    override val readOnly = true
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "url" to mapOf("type" to "string", "description" to "The full URL to fetch (http/https)"),
            "max_chars" to mapOf("type" to "integer", "description" to "Maximum characters to return", "default" to 20000)
        ),
        "required" to listOf("url")
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult = withContext(Dispatchers.IO) {
        val args = try {
            Json.parseToJsonElement(arguments).jsonObject
        } catch (e: Exception) {
            return@withContext ToolResult.error("Invalid JSON: ${e.message}")
        }

        val url = args["url"]?.jsonPrimitive?.contentOrNull
            ?: return@withContext ToolResult.error("Missing required parameter: url")
        val maxChars = args["max_chars"]?.jsonPrimitive?.intOrNull ?: 20_000

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; Forge-Agent/1.0)")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext ToolResult.error("HTTP ${response.code}: ${response.message}")
            }
            val raw = response.body?.string() ?: return@withContext ToolResult.error("Empty response body")
            val text = stripHtml(raw).take(maxChars)
            ToolResult.success(text)
        } catch (e: Exception) {
            ToolResult.error("Fetch failed: ${e.message}")
        }
    }

    private fun stripHtml(html: String): String =
        html.replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
}
