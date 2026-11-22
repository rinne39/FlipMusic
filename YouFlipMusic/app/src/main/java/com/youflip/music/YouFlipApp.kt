package com.youflip.music

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.youflip.music.data.LibraryStore
import com.youflip.music.data.MusicRepository
import com.youflip.music.data.NewPipeDownloader
import com.youflip.music.data.SettingsStore
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale
import java.util.concurrent.TimeUnit

class YouFlipApp : Application(), SingletonImageLoader.Factory {

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    val repository by lazy { MusicRepository() }
    val account by lazy { com.youflip.music.data.auth.AccountStore(this) }
    val ytmClient by lazy { com.youflip.music.data.YtmClient(httpClient, account) }
    val ytm by lazy { com.youflip.music.data.YtmRepository(ytmClient) }
    val neteaseAccount by lazy { com.youflip.music.data.auth.AccountStore(this, "netease_account", "youflip_netease") }
    val netease by lazy {
        com.youflip.music.data.netease.NeteaseRepository(
            com.youflip.music.data.netease.NeteaseClient(com.youflip.music.data.netease.NeteaseWebBridge(this), httpClient, neteaseAccount), neteaseAccount,
        )
    }
    val lyrics by lazy { com.youflip.music.data.LyricsRepository(httpClient, ytmClient, netease) }
    val updater by lazy { com.youflip.music.data.Updater(this, httpClient) }
    val library by lazy { LibraryStore(this) }
    val settings by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val locale = Locale.getDefault()
        NewPipe.init(
            NewPipeDownloader(httpClient),
            Localization.fromLocale(locale),
            ContentCountry(locale.country.ifBlank { "US" }),
        )
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { httpClient }))
                // Retry missing hq720 thumbnails with hqdefault.
                add(coil3.intercept.Interceptor { chain ->
                    val result = chain.proceed()
                    val alt = com.youflip.music.data.Thumbs.fallback(chain.request.data as? String)
                    if (result is coil3.request.ErrorResult && alt != null) {
                        chain.withRequest(chain.request.newBuilder().data(alt).build()).proceed()
                    } else result
                })
            }
            .crossfade(true)
            .build()

    companion object {
        lateinit var instance: YouFlipApp
            private set
    }
}
