package com.pulse.player.ui

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import com.pulse.player.lyrics.LrcLine
import com.pulse.player.player.MusicController
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

enum class WordStyle(val label: String) {
    CONSTELLATION("Takımyıldız"), HEARTLINE("Nabız Çizgisi"), STARDUST("Yıldız Tozu"), ORBIT("Yörünge"),
    INK("Mürekkep Kalemi"), WATER("Su Damlası"), SONAR("Sonar Nabzı"), COMET("Kuyruklu Yıldız"),
    NEON("Neon Tabela"), DEPTH("Derinlik"), SPOTLIGHT("Spot Işığı"), GLITCH("Dijital Bozulma"),
    SINE("Sinüs Yolu"), SPIRAL("Spiral Galaksi"), RAIN("Harf Yağmuru"), HOLOGRAM("Hologram"),
    EMBER("Kıvılcım"), FLIP("Kart Çevirme"), KINETIC("Kinetik Yığın"), AURORA("Aurora"),
}

class WState(
    val words: List<String>,
    val k: Int,
    val wp: Float,
    val lt: Float,
    val active: Int,
    val fade: Float,
    val line: Int,
    val time: Float,
) {
    val n get() = words.size
}

private val splitter = Regex("\\s+")

fun computeWState(lines: List<LrcLine>, posMs: Long, time: Float): WState? {
    val idx = lines.indexOfLast { it.time in 0..posMs }
    if (idx < 0) return null
    val line = lines[idx]
    val words = line.text.split(splitter).filter { it.isNotBlank() }.ifEmpty { listOf("♪") }
    val nextT = lines.getOrNull(idx + 1)?.time ?: (line.time + 5000)
    val dur = ((nextT - line.time) / 1000f).coerceIn(0.4f, 12f)
    val lt = (posMs - line.time) / 1000f
    val usable = min(dur * 0.82f, words.size * 0.9f + 0.2f)
    val weights = words.map { it.length + 2f }
    val tot = weights.sum()
    val starts = FloatArray(words.size)
    var acc = 0f
    for (i in words.indices) { starts[i] = acc / tot * usable; acc += weights[i] }
    var k = 0
    for (i in words.indices) if (starts[i] <= lt) k = i
    val wp = lt - starts[k]
    val active = if (lt - usable < 0.6f) k else -1
    val fade = if (dur > 1.4f) ((lt - (dur - 0.45f)) / 0.45f).coerceIn(0f, 1f) else 0f
    return WState(words, k, wp, lt, active, fade, idx, time)
}

enum class Face { BOLD, REG, ITALIC, CURSIVE, MONO, SANS }

class TextKit(private val density: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val faces = mapOf(
        Face.BOLD to Typeface.create(Typeface.SERIF, Typeface.BOLD),
        Face.REG to Typeface.create(Typeface.SERIF, Typeface.NORMAL),
        Face.ITALIC to Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC),
        Face.CURSIVE to Typeface.create("cursive", Typeface.BOLD),
        Face.MONO to Typeface.create(Typeface.MONOSPACE, Typeface.BOLD),
        Face.SANS to Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
    )

    fun set(
        sizeDp: Float, face: Face = Face.BOLD, color: Color = Color.White, alpha: Float = 1f,
        glow: Float = 0f, glowColor: Color = Color.White, blur: Float = 0f,
    ) {
        val a = alpha.coerceIn(0f, 1f)
        paint.typeface = faces[face]
        paint.textSize = max(1f, sizeDp * density)
        paint.color = color.copy(alpha = (color.alpha * a).coerceIn(0f, 1f)).toArgb()
        if (glow > 0.1f && a > 0.01f) paint.setShadowLayer(glow * density, 0f, 0f, glowColor.copy(alpha = (glowColor.alpha * a).coerceIn(0f, 1f)).toArgb())
        else paint.clearShadowLayer()
        paint.maskFilter = if (blur > 0.3f) BlurMaskFilter(blur * density, BlurMaskFilter.Blur.NORMAL) else null
    }

    fun w(s: String) = paint.measureText(s)

    fun draw(c: android.graphics.Canvas, s: String, x: Float, y: Float, align: Paint.Align = Paint.Align.CENTER) {
        paint.textAlign = align
        val fm = paint.fontMetrics
        c.drawText(s, x, y - (fm.ascent + fm.descent) / 2f, paint)
    }
}

/** Kelime kelime söz görünümü. */
@Composable
fun WordLyricsView(lines: List<LrcLine>, offsetMs: Long, accent: Color, style: WordStyle, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val tk = remember(density) { TextKit(density) }
    var frame by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var start = 0L
        while (true) withFrameNanos { if (start == 0L) start = it; frame = (it - start) / 1e9f }
    }
    Canvas(modifier) {
        val t = frame
        val pos = runCatching { MusicController.player.currentPosition }.getOrDefault(0L) + offsetMs
        val st = computeWState(lines, pos, t)
        if (st == null) {
            dust(5, t)
            tk.set(22f, Face.REG, alpha = 0.35f + 0.25f * sin(t * 3f))
            tk.draw(drawContext.canvas.nativeCanvas, "♪", size.width / 2, size.height / 2)
            return@Canvas
        }
        when (style) {
            WordStyle.CONSTELLATION -> fxConstellation(st, tk)
            WordStyle.HEARTLINE -> fxHeartline(st, tk)
            WordStyle.STARDUST -> fxStardust(st, tk)
            WordStyle.ORBIT -> fxOrbit(st, tk)
            WordStyle.INK -> fxInk(st, tk)
            WordStyle.WATER -> fxWater(st, tk)
            WordStyle.SONAR -> fxSonar(st, tk)
            WordStyle.COMET -> fxComet(st, tk)
            WordStyle.NEON -> fxNeon(st, tk, accent)
            WordStyle.DEPTH -> fxDepth(st, tk)
            WordStyle.SPOTLIGHT -> fxSpotlight(st, tk, accent)
            WordStyle.GLITCH -> fxGlitch(st, tk)
            WordStyle.SINE -> fxSine(st, tk, accent)
            WordStyle.SPIRAL -> fxSpiral(st, tk)
            WordStyle.RAIN -> fxRain(st, tk)
            WordStyle.HOLOGRAM -> fxHologram(st, tk)
            WordStyle.EMBER -> fxEmber(st, tk)
            WordStyle.FLIP -> fxFlip(st, tk, accent)
            WordStyle.KINETIC -> fxKinetic(st, tk, accent)
            WordStyle.AURORA -> fxAurora(st, tk, accent)
        }
    }
}

// ---------------- yardımcılar ----------------
private fun ease(x: Float): Float = 1f - (1f - x.coerceIn(0f, 1f)).pow(3)
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun hh(i: Int, s: Int = 0): Float { val x = sin(i * 12.9898f + s * 78.233f) * 43758.547f; return x - floor(x) }
private fun DrawScope.dp(v: Float) = v * density
private val DrawScope.nc get() = drawContext.canvas.nativeCanvas

private fun DrawScope.dust(seed: Int, t: Float) {
    for (i in 0 until 60) {
        val a = 0.07f + 0.17f * (0.5f + 0.5f * sin(t * 1.3f + hh(i, seed + 2) * 6f))
        drawCircle(Color.White.copy(alpha = a), dp(0.5f) + hh(i, seed + 3) * dp(0.6f), Offset(hh(i, seed) * size.width, hh(i, seed + 1) * size.height))
    }
}

private fun DrawScope.sparkle(x: Float, y: Float, r: Float, a: Float, col: Color = Color.White) {
    if (a <= 0.01f) return
    val c = Offset(x, y)
    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.55f * a), Color.Transparent), c, r * 2.4f), r * 2.4f, c)
    val q = r * 0.16f
    val p = Path().apply {
        moveTo(x, y - r)
        quadraticBezierTo(x + q, y - q, x + r, y)
        quadraticBezierTo(x + q, y + q, x, y + r)
        quadraticBezierTo(x - q, y + q, x - r, y)
        quadraticBezierTo(x - q, y - q, x, y - r)
        close()
    }
    drawPath(p, col.copy(alpha = a.coerceIn(0f, 1f)))
}

private fun DrawScope.bigWord(
    tk: TextKit, word: String, x: Float, y: Float, sizeDp: Float, alpha: Float,
    face: Face = Face.BOLD, color: Color = Color.White, glow: Float = 0f, glowColor: Color = Color.White, blur: Float = 0f,
): Float {
    tk.set(sizeDp, face, color, alpha, glow, glowColor, blur)
    var w = tk.w(word)
    val maxW = size.width * 0.9f
    if (w > maxW) { tk.paint.textSize *= maxW / w; w = maxW }
    val pad = dp(10f)
    val lo = w / 2 + pad; val hi = size.width - w / 2 - pad
    val cx = if (lo >= hi) size.width / 2 else x.coerceIn(lo, hi)
    tk.draw(nc, word, cx, y)
    return cx
}

private fun DrawScope.label(tk: TextKit, word: String, x: Float, y: Float, alpha: Float, sizeDp: Float = 10f, align: Paint.Align = Paint.Align.CENTER, color: Color = Color.White) {
    tk.set(sizeDp, Face.REG, color, alpha)
    tk.draw(nc, word, x, y, align)
}

private fun DrawScope.constPts(s: WState, seed: Int): List<Offset> {
    val r = Random(seed + s.line * 977)
    val W = size.width; val H = size.height
    var x = W * (0.12f + r.nextFloat() * 0.12f)
    var y = H * (0.32f + r.nextFloat() * 0.36f)
    var dir = 1
    return List(s.n) {
        val p = Offset(x, y)
        x += W * (0.15f + r.nextFloat() * 0.08f) * dir
        if (x > W * 0.86f) { x = W * 0.86f - (x - W * 0.86f); dir = -1 }
        if (x < W * 0.14f) { x = W * 0.14f + (W * 0.14f - x); dir = 1 }
        y = (y + (r.nextFloat() - 0.5f) * H * 0.18f).coerceIn(H * 0.22f, H * 0.78f)
        p
    }
}

private class FW(val w: String, val x: Float, val y: Float, val ww: Float, val sizeDp: Float)

private fun DrawScope.flow(tk: TextKit, words: List<String>, y0: Float, sizeDp0: Float, face: Face): List<FW> {
    val maxW = size.width * 0.84f
    tk.set(sizeDp0, face)
    var sizeDp = sizeDp0
    val longest = words.maxOf { tk.w(it) }
    if (longest > maxW) sizeDp *= maxW / longest
    tk.set(sizeDp, face)
    val sp = tk.w(" ")
    val rows = mutableListOf(mutableListOf<Pair<String, Float>>())
    var rw = 0f
    for (w in words) {
        val ww = tk.w(w)
        if (rw + ww > maxW && rows.last().isNotEmpty()) { rows += mutableListOf<Pair<String, Float>>(); rw = 0f }
        rows.last() += w to ww
        rw += ww + sp
    }
    val lh = sizeDp * density * 1.3f
    val top = y0 - (rows.size - 1) * lh / 2
    val out = ArrayList<FW>()
    rows.forEachIndexed { ri, row ->
        val tw = row.sumOf { it.second.toDouble() }.toFloat() + sp * (row.size - 1)
        var x = (size.width - tw) / 2
        for ((w, ww) in row) { out += FW(w, x, top + ri * lh, ww, sizeDp); x += ww + sp }
    }
    return out
}

// ---------------- 1. Takımyıldız ----------------
private fun DrawScope.fxConstellation(s: WState, tk: TextKit) {
    dust(11, s.time)
    val P = constPts(s, 1); val A = 1 - s.fade
    for (i in 1..s.k) {
        val a = P[i - 1]; val b = P[i]; val g = if (i == s.k) ease(s.wp / 0.3f) else 1f
        drawLine(Color.White.copy(alpha = 0.55f * A), a, Offset(lerp(a.x, b.x, g), lerp(a.y, b.y, g)), dp(0.8f))
    }
    for (i in 0..s.k) {
        val p = P[i]
        drawCircle(Color.White.copy(alpha = 0.9f * A), dp(1.6f), p)
        if (i == s.active) {
            val e = ease(s.wp / 0.25f)
            sparkle(p.x, p.y, dp(8f) + sin(s.time * 6f) * dp(1.2f), A * e)
            bigWord(tk, s.words[i], p.x, p.y - dp(22f), lerp(20f, 27f, e), A * e)
        } else {
            val sh = if (i == s.k - 1 && s.active == s.k) 1 - ease(s.wp / 0.3f) else 0f
            label(tk, s.words[i], p.x + dp(4f), p.y + lerp(dp(10f), -dp(18f), sh), (0.55f + sh * 0.45f) * A, lerp(10f, 22f, sh), Paint.Align.LEFT)
        }
    }
}

// ---------------- 2. Nabız Çizgisi ----------------
private fun qrs(d: Float) = exp(-(d / 0.33f).pow(2)) - 0.32f * exp(-((d + 0.85f) / 0.28f).pow(2)) -
    0.4f * exp(-((d - 0.8f) / 0.3f).pow(2)) + 0.14f * exp(-((d - 2.6f) / 0.9f).pow(2))

private fun DrawScope.fxHeartline(s: WState, tk: TextKit) {
    dust(22, s.time)
    val W = size.width; val H = size.height; val A = 1 - s.fade
    val pink = Color(0xFFFF6B86)
    val y0 = H * 0.58f; val amp = H * 0.13f
    val xs = FloatArray(s.n) { W * 0.12f + it * (W * 0.76f / max(1, s.n - 1)) }
    val head = lerp(if (s.k > 0) xs[s.k - 1] else W * 0.04f, xs[s.k], ease(s.wp / 0.35f))
    val path = Path()
    var px = W * 0.04f; var first = true; var hy = y0
    while (px <= head) {
        var y = y0
        for (j in 0..s.k) y -= qrs((px - xs[j]) / dp(7f)) * amp
        if (first) { path.moveTo(px, y); first = false } else path.lineTo(px, y)
        hy = y; px += dp(1.2f)
    }
    drawPath(path, pink.copy(alpha = 0.22f * A), style = Stroke(dp(5f), cap = StrokeCap.Round))
    drawPath(path, pink.copy(alpha = 0.9f * A), style = Stroke(dp(1.3f), cap = StrokeCap.Round))
    drawCircle(Color.White.copy(alpha = A), dp(2.4f), Offset(head, hy))
    for (i in 0..s.k) {
        if (i == s.k && s.wp < 0.3f) continue
        val x = xs[i]; val y = y0 - amp
        if (i == s.active) {
            val e = ease((s.wp - 0.3f) / 0.25f)
            sparkle(x, y, dp(7f) + sin(s.time * 8f) * dp(1.5f), A * e, Color(0xFFFFB0BE))
            bigWord(tk, s.words[i], x, y - dp(24f), 27f, e * A, glow = 10f, glowColor = pink)
        } else {
            label(tk, s.words[i], x, y - dp(10f), 0.6f * A, color = Color(0xFFFFDCE2))
            drawCircle(Color.White.copy(alpha = A), dp(1.4f), Offset(x, y))
        }
    }
}

// ---------------- 3. Yıldız Tozu ----------------
private fun DrawScope.fxStardust(s: WState, tk: TextKit) {
    dust(33, s.time)
    val lay = flow(tk, s.words, size.height * 0.5f, 26f, Face.BOLD)
    lay.forEachIndexed { i, o ->
        if (i > s.k) return@forEachIndexed
        val r = Random(i * 31 + s.line * 7 + 5)
        val isA = i == s.k
        val e = if (isA) ease(s.wp / 0.5f) else 1f
        var x = o.x
        for (ch in o.w) {
            val str = ch.toString()
            tk.set(o.sizeDp, Face.BOLD, alpha = e * (1 - s.fade) * (if (isA) 1f else 0.62f), glow = if (isA) 8f else 0f)
            val cw = tk.w(str)
            val dx = (r.nextFloat() - 0.5f) * dp(90f) * (1 - e) + (r.nextFloat() - 0.5f) * dp(40f) * s.fade
            val dy = (r.nextFloat() - 0.5f) * dp(90f) * (1 - e) - s.fade * (dp(30f) + r.nextFloat() * dp(60f))
            tk.draw(nc, str, x + dx, o.y + dy, Paint.Align.LEFT)
            x += cw
        }
        if (s.fade > 0f) for (p in 0 until 26) {
            val px = o.x + r.nextFloat() * o.ww + (r.nextFloat() - 0.5f) * dp(30f) * s.fade
            val py = o.y + (r.nextFloat() - 0.5f) * dp(18f) - s.fade * (dp(20f) + r.nextFloat() * dp(80f))
            drawCircle(Color.White.copy(alpha = (1 - s.fade) * 0.8f * min(1f, s.fade * 3f)), dp(0.7f), Offset(px, py))
        }
        if (isA && s.wp < 0.6f) for (p in 0 until 16) {
            drawCircle(Color.White.copy(alpha = (1 - s.wp / 0.6f) * 0.7f), dp(0.7f),
                Offset(o.x + r.nextFloat() * o.ww + (r.nextFloat() - 0.5f) * dp(60f) * (1 - e), o.y + (r.nextFloat() - 0.5f) * dp(60f) * (1 - e)))
        }
    }
}

// ---------------- 4. Yörünge ----------------
private fun DrawScope.fxOrbit(s: WState, tk: TextKit) {
    dust(44, s.time)
    val A = 1 - s.fade
    val cx = size.width / 2; val cy = size.height / 2; val R = min(size.width, size.height) * 0.34f
    val rot = s.time * 0.12f
    drawCircle(Color.White.copy(alpha = 0.08f * A), R, Offset(cx, cy), style = Stroke(dp(0.8f)))
    fun ang(i: Int) = (-PI / 2).toFloat() + rot + i * (2 * PI.toFloat() / max(s.n, 5))
    val pulse = exp(-s.wp * 5f)
    val gr = dp(30f) + pulse * dp(25f)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f * A), Color(0x40C8C8FF), Color.Transparent), Offset(cx, cy), gr), gr, Offset(cx, cy))
    if (s.k > 0) {
        val a0 = ang(0); val a1 = ang(s.k - 1) + (ang(s.k) - ang(s.k - 1)) * ease(s.wp / 0.35f)
        drawArc(Color.White.copy(alpha = 0.55f * A), Math.toDegrees(a0.toDouble()).toFloat(), Math.toDegrees((a1 - a0).toDouble()).toFloat(), false,
            Offset(cx - R, cy - R), Size(2 * R, 2 * R), style = Stroke(dp(1.1f)))
    }
    for (i in 0..s.k) {
        val a = ang(i); val x = cx + cos(a) * R; val y = cy + sin(a) * R
        if (i == s.active) {
            val e = ease(s.wp / 0.25f)
            sparkle(x, y, dp(7f), A * e)
            bigWord(tk, s.words[i], x, y + if (sin(a) > 0) dp(24f) else -dp(22f), 25f, e * A)
        } else {
            drawCircle(Color.White.copy(alpha = A), dp(1.6f), Offset(x, y))
            label(tk, s.words[i], x + cos(a) * dp(18f), y + sin(a) * dp(14f), 0.55f * A)
        }
    }
}

// ---------------- 5. Mürekkep Kalemi ----------------
private fun DrawScope.fxInk(s: WState, tk: TextKit) {
    dust(55, s.time)
    val lay = flow(tk, s.words, size.height * 0.5f - s.fade * dp(40f), 28f, Face.ITALIC)
    val ink = Color(0xFFFFECC8)
    lay.forEachIndexed { i, o ->
        if (i > s.k) return@forEachIndexed
        val isA = i == s.k
        val e = if (isA) min(1f, s.wp / 0.5f) else 1f
        tk.set(o.sizeDp, Face.ITALIC, alpha = (if (isA) 1f else 0.55f) * (1 - s.fade), glow = if (isA) 6f else 0f, glowColor = ink)
        nc.save()
        nc.clipRect(o.x - dp(4f), o.y - dp(40f), o.x + o.ww * e + dp(2f), o.y + dp(40f))
        tk.draw(nc, o.w, o.x, o.y, Paint.Align.LEFT)
        nc.restore()
        if (isA && e < 1f) {
            val px = o.x + o.ww * e
            sparkle(px, o.y + sin(s.wp * 40f) * dp(5f), dp(5f), 1 - s.fade, ink)
            drawLine(ink.copy(alpha = 0.35f), Offset(o.x, o.y + dp(16f)), Offset(px, o.y + dp(16f)), dp(0.8f))
        }
    }
}

// ---------------- 6. Su Damlası ----------------
private fun DrawScope.fxWater(s: WState, tk: TextKit) {
    dust(66, s.time)
    val W = size.width; val H = size.height; val A = 1 - s.fade
    val wy = H * 0.6f
    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.25f * A), Color.Transparent)), Offset(0f, wy), Size(W, dp(1f)))
    tk.set(21f, Face.REG)
    val sp = tk.w(" ")
    val ws = s.words.map { tk.w(it) }
    val tot = ws.sum() + sp * (s.n - 1)
    val sc = min(1f, W * 0.9f / max(1f, tot))
    var x = (W - tot * sc) / 2
    for (i in 0 until s.n) {
        val ww = ws[i] * sc
        if (i <= s.k) {
            val isA = i == s.k
            val fall = if (isA) ease(s.wp / 0.32f).let { it * it } else 1f
            val bob = if (isA) 0f else sin(s.time * 2f + i) * dp(1.5f)
            val y = lerp(H * 0.14f, wy - dp(13f), fall) + bob
            val sz = (if (isA && s.active == i) 24f else 20f) * sc
            tk.set(sz, if (isA) Face.BOLD else Face.REG, alpha = (if (isA) 1f else 0.62f) * A)
            tk.draw(nc, s.words[i], x, y, Paint.Align.LEFT)
            nc.save(); nc.scale(1f, -1f, 0f, wy)
            tk.set(sz, Face.REG, Color(0xFFB4C8FF), 0.14f * A)
            tk.draw(nc, s.words[i], x + sin(s.time * 3f + i) * dp(2f), y - dp(4f), Paint.Align.LEFT)
            nc.restore()
            val age = s.wp - 0.32f + (s.k - i) * 0.6f
            if (age in 0f..1.4f) {
                val r = age * dp(55f); val cxw = x + ww / 2
                val col = Color.White.copy(alpha = (1 - age / 1.4f) * 0.6f * A)
                drawOval(col, Offset(cxw - r, wy + dp(2f) - r * 0.18f), Size(2 * r, r * 0.36f), style = Stroke(dp(1f)))
                drawOval(col, Offset(cxw - r * 0.6f, wy + dp(2f) - r * 0.11f), Size(1.2f * r, r * 0.22f), style = Stroke(dp(1f)))
            }
            if (isA && fall < 1f) sparkle(x + ww / 2, y + dp(8f), dp(4f), A, Color(0xFFC8DCFF))
        }
        x += ww + sp * sc
    }
}

// ---------------- 7. Sonar Nabzı ----------------
private fun DrawScope.fxSonar(s: WState, tk: TextKit) {
    dust(77, s.time)
    val W = size.width; val A = 1 - s.fade
    val c = Offset(W / 2, size.height / 2)
    for (off in floatArrayOf(0f, 0.18f)) {
        val r = (s.wp - off) * W * 0.9f
        if (r > 0) drawCircle(Color.White.copy(alpha = max(0f, 0.45f - (s.wp - off) * 0.5f) * A), r, c, style = Stroke(dp(1f)))
    }
    for (i in 0 until s.k) {
        val r = Random(i * 13 + s.line * 5 + 1)
        val ang = r.nextFloat() * 2 * PI.toFloat()
        val age = (s.k - i - 1) + ease(s.wp / 0.4f)
        val d = min(W * 0.42f, dp(40f) + age * dp(38f))
        val p = Offset(c.x + cos(ang) * d, c.y + sin(ang) * d * 1.2f)
        drawLine(Color.White.copy(alpha = 0.12f * A), c, p, dp(0.8f))
        drawCircle(Color.White.copy(alpha = A), dp(1.5f), p)
        label(tk, s.words[i], p.x, p.y - dp(10f), max(0.35f, 0.9f - age * 0.2f) * A, max(10f, 22f - age * 5f))
    }
    if (s.active >= 0) {
        val e = ease(s.wp / 0.2f)
        bigWord(tk, s.words[s.active], c.x, c.y, 30f * (1 + 0.15f * exp(-s.wp * 6f)), e * A, glow = 18f * exp(-s.wp * 4f) + 1f)
    }
}

// ---------------- 8. Kuyruklu Yıldız ----------------
private fun bz(a: Offset, c: Offset, b: Offset, t: Float) = Offset(
    (1 - t) * (1 - t) * a.x + 2 * (1 - t) * t * c.x + t * t * b.x,
    (1 - t) * (1 - t) * a.y + 2 * (1 - t) * t * c.y + t * t * b.y,
)

private fun DrawScope.fxComet(s: WState, tk: TextKit) {
    dust(88, s.time)
    val P = constPts(s, 8); val A = 1 - s.fade; val H = size.height
    val warm = Color(0xFFFFF0DC)
    fun ctrl(a: Offset, b: Offset) = Offset((a.x + b.x) / 2 - (b.y - a.y) * 0.5f, (a.y + b.y) / 2 - H * 0.12f + (b.x - a.x) * 0.15f)
    for (i in 1 until s.k) {
        val a = P[i - 1]; val b = P[i]; val c = ctrl(a, b)
        val p = Path().apply { moveTo(a.x, a.y); quadraticBezierTo(c.x, c.y, b.x, b.y) }
        drawPath(p, Color.White.copy(alpha = 0.18f * A), style = Stroke(dp(0.8f)))
    }
    val fl = 0.38f
    val tt = min(1f, s.wp / fl)
    val a = if (s.k == 0) Offset(-dp(20f), H * 0.12f) else P[s.k - 1]
    val b = P[s.k]; val c = ctrl(a, b)
    val tailA = if (tt < 1f) 1f else max(0f, 1 - (s.wp - fl) * 4f)
    if (tailA > 0f) for (k in 0 until 26) {
        val t2 = ease(tt) - k * 0.025f
        if (t2 < 0f) break
        drawCircle(warm.copy(alpha = (1 - k / 26f) * 0.8f * A * tailA), max(dp(0.4f), dp(2.6f) * (1 - k / 26f)), bz(a, c, b, min(1f, t2)))
    }
    for (i in 0..s.k) {
        val p = P[i]
        if (i == s.k && tt < 1f) continue
        if (i == s.active) {
            val e = ease((s.wp - fl) / 0.25f); val sc = lerp(1.25f, 1f, e)
            sparkle(p.x, p.y, dp(8f) * sc, A, warm)
            bigWord(tk, s.words[i], p.x, p.y - dp(22f), 26f * sc, e * A)
        } else {
            drawCircle(Color.White.copy(alpha = A), dp(1.5f), p)
            label(tk, s.words[i], p.x, p.y + dp(11f), 0.5f * A)
        }
    }
}

// ---------------- 9. Neon Tabela ----------------
private fun DrawScope.fxNeon(s: WState, tk: TextKit, accent: Color) {
    val P = constPts(s, 9); val A = 1 - s.fade
    val cols = listOf(Color(0xFFFF3D9A), Color(0xFF22D3EE))
    fun flick(i: Int, t: Float): Float {
        if (t > 0.4f) return if (sin(s.time * 37f + i * 7f) > 0.97f) 0.4f else 1f
        return if (hh((t * 28).toInt() + i * 13, 4) > 0.45f) 1f else 0.08f
    }
    for (i in 1..s.k) drawLine(Color.White.copy(alpha = 0.22f * A), P[i - 1], P[i], dp(0.8f),
        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(dp(2f), dp(4f))))
    for (i in 0..s.k) {
        val p = P[i]; val isA = i == s.active
        val f = flick(i, if (i == s.k) s.wp else 1f) * A
        val col = cols[i % 2]
        if (isA) {
            bigWord(tk, s.words[i], p.x, p.y - dp(16f), 32f, f, Face.CURSIVE, Color.White, 14f, col)
            bigWord(tk, s.words[i], p.x, p.y - dp(16f), 32f, f * 0.85f, Face.CURSIVE, col, 22f, col)
        } else {
            tk.set(17f, Face.CURSIVE, col, f * 0.8f, 8f, col)
            tk.draw(nc, s.words[i], p.x, p.y - dp(8f))
        }
        drawCircle(col.copy(alpha = A), dp(1.6f), Offset(p.x, p.y + if (isA) dp(6f) else dp(4f)))
    }
}

// ---------------- 10. Derinlik ----------------
private fun DrawScope.fxDepth(s: WState, tk: TextKit) {
    dust(100, s.time)
    val W = size.width; val H = size.height; val A = 1 - s.fade
    val vx = W / 2; val vy = H * 0.45f
    class It(val i: Int, val ox: Float, val oy: Float, val z: Float)
    val items = (0..s.k).map { i ->
        val r = Random(i * 17 + s.line * 3 + 2)
        val ox = (r.nextFloat() - 0.5f) * W * 0.5f; val oy = (r.nextFloat() - 0.5f) * H * 0.42f
        val z = if (i == s.k) lerp(3.2f, 1f, ease(s.wp / 0.45f)) else 1f + ((s.k - i - 1) + ease(s.wp / 0.45f)) * 0.75f + s.fade * 1.5f
        It(i, if (i == s.k) 0f else ox, if (i == s.k) 0f else oy, z)
    }.sortedByDescending { it.z }
    for (it in items) {
        val sc = 1f / it.z
        val a = min(1f, sc.pow(1.1f)) * A * (if (it.i == s.k) ease(s.wp / 0.2f) else 1f)
        tk.set(36f * sc, Face.BOLD, alpha = a, blur = max(0f, (it.z - 1f) * 1.8f))
        tk.draw(nc, s.words[it.i], vx + it.ox * sc, vy + it.oy * sc)
    }
    tk.paint.maskFilter = null
}

// ---------------- 11. Spot Işığı ----------------
private fun DrawScope.fxSpotlight(s: WState, tk: TextKit, accent: Color) {
    val lay = flow(tk, s.words, size.height * 0.5f, 27f, Face.BOLD)
    fun center(i: Int) = lay[i].let { Offset(it.x + it.ww / 2, it.y) }
    val cur = center(s.k); val prev = if (s.k > 0) center(s.k - 1) else cur
    val e = ease(s.wp / 0.35f)
    val sp = Offset(lerp(prev.x, cur.x, e), lerp(prev.y, cur.y, e))
    val R = size.width * 0.34f
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.11f * (1 - s.fade)), Color.Transparent), sp, R), R, sp)
    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Transparent), 0f, sp.y),
        Offset(sp.x - R * 0.4f, 0f), Size(R * 0.8f, sp.y))
    lay.forEachIndexed { i, o ->
        val c = Offset(o.x + o.ww / 2, o.y)
        val d = hypot(c.x - sp.x, c.y - sp.y)
        var a = 0.06f + 0.94f * exp(-(d / (size.width * 0.2f)).pow(2))
        if (i > s.k) a *= 0.35f
        val isA = i == s.k
        tk.set(o.sizeDp, Face.BOLD, alpha = a * (1 - s.fade), glow = if (isA) 14f else 0f, glowColor = accent)
        tk.draw(nc, o.w, o.x, o.y, Paint.Align.LEFT)
    }
}

// ---------------- 12. Dijital Bozulma ----------------
private fun DrawScope.fxGlitch(s: WState, tk: TextKit) {
    val W = size.width; val H = size.height; val A = 1 - s.fade
    for (y in 0 until (H / dp(3f)).toInt()) drawRect(Color.White.copy(alpha = 0.025f), Offset(0f, y * dp(3f)), Size(W, dp(1f)))
    tk.set(11f, Face.MONO, Color(0xFF7CFFB0), 0.5f * A)
    val start = max(0, s.k - 5)
    for (i in start until s.k) tk.draw(nc, "> ${s.words[i]}", W * 0.1f, H * 0.28f + (i - start) * dp(16f), Paint.Align.LEFT)
    if (s.active < 0) return
    val word = s.words[s.active]
    val g = max(0f, 1 - s.wp / 0.4f) + if (hh((s.time * 9).toInt(), 3) > 0.94f) 0.4f else 0f
    val cy = H * 0.56f
    tk.set(34f, Face.SANS)
    var w = tk.w(word); val maxW = W * 0.88f
    val sz = if (w > maxW) 34f * maxW / w else 34f
    tk.set(sz, Face.SANS); w = tk.w(word)
    val x = (W - w) / 2
    val th = sz * density * 1.4f
    val frameSeed = (s.time * 24).toInt()
    val slices = 7
    for (j in 0 until slices) {
        val top = cy - th / 2 + j * th / slices
        val dx = (hh(j, frameSeed) - 0.5f) * dp(34f) * g
        nc.save(); nc.clipRect(0f, top, W, top + th / slices + 1)
        if (g > 0.05f) {
            tk.set(sz, Face.SANS, Color(0xFFFF2A6D), 0.8f * A); tk.draw(nc, word, x - dx * 0.6f - dp(2f) * g, cy, Paint.Align.LEFT)
            tk.set(sz, Face.SANS, Color(0xFF22D3EE), 0.8f * A); tk.draw(nc, word, x + dx * 0.6f + dp(2f) * g, cy, Paint.Align.LEFT)
        }
        tk.set(sz, Face.SANS, Color.White, A * ease(s.wp / 0.08f)); tk.draw(nc, word, x + dx * 0.2f, cy, Paint.Align.LEFT)
        nc.restore()
    }
    if (g > 0.3f) drawRect(Color.White.copy(alpha = 0.6f * A), Offset(x + hh(frameSeed, 9) * w, cy - th * 0.6f), Size(dp(30f) * g, dp(2f)))
}

// ---------------- 13. Sinüs Yolu ----------------
private fun DrawScope.fxSine(s: WState, tk: TextKit, accent: Color) {
    dust(130, s.time)
    val W = size.width; val H = size.height; val A = 1 - s.fade
    val amp = H * 0.12f; val ph = s.time * 0.6f
    fun yAt(x: Float) = H * 0.52f + amp * sin(x / W * 2 * PI.toFloat() * 1.2f + ph)
    val xs = FloatArray(s.n) { W * 0.1f + it * (W * 0.8f / max(1, s.n - 1)) }
    val head = lerp(if (s.k > 0) xs[s.k - 1] else 0f, xs[s.k], ease(s.wp / 0.3f))
    val faint = Path(); val bright = Path()
    var x = 0f
    while (x <= W) {
        val y = yAt(x)
        if (x == 0f) { faint.moveTo(x, y); bright.moveTo(x, y) } else { faint.lineTo(x, y); if (x <= head) bright.lineTo(x, y) }
        x += dp(3f)
    }
    drawPath(faint, Color.White.copy(alpha = 0.08f * A), style = Stroke(dp(1f)))
    drawPath(bright, Brush.horizontalGradient(listOf(accent.copy(alpha = 0.2f * A), Color.White.copy(alpha = 0.8f * A)), 0f, max(1f, head)), style = Stroke(dp(1.4f)))
    for (i in 0..s.k) {
        val px = xs[i]; val py = yAt(px)
        if (i == s.active) {
            val e = ease(s.wp / 0.25f)
            sparkle(px, py, dp(7f), A * e)
            bigWord(tk, s.words[i], px, py - dp(26f), 26f, e * A, glow = 8f, glowColor = accent)
        } else {
            drawCircle(Color.White.copy(alpha = A), dp(1.5f), Offset(px, py))
            label(tk, s.words[i], px, py + dp(13f), 0.55f * A)
        }
    }
}

// ---------------- 14. Spiral Galaksi ----------------
private fun DrawScope.fxSpiral(s: WState, tk: TextKit) {
    dust(140, s.time)
    val A = 1 - s.fade
    val c = Offset(size.width / 2, size.height / 2)
    val m = min(size.width, size.height)
    val rot = s.time * 0.15f
    val step = m * 0.36f / max(s.n, 4)
    fun pt(th: Float, idx: Float) = (m * 0.06f + idx * step).let { r -> Offset(c.x + cos(th + rot) * r, c.y + sin(th + rot) * r) }
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.5f * A), Color(0x308B5CF6), Color.Transparent), c, m * 0.12f), m * 0.12f, c)
    val path = Path()
    val endIdx = (s.k - 1) + ease(s.wp / 0.35f)
    var f = 0f; var first = true
    while (f <= max(0f, endIdx)) {
        val p = pt(f * 0.95f, f)
        if (first) { path.moveTo(p.x, p.y); first = false } else path.lineTo(p.x, p.y)
        f += 0.05f
    }
    drawPath(path, Color.White.copy(alpha = 0.45f * A), style = Stroke(dp(1f)))
    for (i in 0..s.k) {
        val p = pt(i * 0.95f, i.toFloat())
        if (i == s.active) {
            val e = ease(s.wp / 0.25f)
            sparkle(p.x, p.y, dp(7f), A * e, Color(0xFFDCD0FF))
            bigWord(tk, s.words[i], p.x, p.y - dp(22f), 25f, e * A)
        } else {
            drawCircle(Color.White.copy(alpha = A), dp(1.5f), p)
            label(tk, s.words[i], p.x, p.y + dp(11f), 0.55f * A)
        }
    }
}

// ---------------- 15. Harf Yağmuru ----------------
private fun DrawScope.fxRain(s: WState, tk: TextKit) {
    dust(150, s.time)
    val lay = flow(tk, s.words, size.height * 0.5f, 27f, Face.BOLD)
    lay.forEachIndexed { i, o ->
        if (i > s.k) return@forEachIndexed
        val isA = i == s.k
        tk.set(o.sizeDp, Face.BOLD)
        var x = o.x
        o.w.forEachIndexed { j, ch ->
            val str = ch.toString()
            tk.set(o.sizeDp, Face.BOLD)
            val cw = tk.w(str)
            var y = o.y
            var a = if (isA) 1f else 0.6f
            if (isA) {
                val local = (s.wp - j * 0.045f) / 0.32f
                if (local < 0f) { x += cw; return@forEachIndexed }
                if (local < 1f) {
                    y = lerp(-dp(20f), o.y, local * local)
                    drawLine(Color.White.copy(alpha = 0.25f), Offset(x + cw / 2, y - dp(40f)), Offset(x + cw / 2, y - dp(12f)), dp(1f))
                } else {
                    val b = local - 1f
                    y = o.y - sin(min(1f, b * 2.5f) * PI.toFloat()) * dp(6f) * exp(-b * 4f)
                }
            }
            y += s.fade * s.fade * size.height * 0.6f * (0.6f + hh(i * 10 + j, 2))
            a *= (1 - s.fade)
            tk.set(o.sizeDp, Face.BOLD, alpha = a)
            tk.draw(nc, str, x, y, Paint.Align.LEFT)
            x += cw
        }
    }
}

// ---------------- 16. Hologram ----------------
private fun DrawScope.fxHologram(s: WState, tk: TextKit) {
    val W = size.width; val H = size.height; val A = 1 - s.fade
    val holo = Color(0xFF7DF9FF)
    val base = Offset(W / 2, H * 0.86f)
    drawOval(Brush.radialGradient(listOf(holo.copy(alpha = 0.6f * A), Color.Transparent), base, dp(60f)), Offset(base.x - dp(60f), base.y - dp(8f)), Size(dp(120f), dp(16f)))
    val start = max(0, s.k - 3)
    for (i in start until s.k) label(tk, s.words[i], W / 2, H * 0.24f + (i - start) * dp(18f), 0.35f * A, 12f, color = holo)
    if (s.active < 0) return
    val word = s.words[s.active]
    val cy = H * 0.5f
    val flick = if (hh((s.time * 12).toInt(), 6) > 0.93f) 0.45f else 1f
    val e = ease(s.wp / 0.25f)
    tk.set(32f, Face.SANS)
    var w = min(tk.w(word), W * 0.88f)
    val beam = Path().apply {
        moveTo(base.x - dp(40f), base.y); lineTo(W / 2 - w / 2 - dp(10f), cy + dp(20f))
        lineTo(W / 2 + w / 2 + dp(10f), cy + dp(20f)); lineTo(base.x + dp(40f), base.y); close()
    }
    drawPath(beam, Brush.verticalGradient(listOf(holo.copy(alpha = 0.02f * A), holo.copy(alpha = 0.16f * A)), cy, base.y))
    nc.save(); nc.scale(1f, e, W / 2, cy)
    bigWord(tk, word, W / 2 - dp(1.5f), cy, 32f, 0.5f * A * flick, Face.SANS, Color(0xFFFF4FD8))
    bigWord(tk, word, W / 2 + dp(1.5f), cy, 32f, 0.5f * A * flick, Face.SANS, holo)
    bigWord(tk, word, W / 2, cy, 32f, A * flick, Face.SANS, Color(0xFFE8FFFF), 12f, holo)
    nc.restore()
    val sh = (s.time * dp(30f)) % dp(4f)
    var y = cy - dp(26f) + sh
    while (y < cy + dp(26f)) { drawRect(Color.Black.copy(alpha = 0.35f), Offset(W / 2 - w / 2 - dp(6f), y), Size(w + dp(12f), dp(1.2f))); y += dp(4f) }
}

// ---------------- 17. Kıvılcım ----------------
private fun DrawScope.fxEmber(s: WState, tk: TextKit) {
    val W = size.width; val H = size.height; val A = 1 - s.fade
    val fire = Color(0xFFFF7A2F); val hot = Color(0xFFFFE2B0)
    val cx = W / 2; val cy = H * 0.48f
    drawCircle(Brush.radialGradient(listOf(fire.copy(alpha = 0.18f * A * (0.6f + 0.4f * exp(-s.wp * 2f))), Color.Transparent), Offset(cx, cy), W * 0.45f), W * 0.45f, Offset(cx, cy))
    tk.set(30f, Face.BOLD)
    val ww = if (s.active >= 0) min(tk.w(s.words[s.active]), W * 0.85f) else W * 0.4f
    val L = 1.8f
    val intensity = 0.35f + 0.65f * exp(-s.wp * 1.5f)
    for (i in 0 until 54) {
        val age = (s.time + hh(i, 7) * L) % L
        val x = cx + (hh(i, 8) - 0.5f) * ww + sin(age * 3f + i) * dp(10f)
        val y = cy + dp(6f) - age / L * H * 0.38f
        val a = (1 - age / L) * intensity * A
        drawCircle((if (i % 3 == 0) hot else fire).copy(alpha = a.coerceIn(0f, 1f)), dp(0.8f) + hh(i, 9) * dp(1.6f), Offset(x, y))
    }
    if (s.k > 0) {
        val prev = s.words.subList(0, s.k).joinToString(" ")
        bigWord(tk, prev, cx, H * 0.7f, 13f, 0.45f * A, Face.REG, Color(0xFFFFC9A0))
    }
    if (s.active >= 0) {
        val e = ease(s.wp / 0.3f)
        bigWord(tk, s.words[s.active], cx, cy, 30f + 4f * exp(-s.wp * 5f), e * A, Face.BOLD, Color(0xFFFFF1DC), 18f, fire)
    }
}

// ---------------- 18. Kart Çevirme ----------------
private fun DrawScope.fxFlip(s: WState, tk: TextKit, accent: Color) {
    dust(180, s.time)
    val lay = flow(tk, s.words, size.height * 0.5f - s.fade * dp(30f), 27f, Face.BOLD)
    lay.forEachIndexed { i, o ->
        if (i > s.k) return@forEachIndexed
        val isA = i == s.k
        val e = if (isA) ease(s.wp / 0.45f) else 1f
        val c = cos((1 - e) * PI.toFloat())
        val sy = abs(c)
        val h = o.sizeDp * density * 1.05f
        if (c < 0f) {
            drawRoundRect(accent.copy(alpha = 0.85f * (1 - s.fade)), Offset(o.x - dp(3f), o.y - h / 2 * sy),
                Size(o.ww + dp(6f), max(dp(1f), h * sy)), androidx.compose.ui.geometry.CornerRadius(dp(4f)))
        } else {
            nc.save(); nc.scale(1f, max(0.01f, sy), o.x, o.y)
            tk.set(o.sizeDp, Face.BOLD, alpha = (if (isA) 1f else 0.55f) * (1 - s.fade), glow = if (isA) 10f else 0f, glowColor = accent)
            tk.draw(nc, o.w, o.x, o.y, Paint.Align.LEFT)
            nc.restore()
        }
    }
}

// ---------------- 19. Kinetik Yığın ----------------
private fun DrawScope.fxKinetic(s: WState, tk: TextKit, accent: Color) {
    val W = size.width; val H = size.height; val A = 1 - s.fade
    val x0 = W * 0.09f
    val baseY = H * 0.62f - s.fade * dp(60f)
    val q = 0.74f; val S = dp(46f)
    val shift = ease(s.wp / 0.35f)
    fun off(d: Float) = S * (1 - q.pow(d)) / (1 - q)
    for (i in max(0, s.k - 6)..s.k) {
        val d = if (i == s.k) 0f else (s.k - i) - (1 - shift)
        val sz = 42f * q.pow(d)
        val a = max(0f, 1 - d * 0.2f) * A
        tk.set(sz, Face.SANS, alpha = if (i == s.k) a * shift else a * 0.85f)
        var w = tk.w(s.words[i])
        if (w > W * 0.84f) { tk.paint.textSize *= W * 0.84f / w; w = W * 0.84f }
        val x = if (i == s.k) x0 + (1 - shift) * W * 0.4f else x0
        tk.draw(nc, s.words[i], x, baseY - off(d), Paint.Align.LEFT)
        if (i == s.k) drawRect(accent.copy(alpha = A), Offset(x0, baseY + dp(26f)), Size(w * shift, dp(4f)))
    }
}

// ---------------- 20. Aurora ----------------
private fun DrawScope.fxAurora(s: WState, tk: TextKit, accent: Color) {
    dust(200, s.time)
    val W = size.width; val H = size.height; val A = 1 - s.fade
    val beat = exp(-s.wp * 3f)
    val cols = listOf(Color(0xFF39FF9A), accent, Color(0xFF8B5CF6))
    cols.forEachIndexed { b, col ->
        val top = Path(); val pts = ArrayList<Offset>()
        var x = -dp(10f)
        while (x <= W + dp(10f)) {
            pts += Offset(x, H * (0.22f + b * 0.07f) + sin(x / W * 5f + s.time * (0.4f + b * 0.15f) + b * 2f) * H * 0.05f)
            x += dp(8f)
        }
        val band = H * (0.16f + 0.04f * sin(s.time + b))
        top.moveTo(pts.first().x, pts.first().y)
        pts.forEach { top.lineTo(it.x, it.y) }
        pts.asReversed().forEach { top.lineTo(it.x, it.y + band) }
        top.close()
        drawPath(top, Brush.verticalGradient(listOf(col.copy(alpha = (0.22f + beat * 0.15f) * A), Color.Transparent), H * (0.15f + b * 0.07f), H * (0.45f + b * 0.07f)))
    }
    if (s.k > 0) bigWord(tk, s.words.subList(0, s.k).joinToString(" "), W / 2, H * 0.72f, 13f, 0.5f * A, Face.REG)
    if (s.active >= 0) {
        val e = ease(s.wp / 0.3f)
        bigWord(tk, s.words[s.active], W / 2, H * 0.55f - (1 - e) * dp(12f), 32f, e * A, glow = 16f, glowColor = cols[s.active % 3])
    }
}
