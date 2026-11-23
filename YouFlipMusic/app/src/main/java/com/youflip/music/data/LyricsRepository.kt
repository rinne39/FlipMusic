package com.youflip.music.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

data class LyricLine(val timeMs: Long, val text: String)

/** [synced] is true when every line carries a timestamp; otherwise lines are plain text (timeMs = -1). */
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean, val album: String?)

/**
 * Lyrics, in order of preference:
 *  1. YouTube Music's own lyrics (time-synced via the Android Music client when available, else plain text);
 *  2. Third-party sites, only when YouTube Music has no time-synced lyrics: LRCLIB (https://lrclib.net),
 *     then NetEase Cloud Music (better coverage of Chinese songs). They receive the cleaned-up title and artist.
 *  3. YouTube Music's plain text, if no third party has synced lyrics either.
 */
class LyricsRepository(
    private val client: OkHttpClient,
    private val ytm: YtmClient,
    private val netease: com.youflip.music.data.netease.NeteaseRepository,
) {

    @Serializable
    private data class Entry(
        val trackName: String? = null,
        val albumName: String? = null,
        val duration: Double? = null,
        val plainLyrics: String? = null,
        val syncedLyrics: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, Lyrics>()
    private val misses = ConcurrentHashMap.newKeySet<String>()

    suspend fun lyricsFor(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        cache[song.id]?.let { return@withContext it }
        if (song.id in misses || song.title.isBlank()) return@withContext null

        // NetEase songs: their own lyrics, falling back to LRCLIB.
        if (song.isNetease) {
            val own = runCatching { netease.lyrics(song) }.getOrNull()
            val result = own?.takeIf { it.synced } ?: runCatching { fromLrclib(song) }.getOrNull()?.takeIf { it.synced } ?: own
            val withAlbum = result?.let { if (song.album != null) it.copy(album = song.album) else it }
            if (withAlbum == null) misses += song.id else cache[song.id] = withAlbum
            return@withContext withAlbum
        }

        // Another app's track: no id we can look up, so match by title / artist / length.
        if (song.isExternal) {
            val lrclib = runCatching { fromLrclib(song) }.getOrNull()
            val result = lrclib?.takeIf { it.synced } ?: runCatching { fromNetease(song) }.getOrNull() ?: lrclib
            if (result == null) misses += song.id else cache[song.id] = result
            return@withContext result
        }

        // YouTube Music first; third-party sites only when YTM has no synced lyrics.
        val ytm = runCatching { fromYouTubeMusic(song.id) }.getOrNull()
        val own = ytm?.lyrics
        val found = own?.takeIf { it.synced } ?: run {
            val lrclib = runCatching { fromLrclib(song) }.getOrNull()
            lrclib?.takeIf { it.synced }
                ?: runCatching { fromNetease(song) }.getOrNull()
                ?: own ?: lrclib
        }
        val result = found?.let { if (ytm?.album != null) it.copy(album = ytm.album) else it }
        if (result == null) misses += song.id else cache[song.id] = result
        result
    }

    // ---------------- YouTube Music (InnerTube) ----------------

    private data class YtmResult(val lyrics: Lyrics?, val album: String?)

    private fun fromYouTubeMusic(videoId: String): YtmResult {
        // Anonymous on purpose: lyrics don't depend on the account.
        val next = ytm.call("next", buildJsonObject { put("videoId", videoId) }, auth = false)
        val lyricsBrowseId = next.findStrings("browseId").firstOrNull { it.startsWith("MPLY") }
        val album = next.findAll("runs").asSequence().flatMap { (it as? JsonArray).orEmpty().asSequence() }
            .firstOrNull { run -> run.findStrings("browseId").any { it.startsWith("MPRE") } }
            ?.at("text").str
        if (lyricsBrowseId == null) return YtmResult(null, album)
        val browse = buildJsonObject { put("browseId", lyricsBrowseId) }

        // The Android Music client returns time-synced lines (timedLyricsData + cueRange).
        val timed = runCatching {
            ytm.call("browse", browse, client = YtmClient.Client.ANDROID_MUSIC, auth = false).findFirst("timedLyricsData") as? JsonArray
        }.getOrNull()
        if (timed != null && timed.isNotEmpty()) {
            val lines = timed.map { o ->
                val start = o.at("cueRange", "startTimeMilliseconds").str?.toLongOrNull()
                LyricLine(start ?: -1, o.at("lyricLine").str.orEmpty().let { if (it == "♪") "" else it })
            }
            val synced = lines.all { it.timeMs >= 0 }
            return YtmResult(Lyrics(if (synced) lines else lines.map { it.copy(timeMs = -1) }, synced, null), album)
        }

        // Web client: plain text only.
        val plain = ytm.call("browse", browse, auth = false).findAll("description")
            .firstNotNullOfOrNull { it.runsText() }?.takeIf { it.isNotBlank() }
        return YtmResult(plain?.let { t -> Lyrics(t.lines().map { LyricLine(-1, it.trim()) }, false, null) }, album)
    }

    // ---------------- LRCLIB fallback ----------------

    private fun fromLrclib(song: Song): Lyrics? {
        val (title, artist) = cleanup(song.title, song.artist)
        val entries = search(mapOf("track_name" to title, "artist_name" to artist))
            .ifEmpty { search(mapOf("q" to "$artist $title")) }
            .ifEmpty { search(mapOf("q" to title)) }
        return pick(entries, song.durationSec)
    }

    private fun search(params: Map<String, String>): List<Entry> = runCatching {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val req = Request.Builder().url(url).header("User-Agent", "FlipMusic/0.7").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return emptyList()
            json.decodeFromString(ListSerializer(Entry.serializer()), r.body!!.string())
        }
    }.getOrDefault(emptyList())

    private fun pick(entries: List<Entry>, durationSec: Long): Lyrics? {
        val usable = entries.filter { !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }
        val close = if (durationSec > 0) usable.filter { abs((it.duration ?: 0.0) - durationSec) <= 4 }.ifEmpty { usable } else usable
        val best = close.firstOrNull { !it.syncedLyrics.isNullOrBlank() } ?: close.firstOrNull() ?: return null
        // Album names on LRCLIB are user-submitted; the most common one among close matches is the most reliable.
        val album = close.mapNotNull { it.albumName?.trim() }
            .filter { it.length > 1 && !it.equals(best.trackName, true) || close.size == 1 }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val synced = best.syncedLyrics?.let(::parseLrc).orEmpty()
        return if (synced.isNotEmpty()) Lyrics(synced, true, album)
        else Lyrics(best.plainLyrics.orEmpty().lines().map { LyricLine(-1, it.trim()) }, false, album)
    }

    // ---------------- NetEase Cloud Music fallback ----------------

    // Sent by NetEase's web page like every other NetEase request (see NeteaseWebBridge).
    private fun neteaseGet(path: String, vararg args: Pair<String, String>): kotlinx.serialization.json.JsonElement? =
        runCatching { netease.webCall(path, *args) }.getOrNull()

    /** Synced lyrics only; picks the result whose artist and duration match, so covers are skipped. */
    private fun fromNetease(song: Song): Lyrics? {
        val (title, artist) = cleanup(song.title, song.artist)
        val songs = (neteaseGet("search/get", "s" to "$title $artist".trim(), "type" to "1", "limit" to "10").at("result", "songs") as? JsonArray).orEmpty()
        val wantArtist = norm(artist)
        val wantTitle = norm(title)
        val best = songs.map { s ->
            val name = norm(s.at("name").str.orEmpty())
            val artists = (s.at("artists") as? JsonArray).orEmpty().mapNotNull { norm(it.at("name").str.orEmpty()).takeIf { a -> a.isNotEmpty() } }
            val durSec = (s.at("duration").str?.toLongOrNull() ?: 0) / 1000
            val artistOk = wantArtist.isNotEmpty() && artists.any { it.contains(wantArtist) || wantArtist.contains(it) }
            val durOk = song.durationSec > 0 && abs(durSec - song.durationSec) <= 3
            val titleOk = name.isNotEmpty() && (name.contains(wantTitle) || wantTitle.contains(name))
            val score = (if (artistOk) 4 else 0) + (if (durOk) 3 else 0) + (if (titleOk) 2 else 0)
            Triple(s.at("id").str, score, titleOk && (artistOk || durOk))
        }.filter { it.third && it.first != null }.maxByOrNull { it.second } ?: return null

        val lrc = neteaseGet("song/lyric", "id" to best.first!!, "lv" to "-1", "tv" to "-1").at("lrc", "lyric").str ?: return null
        val lines = parseLrc(lrc).filterNot { credit.containsMatchIn(it.text) }
        return lines.takeIf { l -> l.count { it.text.isNotBlank() } >= 3 }?.let { Lyrics(it, true, null) }
    }

    companion object {
        /** NetEase puts credits ("作词 : 方文山") in the first lines. */
        private val credit = Regex("^\\s*(作词|作曲|编曲|制作人|词|曲|Lyrics|Composer|Producer|Arranger)\\s*[:：]", RegexOption.IGNORE_CASE)
        private fun norm(s: String) = s.lowercase().replace(Regex("[\\s·・.,'’\"()（）\\-]"), "")
            .map { TRAD_SIMP[it] ?: it }.joinToString("")
        /** Just enough traditional→simplified folding for artist names like 周杰倫/周杰伦. */
        private val TRAD_SIMP = mapOf('倫' to '伦', '陳' to '陈', '張' to '张', '劉' to '刘', '楊' to '杨', '黃' to '黄', '鄧' to '邓', '蕭' to '萧', '風' to '风', '愛' to '爱', '東' to '东', '華' to '华', '傑' to '杰', '樂' to '乐', '龍' to '龙', '捲' to '卷', '雲' to '云', '說' to '说', '時' to '时', '開' to '开', '後' to '后', '紅' to '红', '飛' to '飞', '夢' to '梦', '戀' to '恋', '這' to '这', '個' to '个', '們' to '们', '會' to '会', '為' to '为', '與' to '与', '無' to '无', '還' to '还', '聽' to '听', '話' to '话', '見' to '见', '鳥' to '鸟', '藍' to '蓝', '練' to '练', '習' to '习', '歲' to '岁', '輕' to '轻', '戰' to '战', '錯' to '错', '過' to '过', '邊' to '边', '單' to '单', '從' to '从', '來' to '来', '長' to '长', '給' to '给', '寫' to '写', '詞' to '词', '曉' to '晓', '謝' to '谢', '孫' to '孙', '燕' to '燕', '鄭' to '郑', '吳' to '吴', '許' to '许', '蘇' to '苏', '葉' to '叶', '惠' to '惠', '騰' to '腾', '韋' to '韦', '羅' to '罗', '譚' to '谭', '歐' to '欧', '陽' to '阳', '齊' to '齐', '閻' to '阎', '亞' to '亚', '麗' to '丽', '寧' to '宁', '濤' to '涛', '強' to '强', '鳳' to '凤', '嬌' to '娇', '靜' to '静', '晉' to '晋')

        private val timeTag = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")

        fun parseLrc(lrc: String): List<LyricLine> = lrc.lines().flatMap { raw ->
            val tags = timeTag.findAll(raw).toList()
            if (tags.isEmpty()) return@flatMap emptyList()
            val text = raw.substring(tags.last().range.last + 1).trim()
            tags.map { m ->
                val (mm, ss, frac) = m.destructured
                val ms = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
                LyricLine(mm.toLong() * 60_000 + ss.toLong() * 1000 + ms, text)
            }
        }.sortedBy { it.timeMs }

        private val noise = Regex(
            "\\s*[(\\[【（](official|music|lyric|lyrics|audio|video|mv|m/v|hd|hq|4k|visualizer|live|官方|完整版|歌词|動態歌詞|高音質)[^)\\]】）]*[)\\]】）]",
            RegexOption.IGNORE_CASE,
        )
        private val feat = Regex("\\s+(ft\\.?|feat\\.?|featuring)\\s+.*$", RegexOption.IGNORE_CASE)

        /** Strips YouTube-style decorations: "Artist - Title (Official Video) ft. X", "Artist - Topic", "ArtistVEVO". */
        fun cleanup(rawTitle: String, rawArtist: String): Pair<String, String> {
            var artist = rawArtist.removeSuffix(" - Topic").removeSuffix("VEVO").replace(feat, "").trim()
            var title = rawTitle.replace(noise, "").trim()
            val dash = Regex("\\s[-–—]\\s").find(title)
            if (dash != null) {
                val left = title.substring(0, dash.range.first).trim()
                val right = title.substring(dash.range.last + 1).trim()
                if (artist.isBlank() || left.contains(artist, true) || artist.contains(left, true) || rawArtist.endsWith("VEVO")) {
                    artist = left; title = right
                }
            }
            title = title.replace(feat, "").replace(noise, "").trim()
            return title to artist
        }
    }
}
