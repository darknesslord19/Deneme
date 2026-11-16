package com.pulse.player.ui

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.pulse.player.player.MusicController
import com.pulse.player.ui.theme.Card
import com.pulse.player.ui.theme.CardHi
import com.pulse.player.ui.theme.Cyan
import com.pulse.player.ui.theme.Neon
import com.pulse.player.ui.theme.Pink
import com.pulse.player.ui.theme.TextDim

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toFloat())
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1L shl 20).toFloat())
    else -> "%.0f KB".format(bytes / 1024f)
}

@Composable
fun ArtImage(model: Any?, modifier: Modifier = Modifier, corner: Dp = 12.dp, iconSize: Dp = 22.dp) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(Neon.copy(alpha = 0.55f), Cyan.copy(alpha = 0.35f), Pink.copy(alpha = 0.4f)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(iconSize))
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

suspend fun extractAccent(ctx: Context, model: Any?): Color? {
    model ?: return null
    return try {
        val req = ImageRequest.Builder(ctx).data(model).allowHardware(false).size(160).build()
        val res = ctx.imageLoader.execute(req) as? SuccessResult ?: return null
        val bmp = (res.drawable as? BitmapDrawable)?.bitmap ?: return null
        val p = Palette.from(bmp).generate()
        val sw = p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.dominantSwatch ?: return null
        Color(sw.rgb)
    } catch (_: Exception) {
        null
    }
}

@Composable
fun PlayingBars(color: Color, modifier: Modifier = Modifier, playing: Boolean = true) {
    val t = rememberInfiniteTransition(label = "bars")
    val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(1f, 0.3f, infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "b")
    val c by t.animateFloat(0.45f, 0.95f, infiniteRepeatable(tween(360), RepeatMode.Reverse), label = "c")
    Row(modifier.size(18.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(a, b, c).forEach { h ->
            Box(
                Modifier.width(4.dp).fillMaxHeight(if (playing) h else 0.2f)
                    .clip(RoundedCornerShape(2.dp)).background(color)
            )
        }
    }
}

@Composable
fun MiniPlayer(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val song by MusicController.current.collectAsStateWithLifecycle()
    val playing by MusicController.isPlaying.collectAsStateWithLifecycle()
    val pos by MusicController.position.collectAsStateWithLifecycle()
    val dur by MusicController.duration.collectAsStateWithLifecycle()
    val s = song ?: return
    Box(
        modifier
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(CardHi, Card)))
            .clickable(onClick = onOpen)
    ) {
        Column {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtImage(s.artUri, Modifier.size(46.dp), corner = 10.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(s.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.artist, color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { MusicController.toggle() }) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Neon, Pink))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White)
                    }
                }
                IconButton(onClick = { MusicController.next() }) { Icon(Icons.Rounded.SkipNext, null) }
            }
            val f = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.08f))) {
                Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(Brush.horizontalGradient(listOf(Neon, Cyan))))
            }
        }
    }
}

@Composable
fun EmptyState(text: String, sub: String = "") {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.MusicNote, null, tint = Neon, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            if (sub.isNotEmpty()) Text(sub, color = TextDim, fontSize = 13.sp)
        }
    }
}
