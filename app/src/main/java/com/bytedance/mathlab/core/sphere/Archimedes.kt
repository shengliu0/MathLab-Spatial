// Archimedes' hat-box theorem and the sphere's area (On the Sphere and Cylinder, ~225 BC).
//
//   * A sphere of radius r sits in the circumscribing cylinder (radius r, height 2r). Cut both by two
//     horizontal planes y0 < y1. The sphere's band and the cylinder's band have the SAME area, 2πr(y1 - y0),
//     wherever the band is and however thick: projecting straight out from the axis stretches the band
//     around by 1/cosθ (radius r·cosθ -> r) and squashes it along the meridian by cosθ (slant h/cosθ -> h).
//   * Hence area(sphere) = area(cylinder side) = 2πr · 2r = 4πr² = 4 × πr² (four "shadows").
//   * Volume (the tombstone): V(sphere) = 4/3 πr³ = 2/3 of the cylinder's 2πr³.
//
// Pure Kotlin, double precision. Distances in any unit (the demo uses metres), angles in radians.
// Latitude θ is measured from the equator (+ north): y = r sinθ, band radius ρ = r cosθ.

package com.bytedance.mathlab.core.sphere

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Archimedes {

    // ---------------------------------------------------------------- closed forms
    /** Exact area of the sphere band between heights [y0] < [y1] (|y| <= r): 2πr(y1 - y0). */
    fun sphereBandArea(r: Double, y0: Double, y1: Double): Double = 2 * PI * r * (clampY(r, y1) - clampY(r, y0))

    /** Area of the cylinder band between the same heights (radius r): 2πr(y1 - y0). */
    fun cylinderBandArea(r: Double, y0: Double, y1: Double): Double = 2 * PI * r * (y1 - y0)

    fun sphereArea(r: Double) = 4 * PI * r * r
    fun shadowArea(r: Double) = PI * r * r
    fun cylinderSideArea(r: Double) = 2 * PI * r * (2 * r)
    /** The unrolled cylinder side: a rectangle 2πr wide and 2r tall. */
    fun unrolledSize(r: Double) = Pair(2 * PI * r, 2 * r)
    fun sphereVolume(r: Double) = 4.0 / 3.0 * PI * r * r * r
    fun cylinderVolume(r: Double) = PI * r * r * (2 * r)
    /** Height the sphere's volume reaches when poured into the cylinder: 4r/3 = 2/3 of 2r. */
    fun pouredHeight(r: Double) = sphereVolume(r) / (PI * r * r)

    fun latitudeOf(r: Double, y: Double) = asin((y / r).coerceIn(-1.0, 1.0))
    fun heightOf(r: Double, lat: Double) = r * sin(lat)
    /** Along the meridian a thin band at latitude θ is h/cosθ wide (slant factor 1/cosθ). */
    fun slantFactor(lat: Double) = 1 / cos(lat)
    /** Its radius is r·cosθ, i.e. cosθ of the cylinder's. */
    fun radiusFactor(lat: Double) = cos(lat)

    private fun clampY(r: Double, y: Double) = y.coerceIn(-r, r)

    // ---------------------------------------------------------------- numerical checks (independent of the closed form)
    /**
     * Band area as an inscribed polyhedral surface of revolution: the meridian arc between [y0] and [y1] is cut
     * into [n] equal-angle chords, each chord sweeps a conical frustum of lateral area π(ρa + ρb)·chord.
     * Converges to the exact area from below as O(1/n²). What the demo calls the "measured" area.
     */
    fun sphereBandAreaFrustums(r: Double, y0: Double, y1: Double, n: Int): Double {
        val a0 = latitudeOf(r, y0); val a1 = latitudeOf(r, y1)
        var s = 0.0
        for (k in 0 until n) {
            val p = a0 + (a1 - a0) * k / n; val q = a0 + (a1 - a0) * (k + 1) / n
            val ra = r * cos(p); val rb = r * cos(q); val ya = r * sin(p); val yb = r * sin(q)
            val chord = sqrt((rb - ra) * (rb - ra) + (yb - ya) * (yb - ya))
            s += PI * (ra + rb) * chord
        }
        return s
    }

    /**
     * Band area as the surface-of-revolution integral A = ∫ 2π ρ(θ) · r dθ over latitude (ρ = r cosθ is the
     * circumference radius, r dθ the slant width), by composite Simpson with [n] (even) panels.
     */
    fun sphereBandAreaIntegral(r: Double, y0: Double, y1: Double, n: Int = 200): Double {
        val a0 = latitudeOf(r, y0); val a1 = latitudeOf(r, y1)
        val m = if (n % 2 == 0) n else n + 1
        val hh = (a1 - a0) / m
        fun f(t: Double) = 2 * PI * (r * cos(t)) * r
        var s = f(a0) + f(a1)
        for (k in 1 until m) s += f(a0 + k * hh) * (if (k % 2 == 1) 4 else 2)
        return s * hh / 3
    }

    /** The slant (meridian arc) width of the band [y0, y1] on the sphere: r(θ1 - θ0). */
    fun slantWidth(r: Double, y0: Double, y1: Double) = r * (latitudeOf(r, y1) - latitudeOf(r, y0))

    /** Sum of [n] equal-height bands from pole to pole, each measured with [sub] frustums. */
    fun sphereAreaFromBands(r: Double, n: Int, sub: Int): Double {
        var s = 0.0
        for (k in 0 until n) s += sphereBandAreaFrustums(r, -r + 2 * r * k / n, -r + 2 * r * (k + 1) / n, sub)
        return s
    }

    /** Volume of the sphere by stacking [n] discs (midpoint rule): Σ π(r² - y²)Δy. */
    fun sphereVolumeSlices(r: Double, n: Int): Double {
        val dy = 2 * r / n
        var v = 0.0
        for (k in 0 until n) { val y = -r + (k + 0.5) * dy; v += PI * (r * r - y * y) * dy }
        return v
    }

    // ---------------------------------------------------------------- the unrolling (isometric bending)
    /**
     * A point of the cylinder side bent to curvature [kappa] (1 = the cylinder of radius r, 0 = flat): arc
     * position [s] ∈ [-πr, πr] from the FRONT generator (which stays fixed at x = 0, z = r), height [y].
     * The sheet keeps every length (isometric), so the rectangle has exactly the cylinder's area.
     * Returns (x, y, z) in the cylinder's frame.
     */
    fun bend(r: Double, s: Double, y: Double, kappa: Double): Triple<Double, Double, Double> {
        if (kappa < 1e-9) return Triple(s, y, r)
        val big = r / kappa
        val a = s / big
        return Triple(big * sin(a), y, r - big + big * cos(a))
    }

    // ---------------------------------------------------------------- strip -> disc (area-preserving)
    /**
     * Cut the 2πr × 2r rectangle into 4 strips (πr/2 × 2r, area πr² each). A strip point (u, t) ∈ [0,1]² (u across,
     * t down from the top edge) goes to the disc point at radius r√t, angle 2π(u - ½) from straight down:
     * the top edge shrinks to the centre, every row becomes a ring. Jacobian = π r² = strip area per unit (u, t):
     * the map is EXACTLY area-preserving, so equal-area stripes become equal-area rings.
     * Returns (x, y) relative to the disc centre.
     */
    fun stripToDisc(r: Double, u: Double, t: Double): Pair<Double, Double> {
        val rho = r * sqrt(t.coerceIn(0.0, 1.0)); val a = 2 * PI * (u - 0.5)
        return Pair(rho * sin(a), -rho * cos(a))
    }

    /** The strip's own point (x, y) relative to its centre, width πr/2, height 2r. */
    fun stripPoint(r: Double, u: Double, t: Double): Pair<Double, Double> = Pair((u - 0.5) * PI * r / 2, (0.5 - t) * 2 * r)

    /** Area of a polygon (shoelace), xy pairs in order. */
    fun polygonArea(xy: DoubleArray): Double {
        var s = 0.0; val n = xy.size / 2
        for (i in 0 until n) { val j = (i + 1) % n; s += xy[i * 2] * xy[j * 2 + 1] - xy[j * 2] * xy[i * 2 + 1] }
        return kotlin.math.abs(s) / 2
    }
}
