package com.bytedance.mathlab.core.conics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Conic sections as slices of a double cone, checked against closed forms:
 *   e = cos(beta) / cos(alpha) (beta = plane <-> axis = 90 deg - tilt) — Dandelin's theorem; the
 *   analytic conic from substituting the plane into the cone; the Dandelin spheres' tangency, foci,
 *   directrices; and the focal-distance laws PF1 + PF2 = 2a (ellipse), |PF1 - PF2| = 2a (hyperbola),
 *   PF = distance to the directrix (parabola). Reference: Hilbert & Cohn-Vossen, "Geometry and the
 *   Imagination", ch. I sec. 2.
 */
class ConicsTest {
    private val deg = PI / 180
    private val alphas = listOf(20.0, 30.0, 45.0).map { it * deg }

    private fun tilts(alpha: Double) = (0..180).map { it * 0.5 * deg } + listOf(Conics.parabolaTilt(alpha))

    @Test
    fun typeAndEccentricityAcrossTheFullRange() {
        var n = 0
        for (al in alphas) for (th in tilts(al)) {
            val beta = PI / 2 - th
            val e = Conics.eccentricity(th, al)
            assertEquals(cos(beta) / cos(al), e, 1e-14)                       // the stated convention
            val want = when {
                th == 0.0 -> ConicType.CIRCLE
                abs(th - Conics.parabolaTilt(al)) < 1e-12 -> ConicType.PARABOLA
                th < Conics.parabolaTilt(al) -> ConicType.ELLIPSE
                else -> ConicType.HYPERBOLA
            }
            assertEquals("alpha ${al / deg} theta ${th / deg}", want, Conics.classify(th, al))
            val s = Section(al, th, 0.1)
            if (s.type == ConicType.ELLIPSE || s.type == ConicType.HYPERBOLA) {
                // e from the slice's own axes: c / a
                val c = abs(s.fociS[0] - s.fociS[1]) / 2
                assertEquals(e, c / s.a, 1e-9)
                assertEquals(e, sqrt(1 - s.qA), 1e-12)
            }
            n++
        }
        // the exact parabola boundary and its neighbours
        for (al in alphas) {
            val tp = Conics.parabolaTilt(al)
            assertEquals(1.0, Conics.eccentricity(tp, al), 1e-15)
            assertEquals(ConicType.PARABOLA, Conics.classify(tp, al))
            assertEquals(ConicType.ELLIPSE, Conics.classify(tp - 1e-6, al))
            assertEquals(ConicType.HYPERBOLA, Conics.classify(tp + 1e-6, al))
            assertEquals(ConicType.CIRCLE, Conics.classify(0.0, al))
            // the vertical plane: e = 1 / cos(alpha)
            assertEquals(1 / cos(al), Conics.eccentricity(PI / 2, al), 1e-15)
            // tiltFor inverts eccentricity
            assertEquals(0.5, Conics.eccentricity(Conics.tiltFor(0.5, al), al), 1e-14)
        }
        val a30 = 30 * deg
        println("CONICS alpha 30: parabola tilt = %.6f deg, e(theta = 25.659 deg) = %.6f, e(90) = %.6f, tilt for e=0.5 = %.4f deg"
            .format(Conics.parabolaTilt(a30) / deg, Conics.eccentricity(25.659 * deg, a30), Conics.eccentricity(PI / 2, a30), Conics.tiltFor(0.5, a30) / deg))
        println("CONICS classified $n (alpha, tilt) pairs")
    }

    @Test
    fun intersectionPointsLieOnConeAndPlane() {
        var worstCone = 0.0; var worstPlane = 0.0; var worstConic = 0.0; var n = 0
        for (al in alphas) for (th in tilts(al)) for (d in listOf(0.03, 0.1, 0.2)) {
            val s = Section(al, th, d)
            for (b in s.branches(-0.6, 0.8, 360)) for ((k, p) in b.pts.withIndex()) {
                // cone residual relative to the squared size, plane residual in metres
                worstCone = maxOf(worstCone, abs(s.coneResidual(p)) / maxOf(1e-6, p.y * p.y))
                worstPlane = maxOf(worstPlane, abs(s.planeResidual(p)))
                assertTrue(p.y >= -0.6 - 1e-9 && p.y <= 0.8 + 1e-9)
                // and on the ANALYTIC conic in the plane's own frame
                val (u, w) = s.toPlane(p)
                val f = if (s.type == ConicType.PARABOLA) w * w + d * d * s.st * s.st * (1 - s.T * s.T * s.T * s.T) - 2 * s.qB * u
                        else s.qA * (u - s.s0) * (u - s.s0) + w * w - d * d * s.T * s.T / s.qA
                worstConic = maxOf(worstConic, abs(f) / maxOf(1e-6, u * u + w * w))
                n++
                if (k > 2000) break
            }
            // the analytic parametrisation maps onto the cone too (validates s0, a, b)
            if (s.type == ConicType.ELLIPSE || s.type == ConicType.CIRCLE) for (j in 0 until 24) {
                val t = 2 * PI * j / 24
                val p = s.fromPlane(s.s0 + s.a * cos(t), s.b * sin(t))
                assertEquals(0.0, s.coneResidual(p), 1e-12)
            }
            if (s.type == ConicType.HYPERBOLA) for (j in -6..6) for (sg in listOf(-1.0, 1.0)) {
                val t = j * 0.3
                val p = s.fromPlane(s.s0 + sg * s.a * cosh(t), s.b * sinh(t))
                assertEquals(0.0, s.coneResidual(p) / maxOf(1e-6, p.y * p.y), 1e-9)
            }
        }
        println("CONICS $n intersection points: worst |x²+z²−T²y²|/y² = %.2e, worst plane residual = %.2e m, worst analytic-conic residual = %.2e".format(worstCone, worstPlane, worstConic))
        assertTrue(worstCone < 1e-9); assertTrue(worstPlane < 1e-9); assertTrue(worstConic < 1e-6)
    }

    @Test
    fun dandelinSpheresTouchConeAndPlaneAtTheFoci() {
        var worst = 0.0; var n = 0
        for (al in alphas) for (th in tilts(al)) for (d in listOf(0.05, 0.13)) {
            val s = Section(al, th, d)
            if (s.type == ConicType.PARABOLA) assertNull(s.far) else assertNotNull(s.far)
            for ((i, sp) in s.spheres.withIndex()) {
                val c = V3d(0.0, sp.c, 0.0)
                // tangent to the plane: centre-plane distance = r, and the focus is the foot of the perpendicular
                worst = maxOf(worst, abs(abs(s.planeResidual(c)) - sp.r))
                worst = maxOf(worst, abs(s.planeResidual(sp.focus)), abs(sp.focus.dist(c) - sp.r))
                // tangent to the cone: distance from the centre to every generator line = r
                for (j in 0 until 12) {
                    val g = s.generator(2 * PI * j / 12)
                    val proj = g * (c dot g)
                    worst = maxOf(worst, abs(c.dist(proj) - sp.r))
                }
                // the contact circle lies on the cone and on the sphere
                for (j in 0 until 12) {
                    val ph = 2 * PI * j / 12
                    val q = V3d(sp.circleR * cos(ph), sp.circleY, sp.circleR * sin(ph))
                    worst = maxOf(worst, abs(s.coneResidual(q)), abs(q.dist(c) - sp.r))
                }
                // contact point == the analytic focus of the section in the plane's 2-D frame
                val (fu, fw) = s.toPlane(sp.focus)
                worst = maxOf(worst, abs(fu - s.fociS[i]), abs(fw))
                // the contact circle's plane cuts the section's plane in the matching directrix
                if (s.type != ConicType.CIRCLE && s.st > 1e-9) {
                    val sd = (sp.circleY - d * s.ct) / s.st
                    worst = maxOf(worst, abs(sd - s.directrixS[i]))
                }
                n++
            }
            // ellipse: both spheres in the upper nappe on either side of the plane; hyperbola: one per nappe
            when (s.type) {
                ConicType.ELLIPSE, ConicType.CIRCLE -> assertTrue(s.near.c > 0 && s.far!!.c > s.near.c)
                ConicType.HYPERBOLA -> assertTrue(s.near.c > 0 && s.far!!.c < 0)
                ConicType.PARABOLA -> {}
            }
        }
        val s = Section(30 * deg, Conics.tiltFor(0.6, 30 * deg), 0.11)
        println("CONICS Dandelin: $n spheres, worst tangency/focus/directrix error = %.2e m".format(worst))
        println("CONICS example alpha 30, e 0.6, d 0.11: near c=%.4f r=%.4f, far c=%.4f r=%.4f, foci s=%s, analytic foci s=%s, 2a=%.4f"
            .format(s.near.c, s.near.r, s.far!!.c, s.far!!.r, s.spheres.map { "%.5f".format(s.toPlane(it.focus).first) }, s.fociS.map { "%.5f".format(it) }, s.twoA))
        assertTrue(worst < 1e-9)
    }

    @Test
    fun focalDistancesSumAndDifferenceAreConstant() {
        var worstSum = 0.0; var worstDiff = 0.0; var worstTan = 0.0; var worstPar = 0.0; var n = 0
        for (al in alphas) for (th in tilts(al)) {
            if (th == 0.0) continue
            val s = Section(al, th, 0.1)
            for (b in s.branches(-1.0, 1.0, 360)) for (p in b.pts) {
                val f = s.spheres.map { p.dist(it.focus) }
                // tangent-length equality: PF_i = PQ_i (Q_i on contact circle i, same generator)
                s.spheres.forEachIndexed { i, sp -> worstTan = maxOf(worstTan, abs(f[i] - s.tangentLength(p, sp))) }
                when (s.type) {
                    ConicType.ELLIPSE -> worstSum = maxOf(worstSum, abs(f[0] + f[1] - s.twoA))
                    ConicType.HYPERBOLA -> worstDiff = maxOf(worstDiff, abs(abs(f[0] - f[1]) - s.twoA))
                    ConicType.PARABOLA -> worstPar = maxOf(worstPar, abs(f[0] - abs(s.toPlane(p).first - s.directrixS[0])))
                    else -> {}
                }
                n++
            }
            // 2a is also the generator segment between the contact circles (Q1Q2)
            val far = s.far
            if (far != null) assertEquals(s.twoA, abs(abs(far.c) + (if (far.c > 0) -1 else 1) * s.near.c) * s.ca, 1e-12)
        }
        // circle: both spheres touch at the centre, PF = radius = d tan(alpha), 2a = diameter
        val c = Section(30 * deg, 0.0, 0.1)
        for (sp in c.spheres) assertEquals(0.0, sp.focus.dist(V3d(0.0, 0.1, 0.0)), 1e-12)
        assertEquals(2 * 0.1 * tan(30 * deg), c.twoA, 1e-12)
        println("CONICS $n points: worst |PF1+PF2−2a| = %.2e, worst ||PF1−PF2|−2a| = %.2e, worst |PF−PQ| = %.2e, worst |PF−Pd| (parabola) = %.2e"
            .format(worstSum, worstDiff, worstTan, worstPar))
        assertTrue(worstSum < 1e-9 && worstDiff < 1e-9 && worstTan < 1e-9 && worstPar < 1e-9)
    }

    @Test
    fun clippedBranchesHaveTheRightShape() {
        val al = 30 * deg
        val ell = Section(al, Conics.tiltFor(0.5, al), 0.1).branches(-0.5, 0.5)
        assertEquals(1, ell.size); assertTrue(ell[0].closed)
        val hyp = Section(al, 75 * deg, 0.1).branches(-0.4, 0.4)
        assertEquals(2, hyp.size)
        assertTrue(hyp.any { b -> b.pts.all { it.y > 0 } } && hyp.any { b -> b.pts.all { it.y < 0 } })
        // clip ends sit on the clip planes
        for (b in hyp) { assertEquals(0.4, abs(b.pts.first().y), 1e-9); assertEquals(0.4, abs(b.pts.last().y), 1e-9) }
        val par = Section(al, Conics.parabolaTilt(al), 0.1, eps = 1e-9).branches(-0.4, 0.4)
        assertEquals(1, par.size); assertEquals(0.4, par[0].pts.first().y, 1e-9)
        println("CONICS branches: ellipse 1 closed (%.3f m), hyperbola %d open (%.3f, %.3f m), parabola 1 open (%.3f m)"
            .format(ell[0].length, hyp.size, hyp[0].length, hyp[1].length, par[0].length))
    }
}
