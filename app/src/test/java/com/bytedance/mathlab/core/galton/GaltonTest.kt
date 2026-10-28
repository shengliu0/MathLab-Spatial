package com.bytedance.mathlab.core.galton

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class GaltonTest {

    @Test
    fun binomialPmfIsExact() {
        // Pascal's triangle: C(12, 6) = 924, row 16 centre C(16, 8) = 12870 (OEIS A000984 central binomials)
        assertEquals(924L, Binomial.choose(12, 6)); assertEquals(12870L, Binomial.choose(16, 8))
        for (n in 8..16) for (p in doubleArrayOf(0.05, 0.3, 0.5, 0.7, 0.95)) {
            val f = Binomial.pmf(n, p)
            assertEquals("sum n=$n p=$p", 1.0, f.sum(), 1e-12)
            for (k in 0..n) {
                // independent product form C(n,k) = n! / (k!(n-k)!)
                var c = 1.0; for (i in 1..n) c *= i; for (i in 1..k) c /= i; for (i in 1..n - k) c /= i
                assertEquals(c * p.pow(k) * (1 - p).pow(n - k), f[k], 1e-14)
            }
            val mean = f.indices.sumOf { it * f[it] }
            val varr = f.indices.sumOf { (it - mean) * (it - mean) * f[it] }
            assertEquals(n * p, mean, 1e-12); assertEquals(n * p * (1 - p), varr, 1e-12)
            // textbook mode floor((n+1)p) when (n+1)p is not an integer
            if (abs((n + 1) * p - Math.rint((n + 1) * p)) > 1e-9) assertEquals(((n + 1) * p).toInt(), Binomial.mode(n, p))
        }
        // centre bin of a 12-row fair board: 924 / 4096
        assertEquals(924.0 / 4096, Binomial.pmf(12, 0.5)[6], 1e-15)
        println("GALTON centre bin n=12 p=0.5: ${Binomial.pmf(12, 0.5)[6]}; mode(12, 0.7) = ${Binomial.mode(12, 0.7)}")
    }

    @Test
    fun normalCdfMatchesTables() {
        assertEquals(0.5, Normal.cdf(0.0), 1e-7)
        assertEquals(0.975, Normal.cdf(1.959964), 1e-6)            // z_0.975
        assertEquals(0.841344746, Normal.cdf(1.0), 1e-6)
        assertEquals(0.001349898, Normal.cdf(-3.0), 1e-7)
        assertEquals(0.398942280, Normal.pdf(0.0), 1e-9)
    }

    @Test
    fun simulatedBinsConverge() {
        for ((n, p) in listOf(12 to 0.5, 12 to 0.7, 16 to 0.3)) {
            val counts = LongArray(n + 1)
            GaltonSim(20261027L).fill(counts, n, p, 100_000)
            val f = Binomial.pmf(n, p)
            val maxErr = (0..n).maxOf { abs(counts[it] / 1e5 - f[it]) }
            val chi = GaltonSim.chiSquare(counts, p)
            val mean = GaltonSim.mean(counts); val v = GaltonSim.variance(counts)
            println("GALTON sim n=$n p=$p N=1e5: max|freq-pmf| = %.5f, chi2 = %.2f (dof %d), mean %.4f (np %.2f), var %.4f (npq %.3f)"
                .format(maxErr, chi, n, mean, n * p, v, n * p * (1 - p)))
            // freq sd <= sqrt(0.25/1e5) = 0.0016; 4 sd bound
            assertTrue(maxErr < 0.0064)
            // chi2 with n dof: 0.999 quantile is 32.9 for 12 dof, 39.3 for 16 dof
            assertTrue("chi2 $chi", chi < 39.3)
            assertEquals(n * p, mean, 0.02); assertEquals(n * p * (1 - p), v, 0.05)
        }
    }

    @Test
    fun deterministicWithSeed() {
        val a = LongArray(13); val b = LongArray(13); val c = LongArray(13)
        GaltonSim(7L).fill(a, 12, 0.5, 5000); GaltonSim(7L).fill(b, 12, 0.5, 5000); GaltonSim(8L).fill(c, 12, 0.5, 5000)
        assertArrayEquals(a, b)
        assertTrue(!a.contentEquals(c))
        val r1 = Rng(1L); val r2 = Rng(1L)
        repeat(1000) { val x = r1.nextDouble(); assertEquals(x, r2.nextDouble(), 0.0); assertTrue(x >= 0.0 && x < 1.0) }
    }

    @Test
    fun diceConvolutionIsExact() {
        // two fair dice: the triangle 1,2,3,4,5,6,5,4,3,2,1 / 36
        assertArrayEquals(longArrayOf(1, 2, 3, 4, 5, 6, 5, 4, 3, 2, 1), Die.fairCounts(2))
        val d2 = Die.FAIR.sumDist(2)
        for (i in d2.indices) assertEquals(Die.fairCounts(2)[i] / 36.0, d2[i], 1e-15)
        // three dice: P(S = 10) = P(S = 11) = 27/216
        assertEquals(27L, Die.fairCounts(3)[10 - 3]); assertEquals(27.0 / 216, Die.FAIR.sumDist(3)[10 - 3], 1e-15)
        // 30 dice: total 6^30 normalised, mean 105, variance 30·35/12 = 87.5
        for (die in Die.ALL) for (k in Die.KS) {
            val d = die.sumDist(k)
            assertEquals(1.0, d.sum(), 1e-12)
            val m = d.indices.sumOf { (k + it) * d[it] }
            val v = d.indices.sumOf { (k + it - m) * (k + it - m) * d[it] }
            assertEquals(k * die.mean, m, 1e-9); assertEquals(k * die.variance, v, 1e-8)
        }
        assertEquals(3.5, Die.FAIR.mean, 1e-15); assertEquals(35.0 / 12, Die.FAIR.variance, 1e-14)
        assertEquals(5, Die.TWO_VALUED.step); assertEquals(1, Die.SKEWED.step)
        assertEquals(32.0 / 63, Die.SKEWED.prob[5], 1e-15)
    }

    @Test
    fun distanceToNormalShrinksWithK() {
        val names = listOf("fair", "skewed", "two-valued")
        for ((i, die) in Die.ALL.withIndex()) {
            val tv = Die.KS.map { die.tv(it) }; val ks = Die.KS.map { die.ks(it) }
            println("GALTON ${names[i]} die (mu %.3f, sd %.3f): ".format(die.mean, die.sd) +
                Die.KS.indices.joinToString { "k=${Die.KS[it]} TV %.4f KS %.4f".format(tv[it], ks[it]) })
            for (j in 1 until tv.size) {
                // lattice TV is degenerate for the {1, 6} die (k = 1 has two cells and matches exactly), so it is
                // asserted for the fair and skewed dice only; KS (what the demo shows) shrinks for all three
                if (i < 2) assertTrue("${names[i]} TV not decreasing at k=${Die.KS[j]}", tv[j] < tv[j - 1])
                assertTrue("${names[i]} KS not decreasing at k=${Die.KS[j]}", ks[j] < ks[j - 1])
            }
            // Berry–Esseen: KS <= 0.4748 rho / (sigma^3 sqrt k) (Shevtsova 2011)
            val rho = (0 until 6).sumOf { abs(it + 1 - die.mean).pow(3) * die.prob[it] }
            for (k in Die.KS) assertTrue("BE ${names[i]} k=$k", die.ks(k) <= 0.4748 * rho / (die.sd.pow(3) * Math.sqrt(k.toDouble())) + 1e-6)
            assertTrue(tv.last() < 0.05); assertTrue(ks.last() < 0.08)
        }
    }
}
