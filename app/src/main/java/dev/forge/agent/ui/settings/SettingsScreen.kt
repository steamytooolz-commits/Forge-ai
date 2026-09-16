package dev.forge.agent.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.forge.agent.core.DefaultPrompts
import dev.forge.agent.data.CuratedModels
import dev.forge.agent.data.ProviderConfig
import dev.forge.agent.data.SettingsRepository
import dev.forge.agent.data.WorkspaceRepository
import dev.forge.agent.permissions.PermissionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val workspaceRepo = remember { WorkspaceRepository(context, settingsRepository) }

    var selectedModel by remember { mutableStateOf(settingsRepository.getSelectedModel()) }
    var planMode by remember { mutableStateOf(settingsRepository.getPlanMode()) }
    var temperature by remember { mutableFloatStateOf(settingsRepository.getTemperature()) }
    var maxTokens by remember { mutableIntStateOf(settingsRepository.getMaxTokens()) }

    var geminiKey by remember { mutableStateOf(settingsRepository.getApiKey(ProviderConfig.Kind.GEMINI)) }
    var openaiKey by remember { mutableStateOf(settingsRepository.getApiKey(ProviderConfig.Kind.OPENAI)) }
    var anthropicKey by remember { mutableStateOf(settingsRepository.getApiKey(ProviderConfig.Kind.ANTHROPIC)) }
    var openRouterKey by remember { mutableStateOf(settingsRepository.getApiKey(ProviderConfig.Kind.OPENROUTER)) }
    var ollamaUrl by remember { mutableStateOf(settingsRepository.getBaseUrl(ProviderConfig.Kind.OLLAMA)) }

    var systemPrompt by remember { mutableStateOf(settingsRepository.getSystemPrompt()) }
    var showKeys by remember { mutableStateOf(false) }
    var currentWorkspaceUri by remember { mutableStateOf(settingsRepository.getWorkspaceUri()) }

    var isBatteryOptimized by remember { mutableStateOf(PermissionManager.isBatteryOptimizationIgnored(context)) }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            PermissionManager.takePersistableUriPermission(context, uri)
            workspaceRepo.setSafWorkspace(uri)
            currentWorkspaceUri = uri.toString()
        }
    }

    val scrollState = rememberScrollState()

    fun saveAll() {
        settingsRepository.setSelectedModel(selectedModel)
        settingsRepository.setPlanMode(planMode)
        settingsRepository.setTemperature(temperature)
        settingsRepository.setMaxTokens(maxTokens)
        settingsRepository.setApiKey(ProviderConfig.Kind.GEMINI, geminiKey.trim())
        settingsRepository.setApiKey(ProviderConfig.Kind.OPENAI, openaiKey.trim())
        settingsRepository.setApiKey(ProviderConfig.Kind.ANTHROPIC, anthropicKey.trim())
        settingsRepository.setApiKey(ProviderConfig.Kind.OPENROUTER, openRouterKey.trim())
        settingsRepository.setBaseUrl(ProviderConfig.Kind.OLLAMA, ollamaUrl.trim())
        settingsRepository.setSystemPrompt(systemPrompt)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        saveAll()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { saveAll() },
                        modifier = Modifier.testTag("settings_save_button")
                    ) {
                        Text("Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Active Model Selection
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Selected Model", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Choose the default model for new agent sessions.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    CuratedModels.all.forEach { model ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedModel == model.id,
                                onClick = {
                                    selectedModel = model.id
                                    saveAll()
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(model.displayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("${model.providerKind.displayName} · ${model.contextWindow / 1000}k ctx", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Generation Parameters & Modes
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Model & Execution Config", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    // Plan mode
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Plan Mode", fontWeight = FontWeight.Medium)
                            Text(
                                "Restricts agent to read-only tools; forbids write/edit/delete/shell commands.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = planMode,
                            onCheckedChange = {
                                planMode = it
                                saveAll()
                            }
                        )
                    }

                    HorizontalDivider()

                    // Temperature slider
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Temperature: ", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(String.format("%.2f", temperature), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = temperature,
                            onValueChange = {
                                temperature = it
                                saveAll()
                            },
                            valueRange = 0.0f..1.0f,
                            steps = 19
                        )
                    }

                    // Max Tokens
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Max Output Tokens: $maxTokens", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.weight(1f))
                        listOf(4096, 8192, 16384).forEach { count ->
                            FilterChip(
                                selected = maxTokens == count,
                                onClick = {
                                    maxTokens = count
                                    saveAll()
                                },
                                label = { Text("${count / 1024}k") }
                            )
                        }
                    }
                }
            }

            // Workspace Storage Location
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Active Workspace Storage", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        if (currentWorkspaceUri != null) {
                            "Custom Folder (SAF Tree): $currentWorkspaceUri"
                        } else {
                            "App-Private Sandbox: ${settingsRepository.getWorkspaceDir().absolutePath}"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { safLauncher.launch(null) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Choose Folder")
                        }

                        if (currentWorkspaceUri != null) {
                            OutlinedButton(
                                onClick = {
                                    workspaceRepo.clear()
                                    currentWorkspaceUri = null
                                }
                            ) {
                                Text("Reset")
                            }
                        }
                    }
                }
            }

            // Background & Battery Optimization
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Background Reliability", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        if (isBatteryOptimized) "Battery optimization is disabled for Forge (foreground services run reliably without OS interruption)."
                        else "Forge may be suspended during long multi-step reasoning tasks when in the background.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!isBatteryOptimized) {
                        Button(
                            onClick = {
                                PermissionManager.requestBatteryOptimizationExemption(context)
                                isBatteryOptimized = PermissionManager.isBatteryOptimizationIgnored(context)
                            }
                        ) {
                            Text("Request Battery Exemption")
                        }
                    }
                }
            }

            // API Keys Section
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Provider Credentials", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showKeys = !showKeys }) {
                            Text(if (showKeys) "Hide Keys" else "Show Keys")
                        }
                    }
                    Text(
                        "Keys are securely stored using Android Keystore & EncryptedSharedPreferences.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val transformation = if (showKeys) VisualTransformation.None else PasswordVisualTransformation()

                    OutlinedTextField(
                        value = geminiKey,
                        onValueChange = { geminiKey = it },
                        label = { Text("Google Gemini API Key") },
                        visualTransformation = transformation,
                        modifier = Modifier.fillMaxWidth().testTag("settings_gemini_key_field")
                    )

                    OutlinedTextField(
                        value = openaiKey,
                        onValueChange = { openaiKey = it },
                        label = { Text("OpenAI API Key") },
                        visualTransformation = transformation,
                        modifier = Modifier.fillMaxWidth().testTag("settings_openai_key_field")
                    )

                    OutlinedTextField(
                        value = anthropicKey,
                        onValueChange = { anthropicKey = it },
                        label = { Text("Anthropic API Key") },
                        visualTransformation = transformation,
                        modifier = Modifier.fillMaxWidth().testTag("settings_anthropic_key_field")
                    )

                    OutlinedTextField(
                        value = openRouterKey,
                        onValueChange = { openRouterKey = it },
                        label = { Text("OpenRouter API Key") },
                        visualTransformation = transformation,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = ollamaUrl,
                        onValueChange = { ollamaUrl = it },
                        label = { Text("Ollama Base URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // System Prompt
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("System Prompt", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                systemPrompt = DefaultPrompts.DEFAULT_SYSTEM_PROMPT
                                saveAll()
                            }
                        ) {
                            Text("Reset Default")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp)
                            .testTag("settings_system_prompt_field"),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        maxLines = 15
                    )
                }
            }
        }
    }
}
