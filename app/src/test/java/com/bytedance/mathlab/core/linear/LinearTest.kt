package com.bytedance.mathlab.core.linear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The maths of the 线性变换与行列式 experiment, checked with real numbers.
 * References: G. Strang, Introduction to Linear Algebra (5th ed.) §5.1 (det = volume of the box),
 * §6.1-6.2 (eigenvalues, A = P D P⁻¹); 3Blue1Brown, Essence of Linear Algebra ch. 4 (composition order),
 * ch. 6 (det = volume scale, sign = orientation), ch. 14 (eigenvectors stay on their span).
 */
class LinearTest {
    private fun rnd(seed: Long) = java.util.Random(seed)
    private fun randMat(r: java.util.Random, k: Double = 2.0) = Mat3(DoubleArray(9) { (r.nextDouble() * 2 - 1) * k })
    private fun randVec(r: java.util.Random) = Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian())

    // ---------------------------------------------------------------- determinant = volume

    @Test
    fun determinantEqualsTheVolumeOfTheTransformedCube() {
        // triple product (det) vs divergence-theorem volume of the 12 transformed triangles (vertices only)
        assertEquals(1.0, Linear.signedVolume(Linear.CUBE, Linear.CUBE_TRIS), 1e-15)
        val r = rnd(1)
        var worst = 0.0; var neg = 0
        repeat(2000) {
            val m = randMat(r)
            val d = m.det(); val v = Linear.imageCubeVolume(m)
            worst = maxOf(worst, abs(d - v))
            if (d < 0) neg++
            // det also = the cofactor expansion along row 0 (a third, textbook formula)
            val cof = m[0, 0] * (m[1, 1] * m[2, 2] - m[1, 2] * m[2, 1]) - m[0, 1] * (m[1, 0] * m[2, 2] - m[1, 2] * m[2, 0]) +
                m[0, 2] * (m[1, 0] * m[2, 1] - m[1, 1] * m[2, 0])
            assertEquals(cof, d, 1e-12)
        }
        println("LINEAR det vs cube volume from vertices: 2000 random matrices, max |det - V| = %.2e, %d with det < 0".format(worst, neg))
        assertTrue(worst < 1e-12); assertTrue(neg in 500..1500)
        // the presets: exact values
        val p = LinearPresets
        val expect = listOf(p.IDENTITY to 1.0, p.ROTATE to 1.0, p.SHEAR to 1.0, p.SCALE to 1.17, p.PROJECT to 0.0, p.REFLECT to -1.0, p.WELCOME to 1.15)
        for ((m, d) in expect) { assertEquals(d, m.det(), 1e-12); assertEquals(d, Linear.imageCubeVolume(m), 1e-12) }
        println("LINEAR presets det: identity 1, rotate %.3f, shear %.3f, scale %.3f, project %.3f, reflect %.3f, welcome %.3f".format(
            p.ROTATE.det(), p.SHEAR.det(), p.SCALE.det(), p.PROJECT.det(), p.REFLECT.det(), p.WELCOME.det()))
        // det is multiplicative: volume factors compose
        repeat(200) { val a = randMat(r); val b = randMat(r); assertEquals(a.det() * b.det(), (b * a).det(), 1e-10) }
    }

    @Test
    fun orientationSign() {
        // det < 0 <=> the image of a right-handed frame is left-handed <=> the cube's outward normals point inward
        val refl = LinearPresets.REFLECT
        assertTrue((refl.col(0) cross refl.col(1)) dot refl.col(2) < 0)
        assertEquals(-1.0, Linear.imageCubeVolume(refl), 1e-15)
        // an "F" (stem up, bars to the right): its right-handedness (right × up) · out flips sign under the mirror
        val right = Vec3(-0.62, 0.78, 0.0); val up = Vec3.Z; val out = right cross up
        fun handed(m: Mat3) = ((m * right) cross (m * up)) dot (m * out)
        assertTrue(handed(Mat3.identity()) > 0); assertTrue(handed(LinearPresets.ROTATE) > 0); assertTrue(handed(LinearPresets.SHEAR) > 0)
        assertTrue(handed(refl) < 0)
        // a rotation never flips; any product with ONE reflection does; two reflections cancel
        val r = rnd(2)
        repeat(100) {
            val rot = Mat3.rotation(randVec(r), r.nextDouble() * 2 * PI)
            assertEquals(1.0, rot.det(), 1e-12)
            assertTrue((rot * refl).det() < 0); assertTrue((refl * rot * refl).det() > 0)
        }
        // squashing: projection has det 0 and the image cube has zero volume (flat)
        assertEquals(0.0, Linear.imageCubeVolume(LinearPresets.PROJECT), 1e-15)
    }

    // ---------------------------------------------------------------- composition

    @Test
    fun compositionIsTheMatrixProductAndOrderMatters() {
        val a = LinearPresets.COMPOSE_A; val b = LinearPresets.COMPOSE_B
        val r = rnd(3)
        repeat(500) { val v = randVec(r); val lhs = b * (a * v); val rhs = (b * a) * v; assertTrue((lhs - rhs).norm < 1e-12) }
        val ba = b * a; val ab = a * b
        // exact columns: shear first sends k̂ to (0,1,1), then the 90° turn sends that to (-1,0,1); the other order keeps (0,1,1)
        assertTrue((ba.col(2) - Vec3(-1.0, 0.0, 1.0)).norm < 1e-12)
        assertTrue((ab.col(2) - Vec3(0.0, 1.0, 1.0)).norm < 1e-12)
        val diff = (ba - ab).maxAbs()
        println("LINEAR compose: BA k̂ = (%.0f, %.0f, %.0f), AB k̂ = (%.0f, %.0f, %.0f), max |BA - AB| = %.3f, det BA = det AB = %.3f".format(
            ba[0, 2], ba[1, 2], ba[2, 2], ab[0, 2], ab[1, 2], ab[2, 2], diff, ba.det()))
        assertEquals(1.0, diff, 1e-12)
        assertEquals(ba.det(), ab.det(), 1e-12)
        // matrix product is associative, not commutative (random)
        var nonCommuting = 0
        repeat(200) { val x = randMat(r); val y = randMat(r); val z = randMat(r)
            assertTrue(((x * y) * z).isClose(x * (y * z), 1e-11)); if (!(x * y).isClose(y * x, 1e-6)) nonCommuting++ }
        assertEquals(200, nonCommuting)
    }

    // ---------------------------------------------------------------- eigen

    private fun assertEigenpairs(m: Mat3, tag: String): List<EigenSpace> {
        val es = Linear.eigen(m)
        var worst = 0.0
        for (e in es) for (v in e.basis) {
            assertEquals(1.0, v.norm, 1e-9)
            worst = maxOf(worst, (m * v - v * e.lambda).norm)
        }
        assertTrue("$tag residual $worst", worst < 1e-8 * maxOf(1.0, m.frob()))
        return es
    }

    @Test
    fun eigenSymmetric() {
        // Strang's symmetric example [[2,0,0],[0,3,4],[0,4,9]]: λ = 11, 2, 1 (the 2x2 block has λ² − 12λ + 11 = 0)
        val s = Mat3(doubleArrayOf(2.0, 0.0, 0.0, 0.0, 3.0, 4.0, 0.0, 4.0, 9.0))
        val es = assertEigenpairs(s, "strang")
        assertEquals(listOf(11.0, 2.0, 1.0), es.map { Math.rint(it.lambda * 1e9) / 1e9 })
        // symmetric -> orthogonal eigenvectors; (1 → (0, 2, −1)/√5, 11 → (0, 1, 2)/√5)
        val v = es.map { it.basis.single() }
        assertEquals(0.0, v[0] dot v[1], 1e-12); assertEquals(0.0, v[0] dot v[2], 1e-12); assertEquals(0.0, v[1] dot v[2], 1e-12)
        assertEquals(1.0, abs(v[0] dot Vec3(0.0, 1.0, 2.0).unit()), 1e-12)
        assertEquals(1.0, abs(v[2] dot Vec3(0.0, 2.0, -1.0).unit()), 1e-12)
        // a symmetric matrix with a repeated eigenvalue: [[2,1,0],[1,2,0],[0,0,3]] -> λ = 3 (plane), 1 (line)
        val r = assertEigenpairs(Mat3(doubleArrayOf(2.0, 1.0, 0.0, 1.0, 2.0, 0.0, 0.0, 0.0, 3.0)), "rep")
        assertEquals(2, r.size); assertEquals(3.0, r[0].lambda, 1e-9); assertEquals(2, r[0].geometric); assertEquals(1.0, r[1].lambda, 1e-9)
        assertEquals(1.0, abs(r[1].basis[0] dot Vec3(1.0, -1.0, 0.0).unit()), 1e-9)
        println("LINEAR eigen symmetric: Strang [[2,0,0],[0,3,4],[0,4,9]] λ = %s".format(es.joinToString { "%.12f".format(it.lambda) }))
    }

    @Test
    fun eigenNonSymmetric() {
        // exact construction A = P D P⁻¹ with P's columns (1,0,1), (1,1,0), (0,1,1) and D = diag(3, 2, −1)
        val p = Mat3.fromCols(Vec3(1.0, 0.0, 1.0), Vec3(1.0, 1.0, 0.0), Vec3(0.0, 1.0, 1.0))
        val a = p * Mat3.diag(3.0, 2.0, -1.0) * p.inverse()!!
        val es = assertEigenpairs(a, "PDP")
        assertEquals(3, es.size)
        assertEquals(3.0, es[0].lambda, 1e-12); assertEquals(2.0, es[1].lambda, 1e-12); assertEquals(-1.0, es[2].lambda, 1e-12)
        for (k in 0 until 3) assertEquals(1.0, abs(es[k].basis.single() dot p.col(k).unit()), 1e-12)
        println("LINEAR eigen non-symmetric P·diag(3,2,−1)·P⁻¹: λ = %s, |cos(v, P col)| = %s".format(
            es.joinToString { "%.12f".format(it.lambda) }, (0 until 3).joinToString { "%.12f".format(abs(es[it].basis.single() dot p.col(it).unit())) }))
        // the presets
        val shear = assertEigenpairs(LinearPresets.SHEAR, "shear")          // defective: λ = 1 three times, eigenspace = the floor
        assertEquals(1, shear.size); assertEquals(3, shear[0].algebraic); assertEquals(2, shear[0].geometric)
        shear[0].basis.forEach { assertEquals(0.0, it.z, 1e-12) }
        val rot = assertEigenpairs(LinearPresets.ROTATE, "rotate")          // one real direction: the axis
        assertEquals(1, rot.size); assertEquals(1.0, rot[0].lambda, 1e-12); assertEquals(1.0, abs(rot[0].basis[0].z), 1e-12)
        val ev = Linear.eigenvalues(LinearPresets.ROTATE)
        assertTrue(ev.hasComplexPair); assertEquals(0.0, ev.re, 1e-12); assertEquals(1.0, ev.im, 1e-12)   // e^{±iπ/2}
        val proj = assertEigenpairs(LinearPresets.PROJECT, "project")      // λ = 1 on the floor plane, 0 vertically
        assertEquals(listOf(1.0, 0.0), proj.map { Math.rint(it.lambda * 1e9) / 1e9 }); assertEquals(2, proj[0].geometric)
        val refl = assertEigenpairs(LinearPresets.REFLECT, "reflect")      // λ = 1 on the mirror plane, −1 across it
        assertEquals(1.0, refl[0].lambda, 1e-12); assertEquals(2, refl[0].geometric)
        assertEquals(-1.0, refl[1].lambda, 1e-12); assertEquals(1.0, abs(refl[1].basis[0] dot Vec3(1.0, -1.0, 0.0).unit()), 1e-12)
        val w = assertEigenpairs(LinearPresets.WELCOME, "welcome")         // triangular: λ = the diagonal
        assertEquals(listOf(1.25, 1.15, 0.80), w.map { Math.rint(it.lambda * 1e9) / 1e9 })
    }

    @Test
    fun eigenRandomMatricesSatisfyAvEqualsLambdaV() {
        // every real eigenpair satisfies Av = λv; Vieta: Σλ = trace, Πλ = det (complex pair contributes re ± i im)
        val r = rnd(4)
        var pairs = 0; var withComplex = 0; var worst = 0.0; var vieta = 0.0
        repeat(3000) {
            val m = randMat(r, 1.5)
            val es = Linear.eigen(m)
            for (e in es) for (v in e.basis) { worst = maxOf(worst, (m * v - v * e.lambda).norm / maxOf(1.0, m.frob())); pairs++ }
            val ev = Linear.eigenvalues(m)
            val sum = ev.real.sum() + if (ev.hasComplexPair) 2 * ev.re else 0.0
            val prod = ev.real.fold(1.0) { a, b -> a * b } * if (ev.hasComplexPair) ev.re * ev.re + ev.im * ev.im else 1.0
            // near-double roots are ill-conditioned (error ~ sqrt(machine eps)), hence 1e-7 not 1e-12
            vieta = maxOf(vieta, abs(m.trace() - sum), abs(m.det() - prod))
            assertEquals(m.trace(), sum, 1e-7); assertEquals(m.det(), prod, 1e-7)
            assertTrue(ev.real.isNotEmpty())                        // a real cubic always has a real root
            if (ev.hasComplexPair) withComplex++
        }
        println("LINEAR eigen random: 3000 matrices, $pairs eigenpairs, max |Av − λv|/|A| = %.2e, %d with a complex pair, max Vieta error (Σλ−tr, Πλ−det) %.1e".format(worst, withComplex, vieta))
        assertTrue(worst < 1e-9)
        assertTrue(withComplex in 300..2700)
    }

    // ---------------------------------------------------------------- SVD + quaternions (the node transforms)

    @Test
    fun svdRebuildsTheMatrixWithProperRotations() {
        val r = rnd(5)
        val cases = (0 until 500).map { randMat(r) } + listOf(LinearPresets.PROJECT, LinearPresets.REFLECT, LinearPresets.SHEAR,
            Mat3.fromCols(Vec3(1.0, 2.0, 0.0), Vec3(2.0, 4.0, 0.0), Vec3(-1.0, -2.0, 0.0)),   // rank 1
            Mat3(DoubleArray(9)), Mat3.identity())
        var worst = 0.0
        for (m in cases) {
            val s = Linear.svd(m)
            val rebuilt = s.u * Mat3.diag(s.s.x, s.s.y, s.s.z) * s.v.transpose()
            worst = maxOf(worst, (rebuilt - m).maxAbs())
            assertTrue(Linear.isRotation(s.u, 1e-9)); assertTrue(Linear.isRotation(s.v, 1e-9))
            assertTrue(s.s.x >= s.s.y - 1e-12 && s.s.y >= abs(s.s.z) - 1e-9)
            if (abs(m.det()) > 1e-9) assertEquals(Math.signum(m.det()), Math.signum(s.s.z), 0.0)
        }
        println("LINEAR svd: ${cases.size} matrices (incl. rank 0/1/2), max |U S Vᵀ − M| = %.2e".format(worst))
        assertTrue(worst < 1e-9)
    }

    @Test
    fun quaternionMatchesTheRotationMatrix() {
        val r = rnd(6)
        fun fromQ(q: DoubleArray): Mat3 { val (x, y, z, w) = q.toList()
            return Mat3(doubleArrayOf(1 - 2 * (y * y + z * z), 2 * (x * y - w * z), 2 * (x * z + w * y),
                2 * (x * y + w * z), 1 - 2 * (x * x + z * z), 2 * (y * z - w * x),
                2 * (x * z - w * y), 2 * (y * z + w * x), 1 - 2 * (x * x + y * y))) }
        val angles = (0 until 300).map { r.nextDouble() * PI } + listOf(0.0, PI, PI - 1e-9, 1e-9)
        for (a in angles) {
            val rot = Mat3.rotation(randVec(r), a)
            assertTrue(fromQ(Linear.quat(rot)).isClose(rot, 1e-12))
            val aa = Linear.axisAngle(rot)!!
            assertTrue(Mat3.rotation(aa.first, aa.second).isClose(rot, 1e-6))
        }
        assertNull(Linear.axisAngle(LinearPresets.SHEAR)); assertNull(Linear.axisAngle(LinearPresets.REFLECT))
    }

    // ---------------------------------------------------------------- the animation path

    @Test
    fun pathTurnsRotationsRigidlyAndKeepsEigenvectorsOnTheirSpan() {
        val i = Mat3.identity()
        // I -> 90° turn: halfway is exactly the 45° turn, det stays 1 all the way (no shrinking)
        val half = Linear.path(i, LinearPresets.ROTATE, 0.5)
        assertTrue(half.isClose(Mat3.rotation(Vec3.Z, PI / 4), 1e-12))
        for (k in 0..20) assertEquals(1.0, Linear.path(i, LinearPresets.ROTATE, k / 20.0).det(), 1e-12)
        // I -> reflection cannot stay a rotation: det goes 1 -> 0 (flat, at t = 1/2) -> -1, continuously
        assertEquals(0.0, Linear.path(i, LinearPresets.REFLECT, 0.5).det(), 1e-15)
        // I -> any M on the straight path: the target's eigenvectors stay on their span the whole time
        val m = LinearPresets.WELCOME
        val es = Linear.eigen(m)
        for (k in 1..19) {
            val mt = Linear.path(i, m, k / 20.0)
            for (e in es) { val v = e.basis[0]; val w = mt * v
                assertEquals(0.0, (w cross v).norm, 1e-12)
                assertEquals(1 - k / 20.0 + k / 20.0 * e.lambda, w dot v, 1e-12) }
        }
        // compose stage 2 (A -> BA with B a rotation) turns the already-sheared space rigidly
        val a = LinearPresets.COMPOSE_A; val ba = LinearPresets.COMPOSE_B * a
        assertTrue(Linear.path(a, ba, 0.5).isClose(Mat3.rotation(Vec3.Z, PI / 4) * a, 1e-12))
        // end points are exact
        assertTrue(Linear.path(a, ba, 0.0) == a); assertTrue(Linear.path(a, ba, 1.0) == ba)
        assertFalse(Linear.isRotation(a))
        println("LINEAR path: I→Rz90 at t=½ = Rz45 (det 1.000), I→reflect at t=½ det = %.1e, eigen-lines fixed along I→M".format(
            Linear.path(i, LinearPresets.REFLECT, 0.5).det()))
    }

    @Test
    fun randomPresetsStayReadable() {
        val dets = (0 until 50).map { LinearPresets.random(it) }.onEach { m ->
            assertTrue(abs(m.det()) in 0.35..1.8); (0 until 3).forEach { assertTrue(m.col(it).norm in 0.45..1.55) }
        }.map { it.det() }
        assertTrue(LinearPresets.random(7) == LinearPresets.random(7))            // reproducible
        assertTrue(dets.any { it < 0 } && dets.any { it > 0 })
        // the cubic solver on textbook polynomials: (λ−1)(λ−2)(λ−3), (λ−1)²λ, λ³ − 1
        assertEquals(listOf(3.0, 2.0, 1.0), Linear.cubicRoots(-6.0, 11.0, -6.0).real.map { Math.rint(it * 1e12) / 1e12 })
        assertEquals(listOf(1.0, 1.0, 0.0), Linear.cubicRoots(-2.0, 1.0, 0.0).real.map { Math.rint(it * 1e7) / 1e7 })
        val c = Linear.cubicRoots(0.0, 0.0, -1.0)
        assertEquals(1, c.real.size); assertEquals(1.0, c.real[0], 1e-12); assertEquals(-0.5, c.re, 1e-12); assertEquals(sqrt(3.0) / 2, c.im, 1e-12)
    }
}
