package dev.forge.agent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import dev.forge.agent.data.SettingsRepository
import java.io.File

class ForgeApplication : Application() {

    companion object {
        const val TAG = "ForgeApplication"
        const val CHANNEL_ID = "forge_agent"
        const val CHANNEL_NAME = "Forge Agent Service"
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Ensure private storage directories exist
        val workspaceDir = File(filesDir, "workspace").apply { mkdirs() }
        val sessionsDir = File(filesDir, "sessions").apply { mkdirs() }

        // 2. Initialize notification channel
        createNotificationChannel()

        // 3. Log app version and initialization state
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            Log.i(TAG, "Forge v${packageInfo.versionName} initialized. Workspace: ${workspaceDir.absolutePath}, Sessions: ${sessionsDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inspect package info", e)
        }

        // 4. Seed initial workspace starter file if empty
        val readme = File(workspaceDir, "README.md")
        if (!readme.exists()) {
            readme.writeText(
                """# Welcome to Forge ⚡
Native Android AI Coding Agent.

Forge gives LLMs real, local tool access directly on Android's ART runtime.
Tools available:
- `read_file`, `write_file`, `edit_file`, `delete_file`, `patch`
- `list_directory`, `glob`, `grep`
- `bash` (runs directly via /system/bin/sh)
- `webfetch`, `websearch`
- `todowrite`, `todoread`
- `task` (subagent orchestration)
- `session_list`, `session_read`, `session_search`, `session_info`
- `question` (interactive user prompts)

Try asking Forge:
- "Create a Kotlin class that calculates Fibonacci numbers"
- "Inspect this workspace and list all files"
- "Run a shell command to check Android system info"
""".trimIndent()
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground execution notifications for Forge AI agent loops"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
            Log.i(TAG, "Notification channel '$CHANNEL_ID' created.")
        }
    }
}
