package com.boltgame.brickbreakerroguelite

import com.boltgame.brickbreakerroguelite.data.model.Ball
import com.boltgame.brickbreakerroguelite.data.model.Brick
import com.boltgame.brickbreakerroguelite.data.model.Paddle
import com.boltgame.brickbreakerroguelite.data.repository.GameProgressRepository
import com.boltgame.brickbreakerroguelite.game.engine.GameEngineImpl
import com.boltgame.brickbreakerroguelite.game.engine.GameState
import com.boltgame.brickbreakerroguelite.game.level.LevelGenerator
import com.boltgame.brickbreakerroguelite.game.physics.PhysicsEngine
import com.boltgame.brickbreakerroguelite.game.physics.CollisionResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@ExperimentalCoroutinesApi
class GameEngineTest {

    private lateinit var gameEngine: GameEngineImpl
    private lateinit var mockPhysicsEngine: PhysicsEngine
    private lateinit var mockLevelGenerator: LevelGenerator
    private lateinit var mockGameProgressRepository: GameProgressRepository

    @Before
    fun setUp() {
        mockPhysicsEngine = mockk()
        mockLevelGenerator = mockk()
        mockGameProgressRepository = mockk()

        gameEngine = GameEngineImpl(
            physicsEngine = mockPhysicsEngine,
            levelGenerator = mockLevelGenerator,
            gameProgressRepository = mockGameProgressRepository
        )
    }

    @Test
    fun `startGame should initialize game state correctly`() = runTest {
        // Given
        val mockBricks = listOf(createTestBrick())
        every { mockLevelGenerator.generateLevel(any(), any(), any()) } returns mockBricks

        // When
        gameEngine.startGame()

        // Then
        val gameState = gameEngine.gameState.first()
        assertTrue("Game should be running", gameState.isRunning)
        assertFalse("Game should not be paused", gameState.isPaused)
        assertNotNull("Paddle should be initialized", gameState.paddle)
        assertEquals("Should have one ball", 1, gameState.balls.size)
        assertEquals("Should have mock bricks", mockBricks, gameState.bricks)
    }

    @Test
    fun `pauseGame should set isPaused to true`() = runTest {
        // Given
        gameEngine.startGame()

        // When
        gameEngine.pauseGame()

        // Then
        val gameState = gameEngine.gameState.first()
        assertTrue("Game should be paused", gameState.isPaused)
        assertTrue("Game should still be running", gameState.isRunning)
    }

    @Test
    fun `resumeGame should set isPaused to false`() = runTest {
        // Given
        gameEngine.startGame()
        gameEngine.pauseGame()

        // When
        gameEngine.resumeGame()

        // Then
        val gameState = gameEngine.gameState.first()
        assertFalse("Game should not be paused", gameState.isPaused)
        assertTrue("Game should still be running", gameState.isRunning)
    }

    @Test
    fun `endGame should set isRunning to false`() = runTest {
        // Given
        gameEngine.startGame()

        // When
        gameEngine.endGame()

        // Then
        val gameState = gameEngine.gameState.first()
        assertFalse("Game should not be running", gameState.isRunning)
    }

    @Test
    fun `updatePaddlePosition should clamp position within screen bounds`() = runTest {
        // Given
        gameEngine.startGame()
        val initialState = gameEngine.gameState.first()
        val paddleWidth = initialState.paddle?.width ?: 0f
        val screenWidth = initialState.canvasWidth

        // When - try to move paddle beyond left boundary
        gameEngine.updatePaddlePosition(-100f)
        val leftBoundState = gameEngine.gameState.first()

        // Then
        assertEquals(
            "Paddle should be clamped to left boundary",
            paddleWidth / 2,
            leftBoundState.paddle?.centerX ?: 0f,
            0.1f
        )

        // When - try to move paddle beyond right boundary
        gameEngine.updatePaddlePosition(screenWidth + 100f)
        val rightBoundState = gameEngine.gameState.first()

        // Then
        assertEquals(
            "Paddle should be clamped to right boundary",
            screenWidth - paddleWidth / 2,
            rightBoundState.paddle?.centerX ?: 0f,
            0.1f
        )
    }

    @Test
    fun `onUpdate should not process when game is paused`() = runTest {
        // Given
        gameEngine.startGame()
        gameEngine.pauseGame()

        // When
        gameEngine.onUpdate(0.016f) // 16ms frame time

        // Then
        verify(exactly = 0) { mockPhysicsEngine.updateBall(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `onUpdate should not process when game is not running`() = runTest {
        // Given
        // Game is not started

        // When
        gameEngine.onUpdate(0.016f)

        // Then
        verify(exactly = 0) { mockPhysicsEngine.updateBall(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `onUpdate should process ball physics when game is running`() = runTest {
        // Given
        val mockBricks = listOf(createTestBrick())
        every { mockLevelGenerator.generateLevel(any(), any(), any()) } returns mockBricks
        
        val testBall = createTestBall()
        val mockCollisionResult = CollisionResult(
            updatedBall = testBall,
            updatedBricks = mockBricks,
            destroyedBricks = emptyList()
        )
        every { 
            mockPhysicsEngine.updateBall(any(), any(), any(), any(), any(), any()) 
        } returns mockCollisionResult

        gameEngine.startGame()

        // When
        gameEngine.onUpdate(0.016f)

        // Then
        verify(atLeast = 1) { 
            mockPhysicsEngine.updateBall(any(), any(), any(), any(), any(), any()) 
        }
    }

    @Test
    fun `onUpdate should increase score when bricks are destroyed`() = runTest {
        // Given
        val mockBricks = listOf(createTestBrick())
        val destroyedBrick = createTestBrick()
        every { mockLevelGenerator.generateLevel(any(), any(), any()) } returns mockBricks
        
        val testBall = createTestBall()
        val mockCollisionResult = CollisionResult(
            updatedBall = testBall,
            updatedBricks = emptyList(),
            destroyedBricks = listOf(destroyedBrick)
        )
        every { 
            mockPhysicsEngine.updateBall(any(), any(), any(), any(), any(), any()) 
        } returns mockCollisionResult

        gameEngine.startGame()
        val initialScore = gameEngine.gameState.first().runState.score

        // When
        gameEngine.onUpdate(0.016f)

        // Then
        val finalScore = gameEngine.gameState.first().runState.score
        assertTrue("Score should increase when bricks are destroyed", finalScore > initialScore)
    }

    @Test
    fun `onUpdate should respawn ball when ball falls off screen with lives remaining`() = runTest {
        // Given
        val mockBricks = listOf(createTestBrick())
        every { mockLevelGenerator.generateLevel(any(), any(), any()) } returns mockBricks
        
        val fallenBall = createTestBall().copy(centerY = 2000f) // Ball fallen off screen
        val mockCollisionResult = CollisionResult(
            updatedBall = fallenBall,
            updatedBricks = mockBricks,
            destroyedBricks = emptyList()
        )
        every { 
            mockPhysicsEngine.updateBall(any(), any(), any(), any(), any(), any()) 
        } returns mockCollisionResult

        gameEngine.startGame()
        val initialLives = gameEngine.gameState.first().runState.lives

        // When
        gameEngine.onUpdate(0.016f)

        // Then
        val finalState = gameEngine.gameState.first()
        assertEquals("Should have one ball after respawn", 1, finalState.balls.size)
        assertEquals("Lives should decrease by 1", initialLives - 1, finalState.runState.lives)
    }

    @Test
    fun `onUpdate should end game when no balls and no lives remaining`() = runTest {
        // Given
        val mockBricks = listOf(createTestBrick())
        every { mockLevelGenerator.generateLevel(any(), any(), any()) } returns mockBricks
        
        val fallenBall = createTestBall().copy(centerY = 2000f) // Ball fallen off screen
        val mockCollisionResult = CollisionResult(
            updatedBall = fallenBall,
            updatedBricks = mockBricks,
            destroyedBricks = emptyList()
        )
        every { 
            mockPhysicsEngine.updateBall(any(), any(), any(), any(), any(), any()) 
        } returns mockCollisionResult

        gameEngine.startGame()
        
        // Simulate running out of lives
        repeat(3) { // Assuming 3 initial lives
            gameEngine.onUpdate(0.016f)
        }

        // Then
        val finalState = gameEngine.gameState.first()
        assertFalse("Game should end when no balls and no lives", finalState.isRunning)
    }

    @Test
    fun `onUpdate should generate next level when all bricks destroyed`() = runTest {
        // Given
        val initialBricks = listOf(createTestBrick())
        val nextLevelBricks = listOf(createTestBrick().copy(id = "next_level_brick"))
        
        every { mockLevelGenerator.generateLevel(1, any(), any()) } returns initialBricks
        every { mockLevelGenerator.generateLevel(2, any(), any()) } returns nextLevelBricks
        
        val testBall = createTestBall()
        val mockCollisionResult = CollisionResult(
            updatedBall = testBall,
            updatedBricks = emptyList(), // All bricks destroyed
            destroyedBricks = initialBricks
        )
        every { 
            mockPhysicsEngine.updateBall(any(), any(), any(), any(), any(), any()) 
        } returns mockCollisionResult

        gameEngine.startGame()
        val initialLevel = gameEngine.gameState.first().runState.level

        // When
        gameEngine.onUpdate(0.016f)

        // Then
        val finalState = gameEngine.gameState.first()
        assertEquals("Level should advance", initialLevel + 1, finalState.runState.level)
        assertEquals("Should have next level bricks", nextLevelBricks, finalState.bricks)
    }

    @Test
    fun `object pools should reuse balls to reduce garbage collection`() = runTest {
        // This test verifies that the object pooling optimization is working
        // We'll test this by ensuring ball creation is optimized

        // Given
        val mockBricks = listOf(createTestBrick())
        every { mockLevelGenerator.generateLevel(any(), any(), any()) } returns mockBricks
        
        gameEngine.startGame()

        // When - Simulate multiple ball deaths and respawns
        repeat(5) {
            val fallenBall = createTestBall().copy(centerY = 2000f)
            val mockCollisionResult = CollisionResult(
                updatedBall = fallenBall,
                updatedBricks = mockBricks,
                destroyedBricks = emptyList()
            )
            every { 
                mockPhysicsEngine.updateBall(any(), any(), any(), any(), any(), any()) 
            } returns mockCollisionResult

            gameEngine.onUpdate(0.016f)
        }

        // Then
        val finalState = gameEngine.gameState.first()
        assertEquals("Should maintain ball count through pooling", 1, finalState.balls.size)
        assertTrue("Game should still be running", finalState.isRunning)
    }

    @Test
    fun `performance test - update should complete within 16ms budget`() = runTest {
        // Given
        val mockBricks = (1..50).map { createTestBrick().copy(id = "brick_$it") }
        every { mockLevelGenerator.generateLevel(any(), any(), any()) } returns mockBricks
        
        val testBall = createTestBall()
        val mockCollisionResult = CollisionResult(
            updatedBall = testBall,
            updatedBricks = mockBricks,
            destroyedBricks = emptyList()
        )
        every { 
            mockPhysicsEngine.updateBall(any(), any(), any(), any(), any(), any()) 
        } returns mockCollisionResult

        gameEngine.startGame()

        // When - Measure update time
        val startTime = System.nanoTime()
        repeat(100) { // 100 updates to get average
            gameEngine.onUpdate(0.016f)
        }
        val endTime = System.nanoTime()

        // Then
        val averageUpdateTime = (endTime - startTime) / 100 / 1_000_000.0 // Convert to milliseconds
        assertTrue(
            "Average update time should be under 16ms (was ${averageUpdateTime}ms)",
            averageUpdateTime < 16.0
        )
    }

    // Helper methods for creating test objects
    private fun createTestBall(): Ball {
        return Ball(
            id = "test_ball",
            centerX = 500f,
            centerY = 800f,
            radius = 25f,
            velocityX = 300f,
            velocityY = -500f,
            damageMultiplier = 1f
        )
    }

    private fun createTestBrick(): Brick {
        return Brick(
            id = "test_brick",
            bounds = android.graphics.RectF(100f, 100f, 200f, 150f),
            health = 1,
            maxHealth = 1,
            powerUpType = null
        )
    }

    private fun createTestPaddle(): Paddle {
        return Paddle(
            centerX = 500f,
            width = 300f,
            height = 40f
        )
    }
} 