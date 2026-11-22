package com.youflip.music.ui.components

import com.youflip.music.ui.theme.LikeRed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.youflip.music.data.Song
import com.youflip.music.data.formatDuration

@Composable
fun Artwork(url: String?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    Box(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (url != null) {
            AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

data class SongActions(
    val onPlay: () -> Unit,
    val onPlayNext: (() -> Unit)? = null,
    val onEnqueue: (() -> Unit)? = null,
    val onToggleFavorite: (() -> Unit)? = null,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    actions: SongActions,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isFavorite: Boolean = false,
    compact: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    val artSize: Dp = if (compact) 44.dp else 52.dp
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(onClick = actions.onPlay, onLongClick = { menu = true })
            .padding(horizontal = if (compact) 10.dp else 16.dp, vertical = if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Box {
            Artwork(song.thumbnail, Modifier.size(artSize))
            if (isCurrent) {
                Box(Modifier.size(artSize).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, end = if (compact) 4.dp else 12.dp)) {
            Text(
                song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                listOf(song.artist, formatDuration(song.durationSec).takeIf { song.durationSec > 0 })
                    .filter { !it.isNullOrBlank() }.joinToString(" · "),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!compact && actions.onToggleFavorite != null) {
            IconButton(onClick = actions.onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "收藏",
                    tint = if (isFavorite) LikeRed else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            // Compact rows (cover screen) use a narrow ⋮ so the title keeps the width.
            IconButton(onClick = { menu = true }, modifier = if (compact) Modifier.size(width = 28.dp, height = 40.dp) else Modifier) {
                Icon(Icons.Rounded.MoreVert, "更多", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = if (compact) Modifier.size(20.dp) else Modifier)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                actions.onPlayNext?.let {
                    DropdownMenuItem(text = { Text("下一首播放") }, leadingIcon = { Icon(Icons.Rounded.QueueMusic, null) }, onClick = { menu = false; it() })
                }
                actions.onEnqueue?.let {
                    DropdownMenuItem(text = { Text("添加到队列") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) }, onClick = { menu = false; it() })
                }
                actions.onToggleFavorite?.let {
                    DropdownMenuItem(
                        text = { Text(if (isFavorite) "取消收藏" else "收藏") },
                        leadingIcon = { Icon(if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null) },
                        onClick = { menu = false; it() },
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun MessageBox(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Click with a ripple plus a small press-in scale, for cards, tabs and capsules. */
@Composable
fun Modifier.pressClick(scaleTo: Float = 0.95f, ripple: Boolean = true, onClick: () -> Unit): Modifier {
    val source = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) scaleTo else 1f,
        androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow), label = "press",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(source, if (ripple) androidx.compose.material3.ripple() else null, onClick = onClick)
}
