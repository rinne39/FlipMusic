package com.youflip.music.data

import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Long = 0,
    val thumbnail: String? = null,
    val album: String? = null,
) {
    val isNetease get() = id.startsWith("ncm:")
    val artworkHd get() = Thumbs.hd(thumbnail)
}

enum class CollectionKind { ALBUM, PLAYLIST }

data class MusicCollection(
    val url: String,
    val title: String,
    val subtitle: String,
    val thumbnail: String?,
    val kind: CollectionKind,
    /** InnerTube browse id (VL… playlist / MPRE… album) when known. */
    val browseId: String? = null,
)

enum class SearchFilter(val label: String, val key: String) {
    SONGS("歌曲", "music_songs"),
    VIDEOS("视频", "music_videos"),
    ALBUMS("专辑", "music_albums"),
    PLAYLISTS("歌单", "music_playlists"),
}

data class SearchPage(
    val songs: List<Song> = emptyList(),
    val collections: List<MusicCollection> = emptyList(),
)

object Thumbs {
    private val googleSize = Regex("=w\\d+-h\\d+[^/]*$")
    private val ytimgName = Regex("/(default|mqdefault|hqdefault|sddefault|hq720)\\.jpg")

    /** Upgrades a thumbnail URL to a large square-friendly variant for full-screen artwork. */
    fun hd(url: String?): String? = when {
        url == null -> null
        url.contains("googleusercontent.com") || url.contains("ggpht.com") ->
            if (googleSize.containsMatchIn(url)) url.replace(googleSize, "=w720-h720-l90-rj") else "$url=w720-h720"
        // hq720 is a true 16:9 frame; hqdefault (4:3) has black letterbox bars baked in.
        url.contains("ytimg.com") -> url.substringBefore('?').replace(ytimgName, "/hq720.jpg")
        url.contains("music.126.net") -> url.substringBefore('?') + "?param=720y720"
        else -> url
    }

    /** A variant that always exists, for when [hd] 404s (old videos have no hq720). */
    fun fallback(url: String?): String? =
        if (url != null && url.contains("ytimg.com") && url.contains("/hq720.jpg")) url.replace("/hq720.jpg", "/hqdefault.jpg") else null
}

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "--:--"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
