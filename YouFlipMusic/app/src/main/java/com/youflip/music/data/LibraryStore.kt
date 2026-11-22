package com.youflip.music.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.libraryStore by preferencesDataStore("library")
private val Context.settingsStore by preferencesDataStore("settings")
private val json = Json { ignoreUnknownKeys = true }
private val songList = ListSerializer(Song.serializer())
private val stringList = ListSerializer(String.serializer())

/** Local, account-free library: listening history and recent searches. */
class LibraryStore(private val context: Context) {
    private val historyKey = stringPreferencesKey("history")
    private val searchesKey = stringPreferencesKey("searches")

    val history: Flow<List<Song>> = context.libraryStore.data.map { it.songs(historyKey) }
    val recentSearches: Flow<List<String>> = context.libraryStore.data.map { p ->
        p[searchesKey]?.let { runCatching { json.decodeFromString(stringList, it) }.getOrNull() }.orEmpty()
    }

    suspend fun addHistory(song: Song) = context.libraryStore.edit { p ->
        val updated = (listOf(song) + p.songs(historyKey).filterNot { it.id == song.id }).take(300)
        p[historyKey] = json.encodeToString(songList, updated)
    }

    suspend fun clearHistory() = context.libraryStore.edit { it.remove(historyKey) }

    suspend fun addSearch(query: String) = context.libraryStore.edit { p ->
        val current = p[searchesKey]?.let { runCatching { json.decodeFromString(stringList, it) }.getOrNull() }.orEmpty()
        p[searchesKey] = json.encodeToString(stringList, (listOf(query) + current.filterNot { it == query }).take(20))
    }

    suspend fun clearSearches() = context.libraryStore.edit { it.remove(searchesKey) }

    private fun Preferences.songs(key: Preferences.Key<String>): List<Song> =
        this[key]?.let { runCatching { json.decodeFromString(songList, it) }.getOrNull() }.orEmpty()
}

enum class ThemeMode(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }

/** What fills the full-width band around the cover screen's cameras on the now-playing page. */
enum class CameraStrip(val label: String) { GLASS("毛玻璃"), BLACK("全黑") }

enum class MusicSource(val label: String) { YTM("YouTube Music"), NETEASE("网易云音乐") }

class SettingsStore(private val context: Context) {
    private val sourceKey = stringPreferencesKey("music_source")
    private val themeKey = stringPreferencesKey("theme_mode")
    val themeMode: Flow<ThemeMode> = context.settingsStore.data.map { p ->
        p[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }
    suspend fun setThemeMode(m: ThemeMode) = context.settingsStore.edit { it[themeKey] = m.name }
    private val stripKey = stringPreferencesKey("camera_strip")
    val cameraStrip: Flow<CameraStrip> = context.settingsStore.data.map { p ->
        p[stripKey]?.let { runCatching { CameraStrip.valueOf(it) }.getOrNull() } ?: CameraStrip.GLASS
    }
    suspend fun setCameraStrip(v: CameraStrip) = context.settingsStore.edit { it[stripKey] = v.name }
    private val autoplayKey = booleanPreferencesKey("autoplay_radio")
    private val hqKey = booleanPreferencesKey("high_quality")
    private val keepOnKey = booleanPreferencesKey("cover_keep_screen_on")
    private val marqueeKey = booleanPreferencesKey("cover_marquee")

    val musicSource: Flow<MusicSource> = context.settingsStore.data.map { p ->
        p[sourceKey]?.let { runCatching { MusicSource.valueOf(it) }.getOrNull() } ?: MusicSource.YTM
    }
    val autoplayRadio: Flow<Boolean> = context.settingsStore.data.map { it[autoplayKey] ?: true }
    val highQuality: Flow<Boolean> = context.settingsStore.data.map { it[hqKey] ?: true }
    val coverKeepScreenOn: Flow<Boolean> = context.settingsStore.data.map { it[keepOnKey] ?: false }
    val coverMarquee: Flow<Boolean> = context.settingsStore.data.map { it[marqueeKey] ?: false }

    suspend fun setMusicSource(s: MusicSource) = context.settingsStore.edit { it[sourceKey] = s.name }
    suspend fun setAutoplayRadio(v: Boolean) = context.settingsStore.edit { it[autoplayKey] = v }
    suspend fun setHighQuality(v: Boolean) = context.settingsStore.edit { it[hqKey] = v }
    suspend fun setCoverKeepScreenOn(v: Boolean) = context.settingsStore.edit { it[keepOnKey] = v }
    suspend fun setCoverMarquee(v: Boolean) = context.settingsStore.edit { it[marqueeKey] = v }
}
