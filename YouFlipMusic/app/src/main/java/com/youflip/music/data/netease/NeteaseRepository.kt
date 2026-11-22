package com.youflip.music.data.netease

import com.youflip.music.data.Account
import com.youflip.music.data.CollectionKind
import com.youflip.music.data.LyricLine
import com.youflip.music.data.Lyrics
import com.youflip.music.data.LyricsRepository
import com.youflip.music.data.MusicCollection
import com.youflip.music.data.MusicRepository
import com.youflip.music.data.SearchFilter
import com.youflip.music.data.SearchPage
import com.youflip.music.data.Shelf
import com.youflip.music.data.Song
import com.youflip.music.data.at
import com.youflip.music.data.auth.AccountStore
import com.youflip.music.data.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** NetEase Cloud Music features. Song ids are prefixed with [PREFIX] so they never clash with YouTube ids. */
class NeteaseRepository(private val client: NeteaseClient, private val account: AccountStore) {

    suspend fun logout() = io {
        runCatching { client.call("logout") }
        account.clear()
    }

    // ---------------- account & library ----------------

    private var cachedUid: Long? = null

    private fun uid(): Long {
        cachedUid?.let { return it }
        val r = client.call("w/nuser/account/get", requireAuth = true)
        val id = r.at("account", "id").str?.toLongOrNull() ?: r.at("profile", "userId").str?.toLongOrNull()
            ?: throw com.youflip.music.data.NotSignedInException(NeteaseClient.NOT_SIGNED_IN)
        return id.also { cachedUid = it }
    }

    suspend fun account(): Account? = io {
        cachedUid = null; cachedLikedPid = null
        val r = client.call("w/nuser/account/get", requireAuth = true)
        val p = r.at("profile") ?: return@io null
        cachedUid = p.at("userId").str?.toLongOrNull()
        Account(name = p.at("nickname").str ?: return@io null, handle = "网易云音乐", avatar = p.at("avatarUrl").str?.https())
    }

    /** Ids of the user's liked songs, newest first. */
    suspend fun likedIds(): List<String> = io {
        val r = client.call("song/like/get", buildJsonObject { put("uid", uid()) }, requireAuth = true)
        (r.at("ids") as? JsonArray).orEmpty().mapNotNull { it.str?.let { id -> PREFIX + id } }
    }

    suspend fun likedSongs(): List<Song> = songDetails(likedIds())

    suspend fun playlists(): List<MusicCollection> = io {
        val r = client.call("user/playlist", buildJsonObject { put("uid", uid()); put("limit", 200); put("offset", 0); put("includeVideo", true) }, requireAuth = true)
        (r.at("playlist") as? JsonArray).orEmpty().mapNotNull { parsePlaylist(it) }
    }

    /** Songs played in the last week (falls back to all-time). */
    suspend fun history(): List<Song> = io {
        fun record(type: Int, key: String) = (client.call("v1/play/record", buildJsonObject { put("uid", uid()); put("type", type) }, requireAuth = true)
            .at(key) as? JsonArray).orEmpty().mapNotNull { parseSong(it.at("song")) }
        record(1, "weekData").ifEmpty { record(0, "allData") }
    }

    suspend fun setLiked(id: String, liked: Boolean) = io {
        val r = client.call("radio/like", buildJsonObject {
            put("alg", "itembased"); put("trackId", rawId(id)); put("like", liked); put("time", "3")
        }, requireAuth = true)
        if (r.code() == 200) return@io
        android.util.Log.w("FlipMusic", "radio/like -> $r")
        // -460: NetEase's risk control refused the like endpoint. Fall back to what the web player's
        // "add to playlist" does: add/remove the track in the user's own 我喜欢的音乐 playlist.
        if (r.code() == -460) {
            val m = client.call("playlist/manipulate/tracks", buildJsonObject {
                put("op", if (liked) "add" else "del"); put("pid", likedPlaylistId()); put("trackIds", "[${rawId(id)}]"); put("imme", "true")
            }, requireAuth = true)
            android.util.Log.w("FlipMusic", "playlist/manipulate/tracks -> $m")
            // 502 = already in / not in the playlist: the end state is what was asked for.
            if (m.code() == 200 || m.code() == 502) return@io
            throw NeteaseApiException(m.code() ?: -1, m.message() ?: r.message() ?: "网易云返回 ${m.code()}")
        }
        throw NeteaseApiException(r.code() ?: -1, r.message() ?: "网易云返回 ${r.code()}")
    }

    /** A plain anonymous web-API call (used by the lyrics lookup for other sources). Blocking. */
    fun webCall(path: String, vararg args: Pair<String, String>): JsonElement =
        client.call(path, buildJsonObject { args.forEach { (k, v) -> put(k, v) } })

    private var cachedLikedPid: Long? = null

    /** Id of the user's 我喜欢的音乐 playlist (specialType 5, always first in their list). */
    private fun likedPlaylistId(): Long {
        cachedLikedPid?.let { return it }
        val lists = (client.call("user/playlist", buildJsonObject { put("uid", uid()); put("limit", 30); put("offset", 0) }, requireAuth = true)
            .at("playlist") as? JsonArray).orEmpty()
        val liked = lists.firstOrNull { it.at("specialType").str == "5" } ?: lists.firstOrNull()
        val pid = liked?.at("id").str?.toLongOrNull() ?: throw NeteaseApiException(-1, "找不到“我喜欢的音乐”歌单")
        return pid.also { cachedLikedPid = it }
    }

    // ---------------- discovery ----------------

    suspend fun home(): List<Shelf> = io {
        val shelves = mutableListOf<Shelf>()
        if (account.isSignedIn) runCatching {
            val daily = (client.call("v3/discovery/recommend/songs", requireAuth = true).at("data", "dailySongs") as? JsonArray)
                .orEmpty().mapNotNull { parseSong(it) }
            if (daily.isNotEmpty()) shelves += Shelf("每日推荐", daily, emptyList())
        }
        runCatching {
            val lists = (client.call("personalized/playlist", buildJsonObject { put("limit", 15); put("total", true); put("n", 1000) })
                .at("result") as? JsonArray).orEmpty().mapNotNull { parsePlaylist(it) }
            if (lists.isNotEmpty()) shelves += Shelf("推荐歌单", emptyList(), lists)
        }
        runCatching {
            val tops = (client.call("toplist").at("list") as? JsonArray).orEmpty().mapNotNull { parsePlaylist(it) }
            if (tops.isNotEmpty()) shelves += Shelf("排行榜", emptyList(), tops)
        }
        shelves
    }

    /** Similar songs, or the personal FM when signed in and NetEase has none. */
    suspend fun related(id: String): List<Song> = io {
        val simi = runCatching {
            (client.call("v1/discovery/simiSong", buildJsonObject { put("songid", rawId(id)); put("limit", 30); put("offset", 0) })
                .at("songs") as? JsonArray).orEmpty().mapNotNull { parseSong(it) }
        }.getOrDefault(emptyList())
        if (simi.isNotEmpty() || !account.isSignedIn) return@io simi
        runCatching {
            (0 until 3).flatMap { (client.call("v1/radio/get", requireAuth = true).at("data") as? JsonArray).orEmpty().mapNotNull { parseSong(it) } }
                .distinctBy { it.id }
        }.getOrDefault(emptyList())
    }

    suspend fun search(query: String, filter: SearchFilter): SearchPage = io {
        val type = when (filter) { SearchFilter.ALBUMS -> 10; SearchFilter.PLAYLISTS -> 1000; else -> 1 }
        val r = client.call("cloudsearch/pc", buildJsonObject { put("s", query); put("type", type); put("limit", 40); put("offset", 0) })
        val result = r.at("result")
        when (type) {
            10 -> SearchPage(collections = (result.at("albums") as? JsonArray).orEmpty().mapNotNull { parseAlbum(it) })
            1000 -> SearchPage(collections = (result.at("playlists") as? JsonArray).orEmpty().mapNotNull { parsePlaylist(it) })
            else -> SearchPage(songs = (result.at("songs") as? JsonArray).orEmpty().mapNotNull { parseSong(it) })
        }
    }

    suspend fun suggestions(query: String): List<String> = io {
        val r = runCatching { client.call("search/suggest/web", buildJsonObject { put("s", query) }) }.getOrNull() ?: return@io emptyList()
        val songs = (r.at("result", "songs") as? JsonArray).orEmpty().mapNotNull { it.at("name").str }
        val artists = (r.at("result", "artists") as? JsonArray).orEmpty().mapNotNull { it.at("name").str }
        (artists + songs).distinct()
    }

    // ---------------- collections ----------------

    suspend fun collection(browseId: String, onProgress: (MusicRepository.CollectionDetail) -> Unit = {}): MusicRepository.CollectionDetail = io {
        val id = browseId.substringAfter(':')
        if (browseId.startsWith(ALBUM)) {
            val r = client.call("v1/album/$id")
            val al = r.at("album")
            val info = MusicCollection(
                url = "${NeteaseClient.ORIGIN}/album?id=$id", title = al.at("name").str.orEmpty(),
                subtitle = al.at("artist", "name").str.orEmpty(), thumbnail = al.at("picUrl").str?.let(::thumb),
                kind = CollectionKind.ALBUM, browseId = browseId,
            )
            val albumName = info.title
            MusicRepository.CollectionDetail(info, (r.at("songs") as? JsonArray).orEmpty().mapNotNull { parseSong(it)?.let { s -> s.copy(album = s.album ?: albumName) } })
        } else {
            val pl = client.call("v6/playlist/detail", buildJsonObject { put("id", id); put("n", 100000); put("s", 0) }).at("playlist")
                ?: throw NeteaseApiException(404, "歌单不存在或无权访问")
            val info = MusicCollection(
                url = "${NeteaseClient.ORIGIN}/playlist?id=$id", title = pl.at("name").str.orEmpty(),
                subtitle = pl.at("creator", "nickname").str.orEmpty(), thumbnail = pl.at("coverImgUrl").str?.let(::thumb),
                kind = CollectionKind.PLAYLIST, browseId = browseId,
            )
            val ids = (pl.at("trackIds") as? JsonArray).orEmpty().mapNotNull { it.at("id").str?.let { s -> PREFIX + s } }
            if (ids.isEmpty()) return@io MusicRepository.CollectionDetail(info, (pl.at("tracks") as? JsonArray).orEmpty().mapNotNull { parseSong(it) })
            val songs = mutableListOf<Song>()
            for (chunk in ids.chunked(DETAIL_CHUNK)) {
                songs += songDetailsBlocking(chunk)
                onProgress(MusicRepository.CollectionDetail(info, songs.toList()))
            }
            MusicRepository.CollectionDetail(info, songs)
        }
    }

    suspend fun songDetails(ids: List<String>): List<Song> = io { ids.chunked(DETAIL_CHUNK).flatMap { songDetailsBlocking(it) } }

    private fun songDetailsBlocking(ids: List<String>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        val c = buildJsonArray { ids.forEach { add(buildJsonObject { put("id", rawId(it)) }) } }.toString()
        val r = client.call("v3/song/detail", buildJsonObject { put("c", c) })
        val byId = (r.at("songs") as? JsonArray).orEmpty().mapNotNull { parseSong(it) }.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    // ---------------- playback & lyrics ----------------

    data class Stream(val url: String, val expiresInSec: Long)

    /** Blocking: called from the player's data-source thread. */
    fun streamBlocking(id: String, highQuality: Boolean): Stream {
        val r = client.call("song/enhance/player/url/v1", buildJsonObject {
            put("ids", "[${rawId(id)}]"); put("level", if (highQuality) "exhigh" else "standard"); put("encodeType", "aac")
        })
        val d = (r.at("data") as? JsonArray)?.firstOrNull()
        val url = d.at("url").str ?: throw NeteaseApiException(
            d.at("code").str?.toIntOrNull() ?: 404,
            if (account.isSignedIn) "这首歌暂时无法播放（无版权或需要会员）" else "这首歌需要登录网易云音乐后播放",
        )
        return Stream(url.https(), d.at("expi").str?.toLongOrNull() ?: 1200)
    }


    suspend fun lyrics(song: Song): Lyrics? = io {
        val r = client.call("song/lyric", buildJsonObject { put("id", rawId(song.id)); put("lv", -1); put("tv", -1) })
        val lrc = r.at("lrc", "lyric").str?.takeIf { it.isNotBlank() } ?: return@io null
        val synced = LyricsRepository.parseLrc(lrc).filterNot { CREDIT.containsMatchIn(it.text) }
        when {
            synced.count { it.text.isNotBlank() } >= 2 -> Lyrics(synced, true, song.album)
            else -> lrc.lines().map { it.replace(Regex("\\[[^]]*]"), "").trim() }.filter { it.isNotEmpty() }
                .takeIf { it.isNotEmpty() }?.let { lines -> Lyrics(lines.map { LyricLine(-1, it) }, false, song.album) }
        }
    }

    // ---------------- parsing ----------------

    private fun parseSong(e: JsonElement?): Song? {
        if (e == null) return null
        val id = e.at("id").str ?: return null
        val name = e.at("name").str ?: return null
        val artists = ((e.at("ar") ?: e.at("artists")) as? JsonArray).orEmpty().mapNotNull { it.at("name").str }.joinToString(" / ")
        val al = e.at("al") ?: e.at("album")
        val durationMs = (e.at("dt") ?: e.at("duration")).str?.toLongOrNull() ?: 0
        return Song(
            id = PREFIX + id, title = name, artist = artists, durationSec = durationMs / 1000,
            thumbnail = al.at("picUrl").str?.let(::thumb), album = al.at("name").str?.takeIf { it.isNotBlank() },
        )
    }

    private fun parsePlaylist(e: JsonElement): MusicCollection? {
        val id = e.at("id").str ?: return null
        val count = e.at("trackCount").str?.toIntOrNull()
        val owner = e.at("creator", "nickname").str ?: e.at("updateFrequency").str ?: e.at("copywriter").str
        return MusicCollection(
            url = "${NeteaseClient.ORIGIN}/playlist?id=$id", title = e.at("name").str ?: return null,
            subtitle = listOfNotNull(owner?.takeIf { it.isNotBlank() }, count?.let { "$it 首" }).joinToString(" · "),
            thumbnail = (e.at("coverImgUrl") ?: e.at("picUrl")).str?.let(::thumb),
            kind = CollectionKind.PLAYLIST, browseId = PLAYLIST + id,
        )
    }

    private fun parseAlbum(e: JsonElement): MusicCollection? {
        val id = e.at("id").str ?: return null
        return MusicCollection(
            url = "${NeteaseClient.ORIGIN}/album?id=$id", title = e.at("name").str ?: return null,
            subtitle = e.at("artist", "name").str.orEmpty(), thumbnail = e.at("picUrl").str?.let(::thumb),
            kind = CollectionKind.ALBUM, browseId = ALBUM + id,
        )
    }

    private fun JsonElement.code(): Int? = at("code").str?.toIntOrNull()
    private fun JsonElement.message(): String? = (at("message") ?: at("msg")).str?.takeIf { it.isNotBlank() }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        const val PREFIX = "ncm:"
        const val PLAYLIST = "ncmpl:"
        const val ALBUM = "ncmal:"
        private const val DETAIL_CHUNK = 400
        /** Login responses that mean "verify this login another way". */
        private val CREDIT = Regex("^\\s*(作词|作曲|编曲|制作人|词|曲|混音|录音|和声|吉他|贝斯|鼓|弦乐|监制|母带|Lyrics|Composer|Producer|Arranger)[^:：]{0,12}[:：]", RegexOption.IGNORE_CASE)

        fun isNetease(id: String) = id.startsWith(PREFIX)
        fun isNeteaseCollection(browseId: String?) = browseId != null && (browseId.startsWith(PLAYLIST) || browseId.startsWith(ALBUM))
        fun rawId(id: String) = id.removePrefix(PREFIX)
        private fun String.https() = replaceFirst("http://", "https://")
        /** List-sized cover; [com.youflip.music.data.Thumbs.hd] upgrades it for full-screen art. */
        fun thumb(url: String) = url.https().substringBefore('?') + "?param=300y300"

        /** `music.163.com/song?id=123` / `/#/song?id=123` → prefixed song id. */
        fun songIdFromUrl(url: String): String? =
            if (url.contains("163.com") || url.contains("163cn.tv")) Regex("song\\?id=(\\d+)").find(url)?.groupValues?.get(1)?.let { PREFIX + it } else null
        fun playlistIdFromUrl(url: String): String? =
            if (url.contains("163.com")) Regex("playlist\\?id=(\\d+)").find(url)?.groupValues?.get(1)?.let { PLAYLIST + it } else null
    }
}
