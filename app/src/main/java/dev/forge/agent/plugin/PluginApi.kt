package dev.forge.agent.plugin

import dev.forge.agent.core.CustomCommand
import dev.forge.agent.llm.LlmMessage
import dev.forge.agent.llm.LlmToolCall
import dev.forge.agent.tools.AgentTool
import dev.forge.agent.tools.ToolResult

interface Plugin {
    val name: String
    val version: String
    fun onLoad(api: PluginApi) {}
    fun onUnload() {}
}

interface PluginApi {
    fun registerTool(tool: AgentTool)
    fun registerCommand(command: CustomCommand)
    fun onBeforeToolExecution(handler: suspend (toolCall: LlmToolCall) -> Unit)
    fun onAfterToolExecution(handler: suspend (toolCall: LlmToolCall, result: ToolResult) -> Unit)
    fun onBeforeLlmRequest(handler: suspend (messages: List<LlmMessage>) -> List<LlmMessage>)
    fun onAfterLlmResponse(handler: suspend (response: String) -> String)
    fun registerEventHook(eventType: String, handler: suspend (Any) -> Unit)
}
