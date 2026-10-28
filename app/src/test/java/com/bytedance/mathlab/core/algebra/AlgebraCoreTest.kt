package com.bytedance.mathlab.core.algebra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The geometric proofs, checked exactly (integer tenths -> hundredths / thousandths, no floating point):
 * every (a, b) on the demo's 0.1 grid, the pieces' measures sum to the left-hand side, the pieces never
 * overlap, and their union (measured independently by coordinate compression) IS the target figure.
 * Sources: Euclid, Elements II.4 ((a+b)² = a² + b² + 2ab) and II.5 (the gnomon proof of (a+b)(a−b) = a² − b²);
 * the cube dissection is the 3-D analogue; 1 + 3 + ... + (2n−1) = n² is the Pythagorean gnomon sum.
 */
class AlgebraCoreTest {
    /** Every admissible (a, b) the demo can show (a 1.5..4.5, b 0.5..2.5, b <= a - 0.5), step 0.1. */
    private fun grid(): List<Pair<Long, Long>> = (Algebra.A_MIN..Algebra.A_MAX).flatMap { a ->
        (Algebra.B_MIN..minOf(Algebra.B_MAX, a - Algebra.GAP)).map { b -> a to b } }

    @Test fun squareDissectionIsExact() {
        var n = 0
        for ((a, b) in grid()) {
            val ps = Algebra.squarePieces(a, b); val big = Algebra.bigSquare(a, b)
            val sum = ps.sumOf { it.rect.area }
            val (lhs, rhs) = Algebra.squareSides(a, b)
            assertEquals("pieces sum (a,b)=($a,$b)", lhs, sum)
            assertEquals("identity (a,b)=($a,$b)", lhs, rhs)
            // a² and b² alone miss exactly the two ab rectangles
            assertEquals(2 * a * b, lhs - (a * a + b * b))
            for (i in ps.indices) for (j in i + 1 until ps.size) assertEquals("overlap ${ps[i].id}/${ps[j].id}", 0L, ps[i].rect.overlapArea(ps[j].rect))
            assertTrue(ps.all { it.rect.inside(big) })
            assertEquals("union = big square", big.area, Algebra.unionArea(ps.map { it.rect }))
            assertEquals(big, Algebra.bounds(ps.map { it.rect }))
            n++
        }
        println("ALGEBRA (a+b)²: $n (a,b) pairs exact, e.g. a=3,b=2: ${Algebra.squareSides(30, 20)} hundredths")
        assertEquals(2500L, Algebra.squareSides(30, 20).first)     // (3+2)² = 25
        // every admissible pair visited: sum over a = 15..45 of min(21, a - 9) = 195 + 336
        assertEquals(531, n)
    }

    @Test fun cutAndMoveTilesTheRectangle() {
        var n = 0
        for ((a, b) in grid()) {
            val (bottom, strip, corner) = Algebra.cutPieces(a, b)
            // the three pieces ARE the a square
            val sq = Rect(0, 0, a, a)
            assertEquals(a * a, Algebra.unionArea(listOf(bottom.rect, strip.rect, corner.rect)))
            assertEquals(0L, bottom.rect.overlapArea(strip.rect) + bottom.rect.overlapArea(corner.rect) + strip.rect.overlapArea(corner.rect))
            assertEquals(sq, Algebra.bounds(listOf(bottom.rect, strip.rect, corner.rect)))
            // the strip, moved by ONE rigid motion (quarter turn), lands exactly in the target slot
            val m = Algebra.stripMotion(a, b)
            val moved = m.apply(strip.rect)
            assertEquals("strip lands (a,b)=($a,$b)", Algebra.stripTarget(a, b), moved)
            assertEquals(strip.rect.area, moved.area)                                // isometry: area kept
            // bottom + moved strip tile (a+b) x (a-b): no gap, no overlap, exact bounds
            val target = Algebra.productRect(a, b)
            assertEquals(0L, bottom.rect.overlapArea(moved))
            assertEquals(target.area, Algebra.unionArea(listOf(bottom.rect, moved)))
            assertEquals(target, Algebra.bounds(listOf(bottom.rect, moved)))
            val (lhs, rhs) = Algebra.diffSides(a, b)
            assertEquals(lhs, target.area); assertEquals(rhs, a * a - corner.rect.area); assertEquals(lhs, rhs)
            // the pivot of the swing is (a/2, a/2 - b) — a fixed point of the motion
            val (px, py) = m.pivot()!!
            assertEquals(a / 2.0, px, 1e-12); assertEquals(a / 2.0 - b, py, 1e-12)
            n++
        }
        println("ALGEBRA (a+b)(a−b): $n pairs; a=3,b=2: strip ${Algebra.cutPieces(30, 20)[1].rect} -> ${Algebra.stripMotion(30, 20).apply(Algebra.cutPieces(30, 20)[1].rect)}, " +
            "target ${Algebra.productRect(30, 20)} area ${Algebra.diffSides(30, 20).first} = 5.00")
        assertEquals(500L, Algebra.diffSides(30, 20).first)                     // (3+2)(3-2) = 5
    }

    @Test fun cubeBlocksPartitionTheCube() {
        var n = 0
        for ((a, b) in grid()) {
            val bl = Algebra.cubeBlocks(a, b); val big = Algebra.bigCube(a, b)
            assertEquals(8, bl.size)
            val (lhs, rhs) = Algebra.cubeSides(a, b)
            assertEquals(lhs, rhs)
            assertEquals("volumes sum (a,b)=($a,$b)", lhs, bl.sumOf { it.box.volume })
            for (i in bl.indices) for (j in i + 1 until bl.size) assertEquals(0L, bl[i].box.overlapVolume(bl[j].box))
            assertTrue(bl.all { it.box.inside(big) })
            assertEquals("union = cube", big.volume, Algebra.unionVolume(bl.map { it.box }))
            // each block's volume is its term: a^(3-nb) b^nb, and the group sums are the binomial terms
            for (blk in bl) {
                var v = 1L; repeat(3 - blk.nb) { v *= a }; repeat(blk.nb) { v *= b }
                assertEquals(v, blk.box.volume)
            }
            assertEquals(a * a * a, bl.filter { it.nb == 0 }.sumOf { it.box.volume })
            assertEquals(3 * a * a * b, bl.filter { it.nb == 1 }.sumOf { it.box.volume })
            assertEquals(3 * a * b * b, bl.filter { it.nb == 2 }.sumOf { it.box.volume })
            assertEquals(b * b * b, bl.filter { it.nb == 3 }.sumOf { it.box.volume })
            n++
        }
        // Pascal row 3: 1 3 3 1 blocks with 0..3 factors of b
        val counts = IntArray(4).also { c -> Algebra.cubeBlocks(30, 20).forEach { c[it.nb]++ } }
        assertEquals(listOf(1, 3, 3, 1), counts.toList())
        assertEquals((0..3).map { Algebra.c3(it) }, counts.toList())
        println("ALGEBRA (a+b)³: $n pairs; a=3,b=2: ${Algebra.cubeSides(30, 20)} thousandths = 125, blocks per nb ${counts.toList()}")
        assertEquals(125_000L, Algebra.cubeSides(30, 20).first)
    }

    @Test fun oddLayersBuildTheSquare() {
        for (n in 1..50) {
            val layers = (1..n).map { Algebra.oddLayer(it) }
            layers.forEachIndexed { i, l -> assertEquals(2 * (i + 1) - 1, l.size) }
            val all = layers.flatten()
            assertEquals("no cell twice (n=$n)", all.size, all.toSet().size)
            assertEquals(n * n, all.size)
            assertTrue(all.all { (c, r) -> c in 0 until n && r in 0 until n })
            assertEquals(n * n, (1..n).sumOf { 2 * it - 1 })
            // gnomon of layer k: (k)^2 - (k-1)^2 = 2k - 1, i.e. the 2ab + b² of station 1 with a = k-1, b = 1
            for (k in 1..n) assertEquals(Algebra.squareSides(10L * (k - 1), 10).first - 100L * (k - 1) * (k - 1), 100L * (2 * k - 1))
        }
        println("ALGEBRA odd sums: 1+3+...+19 = ${(1..10).sumOf { 2 * it - 1 }} = 10², layers checked for n = 1..50")
    }

    @Test fun motionsAndFormatting() {
        // quarter turns compose to the identity, and rotate grid points exactly
        val m = Motion(1, 0, 0)
        var p = 7L to 3L; repeat(4) { p = m.apply(p.first, p.second) }
        assertEquals(7L to 3L, p)
        assertEquals(-3L to 7L, m.apply(7, 3))
        assertEquals("12.25", Algebra.fmt(1225, 2)); assertEquals("9", Algebra.fmt(900, 2)); assertEquals("0.5", Algebra.fmt(5, 1))
        assertEquals("91.125", Algebra.fmt(91125, 3)); assertEquals("343", Algebra.fmt(343000, 3)); assertEquals("0.03", Algebra.fmt(3, 2))
        // the clamp keeps b < a
        assertEquals(30L to 25L, Algebra.clamp(30, 40, keepA = true))
        assertEquals(30L to 25L, Algebra.clamp(10, 25, keepA = false))
        assertEquals(45L to 25L, Algebra.clamp(99, 99, keepA = true))
    }
}
