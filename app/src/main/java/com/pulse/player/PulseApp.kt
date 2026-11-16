package com.pulse.player

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.pulse.player.player.MusicController

class PulseApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        MusicController.init(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
