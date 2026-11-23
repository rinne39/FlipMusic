package com.youflip.music.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.media.session.MediaController as PlatformController

/**
 * Only exists so the user can grant notification access: with it, Android lets us see and control
 * the media sessions of other apps (Apple Music, QQ 音乐, Spotify…). It doesn't read notifications.
 */
class MediaListener : NotificationListenerService()

/** One other app's media session, as picked by [ExternalSessions]. */
data class ExternalSession(val token: android.media.session.MediaSession.Token, val packageName: String, val appName: String)

/**
 * Watches the system's active media sessions and picks the one Flip Music should mirror:
 * the session that is playing, else the most recent one. Our own session is ignored.
 */
class ExternalSessions(private val context: Context) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, MediaListener::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val _enabled = MutableStateFlow(false)
    /** Whether the user has granted notification access (the switch for this feature). */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _session = MutableStateFlow<ExternalSession?>(null)
    val session: StateFlow<ExternalSession?> = _session.asStateFlow()

    private var watching = false
    private val callbacks = mutableMapOf<PlatformController, PlatformController.Callback>()

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list -> update(list.orEmpty()) }

    /** Call when the app comes to the foreground: picks up a permission granted in Settings. */
    fun refresh() {
        val granted = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
        _enabled.value = granted
        if (granted && !watching) {
            runCatching {
                manager.addOnActiveSessionsChangedListener(sessionsChanged, listener, main)
                watching = true
            }
        } else if (!granted && watching) {
            runCatching { manager.removeOnActiveSessionsChangedListener(sessionsChanged) }
            watching = false
        }
        update(if (granted) runCatching { manager.getActiveSessions(listener) }.getOrDefault(emptyList()) else emptyList())
    }

    private fun update(all: List<PlatformController>) {
        val others = all.filter { it.packageName != context.packageName }
        // Follow play/pause in every app, so starting music elsewhere switches over right away.
        callbacks.keys.filter { it !in others }.forEach { c -> callbacks.remove(c)?.let(c::unregisterCallback) }
        others.filter { it !in callbacks }.forEach { c ->
            val cb = object : PlatformController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = pick(callbacks.keys.toList())
                override fun onSessionDestroyed() = refresh()
            }
            c.registerCallback(cb, main)
            callbacks[c] = cb
        }
        pick(others)
    }

    private fun pick(others: List<PlatformController>) {
        val playing = others.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val current = _session.value
        val chosen = playing
            ?: others.firstOrNull { it.sessionToken == current?.token }
            ?: others.firstOrNull()
        _session.value = chosen?.let { c ->
            if (current?.token == c.sessionToken) current
            else ExternalSession(c.sessionToken, c.packageName, appName(c.packageName))
        }
    }

    private fun appName(pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** The system page where the user grants Flip Music notification access. */
    fun settingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= 30)
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener.flattenToString())
        else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}
