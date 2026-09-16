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
 * Streams chat completions from Google Gemini's generateContent API.
 */
class GeminiProvider(
    private val apiKey: String
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
            if (systemText.isNotBlank()) {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") { addJsonObject { put("text", systemText) } }
                }
            }
            putJsonArray("contents") {
                nonSystem.forEach { add(geminiContent(it)) }
            }
            putJsonObject("generationConfig") {
                put("temperature", temperature)
                put("maxOutputTokens", maxTokens)
            }
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    addJsonObject {
                        putJsonArray("functionDeclarations") {
                            tools.forEach { t ->
                                addJsonObject {
                                    put("name", t.name)
                                    put("description", t.description)
                                    put("parameters", sanitizeGeminiSchema(t.parameters.toJsonElement()))
                                }
                            }
                        }
                    }
                }
            }
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
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

        var toolIndex = -1

        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: continue
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload.isEmpty()) continue

            val root = try { json.parseToJsonElement(payload).jsonObject } catch (_: Exception) { continue }
            val candidates = root["candidates"]?.jsonArray ?: continue
            val cand = candidates.firstOrNull()?.jsonObject ?: continue
            val parts = cand["content"]?.jsonObject?.get("parts")?.jsonArray ?: continue

            for (part in parts) {
                val obj = part.jsonObject
                obj["text"]?.jsonPrimitive?.contentOrNull?.let { text ->
                    if (text.isNotEmpty()) emit(StreamEvent.TextDelta(text))
                }
                obj["functionCall"]?.jsonObject?.let { fc ->
                    toolIndex++
                    val name = fc["name"]?.jsonPrimitive?.contentOrNull ?: ""
                    val args = fc["args"]?.toString() ?: "{}"
                    val id = "gemini_$toolIndex"
                    emit(StreamEvent.ToolCallStart(id, name, toolIndex))
                    emit(StreamEvent.ToolCallDelta(id, args, toolIndex))
                }
            }

            cand["finishReason"]?.jsonPrimitive?.contentOrNull?.let { reason ->
                if (reason.isNotEmpty() && reason != "STOP") {
                    // Non-stop reasons are informational
                }
            }
        }

        emit(StreamEvent.Done("stop"))
    }.flowOn(Dispatchers.IO)

    private fun geminiContent(msg: LlmMessage): JsonObject = buildJsonObject {
        put("role", if (msg.role == LlmMessage.Role.ASSISTANT) "model" else "user")
        putJsonArray("parts") {
            if (msg.role == LlmMessage.Role.TOOL) {
                addJsonObject {
                    putJsonObject("functionResponse") {
                        put("name", msg.toolCallId ?: "tool")
                        putJsonObject("response") { put("result", msg.content) }
                    }
                }
            } else if (msg.toolCalls != null) {
                if (msg.content.isNotBlank()) addJsonObject { put("text", msg.content) }
                msg.toolCalls.forEach { tc ->
                    addJsonObject {
                        putJsonObject("functionCall") {
                            put("name", tc.name)
                            put("args", try { Json.parseToJsonElement(tc.arguments) } catch (_: Exception) { buildJsonObject { } })
                        }
                    }
                }
            } else {
                addJsonObject { put("text", msg.content) }
            }
        }
    }

    /**
     * Gemini's schema dialect rejects some JSON Schema keywords.
     * Recursively strip unknown fields before sending.
     */
    private fun sanitizeGeminiSchema(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> buildJsonObject {
            el.forEach { (k, v) ->
                when (k) {
                    "type", "format", "description", "nullable", "enum",
                    "properties", "required", "items", "minimum", "maximum",
                    "minItems", "maxItems", "minLength", "maxLength", "pattern" -> {
                        if (k == "properties" && v is JsonObject) {
                            put(k, buildJsonObject {
                                v.forEach { (pk, pv) -> put(pk, sanitizeGeminiSchema(pv)) }
                            })
                        } else if (k == "items") {
                            put(k, sanitizeGeminiSchema(v))
                        } else {
                            put(k, v)
                        }
                    }
                }
            }
        }
        is JsonArray -> buildJsonArray { el.forEach { add(sanitizeGeminiSchema(it)) } }
        else -> el
    }
}
