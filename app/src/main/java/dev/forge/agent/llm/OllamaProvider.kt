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
 * Streams chat completions from a local Ollama server.
 * Default endpoint is http://localhost:11434.
 */
class OllamaProvider(
    private val baseUrl: String = "http://localhost:11434"
) : LlmProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
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
            putJsonObject("options") {
                put("temperature", temperature)
                put("num_predict", maxTokens)
            }
            putJsonArray("messages") {
                messages.forEach { m ->
                    addJsonObject {
                        put("role", when (m.role) {
                            LlmMessage.Role.SYSTEM -> "system"
                            LlmMessage.Role.USER -> "user"
                            LlmMessage.Role.ASSISTANT -> "assistant"
                            LlmMessage.Role.TOOL -> "tool"
                        })
                        put("content", m.content)
                        m.toolCalls?.let { calls ->
                            putJsonArray("tool_calls") {
                                calls.forEach { tc ->
                                    addJsonObject {
                                        putJsonObject("function") {
                                            put("name", tc.name)
                                            put("arguments", try { Json.parseToJsonElement(tc.arguments) } catch (_: Exception) { buildJsonObject { } })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { t ->
                        addJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", t.name)
                                put("description", t.description)
                                put("parameters", t.parameters.toJsonElement())
                            }
                        }
                    }
                }
            }
        }

        val request = Request.Builder()
            .url("$baseUrl/api/chat")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            emit(StreamEvent.Error("Ollama HTTP ${response.code}"))
            return@flow
        }

        val source = response.body?.source() ?: run {
            emit(StreamEvent.Error("Empty body"))
            return@flow
        }

        var toolIdx = -1

        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: continue
            if (line.isBlank()) continue

            val obj = try { json.parseToJsonElement(line).jsonObject } catch (_: Exception) { continue }
            val msg = obj["message"]?.jsonObject
            msg?.get("content")?.jsonPrimitive?.contentOrNull?.let { text ->
                if (text.isNotEmpty()) emit(StreamEvent.TextDelta(text))
            }
            msg?.get("tool_calls")?.jsonArray?.forEach { tc ->
                toolIdx++
                val fn = tc.jsonObject["function"]?.jsonObject
                val name = fn?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
                val args = fn?.get("arguments")?.toString() ?: "{}"
                val id = "ollama_$toolIdx"
                emit(StreamEvent.ToolCallStart(id, name, toolIdx))
                emit(StreamEvent.ToolCallDelta(id, args, toolIdx))
            }
            if (obj["done"]?.jsonPrimitive?.booleanOrNull == true) {
                emit(StreamEvent.Done("stop"))
                break
            }
        }
    }.flowOn(Dispatchers.IO)
}
