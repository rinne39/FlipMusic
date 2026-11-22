package com.youflip.music.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.youflip.music.MainActivity
import com.youflip.music.YouFlipApp
import com.youflip.music.data.NewPipeDownloader
import com.youflip.music.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

class PlaybackService : MediaSessionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private lateinit var resolver: StreamResolver
    private var radioLoadingFor: String? = null
    private var lastRetriedId: String? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as YouFlipApp
        val highQuality = app.settings.highQuality.stateIn(scope, SharingStarted.Eagerly, true)
        val autoplay = app.settings.autoplayRadio.stateIn(scope, SharingStarted.Eagerly, true)

        resolver = StreamResolver(app.repository, app.netease, { highQuality.value }) { resolved ->
            // Fill in metadata for items that were queued with only an id (e.g. shared links).
            scope.launch { enrichMetadata(resolved.song) }
        }

        val http = OkHttpDataSource.Factory(app.httpClient).setUserAgent(NewPipeDownloader.USER_AGENT)
        val resolving = ResolvingDataSource.Factory(ChunkedYoutubeDataSource.Factory(http)) { spec: DataSpec ->
            val id = StreamResolver.videoIdOf(spec.uri)
            if (id == null) spec else {
                val real = try { resolver.resolve(id) } catch (e: Exception) { throw IOException(e) }
                if (com.youflip.music.data.netease.NeteaseRepository.isNetease(id)) scope.launch { enrichNetease(id) }
                spec.withUri(real)
            }
        }

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Items opened from a bare link have no title yet; they are recorded once metadata arrives.
                mediaItem?.toSong()?.takeIf { it.title.isNotBlank() }?.let { song -> scope.launch { app.library.addHistory(song) } }
                if (autoplay.value) maybeExtendWithRadio()
            }

            override fun onPlayerError(error: PlaybackException) {
                // Stream URLs can expire or get rejected; re-resolve once and retry.
                val id = player.currentMediaItem?.mediaId ?: return
                if (lastRetriedId != id) {
                    lastRetriedId = id
                    resolver.invalidate(id)
                    val pos = player.currentPosition
                    player.prepare()
                    player.seekTo(pos)
                    player.play()
                } else if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
        })

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()
    }

    private fun maybeExtendWithRadio() {
        val current = player.currentMediaItem ?: return
        if (player.mediaItemCount - player.currentMediaItemIndex > 2) return
        if (radioLoadingFor == current.mediaId) return
        radioLoadingFor = current.mediaId
        val app = application as YouFlipApp
        scope.launch {
            val queued = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
            val related = runCatching {
                if (com.youflip.music.data.netease.NeteaseRepository.isNetease(current.mediaId)) app.netease.related(current.mediaId)
                else app.repository.related(current.mediaId)
            }.getOrDefault(emptyList())
            val fresh = related.filterNot { it.id in queued }.take(15)
            if (fresh.isNotEmpty()) player.addMediaItems(fresh.map { it.toMediaItem() })
        }
    }

    /** NetEase items queued from a bare link: fetch title/artist/artwork. */
    private suspend fun enrichNetease(id: String) {
        val needs = (0 until player.mediaItemCount).any { player.getMediaItemAt(it).let { m -> m.mediaId == id && m.mediaMetadata.title.isNullOrBlank() } }
        if (!needs) return
        val song = runCatching { (application as YouFlipApp).netease.songDetails(listOf(id)).firstOrNull() }.getOrNull() ?: return
        enrichMetadata(song)
    }

    private fun enrichMetadata(song: Song) {
        for (i in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(i)
            if (item.mediaId == song.id && item.mediaMetadata.title.isNullOrBlank()) {
                player.replaceMediaItem(i, song.toMediaItem())
                if (i == player.currentMediaItemIndex) {
                    scope.launch { (application as YouFlipApp).library.addHistory(song) }
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session.release()
        player.release()
        super.onDestroy()
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            // Controllers strip URIs; rebuild them from the mediaId (= YouTube videoId).
            Futures.immediateFuture(mediaItems.map { item ->
                item.buildUpon().setUri(StreamResolver.uriFor(Song(item.mediaId, "", ""))).build()
            }.toMutableList())
    }
}

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(StreamResolver.uriFor(this))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri((com.youflip.music.data.Thumbs.fallback(artworkHd) ?: artworkHd)?.let(android.net.Uri::parse))
            .setDurationMs(if (durationSec > 0) durationSec * 1000 else null)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(Bundle().apply { putString("thumb", thumbnail) })
            .build()
    )
    .build()

fun MediaItem.toSong(): Song? {
    if (mediaId.isBlank()) return null
    val md = mediaMetadata
    return Song(
        id = mediaId,
        title = md.title?.toString().orEmpty(),
        artist = md.artist?.toString().orEmpty(),
        durationSec = (md.durationMs ?: 0L) / 1000,
        thumbnail = md.extras?.getString("thumb") ?: md.artworkUri?.toString(),
        album = md.albumTitle?.toString()?.takeIf { it.isNotBlank() },
    )
}
