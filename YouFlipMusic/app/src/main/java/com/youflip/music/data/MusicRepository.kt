package com.youflip.music.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/** All YouTube Music access goes through here (NewPipeExtractor, no account required). */
class MusicRepository {

    private val yt get() = ServiceList.YouTube

    suspend fun search(query: String, filter: SearchFilter): SearchPage = io {
        val handler = yt.searchQHFactory.fromQuery(query, listOf(filter.key), "")
        val info = SearchInfo.getInfo(yt, handler)
        info.relatedItems.toPage()
    }

    suspend fun searchSuggestions(query: String): List<String> = io {
        runCatching { yt.suggestionExtractor.suggestionList(query) }.getOrDefault(emptyList())
    }

    data class CollectionDetail(val collection: MusicCollection, val songs: List<Song>)

    suspend fun collection(url: String, kind: CollectionKind = CollectionKind.PLAYLIST): CollectionDetail = io {
        val info = PlaylistInfo.getInfo(yt, url)
        val songs = info.relatedItems.mapNotNull { it.toSong() }.toMutableList()
        var next = info.nextPage
        // Albums and most playlists are short; fetch a few more pages for long playlists.
        var pages = 0
        while (next != null && pages < 4) {
            val more = PlaylistInfo.getMoreItems(yt, url, next)
            songs += more.items.mapNotNull { it.toSong() }
            next = more.nextPage
            pages++
        }
        val thumb = info.thumbnails.best() ?: songs.firstOrNull()?.thumbnail
        CollectionDetail(
            MusicCollection(url, info.name, info.uploaderName.orEmpty(), thumb, kind),
            songs,
        )
    }

    data class ResolvedStream(val url: String, val song: Song)

    /** Resolves a playable audio URL for a videoId. Blocking — call from a background thread. */
    fun resolveBlocking(videoId: String, preferHighQuality: Boolean): ResolvedStream {
        val info = StreamInfo.getInfo(yt, "https://www.youtube.com/watch?v=$videoId")
        val candidates = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            // Skip dubbed / descriptive tracks, keep the original audio.
            .filter { it.audioTrackType?.name.let { n -> n == null || n == "ORIGINAL" } }
            .ifEmpty { info.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP } }
        val chosen: AudioStream = (if (preferHighQuality) candidates.maxByOrNull { it.averageBitrate }
            else candidates.filter { it.averageBitrate > 0 }.minByOrNull { it.averageBitrate })
            ?: candidates.firstOrNull()
            ?: error("No playable audio stream for $videoId")
        val song = Song(
            id = videoId,
            title = info.name,
            artist = info.uploaderName.orEmpty().removeSuffix(" - Topic"),
            durationSec = info.duration,
            thumbnail = info.thumbnails.best(),
        )
        return ResolvedStream(
            url = chosen.content,
            song = song,
        )
    }

    /** "Radio": songs related to the seed, used for autoplay and the home feed. */
    suspend fun related(videoId: String): List<Song> = io {
        // Prefer the YouTube Music song radio (same as "Start radio" in the official app);
        // fall back to the video's related items, which can contain non-music content.
        val mix = runCatching {
            PlaylistInfo.getInfo(yt, "https://music.youtube.com/watch?v=$videoId&list=RDAMVM$videoId")
                .relatedItems.mapNotNull { it.toSong() }
        }.getOrDefault(emptyList())
        val songs = mix.ifEmpty {
            StreamInfo.getInfo(yt, "https://www.youtube.com/watch?v=$videoId")
                .relatedItems.mapNotNull { (it as? StreamInfoItem)?.toSong() }
        }
        songs.filter { it.id != videoId && it.looksLikeASong() }.distinctBy { it.id }
    }

    /** Drops compilations, livestream replays, podcasts and other long-form videos. */
    private fun Song.looksLikeASong() = durationSec in 1..MAX_SONG_SECONDS

    private fun List<InfoItem>.toPage(): SearchPage {
        val songs = mutableListOf<Song>()
        val collections = mutableListOf<MusicCollection>()
        for (item in this) {
            when (item) {
                is StreamInfoItem -> item.toSong()?.let(songs::add)
                is PlaylistInfoItem -> collections += MusicCollection(
                    url = item.url,
                    title = item.name,
                    subtitle = item.uploaderName.orEmpty(),
                    thumbnail = item.thumbnails.best(),
                    kind = if (item.url.contains("OLAK5uy_")) CollectionKind.ALBUM else CollectionKind.PLAYLIST,
                )
            }
        }
        return SearchPage(songs, collections)
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        private const val MAX_SONG_SECONDS = 600L
        private val idRegex = Regex("(?:v=|youtu\\.be/|/shorts/)([A-Za-z0-9_-]{11})")

        fun videoIdFrom(url: String): String? = idRegex.find(url)?.groupValues?.get(1)


        fun StreamInfoItem.toSong(): Song? {
            if (streamType == StreamType.LIVE_STREAM) return null
            val id = videoIdFrom(url) ?: return null
            return Song(
                id = id,
                title = name,
                artist = uploaderName.orEmpty().removeSuffix(" - Topic"),
                durationSec = duration,
                thumbnail = thumbnails.best(),
            )
        }

        fun List<Image>.best(): String? = maxByOrNull { it.width * it.height }?.url ?: firstOrNull()?.url
    }
}
