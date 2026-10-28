// Fractal core — the Menger sponge (Menger 1926), the Sierpiński carpet and tetrahedron, similarity dimension,
// and a voxel surface mesher with hidden-face culling. Pure Kotlin (no Android / SDK), JVM-tested.
//
// THE NUMBERS (unit cube, level n; cells have side 3^-n):
//   cubes      C_n = 20^n                                   (each kept cube -> 20 of the 27 sub-cubes)
//   volume     V_n = 20^n / 27^n = (20/27)^n  -> 0
//   surface    A_n = 2 (20/9)^n + 4 (8/9)^n   -> infinity
//              derivation: the level-(n+1) sponge is 20 copies of the level-n sponge at scale 1/3, so their surfaces
//              add up to 20 A_n / 9, minus the faces where two copies touch. The 20 copies touch in 24 pairs
//              (2 contacts on each of the 12 cube edges: corner–edge–corner) and each contact hides one level-n
//              carpet face (area (8/9)^n / 9) on BOTH copies:  A_{n+1} = (20/9) A_n − (48/9)(8/9)^n,  A_0 = 6.
//              Trying A_n = a (20/9)^n + b (8/9)^n gives 8b = 20b − 48 -> b = 4, a = 6 − 4 = 2.
//              In cell faces (area 9^-n each): F_n = A_n 9^n = 2·20^n + 4·8^n  (6, 72, 1056, 18048, …).
//              MengerTest counts the exposed faces of the voxel grid for n = 0..3 and gets exactly these.
//   dimension  N = s^D with N copies at scale 1/s:  D = ln N / ln s.  Sponge ln20/ln3 = 2.7268…,
//              carpet ln8/ln3 = 1.8928…, Sierpiński tetrahedron ln4/ln2 = 2 (Mandelbrot, "The Fractal Geometry
//              of Nature", 1982, ch. 14; Falconer, "Fractal Geometry", 3rd ed., §9.2).
//
// Mesh: one quad per exposed cell face (a face between a filled and an empty cell; the grid border counts as
// empty). Each quad's UVs point into an 8 x 8 tile atlas: tile = mask + 16 * shade, where mask = 4-bit set of its
// edges that are REAL geometric edges (the coplanar neighbour face does not exist) and shade 0..3 = how enclosed
// the empty cell in front of the face is (a cheap ambient occlusion: tunnel walls darker than the outside), so the
// demo's texture can light up only true edges and let narrow tunnels read as deep.

package com.bytedance.mathlab.core.fractal

import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow

/** A dense boolean voxel grid; anything outside is empty. */
class VoxelGrid(val nx: Int, val ny: Int, val nz: Int) {
    private val bits = BooleanArray(nx * ny * nz)
    operator fun get(i: Int, j: Int, k: Int): Boolean =
        i in 0 until nx && j in 0 until ny && k in 0 until nz && bits[(k * ny + j) * nx + i]
    operator fun set(i: Int, j: Int, k: Int, v: Boolean) { bits[(k * ny + j) * nx + i] = v }
    fun count(): Int = bits.count { it }

    companion object {
        fun of(nx: Int, ny: Int, nz: Int, f: (Int, Int, Int) -> Boolean) = VoxelGrid(nx, ny, nz).also { g ->
            for (k in 0 until nz) for (j in 0 until ny) for (i in 0 until nx) if (f(i, j, k)) g[i, j, k] = true
        }
    }
}

object Fractal {
    /** Similarity dimension of a shape made of [n] copies of itself at scale 1/[s]. */
    fun dim(n: Int, s: Int): Double = ln(n.toDouble()) / ln(s.toDouble())
    val D_MENGER = dim(20, 3)
    val D_CARPET = dim(8, 3)
    val D_TETRA = dim(4, 2)
    fun pow3(n: Int): Int { var r = 1; repeat(n) { r *= 3 }; return r }
}

object Menger {
    /** Is cell (x, y, z) of the 3^level grid kept? (No base-3 digit position where two coordinates have a 1.) */
    fun keep(level: Int, x: Int, y: Int, z: Int): Boolean {
        var a = x; var b = y; var c = z
        repeat(level) {
            var ones = 0
            if (a % 3 == 1) ones++
            if (b % 3 == 1) ones++
            if (c % 3 == 1) ones++
            if (ones >= 2) return false
            a /= 3; b /= 3; c /= 3
        }
        return true
    }

    fun cubes(n: Int): Long { var r = 1L; repeat(n) { r *= 20 }; return r }
    fun volume(n: Int): Double = (20.0 / 27.0).pow(n)
    fun area(n: Int): Double = 2.0 * (20.0 / 9.0).pow(n) + 4.0 * (8.0 / 9.0).pow(n)
    /** Exposed cell faces of the level-n sponge on its own 3^n grid: 2·20^n + 4·8^n. */
    fun faces(n: Int): Long { var a = 1L; var b = 1L; repeat(n) { a *= 20; b *= 8 }; return 2 * a + 4 * b }

    /** Level-[level] sponge sampled on a [res]^3 grid (res = 3^m, m >= level). */
    fun grid(level: Int, res: Int = Fractal.pow3(level)): VoxelGrid {
        val k = res / Fractal.pow3(level)
        require(k * Fractal.pow3(level) == res) { "res must be 3^m with m >= level" }
        return VoxelGrid.of(res, res, res) { x, y, z -> keep(level, x / k, y / k, z / k) }
    }

    /** The cubes carved away going from level [level] − 1 to [level] (7 per kept cube), on a [res]^3 grid. */
    fun carved(level: Int, res: Int = Fractal.pow3(level)): VoxelGrid {
        require(level >= 1)
        val k = res / Fractal.pow3(level)
        return VoxelGrid.of(res, res, res) { x, y, z -> keep(level - 1, x / (3 * k), y / (3 * k), z / (3 * k)) && !keep(level, x / k, y / k, z / k) }
    }

    /** The 20 sub-cube positions (a, b, c) in 0..2 kept at level 1, in a fixed order. */
    val COPIES: List<IntArray> = buildList { for (c in 0..2) for (b in 0..2) for (a in 0..2) if (keep(1, a, b, c)) add(intArrayOf(a, b, c)) }
}

object Carpet {
    fun keep(level: Int, x: Int, y: Int): Boolean {
        var a = x; var b = y
        repeat(level) { if (a % 3 == 1 && b % 3 == 1) return false; a /= 3; b /= 3 }
        return true
    }
    fun squares(n: Int): Long { var r = 1L; repeat(n) { r *= 8 }; return r }
    fun area(n: Int): Double = (8.0 / 9.0).pow(n)
    /** A level-[level] carpet as a one-cell-thick slab (x, y in the plane). */
    fun slab(level: Int): VoxelGrid { val r = Fractal.pow3(level); return VoxelGrid.of(r, r, 1) { x, y, _ -> keep(level, x, y) } }
}

/** Sierpiński tetrahedron: 4 half-size copies (at the 4 corners) per level. */
object SierpinskiTetra {
    /** Regular tetrahedron with edge 1, centred at the origin, one vertex up. */
    val VERTS: Array<DoubleArray> = run {
        val r = kotlin.math.sqrt(3.0 / 8.0)                 // circumradius for edge 1
        val h = r / 3.0                                      // centroid -> base distance
        val rb = kotlin.math.sqrt(r * r - h * h)             // base circumradius
        arrayOf(doubleArrayOf(0.0, r, 0.0)) + Array(3) { i ->
            val a = Math.PI / 2 + i * 2 * Math.PI / 3
            doubleArrayOf(rb * kotlin.math.cos(a), -h, rb * kotlin.math.sin(a))
        }
    }

    /** Offsets (x, y, z) of the 4^n solid tetrahedra (edge 2^-n) of level n, inside the edge-1 tetrahedron. */
    fun pieces(n: Int): List<DoubleArray> {
        var cur = listOf(doubleArrayOf(0.0, 0.0, 0.0)); var s = 1.0
        repeat(n) {
            val nxt = ArrayList<DoubleArray>()
            for (c in cur) for (v in VERTS) nxt += doubleArrayOf(c[0] + v[0] * s / 2, c[1] + v[1] * s / 2, c[2] + v[2] * s / 2)
            cur = nxt; s /= 2
        }
        return cur
    }
    fun volume(n: Int): Double = 0.5.pow(n)                 // 4 copies of 1/8 the volume each level
}

/**
 * Surface of a voxel grid as quads, internal faces culled. Box = [sx] x [sy] x [sz] centred at the origin.
 * UV: tile t = mask + 16 * shade at (t % 8, t / 8) of an 8 x 8 atlas, inset by [INSET] of a tile; mask bit 0 = edge
 * at u = 0, bit 1 = u = 1, bit 2 = v = 0, bit 3 = v = 1 (set = a real edge); shade from the 8 cells around the
 * cell in front of the face: 0–1 filled -> 0, 2–3 -> 1, 4–5 -> 2, 6–8 -> 3.
 */
object VoxelMesher {
    class Mesh(val pos: FloatArray, val nrm: FloatArray, val uv: FloatArray, val idx: IntArray, val masks: IntArray, val shades: IntArray) {
        val vertexCount get() = pos.size / 3
        val faceCount get() = masks.size
    }

    const val INSET = 0.06f

    // per direction: normal axis & sign, tangent axes t1, t2 with t1 x t2 = n (CCW seen from outside)
    private val DIRS = arrayOf(
        intArrayOf(0, 1, 1, 2), intArrayOf(0, -1, 2, 1),      // +x: (y, z)   −x: (z, y)
        intArrayOf(1, 1, 2, 0), intArrayOf(1, -1, 0, 2),      // +y: (z, x)   −y: (x, z)
        intArrayOf(2, 1, 0, 1), intArrayOf(2, -1, 1, 0),      // +z: (x, y)   −z: (y, x)
    )

    fun exposedFaces(g: VoxelGrid): Int {
        var n = 0
        for (k in 0 until g.nz) for (j in 0 until g.ny) for (i in 0 until g.nx) if (g[i, j, k]) for (d in DIRS) {
            val c = intArrayOf(i, j, k); c[d[0]] += d[1]
            if (!g[c[0], c[1], c[2]]) n++
        }
        return n
    }

    fun build(g: VoxelGrid, sx: Float, sy: Float, sz: Float): Mesh {
        val faces = exposedFaces(g)
        val pos = FloatArray(faces * 12); val nrm = FloatArray(faces * 12); val uv = FloatArray(faces * 8)
        val idx = IntArray(faces * 6); val masks = IntArray(faces); val shades = IntArray(faces)
        val cs = floatArrayOf(sx / g.nx, sy / g.ny, sz / g.nz)
        val o = floatArrayOf(-sx / 2, -sy / 2, -sz / 2)
        var f = 0
        val c = IntArray(3); val e = IntArray(3)
        fun filled(a: IntArray) = g[a[0], a[1], a[2]]
        for (k in 0 until g.nz) for (j in 0 until g.ny) for (i in 0 until g.nx) {
            if (!g[i, j, k]) continue
            for (d in DIRS) {
                val ax = d[0]; val sg = d[1]; val t1 = d[2]; val t2 = d[3]
                c[0] = i; c[1] = j; c[2] = k; c[ax] += sg
                if (filled(c)) continue
                // edge mask: the coplanar face across an edge exists iff the neighbour cell is filled and the cell
                // in front of it (along the normal) is empty
                var mask = 0
                for ((bit, tv) in arrayOf(0 to (t1 to -1), 1 to (t1 to 1), 2 to (t2 to -1), 3 to (t2 to 1))) {
                    e[0] = i; e[1] = j; e[2] = k; e[tv.first] += tv.second
                    val cont = filled(e) && run { e[ax] += sg; !filled(e) }
                    if (!cont) mask = mask or (1 shl bit)
                }
                masks[f] = mask
                // occlusion: filled cells among the 8 tangent neighbours of the (empty) cell in front of the face
                var occ = 0
                for (a in -1..1) for (b in -1..1) {
                    if (a == 0 && b == 0) continue
                    e[0] = i; e[1] = j; e[2] = k; e[ax] += sg; e[t1] += a; e[t2] += b
                    if (filled(e)) occ++
                }
                val shade = min(3, occ / 2)
                shades[f] = shade
                val tile = mask + 16 * shade
                // base corner of the face
                val base = floatArrayOf(o[0] + i * cs[0], o[1] + j * cs[1], o[2] + k * cs[2])
                if (sg > 0) base[ax] += cs[ax]
                val v0 = f * 4
                for (q in 0 until 4) {
                    val du = if (q == 1 || q == 2) 1 else 0; val dv = if (q >= 2) 1 else 0
                    val p = base.copyOf(); p[t1] += du * cs[t1]; p[t2] += dv * cs[t2]
                    pos[(v0 + q) * 3] = p[0]; pos[(v0 + q) * 3 + 1] = p[1]; pos[(v0 + q) * 3 + 2] = p[2]
                    nrm[(v0 + q) * 3 + ax] = sg.toFloat()
                    val tu = (tile % 8).toFloat(); val tw = (tile / 8).toFloat()
                    uv[(v0 + q) * 2] = (tu + INSET + du * (1 - 2 * INSET)) / 8f
                    uv[(v0 + q) * 2 + 1] = (tw + INSET + dv * (1 - 2 * INSET)) / 8f
                }
                idx[f * 6] = v0; idx[f * 6 + 1] = v0 + 1; idx[f * 6 + 2] = v0 + 2
                idx[f * 6 + 3] = v0; idx[f * 6 + 4] = v0 + 2; idx[f * 6 + 5] = v0 + 3
                f++
            }
        }
        return Mesh(pos, nrm, uv, idx, masks, shades)
    }
}
