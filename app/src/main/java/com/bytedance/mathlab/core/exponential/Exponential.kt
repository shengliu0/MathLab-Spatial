// Exponential growth — the numbers behind demos/exponential (pure Kotlin, no Android; JVM-tested in
// core/exponential/ExponentialTest). Four small models, each exact where an exact answer exists:
//
//   Chessboard  Sissa's legend: square n holds 2^(n−1) grains; squares 1..n hold 2^n − 1 (BigInteger, exact).
//   Rice        the physical rice: grain mass, bulk density -> the volume one grain takes in a heap, the
//               column it makes on a chessboard square, landmark heights, world production.
//   Race        n, n^k and 2^n on integers (exact Long arithmetic): who leads, ties, and the n after which
//               2^n leads FOREVER (proved by a ratio argument, not just sampled).
//   Compound    Bernoulli 1683: 100 %/yr compounded n times -> (1 + 1/n)^n -> e; the interest layers.
//   Spread      a branching process with mean R0 offspring: expected R0^g cases in generation g.

package com.bytedance.mathlab.core.exponential

import java.math.BigInteger
import kotlin.math.E
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.expm1
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.pow

/** Sissa's chessboard: the grain counts, exact. */
object Chessboard {
    const val SQUARES = 64

    /** Grains on square [n] (1-based): 2^(n−1). */
    fun grains(n: Int): BigInteger { require(n in 1..SQUARES); return BigInteger.ONE.shiftLeft(n - 1) }

    /** Grains on squares 1..[n]: 2^n − 1 (a geometric series: every square holds one more than all before it). */
    fun total(n: Int): BigInteger { require(n in 0..SQUARES); return BigInteger.ONE.shiftLeft(n).subtract(BigInteger.ONE) }

    /** The whole board as an unsigned 64-bit number: 2^64 − 1 = ULong.MAX_VALUE. */
    val BOARD_TOTAL: ULong get() = total(SQUARES).toString().toULong()

    /** 2^(n−1) as a double (exact: powers of two are exact in binary floating point). */
    fun grainsD(n: Int): Double = 2.0.pow(n - 1)
    fun totalD(n: Int): Double = 2.0.pow(n) - 1.0
}

/**
 * The physical rice, every number stated (the demo's UI quotes the same constants):
 *   - one grain of long-grain milled rice ≈ 25 mg (thousand-kernel weight 20–28 g);
 *   - kernel ≈ 7.0 × 2.05 × 2.3 mm, an ellipsoid of 17.3 mm³; at a kernel density of 1.45 g/cm³ that is 25 mg ✓;
 *   - poured rice has a BULK density ≈ 0.80 g/cm³ (packing ≈ 55 %), so in a heap one grain takes
 *     25 mg / 0.8 g/cm³ = 31.25 mm³;
 *   - a chessboard square of 6.25 cm (a tournament board): rice piled straight up on it rises
 *     31.25 mm³ / 3906.25 mm² = 8 µm per grain — square n's column is exactly 2^(n−1) × 8 µm = 2^(n+2) µm.
 *   - world rice production ≈ 5.2 × 10^11 kg per year — FAO Food Outlook (Nov 2023 / Jun 2024): world
 *     production 2022/23 = 516 Mt and 2023/24 ≈ 523–529 Mt, milled basis. We use 520 Mt/yr (season 2023/24).
 */
object Rice {
    const val GRAIN_KG = 25e-6
    const val BULK_KG_M3 = 800.0
    const val KERNEL_KG_M3 = 1450.0
    /** Kernel length, thickness, width (mm). */
    val KERNEL_MM = doubleArrayOf(7.0, 2.05, 2.3)
    const val SQUARE_M = 0.0625
    const val WORLD_KG_PER_YEAR = 5.2e11          // FAO Food Outlook, 2023/24 season, milled basis (≈ 520 Mt)

    /** Heap volume of one grain (m³): 3.125e-8 m³ = 31.25 mm³. */
    const val GRAIN_BULK_M3 = GRAIN_KG / BULK_KG_M3
    /** Column height per grain on one square (m): 8e-6. */
    const val COLUMN_M_PER_GRAIN = GRAIN_BULK_M3 / (SQUARE_M * SQUARE_M)

    /** Mass of the kernel ellipsoid at [KERNEL_KG_M3] (kg) — consistency check of the stated grain size. */
    fun kernelMassKg(): Double = Math.PI / 6.0 * KERNEL_MM[0] * KERNEL_MM[1] * KERNEL_MM[2] * 1e-9 * KERNEL_KG_M3

    fun massKg(grains: Double) = grains * GRAIN_KG
    /** Years of world rice production that [kg] of rice represents. */
    fun worldYears(kg: Double) = kg / WORLD_KG_PER_YEAR
    /** Height (m) of [grains] piled on one square. */
    fun columnM(grains: Double) = grains * COLUMN_M_PER_GRAIN
    /** Edge (m) of the cube [grains] of rice make when heaped compactly. */
    fun cubeM(grains: Double) = cbrt(grains * GRAIN_BULK_M3)

    // ---- how the demo draws each square, honestly to scale ----
    /** Squares 1..[GRAIN_SQUARES] are drawn grain by grain: 1 + 2 + ... + 128 = 255 grains. */
    const val GRAIN_SQUARES = 8
    /** First square whose rice no longer fits on its square as a cube: from here it is a column on the full square. */
    val FIRST_COLUMN: Int = (1..Chessboard.SQUARES).first { cubeM(Chessboard.grainsD(it)) >= SQUARE_M }

    /** Brick cell of one grain in a drawn pile (mm, along x / up y / along z): 7.5 × 2.05 × 2.0325 = 31.25 mm³. */
    val CELL_MM = doubleArrayOf(7.5, 2.05, 31.25 / (7.5 * 2.05))

    /**
     * How the 2^(n−1) grains of square [n] (n <= 8) are stacked: counts along (x, y, z). Start with one grain and
     * double the SHORTEST side each time, so the pile stays a compact brick (128 grains: 2 × 8 × 8 = 15 × 16 × 16 mm).
     */
    fun pileCells(n: Int): IntArray {
        require(n in 1..GRAIN_SQUARES)
        val c = intArrayOf(1, 1, 1)
        repeat(n - 1) {
            var k = 0
            for (a in 1..2) if (c[a] * CELL_MM[a] < c[k] * CELL_MM[k] - 1e-9) k = a
            c[k] *= 2
        }
        return c
    }

    /** Extent (m) of square [n]'s drawn pile (x, y, z). */
    fun pileM(n: Int): DoubleArray { val c = pileCells(n); return DoubleArray(3) { c[it] * CELL_MM[it] * 1e-3 } }

    class Landmark(val key: String, val zh: String, val en: String, val heightM: Double)

    /** Heights the columns pass. Everest: 8848.86 m (China–Nepal survey 2020); Moon: mean distance 384,400 km;
     *  Sun: 1 au = 149,597,870,700 m (IAU 2012). */
    val LANDMARKS = listOf(
        Landmark("hand", "一只手", "a hand", 0.18),
        Landmark("person", "一个人", "a person", 1.70),
        Landmark("tower", "百米高楼", "a 100 m tower", 100.0),
        Landmark("everest", "珠穆朗玛峰", "Mount Everest", 8848.86),
        Landmark("moon", "月球", "the Moon", 3.844e8),
        Landmark("sun", "太阳", "the Sun", 1.495978707e11),
    )

    /** The first square whose column reaches [heightM]. */
    fun firstSquareReaching(heightM: Double): Int? = (1..Chessboard.SQUARES).firstOrNull { columnM(Chessboard.grainsD(it)) >= heightM }

    /** The highest landmark square [n]'s column reaches (null: not even a hand). */
    fun passed(n: Int): Landmark? = LANDMARKS.lastOrNull { columnM(Chessboard.grainsD(n)) >= it.heightM }
    /** The next landmark above square [n]'s column. */
    fun nextLandmark(n: Int): Landmark? = LANDMARKS.firstOrNull { columnM(Chessboard.grainsD(n)) < it.heightM }

    /** Height on the LOG axis: log2 of the grain count a column of [heightM] holds (= n − 1 for square n). */
    fun log2Grains(heightM: Double) = ln(heightM / COLUMN_M_PER_GRAIN) / ln(2.0)
}

/** The race: n, n^k and 2^n on the integers, exact. */
object Race {
    const val N_MAX = 20

    fun pow2(n: Int): Long { require(n in 0..62); return 1L shl n }
    fun poly(n: Int, k: Int): Long { var v = 1L; repeat(k) { v = Math.multiplyExact(v, n.toLong()) }; return v }
    /** sign(2^n − n^k). */
    fun cmp(n: Int, k: Int): Int = pow2(n).compareTo(poly(n, k))

    /**
     * Past n* = 1 / (2^(1/k) − 1) the ratio 2^n / n^k grows every step: r(n+1)/r(n) = 2 (n/(n+1))^k >= 1 exactly
     * when (1 + 1/n)^k <= 2. So once 2^n > n^k at some n >= n*, it holds forever after.
     */
    fun ratioGrowsFrom(k: Int): Int = ceil(1.0 / (2.0.pow(1.0 / k) - 1.0)).toInt()

    /** The smallest n0 with 2^n > n^k for EVERY n >= n0 (k = 2: 5; k = 3: 10). */
    fun foreverFrom(k: Int): Int {
        val nStar = ratioGrowsFrom(k)
        var last = 0
        var n = 1
        while (true) {
            if (cmp(n, k) <= 0) last = n
            else if (n >= nStar) return last + 1
            n++
        }
    }

    /** Where 2^n == n^k exactly (k = 2: n = 2 and 4). */
    fun ties(k: Int, upTo: Int = N_MAX): List<Int> = (1..upTo).filter { cmp(it, k) == 0 }
}

/**
 * Jacob Bernoulli (1683, Acta Eruditorum 1690): a bank pays 100 % a year; paid as n instalments of 100/n %,
 * each earning interest afterwards, one unit becomes (1 + 1/n)^n — 2, 2.25, 2.4414, ..., increasing in n and
 * bounded by e = 2.71828... (continuous compounding).
 */
object Compound {
    /** Continuous compounding. */
    const val CONTINUOUS = 0L

    /** (1 + 1/n)^n computed as exp(n·ln(1 + 1/n)) with log1p, accurate to ~1e-15 for every n. */
    fun f(n: Long): Double = if (n == CONTINUOUS) E else exp(n * ln1p(1.0 / n))

    /** Balance after period [k] of [n] (k <= n): (1 + 1/n)^k. */
    fun balance(n: Long, k: Long): Double = exp(k * ln1p(1.0 / n))

    /** Relative shortfall 1 − f(n)/e, accurate also when tiny (expm1). */
    fun shortfall(n: Long): Double = if (n == CONTINUOUS) 0.0 else -expm1(n * ln1p(1.0 / n) - 1.0)

    /** The smallest n whose shortfall is below [eps] (shortfall decreases with n; 0.1 % -> 500). */
    fun minPeriods(eps: Double): Long {
        var lo = 1L; var hi = 1L
        while (shortfall(hi) >= eps) hi *= 2
        while (lo < hi) { val m = (lo + hi) / 2; if (shortfall(m) < eps) hi = m else lo = m + 1 }
        return lo
    }

    /** One drawn interest layer: paid at [t] (fraction of the year), from balance [lo] to [hi] (units of principal). */
    class Layer(val t: Double, val lo: Double, val hi: Double, val periods: Long)

    /**
     * The interest layers of an n-period year, at most [maxLayers] drawn boxes: for n <= maxLayers one per period
     * (paid at k/n, height B(k−1)/n); for bigger n consecutive periods are MERGED into one box that starts at the
     * group's first payment — the top of the last box is still exactly (1 + 1/n)^n.
     */
    fun layers(n: Long, maxLayers: Int): List<Layer> {
        require(n >= 1)
        val groups = minOf(n, maxLayers.toLong()).toInt()
        val out = ArrayList<Layer>(groups)
        var kEnd = 0L
        for (j in 1..groups) {
            val k0 = kEnd + 1
            kEnd = if (j == groups) n else (n * j) / groups
            out += Layer(k0.toDouble() / n, balance(n, k0 - 1), balance(n, kEnd), kEnd - k0 + 1)
        }
        return out
    }
}

/**
 * A branching process: every case infects on average R0 others in the next generation, so generation g has
 * R0^g cases on average and g generations together (R0^(g+1) − 1)/(R0 − 1). For R0 < 1 the total stays
 * finite even after infinitely many generations: 1 + R0 + R0² + ... = 1/(1 − R0) — the outbreak dies out.
 */
object Spread {
    const val GENERATIONS = 6       // generations 0..6 are drawn

    fun expected(r0: Double, g: Int): Double = r0.pow(g)
    /** Expected cases in generations 0..[g]. */
    fun totalExpected(r0: Double, g: Int): Double =
        if (kotlin.math.abs(r0 - 1.0) < 1e-12) (g + 1).toDouble() else (r0.pow(g + 1) - 1.0) / (r0 - 1.0)
    /** All generations ever: 1/(1 − R0) for R0 < 1, infinite otherwise. */
    fun limitTotal(r0: Double): Double = if (r0 < 1.0) 1.0 / (1.0 - r0) else Double.POSITIVE_INFINITY

    /** A drawn case: generation, index in it, parent index in generation g−1 (−1 for patient zero), weight 0..1. */
    class Case(val gen: Int, val idx: Int, val parent: Int, val weight: Double)

    /**
     * The drawn tree for [r0]: generation g shows its expected count E = R0^g as floor(E) whole cases plus one
     * partial case of weight frac(E) (drawn faint). The cases of generation g+1 are spread evenly, in order, over
     * the drawn cases of generation g, so no two links cross.
     */
    fun tree(r0: Double, gens: Int = GENERATIONS): List<List<Case>> {
        val out = ArrayList<List<Case>>()
        for (g in 0..gens) {
            val e = expected(r0, g)
            val whole = floor(e + 1e-9).toInt()
            val frac = e - whole
            val n = whole + if (frac > 1e-6) 1 else 0
            val prev = out.lastOrNull()
            out += List(n) { i ->
                val w = if (i < whole) 1.0 else frac
                val p = if (prev == null || prev.isEmpty()) -1 else minOf(prev.size - 1, ((i + 0.5) * prev.size / n).toInt())
                Case(g, i, p, w)
            }
        }
        return out
    }
}
