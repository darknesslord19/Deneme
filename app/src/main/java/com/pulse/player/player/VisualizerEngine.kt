package com.pulse.player.player

import android.media.audiofx.Visualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

object VisualizerEngine {
    const val BANDS = 64
    const val WAVE = 256

    @Volatile var fft = FloatArray(BANDS); private set
    @Volatile var wave = FloatArray(WAVE); private set
    val active = MutableStateFlow(false)

    private var vis: Visualizer? = null
    private var users = 0

    fun acquire(sessionId: Int): Boolean {
        users++
        if (vis != null) return true
        return try {
            val v = Visualizer(sessionId)
            v.setEnabled(false)
            val range = Visualizer.getCaptureSizeRange()
            v.setCaptureSize(range[1].coerceAtMost(1024).coerceAtLeast(range[0]))
            v.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)
            v.setDataCaptureListener(listener, Visualizer.getMaxCaptureRate(), true, true)
            v.setEnabled(true)
            vis = v
            active.value = true
            true
        } catch (_: Throwable) {
            active.value = false
            false
        }
    }

    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) {
            runCatching { vis?.setEnabled(false); vis?.release() }
            vis = null
            active.value = false
            fft = FloatArray(BANDS)
        }
    }

    private val listener = object : Visualizer.OnDataCaptureListener {
        override fun onWaveFormDataCapture(v: Visualizer?, bytes: ByteArray?, rate: Int) {
            bytes ?: return
            val out = FloatArray(WAVE)
            val step = max(1, bytes.size / WAVE)
            for (i in 0 until WAVE) {
                val idx = (i * step).coerceAtMost(bytes.size - 1)
                out[i] = ((bytes[idx].toInt() and 0xFF) - 128) / 128f
            }
            wave = out
        }

        override fun onFftDataCapture(v: Visualizer?, bytes: ByteArray?, rate: Int) {
            bytes ?: return
            val n = bytes.size / 2
            if (n < 4) return
            val mags = FloatArray(n)
            mags[0] = kotlin.math.abs(bytes[0].toFloat())
            for (k in 1 until n) {
                val re = bytes[2 * k].toFloat()
                val im = bytes[2 * k + 1].toFloat()
                mags[k] = hypot(re, im)
            }
            val out = FloatArray(BANDS)
            val maxBin = (n - 1).toDouble()
            for (b in 0 until BANDS) {
                val s = (maxBin.pow(b.toDouble() / BANDS)).toInt().coerceAtLeast(1)
                val e = (maxBin.pow((b + 1).toDouble() / BANDS)).toInt().coerceAtLeast(s + 1).coerceAtMost(n)
                var sum = 0f
                for (k in s until e) sum = max(sum, mags[k])
                val db = 20f * log10(sum + 1f)
                // yüksek frekanslara hafif kazanç
                val tilt = 1f + b / BANDS.toFloat() * 0.35f
                out[b] = ((db - 6f) / 36f * tilt).coerceIn(0f, 1f)
            }
            fft = out
        }
    }
}
