package com.boltgame.brickbreakerroguelite.game.engine

import com.boltgame.brickbreakerroguelite.data.model.Ball
import com.boltgame.brickbreakerroguelite.data.model.Brick
import com.boltgame.brickbreakerroguelite.data.model.Paddle
import com.boltgame.brickbreakerroguelite.data.model.PowerUp
import com.boltgame.brickbreakerroguelite.data.model.RunState
import com.boltgame.brickbreakerroguelite.data.repository.GameProgressRepository
import com.boltgame.brickbreakerroguelite.game.level.LevelGenerator
import com.boltgame.brickbreakerroguelite.game.physics.PhysicsEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min

interface GameEngine {
    val gameState: StateFlow<GameState>
    fun startGame()
    fun pauseGame()
    fun resumeGame()
    fun endGame()
    fun updatePaddlePosition(x: Float)
    fun onUpdate(deltaTime: Float)
}

data class GameState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val runState: RunState = RunState(),
    val paddle: Paddle? = null,
    val balls: List<Ball> = emptyList(),
    val bricks: List<Brick> = emptyList(),
    val powerUps: List<PowerUp> = emptyList(),
    val canvasWidth: Float = 0f,
    val canvasHeight: Float = 0f,
    val difficultyMultiplier: Float = 1f
)

class GameEngineImpl(
    private val physicsEngine: PhysicsEngine,
    private val levelGenerator: LevelGenerator,
    private val gameProgressRepository: GameProgressRepository
) : GameEngine {
    
    private val _gameState = MutableStateFlow(GameState())
    override val gameState: StateFlow<GameState> = _gameState.asStateFlow()
    
    private var screenWidth: Float = 0f
    private var screenHeight: Float = 0f
    
    // Object pools to reduce garbage collection
    private val ballPool = mutableListOf<Ball>()
    private val brickPool = mutableListOf<Brick>()
    private val powerUpPool = mutableListOf<PowerUp>()
    
    // Reusable collections to avoid allocations
    private val reusableBallList = mutableListOf<Ball>()
    private val reusableBrickList = mutableListOf<Brick>()
    private val reusablePowerUpList = mutableListOf<PowerUp>()
    private val reusableDestroyedBricks = mutableListOf<Brick>()
    
    override fun startGame() {
        screenWidth = 1080f  // Default, will be updated when view is ready
        screenHeight = 1920f // Default, will be updated when view is ready
        
        val initialBall = Ball(
            id = "ball_0",
            centerX = screenWidth / 2,
            centerY = screenHeight * 0.7f,
            radius = 25f,
            velocityX = 300f,
            velocityY = -500f
        )
        
        val initialPaddle = Paddle(
            centerX = screenWidth / 2,
            width = 300f,
            height = 40f
        )
        
        val initialBricks = levelGenerator.generateLevel(1, screenWidth, screenHeight * 0.5f)
        
        _gameState.value = GameState(
            isRunning = true,
            isPaused = false,
            runState = RunState(),
            paddle = initialPaddle,
            balls = listOf(initialBall),
            bricks = initialBricks,
            canvasWidth = screenWidth,
            canvasHeight = screenHeight
        )
    }
    
    override fun pauseGame() {
        val currentState = _gameState.value
        _gameState.value = currentState.copy(isPaused = true)
    }
    
    override fun resumeGame() {
        val currentState = _gameState.value
        _gameState.value = currentState.copy(isPaused = false)
    }
    
    override fun endGame() {
        val currentState = _gameState.value
        val finalScore = currentState.runState.score
        
        // Update high score and add rewards
        // In a real app, this would be done through a proper reward system
        val currencyReward = min(finalScore / 100, 500)
        
        _gameState.value = currentState.copy(isRunning = false)
    }
    
    override fun updatePaddlePosition(x: Float) {
        val currentState = _gameState.value
        val currentPaddle = currentState.paddle ?: return
        
        // Ensure paddle stays within screen bounds
        val halfPaddleWidth = currentPaddle.width / 2
        val clampedX = x.coerceIn(halfPaddleWidth, screenWidth - halfPaddleWidth)
        
        val updatedPaddle = currentPaddle.copy(centerX = clampedX)
        _gameState.value = currentState.copy(paddle = updatedPaddle)
    }
    
    override fun onUpdate(deltaTime: Float) {
        val currentState = _gameState.value
        
        if (!currentState.isRunning || currentState.isPaused) {
            return
        }
        
        // Clear reusable collections (avoid new allocations)
        reusableBallList.clear()
        reusableBrickList.clear()
        reusablePowerUpList.clear()
        reusableDestroyedBricks.clear()
        
        var scoreIncrease = 0
        
        // Process ball movement and collisions
        for (ball in currentState.balls) {
            val collisionResult = physicsEngine.updateBall(
                ball,
                currentState.paddle,
                currentState.bricks,
                currentState.canvasWidth,
                currentState.canvasHeight,
                deltaTime
            )
            
            // Only keep balls that are still in play
            if (collisionResult.updatedBall.centerY < currentState.canvasHeight + 100) {
                reusableBallList.add(collisionResult.updatedBall)
            }
            
            // Add score for destroyed bricks
            scoreIncrease += collisionResult.destroyedBricks.size * 10
            
            // Collect destroyed bricks for power-up processing
            reusableDestroyedBricks.addAll(collisionResult.destroyedBricks)
        }
        
        // Update bricks - use the latest collision results
        val latestCollisionResult = if (currentState.balls.isNotEmpty()) {
            physicsEngine.updateBall(
                currentState.balls.first(),
                currentState.paddle,
                currentState.bricks,
                currentState.canvasWidth,
                currentState.canvasHeight,
                deltaTime
            )
        } else null
        
        reusableBrickList.addAll(latestCollisionResult?.updatedBricks ?: currentState.bricks)
        
        // Process power-ups from destroyed bricks
        reusableDestroyedBricks.forEach { brick ->
            if (brick.powerUpType != null) {
                // Create power-up from pool or new instance
                val powerUp = getPowerUpFromPool(brick.bounds.centerX(), brick.bounds.centerY())
                reusablePowerUpList.add(powerUp)
            }
        }
        
        // Update existing power-ups (movement, collection logic)
        currentState.powerUps.forEach { powerUp ->
            // Simple gravity simulation for power-ups
            val updatedPowerUp = powerUp.copy(
                centerY = powerUp.centerY + 200f * deltaTime // Fall speed
            )
            
            // Check if power-up is still on screen and not collected
            if (updatedPowerUp.centerY < currentState.canvasHeight) {
                reusablePowerUpList.add(updatedPowerUp)
            }
        }
        
        // Check for game over (no balls left)
        if (reusableBallList.isEmpty()) {
            val currentLives = currentState.runState.lives
            if (currentLives > 1) {
                // Respawn ball and reduce lives
                val newBall = getBallFromPool(
                    currentState.paddle?.centerX ?: (screenWidth / 2),
                    screenHeight * 0.7f
                )
                reusableBallList.add(newBall)
                
                _gameState.value = currentState.copy(
                    balls = reusableBallList.toList(),
                    bricks = reusableBrickList.toList(),
                    powerUps = reusablePowerUpList.toList(),
                    runState = currentState.runState.copy(
                        lives = currentLives - 1,
                        score = currentState.runState.score + scoreIncrease
                    )
                )
            } else {
                // Game over
                endGame()
            }
        } else {
            // Normal update - convert to immutable lists
            _gameState.value = currentState.copy(
                balls = reusableBallList.toList(),
                bricks = reusableBrickList.toList(),
                powerUps = reusablePowerUpList.toList(),
                runState = currentState.runState.copy(
                    score = currentState.runState.score + scoreIncrease
                )
            )
        }
        
        // Check for level completion
        if (reusableBrickList.isEmpty()) {
            // Level completed - generate next level
            val nextLevel = currentState.runState.level + 1
            val newBricks = levelGenerator.generateLevel(nextLevel, screenWidth, screenHeight * 0.5f)
            
            _gameState.value = _gameState.value.copy(
                bricks = newBricks,
                runState = _gameState.value.runState.copy(level = nextLevel),
                difficultyMultiplier = 1 + (nextLevel - 1) * 0.1f
            )
        }
    }
    
    // Object pool methods to reduce garbage collection
    private fun getBallFromPool(x: Float, y: Float): Ball {
        return if (ballPool.isNotEmpty()) {
            ballPool.removeAt(ballPool.size - 1).copy(
                centerX = x,
                centerY = y,
                velocityX = 300f,
                velocityY = -500f
            )
        } else {
            Ball(
                id = "ball_${System.currentTimeMillis()}",
                centerX = x,
                centerY = y,
                radius = 25f,
                velocityX = 300f,
                velocityY = -500f
            )
        }
    }
    
    private fun getPowerUpFromPool(x: Float, y: Float): PowerUp {
        return if (powerUpPool.isNotEmpty()) {
            powerUpPool.removeAt(powerUpPool.size - 1).copy(
                centerX = x,
                centerY = y
            )
        } else {
            PowerUp(
                id = "powerup_${System.currentTimeMillis()}",
                centerX = x,
                centerY = y,
                type = PowerUp.Type.MULTI_BALL,
                size = 30f
            )
        }
    }
    
    private fun returnBallToPool(ball: Ball) {
        if (ballPool.size < 10) { // Limit pool size
            ballPool.add(ball)
        }
    }
    
    private fun returnPowerUpToPool(powerUp: PowerUp) {
        if (powerUpPool.size < 20) { // Limit pool size
            powerUpPool.add(powerUp)
        }
    }
}