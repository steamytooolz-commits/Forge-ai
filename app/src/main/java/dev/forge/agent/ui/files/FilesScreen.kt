package dev.forge.agent.ui.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun FilesScreen(
    settingsRepository: SettingsRepository,
    onOpenFileInEditor: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val workspace = remember { settingsRepository.getWorkspaceDir() }
    var expandedFolders by remember { mutableStateOf<Set<String>>(setOf("")) }
    var refreshKey by remember { mutableStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var isCreatingFolder by remember { mutableStateOf(false) }
    var newItemName by remember { mutableStateOf("") }
    var fileToDelete by remember { mutableStateOf<File?>(null) }

    val fileItems = remember(refreshKey) {
        val list = mutableListOf<WorkspaceItem>()

        fun walk(dir: File, indent: Int) {
            val children = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: return
            for (child in children) {
                val relPath = child.relativeTo(workspace).path
                val isDir = child.isDirectory
                list.add(WorkspaceItem(file = child, relativePath = relPath, indent = indent, isDirectory = isDir))
                if (isDir && expandedFolders.contains(relPath)) {
                    walk(child, indent + 1)
                }
            }
        }

        walk(workspace, 0)
        list
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Workspace Files", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = workspace.absolutePath,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isCreatingFolder = false
                            showCreateDialog = true
                        },
                        modifier = Modifier.testTag("files_new_file_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New File")
                    }
                    IconButton(
                        onClick = {
                            isCreatingFolder = true
                            showCreateDialog = true
                        },
                        modifier = Modifier.testTag("files_new_folder_button")
                    ) {
                        Icon(Icons.Default.Create, contentDescription = "New Folder")
                    }
                    IconButton(
                        onClick = { refreshKey++ },
                        modifier = Modifier.testTag("files_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
            if (fileItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Workspace is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            isCreatingFolder = false
                            showCreateDialog = true
                        }) {
                            Text("Create File")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(fileItems, key = { it.relativePath }) { item ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable {
                                    if (item.isDirectory) {
                                        expandedFolders = if (expandedFolders.contains(item.relativePath)) {
                                            expandedFolders - item.relativePath
                                        } else {
                                            expandedFolders + item.relativePath
                                        }
                                        refreshKey++
                                    } else {
                                        onOpenFileInEditor(item.relativePath)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    start = (item.indent * 16 + 12).dp,
                                    end = 8.dp,
                                    top = 10.dp,
                                    bottom = 10.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (item.isDirectory) {
                                        if (expandedFolders.contains(item.relativePath)) Icons.Default.KeyboardArrowDown
                                        else Icons.Default.KeyboardArrowRight
                                    } else Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(Modifier.width(8.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.file.name,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                        color = if (item.isDirectory) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (!item.isDirectory) {
                                        Text(
                                            text = formatSize(item.file.length()),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (!item.isDirectory) {
                                    IconButton(
                                        onClick = { onOpenFileInEditor(item.relativePath) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit File", modifier = Modifier.size(16.dp))
                                    }
                                }

                                IconButton(
                                    onClick = { fileToDelete = item.file },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(if (isCreatingFolder) "New Folder" else "New File") },
            text = {
                OutlinedTextField(
                    value = newItemName,
                    onValueChange = { newItemName = it },
                    label = { Text(if (isCreatingFolder) "Folder Name" else "File Name (relative path)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newItemName.trim()
                        if (name.isNotEmpty()) {
                            val f = File(workspace, name)
                            if (isCreatingFolder) {
                                f.mkdirs()
                            } else {
                                f.parentFile?.mkdirs()
                                f.writeText("")
                            }
                            refreshKey++
                            showCreateDialog = false
                            newItemName = ""
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    fileToDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete ${if (f.isDirectory) "Directory" else "File"}?") },
            text = { Text("Are you sure you want to delete ${f.name}?") },
            confirmButton = {
                Button(
                    onClick = {
                        if (f.isDirectory) f.deleteRecursively() else f.delete()
                        fileToDelete = null
                        refreshKey++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

private data class WorkspaceItem(
    val file: File,
    val relativePath: String,
    val indent: Int,
    val isDirectory: Boolean
)

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes.toDouble() / (1024 * 1024))
}
