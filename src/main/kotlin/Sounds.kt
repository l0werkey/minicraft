package me.lowerkey

import javax.sound.sampled.*
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.*
import java.awt.*
import java.awt.event.*

// Game sounds are generated w/ code

private const val SR  = 44100
private val FMT  = AudioFormat(SR.toFloat(), 16, 1, true, true)
private val pool = Executors.newCachedThreadPool { Thread(it, "sfx").also { t -> t.isDaemon = true } }
private val rng  = java.util.Random()

enum class Material(
    val breakDecay: Double, val breakHz: Double,
    val placeHz: Double,    val stepHz: Double,
    val lp: Double,         val noiseMix: Double, val toneMix: Double
) {
    GRASS(0.14, 100.0, 160.0, 85.0, 0.2, 0.75, 0.25),
    STONE(0.05, 40.0, 80.0, 35.0, 0.40, 0.20, 0.80),
    WOOD(0.1, 80.0, 120.0, 65.0, 0.30, 0.50, 0.90)
}

fun playBlockBreak(mat: Material = Material.STONE) = pool.submit {
    val p = jitter()
    play(mix(noise(0.26, 0.9, 0.004, mat.breakDecay, mat.lp),
        sine(mat.breakHz * p, 0.22, 0.005, 0.20, 0.9),
        mat.noiseMix, mat.toneMix * 0.8))
}

fun playBlockPlace(mat: Material = Material.STONE) = pool.submit {
    val p = jitter(0.12)
    play(mix(noise(0.10, 1.0, 0.002, 0.06, mat.lp * 1.4),
        sine(mat.placeHz * p, 0.14, 0.003, 0.10, 0.85),
        mat.noiseMix * 0.9, mat.toneMix))
}

fun playFootstep(mat: Material = Material.GRASS) = pool.submit {
    val p = jitter(0.20)
    play(mix(sine(mat.stepHz * p, 0.13, 0.004, 0.11, 0.80),
        noise(0.09, 0.5, 0.005, 0.055, mat.lp * 0.8),
        mat.toneMix, mat.noiseMix * 0.4))
}

private fun jitter(spread: Double = 0.15) =
    1.0 - spread + rng.nextDouble() * 2.0 * spread

private fun sine(hz: Double, dur: Double,
                 atkS: Double, relS: Double, amp: Double): DoubleArray {
    val n = (SR * dur).toInt()
    val nA = (SR * atkS).toInt(); val nR = (SR * relS).toInt()
    val rS = n - nR
    var ph = 0.0; val inc = hz / SR
    return DoubleArray(n) { i ->
        val env = when {
            i < nA -> i.toDouble() / nA.coerceAtLeast(1)
            i < rS -> 1.0
            else   -> 1.0 - (i - rS).toDouble() / nR.coerceAtLeast(1)
        }
        (sin(2 * PI * ph) * env * amp).also { ph = (ph + inc) % 1.0 }
    }
}

private fun noise(dur: Double, amp: Double, atkS: Double,
                  decay: Double, lp: Double): DoubleArray {
    val n = (SR * dur).toInt(); val nA = (SR * atkS).toInt()
    var prev = 0.0
    return DoubleArray(n) { i ->
        prev = lp * rng.nextGaussian() + (1 - lp) * prev
        val env = if (i < nA) i.toDouble() / nA.coerceAtLeast(1)
        else exp(-(i - nA).toDouble() / SR / decay)
        prev * env * amp
    }
}

private fun mix(a: DoubleArray, b: DoubleArray, ga: Double, gb: Double) =
    DoubleArray(maxOf(a.size, b.size)) { i ->
        (if (i < a.size) a[i] * ga else 0.0) + (if (i < b.size) b[i] * gb else 0.0)
    }

private fun play(buf: DoubleArray) {
    val peak = buf.maxOfOrNull { abs(it) }?.takeIf { it > 0.001 } ?: return
    val norm = 0.3 / peak
    val pcm  = ByteBuffer.allocate(buf.size * 2)
    buf.forEach { pcm.putShort((tanh(it * norm) * Short.MAX_VALUE).toInt().toShort()) }
    try {
        val line = AudioSystem.getLine(DataLine.Info(SourceDataLine::class.java, FMT))
                as SourceDataLine
        line.open(FMT); line.start()
        line.write(pcm.array(), 0, pcm.limit())
        line.drain(); line.close()
    } catch (e: LineUnavailableException) { System.err.println("[sfx] ${e.message}") }
}