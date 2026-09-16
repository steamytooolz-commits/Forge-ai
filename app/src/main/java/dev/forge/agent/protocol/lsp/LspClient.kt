package dev.forge.agent.protocol.lsp

import kotlinx.serialization.json.*
import java.io.File

class LspClient(
    val config: LspServerConfig
) {
    suspend fun getDiagnostics(file: File): List<LspDiagnostic> {
        // Fallback static diagnostics when external server is not connected
        val diagnostics = mutableListOf<LspDiagnostic>()
        if (file.extension == "json") {
            try {
                Json.parseToJsonElement(file.readText())
            } catch (e: Exception) {
                diagnostics.add(
                    LspDiagnostic(
                        range = LspRange(LspPosition(0, 0), LspPosition(0, 0)),
                        severity = 1,
                        message = "JSON Syntax Error: ${e.message}",
                        source = "forge-lsp"
                    )
                )
            }
        }
        return diagnostics
    }
}
