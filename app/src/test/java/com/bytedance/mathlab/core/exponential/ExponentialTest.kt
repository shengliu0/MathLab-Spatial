package com.bytedance.mathlab.core.exponential

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import kotlin.math.E
import kotlin.math.abs

/**
 * The numbers of demos/exponential, checked against exact arithmetic and stated sources:
 *  - Sissa's board: 2^64 − 1 = 18446744073709551615 (BigInteger sum of the 64 squares = ULong.MAX_VALUE);
 *  - the rice: 25 mg grains, bulk 0.8 g/cm³, 6.25 cm squares -> 8 µm of column per grain; world production
 *    5.2e11 kg/yr (FAO Food Outlook, 2023/24 season) -> the board is ≈ 887 years of the world's rice;
 *  - Bernoulli's compounding (1+1/n)^n vs exact rationals ((n+1)^n / n^n in BigDecimal), monotone, -> e;
 *  - the race n^k vs 2^n by exact Long arithmetic; the R0 branching process vs the geometric series.
 */
class ExponentialTest {

    @Test fun theBoardHoldsTwoToTheSixtyFourMinusOne() {
        var sum = BigInteger.ZERO
        for (n in 1..64) {
            assertEquals(BigInteger.TWO.pow(n - 1), Chessboard.grains(n))
            sum = sum.add(Chessboard.grains(n))
            // every square holds one grain more than all squares before it
            assertEquals(Chessboard.total(n - 1).add(BigInteger.ONE), Chessboard.grains(n))
            assertEquals(sum, Chessboard.total(n))
            assertEquals(Chessboard.grains(n).toDouble(), Chessboard.grainsD(n), 0.0)
        }
        assertEquals(BigInteger("18446744073709551615"), sum)
        assertEquals("18446744073709551615", Chessboard.total(64).toString())
        assertEquals(ULong.MAX_VALUE, Chessboard.BOARD_TOTAL)
        assertEquals(BigInteger("9223372036854775808"), Chessboard.grains(64))
        println("EXP board: square 64 = ${Chessboard.grains(64)}, total = ${Chessboard.total(64)} (= ULong.MAX ${ULong.MAX_VALUE})")
    }

    @Test fun riceMassAndYearsOfWorldProduction() {
        // grain size consistent with the stated mass: 7.0 x 2.05 x 2.3 mm ellipsoid at 1.45 g/cm³ ≈ 25 mg
        val km = Rice.kernelMassKg()
        assertEquals(Rice.GRAIN_KG, km, 0.02 * Rice.GRAIN_KG)
        // heap volume 31.25 mm³ per grain; on a 6.25 cm square: exactly 8 µm of column per grain
        assertEquals(31.25e-9, Rice.GRAIN_BULK_M3, 1e-15)
        assertEquals(8e-6, Rice.COLUMN_M_PER_GRAIN, 1e-15)
        assertEquals(31.25, Rice.CELL_MM[0] * Rice.CELL_MM[1] * Rice.CELL_MM[2], 1e-9)
        val totalKg = Rice.massKg(Chessboard.totalD(64))
        val years = Rice.worldYears(totalKg)
        val sq64 = Rice.worldYears(Rice.massKg(Chessboard.grainsD(64)))
        println("EXP rice: kernel %.2f mg; board %.4e kg = %.1f years of world rice; square 64 alone %.1f years".format(km * 1e6, totalKg, years, sq64))
        assertEquals(4.6117e14, totalKg, 0.0001e14)
        assertTrue("≈ 900 years: $years", years in 850.0..920.0)
        assertEquals(886.86, years, 0.05)
        assertTrue("square 64 alone is 'a few hundred years': $sq64", sq64 in 400.0..500.0)
        // a 'bag' (25 kg) and a 'truck' (25 t) are reached early: square 21 and square 31
        assertEquals(21, (1..64).first { Rice.massKg(Chessboard.grainsD(it)) >= 25.0 })
        assertEquals(31, (1..64).first { Rice.massKg(Chessboard.grainsD(it)) >= 25_000.0 })
    }

    @Test fun landmarkHeightsFollowFromTheGrainSize() {
        // column of square n = 2^(n-1) * 8 µm = 2^(n+2) µm, exactly
        for (n in 1..64) assertEquals(2.0.pow(n + 2) * 1e-6, Rice.columnM(Chessboard.grainsD(n)), 1e-9 * Rice.columnM(Chessboard.grainsD(n)))
        val first = Rice.LANDMARKS.associate { it.key to Rice.firstSquareReaching(it.heightM) }
        println("EXP landmarks: " + Rice.LANDMARKS.joinToString { "${it.key} %.4g m -> square ${first[it.key]} (%.4g m)".format(it.heightM, Rice.columnM(Chessboard.grainsD(first[it.key]!!))) })
        assertEquals(mapOf("hand" to 16, "person" to 19, "tower" to 25, "everest" to 32, "moon" to 47, "sun" to 56), first)
        // independent check of two of them by hand: 2^15 * 8 µm = 0.262 m > 0.18 m > 2^14 * 8 µm = 0.131 m
        assertEquals(0.262144, Rice.columnM(Chessboard.grainsD(16)), 1e-9)
        assertEquals(0.131072, Rice.columnM(Chessboard.grainsD(15)), 1e-9)
        // square 64: 2^66 µm = 7.38e13 m ≈ 493 au
        assertEquals(493.2, Rice.columnM(Chessboard.grainsD(64)) / 1.495978707e11, 0.1)
        assertNull(Rice.passed(15)); assertEquals("hand", Rice.passed(16)!!.key); assertEquals("sun", Rice.passed(64)!!.key)
        assertEquals("person", Rice.nextLandmark(18)!!.key); assertNull(Rice.nextLandmark(56))
        // the log axis: square n sits at log2(grains) = n - 1, landmarks in between
        assertEquals(15.0, Rice.log2Grains(Rice.columnM(Chessboard.grainsD(16))), 1e-9)
    }

    @Test fun pilesAreToScaleAndTurnIntoColumns() {
        var grainsDrawn = 0
        for (n in 1..Rice.GRAIN_SQUARES) {
            val c = Rice.pileCells(n)
            assertEquals("square $n", 1 shl (n - 1), c[0] * c[1] * c[2])
            val p = Rice.pileM(n)
            assertEquals(Chessboard.grainsD(n) * Rice.GRAIN_BULK_M3, p[0] * p[1] * p[2], 1e-12)
            grainsDrawn += c[0] * c[1] * c[2]
        }
        assertEquals(255, grainsDrawn)                                  // the pool: 1 + 2 + ... + 128
        val p8 = Rice.pileM(8)
        println("EXP pile 8: %.1f x %.1f x %.1f mm; first column square ${Rice.FIRST_COLUMN}".format(p8[0] * 1e3, p8[1] * 1e3, p8[2] * 1e3))
        assertTrue(p8.all { it in 0.012..0.020 })                       // a compact brick, ~1.6 cm
        // the cube of square 13 (5.0 cm) still fits on a 6.25 cm square, square 14's (6.35 cm) does not
        assertEquals(14, Rice.FIRST_COLUMN)
        assertEquals(0.0504, Rice.cubeM(Chessboard.grainsD(13)), 0.0002)
        assertEquals(0.0635, Rice.cubeM(Chessboard.grainsD(14)), 0.0002)
        // cube and column agree in volume where they meet: column(14) * square² = cube(14)³
        assertEquals(Rice.cubeM(Chessboard.grainsD(14)).pow(3), Rice.columnM(Chessboard.grainsD(14)) * Rice.SQUARE_M * Rice.SQUARE_M, 1e-12)
    }

    /** (n+1)^n / n^n in 40-digit decimal arithmetic. */
    private fun exact(n: Int): Double =
        BigDecimal(BigInteger.valueOf(n + 1L).pow(n)).divide(BigDecimal(BigInteger.valueOf(n.toLong()).pow(n)), MathContext(40)).toDouble()

    @Test fun compoundingClimbsMonotonicallyToE() {
        assertEquals(2.0, Compound.f(1), 0.0)
        assertEquals(2.25, Compound.f(2), 1e-15)
        assertEquals(2.44140625, Compound.f(4), 1e-15)
        assertEquals(2.6130352902246781, Compound.f(12), 1e-14)       // (13/12)^12
        assertEquals(2.7145674820218743, Compound.f(365), 1e-13)      // (366/365)^365
        assertEquals(E, Compound.f(Compound.CONTINUOUS), 0.0)
        for (n in listOf(1, 2, 3, 7, 12, 52, 365, 1000)) assertEquals("n=$n", exact(n), Compound.f(n.toLong()), 2e-14 * exact(n))
        var prev = 1.0
        for (n in 1L..100_000L) { val v = Compound.f(n); assertTrue("increasing at $n", v > prev); assertTrue("below e at $n", v < E); prev = v }
        // shortfall ~ 1/(2n): 0.1 % needs n = 500 (daily, 365, is NOT enough: 0.137 %)
        assertEquals(500L, Compound.minPeriods(1e-3))
        assertTrue(Compound.shortfall(499) >= 1e-3 && Compound.shortfall(500) < 1e-3)
        assertEquals(0.001366, Compound.shortfall(365), 1e-6)
        assertEquals(5000L, Compound.minPeriods(1e-4))
        println("EXP compound: f(1)=2 f(12)=%.10f f(365)=%.10f f(1000)=%.10f e=%.10f; 0.1%% needs n=${Compound.minPeriods(1e-3)}".format(Compound.f(12), Compound.f(365), Compound.f(1000), E))
    }

    @Test fun interestLayersStackToTheBalance() {
        for (n in listOf(1L, 2L, 4L, 12L, 64L, 65L, 365L, 1000L, 10_000L)) {
            val ls = Compound.layers(n, 64)
            assertEquals(minOf(n, 64L).toInt(), ls.size)
            assertEquals(n, ls.sumOf { it.periods })
            assertEquals(1.0, ls.first().lo, 0.0)
            for (i in 1 until ls.size) { assertEquals(ls[i - 1].hi, ls[i].lo, 1e-12); assertTrue(ls[i].t > ls[i - 1].t) }
            assertEquals("top of n=$n", Compound.f(n), ls.last().hi, 1e-12)
            // one period's interest is the balance before it / n (Bernoulli's instalment)
            if (n <= 64) for (l in ls) assertEquals(l.lo / n, l.hi - l.lo, 1e-12)
        }
    }

    @Test fun theRaceCrossings() {
        // exact table for n = 1..6 (k = 2): 2^n vs n²
        assertEquals(listOf(1, 0, -1, 0, 1, 1), (1..6).map { Race.cmp(it, 2) })
        assertEquals(listOf(2, 4), Race.ties(2))
        assertEquals(5, Race.foreverFrom(2))
        assertEquals(3, Race.ratioGrowsFrom(2))
        assertEquals(10, Race.foreverFrom(3))
        assertEquals(emptyList<Int>(), Race.ties(3))
        // brute-force confirmation up to n = 62 (Long range): never behind again after the crossing
        for (k in 2..3) for (n in Race.foreverFrom(k)..62) assertTrue("k=$k n=$n", Race.cmp(n, k) > 0)
        assertTrue(Race.cmp(Race.foreverFrom(2) - 1, 2) <= 0 && Race.cmp(Race.foreverFrom(3) - 1, 3) <= 0)
        println("EXP race: 2^n > n^2 for all n >= ${Race.foreverFrom(2)} (ties ${Race.ties(2)}), 2^n > n^3 for all n >= ${Race.foreverFrom(3)}; 2^20 = ${Race.pow2(20)} vs 20^2 = ${Race.poly(20, 2)}")
    }

    @Test fun spreadTreeSizes() {
        assertEquals(127.0, Spread.totalExpected(2.0, 6), 1e-9)              // 2^7 − 1
        assertEquals(7.0, Spread.totalExpected(1.0, 6), 1e-12)
        assertEquals(32.171875, Spread.totalExpected(1.5, 6), 1e-9)        // (1.5^7 − 1)/0.5
        assertEquals(3.951424, Spread.totalExpected(0.8, 6), 1e-9)
        assertEquals(5.0, Spread.limitTotal(0.8), 1e-12)                    // 1/(1 − 0.8): it dies out
        assertTrue(Spread.limitTotal(1.0).isInfinite())
        for (r0 in listOf(0.8, 1.0, 1.5, 2.0)) {
            val t = Spread.tree(r0)
            assertEquals(Spread.GENERATIONS + 1, t.size)
            for (g in t.indices) {
                assertEquals("R0=$r0 g=$g weights", Spread.expected(r0, g), t[g].sumOf { it.weight }, 1e-9)
                if (g > 0) {
                    val ps = t[g].map { it.parent }
                    assertTrue(ps.all { it in t[g - 1].indices })
                    assertEquals("links never cross", ps, ps.sorted())
                }
            }
        }
        assertEquals(127, Spread.tree(2.0).sumOf { it.size })
        assertEquals(listOf(1, 2, 3, 4, 6, 8, 12), Spread.tree(1.5).map { it.size })
        println("EXP spread: drawn cases R0=2 -> ${Spread.tree(2.0).sumOf { it.size }}, R0=1.5 -> ${Spread.tree(1.5).map { it.size }}, R0=0.8 total ever = ${Spread.limitTotal(0.8)}")
    }

    private fun Double.pow(k: Int) = Math.pow(this, k.toDouble())
}
