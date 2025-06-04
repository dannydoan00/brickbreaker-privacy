package com.boltgame.brickbreakerroguelite.data.model

data class RunState(
    val level: Int = 1,
    val score: Int = 0,
    val lives: Int = 3,
    val temporaryUpgrades: List<TemporaryUpgrade> = emptyList()
)

data class TemporaryUpgrade(
    val id: String,
    val remainingDuration: Float
)