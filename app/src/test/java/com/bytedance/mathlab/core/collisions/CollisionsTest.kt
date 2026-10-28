package com.bytedance.mathlab.core.collisions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Galperin's billiard (Regular and Chaotic Dynamics 8(4), 2003; 3Blue1Brown 2019): with M/m = 100^n the number of
 * elastic collisions is the first n + 1 digits of π. Reference counts: 3, 31, 314, 3141, 31415, 314159
 * (= ⌊π·10^n⌋, OEIS A011545 for n ≤ 5).
 */
class CollisionsTest {
    private val expected = longArrayOf(3, 31, 314, 3141, 31415, 314159)

    @Test fun countsAreTheDigitsOfPi_angleMethod() {
        for (n in 0..5) {
            val c = Collisions.countByAngle(100.0.pow(n))
            println("ANGLE n=$n ratio=1e${2 * n} count=$c  π/θ=${Collisions.piOverTheta(100.0.pow(n))}")
            assertEquals(expected[n], c)
        }
    }

    @Test fun countsAreTheDigitsOfPi_velocityBruteForce() {
        for (n in 0..5) {
            val c = Collisions.countByVelocities(100.0.pow(n))
            println("VELOCITY n=$n count=$c")
            assertEquals(expected[n], c)
        }
    }

    @Test fun otherRatiosAgreeWithTheAngleFormula() {
        // integer π/θ case (M = 3m: θ = π/6 -> 5 collisions) and a few arbitrary ratios
        assertEquals(5L, Collisions.countByVelocities(3.0))
        for (r in doubleArrayOf(2.0, 3.0, 7.5, 16.0, 50.0, 1234.0, 98765.0))
            assertEquals("ratio $r", Collisions.countByAngle(r), Collisions.countByVelocities(r))
    }

    @Test fun eventSimulationWithPositionsMatchesAndConserves() {
        for (n in 0..4) {
            val ratio = 100.0.pow(n)
            val sim = BlockSim(ratio, 0.05, 0.22, 0.62, -0.3)
            var maxE = 0.0; var minGap = Double.MAX_VALUE; var kinds = 0; var last = 0; var alternates = true
            var maxR = 0.0
            while (!sim.done) {
                sim.advance(sim.next().first, 1) { k ->
                    if (k == last) alternates = false
                    last = k; kinds++
                }
                maxE = maxOf(maxE, abs(sim.energy - sim.E0) / sim.E0)
                minGap = minOf(minGap, sim.x2 - sim.x1 - sim.w1)
                // the phase point stays on the unit energy circle
                val ph = Collisions.phase(ratio, sim.vs, sim.vb, sim.V0)
                maxR = maxOf(maxR, abs(sqrt(ph[0] * ph[0] + ph[1] * ph[1]) - 1.0))
            }
            println("EVENT n=$n count=${sim.count} t_end=${"%.4f".format(sim.t)} relE=${"%.1e".format(maxE)} |r-1|=${"%.1e".format(maxR)}")
            assertEquals(expected[n], sim.count)
            assertTrue("energy drift $maxE", maxE < 1e-9)
            // momentum is NOT conserved at the wall (the wall pushes); it is conserved across block collisions — checked below
            assertTrue(alternates)
            assertTrue("blocks overlapped", minGap >= -1e-12)
            assertTrue("phase radius $maxR", maxR < 1e-9)
        }
    }

    @Test fun momentumConservedInEveryBlockCollision_energyEverywhere_n5() {
        val ratio = 1e10
        var v = 0.0; var V = -1.0; var last = 0; var n = 0L
        val E0 = 0.5 * ratio; var worstE = 0.0; var worstP = 0.0
        while (true) {
            val k = Collisions.nextKind(v, V, last)
            if (k == 0) break
            if (k == 1) {
                val p = v + ratio * V
                val r = Collisions.blockBounce(1.0, ratio, v, V); v = r[0]; V = r[1]
                worstP = maxOf(worstP, abs(v + ratio * V - p) / ratio)          // relative to |p0| = M·|V0|
            } else v = -v
            last = k; n++
            worstE = maxOf(worstE, abs(0.5 * v * v + 0.5 * ratio * V * V - E0) / E0)
        }
        println("N5 count=$n relE=${"%.1e".format(worstE)} relP=${"%.1e".format(worstP)}")
        assertEquals(314159L, n)
        assertTrue(worstE < 1e-9)
        assertTrue(worstP < 1e-9)
    }

    @Test fun chordsHaveEqualArcsAndInscribedAngleTheta() {
        for (n in 0..3) {
            val ratio = 100.0.pow(n)
            val th = Collisions.theta(ratio)
            assertEquals(atan(sqrt(1.0 / ratio)), th, 1e-15)
            var v = 0.0; var V = -1.0; var last = 0; var k = 0
            val pts = ArrayList<DoubleArray>()
            pts += Collisions.phase(ratio, v, V, -1.0)
            while (true) {
                val kind = Collisions.nextKind(v, V, last)
                if (kind == 0) break
                if (kind == 1) { val r = Collisions.blockBounce(1.0, ratio, v, V); v = r[0]; V = r[1] } else v = -v
                last = kind; k++
                pts += Collisions.phase(ratio, v, V, -1.0)
            }
            // every state on the circle, at the predicted angle
            for ((i, p) in pts.withIndex()) {
                assertEquals(1.0, sqrt(p[0] * p[0] + p[1] * p[1]), 1e-9)
                val a = atan2(p[1], p[0]); val want = Collisions.stateAngle(ratio, i)
                var d = abs(a - want); if (d > PI) d = 2 * PI - d
                assertTrue("n=$n state $i angle $a vs $want", d < 1e-9 || (i == 0 && abs(abs(a) - PI) < 1e-9))
            }
            // same-side neighbours are 2θ apart (equal arcs); the angle between the two chords at every inner vertex is θ
            for (i in 2 until pts.size) {
                val a0 = atan2(pts[i - 2][1], pts[i - 2][0]); val a1 = atan2(pts[i][1], pts[i][0])
                var d = abs(a0 - a1); if (d > PI) d = 2 * PI - d
                assertEquals("arc n=$n i=$i", 2 * th, d, 1e-9)
            }
            for (i in 1 until pts.size - 1) {
                val ux = pts[i - 1][0] - pts[i][0]; val uy = pts[i - 1][1] - pts[i][1]
                val wx = pts[i + 1][0] - pts[i][0]; val wy = pts[i + 1][1] - pts[i][1]
                val ang = Math.acos((ux * wx + uy * wy) / (sqrt(ux * ux + uy * uy) * sqrt(wx * wx + wy * wy)))
                assertEquals("inscribed n=$n i=$i", th, ang, 1e-7)
            }
            // the last state is in the end zone 0 ≤ angle ≤ θ
            val e = pts.last(); val ae = atan2(e[1], e[0])
            assertTrue("end zone n=$n: $ae", ae >= -1e-12 && ae <= th + 1e-12)
            println("PHASE n=$n θ=${"%.6f".format(th)} states=${pts.size} end angle=${"%.6f".format(ae)}")
        }
    }

    @Test fun cappedAdvanceNeverSkipsACollision() {
        val sim = BlockSim(1e6, 0.05, 0.22, 0.62, -0.3)
        var t = 0.0; var frames = 0
        while (!sim.done && frames < 100000) { t += 1.0 / 60 * 0.45; sim.advance(maxOf(t, sim.t), 11); if (sim.t < t) t = sim.t; frames++ }
        println("CAPPED count=${sim.count} frames=$frames")
        assertEquals(3141L, sim.count)
        assertEquals(3, Collisions.piDigitsMatched(314L))
        assertEquals(0, Collisions.piDigitsMatched(315L))
    }
}
