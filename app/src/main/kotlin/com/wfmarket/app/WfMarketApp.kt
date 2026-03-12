package com.wfmarket.app

import android.app.Application
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.wfmarket.app.data.AppContainer

class WfMarketApp : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.api.http })) }
            .build()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
