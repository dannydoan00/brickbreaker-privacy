package com.boltgame.brickbreakerroguelite.di

import com.boltgame.brickbreakerroguelite.game.engine.GameEngine
import com.boltgame.brickbreakerroguelite.game.engine.GameEngineImpl
import com.boltgame.brickbreakerroguelite.game.level.LevelGenerator
import com.boltgame.brickbreakerroguelite.game.level.ProceduralLevelGenerator
import com.boltgame.brickbreakerroguelite.game.physics.PhysicsEngine
import com.boltgame.brickbreakerroguelite.game.physics.PhysicsEngineImpl
import com.boltgame.brickbreakerroguelite.game.upgrade.UpgradeManager
import com.boltgame.brickbreakerroguelite.game.upgrade.UpgradeManagerImpl
import com.boltgame.brickbreakerroguelite.ui.game.GameViewModel
import com.boltgame.brickbreakerroguelite.ui.home.HomeViewModel
import com.boltgame.brickbreakerroguelite.ui.shop.ShopViewModel
import com.boltgame.brickbreakerroguelite.ui.upgrade.UpgradeViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val gameModule = module {
    // Game components
    single<PhysicsEngine> { PhysicsEngineImpl() }
    single<LevelGenerator> { ProceduralLevelGenerator() }
    single<GameEngine> { GameEngineImpl(get(), get(), get()) }
    single<UpgradeManager> { UpgradeManagerImpl(get()) }
    
    // ViewModels
    viewModel { HomeViewModel(get(), get()) }
    viewModel { GameViewModel(get(), get(), get(), get()) }
    viewModel { ShopViewModel(get(), get(), get()) }
    viewModel { UpgradeViewModel(get(), get()) }
}