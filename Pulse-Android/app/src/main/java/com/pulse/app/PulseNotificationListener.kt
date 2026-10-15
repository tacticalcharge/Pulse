package com.pulse.app

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification

class PulseNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName || !PulseSettings.notificationsOn) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        // We deliberately show only an app/title indicator, never store message bodies.
        PulseOverlayService.instance?.briefNotification(title.take(60))
    }
}
