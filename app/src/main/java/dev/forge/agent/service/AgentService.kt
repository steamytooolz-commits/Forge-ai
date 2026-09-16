package dev.forge.agent.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dev.forge.agent.ForgeApplication
import dev.forge.agent.MainActivity
import dev.forge.agent.core.AgentEvent
import dev.forge.agent.core.AgentLoop
import dev.forge.agent.core.SessionManager
import dev.forge.agent.data.CuratedModels
import dev.forge.agent.data.SettingsRepository
import dev.forge.agent.data.WorkspaceRepository
import dev.forge.agent.llm.ProviderFactory
import dev.forge.agent.tools.ToolRegistry
import kotlinx.coroutines.*
import java.io.File

class AgentService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val ACTION_RUN = "dev.forge.agent.ACTION_RUN"
        const val ACTION_CANCEL = "dev.forge.agent.ACTION_CANCEL"
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context, sessionId: String) {
            val intent = Intent(context, AgentService::class.java).apply {
                action = ACTION_RUN
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AgentService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Forge:AgentLoopWakeLock")?.apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RUN -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return START_NOT_STICKY
                startAgentLoop(sessionId)
            }
            ACTION_CANCEL -> {
                cancelAgentLoop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startAgentLoop(sessionId: String) {
        currentJob?.cancel()
        wakeLock?.acquire(10 * 60 * 1000L) // 10 min timeout safety

        val notification = buildNotification("Forge is reasoning...")
        startForeground(NOTIFICATION_ID, notification)

        AgentBus.isRunning.value = true
        AgentBus.runningSessionId.value = sessionId
        AgentBus.activeToolName.value = null

        currentJob = serviceScope.launch {
            try {
                val settings = SettingsRepository(applicationContext)
                val modelId = settings.getSelectedModel()
                val modelInfo = CuratedModels.all.find { it.id == modelId }
                val providerKind = modelInfo?.providerKind ?: dev.forge.agent.data.ProviderConfig.Kind.GEMINI
                val providerConfig = settings.getProviderConfig(providerKind)
                val provider = ProviderFactory.create(providerConfig)

                if (provider == null) {
                    val err = "No provider configured for $modelId. Please configure API key in Settings."
                    AgentBus.events.emit(AgentEvent.ErrorRaised(err, fatal = true))
                    return@launch
                }

                val workspaceRepo = WorkspaceRepository(applicationContext, settings)
                val workspace = workspaceRepo.current()
                val sessionsDir = File(filesDir, "sessions")
                val sessionManager = SessionManager(sessionsDir)
                val tools = ToolRegistry.defaultRegistry()
                val planMode = settings.getPlanMode()

                sessionManager.ensureSystemPrompt(sessionId, settings.getSystemPrompt())

                val loop = AgentLoop(
                    provider = provider,
                    model = modelId,
                    tools = tools,
                    workspace = workspace,
                    sessionManager = sessionManager,
                    sessionId = sessionId,
                    planMode = planMode,
                    temperature = settings.getTemperature().toDouble(),
                    maxTokens = settings.getMaxTokens(),
                    onEvent = { event ->
                        AgentBus.events.emit(event)
                        when (event) {
                            is AgentEvent.ToolExecutionStarted -> {
                                AgentBus.activeToolName.value = event.toolName
                                updateNotification("Running tool: ${event.toolName}")
                            }
                            is AgentEvent.ToolExecutionFinished -> {
                                AgentBus.activeToolName.value = null
                                updateNotification("Processing tool result...")
                            }
                            is AgentEvent.AssistantTextDelta -> {
                                updateNotification("Forge is generating response...")
                            }
                            is AgentEvent.LoopCompleted, is AgentEvent.Cancelled, is AgentEvent.ErrorRaised -> {
                                AgentBus.activeToolName.value = null
                            }
                            else -> {}
                        }
                    }
                )

                loop.run()
            } finally {
                AgentBus.isRunning.value = false
                AgentBus.runningSessionId.value = null
                AgentBus.activeToolName.value = null
                wakeLock?.let { if (it.isHeld) it.release() }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun cancelAgentLoop() {
        currentJob?.cancel()
        AgentBus.isRunning.value = false
        AgentBus.runningSessionId.value = null
        AgentBus.activeToolName.value = null
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    private fun buildNotification(text: String): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ForgeApplication.CHANNEL_ID)
            .setContentTitle("Forge Agent")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        currentJob?.cancel()
        serviceScope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
    }
}
