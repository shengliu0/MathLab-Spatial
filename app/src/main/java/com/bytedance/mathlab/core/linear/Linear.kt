// Linear maps of 3-space — the maths behind the 线性变换与行列式 experiment (demos/linear).
//
// THE IDEA (Cayley 1858; presentation after 3Blue1Brown, "Essence of Linear Algebra" ch. 3-6, 13-14):
// a 3x3 matrix is nothing but "where î, ĵ, k̂ land" — its columns. Every other vector v = x î + y ĵ + z k̂
// follows by linearity: M v = x M î + y M ĵ + z M k̂. Three numbers summarise what happened to space:
//   * det M   = the SIGNED volume of the image of the unit cube = M î · (M ĵ × M k̂)  (triple product);
//               < 0 means orientation flipped (right hand -> left hand), = 0 means space was squashed
//               onto a plane or a line (and the map cannot be undone);
//   * eigen-directions: lines through the origin that M only stretches (M v = λ v) — they stay on
//               their own span while everything else turns. det = λ1 λ2 λ3 (complex ones included).
//
// Pure Kotlin, doubles, no Android/SDK. Everything the demo shows is computed here and JVM-tested
// (core/linear/LinearTest): det vs the cube's volume from its vertices (divergence theorem), eigen-
// pairs (A v = λ v) for symmetric, non-symmetric, defective and rotation matrices, composition order,
// the SVD that drives the scene's node transforms, and the animation path.

package com.bytedance.mathlab.core.linear

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    val norm: Double get() = sqrt(x * x + y * y + z * z)
    fun unit(): Vec3 { val n = norm; return if (n < 1e-300) this else this * (1.0 / n) }
    operator fun get(i: Int) = when (i) { 0 -> x; 1 -> y; else -> z }

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
        val X = Vec3(1.0, 0.0, 0.0)
        val Y = Vec3(0.0, 1.0, 0.0)
        val Z = Vec3(0.0, 0.0, 1.0)
        fun axis(i: Int) = when (i) { 0 -> X; 1 -> Y; else -> Z }
    }
}

/** 3x3 real matrix, row-major `m[r * 3 + c]`. Column c = the image of basis vector c (î, ĵ, k̂). */
class Mat3(val m: DoubleArray) {
    init { require(m.size == 9) }

    operator fun get(r: Int, c: Int) = m[r * 3 + c]
    fun col(c: Int) = Vec3(m[c], m[3 + c], m[6 + c])
    fun row(r: Int) = Vec3(m[r * 3], m[r * 3 + 1], m[r * 3 + 2])

    operator fun times(o: Mat3) = Mat3(DoubleArray(9) { k ->
        val r = k / 3; val c = k % 3
        m[r * 3] * o.m[c] + m[r * 3 + 1] * o.m[3 + c] + m[r * 3 + 2] * o.m[6 + c]
    })
    operator fun times(v: Vec3) = Vec3(
        m[0] * v.x + m[1] * v.y + m[2] * v.z, m[3] * v.x + m[4] * v.y + m[5] * v.z, m[6] * v.x + m[7] * v.y + m[8] * v.z)
    operator fun times(s: Double) = Mat3(DoubleArray(9) { m[it] * s })
    operator fun plus(o: Mat3) = Mat3(DoubleArray(9) { m[it] + o.m[it] })
    operator fun minus(o: Mat3) = Mat3(DoubleArray(9) { m[it] - o.m[it] })

    fun transpose() = Mat3(DoubleArray(9) { k -> m[(k % 3) * 3 + k / 3] })
    fun trace() = m[0] + m[4] + m[8]
    /** The determinant as the triple product of the columns: M î · (M ĵ × M k̂). */
    fun det(): Double = col(0) dot (col(1) cross col(2))
    fun frob(): Double = sqrt(m.sumOf { it * it })
    fun maxAbs(): Double = m.maxOf { abs(it) }
    fun withCol(c: Int, v: Vec3) = Mat3(m.copyOf().also { it[c] = v.x; it[3 + c] = v.y; it[6 + c] = v.z })

    /** Inverse via the adjugate (rows of the inverse = cross products of columns / det); null if singular. */
    fun inverse(eps: Double = 1e-12): Mat3? {
        val d = det()
        if (abs(d) < eps) return null
        val a = col(0); val b = col(1); val c = col(2)
        val r0 = (b cross c) * (1 / d); val r1 = (c cross a) * (1 / d); val r2 = (a cross b) * (1 / d)
        return Mat3(doubleArrayOf(r0.x, r0.y, r0.z, r1.x, r1.y, r1.z, r2.x, r2.y, r2.z))
    }

    fun isClose(o: Mat3, tol: Double) = (0 until 9).all { abs(m[it] - o.m[it]) <= tol }
    override fun equals(other: Any?) = other is Mat3 && m.contentEquals(other.m)
    override fun hashCode() = m.contentHashCode()
    override fun toString() = (0 until 3).joinToString(prefix = "[", postfix = "]", separator = "; ") { r ->
        (0 until 3).joinToString(" ") { c -> "%.4f".format(this[r, c]) }
    }

    companion object {
        fun identity() = diag(1.0, 1.0, 1.0)
        fun diag(a: Double, b: Double, c: Double) = Mat3(doubleArrayOf(a, 0.0, 0.0, 0.0, b, 0.0, 0.0, 0.0, c))
        fun fromCols(a: Vec3, b: Vec3, c: Vec3) = Mat3(doubleArrayOf(a.x, b.x, c.x, a.y, b.y, c.y, a.z, b.z, c.z))
        /** Rodrigues: rotation by [angle] (rad, right-handed) about [axis]. */
        fun rotation(axis: Vec3, angle: Double): Mat3 {
            val k = axis.unit(); val c = cos(angle); val s = sin(angle); val t = 1 - c
            return Mat3(doubleArrayOf(
                c + t * k.x * k.x, t * k.x * k.y - s * k.z, t * k.x * k.z + s * k.y,
                t * k.y * k.x + s * k.z, c + t * k.y * k.y, t * k.y * k.z - s * k.x,
                t * k.z * k.x - s * k.y, t * k.z * k.y + s * k.x, c + t * k.z * k.z))
        }
    }
}

/** One eigenvalue (real) with its algebraic multiplicity and an orthonormal-ish basis of its eigenspace. */
class EigenSpace(val lambda: Double, val algebraic: Int, val basis: List<Vec3>) {
    val geometric get() = basis.size
}

/** Real roots (descending) of a monic cubic; if only one is real, the complex pair re ± i·im. */
class CubicRoots(val real: DoubleArray, val re: Double = 0.0, val im: Double = 0.0) {
    val hasComplexPair get() = real.size == 1
}

/** M = U · diag(s) · Vᵀ with U, V proper rotations (det +1); s0 >= s1 >= |s2|, s2 carries sign(det M). */
class Svd(val u: Mat3, val s: Vec3, val v: Mat3)

object Linear {

    // ---------------------------------------------------------------- volume, the hard way

    /** The unit cube's corners: index i = x + 2 y + 4 z. */
    val CUBE: List<Vec3> = List(8) { i -> Vec3((i and 1).toDouble(), ((i shr 1) and 1).toDouble(), ((i shr 2) and 1).toDouble()) }

    /** The unit cube's surface as 12 triangles, counter-clockwise seen from OUTSIDE (outward normals). */
    val CUBE_TRIS: Array<IntArray> = arrayOf(
        intArrayOf(0, 2, 3), intArrayOf(0, 3, 1),   // z = 0 (normal -z)
        intArrayOf(4, 5, 7), intArrayOf(4, 7, 6),   // z = 1 (+z)
        intArrayOf(0, 1, 5), intArrayOf(0, 5, 4),   // y = 0 (-y)
        intArrayOf(2, 6, 7), intArrayOf(2, 7, 3),   // y = 1 (+y)
        intArrayOf(0, 4, 6), intArrayOf(0, 6, 2),   // x = 0 (-x)
        intArrayOf(1, 3, 7), intArrayOf(1, 7, 5),   // x = 1 (+x)
    )

    /**
     * Signed volume enclosed by a closed triangle mesh (divergence theorem: V = 1/6 Σ a·(b×c) over
     * outward-wound triangles). Independent of the triple-product determinant — the test compares them.
     */
    fun signedVolume(pts: List<Vec3>, tris: Array<IntArray>): Double =
        tris.sumOf { t -> pts[t[0]] dot (pts[t[1]] cross pts[t[2]]) } / 6.0

    /** Volume of M(unit cube), computed from the 8 transformed VERTICES (not from det). */
    fun imageCubeVolume(m: Mat3): Double = signedVolume(CUBE.map { m * it }, CUBE_TRIS)

    // ---------------------------------------------------------------- characteristic polynomial

    /** (tr, c1, det) of λ³ − tr λ² + c1 λ − det = det(λI − M); c1 = sum of the principal 2x2 minors. */
    fun charPoly(m: Mat3): DoubleArray {
        val c1 = m[0, 0] * m[1, 1] - m[0, 1] * m[1, 0] + m[0, 0] * m[2, 2] - m[0, 2] * m[2, 0] + m[1, 1] * m[2, 2] - m[1, 2] * m[2, 1]
        return doubleArrayOf(m.trace(), c1, m.det())
    }

    /**
     * Roots of λ³ + a λ² + b λ + c. Depressed cubic t³ + p t + q (λ = t − a/3); three real roots by the
     * trigonometric formula (Viète) when the discriminant (q/2)² + (p/3)³ <= ~0, else Cardano.
     * Near-repeated roots (projection: 1, 1, 0) give a discriminant that is 0 up to rounding: it is
     * treated as "three real" so a plane of eigenvectors is not mistaken for a complex pair.
     */
    fun cubicRoots(a: Double, b: Double, c: Double): CubicRoots {
        val sc = maxOf(1.0, abs(a) / 3, sqrt(abs(b)), cbrt(abs(c)))
        val p = b - a * a / 3
        val q = 2 * a * a * a / 27 - a * b / 3 + c
        val shift = -a / 3
        if (abs(p) < 1e-12 * sc * sc && abs(q) < 1e-12 * sc * sc * sc) return CubicRoots(doubleArrayOf(shift, shift, shift))
        val disc = (q / 2) * (q / 2) + (p / 3) * (p / 3) * (p / 3)
        if (disc > 1e-14 * sc * sc * sc * sc * sc * sc) {
            val sd = sqrt(disc)
            val u = cbrt(-q / 2 + sd); val v = cbrt(-q / 2 - sd)
            return CubicRoots(doubleArrayOf(u + v + shift), re = -(u + v) / 2 + shift, im = abs(u - v) * sqrt(3.0) / 2)
        }
        if (p >= 0) return CubicRoots(doubleArrayOf(shift, shift, shift))    // only reachable through rounding
        val r = 2 * sqrt(-p / 3)
        val arg = (3 * q / (2 * p) * sqrt(-3 / p)).coerceIn(-1.0, 1.0)
        val phi = acos(arg) / 3
        val roots = DoubleArray(3) { k -> r * cos(phi - 2 * PI * k / 3) + shift }
        roots.sortDescending()
        return CubicRoots(roots)
    }

    /** Eigenvalues of [m] (real ones, descending; the complex pair, if any, in re/im). */
    fun eigenvalues(m: Mat3): CubicRoots {
        val (tr, c1, d) = charPoly(m).let { Triple(it[0], it[1], it[2]) }
        val r = cubicRoots(-tr, c1, -d)
        fun newton(l0: Double): Double {
            var l = l0
            repeat(2) {
                val f = ((l - tr) * l + c1) * l - d
                val df = (3 * l - 2 * tr) * l + c1
                if (abs(df) > 1e-300) l -= f / df
            }
            return l
        }
        if (r.hasComplexPair) return CubicRoots(doubleArrayOf(newton(r.real[0])), r.re, r.im)
        // polish well-separated roots with Newton on the characteristic polynomial
        val out = r.real.copyOf()
        for (i in out.indices) {
            val sep = out.indices.filter { it != i }.minOf { abs(out[it] - out[i]) }
            if (sep < 1e-6 * max(1.0, abs(out[i]))) continue
            out[i] = newton(out[i])
        }
        out.sortDescending()
        return CubicRoots(out)
    }

    /**
     * Real eigenspaces of [m]. Real roots closer than [tol]·scale are merged (algebraic multiplicity);
     * the eigenspace is the numerical null space of (M − λI): singular values <= [tol]·scale.
     * A 1-D eigenspace uses the best cross product of two rows of M − λI (accurate to rounding).
     * The display uses tol ~ 0.02 (what the eye can tell apart); the tests 1e-7.
     */
    fun eigen(m: Mat3, tol: Double = 1e-7): List<EigenSpace> {
        val ev = eigenvalues(m)
        val scale = max(1.0, m.frob())
        val clusters = ArrayList<MutableList<Double>>()
        for (l in ev.real) {
            val last = clusters.lastOrNull()
            if (last != null && abs(last.last() - l) <= tol * scale) last += l else clusters += mutableListOf(l)
        }
        return clusters.map { cl ->
            val lam = cl.average()
            val b = m - Mat3.identity() * lam
            val svd = svd(b)
            val sv = doubleArrayOf(svd.s.x, svd.s.y, abs(svd.s.z))
            val nullity = sv.count { it <= tol * scale }.coerceIn(1, cl.size)
            val basis: List<Vec3> = if (nullity == 1) {
                val rows = listOf(b.row(0), b.row(1), b.row(2))
                val cands = listOf(rows[0] cross rows[1], rows[0] cross rows[2], rows[1] cross rows[2])
                val best = cands.maxByOrNull { it.norm }!!
                listOf(if (best.norm > 1e-9 * scale * scale) best.unit() else svd.v.col(2))
            } else (3 - nullity until 3).map { svd.v.col(it) }
            EigenSpace(lam, cl.size, basis)
        }
    }

    // ---------------------------------------------------------------- SVD (drives the node transforms)

    /** Symmetric 3x3 eigen-decomposition by cyclic Jacobi rotations: (values descending, vectors as columns). */
    fun symEigen(s: Mat3): Pair<DoubleArray, Mat3> {
        val a = Array(3) { r -> DoubleArray(3) { c -> 0.5 * (s[r, c] + s[c, r]) } }
        val v = Array(3) { r -> DoubleArray(3) { c -> if (r == c) 1.0 else 0.0 } }
        for (sweep in 0 until 60) {
            val off = a[0][1] * a[0][1] + a[0][2] * a[0][2] + a[1][2] * a[1][2]
            val diag = a[0][0] * a[0][0] + a[1][1] * a[1][1] + a[2][2] * a[2][2]
            if (off <= 1e-34 * max(diag, 1e-300) || off < 1e-300) break
            for ((p, q) in listOf(0 to 1, 0 to 2, 1 to 2)) {
                if (abs(a[p][q]) < 1e-300) continue
                val theta = (a[q][q] - a[p][p]) / (2 * a[p][q])
                val t = (if (theta >= 0) 1.0 else -1.0) / (abs(theta) + sqrt(theta * theta + 1))
                val c = 1 / sqrt(t * t + 1); val sn = t * c
                for (k in 0 until 3) {           // A <- A J  (columns p, q)
                    val akp = a[k][p]; val akq = a[k][q]
                    a[k][p] = c * akp - sn * akq; a[k][q] = sn * akp + c * akq
                }
                for (k in 0 until 3) {           // A <- Jᵀ A (rows p, q)
                    val apk = a[p][k]; val aqk = a[q][k]
                    a[p][k] = c * apk - sn * aqk; a[q][k] = sn * apk + c * aqk
                }
                for (k in 0 until 3) {           // V <- V J
                    val vkp = v[k][p]; val vkq = v[k][q]
                    v[k][p] = c * vkp - sn * vkq; v[k][q] = sn * vkp + c * vkq
                }
            }
        }
        val order = (0 until 3).sortedByDescending { a[it][it] }
        val vals = DoubleArray(3) { a[order[it]][order[it]] }
        val cols = Array(3) { j -> Vec3(v[0][order[j]], v[1][order[j]], v[2][order[j]]) }
        return vals to Mat3.fromCols(cols[0], cols[1], cols[2])
    }

    /** Any unit vector perpendicular to [a]. */
    fun perp(a: Vec3): Vec3 {
        val t = if (abs(a.x) < 0.6) Vec3.X else if (abs(a.y) < 0.6) Vec3.Y else Vec3.Z
        return (a cross t).unit()
    }

    /**
     * Singular value decomposition M = U diag(s) Vᵀ, with U and V PROPER rotations so that each can be a
     * node rotation (quaternion); the sign of det M rides on s.z. Works for singular M (rank 0..2).
     */
    fun svd(m: Mat3): Svd {
        val (ev, v0) = symEigen(m.transpose() * m)
        var v = v0
        if (v.det() < 0) v = v.withCol(2, -v.col(2))
        val s0 = sqrt(max(0.0, ev[0]))
        val eps = 1e-12 * max(1.0, s0)
        val m0 = m * v.col(0); val m1 = m * v.col(1)
        val u0 = if (m0.norm > eps) m0.unit() else Vec3.X
        val g1 = m1 - u0 * (u0 dot m1)
        val u1 = if (g1.norm > eps) g1.unit() else perp(u0)
        val u2 = u0 cross u1
        val u = Mat3.fromCols(u0, u1, u2)
        val s = Vec3(u0 dot m0, u1 dot m1, u2 dot (m * v.col(2)))
        return Svd(u, s, v)
    }

    // ---------------------------------------------------------------- rotations

    /** Quaternion (x, y, z, w) of a proper rotation matrix (Shepperd's method, branch on the largest term). */
    fun quat(r: Mat3): DoubleArray {
        val t = r.trace()
        val q = when {
            t > 0 -> { val s = sqrt(t + 1) * 2; doubleArrayOf((r[2, 1] - r[1, 2]) / s, (r[0, 2] - r[2, 0]) / s, (r[1, 0] - r[0, 1]) / s, 0.25 * s) }
            r[0, 0] > r[1, 1] && r[0, 0] > r[2, 2] -> { val s = sqrt(1 + r[0, 0] - r[1, 1] - r[2, 2]) * 2
                doubleArrayOf(0.25 * s, (r[0, 1] + r[1, 0]) / s, (r[0, 2] + r[2, 0]) / s, (r[2, 1] - r[1, 2]) / s) }
            r[1, 1] > r[2, 2] -> { val s = sqrt(1 + r[1, 1] - r[0, 0] - r[2, 2]) * 2
                doubleArrayOf((r[0, 1] + r[1, 0]) / s, 0.25 * s, (r[1, 2] + r[2, 1]) / s, (r[0, 2] - r[2, 0]) / s) }
            else -> { val s = sqrt(1 + r[2, 2] - r[0, 0] - r[1, 1]) * 2
                doubleArrayOf((r[0, 2] + r[2, 0]) / s, (r[1, 2] + r[2, 1]) / s, 0.25 * s, (r[1, 0] - r[0, 1]) / s) }
        }
        val l = sqrt(q.sumOf { it * it })
        return DoubleArray(4) { q[it] / l }
    }

    /** True if [m] is a proper rotation (MᵀM = I, det = +1) within [tol]. */
    fun isRotation(m: Mat3, tol: Double = 1e-9): Boolean =
        (m.transpose() * m).isClose(Mat3.identity(), tol) && abs(m.det() - 1) < tol

    /** (axis, angle in [0, π]) of a proper rotation, or null if [m] is not one. */
    fun axisAngle(m: Mat3, tol: Double = 1e-9): Pair<Vec3, Double>? {
        if (!isRotation(m, tol)) return null
        val c = ((m.trace() - 1) / 2).coerceIn(-1.0, 1.0)
        val ang = acos(c)
        if (ang < 1e-12) return Vec3.Z to 0.0
        if (ang > PI - 1e-6) {
            val b = m + Mat3.identity()
            val axis = (0 until 3).map { b.col(it) }.maxByOrNull { it.norm }!!.unit()
            return axis to ang
        }
        val axis = Vec3(m[2, 1] - m[1, 2], m[0, 2] - m[2, 0], m[1, 0] - m[0, 1]) * (1 / (2 * sin(ang)))
        return axis.unit() to ang
    }

    // ---------------------------------------------------------------- animation path

    /**
     * The in-between matrix at [t] in [0, 1] when space moves from [from] to [to]. If the step
     * to·from⁻¹ is a pure rotation, space turns rigidly about its axis (a rotation looks like one);
     * otherwise every point slides on a straight line: (1−t)·from + t·to. From the identity that
     * linear path keeps the target's eigenvectors on their spans for the WHOLE move
     * ((1−t)I + tM)v = (1 − t + tλ) v — which is why the glowing lines stay put while the rest turns.
     */
    fun path(from: Mat3, to: Mat3, t: Double): Mat3 {
        if (t <= 0) return from
        if (t >= 1) return to
        from.inverse()?.let { inv ->
            val aa = axisAngle(to * inv, 1e-9)
            if (aa != null && aa.second > 1e-9) return Mat3.rotation(aa.first, aa.second * t) * from
        }
        return from * (1 - t) + to * t
    }
}

/** The experiment's preset matrices (columns = where î, ĵ, k̂ land). */
object LinearPresets {
    val IDENTITY = Mat3.identity()
    /** 90° about the vertical (z) axis: î -> ĵ, ĵ -> −î. */
    val ROTATE = Mat3.rotation(Vec3.Z, PI / 2).let { r -> Mat3(DoubleArray(9) { k -> Math.rint(r.m[k] * 1e12) / 1e12 }) }
    /** Shear: k̂ -> k̂ + ĵ (the top slides, base and height stay). det = 1. */
    val SHEAR = Mat3.fromCols(Vec3.X, Vec3.Y, Vec3(0.0, 1.0, 1.0))
    /** Non-uniform scale: x ×1.5, y ×0.6, z ×1.3. det = 1.17. */
    val SCALE = Mat3.diag(1.5, 0.6, 1.3)
    /** Orthogonal projection onto the floor (the xy-plane): k̂ -> 0. det = 0. */
    val PROJECT = Mat3.diag(1.0, 1.0, 0.0)
    /** Mirror in the plane x = y (swaps î and ĵ): det = −1. */
    val REFLECT = Mat3.fromCols(Vec3.Y, Vec3.X, Vec3.Z)
    /** The composition pair: A = shear, B = rotate. "A then B" = B·A ≠ A·B. */
    val COMPOSE_A = SHEAR
    val COMPOSE_B = ROTATE
    /** The opening state: a general map with three real, non-orthogonal eigen-directions (det 1.15). */
    val WELCOME = Mat3(doubleArrayOf(1.25, 0.40, 0.20, 0.0, 0.80, 0.35, 0.0, 0.0, 1.15))

    /**
     * A "random" matrix, reproducible from [seed]: entries near the identity, re-drawn until it stays
     * readable (|det| in [0.35, 1.8], every column 0.45..1.55 long).
     */
    fun random(seed: Int): Mat3 {
        val rnd = java.util.Random(7919L * seed + 17)
        while (true) {
            val m = Mat3(DoubleArray(9) { k -> if (k % 4 == 0) 0.55 + 0.85 * rnd.nextDouble() else -0.65 + 1.3 * rnd.nextDouble() })
            if (rnd.nextDouble() < 0.25) {     // sometimes orientation-reversing
                val f = m.withCol(0, -m.col(0)); if (ok(f)) return f
            }
            if (ok(m)) return m
        }
    }

    private fun ok(m: Mat3) = abs(m.det()) in 0.35..1.8 && (0 until 3).all { m.col(it).norm in 0.45..1.55 }
}
