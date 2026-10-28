// Galton board and the central limit theorem — pure Kotlin, no Android.
//
// ① Galton board (Galton, "Natural Inheritance", 1889): a ball meets n rows of pegs and at each peg goes right
//    with probability p, independently. Its bin = number of rights ~ Binomial(n, p):
//        P(K = k) = C(n, k) p^k (1 − p)^(n − k),   E K = n p,   Var K = n p (1 − p).
//    De Moivre–Laplace: for large n, K ≈ N(np, np(1 − p)).
// ② Sums of anything: S_k = X_1 + … + X_k for i.i.d. die faces X with any face weights. The exact law of S_k
//    is the k-fold convolution of the face law. CLT: (S_k − kμ)/(σ√k) → N(0, 1); Berry–Esseen bounds the
//    Kolmogorov distance by C ρ / (σ³ √k), so it shrinks like 1/√k whatever the die looks like.
//
// Distances to the normal (both computed EXACTLY from the convolution; only Φ is approximated, to ~1e-7):
//   * TV  = ½ Σ_s |P(S = s) − Q(s)| where Q(s) is the normal mass of the lattice cell [s − h/2, s + h/2]
//           (h = lattice step of the die; the end cells extend to ±∞ so Q sums to 1). This is the
//           "area between the histogram and the bell" the user sees, halved.
//   * KS  = sup_x |F_S(x) − Φ((x − kμ)/(σ√k))| (no continuity correction), the Berry–Esseen quantity.

package com.bytedance.mathlab.core.galton

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/** SplitMix64 (Steele, Lea & Flood 2014): tiny, fast, platform-independent — same sequence on JVM and headset. */
class Rng(seed: Long) {
    private var s = seed
    fun nextLong(): Long {
        s += -0x61c8864680b583ebL
        var z = s
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }
    /** Uniform in [0, 1) with 53 random bits. */
    fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))
}

object Binomial {
    /** C(n, k) exactly (n ≤ 62). */
    fun choose(n: Int, k: Int): Long {
        if (k < 0 || k > n) return 0L
        val kk = minOf(k, n - k)
        var c = 1L
        for (i in 1..kk) c = c * (n - kk + i) / i       // stays an integer at every step
        return c
    }

    /** P(K = k), k = 0..n. */
    fun pmf(n: Int, p: Double): DoubleArray = DoubleArray(n + 1) { k -> choose(n, k) * p.pow(k) * (1 - p).pow(n - k) }

    fun mean(n: Int, p: Double) = n * p
    fun variance(n: Int, p: Double) = n * p * (1 - p)

    /** The most likely bin (lowest index on a tie) — the textbook value is ⌊(n + 1) p⌋. */
    fun mode(n: Int, p: Double): Int { val f = pmf(n, p); var m = 0; for (k in 1..n) if (f[k] > f[m] + 1e-15) m = k; return m }
}

object Normal {
    /** Complementary error function, Numerical Recipes (2nd ed.) §6.2 `erfcc`: fractional error < 1.2e-7 everywhere. */
    fun erfc(x: Double): Double {
        val z = abs(x)
        val t = 1.0 / (1.0 + 0.5 * z)
        val ans = t * exp(-z * z - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418 + t * (-0.18628806 +
            t * (0.27886807 + t * (-1.13520398 + t * (1.48851587 + t * (-0.82215223 + t * 0.17087277)))))))))
        return if (x >= 0) ans else 2.0 - ans
    }
    /** Standard normal CDF Φ(z). */
    fun cdf(z: Double): Double = 0.5 * erfc(-z / sqrt(2.0))
    /** Standard normal density φ(z). */
    fun pdf(z: Double): Double = exp(-0.5 * z * z) / sqrt(2 * Math.PI)
}

/** The board: each ball = n independent coin flips with P(right) = p. */
class GaltonSim(seed: Long) {
    private val rng = Rng(seed)

    /** One ball's decisions: bit r = 1 means "right" at row r. Its bin is the number of set bits. */
    fun path(n: Int, p: Double): Int {
        var bits = 0
        for (r in 0 until n) if (rng.nextDouble() < p) bits = bits or (1 shl r)
        return bits
    }

    fun drop(n: Int, p: Double): Int = Integer.bitCount(path(n, p))

    /** Drop [m] balls straight into [counts] (size n + 1) — the "fast" mode, no animation. */
    fun fill(counts: LongArray, n: Int, p: Double, m: Int) { repeat(m) { counts[drop(n, p)]++ } }

    companion object {
        fun mean(counts: LongArray): Double { val t = counts.sum(); if (t == 0L) return 0.0; var s = 0.0; for (k in counts.indices) s += k * counts[k].toDouble(); return s / t }
        /** Population variance of the landed balls. */
        fun variance(counts: LongArray): Double {
            val t = counts.sum(); if (t == 0L) return 0.0
            val m = mean(counts); var s = 0.0
            for (k in counts.indices) s += (k - m) * (k - m) * counts[k]
            return s / t
        }
        /** Pearson χ² of [counts] against B(n, p) (bins with expectation < 1e-9 skipped). */
        fun chiSquare(counts: LongArray, p: Double): Double {
            val n = counts.size - 1; val f = Binomial.pmf(n, p); val t = counts.sum().toDouble(); var c = 0.0
            for (k in 0..n) { val e = t * f[k]; if (e > 1e-9) c += (counts[k] - e) * (counts[k] - e) / e }
            return c
        }
    }
}

/** A six-faced die with arbitrary face weights (faces 1..6). */
class Die(val weights: DoubleArray) {
    init { require(weights.size == 6) }
    val prob: DoubleArray = weights.sum().let { s -> DoubleArray(6) { weights[it] / s } }
    val mean: Double = (0 until 6).sumOf { (it + 1) * prob[it] }
    val variance: Double = (0 until 6).sumOf { (it + 1 - mean) * (it + 1 - mean) * prob[it] }
    val sd: Double get() = sqrt(variance)
    private val faces = (1..6).filter { prob[it - 1] > 0.0 }
    /** Smallest face with non-zero weight. */
    val minFace: Int = faces.first()
    /** Lattice step of the sums: gcd of the differences between possible faces ({1, 6} -> 5). */
    val step: Int = faces.drop(1).fold(0) { g, f -> gcd(g, f - faces[0]) }.let { if (it == 0) 1 else it }

    /** Exact law of the sum of [k] dice: element i = P(S = k + i), sums k..6k (k-fold convolution). */
    fun sumDist(k: Int): DoubleArray {
        var d = doubleArrayOf(1.0)                       // the sum of 0 dice is 0
        repeat(k) {
            val o = DoubleArray(d.size + 5)
            for (i in d.indices) if (d[i] != 0.0) for (f in 0 until 6) o[i + f] += d[i] * prob[f]
            d = o
        }
        return d
    }

    /** Total-variation distance between S_k and the normal N(kμ, kσ²) discretised on the lattice of S_k. */
    fun tv(k: Int): Double {
        val d = sumDist(k); val mu = k * mean; val s = sqrt(k * variance); val h = step.toDouble()
        var acc = 0.0
        val first = k * minFace; val last = k * minFace + ((d.size - 1 - (first - k)) / step) * step
        var x = first
        while (x <= last) {
            val lo = if (x == first) Double.NEGATIVE_INFINITY else (x - h / 2 - mu) / s
            val hi = if (x == last) Double.POSITIVE_INFINITY else (x + h / 2 - mu) / s
            val q = (if (hi.isInfinite()) 1.0 else Normal.cdf(hi)) - (if (lo.isInfinite()) 0.0 else Normal.cdf(lo))
            acc += abs(d[x - k] - q)
            x += step
        }
        return acc / 2
    }

    /** Kolmogorov–Smirnov distance sup_x |F_S(x) − Φ((x − kμ)/(σ√k))|; checked at both sides of every jump. */
    fun ks(k: Int): Double {
        val d = sumDist(k); val mu = k * mean; val s = sqrt(k * variance)
        var cum = 0.0; var worst = 0.0
        for (i in d.indices) {
            if (d[i] == 0.0) continue
            val phi = Normal.cdf((k + i - mu) / s)
            worst = maxOf(worst, abs(cum - phi))          // just before the jump
            cum += d[i]
            worst = maxOf(worst, abs(cum - phi))          // at the jump
        }
        return worst
    }

    companion object {
        private fun gcd(a: Int, b: Int): Int = if (b == 0) abs(a) else gcd(b, a % b)
        val FAIR = Die(doubleArrayOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0))
        /** Strongly loaded towards 6: weights 1:2:4:8:16:32 (P(6) = 32/63 ≈ 0.51, P(1) ≈ 0.016). */
        val SKEWED = Die(doubleArrayOf(1.0, 2.0, 4.0, 8.0, 16.0, 32.0))
        /** Only 1 or 6, half and half — a U-shaped, gappy die. */
        val TWO_VALUED = Die(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 1.0))
        val ALL = listOf(FAIR, SKEWED, TWO_VALUED)
        /** The k of each row of the convergence wall. */
        val KS = intArrayOf(1, 2, 3, 5, 10, 30)

        /** Exact counts of the sum of k FAIR dice (6^k outcomes, k ≤ 24 fits a Long): element i = #(S = k + i). */
        fun fairCounts(k: Int): LongArray {
            var d = longArrayOf(1L)
            repeat(k) { val o = LongArray(d.size + 5); for (i in d.indices) for (f in 0 until 6) o[i + f] += d[i]; d = o }
            return d
        }
    }
}
