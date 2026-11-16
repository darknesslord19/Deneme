package com.pulse.player.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.player.MainActivity
import com.pulse.player.data.LibraryViewModel
import com.pulse.player.data.MediaRepository
import com.pulse.player.data.VideoItem
import com.pulse.player.player.MusicController
import com.pulse.player.ui.theme.Bg
import com.pulse.player.ui.theme.Neon
import com.pulse.player.ui.theme.TextDim

data class VideoSession(val list: List<VideoItem>, val index: Int)

fun hasMediaPermission(ctx: Context): Boolean {
    fun g(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    return if (Build.VERSION.SDK_INT >= 33) g(Manifest.permission.READ_MEDIA_AUDIO) || g(Manifest.permission.READ_MEDIA_VIDEO)
    else g(Manifest.permission.READ_EXTERNAL_STORAGE)
}

@Composable
fun PulseRoot(activity: MainActivity) {
    val ctx = LocalContext.current
    val vm: LibraryViewModel = viewModel()
    val perms = remember {
        if (Build.VERSION.SDK_INT >= 33) arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.POST_NOTIFICATIONS
        ) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    var granted by remember { mutableStateOf(hasMediaPermission(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasMediaPermission(ctx)
        if (granted) vm.load(force = true)
    }
    LaunchedEffect(Unit) { if (granted) vm.load() else launcher.launch(perms) }

    var nowPlaying by rememberSaveable { mutableStateOf(false) }
    var video by remember { mutableStateOf<VideoSession?>(null) }

    val ext by activity.externalIntent
    LaunchedEffect(ext) {
        val intent = ext ?: return@LaunchedEffect
        activity.externalIntent.value = null
        if (intent.action != Intent.ACTION_VIEW) return@LaunchedEffect
        val uri = intent.data ?: return@LaunchedEffect
        val type = intent.type ?: runCatching { ctx.contentResolver.getType(uri) }.getOrNull() ?: ""
        val path = uri.toString().lowercase()
        val isVideo = type.startsWith("video") ||
            listOf(".mp4", ".mkv", ".webm", ".avi", ".mov", ".3gp", ".ts", ".m3u8").any { path.endsWith(it) }
        if (isVideo) {
            video = VideoSession(listOf(MediaRepository.externalVideo(ctx, uri)), 0)
        } else {
            MusicController.playQueue(listOf(MediaRepository.externalSong(ctx, uri)), 0)
            nowPlaying = true
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        if (!granted) {
            PermissionScreen { launcher.launch(perms) }
        } else {
            LibraryScreen(
                vm = vm,
                onOpenVideo = { list, i -> video = VideoSession(list, i) },
                onOpenPlayer = { nowPlaying = true },
            )
        }
        AnimatedVisibility(
            visible = nowPlaying,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            NowPlayingScreen(vm = vm, onClose = { nowPlaying = false })
        }
        BackHandler(enabled = nowPlaying && video == null) { nowPlaying = false }
        video?.let { v ->
            VideoPlayerScreen(v.list, v.index, activity, onClose = { video = null })
        }
    }
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.GraphicEq, null, tint = Neon, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(16.dp))
            Text("Pulse Player", fontSize = 28.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text(
                "Müziklerini ve videolarını listeleyebilmem için medya erişim izni gerekiyor.",
                color = TextDim, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onGrant) { Text("İzin Ver") }
        }
    }
}
