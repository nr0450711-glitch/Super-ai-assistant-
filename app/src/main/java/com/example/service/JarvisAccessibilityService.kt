package com.example.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class JarvisAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onServiceConnected() {
        super.onServiceConnected()
        JarvisServiceBus.setAccessibilityConnected(true)
        scope.launch {
            JarvisServiceBus.pendingAccessibilityAction.collect { action ->
                performGlobalAction(action)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Opt-in navigation actions only; no passive content scraping.
    }

    override fun onInterrupt() {
        JarvisServiceBus.setAccessibilityConnected(false)
    }

    override fun onDestroy() {
        JarvisServiceBus.setAccessibilityConnected(false)
        scope.cancel()
        super.onDestroy()
    }
}
