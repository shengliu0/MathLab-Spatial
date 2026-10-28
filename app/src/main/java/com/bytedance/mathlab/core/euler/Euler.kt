// Euler's formula — the pure maths behind the 欧拉公式 · 复数即旋转 experiment (no Android, no SDK).
//
//   e^{iθ} = cos θ + i sin θ        (Euler, Introductio in analysin infinitorum, 1748, §138)
//
// Three readings, one per station of the demo:
//   ① TIME:  z(t) = e^{(σ+iω)t} solves z' = (σ+iω) z, z(0) = 1. With t as a third axis it is a helix
//            (σ = 0) or a cone spiral (σ < 0) whose shadows are cos ωt and sin ωt (times e^{σt}).
//   ② ×:     z·w multiplies the lengths and ADDS the angles: multiplying by w rotates the whole plane by
//            arg w and scales it by |w|; ×i is a quarter turn, so i² = −1 is two quarter turns.
//   ③ SERIES: e^{iθ} = Σ (iθ)^k / k!. Each term is the previous one × iθ/k: turned 90°, scaled by θ/k —
//            a chain of arrows that spirals in onto the point at arc length θ on the unit circle.
//
// Everything is double precision; the demo converts to metres. Tests: EulerTest (real numbers, sources).

package com.bytedance.mathlab.core.euler

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.PI

/** A complex number re + i·im (immutable). */
data class Complex(val re: Double, val im: Double) {
    operator fun plus(o: Complex) = Complex(re + o.re, im + o.im)
    operator fun minus(o: Complex) = Complex(re - o.re, im - o.im)
    operator fun unaryMinus() = Complex(-re, -im)
    /** (a + bi)(c + di) = (ac − bd) + (ad + bc)i — the definition; no trigonometry involved. */
    operator fun times(o: Complex) = Complex(re * o.re - im * o.im, re * o.im + im * o.re)
    operator fun times(s: Double) = Complex(re * s, im * s)
    operator fun div(o: Complex): Complex {
        val d = o.re * o.re + o.im * o.im
        return Complex((re * o.re + im * o.im) / d, (im * o.re - re * o.im) / d)
    }
    /** |z| (hypot: no overflow / underflow in the squares). */
    val abs: Double get() = hypot(re, im)
    /** arg z in (−π, π]; 0 for z = 0. */
    val arg: Double get() = atan2(im, re)
    fun conj() = Complex(re, -im)

    companion object {
        val ZERO = Complex(0.0, 0.0)
        val ONE = Complex(1.0, 0.0)
        val I = Complex(0.0, 1.0)
        /** r·e^{iθ} = r(cos θ + i sin θ). */
        fun polar(r: Double, theta: Double) = Complex(r * cos(theta), r * sin(theta))
        /** e^{x + iy} = e^x (cos y + i sin y). */
        fun exp(z: Complex): Complex = polar(kotlin.math.exp(z.re), z.im)
    }
}

object Euler {
    const val TAU = 2.0 * PI

    /** e^{iθ}: the point reached by walking arc length θ counter-clockwise from 1 along the unit circle. */
    fun cis(theta: Double) = Complex(cos(theta), sin(theta))

    // ------------------------------------------------------------------ ① the helix e^{(σ+iω)t}

    /** z(t) = e^{(σ+iω)t}: the helix point (σ = 0: exactly (cos ωt, sin ωt)). */
    fun helix(omega: Double, sigma: Double, t: Double): Complex = Complex.exp(Complex(sigma * t, omega * t))

    /** |z(t)| = e^{σt}: the radius of the damped spiral (1 for the pure helix). */
    fun radius(sigma: Double, t: Double): Double = exp(sigma * t)

    /** Period of one turn, 2π/|ω| (infinite for ω = 0: the point just sits on the real axis). */
    fun period(omega: Double): Double = if (omega == 0.0) Double.POSITIVE_INFINITY else TAU / abs(omega)

    /**
     * An INDEPENDENT route to e^{λt}: integrate z' = λz, z(0) = 1 with classical Runge–Kutta (RK4), using
     * only complex multiplication — no cos, no sin. Euler's formula says the result is cos ωt + i sin ωt.
     */
    fun rk4(lambda: Complex, t: Double, steps: Int): Complex {
        val h = t / steps
        var z = Complex.ONE
        repeat(steps) {
            val k1 = lambda * z
            val k2 = lambda * (z + k1 * (h / 2))
            val k3 = lambda * (z + k2 * (h / 2))
            val k4 = lambda * (z + k3 * h)
            z = z + (k1 + k2 * 2.0 + k3 * 2.0 + k4) * (h / 6)
        }
        return z
    }

    /**
     * [n] samples (re, im, t) of z(t) for t in [0, tMax] — what the demo turns into the helix tube and its
     * three shadows (re → floor, im → side wall, (re, im) → front plane).
     */
    fun helixPath(omega: Double, sigma: Double, tMax: Double, n: Int): DoubleArray {
        val out = DoubleArray(n * 3)
        for (k in 0 until n) {
            val t = tMax * k / (n - 1)
            val z = helix(omega, sigma, t)
            out[k * 3] = z.re; out[k * 3 + 1] = z.im; out[k * 3 + 2] = t
        }
        return out
    }

    // ------------------------------------------------------------------ ② multiplication = rotation + scaling

    /** arg zw − (arg z + arg w), wrapped to (−π, π]: 0 for every z, w ≠ 0 (angles add mod 2π). */
    fun argDefect(z: Complex, w: Complex): Double = wrap((z * w).arg - z.arg - w.arg)

    /** Angle wrapped to (−π, π]. */
    fun wrap(a: Double): Double {
        var x = a % TAU
        if (x <= -PI) x += TAU
        if (x > PI) x -= TAU
        return x
    }

    /** z · i^k: k quarter turns counter-clockwise (exact: no rounding, just swaps and signs). */
    fun quarterTurns(z: Complex, k: Int): Complex = when (((k % 4) + 4) % 4) {
        0 -> z
        1 -> Complex(-z.im, z.re)
        2 -> Complex(-z.re, -z.im)
        else -> Complex(z.im, -z.re)
    }

    // ------------------------------------------------------------------ ③ the series 1 + iθ + (iθ)²/2! + …

    /**
     * Terms t_k = (iθ)^k / k!, k = 0 until [n], built as t_k = t_{k−1} · (iθ / k): each arrow is the previous
     * one turned by 90° (× i) and scaled by θ/k. No factorials, no powers, no overflow.
     */
    fun seriesTerms(theta: Double, n: Int): Array<Complex> {
        val out = Array(n) { Complex.ZERO }
        var t = Complex.ONE
        for (k in 0 until n) {
            if (k > 0) t = Complex(-t.im * theta / k, t.re * theta / k)     // t · iθ/k
            out[k] = t
        }
        return out
    }

    /** Partial sums S_1 … S_n (S_n = t_0 + … + t_{n−1}): the heads of the arrow chain. */
    fun partialSums(theta: Double, n: Int): Array<Complex> {
        val t = seriesTerms(theta, n)
        val out = Array(n) { Complex.ZERO }
        var re = 0.0; var im = 0.0
        for (k in 0 until n) { re += t[k].re; im += t[k].im; out[k] = Complex(re, im) }
        return out
    }

    /** S_n = Σ_{k<n} (iθ)^k / k!. */
    fun partialSum(theta: Double, n: Int): Complex = if (n <= 0) Complex.ZERO else partialSums(theta, n)[n - 1]

    /**
     * |e^{iθ} − S_n| ≤ |θ|^n / n! for real θ. Taylor with integral remainder along [0, θ]:
     * R_n = (iθ)^n/(n−1)! ∫₀¹ (1−s)^{n−1} e^{iθs} ds and |e^{iθs}| = 1, so |R_n| ≤ |θ|^n/(n−1)! · 1/n.
     * (Any analysis text, e.g. Apostol, Calculus I, §7.5 / Rudin, Principles, Thm 5.15 for the real case.)
     */
    fun remainderBound(theta: Double, n: Int): Double {
        var b = 1.0
        for (k in 1..n) b *= abs(theta) / k
        return b
    }

    /** Fewest terms n with |θ|^n / n! < [tol] (enough for the chain to look finished, or for a precision goal). */
    fun termsFor(theta: Double, tol: Double, nMax: Int = 200): Int {
        for (n in 1..nMax) if (remainderBound(theta, n) < tol) return n
        return nMax
    }
}
