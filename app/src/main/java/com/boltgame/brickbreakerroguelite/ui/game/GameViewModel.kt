package com.boltgame.brickbreakerroguelite.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boltgame.brickbreakerroguelite.data.repository.GameProgressRepository
import com.boltgame.brickbreakerroguelite.game.engine.GameEngine
import com.boltgame.brickbreakerroguelite.game.engine.GameState
import com.boltgame.brickbreakerroguelite.monetization.ads.AdManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GameViewModel(
    private val gameEngine: GameEngine,
    private val gameProgressRepository: GameProgressRepository,
    private val adManager: AdManager,
    // Other dependencies as needed
) : ViewModel() {
    
    val gameState: StateFlow<GameState> = gameEngine.gameState
    
    private val _isGameOver = MutableStateFlow(false)
    val isGameOver: StateFlow<Boolean> = _isGameOver.asStateFlow()
    
    private val _finalScore = MutableStateFlow(0)
    val finalScore: StateFlow<Int> = _finalScore.asStateFlow()
    
    private var gameLoopJob: Job? = null
    
    fun startGame() {
        gameEngine.startGame()
        _isGameOver.value = false
        startGameLoop()
    }
    
    fun pauseGame() {
        gameEngine.pauseGame()
    }
    
    fun resumeGame() {
        gameEngine.resumeGame()
    }
    
    fun togglePause() {
        val currentState = gameState.value
        if (currentState.isPaused) {
            gameEngine.resumeGame()
        } else {
            gameEngine.pauseGame()
        }
    }
    
    fun endGame() {
        gameEngine.endGame()
        gameLoopJob?.cancel()
        
        // Update game progress and prepare for game over
        val finalScore = gameState.value.runState.score
        _finalScore.value = finalScore
        
        viewModelScope.launch {
            gameProgressRepository.updateHighScore(finalScore)
            
            // Add rewards based on score
            val softCurrencyReward = (finalScore / 100).coerceAtMost(500)
            gameProgressRepository.addSoftCurrency(softCurrencyReward)
            
            _isGameOver.value = true
        }
    }
    
    fun updatePaddlePosition(x: Float) {
        gameEngine.updatePaddlePosition(x)
    }
    
    fun watchAdForReward() {
        if (adManager.adState.value.isRewardedAdLoaded) {
            adManager.showRewardedAd(
                onRewarded = { rewardAmount ->
                    // Apply reward (e.g., continue game with extra life or bonus)
                    viewModelScope.launch {
                        gameProgressRepository.addSoftCurrency(rewardAmount)
                    }
                },
                onDismissed = {
                    // Ad dismissed without reward, do nothing
                }
            )
        } else {
            // Ad not loaded, show message to user
            adManager.loadRewardedAd()
        }
    }
    
    private fun startGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = viewModelScope.launch {
            var lastFrameTime = System.currentTimeMillis()
            
            while (isActive && gameState.value.isRunning) {
                val currentTime = System.currentTimeMillis()
                val deltaTime = (currentTime - lastFrameTime) / 1000f
                lastFrameTime = currentTime
                
                gameEngine.onUpdate(deltaTime)
                
                // Aim for approximately 60 FPS
                delay(16)
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        gameLoopJob?.cancel()
    }
}