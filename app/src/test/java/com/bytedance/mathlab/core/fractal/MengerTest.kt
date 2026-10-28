package com.bytedance.mathlab.core.fractal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln

/**
 * The Menger sponge numbers, checked against the generated voxel grids (not just the formulas):
 * cubes 20^n, volume (20/27)^n, surface area 2(20/9)^n + 4(8/9)^n (derived in Fractal.kt), the culled mesh has
 * exactly the analytic number of faces, D = ln20/ln3; plus the device vertex budget of what the demo uploads.
 */
class MengerTest {

    @Test
    fun cubeCountIs20PowN() {
        for (n in 0..4) {
            val g = Menger.grid(n)
            assertEquals("level $n", Menger.cubes(n), g.count().toLong())
            println("MENGER n=$n cubes=${g.count()} (20^$n = ${Menger.cubes(n)})")
        }
        assertEquals(8000L, Menger.cubes(3))
        assertEquals(20, Menger.COPIES.size)
    }

    @Test
    fun volumeIs20Over27PowN() {
        for (n in 0..4) {
            val r = Fractal.pow3(n).toDouble()
            val v = Menger.grid(n).count() / (r * r * r)
            assertEquals(Menger.volume(n), v, 1e-15)
        }
        assertEquals(8000.0 / 19683.0, Menger.volume(3), 1e-15)
        assertEquals(0.40644, Menger.volume(3), 5e-6)                      // 40.6 %
        assertTrue(Menger.volume(60) < 1e-7)                              // -> 0
        println("MENGER V_3 = %.5f, V_10 = %.4f".format(Menger.volume(3), Menger.volume(10)))
    }

    @Test
    fun surfaceAreaFormulaMatchesCountedFaces() {
        val expect = longArrayOf(6, 72, 1056, 18048)
        for (n in 0..3) {
            val g = Menger.grid(n)
            val faces = VoxelMesher.exposedFaces(g)
            assertEquals("faces at level $n", expect[n], faces.toLong())
            assertEquals(Menger.faces(n), faces.toLong())
            val r = Fractal.pow3(n).toDouble()
            assertEquals("area at level $n", Menger.area(n), faces / (r * r), 1e-12)
            println("MENGER n=$n exposed faces=$faces area=%.4f".format(faces / (r * r)))
        }
        // the recurrence A_{n+1} = (20/9) A_n − (48/9)(8/9)^n that the closed form solves
        for (n in 0..10) assertEquals(Menger.area(n + 1), 20.0 / 9 * Menger.area(n) - 48.0 / 9 * Math.pow(8.0 / 9, n.toDouble()), 1e-9)
        assertEquals(24.7572, Menger.area(3), 1e-4)
        assertTrue(Menger.area(30) > 1e10)                               // -> infinity
        // a finer sampling of the same sponge has the same area (faces scale by 9 per extra subdivision)
        assertEquals(Menger.area(1), VoxelMesher.exposedFaces(Menger.grid(1, 9)) / 81.0, 1e-12)
    }

    @Test
    fun meshCullsHiddenFacesExactly() {
        for (n in 0..3) {
            val g = Menger.grid(n)
            val m = VoxelMesher.build(g, 1f, 1f, 1f)
            assertEquals(Menger.faces(n), m.faceCount.toLong())
            assertEquals(4 * Menger.faces(n), m.vertexCount.toLong())
            // every quad lies on a filled/empty boundary and faces outward: CCW winding agrees with the normal
            val r = Fractal.pow3(n)
            for (t in 0 until m.idx.size / 3) {
                val a = m.idx[t * 3]; val b = m.idx[t * 3 + 1]; val c = m.idx[t * 3 + 2]
                fun p(v: Int, k: Int) = m.pos[v * 3 + k]
                val ux = p(b, 0) - p(a, 0); val uy = p(b, 1) - p(a, 1); val uz = p(b, 2) - p(a, 2)
                val vx = p(c, 0) - p(a, 0); val vy = p(c, 1) - p(a, 1); val vz = p(c, 2) - p(a, 2)
                val cx = uy * vz - uz * vy; val cy = uz * vx - ux * vz; val cz = ux * vy - uy * vx
                val d = cx * m.nrm[a * 3] + cy * m.nrm[a * 3 + 1] + cz * m.nrm[a * 3 + 2]
                assertTrue("triangle $t wound inward", d > 0f)
                // the cell behind the face is filled, the one in front empty
                val f0 = (t / 2) * 4                                   // the quad's centre
                fun mid(k: Int) = (p(f0, k) + p(f0 + 1, k) + p(f0 + 2, k) + p(f0 + 3, k)) / 4
                val mx = mid(0); val my = mid(1); val mz = mid(2)
                fun cell(x: Float) = kotlin.math.floor((x + 0.5f) * r).toInt()
                val e = 0.25f / r
                val nx = m.nrm[a * 3]; val ny = m.nrm[a * 3 + 1]; val nz = m.nrm[a * 3 + 2]
                assertTrue(g[cell(mx - nx * e), cell(my - ny * e), cell(mz - nz * e)])
                assertTrue(!g[cell(mx + nx * e), cell(my + ny * e), cell(mz + nz * e)])
            }
        }
        // edge mask: a solid cube on a 3x3x3 grid glows only along its 12 edges: 12 edges x 3 segments x 2 faces
        val cube = VoxelMesher.build(Menger.grid(0, 3), 1f, 1f, 1f)
        assertEquals(54, cube.faceCount)
        assertEquals(72, cube.masks.sumOf { Integer.bitCount(it) })
        assertTrue(cube.shades.all { it == 0 })
        // occlusion shade: the 48 outside faces of level 1 are open (0), its 24 tunnel walls are shaded
        val l1 = VoxelMesher.build(Menger.grid(1), 1f, 1f, 1f)
        assertEquals(24, l1.shades.count { it > 0 })
    }

    /**
     * What the demo actually uploads to the headset (~20 µs per vertex): level 3 is drawn as 20 instances of the
     * level-2 mesh (self-similarity!) instead of one merged 72 192-vertex mesh (~1.4 s of upload).
     */
    @Test
    fun level3IsTwentyCopiesOfLevel2AndFitsTheVertexBudget() {
        val g3 = Menger.grid(3)
        val g2 = Menger.grid(2)
        val union = VoxelGrid(27, 27, 27)
        for (cp in Menger.COPIES) for (k in 0 until 9) for (j in 0 until 9) for (i in 0 until 9)
            if (g2[i, j, k]) union[cp[0] * 9 + i, cp[1] * 9 + j, cp[2] * 9 + k] = true
        for (k in 0 until 27) for (j in 0 until 27) for (i in 0 until 27) assertEquals(g3[i, j, k], union[i, j, k])

        val merged3 = VoxelMesher.build(g3, 1f, 1f, 1f).vertexCount
        val m0 = VoxelMesher.build(Menger.grid(0, 3), 1f, 1f, 1f).vertexCount
        val m1 = VoxelMesher.build(Menger.grid(1, 3), 1f, 1f, 1f).vertexCount
        val m2 = VoxelMesher.build(Menger.grid(2, 9), 1f, 1f, 1f).vertexCount
        val h1 = VoxelMesher.build(Menger.carved(1, 3), 1f, 1f, 1f).vertexCount
        val h2 = VoxelMesher.build(Menger.carved(2, 9), 1f, 1f, 1f).vertexCount
        val total = m0 + m1 + m2 + h1 + h2
        println("MENGER verts: merged L3 = $merged3 (%.2f s at 20 us), uploaded M0 $m0 + M1 $m1 + M2 $m2 + H1 $h1 + H2 $h2 = $total (%.0f ms)"
            .format(merged3 * 20e-6, total * 20e-3))
        assertEquals(72192, merged3)
        assertTrue("level-3 geometry (the shared level-2 mesh) over budget: $m2", m2 <= 4500)
        assertTrue("station-1 uploads over budget: $total", total <= 10_000)
        assertEquals(7, Menger.carved(1).count())
        assertEquals(140, Menger.carved(2).count())
    }

    @Test
    fun dimensions() {
        assertEquals(2.7268330278608417, Fractal.D_MENGER, 1e-12)        // ln 20 / ln 3
        assertEquals(1.8927892607143724, Fractal.D_CARPET, 1e-12)        // ln 8 / ln 3
        assertEquals(2.0, Fractal.D_TETRA, 1e-12)
        assertEquals(1.0, Fractal.dim(3, 3), 1e-15)
        assertEquals(2.0, Fractal.dim(9, 3), 1e-15)
        assertEquals(3.0, Fractal.dim(27, 3), 1e-15)
        assertEquals(20.0, Math.pow(3.0, Fractal.D_MENGER), 1e-12)
        // box counting on the generated grids: N(3^-n) = 20^n -> slope ln N / ln 3^n = D exactly
        for (n in 1..3) assertEquals(Fractal.D_MENGER, ln(Menger.grid(n).count().toDouble()) / ln(Fractal.pow3(n).toDouble()), 1e-12)
        for (n in 1..4) {
            val c = Carpet.slab(n).count()
            assertEquals(Carpet.squares(n), c.toLong())
            assertEquals(Fractal.D_CARPET, ln(c.toDouble()) / ln(Fractal.pow3(n).toDouble()), 1e-12)
        }
        println("DIM sponge %.6f carpet %.6f tetra %.6f".format(Fractal.D_MENGER, Fractal.D_CARPET, Fractal.D_TETRA))
    }

    @Test
    fun sierpinskiTetrahedron() {
        val v = SierpinskiTetra.VERTS
        fun d(a: DoubleArray, b: DoubleArray) = Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]))
        for (i in 0..3) for (j in i + 1..3) assertEquals(1.0, d(v[i], v[j]), 1e-12)
        for (n in 0..4) assertEquals(Math.pow(4.0, n.toDouble()).toInt(), SierpinskiTetra.pieces(n).size)
        // pieces of level 2 are distinct and inside the big tetrahedron's circumsphere
        val p = SierpinskiTetra.pieces(2)
        for (i in p.indices) for (j in i + 1 until p.size) assertTrue(d(p[i], p[j]) > 0.2)
        assertTrue(abs(SierpinskiTetra.volume(3) - 0.125) < 1e-15)
    }
}
