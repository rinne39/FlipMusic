package com.youflip.music.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.youflip.music.YouFlipApp
import com.youflip.music.data.CollectionKind
import com.youflip.music.data.Account
import com.youflip.music.data.MusicSource
import com.youflip.music.data.netease.NeteaseApiException
import com.youflip.music.data.netease.NeteaseRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import com.youflip.music.data.NotSignedInException
import com.youflip.music.data.Shelf
import com.youflip.music.data.YtmRepository
import com.youflip.music.data.Lyrics
import com.youflip.music.data.MusicCollection
import com.youflip.music.data.MusicRepository
import com.youflip.music.data.SearchFilter
import com.youflip.music.data.SearchPage
import com.youflip.music.data.Song
import com.youflip.music.playback.PlayerConnection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface Load<out T> {
    data object Idle : Load<Nothing>
    data object Loading : Load<Nothing>
    data class Ready<T>(val data: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter.SONGS,
    val suggestions: List<String> = emptyList(),
    val result: Load<SearchPage> = Load.Idle,
)

data class HomeUiState(
    val radio: Load<List<Song>> = Load.Idle,
    val radioSeed: Song? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val yf = app as YouFlipApp
    private val repo = yf.repository
    val library = yf.library
    val settings = yf.settings

    val player = PlayerConnection(app, viewModelScope).also { it.connect() }

    // ---- YouTube account ----
    private val accountStore = yf.account
    private val ytm = yf.ytm
    private val ncmAccount = yf.neteaseAccount
    private val netease = yf.netease
    /** Which service powers discovery, search, library and login. */
    val source = settings.musicSource.stateIn(viewModelScope, SharingStarted.Eagerly, MusicSource.YTM)
    private val sourceAndSignIn = combine(settings.musicSource, accountStore.cookie, ncmAccount.cookie) { src, yt, ncm ->
        src to (if (src == MusicSource.NETEASE) ncm != null else yt != null)
    }.distinctUntilChanged()
    /** Signed in to the active source. */
    val signedIn = sourceAndSignIn.map { it.second }.stateIn(viewModelScope, SharingStarted.Eagerly, accountStore.isSignedIn)
    /** NetEase has no music-video search. */
    val searchFilters = source.map { src -> if (src == MusicSource.NETEASE) SearchFilter.entries - SearchFilter.VIDEOS else SearchFilter.entries }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchFilter.entries)
    /** The source the loaded home/library data belongs to. */
    @Volatile private var activeSource = MusicSource.YTM
    private val isNcm get() = activeSource == MusicSource.NETEASE
    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account.asStateFlow()
    private val _showLogin = MutableStateFlow(false)
    val showLogin: StateFlow<Boolean> = _showLogin.asStateFlow()
    private val _ytHome = MutableStateFlow<Load<List<Shelf>>>(Load.Idle)
    val ytHome: StateFlow<Load<List<Shelf>>> = _ytHome.asStateFlow()
    private val _ytLiked = MutableStateFlow<Load<List<Song>>>(Load.Idle)
    val ytLiked: StateFlow<Load<List<Song>>> = _ytLiked.asStateFlow()
    private val _ytPlaylists = MutableStateFlow<Load<List<MusicCollection>>>(Load.Idle)
    val ytPlaylists: StateFlow<Load<List<MusicCollection>>> = _ytPlaylists.asStateFlow()
    private val _ytHistory = MutableStateFlow<Load<List<Song>>>(Load.Idle)
    val ytHistory: StateFlow<Load<List<Song>>> = _ytHistory.asStateFlow()
    private val ytLikedIds = MutableStateFlow<Set<String>>(emptySet())

    /** One-off user-facing messages (shown as toasts). */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Hearts are the signed-in account's likes (YouTube Music likes / NetEase 红心); there are no local favourites. */
    val favoriteIds = combine(ytLikedIds, sourceAndSignIn) { remote, (_, isIn) -> if (isIn) remote else emptySet() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    /** Local play history of the active source only. */
    val history = combine(library.history, settings.musicSource) { h, src -> h.filter { it.isNetease == (src == MusicSource.NETEASE) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recentSearches = library.recentSearches.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val themeMode = settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, com.youflip.music.data.ThemeMode.SYSTEM)
    fun setThemeMode(m: com.youflip.music.data.ThemeMode) = viewModelScope.launch { settings.setThemeMode(m) }
    val cameraStrip = settings.cameraStrip.stateIn(viewModelScope, SharingStarted.Eagerly, com.youflip.music.data.CameraStrip.GLASS)
    fun setCameraStrip(v: com.youflip.music.data.CameraStrip) = viewModelScope.launch { settings.setCameraStrip(v) }
    val autoplay = settings.autoplayRadio.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val highQuality = settings.highQuality.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val coverKeepScreenOn = settings.coverKeepScreenOn.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val coverMarquee = settings.coverMarquee.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _search = MutableStateFlow(SearchUiState())
    val search: StateFlow<SearchUiState> = _search.asStateFlow()

    private val _home = MutableStateFlow(HomeUiState())
    val home: StateFlow<HomeUiState> = _home.asStateFlow()

    private val _collection = MutableStateFlow<Load<MusicRepository.CollectionDetail>>(Load.Idle)
    val collection: StateFlow<Load<MusicRepository.CollectionDetail>> = _collection.asStateFlow()

    /** Lyrics for the current song; Idle when nothing is playing. */
    private val _lyrics = MutableStateFlow<Load<Lyrics?>>(Load.Idle)
    val lyrics: StateFlow<Load<Lyrics?>> = _lyrics.asStateFlow()
    private var lyricsFor: String? = null
    private var lyricsJob: Job? = null

    private var suggestJob: Job? = null
    private var searchJob: Job? = null

    // ---- In-app updates (player.pjsk.store) ----
    private val updater = yf.updater
    /** [info]: a newer build exists; [progress]: downloading (0..1); [show]: the update dialog is open. */
    data class UpdateUi(val info: com.youflip.music.data.UpdateInfo? = null, val progress: Float? = null, val show: Boolean = false)
    private val _update = MutableStateFlow(UpdateUi())
    val update: StateFlow<UpdateUi> = _update.asStateFlow()

    /** Startup check is silent; a manual check (Settings) also says when it's already up to date. */
    fun checkUpdate(manual: Boolean) = viewModelScope.launch {
        if (_update.value.progress != null) return@launch
        runCatching { updater.check() }
            .onSuccess { info ->
                if (info != null) _update.value = _update.value.copy(info = info, show = true)
                else if (manual) _messages.tryEmit("已是最新版本")
            }
            .onFailure { if (manual) _messages.tryEmit("检查更新失败：${it.message ?: "网络错误"}") }
    }

    fun dismissUpdate() { if (_update.value.progress == null) _update.value = _update.value.copy(show = false) }

    fun startUpdate() {
        val info = _update.value.info ?: return
        if (_update.value.progress != null) return
        val ctx = getApplication<Application>()
        if (!updater.canInstall()) {
            _messages.tryEmit("请允许 Flip Music 安装应用，然后再点一次更新")
            ctx.startActivity(updater.installPermissionIntent())
            return
        }
        _update.value = _update.value.copy(progress = 0f)
        viewModelScope.launch {
            runCatching { updater.download(info) { p -> _update.value = _update.value.copy(progress = p) } }
                .onSuccess { file ->
                    _update.value = _update.value.copy(progress = null, show = false)
                    ctx.startActivity(updater.installIntent(file))
                }
                .onFailure {
                    _update.value = _update.value.copy(progress = null)
                    _messages.tryEmit(it.message ?: "下载失败")
                }
        }
    }

    init {
        checkUpdate(manual = false)
        viewModelScope.launch { sourceAndSignIn.collect { (src, isIn) -> onAccountChanged(src, isIn) } }
        // First song ever played: seed the home radio from it.
        viewModelScope.launch {
            player.state.collect { s ->
                val song = s.current
                if (_home.value.radioSeed == null && song != null && song.title.isNotBlank() && song.isNetease == isNcm) loadRadio(song)
                // Wait for real metadata (bare links get it a moment after playback starts).
                if (song == null) { lyricsFor = null; _lyrics.value = Load.Idle }
                else if (song.title.isNotBlank() && song.id != lyricsFor) {
                    lyricsFor = song.id
                    _lyrics.value = Load.Loading
                    lyricsJob?.cancel()
                    lyricsJob = viewModelScope.launch { _lyrics.value = Load.Ready(yf.lyrics.lyricsFor(song)) }
                }
            }
        }
    }

    // ---- Playback shortcuts ----
    fun playList(songs: List<Song>, index: Int) = player.play(songs, index)
    fun toggleFavorite(song: Song) {
        if (song.isNetease != isNcm) {
            _messages.tryEmit("这首歌来自${if (song.isNetease) "网易云音乐" else "YouTube Music"}，切换到该音源后才能收藏")
            return
        }
        if (!signedIn.value) {
            _messages.tryEmit("登录${if (isNcm) "网易云音乐" else " YouTube"}后才能收藏")
            openLogin()
            return
        }
        val like = song.id !in ytLikedIds.value
        // Optimistic update, rolled back if the service rejects it.
        applyLike(song, like)
        viewModelScope.launch {
            runCatching { if (song.isNetease) netease.setLiked(song.id, like) else ytm.setLiked(song.id, like) }.onFailure {
                applyLike(song, !like)
                android.util.Log.w("FlipMusic", "setLiked(${song.id}, $like) failed", it)
                // Show the service's own reason so a failure can be diagnosed from the toast.
                val why = it.message?.takeIf { m -> m.isNotBlank() }?.let { m -> "：$m" }.orEmpty()
                handleYtError(it, (if (like) "收藏失败" else "取消收藏失败") + why)
            }
        }
    }

    private fun applyLike(song: Song, like: Boolean) {
        ytLikedIds.value = if (like) ytLikedIds.value + song.id else ytLikedIds.value - song.id
        (_ytLiked.value as? Load.Ready)?.let { r ->
            _ytLiked.value = Load.Ready(if (like) listOf(song) + r.data.filterNot { it.id == song.id } else r.data.filterNot { it.id == song.id })
        }
    }

    // ---- Account ----
    fun openLogin() { _showLogin.value = true }
    fun closeLogin() { _showLogin.value = false }

    fun onSignedIn(cookie: String) {
        accountStore.save(cookie)
        _showLogin.value = false
        _messages.tryEmit("已登录 YouTube")
    }

    fun signOut() {
        if (isNcm) {
            viewModelScope.launch { netease.logout() }
        } else {
            accountStore.clear()
            clearWebSession()
        }
        _messages.tryEmit("已退出登录")
    }

    fun setMusicSource(s: MusicSource) = viewModelScope.launch { settings.setMusicSource(s) }

    // ---- NetEase login (official web page only) ----
    /** Signed in on NetEase's own web login page: keep only that site's cookie. */
    fun onNeteaseWebSignedIn(cookie: String) {
        ncmAccount.save(cookie)
        _showLogin.value = false
        _messages.tryEmit("已登录网易云音乐")
    }

    private fun onAccountChanged(src: MusicSource, isIn: Boolean) {
        activeSource = src
        libraryJob?.cancel()
        _account.value = null
        ytLikedIds.value = emptySet()
        _ytLiked.value = Load.Idle; _ytPlaylists.value = Load.Idle; _ytHistory.value = Load.Idle
        clearSearch()
        // Home radio belongs to the previous source: rebuild them for this one.
        viewModelScope.launch {
            _home.value = HomeUiState()
            val seed = library.history.first().firstOrNull { it.isNetease == (src == MusicSource.NETEASE) }
            if (seed != null && activeSource == src) loadRadio(seed)
        }
        if (src == MusicSource.NETEASE && _search.value.filter == SearchFilter.VIDEOS) _search.value = _search.value.copy(filter = SearchFilter.SONGS)
        refreshYtHome()
        if (isIn) {
            viewModelScope.launch {
                _account.value = runCatching { if (src == MusicSource.NETEASE) netease.account() else ytm.account() }
                    .onFailure { handleYtError(it, null) }.getOrNull()
            }
            refreshYtLibrary()
        }
    }

    fun refreshYtHome() {
        _ytHome.value = Load.Loading
        val ncm = isNcm
        viewModelScope.launch { _ytHome.value = ytLoad { if (ncm) netease.home() else ytm.home() } }
    }

    fun refreshYtLibrary() {
        if (!signedIn.value) return
        _ytLiked.value = Load.Loading; _ytPlaylists.value = Load.Loading; _ytHistory.value = Load.Loading
        // A second refresh (e.g. account flow re-emitting) replaces the first instead of downloading everything twice.
        libraryJob?.cancel()
        libraryJob = viewModelScope.launch { loadYtLibrary() }
    }

    private var libraryJob: kotlinx.coroutines.Job? = null

    private suspend fun loadYtLibrary() = kotlinx.coroutines.coroutineScope {
        if (isNcm) {
            launch {
                // Hearts work as soon as the id list arrives; the song details follow.
                val ids = ytLoad { netease.likedIds() }
                if (ids is Load.Ready) {
                    ytLikedIds.value = ids.data.toSet()
                    _ytLiked.value = ytLoad { netease.songDetails(ids.data) }
                } else _ytLiked.value = ids as Load.Failed
            }
            launch { _ytPlaylists.value = ytLoad { netease.playlists() } }
            launch { _ytHistory.value = ytLoad { netease.history() } }
            return@coroutineScope
        }
        launch {
            val liked = ytLoad { ytm.likedSongs() }
            _ytLiked.value = liked
            if (liked is Load.Ready) ytLikedIds.value = liked.data.map { it.id }.toSet()
        }
        launch { _ytPlaylists.value = ytLoad { ytm.libraryPlaylists() } }
        launch { _ytHistory.value = ytLoad { ytm.history() } }
    }

    private suspend fun <T> ytLoad(block: suspend () -> T): Load<T> =
        runCatching { Load.Ready(block()) }.getOrElse {
            if (it is kotlinx.coroutines.CancellationException) throw it
            handleYtError(it, null); Load.Failed(it.friendly())
        }

    /** A rejected session (expired / revoked cookie) signs the user out instead of failing forever. */
    private fun handleYtError(t: Throwable, message: String?) {
        if (t is NotSignedInException && isNcm && ncmAccount.isSignedIn) {
            ncmAccount.clear()
            _messages.tryEmit("网易云音乐登录已失效，请重新登录")
        } else if (t is NotSignedInException && !isNcm && accountStore.isSignedIn) {
            accountStore.clear()
            _messages.tryEmit("YouTube 登录已失效，请重新登录")
        } else if (message != null) {
            _messages.tryEmit(message)
        }
    }

    /** Plays the song and lets autoplay fill the queue with its radio. */
    fun playRadio(song: Song) = player.play(listOf(song))

    // ---- Home ----
    fun loadRadio(seed: Song) {
        _home.value = _home.value.copy(radioSeed = seed, radio = Load.Loading)
        viewModelScope.launch {
            _home.value = _home.value.copy(
                radio = runCatching { Load.Ready(related(seed.id).take(25)) }
                    .getOrElse { Load.Failed(it.friendly()) }
            )
        }
    }

    // ---- Search ----
    fun onQueryChange(q: String) {
        _search.value = _search.value.copy(query = q)
        suggestJob?.cancel()
        if (q.isBlank()) { _search.value = _search.value.copy(suggestions = emptyList()); return }
        suggestJob = viewModelScope.launch {
            delay(250)
            val list = if (isNcm) netease.suggestions(q) else repo.searchSuggestions(q)
            _search.value = _search.value.copy(suggestions = list.take(8))
        }
    }

    fun setFilter(filter: SearchFilter) {
        _search.value = _search.value.copy(filter = filter)
        if (_search.value.query.isNotBlank() && _search.value.result != Load.Idle) submitSearch()
    }

    fun submitSearch(query: String = _search.value.query) {
        val q = query.trim()
        if (q.isEmpty()) return
        suggestJob?.cancel()
        _search.value = _search.value.copy(query = q, suggestions = emptyList(), result = Load.Loading)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            library.addSearch(q)
            _search.value = _search.value.copy(
                result = runCatching { Load.Ready(searchActive(q, _search.value.filter)) }
                    .getOrElse { Load.Failed(it.friendly()) }
            )
        }
    }

    private suspend fun related(id: String): List<Song> =
        if (NeteaseRepository.isNetease(id)) netease.related(id) else repo.related(id)

    private suspend fun searchActive(q: String, filter: SearchFilter): SearchPage =
        if (isNcm) netease.search(q, if (filter == SearchFilter.VIDEOS) SearchFilter.SONGS else filter) else repo.search(q, filter)

    fun clearSearch() { _search.value = SearchUiState(filter = _search.value.filter) }

    // ---- Collections ----
    fun openCollection(c: MusicCollection) = openCollection(c.url, c.kind, browseId = c.browseId)

    /** Shows an in-memory song list (liked, recent…) in the collection view instead of playing it. */
    /** True while the open list is this phone's own play history (which can be cleared). */
    private val _collectionIsLocalHistory = MutableStateFlow(false)
    val collectionIsLocalHistory: StateFlow<Boolean> = _collectionIsLocalHistory.asStateFlow()

    fun showSongs(title: String, songs: List<Song>, localHistory: Boolean = false) {
        collectionRequest++
        _collectionIsLocalHistory.value = localHistory
        _collection.value = Load.Ready(
            MusicRepository.CollectionDetail(
                MusicCollection(url = "", title = title, subtitle = "${songs.size} 首", thumbnail = songs.firstOrNull()?.thumbnail, kind = CollectionKind.PLAYLIST),
                songs,
            )
        )
    }

    /** Bumped on every open/close so a slow load can't reopen a list the user already left. */
    @Volatile private var collectionRequest = 0

    fun openCollection(url: String, kind: CollectionKind = CollectionKind.PLAYLIST, autoplay: Boolean = false, browseId: String? = null) {
        val req = ++collectionRequest
        val id = browseId ?: YtmRepository.browseIdFromUrl(url)
        // "Liked music" is already loaded for the library: show it instantly instead of re-downloading every page.
        val liked = (ytLiked.value as? Load.Ready)?.data
        if (id == "VLLM" && !liked.isNullOrEmpty() && !autoplay) { showSongs("赞过的音乐", liked); return }
        _collectionIsLocalHistory.value = false
        _collection.value = Load.Loading
        if (NeteaseRepository.isNeteaseCollection(id)) {
            viewModelScope.launch {
                val result = runCatching {
                    netease.collection(id!!) { partial -> if (req == collectionRequest && partial.songs.isNotEmpty()) _collection.value = Load.Ready(partial) }
                }
                if (req != collectionRequest) return@launch
                _collection.value = result.map { Load.Ready(it) }.getOrElse { Load.Failed(it.friendly()) }
                if (autoplay) result.getOrNull()?.songs?.takeIf { it.isNotEmpty() }?.let { player.play(it) }
            }
            return
        }
        viewModelScope.launch {
            // InnerTube first (albums, and private playlists when signed in); NewPipe as fallback.
            val result = runCatching {
                id?.let { bid ->
                    runCatching {
                        ytm.collection(bid) { partial -> if (req == collectionRequest && partial.songs.isNotEmpty()) _collection.value = Load.Ready(partial) }
                    }.getOrNull()
                }?.takeIf { it.songs.isNotEmpty() }
                    ?: repo.collection(url, kind)
            }
            if (req != collectionRequest) return@launch
            _collection.value = result.map { Load.Ready(it) }.getOrElse { Load.Failed(it.friendly()) }
            if (autoplay) result.getOrNull()?.songs?.takeIf { it.isNotEmpty() }?.let { player.play(it) }
        }
    }

    fun closeCollection() { collectionRequest++; _collectionIsLocalHistory.value = false; _collection.value = Load.Idle }

    /** Clears this phone's play history and closes the history list if it is open. */
    fun clearLocalHistory() {
        viewModelScope.launch { library.clearHistory() }
        if (_collectionIsLocalHistory.value) closeCollection()
        _messages.tryEmit("已清空播放历史")
    }

    /** Handles shared / opened YouTube & YouTube Music links. Returns true if something was opened. */
    fun handleLink(text: String): Boolean {
        val url = Regex("https?://\\S+").find(text)?.value ?: return false
        NeteaseRepository.songIdFromUrl(url)?.let { id -> player.play(listOf(Song(id, "", ""))); return true }
        NeteaseRepository.playlistIdFromUrl(url)?.let { id -> openCollection(url, autoplay = true, browseId = id); return true }
        MusicRepository.videoIdFrom(url)?.let { id ->
            player.play(listOf(Song(id, "", "")))
            return true
        }
        if (url.contains("list=")) {
            openCollection(url, autoplay = true)
            return true
        }
        return false
    }

    // ---- Settings ----
    fun setAutoplay(v: Boolean) = viewModelScope.launch { settings.setAutoplayRadio(v) }
    fun setHighQuality(v: Boolean) = viewModelScope.launch { settings.setHighQuality(v) }
    fun setCoverKeepScreenOn(v: Boolean) = viewModelScope.launch { settings.setCoverKeepScreenOn(v) }
    fun setCoverMarquee(v: Boolean) = viewModelScope.launch { settings.setCoverMarquee(v) }

    fun clearRecentSearches() = viewModelScope.launch { library.clearSearches() }

    override fun onCleared() {
        player.release()
    }

    private fun Throwable.friendly(): String = when {
        this is java.net.UnknownHostException -> "网络不可用，请检查连接"
        this is NotSignedInException -> message ?: "需要登录"
        this is NeteaseApiException -> message ?: "网易云音乐请求失败"
        message?.contains("reCaptcha", true) == true -> "YouTube 要求验证，请稍后再试"
        else -> "加载失败：${javaClass.simpleName}"
    }

}
