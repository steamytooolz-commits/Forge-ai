package dev.forge.agent.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

object PermissionManager {
    private const val TAG = "PermissionManager"

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun requestNotificationPermission(
        activity: Activity,
        launcher: ActivityResultLauncher<String>
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasNotificationPermission(activity)) {
                Log.i(TAG, "Requesting POST_NOTIFICATIONS permission")
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }

    fun requestBatteryOptimizationExemption(context: Context) {
        if (isBatteryOptimizationIgnored(context)) {
            Log.i(TAG, "Battery optimization is already ignored")
            return
        }
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.i(TAG, "Launched ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch direct battery optimization intent, falling back to settings", e)
            try {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Failed to launch battery optimization settings", fallbackEx)
            }
        }
    }

    fun takePersistableUriPermission(context: Context, uri: Uri): Boolean {
        return try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            Log.i(TAG, "Successfully took persistable URI permission for: $uri")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to take persistable URI permission for $uri", e)
            false
        }
    }
}
