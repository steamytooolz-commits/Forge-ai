package dev.forge.agent.tools

import dev.forge.agent.data.Workspace
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.*

object QuestionBridge {
    private var pendingQuestion: Pair<String, CompletableDeferred<String>>? = null

    fun askQuestion(question: String, deferred: CompletableDeferred<String>) {
        pendingQuestion = question to deferred
    }

    fun getPendingQuestion(): String? = pendingQuestion?.first

    fun answerQuestion(answer: String) {
        pendingQuestion?.second?.complete(answer)
        pendingQuestion = null
    }

    fun cancel() {
        pendingQuestion?.second?.complete("User cancelled the question.")
        pendingQuestion = null
    }
}

class QuestionTool : AgentTool {
    override val name = "question"
    override val description =
        "Ask the user a clarifying question when a request is ambiguous or requires user confirmation/input."
    override val readOnly = true
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "question" to mapOf("type" to "string", "description" to "The question prompt to present to the user")
        ),
        "required" to listOf("question")
    )

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult {
        val args = try { Json.parseToJsonElement(arguments).jsonObject } catch (e: Exception) {
            return ToolResult.error("Invalid JSON: ${e.message}")
        }
        val question = args["question"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing question parameter")

        val deferred = CompletableDeferred<String>()
        QuestionBridge.askQuestion(question, deferred)

        return try {
            val answer = deferred.await()
            ToolResult.success("User answered: $answer")
        } catch (e: Exception) {
            ToolResult.error("Question error: ${e.message}")
        }
    }
}
