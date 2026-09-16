package dev.forge.agent.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.forge.agent.data.SettingsRepository
import dev.forge.agent.ui.chat.TerminalLogBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier
) {
    val logs by TerminalLogBus.logs.collectAsState()
    val workspace = remember { settingsRepository.getWorkspaceDir() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current

    var commandInput by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    fun executeShell(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return
        if (trimmed == "clear") {
            TerminalLogBus.clear()
            commandInput = ""
            return
        }

        isExecuting = true
        TerminalLogBus.append("forge@android:workspace$ $trimmed")
        commandInput = ""

        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val shPath = if (File("/system/bin/sh").exists()) "/system/bin/sh" else "/bin/sh"
                    val proc = ProcessBuilder(shPath, "-c", trimmed)
                        .directory(workspace)
                        .redirectErrorStream(true)
                        .apply {
                            environment()["PATH"] = "/system/bin:/system/xbin:/vendor/bin:/bin:/usr/bin"
                            environment()["HOME"] = workspace.absolutePath
                            environment()["TMPDIR"] = workspace.resolve(".tmp").apply { mkdirs() }.absolutePath
                        }
                        .start()

                    proc.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            TerminalLogBus.append(line)
                        }
                    }

                    val finished = proc.waitFor(30, TimeUnit.SECONDS)
                    if (!finished) {
                        proc.destroyForcibly()
                        TerminalLogBus.append("[Process timed out after 30s]")
                    } else {
                        val exit = proc.exitValue()
                        if (exit != 0) {
                            TerminalLogBus.append("[Exit code: $exit]")
                        }
                    }
                } catch (e: Exception) {
                    TerminalLogBus.append("[Error: ${e.message}]")
                } finally {
                    isExecuting = false
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Terminal", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("sh in ${workspace.name}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(logs.joinToString("\n")))
                        },
                        modifier = Modifier.testTag("terminal_copy_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Copy Logs")
                    }
                    IconButton(
                        onClick = { TerminalLogBus.clear() },
                        modifier = Modifier.testTag("terminal_clear_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Terminal")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF030712)) // Dark terminal canvas
        ) {
            // Quick Commands Row
            val quickCommands = listOf("ls -la", "pwd", "uname -a", "df -h", "cat README.md", "clear")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickCommands.forEach { qcmd ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF1F2937),
                        modifier = Modifier.clickable(enabled = !isExecuting) {
                            executeShell(qcmd)
                        }
                    ) {
                        Text(
                            text = qcmd,
                            color = Color(0xFF38BDF8),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Terminal Logs Area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                items(logs) { line ->
                    val color = when {
                        line.startsWith("forge@android") -> Color(0xFF34D399) // Emerald
                        line.startsWith("$ ") -> Color(0xFF38BDF8) // Sky blue
                        line.startsWith("-> ERROR") || line.contains("[Error") || line.contains("[Exit code") -> Color(0xFFF87171) // Red
                        line.startsWith("-> ") -> Color(0xFFA78BFA) // Purple
                        else -> Color(0xFFE2E8F0) // Light gray
                    }

                    Text(
                        text = line,
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }

                if (isExecuting) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = Color(0xFF38BDF8)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Executing...",
                                color = Color(0xFF94A3B8),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Command prompt bar
            Surface(
                color = Color(0xFF0F172A),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$ ",
                        color = Color(0xFF34D399),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    TextField(
                        value = commandInput,
                        onValueChange = { commandInput = it },
                        placeholder = {
                            Text("Type shell command...", color = Color(0xFF64748B), fontSize = 12.sp)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("terminal_input_field"),
                        textStyle = LocalTextStyle.current.copy(
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    IconButton(
                        onClick = { executeShell(commandInput) },
                        enabled = commandInput.isNotBlank() && !isExecuting,
                        modifier = Modifier.testTag("terminal_run_button")
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Run",
                            tint = if (commandInput.isNotBlank() && !isExecuting) Color(0xFF34D399) else Color(0xFF475569)
                        )
                    }
                }
            }
        }
    }
}
