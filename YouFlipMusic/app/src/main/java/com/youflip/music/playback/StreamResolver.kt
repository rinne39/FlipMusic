package com.youflip.music.playback

import android.net.Uri
import com.youflip.music.data.MusicRepository
import com.youflip.music.data.Song
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns `youflip://stream/<videoId>` into a real googlevideo URL just before ExoPlayer opens it.
 * Resolved URLs are cached until shortly before YouTube's `expire` timestamp.
 */
class StreamResolver(
    private val repository: MusicRepository,
    private val netease: com.youflip.music.data.netease.NeteaseRepository,
    private val highQuality: () -> Boolean,
    private val onResolved: (MusicRepository.ResolvedStream) -> Unit,
) {
    private data class Entry(val url: String, val expiresAtMs: Long)
    private val cache = ConcurrentHashMap<String, Entry>()

    fun resolve(videoId: String): Uri {
        val now = System.currentTimeMillis()
        cache[videoId]?.takeIf { it.expiresAtMs > now }?.let { return Uri.parse(it.url) }

        if (com.youflip.music.data.netease.NeteaseRepository.isNetease(videoId)) {
            val stream = netease.streamBlocking(videoId, highQuality())
            // NetEase URLs live `expi` seconds; refresh two minutes early.
            cache[videoId] = Entry(stream.url, now + (stream.expiresInSec - 120).coerceAtLeast(60) * 1000)
            return Uri.parse(stream.url)
        }

        val resolved = repository.resolveBlocking(videoId, highQuality())
        val expire = Uri.parse(resolved.url).getQueryParameter("expire")?.toLongOrNull()
        val expiresAt = expire?.let { it * 1000 - 10 * 60_000 } ?: (now + 60 * 60_000)
        cache[videoId] = Entry(resolved.url, expiresAt)
        onResolved(resolved)
        return Uri.parse(resolved.url)
    }

    fun invalidate(videoId: String) { cache.remove(videoId) }

    companion object {
        const val SCHEME = "youflip"
        fun uriFor(song: Song): Uri = Uri.parse("$SCHEME://stream/${song.id}")
        fun videoIdOf(uri: Uri): String? = if (uri.scheme == SCHEME) uri.lastPathSegment else null
    }
}
