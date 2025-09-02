package com.example.shadowdash

import android.content.Context
import android.graphics.*
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.MotionEvent
import kotlin.random.Random
import kotlin.math.sin
import com.example.shadowdash.R

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private val thread: GameThread
    private var playerY = 0f
    private var velocity = 0f
    private val gravity = 1.5f
    private val jumpForce = -28f

    // Paints Shadow Theme
    private val playerBodyPaint = Paint().apply {
        color = Color.rgb(0, 200, 255)
        isAntiAlias = true
        setShadowLayer(10f, 0f, 0f, Color.CYAN)
    }
    
    private val playerEyePaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
    }
    
    private val playerPupilPaint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = true
    }

    private val glowPaint = Paint().apply {
        color = Color.CYAN
        isAntiAlias = true
        setShadowLayer(20f, 0f, 0f, Color.CYAN)
    }

    private val obstaclePaint = Paint().apply {
        color = Color.rgb(255, 50, 50)
        isAntiAlias = true
        setShadowLayer(15f, 0f, 0f, Color.RED)
    }
    
    private val spikesPaint = Paint().apply {
        color = Color.rgb(200, 0, 0)
        isAntiAlias = true
        setShadowLayer(8f, 0f, 0f, Color.RED)
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 50f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
        setShadowLayer(5f, 2f, 2f, Color.BLACK)
    }

    private val titlePaint = Paint().apply {
        color = Color.CYAN
        textSize = 80f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
        setShadowLayer(10f, 0f, 0f, Color.BLUE)
    }
    
    private val buttonPaint = Paint().apply {
        color = Color.rgb(0, 150, 255)
        isAntiAlias = true
        setShadowLayer(8f, 0f, 0f, Color.CYAN)
    }

    // Game objects
    private var playerX = 200f
    private val playerSize = 300f
    private val groundY by lazy { height - 120f }
    
    // Movement
    private var playerVelocityX = 0f
    private val moveSpeed = 8f
    
    // Animation
    private var animFrame = 0f
    private var isJumping = false
    
    // Player sprite
    private val playerBitmap by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.player_sprite)
    }
    
    // Rock obstacle sprite
    private val rockBitmap by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.rock_obstacle)
    }
    
    // Controller button sprite
    private val controllerBitmap by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.controller_button)
    }

    private val obstacles = mutableListOf<RectF>()
    private var obstacleSpeed = 15f
    private var spawnCounter = 0

    // Parallax layers
    private var bg1Offset = 0f
    private var bg2Offset = 0f
    private var bg3Offset = 0f

    private var score = 0
    private var gameOver = false
    private var gameStarted = false

    // Particle system
    private val particles = mutableListOf<Particle>()
    
    // UI Button
    private val buttonSize = 120f
    private val buttonMargin = 50f

    init {
        holder.addCallback(this)
        thread = GameThread(holder, this)
        setLayerType(LAYER_TYPE_SOFTWARE, null) // Pour les shadows
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        thread.setRunning(true)
        thread.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        var retry = true
        thread.setRunning(false)
        while (retry) {
            try {
                thread.join()
                retry = false
            } catch (_: InterruptedException) {}
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val centerX = width - 150f
            val centerY = height / 2f
            val spacing = 100f
            val touchX = event.x
            val touchY = event.y
            
            // Zones des 4 flèches
            val upButton = (touchX >= centerX - 50 && touchX <= centerX + 50 &&
                           touchY >= centerY - spacing - 50 && touchY <= centerY - spacing + 50)
            val downButton = (touchX >= centerX - 50 && touchX <= centerX + 50 &&
                             touchY >= centerY + spacing - 50 && touchY <= centerY + spacing + 50)
            val leftButton = (touchX >= centerX - spacing - 50 && touchX <= centerX - spacing + 50 &&
                             touchY >= centerY - 50 && touchY <= centerY + 50)
            val rightButton = (touchX >= centerX + spacing - 50 && touchX <= centerX + spacing + 50 &&
                              touchY >= centerY - 50 && touchY <= centerY + 50)
            
            if (!gameStarted) {
                gameStarted = true
                resetGame()
            } else if (gameOver) {
                resetGame()
            } else {
                when {
                    upButton -> { // Saut
                        if (playerY >= groundY - 5) {
                            velocity = jumpForce
                            repeat(8) {
                                particles.add(Particle(playerX, playerY + playerSize/2, Random.nextFloat() * 12 - 6, Random.nextFloat() * -8))
                            }
                        }
                    }
                    leftButton -> { // Aller à gauche
                        playerVelocityX = -moveSpeed
                    }
                    rightButton -> { // Aller à droite
                        playerVelocityX = moveSpeed
                    }
                    downButton -> { // Descendre plus vite
                        if (velocity < 0) velocity = 5f
                    }
                    else -> { // Tap général pour sauter
                        if (playerY >= groundY - 5) {
                            velocity = jumpForce
                            repeat(8) {
                                particles.add(Particle(playerX, playerY + playerSize/2, Random.nextFloat() * 12 - 6, Random.nextFloat() * -8))
                            }
                        }
                    }
                }
            }
        } else if (event.action == MotionEvent.ACTION_UP) {
            // Arrêter le mouvement horizontal quand on relâche
            playerVelocityX = 0f
        }
        return true
    }

    fun update() {
        if (!gameStarted || gameOver) return

        // Mouvement horizontal
        playerX += playerVelocityX
        if (playerX < 0) playerX = 0f
        if (playerX > width - playerSize) playerX = width - playerSize

        // Gravité
        velocity += gravity
        playerY += velocity
        
        // Animation
        animFrame += 0.3f
        isJumping = velocity < 0

        // Sol
        if (playerY > groundY) {
            playerY = groundY
            velocity = 0f
            isJumping = false
        }

        // Parallax
        bg1Offset -= 2f
        bg2Offset -= 6f
        bg3Offset -= 12f

        // Obstacles
        spawnCounter++
        if (spawnCounter > 80 - (score / 500)) { // Plus rapide avec le temps
            val size = Random.nextInt(60, 120)
            obstacles.add(RectF(width.toFloat(), groundY - size, width + 80f, groundY))
            spawnCounter = 0
        }

        val iterator = obstacles.iterator()
        while (iterator.hasNext()) {
            val rect = iterator.next()
            rect.offset(-obstacleSpeed, 0f)

            // Collision
            if (RectF.intersects(rect, RectF(playerX - playerSize/2, playerY - playerSize/2, playerX + playerSize/2, playerY + playerSize/2))) {
                gameOver = true
                // Explosion de particules
                repeat(15) {
                    particles.add(Particle(playerX, playerY, Random.nextFloat() * 20 - 10, Random.nextFloat() * 20 - 10))
                }
            }

            if (rect.right < 0) {
                iterator.remove()
                score += 10
            }
        }

        // Particules
        particles.removeAll { particle ->
            particle.update()
            particle.life <= 0
        }

        score++
        obstacleSpeed = 15f + (score / 1000f) // Accélération progressive
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        // Fond dégradé sombre avec étoiles
        val gradient = LinearGradient(0f, 0f, 0f, height.toFloat(), 
            Color.rgb(5, 5, 20), Color.rgb(20, 5, 40), Shader.TileMode.CLAMP)
        val bgPaint = Paint().apply { shader = gradient }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        
        // Étoiles scintillantes
        val starPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }
        for (i in 0..30) {
            val x = (i * 137 + bg1Offset * 0.1f) % width
            val y = (i * 73) % (height * 0.7f)
            val twinkle = sin(animFrame + i) * 0.5f + 0.5f
            starPaint.alpha = (twinkle * 255).toInt()
            canvas.drawCircle(x, y, 2f, starPaint)
        }

        if (!gameStarted) {
            // Écran titre
            val title = "SHADOW DASH"
            val titleWidth = titlePaint.measureText(title)
            canvas.drawText(title, (width - titleWidth) / 2, height / 3f, titlePaint)
            
            val subtitle = "Tap to Start"
            val subtitleWidth = textPaint.measureText(subtitle)
            canvas.drawText(subtitle, (width - subtitleWidth) / 2, height / 2f, textPaint)
            return
        }

        // Parallax layers
        drawParallaxLayer(canvas, bg1Offset, Color.rgb(20, 20, 40), 0.5f)
        drawParallaxLayer(canvas, bg2Offset, Color.rgb(40, 20, 60), 0.3f)
        drawParallaxLayer(canvas, bg3Offset, Color.rgb(60, 30, 80), 0.1f)

        // Sol avec effet néon
        val groundPaint = Paint().apply {
            color = Color.rgb(0, 100, 150)
            setShadowLayer(15f, 0f, -5f, Color.CYAN)
        }
        canvas.drawRect(0f, groundY, width.toFloat(), height.toFloat(), groundPaint)

        // Ligne néon au sol
        val neonPaint = Paint().apply {
            color = Color.CYAN
            strokeWidth = 3f
            setShadowLayer(10f, 0f, 0f, Color.CYAN)
        }
        canvas.drawLine(0f, groundY, width.toFloat(), groundY, neonPaint)

        // Joueur (personnage animé)
        drawPlayer(canvas)

        // Obstacles avec rochers
        for (rect in obstacles) {
            drawObstacle(canvas, rect)
        }

        // Particules colorées
        for (particle in particles) {
            val particlePaint = Paint().apply {
                color = when {
                    particle.life > 0.7f -> Color.CYAN
                    particle.life > 0.4f -> Color.BLUE
                    else -> Color.WHITE
                }
                alpha = (particle.life * 255).toInt()
                isAntiAlias = true
                setShadowLayer(5f, 0f, 0f, color)
            }
            canvas.drawCircle(particle.x, particle.y, 4f, particlePaint)
        }

        // UI
        canvas.drawText("Score: $score", 30f, 80f, textPaint)
        canvas.drawText("Speed: ${String.format("%.1f", obstacleSpeed)}", 30f, 140f, textPaint)
        
        // Bouton flèche en bas à droite
        if (gameStarted && !gameOver) {
            drawJumpButton(canvas)
        }

        if (gameOver) {
            val msg = "GAME OVER"
            val msgWidth = titlePaint.measureText(msg)
            canvas.drawText(msg, (width - msgWidth) / 2, height / 2f - 50, titlePaint)
            
            val restart = "Tap to Restart"
            val restartWidth = textPaint.measureText(restart)
            canvas.drawText(restart, (width - restartWidth) / 2, height / 2f + 50, textPaint)
        }
    }

    private fun drawPlayer(canvas: Canvas) {
        val bounce = if (isJumping) sin(animFrame * 2) * 8 else sin(animFrame) * 4
        val bodyY = playerY + bounce
        
        // Rotation pour l'effet de saut
        val rotation = if (isJumping) sin(animFrame * 3) * 15 else 0f
        
        canvas.save()
        canvas.translate(playerX, bodyY)
        canvas.rotate(rotation)
        
        // Dessiner le sprite avec fond transparent
        val spriteRect = RectF(-playerSize/2, -playerSize/2, playerSize/2, playerSize/2)
        canvas.drawBitmap(playerBitmap, null, spriteRect, null)
        
        canvas.restore()
    }
    
    private fun drawObstacle(canvas: Canvas, rect: RectF) {
        // Dessiner le rocher à la place du carré rouge
        canvas.drawBitmap(rockBitmap, null, rect, null)
    }

    private fun drawJumpButton(canvas: Canvas) {
        val centerX = width - 150f // Sur la droite
        val centerY = height / 2f
        val arrowSize = 60f
        val spacing = 100f
        
        val arrowPaint = Paint().apply {
            color = Color.WHITE
            strokeWidth = 12f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
            setShadowLayer(8f, 0f, 0f, Color.BLACK)
        }
        
        val bgPaint = Paint().apply {
            color = Color.rgb(0, 100, 200)
            isAntiAlias = true
            setShadowLayer(10f, 0f, 0f, Color.BLUE)
        }
        
        // Flèche HAUT
        val upX = centerX
        val upY = centerY - spacing
        canvas.drawCircle(upX, upY, 50f, bgPaint)
        canvas.drawLine(upX, upY + 25, upX, upY - 25, arrowPaint)
        canvas.drawLine(upX, upY - 25, upX - 15, upY - 10, arrowPaint)
        canvas.drawLine(upX, upY - 25, upX + 15, upY - 10, arrowPaint)
        
        // Flèche BAS
        val downX = centerX
        val downY = centerY + spacing
        canvas.drawCircle(downX, downY, 50f, bgPaint)
        canvas.drawLine(downX, downY - 25, downX, downY + 25, arrowPaint)
        canvas.drawLine(downX, downY + 25, downX - 15, downY + 10, arrowPaint)
        canvas.drawLine(downX, downY + 25, downX + 15, downY + 10, arrowPaint)
        
        // Flèche GAUCHE
        val leftX = centerX - spacing
        val leftY = centerY
        canvas.drawCircle(leftX, leftY, 50f, bgPaint)
        canvas.drawLine(leftX + 25, leftY, leftX - 25, leftY, arrowPaint)
        canvas.drawLine(leftX - 25, leftY, leftX - 10, leftY - 15, arrowPaint)
        canvas.drawLine(leftX - 25, leftY, leftX - 10, leftY + 15, arrowPaint)
        
        // Flèche DROITE
        val rightX = centerX + spacing
        val rightY = centerY
        canvas.drawCircle(rightX, rightY, 50f, bgPaint)
        canvas.drawLine(rightX - 25, rightY, rightX + 25, rightY, arrowPaint)
        canvas.drawLine(rightX + 25, rightY, rightX + 10, rightY - 15, arrowPaint)
        canvas.drawLine(rightX + 25, rightY, rightX + 10, rightY + 15, arrowPaint)
    }

    private fun drawParallaxLayer(canvas: Canvas, offset: Float, color: Int, alpha: Float) {
        val paint = Paint().apply {
            this.color = color
            this.alpha = (alpha * 255).toInt()
        }
        
        val layerHeight = 100f
        val y = groundY - layerHeight
        
        for (i in 0..3) {
            val x = (offset % (width * 2)) + (i * width / 2)
            canvas.drawRect(x, y, x + width / 2f, groundY, paint)
        }
    }

    private fun resetGame() {
        playerX = 200f
        playerY = groundY
        velocity = 0f
        playerVelocityX = 0f
        obstacles.clear()
        particles.clear()
        score = 0
        spawnCounter = 0
        obstacleSpeed = 15f
        gameOver = false
    }

    data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float = 1f) {
        fun update() {
            x += vx
            y += vy
            vy += 0.5f // Gravité
            life -= 0.02f
        }
    }
}
