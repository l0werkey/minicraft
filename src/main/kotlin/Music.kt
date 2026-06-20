package me.lowerkey

import javax.sound.sampled.*
import java.nio.ByteBuffer
import java.util.Random
import java.util.concurrent.Executors
import kotlin.math.*

// Music generated with code
// 2 tracks:
// 1. Ambient
// 2. Terror

private const val SR = 44100
private val FMT = AudioFormat(SR.toFloat(), 16, 1, true, true)
private val rng = Random()

private val musicPool = Executors.newSingleThreadExecutor {
    Thread(it, "ambient-music").also { t -> t.isDaemon = true }
}
private val hitPool = Executors.newCachedThreadPool {
    Thread(it, "ambient-hit").also { t -> t.isDaemon = true }
}

@Volatile private var musicOn = false
@Volatile private var scary   = false
@Volatile private var bpm     = 74.0

private val beatMs get() = 60000.0 / bpm
private val barMs  get() = beatMs * 4.0

fun setBpm(value: Double) { bpm = value.coerceIn(45.0, 120.0) }

fun setScary(value: Boolean) { scary = value }

fun startAmbientMusic() {
    if (musicOn) return
    musicOn = true

    musicPool.submit {
        val calmProgression = listOf(
            intArrayOf(36, 55, 59, 64),
            intArrayOf(36, 55, 60, 64),
            intArrayOf(41, 57, 60, 64),
            intArrayOf(43, 57, 60, 62),
            intArrayOf(45, 57, 60, 64),
            intArrayOf(36, 55, 60, 67)
        )

        val scaryProgression = listOf(
            intArrayOf(24, 30, 36, 42),   // C1, F#1, C2, F#2  - pure tritone across three octaves
            intArrayOf(23, 29, 34, 37),   // B0, F1, Bb1, Db2  - half-diminished cluster
            intArrayOf(24, 25, 36, 37),   // C, Db, C, Db      - minor-2nd stacked at two octaves
            intArrayOf(22, 28, 33, 39),   // Bb0 fully diminished 7th
            intArrayOf(23, 30, 35, 42),   // B, F#, B, F#      - tritone power chord
            intArrayOf(22, 29, 34, 40),   // Bb, F, Bb, E      - tritone resolution eternally denied
            intArrayOf(24, 27, 30, 33),   // C, Eb, F#, A      - diminished 7th, root position
            intArrayOf(21, 27, 31, 36)    // A0, Eb1, G1, C2   - aug4 + aug4, stacked augmented
        )

        var bar = 0
        while (musicOn) {
            val isScary    = scary
            val progression = if (isScary) scaryProgression else calmProgression
            val chord      = progression[bar % progression.size]
            val barBuf     = if (isScary) makeScaryBar(chord, bar) else makeBar(chord, bar)

            playAsync(barBuf, if (isScary) 0.14 else 0.10)

            val jitter = if (isScary) {
                val roll = rng.nextDouble()
                when {
                    roll < 0.08 -> beatMs * (1.5 + rng.nextDouble() * 2.0)  // hold your breath
                    roll < 0.20 -> -(beatMs * 0.18)                          // lurch forward
                    else        -> (rng.nextDouble() - 0.5) * beatMs * 0.25
                }
            } else 0.0

            Thread.sleep((barMs + jitter).toLong().coerceAtLeast(100))
            bar++
        }
    }
}

fun stopAmbientMusic() { musicOn = false }

private fun makeBar(chord: IntArray, bar: Int): DoubleArray {
    val dur = barMs / 1000.0 + 0.55
    val n   = (SR * dur).toInt()
    val out = DoubleArray(n)

    chord.forEachIndexed { idx, midi ->
        val voice = padPiano(
            hz      = midiToHz(midi) * detune(idx),
            dur     = dur,
            attack  = 0.08 + idx * 0.015,
            release = dur * 0.78,
            amp     = when (idx) { 0 -> 0.060; 1 -> 0.050; 2 -> 0.042; else -> 0.035 }
        )
        mixInto(out, voice, 0)
    }

    val bassPattern = when (bar % 4) {
        0    -> intArrayOf(chord[0], chord[1], chord[0], chord[2])
        1    -> intArrayOf(chord[0], chord[1], chord[2], chord[1])
        2    -> intArrayOf(chord[0], chord[2], chord[0], chord[1])
        else -> intArrayOf(chord[0], chord[1], chord[0], chord[3])
    }
    bassPattern.forEachIndexed { beat, midi ->
        mixInto(out, lowPulse(midiToHz(midi), 0.95, 0.060),
            ((beat * beatMs) / 1000.0 * SR).toInt())
    }

    val topStart = ((beatMs * 1.2) / 1000.0 * SR).toInt()
    melodyFor(chord, bar).forEach { (offsetBeats, midi, amp, len) ->
        mixInto(out, softBell(midiToHz(midi), len, amp),
            topStart + ((offsetBeats * beatMs) / 1000.0 * SR).toInt())
    }

    if (bar % 8 == 7) {
        mixInto(out, airTone(midiToHz(chord.last() + 12), 1.6, 0.012),
            ((beatMs * 2.6) / 1000.0 * SR).toInt())
    }

    return out
}

private fun melodyFor(chord: IntArray, bar: Int): List<MelNote> {
    val top   = chord.last()
    val upper = listOf(top, top + 2, top - 2, top - 3, top + 4)
    return when (bar % 8) {
        0    -> listOf(MelNote(0.0, upper[0], 0.020, 0.65), MelNote(1.0, upper[1], 0.018, 0.55))
        1    -> listOf(MelNote(0.5, upper[0], 0.018, 0.55), MelNote(1.5, upper[2], 0.017, 0.60))
        2    -> listOf(MelNote(0.0, upper[2], 0.017, 0.50), MelNote(1.0, upper[0], 0.019, 0.70))
        3    -> listOf(MelNote(0.75, upper[3], 0.018, 0.50), MelNote(1.5, upper[0], 0.020, 0.75))
        4    -> listOf(MelNote(0.0, upper[0], 0.019, 0.60), MelNote(0.75, upper[1], 0.018, 0.50), MelNote(1.5, upper[0], 0.017, 0.65))
        5    -> listOf(MelNote(1.0, upper[2], 0.017, 0.55))
        6    -> listOf(MelNote(0.0, upper[0], 0.018, 0.55), MelNote(1.5, upper[4], 0.016, 0.45))
        else -> listOf(MelNote(0.5, upper[3], 0.018, 0.50), MelNote(1.25, upper[2], 0.017, 0.55), MelNote(2.0, upper[0], 0.020, 0.90))
    }
}

private data class MelNote(val offsetBeats: Double, val midi: Int, val amp: Double, val len: Double)

private fun makeScaryBar(chord: IntArray, bar: Int): DoubleArray {
    val dur = barMs / 1000.0 + 0.80   // longer tail blurs bar edges, removes rhythmic safety
    val n   = (SR * dur).toInt()
    val out = DoubleArray(n)

    val lfoRates = listOf(0.07, 0.11, 0.17, 0.13)
    chord.forEachIndexed { idx, midi ->
        val detuneAmt = 1.0 + idx * 0.004 + (rng.nextDouble() - 0.5) * 0.008
        mixInto(out,
            eerieHum(midiToHz(midi) * detuneAmt, dur, lfoRates[idx],
                when (idx) { 0 -> 0.055; 1 -> 0.048; 2 -> 0.040; else -> 0.034 }),
            0)
    }

    listOf(1.0, 1.0046).forEach { factor ->
        mixInto(out, deepDrone(midiToHz(chord[0]) * factor, dur, 0.065), 0)
    }

    mixInto(out,
        deepDrone(midiToHz(chord[0] + 6), dur * 0.82, 0.032),
        ((beatMs * 0.22) / 1000.0 * SR).toInt())

    val stabBeats = when (bar % 6) {
        0    -> listOf(0.00, 2.73)
        1    -> listOf(1.25, 3.60)
        2    -> listOf(0.00, 0.55, 3.10)
        3    -> listOf(2.00, 3.75)
        4    -> listOf(0.33, 1.90, 3.45)
        else -> listOf(0.00, 1.55, 2.80, 3.95) 
    }
    stabBeats.forEach { beat ->
        val stabMidi   = chord[rng.nextInt(chord.size)]
        val jitter     = (rng.nextDouble() - 0.5) * 0.20
        val offset     = (((beat + jitter) * beatMs) / 1000.0 * SR).toInt().coerceAtLeast(0)
        mixInto(out, dissonantStab(midiToHz(stabMidi), 0.42, 0.050), offset)
    }

    if (bar % 2 == 1) {
        val swellOffset = ((beatMs * (0.4 + rng.nextDouble() * 1.3)) / 1000.0 * SR).toInt()
        mixInto(out, whisperSwell(1.7, 0.024), swellOffset)
    }

    if (bar % 3 == 2) {
        val scrapeOffset = ((beatMs * rng.nextDouble() * 2.2) / 1000.0 * SR).toInt()
        mixInto(out, metalScrape(0.95, 0.016), scrapeOffset)
    }

    if (bar % 5 == 4) {
        val keenHz = midiToHz(chord.last() + 22)
        mixInto(out,
            eerieHum(keenHz, 2.3, 0.31, 0.020),
            ((beatMs * 0.45) / 1000.0 * SR).toInt())
    }

    if (rng.nextDouble() < 0.12) {
        val boomOffset = ((beatMs * rng.nextDouble() * 3.5) / 1000.0 * SR).toInt()
        mixInto(out, subBoom(midiToHz(chord[0] - 12), 0.58, 0.095), boomOffset)
    }

    if (bar % 7 == 6) {
        mixInto(out, shepardRise(midiToHz(chord[0]), dur, 0.017), 0)
    }

    return out
}

private fun padPiano(hz: Double, dur: Double, attack: Double, release: Double, amp: Double): DoubleArray {
    val n        = (SR * dur).toInt()
    val a        = (SR * attack).toInt().coerceAtLeast(1)
    val relStart = (n - (SR * release).toInt()).coerceAtLeast(0)
    var p1 = 0.0; var p2 = 0.0; var p3 = 0.0
    val i1 = hz / SR; val i2 = hz * 2.0 / SR; val i3 = hz * 3.0 / SR
    var noise = 0.0
    return DoubleArray(n) { i ->
        val env = when {
            i < a        -> i.toDouble() / a
            i < relStart -> 1.0
            else         -> 1.0 - (i - relStart).toDouble() / (n - relStart).coerceAtLeast(1)
        }
        noise = 0.06 * rng.nextGaussian() + 0.94 * noise
        val felt = noise * exp(-i.toDouble() / n * 8.0) * 0.016
        val s = sin(2 * PI * p1) * 0.78 + sin(2 * PI * p2) * 0.16 + sin(2 * PI * p3) * 0.06 + felt
        p1 = (p1 + i1) % 1.0; p2 = (p2 + i2) % 1.0; p3 = (p3 + i3) % 1.0
        tanh(s * env * amp * 1.45)
    }
}

private fun lowPulse(hz: Double, dur: Double, amp: Double): DoubleArray {
    val n = (SR * dur).toInt(); var p = 0.0; val inc = hz / SR
    return DoubleArray(n) { i ->
        val t = i.toDouble() / n; val env = exp(-t * 4.3)
        val s = sin(2 * PI * p) * 0.86 + sin(2 * PI * p * 2.0) * 0.14
        p = (p + inc) % 1.0; tanh(s * env * amp * 1.35)
    }
}

private fun softBell(hz: Double, dur: Double, amp: Double): DoubleArray {
    val n = (SR * dur).toInt(); var p1 = 0.0; var p2 = 0.0
    val i1 = hz / SR; val i2 = hz * 2.01 / SR
    return DoubleArray(n) { i ->
        val t = i.toDouble() / n; val env = exp(-t * 5.3)
        val s = sin(2 * PI * p1) * 0.68 + sin(2 * PI * p2) * 0.32
        p1 = (p1 + i1) % 1.0; p2 = (p2 + i2) % 1.0
        tanh(s * env * amp * 1.18)
    }
}

private fun airTone(hz: Double, dur: Double, amp: Double): DoubleArray {
    val n = (SR * dur).toInt(); var p = 0.0; val inc = hz / SR; var noise = 0.0
    return DoubleArray(n) { i ->
        val t = i.toDouble() / n; val env = exp(-t * 2.8)
        noise = 0.05 * rng.nextGaussian() + 0.95 * noise
        val s = sin(2 * PI * p) * 0.72 + noise * 0.06
        p = (p + inc) % 1.0; tanh(s * env * amp)
    }
}

private fun eerieHum(hz: Double, dur: Double, lfoHz: Double, amp: Double): DoubleArray {
    val n        = (SR * dur).toInt()
    var phase    = 0.0
    var lfoPhase = 0.0
    val inc      = hz / SR
    val lfoInc   = lfoHz / SR

    return DoubleArray(n) { i ->
        val t       = i.toDouble() / n
        val attack  = minOf(t / 0.40, 1.0)
        val release = if (t > 0.78) 1.0 - (t - 0.78) / 0.22 else 1.0
        val env     = attack * release

        val tremolo = 0.52 + 0.48 * sin(2 * PI * lfoPhase)
        val vibrato = 1.0  + 0.006 * sin(2 * PI * lfoPhase * 3.1)

        val s = sin(2 * PI * phase)       * 0.82 +
                sin(2 * PI * phase * 3.0) * 0.12 +   // 3rd harmonic
                sin(2 * PI * phase * 5.0) * 0.06     // 5th harmonic

        phase    = (phase    + inc * vibrato) % 1.0
        lfoPhase = (lfoPhase + lfoInc)        % 1.0

        tanh(s * env * amp * tremolo * 1.4)
    }
}

private fun deepDrone(hz: Double, dur: Double, amp: Double): DoubleArray {
    val n  = (SR * dur).toInt()
    var p1 = 0.0; var p2 = 0.0; var p3 = 0.0
    val i1 = hz          / SR
    val i2 = hz * 1.0046 / SR
    val i3 = hz * 2.009  / SR 
    var noise = 0.0

    return DoubleArray(n) { i ->
        val t       = i.toDouble() / n
        val attack  = minOf(t / 0.60, 1.0)
        val release = if (t > 0.70) 1.0 - (t - 0.70) / 0.30 else 1.0
        val env     = attack * release

        noise = 0.06 * rng.nextGaussian() + 0.94 * noise

        val s = sin(2 * PI * p1) * 0.58 +
                sin(2 * PI * p2) * 0.30 +
                sin(2 * PI * p3) * 0.08 +
                noise            * 0.04   // subsurface grit

        p1 = (p1 + i1) % 1.0
        p2 = (p2 + i2) % 1.0
        p3 = (p3 + i3) % 1.0

        tanh(s * env * amp * 1.7)
    }
}

private fun dissonantStab(hz: Double, dur: Double, amp: Double): DoubleArray {
    val n  = (SR * dur).toInt()
    var p1 = 0.0; var p2 = 0.0; var p3 = 0.0
    val i1 = hz          / SR
    val i2 = hz * 1.4142 / SR   // tritone: sqrt(2) ratio
    val i3 = hz * 1.0595 / SR   // minor 2nd: one semitone up

    return DoubleArray(n) { i ->
        val t   = i.toDouble() / n
        val env = if (t < 0.04) t / 0.04 else exp(-(t - 0.04) * 13.0)

        val s = sin(2 * PI * p1) * 0.45 +
                sin(2 * PI * p2) * 0.38 +
                sin(2 * PI * p3) * 0.17

        p1 = (p1 + i1) % 1.0
        p2 = (p2 + i2) % 1.0
        p3 = (p3 + i3) % 1.0

        tanh(s * env * amp * 2.4)
    }
}

private fun whisperSwell(dur: Double, amp: Double): DoubleArray {
    val n   = (SR * dur).toInt()
    var lo  = 0.0
    var hi  = 0.0

    return DoubleArray(n) { i ->
        val t   = i.toDouble() / n
        val env = sin(PI * t).pow(0.6)    // asymmetric: fast rise, slow fade

        val raw    = rng.nextGaussian()
        lo = 0.18 * raw + 0.82 * lo
        hi = 0.04 * raw + 0.96 * hi
        val shaped = lo * 0.60 + (lo - hi) * 0.40

        shaped * env * amp
    }
}

private fun metalScrape(dur: Double, amp: Double): DoubleArray {
    val n = (SR * dur).toInt()
    val partials = listOf(317.0 to 0.28, 529.0 to 0.22, 743.0 to 0.18,
        1151.0 to 0.14, 1847.0 to 0.10, 2999.0 to 0.08)
    val phases = DoubleArray(partials.size)
    var noise = 0.0

    return DoubleArray(n) { i ->
        val t   = i.toDouble() / n
        val env = exp(-t * 2.5) * (0.7 + 0.3 * sin(2 * PI * t * 11.3))  // flutter

        noise = 0.35 * rng.nextGaussian() + 0.65 * noise
        var s = noise * 0.22   // broadband noise base

        partials.forEachIndexed { idx, (freq, gain) ->
            s += sin(2 * PI * phases[idx]) * gain
            phases[idx] = (phases[idx] + freq / SR) % 1.0
        }

        s * env * amp
    }
}

private fun subBoom(hz: Double, dur: Double, amp: Double): DoubleArray {
    val f  = hz.coerceIn(18.0, 55.0)
    val n  = (SR * dur).toInt()
    var p1 = 0.0; var p2 = 0.0
    val i1 = f          / SR
    val i2 = f * 1.0085 / SR

    return DoubleArray(n) { i ->
        val t      = i.toDouble() / n
        val attack = minOf(t / 0.015, 1.0)   // 15 ms attack: physical snap
        val decay  = exp(-t * 7.5)
        val env    = attack * decay

        val s = sin(2 * PI * p1) * 0.68 + sin(2 * PI * p2) * 0.32
        p1 = (p1 + i1) % 1.0
        p2 = (p2 + i2) % 1.0

        tanh(s * env * amp * 2.1)
    }
}

private fun shepardRise(rootHz: Double, dur: Double, amp: Double): DoubleArray {
    val n          = (SR * dur).toInt()
    val numOctaves = 7
    val centerLog  = ln(480.0)   // bell center at 480 Hz (sits in vocal range - most disturbing)
    val sigma      = 1.6         // width in log-Hz units

    // Distribute 7 voices across octaves, all anchored near the root pitch class
    val startFreqs = Array(numOctaves) { oct ->
        var f = rootHz
        while (f < 55.0)   f *= 2.0
        while (f > 3500.0) f /= 2.0
        repeat(oct) { f *= 2.0 }
        f
    }

    val phases = DoubleArray(numOctaves)

    return DoubleArray(n) { i ->
        val t              = i.toDouble() / n
        val riseMultiplier = 2.0.pow(t / 12.0)   // one semitone per bar duration

        var s = 0.0
        for (oct in 0 until numOctaves) {
            var currentFreq = startFreqs[oct] * riseMultiplier
            // Wrap: when a voice exits the top, it silently re-enters at the bottom
            while (currentFreq > 5000.0) currentFreq /= 2.0
            while (currentFreq < 30.0)   currentFreq *= 2.0

            val logF   = ln(currentFreq)
            val weight = exp(-((logF - centerLog).pow(2)) / (2.0 * sigma * sigma))

            s += sin(2 * PI * phases[oct]) * weight
            phases[oct] = (phases[oct] + currentFreq / SR) % 1.0
        }

        val env = sin(PI * t).pow(0.5)   // fade in/out to blend with bar edges
        tanh(s * env * amp * 0.9)
    }
}

private fun midiToHz(note: Int): Double = 440.0 * 2.0.pow((note - 69) / 12.0)

private fun detune(index: Int): Double =
    1.0 + (index - 1.5) * 0.0012 + (rng.nextDouble() - 0.5) * 0.0012

private fun mixInto(dst: DoubleArray, src: DoubleArray, start: Int) {
    for (i in src.indices) {
        val j = start + i
        if (j in dst.indices) dst[j] += src[i]
    }
}

private fun playAsync(buf: DoubleArray, volume: Double) {
    hitPool.submit { play(buf, volume) }
}

private fun play(buf: DoubleArray, volume: Double = 0.10) {
    val peak = buf.maxOfOrNull { abs(it) }?.takeIf { it > 0.0001 } ?: return
    val pcm  = ByteBuffer.allocate(buf.size * 2)
    buf.forEach {
        val s = ((it / peak) * volume * Short.MAX_VALUE).toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        pcm.putShort(s.toShort())
    }
    try {
        val line = AudioSystem.getLine(DataLine.Info(SourceDataLine::class.java, FMT)) as SourceDataLine
        line.open(FMT); line.start()
        line.write(pcm.array(), 0, pcm.limit())
        line.drain(); line.close()
    } catch (e: LineUnavailableException) {
        System.err.println("[audio] ${e.message}")
    }
}