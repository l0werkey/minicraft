package me.lowerkey

import java.util.Random
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin

data class ChunkPos(val cx: Int, val cz: Int)


class World {
    val blocks: MutableMap<BlockPos, Block> = mutableMapOf()
    val generatedChunks: MutableSet<ChunkPos> = mutableSetOf()
    val tracking: MutableSet<Block> = mutableSetOf()

    companion object {
        const val CHUNK_SIZE = 16          // blocks per side
        const val CHUNK_RADIUS = 8         // chunks to keep loaded around player
        const val SEA_LEVEL = 0            // y=0 is the minimum surface
        const val MAX_HEIGHT = 12          // terrain height amplitude
        const val NOISE_SEED = 0x9e3779b9L // arbitrary seed
    }

    // Call this every frame!
    fun updateChunks(playerX: Double, playerZ: Double) {
        val pcx = floor(playerX / CHUNK_SIZE).toInt()
        val pcz = floor(playerZ / CHUNK_SIZE).toInt()

        for (dcx in -CHUNK_RADIUS..CHUNK_RADIUS) {
            for (dcz in -CHUNK_RADIUS..CHUNK_RADIUS) {
                val cp = ChunkPos(pcx + dcx, pcz + dcz)
                if (cp !in generatedChunks) generateChunk(cp)
            }
        }
    }

    fun markUpdating(block: Block, updating: Boolean) {
        if (updating) {
            tracking.add(block)
        } else {
            tracking.remove(block)
        }
    }

    fun update() {
        for (block in tracking.toList()) {
            block.update()
        }
    }

    fun removeBlock(pos: BlockPos) {
        val block = blocks.remove(pos)
        block?.let { markUpdating(it, false) }
    }

    private fun generateChunk(cp: ChunkPos) {
        generatedChunks += cp

        val originX = cp.cx * CHUNK_SIZE
        val originZ = cp.cz * CHUNK_SIZE

        for (lx in 0 until CHUNK_SIZE) {
            for (lz in 0 until CHUNK_SIZE) {
                val wx = originX + lx
                val wz = originZ + lz

                val height = surfaceHeight(wx, wz)

                for (y in (SEA_LEVEL - 1)..height) {
                    setBlock(BlockPos(wx, y, wz), if(y == height) GrassBlock(this) else Block(this))
                }

                setBlock(BlockPos(wx, SEA_LEVEL - 2, wz), BedrockBlock(this))
            }
        }

        val random = Random()
        val rx = random.nextInt(16) + originX
        val rz = random.nextInt(16) + originZ
        val height = surfaceHeight(rx, rz)

        if (random.nextInt(10) == 0) {
            if (random.nextBoolean()) {
                setBlock(BlockPos(rx, height - 1, rz), HazardBlock(this))
                setBlock(BlockPos(rx, height + 1, rz), Block(this))
                setBlock(BlockPos(rx, height + 2, rz), Block(this))
                setBlock(BlockPos(rx, height + 3, rz), Block(this))
                setBlock(BlockPos(rx + 1, height + 3, rz), Block(this))
                setBlock(BlockPos(rx - 1, height + 3, rz), Block(this))
                setBlock(BlockPos(rx, height + 4, rz), Block(this))
            } else {
                generateParkourSpiral(originX, originZ, random)
            }
        } else {
            generateTree(rx, height, rz)
        }
    }

    fun setBlock(pos: BlockPos, block: Block) {
        blocks[pos]?.let { removeBlock(pos) }
        block.pos = pos
        blocks[pos] = block
    }

    private fun generateParkourSpiral(originX: Int, originZ: Int, random: Random) {
        val centerX = originX + 8
        val centerZ = originZ + 8

        val groundY = surfaceHeight(centerX, centerZ)
        val startY = groundY + 2


        val steps = 14 + random.nextInt(7)                  // 14..20 parkour blocks
        val radius = 3 + random.nextInt(2)                  // 3..4
        val riseEvery = 1                                           // every step goes upward
        val angleStep = Math.PI / (2.0 + random.nextDouble() * 0.8) // around quarter-turn-ish
        val phase = random.nextDouble() * Math.PI * 2.0

        var prevX = centerX
        var prevY = startY
        var prevZ = centerZ

        // Start platform
        placePlatform(prevX, prevY, prevZ, 3)

        for (i in 1..steps) {
            val angle = phase + i * angleStep

            var px = centerX + round(cos(angle) * radius).toInt()
            var pz = centerZ + round(sin(angle) * radius).toInt()
            var py = startY + i / riseEvery

            if (random.nextBoolean()) px += random.nextInt(3) - 1
            if (random.nextBoolean()) pz += random.nextInt(3) - 1

            // Clamp to chunk interior so we dont spill into neighboring chunks too much
            px = px.coerceIn(originX + 1, originX + CHUNK_SIZE - 2)
            pz = pz.coerceIn(originZ + 1, originZ + CHUNK_SIZE - 2)

            // Reachability guard
            val dx = (px - prevX).coerceIn(-3, 3)
            val dz = (pz - prevZ).coerceIn(-3, 3)
            val dy = (py - prevY).coerceIn(1, 2)

            px = prevX + dx
            pz = prevZ + dz
            py = prevY + dy

            // Occasionally make a slightly wider step
            val platformSize = when {
                i == steps -> 5
                i % 5 == 0 -> 2
                else -> 1
            }

            placePlatform(px, py, pz, platformSize)

            if (i == steps) {
                setBlock(BlockPos(px, py + 1, pz), InfoBlock(if (Random().nextBoolean()) "Congrats on completing the parkour" else "do not look under the cross", this))
            }

            prevX = px
            prevY = py
            prevZ = pz
        }

        setBlock(BlockPos(centerX, startY + 1, centerZ), InfoBlock("There is another message at the top", this))
    }

    private fun placePlatform(x: Int, y: Int, z: Int, size: Int) {
        val r = size / 2
        for (dx in -r..r) {
            for (dz in -r..r) {
                setBlock(BlockPos(x + dx, y, z + dz), Block(this))
            }
        }
    }

    fun generateTree(rx: Int, height: Int, rz: Int) {
        val trunkHeight = 5
        val baseY = height + 1

        // trunk
        for (yp in 0 until trunkHeight) {
            setBlock(BlockPos(rx, baseY + yp, rz), WoodBlock(this))
        }

        
        // leaves
        val topY = baseY + trunkHeight - 1

        for (dy in -2..2) {
            for (dx in -2..2) {
                for (dz in -2..2) {
                    val dist = abs(dx) + abs(dz) + abs(dy)
                    val isLeaf = dist <= 3 && !(dx == 0 && dz == 0 && dy <= 0)

                    if (isLeaf) {
                        val pos = BlockPos(rx + dx, topY + dy, rz + dz)
                        if (blocks[pos] == null) {
                            setBlock(pos, LeavesBlock(this))
                        }
                    }
                }
            }
        }

        setBlock(BlockPos(rx, topY + 3, rz), LeavesBlock(this))
    }

    // Layered value noise
    private fun surfaceHeight(wx: Int, wz: Int): Int {
        val n1 = valueNoise(wx, wz, scale = 48) * MAX_HEIGHT
        val n2 = valueNoise(wx, wz, scale = 24) * (MAX_HEIGHT * 0.4)
        val n3 = valueNoise(wx, wz, scale = 12) * (MAX_HEIGHT * 0.15)
        return SEA_LEVEL + (n1 + n2 + n3).roundToInt()
    }

    /**
     * Simple bilinear interpolated noise
     * Returns a value in [0, 1]
     */
    private fun valueNoise(wx: Int, wz: Int, scale: Int): Double {
        val fx = wx.toDouble() / scale
        val fz = wz.toDouble() / scale
        val ix = floor(fx).toInt()
        val iz = floor(fz).toInt()
        val tx = fx - ix          // local [0,1] within the cell
        val tz = fz - iz

        // Smooth (quintic) step
        val sx = tx * tx * tx * (tx * (tx * 6 - 15) + 10)
        val sz = tz * tz * tz * (tz * (tz * 6 - 15) + 10)

        val v00 = rand2(ix,     iz)
        val v10 = rand2(ix + 1, iz)
        val v01 = rand2(ix,     iz + 1)
        val v11 = rand2(ix + 1, iz + 1)

        return lerp(lerp(v00, v10, sx), lerp(v01, v11, sx), sz)
    }

    // Magic
    private fun rand2(gx: Int, gz: Int): Double {
        var h = NOISE_SEED xor (gx.toLong() * 0x6c62272e07bb0142L) xor
                (gz.toLong() * ((0x94d049bbL shl 32) or 0x133111ebL))
        h = h xor (h ushr 30)
        h *= -4658895793534929339L
        h = h xor (h ushr 27)
        h *= -7723592293110705685L
        h = h xor (h ushr 31)
        return (h and 0x7fffffffffffffffL).toDouble() / Long.MAX_VALUE.toDouble()
    }

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    fun raycast(start: Vecd, pitch: Double, yaw: Double, maxDistance: Double = 100.0): RaycastResult? {
        val dx = cos(pitch) * sin(yaw)
        val dy = sin(pitch)
        val dz = cos(pitch) * cos(yaw)
        return raycast(start, dx, dy, dz, maxDistance)
    }

    // A simple 3d dda raycasting implementation (doesnt support transparent surfaces)
    fun raycast(start: Vecd, dx: Double, dy: Double, dz: Double, maxDistance: Double = 100.0): RaycastResult? {
        val key = Veci(0, 0, 0)

        var bx = floor(start.x).toInt()
        var by = floor(start.y).toInt()
        var bz = floor(start.z).toInt()

        val stepX = if (dx >= 0) 1 else -1
        val stepY = if (dy >= 0) 1 else -1
        val stepZ = if (dz >= 0) 1 else -1

        val tDeltaX = if (dx != 0.0) abs(1.0 / dx) else Double.MAX_VALUE
        val tDeltaY = if (dy != 0.0) abs(1.0 / dy) else Double.MAX_VALUE
        val tDeltaZ = if (dz != 0.0) abs(1.0 / dz) else Double.MAX_VALUE

        var tMaxX = if (dx >= 0) (ceil(start.x)  - start.x) * tDeltaX else (start.x - floor(start.x)) * tDeltaX
        var tMaxY = if (dy >= 0) (ceil(start.y)  - start.y) * tDeltaY else (start.y - floor(start.y)) * tDeltaY
        var tMaxZ = if (dz >= 0) (ceil(start.z)  - start.z) * tDeltaZ else (start.z - floor(start.z)) * tDeltaZ

        var lastAxis = -1

        while (true) {
            val t: Double
            when {
                tMaxX < tMaxY && tMaxX < tMaxZ -> { t = tMaxX; tMaxX += tDeltaX; bx += stepX; lastAxis = 0 }
                tMaxY < tMaxZ                  -> { t = tMaxY; tMaxY += tDeltaY; by += stepY; lastAxis = 1 }
                else                           -> { t = tMaxZ; tMaxZ += tDeltaZ; bz += stepZ; lastAxis = 2 }
            }

            if (t > maxDistance) break

            key.x = bx; key.y = by; key.z = bz
            val block = blocks[key] ?: continue

            val hx = start.x + dx * t
            val hy = start.y + dy * t
            val hz = start.z + dz * t

            val u: Double
            val v: Double
            when (lastAxis) {
                0    -> { u = hz - floor(hz); v = hy - floor(hy) }
                1    -> { u = hx - floor(hx); v = hz - floor(hz) }
                else -> { u = hx - floor(hx); v = hy - floor(hy) }
            }

            val normal = when (lastAxis) {
                0    -> Vec(-stepX, 0, 0)
                1    -> Vec(0, -stepY, 0)
                else -> Vec(0, 0, -stepZ)
            }

            return RaycastResult(block, Vec(bx, by, bz), Vec(hx, hy, hz), normal, u, v, t)
        }

        return null
    }

    data class RaycastResult(
        val hit: Block,
        val blockPos: BlockPos,
        val pos: Vecd,
        val normal: Veci,
        val u: Double,
        val v: Double,
        val distance: Double
    )
}