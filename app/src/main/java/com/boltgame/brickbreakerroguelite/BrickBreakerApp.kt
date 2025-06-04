package com.boltgame.brickbreakerroguelite

import android.app.Application
import com.boltgame.brickbreakerroguelite.di.appModule
import com.boltgame.brickbreakerroguelite.di.gameModule
import com.boltgame.brickbreakerroguelite.di.monetizationModule
import com.google.android.gms.ads.MobileAds
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class BrickBreakerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Koin for dependency injection
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@BrickBreakerApp)
            modules(listOf(appModule, gameModule, monetizationModule))
        }
        
        // Initialize AdMob
        MobileAds.initialize(this)
    }
}