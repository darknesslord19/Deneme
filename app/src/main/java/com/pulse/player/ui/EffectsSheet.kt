package com.pulse.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.player.player.AudioEffects
import com.pulse.player.player.MusicController
import com.pulse.player.ui.theme.Card
import com.pulse.player.ui.theme.CardHi
import com.pulse.player.ui.theme.Cyan
import com.pulse.player.ui.theme.Pink
import com.pulse.player.ui.theme.TextDim

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectsSheet(accent: Color, onDismiss: () -> Unit) {
    val s by AudioEffects.state.collectAsStateWithLifecycle()
    val available by AudioEffects.available.collectAsStateWithLifecycle()
    val speed by MusicController.speed.collectAsStateWithLifecycle()
    val pitch by MusicController.pitch.collectAsStateWithLifecycle()
    val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent, selectedLabelColor = Color.White)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Card,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ses Efektleri", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(if (available) "Ekolayzır • ${s.preset}" else "Bu cihaz ekolayzırı desteklemiyor", color = TextDim, fontSize = 12.sp)
                }
                Switch(
                    checked = s.enabled,
                    onCheckedChange = { v -> AudioEffects.update { it.copy(enabled = v) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = accent)
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AudioEffects.presets.forEach { (name, _) ->
                    FilterChip(s.preset == name, { AudioEffects.applyPreset(name) }, label = { Text(name) }, colors = chipColors)
                }
            }
            if (AudioEffects.bandCount > 0) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardHi).padding(vertical = 12.dp, horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (i in 0 until AudioEffects.bandCount) {
                        val lvl = s.bands.getOrElse(i) { 0 }
                        val min = AudioEffects.minLevel; val max = AudioEffects.maxLevel
                        BandSlider(
                            value = (lvl - min).toFloat() / (max - min),
                            label = AudioEffects.freqs.getOrNull(i)?.let { if (it >= 1000) "${it / 1000}k" else "$it" } ?: "",
                            db = lvl / 100f,
                            accent = if (s.enabled) accent else TextDim,
                        ) { f -> AudioEffects.setBand(i, (min + f * (max - min)).toInt()) }
                    }
                }
                TextButton(onClick = { AudioEffects.reset() }) { Text("Sıfırla", color = accent) }
            }

            EffectSlider("Bas Güçlendirici", "${s.bass / 10}%", s.bass / 1000f, accent) { v -> AudioEffects.update { it.copy(bass = (v * 1000).toInt(), enabled = true) } }
            EffectSlider("3D Surround", "${s.virt / 10}%", s.virt / 1000f, accent) { v -> AudioEffects.update { it.copy(virt = (v * 1000).toInt(), enabled = true) } }
            EffectSlider("Ses Yükseltici", "+%.1f dB".format(s.loud / 100f), s.loud / 1500f, accent) { v -> AudioEffects.update { it.copy(loud = (v * 1500).toInt(), enabled = true) } }

            Text("Yankı (Reverb)", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AudioEffects.reverbNames.forEachIndexed { i, n ->
                    FilterChip(s.reverb == i, { AudioEffects.update { it.copy(reverb = i, enabled = true) } }, label = { Text(n) }, colors = chipColors)
                }
            }

            EffectSlider("Hız", "%.2fx".format(speed), (speed - 0.5f) / 1.5f, accent) { v ->
                MusicController.setSpeed(((0.5f + v * 1.5f) * 20).toInt() / 20f)
            }
            EffectSlider("Perde (Pitch)", "%.2fx".format(pitch), (pitch - 0.5f) / 1.5f, accent) { v ->
                MusicController.setPitch(((0.5f + v * 1.5f) * 20).toInt() / 20f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Nightcore" to (1.25f to 1.25f), "Slowed" to (0.85f to 0.85f), "Normal" to (1f to 1f), "Chipmunk" to (1f to 1.6f)).forEach { (n, sp) ->
                    FilterChip(speed == sp.first && pitch == sp.second, {
                        MusicController.player.playbackParameters = androidx.media3.common.PlaybackParameters(sp.first, sp.second)
                    }, label = { Text(n) }, colors = chipColors)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EffectSlider(title: String, value: String, f: Float, accent: Color, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = 10.dp)) {
        Row {
            Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(value, color = accent, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = f.coerceIn(0f, 1f), onValueChange = onChange,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = accent, inactiveTrackColor = Color.White.copy(alpha = 0.1f))
        )
    }
}

@Composable
private fun BandSlider(value: Float, label: String, db: Float, accent: Color, onChange: (Float) -> Unit) {
    val cb by rememberUpdatedState(onChange)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("%+.0f".format(db), fontSize = 10.sp, color = TextDim)
        Canvas(
            Modifier.width(34.dp).height(170.dp)
                .pointerInput(Unit) { detectTapGestures { o -> cb((1f - o.y / size.height).coerceIn(0f, 1f)) } }
                .pointerInput(Unit) { detectVerticalDragGestures { ch, _ -> cb((1f - ch.position.y / size.height).coerceIn(0f, 1f)) } }
        ) {
            val w = size.width; val h = size.height
            val tw = 6.dp.toPx()
            val x = (w - tw) / 2
            drawRoundRect(Color.White.copy(alpha = 0.08f), Offset(x, 0f), Size(tw, h), CornerRadius(tw))
            val y = (1f - value) * h
            val mid = h / 2
            val top = minOf(y, mid); val bottom = maxOf(y, mid)
            drawRoundRect(Brush.verticalGradient(listOf(Pink, accent, Cyan)), Offset(x, top), Size(tw, (bottom - top).coerceAtLeast(2f)), CornerRadius(tw))
            drawLine(Color.White.copy(alpha = 0.2f), Offset(4f, mid), Offset(w - 4f, mid), 1f)
            drawCircle(accent.copy(alpha = 0.35f), 13.dp.toPx(), Offset(w / 2, y))
            drawCircle(Color.White, 8.dp.toPx(), Offset(w / 2, y))
        }
        Text(label, fontSize = 11.sp, color = TextDim)
    }
}
