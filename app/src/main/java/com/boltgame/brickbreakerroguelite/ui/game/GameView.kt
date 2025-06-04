package com.boltgame.brickbreakerroguelite.ui.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.boltgame.brickbreakerroguelite.data.model.Ball
import com.boltgame.brickbreakerroguelite.data.model.Brick
import com.boltgame.brickbreakerroguelite.data.model.BrickType
import com.boltgame.brickbreakerroguelite.data.model.Paddle
import com.boltgame.brickbreakerroguelite.data.model.PowerUp
import com.boltgame.brickbreakerroguelite.game.engine.GameState

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {
    
    private var gameThread: GameThread? = null
    private var lastFrameTime = System.currentTimeMillis()
    
    // Rendering components
    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#121212")
    }
    
    private val paddlePaint = Paint().apply {
        color = Color.parseColor("#4285F4") // Google Blue
        isAntiAlias = true
    }
    
    private val ballPaint = Paint().apply {
        color = Color.parseColor("#FFFFFF")
        isAntiAlias = true
    }
    
    private val normalBrickPaint = Paint().apply {
        color = Color.parseColor("#EA4335") // Google Red
        isAntiAlias = true
    }
    
    private val toughBrickPaint = Paint().apply {
        color = Color.parseColor("#FBBC05") // Google Yellow
        isAntiAlias = true
    }
    
    private val explosiveBrickPaint = Paint().apply {
        color = Color.parseColor("#34A853") // Google Green
        isAntiAlias = true
    }
    
    private val powerupBrickPaint = Paint().apply {
        color = Color.parseColor("#AB47BC") // Purple
        isAntiAlias = true
    }
    
    private val powerupPaint = Paint().apply {
        color = Color.parseColor("#7C4DFF") // Deep Purple
        isAntiAlias = true
    }
    
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        isAntiAlias = true
    }
    
    private var currentGameState: GameState? = null
    private var onPaddleMoved: ((Float) -> Unit)? = null
    
    init {
        holder.addCallback(this)
        isFocusable = true
    }
    
    override fun surfaceCreated(holder: SurfaceHolder) {
        gameThread = GameThread(holder).also {
            it.running = true
            it.start()
        }
    }
    
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Nothing specific needed here
    }
    
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        gameThread?.running = false
        try {
            gameThread?.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }
    
    fun setOnPaddleMovedListener(listener: (Float) -> Unit) {
        onPaddleMoved = listener
    }
    
    fun updateGameState(gameState: GameState) {
        currentGameState = gameState
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_DOWN) {
            onPaddleMoved?.invoke(event.x)
            return true
        }
        return super.onTouchEvent(event)
    }
    
    private fun render(canvas: Canvas) {
        val gameState = currentGameState ?: return
        
        // Clear background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        
        // Draw paddle
        gameState.paddle?.let { paddle ->
            canvas.drawRoundRect(
                paddle.centerX - paddle.width / 2,
                height - paddle.height,
                paddle.centerX + paddle.width / 2,
                height.toFloat(),
                16f, 16f,
                paddlePaint
            )
        }
        
        // Draw balls
        for (ball in gameState.balls) {
            canvas.drawCircle(
                ball.centerX,
                ball.centerY,
                ball.radius,
                ballPaint
            )
        }
        
        // Draw bricks
        for (brick in gameState.bricks) {
            val paint = when (brick.type) {
                BrickType.NORMAL -> normalBrickPaint
                BrickType.TOUGH -> toughBrickPaint
                BrickType.EXPLOSIVE -> explosiveBrickPaint
                BrickType.POWERUP -> powerupBrickPaint
            }
            
            canvas.drawRoundRect(
                brick.bounds,
                8f, 8f,
                paint
            )
        }
        
        // Draw power-ups
        for (powerUp in gameState.powerUps) {
            canvas.drawCircle(
                powerUp.centerX,
                powerUp.centerY,
                powerUp.radius,
                powerupPaint
            )
        }
        
        // Draw game info
        val runState = gameState.runState
        canvas.drawText("Score: ${runState.score}", 20f, 60f, textPaint)
        canvas.drawText("Level: ${runState.level}", 20f, 120f, textPaint)
        canvas.drawText("Lives: ${runState.lives}", 20f, 180f, textPaint)
    }
    
    private inner class GameThread(private val surfaceHolder: SurfaceHolder) : Thread() {
        var running = false
        
        override fun run() {
            while (running) {
                val currentTime = System.currentTimeMillis()
                val deltaTime = (currentTime - lastFrameTime) / 1000f
                lastFrameTime = currentTime
                
                val canvas = surfaceHolder.lockCanvas() ?: continue
                try {
                    synchronized(surfaceHolder) {
                        render(canvas)
                    }
                } finally {
                    surfaceHolder.unlockCanvasAndPost(canvas)
                }
                
                // Limit frame rate to ~60fps
                val frameTime = System.currentTimeMillis() - currentTime
                if (frameTime < 16) {
                    try {
                        sleep(16 - frameTime)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}