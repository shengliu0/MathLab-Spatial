// Colliding blocks compute π (G. Galperin, "Playing pool with π", Regular and Chaotic Dynamics 8(4), 2003;
// popularised by 3Blue1Brown, "The most unexpected answer to a counting puzzle", 2019).
//
// A big block M slides at V0 < 0 toward a small block m resting near a wall; floor frictionless, every
// collision perfectly elastic. With M/m = 100^n the total number of collisions is the first n + 1 digits
// of π: 3, 31, 314, 3141, 31415, 314159, …
//
// WHY (the phase-space picture). Put x = √M·V, y = √m·v (velocities scaled by √mass):
//   energy    ½MV² + ½mv² = E      ->  x² + y² = 2E            a CIRCLE
//   momentum  MV + mv = p          ->  √M·x + √m·y = p         lines of slope −√(M/m)
// A block–block collision keeps both: it is the reflection across the line through 0 with direction
// (√M, √m), at angle θ = arctan √(m/M). A wall collision flips v: reflection across the x axis. Two
// reflections = a rotation by 2θ, so the state hops round the circle in arcs of 2θ (inscribed angle θ at
// every bounce). The start is angle π; the game ends in the "end zone" 0 ≤ angle ≤ θ (0 ≤ v ≤ V: nobody
// catches anybody). Count = the number of θ steps that fit strictly below π: N = ⌈π/θ⌉ − 1
// (= ⌊π/θ⌋ unless π/θ is an integer, e.g. M = m: θ = π/4, N = 3). For M/m = 100^n, θ ≈ 10^−n, N ≈ π·10^n.
//
// Pure Kotlin, no Android. Everything in doubles; the event simulation is exact (closed-form collision
// times — the motion between collisions is uniform), not time-stepped.

package com.bytedance.mathlab.core.collisions

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sqrt

object Collisions {
    /** The bounce angle θ = arctan √(m/M) for [ratio] = M/m. */
    fun theta(ratio: Double): Double = atan(sqrt(1.0 / ratio))

    /** The count by the angle argument: the θ steps that fit strictly below π, N = ⌈π/θ⌉ − 1. */
    fun countByAngle(ratio: Double): Long {
        val q = PI / theta(ratio)
        val r = round(q)
        return if (abs(q - r) < 1e-9 * q) r.toLong() - 1 else floor(q).toLong()
    }

    /** π/θ (the readout shows it next to the count). */
    fun piOverTheta(ratio: Double): Double = PI / theta(ratio)

    /** Elastic block–block collision: (v', V') from (v, V) for masses m (small) and M (big). */
    fun blockBounce(m: Double, M: Double, v: Double, V: Double): DoubleArray {
        val s = m + M
        return doubleArrayOf(((m - M) * v + 2 * M * V) / s, ((M - m) * V + 2 * m * v) / s)
    }

    /** True when no further collision can happen: 0 ≤ v ≤ V (small block moving right, not faster than the big one). */
    fun finished(v: Double, V: Double): Boolean = v >= 0.0 && v <= V

    /** Next collision kind from velocities alone: 1 = blocks, 2 = wall, 0 = none. (Positions never change the order:
     *  after a block collision the blocks separate, after a wall bounce the small block moves away from the wall.) */
    fun nextKind(v: Double, V: Double, last: Int): Int = when {
        last != 1 && v > V -> 1
        last != 2 && v < 0.0 -> 2
        else -> 0
    }

    /** Count by brute force on the velocities alone (no positions): ratio M/m, big block launched at V0 = −1. */
    fun countByVelocities(ratio: Double): Long {
        var v = 0.0; var V = -1.0; var last = 0; var n = 0L
        while (true) {
            when (nextKind(v, V, last)) {
                1 -> { val r = blockBounce(1.0, ratio, v, V); v = r[0]; V = r[1]; last = 1 }
                2 -> { v = -v; last = 2 }
                else -> return n
            }
            n++
        }
    }

    /** Phase point (x, y) = (√M·V, √m·v) normalised by √M·|V0| (so the energy circle has radius 1). */
    fun phase(ratio: Double, v: Double, V: Double, V0: Double): DoubleArray {
        val k = abs(V0)
        return doubleArrayOf(V / k, sqrt(1.0 / ratio) * v / k)
    }

    /** Polar angle of the k-th state after a launch from angle π (exact): upper half π − 2jθ, lower half −(π − 2jθ). */
    fun stateAngle(ratio: Double, k: Int): Double {
        val th = theta(ratio)
        return if (k % 2 == 0) PI - k * th else -(PI - (k + 1) * th)
    }

    /** Polar angle helper. */
    fun angleOf(x: Double, y: Double): Double = atan2(y, x)

    /** π's digits, for the "the count IS π" reveal. */
    const val PI_DIGITS = "314159265358979"

    /** Leading digits of π that [count] matches (0 = none). */
    fun piDigitsMatched(count: Long): Int {
        val s = count.toString()
        var k = 0
        while (k < s.length && k < PI_DIGITS.length && s[k] == PI_DIGITS[k]) k++
        return if (k == s.length) k else 0
    }

    /** ⌈x⌉ helper exported for the UI (number of chords drawn per stride etc.). */
    fun ceilDiv(a: Long, b: Long): Long = ceil(a.toDouble() / b).toLong()
}

/**
 * Event-driven exact simulation of the two blocks and the wall (wall at x = 0, everything moves along x).
 *   small block: left face x1, width [w1], velocity v, mass 1
 *   big block:   left face x2, velocity V, mass [ratio]
 * [advance] processes collisions in order up to a target time and at most [maxEvents] of them per call, so a
 * renderer can cap the work per frame; when the cap bites, time stops at the last collision (= slow motion),
 * and NO collision is ever skipped — the count is exact.
 */
class BlockSim(val ratio: Double, val w1: Double, x1Start: Double, x2Start: Double, val V0: Double) {
    var x1 = x1Start; private set
    var x2 = x2Start; private set
    var vs = 0.0; private set
    var vb = V0; private set
    var t = 0.0; private set
    var count = 0L; private set
    /** 1 = blocks, 2 = wall, 0 = none yet. */
    var lastKind = 0; private set
    var lastEventT = 0.0; private set
    val E0 = 0.5 * ratio * V0 * V0
    val p0 = ratio * V0

    val energy get() = 0.5 * vs * vs + 0.5 * ratio * vb * vb
    val momentum get() = vs + ratio * vb
    val done get() = Collisions.nextKind(vs, vb, lastKind) == 0

    /** Time of the next collision (absolute) and its kind, or (+∞, 0). */
    fun next(): Pair<Double, Int> {
        return when (Collisions.nextKind(vs, vb, lastKind)) {
            1 -> { val gap = (x2 - x1 - w1).coerceAtLeast(0.0); (t + gap / (vs - vb)) to 1 }
            2 -> { val gap = x1.coerceAtLeast(0.0); (t + gap / -vs) to 2 }
            else -> Double.POSITIVE_INFINITY to 0
        }
    }

    private fun drift(to: Double) {
        val dt = to - t
        if (dt > 0) { x1 += vs * dt; x2 += vb * dt; t = to }
    }

    /**
     * Advance to [tTarget], processing at most [maxEvents] collisions. Calls [onEvent](kind) after each.
     * Returns the number of collisions processed. If the cap was hit, t stays at the last collision time.
     */
    fun advance(tTarget: Double, maxEvents: Int, onEvent: (Int) -> Unit = {}): Int {
        var n = 0
        while (true) {
            val (te, kind) = next()
            if (kind == 0 || te > tTarget) { drift(tTarget); return n }
            if (n >= maxEvents) return n
            drift(te)
            if (kind == 1) {
                x1 = x2 - w1                           // exact contact (no rounding drift into each other)
                val r = Collisions.blockBounce(1.0, ratio, vs, vb); vs = r[0]; vb = r[1]
            } else { x1 = 0.0; vs = -vs }
            lastKind = kind; count++; n++; lastEventT = te
            onEvent(kind)
        }
    }
}
