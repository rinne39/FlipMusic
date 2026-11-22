package com.youflip.music.ui.cover

import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.ui.zIndex
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.BiasAlignment
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.withStyle
import com.youflip.music.ui.theme.LikeRed
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntSize
import com.youflip.music.data.CameraStrip
import com.youflip.music.ui.components.SeekBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.youflip.music.data.LyricLine
import kotlinx.coroutines.delay
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import com.youflip.music.ui.rememberCameraIsland
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import coil3.compose.AsyncImage
import com.youflip.music.data.Lyrics
import com.youflip.music.data.formatDuration
import com.youflip.music.playback.PlayerState
import com.youflip.music.ui.CoverScreenSpec
import com.youflip.music.ui.components.PlayPauseButton
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import com.youflip.music.ui.components.pressClick
import androidx.compose.material3.Button
import androidx.compose.material3.TextField
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.youflip.music.data.SearchFilter
import com.youflip.music.data.Song
import com.youflip.music.ui.Load
import com.youflip.music.ui.MainViewModel
import com.youflip.music.ui.SettingsContent
import com.youflip.music.ui.components.Artwork
import com.youflip.music.ui.components.LoadingBox
import com.youflip.music.ui.components.MessageBox
import com.youflip.music.ui.components.SongActions
import com.youflip.music.ui.components.SongRow
import com.youflip.music.ui.components.CollectionCards
import com.youflip.music.ui.components.ShelfRow
import kotlinx.coroutines.launch

/** Pages left to right: queue ← now playing → discover (with search at its top). */
private enum class CoverPage(val label: String) { QUEUE("队列"), NOW("正在播放"), DISCOVER("发现") }

/**
 * Layout for the Galaxy Z Flip 7 FlexWindow (~380 x 420 dp, edge-to-edge with camera cut-outs).
 * One full-bleed page at a time, swiped horizontally; every touch target is >= 48 dp and every
 * page is padded by the display cut-out insets so nothing sits under the cameras.
 */
@Composable
fun CoverApp(vm: MainViewModel) {
    val base = androidx.compose.ui.platform.LocalDensity.current
    // The portrait letterbox is only ~62 % as wide as the full FlexWindow, so at the full 1.5× the top-bar
    // icons, page dots and row buttons crowd out the titles there: scale it up less.
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val tall = cfg.screenHeightDp > cfg.screenWidthDp * CoverScreenSpec.MAX_FULL_TALLNESS
    val scale = if (tall) CoverScreenSpec.UI_SCALE_TALL else CoverScreenSpec.UI_SCALE
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density * scale, base.fontScale * (if (tall) CoverScreenSpec.FONT_SCALE_TALL else CoverScreenSpec.FONT_SCALE)),
    ) { CoverAppContent(vm) }
}

@Composable
private fun CoverAppContent(vm: MainViewModel) {
    val state by vm.player.state.collectAsState()
    val pager = rememberPagerState(initialPage = if (state.current == null) CoverPage.DISCOVER.ordinal else CoverPage.NOW.ordinal) { CoverPage.entries.size }
    val scope = rememberCoroutineScope()
    val collection by vm.collection.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    val goTo: (CoverPage) -> Unit = { p -> scope.launch { pager.animateScrollToPage(p.ordinal) } }
    // When playback starts from outside (shared link, notification), jump to the player.
    val hasSong = state.current != null
    LaunchedEffect(hasSong) { if (hasSong && pager.currentPage == CoverPage.DISCOVER.ordinal) pager.animateScrollToPage(CoverPage.NOW.ordinal) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            when (CoverPage.entries[page]) {
                CoverPage.NOW -> com.youflip.music.ui.theme.ForceDark {
                    com.youflip.music.ui.theme.DarkSystemBars(pager.currentPage == page && collection is Load.Idle && !showSettings)
                    NowPlayingPage(vm, isCurrent = pager.currentPage == page, onOpenDiscover = { goTo(CoverPage.DISCOVER) }, onOpenQueue = { goTo(CoverPage.QUEUE) })
                }
                CoverPage.QUEUE -> QueuePage(vm)
                CoverPage.DISCOVER -> {
                    // Text on the discover page is set a little larger (12 %) than elsewhere on the cover.
                    val d = LocalDensity.current
                    androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale * 1.12f)) {
                        DiscoverPage(vm, onPlayed = { goTo(CoverPage.NOW) }, onSettings = { showSettings = true })
                    }
                }
            }
        }
        // On the now-playing page the dots only show while swiping between pages.
        val dotsAlpha by animateFloatAsState(if (pager.currentPage != CoverPage.NOW.ordinal || pager.isScrollInProgress) 1f else 0f, label = "dots")
        PageDots(pager, Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(top = 4.dp).graphicsLayer { alpha = dotsAlpha })

        // Overlays slide in from the right like a system page.
        val collectionOpen = collection !is Load.Idle
        var shownCollection by remember { mutableStateOf(collection) }
        if (collectionOpen) shownCollection = collection
        if (collectionOpen) BackHandler { vm.closeCollection() }
        OneUiOverlay(collectionOpen) {
            CoverCollection(vm, shownCollection, onPlayed = { vm.closeCollection(); goTo(CoverPage.NOW) })
        }
        if (showSettings) BackHandler { showSettings = false }
        OneUiOverlay(showSettings) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.statusBars)) {
                CoverTopBar("设置", onClose = { showSettings = false })
                SettingsContent(vm, compact = true)
            }
        }
    }
}

/** Android's "emphasized" motion curve. */
private val SystemEasing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)

@Composable
private fun OneUiOverlay(visible: Boolean, content: @Composable () -> Unit) {
    // Same system page transition as the unfolded layout: slide in from the right, slide back out.
    // Only open/close animates; the content itself switches instantly so loading never flashes.
    androidx.compose.animation.AnimatedVisibility(
        visible,
        enter = androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(350, easing = SystemEasing)) { it / 4 } +
            fadeIn(androidx.compose.animation.core.tween(250)),
        exit = androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(300, easing = SystemEasing)) { it / 4 } +
            fadeOut(androidx.compose.animation.core.tween(200)),
    ) { Box(Modifier.fillMaxSize()) { content() } }
}

@Composable
private fun PageDots(pager: PagerState, modifier: Modifier) {
    // The now-playing page is always dark; the others follow the theme.
    val dot = if (pager.currentPage == CoverPage.NOW.ordinal) Color.White else MaterialTheme.colorScheme.onBackground
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(pager.pageCount) { i ->
            Box(
                Modifier.size(if (pager.currentPage == i) 14.dp else 5.dp, 5.dp).clip(CircleShape)
                    .background(dot.copy(alpha = if (pager.currentPage == i) 1f else 0.35f))
            )
        }
    }
}

@Composable
private fun CoverTopBar(title: String, onClose: (() -> Unit)? = null, wrap: Boolean = false, actions: @Composable () -> Unit = {}) {
    // Fixed height so every page's title sits at the same place whether or not it has action buttons;
    // with [wrap] a long title (playlist names) breaks onto more lines instead of being cut off.
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).then(if (wrap) Modifier.heightIn(min = 48.dp) else Modifier.height(48.dp)).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = if (wrap) Alignment.Top else Alignment.CenterVertically,
    ) {
        val fg = MaterialTheme.colorScheme.onBackground
        // Shadow only for light text (it sits on album art in the player); none on light pages.
        val titleStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 1.15.em, shadow = if (fg.luminance() > 0.5f) TextShadow else null)
        if (wrap) {
            // Long titles: break at a comma / bar (dropped) into two lines.
            BoxWithConstraints(Modifier.weight(1f).padding(vertical = 4.dp)) {
                val measurer = androidx.compose.ui.text.rememberTextMeasurer()
                val widthPx = constraints.maxWidth
                val shown = remember(title, widthPx) { breakTitle(title, titleStyle, widthPx, measurer) }
                Text(shown, style = titleStyle, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        } else {
            // The (short) title is measured first at its full width; the spacer takes the rest so the actions sit at the right edge.
            Text(title, style = titleStyle, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
        }
        actions()
        if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "关闭") }
    }
}

// ---------------- Now playing ----------------

/**
 * FlexWindow now-playing layout, built around the camera row in the bottom-right corner:
 *
 *  ┌──────────────────────────────┐
 *  │ previous lyric (blurred)     │
 *  │ CURRENT LYRIC (tap = seek)   │
 *  │ next lyric (blurred)         │
 *  │ Title                        │
 *  │ Artist · Album         1:38  │
 *  │  ⏮  ⏯  ⏭    ╭─(·)(◉)(◉)──╮  │  ← progress runs around the cameras;
 *  │              │  cut-out bg  │  │    inside: frosted art or black
 *  │              ╰────────────╯  │
 *  └──────────────────────────────┘
 * After 5 s untouched (or a tap on the title) the controls fade out and the title, artist and album
 * fly down into the band beside the cameras, leaving the upper area to the lyrics.
 * Swipe left/right for the queue.
 */
@Composable
private fun NowPlayingPage(vm: MainViewModel, isCurrent: Boolean, onOpenDiscover: () -> Unit, onOpenQueue: () -> Unit) {
    val state by vm.player.state.collectAsState()
    val lyrics by vm.lyrics.collectAsState()
    val favIds by vm.favoriteIds.collectAsState()
    val marquee by vm.coverMarquee.collectAsState()
    val song = state.current

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (song == null) {
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(20.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Flip Music", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("合上手机也能听。去发现页挑一首吧", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = onOpenDiscover) { Icon(Icons.Rounded.Explore, null); Spacer(Modifier.width(6.dp)); Text("发现音乐") }
            }
            return@Box
        }

        // Cover + dark scrim for legibility.
        AsyncImage(song.artworkHd, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.25f), 0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f))))

        val lyricData = (lyrics as? Load.Ready)?.data
        val album = lyricData?.album ?: song.album   // lyrics lookup first, else the song's own album
        val lines = lyricData?.lines?.takeIf { l -> l.any { it.text.isNotBlank() } }
        val synced = lyricData?.synced == true && lines != null

        // Any touch shows the controls; 5 s without one lets the lyrics take the whole upper area.
        var lastTouch by remember { mutableLongStateOf(0L) }
        // Taps on the title/artist toggle immersive mode instead of just waking the controls.
        var titleBounds by remember { mutableStateOf(Rect.Zero) }
        var rootPos by remember { mutableStateOf(Offset.Zero) }
        var immersive by remember { mutableStateOf(false) }
        LaunchedEffect(lastTouch, lines != null, isCurrent) {
            immersive = false
            if (lines != null && isCurrent) { delay(5_000); immersive = true }
        }
        // The now-playing page hides the navigation bar (swipe up from the bottom brings it back briefly),
        // so the handle never sits on top of the controls beside the cameras.
        val view = LocalView.current
        DisposableEffect(isCurrent) {
            val window = (view.context as? android.app.Activity)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isCurrent) controller?.hide(WindowInsetsCompat.Type.navigationBars())
            onDispose { controller?.show(WindowInsetsCompat.Type.navigationBars()) }
        }
        val stripMode by vm.cameraStrip.collectAsState()
        val letterboxed = com.youflip.music.ui.rememberCoverLetterboxed()

        BoxWithConstraints(
            Modifier.fillMaxSize().onGloballyPositioned { rootPos = it.positionInRoot() }.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.type == PointerEventType.Press && e.changes.none { titleBounds.contains(it.position) }) lastTouch = SystemClock.uptimeMillis()
                    }
                }
            }
        ) {
            val density = LocalDensity.current
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            // Letterboxed (forced 16:9) the cameras are outside the window: no track around them,
            // just a plain progress line above the controls.
            // The full FlexWindow is a little taller than wide (948 × 1048). Any landscape-shaped window
            // (One UI's compat modes, e.g. 948 × 830) doesn't reach the cameras, so it gets no camera track.
            val wide = maxWidth > maxHeight
            val tall = maxHeight > maxWidth * CoverScreenSpec.MAX_FULL_TALLNESS
            val island = if (letterboxed || wide || tall) null else rememberCameraIsland(wPx, hPx)
            val ring = island?.let {
                with(density) {
                    val gap = 9.dp.toPx()
                    val edge = 3.dp.toPx()
                    Rect(it.left - gap, it.top - gap, minOf(it.right + gap, wPx - edge), minOf(it.bottom + gap, hPx - edge))
                }
            }
            val fullH = maxHeight
            val stripTop = if (ring != null) with(density) { ring.top.toDp() } - 10.dp else fullH - 84.dp   // room for the slim progress bar above the control row
            // Without the cameras in the window (One UI's 948 × 839 mode above the cutout, or other letterboxes):
            // one full-width bottom row with like / shuffle / prev / play / next / repeat. It hides in immersive
            // mode, where a one-line "title · artist · album" with a progress bar takes its place; a tap brings it back.
            val portraitBox = ring == null
            val controlsLeft = 16.dp
            val regionEnd = if (ring != null) with(density) { ring.left.toDp() } - 8.dp else controlsLeft - 8.dp
            var seekPreview by remember { mutableStateOf<Float?>(null) }
            val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val shownMs = seekPreview?.let { (it * state.durationMs).toLong() } ?: state.positionMs
            val tapTitle: () -> Unit = {
                if (immersive) lastTouch = SystemClock.uptimeMillis()   // show controls, restart the 5 s timer
                else if (lines != null) immersive = true
            }

            CameraHoleBackground(song.artworkHd, stripMode, ring)

            // Song info: title, artist and album are separate texts that each glide from their normal spot
            // (title row + "artist · album" line right above the band) to their immersive spot, driven by one
            // progress value, so every part moves in a single step with nothing re-laying out midway.
            //  · 1:1: three centred lines beside the cameras (a little larger).
            //  · landscape letterbox: one "title · artist · album" line in the band.
            //  · portrait letterbox: title / artist / album stacked; immersive mode just lowers the stack.
            val bandH = fullH - stripTop
            val infoMove = tween<androidx.compose.ui.unit.Dp>(420, easing = SystemEasing)
            val leverGap = if (ring != null) LeverSpace else 0.dp
            val stacked = ring == null && !wide   // portrait letterbox
            val infoH = 34.dp + 20.dp + (if (stacked) 20.dp else 0.dp)   // title row + one (or two) sub lines
            // Like / shuffle / repeat and the time sit in their own layer on the right: in immersive mode
            // they blur and fade out where they are instead of travelling with the song info.
            var extrasW by remember { mutableStateOf(0.dp) }
            val extrasAlpha by animateFloatAsState(if (immersive) 0f else 1f, tween(320), label = "extrasAlpha")
            val extrasBlur by animateFloatAsState(if (immersive) 14f else 0f, tween(320), label = "extrasBlur")
            // The lyrics give up the info's space only while it sits above the band.
            val reserve by animateDpAsState(if (immersive) 0.dp else infoH + (if (stacked) 10.dp else 4.dp) + leverGap, infoMove, label = "reserve")

            Column(Modifier.fillMaxWidth().height(stripTop - reserve).padding(top = topInset)) {
                LyricsPane(
                    lines, synced, lyrics is Load.Loading, state.positionMs, immersive,
                    onSeek = { vm.player.seekTo(it) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    lineGap = 2.dp,   // tighter lines on the small cover screen
                )
            }

            if (ring != null) {
                // 0 = normal, 1 = immersive: the progress bar between the info and the controls flies into
                // the camera track (shrinking to its width, sliding onto its top edge) and fades into it.
                val merge by animateFloatAsState(if (immersive) 1f else 0f, tween(450, easing = SystemEasing), label = "barMerge")
                CameraProgressRing(ring, state, external = seekPreview, glow = merge)
                if (merge < 1f) {
                    val barW = maxWidth - 32.dp
                    val barTop = stripTop - LeverSpace
                    Box(
                        Modifier.offset(x = 16.dp, y = barTop).width(barW)
                            .graphicsLayer {
                                val wPxBar = barW.toPx()
                                val cx = 16.dp.toPx() + wPxBar / 2
                                val cy = barTop.toPx() + 7.dp.toPx()
                                translationX = (ring.center.x - cx) * merge
                                translationY = (ring.top - cy) * merge
                                scaleX = 1f + (ring.width / wPxBar - 1f) * merge
                                alpha = 1f - merge
                            }
                    ) {
                        SlimSeekBar(state, vm.player::seekTo, enabled = !immersive, onPreview = { seekPreview = it })
                    }
                }
            }

            // Camera band, left of the lenses: the controls (hidden in immersive mode).
            val controlsRight = if (ring != null) regionEnd else maxWidth - 16.dp
            val showControls = !immersive
            Column(Modifier.offset(x = controlsLeft, y = stripTop).width(controlsRight - controlsLeft).height(bandH)) {
                // No cameras in the window: a draggable progress bar under the time, above the controls.
                if (portraitBox) {
                    androidx.compose.animation.AnimatedVisibility(!immersive, enter = fadeIn(tween(200, delayMillis = 260)), exit = fadeOut(tween(120))) {
                        if (stacked) {
                            // Portrait letterbox: the time rides along on the progress bar's line.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) { SlimSeekBar(state, vm.player::seekTo, onPreview = { seekPreview = it }) }
                                Text(
                                    "${formatDuration(shownMs / 1000)} / ${formatDuration(state.durationMs / 1000)}",
                                    style = TextStyle(fontSize = 12.sp, shadow = TextShadow), maxLines = 1, modifier = Modifier.padding(start = 8.dp),
                                    color = if (seekPreview != null) Color.White else Color.White.copy(alpha = 0.6f),
                                )
                            }
                        } else SlimSeekBar(state, vm.player::seekTo)
                    }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    val btn = (maxWidth / (if (portraitBox) 5.6f else 3.3f)).coerceIn(30.dp, 52.dp)
                    androidx.compose.animation.AnimatedVisibility(
                        showControls,
                        // Back from immersive: the info lifts up first, then the controls pop in.
                        enter = fadeIn(tween(200, delayMillis = 260)) + scaleIn(tween(320, delayMillis = 260, easing = SystemEasing), initialScale = 0.6f),
                        exit = fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.85f),
                    ) {
                        if (portraitBox) {
                            // Letterboxed row: queue at the left edge (shuffle / repeat live there), like at the right edge,
                            // the transport grouped in the middle with a larger play button. The row is widened by the icon
                            // padding so the side glyphs line up with the 16 dp edge of the text and progress bar.
                            val modeOff = Color.White.copy(alpha = 0.6f)
                            val side = btn * 0.5f
                            val inset = (btn - side) / 2
                            Row(Modifier.requiredWidth(maxWidth + inset * 2), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onOpenQueue, Modifier.size(btn)) {
                                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, "播放队列", tint = modeOff, modifier = Modifier.size(side))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(btn * 0.35f), verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = vm.player::previous, Modifier.size(btn)) { Icon(Icons.Rounded.SkipPrevious, "上一首", tint = Color.White, modifier = Modifier.size(btn * 0.62f)) }
                                    PlayPauseButton(state, vm.player::togglePlay, btn * 1.18f)
                                    IconButton(onClick = vm.player::next, Modifier.size(btn)) { Icon(Icons.Rounded.SkipNext, "下一首", tint = Color.White, modifier = Modifier.size(btn * 0.62f)) }
                                }
                                val isFav = song.id in favIds
                                IconButton(onClick = { vm.toggleFavorite(song) }, Modifier.size(btn)) {
                                    Icon(if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "收藏", tint = if (isFav) LikeRed else modeOff, modifier = Modifier.size(side))
                                }
                            }
                        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = vm.player::previous, Modifier.size(btn)) { Icon(Icons.Rounded.SkipPrevious, "上一首", tint = Color.White, modifier = Modifier.size(btn * 0.62f)) }
                            PlayPauseButton(state, vm.player::togglePlay, btn)
                            IconButton(onClick = vm.player::next, Modifier.size(btn)) { Icon(Icons.Rounded.SkipNext, "下一首", tint = Color.White, modifier = Modifier.size(btn * 0.62f)) }
                        }
                    }
                }
            }

            run {
                val t by animateFloatAsState(if (immersive) 1f else 0f, tween(450, easing = SystemEasing), label = "infoMove")
                fun lerp(a: Float, b: Float) = a + (b - a) * t
                val big = ring != null
                val titleStyle = TextStyle(fontSize = lerp(20f, if (big) 22f else 20f).sp, fontWeight = FontWeight.SemiBold, shadow = TextShadow)
                val subStyle = TextStyle(fontSize = lerp(14f, if (big) 15f else 14f).sp, shadow = TextShadow)
                var tSz by remember { mutableStateOf(IntSize.Zero) }
                var aSz by remember { mutableStateOf(IntSize.Zero) }
                var lSz by remember { mutableStateOf(IntSize.Zero) }
                var sepW by remember { mutableIntStateOf(0) }
                val hasAlbum = !album.isNullOrBlank()
                val px = with(density) { object { val x0 = 16.dp.toPx(); val row = 34.dp.toPx(); val gap8 = 8.dp.toPx() } }
                val w = with(density) { (maxWidth - 32.dp).toPx() }
                val extrasPx = with(density) { extrasW.toPx() }
                val normTop = with(density) { (stripTop - leverGap - (if (stacked) 10.dp else 4.dp) - infoH).toPx() }
                fun at(x: Float, y: Float) = Modifier.offset { IntOffset(x.toInt(), y.toInt()) }
                fun maxW(p: Float) = with(density) { p.coerceAtLeast(0f).toDp() }
                val scroll = marquee && (t == 0f || t == 1f)

                // Normal spots
                // Portrait letterbox has nothing on the right (time sits on the progress bar): use the full width.
                val subAvail = if (stacked) w else w - extrasPx - px.gap8
                val nTitle = Offset(px.x0, normTop + (px.row - tSz.height) / 2)
                val nArtist = Offset(px.x0, normTop + px.row)
                val nAlbum = if (stacked) Offset(px.x0, normTop + px.row + aSz.height) else Offset(px.x0 + aSz.width + sepW, normTop + px.row)
                val nTitleMax = subAvail
                val nArtistMax = if (hasAlbum && !stacked) subAvail * 0.55f else subAvail
                val nAlbumMax = if (stacked) w else subAvail - aSz.width - sepW

                // Immersive spots
                val iTitle: Offset; val iArtist: Offset; val iAlbum: Offset
                val iTitleMax: Float; val iArtistMax: Float; val iAlbumMax: Float
                if (ring != null) {
                    val regW = with(density) { (regionEnd - 16.dp).toPx() }
                    val total = tSz.height + aSz.height + (if (hasAlbum) lSz.height else 0)
                    val top = ring.center.y - total / 2f
                    iTitle = Offset(px.x0 + (regW - tSz.width) / 2, top)
                    iArtist = Offset(px.x0 + (regW - aSz.width) / 2, top + tSz.height)
                    iAlbum = Offset(px.x0 + (regW - lSz.width) / 2, top + tSz.height + aSz.height)
                    iTitleMax = regW; iArtistMax = regW; iAlbumMax = regW
                } else if (stacked) {
                    // Same stack, just lowered into the band where the controls were.
                    val total = tSz.height + aSz.height + (if (hasAlbum) lSz.height else 0)
                    val top = with(density) { (fullH - 16.dp).toPx() } - total
                    iTitle = Offset(px.x0, top)
                    iArtist = Offset(px.x0, top + tSz.height)
                    iAlbum = Offset(px.x0, top + tSz.height + aSz.height)
                    iTitleMax = nTitleMax; iArtistMax = nArtistMax; iAlbumMax = w
                } else {
                    val lineC = with(density) { (stripTop + bandH / 2 - 6.dp).toPx() }
                    iTitle = Offset(px.x0, lineC - tSz.height / 2f)
                    iArtist = Offset(px.x0 + tSz.width + sepW, lineC - aSz.height / 2f)
                    iAlbum = Offset(iArtist.x + aSz.width + sepW, lineC - aSz.height / 2f)
                    iTitleMax = w * 0.45f
                    val rest = w - tSz.width - 2 * sepW
                    iArtistMax = if (hasAlbum) rest * 0.5f else rest
                    iAlbumMax = w - (iAlbum.x - px.x0)
                }
                fun mix(a: Offset, b: Offset) = Offset(lerp(a.x, b.x), lerp(a.y, b.y))

                val title = mix(nTitle, iTitle)
                com.youflip.music.ui.components.ScrollingText(
                    song.title.ifBlank { "加载中…" }, Color.White, titleStyle, scroll,
                    at(title.x, title.y).widthIn(max = maxW(lerp(nTitleMax, iTitleMax))).onSizeChanged { tSz = it },
                )
                // " · " between title and artist: only on the single immersive line (letterboxed).
                Text(
                    " · ", color = Color.White.copy(alpha = if (ring == null && !stacked) 0.7f * t else 0f), style = subStyle, maxLines = 1,
                    modifier = at(px.x0 + tSz.width, iArtist.y).onSizeChanged { sepW = it.width },
                )
                val artist = mix(nArtist, iArtist)
                com.youflip.music.ui.components.ScrollingText(
                    song.artist, Color.White.copy(alpha = 0.7f), subStyle, scroll,
                    at(artist.x, artist.y).widthIn(max = maxW(lerp(nArtistMax, iArtistMax))).onSizeChanged { aSz = it },
                )
                if (hasAlbum) {
                    // " · " between artist and album; it fades out where the album gets its own line (1:1).
                    val sepAlpha = when { stacked -> 0f; ring != null -> 0.7f * (1f - t); else -> 0.7f }
                    Text(" · ", color = Color.White.copy(alpha = sepAlpha), style = subStyle, maxLines = 1, modifier = at(artist.x + aSz.width, artist.y))
                    val al = mix(nAlbum, iAlbum)
                    com.youflip.music.ui.components.ScrollingText(
                        album.orEmpty(), Color.White.copy(alpha = 0.7f), subStyle, scroll,
                        at(al.x, al.y).widthIn(max = maxW(lerp(nAlbumMax, iAlbumMax))).onSizeChanged { lSz = it },
                    )
                }
                // One tap target over the whole info area (not just the glyphs), moving with it: a tap anywhere
                // in it toggles immersive mode.
                val nRect = Rect(px.x0, normTop, px.x0 + w, normTop + with(density) { infoH.toPx() })
                val iRect = when {
                    ring != null -> { val regW = with(density) { (regionEnd - 16.dp).toPx() }; Rect(px.x0, iTitle.y, px.x0 + regW, iAlbum.y.coerceAtLeast(iArtist.y) + (if (hasAlbum) lSz.height else aSz.height)) }
                    stacked -> Rect(px.x0, iTitle.y, px.x0 + w, iAlbum.y.coerceAtLeast(iArtist.y) + (if (hasAlbum) lSz.height else aSz.height))
                    else -> Rect(px.x0, minOf(iTitle.y, iArtist.y), px.x0 + w, maxOf(iTitle.y + tSz.height, iArtist.y + aSz.height))
                }
                val hot = Rect(lerp(nRect.left, iRect.left), lerp(nRect.top, iRect.top), lerp(nRect.right, iRect.right), lerp(nRect.bottom, iRect.bottom))
                Box(
                    Modifier.offset { IntOffset(hot.left.toInt(), hot.top.toInt()) }
                        .size(with(density) { hot.width.coerceAtLeast(1f).toDp() }, with(density) { hot.height.coerceAtLeast(1f).toDp() })
                        .onGloballyPositioned { titleBounds = it.boundsInRoot().translate(-rootPos) }
                        .clickable(interactionSource = null, indication = null, onClick = tapTitle)
                )
                // Letterboxed: display-only progress line under the single line (fades in with immersive mode).
                if (ring == null && !stacked && t > 0f) {
                    val progress = state.progress.coerceIn(0f, 1f)
                    Canvas(
                        Modifier.offset(x = 16.dp, y = stripTop + bandH / 2 + 12.dp).width(maxWidth - 32.dp).height(3.dp)
                            .graphicsLayer { alpha = t }
                    ) {
                        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                        drawRoundRect(Color.White.copy(alpha = 0.25f), cornerRadius = r)
                        drawRoundRect(Color.White, size = size.copy(width = size.width * progress), cornerRadius = r)
                    }
                }
            }

            if (extrasAlpha > 0f && !stacked) {
                Column(
                    Modifier.align(Alignment.TopEnd).offset(x = (-16).dp, y = stripTop - leverGap - infoH - 4.dp)
                        .onSizeChanged { extrasW = with(density) { it.width.toDp() } }
                        .graphicsLayer { alpha = extrasAlpha }
                        .then(if (extrasBlur > 0.5f) Modifier.blur(extrasBlur.dp, BlurredEdgeTreatment.Unbounded) else Modifier),
                    horizontalAlignment = Alignment.End,
                ) {
                    // In letterboxed portrait these three live in the control row instead; only the time stays here.
                    if (portraitBox) Spacer(Modifier.height(34.dp)) else Row(Modifier.height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                        val isFav = song.id in favIds
                        val off = Color.White.copy(alpha = 0.6f)
                        val on = MaterialTheme.colorScheme.primary
                        IconButton(onClick = { vm.toggleFavorite(song) }, Modifier.size(34.dp)) {
                            Icon(if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "收藏", tint = if (isFav) LikeRed else off, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = vm.player::toggleShuffle, Modifier.size(34.dp)) {
                            Icon(Icons.Rounded.Shuffle, "随机", tint = if (state.shuffle) on else off, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = vm.player::cycleRepeat, Modifier.size(34.dp)) {
                            Icon(
                                if (state.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "循环",
                                tint = if (state.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) on else off, modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Text(
                        "${formatDuration(shownMs / 1000)} / ${formatDuration(state.durationMs / 1000)}",
                        style = TextStyle(fontSize = 14.sp, shadow = TextShadow), maxLines = 1,
                        color = if (seekPreview != null) Color.White else Color.White.copy(alpha = 0.6f),
                    )
                }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = topInset + 6.dp))
            }
        }
    }
}

private val NpTween = tween<Float>(300)


/** Height the progress bar (14 dp) plus its gap takes above the camera band. */
private val LeverSpace = 22.dp

/** Fills only the camera cut-out area (inside the progress track): frosted album art or black (Settings › 外屏镜头区域). */
@Composable
private fun CameraHoleBackground(art: String?, mode: CameraStrip, ring: Rect?) {
    if (ring == null) return
    val hole = remember(ring) { stadiumPath(ring) }
    if (mode == CameraStrip.BLACK) {
        Box(Modifier.fillMaxSize().drawBehind { drawPath(hole, Color.Black) })
        return
    }
    Box(Modifier.fillMaxSize().drawWithContent { clipPath(hole) { this@drawWithContent.drawContent() } }) {
        AsyncImage(art, null, Modifier.fillMaxSize().blur(40.dp), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.06f)))
    }
}

/**
 * Synced lyrics show five lines (two before, the current one large, two after) that slide up as
 * the song moves on; tapping a line seeks there. Plain lyrics fall back to a scrollable list.
 */
@Composable
internal fun LyricsPane(lines: List<LyricLine>?, synced: Boolean, loading: Boolean, positionMs: Long, immersive: Boolean, onSeek: (Long) -> Unit, modifier: Modifier, lineGap: androidx.compose.ui.unit.Dp = 6.dp) {
    if (lines == null) {
        Box(modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                if (loading) "正在获取歌词…" else "暂无歌词", color = Color.White.copy(alpha = 0.45f),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, shadow = TextShadow),
            )
        }
        return
    }
    if (synced) {
        val cur = lines.indexOfLast { it.timeMs <= positionMs + 250 }
        FiveLineLyrics(lines, cur, immersive, onSeek, modifier, lineGap)
        return
    }
    // Plain (unsynced) lyrics: just a scrollable list.
    val list = rememberLazyListState()
    BoxWithConstraints(modifier) {
        val pad = maxHeight * 0.3f
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(top = pad, bottom = maxHeight - pad),
            modifier = Modifier.fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(0f to Color.Transparent, 0.12f to Color.Black, 0.82f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            items(lines) { line ->
                Text(
                    line.text.ifBlank { "♪" }, color = Color.White.copy(alpha = 0.85f),
                    style = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 1.22.em, shadow = TextShadow),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp),
                )
            }
        }
    }
}


/**
 * Apple Music-style lyric window: two lines before, the current one, two after, all the same large
 * bold size (larger in immersive mode). The current line is white; the others are dimmed and softly
 * blurred. Drag vertically to browse, tap a line to jump there.
 */
@Composable
internal fun FiveLineLyrics(lines: List<LyricLine>, cur: Int, immersive: Boolean, onSeek: (Long) -> Unit, modifier: Modifier, lineGap: androidx.compose.ui.unit.Dp = 6.dp) {
    val fontSize by animateFloatAsState(if (immersive) 36f else 30f, label = "lyricFont")
    val style = TextStyle(fontSize = fontSize.sp, fontWeight = FontWeight.Bold, lineHeight = 1.2.em, shadow = TextShadow)
    val list = rememberLazyListState()
    // Native scrolling (with fling). While the user scrolls, and for 3 s after, the list stays put and
    // lines are unblurred; then it glides back so the current line sits at the anchor.
    var autoScrolling by remember { mutableStateOf(false) }
    var userScrolledAt by remember { mutableLongStateOf(0L) }
    val userScrolling = list.isScrollInProgress && !autoScrolling
    LaunchedEffect(userScrolling) { if (userScrolling) userScrolledAt = SystemClock.uptimeMillis() else if (userScrolledAt > 0) userScrolledAt = SystemClock.uptimeMillis() }
    val browsing = userScrolling || SystemClock.uptimeMillis() - userScrolledAt < 3_000
    LaunchedEffect(cur, userScrolledAt, immersive) {
        val wait = 3_000 - (SystemClock.uptimeMillis() - userScrolledAt)
        if (wait > 0) delay(wait)
        val target = cur.coerceAtLeast(0)
        autoScrolling = true
        try {
            val info = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
            if (info != null) list.animateScrollBy(info.offset.toFloat(), androidx.compose.animation.core.tween(500, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            else list.scrollToItem(target)
        } finally { autoScrolling = false }
        userScrolledAt = 0L
    }

    BoxWithConstraints(modifier) {
        val anchor = maxHeight * 0.38f   // ~2 lines above the current one
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(top = anchor, bottom = maxHeight - anchor),
            verticalArrangement = Arrangement.spacedBy(lineGap),
            modifier = Modifier.fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.1f to Color.Black, 0.86f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                },
        ) {
            itemsIndexed(lines) { i, line ->
                val active = i == cur
                val color by animateColorAsState(
                    if (active) Color.White else Color.White.copy(alpha = if (browsing) 0.5f else 0.32f), label = "lyricColor",
                )
                Text(
                    line.text.ifBlank { "♪" }, color = color, style = style,
                    modifier = Modifier.fillMaxWidth()
                        // Only the current line is sharp; the rest sit under a blur that grows with distance.
                        .then(if (active || browsing) Modifier else Modifier.blur((2f + 1.3f * minOf(if (cur < 0) 2 else kotlin.math.abs(i - cur), 3)).dp, BlurredEdgeTreatment.Unbounded))
                        .clip(RoundedCornerShape(12.dp))
                        .then(if (line.timeMs >= 0) Modifier.clickable { userScrolledAt = 0L; onSeek(line.timeMs) } else Modifier)
                        .padding(horizontal = 16.dp, vertical = lineGap),
                )
            }
        }
    }
}

/** Stadium-shaped track around the camera row; progress starts at its left end and runs clockwise. */
private fun stadiumPath(r: Rect): Path = Path().apply {
    val rad = r.height / 2
    moveTo(r.left, r.top + rad)
    arcTo(Rect(r.left, r.top, r.left + 2 * rad, r.bottom), 180f, 90f, false)
    lineTo(r.right - rad, r.top)
    arcTo(Rect(r.right - 2 * rad, r.top, r.right, r.bottom), 270f, 180f, false)
    lineTo(r.left + rad, r.bottom)
    arcTo(Rect(r.left, r.top, r.left + 2 * rad, r.bottom), 90f, 90f, false)
}

/** A thin draggable progress line (14 dp tall touch strip) for the small letterboxed windows. */
@Composable
private fun SlimSeekBar(state: PlayerState, onSeek: (Long) -> Unit, enabled: Boolean = true, onPreview: (Float?) -> Unit = {}) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val value = (drag ?: state.progress).coerceIn(0f, 1f)
    val duration = state.durationMs
    Canvas(
        Modifier.fillMaxWidth().height(14.dp).pointerInput(duration, enabled) {
            if (duration <= 0 || !enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                drag = (down.position.x / size.width).coerceIn(0f, 1f); onPreview(drag)
                while (true) {
                    val e = awaitPointerEvent()
                    val c = e.changes.firstOrNull { it.id == down.id } ?: break
                    if (!c.pressed) break
                    drag = (c.position.x / size.width).coerceIn(0f, 1f); onPreview(drag)
                    c.consume()
                }
                drag?.let { onSeek((it * duration).toLong()) }
                drag = null; onPreview(null)
            }
        }
    ) {
        val h = (if (drag != null) 5.dp else 3.dp).toPx()
        val top = (size.height - h) / 2
        val r = androidx.compose.ui.geometry.CornerRadius(h / 2)
        drawRoundRect(Color.White.copy(alpha = 0.25f), topLeft = Offset(0f, top), size = Size(size.width, h), cornerRadius = r)
        drawRoundRect(Color.White, topLeft = Offset(0f, top), size = Size(size.width * value, h), cornerRadius = r)
    }
}

/** Progress drawn around the camera row: display only (no knob, no touch); the jog lever scrubs. */
@Composable
private fun CameraProgressRing(ring: Rect, state: PlayerState, external: Float?, glow: Float = 0f) {
    val density = LocalDensity.current
    val pad = with(density) { 4.dp.toPx() }
    val local = Rect(pad, pad, pad + ring.width, pad + ring.height)
    val path = remember(ring) { stadiumPath(local) }
    val measure = remember(path) { PathMeasure().apply { setPath(path, false) } }
    val progress = (external ?: state.progress).coerceIn(0f, 1f)
    Canvas(
        Modifier
            .offset { IntOffset((ring.left - pad).toInt(), (ring.top - pad).toInt()) }
            .size(with(density) { (ring.width + 2 * pad).toDp() }, with(density) { (ring.height + 2 * pad).toDp() })
    ) {
        // The track brightens a little as the progress bar merges into it.
        val stroke = 4.dp.toPx()
        drawPath(path, Color.White.copy(alpha = 0.18f + 0.1f * glow), style = Stroke(stroke, cap = StrokeCap.Round))
        if (progress > 0f) {
            val seg = Path()
            measure.getSegment(0f, measure.length * progress, seg, true)
            drawPath(seg, Color.White.copy(alpha = 0.75f + 0.25f * glow), style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}


/**
 * A playlist / album title too long for one line is split in two at the comma or bar closest to its
 * middle, with that separator dropped ("夜晚｜安静的歌" → "夜晚\n安静的歌"). Titles that fit, or have no
 * such separator, are left as they are.
 */
private fun breakTitle(text: String, style: TextStyle, maxWidthPx: Int, measurer: androidx.compose.ui.text.TextMeasurer): String {
    if (maxWidthPx <= 0 || text.isBlank()) return text
    val fits = measurer.measure(text, style, softWrap = false, maxLines = 1).size.width <= maxWidthPx
    if (fits) return text
    val seps = text.indices.filter { text[it] in TitleSeparators && it > 0 && it < text.length - 1 }
    if (seps.isEmpty()) return text
    val mid = seps.minBy { kotlin.math.abs(it - text.length / 2) }
    return text.substring(0, mid).trimEnd() + "\n" + text.substring(mid + 1).trimStart()
}

private const val TitleSeparators = ",，|｜丨"

internal val TextShadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), blurRadius = 6f)

// ---------------- Queue ----------------

@Composable
private fun QueuePage(vm: MainViewModel) {
    val state by vm.player.state.collectAsState()
    val list = rememberLazyListState()
    LaunchedEffect(state.currentIndex) { if (state.currentIndex >= 0) list.animateScrollToItem(state.currentIndex) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        CoverTopBar("播放队列", actions = {
            IconButton(onClick = vm.player::toggleShuffle) {
                Icon(Icons.Rounded.Shuffle, "随机", tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = vm.player::cycleRepeat) {
                Icon(
                    if (state.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "循环",
                    tint = if (state.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        })
        if (state.queue.isEmpty()) MessageBox("队列是空的")
        // Long-press a row and drag to reorder. The list is mirrored locally while dragging so rows can
        // swap live; the player is told once, on release.
        val rows = remember { mutableStateListOf<Pair<String, Song>>() }
        var dragKey by remember { mutableStateOf<String?>(null) }
        var dragFrom by remember { mutableIntStateOf(-1) }
        var dragDy by remember { mutableFloatStateOf(0f) }
        val haptics = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        LaunchedEffect(state.queue) {
            if (dragKey == null) { rows.clear(); rows.addAll(state.queue.mapIndexed { i, s -> "$i-${s.id}" to s }) }
        }
        val currentKey = state.queue.getOrNull(state.currentIndex)?.let { "${state.currentIndex}-${it.id}" }
        LazyColumn(
            state = list, contentPadding = PaddingValues(bottom = CoverScreenSpec.LIST_BOTTOM_PADDING),
            modifier = Modifier.pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { pos ->
                        val hit = list.layoutInfo.visibleItemsInfo.firstOrNull { pos.y.toInt() in it.offset..(it.offset + it.size) } ?: return@detectDragGesturesAfterLongPress
                        dragKey = hit.key as? String; dragFrom = hit.index; dragDy = 0f
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = {
                        val to = rows.indexOfFirst { it.first == dragKey }
                        if (dragFrom >= 0 && to >= 0 && to != dragFrom) vm.player.move(dragFrom, to)
                        dragKey = null; dragDy = 0f
                    },
                    onDragCancel = {
                        dragKey = null; dragDy = 0f
                        rows.clear(); rows.addAll(state.queue.mapIndexed { i, s -> "$i-${s.id}" to s })
                    },
                ) { change, amount ->
                    val key = dragKey ?: return@detectDragGesturesAfterLongPress
                    change.consume()
                    dragDy += amount.y
                    val info = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return@detectDragGesturesAfterLongPress
                    val center = info.offset + dragDy + info.size / 2f
                    val target = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key != key && center.toInt() in it.offset..(it.offset + it.size) }
                    if (target != null) {
                        rows.add(target.index, rows.removeAt(info.index))
                        dragDy -= (target.offset - info.offset)
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                    // Near the top or bottom edge, scroll so the row can travel the whole queue.
                    val edge = 56.dp.toPx()
                    val vp = list.layoutInfo
                    when {
                        center < vp.viewportStartOffset + edge -> scope.launch { list.scrollBy(-24f) }
                        center > vp.viewportEndOffset - edge -> scope.launch { list.scrollBy(24f) }
                    }
                }
            },
        ) {
            itemsIndexed(rows, key = { _, r -> r.first }) { i, (key, song) ->
                val dragging = key == dragKey
                QueueRow(
                    song, isCurrent = key == currentKey,
                    onClick = { if (dragKey == null) vm.player.skipTo(i) },
                    modifier = if (dragging) Modifier.zIndex(1f).graphicsLayer {
                        translationY = dragDy; scaleX = 1.03f; scaleY = 1.03f; shadowElevation = 12.dp.toPx()
                    } else Modifier.animateItem(),
                )
            }
        }
    }
}

/** Queue row on the cover: bigger text and a smaller cover than the regular song rows. */
@Composable
private fun QueueRow(song: Song, isCurrent: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Artwork(song.thumbnail, Modifier.size(36.dp))
            if (isCurrent) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.DragHandle, "长按拖动排序", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}

// ---------------- Discover (with search) ----------------

/** The FlexWindow's own corner radius, so the search / quick buttons follow the screen's rounding. */
@Composable
private fun rememberScreenCornerRadius(): androidx.compose.ui.unit.Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view, density) {
        val px = if (android.os.Build.VERSION.SDK_INT >= 31)
            view.rootWindowInsets?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0 else 0
        if (px > 0) with(density) { px.toDp() } else 28.dp
    }
}

@Composable
private fun DiscoverPage(vm: MainViewModel, onPlayed: () -> Unit, onSettings: () -> Unit) {
    val s by vm.search.collectAsState()
    val recentSearches by vm.recentSearches.collectAsState()
    val filters by vm.searchFilters.collectAsState()
    val home by vm.home.collectAsState()
    val history by vm.history.collectAsState()
    val current by vm.player.state.collectAsState()
    val favIds by vm.favoriteIds.collectAsState()
    val signedIn by vm.signedIn.collectAsState()
    val ytLiked by vm.ytLiked.collectAsState()
    val ytPlaylists by vm.ytPlaylists.collectAsState()
    val ytHome by vm.ytHome.collectAsState()
    val ytHistory by vm.ytHistory.collectAsState()
    val favorites = (ytLiked as? Load.Ready)?.data.orEmpty()
    // Signed in: the account's own play history; otherwise what was played on this phone.
    val recent = if (signedIn) (ytHistory as? Load.Ready)?.data.orEmpty() else history
    val play: (List<Song>, Int) -> Unit = { l, i -> vm.playList(l, i); onPlayed() }
    val openLiked = { if (signedIn) vm.showSongs("喜欢的音乐", favorites) else vm.openLogin() }
    val openRecent = { if (recent.isNotEmpty()) vm.showSongs("最近播放", recent, localHistory = !signedIn) }

    var fieldFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    // The keyboard covers most of the 4" FlexWindow, so dismiss it as soon as a search is submitted.
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val submit: (String) -> Unit = { q -> keyboard?.hide(); focus.clearFocus(); vm.submitSearch(q) }
    val searching = fieldFocused || s.query.isNotEmpty() || s.result !is Load.Idle
    val exitSearch = { vm.clearSearch(); focus.clearFocus() }
    if (searching) BackHandler { exitSearch() }

    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Once the search / 喜欢 / 最近 row has scrolled away, the same three show as icons in the title bar.
    val collapsed by remember {
        derivedStateOf {
            list.firstVisibleItemIndex > 0 ||
                list.firstVisibleItemScrollOffset > (list.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: Int.MAX_VALUE) / 2
        }
    }
    val radius = rememberScreenCornerRadius()
    val bg = MaterialTheme.colorScheme.background
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = list, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topInset + 56.dp, bottom = CoverScreenSpec.LIST_BOTTOM_PADDING),
        ) {
            item(key = "quick") {
                Row(Modifier.fillMaxWidth()
                    .graphicsLayer {
                        // As the row scrolls under the title it shrinks and fades, as if folding into the bar's icons.
                        val f = if (list.firstVisibleItemIndex == 0 && size.height > 0f) (list.firstVisibleItemScrollOffset / size.height).coerceIn(0f, 1f) else 0f
                        alpha = 1f - f
                        scaleX = 1f - 0.12f * f; scaleY = scaleX
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = s.query, onValueChange = vm::onQueryChange,
                        modifier = Modifier.weight(1f).focusRequester(focusRequester).onFocusChanged { fieldFocused = it.isFocused },
                        singleLine = true,
                        placeholder = { Text("搜索", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = if (searching) ({ IconButton(onClick = exitSearch) { Icon(Icons.Rounded.Close, "取消搜索") } }) else null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit(s.query) }),
                        shape = RoundedCornerShape(radius),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                    // While searching the field takes the whole row.
                    androidx.compose.animation.AnimatedVisibility(!searching, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuickButton(Icons.Rounded.Favorite, "喜欢", radius, tint = LikeRed, onClick = openLiked)
                            QuickButton(Icons.Rounded.History, "最近播放", radius, enabled = recent.isNotEmpty(), onClick = openRecent)
                        }
                    }
                }
            }
            if (searching) {
                item { SegmentedFilter(filters, s.filter, vm::setFilter, Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
                if (s.suggestions.isNotEmpty()) {
                    items(s.suggestions) { sug ->
                        Text(sug, Modifier.fillMaxWidth().clickable { submit(sug) }.padding(horizontal = 16.dp, vertical = 12.dp))
                    }
                } else when (val r = s.result) {
                    Load.Idle -> if (recentSearches.isNotEmpty()) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("最近搜索", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                androidx.compose.material3.TextButton(onClick = vm::clearRecentSearches) { Text("清空") }
                            }
                        }
                        items(recentSearches) { q ->
                            Row(Modifier.fillMaxWidth().clickable { submit(q) }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                Text(q, Modifier.padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Load.Loading -> item { LoadingBox() }
                    is Load.Failed -> item { MessageBox(r.message) }
                    is Load.Ready -> {
                        if (r.data.songs.isEmpty() && r.data.collections.isEmpty()) item { MessageBox("没有找到结果") }
                        itemsIndexed(r.data.songs, key = { i, x -> "s$i${x.id}" }) { _, song ->
                            SongRow(
                                song,
                                SongActions(onPlay = { vm.playRadio(song); onPlayed() }, onPlayNext = { vm.player.playNext(song) }, onEnqueue = { vm.player.enqueue(song) }, onToggleFavorite = { vm.toggleFavorite(song) }),
                                isCurrent = current.current?.id == song.id, isFavorite = song.id in favIds, compact = true,
                            )
                        }
                        items(r.data.collections, key = { it.url }) { c ->
                            Row(Modifier.fillMaxWidth().clickable { vm.openCollection(c) }.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Artwork(c.thumbnail, Modifier.size(52.dp))
                                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                    Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                    Text(c.subtitle, maxLines = 1, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                (ytPlaylists as? Load.Ready)?.data?.takeIf { it.isNotEmpty() }?.let { lists ->
                    item { CoverSectionTitle("我的歌单") }
                    item { CollectionCards(lists, vm::openCollection, cardSize = 92.dp, horizontalPadding = 12.dp) }
                }
                (ytHome as? Load.Ready)?.data?.forEach { shelf ->
                    item(key = "cs-${shelf.title}") {
                        CoverSectionTitle(shelf.title)
                        ShelfRow(shelf, onSong = play, onCollection = vm::openCollection, cardSize = 92.dp, horizontalPadding = 12.dp)
                    }
                }
                val seed = home.radioSeed
                val load = home.radio
                if (seed != null) item { CoverSectionTitle("因为你听了「${seed.title}」") }
                when (load) {
                    is Load.Loading -> item { LoadingBox() }
                    is Load.Failed -> item { MessageBox(load.message) }
                    is Load.Ready -> itemsIndexed(load.data, key = { i, x -> "d$i${x.id}" }) { i, song ->
                        SongRow(
                            song,
                            SongActions(onPlay = { play(load.data, i) }, onPlayNext = { vm.player.playNext(song) }, onEnqueue = { vm.player.enqueue(song) }, onToggleFavorite = { vm.toggleFavorite(song) }),
                            isCurrent = current.current?.id == song.id, isFavorite = song.id in favIds, compact = true,
                        )
                    }
                    Load.Idle -> if ((ytHome as? Load.Ready)?.data.isNullOrEmpty()) item { MessageBox("搜索一首喜欢的歌开始吧") }
                }
            }
        }

        // Title bar floats over the list; content fades out underneath it. At the very top there's nothing
        // underneath, so the fade stays off (it would cover the search row) and comes in as you scroll.
        val scrimTarget by remember {
            derivedStateOf { if (list.firstVisibleItemIndex > 0) 1f else (list.firstVisibleItemScrollOffset / 60f).coerceIn(0f, 1f) }
        }
        val scrim by animateFloatAsState(scrimTarget, tween(150), label = "discoverScrim")
        Column(
            Modifier.fillMaxWidth().drawBehind {
                if (scrim > 0f) drawRect(Brush.verticalGradient(0f to bg.copy(alpha = scrim), 0.72f to bg.copy(alpha = 0.92f * scrim), 1f to bg.copy(alpha = 0f)))
            }.padding(top = topInset, bottom = 14.dp)
        ) {
            CoverTopBar("发现", actions = {
                // When the row reaches the title, the gear rolls up and out and the three icons roll up
                // in from below; scrolling back reverses it (icons roll down, gear comes back from above).
                androidx.compose.animation.AnimatedContent(
                    collapsed && !searching,
                    transitionSpec = {
                        val up = targetState
                        val spec = tween<IntOffset>(320, easing = SystemEasing)
                        (slideInVertically(spec) { if (up) it else -it } + fadeIn(tween(220)))
                            .togetherWith(slideOutVertically(spec) { if (up) -it else it } + fadeOut(tween(160)))
                            .using(androidx.compose.animation.SizeTransform(clip = true))
                    },
                    contentAlignment = Alignment.CenterEnd, label = "discoverActions",
                ) { icons ->
                    // Compact buttons (36 dp, 22 dp glyphs) so the title never gets squeezed in narrow windows.
                    if (icons) Row {
                        val b = Modifier.size(36.dp); val g = Modifier.size(22.dp)
                        IconButton(onClick = {
                            scope.launch { list.animateScrollToItem(0); runCatching { focusRequester.requestFocus() } }
                        }, b) { Icon(Icons.Rounded.Search, "搜索", g) }
                        IconButton(onClick = openLiked, b) { Icon(Icons.Rounded.Favorite, "喜欢", g, tint = LikeRed) }
                        IconButton(onClick = openRecent, b, enabled = recent.isNotEmpty()) { Icon(Icons.Rounded.History, "最近播放", g) }
                    } else IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "设置") }
                }
            })
        }
    }
}

@Composable
private fun QuickButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, radius: androidx.compose.ui.unit.Dp, enabled: Boolean = true, tint: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(radius)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (enabled) Modifier.pressClick(scaleTo = 0.92f, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint.copy(alpha = if (enabled) 1f else 0.35f))
    }
}

@Composable
private fun CoverSectionTitle(text: String) =
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 8.dp))

// ---------------- Album / playlist ----------------

@Composable
private fun CoverCollection(vm: MainViewModel, load: Load<com.youflip.music.data.MusicRepository.CollectionDetail>, onPlayed: () -> Unit) {
    val current by vm.player.state.collectAsState()
    run {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.statusBars)) {
        when (load) {
            is Load.Ready -> {
                val d = load.data
                val clearable by vm.collectionIsLocalHistory.collectAsState()
                CoverTopBar(d.collection.title, onClose = vm::closeCollection, wrap = true, actions = {
                    if (clearable) androidx.compose.material3.TextButton(onClick = vm::clearLocalHistory) { Text("清空") }
                })
                LazyColumn(contentPadding = PaddingValues(bottom = CoverScreenSpec.LIST_BOTTOM_PADDING)) {
                    item {
                        // Header: artwork on the left; subtitle and the two play buttons stacked to its right.
                        // In the narrow portrait window that column would squeeze the buttons, so there the
                        // buttons move under the artwork and share the full width.
                        val hasArt = d.collection.thumbnail != null
                        val playAll: @Composable (Modifier) -> Unit = { m ->
                            Button(onClick = { vm.playList(d.songs, 0); onPlayed() }, enabled = d.songs.isNotEmpty(), modifier = m,
                                contentPadding = PaddingValues(horizontal = 12.dp)) {
                                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("播放全部", maxLines = 1, softWrap = false)
                            }
                        }
                        val shuffleAll: @Composable (Modifier) -> Unit = { m ->
                            FilledTonalButton(onClick = { vm.playList(d.songs.shuffled(), 0); onPlayed() }, enabled = d.songs.isNotEmpty(), modifier = m,
                                contentPadding = PaddingValues(horizontal = 12.dp)) {
                                Icon(Icons.Rounded.Shuffle, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("随机播放", maxLines = 1, softWrap = false)
                            }
                        }
                        val subtitle: @Composable () -> Unit = {
                            if (d.collection.subtitle.isNotBlank()) Text(d.collection.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                        }
                        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                            val full = maxWidth
                            val side = full - (if (hasArt) 132.dp else 0.dp)
                            if (side >= 150.dp) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (hasArt) {
                                        Artwork(com.youflip.music.data.Thumbs.hd(d.collection.thumbnail), Modifier.size(120.dp), RoundedCornerShape(12.dp))
                                        Spacer(Modifier.width(12.dp))
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        subtitle()
                                        playAll(Modifier.fillMaxWidth())
                                        shuffleAll(Modifier.fillMaxWidth())
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (hasArt) {
                                            Artwork(com.youflip.music.data.Thumbs.hd(d.collection.thumbnail), Modifier.size(96.dp), RoundedCornerShape(12.dp))
                                            Spacer(Modifier.width(12.dp))
                                        }
                                        Box(Modifier.weight(1f)) { subtitle() }
                                    }
                                    // Side by side when each button still fits its icon and label, else one per row.
                                    if (full >= 240.dp) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        playAll(Modifier.weight(1f)); shuffleAll(Modifier.weight(1f))
                                    } else {
                                        playAll(Modifier.fillMaxWidth()); shuffleAll(Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        }
                    }
                    itemsIndexed(d.songs, key = { i, s -> "c$i${s.id}" }) { i, song ->
                        SongRow(song, SongActions(onPlay = { vm.playList(d.songs, i); onPlayed() }, onPlayNext = { vm.player.playNext(song) }, onEnqueue = { vm.player.enqueue(song) }, onToggleFavorite = { vm.toggleFavorite(song) }),
                            isCurrent = current.current?.id == song.id, compact = true)
                    }
                }
            }
            is Load.Failed -> { CoverTopBar("出错了", onClose = vm::closeCollection); MessageBox(load.message) }
            else -> { CoverTopBar("加载中", onClose = vm::closeCollection); LoadingBox() }
        }
    }
    }
}

/** Apple-style segmented control: a gray capsule track with the selected segment raised. */
@Composable
private fun SegmentedFilter(options: List<com.youflip.music.data.SearchFilter>, selected: com.youflip.music.data.SearchFilter, onSelect: (com.youflip.music.data.SearchFilter) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(2.dp)) {
        options.forEach { f ->
            val on = f == selected
            val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.surfaceContainerLowest.takeIf { MaterialTheme.colorScheme.background.luminance() > 0.5f } ?: MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent, label = "seg")
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(bg).clickable { onSelect(f) },
                contentAlignment = Alignment.Center,
            ) { Text(f.label, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}
