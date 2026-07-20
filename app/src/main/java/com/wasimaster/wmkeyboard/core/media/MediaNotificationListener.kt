package com.wasimaster.wmkeyboard.core.media

import android.service.notification.NotificationListenerService

/**
 * A do-nothing notification listener that exists only so the media-control
 * tool can read the active [android.media.session.MediaSession]s.
 *
 * [android.media.session.MediaSessionManager.getActiveSessions] and its
 * change listener both demand the caller name an *enabled* notification
 * listener component — that grant is the platform's gate for "this app may
 * see and control other apps' media". This class is that component. It never
 * inspects a single notification: the keyboard has no interest in them, and
 * overriding the posted/removed callbacks would only invite the impression
 * that it does. The user turns the grant on in Settings › Notification
 * access, and can revoke it there at any time.
 */
class MediaNotificationListener : NotificationListenerService()
