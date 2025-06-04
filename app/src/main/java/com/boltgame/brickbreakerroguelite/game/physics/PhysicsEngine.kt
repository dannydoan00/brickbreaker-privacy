package com.boltgame.brickbreakerroguelite.game.physics

import android.graphics.RectF
import com.boltgame.brickbreakerroguelite.data.model.Ball
import com.boltgame.brickbreakerroguelite.data.model.Brick
import com.boltgame.brickbreakerroguelite.data.model.Paddle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

interface PhysicsEngine {
    fun updateBall(
        ball: Ball,
        paddle: Paddle?,
        bricks: List<Brick>,
        canvasWidth: Float,
        canvasHeight: Float,
        deltaTime: Float
    ): CollisionResult
}

data class CollisionResult(
    val updatedBall: Ball,
    val updatedBricks: List<Brick>,
    val destroyedBricks: List<Brick>
)

class PhysicsEngineImpl : PhysicsEngine {
    
    // Cache frequently used values to avoid recalculation
    private var cachedCanvasWidth: Float = 0f
    private var cachedCanvasHeight: Float = 0f
    private var cachedPaddleTop: Float = 0f
    private var frameCount = 0
    
    // Reusable objects to avoid allocations
    private val tempRect = RectF()
    private val reusableBrickList = mutableListOf<Brick>()
    private val reusableDestroyedList = mutableListOf<Brick>()
    
    override fun updateBall(
        ball: Ball,
        paddle: Paddle?,
        bricks: List<Brick>,
        canvasWidth: Float,
        canvasHeight: Float,
        deltaTime: Float
    ): CollisionResult {
        
        // Update cached values only when needed
        if (cachedCanvasWidth != canvasWidth || cachedCanvasHeight != canvasHeight) {
            cachedCanvasWidth = canvasWidth
            cachedCanvasHeight = canvasHeight
            cachedPaddleTop = canvasHeight - (paddle?.height ?: 40f)
        }
        
        frameCount++
        
        // Calculate new position with clamped deltaTime to prevent large jumps
        val clampedDeltaTime = deltaTime.coerceIn(0f, 0.016f) // Max 16ms frame time
        var newX = ball.centerX + ball.velocityX * clampedDeltaTime
        var newY = ball.centerY + ball.velocityY * clampedDeltaTime
        
        var newVelocityX = ball.velocityX
        var newVelocityY = ball.velocityY
        
        // Cache ball radius for repeated use
        val ballRadius = ball.radius
        
        // Wall collision checks with early exit optimization
        if (newX - ballRadius < 0) {
            newX = ballRadius
            newVelocityX = -newVelocityX * 0.98f // Slight energy loss for realism
        } else if (newX + ballRadius > canvasWidth) {
            newX = canvasWidth - ballRadius
            newVelocityX = -newVelocityX * 0.98f
        }
        
        // Top wall collision
        if (newY - ballRadius < 0) {
            newY = ballRadius
            newVelocityY = -newVelocityY * 0.98f
        }
        
        // Paddle collision with improved accuracy
        if (paddle != null && newVelocityY > 0) {  // Ball moving down
            val paddleHalfWidth = paddle.width * 0.5f
            val paddleLeft = paddle.centerX - paddleHalfWidth
            val paddleRight = paddle.centerX + paddleHalfWidth
            
            if (newY + ballRadius >= cachedPaddleTop && 
                newY - ballRadius <= canvasHeight &&
                newX >= paddleLeft && 
                newX <= paddleRight) {
                
                // Calculate bounce angle based on hit position (normalized -1 to 1)
                val hitPosition = (newX - paddle.centerX) / paddleHalfWidth
                val bounceAngle = hitPosition * 1.2f // Max ~69 degree angle
                
                // Use cached speed calculation to avoid sqrt
                val speedSquared = newVelocityX * newVelocityX + newVelocityY * newVelocityY
                val speed = if (speedSquared > 0) {
                    sqrt(speedSquared) * 1.02f // Slight speed increase
                } else {
                    500f // Fallback speed
                }
                
                // More responsive paddle physics
                newVelocityX = speed * bounceAngle * 0.7f
                newVelocityY = -speed * 0.8f // Ensure upward movement
                
                // Position ball above paddle with small buffer
                newY = cachedPaddleTop - ballRadius - 2f
            }
        }
        
        // Clear reusable collections
        reusableBrickList.clear()
        reusableDestroyedList.clear()
        
        // Optimized brick collision detection
        var hasCollision = false
        
        // Early exit if ball is moving away from brick area
        if (newY > canvasHeight * 0.6f && newVelocityY > 0) {
            // Ball is in lower area and moving down - no brick collisions possible
            reusableBrickList.addAll(bricks)
        } else {
            // Use spatial optimization - only check nearby bricks
            val ballRadiusSquared = ballRadius * ballRadius
            
            for (brick in bricks) {
                // Quick AABB check first (faster than circle-rect)
                val brickBounds = brick.bounds
                if (newX + ballRadius < brickBounds.left || 
                    newX - ballRadius > brickBounds.right ||
                    newY + ballRadius < brickBounds.top ||
                    newY - ballRadius > brickBounds.bottom) {
                    
                    // No collision possible
                    reusableBrickList.add(brick)
                    continue
                }
                
                // More accurate circle-rectangle collision
                if (circleRectCollisionOptimized(newX, newY, ballRadiusSquared, brickBounds)) {
                    hasCollision = true
                    
                    // Determine collision side using distance-based approach
                    val centerX = brickBounds.centerX()
                    val centerY = brickBounds.centerY()
                    val deltaX = newX - centerX
                    val deltaY = newY - centerY
                    
                    // Use aspect ratio to determine primary collision axis
                    val brickWidth = brickBounds.width()
                    val brickHeight = brickBounds.height()
                    
                    if (abs(deltaX / brickWidth) > abs(deltaY / brickHeight)) {
                        // Horizontal collision
                        newVelocityX = -newVelocityX
                        newX = if (deltaX > 0) brickBounds.right + ballRadius + 1f 
                               else brickBounds.left - ballRadius - 1f
                    } else {
                        // Vertical collision
                        newVelocityY = -newVelocityY
                        newY = if (deltaY > 0) brickBounds.bottom + ballRadius + 1f 
                               else brickBounds.top - ballRadius - 1f
                    }
                    
                    // Apply damage to brick
                    val newHealth = brick.health - ball.damageMultiplier.toInt()
                    if (newHealth <= 0) {
                        reusableDestroyedList.add(brick)
                    } else {
                        reusableBrickList.add(brick.copy(health = newHealth))
                    }
                    
                    // Process only first collision per frame for performance
                    break
                } else {
                    reusableBrickList.add(brick)
                }
            }
        }
        
        // Velocity damping to prevent infinite acceleration
        newVelocityX = newVelocityX.coerceIn(-1200f, 1200f)
        newVelocityY = newVelocityY.coerceIn(-1200f, 1200f)
        
        // Create updated ball
        val updatedBall = ball.copy(
            centerX = newX,
            centerY = newY,
            velocityX = newVelocityX,
            velocityY = newVelocityY
        )
        
        return CollisionResult(
            updatedBall = updatedBall,
            updatedBricks = reusableBrickList.toList(),
            destroyedBricks = reusableDestroyedList.toList()
        )
    }
    
    // Optimized collision detection using squared distances
    private fun circleRectCollisionOptimized(
        circleX: Float,
        circleY: Float,
        radiusSquared: Float,
        rect: RectF
    ): Boolean {
        // Find closest point on rectangle to circle center
        val closestX = circleX.coerceIn(rect.left, rect.right)
        val closestY = circleY.coerceIn(rect.top, rect.bottom)
        
        // Calculate squared distance (avoid sqrt)
        val deltaX = circleX - closestX
        val deltaY = circleY - closestY
        val distanceSquared = deltaX * deltaX + deltaY * deltaY
        
        return distanceSquared <= radiusSquared
    }
    
    // Original method kept for compatibility
    private fun circleRectCollision(
        circleX: Float,
        circleY: Float,
        radius: Float,
        rect: RectF
    ): Boolean {
        return circleRectCollisionOptimized(circleX, circleY, radius * radius, rect)
    }
}