package com.boltgame.brickbreakerroguelite.di

import android.content.Context
import android.content.SharedPreferences
import com.boltgame.brickbreakerroguelite.data.repository.GameProgressRepository
import com.boltgame.brickbreakerroguelite.data.repository.GameProgressRepositoryImpl
import com.boltgame.brickbreakerroguelite.data.repository.UserRepository
import com.boltgame.brickbreakerroguelite.data.repository.UserRepositoryImpl
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    
    // Provide SharedPreferences
    single<SharedPreferences> {
        androidContext().getSharedPreferences("BrickBreakerPrefs", Context.MODE_PRIVATE)
    }
    
    // Repositories
    single<UserRepository> { UserRepositoryImpl(get()) }
    single<GameProgressRepository> { GameProgressRepositoryImpl(get()) }
}