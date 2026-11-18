package com.pulse.player.player

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import com.pulse.player.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object MusicController {
    private var exo: ExoPlayer? = null
    val player: ExoPlayer get() = exo ?: error("MusicController not initialised")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val queue = MutableStateFlow<List<Song>>(emptyList())
    val currentIndex = MutableStateFlow(-1)
    val current = MutableStateFlow<Song?>(null)
    val isPlaying = MutableStateFlow(false)
    val position = MutableStateFlow(0L)
    val duration = MutableStateFlow(0L)
    val shuffle = MutableStateFlow(false)
    val repeat = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val speed = MutableStateFlow(1f)
    val pitch = MutableStateFlow(1f)
    val sleepRemaining = MutableStateFlow<Long?>(null)
    val sleepEndOfTrack = MutableStateFlow(false)
    var audioSessionId = 0
        private set
    private var sleepJob: Job? = null

    fun init(ctx: Context) {
        if (exo != null) return
        val app = ctx.applicationContext
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioSessionId = am.generateAudioSessionId()
        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val renderers = object : DefaultRenderersFactory(app) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(TeeAudioProcessor(AudioTap)))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
        }
        AudioTap.gain = com.pulse.player.data.Prefs.get(app).getFloat("vis_gain", 1f)
        exo = ExoPlayer.Builder(app, renderers)
            .setAudioAttributes(attrs, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build().apply {
                setAudioSessionId(audioSessionId)
                addListener(listener)
            }
        AudioEffects.init(app, audioSessionId)
        scope.launch {
            while (isActive) {
                exo?.let {
                    position.value = it.currentPosition.coerceAtLeast(0)
                    val d = it.duration
                    duration.value = if (d == C.TIME_UNSET || d < 0) 0 else d
                }
                delay(80)
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying.value = playing
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncCurrent()
            if (sleepEndOfTrack.value && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                player.pause()
                sleepEndOfTrack.value = false
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            shuffle.value = shuffleModeEnabled
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            repeat.value = repeatMode
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            speed.value = playbackParameters.speed
            pitch.value = playbackParameters.pitch
        }

        override fun onPlayerError(error: PlaybackException) {
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    private fun syncCurrent() {
        val idx = player.currentMediaItemIndex
        currentIndex.value = idx
        current.value = queue.value.getOrNull(idx)
    }

    fun playQueue(songs: List<Song>, index: Int, shuffled: Boolean = false) {
        if (songs.isEmpty()) return
        queue.value = songs
        val start = if (shuffled) songs.indices.random() else index.coerceIn(0, songs.lastIndex)
        player.shuffleModeEnabled = shuffled
        player.setMediaItems(songs.map { it.toMediaItem() }, start, 0)
        player.prepare()
        player.play()
        syncCurrent()
    }

    fun toggle() {
        val p = player
        if (p.isPlaying) p.pause() else {
            if (p.playbackState == Player.STATE_IDLE) p.prepare()
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(p.currentMediaItemIndex, 0)
            p.play()
        }
    }

    fun next() { if (player.hasNextMediaItem()) player.seekToNextMediaItem() else player.seekTo(0, 0) }
    fun previous() = player.seekToPrevious()
    fun seekTo(ms: Long) = player.seekTo(ms.coerceAtLeast(0))
    fun playAt(index: Int) { player.seekTo(index, 0); player.play() }
    fun toggleShuffle() { player.shuffleModeEnabled = !player.shuffleModeEnabled }
    fun cycleRepeat() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeed(s: Float) { player.playbackParameters = PlaybackParameters(s, player.playbackParameters.pitch) }
    fun setPitch(p: Float) { player.playbackParameters = PlaybackParameters(player.playbackParameters.speed, p) }

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepEndOfTrack.value = false
        player.volume = 1f
        if (minutes <= 0) { sleepRemaining.value = null; return }
        val end = SystemClock.elapsedRealtime() + minutes * 60_000L
        sleepJob = scope.launch {
            while (isActive) {
                val r = end - SystemClock.elapsedRealtime()
                if (r <= 0) {
                    player.pause()
                    player.volume = 1f
                    sleepRemaining.value = null
                    break
                }
                if (r < 15_000) player.volume = (r / 15_000f).coerceIn(0.05f, 1f)
                sleepRemaining.value = r
                delay(500)
            }
        }
    }

    fun sleepAtEndOfTrack() {
        setSleepTimer(0)
        sleepEndOfTrack.value = true
    }
}
