package com.bytedance.mathlab.core.euler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Euler's formula, checked with real numbers (sources in comments):
 *  ① the helix point is (cos ωt, sin ωt), also by an independent RK4 integration of z' = iωz;
 *    the damped spiral has radius e^{σt};
 *  ② |zw| = |z||w| and arg zw = arg z + arg w (mod 2π) on random samples; ×i is a quarter turn, i² = −1;
 *  ③ the series partial sums converge to e^{iθ} inside the Taylor bound |θ|^n/n!;
 *    e^{iπ} + 1 = 0 to within one rounding of π.
 */
class EulerTest {
    private fun dist(a: Complex, b: Complex) = (a - b).abs

    // ------------------------------------------------------------------ ①
    @Test fun helixPointIsCosSin() {
        var worst = 0.0
        for (omega in doubleArrayOf(-3.0, -1.0, -0.25, 0.0, 0.5, 1.0, 2.75, 3.0))
            for (k in 0..400) {
                val t = 4 * PI * k / 400
                val z = Euler.helix(omega, 0.0, t)
                worst = maxOf(worst, abs(z.re - cos(omega * t)), abs(z.im - sin(omega * t)))
            }
        println("EULER helix vs (cos ωt, sin ωt): max deviation %.2e".format(worst))
        assertTrue(worst < 1e-15)
        // landmarks: e^{iπ/2} = i, e^{iπ/3} = 1/2 + i√3/2, e^{2πi} = 1
        assertTrue(dist(Euler.cis(PI / 2), Complex.I) < 1e-15)
        assertTrue(dist(Euler.cis(PI / 3), Complex(0.5, sqrt(3.0) / 2)) < 1e-15)
        assertTrue(dist(Euler.cis(2 * PI), Complex.ONE) < 1e-15)
    }

    /** No trigonometry: integrate z' = iωz, z(0) = 1 (RK4, complex products only). It lands on cos ωt + i sin ωt. */
    @Test fun rk4OfZPrimeEqualsIOmegaZIsTheHelix() {
        val omega = 1.0; val t = 4 * PI
        val z = Euler.rk4(Complex(0.0, omega), t, 20_000)
        val err = dist(z, Complex(cos(omega * t), sin(omega * t)))
        println("EULER RK4 z' = iz to t = 4π (20,000 steps): |z − e^{4πi}| = %.2e, |z| = %.15f".format(err, z.abs))
        assertTrue(err < 1e-10)
        // the velocity iωz is perpendicular to z: the point runs round the circle at speed |ω|
        val p = Euler.helix(2.0, 0.0, 0.7); val v = Complex(0.0, 2.0) * p
        assertEquals(0.0, p.re * v.re + p.im * v.im, 1e-15)
        assertEquals(2.0, v.abs, 1e-15)
    }

    @Test fun dampedSpiralRadiusIsExpSigmaT() {
        var worst = 0.0
        for (sigma in doubleArrayOf(-0.4, -0.25, -0.1, -0.02, 0.0))
            for (k in 0..200) {
                val t = 4 * PI * k / 200
                worst = maxOf(worst, abs(Euler.helix(1.5, sigma, t).abs / exp(sigma * t) - 1))
            }
        // independent: RK4 of z' = (σ + iω) z
        val sigma = -0.25; val omega = 2.0; val t = 4 * PI
        val z = Euler.rk4(Complex(sigma, omega), t, 20_000)
        val rel = abs(z.abs / Euler.radius(sigma, t) - 1)
        println("EULER damped spiral: max |r/e^{σt} − 1| = %.2e; RK4 at σ = −0.25, t = 4π: r = %.6e vs e^{σt} = %.6e (rel %.1e)"
            .format(worst, z.abs, Euler.radius(sigma, t), rel))
        assertTrue(worst < 1e-14)
        assertTrue(rel < 1e-9)
        assertEquals(exp(-PI), Euler.radius(-0.25, 4 * PI), 1e-16)        // σt = −π
    }

    // ------------------------------------------------------------------ ②
    @Test fun multiplicationMultipliesLengthsAndAddsAngles() {
        val rnd = java.util.Random(1748)
        var worstLen = 0.0; var worstArg = 0.0
        repeat(100_000) {
            val z = Complex.polar(0.05 + 3 * rnd.nextDouble(), (rnd.nextDouble() * 2 - 1) * PI)
            val w = Complex(rnd.nextGaussian(), rnd.nextGaussian())
            if (w.abs < 1e-6) return@repeat
            worstLen = maxOf(worstLen, abs((z * w).abs / (z.abs * w.abs) - 1))
            worstArg = maxOf(worstArg, abs(Euler.argDefect(z, w)))
        }
        println("EULER |zw| = |z||w|: max rel error %.2e; arg zw = arg z + arg w (mod 2π): max defect %.2e rad (100,000 samples)".format(worstLen, worstArg))
        assertTrue(worstLen < 1e-15)
        assertTrue(worstArg < 1e-14)
        // the demo's default pair: 1.2∠30° × 0.8∠45° = 0.96∠75°
        val zw = Complex.polar(1.2, PI / 6) * Complex.polar(0.8, PI / 4)
        assertEquals(0.96, zw.abs, 1e-15)
        assertEquals(75.0, Math.toDegrees(zw.arg), 1e-12)
    }

    @Test fun timesIIsAQuarterTurn() {
        assertEquals(Complex(-1.0, 0.0), Complex.I * Complex.I)                       // i² = −1, exactly
        assertEquals(0.0, dist(Complex.I * Complex.I * Complex.I * Complex.I, Complex.ONE), 0.0)   // i⁴ = 1 (im = −0.0)
        val rnd = java.util.Random(90)
        repeat(1000) {
            val z = Complex(rnd.nextGaussian(), rnd.nextGaussian())
            assertEquals(z * Complex.I, Euler.quarterTurns(z, 1))
            assertEquals(-z, Euler.quarterTurns(z, 2))
            assertEquals(z, Euler.quarterTurns(z, 4))
            assertEquals(PI / 2, Euler.wrap((z * Complex.I).arg - z.arg), 1e-14)
            assertEquals(z.abs, (z * Complex.I).abs, 0.0)
        }
        // de Moivre: (e^{iθ})^n = e^{inθ}
        val c = Euler.cis(0.4); var p = Complex.ONE
        for (n in 1..12) { p = p * c; assertTrue(dist(p, Euler.cis(0.4 * n)) < 1e-14) }
    }

    // ------------------------------------------------------------------ ③
    @Test fun seriesTermsTurn90DegreesEachStep() {
        val t = Euler.seriesTerms(PI, 12)
        // lengths π^k/k!: 1, π, π²/2 = 4.9348, π³/6 = 5.1677, π⁴/24 = 4.0587
        assertEquals(1.0, t[0].abs, 0.0); assertEquals(PI, t[1].abs, 1e-15)
        assertEquals(PI * PI / 2, t[2].abs, 1e-14); assertEquals(PI * PI * PI / 6, t[3].abs, 1e-14)
        assertEquals(4.058712126416768, t[4].abs, 1e-13)
        for (k in 1 until t.size) assertEquals(PI / 2, Euler.wrap(t[k].arg - t[k - 1].arg), 1e-12)
        // the chain's first corners at θ = π: S_2 = 1 + πi, S_3 = 1 − π²/2 + πi = −3.9348 + 3.1416i
        val s = Euler.partialSums(PI, 4)
        assertTrue(dist(s[1], Complex(1.0, PI)) < 1e-15)
        assertTrue(dist(s[2], Complex(1 - PI * PI / 2, PI)) < 1e-14)
    }

    @Test fun partialSumsConvergeInsideTheTaylorBound() {
        val sb = StringBuilder()
        for (theta in doubleArrayOf(0.5, 1.0, PI / 2, PI, 2 * PI)) {
            val exact = Euler.cis(theta)
            // floating-point floor: the chain swings out to max|S_k| (≈ 1e2 at θ = 2π) and cancels back to 1,
            // so the sum cannot be better than a few ulps of that swing
            val floor = 4 * Math.ulp(1.0) * Euler.partialSums(theta, 45).maxOf { maxOf(1.0, it.abs) }
            for (n in 1..45) {
                val err = dist(Euler.partialSum(theta, n), exact)
                val b = Euler.remainderBound(theta, n)
                assertTrue("θ = $theta, n = $n: error $err > bound $b + floor $floor", err <= b * (1 + 1e-12) + floor)
            }
        }
        // at θ = π (the numbers the reveal quotes)
        for (n in intArrayOf(5, 10, 12, 15, 20, 24, 25)) sb.append(" n=%d: %.2e (bound %.2e);".format(n, dist(Euler.partialSum(PI, n), Complex(-1.0, 0.0)), Euler.remainderBound(PI, n)))
        println("EULER |S_n − e^{iπ}| at θ = π:$sb")
        val e20 = dist(Euler.partialSum(PI, 20), Euler.cis(PI))
        val e25 = dist(Euler.partialSum(PI, 25), Euler.cis(PI))
        // 20 terms are NOT enough for 1e-12 (the first omitted term π²⁰/20! = 3.6e-9 dominates); 25 are.
        assertTrue(e20 in 1e-9..4e-9)
        assertTrue(e25 < 1e-12)
        assertEquals(25, Euler.termsFor(PI, 1e-12))
        assertEquals(12, Euler.termsFor(PI, 2e-3))       // what the demo draws (2e-3 units = 0.2 mm at 10 cm per unit)
        assertTrue(dist(Euler.partialSum(2 * PI, 45), Complex.ONE) < 1e-12)  // a full turn: back to 1
    }

    @Test fun eToTheIPiPlusOneIsZero() {
        val r = Complex.exp(Complex(0.0, PI)) + Complex.ONE
        // the residue is sin(fl(π)) = π − fl(π) = 1.2246467991473532e-16: double π is short of π by that much
        println("EULER e^{iπ} + 1 = (%.3e, %.17e)".format(r.re, r.im))
        assertTrue(r.abs < 1e-15)
        assertEquals(0.0, r.re, 0.0)
        assertEquals(1.2246467991473532e-16, r.im, 1e-31)
        // and through the series (25 terms) and through RK4 (no trig at all)
        assertTrue((Euler.partialSum(PI, 25) + Complex.ONE).abs < 1e-12)
        assertTrue((Euler.rk4(Complex.I, PI, 20_000) + Complex.ONE).abs < 1e-12)
    }
}
