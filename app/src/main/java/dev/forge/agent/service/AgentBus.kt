package dev.forge.agent.service

import dev.forge.agent.core.AgentEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

object AgentBus {
    val events = MutableSharedFlow<AgentEvent>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val isRunning = MutableStateFlow(false)
    val runningSessionId = MutableStateFlow<String?>(null)
    val activeToolName = MutableStateFlow<String?>(null)
}
