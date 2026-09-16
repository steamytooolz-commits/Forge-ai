package dev.forge.agent.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.forge.agent.data.SettingsRepository
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    settingsRepository: SettingsRepository,
    initialFilePath: String? = null,
    modifier: Modifier = Modifier
) {
    val workspace = remember { settingsRepository.getWorkspaceDir() }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var content by remember { mutableStateOf("") }
    var originalContent by remember { mutableStateOf("") }
    var filesInWorkspace by remember { mutableStateOf<List<File>>(emptyList()) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun refreshFiles() {
        filesInWorkspace = workspace.walkTopDown().filter { it.isFile }.toList()
    }

    LaunchedEffect(Unit) {
        refreshFiles()
        if (initialFilePath != null) {
            val f = File(workspace, initialFilePath)
            if (f.exists() && f.isFile) {
                selectedFile = f
                val text = f.readText()
                content = text
                originalContent = text
            }
        } else if (selectedFile == null && filesInWorkspace.isNotEmpty()) {
            val first = filesInWorkspace.first()
            selectedFile = first
            val text = first.readText()
            content = text
            originalContent = text
        }
    }

    val isModified = content != originalContent

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Code Editor", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        selectedFile?.let { f ->
                            Text(
                                text = f.relativeTo(workspace).path + if (isModified) " • Modified" else "",
                                fontSize = 11.sp,
                                color = if (isModified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showNewFileDialog = true },
                        modifier = Modifier.testTag("editor_new_file_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New File")
                    }
                    IconButton(
                        onClick = {
                            selectedFile?.let { f ->
                                f.writeText(content)
                                originalContent = content
                                statusMessage = "Saved ${f.name}"
                            }
                        },
                        enabled = isModified,
                        modifier = Modifier.testTag("editor_save_button")
                    ) {
                        Icon(
                            Icons.Default.Done,
                            contentDescription = "Save File",
                            tint = if (isModified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
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
        ) {
            // File Selector Dropdown / Chips Row
            ScrollableTabRow(
                selectedTabIndex = filesInWorkspace.indexOf(selectedFile).coerceAtLeast(0),
                edgePadding = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                filesInWorkspace.forEach { file ->
                    val isSelected = file == selectedFile
                    Tab(
                        selected = isSelected,
                        onClick = {
                            selectedFile = file
                            val text = file.readText()
                            content = text
                            originalContent = text
                        },
                        text = {
                            Text(
                                text = file.name,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            statusMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(msg, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { statusMessage = null }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            if (selectedFile == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No file selected", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { showNewFileDialog = true }) {
                            Text("Create File")
                        }
                    }
                }
            } else {
                // Code Editor with Line Numbers
                val lines = content.lines()
                val lineCount = lines.size
                val scrollStateV = rememberScrollState()
                val scrollStateH = rememberScrollState()

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                ) {
                    // Line numbers column
                    Column(
                        modifier = Modifier
                            .verticalScroll(scrollStateV)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                    ) {
                        for (i in 1..lineCount) {
                            Text(
                                text = "$i",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // Code text area
                    TextField(
                        value = content,
                        onValueChange = { content = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("editor_text_area"),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                }

                // Editor Bottom Status Bar
                Surface(
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lines: $lineCount • Chars: ${content.length}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        if (isModified) {
                            Text(
                                text = "Unsaved Changes",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("Create New File") },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("File Name (e.g. main.kt)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newFileName.trim()
                        if (name.isNotEmpty()) {
                            val newFile = File(workspace, name)
                            newFile.parentFile?.mkdirs()
                            newFile.writeText("")
                            refreshFiles()
                            selectedFile = newFile
                            content = ""
                            originalContent = ""
                            showNewFileDialog = false
                            newFileName = ""
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
