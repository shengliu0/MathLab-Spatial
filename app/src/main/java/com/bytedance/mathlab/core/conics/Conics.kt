// Conic sections as plane slices of a double cone, with the Dandelin spheres (Apollonius ~200 BC,
// Dandelin 1822). Pure Kotlin, double precision, no Android.
//
// CANONICAL FRAME (metres): apex at the origin, cone axis +y, half-angle alpha (axis <-> generator).
//   cone:   x^2 + z^2 = (y tan alpha)^2                       (both nappes)
//   plane:  n . p = d,  n = (-sin theta, cos theta, 0),  d > 0 (the apex is on the -n side)
//           theta = TILT of the plane from horizontal (0 = horizontal, 90 deg = parallel to the axis);
//           the textbook angle beta between the plane and the AXIS is beta = 90 deg - theta.
//   e = cos beta / cos alpha = sin theta / cos alpha           (Dandelin; e.g. Hilbert & Cohn-Vossen,
//       "Geometry and the Imagination", ch. I sec. 2, the Dandelin spheres)
//   circle theta = 0, ellipse theta < 90 - alpha, parabola theta = 90 - alpha (plane || a generator),
//   hyperbola theta > 90 - alpha (the plane cuts both nappes).
//
// The plane's own 2-D frame: p = d n + s u + w z^, u = (cos theta, sin theta, 0) ("uphill"), z^ = (0,0,1).
// Substituting into the cone gives  A s^2 - 2 B s + w^2 + d^2 (sin^2 theta - T^2 cos^2 theta) = 0  with
// T = tan alpha, A = cos^2 theta - T^2 sin^2 theta, B = d sin theta cos theta / cos^2 alpha, and after
// completing the square (algebra in ConicsTest):  A (s - s0)^2 + w^2 = d^2 T^2 / A,  s0 = qB / qA, so
//   ellipse  (A > 0): a = d T / A,    b = d T / sqrt(A),    c = sqrt(a^2 - b^2),  foci s0 +- c
//   hyperbola(A < 0): a = d T / |A|,  b = d T / sqrt(|A|),  c = sqrt(a^2 + b^2),  foci s0 +- c
//   parabola (A = 0): s = (w^2 + d^2 sin^2 theta (1 - T^4)) / (2 B),  focal parameter p = B / 2.
// and e = c / a = sqrt(1 - A) for both, which is sin theta / cos alpha again.
//
// DANDELIN SPHERES: a sphere centred on the axis at (0, c, 0) touches the cone iff r = |c| sin alpha and
// the plane iff |c cos theta - d| = r, so c = d / (cos theta + sin alpha) (always; between apex and plane)
// and c = d / (cos theta - sin alpha) (ellipse: above the plane, same nappe; hyperbola: c < 0, the other
// nappe; parabola: none). Each touches the plane at a FOCUS (s_F = c sin theta) and the cone along a circle
// at height c cos^2 alpha. Tangent segments from one point to one sphere are equal, so for a point P on
// the section PF_i = PQ_i (Q_i = where the generator through P meets contact circle i) and
//   ellipse   PF1 + PF2 = Q1Q2 = (c2 - c1) cos alpha = 2a        (the same for every P)
//   hyperbola |PF1 - PF2| = Q1Q2 = (|c1| + |c2|) cos alpha = 2a
// The plane of a contact circle cuts the section's plane in the matching DIRECTRIX.

package com.bytedance.mathlab.core.conics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class V3d(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: V3d) = V3d(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3d) = V3d(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = V3d(x * k, y * k, z * k)
    infix fun dot(o: V3d) = x * o.x + y * o.y + z * o.z
    val length get() = sqrt(x * x + y * y + z * z)
    fun dist(o: V3d) = (this - o).length
}

enum class ConicType { CIRCLE, ELLIPSE, PARABOLA, HYPERBOLA }

object Conics {
    /** e = cos beta / cos alpha with beta = 90 deg - theta (plane <-> axis), i.e. sin theta / cos alpha. */
    fun eccentricity(theta: Double, alpha: Double): Double = sin(theta) / cos(alpha)

    /** Tilt at which the plane is parallel to a generator (the parabola): 90 deg - alpha. */
    fun parabolaTilt(alpha: Double): Double = PI / 2 - alpha

    /** Tilt that gives eccentricity [e] (0 <= e <= 1 / cos alpha). */
    fun tiltFor(e: Double, alpha: Double): Double = kotlin.math.asin((e * cos(alpha)).coerceIn(0.0, 1.0))

    fun classify(theta: Double, alpha: Double, eps: Double = 1e-9): ConicType {
        val e = eccentricity(theta, alpha)
        return when {
            abs(theta) <= eps -> ConicType.CIRCLE
            abs(e - 1.0) <= eps -> ConicType.PARABOLA
            e < 1.0 -> ConicType.ELLIPSE
            else -> ConicType.HYPERBOLA
        }
    }
}

/**
 * One slice: cone half-angle [alpha], plane tilt [theta] in [0, pi/2], plane distance from the apex [d] > 0.
 * [eps] decides when the slice counts as a circle / parabola (the demo snaps to those exactly).
 */
class Section(val alpha: Double, val theta: Double, val d: Double, eps: Double = 1e-9) {
    val T = tan(alpha)
    val sa = sin(alpha); val ca = cos(alpha)
    val st = sin(theta); val ct = cos(theta)
    val n = V3d(-st, ct, 0.0)
    val u = V3d(ct, st, 0.0)
    val wAxis = V3d(0.0, 0.0, 1.0)
    val e = Conics.eccentricity(theta, alpha)
    val type = Conics.classify(theta, alpha, eps)

    // ---------------------------------------------------------------- analytic conic in the plane (s, w)
    val qA = ct * ct - T * T * st * st
    val qB = d * st * ct / (ca * ca)
    /** Centre (s) of an ellipse / hyperbola; for the parabola, its vertex. */
    val s0: Double
    /** Semi-major (transverse) axis; for the parabola, the focal parameter p (vertex -> focus). */
    val a: Double
    /** Semi-minor (conjugate) axis; 0 for the parabola. */
    val b: Double
    /** Focus positions s (w = 0), ordered as the Dandelin spheres: [near sphere, far sphere]. */
    val fociS: List<Double>
    /** Directrix positions s (lines s = const in the plane), in the same order as [fociS]. */
    val directrixS: List<Double>

    init {
        when (type) {
            ConicType.PARABOLA -> {
                val p = qB / 2
                val sv = d * d * st * st * (1 - T * T * T * T) / (2 * qB)
                s0 = sv; a = p; b = 0.0
                fociS = listOf(sv + p); directrixS = listOf(sv - p)
            }
            else -> {
                s0 = qB / qA
                a = d * T / abs(qA)
                b = d * T / sqrt(abs(qA))
                val c = if (qA > 0) sqrt(maxOf(0.0, a * a - b * b)) else sqrt(a * a + b * b)
                // near sphere's focus: the one nearer the apex side (smaller s for the ellipse,
                // the upper-nappe branch's focus for the hyperbola)
                fociS = if (qA > 0) listOf(s0 - c, s0 + c) else listOf(s0 + c, s0 - c)
                directrixS = if (e < 1e-12) emptyList() else if (qA > 0) listOf(s0 - a / e, s0 + a / e) else listOf(s0 + a / e, s0 - a / e)
            }
        }
    }

    /** 2a: the constant sum (ellipse) / difference (hyperbola) of focal distances; circle: the diameter. */
    val twoA get() = 2 * a

    fun fromPlane(s: Double, w: Double): V3d = n * d + u * s + wAxis * w
    fun toPlane(p: V3d): Pair<Double, Double> = (p dot u) to (p dot wAxis)

    /** Implicit equations, for the tests: zero on the cone / on the plane. */
    fun coneResidual(p: V3d) = p.x * p.x + p.z * p.z - T * T * p.y * p.y
    fun planeResidual(p: V3d) = (p dot n) - d

    // ---------------------------------------------------------------- cone parametrisation (radial from the apex)

    /** Unit generator direction at azimuth [phi] (upper nappe). */
    fun generator(phi: Double) = V3d(sa * cos(phi), ca, sa * sin(phi))
    /** Denominator of t(phi): > 0 upper nappe, < 0 lower nappe, 0 = direction parallel to the plane. */
    fun den(phi: Double) = ca * ct - sa * st * cos(phi)
    /** Signed distance along generator(phi) from the apex to the plane (Double.NaN when parallel). */
    fun tOf(phi: Double): Double { val dd = den(phi); return if (abs(dd) < 1e-12) Double.NaN else d / dd }
    fun pointAt(phi: Double): V3d { val t = tOf(phi); return generator(phi) * t }

    // ---------------------------------------------------------------- Dandelin spheres

    class Sphere(
        /** Centre (0, c, 0). */
        val c: Double,
        val r: Double,
        /** Where it touches the plane. */
        val focus: V3d,
        /** Contact circle with the cone: height and radius. */
        val circleY: Double,
        val circleR: Double,
    )

    /** [near] = between the apex and the plane (always); [far] = the other one (null for the parabola). */
    val near: Sphere = sphere(d / (ct + sa))
    val far: Sphere? = if (type == ConicType.PARABOLA) null else sphere(d / (ct - sa))
    val spheres: List<Sphere> get() = listOfNotNull(near, far)

    private fun sphere(c: Double): Sphere {
        val r = abs(c) * sa
        val foot = V3d(0.0, c, 0.0) - n * (c * ct - d)
        return Sphere(c, r, foot, c * ca * ca, abs(c) * sa * ca)
    }

    /**
     * Q: where the generator through [p] meets the contact circle of [sp]. Q = c cos(alpha) g, with g the
     * unit generator direction through p pointing into the UPPER nappe (c < 0 lands in the lower nappe).
     */
    fun contactOnGenerator(p: V3d, sp: Sphere): V3d {
        val l = p.length
        val g = if (p.y >= 0) p * (1 / l) else p * (-1 / l)
        return g * (sp.c * ca)
    }

    /** Tangent length from p to sphere sp along the cone: |PQ| (= |PF| for a point on the section). */
    fun tangentLength(p: V3d, sp: Sphere) = p.dist(contactOnGenerator(p, sp))

    // ---------------------------------------------------------------- clipped curve (for drawing)

    /** One connected piece of the section inside yMin <= y <= yMax: azimuths and points, in order. */
    class Branch(val phi: DoubleArray, val pts: List<V3d>, val closed: Boolean) {
        val length: Double by lazy {
            var l = 0.0; for (k in 1 until pts.size) l += pts[k].dist(pts[k - 1]); if (closed && pts.size > 1) l += pts.last().dist(pts[0]); l
        }
    }

    /**
     * The section clipped to the drawn cone (yMin <= y <= yMax), sampled at [n] azimuths with every clip
     * end found by bisection, so branch ends sit exactly on the clip planes.
     */
    fun branches(yMin: Double, yMax: Double, n: Int = 720): List<Branch> {
        fun ok(phi: Double): Boolean { val t = tOf(phi); if (t.isNaN()) return false; val y = t * ca; return y in yMin..yMax }
        fun edge(pIn: Double, pOut: Double): Double {
            var lo = pIn; var hi = pOut
            repeat(48) { val m = (lo + hi) / 2; if (ok(m)) lo = m else hi = m }
            return lo
        }
        val step = 2 * PI / n
        val valid = BooleanArray(n) { ok(it * step) }
        if (valid.all { it }) {
            val phis = DoubleArray(n) { it * step }
            return listOf(Branch(phis, phis.map { pointAt(it) }, closed = true))
        }
        if (valid.none { it }) return emptyList()
        // start at a run's beginning so no run wraps around index 0
        var start = 0
        while (!(valid[start] && !valid[(start - 1 + n) % n])) start++
        val out = ArrayList<Branch>()
        var k = 0
        while (k < n) {
            val i = (start + k) % n
            if (!valid[i]) { k++; continue }
            val list = ArrayList<Double>()
            // bisect between the last invalid sample (start + k - 1) and this first valid one
            list += edge((start + k) * step, (start + k - 1) * step)
            var j = k
            while (j < n && valid[(start + j) % n]) { list += (start + j) * step; j++ }
            list += edge((start + j - 1) * step, (start + j) * step)
            val phis = list.toDoubleArray()
            out += Branch(phis, phis.map { pointAt(it) }, closed = false)
            k = j
        }
        return out
    }

    /** Azimuth of the upper-nappe branch's lowest point (phi = pi): where a running point starts. */
    val startPhi get() = PI

    companion object {
        /** acos clamped, for angle readouts. */
        fun safeAcos(v: Double) = acos(v.coerceIn(-1.0, 1.0))
    }
}
