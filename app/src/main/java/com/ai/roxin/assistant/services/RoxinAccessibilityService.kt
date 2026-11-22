package com.ai.roxin.assistant.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class RoxinAccessibilityService : AccessibilityService() {

    companion object {
        var instance: RoxinAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun dumpScreenText(): String {
        val rootNode = rootInActiveWindow ?: return "اسکرین مواد دستیاب نہیں ہے۔"
        val sb = StringBuilder()
        collectText(rootNode, sb)
        return if (sb.isEmpty()) "اسکرین خالی ہے" else sb.toString()
    }

    private fun collectText(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        if (!text.isNullOrEmpty()) sb.append(text).append("\n")
        else if (!desc.isNullOrEmpty()) sb.append(desc).append("\n")
        for (i in 0 until node.childCount) {
            collectText(node.getChild(i), sb)
        }
    }

    fun clickByText(targetText: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(targetText)
        for (node in nodes) {
            if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent = parent.parent
            }
        }
        return false
    }

    fun typeTextIntoField(targetHintOrText: String?, textToType: String): Boolean {
        val root = rootInActiveWindow ?: return false
        if (!targetHintOrText.isNullOrEmpty()) {
            val nodes = root.findAccessibilityNodeInfosByText(targetHintOrText)
            for (node in nodes) {
                if (node.isEditable) {
                    val args = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
                    }
                    return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                }
            }
        }
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            }
            return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
        return false
    }

    fun doGlobalAction(actionType: String): Boolean {
        return when (actionType.lowercase()) {
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            else -> false
        }
    }

    fun launchApp(appNameOrPackage: String): Boolean {
        val pm = packageManager
        val pkg = when (appNameOrPackage.lowercase().trim()) {
            "whatsapp" -> "com.whatsapp"
            "gmail" -> "com.google.android.gm"
            "youtube" -> "com.google.android.youtube"
            "chrome" -> "com.android.chrome"
            "camera" -> "com.android.camera"
            "settings" -> "com.android.settings"
            else -> appNameOrPackage
        }
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        return true
    }
}
