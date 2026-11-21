package com.example.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class CapturedNotification(
    val key: String,
    val appName: String,
    val title: String,
    val text: String,
    val postedAt: Long
)

object JarvisServiceBus {
    private val _isHandsFreeRunning = MutableStateFlow(false)
    val isHandsFreeRunning: StateFlow<Boolean> = _isHandsFreeRunning.asStateFlow()

    private val _wakeStatus = MutableStateFlow("Hands-free standby")
    val wakeStatus: StateFlow<String> = _wakeStatus.asStateFlow()

    private val _wakeCommands = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val wakeCommands: SharedFlow<String> = _wakeCommands.asSharedFlow()

    private val _capturedNotifications = MutableStateFlow<List<CapturedNotification>>(emptyList())
    val capturedNotifications: StateFlow<List<CapturedNotification>> = _capturedNotifications.asStateFlow()

    private val _isAccessibilityConnected = MutableStateFlow(false)
    val isAccessibilityConnected: StateFlow<Boolean> = _isAccessibilityConnected.asStateFlow()

    private val _pendingAccessibilityAction = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val pendingAccessibilityAction: SharedFlow<Int> = _pendingAccessibilityAction.asSharedFlow()

    fun setHandsFreeRunning(running: Boolean, status: String) {
        _isHandsFreeRunning.value = running
        _wakeStatus.value = status
    }

    fun emitWakeCommand(command: String) {
        _wakeCommands.tryEmit(command)
    }

    fun pushNotification(item: CapturedNotification) {
        val current = _capturedNotifications.value.filterNot { it.key == item.key }
        _capturedNotifications.value = (listOf(item) + current).take(25)
    }

    fun clearNotifications() {
        _capturedNotifications.value = emptyList()
    }

    fun setAccessibilityConnected(connected: Boolean) {
        _isAccessibilityConnected.value = connected
    }

    fun requestGlobalAction(action: Int) {
        _pendingAccessibilityAction.tryEmit(action)
    }
}
