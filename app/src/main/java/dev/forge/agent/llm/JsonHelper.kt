package dev.forge.agent.llm

import kotlinx.serialization.json.*

/**
 * Utility to reliably convert arbitrary Kotlin objects/maps/lists to JsonElement
 * without requiring kotlinx.serialization plugin descriptors for Any?.
 */
fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is String -> JsonPrimitive(this)
    is Iterable<*> -> JsonArray(this.map { it.toJsonElement() })
    is Array<*> -> JsonArray(this.map { it.toJsonElement() })
    is Map<*, *> -> JsonObject(this.map { (k, v) -> k.toString() to v.toJsonElement() }.toMap())
    else -> JsonPrimitive(this.toString())
}
