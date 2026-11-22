package com.youflip.music.ui.main

import com.youflip.music.ui.theme.LikeRed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.youflip.music.ui.components.pressClick
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.youflip.music.ui.cover.LyricsPane
import com.youflip.music.ui.cover.TextShadow
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.youflip.music.data.MusicCollection
import com.youflip.music.data.MusicRepository
import com.youflip.music.data.Song
import com.youflip.music.ui.Load
import com.youflip.music.ui.MainViewModel
import com.youflip.music.ui.SettingsContent
import com.youflip.music.ui.components.ArtBackdrop
import com.youflip.music.ui.components.Artwork
import com.youflip.music.ui.components.LoadingBox
import com.youflip.music.ui.components.MessageBox
import com.youflip.music.ui.components.SectionHeader
import com.youflip.music.ui.components.ShelfRow
import com.youflip.music.ui.components.SeekBar
import com.youflip.music.ui.components.SongActions
import com.youflip.music.ui.components.SongRow
import com.youflip.music.ui.components.TransportRow

private enum class Tab(val label: String) { HOME("发现"), LIBRARY("资料库") }

/** Bottom padding lists need so their last rows scroll clear of the floating tab bar + mini player. */
internal val LocalBottomInset = androidx.compose.runtime.compositionLocalOf { 16.dp }

/** Standard layout for the unfolded main screen, in the style of Apple Music. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MainApp(vm: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val state by vm.player.state.collectAsState()
    val collection by vm.collection.collectAsState()
    val hasSong = state.current != null
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset = navBottom + 6.dp + 58.dp + (if (hasSong) 64.dp else 0.dp) + 16.dp

    SharedTransitionLayout(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { Box(Modifier.fillMaxSize()) {
        // Pages draw all the way down; the translucent capsules float over them.
        CompositionLocalProvider(LocalBottomInset provides bottomInset) {
            val page: Any = when { collection !is Load.Idle -> "collection"; searchOpen -> "search"; else -> tab }
            // Tabs < search < a list: going deeper slides in from the right (Android shared-axis X), back reverses.
            fun depth(p: Any) = when (p) { "collection" -> 2; "search" -> 1; else -> 0 }
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    when {
                        depth(targetState) > depth(initialState) -> (slideInHorizontally(tween(350, easing = EmphasizedEasing)) { it / 4 } + fadeIn(tween(250))) togetherWith
                            (slideOutHorizontally(tween(350, easing = EmphasizedEasing)) { -it / 10 } + fadeOut(tween(150)))
                        depth(targetState) < depth(initialState) -> (slideInHorizontally(tween(350, easing = EmphasizedEasing)) { -it / 10 } + fadeIn(tween(250))) togetherWith
                            (slideOutHorizontally(tween(350, easing = EmphasizedEasing)) { it / 4 } + fadeOut(tween(150)))
                        // Switch tabs: Material fade-through.
                        else -> (fadeIn(tween(220, delayMillis = 90)) + scaleIn(tween(220, delayMillis = 90), initialScale = 0.94f)) togetherWith fadeOut(tween(90))
                    }
                },
                label = "page",
            ) { p ->
                Box(Modifier.fillMaxSize()) {
                    when (p) {
                        "collection" -> {
                            BackHandler { vm.closeCollection() }
                            CollectionScreen(vm, collection)
                        }
                        "search" -> {
                            BackHandler { searchOpen = false }
                            SearchScreen(vm, onBack = { searchOpen = false })
                        }
                        Tab.HOME -> HomeScreen(vm, onSettings = { settingsOpen = true }, onSearch = { searchOpen = true })
                        Tab.LIBRARY -> LibraryScreen(vm)
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 6.dp)) {
            if (hasSong) {
                Box(Modifier.fillMaxWidth().height(56.dp)) {
                    androidx.compose.animation.AnimatedVisibility(!playerOpen, enter = fadeIn(tween(1)), exit = fadeOut(tween(140))) {
                        MiniPlayer(vm, this@SharedTransitionLayout, this) { playerOpen = true }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            TabBar(tab) { t -> tab = t; searchOpen = false; vm.closeCollection() }
        }

        AnimatedVisibility(
            playerOpen && hasSong,
            enter = slideInVertically(tween(420, easing = EmphasizedEasing)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(380, easing = EmphasizedEasing)) { it } + fadeOut(tween(300, delayMillis = 80)),
        ) {
            BackHandler { playerOpen = false }
            com.youflip.music.ui.theme.ForceDark {
                com.youflip.music.ui.theme.DarkSystemBars(true)
                FullPlayer(vm, this@SharedTransitionLayout, this, onClose = { playerOpen = false })
            }
        }
        AnimatedVisibility(
            settingsOpen,
            enter = slideInHorizontally(tween(350, easing = EmphasizedEasing)) { it / 4 } + fadeIn(tween(250)),
            exit = slideOutHorizontally(tween(300, easing = EmphasizedEasing)) { it / 4 } + fadeOut(tween(200)),
        ) {
            BackHandler { settingsOpen = false }
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
                IconButton(onClick = { settingsOpen = false }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
                LargeTitle("设置")
                SettingsContent(vm, compact = false)
            }
        }
    } }
}

/** Android's "emphasized" motion curve. */
private val EmphasizedEasing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Apple Music-style large bold page title. */
@Composable
internal fun LargeTitle(text: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

/** Compact floating capsule tab bar: small icon + caption, no Material indicator pill. */
@Composable
private fun TabBar(tab: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(58.dp).shadow(12.dp, RoundedCornerShape(29.dp))
            .clip(RoundedCornerShape(29.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { t ->
            val selected = tab == t
            val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(29.dp)).pressClick(scaleTo = 0.88f) { onSelect(t) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    when (t) { Tab.HOME -> Icons.Rounded.Explore; Tab.LIBRARY -> Icons.Rounded.LibraryMusic },
                    t.label, tint = color, modifier = Modifier.size(24.dp),
                )
                Text(t.label, fontSize = 10.sp, lineHeight = 12.sp, color = color, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}

/** Floating "now playing" capsule above the tab bar; its cover, title and artist morph into the full player. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MiniPlayer(vm: MainViewModel, shared: SharedTransitionScope, anim: AnimatedVisibilityScope, onOpen: () -> Unit) {
    val state by vm.player.state.collectAsState()
    val song = state.current ?: return
    val marquee by vm.coverMarquee.collectAsState()
    // Closing the player: the capsule grows out around the cover, title and artist while they fly
    // down, finishing exactly as they land (PlayerBounds is 420 ms). Opening reverses it quickly.
    val pill by anim.transition.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) tween(260, delayMillis = 160, easing = androidx.compose.animation.core.CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)) else tween(140)
        },
        label = "pill",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val pillColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f)
    with(shared) {
        Box(Modifier.fillMaxWidth().height(56.dp).pressClick(scaleTo = 0.97f, onClick = onOpen)) {
            // The capsule itself, scaled separately so the flying text keeps its landing spot.
            Box(
                Modifier.matchParentSize().graphicsLayer {
                    alpha = pill
                    scaleX = 0.3f + 0.7f * pill
                    scaleY = 0.75f + 0.25f * pill
                    transformOrigin = TransformOrigin(0.28f, 0.5f)
                }.shadow(12.dp * pill, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(pillColor)
            )
            Row(Modifier.fillMaxSize().padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(
                    song.artworkHd,
                    Modifier.size(40.dp).sharedElement(rememberSharedContentState(SharedArt), anim, boundsTransform = PlayerBounds),
                    RoundedCornerShape(8.dp),
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    // Scroll only while the capsule is at rest, not while it morphs from/to the player.
                    val scroll = marquee && pill == 1f
                    com.youflip.music.ui.components.ScrollingText(
                        song.title.ifBlank { "加载中…" }, MaterialTheme.colorScheme.onSurface, TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp), scroll,
                        Modifier.sharedBounds(rememberSharedContentState(SharedTitle), anim, boundsTransform = PlayerBounds, resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(ContentScale.Fit, Alignment.CenterStart)),
                    )
                    com.youflip.music.ui.components.ScrollingText(
                        song.artist, MaterialTheme.colorScheme.onSurfaceVariant, TextStyle(fontSize = 13.sp), scroll,
                        Modifier.sharedBounds(rememberSharedContentState(SharedArtist), anim, boundsTransform = PlayerBounds, resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(ContentScale.Fit, Alignment.CenterStart)),
                    )
                }
                Row(Modifier.graphicsLayer { alpha = pill }) {
                IconButton(onClick = vm.player::previous) { Icon(Icons.Rounded.SkipPrevious, "上一首", Modifier.size(28.dp)) }
                IconButton(onClick = vm.player::togglePlay) { Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "播放/暂停", Modifier.size(30.dp)) }
                IconButton(onClick = vm.player::next) { Icon(Icons.Rounded.SkipNext, "下一首", Modifier.size(28.dp)) }
                }
            }
            // Thin progress line along the bottom of the capsule.
            Box(Modifier.align(Alignment.BottomStart).graphicsLayer { alpha = pill }.padding(horizontal = 24.dp).fillMaxWidth().height(2.dp)) {
                Box(Modifier.fillMaxWidth(state.progress.coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)))
            }
        }
    }
}

private const val SharedArt = "player-art"
private const val SharedTitle = "player-title"
private const val SharedArtist = "player-artist"
@OptIn(ExperimentalSharedTransitionApi::class)
private val PlayerEase = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)
@OptIn(ExperimentalSharedTransitionApi::class)
private val PlayerBounds = BoundsTransform { _, _ -> tween(420, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)) }

private enum class PlayerView { ARTWORK, LYRICS, QUEUE }

/**
 * Full-screen player. Same idea as the cover screen: title in bold, "artist · album" below; in lyrics view,
 * 5 s without a touch (or a tap on the title) enters immersive mode — controls fade out, lyrics grow,
 * the title block gets bigger and the album moves to its own line.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun FullPlayer(vm: MainViewModel, shared: SharedTransitionScope, anim: AnimatedVisibilityScope, onClose: () -> Unit) = with(shared) {
    val state by vm.player.state.collectAsState()
    val favIds by vm.favoriteIds.collectAsState()
    val lyrics by vm.lyrics.collectAsState()
    val song = state.current ?: return
    var view by rememberSaveable { mutableStateOf(PlayerView.LYRICS) }

    val lyricData = (lyrics as? Load.Ready)?.data
    val album = lyricData?.album ?: song.album
    val lines = lyricData?.lines?.takeIf { l -> l.any { it.text.isNotBlank() } }
    val synced = lyricData?.synced == true && lines != null
    val noLyrics = lyrics is Load.Ready && lines == null
    // No lyrics at all: show the artwork instead of an empty lyrics view.
    val shown = if (view == PlayerView.LYRICS && noLyrics) PlayerView.ARTWORK else view
    val isFav = song.id in favIds

    var lastTouch by remember { mutableLongStateOf(0L) }
    var immersive by remember { mutableStateOf(false) }
    LaunchedEffect(lastTouch, shown, lines != null) {
        immersive = false
        if (shown == PlayerView.LYRICS && lines != null) { delay(5_000); immersive = true }
    }
    val localView = LocalView.current
    DisposableEffect(immersive) {
        val window = (localView.context as? android.app.Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, localView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (immersive) controller?.hide(WindowInsetsCompat.Type.navigationBars()) else controller?.show(WindowInsetsCompat.Type.navigationBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.navigationBars()) }
    }
    var titleBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }

    // Drag the title area (or the artwork) down to collapse into the mini player.
    val scope = rememberCoroutineScope()
    val dragY = remember { Animatable(0f) }
    val density = LocalDensity.current
    val dragToClose = Modifier.draggable(
        rememberDraggableState { d -> scope.launch { dragY.snapTo((dragY.value + d).coerceAtLeast(0f)) } },
        Orientation.Vertical,
        onDragStopped = { v ->
            if (dragY.value > with(density) { 110.dp.toPx() } || v > 1800f) onClose()
            else scope.launch { dragY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
        },
    )
    val artMod = Modifier.sharedElement(rememberSharedContentState(SharedArt), anim, boundsTransform = PlayerBounds)
    val titleMod = Modifier.sharedBounds(rememberSharedContentState(SharedTitle), anim, boundsTransform = PlayerBounds, resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(ContentScale.Fit, Alignment.CenterStart))
    val artistMod = Modifier.sharedBounds(rememberSharedContentState(SharedArtist), anim, boundsTransform = PlayerBounds, resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(ContentScale.Fit, Alignment.CenterStart))

    Box(
        Modifier.fillMaxSize()
            .offset { IntOffset(0, dragY.value.roundToInt()) }
            .graphicsLayer { val f = (dragY.value / size.height).coerceIn(0f, 1f); scaleX = 1f - f * 0.08f; scaleY = 1f - f * 0.08f }
            .clip(RoundedCornerShape(if (dragY.value > 0f) 28.dp else 0.dp))
            .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    if (e.type == PointerEventType.Press && e.changes.none { titleBounds.contains(it.position) }) lastTouch = SystemClock.uptimeMillis()
                }
            }
        }
    ) {
        ArtBackdrop(song.artworkHd)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp)) {
            // Grabber: tap (or system back) to close.
            Box(Modifier.fillMaxWidth().height(28.dp).then(dragToClose).clickable(interactionSource = null, indication = null, onClick = onClose), contentAlignment = Alignment.Center) {
                Box(Modifier.size(40.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.45f)))
            }
            val toggleImmersive: () -> Unit = {
                if (immersive) lastTouch = SystemClock.uptimeMillis()
                else if (shown == PlayerView.LYRICS && lines != null) immersive = true
            }
            val marquee by vm.coverMarquee.collectAsState()
            // Last list view (lyrics / queue), so the list side keeps its content while it fades out to the artwork.
            var listView by remember { mutableStateOf(PlayerView.LYRICS) }
            if (shown != PlayerView.ARTWORK) listView = shown
            // Artwork ⇄ list: cross-fade with a slight zoom. The shared artwork / title links to the mini
            // player only while this switch is at rest, so the two sides never claim them at the same time.
            AnimatedContent(
                targetState = shown == PlayerView.ARTWORK,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    (fadeIn(tween(260, delayMillis = 60)) + scaleIn(tween(320, easing = PlayerEase), initialScale = 0.94f))
                        .togetherWith(fadeOut(tween(160)) + androidx.compose.animation.scaleOut(tween(200), targetScale = 0.97f))
                },
                label = "playerView",
            ) { artView ->
                val settled = transition.currentState == transition.targetState
                val art = if (settled) artMod else Modifier
                val tMod = if (settled) titleMod else Modifier
                val aMod = if (settled) artistMod else Modifier
                Column(Modifier.fillMaxSize()) {
                    if (artView) {
                        // Big artwork that shrinks a little while paused, like Apple Music.
                        val scale by animateFloatAsState(if (state.isPlaying) 1f else 0.86f, tween(450), label = "artScale")
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().then(dragToClose), contentAlignment = Alignment.Center) {
                            Artwork(
                                song.artworkHd, Modifier.size(min(maxWidth, maxHeight)).then(art).graphicsLayer { scaleX = scale; scaleY = scale }.shadow(24.dp, RoundedCornerShape(14.dp)),
                                shape = RoundedCornerShape(14.dp),
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Row(Modifier.then(dragToClose), verticalAlignment = Alignment.CenterVertically) {
                            SongInfo(song, album, immersive = false, large = true, Modifier.weight(1f), tMod, aMod, scroll = marquee && settled, onClick = {})
                            FavoriteButton(isFav) { vm.toggleFavorite(song) }
                        }
                    } else {
                        // Compact header: small artwork + the title block (tap it to toggle immersive lyrics).
                        Row(Modifier.then(dragToClose).padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Artwork(song.artworkHd, Modifier.size(64.dp).then(art), RoundedCornerShape(8.dp))
                            SongInfo(
                                song, album, immersive, large = false,
                                Modifier.weight(1f).padding(start = 14.dp)
                                    .onGloballyPositioned { titleBounds = it.boundsInRoot() },
                                tMod, aMod, scroll = marquee && settled,
                                onClick = toggleImmersive,
                            )
                            AnimatedVisibility(!immersive, enter = fadeIn(), exit = fadeOut()) { FavoriteButton(isFav) { vm.toggleFavorite(song) } }
                        }
                        // Lyrics ⇄ queue: the queue slides up from below, the lyrics come back down from above.
                        AnimatedContent(
                            targetState = listView,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            transitionSpec = {
                                val up = targetState == PlayerView.QUEUE
                                (slideInVertically(tween(340, easing = PlayerEase)) { h -> if (up) h / 6 else -h / 6 } + fadeIn(tween(240, delayMillis = 60)))
                                    .togetherWith(slideOutVertically(tween(260)) { h -> if (up) -h / 10 else h / 10 } + fadeOut(tween(160)))
                            },
                            label = "listView",
                        ) { v ->
                            if (v == PlayerView.LYRICS) {
                                LyricsPane(
                                    lines, synced, lyrics is Load.Loading, state.positionMs, immersive,
                                    onSeek = { vm.player.seekTo(it) },
                                    modifier = Modifier.fillMaxSize().padding(top = 4.dp),
                                )
                            } else Column(Modifier.fillMaxSize()) {
                                Text("接下来播放", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                                LazyColumn(Modifier.weight(1f)) {
                                    itemsIndexed(state.queue, key = { i, s -> "q$i${s.id}" }) { i, s ->
                                        SongRow(s, SongActions(onPlay = { vm.player.skipTo(i) }), isCurrent = i == state.currentIndex, compact = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(!immersive, enter = fadeIn(tween(300)) + expandVertically(tween(300)), exit = fadeOut(tween(200)) + shrinkVertically(tween(300))) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    SeekBar(state, vm.player::seekTo)
                    TransportRow(state, vm.player, Modifier.padding(vertical = 8.dp))
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium) }
                    // Apple Music bottom toolbar: lyrics / queue toggles.
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ToolbarToggle(Icons.Rounded.Lyrics, "歌词", shown == PlayerView.LYRICS) {
                            view = if (view == PlayerView.LYRICS) PlayerView.ARTWORK else PlayerView.LYRICS
                        }
                        ToolbarToggle(Icons.AutoMirrored.Rounded.QueueMusic, "播放队列", shown == PlayerView.QUEUE) {
                            view = if (view == PlayerView.QUEUE) PlayerView.ARTWORK else PlayerView.QUEUE
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Title (bold) over "artist · album"; immersive mode enlarges both and gives the album its own line. */
@Composable
private fun SongInfo(
    song: Song, album: String?, immersive: Boolean, large: Boolean, modifier: Modifier,
    titleModifier: Modifier = Modifier, artistModifier: Modifier = Modifier, scroll: Boolean = false, onClick: () -> Unit,
) {
    val titleSize by animateFloatAsState(when { large -> 24f; immersive -> 26f; else -> 20f }, label = "titleSize")
    val subSize by animateFloatAsState(if (immersive) 17f else if (large) 17f else 15f, label = "subSize")
    Column(modifier.clickable(interactionSource = null, indication = null, onClick = onClick)) {
        com.youflip.music.ui.components.ScrollingText(
            song.title.ifBlank { "加载中…" }, Color.White,
            TextStyle(fontSize = titleSize.sp, fontWeight = FontWeight.Bold, shadow = TextShadow), scroll, titleModifier,
        )
        AnimatedContent(
            modifier = artistModifier,
            targetState = immersive || album.isNullOrBlank(),
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "artistLine",
        ) { artistOnly ->
            com.youflip.music.ui.components.ScrollingText(
                if (artistOnly) song.artist else listOfNotNull(song.artist.takeIf { it.isNotBlank() }, album).joinToString(" · "),
                Color.White.copy(alpha = 0.7f), TextStyle(fontSize = subSize.sp, shadow = TextShadow), scroll,
            )
        }
        AnimatedVisibility(
            immersive && !album.isNullOrBlank(),
            enter = fadeIn(tween(300)) + expandVertically(tween(300)), exit = fadeOut(tween(200)) + shrinkVertically(tween(300)),
        ) {
            com.youflip.music.ui.components.ScrollingText(album.orEmpty(), Color.White.copy(alpha = 0.55f), TextStyle(fontSize = subSize.sp, shadow = TextShadow), scroll)
        }
    }
}

@Composable
private fun FavoriteButton(isFav: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "收藏",
            tint = if (isFav) LikeRed else Color.White.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun ToolbarToggle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent),
    ) {
        Icon(icon, label, tint = if (selected) Color.White else Color.White.copy(alpha = 0.7f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(vm: MainViewModel, onSettings: () -> Unit, onSearch: () -> Unit) {
    val home by vm.home.collectAsState()
    val ytHome by vm.ytHome.collectAsState()
    val history by vm.history.collectAsState()
    val favIds by vm.favoriteIds.collectAsState()
    val current by vm.player.state.collectAsState()

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = LocalBottomInset.current)) {
        item {
            LargeTitle("发现") {
                IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "搜索") }
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "设置") }
            }
        }
        if (history.isNotEmpty()) {
            item { SectionHeader("最近播放") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(history.take(20), key = { i, s -> "h$i${s.id}" }) { i, s ->
                        // Same card as the feed shelves (每日推荐 / 再听一遍).
                        com.youflip.music.ui.components.MediaCard(s.title, s.artist, s.thumbnail, 140.dp) { vm.playList(history, i) }
                    }
                }
            }
        }
        // Home feed of the active source (YouTube Music / NetEase), personalised when signed in.
        when (val yt = ytHome) {
            is Load.Ready -> yt.data.forEach { shelf ->
                item(key = "shelf-${shelf.title}") {
                    SectionHeader(shelf.title)
                    ShelfRow(shelf, onSong = { l, i -> vm.playList(l, i) }, onCollection = vm::openCollection)
                }
            }
            Load.Loading -> item { LoadingBox() }
            is Load.Failed -> item { MessageBox(yt.message) }
            else -> {}
        }
        home.radioSeed?.let { seed ->
            item { SectionHeader("因为你听了「${seed.title}」") }
            songSection(home.radio, vm, favIds, current.current?.id)
        }
        if (history.isEmpty() && (ytHome as? Load.Ready)?.data.isNullOrEmpty()) {
            item { MessageBox("去搜索页找一首喜欢的歌开始吧") }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.songSection(load: Load<List<Song>>, vm: MainViewModel, favIds: Set<String>, currentId: String?) {
    when (load) {
        Load.Idle -> {}
        Load.Loading -> item { LoadingBox() }
        is Load.Failed -> item { MessageBox(load.message) }
        is Load.Ready -> itemsIndexed(load.data, key = { i, s -> "x${load.hashCode()}$i${s.id}" }) { i, s ->
            SongRow(s, songActions(vm, s) { vm.playList(load.data, i) }, isCurrent = currentId == s.id, isFavorite = s.id in favIds)
        }
    }
}

private fun songActions(vm: MainViewModel, s: Song, onPlay: () -> Unit) = SongActions(
    onPlay = onPlay, onPlayNext = { vm.player.playNext(s) }, onEnqueue = { vm.player.enqueue(s) }, onToggleFavorite = { vm.toggleFavorite(s) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(vm: MainViewModel, onBack: () -> Unit) {
    val s by vm.search.collectAsState()
    val recent by vm.recentSearches.collectAsState()
    val favIds by vm.favoriteIds.collectAsState()
    val current by vm.player.state.collectAsState()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
        LargeTitle("搜索")
        OutlinedTextField(
            value = s.query, onValueChange = vm::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), singleLine = true,
            placeholder = { Text("搜索歌曲、专辑、歌手") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = if (s.query.isNotEmpty()) ({ IconButton(onClick = vm::clearSearch) { Icon(Icons.Rounded.Close, "清除") } }) else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.submitSearch() }),
            shape = RoundedCornerShape(28.dp),
        )
        val filters by vm.searchFilters.collectAsState()
        PrimaryTabRow(selectedTabIndex = filters.indexOf(s.filter).coerceAtLeast(0)) {
            filters.forEach { f -> Tab(selected = s.filter == f, onClick = { vm.setFilter(f) }, text = { Text(f.label) }) }
        }
        // Crossfade between suggestions, the spinner and results (and when the filter changes).
        AnimatedContent(
            Triple(s.filter, s.suggestions.isNotEmpty(), s.result::class),
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "searchBody",
        ) { _ ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomInset.current)) {
                if (s.suggestions.isNotEmpty()) {
                    items(s.suggestions) { sug ->
                        Row(Modifier.fillMaxWidth().clickable { vm.submitSearch(sug) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(sug, Modifier.padding(start = 12.dp))
                        }
                    }
                } else when (val r = s.result) {
                    Load.Idle -> if (recent.isNotEmpty()) {
                        item { SectionHeader("最近搜索", trailing = { androidx.compose.material3.TextButton(onClick = vm::clearRecentSearches) { Text("清空") } }) }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(recent) { q -> AssistChip(onClick = { vm.submitSearch(q) }, label = { Text(q) }) }
                            }
                        }
                    }
                    Load.Loading -> item { LoadingBox() }
                    is Load.Failed -> item { MessageBox(r.message) }
                    is Load.Ready -> {
                        if (r.data.songs.isEmpty() && r.data.collections.isEmpty()) item { MessageBox("没有找到结果") }
                        itemsIndexed(r.data.songs, key = { i, x -> "s$i${x.id}" }) { _, song ->
                            SongRow(song, songActions(vm, song) { vm.playRadio(song) }, isCurrent = current.current?.id == song.id, isFavorite = song.id in favIds)
                        }
                        items(r.data.collections, key = { it.url }) { c -> CollectionRow(c) { vm.openCollection(c) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionRow(c: MusicCollection, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressClick(scaleTo = 0.98f, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(c.thumbnail, Modifier.size(64.dp))
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(c.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(c.subtitle, maxLines = 1, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CollectionScreen(vm: MainViewModel, load: Load<MusicRepository.CollectionDetail>) {
    val favIds by vm.favoriteIds.collectAsState()
    val current by vm.player.state.collectAsState()
    // Fade from the spinner to the list (later pages of a long playlist update in place).
    AnimatedContent(load, contentKey = { it is Load.Ready }, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "collection") { load ->
        when (load) {
            Load.Idle, Load.Loading -> LoadingBox(Modifier.statusBarsPadding())
            is Load.Failed -> Column(Modifier.statusBarsPadding()) {
                IconButton(onClick = vm::closeCollection) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
                MessageBox(load.message)
            }
            is Load.Ready -> {
                val d = load.data
                LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = LocalBottomInset.current)) {
                    item { IconButton(onClick = vm::closeCollection) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } }
                    item {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Artwork(com.youflip.music.data.Thumbs.hd(d.collection.thumbnail), Modifier.size(200.dp).clip(RoundedCornerShape(16.dp)), RoundedCornerShape(16.dp))
                            Text(d.collection.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
                            Text("${d.collection.subtitle} · ${d.songs.size} 首", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = { vm.playList(d.songs, 0) }, enabled = d.songs.isNotEmpty()) { Icon(Icons.Rounded.PlayArrow, null); Text("播放全部") }
                                FilledTonalButton(onClick = { vm.playList(d.songs.shuffled(), 0) }, enabled = d.songs.isNotEmpty()) { Icon(Icons.Rounded.Shuffle, null); Text("随机播放") }
                            }
                        }
                    }
                    itemsIndexed(d.songs, key = { i, s -> "c$i${s.id}" }) { i, s ->
                        SongRow(s, songActions(vm, s) { vm.playList(d.songs, i) }, isCurrent = current.current?.id == s.id, isFavorite = s.id in favIds)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreen(vm: MainViewModel) {
    val signedIn by vm.signedIn.collectAsState()
    val history by vm.history.collectAsState()
    val ytLiked by vm.ytLiked.collectAsState()
    val ytPlaylists by vm.ytPlaylists.collectAsState()
    val ytHistory by vm.ytHistory.collectAsState()
    val favIds by vm.favoriteIds.collectAsState()
    val current by vm.player.state.collectAsState()
    val tabs = if (signedIn) listOf("喜欢", "歌单", "历史") else listOf("最近播放")
    var sub by rememberSaveable(signedIn) { mutableIntStateOf(0) }
    // Search inside the library only (liked songs, playlists, history), never the online catalogue.
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    fun Song.matches() = q.isEmpty() || title.contains(q, true) || artist.contains(q, true) || album?.contains(q, true) == true
    fun MusicCollection.matches() = q.isEmpty() || title.contains(q, true) || subtitle.contains(q, true)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LargeTitle("资料库") {
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, "在资料库中搜索") }
            if (signedIn) IconButton(onClick = vm::refreshYtLibrary) { Icon(Icons.Rounded.Refresh, "刷新") }
            else TextButton(onClick = vm::openLogin) { Text(if (vm.source.collectAsState().value == com.youflip.music.data.MusicSource.NETEASE) "登录网易云" else "登录 YouTube") }
        }
        AnimatedVisibility(searching, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            TextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), singleLine = true,
                placeholder = { Text("搜索资料库") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "清除") } }) else null,
                shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }
        PrimaryTabRow(selectedTabIndex = sub) {
            tabs.forEachIndexed { i, t -> Tab(sub == i, onClick = { sub = i }, text = { Text(t) }) }
        }
        // Sliding between 喜欢 / 歌单 / 历史 in the direction of the tab.
        AnimatedContent(
            targetState = sub,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(320, easing = EmphasizedEasing)) { dir * it / 5 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(320, easing = EmphasizedEasing)) { -dir * it / 5 } + fadeOut(tween(150)))
            },
            label = "librarySub",
        ) { page ->
        Column(Modifier.fillMaxSize()) {
            val songs: Load<List<Song>>? = when {
                !signedIn -> Load.Ready(history)
                page == 0 -> ytLiked
                page == 2 -> ytHistory
                else -> null
            }
            if (songs == null) {
                when (val p = ytPlaylists) {
                    is Load.Ready -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomInset.current)) {
                        val shown = p.data.filter { it.matches() }
                        if (shown.isEmpty()) item { MessageBox(if (q.isEmpty()) "还没有歌单" else "资料库里没有匹配的歌单") }
                        items(shown, key = { it.url }) { c -> CollectionRow(c) { vm.openCollection(c) } }
                    }
                    is Load.Failed -> MessageBox(p.message)
                    else -> LoadingBox()
                }
            } else when (songs) {
                is Load.Ready -> {
                    val list = songs.data.filter { it.matches() }
                    if (list.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { vm.playList(list, 0) }) { Icon(Icons.Rounded.PlayArrow, null); Text("播放全部") }
                                FilledTonalButton(onClick = { vm.playList(list.shuffled(), 0) }) { Icon(Icons.Rounded.Shuffle, null); Text("随机播放") }
                            // Signed out, this tab is the phone's own play history.
                            if (!signedIn) {
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = vm::clearLocalHistory) { Text("清空") }
                            }
                        }
                    }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomInset.current)) {
                        if (list.isEmpty()) item { MessageBox(if (q.isEmpty()) "这里还是空的" else "资料库里没有匹配的歌曲") }
                        itemsIndexed(list, key = { i, s -> "l$page$i${s.id}" }) { i, s ->
                            SongRow(s, songActions(vm, s) { vm.playList(list, i) }, isCurrent = current.current?.id == s.id, isFavorite = s.id in favIds)
                        }
                    }
                }
                is Load.Failed -> MessageBox(songs.message)
                else -> LoadingBox()
            }
        }
        }
    }
}
