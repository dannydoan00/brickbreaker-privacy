package com.boltgame.brickbreakerroguelite.data.model

import android.graphics.RectF

enum class BrickType {
    NORMAL,
    TOUGH,
    EXPLOSIVE,
    POWERUP
}

data class Brick(
    val id: String,
    val type: BrickType,
    val health: Int,
    val bounds: RectF,
    val powerUpType: String? = null
)

data class Ball(
    val id: String,
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val velocityX: Float,
    val velocityY: Float,
    val damageMultiplier: Float = 1f,
    val skin: String = "default"
)

data class Paddle(
    val centerX: Float,
    val width: Float,
    val height: Float,
    val skin: String = "default"
)

data class PowerUp(
    val id: String,
    val type: PowerUpType,
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val velocityY: Float
)

enum class PowerUpType {
    MULTI_BALL,
    EXPAND_PADDLE,
    SHRINK_PADDLE,
    SPEED_UP,
    SLOW_DOWN,
    EXTRA_LIFE,
    FIREBALL,
    STICKY_PADDLE,
    LASER_PADDLE
}