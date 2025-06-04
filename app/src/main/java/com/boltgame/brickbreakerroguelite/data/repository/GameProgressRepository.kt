package com.boltgame.brickbreakerroguelite.data.repository

import android.content.SharedPreferences
import com.boltgame.brickbreakerroguelite.data.model.GameProgress
import com.boltgame.brickbreakerroguelite.data.model.PermanentUpgrade
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface GameProgressRepository {
    fun getGameProgress(): Flow<GameProgress>
    suspend fun updateGameProgress(progress: GameProgress)
    suspend fun addSoftCurrency(amount: Int)
    suspend fun addHardCurrency(amount: Int)
    suspend fun spendSoftCurrency(amount: Int): Boolean
    suspend fun spendHardCurrency(amount: Int): Boolean
    suspend fun updateHighScore(score: Int)
    suspend fun unlockPaddle(paddleId: String)
    suspend fun unlockBall(ballId: String)
    suspend fun equipPaddle(paddleId: String)
    suspend fun equipBall(ballId: String)
    suspend fun upgradePermanent(upgradeId: String): Boolean
}

class GameProgressRepositoryImpl(
    private val sharedPreferences: SharedPreferences
) : GameProgressRepository {
    
    private val gson = Gson()
    private val _gameProgress = MutableStateFlow<GameProgress>(loadProgress())
    
    override fun getGameProgress(): Flow<GameProgress> = _gameProgress.asStateFlow()
    
    override suspend fun updateGameProgress(progress: GameProgress) {
        _gameProgress.value = progress
        saveProgress(progress)
    }
    
    override suspend fun addSoftCurrency(amount: Int) {
        val currentProgress = _gameProgress.value
        val updatedProgress = currentProgress.copy(
            softCurrency = currentProgress.softCurrency + amount
        )
        _gameProgress.value = updatedProgress
        saveProgress(updatedProgress)
    }
    
    override suspend fun addHardCurrency(amount: Int) {
        val currentProgress = _gameProgress.value
        val updatedProgress = currentProgress.copy(
            hardCurrency = currentProgress.hardCurrency + amount
        )
        _gameProgress.value = updatedProgress
        saveProgress(updatedProgress)
    }
    
    override suspend fun spendSoftCurrency(amount: Int): Boolean {
        val currentProgress = _gameProgress.value
        if (currentProgress.softCurrency < amount) {
            return false
        }
        
        val updatedProgress = currentProgress.copy(
            softCurrency = currentProgress.softCurrency - amount
        )
        _gameProgress.value = updatedProgress
        saveProgress(updatedProgress)
        return true
    }
    
    override suspend fun spendHardCurrency(amount: Int): Boolean {
        val currentProgress = _gameProgress.value
        if (currentProgress.hardCurrency < amount) {
            return false
        }
        
        val updatedProgress = currentProgress.copy(
            hardCurrency = currentProgress.hardCurrency - amount
        )
        _gameProgress.value = updatedProgress
        saveProgress(updatedProgress)
        return true
    }
    
    override suspend fun updateHighScore(score: Int) {
        val currentProgress = _gameProgress.value
        if (score > currentProgress.highScore) {
            val updatedProgress = currentProgress.copy(highScore = score)
            _gameProgress.value = updatedProgress
            saveProgress(updatedProgress)
        }
    }
    
    override suspend fun unlockPaddle(paddleId: String) {
        val currentProgress = _gameProgress.value
        if (!currentProgress.unlockedPaddles.contains(paddleId)) {
            val updatedUnlocked = currentProgress.unlockedPaddles + paddleId
            val updatedProgress = currentProgress.copy(unlockedPaddles = updatedUnlocked)
            _gameProgress.value = updatedProgress
            saveProgress(updatedProgress)
        }
    }
    
    override suspend fun unlockBall(ballId: String) {
        val currentProgress = _gameProgress.value
        if (!currentProgress.unlockedBalls.contains(ballId)) {
            val updatedUnlocked = currentProgress.unlockedBalls + ballId
            val updatedProgress = currentProgress.copy(unlockedBalls = updatedUnlocked)
            _gameProgress.value = updatedProgress
            saveProgress(updatedProgress)
        }
    }
    
    override suspend fun equipPaddle(paddleId: String) {
        val currentProgress = _gameProgress.value
        if (currentProgress.unlockedPaddles.contains(paddleId)) {
            val updatedProgress = currentProgress.copy(equippedPaddle = paddleId)
            _gameProgress.value = updatedProgress
            saveProgress(updatedProgress)
        }
    }
    
    override suspend fun equipBall(ballId: String) {
        val currentProgress = _gameProgress.value
        if (currentProgress.unlockedBalls.contains(ballId)) {
            val updatedProgress = currentProgress.copy(equippedBall = ballId)
            _gameProgress.value = updatedProgress
            saveProgress(updatedProgress)
        }
    }
    
    override suspend fun upgradePermanent(upgradeId: String): Boolean {
        val currentProgress = _gameProgress.value
        val upgrade = currentProgress.permanentUpgrades.find { it.id == upgradeId }
        
        // If upgrade doesn't exist yet, create it
        if (upgrade == null) {
            val newUpgrade = PermanentUpgrade(id = upgradeId, level = 1, maxLevel = 5)
            val updatedUpgrades = currentProgress.permanentUpgrades + newUpgrade
            val updatedProgress = currentProgress.copy(permanentUpgrades = updatedUpgrades)
            _gameProgress.value = updatedProgress
            saveProgress(updatedProgress)
            return true
        }
        
        // If upgrade exists but is maxed out, return false
        if (upgrade.level >= upgrade.maxLevel) {
            return false
        }
        
        // Otherwise upgrade it
        val updatedUpgrade = upgrade.copy(level = upgrade.level + 1)
        val updatedUpgrades = currentProgress.permanentUpgrades.map {
            if (it.id == upgradeId) updatedUpgrade else it
        }
        val updatedProgress = currentProgress.copy(permanentUpgrades = updatedUpgrades)
        _gameProgress.value = updatedProgress
        saveProgress(updatedProgress)
        return true
    }
    
    private fun loadProgress(): GameProgress {
        val json = sharedPreferences.getString("game_progress", null) ?: return GameProgress()
        
        return try {
            val type = object : TypeToken<GameProgress>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            GameProgress()
        }
    }
    
    private fun saveProgress(progress: GameProgress) {
        val json = gson.toJson(progress)
        sharedPreferences.edit().putString("game_progress", json).apply()
    }
}