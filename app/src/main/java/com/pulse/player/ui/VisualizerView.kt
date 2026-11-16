package com.pulse.player.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.pulse.player.player.MusicController
import com.pulse.player.player.VisualizerEngine
import com.pulse.player.ui.theme.Cyan
import com.pulse.player.ui.theme.Pink
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class VisStyle(val label: String) {
    BARS("Neon Çubuklar"), MIRROR("Ayna"), CIRCLE("Dairesel"), WAVE("Dalga"),
    PARTICLES("Parçacık"), RINGS("Halkalar"), BLOB("Sıvı Küre"), MOUNTAIN("Dağlar"),
    GALAXY("Galaksi"), LED("LED Matris"),
}

private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val size: Float, val hue: Float)
private class Star(val r: Float, val a: Float, val speed: Float, val size: Float, val band: Int)

@Composable
fun AudioVisualizer(style: VisStyle, accent: Color, modifier: Modifier = Modifier, onBass: ((Float) -> Unit)? = null) {
    val ctx = LocalContext.current
    val hasPerm = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    DisposableEffect(hasPerm) {
        if (hasPerm) VisualizerEngine.acquire(MusicController.audioSessionId)
        onDispose { if (hasPerm) VisualizerEngine.release() }
    }
    val bassCb by rememberUpdatedState(onBass)
    val styleNow by rememberUpdatedState(style)
    val n = VisualizerEngine.BANDS
    val bands = remember { FloatArray(n) }
    val peaks = remember { FloatArray(n) }
    val wave = remember { FloatArray(VisualizerEngine.WAVE) }
    val particles = remember { ArrayList<Particle>() }
    val stars = remember {
        List(260) { Star(Random.nextFloat().let { it * it } * 0.95f + 0.05f, Random.nextFloat() * 6.283f, 0.15f + Random.nextFloat() * 0.6f, 1f + Random.nextFloat() * 2.5f, Random.nextInt(n)) }
    }
    var time by remember { mutableFloatStateOf(0f) }
    var bass by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                val dt = if (last == 0L) 0.016f else ((t - last) / 1e9f).coerceIn(0.001f, 0.1f)
                last = t
                val playing = MusicController.isPlaying.value
                val real = VisualizerEngine.active.value && playing
                val src = VisualizerEngine.fft
                val sec = t / 1e9f
                for (i in 0 until n) {
                    val target = if (real) src[i] else if (playing) {
                        // ses analizi yoksa yumuşak sentetik hareket
                        val base = 0.55f - i / n.toFloat() * 0.35f
                        (base * (0.55f + 0.45f * sin(sec * 3.1f + i * 0.37f) * sin(sec * 1.3f + i * 0.11f)) +
                            0.12f * abs(sin(sec * 7f + i))).coerceIn(0f, 1f)
                    } else 0.02f
                    bands[i] = if (target > bands[i]) bands[i] + (target - bands[i]) * 0.55f else bands[i] * 0.9f + target * 0.1f
                    peaks[i] = max(peaks[i] - dt * 0.35f, bands[i])
                }
                val w = VisualizerEngine.wave
                for (i in wave.indices) {
                    val target = if (real) w[i] else if (playing) 0.35f * sin(sec * 4f + i * 0.12f) * sin(sec * 1.7f + i * 0.031f) else 0f
                    wave[i] += (target - wave[i]) * 0.45f
                }
                var b = 0f
                for (i in 0 until 6) b += bands[i]
                bass = b / 6f
                bassCb?.invoke(bass)

                if (styleNow == VisStyle.PARTICLES) {
                    val spawn = (bass * bass * 14).toInt() + if (playing) 1 else 0
                    repeat(spawn) {
                        val ang = Random.nextFloat() * 6.283f
                        val sp = 0.25f + Random.nextFloat() * 0.9f * (0.4f + bass)
                        particles += Particle(cos(ang) * 0.22f, sin(ang) * 0.22f, cos(ang) * sp, sin(ang) * sp, 1f, 1.5f + Random.nextFloat() * 4f, Random.nextFloat())
                    }
                    val iter = particles.iterator()
                    while (iter.hasNext()) {
                        val p = iter.next()
                        p.x += p.vx * dt; p.y += p.vy * dt; p.life -= dt * 0.55f
                        if (p.life <= 0f || abs(p.x) > 1.6f || abs(p.y) > 1.6f) iter.remove()
                    }
                    if (particles.size > 900) particles.subList(0, particles.size - 900).clear()
                }
                time = sec
            }
        }
    }

    Canvas(modifier) {
        val tm = time
        when (style) {
            VisStyle.BARS -> drawBars(bands, peaks, accent)
            VisStyle.MIRROR -> drawMirror(bands, accent)
            VisStyle.CIRCLE -> drawCircleSpec(bands, accent, bass, tm)
            VisStyle.WAVE -> drawWave(wave, accent, bass)
            VisStyle.PARTICLES -> drawParticles(particles, accent, bass)
            VisStyle.RINGS -> drawRings(bands, accent, tm)
            VisStyle.BLOB -> drawBlob(bands, accent, bass, tm)
            VisStyle.MOUNTAIN -> drawMountain(bands, accent)
            VisStyle.GALAXY -> drawGalaxy(stars, bands, accent, bass, tm)
            VisStyle.LED -> drawLed(bands, peaks, accent)
        }
    }
}

private fun sample(b: FloatArray, f: Float): Float {
    val x = f.coerceIn(0f, 1f) * (b.size - 1)
    val i = x.toInt()
    val j = min(i + 1, b.size - 1)
    return b[i] + (b[j] - b[i]) * (x - i)
}

private fun DrawScope.drawBars(b: FloatArray, peaks: FloatArray, accent: Color) {
    val count = 48
    val bw = size.width / count
    val h = size.height
    for (i in 0 until count) {
        val f = i / (count - 1f)
        val v = sample(b, f)
        val bh = max(4f, v * h * 0.9f)
        val col = lerp(accent, Cyan, f)
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Pink, col, col.copy(alpha = 0.5f)), startY = h - bh, endY = h),
            topLeft = Offset(i * bw + bw * 0.15f, h - bh),
            size = Size(bw * 0.7f, bh),
            cornerRadius = CornerRadius(bw * 0.35f)
        )
        // yansıma
        drawRoundRect(
            color = col.copy(alpha = 0.12f),
            topLeft = Offset(i * bw + bw * 0.15f, h - bh - 2f),
            size = Size(bw * 0.7f, 2f),
        )
        val py = h - max(6f, sample(peaks, f) * h * 0.9f) - 8f
        drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(i * bw + bw * 0.15f, py), Size(bw * 0.7f, 4f), CornerRadius(2f))
    }
}

private fun DrawScope.drawMirror(b: FloatArray, accent: Color) {
    val count = 40
    val half = size.width / 2
    val bw = half / count
    val cy = size.height / 2
    for (i in 0 until count) {
        val f = i / (count - 1f)
        val v = sample(b, f)
        val bh = max(3f, v * size.height * 0.45f)
        val col = lerp(accent, Pink, f)
        val brush = Brush.verticalGradient(listOf(Color.Transparent, col, Color.White, col, Color.Transparent), startY = cy - bh, endY = cy + bh)
        for (side in listOf(-1, 1)) {
            val x = half + side * (i * bw + bw / 2)
            drawLine(brush, Offset(x, cy - bh), Offset(x, cy + bh), strokeWidth = bw * 0.6f, cap = StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawCircleSpec(b: FloatArray, accent: Color, bass: Float, t: Float) {
    val c = center
    val m = min(size.width, size.height)
    val r = m * 0.3f * (1f + bass * 0.08f)
    val count = 120
    for (i in 0 until count) {
        val f = i / count.toFloat()
        val mf = if (f < 0.5f) f * 2 else (1 - f) * 2
        val v = sample(b, mf)
        val ang = f * 2 * PI.toFloat() + t * 0.15f - PI.toFloat() / 2
        val dx = cos(ang); val dy = sin(ang)
        val len = 4f + v * m * 0.19f
        val col = lerp(accent, Cyan, mf)
        drawLine(col, Offset(c.x + dx * r, c.y + dy * r), Offset(c.x + dx * (r + len), c.y + dy * (r + len)), strokeWidth = m / 160f + 2f, cap = StrokeCap.Round)
        drawCircle(Color.White.copy(alpha = 0.6f * v), radius = 2f, center = Offset(c.x + dx * (r + len + 6f), c.y + dy * (r + len + 6f)))
    }
    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f + bass * 0.3f), Color.Transparent), c, r * 1.05f), r * 1.05f, c)
    drawCircle(accent.copy(alpha = 0.8f), r * 0.98f, c, style = Stroke(3f))
}

private fun DrawScope.drawWave(w: FloatArray, accent: Color, bass: Float) {
    val cy = size.height / 2
    val amp = size.height * 0.42f
    for (layer in 0 until 3) {
        val path = Path()
        val k = 1f - layer * 0.3f
        for (i in w.indices) {
            val x = i / (w.size - 1f) * size.width
            val env = sin(i / (w.size - 1f) * PI.toFloat())
            val y = cy + w[i] * amp * k * env * (1f + bass * 0.5f) * if (layer == 1) -1 else 1
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val brush = Brush.horizontalGradient(listOf(accent, Cyan, Pink, accent))
        drawPath(path, brush, alpha = 0.18f, style = Stroke(width = 18f, cap = StrokeCap.Round))
        drawPath(path, brush, alpha = if (layer == 0) 1f else 0.5f, style = Stroke(width = if (layer == 0) 4f else 2f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawParticles(ps: List<Particle>, accent: Color, bass: Float) {
    val c = center
    val m = min(size.width, size.height) / 2
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), accent, Color.Transparent), c, m * (0.25f + bass * 0.2f)), m * (0.25f + bass * 0.2f), c)
    for (p in ps) {
        val col = lerp(lerp(accent, Cyan, p.hue), Pink, (1 - p.life) * 0.6f)
        drawCircle(col.copy(alpha = p.life.coerceIn(0f, 1f)), p.size * (0.6f + p.life), Offset(c.x + p.x * m, c.y + p.y * m))
    }
}

private fun DrawScope.drawRings(b: FloatArray, accent: Color, t: Float) {
    val c = center
    val m = min(size.width, size.height) / 2
    val rings = 9
    for (i in 0 until rings) {
        val f = i / (rings - 1f)
        val v = sample(b, f)
        val r = m * (0.15f + f * 0.8f) * (1f + v * 0.18f)
        val col = lerp(Pink, lerp(accent, Cyan, f), f)
        drawCircle(col.copy(alpha = 0.25f + v * 0.75f), r, c, style = Stroke(width = 2f + v * 14f))
        val ang = t * (0.6f + f) * if (i % 2 == 0) 1 else -1
        drawCircle(Color.White.copy(alpha = 0.4f + v * 0.6f), 3f + v * 5f, Offset(c.x + cos(ang) * r, c.y + sin(ang) * r))
    }
}

private fun DrawScope.drawBlob(b: FloatArray, accent: Color, bass: Float, t: Float) {
    val c = center
    val m = min(size.width, size.height) / 2
    for (layer in 0 until 3) {
        val path = Path()
        val steps = 120
        val base = m * (0.42f - layer * 0.08f)
        for (s in 0..steps) {
            val f = s / steps.toFloat()
            val mf = if (f < 0.5f) f * 2 else (1 - f) * 2
            val ang = f * 2 * PI.toFloat() + t * (0.2f + layer * 0.15f)
            val r = base * (1f + sample(b, mf) * (0.45f - layer * 0.1f) + 0.04f * sin(ang * 5 + t * 2))
            val x = c.x + cos(ang) * r; val y = c.y + sin(ang) * r
            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        val col = when (layer) { 0 -> accent; 1 -> Pink; else -> Cyan }
        drawPath(path, Brush.radialGradient(listOf(col.copy(alpha = 0.7f), col.copy(alpha = 0.15f)), c, m), alpha = 0.55f + bass * 0.3f)
        drawPath(path, col, style = Stroke(2.5f))
    }
}

private fun DrawScope.drawMountain(b: FloatArray, accent: Color) {
    val h = size.height
    val layers = listOf(Triple(0.0f, Pink, 0.35f), Triple(0.12f, accent, 0.55f), Triple(0.25f, Cyan, 0.8f))
    for ((shift, col, alpha) in layers) {
        val path = Path()
        path.moveTo(0f, h)
        val steps = 64
        for (s in 0..steps) {
            val f = s / steps.toFloat()
            val v = sample(b, (f + shift) % 1f)
            path.lineTo(f * size.width, h - (0.08f + v * (0.85f - shift)) * h)
        }
        path.lineTo(size.width, h); path.close()
        drawPath(path, Brush.verticalGradient(listOf(col.copy(alpha = alpha), Color.Transparent)))
    }
}

private fun DrawScope.drawGalaxy(stars: List<Star>, b: FloatArray, accent: Color, bass: Float, t: Float) {
    val c = center
    val m = min(size.width, size.height) / 2
    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.6f + bass * 0.4f), Pink.copy(alpha = 0.15f), Color.Transparent), c, m * 0.6f), m * 0.6f, c)
    for (s in stars) {
        val v = b[s.band]
        val ang = s.a + t * s.speed * (0.25f + bass * 1.5f) / (s.r + 0.2f)
        val r = s.r * m * (1f + v * 0.25f)
        val col = lerp(Color.White, lerp(accent, Cyan, s.r), s.r)
        drawCircle(col.copy(alpha = 0.35f + v * 0.65f), s.size * (1f + v * 1.6f), Offset(c.x + cos(ang) * r, c.y + sin(ang) * r * 0.62f))
    }
}

private fun DrawScope.drawLed(b: FloatArray, peaks: FloatArray, accent: Color) {
    val cols = 28; val rows = 18
    val cw = size.width / cols; val ch = size.height / rows
    for (x in 0 until cols) {
        val f = x / (cols - 1f)
        val lvl = (sample(b, f) * rows).toInt()
        val pk = (sample(peaks, f) * rows).toInt()
        for (y in 0 until rows) {
            val on = y < lvl
            val rf = y / (rows - 1f)
            val col = when {
                rf > 0.8f -> Pink
                rf > 0.55f -> accent
                else -> Cyan
            }
            val a = if (on || y == pk) 1f else 0.07f
            drawRoundRect(col.copy(alpha = a), Offset(x * cw + cw * 0.12f, size.height - (y + 1) * ch + ch * 0.12f), Size(cw * 0.76f, ch * 0.76f), CornerRadius(3f))
        }
    }
}
