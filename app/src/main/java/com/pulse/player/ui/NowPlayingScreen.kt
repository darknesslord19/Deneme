package com.pulse.player.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.pulse.player.data.LibraryViewModel
import com.pulse.player.data.Prefs
import com.pulse.player.data.Song
import com.pulse.player.player.MusicController
import com.pulse.player.ui.theme.Bg
import com.pulse.player.ui.theme.Card
import com.pulse.player.ui.theme.Cyan
import com.pulse.player.ui.theme.Neon
import com.pulse.player.ui.theme.Pink
import com.pulse.player.ui.theme.TextDim

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(vm: LibraryViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val songOrNull by MusicController.current.collectAsStateWithLifecycle()
    val playing by MusicController.isPlaying.collectAsStateWithLifecycle()
    val pos by MusicController.position.collectAsStateWithLifecycle()
    val dur by MusicController.duration.collectAsStateWithLifecycle()
    val shuffle by MusicController.shuffle.collectAsStateWithLifecycle()
    val repeat by MusicController.repeat.collectAsStateWithLifecycle()
    val sleep by MusicController.sleepRemaining.collectAsStateWithLifecycle()
    val sleepEnd by MusicController.sleepEndOfTrack.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()

    var accentTarget by remember { mutableStateOf(Neon) }
    val accent by animateColorAsState(accentTarget, tween(900), label = "acc")
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var visStyle by remember { mutableStateOf(VisStyle.entries.getOrElse(Prefs.get(ctx).getInt("vis_style", 0)) { VisStyle.BARS }) }
    var showEffects by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var hasRec by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val recLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasRec = it }

    val song = songOrNull
    LaunchedEffect(song?.albumId) { accentTarget = extractAccent(ctx, song?.artUri)?.let { boost(it) } ?: Neon }

    Box(Modifier.fillMaxSize().background(Bg).clickable(remember { MutableInteractionSource() }, null) {}) {
        if (song != null) {
            AsyncImage(song.artUri, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(80.dp).graphicsLayer { alpha = 0.5f })
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), Bg.copy(alpha = 0.75f), Bg))))

        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Kapat", Modifier.size(32.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ŞİMDİ ÇALIYOR", fontSize = 11.sp, letterSpacing = 3.sp, color = TextDim)
                    Text(song?.album ?: "", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = { showEffects = true }) { Icon(Icons.Rounded.Tune, "Efektler") }
            }
            ModeSelector(mode, accent) { mode = it }

            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                if (song == null) {
                    Text("Çalan bir şey yok", color = TextDim)
                } else Crossfade(mode, label = "mode") { m ->
                    when (m) {
                        0 -> CoverMode(song, accent, playing, hasRec)
                        1 -> Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                                .background(Color.Black.copy(alpha = 0.25f))
                                .clickable { visStyle = VisStyle.entries[(visStyle.ordinal + 1) % VisStyle.entries.size]; Prefs.get(ctx).edit().putInt("vis_style", visStyle.ordinal).apply() }
                            ) {
                                AudioVisualizer(visStyle, accent, Modifier.fillMaxSize().padding(8.dp))
                                if (!hasRec) PermHint(accent, Modifier.align(Alignment.BottomCenter)) { recLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                            }
                            val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent, selectedLabelColor = Color.White)
                            val rowState = rememberLazyListState()
                            LazyRow(state = rowState, modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(VisStyle.entries) { st ->
                                    FilterChip(st == visStyle, {
                                        visStyle = st; Prefs.get(ctx).edit().putInt("vis_style", st.ordinal).apply()
                                    }, label = { Text(st.label) }, colors = chipColors)
                                }
                            }
                        }
                        else -> LyricsView(song, accent, Modifier.fillMaxSize())
                    }
                }
            }

            if (song != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(song.title, fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee())
                        Text(song.artist, color = TextDim, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val fav = song.id in favs
                    IconButton(onClick = { vm.toggleFavorite(song.id) }) {
                        Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (fav) Pink else Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }
            SeekBar(pos, dur, accent)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { MusicController.toggleShuffle() }) {
                    Icon(Icons.Rounded.Shuffle, "Karıştır", tint = if (shuffle) accent else TextDim)
                }
                IconButton(onClick = { MusicController.previous() }, Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(40.dp)) }
                Box(
                    Modifier.size(78.dp).shadow(24.dp, CircleShape, ambientColor = accent, spotColor = accent)
                        .clip(CircleShape).background(Brush.linearGradient(listOf(accent, Pink)))
                        .clickable { MusicController.toggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(42.dp))
                }
                IconButton(onClick = { MusicController.next() }, Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipNext, null, Modifier.size(40.dp)) }
                IconButton(onClick = { MusicController.cycleRepeat() }) {
                    Icon(if (repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Tekrar",
                        tint = if (repeat != Player.REPEAT_MODE_OFF) accent else TextDim)
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showSleep = true }) {
                    Icon(Icons.Rounded.Bedtime, "Uyku", tint = if (sleep != null || sleepEnd) accent else TextDim)
                }
                if (sleep != null) Text(formatTime(sleep!!), color = accent, fontSize = 12.sp)
                IconButton(onClick = { showEffects = true }) { Icon(Icons.Rounded.GraphicEq, "Ekolayzır", tint = TextDim) }
                IconButton(onClick = { showQueue = true }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Sıra", tint = TextDim) }
            }
        }
    }

    if (showEffects) EffectsSheet(accent) { showEffects = false }
    if (showQueue) QueueSheet(accent) { showQueue = false }
    if (showSleep) SleepDialog(accent) { showSleep = false }
}

private fun boost(c: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(android.graphics.Color.argb(255, (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()), hsv)
    hsv[1] = hsv[1].coerceAtLeast(0.55f); hsv[2] = hsv[2].coerceAtLeast(0.8f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
private fun ModeSelector(mode: Int, accent: Color, onSelect: (Int) -> Unit) {
    val items = listOf(Icons.Rounded.Album to "Kapak", Icons.Rounded.GraphicEq to "Spektrum", Icons.Rounded.Lyrics to "Sözler")
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.06f)).padding(4.dp)
    ) {
        items.forEachIndexed { i, (icon, label) ->
            val sel = i == mode
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(if (sel) Brush.horizontalGradient(listOf(accent, Pink)) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable { onSelect(i) }.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, Modifier.size(16.dp), tint = if (sel) Color.White else TextDim)
                Text(" $label", fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, color = if (sel) Color.White else TextDim)
            }
        }
    }
}

@Composable
private fun CoverMode(song: Song, accent: Color, playing: Boolean, hasRec: Boolean) {
    var angle by remember { mutableFloatStateOf(0f) }
    var bass by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        var last = 0L
        while (playing) {
            withFrameNanos { t ->
                if (last != 0L) angle = (angle + (t - last) / 1e9f * 12f) % 360f
                last = t
            }
        }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        AudioVisualizer(VisStyle.CIRCLE, accent, Modifier.fillMaxSize(), onBass = { bass = it })
        val scale = 1f + bass * 0.07f
        Box(
            Modifier.fillMaxSize(0.56f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(30.dp, CircleShape, ambientColor = accent, spotColor = accent)
                .clip(CircleShape)
                .background(Color.Black)
                .rotate(angle),
            contentAlignment = Alignment.Center
        ) {
            ArtImage(song.artUri, Modifier.fillMaxSize(), corner = 500.dp, iconSize = 64.dp)
            Box(Modifier.fillMaxSize(0.16f).clip(CircleShape).background(Bg.copy(alpha = 0.9f)))
            Box(Modifier.fillMaxSize(0.05f).clip(CircleShape).background(accent))
        }
    }
}

@Composable
private fun PermHint(accent: Color, modifier: Modifier, onGrant: () -> Unit) {
    Column(
        modifier.padding(12.dp).clip(RoundedCornerShape(16.dp)).background(Card.copy(alpha = 0.92f)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Gerçek zamanlı spektrum için ses analizi izni gerekli", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text("Android bunu “Mikrofon” izni olarak gösterir; uygulama mikrofonu dinlemez, yalnızca çalan sesi analiz eder.", fontSize = 11.sp, color = TextDim)
        Spacer(Modifier.height(6.dp))
        Button(onClick = onGrant, colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("İzin Ver") }
    }
}

@Composable
private fun SeekBar(pos: Long, dur: Long, accent: Color) {
    var dragging by remember { mutableStateOf(false) }
    var dragVal by remember { mutableFloatStateOf(0f) }
    val f = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Column {
        Slider(
            value = if (dragging) dragVal else f,
            onValueChange = { dragging = true; dragVal = it },
            onValueChangeFinished = { MusicController.seekTo((dragVal * dur).toLong()); dragging = false },
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = accent, inactiveTrackColor = Color.White.copy(alpha = 0.12f)),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(if (dragging) (dragVal * dur).toLong() else pos), fontSize = 12.sp, color = TextDim)
            Spacer(Modifier.weight(1f))
            Text("-" + formatTime(dur - (if (dragging) (dragVal * dur).toLong() else pos)), fontSize = 12.sp, color = TextDim)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(accent: Color, onDismiss: () -> Unit) {
    val queue by MusicController.queue.collectAsStateWithLifecycle()
    val idx by MusicController.currentIndex.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Card) {
        Text("Çalma Sırası • ${queue.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        val st = rememberLazyListState(initialFirstVisibleItemIndex = (idx - 2).coerceAtLeast(0))
        LazyColumn(state = st, modifier = Modifier.fillMaxWidth().height(480.dp)) {
            itemsIndexed(queue) { i, s ->
                Row(
                    Modifier.fillMaxWidth().clickable { MusicController.playAt(i) }
                        .background(if (i == idx) accent.copy(alpha = 0.15f) else Color.Transparent)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${i + 1}", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(end = 12.dp))
                    ArtImage(s.artUri, Modifier.size(42.dp), corner = 8.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (i == idx) Cyan else Color.White, fontWeight = FontWeight.Medium)
                        Text(s.artist, maxLines = 1, color = TextDim, fontSize = 12.sp)
                    }
                    if (i == idx) PlayingBars(accent)
                }
            }
        }
    }
}

@Composable
private fun SleepDialog(accent: Color, onDismiss: () -> Unit) {
    val opts = listOf(0 to "Kapalı", 5 to "5 dakika", 15 to "15 dakika", 30 to "30 dakika", 45 to "45 dakika", 60 to "1 saat", 90 to "1,5 saat", -1 to "Şarkı bitince")
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card,
        title = { Text("Uyku Zamanlayıcı") },
        text = {
            Column {
                opts.forEach { (m, label) ->
                    Text(label, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                        if (m == -1) MusicController.sleepAtEndOfTrack() else MusicController.setSleepTimer(m)
                        onDismiss()
                    }.padding(vertical = 12.dp, horizontal = 8.dp), fontSize = 16.sp)
                }
                Text("Son 15 saniyede ses yavaşça kısılır.", color = TextDim, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat", color = accent) } },
    )
}
