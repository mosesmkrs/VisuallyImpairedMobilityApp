package com.example.newapp

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class MyAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            if (it.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                announce("Button clicked: ${it.text}")
            }
        }
    }

    override fun onInterrupt() {}

    private fun announce(message: String) {
        performGlobalAction(GLOBAL_ACTION_BACK) // Example action
    }
}
