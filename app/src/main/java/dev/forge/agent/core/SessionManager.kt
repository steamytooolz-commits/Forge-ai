package dev.forge.agent.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID

@Serializable
data class SessionHeader(
    val type: String = "header",
    val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val model: String = "",
    val providerKind: String = ""
)

data class SessionInfo(
    val id: String,
    val title: String,
    val createdAt: Long,
    val messageCount: Int,
    val file: File
)

class SessionManager(
    private val sessionsDir: File
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    init {
        sessionsDir.mkdirs()
    }

    fun listSessions(): List<SessionInfo> {
        val files = sessionsDir.listFiles { f -> f.extension == "jsonl" } ?: return emptyList()
        return files.mapNotNull { file ->
            try {
                file.useLines { lines ->
                    val iterator = lines.iterator()
                    if (!iterator.hasNext()) return@useLines null
                    val firstLine = iterator.next()
                    val header = json.decodeFromString<SessionHeader>(firstLine)
                    var count = 0
                    while (iterator.hasNext()) {
                        iterator.next()
                        count++
                    }
                    SessionInfo(header.id, header.title, header.createdAt, count, file)
                }
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { it.createdAt }
    }

    fun createSession(
        title: String = "New Session",
        model: String = "",
        providerKind: String = ""
    ): String {
        val id = UUID.randomUUID().toString().take(8)
        val file = File(sessionsDir, "$id.jsonl")
        val header = SessionHeader(
            id = id,
            title = title,
            createdAt = System.currentTimeMillis(),
            model = model,
            providerKind = providerKind
        )
        file.writeText(json.encodeToString(header) + "\n")
        return id
    }

    fun append(sessionId: String, message: ChatMessage) {
        val file = File(sessionsDir, "$sessionId.jsonl")
        val line = encodeMessage(message)
        file.appendText(line + "\n")
    }

    fun load(sessionId: String): List<ChatMessage> {
        val file = File(sessionsDir, "$sessionId.jsonl")
        if (!file.exists()) return emptyList()

        val messages = mutableListOf<ChatMessage>()
        file.forEachLine { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEachLine
            try {
                val element = json.parseToJsonElement(line).jsonObject
                val type = element["type"]?.jsonPrimitive?.contentOrNull
                if (type == "header") return@forEachLine

                val msg = decodeMessage(type, element)
                if (msg != null) messages.add(msg)
            } catch (_: Exception) {
                // Ignore malformed line
            }
        }
        return messages
    }

    fun ensureSystemPrompt(sessionId: String, systemPrompt: String) {
        val messages = load(sessionId)
        if (messages.none { it is ChatMessage.System }) {
            val systemMsg = ChatMessage.System(
                id = UUID.randomUUID().toString(),
                content = systemPrompt
            )
            append(sessionId, systemMsg)
        }
    }

    fun deleteSession(sessionId: String): Boolean {
        val file = File(sessionsDir, "$sessionId.jsonl")
        return file.delete()
    }

    private fun encodeMessage(msg: ChatMessage): String = when (msg) {
        is ChatMessage.System -> buildJsonObject {
            put("type", "system")
            put("id", msg.id)
            put("timestamp", msg.timestamp)
            put("content", msg.content)
        }.toString()
        is ChatMessage.User -> buildJsonObject {
            put("type", "user")
            put("id", msg.id)
            put("timestamp", msg.timestamp)
            put("content", msg.content)
            putJsonArray("images") { msg.images.forEach { add(it) } }
        }.toString()
        is ChatMessage.Assistant -> buildJsonObject {
            put("type", "assistant")
            put("id", msg.id)
            put("timestamp", msg.timestamp)
            put("content", msg.content)
            msg.finishReason?.let { put("finishReason", it) }
            putJsonArray("toolCalls") {
                msg.toolCalls.forEach { tc ->
                    addJsonObject {
                        put("id", tc.id)
                        put("name", tc.name)
                        put("arguments", tc.arguments)
                    }
                }
            }
        }.toString()
        is ChatMessage.Tool -> buildJsonObject {
            put("type", "tool")
            put("id", msg.id)
            put("timestamp", msg.timestamp)
            put("toolCallId", msg.toolCallId)
            put("toolName", msg.toolName)
            put("content", msg.content)
            put("isError", msg.isError)
        }.toString()
    }

    private fun decodeMessage(type: String?, obj: JsonObject): ChatMessage? {
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: UUID.randomUUID().toString()
        val timestamp = obj["timestamp"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
        return when (type) {
            "system" -> ChatMessage.System(
                id = id,
                timestamp = timestamp,
                content = obj["content"]?.jsonPrimitive?.contentOrNull ?: ""
            )
            "user" -> {
                val imgs = obj["images"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                ChatMessage.User(
                    id = id,
                    timestamp = timestamp,
                    content = obj["content"]?.jsonPrimitive?.contentOrNull ?: "",
                    images = imgs
                )
            }
            "assistant" -> {
                val calls = obj["toolCalls"]?.jsonArray?.mapNotNull { item ->
                    val o = item.jsonObject
                    val cid = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val cname = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val cargs = o["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}"
                    SerializedToolCall(cid, cname, cargs)
                } ?: emptyList()
                ChatMessage.Assistant(
                    id = id,
                    timestamp = timestamp,
                    content = obj["content"]?.jsonPrimitive?.contentOrNull ?: "",
                    toolCalls = calls,
                    finishReason = obj["finishReason"]?.jsonPrimitive?.contentOrNull
                )
            }
            "tool" -> ChatMessage.Tool(
                id = id,
                timestamp = timestamp,
                toolCallId = obj["toolCallId"]?.jsonPrimitive?.contentOrNull ?: "",
                toolName = obj["toolName"]?.jsonPrimitive?.contentOrNull ?: "",
                content = obj["content"]?.jsonPrimitive?.contentOrNull ?: "",
                isError = obj["isError"]?.jsonPrimitive?.booleanOrNull ?: false
            )
            else -> null
        }
    }
}
