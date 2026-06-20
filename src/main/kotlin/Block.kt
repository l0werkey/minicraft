package me.lowerkey

import java.util.Random
import javax.swing.JOptionPane

open class Block(val world: World) {
    var updatable = false
        set(value) {
            field = value
            world.markUpdating(this, value)
        }

    var pos: BlockPos = BlockPos(0, 0, 0)

    open fun getTexture(normal: Veci): IntArray {
        return Textures.COBBLESTONE
    }

    open fun onBreak() {
        playBlockBreak(Material.STONE)
    }

    open fun update() {

    }

    open fun onPlace() {
        playBlockPlace(Material.STONE)
    }

    open fun getMaterial(): Material = Material.STONE
}

class GrassBlock(world: World) : Block(world) {
    override fun getTexture(normal: Veci): IntArray {
        if (normal == UP) return Textures.GRASS_TOP
        if (normal == DOWN) return Textures.DIRT
        return Textures.GRASS_SIDE
    }

    override fun onBreak() {
        playBlockBreak(Material.GRASS)
    }

    override fun getMaterial(): Material = Material.GRASS
}

class WoodBlock(world: World) : Block(world) {
    override fun getTexture(normal: Veci): IntArray {
        if (normal == UP || normal == DOWN) return Textures.WOOD
        return Textures.WOOD_SIDE
    }

    override fun onBreak() {
        playBlockBreak(Material.WOOD)
    }

    override fun getMaterial(): Material = Material.WOOD
}


class LeavesBlock(world: World) : Block(world) {
    override fun getTexture(normal: Veci): IntArray {
        return Textures.LEAVES
    }

    override fun onBreak() {
        playBlockBreak(Material.GRASS)
    }

    override fun getMaterial(): Material = Material.GRASS
}


class InfoBlock(val message: String, world: World) : Block(world) {
    override fun getTexture(normal: Veci): IntArray {
        return Textures.INFO
    }

    override fun onBreak() {
        JOptionPane.showMessageDialog(null, message);
        playBlockBreak(Material.STONE)
    }
}

class VoidBlock(world: World) : Block(world) {
    private val offset = Random().nextInt(0, 35)

    init {
        updatable = true
    }

    override fun getTexture(normal: Veci): IntArray {
        return Textures.VOID
    }

    override fun update() {
        if (Game.frames % 35 == offset) {
            trySpread(BlockPos(1, 0, 0))
            trySpread(BlockPos(-1, 0, 0))
            trySpread(BlockPos(0, 1, 0))
            trySpread(BlockPos(0, -1, 0))
            trySpread(BlockPos(0, 0, 1))
            trySpread(BlockPos(0, 0, -1))
            world.removeBlock(pos)
        }
    }

    private fun trySpread(dir: BlockPos) {
        val target = BlockPos(pos.x+dir.x, pos.y+dir.y, pos.z+dir.z)
        if (world.blocks[target] == null) return
        world.setBlock(target, VoidBlock(world))
    }

}

class HazardBlock(world: World) : Block(world) {
    override fun getTexture(normal: Veci): IntArray {
        return Textures.HAZARD
    }

    override fun onBreak() {
        playBlockBreak(Material.STONE)
        world.setBlock(pos, VoidBlock(world))
        setScary(true)
        JOptionPane.showMessageDialog(null, "they are coming")
        JOptionPane.showMessageDialog(null, "and it is your fault")
    }
}


class BedrockBlock(world: World) : Block(world) {
    override fun getTexture(normal: Veci): IntArray {
        return Textures.BEDROCK
    }

    override fun onBreak() {
        world.setBlock(pos, BedrockBlock(world))
    }
}