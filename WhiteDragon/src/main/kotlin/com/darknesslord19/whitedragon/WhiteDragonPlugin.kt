package com.darknesslord19.whitedragon

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class WhiteDragonPlugin: Plugin() {
    override fun load(context: Context) {
        // Bu eklentinin sağlayıcısını kaydet
        registerMainAPI(WhiteDragon())
    }
}
