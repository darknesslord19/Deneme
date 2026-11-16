package com.pulse.player

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.pulse.player.player.MusicController
import com.pulse.player.ui.PulseRoot
import com.pulse.player.ui.theme.PulseTheme

class MainActivity : ComponentActivity() {
    val pipMode = mutableStateOf(false)
    val externalIntent = mutableStateOf<Intent?>(null)
    var videoAspect: Rational? = null
    var videoPlaying = false
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MusicController.init(this)
        if (savedInstanceState == null) externalIntent.value = intent
        setContent { PulseTheme { PulseRoot(this) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        externalIntent.value = intent
    }

    override fun onStart() {
        super.onStart()
        if (controllerFuture == null) {
            controllerFuture = MediaController.Builder(
                this, SessionToken(this, ComponentName(this, PlaybackService::class.java))
            ).buildAsync()
        }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onDestroy()
    }

    fun enterPip(): Boolean {
        val ratio = videoAspect ?: return false
        return try {
            val r = ratio.toFloat()
            val safe = when {
                r > 2.39f -> Rational(239, 100)
                r < 1f / 2.39f -> Rational(100, 239)
                else -> ratio
            }
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(safe).build())
        } catch (e: Exception) {
            false
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (videoPlaying) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipMode.value = isInPictureInPictureMode
    }
}
