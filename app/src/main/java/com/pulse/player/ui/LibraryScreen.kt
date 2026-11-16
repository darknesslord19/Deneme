package com.pulse.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pulse.player.data.Album
import com.pulse.player.data.Artist
import com.pulse.player.data.LibraryViewModel
import com.pulse.player.data.Song
import com.pulse.player.data.VideoItem
import com.pulse.player.player.MusicController
import com.pulse.player.ui.theme.Bg
import com.pulse.player.ui.theme.Card
import com.pulse.player.ui.theme.Cyan
import com.pulse.player.ui.theme.Neon
import com.pulse.player.ui.theme.Pink
import com.pulse.player.ui.theme.TextDim

private val tabs = listOf("Şarkılar", "Albümler", "Sanatçılar", "Videolar", "Favoriler")

@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onOpenVideo: (List<VideoItem>, Int) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val videos by vm.videos.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var detailTitle by remember { mutableStateOf<String?>(null) }
    var detailSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var detailArt by remember { mutableStateOf<Any?>(null) }

    BackHandler(enabled = detailTitle != null) { detailTitle = null }

    val q = query.trim().lowercase()
    val fSongs = remember(songs, q) {
        if (q.isEmpty()) songs else songs.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q)
        }
    }
    val fVideos = remember(videos, q) { if (q.isEmpty()) videos else videos.filter { it.title.lowercase().contains(q) } }

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(brush = Brush.linearGradient(listOf(Neon, Pink, Cyan)), fontWeight = FontWeight.Black)) { append("Pulse") }
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Light)) { append(" Player") }
                },
                fontSize = 30.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { vm.load(force = true) }) { Icon(Icons.Rounded.Refresh, "Yenile") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Şarkı, sanatçı, albüm veya video ara") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, null) } },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Neon, unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                focusedContainerColor = Card, unfocusedContainerColor = Card,
            ),
        )
        ScrollableTabRow(
            selectedTabIndex = tab,
            edgePadding = 12.dp,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            divider = {},
        ) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i; detailTitle = null }, text = {
                    Text(t, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal, color = if (tab == i) Color.White else TextDim)
                })
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Neon, trackColor = Color.Transparent)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (detailTitle != null) {
                DetailList(detailTitle!!, detailArt, detailSongs, favs, vm::toggleFavorite) { detailTitle = null }
            } else when (tab) {
                0 -> SongList(fSongs, favs, vm::toggleFavorite, emptyText = if (q.isEmpty()) "Müzik bulunamadı" else "Sonuç yok")
                1 -> AlbumGrid(if (q.isEmpty()) albums else albums.filter { it.name.lowercase().contains(q) || it.artist.lowercase().contains(q) }) {
                    detailTitle = it.name; detailSongs = it.songs; detailArt = it.artUri
                }
                2 -> ArtistList(if (q.isEmpty()) artists else artists.filter { it.name.lowercase().contains(q) }) {
                    detailTitle = it.name; detailSongs = it.songs; detailArt = it.artUri
                }
                3 -> VideoGrid(fVideos, onOpenVideo)
                else -> SongList(songs.filter { it.id in favs }, favs, vm::toggleFavorite, emptyText = "Henüz favori yok", sub = "Şarkılardaki kalbe dokun")
            }
        }
        Box(Modifier.navigationBarsPadding()) { MiniPlayer(onOpen = onOpenPlayer) }
    }
}

@Composable
private fun PlayButtons(songs: List<Song>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = { MusicController.playQueue(songs, 0, shuffled = true) },
            colors = ButtonDefaults.buttonColors(containerColor = Neon),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Karıştır")
        }
        Spacer(Modifier.width(10.dp))
        OutlinedButton(onClick = { MusicController.playQueue(songs, 0) }, shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Tümünü Çal")
        }
        Spacer(Modifier.weight(1f))
        Text("${songs.size} şarkı", color = TextDim, fontSize = 13.sp)
    }
}

@Composable
fun SongList(
    songs: List<Song>, favs: Set<Long>, onFav: (Long) -> Unit,
    emptyText: String = "Müzik bulunamadı", sub: String = "",
    header: (@Composable () -> Unit)? = null,
) {
    val current by MusicController.current.collectAsStateWithLifecycle()
    val playing by MusicController.isPlaying.collectAsStateWithLifecycle()
    if (songs.isEmpty() && header == null) { EmptyState(emptyText, sub); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        if (header != null) item { header() }
        if (songs.isNotEmpty()) item { PlayButtons(songs) }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
            SongRow(s, current?.id == s.id, playing, s.id in favs, { onFav(s.id) }) {
                MusicController.playQueue(songs, i)
            }
        }
    }
}

@Composable
fun SongRow(s: Song, isCurrent: Boolean, playing: Boolean, fav: Boolean, onFav: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isCurrent) Neon.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            ArtImage(s.artUri, Modifier.size(50.dp))
            if (isCurrent) Box(Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                PlayingBars(Cyan, playing = playing)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                color = if (isCurrent) Cyan else Color.White, fontSize = 15.sp)
            Text("${s.artist} • ${s.album}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = TextDim, fontSize = 12.sp)
        }
        Text(formatTime(s.duration), color = TextDim, fontSize = 12.sp)
        IconButton(onClick = onFav) {
            Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (fav) Pink else TextDim)
        }
    }
}

@Composable
private fun DetailList(title: String, art: Any?, songs: List<Song>, favs: Set<Long>, onFav: (Long) -> Unit, onBack: () -> Unit) {
    SongList(songs, favs, onFav, header = {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) }
            ArtImage(art, Modifier.size(96.dp), corner = 18.dp, iconSize = 36.dp)
            Column(Modifier.padding(start = 16.dp)) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${songs.size} şarkı • ${formatTime(songs.sumOf { it.duration })}", color = TextDim)
            }
        }
    })
}

@Composable
private fun AlbumGrid(albums: List<Album>, onClick: (Album) -> Unit) {
    if (albums.isEmpty()) { EmptyState("Albüm yok"); return }
    LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items(albums, key = { it.id }) { a ->
            Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable { onClick(a) }) {
                ArtImage(a.artUri, Modifier.fillMaxWidth().aspectRatio(1f), corner = 16.dp, iconSize = 40.dp)
                Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
                Text("${a.artist} • ${a.songs.size}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun ArtistList(artists: List<Artist>, onClick: (Artist) -> Unit) {
    if (artists.isEmpty()) { EmptyState("Sanatçı yok"); return }
    LazyColumn(Modifier.fillMaxSize()) {
        items(artists, key = { it.name }) { a ->
            Row(Modifier.fillMaxWidth().clickable { onClick(a) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(54.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Neon, Pink))), contentAlignment = Alignment.Center) {
                    Text(a.name.take(1).uppercase(), fontWeight = FontWeight.Black, fontSize = 22.sp)
                    AsyncImage(a.artUri, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().clip(CircleShape))
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(a.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${a.songs.size} şarkı", color = TextDim, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun VideoGrid(videos: List<VideoItem>, onOpen: (List<VideoItem>, Int) -> Unit) {
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    val folders = remember(videos) { videos.map { it.folder }.distinct().sorted() }
    val shown = remember(videos, folder) { if (folder == null) videos else videos.filter { it.folder == folder } }
    if (videos.isEmpty()) { EmptyState("Video bulunamadı"); return }
    Column {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = Neon, selectedLabelColor = Color.White)
            FilterChip(folder == null, { folder = null }, label = { Text("Tümü (${videos.size})") }, colors = chipColors)
            folders.forEach { f -> FilterChip(folder == f, { folder = f }, label = { Text(f) }, colors = chipColors) }
        }
        LazyVerticalGrid(GridCells.Adaptive(170.dp), contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(shown.size, key = { shown[it].id }) { i ->
                val v = shown[i]
                Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable { onOpen(shown, i) }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Card)) {
                        AsyncImage(v.uri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))))
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(36.dp)
                            .clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).padding(4.dp))
                        Text(formatTime(v.duration), fontSize = 11.sp, style = TextStyle(fontWeight = FontWeight.Bold),
                            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                                .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 2.dp))
                        if (v.height >= 720) Text(if (v.height >= 2160) "4K" else if (v.height >= 1080) "FHD" else "HD",
                            fontSize = 10.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(5.dp))
                                .background(Brush.linearGradient(listOf(Neon, Pink))).padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                    Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
                    Text("${formatSize(v.size)} • ${v.folder}", color = TextDim, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(start = 2.dp))
                }
            }
        }
    }
}
