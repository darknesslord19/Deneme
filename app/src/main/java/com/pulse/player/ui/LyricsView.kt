package com.pulse.player.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.player.data.Song
import com.pulse.player.lyrics.LyricsRepository
import com.pulse.player.player.MusicController
import com.pulse.player.ui.theme.Card
import com.pulse.player.ui.theme.Cyan
import com.pulse.player.ui.theme.TextDim
import kotlinx.coroutines.launch
import kotlin.math.abs

private sealed interface LState {
    data object Loading : LState
    data object NotFound : LState
    data class Found(val r: LyricsRepository.Result) : LState
}

@Composable
fun LyricsView(song: Song, accent: Color, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember(song.id) { mutableStateOf<LState>(LState.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    var offset by remember(song.id) { mutableLongStateOf(LyricsRepository.getOffset(ctx, song)) }
    var showSearch by remember { mutableStateOf(false) }

    LaunchedEffect(song.id, reload) {
        state = LState.Loading
        val r = LyricsRepository.load(ctx, song)
        state = if (r == null) LState.NotFound else LState.Found(r)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val r = LyricsRepository.importFile(ctx, song, uri)
            if (r != null) state = LState.Found(r)
        }
    }

    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val s = state) {
                LState.Loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = accent)
                    Spacer(Modifier.height(12.dp))
                    Text("Şarkı sözleri aranıyor…", color = TextDim)
                }
                LState.NotFound -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Söz bulunamadı", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Çevrimiçi arayabilir ya da bir .lrc dosyası yükleyebilirsin.", color = TextDim, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { showSearch = true }, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                            Icon(Icons.Rounded.Search, null); Text(" Ara")
                        }
                        OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Icon(Icons.Rounded.FolderOpen, null); Text(" .lrc Yükle") }
                    }
                }
                is LState.Found -> SyncedLyrics(s.r, offset, accent)
            }
        }
        if (state is LState.Found) {
            val r = (state as LState.Found).r
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (r.synced) "● Senkron • ${r.source}" else "Senkronsuz • ${r.source}", color = if (r.synced) Cyan else TextDim, fontSize = 11.sp, modifier = Modifier.weight(1f))
                if (r.synced) {
                    IconButton(onClick = { offset -= 250; LyricsRepository.setOffset(ctx, song, offset) }, Modifier.size(32.dp)) { Icon(Icons.Rounded.Remove, "Geri", Modifier.size(18.dp)) }
                    Text("%+.2fs".format(offset / 1000f), fontSize = 11.sp, color = TextDim)
                    IconButton(onClick = { offset += 250; LyricsRepository.setOffset(ctx, song, offset) }, Modifier.size(32.dp)) { Icon(Icons.Rounded.Add, "İleri", Modifier.size(18.dp)) }
                }
                IconButton(onClick = { showSearch = true }, Modifier.size(36.dp)) { Icon(Icons.Rounded.Search, "Ara", Modifier.size(20.dp)) }
                IconButton(onClick = { picker.launch(arrayOf("*/*")) }, Modifier.size(36.dp)) { Icon(Icons.Rounded.FolderOpen, "Dosya", Modifier.size(20.dp)) }
            }
        }
    }

    if (showSearch) {
        LyricsSearchDialog(song, accent, onDismiss = { showSearch = false }) { hit ->
            showSearch = false
            scope.launch {
                val r = LyricsRepository.useHit(ctx, song, hit)
                state = if (r == null) LState.NotFound else LState.Found(r)
            }
        }
    }
}

@Composable
private fun SyncedLyrics(r: LyricsRepository.Result, offset: Long, accent: Color) {
    val pos by MusicController.position.collectAsStateWithLifecycle()
    val lines = r.lines
    val listState = rememberLazyListState()
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    val lastDrag = remember { longArrayOf(0L) }
    if (dragged) lastDrag[0] = System.currentTimeMillis()
    val t = pos + offset
    val active = if (!r.synced) -1 else lines.indexOfLast { it.time <= t }

    LaunchedEffect(active) {
        if (active >= 0 && System.currentTimeMillis() - lastDrag[0] > 2500) listState.animateScrollToItem(active)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = if (r.synced) h * 0.36f else 12.dp, bottom = h * 0.55f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(lines) { i, line ->
                val isActive = i == active
                val dist = if (active < 0) 0 else abs(i - active)
                val scale by animateFloatAsState(if (isActive) 1.08f else 1f, label = "s")
                val alpha by animateFloatAsState(
                    when { !r.synced -> 0.9f; isActive -> 1f; else -> (0.55f - dist * 0.08f).coerceAtLeast(0.18f) }, label = "a"
                )
                val text = line.text.ifBlank { "♪ ♪ ♪" }
                val style = if (isActive) {
                    val end = lines.getOrNull(i + 1)?.time ?: (line.time + 4000)
                    val p = ((t - line.time).toFloat() / (end - line.time).coerceAtLeast(1)).coerceIn(0f, 1f)
                    TextStyle(
                        brush = Brush.horizontalGradient(
                            0f to Color.White, p to Color.White,
                            (p + 0.03f).coerceAtMost(1f) to Color.White.copy(alpha = 0.38f), 1f to Color.White.copy(alpha = 0.38f)
                        ),
                        shadow = Shadow(accent, Offset.Zero, 28f),
                        fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 32.sp,
                    )
                } else TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
                Text(
                    text, style = style,
                    modifier = Modifier.fillMaxWidth()
                        .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha; transformOrigin = TransformOrigin(0f, 0.5f) }
                        .clickable(enabled = r.synced && line.time >= 0) { MusicController.seekTo(line.time - offset) }
                        .padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun LyricsSearchDialog(song: Song, accent: Color, onDismiss: () -> Unit, onPick: (LyricsRepository.Hit) -> Unit) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(LyricsRepository.cleanTitle(song.title)) }
    var artist by remember { mutableStateOf(if (song.artist.startsWith("Bilinmeyen")) "" else song.artist) }
    var hits by remember { mutableStateOf<List<LyricsRepository.Hit>?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun run() { busy = true; scope.launch { hits = LyricsRepository.searchHits(title, artist); busy = false } }
    LaunchedEffect(Unit) { run() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card,
        title = { Text("Söz Ara (LRCLIB)") },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text("Şarkı adı") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(artist, { artist = it }, label = { Text("Sanatçı") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Button(onClick = { run() }, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = accent), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.CloudDownload, null); Text(" Ara")
                }
                Spacer(Modifier.height(8.dp))
                if (busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = accent)
                val h = hits
                if (h != null && !busy) {
                    if (h.isEmpty()) Text("Sonuç yok", color = TextDim)
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(h) { hit ->
                            Column(Modifier.fillMaxWidth().clickable { onPick(hit) }.padding(vertical = 8.dp)) {
                                Text(hit.track, fontWeight = FontWeight.SemiBold)
                                Text("${hit.artist} • ${formatTime((hit.duration * 1000).toLong())}" + if (hit.synced != null) "  • SENKRON" else "",
                                    color = if (hit.synced != null) Cyan else TextDim, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
        shape = RoundedCornerShape(24.dp),
    )
}
