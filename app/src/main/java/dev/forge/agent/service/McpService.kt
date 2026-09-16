package dev.forge.agent.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dev.forge.agent.data.McpConfigRepository
import dev.forge.agent.protocol.mcp.HttpSseMcpTransport
import dev.forge.agent.protocol.mcp.McpClient
import dev.forge.agent.tools.AgentTool
import dev.forge.agent.tools.ToolRegistry
import kotlinx.coroutines.*

class McpService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeClients = mutableListOf<McpClient>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repo = McpConfigRepository(applicationContext)
        val configs = repo.loadServers().filter { it.enabled }

        serviceScope.launch {
            configs.forEach { config ->
                if (!config.url.isNullOrBlank()) {
                    try {
                        val transport = HttpSseMcpTransport(config.url, config.headers)
                        val client = McpClient(config, transport)
                        val tools = client.listTools()
                        val agentTools = client.toAgentTools(tools)
                        agentTools.forEach { ToolRegistry.default().register(it) }
                        activeClients.add(client)
                    } catch (_: Exception) {}
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
