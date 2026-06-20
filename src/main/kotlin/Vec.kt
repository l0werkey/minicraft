package me.lowerkey

import kotlin.math.floor

data class Vec<T : Number>(var x: T, var y: T, var z: T) {
    fun toInt() = Vec(x.toInt(), y.toInt(), z.toInt())
    fun toDouble() = Vec(x.toDouble(), y.toDouble(), z.toDouble())
}

fun Vec<Double>.blockPos() = Vec(
    floor(x).toInt(),
    floor(y).toInt(),
    floor(z).toInt()
)

// For DX
typealias Veci = Vec<Int>
typealias BlockPos = Vec<Int>
typealias Vecd = Vec<Double>

val UP = Veci(0, 1, 0)
val DOWN = Veci(0, -1, 0)

