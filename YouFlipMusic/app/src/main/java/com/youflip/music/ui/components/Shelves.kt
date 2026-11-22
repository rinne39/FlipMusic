package com.youflip.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.youflip.music.data.MusicCollection
import com.youflip.music.data.Shelf
import com.youflip.music.data.Song

@Composable
fun MediaCard(title: String, subtitle: String, thumbnail: String?, size: Dp, onClick: () -> Unit) {
    Column(Modifier.width(size).pressClick(onClick = onClick)) {
        Artwork(thumbnail, Modifier.size(size), RoundedCornerShape(10.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp),
            style = if (size < 110.dp) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium)
        if (subtitle.isNotBlank()) {
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Horizontal row of a shelf's songs and albums/playlists. */
@Composable
fun ShelfRow(
    shelf: Shelf,
    onSong: (List<Song>, Int) -> Unit,
    onCollection: (MusicCollection) -> Unit,
    cardSize: Dp = 140.dp,
    horizontalPadding: Dp = 16.dp,
) {
    LazyRow(contentPadding = PaddingValues(horizontal = horizontalPadding), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(shelf.songs.size) { i ->
            val s = shelf.songs[i]
            MediaCard(s.title, s.artist, s.thumbnail, cardSize) { onSong(shelf.songs, i) }
        }
        items(shelf.collections, key = { it.url }) { c ->
            MediaCard(c.title, c.subtitle, c.thumbnail, cardSize) { onCollection(c) }
        }
    }
}

@Composable
fun CollectionCards(list: List<MusicCollection>, onCollection: (MusicCollection) -> Unit, cardSize: Dp, horizontalPadding: Dp) {
    LazyRow(contentPadding = PaddingValues(horizontal = horizontalPadding), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(list, key = { it.url }) { c -> MediaCard(c.title, c.subtitle, c.thumbnail, cardSize) { onCollection(c) } }
    }
}
