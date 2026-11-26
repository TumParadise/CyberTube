package com.cybertube.app

import android.app.Application
import com.cybertube.app.core.Store
import com.cybertube.app.core.YtDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import com.cybertube.app.player.PlayerHolder
import com.cybertube.app.potoken.PoTokenProviderImpl

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Store.init(this)
        NewPipe.init(YtDownloader(), Localization("en", "US"), ContentCountry("TH"))
        YoutubeStreamExtractor.setPoTokenProvider(PoTokenProviderImpl)
        PlayerHolder.purgeOldCache(this) // drop cached videos unused for 7 days
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
