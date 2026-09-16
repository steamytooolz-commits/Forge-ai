package dev.forge.agent.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.forge.agent.core.CustomCommand

@Composable
fun CommandPalette(
    commands: List<CustomCommand>,
    filter: String,
    onSelectCommand: (CustomCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    val query = filter.removePrefix("/").trim()
    val filtered = commands.filter { it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }

    if (filtered.isNotEmpty()) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .padding(8.dp)
            ) {
                items(filtered, key = { it.name }) { cmd ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectCommand(cmd) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("command_palette_item_${cmd.name}")
                    ) {
                        Text(
                            text = "/${cmd.name}",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = cmd.description,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}
