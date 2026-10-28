// 分形 · 门格海绵 · Fractals · Menger Sponge (Karl Menger, 1926).
//
// THE AHA: repeat one simple rule forever — cut every cube into 27, throw away the centre and the 6 face centres —
// and you get an object whose VOLUME vanishes ((20/27)^n -> 0) while its SURFACE grows without bound
// (2(20/9)^n + 4(8/9)^n -> ∞), and whose dimension is not a whole number: shrink by 3 and you get 20 copies, so
// 3^D = 20, D = ln20/ln3 ≈ 2.727 — more than a surface, less than a solid.
//   ① The sponge on a plinth, level 0 -> 3. Pinch the little gold sponge ("carve the next level"): every cube loses
//      its 7 inner sub-cubes, which fly out glowing and fade. Bars beside it: volume V_n shrinking, area A_n growing.
//      Walk-in: drag the zoom knob (or switch on "Dive in"): the sponge scales up about its front corner; pieces
//      that would leave the volume are clipped, and at ×3 the corner piece is exactly the whole sponge of the
//      level before — the dive then loops (self-similarity: you can fall in forever).
//   ② Dimension by counting: line (3 copies at 1/3), Sierpiński carpet (8), square (9), Sierpiński tetrahedron
//      (4 at 1/2), Menger sponge (20), cube (27), sorted by D on a ruler. Pinch an exhibit: it explodes into its
//      copies and counts them one by one; its D appears on the ruler. Challenge: slide D until 3^D = 20.
//
// DEVICE COST (core/fractal VoxelMesher, MengerTest): one merged, face-culled level-3 mesh would be 18 048 quads =
// 72 192 vertices ≈ 1.4 s of upload at ~20 µs/vertex. Instead level n (n >= 1) is drawn as 20 INSTANCES of the
// level-(n−1) mesh at scale 1/3 — the self-similarity itself — so the largest uploaded mesh is level 2 (4 224
// vertices) and everything this demo uploads is ~7 k vertices (~0.15 s, once, at build). Levels switch by setMesh
// between cached meshes; per frame only transforms, visibility and (quantised) materials change.

package com.bytedance.mathlab.demos.fractal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.bytedance.mathlab.core.P3
import com.bytedance.mathlab.core.fractal.Carpet
import com.bytedance.mathlab.core.fractal.Fractal
import com.bytedance.mathlab.core.fractal.Menger
import com.bytedance.mathlab.core.fractal.SierpinskiTetra
import com.bytedance.mathlab.core.fractal.VoxelGrid
import com.bytedance.mathlab.core.fractal.VoxelMesher
import com.bytedance.mathlab.lab.BakeCache
import com.bytedance.mathlab.lab.Bi
import com.bytedance.mathlab.lab.Challenge
import com.bytedance.mathlab.lab.Lesson
import com.bytedance.mathlab.lab.LessonStep
import com.bytedance.mathlab.lab.Q
import com.bytedance.mathlab.lab.SpecDemo
import com.bytedance.mathlab.lab.TapTarget
import com.bytedance.mathlab.lab.spec.Blend
import com.bytedance.mathlab.lab.spec.LabelSpec
import com.bytedance.mathlab.lab.spec.Mat
import com.bytedance.mathlab.lab.spec.Node
import com.bytedance.mathlab.lab.tr
import com.bytedance.mathlab.lab.ui.Button
import com.bytedance.mathlab.lab.ui.LabPanel
import com.bytedance.mathlab.lab.ui.LabRow
import com.bytedance.mathlab.lab.ui.LabSlider
import com.bytedance.mathlab.lab.ui.LabSwitch
import com.bytedance.mathlab.lab.ui.SegmentControl
import com.bytedance.mathlab.lab.ui.SegmentItem
import com.bytedance.mathlab.render.Image
import com.bytedance.mathlab.render.MeshData
import com.bytedance.mathlab.render.MeshGen
import com.bytedance.mathlab.render.OptGeo
import com.bytedance.mathlab.render.ProcTex
import com.bytedance.mathlab.render.Rgb
import com.pico.spatial.ui.design.Text
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

object FractalDemo : SpecDemo() {
    override val id = "fractal"
    override val title = "分形 · 门格海绵"  // i18n-ok (zh half of DemoRegistry.titleOf; English in TITLE_EN)
    override val credit = "Menger · 1926"

    // ================================================================ layout (volume metres)
    private const val PLINTH_Y = -0.262f
    private const val S = 0.46f                                   // sponge edge
    private val C = P3(-0.40f, -0.004f, -0.03f)                   // sponge centre
    private const val YAW = -30f                                  // degrees: front + right faces both visible
    // walk-in clip region (volume coordinates): pieces must stay inside it
    private val CLIP_LO = floatArrayOf(-0.885f, -0.245f, -0.585f)
    private val CLIP_HI = floatArrayOf(0.005f, 0.43f, 0.585f)
    private const val CTRL_X = 0.075f
    private const val NEXT_Y = 0.17f
    private const val PREV_Y = 0.06f
    private const val RAIL_Y0 = -0.205f
    private const val RAIL_Y1 = -0.085f
    // chart
    private const val CH_X0 = 0.215f
    private const val CH_DX = 0.088f
    private const val CH_Y = -0.205f
    private const val CH_H = 0.30f
    // station ②
    private val EX_X = FloatArray(6) { -0.68f + it * 0.236f }
    private const val EX_Y = 0.175f
    private const val L = 0.16f
    private const val RULER_Y = -0.105f
    private const val RULER_Z = 0.05f
    private fun rx(d: Double): Float = (-0.70 + (d - 0.8) * 0.52).toFloat()
    // shelf
    private const val SHELF_X = 0.76f
    private val TOK_Y = floatArrayOf(0.31f, 0.17f)

    // ================================================================ colours (sRGB)
    private val WHITE = Rgb(0.97f, 0.97f, 1.0f)
    private val ICE = Rgb(0.55f, 0.80f, 1.00f)
    private val GOLD = Rgb(1.00f, 0.80f, 0.32f)
    private val CYAN = Rgb(0.35f, 0.85f, 1.00f)
    private val ORANGE = Rgb(1.00f, 0.58f, 0.25f)
    private val DIM = Rgb(0.55f, 0.59f, 0.68f)
    private val LEVEL_TINT = arrayOf(Rgb(0.55f, 0.78f, 1.0f), Rgb(0.36f, 0.92f, 0.80f), Rgb(0.72f, 0.58f, 1.0f), Rgb(1.0f, 0.74f, 0.36f))
    // exhibits, in D order: line, carpet, square, tetrahedron, sponge, cube
    private val EX_COL = arrayOf(Rgb(0.85f, 0.90f, 1.0f), Rgb(0.78f, 0.55f, 1.0f), Rgb(0.32f, 0.88f, 0.80f), Rgb(0.45f, 0.95f, 0.45f), Rgb(1.0f, 0.74f, 0.36f), Rgb(0.42f, 0.66f, 1.0f))
    private val EX_N = intArrayOf(3, 8, 9, 4, 20, 27)
    private val EX_S = intArrayOf(3, 3, 3, 2, 3, 3)
    private val EX_D = DoubleArray(6) { Fractal.dim(EX_N[it], EX_S[it]) }
    private fun glow(c: Rgb, k: Float = 0.30f) = Rgb(c.r * k, c.g * k, c.b * k)
    private fun solid(c: Rgb, e: Float = 0.30f, rough: Float = 0.36f) = Mat.Pbr(c, metallic = 0f, roughness = rough, emissive = glow(c, e))
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.32f)
    private val plateOff = Mat.Pbr(Rgb(0.10f, 0.11f, 0.13f), metallic = 0.3f, roughness = 0.35f)
    private val plateOn = Mat.Pbr(Rgb(0.16f, 0.24f, 0.40f), metallic = 0.2f, roughness = 0.30f, emissive = Rgb(0.10f, 0.24f, 0.50f))

    // ================================================================ text atlas
    private const val TEX_VERSION = 1
    private const val EM_PX = 56
    private fun f1(v: Double) = String.format(Locale.US, "%.1f", v)
    private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)
    private fun f3(v: Double) = String.format(Locale.US, "%.3f", v)
    private fun pctKey(n: Int) = if (n == 0) "100 %" else f1(Menger.volume(n) * 100) + " %"
    private fun areaKey(n: Int) = if (n <= 1) "${Menger.area(n).roundToInt()}" else f2(Menger.area(n))
    private fun counterKey(n: Int) = "n = $n   20^$n = ${Menger.cubes(n)}"
    private fun vKey(n: Int) = "V_n = (20/27)^n = " + pctKey(n)
    private fun aKey(n: Int) = "A_n = 2(20/9)^n + 4(8/9)^n = " + areaKey(n)
    private fun nsKey(i: Int) = "N = ${EX_N[i]}, s = ${EX_S[i]}"
    private fun dKey(i: Int) = if (abs(EX_D[i] - EX_D[i].roundToInt()) < 1e-9) "D = ${EX_D[i].roundToInt()}" else "D ≈ ${f3(EX_D[i])}"

    private val ATLAS_ITEMS: List<FractalAtlas.Item> by lazy {
        val keys = LinkedHashMap<String, FractalAtlas.Item>()
        fun add(t: String) { if (t !in keys) keys[t] = FractalAtlas.Item(t, listOf(FractalText.Span(t, WHITE)), EM_PX) }
        for (c in "0123456789.") add(c.toString())
        for (k in 10..27) add("$k")
        for (n in 0..3) { add(counterKey(n)); add(vKey(n)); add(aKey(n)) }
        for (i in 0..5) { add(nsKey(i)); add(dKey(i)) }
        listOf("N = ?", "D = ?", "D", "D =", "3^D =", "N = s^D", "D = log N / log s", "×1", "×3", "n").forEach { add(it) }
        keys.values.toList()
    }
    private lateinit var atlas: FractalAtlas
    private val textMats = HashMap<Rgb, Mat>()
    private fun textMat(c: Rgb) = textMats.getOrPut(c) { Mat.Unlit(c, tex = atlas.image, blend = Blend.TRANSPARENT) }
    private val decalKey = java.util.IdentityHashMap<Node, String>()

    private fun decal(parent: Node, name: String, key: String, em: Float, tint: Rgb = WHITE, pos: P3 = P3(0f, 0f, 0f)): Node {
        val d = atlas.decal(key)
        val n = parent.add(name, d.mesh, textMat(tint), pos)
        decalKey[n] = key
        val h = em / d.emFrac; n.scale = P3(h * d.aspect, h, 1f)
        return n
    }
    private fun setDecal(n: Node, key: String, em: Float) {
        val d = atlas.decal(key)
        if (decalKey[n] != key) { n.setMesh(d.mesh); decalKey[n] = key }
        val h = em / d.emFrac; n.dims(h * d.aspect, h, 1f)
    }
    private fun decalWidth(key: String, em: Float): Float { val d = atlas.decal(key); return em / d.emFrac * d.aspect }

    /** A row of [cells] monospaced characters drawn from single-character decals (live numbers in 3-D). */
    private class DigitRow(val nodes: Array<Node>, val em: Float, val x0: Float, val y: Float, val z: Float) { var shown = "" }
    private fun digitRow(parent: Node, name: String, cells: Int, em: Float, tint: Rgb, x0: Float, y: Float, z: Float): DigitRow =
        DigitRow(Array(cells) { k -> decal(parent, "${name}_$k", "0", em, tint).also { it.visible = false } }, em, x0, y, z)
    private fun DigitRow.set(s: String) {
        if (s == shown) return
        shown = s
        var x = x0
        for (k in nodes.indices) {
            val n = nodes[k]
            if (k >= s.length) { n.show(false); continue }
            val adv = em * (if (s[k] == '.') 0.30f else 0.61f)
            setDecal(n, s[k].toString(), em); n.show(true)
            n.at(x + adv / 2, y, z); x += adv
        }
    }

    // ================================================================ state
    /** 0 ① the sponge, 1 ② dimension by counting. */
    var station = 0
    /** 0 slow, 1 medium, 2 fast. */
    var speed = 1
    private val SPEEDS = floatArrayOf(0.45f, 1f, 2.2f)
    private val spd get() = SPEEDS[speed]
    /** The level the sponge is at (a carve switches at its start, an un-carve at its end). */
    var level = 3; private set
    /** Where the queued carving is heading. */
    var targetLevel = 3; private set
    private var animDir = 0                   // +1 carving away, −1 putting back, 0 idle
    private var animT = 0f
    private var animK = 0                     // the transition is between level animK − 1 and animK
    /** Walk-in zoom, 1 … 3. */
    var zoom = 1f; private set
    var diving = false; private set
    /** How many times the dive has wrapped (fell one level deeper). */
    var dives = 0; private set
    private var zoomShown = -1f
    private var levelShown = -1
    private val clipOn = BooleanArray(20)
    private val barV = FloatArray(5)
    private val barA = FloatArray(5)
    // ②
    private val explode = FloatArray(6)
    private val exploded = BooleanArray(6)
    private val counting = IntArray(6)            // copies counted so far (−1 = not started)
    private val countClock = FloatArray(6)
    private val counted = BooleanArray(6)
    private var lastCounted = -1
    /** The challenge's dimension, 1 … 3. */
    var dUser = 2.0; private set
    private var clock = 0f
    private var dragAcc = 0f
    private var dragZ0 = 1f
    private var dragD0 = 2.0
    private var panelEpoch by mutableIntStateOf(0)
    private fun bump() { panelEpoch++ }
    private const val CARVE_S = 1.6f
    private const val COUNT_S = 0.13f

    private fun resetState() {
        station = 0; speed = 1; level = 3; targetLevel = 3; animDir = 0; animT = 0f; animK = 0
        zoom = 1f; diving = false; dives = 0; zoomShown = -1f; levelShown = -1; clipOn.fill(true)
        for (n in 0..4) { barV[n] = CH_H * Menger.volume(n).toFloat(); barA[n] = CH_H * (Menger.area(n) / Menger.area(4)).toFloat() }
        explode.fill(0f); exploded.fill(false); counting.fill(-1); countClock.fill(0f)
        counted.fill(false); counted[0] = true; counted[2] = true; counted[5] = true; lastCounted = -1
        dUser = 2.0; clock = 0f; dragAcc = 0f; dragZ0 = 1f; dragD0 = 2.0
        decalKey.clear(); textMats.clear(); digitRowsShownReset()
    }

    // ================================================================ meshes & nodes
    private lateinit var edgeTex: Image
    private lateinit var cubeMesh: MeshData          // one cell, every edge lit
    private lateinit var m0: MeshData                // level 0 on a 3^3 grid (edge lines at the cube's edges only)
    private lateinit var m1: MeshData
    private lateinit var m2: MeshData                // level 2 on 9^3 = 1 056 quads = 4 224 vertices
    private lateinit var h1: MeshData                // the 7 cubes carved going to level 1 (the "cross")
    private lateinit var h2: MeshData                // the 140 cubes carved going to level 2
    private lateinit var carpetMesh: MeshData
    private lateinit var tetraMesh: MeshData
    private lateinit var ball: MeshData
    private lateinit var cyl: MeshData
    private lateinit var unitBox: MeshData
    private lateinit var spongeMat: Array<Mat>
    private lateinit var debrisMat: Array<Array<Mat>>  // [level tint][fade step]
    private const val FADE_STEPS = 12

    private lateinit var groups: Array<Node>
    private lateinit var tokens: Array<Node>
    private lateinit var plates: Array<Node>
    private lateinit var poolGlow: Node
    // ①
    private lateinit var spongeRoot: Node
    private lateinit var zoomNode: Node
    private lateinit var body: Node
    private lateinit var lvl0: Node
    private lateinit var copies: Array<Node>
    private lateinit var chunks: Array<Node>
    private lateinit var debris: Array<Node>
    private lateinit var counter: Node
    private lateinit var nextBtn: Node
    private lateinit var prevBtn: Node
    private lateinit var zoomKnob: Node
    private lateinit var vBars: Array<Node>
    private lateinit var aBars: Array<Node>
    private lateinit var vHead: Node
    private lateinit var aHead: Node
    private lateinit var vMat: Array<Mat>             // [0 ghost, 1 done, 2 current]
    private lateinit var aMat: Array<Mat>
    // ②
    private lateinit var exNode: Array<Node>
    private lateinit var exRot: Array<Node>
    private lateinit var exCopies: Array<Array<Node>>
    private lateinit var exBase: Array<Array<P3>>
    private lateinit var exMat: Array<Array<Mat>>     // [exhibit][0 base, 1 counted, 2 flash]
    private lateinit var exNs: Array<Node>
    private lateinit var exD: Array<Node>
    private lateinit var exCount: Array<Node>
    private lateinit var markers: Array<Node>
    private lateinit var leaders: Array<Node>
    private lateinit var dKnob: Node
    private lateinit var dRow: DigitRow
    private lateinit var powRow: DigitRow
    private fun digitRowsShownReset() { if (::dRow.isInitialized) { dRow.shown = "#"; powRow.shown = "#" } }

    // ================================================================ small node helpers
    private fun Node.at(x: Float, y: Float, z: Float) { val q = pos; if (q.x != x || q.y != y || q.z != z) pos = P3(x, y, z) }
    private fun Node.dims(x: Float, y: Float, z: Float) { val s = scale; if (s.x != x || s.y != y || s.z != z) scale = P3(x, y, z) }
    private fun Node.turn(q: Q) { if (rot != q) rot = q }
    private fun Node.show(v: Boolean) { visible = v }
    private fun approach(v: Float, target: Float, rate: Float, dt: Float): Float =
        if (v < target) min(target, v + rate * dt) else max(target, v - rate * dt)
    private fun Node.span(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, r: Float) {
        val dx = x1 - x0; val dy = y1 - y0; val dz = z1 - z0
        val l = sqrt(dx * dx + dy * dy + dz * dz)
        at((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2)
        dims(r, max(l, 1e-4f), r)
        turn(if (l < 1e-6f) Q.identity() else Q.fromTo(0f, 1f, 0f, dx, dy, dz))
    }

    private fun md(m: VoxelMesher.Mesh) = MeshData(m.pos, m.nrm, m.uv, m.idx)
    private fun voxMesh(g: VoxelGrid, sz: Float = 1f) = md(VoxelMesher.build(g, 1f, 1f, sz))

    /** 8 x 8 tiles (see VoxelMesher): tile = edge mask + 16 * shade. Bright body, glowing true edges, deeper = darker. */
    private fun bakeEdgeTex(): Image {
        val t = 32; val img = Image(8 * t, 8 * t)
        val shadeK = floatArrayOf(1.0f, 0.80f, 0.62f, 0.46f)
        for (tile in 0 until 64) {
            val mask = tile % 16; val sh = tile / 16
            val tx = tile % 8; val ty = tile / 8
            for (py in 0 until t) for (px in 0 until t) {
                val u = (px + 0.5f) / t; val v = (py + 0.5f) / t
                var d = 9f
                if (mask and 1 != 0) d = min(d, u)
                if (mask and 2 != 0) d = min(d, 1f - u)
                if (mask and 4 != 0) d = min(d, v)
                if (mask and 8 != 0) d = min(d, 1f - v)
                val line = ((0.14f - d) / 0.09f).coerceIn(0f, 1f)
                val c = (0.80f + 0.20f * line * line * (3 - 2 * line)) * shadeK[sh] + 0.25f * line * (1f - shadeK[sh])
                img.px[(ty * t + py) * img.w + tx * t + px] = ProcTex.argb(1f, c, c, c)
            }
        }
        return img
    }

    private fun tetraMeshOf(): MeshData {
        val pos = ArrayList<Float>(); val nrm = ArrayList<Float>(); val uv = ArrayList<Float>(); val idx = ArrayList<Int>()
        val v = SierpinskiTetra.VERTS
        val faces = arrayOf(intArrayOf(0, 1, 2), intArrayOf(0, 2, 3), intArrayOf(0, 3, 1), intArrayOf(1, 3, 2))
        for (p in SierpinskiTetra.pieces(1)) for (f in faces) {
            val a = FloatArray(9) { k -> (p[k % 3] + v[f[k / 3]][k % 3] * 0.5).toFloat() }
            val ux = a[3] - a[0]; val uy = a[4] - a[1]; val uz = a[5] - a[2]; val wx = a[6] - a[0]; val wy = a[7] - a[1]; val wz = a[8] - a[2]
            var nx = uy * wz - uz * wy; var ny = uz * wx - ux * wz; var nz = ux * wy - uy * wx
            // outward = away from the piece centre
            val cx = (a[0] + a[3] + a[6]) / 3 - p[0].toFloat(); val cy = (a[1] + a[4] + a[7]) / 3 - p[1].toFloat(); val cz = (a[2] + a[5] + a[8]) / 3 - p[2].toFloat()
            val flip = nx * cx + ny * cy + nz * cz < 0
            val l = sqrt(nx * nx + ny * ny + nz * nz); nx /= l; ny /= l; nz /= l
            if (flip) { nx = -nx; ny = -ny; nz = -nz }
            val order = if (flip) intArrayOf(0, 2, 1) else intArrayOf(0, 1, 2)
            for (o in order) { pos += a[o * 3]; pos += a[o * 3 + 1]; pos += a[o * 3 + 2]; nrm += nx; nrm += ny; nrm += nz; uv += 0.0625f; uv += 0.0625f; idx += pos.size / 3 - 1 }
        }
        return MeshData(pos.toFloatArray(), nrm.toFloatArray(), uv.toFloatArray(), idx.toIntArray())
    }

    // ================================================================ build

    override fun buildSpec(cacheDir: File): Node {
        resetState()
        val imgs = BakeCache.load(cacheDir, "fractal_text", TEX_VERSION) {
            listOf(FractalAtlas.bakeImage(ATLAS_ITEMS), ProcTex.bakePlanet(256, 128, Rgb(0.07f, 0.075f, 0.085f), Rgb(0.12f, 0.125f, 0.14f), Rgb(0.20f, 0.205f, 0.22f), 0f, 77))
        }
        atlas = FractalAtlas.of(imgs[0], ATLAS_ITEMS)
        edgeTex = bakeEdgeTex()
        cubeMesh = voxMesh(VoxelGrid.of(1, 1, 1) { _, _, _ -> true })
        m0 = voxMesh(Menger.grid(0, 3)); m1 = voxMesh(Menger.grid(1, 3)); m2 = voxMesh(Menger.grid(2, 9))
        h1 = voxMesh(Menger.carved(1, 3)); h2 = voxMesh(Menger.carved(2, 9))
        carpetMesh = voxMesh(Carpet.slab(1), 0.15f)
        tetraMesh = tetraMeshOf()
        ball = MeshGen.sphere(1f, 10, 16, MeshGen.SphereUv.EQUIRECT)
        cyl = MeshGen.cylinder(1f, 1f, 10)
        unitBox = MeshGen.box(1f, 1f, 1f)
        spongeMat = Array(4) { k -> Mat.Pbr(LEVEL_TINT[k], metallic = 0.1f, roughness = 0.38f, tex = edgeTex, emissive = glow(LEVEL_TINT[k], 0.30f)) }
        debrisMat = Array(4) { k -> Array(FADE_STEPS + 1) { s -> val a = s / FADE_STEPS.toFloat(); Mat.Unlit(Rgb(LEVEL_TINT[k].r * a, LEVEL_TINT[k].g * a, LEVEL_TINT[k].b * a), tex = edgeTex, blend = Blend.ADD) } }

        val root = Node("FractalRoot")
        buildBench(root, imgs[1])
        groups = Array(2) { k -> root.add(Node("St${k + 1}")) }
        build1(groups[0]); build2(groups[1])
        buildTokens(root)
        update(0f)
        return root
    }

    private fun buildBench(root: Node, slabTex: Image) {
        root.add("Plinth", MeshGen.box(1.74f, 0.035f, 0.92f), Mat.Pbr(Rgb(1f, 1f, 1f), metallic = 0f, roughness = 0.55f, tex = slabTex), P3(0f, PLINTH_Y, -0.04f))
        root.add("PlinthBand", MeshGen.box(1.75f, 0.008f, 0.93f), alu, P3(0f, PLINTH_Y, -0.04f))
        val pool = OptGeo.lumTex(64, 64) { u, v -> val dx = (u - 0.5f) * 2f; val dy = (v - 0.5f) * 2f; val r2 = dx * dx + dy * dy; if (r2 >= 1f) 0f else (1f - r2) * (1f - r2) }
        poolGlow = root.add("Glow", OptGeo.quad(1f, 1f), Mat.Unlit(ICE, alpha = 0.22f, tex = pool, blend = Blend.ADD)).also {
            it.rot = Q.axisAngle(1f, 0f, 0f, -PI.toFloat() / 2); it.scale = P3(0.80f, 0.70f, 1f); it.pos = P3(C.x, PLINTH_Y + 0.019f, C.z)
        }
    }

    // ---------------------------------------------------------------- ① the sponge
    private fun build1(g: Node) {
        spongeRoot = g.add(Node("Sponge")).also { it.pos = C; it.rot = Q.axisAngle(0f, 1f, 0f, YAW * PI.toFloat() / 180f) }
        zoomNode = spongeRoot.add(Node("Zoom")).also { it.pos = P3(-S / 2, -S / 2, S / 2) }
        body = zoomNode.add(Node("Body")).also { it.pos = P3(S / 2, S / 2, -S / 2); it.uniformScale(S) }
        lvl0 = body.add("L0", m0, spongeMat[0])
        copies = Array(20) { i -> val c = Menger.COPIES[i]
            body.add("Q_$i", m2, spongeMat[3], P3((c[0] - 1) / 3f, (c[1] - 1) / 3f, (c[2] - 1) / 3f)).also { it.uniformScale(1f / 3f) } }
        chunks = Array(20) { i -> body.add("Chunk_$i", h2, debrisMat[0][0]).also { it.uniformScale(1f / 3f); it.visible = false } }
        debris = Array(7) { j -> body.add("Deb_$j", cubeMesh, debrisMat[0][0]).also { it.uniformScale(1f / 3f); it.visible = false } }
        counter = decal(g, "Counter", counterKey(3), 0.040f, GOLD, P3(C.x, 0.30f, C.z + 0.12f))
        // controls column: carve (a little level-1 sponge), un-carve (a little cube), zoom rail + knob
        nextBtn = g.add("NextBtn", m1, spongeMat[3], P3(CTRL_X, NEXT_Y, 0.04f)).also { it.uniformScale(0.058f); it.tapRadius = 0.05f / 0.058f }
        prevBtn = g.add("PrevBtn", m0, spongeMat[0], P3(CTRL_X, PREV_Y, 0.04f)).also { it.uniformScale(0.042f); it.tapRadius = 0.048f / 0.042f }
        g.add("ZoomRail", cyl, alu).span(CTRL_X, RAIL_Y0 - 0.012f, 0.04f, CTRL_X, RAIL_Y1 + 0.012f, 0.04f, 0.0032f)
        zoomKnob = g.add("ZoomKnob", ball, solid(ICE, 0.45f, 0.22f)).also { it.uniformScale(0.017f); it.tapRadius = 0.05f / 0.017f }
        decal(g, "ZoomT1", "×1", 0.022f, DIM, P3(CTRL_X + 0.022f + decalWidth("×1", 0.022f) / 2, RAIL_Y0, 0.04f))
        decal(g, "ZoomT3", "×3", 0.022f, DIM, P3(CTRL_X + 0.022f + decalWidth("×3", 0.022f) / 2, RAIL_Y1, 0.04f))
        // chart: V_n (cyan) and A_n (orange) for n = 0..4; n = 4 is computed, never drawn as a sponge
        vMat = arrayOf(Mat.Pbr(CYAN, roughness = 0.4f, alpha = 0.30f, emissive = glow(CYAN, 0.15f)), solid(CYAN, 0.30f), solid(CYAN, 0.75f, 0.25f))
        aMat = arrayOf(Mat.Pbr(ORANGE, roughness = 0.4f, alpha = 0.30f, emissive = glow(ORANGE, 0.15f)), solid(ORANGE, 0.30f), solid(ORANGE, 0.75f, 0.25f))
        vBars = Array(5) { n -> g.add("VBar_$n", unitBox, vMat[1]) }
        aBars = Array(5) { n -> g.add("ABar_$n", unitBox, aMat[1]) }
        g.add("ChartBase", unitBox, Mat.Pbr(Rgb(0.09f, 0.10f, 0.13f), metallic = 0.4f, roughness = 0.35f), P3(CH_X0 + 2 * CH_DX, CH_Y - 0.004f, 0f)).also { it.scale = P3(5 * CH_DX + 0.02f, 0.008f, 0.06f) }
        for (n in 0..4) decal(g, "ChN_$n", "$n", 0.024f, DIM, P3(CH_X0 + n * CH_DX, CH_Y - 0.026f, 0.035f))
        decal(g, "ChNn", "n", 0.024f, DIM, P3(CH_X0 - 0.055f, CH_Y - 0.026f, 0.035f))
        vHead = decal(g, "VHead", vKey(3), 0.024f, CYAN)
        aHead = decal(g, "AHead", aKey(3), 0.024f, ORANGE)
    }

    // ---------------------------------------------------------------- ② dimension by counting
    private fun build2(g: Node) {
        exNode = Array(6) { i -> g.add(Node("Ex$i")).also { it.pos = P3(EX_X[i], EX_Y, 0.02f); it.tapRadius = 0.09f } }
        exRot = Array(6) { i -> exNode[i].add(Node("ExRot$i")) }
        exMat = Array(6) { i -> val c = EX_COL[i]; val tex = if (i == 3) null else edgeTex
            arrayOf(Mat.Pbr(c, metallic = 0.1f, roughness = 0.40f, tex = tex, emissive = glow(c, 0.18f)),
                Mat.Pbr(c, metallic = 0.1f, roughness = 0.34f, tex = tex, emissive = glow(c, 0.45f)),
                Mat.Pbr(WHITE, metallic = 0f, roughness = 0.3f, tex = tex, emissive = Rgb(0.85f, 0.85f, 0.85f))) }
        val third = L / 3
        val th = 0.15f * third                                         // slab thickness (line, square, carpet)
        exBase = Array(6) { i -> when (i) {
            0 -> Array(3) { a -> P3((a - 1) * third, 0f, 0f) }
            1 -> (0 until 9).filter { it != 4 }.map { k -> P3((k % 3 - 1) * third, (k / 3 - 1) * third, 0f) }.toTypedArray()
            2 -> Array(9) { k -> P3((k % 3 - 1) * third, (k / 3 - 1) * third, 0f) }
            3 -> Array(4) { v -> val p = SierpinskiTetra.VERTS[v]; val e = L * 1.25f / 2; P3(p[0].toFloat() * e, p[1].toFloat() * e, p[2].toFloat() * e) }
            4 -> Array(20) { k -> val c = Menger.COPIES[k]; P3((c[0] - 1) * third, (c[1] - 1) * third, (c[2] - 1) * third) }
            else -> Array(27) { k -> P3((k % 3 - 1) * third, (k / 3 % 3 - 1) * third, (k / 9 - 1) * third) }
        } }
        exCopies = Array(6) { i -> Array(exBase[i].size) { k ->
            val (mesh, sc) = when (i) {
                0 -> cubeMesh to P3(third * 0.97f, th, th)
                1 -> carpetMesh to P3(third, third, third)
                2 -> cubeMesh to P3(third, third, th)
                3 -> tetraMesh to P3(L * 1.25f / 2, L * 1.25f / 2, L * 1.25f / 2)
                4 -> m1 to P3(third, third, third)
                else -> cubeMesh to P3(third, third, third)
            }
            exRot[i].add("ExC_${i}_$k", mesh, exMat[i][0], exBase[i][k]).also { it.scale = sc }
        } }
        exNs = Array(6) { i -> decal(g, "ExNs$i", "N = ?", 0.026f, EX_COL[i], P3(EX_X[i], 0.030f, 0.06f)) }
        exD = Array(6) { i -> decal(g, "ExD$i", "D = ?", 0.028f, WHITE, P3(EX_X[i], -0.012f, 0.06f)) }
        exCount = Array(6) { i -> decal(g, "ExCnt$i", "1", 0.040f, WHITE, P3(EX_X[i], EX_Y + 0.13f, 0.10f)).also { it.visible = false } }
        decal(g, "Law", "N = s^D", 0.034f, GOLD, P3(-0.30f, 0.43f, -0.02f))
        decal(g, "Law2", "D = log N / log s", 0.034f, GOLD, P3(0.12f, 0.43f, -0.02f))
        // the D ruler
        g.add("Ruler", cyl, alu).span(rx(0.8), RULER_Y, RULER_Z, rx(3.2), RULER_Y, RULER_Z, 0.0030f)
        val tickMat = Mat.Unlit(Rgb(0.80f, 0.84f, 0.92f))
        for (k in 0..4) { val d = 1.0 + k * 0.5; val h = if (k % 2 == 0) 0.016f else 0.009f
            g.add("Tick$k", cyl, tickMat).span(rx(d), RULER_Y - h, RULER_Z, rx(d), RULER_Y + h, RULER_Z, 0.0016f) }
        for (k in 1..3) decal(g, "TickL$k", "$k", 0.026f, WHITE, P3(rx(k.toDouble()), RULER_Y - 0.036f, RULER_Z))
        decal(g, "RulerD", "D", 0.030f, GOLD, P3(rx(0.8) - 0.03f, RULER_Y, RULER_Z))
        markers = Array(6) { i -> g.add("Mark$i", ball, solid(EX_COL[i], 0.6f, 0.25f), P3(rx(EX_D[i]), RULER_Y + (if (i == 3) 0.024f else 0f), RULER_Z + 0.01f)).also { it.uniformScale(0.011f) } }
        leaders = Array(6) { i -> g.add("Lead$i", cyl, Mat.Unlit(glow(EX_COL[i], 0.55f), blend = Blend.ADD)).also {
            it.span(EX_X[i], -0.035f, 0.06f, rx(EX_D[i]), RULER_Y + 0.012f + (if (i == 3) 0.024f else 0f), RULER_Z + 0.01f, 0.0014f) } }
        dKnob = g.add("DKnob", ball, solid(GOLD, 0.55f, 0.22f)).also { it.uniformScale(0.017f); it.tapRadius = 0.05f / 0.017f }
        // live numbers under the ruler: "D = 2.500   3^D = 15.59"
        val ly = RULER_Y - 0.085f; val em = 0.030f
        val x0 = -0.42f
        decal(g, "DEq", "D =", em, GOLD, P3(x0 + decalWidth("D =", em) / 2, ly, RULER_Z))
        dRow = digitRow(g, "DDig", 5, em, GOLD, x0 + decalWidth("D =", em) + 0.004f, ly, RULER_Z)
        val x1 = x0 + 0.33f
        decal(g, "PowEq", "3^D =", em, WHITE, P3(x1 + decalWidth("3^D =", em) / 2, ly, RULER_Z))
        powRow = digitRow(g, "PDig", 5, em, WHITE, x1 + decalWidth("3^D =", em) + 0.004f, ly, RULER_Z)
    }

    // ---------------------------------------------------------------- shelves
    private fun buildTokens(root: Node) {
        val plateMesh = MeshGen.box(0.130f, 0.095f, 0.012f)
        tokens = Array(2) { k -> root.add(Node("Tok$k")).also { it.pos = P3(SHELF_X, TOK_Y[k], 0.02f); it.tapRadius = 0.06f } }
        plates = Array(2) { k -> tokens[k].add("Plate$k", plateMesh, plateOff, P3(0f, 0f, -0.02f)) }
        // ① a tiny level-1 sponge   ② a tiny ruler with three counted shapes
        tokens[0].add("TkSponge", m1, spongeMat[3], P3(0f, 0f, 0.012f)).also { it.uniformScale(0.058f); it.rot = Q.axisAngle(0f, 1f, 0f, -0.5f) * Q.axisAngle(1f, 0f, 0f, 0.35f) }
        for (k in 0..2) tokens[1].add("TkSeg$k", cubeMesh, exMat[0][1], P3(-0.040f + k * 0.0135f, 0.024f, 0f)).also { it.scale = P3(0.012f, 0.004f, 0.004f) }
        for (k in 0 until 9) tokens[1].add("TkSq$k", cubeMesh, exMat[2][1], P3(0.026f + (k % 3 - 1) * 0.011f, 0.020f + (k / 3 - 1) * 0.011f, 0f)).also { it.scale = P3(0.010f, 0.010f, 0.004f) }
        tokens[1].add("TkRule", cyl, alu).span(-0.05f, -0.028f, 0f, 0.05f, -0.028f, 0f, 0.002f)
        for (k in 0..2) tokens[1].add("TkMark$k", ball, solid(EX_COL[k * 2 + 1], 0.6f), P3(-0.04f + k * 0.04f, -0.028f, 0.004f)).also { it.uniformScale(0.006f) }
    }

    // ================================================================ per frame

    override fun update(dt: Float) {
        clock += dt
        for (s in 0 until 2) { groups[s].show(s == station); plates[s].setMat(if (s == station) plateOn else plateOff) }
        if (station == 0) update1(dt) else update2(dt)
        poolGlow.show(station == 0)
        poolGlow.setMat(Mat.Unlit(LEVEL_TINT[level], alpha = 0.20f, tex = (poolGlow.mat as Mat.Unlit).tex, blend = Blend.ADD))
        updateReadout()
        updateLabels()
    }

    private fun update1(dt: Float) {
        // ---- carving queue
        if (animDir == 0 && targetLevel != level) {
            if (targetLevel > level) { animDir = 1; animK = level + 1; level = animK } else { animDir = -1; animK = level }
            animT = 0f; prepDebris()
        }
        if (animDir != 0) {
            animT += dt * spd / CARVE_S
            if (animT >= 1f) {
                if (animDir < 0) level = animK - 1
                animDir = 0; animT = 0f
                hideDebris(); bump()
            }
        }
        if (level == 0) { zoom = 1f; diving = false }
        // ---- walk-in
        if (diving && level >= 1) {
            zoom *= kotlin.math.exp(dt * spd * 0.36f)
            if (zoom >= 3f) { zoom = 1f; dives++ }
        }
        if (zoom != zoomShown || level != levelShown) applyLevelAndZoom()
        if (animDir != 0) animateDebris()
        // ---- controls
        nextBtn.turn(Q.axisAngle(0f, 1f, 0f, clock * 0.6f) * Q.axisAngle(1f, 0f, 0f, 0.35f))
        nextBtn.setMat(spongeMat[min(3, level + 1)])
        prevBtn.turn(Q.axisAngle(0f, 1f, 0f, -clock * 0.4f + 0.5f) * Q.axisAngle(1f, 0f, 0f, 0.35f))
        prevBtn.setMat(spongeMat[max(0, level - 1)])
        zoomKnob.at(CTRL_X, RAIL_Y0 + (RAIL_Y1 - RAIL_Y0) * ((zoom - 1f) / 2f), 0.04f)
        // ---- chart: bars grow/shrink to their values; lit up to the current level
        for (n in 0..4) {
            val st = if (n > level || n == 4) 0 else if (n == level) 2 else 1
            val x = CH_X0 + n * CH_DX
            vBars[n].dims(0.028f, barV[n], 0.028f); vBars[n].at(x - 0.017f, CH_Y + barV[n] / 2, 0f); vBars[n].setMat(vMat[st])
            aBars[n].dims(0.028f, barA[n], 0.028f); aBars[n].at(x + 0.017f, CH_Y + barA[n] / 2, 0f); aBars[n].setMat(aMat[st])
        }
        setDecal(vHead, vKey(level), 0.024f); vHead.at(CH_X0 - 0.03f + decalWidth(vKey(level), 0.024f) / 2, CH_Y + CH_H + 0.105f, 0f)
        setDecal(aHead, aKey(level), 0.024f); aHead.at(CH_X0 - 0.03f + decalWidth(aKey(level), 0.024f) / 2, CH_Y + CH_H + 0.06f, 0f)
        setDecal(counter, counterKey(level), 0.040f)
        counter.show(zoom < 1.02f)
    }

    /** Put the right mesh / material on the sponge and clip the walk-in (only when the level or zoom changes). */
    private fun applyLevelAndZoom() {
        zoomNode.uniformScale(zoom)
        lvl0.show(level == 0)
        val sub = when (level) { 1 -> m0; 2 -> m1; else -> m2 }
        val rot = spongeRoot.rot
        for (i in 0 until 20) {
            val c = Menger.COPIES[i]
            // world AABB of copy i at this zoom: C + R (anchor + zoom (bodyPos + S·local))
            var ok = true
            for (corner in 0 until 8) {
                val lx = (c[0] + (corner and 1)) / 3f - 0.5f; val ly = (c[1] + (corner shr 1 and 1)) / 3f - 0.5f; val lz = (c[2] + (corner shr 2 and 1)) / 3f - 0.5f
                val p = rot.rotate(floatArrayOf(-S / 2 + zoom * (S / 2 + S * lx), -S / 2 + zoom * (S / 2 + S * ly), S / 2 + zoom * (-S / 2 + S * lz)))
                val w = floatArrayOf(C.x + p[0], C.y + p[1], C.z + p[2])
                for (a in 0..2) if (w[a] < CLIP_LO[a] || w[a] > CLIP_HI[a]) ok = false
            }
            clipOn[i] = ok
            val q = copies[i]
            if (q.mesh !== sub) q.setMesh(sub)
            q.setMat(spongeMat[level])
            q.show(level >= 1 && ok)
        }
        lvl0.setMat(spongeMat[0])
        zoomShown = zoom; levelShown = level
    }

    private fun prepDebris() {
        hideDebris()
        if (animK >= 2) { val m = if (animK == 2) h1 else h2; for (c in chunks) if (c.mesh !== m) c.setMesh(m) }
    }
    private fun hideDebris() { chunks.forEach { it.show(false) }; debris.forEach { it.show(false) } }

    // the 7 carved cubes of one cell: centre + 6 face centres (offsets in cells); the centre one leaves through the front
    private val CROSS = arrayOf(intArrayOf(0, 0, 0), intArrayOf(1, 0, 0), intArrayOf(-1, 0, 0), intArrayOf(0, 1, 0), intArrayOf(0, -1, 0), intArrayOf(0, 0, 1), intArrayOf(0, 0, -1))

    private fun animateDebris() {
        val t = if (animDir > 0) animT else 1f - animT                 // 0 = in place, 1 = gone
        val e = t * t * (3 - 2 * t)
        val fade = ((1f - t) * (1f - t) * FADE_STEPS).roundToInt().coerceIn(0, FADE_STEPS)
        val mat = debrisMat[animK - 1][fade]
        val dist = 0.55f * e                                          // unit-cube units (x S metres)
        val sc = 1f - 0.35f * e
        if (animK == 1) {
            for (j in 0 until 7) {
                val o = CROSS[j]; val n = debris[j]
                val dx = o[0].toFloat(); val dy = o[1].toFloat(); val dz = if (j == 0) 1f else o[2].toFloat()
                n.show(fade > 0)
                n.at(o[0] / 3f + dx * dist * 1.4f, o[1] / 3f + dy * dist * 1.4f, o[2] / 3f + dz * dist * 1.4f)
                n.uniformScale(sc / 3f); n.setMat(mat)
            }
        } else {
            for (i in 0 until 20) {
                val c = Menger.COPIES[i]; val n = chunks[i]
                val ox = (c[0] - 1) / 3f; val oy = (c[1] - 1) / 3f; val oz = (c[2] - 1) / 3f
                val l = sqrt(ox * ox + oy * oy + oz * oz).coerceAtLeast(1e-3f)
                n.show(fade > 0 && clipOn[i])
                n.at(ox + ox / l * dist, oy + oy / l * dist, oz + oz / l * dist)
                n.uniformScale(sc / 3f); n.setMat(mat)
            }
        }
    }

    private fun update2(dt: Float) {
        for (i in 0..5) {
            explode[i] = approach(explode[i], if (exploded[i]) 1f else 0f, 1.6f * spd, dt)
            val g = explode[i]; val e = g * g * (3 - 2 * g)
            // counting: one copy every COUNT_S seconds while exploded
            if (counting[i] >= 0 && counting[i] < exBase[i].size && g >= 0.98f) {
                countClock[i] += dt * spd
                while (countClock[i] >= COUNT_S && counting[i] < exBase[i].size) { countClock[i] -= COUNT_S; counting[i]++ }
                if (counting[i] >= exBase[i].size) { counted[i] = true; lastCounted = i; bump() }
            }
            val cnt = counting[i]
            for (k in exCopies[i].indices) {
                val b = exBase[i][k]; val f = 1f + 0.32f * e
                val n = exCopies[i][k]
                n.at(b.x * f, b.y * f, b.z * f)
                n.setMat(exMat[i][when { cnt < 0 -> 0; k == cnt - 1 && cnt < exBase[i].size -> 2; k < cnt -> 1; else -> 0 }])
            }
            // the 3-D exhibits turn slowly (tilted toward the viewer); the flat ones face the viewer
            val spin = when (i) { 3, 4, 5 -> clock * 0.30f * spd + i; else -> 0f }
            exRot[i].turn(if (i >= 3) Q.axisAngle(1f, 0f, 0f, 0.38f) * Q.axisAngle(0f, 1f, 0f, spin) else Q.identity())
            exCount[i].show(cnt > 0 && exploded[i])
            if (cnt > 0) setDecal(exCount[i], "$cnt", 0.040f)
            setDecal(exNs[i], if (counted[i]) nsKey(i) else "N = ?", 0.026f)
            setDecal(exD[i], if (counted[i]) dKey(i) else "D = ?", 0.028f)
            markers[i].show(counted[i]); leaders[i].show(counted[i])
        }
        dKnob.at(rx(dUser), RULER_Y + 0.026f, RULER_Z + 0.02f)
        dRow.set(f3(dUser))
        powRow.set(f2(3.0.pow(dUser)))
    }

    private fun exName(i: Int) = when (i) {
        0 -> tr("线段", "line"); 1 -> tr("谢尔宾斯基地毯", "Sierpiński carpet"); 2 -> tr("正方形", "square")
        3 -> tr("谢尔宾斯基四面体", "Sierpiński tetrahedron"); 4 -> tr("门格海绵", "Menger sponge"); else -> tr("立方体", "cube")
    }
    private fun exShort(i: Int) = when (i) {
        0 -> tr("线段", "Line"); 1 -> tr("地毯", "Carpet"); 2 -> tr("正方形", "Square"); 3 -> tr("四面体", "Tetrahedron"); 4 -> tr("海绵", "Sponge"); else -> tr("立方体", "Cube")
    }

    private fun updateReadout() {
        val n = level
        val sup = "⁰¹²³"[n]
        val vol = if (n == 0) "100" else f1(Menger.volume(n) * 100)
        val l1 = if (station == 0) tr("第 $n 层 · 小方块 20$sup = ${Menger.cubes(n)} · 体积 (20/27)$sup = $vol % · 表面积 ${areaKey(n)}（原来 6）",
            "Level $n · cubes 20$sup = ${Menger.cubes(n)} · volume (20/27)$sup = $vol % · surface area ${areaKey(n)} (started at 6)")
        else tr("② 缩小到 1/s 得 N 份：N = s^D → D = log N / log s（线 1 · 正方形 2 · 立方体 3）",
            "② Scale 1/s gives N copies: N = s^D → D = log N / log s (line 1 · square 2 · cube 3)")
        val l2 = if (station == 0) tr("维数：缩小到 1/3 得 20 份 → 3^D = 20 → D = ln20/ln3 ≈ ${f3(Fractal.D_MENGER)}（面 2 < D < 体 3）",
            "Dimension: scale 1/3 gives 20 copies → 3^D = 20 → D = ln 20/ln 3 ≈ ${f3(Fractal.D_MENGER)} (surface 2 < D < solid 3)")
        else tr("你的 D = ${f3(dUser)} → 3^D = ${f2(3.0.pow(dUser))}（门格海绵：缩小 1/3 得 20 份）",
            "Your D = ${f3(dUser)} → 3^D = ${f2(3.0.pow(dUser))} (Menger sponge: 20 copies at scale 1/3)")
        val deep = if (dives > 0) tr("（已钻深 $dives 层）", " ($dives levels deeper)") else ""
        val l3 = when {
            station == 1 -> when (lastCounted) {
                1 -> tr("地毯：8 份，3¹ < 8 < 3² → D = ln8/ln3 ≈ ${f3(Fractal.D_CARPET)}：比线多，比面少", "Carpet: 8 copies, 3¹ < 8 < 3² → D = ln 8/ln 3 ≈ ${f3(Fractal.D_CARPET)}: more than a line, less than a surface")
                3 -> tr("四面体：缩小 1/2 得 4 份，2^D = 4 → D = 2：一个分形，维数却恰好是整数", "Tetrahedron: scale 1/2 gives 4 copies, 2^D = 4 → D = 2: a fractal whose dimension is a whole number")
                4 -> tr("海绵：20 份，9 = 3² < 20 < 27 = 3³ → 维数在 2 与 3 之间", "Sponge: 20 copies, 9 = 3² < 20 < 27 = 3³ → its dimension lies between 2 and 3")
                else -> tr("捏一件展品：它散成缩小的复制品，一个个数出来，维数落到下面的尺子上", "Pinch an exhibit: it splits into scaled copies, counts them, and drops its D on the ruler")
            }
            animDir > 0 -> tr("挖第 $animK 层：每个方块切成 27 块，挖掉中心和 6 个面心，留下 20 块", "Carving level $animK: every cube is cut into 27, the centre and 6 face centres go, 20 stay")
            zoom > 1.01f -> tr("放大 ×${f1(zoom.toDouble())}$deep：隧道壁上又有隧道 —— 角上一块和整体一样（自相似）",
                "Zoom ×${f1(zoom.toDouble())}$deep: tunnels in the tunnel walls — the corner piece looks like the whole (self-similar)")
            else -> tr("同一条规则一直重复：体积 → 0，表面积 → ∞（第 n 层 = 20 个缩小 1/3 的第 n−1 层）",
                "Repeat one rule forever: volume → 0, surface area → ∞ (level n = 20 copies of level n−1 at 1/3)")
        }
        readoutText = "$l1\n$l2\n$l3"
    }

    private fun updateLabels() {
        labels.clear()
        if (station == 0) {
            labels += LabelSpec(tr("挖下一层", "carve next level"), P3(CTRL_X, NEXT_Y + 0.065f, 0.06f))
            labels += LabelSpec(tr("退一层", "undo a level"), P3(CTRL_X, PREV_Y + 0.055f, 0.06f))
            labels += LabelSpec(tr("放大 · 钻进去", "zoom · dive in"), P3(CTRL_X, RAIL_Y1 + 0.045f, 0.06f))
            labels += LabelSpec(tr("体积 → 0 · 表面积 → ∞", "volume → 0 · area → ∞"), P3(CH_X0 + 2 * CH_DX, CH_Y + CH_H + 0.165f, 0.02f))
            if (zoom < 1.02f) labels += LabelSpec(tr("门格海绵 · 第 $level 层", "Menger sponge · level $level"), P3(C.x, 0.37f, C.z + 0.12f))
        } else {
            for (i in 0..5) labels += LabelSpec(exShort(i), P3(EX_X[i], EX_Y + 0.195f, 0.04f))
        }
    }

    // ================================================================ interaction

    private val TOK_NAMES = listOf("Tok0", "Tok1")
    private fun stationTargets(s: Int): List<String> = if (s == 0) listOf("NextBtn", "PrevBtn", "ZoomKnob") else listOf("Ex0", "Ex1", "Ex2", "Ex3", "Ex4", "Ex5", "DKnob")

    override val tapTargets: List<TapTarget> get() {
        val t = ArrayList<TapTarget>()
        if (station == 0) {
            t += TapTarget("NextBtn", "小海绵 → 挖下一层", "Little sponge → carve")
            t += TapTarget("PrevBtn", "小方块 → 退一层", "Little cube → undo")
            t += TapTarget("ZoomKnob", "放大把手 → 钻进去", "Zoom knob → dive in") { diving }
        } else {
            for (i in 0..5) t += TapTarget("Ex$i", exShortZh(i) + " → 数", exShortEn(i) + " → count") { exploded[i] }
            t += TapTarget("DKnob", "D 把手 → +0.25", "D knob → +0.25")
        }
        t += TapTarget("Tok0", "① 门格海绵", "① Sponge") { station == 0 }
        t += TapTarget("Tok1", "② 数维数", "② Dimension") { station == 1 }
        return t
    }
    private fun exShortZh(i: Int) = arrayOf("线段", "地毯", "正方形", "四面体", "海绵", "立方体")[i]  // i18n-ok (zh half of a TapTarget pair)
    private fun exShortEn(i: Int) = arrayOf("Line", "Carpet", "Square", "Tetra", "Sponge", "Cube")[i]

    override fun isTarget(name: String) = name in stationTargets(station) || name in TOK_NAMES

    override fun onTap(name: String) {
        when (name) {
            "Tok0" -> selectStation(0)
            "Tok1" -> selectStation(1)
            "NextBtn" -> carveTo(targetLevel + 1)
            "PrevBtn" -> carveTo(targetLevel - 1)
            "ZoomKnob" -> diveTo(!diving)
            "DKnob" -> setD(if (dUser >= 2.999) 1.0 else min(3.0, (dUser / 0.25 + 1e-6).toInt() * 0.25 + 0.25))
            else -> if (name.startsWith("Ex")) name.drop(2).toIntOrNull()?.let { tapExhibit(it) }
        }
    }

    override fun tapMessage(name: String): String? = when (name) {
        "Tok0" -> tr("① 门格海绵：挖一层，体积 ×20/27", "① Menger sponge: each level keeps 20/27 of the volume")
        "Tok1" -> tr("② 数复制品，算维数", "② Count the copies, get the dimension")
        "NextBtn" -> if (targetLevel >= 3 && level >= 3) tr("已到第 3 层（8000 块）；第 4 层要 160000 块", "Level 3 already (8000 cubes); level 4 would need 160 000")
                     else tr("挖到第 $targetLevel 层：方块 ${Menger.cubes(targetLevel)} 个", "Carving to level $targetLevel: ${Menger.cubes(targetLevel)} cubes")
        "PrevBtn" -> tr("退回第 $targetLevel 层", "Back to level $targetLevel")
        "ZoomKnob" -> if (level == 0) tr("先挖一层：实心方块放大还是方块", "Carve a level first: a solid cube just looks like a cube")
                      else if (diving) tr("钻进去：放大 3 倍时，角上一块 = 整个海绵", "Diving in: at ×3 the corner piece = the whole sponge") else tr("停在 ×${f1(zoom.toDouble())}", "Stopped at ×${f1(zoom.toDouble())}")
        "DKnob" -> tr("D = ${f3(dUser)} → 3^D = ${f2(3.0.pow(dUser))}", "D = ${f3(dUser)} → 3^D = ${f2(3.0.pow(dUser))}")
        else -> if (name.startsWith("Ex")) name.drop(2).toIntOrNull()?.let { i ->
            if (exploded[i]) tr("${exName(i)}：缩小 1/${EX_S[i]}，数一数有几份", "${exName(i)}: scaled by 1/${EX_S[i]} — count the copies")
            else tr("${exName(i)}：${EX_N[i]} 份 → ${dKey(i)}", "${exName(i)}: ${EX_N[i]} copies → ${dKey(i)}")
        } else null
    }

    override fun isDragTarget(name: String) = (station == 0 && name == "ZoomKnob") || (station == 1 && name == "DKnob")

    override fun onDragStart(name: String) { dragAcc = 0f; dragZ0 = zoom; dragD0 = dUser; if (name == "ZoomKnob") diving = false }

    override fun onDrag(name: String, dx: Float, dy: Float, dz: Float) {
        when (name) {
            "ZoomKnob" -> { dragAcc += dy; zoomTo(dragZ0 + dragAcc / (RAIL_Y1 - RAIL_Y0) * 2f) }
            "DKnob" -> { dragAcc += dx; setD(dragD0 + dragAcc / 0.52) }
        }
    }

    override fun onDragEnd(name: String) { bump() }

    // ---- actions (panel, lesson autoplay and tests call these)
    fun selectStation(s: Int) { station = s.coerceIn(0, 1); bump() }
    fun carveTo(n: Int) { targetLevel = n.coerceIn(0, 3); bump() }
    /** Jump without animation (lesson resets). */
    fun levelNow(n: Int) { level = n.coerceIn(0, 3); targetLevel = level; animDir = 0; animT = 0f; hideDebris(); bump() }
    fun zoomTo(z: Float) { zoom = if (level == 0) 1f else z.coerceIn(1f, 3f); bump() }
    fun diveTo(on: Boolean) { diving = on && level >= 1; bump() }
    fun setD(d: Double) { dUser = (d * 1000).roundToInt().coerceIn(1000, 3000) / 1000.0; bump() }
    fun tapExhibit(i: Int) {
        if (!exploded[i]) { exploded[i] = true; counting[i] = 0; countClock[i] = 0f }
        else if (counting[i] >= exBase[i].size) { exploded[i] = false; counting[i] = -1 }
        bump()
    }
    fun countAll() { for (i in 0..5) if (!exploded[i]) tapExhibit(i); bump() }
    fun resetAll() {
        if (station == 0) { levelNow(0); zoomTo(1f); diving = false }
        else { exploded.fill(false); counting.fill(-1); counted.fill(false); counted[0] = true; counted[2] = true; counted[5] = true; lastCounted = -1; setD(2.0) }
        bump()
    }
    val isIdle get() = animDir == 0 && level == targetLevel

    // ================================================================ guided track (导学)

    private var baseDives = 0

    override val lesson: Lesson by lazy {
        val v3 = f1(Menger.volume(3) * 100); val a3 = f2(Menger.area(3))
        Lesson(
            listOf(
                LessonStep(
                    say = Bi("① 规则：把方块切成 27 块，挖掉中心和 6 个面心。一直挖下去，海绵的体积会怎样？", "① The rule: cut the cube into 27, remove the centre and the 6 face centres. Keep going forever: what happens to the volume?"),
                    options = listOf(Bi("趋于 0", "It tends to 0"), Bi("停在某个正数", "It levels off above 0"), Bi("不变", "It stays the same")),
                    answer = 0,
                    onEnter = { selectStation(0); levelNow(0); zoomTo(1f); diveTo(false) },
                ),
                LessonStep(
                    say = Bi("捏旁边旋转的金色小海绵（或点面板「第 3 层」），一层层挖到第 3 层。", "Pinch the little spinning gold sponge (or tap 'Level 3' on the panel) to carve level by level up to level 3."),
                    target = "NextBtn",
                    onEnter = { selectStation(0) },
                    done = { station == 0 && level == 3 && isIdle },
                    autoplay = { selectStation(0); carveTo(3) },
                    reveal = true,
                    insight = Bi("每挖一层只留 20/27 ≈ 74 %。第 3 层：8000 块，每块 1/27 边长，体积 (20/27)³ = 8000/19683 ≈ $v3 %；第 10 层只剩 5 %，一直乘下去 → 0。", "Each level keeps 20/27 ≈ 74 %. Level 3: 8000 cubes of side 1/27, volume (20/27)³ = 8000/19683 ≈ $v3 %; level 10 keeps 5 %, and multiplying on → 0."),
                ),
                LessonStep(
                    say = Bi("体积越来越小。那表面积呢？", "The volume keeps shrinking. What about the surface area?"),
                    options = listOf(Bi("也越来越小", "It shrinks too"), Bi("基本不变", "About the same"), Bi("越来越大，趋于无穷", "It grows without bound")),
                    answer = 2,
                    onEnter = { selectStation(0) },
                ),
                LessonStep(
                    say = Bi("往上拖放大把手（或打开「钻进去」），钻进海绵的隧道，一直放大到 ×3。", "Drag the zoom knob up (or switch on 'Dive in') to fly into the tunnels, all the way to ×3."),
                    target = "ZoomKnob",
                    onEnter = { selectStation(0); baseDives = dives },
                    done = { station == 0 && level >= 1 && (dives > baseDives || zoom >= 2.95f) },
                    autoplay = { selectStation(0); if (level == 0) levelNow(3); diveTo(true) },
                    reveal = true,
                    insight = Bi("一个方块挖掉 7 块：外表少了 6 个小面，隧道里却多出 24 个 —— 面越挖越多：A₃ = 2(20/9)³ + 4(8/9)³ ≈ $a3，已是原来 6 的 4 倍，每层约 ×20/9 → ∞。放大 3 倍，角上一块和整个海绵一模一样 —— 隧道壁上永远还有隧道。", "Carving one cube removes 6 small outer faces but opens 24 inside the tunnels — every carve adds surface: A₃ = 2(20/9)³ + 4(8/9)³ ≈ $a3, 4 times the original 6, growing about ×20/9 per level → ∞. Zoom ×3 and the corner piece is the whole sponge again — there are always tunnels in the tunnel walls."),
                ),
                LessonStep(
                    say = Bi("② 维数靠数：捏门格海绵模型，它散成缩小 1/3 的小海绵 —— 数一数有几块？", "② Dimension by counting: pinch the Menger sponge exhibit; it splits into copies at scale 1/3 — how many are there?"),
                    target = "Ex4",
                    onEnter = { selectStation(1) },
                    done = { station == 1 && counted[4] },
                    autoplay = { selectStation(1); if (!exploded[4]) tapExhibit(4) },
                    insight = Bi("线段缩小 1/3 得 3 = 3¹ 份，正方形 9 = 3²，立方体 27 = 3³：份数 N = 3^D，D 就是维数。海绵得 20 份：9 < 20 < 27，所以 D 在 2 和 3 之间。", "A line at scale 1/3 gives 3 = 3¹ copies, a square 9 = 3², a cube 27 = 3³: N = 3^D, and D is the dimension. The sponge gives 20: 9 < 20 < 27, so D lies between 2 and 3."),
                ),
            ),
            challenge = Challenge(
                say = Bi("挑战：算出门格海绵的维数 —— 拖动尺子上的 D 把手（或面板滑块），让 3^D = 20（D 误差 ±0.01）。", "Challenge: find the Menger sponge's dimension — slide the D knob on the ruler (or the panel slider) until 3^D = 20 (D within ±0.01)."),
                progress = { tr("D = ${f3(dUser)} → 3^D = ${f2(3.0.pow(dUser))}（目标 20）", "D = ${f3(dUser)} → 3^D = ${f2(3.0.pow(dUser))} (goal: 20)") },
                done = { station == 1 && abs(dUser - Fractal.D_MENGER) <= 0.01 },
                autoplay = { selectStation(1); setD(ln(20.0) / ln(3.0)) },
                onEnter = { selectStation(1); setD(2.0) },
            ),
        )
    }

    // ================================================================ review states

    override fun reviewVariants(): Map<String, () -> Unit> = mapOf(
        "level1" to { selectStation(0); speed = 1; levelNow(1) },
        "carving" to { selectStation(0); speed = 0; levelNow(2); carveTo(3) },
        "zoom" to { selectStation(0); speed = 1; levelNow(3); zoomTo(2.2f) },
        "counted" to { selectStation(1); speed = 2; countAll(); repeat(600) { update(1f / 60f) }; setD(2.727) },
        "sponge_open" to { selectStation(1); speed = 1; resetAll(); repeat(60) { update(1f / 60f) }; tapExhibit(4); repeat(150) { update(1f / 60f) } },
    )

    // ================================================================ panel

    @Composable
    override fun Panel() {
        @Suppress("UNUSED_VARIABLE") val epoch = panelEpoch
        LabPanel(readout) {
            LabRow {
                SegmentControl {
                    SegmentItem(selected = speed == 0, onClick = { speed = 0; bump() }, title = { Text(tr("慢", "Slow")) })
                    SegmentItem(selected = speed == 1, onClick = { speed = 1; bump() }, title = { Text(tr("中", "Medium")) })
                    SegmentItem(selected = speed == 2, onClick = { speed = 2; bump() }, title = { Text(tr("快", "Fast")) })
                }
                if (station == 0) {
                    SegmentControl {
                        for (n in 0..3) SegmentItem(selected = targetLevel == n, onClick = { carveTo(n) }, title = { Text(tr("第 $n 层", "Level $n")) })
                    }
                    LabSwitch(tr("钻进去", "Dive in"), diving) { diveTo(it) }
                } else {
                    Button(onClick = { countAll() }) { Text(tr("全部数一遍", "Count all")) }
                }
                Button(onClick = { resetAll() }) { Text(tr("重置", "Reset")) }
            }
            LabRow {
                if (station == 0) LabSlider(tr("放大", "Zoom"), zoom, 1f..3f, "×" + f1(zoom.toDouble()), width = 420) { diveTo(false); zoomTo(it) }
                else LabSlider(tr("维数 D", "Dimension D"), dUser.toFloat(), 1f..3f, f3(dUser) + " → " + f2(3.0.pow(dUser)), width = 420) { setD(it.toDouble()) }
            }
        }
    }
}
