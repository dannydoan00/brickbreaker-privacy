package com.boltgame.brickbreakerroguelite.data.model

data class GameProgress(
    val userId: String = "",
    val softCurrency: Int = 0,
    val hardCurrency: Int = 0,
    val highScore: Int = 0,
    val totalGamesPlayed: Int = 0,
    val totalBricksDestroyed: Int = 0,
    val permanentUpgrades: List<PermanentUpgrade> = emptyList(),
    val unlockedPaddles: List<String> = listOf("default"),
    val unlockedBalls: List<String> = listOf("default"),
    val equippedPaddle: String = "default",
    val equippedBall: String = "default",
    val lastSaved: Long = System.currentTimeMillis()
)

data class PermanentUpgrade(
    val id: String,
    val level: Int,
    val maxLevel: Int
)