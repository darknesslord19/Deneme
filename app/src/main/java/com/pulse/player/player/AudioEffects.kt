package com.pulse.player.player

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import com.pulse.player.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow

object AudioEffects {
    data class State(
        val enabled: Boolean = false,
        val bands: List<Int> = emptyList(),
        val bass: Int = 0,
        val virt: Int = 0,
        val loud: Int = 0,
        val reverb: Int = 0,
        val preset: String = "Düz",
    )

    val state = MutableStateFlow(State())
    val available = MutableStateFlow(false)
    var bandCount = 0; private set
    var minLevel = -1500; private set
    var maxLevel = 1500; private set
    var freqs: List<Int> = emptyList(); private set

    private var eq: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var reverb: PresetReverb? = null
    private var appCtx: Context? = null

    val reverbNames = listOf("Kapalı", "Küçük Oda", "Orta Oda", "Büyük Oda", "Orta Salon", "Büyük Salon", "Plaka")

    // 5 noktalı eğriler (-1..1) -> cihaz bant sayısına enterpole edilir
    val presets: List<Pair<String, FloatArray>> = listOf(
        "Düz" to floatArrayOf(0f, 0f, 0f, 0f, 0f),
        "Bas Canavarı" to floatArrayOf(0.85f, 0.55f, 0.05f, 0f, 0.1f),
        "Rock" to floatArrayOf(0.5f, 0.25f, -0.15f, 0.3f, 0.5f),
        "Pop" to floatArrayOf(-0.1f, 0.3f, 0.5f, 0.3f, -0.1f),
        "Vokal" to floatArrayOf(-0.25f, 0f, 0.5f, 0.4f, 0.1f),
        "Elektronik" to floatArrayOf(0.6f, 0.3f, 0f, 0.3f, 0.55f),
        "Hip-Hop" to floatArrayOf(0.65f, 0.4f, 0f, 0.15f, 0.3f),
        "Klasik" to floatArrayOf(0.4f, 0.2f, -0.1f, 0.2f, 0.4f),
        "Caz" to floatArrayOf(0.3f, 0.15f, -0.1f, 0.15f, 0.35f),
        "Akustik" to floatArrayOf(0.35f, 0.1f, 0.15f, 0.3f, 0.25f),
        "Tiz Güçlendir" to floatArrayOf(-0.1f, 0f, 0.05f, 0.4f, 0.75f),
        "Gece Modu" to floatArrayOf(-0.3f, -0.1f, 0.2f, 0.1f, -0.3f),
    )

    fun init(ctx: Context, sessionId: Int) {
        appCtx = ctx.applicationContext
        releaseAll()
        try {
            val e = Equalizer(0, sessionId)
            eq = e
            bandCount = e.numberOfBands.toInt()
            val r = e.bandLevelRange
            minLevel = r[0].toInt(); maxLevel = r[1].toInt()
            freqs = (0 until bandCount).map { e.getCenterFreq(it.toShort()) / 1000 }
        } catch (_: Throwable) { eq = null; bandCount = 0 }
        bassBoost = runCatching { BassBoost(0, sessionId) }.getOrNull()
        virtualizer = runCatching { Virtualizer(0, sessionId) }.getOrNull()
        loudness = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()
        reverb = runCatching { PresetReverb(0, sessionId) }.getOrNull()
        available.value = eq != null
        state.value = load()
        apply()
    }

    private fun releaseAll() {
        listOf(eq, bassBoost, virtualizer, loudness, reverb).forEach { runCatching { it?.release() } }
        eq = null; bassBoost = null; virtualizer = null; loudness = null; reverb = null
    }

    private fun load(): State {
        val p = Prefs.get(appCtx ?: return State())
        val bands = p.getString("eq_bands", "")!!.split(',').mapNotNull { it.toIntOrNull() }
        return State(
            enabled = p.getBoolean("eq_on", false),
            bands = if (bands.size == bandCount) bands else List(bandCount) { 0 },
            bass = p.getInt("eq_bass", 0),
            virt = p.getInt("eq_virt", 0),
            loud = p.getInt("eq_loud", 0),
            reverb = p.getInt("eq_rev", 0),
            preset = p.getString("eq_preset", "Düz")!!,
        )
    }

    private fun save() {
        val s = state.value
        Prefs.get(appCtx ?: return).edit()
            .putBoolean("eq_on", s.enabled)
            .putString("eq_bands", s.bands.joinToString(","))
            .putInt("eq_bass", s.bass).putInt("eq_virt", s.virt)
            .putInt("eq_loud", s.loud).putInt("eq_rev", s.reverb)
            .putString("eq_preset", s.preset)
            .apply()
    }

    private fun apply() {
        val s = state.value
        runCatching {
            eq?.let { e ->
                s.bands.forEachIndexed { i, l -> e.setBandLevel(i.toShort(), l.coerceIn(minLevel, maxLevel).toShort()) }
                e.setEnabled(s.enabled)
            }
        }
        runCatching {
            bassBoost?.let { b ->
                if (b.strengthSupported) b.setStrength(s.bass.coerceIn(0, 1000).toShort())
                b.setEnabled(s.enabled && s.bass > 0)
            }
        }
        runCatching {
            virtualizer?.let { v ->
                if (v.strengthSupported) v.setStrength(s.virt.coerceIn(0, 1000).toShort())
                v.setEnabled(s.enabled && s.virt > 0)
            }
        }
        runCatching {
            loudness?.let { l ->
                l.setTargetGain(s.loud)
                l.setEnabled(s.enabled && s.loud > 0)
            }
        }
        runCatching {
            reverb?.let { r ->
                r.setPreset(s.reverb.toShort())
                r.setEnabled(s.enabled && s.reverb > 0)
            }
        }
    }

    fun update(transform: (State) -> State) {
        state.value = transform(state.value)
        apply()
        save()
    }

    fun setBand(i: Int, level: Int) = update { s ->
        val b = s.bands.toMutableList()
        if (i in b.indices) b[i] = level
        s.copy(bands = b, preset = "Özel", enabled = true)
    }

    fun applyPreset(name: String) {
        val curve = presets.firstOrNull { it.first == name }?.second ?: return
        val levels = List(bandCount) { i ->
            val t = if (bandCount <= 1) 0f else i.toFloat() / (bandCount - 1) * (curve.size - 1)
            val lo = t.toInt().coerceIn(0, curve.size - 1)
            val hi = (lo + 1).coerceAtMost(curve.size - 1)
            val v = curve[lo] + (curve[hi] - curve[lo]) * (t - lo)
            (if (v >= 0) v * maxLevel else -v * minLevel).toInt()
        }
        update { it.copy(bands = levels, preset = name, enabled = true) }
    }

    fun reset() = update { State(enabled = it.enabled, bands = List(bandCount) { 0 }) }
}
