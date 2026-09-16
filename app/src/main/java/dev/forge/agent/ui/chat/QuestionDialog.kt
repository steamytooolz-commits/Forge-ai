package dev.forge.agent.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun QuestionDialog(
    question: String,
    onAnswer: (String) -> Unit,
    onCancel: () -> Unit
) {
    var answerText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(
                Icons.Default.QuestionAnswer,
                contentDescription = "Agent Question",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Agent Question", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(question)
                OutlinedTextField(
                    value = answerText,
                    onValueChange = { answerText = it },
                    label = { Text("Your answer") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("question_dialog_input"),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (answerText.isNotBlank()) onAnswer(answerText.trim()) },
                enabled = answerText.isNotBlank(),
                modifier = Modifier.testTag("question_dialog_submit_button")
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag("question_dialog_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
