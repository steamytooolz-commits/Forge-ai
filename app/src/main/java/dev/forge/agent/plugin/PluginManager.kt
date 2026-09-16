package dev.forge.agent.plugin

import dev.forge.agent.core.CustomCommand
import dev.forge.agent.llm.LlmMessage
import dev.forge.agent.llm.LlmToolCall
import dev.forge.agent.tools.AgentTool
import dev.forge.agent.tools.ToolResult

class HookRegistry {
    val beforeToolHooks = mutableListOf<suspend (LlmToolCall) -> Unit>()
    val afterToolHooks = mutableListOf<suspend (LlmToolCall, ToolResult) -> Unit>()
    val beforeLlmHooks = mutableListOf<suspend (List<LlmMessage>) -> List<LlmMessage>>()
    val afterLlmHooks = mutableListOf<suspend (String) -> String>()
    val eventHooks = mutableMapOf<String, MutableList<suspend (Any) -> Unit>>()

    fun clear() {
        beforeToolHooks.clear()
        afterToolHooks.clear()
        beforeLlmHooks.clear()
        afterLlmHooks.clear()
        eventHooks.clear()
    }
}

class PluginManager {
    val hookRegistry = HookRegistry()
    private val plugins = mutableListOf<Plugin>()
    private val customTools = mutableListOf<AgentTool>()
    private val customCommands = mutableListOf<CustomCommand>()

    fun registerPlugin(plugin: Plugin) {
        plugins.add(plugin)
        val api = object : PluginApi {
            override fun registerTool(tool: AgentTool) {
                customTools.add(tool)
            }

            override fun registerCommand(command: CustomCommand) {
                customCommands.add(command)
            }

            override fun onBeforeToolExecution(handler: suspend (LlmToolCall) -> Unit) {
                hookRegistry.beforeToolHooks.add(handler)
            }

            override fun onAfterToolExecution(handler: suspend (LlmToolCall, ToolResult) -> Unit) {
                hookRegistry.afterToolHooks.add(handler)
            }

            override fun onBeforeLlmRequest(handler: suspend (List<LlmMessage>) -> List<LlmMessage>) {
                hookRegistry.beforeLlmHooks.add(handler)
            }

            override fun onAfterLlmResponse(handler: suspend (String) -> String) {
                hookRegistry.afterLlmHooks.add(handler)
            }

            override fun registerEventHook(eventType: String, handler: suspend (Any) -> Unit) {
                hookRegistry.eventHooks.getOrPut(eventType) { mutableListOf() }.add(handler)
            }
        }
        plugin.onLoad(api)
    }

    fun getPlugins(): List<Plugin> = plugins.toList()
    fun getRegisteredTools(): List<AgentTool> = customTools.toList()
    fun getRegisteredCommands(): List<CustomCommand> = customCommands.toList()
}
