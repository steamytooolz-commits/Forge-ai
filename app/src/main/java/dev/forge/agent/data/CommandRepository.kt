package dev.forge.agent.data

import dev.forge.agent.core.CustomCommand
import java.io.File

class CommandRepository(
    private val workspace: File
) {
    fun loadCommands(): List<CustomCommand> {
        val commands = mutableListOf<CustomCommand>()

        // Built-in slash commands
        commands.add(
            CustomCommand(
                name = "init",
                description = "Analyze workspace architecture and generate AGENTS.md conventions file.",
                promptTemplate = "Initialize this project: analyze all directories and files, generate a comprehensive AGENTS.md file detailing architecture and code conventions."
            )
        )
        commands.add(
            CustomCommand(
                name = "compact",
                description = "Summarize and compact conversation history to free token budget.",
                promptTemplate = "Please summarize all key findings, architectural decisions, and current tasks from this session into a single concise briefing."
            )
        )
        commands.add(
            CustomCommand(
                name = "clear",
                description = "Clear current conversation and start fresh.",
                promptTemplate = "/clear"
            )
        )
        commands.add(
            CustomCommand(
                name = "share",
                description = "Export session transcript for sharing.",
                promptTemplate = "Generate a formatted export summary of this session suitable for sharing with the team."
            )
        )
        commands.add(
            CustomCommand(
                name = "undo",
                description = "Revert recent changes made to workspace files.",
                promptTemplate = "Undo the last tool file modification or patch."
            )
        )

        // Load project-level custom markdown commands (.opencode/command/ or .claude/commands/)
        val searchDirs = listOf(
            File(workspace, ".opencode/command"),
            File(workspace, ".opencode/commands"),
            File(workspace, ".claude/commands")
        )

        searchDirs.forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles { f -> f.extension.equals("md", ignoreCase = true) }?.forEach { f ->
                    val name = f.nameWithoutExtension
                    val text = f.readText()
                    commands.add(
                        CustomCommand(
                            name = name,
                            description = "Custom workspace command: $name",
                            promptTemplate = text,
                            scope = "project"
                        )
                    )
                }
            }
        }

        return commands
    }
}
