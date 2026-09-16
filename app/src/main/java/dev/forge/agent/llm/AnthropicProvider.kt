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
 * Streams chat completions from Anthropic's Messages API.
 */
class AnthropicProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://api.anthropic.com/v1"
) : LlmProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
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
        val systemText = messages.filter { it.role == LlmMessage.Role.SYSTEM }
            .joinToString("\n\n") { it.content }
        val nonSystem = messages.filter { it.role != LlmMessage.Role.SYSTEM }

        val bodyJson = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            if (systemText.isNotBlank()) put("system", systemText)
            putJsonArray("messages") {
                nonSystem.forEach { add(anthropicMessage(it)) }
            }
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { t ->
                        addJsonObject {
                            put("name", t.name)
                            put("description", t.description)
                            put("input_schema", t.parameters.toJsonElement())
                        }
                    }
                }
            }
        }

        val request = Request.Builder()
            .url("$baseUrl/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
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

        var currentToolIndex = -1
        var currentToolId = ""
        var currentToolName = ""

        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: continue
            if (line.isBlank()) continue

            if (line.startsWith("event:")) continue

            if (line.startsWith("data:")) {
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty()) continue

                val event = try { json.parseToJsonElement(payload).jsonObject } catch (_: Exception) { continue }
                val type = event["type"]?.jsonPrimitive?.contentOrNull ?: continue

                when (type) {
                    "content_block_start" -> {
                        val block = event["content_block"]?.jsonObject
                        val blockType = block?.get("type")?.jsonPrimitive?.contentOrNull
                        if (blockType == "tool_use") {
                            currentToolIndex++
                            currentToolId = block["id"]?.jsonPrimitive?.contentOrNull ?: ""
                            currentToolName = block["name"]?.jsonPrimitive?.contentOrNull ?: ""
                            emit(StreamEvent.ToolCallStart(currentToolId, currentToolName, currentToolIndex))
                        }
                    }
                    "content_block_delta" -> {
                        val delta = event["delta"]?.jsonObject ?: continue
                        val deltaType = delta["type"]?.jsonPrimitive?.contentOrNull
                        when (deltaType) {
                            "text_delta" -> {
                                val text = delta["text"]?.jsonPrimitive?.contentOrNull ?: ""
                                if (text.isNotEmpty()) emit(StreamEvent.TextDelta(text))
                            }
                            "input_json_delta" -> {
                                val partial = delta["partial_json"]?.jsonPrimitive?.contentOrNull ?: ""
                                emit(StreamEvent.ToolCallDelta(currentToolId, partial, currentToolIndex))
                            }
                        }
                    }
                    "message_delta" -> {
                        val usage = event["usage"]?.jsonObject
                        val out = usage?.get("output_tokens")?.jsonPrimitive?.intOrNull ?: 0
                        // Report usage at end of stream
                        if (out > 0) emit(StreamEvent.Usage(0, out, out))
                    }
                    "message_stop" -> {
                        emit(StreamEvent.Done("stop"))
                        break
                    }
                    "error" -> {
                        val msg = event["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                        emit(StreamEvent.Error(msg ?: "Unknown Anthropic error"))
                        break
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun anthropicMessage(msg: LlmMessage): JsonObject = buildJsonObject {
        put("role", when (msg.role) {
            LlmMessage.Role.USER -> "user"
            LlmMessage.Role.ASSISTANT -> "assistant"
            else -> "user"
        })
        if (msg.role == LlmMessage.Role.TOOL) {
            putJsonArray("content") {
                addJsonObject {
                    put("type", "tool_result")
                    put("tool_use_id", msg.toolCallId ?: "")
                    put("content", msg.content)
                }
            }
        } else if (msg.toolCalls != null) {
            putJsonArray("content") {
                if (msg.content.isNotBlank()) {
                    addJsonObject { put("type", "text"); put("text", msg.content) }
                }
                msg.toolCalls.forEach { tc ->
                    addJsonObject {
                        put("type", "tool_use")
                        put("id", tc.id)
                        put("name", tc.name)
                        put("input", try { Json.parseToJsonElement(tc.arguments) } catch (_: Exception) { buildJsonObject { } })
                    }
                }
            }
        } else {
            put("content", msg.content)
        }
    }
}
