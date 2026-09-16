package dev.forge.agent.tools

import dev.forge.agent.data.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class WebSearchTool : AgentTool {
    override val name = "websearch"
    override val description =
        "Search the web using DuckDuckGo/HTML search for up-to-date documentation, APIs, and libraries."
    override val readOnly = true
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf("type" to "string", "description" to "Search query text")
        ),
        "required" to listOf("query")
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult = withContext(Dispatchers.IO) {
        val args = try { Json.parseToJsonElement(arguments).jsonObject } catch (e: Exception) {
            return@withContext ToolResult.error("Invalid JSON: ${e.message}")
        }
        val query = args["query"]?.jsonPrimitive?.contentOrNull ?: return@withContext ToolResult.error("Missing query")

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://html.duckduckgo.com/html/?q=$encodedQuery")
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0) Gecko/119.0 Firefox/119.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext ToolResult.error("Web search failed with HTTP ${response.code}")
            }

            val html = response.body?.string() ?: ""
            // Extract snippet results
            val regex = Regex("""<a class="result__snippet[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
            val matches = regex.findAll(html).map {
                it.groupValues[1]
                    .replace(Regex("<[^>]*>"), "")
                    .replace("&amp;", "&")
                    .replace("&quot;", "\"")
                    .replace("&#x27;", "'")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .trim()
            }.take(5).toList()

            if (matches.isEmpty()) {
                ToolResult.success("No search results found for query: $query")
            } else {
                val formatted = matches.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n\n")
                ToolResult.success("Search results for '$query':\n\n$formatted")
            }
        } catch (e: Exception) {
            ToolResult.error("Search request error: ${e.message}")
        }
    }
}
