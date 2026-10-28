// AlgebraCore — "multiplication IS area, cubing IS volume" (Euclid, Elements II, Props. 4-5), exact.
//
// Every length lives on a 0.1 grid and is stored as an integer number of TENTHS (a = 3.5 -> 35), so
// areas are exact integers of hundredths and volumes exact integers of thousandths. Nothing here is a
// floating-point approximation: "the two sides of the identity are equal" and "the pieces tile the
// square with no gap and no overlap" are checked with integer arithmetic (AlgebraCoreTest).
//
// The four stations of the demo:
//   1. (a+b)^2 = a^2 + 2ab + b^2      the (a+b) square cut at a on both sides       Elements II.4
//   2. (a+b)(a-b) = a^2 - b^2         cut-and-move: a^2 minus a b^2 corner, the     Elements II.5
//                                     top strip turned a quarter turn to the right
//   3. (a+b)^3 = a^3+3a^2b+3ab^2+b^3  the (a+b) cube cut at a on all three axes: 2x2x2 = 8 blocks,
//                                     C(3,k) of them carry k factors of b (Pascal row 1 3 3 1)
//   4. 1 + 3 + ... + (2n-1) = n^2     the n x n square peeled into L-shaped layers (gnomons);
//                                     the k-th L holds 2k-1 cells (Nicomachus / Pythagoreans)
// Pure Kotlin: no Android, no SDK.

package com.bytedance.mathlab.core.algebra

/** Axis-aligned rectangle [x0,x1] x [y0,y1] in tenths. */
data class Rect(val x0: Long, val y0: Long, val x1: Long, val y1: Long) {
    init { require(x1 >= x0 && y1 >= y0) { "degenerate rect $this" } }
    val w get() = x1 - x0
    val h get() = y1 - y0
    /** Area in hundredths. */
    val area get() = w * h
    fun overlapArea(o: Rect): Long {
        val w = minOf(x1, o.x1) - maxOf(x0, o.x0); val h = minOf(y1, o.y1) - maxOf(y0, o.y0)
        return if (w > 0 && h > 0) w * h else 0
    }
    fun inside(o: Rect) = x0 >= o.x0 && y0 >= o.y0 && x1 <= o.x1 && y1 <= o.y1
}

/** Axis-aligned box in tenths. */
data class Box(val x0: Long, val y0: Long, val z0: Long, val x1: Long, val y1: Long, val z1: Long) {
    val volume get() = (x1 - x0) * (y1 - y0) * (z1 - z0)
    fun overlapVolume(o: Box): Long {
        val dx = minOf(x1, o.x1) - maxOf(x0, o.x0); val dy = minOf(y1, o.y1) - maxOf(y0, o.y0); val dz = minOf(z1, o.z1) - maxOf(z0, o.z0)
        return if (dx > 0 && dy > 0 && dz > 0) dx * dy * dz else 0
    }
    fun inside(o: Box) = x0 >= o.x0 && y0 >= o.y0 && z0 >= o.z0 && x1 <= o.x1 && y1 <= o.y1 && z1 <= o.z1
}

/** A named piece of a 2-D dissection: [term] is its algebra ("a²", "ab", ...). */
data class Piece(val id: String, val term: String, val rect: Rect)

/** One of the 8 blocks of the (a+b) cube: bits (i, j, k) pick the a-part (0) or b-part (1) of each axis. */
data class Block(val i: Int, val j: Int, val k: Int, val box: Box) {
    /** How many of its three edges have length b (0..3) — its term is a^(3-nb) b^nb. */
    val nb get() = i + j + k
    val index get() = i + 2 * j + 4 * k
}

/**
 * A rigid motion of the plane: rotate by [quarterTurns] x 90 deg (counter-clockwise positive) about the
 * origin, then translate by ([tx], [ty]). Integer coefficients, so images of grid points are exact.
 */
data class Motion(val quarterTurns: Int, val tx: Long, val ty: Long) {
    fun apply(x: Long, y: Long): Pair<Long, Long> {
        val (rx, ry) = when (((quarterTurns % 4) + 4) % 4) {
            0 -> x to y
            1 -> -y to x
            2 -> -x to -y
            else -> y to -x
        }
        return (rx + tx) to (ry + ty)
    }
    /** Image of a rectangle (rotations by multiples of 90 deg keep it axis-aligned). */
    fun apply(r: Rect): Rect {
        val p = apply(r.x0, r.y0); val q = apply(r.x1, r.y1)
        return Rect(minOf(p.first, q.first), minOf(p.second, q.second), maxOf(p.first, q.first), maxOf(p.second, q.second))
    }
    /** The fixed point of the motion (its centre of rotation), as doubles; null for a pure translation. */
    fun pivot(): Pair<Double, Double>? {
        val q = ((quarterTurns % 4) + 4) % 4
        if (q == 0) return null
        // solve R p + t = p  ->  (I - R) p = t
        val (c, s) = when (q) { 1 -> 0.0 to 1.0; 2 -> -1.0 to 0.0; else -> 0.0 to -1.0 }
        val m00 = 1 - c; val m01 = s; val m10 = -s; val m11 = 1 - c
        val det = m00 * m11 - m01 * m10
        return ((tx * m11 - m01 * ty) / det) to ((m00 * ty - m10 * tx) / det)
    }
}

object Algebra {
    // ---------------------------------------------------------------- parameter domain (tenths)
    const val A_MIN = 15L; const val A_MAX = 45L
    const val B_MIN = 5L; const val B_MAX = 25L
    /** b <= a - GAP so that a - b > 0 (station 2 needs a real strip). */
    const val GAP = 5L
    const val N_MIN = 1; const val N_MAX = 10

    /** Clamp (a, b) into the domain, keeping [keepA] fixed when the two constraints collide. */
    fun clamp(a: Long, b: Long, keepA: Boolean): Pair<Long, Long> {
        var aa = a.coerceIn(A_MIN, A_MAX); var bb = b.coerceIn(B_MIN, B_MAX)
        if (bb > aa - GAP) { if (keepA) bb = aa - GAP else aa = bb + GAP }
        aa = aa.coerceIn(A_MIN, A_MAX); bb = bb.coerceIn(B_MIN, minOf(B_MAX, aa - GAP))
        return aa to bb
    }

    // ---------------------------------------------------------------- station 1: (a+b)^2
    /** The (a+b) square cut at a along both sides: a² (bottom left), ab (right), ab (top), b² (top right). */
    fun squarePieces(a: Long, b: Long): List<Piece> = listOf(
        Piece("a2", "a²", Rect(0, 0, a, a)),
        Piece("ab1", "ab", Rect(a, 0, a + b, a)),
        Piece("ab2", "ab", Rect(0, a, a, a + b)),
        Piece("b2", "b²", Rect(a, a, a + b, a + b)),
    )
    fun bigSquare(a: Long, b: Long) = Rect(0, 0, a + b, a + b)

    // ---------------------------------------------------------------- station 2: (a+b)(a-b)
    /**
     * The a square with the b² corner (top right) and the cut below the top strip:
     *   bottom = a x (a-b), strip = (a-b) x b (top left), corner = b x b (top right).
     */
    fun cutPieces(a: Long, b: Long): List<Piece> = listOf(
        Piece("bottom", "a(a−b)", Rect(0, 0, a, a - b)),
        Piece("strip", "b(a−b)", Rect(0, a - b, a - b, a)),
        Piece("corner", "b²", Rect(a - b, a - b, a, a)),
    )
    /** Where the strip goes: stood on its end to the right of the bottom piece. */
    fun stripTarget(a: Long, b: Long) = Rect(a, 0, a + b, a - b)
    /** The (a+b) x (a-b) rectangle the L becomes. */
    fun productRect(a: Long, b: Long) = Rect(0, 0, a + b, a - b)
    /**
     * The strip's move as ONE rigid motion: a quarter turn clockwise, (x, y) -> (y + b, -x + a - b).
     * Its fixed point (a/2, a/2 - b) is the pivot the demo swings the strip about.
     */
    fun stripMotion(a: Long, b: Long) = Motion(-1, b, a - b)

    // ---------------------------------------------------------------- station 3: (a+b)^3
    /** The (a+b) cube cut at a on every axis: 8 blocks, block (i,j,k) has edges (a|b, a|b, a|b). */
    fun cubeBlocks(a: Long, b: Long): List<Block> {
        val lo = longArrayOf(0, a); val hi = longArrayOf(a, a + b)
        return (0 until 8).map { n ->
            val i = n and 1; val j = (n shr 1) and 1; val k = (n shr 2) and 1
            Block(i, j, k, Box(lo[i], lo[j], lo[k], hi[i], hi[j], hi[k]))
        }
    }
    fun bigCube(a: Long, b: Long) = Box(0, 0, 0, a + b, a + b, a + b)
    /** The algebra term of a block with [nb] b-edges. */
    fun cubeTerm(nb: Int) = when (nb) { 0 -> "a³"; 1 -> "a²b"; 2 -> "ab²"; else -> "b³" }
    /** Binomial coefficient C(3, k): how many blocks carry k factors of b. */
    fun c3(k: Int) = intArrayOf(1, 3, 3, 1)[k]

    // ---------------------------------------------------------------- station 4: odd numbers
    /** Cells (column, row) of the k-th L-shaped layer (1-based) of an n x n square: max(col, row) = k - 1. */
    fun oddLayer(k: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>(2 * k - 1)
        for (c in 0 until k) out += c to (k - 1)               // top arm, left to right
        for (r in k - 2 downTo 0) out += (k - 1) to r           // right arm, top to bottom
        return out
    }

    // ---------------------------------------------------------------- exact identities (tenths in)
    /** (a+b)² and a² + 2ab + b², in hundredths. */
    fun squareSides(a: Long, b: Long) = (a + b) * (a + b) to (a * a + 2 * a * b + b * b)
    /** (a+b)(a-b) and a² - b², in hundredths. */
    fun diffSides(a: Long, b: Long) = (a + b) * (a - b) to (a * a - b * b)
    /** (a+b)³ and a³ + 3a²b + 3ab² + b³, in thousandths. */
    fun cubeSides(a: Long, b: Long) = (a + b) * (a + b) * (a + b) to (a * a * a + 3 * a * a * b + 3 * a * b * b + b * b * b)

    // ---------------------------------------------------------------- exact measure of unions
    /** Area of the union of [rs] (hundredths), by coordinate compression — independent of the piece sums. */
    fun unionArea(rs: List<Rect>): Long {
        val xs = rs.flatMap { listOf(it.x0, it.x1) }.distinct().sorted()
        val ys = rs.flatMap { listOf(it.y0, it.y1) }.distinct().sorted()
        var s = 0L
        for (i in 0 until xs.size - 1) for (j in 0 until ys.size - 1) {
            val cx = xs[i] + xs[i + 1]; val cy = ys[j] + ys[j + 1]          // doubled cell centre
            if (rs.any { 2 * it.x0 < cx && cx < 2 * it.x1 && 2 * it.y0 < cy && cy < 2 * it.y1 }) s += (xs[i + 1] - xs[i]) * (ys[j + 1] - ys[j])
        }
        return s
    }
    /** Volume of the union of [bs] (thousandths), by coordinate compression. */
    fun unionVolume(bs: List<Box>): Long {
        val xs = bs.flatMap { listOf(it.x0, it.x1) }.distinct().sorted()
        val ys = bs.flatMap { listOf(it.y0, it.y1) }.distinct().sorted()
        val zs = bs.flatMap { listOf(it.z0, it.z1) }.distinct().sorted()
        var s = 0L
        for (i in 0 until xs.size - 1) for (j in 0 until ys.size - 1) for (k in 0 until zs.size - 1) {
            val cx = xs[i] + xs[i + 1]; val cy = ys[j] + ys[j + 1]; val cz = zs[k] + zs[k + 1]
            if (bs.any { 2 * it.x0 < cx && cx < 2 * it.x1 && 2 * it.y0 < cy && cy < 2 * it.y1 && 2 * it.z0 < cz && cz < 2 * it.z1 })
                s += (xs[i + 1] - xs[i]) * (ys[j + 1] - ys[j]) * (zs[k + 1] - zs[k])
        }
        return s
    }
    /** Bounding box of a list of rectangles. */
    fun bounds(rs: List<Rect>) = Rect(rs.minOf { it.x0 }, rs.minOf { it.y0 }, rs.maxOf { it.x1 }, rs.maxOf { it.y1 })

    // ---------------------------------------------------------------- formatting
    /** Exact decimal of [v] / 10^[dec], trailing zeros dropped: (1225, 2) -> "12.25", (900, 2) -> "9". */
    fun fmt(v: Long, dec: Int): String {
        val neg = v < 0; var x = if (neg) -v else v
        var p = 1L; repeat(dec) { p *= 10 }
        val ip = x / p; var fp = x % p; var d = dec
        while (d > 0 && fp % 10 == 0L) { fp /= 10; d-- }
        x = ip
        val s = if (d == 0) "$x" else "$x." + fp.toString().padStart(d, '0')
        return if (neg) "-$s" else s
    }
}
