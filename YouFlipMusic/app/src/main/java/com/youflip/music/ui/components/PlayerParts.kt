package com.youflip.music.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.youflip.music.data.formatDuration
import com.youflip.music.playback.PlayerConnection
import com.youflip.music.playback.PlayerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeekBar(state: PlayerState, onSeek: (Long) -> Unit, modifier: Modifier = Modifier, showTimes: Boolean = true) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val value = dragging ?: state.progress
    Column(modifier.fillMaxWidth()) {
        Slider(
            value = value,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * state.durationMs).toLong()) }
                dragging = null
            },
            enabled = state.durationMs > 0,
            // Plain round thumb on a thin line instead of Material's tall bar thumb and thick track.
            thumb = {
                val size by androidx.compose.animation.core.animateDpAsState(if (dragging != null) 20.dp else 14.dp, label = "thumb")
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(size).clip(CircleShape).background(Color.White))
                }
            },
            track = {
                androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                    val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                    drawRoundRect(Color.White.copy(alpha = 0.25f), cornerRadius = r)
                    drawRoundRect(Color.White, size = size.copy(width = size.width * value.coerceIn(0f, 1f)), cornerRadius = r)
                }
            },
        )
        if (showTimes) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration((value * state.durationMs / 1000).toLong()), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                Text(formatDuration(state.durationMs / 1000), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun PlayPauseButton(state: PlayerState, onClick: () -> Unit, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            if (state.isBuffering && !state.isPlaying) {
                CircularProgressIndicator(Modifier.size(size * 0.45f), color = Color.Black, strokeWidth = 3.dp)
            } else {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (state.isPlaying) "暂停" else "播放",
                    tint = Color.Black,
                    modifier = Modifier.size(size * 0.55f),
                )
            }
        }
    }
}

@Composable
fun TransportRow(
    state: PlayerState,
    player: PlayerConnection,
    modifier: Modifier = Modifier,
    mainSize: Dp = 72.dp,
    sideSize: Dp = 44.dp,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = player::toggleShuffle) {
            Icon(Icons.Rounded.Shuffle, "随机", tint = if (state.shuffle) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f))
        }
        IconButton(onClick = player::previous, modifier = Modifier.size(sideSize + 8.dp)) {
            Icon(Icons.Rounded.SkipPrevious, "上一首", tint = Color.White, modifier = Modifier.size(sideSize))
        }
        PlayPauseButton(state, player::togglePlay, mainSize)
        IconButton(onClick = player::next, modifier = Modifier.size(sideSize + 8.dp)) {
            Icon(Icons.Rounded.SkipNext, "下一首", tint = Color.White, modifier = Modifier.size(sideSize))
        }
        IconButton(onClick = player::cycleRepeat) {
            Icon(
                if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "循环",
                tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

/** Blurred album art backdrop with a dark scrim, shared by the full player and cover now-playing page. */
@Composable
fun ArtBackdrop(url: String?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (url != null) {
            coil3.compose.AsyncImage(
                url, null,
                Modifier.fillMaxSize().blur(48.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alpha = 0.55f,
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.85f))
                )
            )
        )
    }
}

/**
 * One line of song info. With [scroll] (Settings › 显示不全时自动滚动) a line that doesn't fit glides to its
 * end and back (pausing 1.5 s at each end); otherwise it's cut with an ellipsis. The box keeps the
 * width the caller allows, so the surrounding layout is the same in both modes.
 */
@Composable
fun ScrollingText(
    text: String, color: Color, style: androidx.compose.ui.text.TextStyle, scroll: Boolean, modifier: Modifier = Modifier,
) {
    if (!scroll) {
        Text(text, color = color, style = style, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = modifier)
        return
    }
    var boxW by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var textW by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val shift = remember(text) { androidx.compose.animation.core.Animatable(0f) }
    val speed = with(androidx.compose.ui.platform.LocalDensity.current) { 30.dp.toPx() }   // px per second
    androidx.compose.runtime.LaunchedEffect(text, boxW, textW) {
        shift.snapTo(0f)
        val over = textW - boxW
        if (boxW <= 0 || over <= 0) return@LaunchedEffect
        val ms = (over / speed * 1000).toInt().coerceAtLeast(600)
        val ease = androidx.compose.animation.core.LinearEasing
        while (true) {
            kotlinx.coroutines.delay(1500); shift.animateTo(-over.toFloat(), androidx.compose.animation.core.tween(ms, easing = ease))
            kotlinx.coroutines.delay(1500); shift.animateTo(0f, androidx.compose.animation.core.tween(ms, easing = ease))
        }
    }
    Box(modifier.then(Modifier.clipToBounds()).onSizeChanged { boxW = it.width }) {
        Text(
            text, color = color, style = style, maxLines = 1, softWrap = false,
            modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true).onSizeChanged { textW = it.width }
                .graphicsLayer { translationX = shift.value },
        )
    }
}
