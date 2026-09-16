package dev.forge.agent.core

import kotlinx.serialization.Serializable

@Serializable
data class CustomCommand(
    val name: String,
    val description: String,
    val promptTemplate: String,
    val model: String? = null,
    val agent: String? = null,
    val scope: String = "project" // "project" or "user"
) {
    fun render(args: Map<String, String>): String {
        var result = promptTemplate
        args.forEach { (key, value) ->
            result = result.replace("{$key}", value).replace("$$key", value)
        }
        return result
    }
}
