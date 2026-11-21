package com.trailguardian.app.ui

import com.trailguardian.core.model.ToastType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

data class ToastMessage(
    val id: Long,
    val message: String,
    val type: ToastType,
)

class ToastCenter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val counter = AtomicLong(0)
    private val _messages = MutableStateFlow<List<ToastMessage>>(emptyList())
    val messages: StateFlow<List<ToastMessage>> = _messages.asStateFlow()

    fun show(message: String, type: ToastType = ToastType.INFO) {
        val toast = ToastMessage(counter.incrementAndGet(), message, type)
        _messages.value = (_messages.value + toast).takeLast(MAX_TOASTS)
        scope.launch {
            delay(DURATION_MS)
            _messages.value = _messages.value.filterNot { it.id == toast.id }
        }
    }

    private companion object {
        const val MAX_TOASTS = 5
        const val DURATION_MS = 3200L
    }
}
