package me.lowerkey

import java.awt.*
import java.awt.event.*
import java.awt.image.BufferStrategy
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import javax.swing.JFrame
import javax.swing.JOptionPane
import kotlin.math.*
import kotlin.system.exitProcess

class Game(
    val gameWidth: Int,
    val gameHeight: Int,
    val scale: Int = 3
) : Canvas(), Runnable, KeyListener, MouseMotionListener, MouseListener {

    private var running = false
    private var thread: Thread? = null
    private val gameBuffer = BufferedImage(gameWidth, gameHeight, BufferedImage.TYPE_INT_RGB)
    private val pixels = (gameBuffer.raster.dataBuffer as java.awt.image.DataBufferInt).data

    private val FOV = 90.0
    private val hFovRadians = FOV * PI / 180.0
    private val halfW = tan(hFovRadians / 2.0)
    private val halfH = halfW / (gameWidth.toDouble() / gameHeight.toDouble())

    val world = World()

    private val planeX = FloatArray(gameWidth * gameHeight)
    private val planeY = FloatArray(gameWidth * gameHeight)

    private val MAX_DISTANCE = 20.0
    private val MOUSE_SENS = 0.002
    private val GRAVITY = -0.012
    private val JUMP_FORCE = 0.2

    private val MAX_SPEED = 0.15
    private val GROUND_ACCEL = 0.06
    private val AIR_ACCEL = 0.03
    private val GROUND_FRICTION = 0.75
    private val AIR_FRICTION = 0.85

    private val PLAYER_W = 0.3
    private val PLAYER_H = 1.8

    var playerPos = Vecd(0.0, 10.0, 0.0)
    var playerYaw = 0.0
    var playerPitch = 0.0
    var velocityX = 0.0
    var velocityY = 0.0
    var velocityZ = 0.0
    var onGround = false

    private var coyoteFrames = 0
    private val COYOTE_TIME = 8

    private var jumpBufferFrames = 0
    private val JUMP_BUFFER_TIME = 10

    private val keys = mutableSetOf<Int>()

    private lateinit var robot: Robot
    private var centerX = 0
    private var centerY = 0
    private var ignoreMouse = false

    private var targetResult: World.RaycastResult? = null
    private var leftClick = false
    private var rightClick = false

    private var standingOn: Block? = null

    init {
        Game.width = gameWidth
        Game.height = gameHeight

        for (py in 0 until gameHeight) {
            for (px in 0 until gameWidth) {
                val i = py * gameWidth + px
                planeX[i] = (((px + 0.5) / gameWidth - 0.5) * 2.0 * halfW).toFloat()
                planeY[i] = ((0.5 - (py + 0.5) / gameHeight) * 2.0 * halfH).toFloat()
            }
        }

        addKeyListener(this)
        addMouseMotionListener(this)
        addMouseListener(this)

        isFocusable = true

        startAmbientMusic()
    }

    private val pool = java.util.concurrent.Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors()
    )

    override fun getPreferredSize(): Dimension = Dimension(gameWidth * scale, gameHeight * scale)

    fun start() {
        running = true
        thread = Thread(this).also { it.start() }
    }

    private fun initCursorLock() {
        robot = Robot()
        val loc = locationOnScreen
        centerX = loc.x + width / 2
        centerY = loc.y + height / 2

        val blank = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        cursor = toolkit.createCustomCursor(blank, Point(0, 0), "blank")

        robot.mouseMove(centerX, centerY)
    }

    override fun run() {
        createBufferStrategy(3)
        initCursorLock()
        val bs = bufferStrategy
        val nsPerFrame = 1_000_000_000L / 60L

        try {
            while (running) {
                val t0 = System.nanoTime()
                update()
                render(bs)
                val sleep = (nsPerFrame - (System.nanoTime() - t0)) / 1_000_000L
                if (sleep > 0) Thread.sleep(sleep)
            }
        } finally {
            pool.shutdown()
        }
    }

    fun update() {
        frames++
        world.updateChunks(playerPos.x, playerPos.z)
        world.update()

        val fwdX = sin(playerYaw)
        val fwdZ = cos(playerYaw)

        var wishX = 0.0
        var wishZ = 0.0
        if (KeyEvent.VK_W in keys) { wishX += fwdX; wishZ += fwdZ }
        if (KeyEvent.VK_S in keys) { wishX -= fwdX; wishZ -= fwdZ }
        if (KeyEvent.VK_A in keys) { wishX -= fwdZ; wishZ += fwdX }
        if (KeyEvent.VK_D in keys) { wishX += fwdZ; wishZ -= fwdX }

        val wishLen = sqrt(wishX * wishX + wishZ * wishZ)
        val hasInput = wishLen > 0.0
        if (hasInput) {
            wishX /= wishLen
            wishZ /= wishLen
        }

        if (onGround) {
            if (hasInput) {
                val accel = GROUND_ACCEL
                velocityX += wishX * accel
                velocityZ += wishZ * accel

                val spd = sqrt(velocityX * velocityX + velocityZ * velocityZ)
                if (spd > MAX_SPEED) {
                    velocityX = velocityX / spd * MAX_SPEED
                    velocityZ = velocityZ / spd * MAX_SPEED
                }

                velocityX *= 0.90
                velocityZ *= 0.90
            } else {
                velocityX *= GROUND_FRICTION
                velocityZ *= GROUND_FRICTION
                if (abs(velocityX) < 0.001) velocityX = 0.0
                if (abs(velocityZ) < 0.001) velocityZ = 0.0
            }
        } else {
            if (hasInput) {
                val projectedSpeed = velocityX * wishX + velocityZ * wishZ
                val addSpeed = (MAX_SPEED - projectedSpeed).coerceIn(0.0, AIR_ACCEL)
                velocityX += wishX * addSpeed
                velocityZ += wishZ * addSpeed
            }
            velocityX *= AIR_FRICTION
            velocityZ *= AIR_FRICTION
        }

        if (onGround) {
            coyoteFrames = COYOTE_TIME
        } else if (coyoteFrames > 0) {
            coyoteFrames--
        }

        if (KeyEvent.VK_SPACE in keys) {
            jumpBufferFrames = JUMP_BUFFER_TIME
        } else if (jumpBufferFrames > 0) {
            jumpBufferFrames--
        }

        if (jumpBufferFrames > 0 && coyoteFrames > 0) {
            velocityY = JUMP_FORCE
            onGround = false
            coyoteFrames = 0
            jumpBufferFrames = 0
        }

        velocityY += GRAVITY

        playerPos = moveWithCollision(velocityX, 0.0, 0.0)
        playerPos = moveWithCollision(0.0, velocityY, 0.0)
        playerPos = moveWithCollision(0.0, 0.0, velocityZ)

        val moving = abs(velocityX) > 0.01 || abs(velocityZ) > 0.01
        if (moving) {
            if (frames % 25 == 0)
                standingOn?.getMaterial()?.let { playFootstep(it) }
        }

        playerPitch = playerPitch.coerceIn(-PI / 2 + 0.01, PI / 2 - 0.01)

        val camPos = Vecd(playerPos.x, playerPos.y + 1.5, playerPos.z)

        targetResult = world.raycast(camPos, playerPitch, playerYaw, maxDistance = 5.0)

        val hit = targetResult
        if (hit != null) {
            if (leftClick) {
                val block = world.blocks[hit.blockPos]
                world.removeBlock(hit.blockPos)
                block?.onBreak()
            }
            if (rightClick) {
                val n = hit.normal
                val placePos = BlockPos(hit.blockPos.x + n.x, hit.blockPos.y + n.y, hit.blockPos.z + n.z)

                val px = placePos.x.toDouble()
                val py = placePos.y.toDouble()
                val pz = placePos.z.toDouble()
                val overlapX = px + 1 > playerPos.x - PLAYER_W && px < playerPos.x + PLAYER_W
                val overlapY = py + 1 > playerPos.y && py < playerPos.y + PLAYER_H
                val overlapZ = pz + 1 > playerPos.z - PLAYER_W && pz < playerPos.z + PLAYER_W

                if (!(overlapX && overlapY && overlapZ)) {
                    val block = Block(world)
                    world.setBlock(placePos, block)
                    block.onPlace()
                }
            }
        }

        leftClick = false
        rightClick = false

        if (playerPos.y < -10) {
            JOptionPane.showMessageDialog(null, "you were eaten")
            exitProcess(0)
        }
    }

    private fun moveWithCollision(dx: Double, dy: Double, dz: Double): Vecd {
        var nx = playerPos.x + dx
        var ny = playerPos.y + dy
        var nz = playerPos.z + dz

        val minX = nx - PLAYER_W
        val maxX = nx + PLAYER_W
        val minY = ny
        val maxY = ny + PLAYER_H
        val minZ = nz - PLAYER_W
        val maxZ = nz + PLAYER_W

        val bx0 = floor(minX).toInt()
        val bx1 = floor(maxX).toInt()
        val by0 = floor(minY).toInt()
        val by1 = floor(maxY).toInt()
        val bz0 = floor(minZ).toInt()
        val bz1 = floor(maxZ).toInt()

        for (bx in bx0..bx1) for (by in by0..by1) for (bz in bz0..bz1) {
            world.blocks[BlockPos(bx, by, bz)] ?: continue

            val blkMinX = bx.toDouble()
            val blkMaxX = bx + 1.0
            val blkMinY = by.toDouble()
            val blkMaxY = by + 1.0
            val blkMinZ = bz.toDouble()
            val blkMaxZ = bz + 1.0

            if (maxX <= blkMinX || minX >= blkMaxX) continue
            if (maxY <= blkMinY || minY >= blkMaxY) continue
            if (maxZ <= blkMinZ || minZ >= blkMaxZ) continue

            if (dy != 0.0) {
                if (dy < 0.0) {
                    ny = blkMaxY
                    velocityY = 0.0
                    onGround = true
                    standingOn = world.blocks[BlockPos(bx, by, bz)]
                } else {
                    ny = blkMinY - PLAYER_H
                    velocityY = 0.0
                }
            }

            if (dx != 0.0) {
                nx = if (dx < 0.0) blkMaxX + PLAYER_W else blkMinX - PLAYER_W
            }

            if (dz != 0.0) {
                nz = if (dz < 0.0) blkMaxZ + PLAYER_W else blkMinZ - PLAYER_W
            }
        }

        if (dy < 0.0 && ny == playerPos.y + dy) {
            onGround = false
            standingOn = null
        }

        return Vecd(nx, ny, nz)
    }

    fun render(bs: BufferStrategy) {
        drawGame()
        val g = bs.drawGraphics as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        g.drawImage(gameBuffer, 0, 0, gameWidth * scale, gameHeight * scale, null)
        g.dispose()
        bs.show()
        Toolkit.getDefaultToolkit().sync()
    }

    private fun drawGame() {
        pixels.fill(0)

        val camPos = Vecd(playerPos.x, playerPos.y + 1.5, playerPos.z)

        val fwdX = cos(playerPitch) * sin(playerYaw)
        val fwdY = sin(playerPitch)
        val fwdZ = cos(playerPitch) * cos(playerYaw)

        val rightX = cos(playerYaw)
        val rightY = 0.0
        val rightZ = -sin(playerYaw)

        val upX = -sin(playerPitch) * sin(playerYaw)
        val upY = cos(playerPitch)
        val upZ = -sin(playerPitch) * cos(playerYaw)

        val futures = (0 until gameHeight).map { py ->
            pool.submit {
                val rowOffset = py * gameWidth
                for (px in 0 until gameWidth) {
                    val i = rowOffset + px
                    val px_ = planeX[i].toDouble()
                    val py_ = planeY[i].toDouble()

                    val rdx = fwdX + px_ * rightX + py_ * upX
                    val rdy = fwdY + px_ * rightY + py_ * upY
                    val rdz = fwdZ + px_ * rightZ + py_ * upZ

                    val result = world.raycast(camPos, rdx, rdy, rdz, MAX_DISTANCE) ?: continue

                    val fog = 1.0 - (result.distance / MAX_DISTANCE)
                    val u = ((1 - result.u) * 16).toInt().coerceIn(0, 15)
                    val v = ((1 - result.v) * 16).toInt().coerceIn(0, 15)

                    val r: Int
                    val g: Int
                    val b: Int

                    if (targetResult?.blockPos == result.blockPos && (u == 0 || v == 0 || u == 15 || v == 15)) {
                        r = 255
                        g = 255
                        b = 255
                    } else {
                        val texel = result.hit.getTexture(result.normal)[v * 16 + u]

                        r = ((texel shr 16 and 0xFF) * fog).toInt()
                        g = ((texel shr 8 and 0xFF) * fog).toInt()
                        b = ((texel and 0xFF) * fog).toInt()
                    }

                    pixels[i] = (r shl 16) or (g shl 8) or b
                }
            }
        }

        futures.forEach { it.get() }
    }

    override fun keyPressed(e: KeyEvent) { keys.add(e.keyCode) }
    override fun keyReleased(e: KeyEvent) { keys.remove(e.keyCode) }
    override fun keyTyped(e: KeyEvent) {}

    override fun mouseMoved(e: MouseEvent) {
        if (!::robot.isInitialized) return
        if (ignoreMouse) {
            ignoreMouse = false
            return
        }

        val dx = e.xOnScreen - centerX
        val dy = e.yOnScreen - centerY

        if (dx == 0 && dy == 0) return

        playerYaw += dx * MOUSE_SENS
        playerPitch -= dy * MOUSE_SENS

        ignoreMouse = true
        robot.mouseMove(centerX, centerY)
    }

    override fun mouseDragged(e: MouseEvent) = mouseMoved(e)

    override fun mousePressed(e: MouseEvent) {
        when (e.button) {
            MouseEvent.BUTTON1 -> leftClick = true
            MouseEvent.BUTTON3 -> rightClick = true
        }
    }

    override fun mouseClicked(e: MouseEvent) {}
    override fun mouseReleased(e: MouseEvent) {}
    override fun mouseEntered(e: MouseEvent) {}
    override fun mouseExited(e: MouseEvent) {}

    companion object {
        var width = 0
        var height = 0
        var frames = 0
    }
}

fun main() {
    val canvas = Game(300, 225, scale = 4)

    val frame = JFrame("My Game").apply {
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        add(canvas)
        pack()
        isResizable = false
        isVisible = true
    }

    canvas.requestFocus()
    canvas.start()
}