package com.boltgame.brickbreakerroguelite.di

import com.boltgame.brickbreakerroguelite.monetization.ads.AdManager
import com.boltgame.brickbreakerroguelite.monetization.ads.AdManagerImpl
import com.boltgame.brickbreakerroguelite.monetization.iap.IapManager
import com.boltgame.brickbreakerroguelite.monetization.iap.IapManagerImpl
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val monetizationModule = module {
    single<AdManager> { AdManagerImpl(androidContext()) }
    single<IapManager> { IapManagerImpl(androidContext()) }
}