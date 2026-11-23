package com.youflip.music.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.youflip.music.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PlayerState(
    val current: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val error: String? = null,
    /** Set while the UI mirrors another app (Apple Music, QQ 音乐…): that app's name. */
    val externalApp: String? = null,
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * UI-side handle on playback. Normally that is [PlaybackService] through a MediaController; when the
 * user has granted notification access and another music app is the one playing, the same state and
 * controls are routed to that app's media session instead, so every screen works as a remote for it.
 */
class PlayerConnection(
    private val context: Context,
    private val scope: CoroutineScope,
    private val external: ExternalSessions? = null,
) {

    private var controller: MediaController? = null
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()
    private val pending = mutableListOf<(MediaController) -> Unit>()

    // The other app's session, when there is one.
    private var remote: MediaController? = null
    private var remoteInfo: ExternalSession? = null
    private var remoteJob: Job? = null
    /** Whether the UI currently follows [remote] rather than our own player. */
    private var useRemote = false

    private val artDir by lazy { File(context.cacheDir, "external_art").apply { mkdirs() } }
    private var artFor: Int = 0
    private var artPath: String? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    fun connect() {
        if (controller != null) return
        scope.launch {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val c = MediaController.Builder(context, token).buildAsync().await()
            controller = c
            c.addListener(listener)
            pending.forEach { it(c) }
            pending.clear()
            refresh()
            while (isActive) {
                if (active()?.isPlaying == true) refreshPosition()
                delay(500)
            }
        }
        external?.let { ext -> scope.launch { ext.session.collect(::attach) } }
    }

    fun release() {
        controller?.release()
        controller = null
        remote?.release()
        remote = null
    }

    private fun attach(info: ExternalSession?) {
        if (info?.token == remoteInfo?.token) { remoteInfo = info; return }
        remoteJob?.cancel()
        remote?.release()
        remote = null
        remoteInfo = info
        if (info == null) { useRemote = false; refresh(); return }
        remoteJob = scope.launch {
            val c = runCatching {
                val token = SessionToken.createSessionToken(context, info.token).await()
                MediaController.Builder(context, token).buildAsync().await()
            }.getOrNull() ?: return@launch
            if (remoteInfo?.token != info.token) { c.release(); return@launch }
            remote = c
            c.addListener(listener)
            refresh()
        }
    }

    private fun active(): MediaController? = if (useRemote) remote ?: controller else controller

    /** Follow whichever player started most recently; stay put while both are paused. */
    private fun choose() {
        val own = controller
        val other = remote
        useRemote = when {
            other == null -> false
            own?.isPlaying == true -> false
            other.isPlaying -> true
            own == null || own.mediaItemCount == 0 -> true
            else -> useRemote
        }
    }

    private fun withController(block: (MediaController) -> Unit) {
        controller?.let(block) ?: pending.add(block)
    }

    /** Transport controls go to whichever player the UI is showing. */
    private fun withActive(block: (MediaController) -> Unit) {
        if (useRemote) remote?.let(block) else withController(block)
    }

    private fun refreshPosition() {
        val c = active() ?: return
        _state.value = _state.value.copy(positionMs = c.currentPosition, durationMs = c.duration.coerceAtLeast(0))
    }

    private fun refresh() {
        choose()
        val c = active() ?: return
        if (useRemote) { refreshRemote(c); return }
        val queue = (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).toSong() }
        _state.value = PlayerState(
            current = c.currentMediaItem?.toSong(),
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition,
            durationMs = c.duration.coerceAtLeast(0),
            queue = queue,
            currentIndex = c.currentMediaItemIndex,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            error = c.playerError?.let { "播放失败：${it.errorCodeName}" },
        )
    }

    private fun refreshRemote(c: MediaController) {
        val info = remoteInfo ?: return
        val current = c.currentMediaItem?.let { externalSong(it, info, c.mediaMetadata, art = true) }
        val queue = (0 until c.mediaItemCount).map { externalSong(c.getMediaItemAt(it), info, null, art = false) }
        val duration = c.duration.takeIf { it > 0 } ?: (c.mediaMetadata.durationMs ?: 0L)
        _state.value = PlayerState(
            current = current?.copy(durationSec = duration / 1000),
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition,
            durationMs = duration.coerceAtLeast(0),
            queue = queue,
            currentIndex = if (queue.isEmpty()) -1 else c.currentMediaItemIndex,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            externalApp = info.appName,
        )
    }

    /** Another app's track as a [Song]; its artwork (a bitmap in the session) is cached to a file for Coil. */
    private fun externalSong(item: MediaItem, info: ExternalSession, playing: androidx.media3.common.MediaMetadata?, art: Boolean): Song {
        val md = playing ?: item.mediaMetadata
        val title = md.title?.toString() ?: md.displayTitle?.toString() ?: ""
        val artist = md.artist?.toString() ?: md.albumArtist?.toString() ?: ""
        val key = item.mediaId.takeIf { it.isNotBlank() && !it.startsWith("androidx://") } ?: "$title|$artist"
        val thumb = when {
            !art -> md.artworkUri?.toString()?.takeIf { it.startsWith("http") }
            md.artworkData != null -> artworkFile(md.artworkData!!)
            else -> md.artworkUri?.toString()?.takeIf { it.startsWith("http") }
        }
        return Song(
            id = "ext:${info.packageName}:$key",
            title = title,
            artist = artist,
            durationSec = (md.durationMs ?: 0L) / 1000,
            thumbnail = thumb,
            album = md.albumTitle?.toString()?.takeIf { it.isNotBlank() },
        )
    }

    private fun artworkFile(bytes: ByteArray): String? {
        val hash = bytes.contentHashCode()
        if (hash == artFor) return artPath
        artFor = hash
        val file = File(artDir, "${Integer.toHexString(hash)}.img")
        artPath = "file://${file.absolutePath}"
        if (!file.exists()) scope.launch {
            withContext(Dispatchers.IO) {
                // Keep only the current cover around.
                artDir.listFiles()?.filter { it != file }?.forEach { it.delete() }
                runCatching { file.writeBytes(bytes) }
            }
            refresh()
        }
        return if (file.exists()) artPath else null
    }

    // ---- Our own queue: always our player, which then becomes the one shown. ----

    fun play(songs: List<Song>, startIndex: Int = 0) = withController { c ->
        useRemote = false
        remote?.takeIf { it.isPlaying }?.pause()
        c.setMediaItems(songs.map(Song::toMediaItem), startIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0)), 0)
        c.prepare()
        c.play()
    }

    fun playNext(song: Song) = withController { c ->
        if (c.mediaItemCount == 0 || useRemote) { play(listOf(song)); return@withController }
        c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
    }

    fun enqueue(song: Song) = withController { c ->
        if (c.mediaItemCount == 0 || useRemote) { play(listOf(song)); return@withController }
        c.addMediaItem(song.toMediaItem())
    }

    // ---- Transport: whichever player is shown. ----

    fun togglePlay() = withActive { c ->
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = withActive { it.seekToNext() }
    fun previous() = withActive { it.seekToPrevious() }
    fun seekTo(ms: Long) = withActive { it.seekTo(ms); refreshPosition() }
    fun skipTo(index: Int) = withActive { it.seekToDefaultPosition(index); it.play() }
    fun remove(index: Int) { if (!useRemote) withController { it.removeMediaItem(index) } }
    fun move(from: Int, to: Int) { if (!useRemote) withController { it.moveMediaItem(from, to) } }
    fun toggleShuffle() = withActive { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun cycleRepeat() = withActive {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
}
