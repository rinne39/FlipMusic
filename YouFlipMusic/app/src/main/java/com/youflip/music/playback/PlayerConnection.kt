package com.youflip.music.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.youflip.music.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** UI-side handle on [PlaybackService] through a MediaController. */
class PlayerConnection(private val context: Context, private val scope: CoroutineScope) {

    private var controller: MediaController? = null
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()
    private val pending = mutableListOf<(MediaController) -> Unit>()

    fun connect() {
        if (controller != null) return
        scope.launch {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val c = MediaController.Builder(context, token).buildAsync().await()
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = refresh()
            })
            pending.forEach { it(c) }
            pending.clear()
            refresh()
            while (isActive) {
                if (c.isPlaying) refreshPosition()
                delay(500)
            }
        }
    }

    fun release() {
        controller?.release()
        controller = null
    }

    private fun withController(block: (MediaController) -> Unit) {
        controller?.let(block) ?: pending.add(block)
    }

    private fun refreshPosition() {
        val c = controller ?: return
        _state.value = _state.value.copy(positionMs = c.currentPosition, durationMs = c.duration.coerceAtLeast(0))
    }

    private fun refresh() {
        val c = controller ?: return
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

    fun play(songs: List<Song>, startIndex: Int = 0) = withController { c ->
        c.setMediaItems(songs.map(Song::toMediaItem), startIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0)), 0)
        c.prepare()
        c.play()
    }

    fun playNext(song: Song) = withController { c ->
        if (c.mediaItemCount == 0) { play(listOf(song)); return@withController }
        c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
    }

    fun enqueue(song: Song) = withController { c ->
        if (c.mediaItemCount == 0) { play(listOf(song)); return@withController }
        c.addMediaItem(song.toMediaItem())
    }

    fun togglePlay() = withController { c ->
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms); refreshPosition() }
    fun skipTo(index: Int) = withController { it.seekToDefaultPosition(index); it.play() }
    fun remove(index: Int) = withController { it.removeMediaItem(index) }
    fun move(from: Int, to: Int) = withController { it.moveMediaItem(from, to) }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
}
