package com.wasimaster.wmkeyboard.core.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.wasimaster.wmkeyboard.core.otp.NotificationOtpCapture
import com.wasimaster.wmkeyboard.core.otp.NotificationOtpMonitor

/**
 * The keyboard's one notification listener, worn by two features:
 *
 *  - The media-control tool needs the *grant*, not the notifications.
 *    [android.media.session.MediaSessionManager.getActiveSessions] and its
 *    change listener both demand the caller name an enabled notification
 *    listener component — the grant is the platform's gate for "this app may
 *    see and control other apps' media". For that feature this class stays
 *    what it always was: an empty stub.
 *
 *  - The one-time-code chip reads arriving notifications' text — on the
 *    device, in this process, never stored — to offer a verification code on
 *    the suggestion strip. That reading happens *only* while its setting is
 *    on, checked here per notification via [NotificationOtpCapture] because
 *    this module cannot see the settings repository.
 *
 * One class rather than one per feature because Android grants notification
 * access per component: a second listener would mean a second, near-identical
 * row on the system screen, and renaming this one would silently revoke the
 * grant existing users already gave. The disclosure copy for both features
 * (`strings_ui.xml`, `common_special_access_*`) names both uses.
 */
class MediaNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // The gate lives out here, not in the monitor: while the code chip is
        // off this method must read nothing of the notification at all.
        if (!NotificationOtpCapture.isEnabled(this)) return
        NotificationOtpMonitor.process(this, sbn)
    }

    companion object {
        /**
         * The live listener, while the system has one bound. Kept so the
         * "clear the notification after use" option can cancel the code's
         * notification — cancellation is a listener method, and the IME
         * service has no other way to reach it.
         */
        @Volatile
        private var instance: MediaNotificationListener? = null

        /** Cancels the notification with [key], if the listener is connected. */
        fun dismissNotification(key: String) {
            runCatching { instance?.cancelNotification(key) }
        }
    }
}
