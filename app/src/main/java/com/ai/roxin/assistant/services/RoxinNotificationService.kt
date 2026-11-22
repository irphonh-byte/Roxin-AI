package com.ai.roxin.assistant.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class RoxinNotificationService : NotificationListenerService() {
    companion object {
        var lastNotificationSender: String = ""
        var lastNotificationText: String = ""
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val extras = sbn?.notification?.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (title.isNotEmpty() && text.isNotEmpty()) {
            lastNotificationSender = title
            lastNotificationText = text
        }
    }
}
