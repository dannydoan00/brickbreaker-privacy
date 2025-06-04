package com.boltgame.brickbreakerroguelite.game.level

import android.graphics.RectF
import com.boltgame.brickbreakerroguelite.data.model.Brick
import com.boltgame.brickbreakerroguelite.data.model.BrickType
import java.util.UUID
import kotlin.math.min
import kotlin.random.Random

interface LevelGenerator {
    fun generateLevel(level: Int, width: Float, height: Float): List<Brick>
}

class ProceduralLevelGenerator : LevelGenerator {
    
    override fun generateLevel(level: Int, width: Float, height: Float): List<Brick> {
        val bricks = mutableListOf<Brick>()
        
        // Adjust difficulty based on level
        val rows = min(3 + level / 2, 10)
        val cols = min(5 + level / 3, 12)
        
        // Calculate brick dimensions based on screen size
        val padding = 8f
        val brickWidth = (width - (cols + 1) * padding) / cols
        val brickHeight = (height * 0.6f - (rows + 1) * padding) / rows
        val startY = height * 0.1f
        
        // Increase health with level
        val baseHealth = 1 + level / 3
        
        // Increase special brick chance with level
        val toughBrickChance = min(0.05f * level, 0.4f)
        val explosiveBrickChance = min(0.02f * level, 0.2f)
        val powerupBrickChance = min(0.1f + 0.02f * level, 0.3f)
        
        // Generate brick layout
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                // Skip some bricks randomly to create interesting patterns
                // Higher levels have fewer gaps
                val gapChance = 0.2f - min(0.02f * level, 0.18f)
                if (Random.nextFloat() < gapChance) continue
                
                val left = padding + col * (brickWidth + padding)
                val top = startY + row * (brickHeight + padding)
                val right = left + brickWidth
                val bottom = top + brickHeight
                
                // Determine brick type
                val brickType = when {
                    Random.nextFloat() < toughBrickChance -> BrickType.TOUGH
                    Random.nextFloat() < explosiveBrickChance -> BrickType.EXPLOSIVE
                    Random.nextFloat() < powerupBrickChance -> BrickType.POWERUP
                    else -> BrickType.NORMAL
                }
                
                // Determine brick health based on type and level
                val health = when (brickType) {
                    BrickType.TOUGH -> baseHealth * 2
                    BrickType.EXPLOSIVE -> baseHealth
                    BrickType.POWERUP -> baseHealth
                    BrickType.NORMAL -> baseHealth
                }
                
                // Determine power-up type if applicable
                val powerUpType = if (brickType == BrickType.POWERUP) {
                    // In a real game, this would select a power-up type from available options
                    // For simplicity, we'll just use a placeholder
                    "random_powerup"
                } else null
                
                // Create brick
                val brick = Brick(
                    id = "brick_${UUID.randomUUID()}",
                    type = brickType,
                    health = health,
                    bounds = RectF(left, top, right, bottom),
                    powerUpType = powerUpType
                )
                
                bricks.add(brick)
            }
        }
        
        return bricks
    }
}