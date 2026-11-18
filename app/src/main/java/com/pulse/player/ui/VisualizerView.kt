package com.pulse.player.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.pulse.player.player.AudioTap
import com.pulse.player.player.MusicController
import com.pulse.player.ui.theme.Cyan
import com.pulse.player.ui.theme.Pink
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class VisStyle(val label: String) {
    BARS("Neon Çubuklar"), MIRROR("Ayna"), CIRCLE("Dairesel"), WAVE("Dalga"),
    PARTICLES("Parçacık"), RINGS("Halkalar"), BLOB("Sıvı Küre"), MOUNTAIN("Dağlar"),
    GALAXY("Galaksi"), LED("LED Matris"),
    ANALYZER("Pro Analizör"), SCOPE("Osiloskop"), VECTOR("Vektörskop"), VU("VU Metre"),
    SPECTROGRAM("Spektrogram"), SUNBURST("Güneş Patlaması"), TUNNEL("Tünel"), DNA("DNA Sarmalı"),
    FIREWORKS("Havai Fişek"), CRYSTAL("Kristal"), HEART("Kalp Monitörü"), CITY("3D Şehir"),
    RING_WAVE("Dairesel Dalga"), RAIN("Dijital Yağmur"),
}

private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val size: Float, val hue: Float, val gravity: Float = 0f)
private class Star(val r: Float, val a: Float, val speed: Float, val size: Float, val band: Int)

private class VisState {
    val n = AudioTap.BANDS
    val bands = FloatArray(n)
    val peaks = FloatArray(n)
    val wave = FloatArray(AudioTap.WAVE)
    var raw: AudioTap.Snapshot? = null
    var levelL = 0f
    var levelR = 0f
    var vuL = 0f
    var vuR = 0f
    var bass = 0f
    var beat = 0f
    var lastBeatSeen = 0L
    var beatCount = 0
    var time = 0f
    val particles = ArrayList<Particle>()
    val stars = List(260) {
        Star(Random.nextFloat().let { it * it } * 0.95f + 0.05f, Random.nextFloat() * 6.283f,
            0.15f + Random.nextFloat() * 0.6f, 1f + Random.nextFloat() * 2.5f, Random.nextInt(AudioTap.BANDS))
    }
    // spektrogram
    val specW = 180
    val specH = 64
    val specPx = IntArray(specW * specH)
    val specBmp: Bitmap = Bitmap.createBitmap(specW, specH, Bitmap.Config.ARGB_8888)
    val specImg = specBmp.asImageBitmap()
    var palette = IntArray(256)
    var paletteFor = 0
    // kalp monitörü
    val ecg = FloatArray(320)
    var ecgIdx = 0
    // dijital yağmur
    val rainY = FloatArray(28) { Random.nextFloat() }
}

@Composable
fun AudioVisualizer(style: VisStyle, accent: Color, modifier: Modifier = Modifier, onBass: ((Float) -> Unit)? = null) {
    val bassCb by rememberUpdatedState(onBass)
    val styleNow by rememberUpdatedState(style)
    val accentNow by rememberUpdatedState(accent)
    val st = remember { VisState() }
    var frame by remember { mutableIntStateOf(0) }
    val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) } }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                val dt = if (last == 0L) 0.016f else ((t - last) / 1e9f).coerceIn(0.001f, 0.1f)
                last = t
                st.time += dt
                val snap = AudioTap.current()
                st.raw = snap
                val src = snap.bands
                for (i in 0 until st.n) {
                    val target = src[i]
                    st.bands[i] = if (target > st.bands[i]) st.bands[i] + (target - st.bands[i]) * 0.6f else st.bands[i] * 0.86f + target * 0.14f
                    st.peaks[i] = max(st.peaks[i] - dt * 0.35f, st.bands[i])
                }
                for (i in st.wave.indices) st.wave[i] += (snap.wave[i] - st.wave[i]) * 0.5f
                st.levelL = if (snap.levelL > st.levelL) snap.levelL else st.levelL * 0.9f
                st.levelR = if (snap.levelR > st.levelR) snap.levelR else st.levelR * 0.9f
                // VU iğnesi: hızlı yüksel, yavaş düş
                fun vu(level: Float) = ((20f * log10(level + 1e-6f) + 40f) / 43f).coerceIn(0f, 1f)
                st.vuL += (vu(st.levelL) - st.vuL) * (if (vu(st.levelL) > st.vuL) 0.35f else 0.08f)
                st.vuR += (vu(st.levelR) - st.vuR) * (if (vu(st.levelR) > st.vuR) 0.35f else 0.08f)
                var b = 0f
                for (i in 0 until 6) b += st.bands[i]
                st.bass = b / 6f
                val now = System.nanoTime()
                if (snap.beatAt != st.lastBeatSeen && snap.beatAt in 1..now) { st.lastBeatSeen = snap.beatAt; st.beatCount++ ; onBeat(st, styleNow) }
                st.beat = if (st.lastBeatSeen > 0) exp(-((now - st.lastBeatSeen) / 1e9f) / 0.12f) else 0f
                bassCb?.invoke(st.bass)
                stepParticles(st, styleNow, dt)
                if (styleNow == VisStyle.SPECTROGRAM && st.beatCount >= 0) pushSpectrogram(st, accentNow)
                if (styleNow == VisStyle.HEART) pushEcg(st, now)
                if (styleNow == VisStyle.RAIN) for (i in st.rainY.indices) {
                    val v = st.bands[(i * st.n / st.rainY.size).coerceAtMost(st.n - 1)]
                    st.rainY[i] = (st.rainY[i] + dt * (0.15f + v * 1.4f)) % 1.3f
                }
                frame++
            }
        }
    }

    Canvas(modifier) {
        frame.let { }
        when (style) {
            VisStyle.BARS -> drawBars(st.bands, st.peaks, accent)
            VisStyle.MIRROR -> drawMirror(st.bands, accent)
            VisStyle.CIRCLE -> drawCircleSpec(st.bands, accent, st.bass, st.time)
            VisStyle.WAVE -> drawWave(st.wave, accent, st.bass)
            VisStyle.PARTICLES -> drawParticles(st.particles, accent, st.bass)
            VisStyle.RINGS -> drawRings(st.bands, accent, st.time)
            VisStyle.BLOB -> drawBlob(st.bands, accent, st.bass, st.time)
            VisStyle.MOUNTAIN -> drawMountain(st.bands, accent)
            VisStyle.GALAXY -> drawGalaxy(st.stars, st.bands, accent, st.bass, st.time)
            VisStyle.LED -> drawLed(st.bands, st.peaks, accent)
            VisStyle.ANALYZER -> drawAnalyzer(st, accent, textPaint)
            VisStyle.SCOPE -> drawScope(st, textPaint)
            VisStyle.VECTOR -> drawVector(st, accent)
            VisStyle.VU -> drawVu(st, accent, textPaint)
            VisStyle.SPECTROGRAM -> drawSpectrogram(st, textPaint)
            VisStyle.SUNBURST -> drawSunburst(st, accent)
            VisStyle.TUNNEL -> drawTunnel(st, accent)
            VisStyle.DNA -> drawDna(st, accent)
            VisStyle.FIREWORKS -> drawParticlesSimple(st, accent)
            VisStyle.CRYSTAL -> drawCrystal(st, accent)
            VisStyle.HEART -> drawHeart(st, textPaint)
            VisStyle.CITY -> drawCity(st, accent)
            VisStyle.RING_WAVE -> drawRingWave(st, accent)
            VisStyle.RAIN -> drawRain(st, accent)
        }
    }
}

// ---------------- durum güncellemeleri ----------------
private fun onBeat(st: VisState, style: VisStyle) {
    if (style != VisStyle.FIREWORKS) return
    val cx = (Random.nextFloat() - 0.5f) * 1.2f
    val cy = (Random.nextFloat() - 0.6f) * 1.0f
    val hue = Random.nextFloat()
    repeat(70) {
        val a = Random.nextFloat() * 6.283f
        val sp = 0.3f + Random.nextFloat() * 0.8f
        st.particles += Particle(cx, cy, cos(a) * sp, sin(a) * sp, 1f, 1.5f + Random.nextFloat() * 2.5f, hue, 0.5f)
    }
}

private fun stepParticles(st: VisState, style: VisStyle, dt: Float) {
    if (style == VisStyle.PARTICLES) {
        val spawn = (st.bass * st.bass * 14).toInt() + if (MusicController.isPlaying.value) 1 else 0
        repeat(spawn) {
            val ang = Random.nextFloat() * 6.283f
            val sp = 0.25f + Random.nextFloat() * 0.9f * (0.4f + st.bass)
            st.particles += Particle(cos(ang) * 0.22f, sin(ang) * 0.22f, cos(ang) * sp, sin(ang) * sp, 1f, 1.5f + Random.nextFloat() * 4f, Random.nextFloat())
        }
    } else if (style == VisStyle.FIREWORKS && st.particles.isEmpty() && MusicController.isPlaying.value && Random.nextFloat() < 0.02f) {
        onBeat(st, style)
    }
    if (style != VisStyle.PARTICLES && style != VisStyle.FIREWORKS) { st.particles.clear(); return }
    val iter = st.particles.iterator()
    while (iter.hasNext()) {
        val p = iter.next()
        p.vy += p.gravity * dt
        p.vx *= (1 - p.gravity * 0.6f * dt); p.vy *= (1 - p.gravity * 0.3f * dt)
        p.x += p.vx * dt; p.y += p.vy * dt; p.life -= dt * 0.55f
        if (p.life <= 0f || abs(p.x) > 1.8f || abs(p.y) > 1.8f) iter.remove()
    }
    if (st.particles.size > 1200) st.particles.subList(0, st.particles.size - 1200).clear()
}

private fun pushSpectrogram(st: VisState, accent: Color) {
    val key = accent.toArgb()
    if (st.paletteFor != key || st.palette[255] == 0) {
        st.paletteFor = key
        val stops = listOf(Color(0xFF05050A), accent.copy(alpha = 1f), Pink, Color(0xFFFFD34D), Color.White)
        for (i in 0 until 256) {
            val f = i / 255f * (stops.size - 1)
            val a = f.toInt().coerceAtMost(stops.size - 2)
            st.palette[i] = lerp(stops[a], stops[a + 1], f - a).toArgb()
        }
    }
    val w = st.specW; val h = st.specH
    for (y in 0 until h) {
        val row = y * w
        System.arraycopy(st.specPx, row + 1, st.specPx, row, w - 1)
        val band = (h - 1 - y) * st.n / h
        st.specPx[row + w - 1] = st.palette[(st.bands[band] * 255).toInt().coerceIn(0, 255)]
    }
    st.specBmp.setPixels(st.specPx, 0, w, 0, 0, w, h)
}

private fun ecgShape(t: Float): Float {
    val g = { c: Float, w: Float, a: Float -> a * exp(-((t - c) / w).let { it * it }) }
    return g(0.04f, 0.02f, 0.12f) - g(0.09f, 0.006f, 0.2f) + g(0.105f, 0.008f, 1f) - g(0.12f, 0.008f, 0.35f) + g(0.28f, 0.04f, 0.25f)
}

private fun pushEcg(st: VisState, now: Long) {
    val since = if (st.lastBeatSeen > 0) (now - st.lastBeatSeen) / 1e9f else 9f
    val amp = 0.45f + st.bass * 0.8f
    st.ecg[st.ecgIdx] = ecgShape(since) * amp + (Random.nextFloat() - 0.5f) * 0.015f
    st.ecgIdx = (st.ecgIdx + 1) % st.ecg.size
}

// ---------------- çizim yardımcıları ----------------
private fun sample(b: FloatArray, f: Float): Float {
    val x = f.coerceIn(0f, 1f) * (b.size - 1)
    val i = x.toInt()
    val j = min(i + 1, b.size - 1)
    return b[i] + (b[j] - b[i]) * (x - i)
}

private fun DrawScope.text(p: Paint, s: String, x: Float, y: Float, sizeDp: Float, color: Color, align: Paint.Align = Paint.Align.LEFT) {
    p.textSize = sizeDp * density; p.color = color.toArgb(); p.textAlign = align
    drawContext.canvas.nativeCanvas.drawText(s, x, y, p)
}

// ---------------- klasik 10 stil ----------------
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
            topLeft = Offset(i * bw + bw * 0.15f, h - bh), size = Size(bw * 0.7f, bh), cornerRadius = CornerRadius(bw * 0.35f)
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
        val bh = max(3f, sample(b, f) * size.height * 0.45f)
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
        drawLine(lerp(accent, Cyan, mf), Offset(c.x + dx * r, c.y + dy * r), Offset(c.x + dx * (r + len), c.y + dy * (r + len)), strokeWidth = m / 160f + 2f, cap = StrokeCap.Round)
        drawCircle(Color.White.copy(alpha = (0.6f * v).coerceIn(0f, 1f)), radius = 2f, center = Offset(c.x + dx * (r + len + 6f), c.y + dy * (r + len + 6f)))
    }
    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = (0.35f + bass * 0.3f).coerceIn(0f, 1f)), Color.Transparent), c, r * 1.05f), r * 1.05f, c)
    drawCircle(accent.copy(alpha = 0.8f), r * 0.98f, c, style = Stroke(3f))
}

private fun DrawScope.drawWave(w: FloatArray, accent: Color, bass: Float) {
    val cy = size.height / 2
    val amp = size.height * 0.45f
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
    val r = m * (0.25f + bass * 0.2f)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), accent, Color.Transparent), c, r), r, c)
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
        drawCircle(col.copy(alpha = (0.25f + v * 0.75f).coerceIn(0f, 1f)), r, c, style = Stroke(width = 2f + v * 14f))
        val ang = t * (0.6f + f) * if (i % 2 == 0) 1 else -1
        drawCircle(Color.White.copy(alpha = (0.4f + v * 0.6f).coerceIn(0f, 1f)), 3f + v * 5f, Offset(c.x + cos(ang) * r, c.y + sin(ang) * r))
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
        drawPath(path, Brush.radialGradient(listOf(col.copy(alpha = 0.7f), col.copy(alpha = 0.15f)), c, m), alpha = (0.55f + bass * 0.3f).coerceIn(0f, 1f))
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
            path.lineTo(f * size.width, h - (0.08f + sample(b, (f + shift) % 1f) * (0.85f - shift)) * h)
        }
        path.lineTo(size.width, h); path.close()
        drawPath(path, Brush.verticalGradient(listOf(col.copy(alpha = alpha), Color.Transparent)))
    }
}

private fun DrawScope.drawGalaxy(stars: List<Star>, b: FloatArray, accent: Color, bass: Float, t: Float) {
    val c = center
    val m = min(size.width, size.height) / 2
    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = (0.6f + bass * 0.4f).coerceIn(0f, 1f)), Pink.copy(alpha = 0.15f), Color.Transparent), c, m * 0.6f), m * 0.6f, c)
    for (s in stars) {
        val v = b[s.band]
        val ang = s.a + t * s.speed * (0.25f + bass * 1.5f) / (s.r + 0.2f)
        val r = s.r * m * (1f + v * 0.25f)
        val col = lerp(Color.White, lerp(accent, Cyan, s.r), s.r)
        drawCircle(col.copy(alpha = (0.35f + v * 0.65f).coerceIn(0f, 1f)), s.size * (1f + v * 1.6f), Offset(c.x + cos(ang) * r, c.y + sin(ang) * r * 0.62f))
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
            val rf = y / (rows - 1f)
            val col = when { rf > 0.8f -> Pink; rf > 0.55f -> accent; else -> Cyan }
            val a = if (y < lvl || y == pk) 1f else 0.07f
            drawRoundRect(col.copy(alpha = a), Offset(x * cw + cw * 0.12f, size.height - (y + 1) * ch + ch * 0.12f), Size(cw * 0.76f, ch * 0.76f), CornerRadius(3f))
        }
    }
}

private fun DrawScope.drawParticlesSimple(st: VisState, accent: Color) {
    val c = center
    val m = min(size.width, size.height) / 2
    for (p in st.particles) {
        val col = lerp(lerp(accent, Cyan, p.hue), lerp(Pink, Color(0xFFFFD34D), p.hue), 1 - p.life)
        val pos = Offset(c.x + p.x * m, c.y + p.y * m)
        drawCircle(col.copy(alpha = (p.life * 0.25f).coerceIn(0f, 1f)), p.size * 3f, pos)
        drawCircle(col.copy(alpha = p.life.coerceIn(0f, 1f)), p.size, pos)
    }
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.12f + st.bass * 0.2f))), Offset(0f, size.height * 0.75f), Size(size.width, size.height * 0.25f))
}

// ---------------- yeni 14 stil ----------------
private const val F_LO = 30f
private const val F_HI = 16000f

private fun DrawScope.drawAnalyzer(st: VisState, accent: Color, p: Paint) {
    val W = size.width; val H = size.height
    val top = H * 0.06f; val bottom = H * 0.9f; val left = 32 * density
    val ph = bottom - top
    // dB ızgarası
    for (db in listOf(0, -12, -24, -36, -48, -60)) {
        val v = ((db + 66f) / 60f).coerceIn(0f, 1f)
        val y = bottom - v * ph
        drawLine(Color.White.copy(alpha = 0.07f), Offset(left, y), Offset(W, y), 1f)
        text(p, "$db", left - 4 * density, y + 3 * density, 8f, Color.White.copy(alpha = 0.4f), Paint.Align.RIGHT)
    }
    val lnSpan = ln(F_HI / F_LO)
    for (f in listOf(50, 100, 250, 500, 1000, 2000, 4000, 8000, 16000)) {
        val x = left + ln(f / F_LO) / lnSpan * (W - left)
        drawLine(Color.White.copy(alpha = 0.06f), Offset(x, top), Offset(x, bottom), 1f)
        text(p, if (f >= 1000) "${f / 1000}k" else "$f", x, bottom + 12 * density, 8f, Color.White.copy(alpha = 0.45f), Paint.Align.CENTER)
    }
    val n = st.n
    val bw = (W - left) / n
    val area = Path().apply { moveTo(left, bottom) }
    for (i in 0 until n) {
        val v = st.bands[i]
        val x = left + i * bw
        val y = bottom - v * ph
        drawRect(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.15f)), y, bottom), Offset(x + 1, y), Size(max(1f, bw - 2), bottom - y))
        val py = bottom - st.peaks[i] * ph
        drawRect(Color.White, Offset(x + 1, py - 2), Size(max(1f, bw - 2), 2f))
        area.lineTo(x + bw / 2, y)
    }
    area.lineTo(W, bottom); area.close()
    drawPath(area, Cyan.copy(alpha = 0.6f), style = Stroke(1.5f))
    st.raw?.let { s ->
        text(p, "%.1f dBFS".format(s.rmsDb), W - 6 * density, top + 10 * density, 10f, Color.White.copy(alpha = 0.8f), Paint.Align.RIGHT)
    }
}

private fun DrawScope.drawScope(st: VisState, p: Paint) {
    val W = size.width; val H = size.height
    val green = Color(0xFF39FF8A)
    val gs = min(W, H) / 8
    var x = W / 2 % gs
    while (x < W) { drawLine(green.copy(alpha = 0.08f), Offset(x, 0f), Offset(x, H), 1f); x += gs }
    var y = H / 2 % gs
    while (y < H) { drawLine(green.copy(alpha = 0.08f), Offset(0f, y), Offset(W, y), 1f); y += gs }
    drawLine(green.copy(alpha = 0.25f), Offset(0f, H / 2), Offset(W, H / 2), 1f)
    val w = st.raw?.wave ?: return
    var start = 0
    for (i in 1 until w.size / 2) if (w[i - 1] < 0f && w[i] >= 0f) { start = i; break }
    val count = w.size / 2
    val path = Path()
    for (i in 0 until count) {
        val px = i / (count - 1f) * W
        val py = H / 2 - w[(start + i).coerceAtMost(w.size - 1)] * H * 0.45f
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    drawPath(path, green.copy(alpha = 0.2f), style = Stroke(10f, cap = StrokeCap.Round))
    drawPath(path, green, style = Stroke(2.5f, cap = StrokeCap.Round))
    text(p, "CH1  1V/div", 8 * density, 14 * density, 9f, green.copy(alpha = 0.7f))
}

private fun DrawScope.drawVector(st: VisState, accent: Color) {
    val c = center
    val r = min(size.width, size.height) * 0.45f
    drawCircle(Color.White.copy(alpha = 0.08f), r, c, style = Stroke(1f))
    drawCircle(Color.White.copy(alpha = 0.05f), r * 0.5f, c, style = Stroke(1f))
    drawLine(Color.White.copy(alpha = 0.08f), Offset(c.x - r * 0.7f, c.y - r * 0.7f), Offset(c.x + r * 0.7f, c.y + r * 0.7f), 1f)
    drawLine(Color.White.copy(alpha = 0.08f), Offset(c.x + r * 0.7f, c.y - r * 0.7f), Offset(c.x - r * 0.7f, c.y + r * 0.7f), 1f)
    drawLine(Color.White.copy(alpha = 0.12f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1f)
    val s = st.raw ?: return
    val path = Path()
    for (i in s.waveL.indices) {
        val l = s.waveL[i]; val rr = s.waveR[i]
        val x = c.x + (rr - l) * 0.707f * r
        val y = c.y - (l + rr) * 0.707f * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, accent.copy(alpha = 0.25f), style = Stroke(8f))
    drawPath(path, lerp(accent, Color.White, 0.5f), style = Stroke(1.5f))
}

private fun DrawScope.drawVu(st: VisState, accent: Color, p: Paint) {
    val W = size.width; val H = size.height
    val mw = W / 2 - 12 * density
    fun meter(cx: Float, v: Float, label: String) {
        val cy = H * 0.78f
        val r = min(mw * 0.48f, H * 0.6f)
        drawRoundRect(Color(0xFF1A1610), Offset(cx - mw / 2, H * 0.1f), Size(mw, H * 0.8f), CornerRadius(14 * density))
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFF4D6), Color(0xFFE9D6A6))), Offset(cx - mw / 2 + 6 * density, H * 0.1f + 6 * density), Size(mw - 12 * density, H * 0.8f - 12 * density), CornerRadius(10 * density))
        val a0 = -140f; val sweep = 100f
        drawArc(Color(0xFF2B2418), a0, sweep * 0.72f, false, Offset(cx - r, cy - r), Size(2 * r, 2 * r), style = Stroke(2 * density))
        drawArc(Color(0xFFD7263D), a0 + sweep * 0.72f, sweep * 0.28f, false, Offset(cx - r, cy - r), Size(2 * r, 2 * r), style = Stroke(4 * density))
        for (i in 0..10) {
            val ang = Math.toRadians((a0 + sweep * i / 10f).toDouble()).toFloat()
            val r1 = r; val r2 = r - (if (i % 2 == 0) 10 else 6) * density
            drawLine(if (i >= 8) Color(0xFFD7263D) else Color(0xFF2B2418), Offset(cx + cos(ang) * r1, cy + sin(ang) * r1), Offset(cx + cos(ang) * r2, cy + sin(ang) * r2), 1.5f * density)
        }
        text(p, "VU", cx, cy - r * 0.35f, 14f, Color(0xFF2B2418), Paint.Align.CENTER)
        text(p, label, cx - mw / 2 + 14 * density, H * 0.1f + 22 * density, 11f, Color(0xFF2B2418))
        val ang = Math.toRadians((a0 + sweep * v).toDouble()).toFloat()
        drawLine(Color(0xFF111111), Offset(cx, cy), Offset(cx + cos(ang) * r * 1.02f, cy + sin(ang) * r * 1.02f), 2 * density, cap = StrokeCap.Round)
        drawCircle(Color(0xFF111111), 7 * density, Offset(cx, cy))
        drawCircle(if (v > 0.72f) Color(0xFFD7263D) else accent, 3 * density, Offset(cx + mw / 2 - 16 * density, H * 0.1f + 18 * density))
    }
    meter(W * 0.25f + 2 * density, st.vuL, "L")
    meter(W * 0.75f - 2 * density, st.vuR, "R")
}

private fun DrawScope.drawSpectrogram(st: VisState, p: Paint) {
    drawImage(st.specImg, IntOffset.Zero, IntSize(st.specW, st.specH), IntOffset.Zero,
        IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Low)
    for ((label, f) in listOf("16k" to 0.02f, "4k" to 0.25f, "1k" to 0.5f, "250" to 0.72f, "60" to 0.93f)) {
        text(p, label, 6 * density, size.height * f + 4 * density, 8f, Color.White.copy(alpha = 0.6f))
    }
}

private fun DrawScope.drawSunburst(st: VisState, accent: Color) {
    val c = center
    val m = min(size.width, size.height) / 2
    val rays = 72
    rotate(st.time * 8f) {
        for (i in 0 until rays) {
            val f = i / rays.toFloat()
            val mf = if (f < 0.5f) f * 2 else (1 - f) * 2
            val v = sample(st.bands, mf)
            val ang = f * 2 * PI.toFloat()
            val r0 = m * 0.18f
            val r1 = r0 + m * (0.1f + v * 0.75f)
            val hue = (f * 360f + st.time * 30f) % 360f
            val col = Color.hsv(hue, 0.75f, 1f)
            drawLine(Brush.linearGradient(listOf(col.copy(alpha = 0.9f), col.copy(alpha = 0f)), Offset(c.x + cos(ang) * r0, c.y + sin(ang) * r0), Offset(c.x + cos(ang) * r1, c.y + sin(ang) * r1)),
                Offset(c.x + cos(ang) * r0, c.y + sin(ang) * r0), Offset(c.x + cos(ang) * r1, c.y + sin(ang) * r1), strokeWidth = m * 0.035f, cap = StrokeCap.Round)
        }
    }
    val r = m * (0.16f + st.beat * 0.05f)
    drawCircle(Brush.radialGradient(listOf(Color.White, accent, Color.Transparent), c, r * 1.8f), r * 1.8f, c)
}

private fun DrawScope.drawTunnel(st: VisState, accent: Color) {
    val c = center
    val m = max(size.width, size.height) * 0.8f
    val count = 16
    val speed = 0.25f + st.bass * 0.6f
    for (i in 0 until count) {
        val z = ((i / count.toFloat()) + st.time * speed * 0.3f) % 1f
        val s = z * z
        val half = m * s
        val v = sample(st.bands, i / (count - 1f))
        val col = lerp(lerp(accent, Cyan, z), Pink, v)
        rotate(z * 90f + st.time * 10f, c) {
            drawRect(col.copy(alpha = (z * (0.3f + v)).coerceIn(0f, 1f)), Offset(c.x - half, c.y - half), Size(half * 2, half * 2), style = Stroke(2f + z * 6f + v * 6f))
        }
    }
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.3f + st.beat * 0.5f), Color.Transparent), c, m * 0.08f), m * 0.08f, c)
}

private fun DrawScope.drawDna(st: VisState, accent: Color) {
    val W = size.width; val H = size.height; val cy = H / 2
    val steps = 40
    for (i in 0 until steps) {
        val f = i / (steps - 1f)
        val x = W * 0.05f + f * W * 0.9f
        val v = sample(st.bands, f)
        val amp = H * (0.12f + v * 0.25f)
        val ph = f * 4 * PI.toFloat() + st.time * 2f
        val y1 = cy + sin(ph) * amp; val y2 = cy - sin(ph) * amp
        val depth = (cos(ph) + 1) / 2
        drawLine(Color.White.copy(alpha = 0.12f + v * 0.3f), Offset(x, y1), Offset(x, y2), 2f)
        drawCircle(accent.copy(alpha = 0.4f + depth * 0.6f), 3f + depth * 5f + v * 4f, Offset(x, y1))
        drawCircle(Cyan.copy(alpha = 0.4f + (1 - depth) * 0.6f), 3f + (1 - depth) * 5f + v * 4f, Offset(x, y2))
    }
}

private fun DrawScope.drawCrystal(st: VisState, accent: Color) {
    val c = center
    val m = min(size.width, size.height) / 2
    for (layer in 0 until 3) {
        val n = 12
        val path = Path()
        val rot = st.time * (0.3f + layer * 0.2f) * if (layer % 2 == 0) 1 else -1
        for (i in 0..n) {
            val k = i % n
            val v = sample(st.bands, k / n.toFloat() * 0.8f + layer * 0.08f)
            val r = m * (0.25f + layer * 0.15f) * (if (k % 2 == 0) 1f + v * 0.9f else 0.55f + v * 0.3f)
            val a = k / n.toFloat() * 2 * PI.toFloat() + rot
            val x = c.x + cos(a) * r; val y = c.y + sin(a) * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            if (layer == 2 && k % 2 == 0) drawLine(Color.White.copy(alpha = 0.08f), c, Offset(x, y), 1f)
        }
        path.close()
        val col = when (layer) { 0 -> Pink; 1 -> accent; else -> Cyan }
        drawPath(path, col.copy(alpha = 0.12f + st.beat * 0.1f))
        drawPath(path, col, style = Stroke(2f))
    }
}

private fun DrawScope.drawHeart(st: VisState, p: Paint) {
    val W = size.width; val H = size.height
    val green = Color(0xFF39FF8A)
    val gs = 20 * density
    var x = 0f
    while (x < W) { drawLine(green.copy(alpha = 0.06f), Offset(x, 0f), Offset(x, H), 1f); x += gs }
    var y = 0f
    while (y < H) { drawLine(green.copy(alpha = 0.06f), Offset(0f, y), Offset(W, y), 1f); y += gs }
    val n = st.ecg.size
    val base = H * 0.62f; val amp = H * 0.42f
    val path = Path(); var started = false
    for (i in 0 until n) {
        val idx = (st.ecgIdx + i) % n
        if (i > n - 6) { started = false; continue }
        val px = i / (n - 1f) * W
        val py = base - st.ecg[idx] * amp
        if (!started) { path.moveTo(px, py); started = true } else path.lineTo(px, py)
    }
    drawPath(path, green.copy(alpha = 0.2f), style = Stroke(8f, cap = StrokeCap.Round))
    drawPath(path, green, style = Stroke(2.2f, cap = StrokeCap.Round))
    val bpm = st.raw?.bpm ?: 0
    text(p, "♥", W - 70 * density, 30 * density, 18f, Color(0xFFFF2A55).copy(alpha = 0.4f + st.beat * 0.6f))
    text(p, if (bpm > 0) "$bpm" else "--", W - 12 * density, 32 * density, 26f, green, Paint.Align.RIGHT)
    text(p, "BPM", W - 12 * density, 44 * density, 9f, green.copy(alpha = 0.7f), Paint.Align.RIGHT)
    text(p, "II  HR", 10 * density, 18 * density, 10f, green.copy(alpha = 0.8f))
}

private fun DrawScope.drawCity(st: VisState, accent: Color) {
    val W = size.width; val H = size.height
    val count = 20
    val bw = W * 0.9f / count
    val depth = bw * 0.6f
    val ground = H * 0.88f
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.15f)), 0f, ground), Offset(0f, 0f), Size(W, ground))
    for (i in 0 until count) {
        val v = sample(st.bands, i / (count - 1f))
        val h = 6f + v * H * 0.7f
        val x = W * 0.05f + i * bw
        val col = lerp(accent, Cyan, i / (count - 1f))
        drawRect(Brush.verticalGradient(listOf(col, col.copy(alpha = 0.35f)), ground - h, ground), Offset(x, ground - h), Size(bw * 0.8f, h))
        val top = Path().apply { moveTo(x, ground - h); lineTo(x + depth * 0.6f, ground - h - depth * 0.5f); lineTo(x + bw * 0.8f + depth * 0.6f, ground - h - depth * 0.5f); lineTo(x + bw * 0.8f, ground - h); close() }
        drawPath(top, lerp(col, Color.White, 0.45f))
        val side = Path().apply { moveTo(x + bw * 0.8f, ground - h); lineTo(x + bw * 0.8f + depth * 0.6f, ground - h - depth * 0.5f); lineTo(x + bw * 0.8f + depth * 0.6f, ground - depth * 0.5f); lineTo(x + bw * 0.8f, ground); close() }
        drawPath(side, lerp(col, Color.Black, 0.45f))
        var wy = ground - h + 6f
        while (wy < ground - 8f) { if (((i * 7 + wy.toInt() / 9) % 3) != 0) drawRect(Color(0xFFFFE9A8).copy(alpha = 0.5f * v + 0.1f), Offset(x + bw * 0.25f, wy), Size(bw * 0.3f, 3f)); wy += 9f }
    }
    drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, ground), Offset(W, ground), 1.5f)
}

private fun DrawScope.drawRingWave(st: VisState, accent: Color) {
    val c = center
    val m = min(size.width, size.height) / 2
    val w = st.wave
    for (layer in 0 until 2) {
        val path = Path()
        val R = m * (0.55f - layer * 0.15f) * (1 + st.beat * 0.06f)
        for (i in 0..w.size) {
            val k = i % w.size
            val a = k / w.size.toFloat() * 2 * PI.toFloat() + st.time * 0.2f * (if (layer == 0) 1 else -1)
            val r = R + w[k] * m * 0.35f * (if (layer == 0) 1 else -1)
            val x = c.x + cos(a) * r; val y = c.y + sin(a) * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val col = if (layer == 0) accent else Cyan
        drawPath(path, col.copy(alpha = 0.2f), style = Stroke(10f))
        drawPath(path, col, style = Stroke(2f))
    }
}

private fun DrawScope.drawRain(st: VisState, accent: Color) {
    val cols = st.rainY.size
    val cw = size.width / cols
    val cell = cw * 0.7f
    for (i in 0 until cols) {
        val head = st.rainY[i] * size.height
        val v = st.bands[(i * st.n / cols).coerceAtMost(st.n - 1)]
        val len = 6 + (v * 14).toInt()
        for (k in 0 until len) {
            val y = head - k * cw
            if (y < -cw || y > size.height) continue
            val a = (1 - k / len.toFloat()) * (0.25f + v * 0.75f)
            val col = if (k == 0) Color.White else lerp(Color(0xFF39FF8A), accent, v)
            drawRoundRect(col.copy(alpha = a.coerceIn(0f, 1f)), Offset(i * cw + (cw - cell) / 2, y), Size(cell, cell * 0.8f), CornerRadius(2f))
        }
    }
}
