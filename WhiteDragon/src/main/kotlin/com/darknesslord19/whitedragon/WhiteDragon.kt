package com.darknesslord19.whitedragon

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class WhiteDragon : MainAPI() {
    override var mainUrl = "https://fullhdfilmizlesene.now"
    override var name = "WhiteDragon"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie)

    // TODO: getMainPage, search, load ve loadLinks fonksiyonlarını
    // kaynağının HTML/API yapısına göre doldur.
    // Kılavuz: https://recloudstream.github.io/csdocs/devs/adding-a-provider/
}
