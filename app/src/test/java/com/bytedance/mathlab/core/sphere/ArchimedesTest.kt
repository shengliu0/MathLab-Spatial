package com.bytedance.mathlab.core.sphere

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Archimedes, "On the Sphere and Cylinder" I, Prop. 33 (area 4πr²), Prop. 34 cor. (volume 2/3 of the
 * circumscribed cylinder), and the hat-box theorem (zone area = 2πrh, the band of the cylinder of the same
 * height). Every check below computes the sphere side NUMERICALLY (inscribed frustums or a Simpson integral
 * of 2πρ ds) and compares it to the cylinder / closed form.
 */
class ArchimedesTest {
    private val r = 0.13
    private val deg = PI / 180

    @Test
    fun sphereBandIs2PiRhByIntegration() {
        var worst = 0.0
        for (lat in -85..85 step 5) for (h in listOf(0.002, 0.01, 0.02, 0.04)) {
            val yc = r * kotlin.math.sin(lat * deg)
            val y0 = (yc - h / 2).coerceAtLeast(-r); val y1 = (yc + h / 2).coerceAtMost(r)
            val exact = 2 * PI * r * (y1 - y0)
            val simpson = Archimedes.sphereBandAreaIntegral(r, y0, y1, 400)
            val frust = Archimedes.sphereBandAreaFrustums(r, y0, y1, 2000)
            worst = maxOf(worst, abs(simpson / exact - 1), abs(frust / exact - 1))
            assertEquals(exact, Archimedes.sphereBandArea(r, y0, y1), 1e-15)
        }
        println("SPHERE band: worst |numeric/2πrh - 1| = %.2e over 35 latitudes x 4 thicknesses".format(worst))
        assertTrue(worst < 1e-6)
    }

    @Test
    fun projectedBandOnCylinderIs2PiRhAndRatioIsOne() {
        // cylinder band = circumference 2πr times height h (exact); ratio to the numerically measured sphere band = 1
        var worst = 0.0
        for (lat in listOf(0.0, 30.0, 45.0, 60.0, 75.0, 85.0, -70.0)) for (h in listOf(0.001, 0.005, 0.02)) {
            val yc = r * kotlin.math.sin(lat * deg)
            val y0 = (yc - h / 2).coerceAtLeast(-r); val y1 = (yc + h / 2).coerceAtMost(r)
            val cyl = Archimedes.cylinderBandArea(r, y0, y1)
            assertEquals(2 * PI * r * (y1 - y0), cyl, 1e-15)
            val ratio = Archimedes.sphereBandAreaFrustums(r, y0, y1, 1000) / cyl
            worst = maxOf(worst, abs(ratio - 1))
        }
        println("RATIO sphere/cylinder band: worst |ratio - 1| = %.2e".format(worst))
        assertTrue(worst < 1e-6)
    }

    @Test
    fun thinBandFactorsCancel() {
        // slant width -> h/cosθ and radius -> r cosθ as h -> 0: the two factors multiply to 1
        for (lat in listOf(10.0, 45.0, 60.0, 75.0, 80.0)) {
            val th = lat * deg; val h = 1e-6
            val yc = r * kotlin.math.sin(th)
            val slant = Archimedes.slantWidth(r, yc - h / 2, yc + h / 2) / h
            assertEquals(Archimedes.slantFactor(th), slant, 1e-5 * Archimedes.slantFactor(th))
            val product = slant * Archimedes.radiusFactor(th)
            assertEquals(1.0, product, 1e-6)
            if (lat == 75.0) println("FACTORS at 75°: slant x%.4f, radius x%.4f, product %.6f".format(slant, cos(th), product))
        }
    }

    @Test
    fun bandsSumTo4PiR2() {
        val s = Archimedes.sphereAreaFromBands(r, 12, 400)
        println("BANDS 12 x 400 frustums: %.8f vs 4πr² = %.8f (rel %.1e)".format(s, 4 * PI * r * r, abs(s / (4 * PI * r * r) - 1)))
        assertEquals(4 * PI * r * r, s, 4 * PI * r * r * 1e-5)
        // the 12 equal-height bands are equal-area (Archimedes): each 1/12 of the sphere
        for (k in 0 until 12) assertEquals(4 * PI * r * r / 12, Archimedes.sphereBandAreaFrustums(r, -r + 2 * r * k / 12, -r + 2 * r * (k + 1) / 12, 400), 4 * PI * r * r / 12 * 1e-5)
        assertEquals(Archimedes.sphereArea(r), 4 * Archimedes.shadowArea(r), 1e-15)
    }

    @Test
    fun unrolledRectangleIs4PiR2AtEveryStage() {
        val (w, h) = Archimedes.unrolledSize(r)
        assertEquals(4 * PI * r * r, w * h, 1e-15)
        assertEquals(Archimedes.cylinderSideArea(r), w * h, 1e-15)
        // the bent sheet is isometric: a fine triangulation has the same area at every curvature
        for (kappa in listOf(1.0, 0.7, 0.35, 0.1, 0.0)) {
            val n = 4000; var a = 0.0
            for (k in 0 until n) {
                val s0 = -PI * r + 2 * PI * r * k / n; val s1 = -PI * r + 2 * PI * r * (k + 1) / n
                val p = Archimedes.bend(r, s0, 0.0, kappa); val q = Archimedes.bend(r, s1, 0.0, kappa)
                val chord = sqrt((q.first - p.first).let { it * it } + (q.third - p.third).let { it * it })
                a += chord * 2 * r
            }
            assertEquals(4 * PI * r * r, a, 4 * PI * r * r * 1e-6)
            // the front generator never moves
            val f = Archimedes.bend(r, 0.0, 0.05, kappa)
            assertEquals(0.0, f.first, 1e-12); assertEquals(r, f.third, 1e-12)
        }
        val back = Archimedes.bend(r, PI * r, 0.0, 1.0)
        assertEquals(-r, back.third, 1e-12)                    // kappa = 1 is the cylinder itself
        println("UNROLL: 2πr x 2r = %.6f x %.6f = %.8f = 4πr² %.8f".format(w, h, w * h, 4 * PI * r * r))
    }

    @Test
    fun stripToDiscPreservesArea() {
        // each of the 4 strips has area πr²; every cell keeps its area under the map
        val (sw, sh) = (PI * r / 2) to (2 * r)
        assertEquals(PI * r * r, sw * sh, 1e-15)
        var worst = 0.0
        for (cu in 0 until 6) for (ct in 0 until 12) {
            val u0 = cu / 6.0; val u1 = (cu + 1) / 6.0; val t0 = ct / 12.0; val t1 = (ct + 1) / 12.0
            val m = 400; val xy = ArrayList<Double>()
            fun add(u: Double, t: Double) { val (x, y) = Archimedes.stripToDisc(r, u, t); xy += x; xy += y }
            for (i in 0 until m) add(u0 + (u1 - u0) * i / m, t0)
            for (i in 0 until m) add(u1, t0 + (t1 - t0) * i / m)
            for (i in 0 until m) add(u1 - (u1 - u0) * i / m, t1)
            for (i in 0 until m) add(u0, t1 - (t1 - t0) * i / m)
            val disc = Archimedes.polygonArea(xy.toDoubleArray())
            val cell = sw * sh * (u1 - u0) * (t1 - t0)
            worst = maxOf(worst, abs(disc / cell - 1))
        }
        println("STRIP->DISC: worst cell area error %.2e (72 cells)".format(worst))
        assertTrue(worst < 1e-4)
        val rim = Archimedes.stripToDisc(r, 0.3, 1.0)
        assertEquals(r, sqrt(rim.first * rim.first + rim.second * rim.second), 1e-12)
    }

    @Test
    fun volumeIsTwoThirdsOfTheCylinder() {
        val v = Archimedes.sphereVolumeSlices(r, 20000)
        val ratio = v / Archimedes.cylinderVolume(r)
        println("VOLUME: slices %.10f / cylinder -> ratio %.8f (2/3 = %.8f); poured height %.4f of 2r".format(v, ratio, 2.0 / 3, Archimedes.pouredHeight(r) / (2 * r)))
        assertEquals(2.0 / 3.0, ratio, 1e-8)
        assertEquals(4 * r / 3, Archimedes.pouredHeight(r), 1e-15)
    }
}
