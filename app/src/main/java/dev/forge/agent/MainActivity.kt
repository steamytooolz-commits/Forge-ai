package dev.forge.agent

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import dev.forge.agent.data.SettingsRepository
import dev.forge.agent.ui.agents.AgentViewModel
import dev.forge.agent.ui.agents.AgentsScreen
import dev.forge.agent.ui.chat.ChatScreen
import dev.forge.agent.ui.chat.ChatViewModel
import dev.forge.agent.ui.editor.EditorScreen
import dev.forge.agent.ui.files.FilesScreen
import dev.forge.agent.ui.sessions.SessionsScreen
import dev.forge.agent.ui.settings.SettingsScreen
import dev.forge.agent.ui.terminal.TerminalScreen
import dev.forge.agent.ui.theme.ForgeTheme

class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()
    private val agentViewModel: AgentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settingsRepository = remember { SettingsRepository(applicationContext) }
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route ?: "chat"

            var editorTargetFile by remember { mutableStateOf<String?>(null) }

            // Request Notification permission for Android 13+
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { _ -> }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            ForgeTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        // Only show bottom navigation on primary screens
                        if (currentRoute != "settings") {
                            NavigationBar(
                                tonalElevation = 3.dp,
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                NavigationBarItem(
                                    selected = currentRoute == "chat",
                                    onClick = {
                                        navController.navigate("chat") {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Home, contentDescription = "Chat") },
                                    label = { Text("Chat") },
                                    modifier = Modifier.testTag("nav_chat")
                                )

                                NavigationBarItem(
                                    selected = currentRoute == "editor",
                                    onClick = {
                                        navController.navigate("editor") {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Edit, contentDescription = "Editor") },
                                    label = { Text("Editor") },
                                    modifier = Modifier.testTag("nav_editor")
                                )

                                NavigationBarItem(
                                    selected = currentRoute == "files",
                                    onClick = {
                                        navController.navigate("files") {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Build, contentDescription = "Files") },
                                    label = { Text("Files") },
                                    modifier = Modifier.testTag("nav_files")
                                )

                                NavigationBarItem(
                                    selected = currentRoute == "terminal",
                                    onClick = {
                                        navController.navigate("terminal") {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Terminal") },
                                    label = { Text("Shell") },
                                    modifier = Modifier.testTag("nav_terminal")
                                )

                                NavigationBarItem(
                                    selected = currentRoute == "agents",
                                    onClick = {
                                        navController.navigate("agents") {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Person, contentDescription = "Agents") },
                                    label = { Text("Agents") },
                                    modifier = Modifier.testTag("nav_agents")
                                )

                                NavigationBarItem(
                                    selected = currentRoute == "sessions",
                                    onClick = {
                                        navController.navigate("sessions") {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.List, contentDescription = "Sessions") },
                                    label = { Text("Sessions") },
                                    modifier = Modifier.testTag("nav_sessions")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "chat",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("chat") {
                            ChatScreen(
                                viewModel = chatViewModel,
                                onNavigateToSettings = { navController.navigate("settings") }
                            )
                        }

                        composable("editor") {
                            EditorScreen(
                                settingsRepository = settingsRepository,
                                initialFilePath = editorTargetFile
                            )
                        }

                        composable("files") {
                            FilesScreen(
                                settingsRepository = settingsRepository,
                                onOpenFileInEditor = { path ->
                                    editorTargetFile = path
                                    navController.navigate("editor")
                                }
                            )
                        }

                        composable("terminal") {
                            TerminalScreen(
                                settingsRepository = settingsRepository
                            )
                        }

                        composable("agents") {
                            AgentsScreen(
                                viewModel = agentViewModel
                            )
                        }

                        composable("sessions") {
                            SessionsScreen(
                                viewModel = chatViewModel,
                                onSelectSession = {
                                    navController.navigate("chat") {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                settingsRepository = settingsRepository,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
