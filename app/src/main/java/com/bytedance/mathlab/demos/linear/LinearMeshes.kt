// Geometry for the linear-map experiment: painted ("baked light") boxes that keep reading as solids
// under ANY linear map (shear, squash, mirror), arrow cones, a quad, and mirrored mesh variants.
//
// Why baked shading: the solids (unit cube, the "F", the shelf minis) are drawn through a node chain
// R(U)·S(σ)·R(Vᵀ) = M (an SVD), so a shear or a squash is a node transform, never a mesh rebuild. Lit
// normals under a near-singular scale are meaningless, so every face carries its own grey level in a
// tiny texture (unlit x tint): the cube looks lit from above-front whatever M does, like a painted die.
//
// Why mirrored variants: an orientation-reversing M would need a NEGATIVE node scale, which flips the
// triangle winding on device (back-face culling then shows the wrong faces). Instead the demo swaps to
// a copy of the mesh reflected by F0 = diag(−1, 1, 1) with its winding reversed, and drives the chain
// with M·F0 (det > 0): world = (M F0)(F0 p) = M p, every node scale stays positive.

package com.bytedance.mathlab.demos.linear

import com.bytedance.mathlab.render.Image
import com.bytedance.mathlab.render.MeshData
import com.bytedance.mathlab.render.ProcTex
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal object LinearMeshes {
    /** Face order of [shadedBoxes]: +x, −x, +y, −y, +z, −z (z = math "up"). */
    private val SHADE = floatArrayOf(0.80f, 0.56f, 0.68f, 0.48f, 1.00f, 0.40f)
    private const val BLOCK = 8

    /** 6 grey blocks (one per face direction), sampled at block centres: exact values, no bleeding. */
    fun shadeTex(): Image {
        val img = Image(6 * BLOCK, BLOCK)
        for (y in 0 until BLOCK) for (x in 0 until 6 * BLOCK) { val g = SHADE[x / BLOCK]; img.px[y * img.w + x] = ProcTex.argb(1f, g, g, g) }
        return img
    }

    /** Solid colour blocks (e.g. red/green/blue for the shelf minis' basis edges). */
    fun paletteTex(cols: List<FloatArray>): Image {
        val img = Image(cols.size * BLOCK, BLOCK)
        for (y in 0 until BLOCK) for (x in 0 until img.w) { val c = cols[x / BLOCK]; img.px[y * img.w + x] = ProcTex.argb(1f, c[0], c[1], c[2]) }
        return img
    }

    private fun faceU(f: Int, n: Int = 6) = (f + 0.5f) / n

    /**
     * Axis-aligned boxes [lo, hi] (each a FloatArray(6): x0,y0,z0,x1,y1,z1) merged into one mesh, outward
     * CCW, each face's UV at the centre of its shade block. [paint] = palette index per box (u layout
     * switches to the palette when given).
     */
    fun shadedBoxes(boxes: List<FloatArray>, paint: IntArray? = null, paletteN: Int = 3): MeshData {
        val pos = ArrayList<Float>(); val nrm = ArrayList<Float>(); val uv = ArrayList<Float>(); val idx = ArrayList<Int>()
        for ((bi, b) in boxes.withIndex()) {
            val x0 = b[0]; val y0 = b[1]; val z0 = b[2]; val x1 = b[3]; val y1 = b[4]; val z1 = b[5]
            // per face: normal, 4 corners CCW seen from outside
            val faces = arrayOf(
                floatArrayOf(1f, 0f, 0f, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1),
                floatArrayOf(-1f, 0f, 0f, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0),
                floatArrayOf(0f, 1f, 0f, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0),
                floatArrayOf(0f, -1f, 0f, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1),
                floatArrayOf(0f, 0f, 1f, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1),
                floatArrayOf(0f, 0f, -1f, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0),
            )
            for ((f, d) in faces.withIndex()) {
                val base = pos.size / 3
                val u = if (paint != null) faceU(paint[bi], paletteN) else faceU(f)
                for (k in 0 until 4) {
                    pos += d[3 + k * 3]; pos += d[4 + k * 3]; pos += d[5 + k * 3]
                    nrm += d[0]; nrm += d[1]; nrm += d[2]
                    uv += u; uv += 0.5f
                }
                idx += base; idx += base + 1; idx += base + 2; idx += base; idx += base + 2; idx += base + 3
            }
        }
        return MeshData(pos.toFloatArray(), nrm.toFloatArray(), uv.toFloatArray(), idx.toIntArray())
    }

    /** Copy of [md] reflected by x -> −x with the winding reversed (still outward CCW). */
    fun mirrored(md: MeshData): MeshData {
        val pos = md.pos.copyOf(); val nrm = md.nrm.copyOf()
        for (k in 0 until md.vertexCount) { pos[k * 3] = -pos[k * 3]; nrm[k * 3] = -nrm[k * 3] }
        val idx = md.idx.copyOf()
        for (t in 0 until md.triangleCount) { val a = idx[t * 3 + 1]; idx[t * 3 + 1] = idx[t * 3 + 2]; idx[t * 3 + 2] = a }
        return MeshData(pos, nrm, md.uv.copyOf(), idx)
    }

    /** Unit cone along +Y: base radius 1 at y = 0, apex at y = 1 (scale it to (r, h, r)). */
    fun cone(seg: Int = 20): MeshData {
        val pos = ArrayList<Float>(); val nrm = ArrayList<Float>(); val uv = ArrayList<Float>(); val idx = ArrayList<Int>()
        val k = 1f / kotlin.math.sqrt(2f)
        for (s in 0..seg) {
            val a = 2.0 * PI * s / seg; val c = cos(a).toFloat(); val sn = sin(a).toFloat()
            pos += c; pos += 0f; pos += sn; nrm += c * k; nrm += k; nrm += sn * k; uv += s.toFloat() / seg; uv += 1f
            pos += 0f; pos += 1f; pos += 0f; nrm += c * k; nrm += k; nrm += sn * k; uv += s.toFloat() / seg; uv += 0f
        }
        for (s in 0 until seg) { val a = s * 2; idx += a; idx += a + 1; idx += a + 2; idx += a + 2; idx += a + 1; idx += a + 3 }
        // base cap
        val ctr = pos.size / 3
        pos += 0f; pos += 0f; pos += 0f; nrm += 0f; nrm += -1f; nrm += 0f; uv += 0.5f; uv += 0.5f
        for (s in 0..seg) {
            val a = 2.0 * PI * s / seg
            pos += cos(a).toFloat(); pos += 0f; pos += sin(a).toFloat(); nrm += 0f; nrm += -1f; nrm += 0f; uv += 0.5f; uv += 0.5f
        }
        for (s in 0 until seg) { idx += ctr; idx += ctr + 1 + s; idx += ctr + 2 + s }
        return MeshData(pos.toFloatArray(), nrm.toFloatArray(), uv.toFloatArray(), idx.toIntArray())
    }

    /** Square [−h, h]² in the local XY plane (normal +Z). */
    fun quad(h: Float): MeshData = MeshData(
        floatArrayOf(-h, -h, 0f, h, -h, 0f, h, h, 0f, -h, h, 0f),
        floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
        floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f),
        intArrayOf(0, 1, 2, 0, 2, 3))
}
