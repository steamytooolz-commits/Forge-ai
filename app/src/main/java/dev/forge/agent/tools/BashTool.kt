package dev.forge.agent.tools

import dev.forge.agent.data.FileWorkspace
import dev.forge.agent.data.SafWorkspace
import dev.forge.agent.data.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.util.concurrent.TimeUnit

class BashTool : AgentTool {
    override val name = "bash"
    override val description =
        "Execute a shell command via /system/bin/sh. " +
        "On local file workspaces, commands run inside the workspace root. " +
        "On SAF document workspaces, commands run from '/' as SAF directories are virtual content URIs. " +
        "Output is captured and returned. Output longer than 30,000 characters is truncated."
    override val readOnly = false
    override val defaultPermission = ToolPermission.ASK
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "command" to mapOf("type" to "string", "description" to "The shell command string to execute"),
            "timeout_ms" to mapOf("type" to "integer", "description" to "Timeout in milliseconds", "default" to 120000)
        ),
        "required" to listOf("command")
    )

    override suspend fun execute(arguments: String, workspace: Workspace): ToolResult = withContext(Dispatchers.IO) {
        val args = try {
            Json.parseToJsonElement(arguments).jsonObject
        } catch (e: Exception) {
            return@withContext ToolResult.error("Invalid JSON: ${e.message}")
        }

        val rawCommand = args["command"]?.jsonPrimitive?.contentOrNull
            ?: return@withContext ToolResult.error("Missing required argument: command")
        val timeoutMs = args["timeout_ms"]?.jsonPrimitive?.longOrNull ?: 120_000L

        val shPath = if (File("/system/bin/sh").exists()) "/system/bin/sh" else "/bin/sh"

        val workingDir: File
        val finalCommand: String

        when (workspace) {
            is FileWorkspace -> {
                workingDir = workspace.root
                finalCommand = rawCommand
            }
            is SafWorkspace -> {
                workingDir = File("/")
                finalCommand = "cd / && $rawCommand"
            }
        }

        val process = try {
            ProcessBuilder(shPath, "-c", finalCommand)
                .directory(workingDir)
                .redirectErrorStream(true)
                .apply {
                    environment()["PATH"] = "/system/bin:/system/xbin:/vendor/bin:/bin:/usr/bin"
                    if (workspace is FileWorkspace) {
                        environment()["HOME"] = workspace.root.absolutePath
                        environment()["TMPDIR"] = workspace.root.resolve(".tmp").apply { mkdirs() }.absolutePath
                    }
                }
                .start()
        } catch (e: Exception) {
            return@withContext ToolResult.error("Failed to spawn shell: ${e.message}")
        }

        val output = StringBuilder()
        val readerThread = Thread {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (output.length < 30_000) {
                        output.append(line).append('\n')
                    }
                }
            }
        }
        readerThread.start()

        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            readerThread.join(1000)
            return@withContext ToolResult.error("Command timed out after ${timeoutMs}ms\n\n$output")
        }
        readerThread.join(2000)

        val exit = process.exitValue()
        val text = output.toString()
        val result = if (text.length >= 30_000) text + "\n(output truncated at 30,000 characters)" else text

        if (exit != 0) {
            ToolResult(output = result.ifEmpty { "Process exited with code $exit" }, isError = true, exitCode = exit)
        } else {
            ToolResult.success(result.ifEmpty { "(command completed with no output)" })
        }
    }
}
