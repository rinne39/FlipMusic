package com.youflip.music.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

data class Account(val name: String, val handle: String?, val avatar: String?)

/** A titled row of the YouTube Music home feed. */
data class Shelf(val title: String, val songs: List<Song>, val collections: List<MusicCollection>)

/** YouTube Music features backed by InnerTube; account-only calls throw [NotSignedInException] when signed out. */
class YtmRepository(private val client: YtmClient) {

    suspend fun account(): Account? = io {
        val r = client.call("account/account_menu", requireAuth = true)
        val h = r.findFirst("activeAccountHeaderRenderer") ?: return@io null
        Account(
            name = h.at("accountName").runsText() ?: return@io null,
            handle = h.at("channelHandle").runsText(),
            avatar = h.at("accountPhoto").bestThumbnail(),
        )
    }

    /** Personalised when signed in, YouTube Music's generic charts/moods otherwise. */
    suspend fun home(): List<Shelf> = io {
        val first = client.browse("FEmusic_home")
        val shelves = parseShelves(first).toMutableList()
        // Home loads more shelves lazily; one extra page gives a fuller feed.
        nextSectionToken(first)?.let { token ->
            runCatching { shelves += parseShelves(client.continuation(token)) }
        }
        shelves.filter { it.songs.isNotEmpty() || it.collections.isNotEmpty() }
    }

    suspend fun likedSongs(): List<Song> = io { songsWithContinuations("VLLM", requireAuth = true, maxPages = 20).songs }

    suspend fun history(): List<Song> = io {
        client.browse("FEmusic_history", requireAuth = true).findAll("musicResponsiveListItemRenderer").mapNotNull { parseSong(it) }
    }

    suspend fun libraryPlaylists(): List<MusicCollection> = io {
        val page = client.browse("FEmusic_liked_playlists", requireAuth = true)
        page.findAll("musicTwoRowItemRenderer").mapNotNull { parseCollection(it) }
    }

    /**
     * Album (MPRE…) or playlist (VL…) page; private playlists work when signed in.
     * [onProgress] gets the list after the first page and after each further page, so long playlists show at once.
     */
    suspend fun collection(
        browseId: String,
        onProgress: (MusicRepository.CollectionDetail) -> Unit = {},
    ): MusicRepository.CollectionDetail = io {
        var build: ((List<Song>) -> MusicRepository.CollectionDetail)? = null
        val page = songsWithContinuations(browseId, requireAuth = false, maxPages = 30) { first, songs ->
            val b = build ?: collectionBuilder(browseId, first).also { build = it }
            onProgress(b(songs))
        }
        (build ?: collectionBuilder(browseId, page.first))(page.songs)
    }

    private fun collectionBuilder(browseId: String, first: JsonElement): (List<Song>) -> MusicRepository.CollectionDetail {
        val header = first.findFirst("musicResponsiveHeaderRenderer") ?: first.findFirst("musicDetailHeaderRenderer")
        val title = header.at("title").runsText() ?: ""
        val owner = header.at("straplineTextOne").runsText() ?: header.at("subtitle").runsText().orEmpty()
        val thumb = header.bestThumbnail()
        val kind = if (browseId.startsWith("MPRE")) CollectionKind.ALBUM else CollectionKind.PLAYLIST
        val info = MusicCollection(url = "${ORIGIN}/browse/$browseId", title = title, subtitle = owner, thumbnail = thumb, kind = kind, browseId = browseId)
        // Album tracks carry neither artwork nor (usually) the artist: inherit them from the header.
        return { songs -> MusicRepository.CollectionDetail(info, songs.map { s -> s.copy(thumbnail = s.thumbnail ?: thumb, artist = s.artist.ifBlank { owner }) }) }
    }

    suspend fun setLiked(videoId: String, liked: Boolean) = io {
        client.call(
            if (liked) "like/like" else "like/removelike",
            buildJsonObject { putJsonObject("target") { put("videoId", videoId) } },
            requireAuth = true,
        )
        Unit
    }

    // ---------------- parsing ----------------

    private class Page(val first: JsonElement, val songs: List<Song>)

    private fun songsWithContinuations(
        browseId: String, requireAuth: Boolean, maxPages: Int,
        onPage: (first: JsonElement, songs: List<Song>) -> Unit = { _, _ -> },
    ): Page {
        val first = client.browse(browseId, requireAuth)
        val songs = first.findAll("musicResponsiveListItemRenderer").mapNotNull { parseSong(it) }.toMutableList()
        var token = shelfToken(first)
        if (token != null) onPage(first, songs.distinctBy { it.id })
        var pages = 0
        while (token != null && pages < maxPages) {
            val next = client.continuation(token, requireAuth)
            val more = next.findAll("musicResponsiveListItemRenderer").mapNotNull { parseSong(it) }
            if (more.isEmpty()) break
            songs += more
            token = shelfToken(next)
            pages++
            if (token != null) onPage(first, songs.distinctBy { it.id })
        }
        return Page(first, songs.distinctBy { it.id })
    }

    /** Continuation of the track list itself (not the "related playlists" section below it). */
    private fun shelfToken(page: JsonElement): String? {
        page.findAll("continuationItemRenderer").firstNotNullOfOrNull {
            it.at("continuationEndpoint", "continuationCommand", "token").str
        }?.let { return it }
        for (key in listOf("musicPlaylistShelfRenderer", "musicShelfRenderer", "musicPlaylistShelfContinuation", "musicShelfContinuation")) {
            page.findAll(key).firstNotNullOfOrNull { it.at("continuations", 0, "nextContinuationData", "continuation").str }
                ?.let { return it }
        }
        return null
    }

    private fun nextSectionToken(page: JsonElement): String? =
        (page.findFirst("sectionListRenderer") ?: page.findFirst("sectionListContinuation"))
            .at("continuations", 0, "nextContinuationData", "continuation").str

    private fun parseShelves(page: JsonElement): List<Shelf> =
        page.findAll("musicCarouselShelfRenderer").map { shelf ->
            val title = shelf.at("header", "musicCarouselShelfBasicHeaderRenderer", "title").runsText().orEmpty()
            val contents = (shelf.at("contents") as? JsonArray).orEmpty()
            val songs = mutableListOf<Song>()
            val collections = mutableListOf<MusicCollection>()
            for (item in contents) {
                item.at("musicResponsiveListItemRenderer")?.let { r -> parseSong(r)?.let(songs::add) }
                item.at("musicTwoRowItemRenderer")?.let { r ->
                    parseCollection(r)?.let(collections::add) ?: parseTwoRowSong(r)?.let(songs::add)
                }
            }
            Shelf(title, songs, collections)
        }

    private fun parseSong(r: JsonElement): Song? {
        val flex = (r.at("flexColumns") as? JsonArray).orEmpty()
            .map { it.at("musicResponsiveListItemFlexColumnRenderer", "text") }
        val videoId = r.at("playlistItemData", "videoId").str
            ?: flex.firstOrNull()?.findStrings("videoId")?.firstOrNull()
            ?: r.at("overlay")?.findStrings("videoId")?.firstOrNull()
            ?: return null
        val title = flex.getOrNull(0).runsText() ?: return null
        val details = flex.drop(1).mapNotNull { it.runsText() }
            .flatMap { it.split(" • ") }.map { it.trim() }.filter { it.isNotEmpty() }
        val duration = (r.at("fixedColumns", 0, "musicResponsiveListItemFixedColumnRenderer", "text").runsText()
            ?: details.firstOrNull { DURATION.matches(it) })?.let(::parseDuration) ?: 0
        val artist = details.firstOrNull { it !in TYPE_WORDS && !DURATION.matches(it) && !it.contains("播放") && !it.contains("plays", true) && !it.contains("views", true) }.orEmpty()
        return Song(videoId, title, artist, duration, r.at("thumbnail").bestThumbnail())
    }

    private fun parseCollection(r: JsonElement): MusicCollection? {
        val browseId = r.at("navigationEndpoint", "browseEndpoint", "browseId").str
            ?: r.at("title", "runs", 0, "navigationEndpoint", "browseEndpoint", "browseId").str
            ?: return null
        val kind = when {
            browseId.startsWith("MPRE") -> CollectionKind.ALBUM
            browseId.startsWith("VL") -> CollectionKind.PLAYLIST
            else -> return null // artists, podcasts, "new playlist" button…
        }
        return MusicCollection(
            url = "$ORIGIN/browse/$browseId",
            title = r.at("title").runsText() ?: return null,
            subtitle = r.at("subtitle").runsText().orEmpty(),
            thumbnail = r.at("thumbnailRenderer").bestThumbnail(),
            kind = kind,
            browseId = browseId,
        )
    }

    private fun parseTwoRowSong(r: JsonElement): Song? {
        val id = r.at("navigationEndpoint", "watchEndpoint", "videoId").str ?: return null
        val subtitle = r.at("subtitle").runsText().orEmpty().split(" • ").map { it.trim() }
        return Song(
            id = id,
            title = r.at("title").runsText() ?: return null,
            artist = subtitle.firstOrNull { it !in TYPE_WORDS }.orEmpty(),
            thumbnail = r.at("thumbnailRenderer").bestThumbnail(),
        )
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        private const val ORIGIN = "https://music.youtube.com"
        private val DURATION = Regex("^\\d{1,2}(:\\d{2}){1,2}$")
        private val TYPE_WORDS = setOf("歌曲", "Song", "视频", "Video", "影片", "單曲", "Single", "EP", "专辑", "Album", "歌单", "Playlist")

        private fun parseDuration(s: String): Long =
            s.split(':').fold(0L) { acc, part -> acc * 60 + (part.toLongOrNull() ?: 0) }

        /** `…/playlist?list=PL…` (NewPipe search results) → InnerTube browse id. */
        fun browseIdFromUrl(url: String): String? =
            Regex("[?&]list=([A-Za-z0-9_-]+)").find(url)?.groupValues?.get(1)?.let { "VL$it" }
    }
}
