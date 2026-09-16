package dev.forge.agent.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PermissionDialog(
    toolName: String,
    arguments: String,
    onAllow: () -> Unit,
    onDeny: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDeny,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = "Permission Request",
                tint = MaterialTheme.colorScheme.tertiary
            )
        },
        title = {
            Text("Tool Execution Permission", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The agent wants to execute the following tool:")
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = toolName,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = arguments,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 6
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAllow,
                modifier = Modifier.testTag("permission_allow_button")
            ) {
                Text("Allow")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDeny,
                modifier = Modifier.testTag("permission_deny_button")
            ) {
                Text("Deny")
            }
        }
    )
}
