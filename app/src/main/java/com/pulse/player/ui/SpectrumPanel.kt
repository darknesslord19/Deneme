package com.pulse.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pulse.player.data.Prefs
import com.pulse.player.data.Song
import com.pulse.player.player.AudioTap
import com.pulse.player.ui.theme.Cyan
import com.pulse.player.ui.theme.Pink
import com.pulse.player.ui.theme.TextDim
import kotlinx.coroutines.delay
import kotlin.math.log10

private class Stats(val bpm: Int, val db: Float, val hz: Float, val l: Float, val r: Float)

@Composable
private fun rememberStats(): Stats {
    var s by remember { mutableStateOf(Stats(0, -90f, 0f, 0f, 0f)) }
    LaunchedEffect(Unit) {
        var l = 0f; var r = 0f; var hz = 0f
        while (true) {
            val snap = AudioTap.current()
            l = maxOf(snap.levelL, l * 0.75f); r = maxOf(snap.levelR, r * 0.75f)
            if (snap.rmsDb > -50f) hz = snap.domHz
            s = Stats(snap.bpm, snap.rmsDb, hz, l, r)
            delay(120)
        }
    }
    return s
}

@Composable
fun SpectrumPanel(song: Song, accent: Color) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    var style by remember { mutableStateOf(VisStyle.entries.getOrElse(prefs.getInt("vis_style", 0)) { VisStyle.BARS }) }
    var auto by remember { mutableStateOf(prefs.getBoolean("vis_auto", false)) }
    var showGain by remember { mutableStateOf(false) }
    var gain by remember { mutableFloatStateOf(AudioTap.gain) }
    var full by remember { mutableStateOf(false) }
    var labelTick by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    fun select(v: VisStyle) { style = v; prefs.edit().putInt("vis_style", v.ordinal).apply(); labelTick++ }
    fun step(d: Int) = select(VisStyle.entries[(style.ordinal + d + VisStyle.entries.size) % VisStyle.entries.size])

    LaunchedEffect(auto) { while (auto) { delay(12_000); step(1) } }
    LaunchedEffect(style) { listState.animateScrollToItem(style.ordinal) }

    val stats = rememberStats()

    Column(Modifier.fillMaxSize()) {
        StatsRow(stats, accent)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable { step(1) }
        ) {
            AudioVisualizer(style, accent, Modifier.fillMaxSize().padding(8.dp))
            Row(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                SmallBtn(Icons.Rounded.Autorenew, auto, accent) { auto = !auto; prefs.edit().putBoolean("vis_auto", auto).apply() }
                SmallBtn(Icons.Rounded.Tune, showGain, accent) { showGain = !showGain }
                SmallBtn(Icons.Rounded.Fullscreen, false, accent) { full = true }
            }
            StyleBadge(style, labelTick, Modifier.align(Alignment.BottomStart).padding(10.dp))
        }
        AnimatedVisibility(showGain) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text("Hassasiyet", fontSize = 12.sp, color = TextDim)
                Slider(
                    value = gain, valueRange = 0.5f..3f,
                    onValueChange = { gain = it; AudioTap.gain = it },
                    onValueChangeFinished = { prefs.edit().putFloat("vis_gain", gain).apply() },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = accent)
                )
                Text("%.1fx".format(gain), fontSize = 12.sp, color = accent, fontWeight = FontWeight.Bold)
            }
        }
        val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent, selectedLabelColor = Color.White)
        LazyRow(state = listState, modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(VisStyle.entries) { st ->
                FilterChip(st == style, { select(st) }, label = { Text("${st.ordinal + 1}. ${st.label}") }, colors = chipColors)
            }
        }
    }

    if (full) {
        Dialog(onDismissRequest = { full = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable(remember { MutableInteractionSource() }, null) { full = false }) {
                AudioVisualizer(style, accent, Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(vertical = 60.dp, horizontal = 8.dp))
                Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(song.title.uppercase(), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, letterSpacing = 3.sp, maxLines = 1)
                    Text(song.artist, color = TextDim, fontSize = 11.sp, maxLines = 1)
                }
                Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    StatsRow(stats, accent, compact = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { step(-1) }) { Icon(Icons.Rounded.ChevronLeft, null, tint = Color.White.copy(alpha = 0.6f)) }
                        Text("${style.ordinal + 1}/${VisStyle.entries.size}  ·  ${style.label.uppercase()}", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, letterSpacing = 2.sp)
                        IconButton(onClick = { step(1) }) { Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = 0.6f)) }
                        IconButton(onClick = { auto = !auto; prefs.edit().putBoolean("vis_auto", auto).apply() }) {
                            Icon(Icons.Rounded.Autorenew, "Otomatik", tint = if (auto) accent else Color.White.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StyleBadge(style: VisStyle, tick: Int, modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(tick) { if (tick > 0) { visible = true; delay(1600); visible = false } }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Text(
            "${style.ordinal + 1}. ${style.label}", fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun SmallBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, accent: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(38.dp)) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(if (on) accent.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = Color.White)
        }
    }
}

@Composable
private fun StatsRow(s: Stats, accent: Color, compact: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatChip("BPM", if (s.bpm > 0) "${s.bpm}" else "--", accent, Modifier.weight(1f), compact)
        StatChip("SEVİYE", if (s.db > -80f) "%.0f dB".format(s.db) else "-∞", Cyan, Modifier.weight(1f), compact)
        StatChip("NOTA", if (s.hz > 20f) "${AudioTap.noteName(s.hz)}" else "—", Pink, Modifier.weight(1f), compact,
            sub = if (s.hz > 20f) "${s.hz.toInt()} Hz" else null)
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text("L / R", fontSize = 8.sp, color = TextDim, letterSpacing = 1.sp)
            Meter(s.l, accent); Meter(s.r, Cyan)
        }
    }
}

@Composable
private fun Meter(level: Float, color: Color) {
    val v = ((20f * log10(level + 1e-6f) + 48f) / 48f).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.08f))) {
        Box(Modifier.fillMaxWidth(v).fillMaxHeight().background(Brush.horizontalGradient(listOf(color, if (v > 0.9f) Color(0xFFFF3B3B) else color))))
    }
}

@Composable
private fun StatChip(title: String, value: String, color: Color, modifier: Modifier, compact: Boolean, sub: String? = null) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(title, fontSize = 8.sp, color = TextDim, letterSpacing = 1.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = FontFamily.Monospace, maxLines = 1)
            if (sub != null && !compact) { Spacer(Modifier.width(4.dp)); Text(sub, fontSize = 8.sp, color = TextDim, maxLines = 1) }
        }
    }
}
