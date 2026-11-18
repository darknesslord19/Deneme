package com.pulse.player.player

import androidx.media3.common.C
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Oynatıcının PCM verisini doğrudan dinler ve FFT hesaplar.
 * Mikrofon izni gerektirmez, her cihazda çalışır.
 */
object AudioTap : TeeAudioProcessor.AudioBufferSink {
    const val BANDS = 64
    const val WAVE = 256
    private const val N = 1024
    private const val HOP = 512
    private const val LATENCY_NS = 330_000_000L

    class Snapshot(
        val bands: FloatArray,
        val wave: FloatArray,
        val waveL: FloatArray,
        val waveR: FloatArray,
        val rmsDb: Float,
        val levelL: Float,
        val levelR: Float,
        val domHz: Float,
        val bpm: Int,
        val beatAt: Long,
        val at: Long,
    )

    @Volatile var gain = 1f
    private val empty = Snapshot(FloatArray(BANDS), FloatArray(WAVE), FloatArray(WAVE), FloatArray(WAVE), -90f, 0f, 0f, 0f, 0, 0L, 0L)
    private val queue = ArrayDeque<Snapshot>()
    @Volatile private var latest: Snapshot = empty

    // tee thread durumu
    private var sampleRate = 44100
    private var channels = 2
    private var encoding = C.ENCODING_PCM_16BIT
    private val ringM = FloatArray(N)
    private val ringL = FloatArray(N)
    private val ringR = FloatArray(N)
    private var w = 0
    private var sinceHop = 0
    private val window = FloatArray(N) { (0.5 - 0.5 * cos(2 * PI * it / (N - 1))).toFloat() }
    private val re = FloatArray(N)
    private val im = FloatArray(N)
    private val energyHist = FloatArray(43)
    private var eIdx = 0
    private var lastBeat = 0L
    private val beatIntervals = ArrayDeque<Long>()
    private var bpm = 0
    private var peakL = 0f
    private var peakR = 0f

    /** UI: şu an duyulan sese karşılık gelen anlık görüntü. */
    fun current(): Snapshot {
        val now = System.nanoTime()
        synchronized(queue) {
            while (queue.isNotEmpty() && queue.first().at <= now) latest = queue.removeFirst()
        }
        // sessizlik / duraklama: yavaşça sönsün
        return if (now - latest.at > 400_000_000L) empty else latest
    }

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        sampleRate = sampleRateHz.coerceAtLeast(8000)
        channels = channelCount.coerceAtLeast(1)
        this.encoding = encoding
        synchronized(queue) { queue.clear() }
        sinceHop = 0
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        val b = buffer.duplicate().order(ByteOrder.nativeOrder())
        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_FLOAT -> 4
            C.ENCODING_PCM_16BIT -> 2
            else -> return
        }
        val frameBytes = bytesPerSample * channels
        var pos = b.position()
        val lim = b.limit()
        while (pos + frameBytes <= lim) {
            var l: Float; var r: Float
            if (bytesPerSample == 2) {
                l = b.getShort(pos) / 32768f
                r = if (channels > 1) b.getShort(pos + 2) / 32768f else l
            } else {
                l = b.getFloat(pos)
                r = if (channels > 1) b.getFloat(pos + 4) else l
            }
            ringL[w] = l; ringR[w] = r; ringM[w] = (l + r) * 0.5f
            peakL = max(peakL, abs(l)); peakR = max(peakR, abs(r))
            w = (w + 1) % N
            pos += frameBytes
            if (++sinceHop >= HOP) { sinceHop = 0; analyze() }
        }
    }

    private fun analyze() {
        var sumSq = 0f
        for (i in 0 until N) {
            val s = ringM[(w + i) % N]
            sumSq += s * s
            re[i] = s * window[i] * gain
            im[i] = 0f
        }
        fft(re, im)
        val half = N / 2
        val binHz = sampleRate.toFloat() / N
        val bands = FloatArray(BANDS)
        val lo = 30f; val hi = minOf(16000f, sampleRate / 2f - binHz)
        var domMag = 0f; var domBin = 0
        for (k in 2 until half) {
            val m = sqrt(re[k] * re[k] + im[k] * im[k])
            if (m > domMag) { domMag = m; domBin = k }
        }
        var bassEnergy = 0f
        for (bnd in 0 until BANDS) {
            val f0 = lo * (hi / lo).pow(bnd / BANDS.toFloat())
            val f1 = lo * (hi / lo).pow((bnd + 1) / BANDS.toFloat())
            val k0 = (f0 / binHz).toInt().coerceIn(1, half - 1)
            val k1 = (f1 / binHz).toInt().coerceIn(k0 + 1, half)
            var m = 0f
            for (k in k0 until k1) m = max(m, sqrt(re[k] * re[k] + im[k] * im[k]))
            val db = 20f * log10(m / (N / 4f) + 1e-9f)
            val tilt = 1f + bnd / BANDS.toFloat() * 0.5f
            bands[bnd] = ((db + 66f) / 60f * tilt).coerceIn(0f, 1f)
            if (f1 < 160f) bassEnergy += m * m
        }
        val wave = FloatArray(WAVE); val wl = FloatArray(WAVE); val wr = FloatArray(WAVE)
        val step = N / WAVE
        for (i in 0 until WAVE) {
            val idx = (w + i * step) % N
            wave[i] = (ringM[idx] * gain).coerceIn(-1f, 1f)
            wl[i] = (ringL[idx] * gain).coerceIn(-1f, 1f)
            wr[i] = (ringR[idx] * gain).coerceIn(-1f, 1f)
        }
        val rms = sqrt(sumSq / N)
        val rmsDb = (20f * log10(rms + 1e-9f)).coerceAtLeast(-90f)

        // vuruş (beat) tespiti
        val now = System.nanoTime()
        val avg = energyHist.average().toFloat()
        energyHist[eIdx] = bassEnergy; eIdx = (eIdx + 1) % energyHist.size
        val playAt = now + LATENCY_NS
        if (bassEnergy > avg * 1.45f && bassEnergy > 2f && playAt - lastBeat > 280_000_000L) {
            if (lastBeat > 0) {
                val iv = playAt - lastBeat
                if (iv in 280_000_000L..1_500_000_000L) {
                    beatIntervals.addLast(iv)
                    if (beatIntervals.size > 12) beatIntervals.removeFirst()
                    val sorted = beatIntervals.sorted()
                    var b = 60e9 / sorted[sorted.size / 2]
                    while (b < 70) b *= 2.0
                    while (b > 180) b /= 2.0
                    bpm = b.roundToInt()
                }
            }
            lastBeat = playAt
        }

        val snap = Snapshot(
            bands, wave, wl, wr, rmsDb,
            (peakL * gain).coerceIn(0f, 1f), (peakR * gain).coerceIn(0f, 1f),
            domBin * binHz, bpm, lastBeat, playAt,
        )
        peakL = 0f; peakR = 0f
        synchronized(queue) {
            queue.addLast(snap)
            while (queue.size > 200) queue.removeFirst()
        }
    }

    private fun fft(xr: FloatArray, xi: FloatArray) {
        val n = xr.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = xr[i]; xr[i] = xr[j]; xr[j] = t
                t = xi[i]; xi[i] = xi[j]; xi[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f; var ci = 0f
                for (k in 0 until len / 2) {
                    val ar = xr[i + k + len / 2] * cr - xi[i + k + len / 2] * ci
                    val ai = xr[i + k + len / 2] * ci + xi[i + k + len / 2] * cr
                    xr[i + k + len / 2] = xr[i + k] - ar; xi[i + k + len / 2] = xi[i + k] - ai
                    xr[i + k] += ar; xi[i + k] += ai
                    val ncr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }

    private val NOTES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    fun noteName(hz: Float): String {
        if (hz < 20f) return "—"
        val n = (12 * log2(hz / 440f) + 69).roundToInt()
        return NOTES[((n % 12) + 12) % 12] + (n / 12 - 1)
    }
}
