package com.pulse.player.ui

import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.pulse.player.MainActivity
import com.pulse.player.data.Prefs
import com.pulse.player.data.VideoItem
import com.pulse.player.player.MusicController
import com.pulse.player.ui.theme.Card
import com.pulse.player.ui.theme.Cyan
import com.pulse.player.ui.theme.Neon
import com.pulse.player.ui.theme.Pink
import com.pulse.player.ui.theme.TextDim
import kotlinx.coroutines.delay
import kotlin.math.abs

private enum class Gesture { NONE, SEEK, BRIGHT, VOLUME }

private val resizeModes = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Sığdır",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Yakınlaştır",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Uzat",
    AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to "Genişliğe Sığdır",
)

private val filters: List<Pair<String, () -> List<Effect>>> = listOf(
    "Normal" to { emptyList() },
    "Canlı" to { listOf(HslAdjustment.Builder().adjustSaturation(35f).build(), Contrast(0.15f)) },
    "Sinematik" to { listOf(HslAdjustment.Builder().adjustSaturation(-20f).build(), Contrast(0.25f), Brightness(-0.04f)) },
    "Siyah-Beyaz" to { listOf(RgbFilter.createGrayscaleFilter()) },
    "Negatif" to { listOf(RgbFilter.createInvertedFilter()) },
    "Parlak" to { listOf(Brightness(0.12f)) },
    "Gece" to { listOf(Brightness(-0.12f), HslAdjustment.Builder().adjustSaturation(-15f).build()) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(videos: List<VideoItem>, startIndex: Int, activity: MainActivity, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val sessionId = remember { am.generateAudioSessionId() }
    val player = remember {
        ExoPlayer.Builder(ctx)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000)
            .build().apply {
                setAudioSessionId(sessionId)
                val start = videos.getOrNull(startIndex)
                val resume = start?.let { prefs.getLong("vpos_${it.id}", 0L) } ?: 0L
                setMediaItems(videos.map { it.toMediaItem() }, startIndex, if (resume > 5000 && (start!!.duration == 0L || resume < start.duration - 8000)) resume else 0L)
                prepare()
                playWhenReady = true
            }
    }
    val booster = remember { runCatching { LoudnessEnhancer(sessionId) }.getOrNull() }

    var isPlaying by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var index by remember { mutableIntStateOf(startIndex) }
    var vSize by remember { mutableStateOf(VideoSize.UNKNOWN) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var error by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var resize by remember { mutableIntStateOf(0) }
    var showSheet by remember { mutableStateOf(false) }
    var indicator by remember { mutableStateOf<Triple<ImageVector?, String, Float?>?>(null) }
    var tapFlash by remember { mutableStateOf<Pair<Boolean, Long>?>(null) }
    var landscape by remember { mutableStateOf<Boolean?>(null) }
    var filter by remember { mutableIntStateOf(0) }
    var effectsReady by remember { mutableStateOf(false) }
    var boost by remember { mutableIntStateOf(0) }
    var speed by remember { mutableFloatStateOf(1f) }
    val pip by activity.pipMode

    fun savePos() {
        val v = videos.getOrNull(player.currentMediaItemIndex) ?: return
        prefs.edit().putLong("vpos_${v.id}", player.currentPosition).apply()
    }

    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { isPlaying = p; activity.videoPlaying = p }
            override fun onVideoSizeChanged(s: VideoSize) { vSize = s }
            override fun onTracksChanged(t: Tracks) { tracks = t }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { index = player.currentMediaItemIndex; error = null }
            override fun onPlayerError(e: PlaybackException) { error = "Oynatılamadı: ${e.errorCodeName}" }
        }
        player.addListener(l)
        MusicController.player.pause()
        val window = activity.window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val ic = WindowCompat.getInsetsController(window, window.decorView)
        ic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ic.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            savePos()
            player.removeListener(l)
            player.release()
            runCatching { booster?.release() }
            activity.videoPlaying = false
            activity.videoAspect = null
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            ic.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val lp = window.attributes; lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE; window.attributes = lp
        }
    }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) { player.pause(); savePos() }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.let { if (it == C.TIME_UNSET || it < 0) 0 else it }
            buffered = player.bufferedPosition
            delay(250)
        }
    }
    LaunchedEffect(vSize, landscape) {
        if (vSize.width > 0 && vSize.height > 0) {
            val w = (vSize.width * vSize.pixelWidthHeightRatio).toInt().coerceAtLeast(1)
            activity.videoAspect = Rational(w, vSize.height)
            val land = landscape ?: (w > vSize.height)
            activity.requestedOrientation = if (land) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }
    LaunchedEffect(showControls, isPlaying, locked) {
        if (showControls && isPlaying) { delay(if (locked) 1800 else 3800); showControls = false }
    }
    LaunchedEffect(indicator) { if (indicator != null) { delay(900); indicator = null } }
    LaunchedEffect(tapFlash) { if (tapFlash != null) { delay(650); tapFlash = null } }

    BackHandler {
        if (locked) { showControls = true } else onClose()
    }

    val subPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = uri.toString().lowercase()
            val mime = when {
                name.endsWith(".vtt") -> MimeTypes.TEXT_VTT
                name.endsWith(".ass") || name.endsWith(".ssa") -> MimeTypes.TEXT_SSA
                else -> MimeTypes.APPLICATION_SUBRIP
            }
            val idx = player.currentMediaItemIndex
            val pos = player.currentPosition
            val cur = player.currentMediaItem ?: return@rememberLauncherForActivityResult
            val sub = MediaItem.SubtitleConfiguration.Builder(uri).setMimeType(mime).setLanguage("tr")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).setLabel("Harici altyazı").build()
            val item = cur.buildUpon().setSubtitleConfigurations(listOf(sub)).build()
            player.replaceMediaItem(idx, item)
            player.seekTo(idx, pos)
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        }
    }

    fun applyFilter(i: Int) {
        filter = i
        try {
            val effects = filters[i].second()
            if (!effectsReady) {
                if (effects.isEmpty()) return
                val pos = player.currentPosition
                val idx = player.currentMediaItemIndex
                player.setVideoEffects(effects)
                player.stop()
                player.seekTo(idx, pos)
                player.prepare()
                effectsReady = true
            } else player.setVideoEffects(effects)
        } catch (e: Throwable) {
            error = "Bu cihaz video filtrelerini desteklemiyor"
            filter = 0
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    this.player = player
                    setKeepContentOnPlayerReset(true)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { it.resizeMode = resizeModes[resize].first },
            modifier = Modifier.fillMaxSize()
        )

        if (!pip) {
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(locked) {
                        detectTapGestures(
                            onTap = { showControls = !showControls },
                            onDoubleTap = { o ->
                                if (locked) return@detectTapGestures
                                val fwd = o.x > size.width / 2
                                if (fwd) player.seekForward() else player.seekBack()
                                tapFlash = fwd to System.currentTimeMillis()
                            }
                        )
                    }
                    .pointerInput(locked) {
                        if (locked) return@pointerInput
                        var mode = Gesture.NONE
                        var startX = 0f
                        var acc = Offset2()
                        var seekBase = 0L
                        var seekTarget = 0L
                        var bright = 0.5f
                        var vol = 0f
                        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
                        detectDragGestures(
                            onDragStart = { o ->
                                mode = Gesture.NONE; startX = o.x; acc = Offset2()
                                seekBase = player.currentPosition
                                val b = activity.window.attributes.screenBrightness
                                bright = if (b < 0) 0.5f else b
                                vol = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                            },
                            onDragEnd = {
                                if (mode == Gesture.SEEK) player.seekTo(seekTarget)
                                mode = Gesture.NONE
                            },
                            onDragCancel = { mode = Gesture.NONE },
                            onDrag = { ch, d ->
                                ch.consume()
                                acc = Offset2(acc.x + d.x, acc.y + d.y)
                                if (mode == Gesture.NONE && (abs(acc.x) > 24 || abs(acc.y) > 24)) {
                                    mode = if (abs(acc.x) > abs(acc.y)) Gesture.SEEK else if (startX < size.width / 2) Gesture.BRIGHT else Gesture.VOLUME
                                }
                                when (mode) {
                                    Gesture.SEEK -> {
                                        val dur = player.duration.coerceAtLeast(1)
                                        val delta = (acc.x / size.width * minOf(dur, 180_000L) * 1.2f).toLong()
                                        seekTarget = (seekBase + delta).coerceIn(0, dur)
                                        indicator = Triple(null, "${formatTime(seekTarget)}  (${if (delta >= 0) "+" else "-"}${formatTime(abs(delta))})", seekTarget.toFloat() / dur)
                                    }
                                    Gesture.BRIGHT -> {
                                        bright = (bright - d.y / size.height * 1.3f).coerceIn(0.01f, 1f)
                                        val lp = activity.window.attributes; lp.screenBrightness = bright; activity.window.attributes = lp
                                        indicator = Triple(Icons.Rounded.BrightnessMedium, "Parlaklık ${(bright * 100).toInt()}%", bright)
                                    }
                                    Gesture.VOLUME -> {
                                        vol = (vol - d.y / size.height * maxVol * 1.3f).coerceIn(0f, maxVol)
                                        am.setStreamVolume(AudioManager.STREAM_MUSIC, vol.toInt(), 0)
                                        indicator = Triple(Icons.AutoMirrored.Rounded.VolumeUp, "Ses ${(vol / maxVol * 100).toInt()}%", vol / maxVol)
                                    }
                                    Gesture.NONE -> {}
                                }
                            }
                        )
                    }
            )

            tapFlash?.let { (fwd, _) ->
                Box(Modifier.fillMaxHeight().fillMaxWidth(0.4f).align(if (fwd) Alignment.CenterEnd else Alignment.CenterStart)
                    .background(Brush.horizontalGradient(if (fwd) listOf(Color.Transparent, Color.White.copy(alpha = 0.12f)) else listOf(Color.White.copy(alpha = 0.12f), Color.Transparent))),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(if (fwd) Icons.Rounded.Forward10 else Icons.Rounded.Replay10, null, Modifier.size(44.dp))
                        Text(if (fwd) "+10 sn" else "-10 sn", fontWeight = FontWeight.Bold)
                    }
                }
            }

            indicator?.let { (icon, text, frac) ->
                Column(
                    Modifier.align(Alignment.Center).clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 22.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (icon != null) Icon(icon, null, Modifier.size(30.dp))
                    Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (frac != null) {
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.width(160.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.2f))) {
                            Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().background(Brush.horizontalGradient(listOf(Neon, Cyan))))
                        }
                    }
                }
            }

            error?.let {
                Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Pink.copy(alpha = 0.8f)).padding(12.dp))
            }

            AnimatedVisibility(showControls && locked, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.CenterStart)) {
                IconButton(onClick = { locked = false; showControls = true }, Modifier.safeDrawingPadding().padding(16.dp).size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))) {
                    Icon(Icons.Rounded.Lock, "Kilidi aç", tint = Cyan)
                }
            }

            AnimatedVisibility(showControls && !locked, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.85f))))) {
                    Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Geri") }
                        Column(Modifier.weight(1f)) {
                            Text(videos.getOrNull(index)?.title ?: "", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (vSize.height > 0) Text("${vSize.width}×${vSize.height}" + if (videos.size > 1) "  •  ${index + 1}/${videos.size}" else "", color = TextDim, fontSize = 11.sp)
                        }
                        IconButton(onClick = { showSheet = true }) { Icon(Icons.Rounded.Subtitles, "Altyazı") }
                        IconButton(onClick = { activity.enterPip() }) { Icon(Icons.Rounded.PictureInPictureAlt, "PiP") }
                        IconButton(onClick = { showSheet = true }) { Icon(Icons.Rounded.MoreVert, "Ayarlar") }
                    }

                    Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { player.seekToPreviousMediaItem() }, enabled = player.hasPreviousMediaItem()) { Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(34.dp)) }
                        IconButton(onClick = { player.seekBack() }) { Icon(Icons.Rounded.Replay10, null, Modifier.size(34.dp)) }
                        Box(
                            Modifier.size(76.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Neon, Pink)))
                                .clickable { if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0); player.play() } },
                            contentAlignment = Alignment.Center
                        ) { Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(44.dp)) }
                        IconButton(onClick = { player.seekForward() }) { Icon(Icons.Rounded.Forward10, null, Modifier.size(34.dp)) }
                        IconButton(onClick = { player.seekToNextMediaItem() }, enabled = player.hasNextMediaItem()) { Icon(Icons.Rounded.SkipNext, null, Modifier.size(34.dp)) }
                    }

                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(horizontal = 16.dp)) {
                        var dragging by remember { mutableStateOf(false) }
                        var dragVal by remember { mutableFloatStateOf(0f) }
                        val frac = if (duration > 0) position.toFloat() / duration else 0f
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatTime(if (dragging) (dragVal * duration).toLong() else position), fontSize = 12.sp)
                            Slider(
                                value = if (dragging) dragVal else frac.coerceIn(0f, 1f),
                                onValueChange = { dragging = true; dragVal = it; showControls = true },
                                onValueChangeFinished = { player.seekTo((dragVal * duration).toLong()); dragging = false },
                                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Neon, inactiveTrackColor = Color.White.copy(alpha = 0.25f))
                            )
                            Text(formatTime(duration), fontSize = 12.sp)
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { locked = true; showControls = true }) { Icon(Icons.Rounded.LockOpen, "Kilitle") }
                            TextButton(onClick = { resize = (resize + 1) % resizeModes.size; indicator = Triple(Icons.Rounded.AspectRatio, resizeModes[resize].second, null) }) {
                                Icon(Icons.Rounded.AspectRatio, null, tint = Color.White); Text(" ${resizeModes[resize].second}", color = Color.White, fontSize = 12.sp)
                            }
                            TextButton(onClick = {
                                val list = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                                speed = list[(list.indexOf(speed).coerceAtLeast(0) + 1) % list.size]
                                player.setPlaybackSpeed(speed)
                            }) { Text("${speed}x", color = if (speed != 1f) Cyan else Color.White, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = {
                                val cur = landscape ?: (vSize.width > vSize.height)
                                landscape = !cur
                            }) { Icon(Icons.Rounded.ScreenRotation, "Döndür") }
                        }
                    }
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Card) {
            val chip = FilterChipDefaults.filterChipColors(selectedContainerColor = Neon, selectedLabelColor = Color.White)
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
                Text("Oynatıcı Ayarları", fontSize = 20.sp, fontWeight = FontWeight.Bold)

                SectionTitle("Oynatma Hızı")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f).forEach { s ->
                        FilterChip(speed == s, { speed = s; player.setPlaybackSpeed(s) }, label = { Text("${s}x") }, colors = chip)
                    }
                }

                SectionTitle("Görüntü Oranı")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    resizeModes.forEachIndexed { i, (_, n) -> FilterChip(resize == i, { resize = i }, label = { Text(n) }, colors = chip) }
                }

                SectionTitle("Video Filtresi")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    filters.forEachIndexed { i, (n, _) -> FilterChip(filter == i, { applyFilter(i) }, label = { Text(n) }, colors = chip) }
                }

                SectionTitle("Ses Yükseltme  +${boost / 100} dB")
                Slider(
                    value = boost / 1500f,
                    onValueChange = { v ->
                        boost = (v * 1500).toInt()
                        runCatching { booster?.setTargetGain(boost); booster?.setEnabled(boost > 0) }
                    },
                    enabled = booster != null,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Pink)
                )

                val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                if (audioGroups.size > 1 || audioGroups.any { it.length > 1 }) {
                    SectionTitle("Ses Parçası")
                    audioGroups.forEachIndexed { gi, g ->
                        for (t in 0 until g.length) {
                            val f = g.getTrackFormat(t)
                            val label = listOfNotNull(f.label, f.language?.uppercase(), f.sampleMimeType?.substringAfter('/'), if (f.channelCount > 0) "${f.channelCount}ch" else null).joinToString(" • ").ifEmpty { "Parça ${gi + 1}" }
                            TrackRow(label, g.isTrackSelected(t)) {
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                    .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, t)).build()
                            }
                        }
                    }
                }

                SectionTitle("Altyazı")
                val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                val textDisabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
                TrackRow("Kapalı", textDisabled || textGroups.none { it.isSelected }) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                }
                textGroups.forEachIndexed { gi, g ->
                    for (t in 0 until g.length) {
                        val f = g.getTrackFormat(t)
                        val label = listOfNotNull(f.label, f.language?.uppercase()).joinToString(" • ").ifEmpty { "Altyazı ${gi + 1}" }
                        TrackRow(label, !textDisabled && g.isTrackSelected(t)) {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, t)).build()
                        }
                    }
                }
                OutlinedButton(onClick = { subPicker.launch(arrayOf("*/*")) }, modifier = Modifier.padding(vertical = 8.dp)) {
                    Icon(Icons.Rounded.Subtitles, null); Text("  Altyazı dosyası yükle (.srt / .vtt / .ass)")
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

private data class Offset2(val x: Float = 0f, val y: Float = 0f)

@Composable
private fun SectionTitle(t: String) {
    Text(t, fontWeight = FontWeight.SemiBold, color = TextDim, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .background(if (selected) Neon.copy(alpha = 0.18f) else Color.Transparent).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (selected) Cyan else Color.White.copy(alpha = 0.2f)))
        Text(label, modifier = Modifier.padding(start = 12.dp), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
