package dev.forge.agent.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File

class WorkspaceRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val TAG = "WorkspaceRepository"

    fun current(): Workspace {
        val uriString = settingsRepository.getWorkspaceUri()
        if (!uriString.isNullOrBlank()) {
            try {
                val uri = Uri.parse(uriString)
                val doc = DocumentFile.fromTreeUri(context, uri)
                if (doc != null && doc.exists() && doc.canRead()) {
                    return SafWorkspace(doc, context.contentResolver)
                } else {
                    Log.w(TAG, "Persisted SAF URI is invalid or inaccessible: $uriString, falling back to local filesDir")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error opening SAF workspace from $uriString", e)
            }
        }
        val defaultDir = File(context.filesDir, "workspace").apply { mkdirs() }
        return FileWorkspace(defaultDir)
    }

    fun setSafWorkspace(uri: Uri) {
        settingsRepository.setWorkspaceUri(uri.toString())
        Log.i(TAG, "Set SAF workspace URI: $uri")
    }

    fun clear() {
        settingsRepository.setWorkspaceUri(null)
        Log.i(TAG, "Cleared SAF workspace; reverted to internal sandbox")
    }
}
