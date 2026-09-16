package dev.forge.agent.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Streams chat completions from OpenAI-compatible endpoints.
 *
 * Also works with OpenRouter, Together, Groq, LM Studio, and any
 * service that exposes /chat/completions with SSE streaming.
 */
class OpenAiProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1"
) : LlmProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun streamChat(
        messages: List<LlmMessage>,
        tools: List<LlmToolDefinition>,
        model: String,
        temperature: Double,
        maxTokens: Int
    ): Flow<StreamEvent> = flow {
        val bodyJson = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            putJsonArray("messages") {
                messages.forEach { add(messageToJson(it)) }
            }
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { add(toolDefinitionToJson(it)) }
                }
            }
        }

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errText = response.body?.string()?.take(500) ?: "Empty error"
            emit(StreamEvent.Error("HTTP ${response.code}: $errText"))
            return@flow
        }

        val source = response.body?.source() ?: run {
            emit(StreamEvent.Error("Empty response body"))
            return@flow
        }

        val toolCallBuffers = mutableMapOf<Int, Pair<String, StringBuilder>>()

        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: continue
            if (line.isBlank() || !line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]") {
                emit(StreamEvent.Done("stop"))
                break
            }

            val root = try { json.parseToJsonElement(payload).jsonObject } catch (_: Exception) { continue }
            val choices = root["choices"]?.jsonArray ?: continue
            val choice = choices.firstOrNull()?.jsonObject ?: continue
            val delta = choice["delta"]?.jsonObject ?: continue

            delta["content"]?.jsonPrimitive?.contentOrNull?.let { text ->
                if (text.isNotEmpty()) emit(StreamEvent.TextDelta(text))
            }

            delta["tool_calls"]?.jsonArray?.forEach { tcElement ->
                val tc = tcElement.jsonObject
                val idx = tc["index"]?.jsonPrimitive?.intOrNull ?: 0
                val id = tc["id"]?.jsonPrimitive?.contentOrNull
                val name = tc["function"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                val argsDelta = tc["function"]?.jsonObject?.get("arguments")?.jsonPrimitive?.contentOrNull

                if (id != null && name != null) {
                    toolCallBuffers[idx] = id to StringBuilder()
                    emit(StreamEvent.ToolCallStart(id, name, idx))
                }
                if (argsDelta != null) {
                    val buf = toolCallBuffers[idx]?.second
                    buf?.append(argsDelta)
                    val callId = toolCallBuffers[idx]?.first ?: ""
                    emit(StreamEvent.ToolCallDelta(callId, argsDelta, idx))
                }
            }

            root["usage"]?.jsonObject?.let { usage ->
                val p = usage["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                val c = usage["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                val t = usage["total_tokens"]?.jsonPrimitive?.intOrNull ?: (p + c)
                if (t > 0) emit(StreamEvent.Usage(p, c, t))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun messageToJson(msg: LlmMessage): JsonObject = buildJsonObject {
        put("role", when (msg.role) {
            LlmMessage.Role.SYSTEM -> "system"
            LlmMessage.Role.USER -> "user"
            LlmMessage.Role.ASSISTANT -> "assistant"
            LlmMessage.Role.TOOL -> "tool"
        })
        put("content", msg.content)
        msg.toolCallId?.let { put("tool_call_id", it) }
        msg.toolCalls?.let { calls ->
            putJsonArray("tool_calls") {
                calls.forEach { tc ->
                    addJsonObject {
                        put("id", tc.id)
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", tc.name)
                            put("arguments", tc.arguments)
                        }
                    }
                }
            }
        }
    }

    private fun toolDefinitionToJson(tool: LlmToolDefinition): JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", tool.name)
            put("description", tool.description)
            put("parameters", tool.parameters.toJsonElement())
        }
    }
}
