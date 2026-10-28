// 指数增长 · Exponential Growth (Sissa's chessboard; Bernoulli 1683).
//
// THE AHA: doubling is gentle, then it explodes — and our linear intuition never sees it coming. On a LOG
// axis the explosion is a straight line.
//   ① Rice on a chessboard, TO SCALE (6.25 cm squares, 25 mg grains, heap density 0.8 g/cm³ -> 8 µm of
//      column per grain, core/exponential/Rice). Squares 1-8 are real grains (255 pooled nodes) piled as small
//      bricks, 9-13 solid cubes of rice, from 14 on columns on the full square. Square 16 (26 cm) passes a hand,
//      17 is 52 cm, 18 (1 m) already leaves the volume: tall columns are drawn CLIPPED under a glowing ceiling
//      with an arrow, the true height and the landmark it passed (person, tower, Everest, Moon, Sun) in the
//      readout. Flip the scale to LOG: every column rises by the same step (×2) and the board becomes a
//      neat straight ramp; the landmarks become evenly spaced levels.
//   ② The race n, n², 2ⁿ (or n³): linear axis auto-zooms to the leader, so n² flattens into the floor; the
//      crossing (ties at 2 and 4, 2ⁿ ahead for good from n = 5) is marked once reached.
//   ③ Compounding: 100 %/yr paid as n instalments — a staircase of interest layers over the year, the
//      year-end stacks for n = 1, 2, 4, 12, 365, ∞ climbing to the glowing e line: (1 + 1/n)ⁿ → e.
//   ④ Spread: a branching tree with R0 new cases per case; R0 < 1 dies out (total 1/(1 − R0)).
//
// Everything moves by NODE TRANSFORMS (unit meshes, pooled grains / cases / layers); the only texture is
// one text atlas baked once (ExpText + BakeCache). No mesh is rebuilt after buildSpec.

package com.bytedance.mathlab.demos.exponential

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.bytedance.mathlab.core.P3
import com.bytedance.mathlab.core.exponential.Chessboard
import com.bytedance.mathlab.core.exponential.Compound
import com.bytedance.mathlab.core.exponential.Race
import com.bytedance.mathlab.core.exponential.Rice
import com.bytedance.mathlab.core.exponential.Spread
import com.bytedance.mathlab.lab.BakeCache
import com.bytedance.mathlab.lab.About
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
import com.bytedance.mathlab.lab.ui.FieldRef
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
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.util.Locale
import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

object ExponentialDemo : SpecDemo() {
    override val id = "exponential"
    override val title = "指数增长"  // i18n-ok (zh half of DemoRegistry.titleOf; English in TITLE_EN)
    override val credit = "Sissa's chessboard · Bernoulli 1683"

    // ================================================================ layout (volume metres)
    private const val PLINTH_Y = -0.262f
    // ① the board, rank 1 (squares 1-8) nearest the viewer, files a-h left to right
    private val SQ = Rice.SQUARE_M.toFloat()            // 6.25 cm: the real scale of the rice model
    private const val BX = -0.14f                       // board centre x
    private const val BZ = -0.04f                       // board centre z
    private const val Y_TOP = -0.211f                   // board top surface
    private const val BOARD_T = 0.032f
    private const val H_CLIP = 0.56f                    // true-scale columns are drawn up to here (then clipped)
    private const val K_LOG = 0.0085f                   // LOG mode: metres per doubling (63 doublings = 0.54 m)
    private const val LOG_BASE = 0.004f
    private const val COL_W = 0.90f                     // drawn column footprint / square (gaps between columns)
    private const val X_RAIL = -0.455f                  // the n rail (= the log axis) left of the board
    private const val X_LINRULER = 0.150f
    private val LOGTOG = P3(0.415f, -0.150f, 0.17f)
    // ② the race
    private const val X2_0 = -0.62f
    private const val DX2 = 0.060f
    private const val Y2 = -0.200f
    private const val H2 = 0.52f
    private const val BAR_W = 0.0155f
    private val BAR_OFF = floatArrayOf(-0.0185f, 0f, 0.0185f)
    private val BAR_Z = floatArrayOf(0.022f, 0f, -0.022f)
    // ③ compounding
    private const val X3_0 = -0.70f
    private const val X3_1 = -0.07f
    private const val X3_E = -0.005f
    private const val Y3 = -0.200f
    private const val S3 = 0.150f                       // metres per unit of money
    private const val D3 = 0.090f
    private const val XC_0 = 0.115f
    private const val DXC = 0.086f
    private const val WC = 0.052f
    private val COMPARE = longArrayOf(1L, 2L, 4L, 12L, 365L, Compound.CONTINUOUS)
    // ④ spread
    private const val X4 = -0.12f
    private const val Y4 = -0.175f
    private const val RG = 0.081f
    private const val R0_X0 = -0.42f                    // R0 = 0.5 on the rail ...
    private const val R0_M = 0.40f                      // ... metres per unit of R0
    private const val R0_Y = -0.226f
    private const val R0_Z = 0.17f
    // station shelf
    private const val TOK_X = 0.755f
    private val TOK_Y = floatArrayOf(0.30f, 0.16f, 0.02f, -0.12f)

    private const val MAX_LAYERS = 64

    // ================================================================ colours (sRGB)
    private val WHITE = Rgb(0.97f, 0.97f, 1.0f)
    private val RICE = Rgb(0.97f, 0.94f, 0.84f)
    private val N_COL = Rgb(0.36f, 0.68f, 1.00f)
    private val SQ_COL = Rgb(0.30f, 0.90f, 0.52f)
    private val EXP_COL = Rgb(1.00f, 0.60f, 0.18f)
    private val GOLD = Rgb(1.00f, 0.77f, 0.30f)
    private val GREEN_A = Rgb(0.32f, 0.88f, 0.56f)
    private val GREEN_B = Rgb(0.15f, 0.62f, 0.42f)
    private val E_COL = Rgb(1.00f, 0.40f, 0.72f)
    private val ICE = Rgb(0.55f, 0.75f, 1.00f)
    private val DIM = Rgb(0.52f, 0.56f, 0.64f)
    private val RAMP = listOf(1 to Rgb(0.97f, 0.93f, 0.80f), 14 to Rgb(1.00f, 0.83f, 0.38f), 28 to Rgb(1.00f, 0.56f, 0.22f),
        44 to Rgb(0.98f, 0.34f, 0.44f), 64 to Rgb(0.64f, 0.42f, 1.00f))

    /** Column colour of square [n]: ivory -> gold -> orange -> rose -> violet. */
    private fun colN(n: Int): Rgb {
        for (i in 0 until RAMP.size - 1) {
            val (a, ca) = RAMP[i]; val (b, cb) = RAMP[i + 1]
            if (n <= b) return ProcTex.okLerp(ca, cb, (n - a).toFloat() / (b - a))
        }
        return RAMP.last().second
    }
    private fun genCol(g: Int) = ProcTex.okLerp(Rgb(1.0f, 0.86f, 0.36f), Rgb(1.0f, 0.28f, 0.30f), g / 6f)
    private fun glow(c: Rgb, k: Float = 0.30f) = Rgb(c.r * k, c.g * k, c.b * k)
    private fun solid(c: Rgb, e: Float = 0.30f, rough: Float = 0.36f) = Mat.Pbr(c, metallic = 0f, roughness = rough, emissive = glow(c, e))
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.32f)
    private val plateOff = Mat.Pbr(Rgb(0.10f, 0.11f, 0.13f), metallic = 0.3f, roughness = 0.35f)
    private val plateOn = Mat.Pbr(Rgb(0.16f, 0.24f, 0.40f), metallic = 0.2f, roughness = 0.30f, emissive = Rgb(0.10f, 0.24f, 0.50f))

    // ================================================================ text atlas
    private const val TEX_VERSION = 5
    private const val EM_PX = 64
    private val LANDMARK_TEXT = mapOf(
        "hand" to "${ExpText.HAND} 18 cm", "person" to "${ExpText.PERSON} 1.7 m", "tower" to "${ExpText.TOWER} 100 m",
        "everest" to "${ExpText.MOUNTAIN} 8,849 m", "moon" to "${ExpText.MOON} 384,400 km", "sun" to "${ExpText.SUN} 1.5×10^8 km")
    private val GRID_MANT = intArrayOf(1, 2, 3, 4, 5, 6, 8, 10, 15, 20, 25)

    private fun grp(v: Long) = String.format(Locale.US, "%,d", v)
    private fun grp(v: BigInteger) = String.format(Locale.US, "%,d", v)

    private val ATLAS_ITEMS: List<TextAtlas.Item> by lazy {
        val keys = LinkedHashMap<String, TextAtlas.Item>()
        fun add(t: String, spans: List<ExpText.Span> = listOf(ExpText.Span(t, WHITE)), key: String = t) { if (key !in keys) keys[key] = TextAtlas.Item(key, spans, EM_PX) }
        for (k in 0..63) add("2^$k")
        LANDMARK_TEXT.values.forEach { add(it) }
        for (k in 0..18 step 3) add(if (k == 0) "1" else "10^$k")
        listOf("30 cm", "50 cm", "lin", "log", "n", "n^2", "n^3", "2^n", "=", "n ≥ 5", "n ≥ 10", "t = 0", "t = 1", "∞", "12", "365", "↑",
            "R_0", "0.5", "1.5", "×2", "+1").forEach { add(it) }
        for (e in 0..6) add(if (e == 0) "1" else if (e == 1) "10" else "10^$e")
        for (e in 0..5) for (m in GRID_MANT) add(grp(m * 10.0.pow(e).toLong()))
        add("e", listOf(ExpText.Span("e = 2.71828…", E_COL)), key = "e_full")
        add("f", listOf(ExpText.Span("(1 + 1/n)^n", WHITE)), key = "f_n")
        keys.values.toList()
    }
    private lateinit var atlas: TextAtlas
    private val textMats = HashMap<Rgb, Mat>()
    private fun textMat(c: Rgb) = textMats.getOrPut(c) { Mat.Unlit(c, tex = atlas.image, blend = Blend.TRANSPARENT) }
    private val decalKey = java.util.IdentityHashMap<Node, String>()

    /** A text decal: [key] in the atlas, [em] metres per em, centred at its node. */
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

    // ================================================================ state
    /** 0 ① board, 1 ② race, 2 ③ compounding, 3 ④ spread. */
    var station = 0
    /** 0 slow, 1 medium, 2 fast. */
    var speed = 1
    private val SPEEDS = floatArrayOf(0.4f, 1f, 2.5f)
    private val spd get() = SPEEDS[speed]
    // ①
    var filled = 16
    var log1 = false
    var auto1 = false
    private var shown = 0f
    private val grow = FloatArray(65)
    private var morph1 = 0f
    private var autoT1 = 0f
    // ②
    var raceN = 3
    var polyK = 2
    var log2 = false
    var auto2 = false
    private var autoT2 = 0f
    private val barGrow = FloatArray(Race.N_MAX + 1)
    private var scaleL = 0f                      // displayed log2 of the linear axis top
    private var morph2 = 0f
    // ③
    var compN = 12L
    var yearT = 1f
    var yearPlay = false
    private var lastCompN = -1L
    // ④
    var r0 = 1.5f
    var spreadG = 0f
    var spreadPlay = false
    private var lastR0 = -1f
    private var tree: List<List<Spread.Case>> = emptyList()
    // drag
    private var dragAcc = 0f
    private var dragI0 = 0
    private var dragL0 = 0L
    private var dragF0 = 0f
    private var clock = 0f
    private var panelEpoch by mutableIntStateOf(0)
    private fun bump() { panelEpoch++ }

    private fun resetState() {
        station = 0; speed = 1
        filled = 16; log1 = false; auto1 = false; shown = 0f; grow.fill(0f); morph1 = 0f; autoT1 = 0f
        raceN = 3; polyK = 2; log2 = false; auto2 = false; autoT2 = 0f; barGrow.fill(0f); scaleL = log2Max(3).toFloat(); morph2 = 0f
        compN = 12L; yearT = 1f; yearPlay = false; lastCompN = -1L
        r0 = 1.5f; spreadG = Spread.GENERATIONS.toFloat(); spreadPlay = false; lastR0 = -1f; tree = emptyList()
        dragAcc = 0f; dragI0 = 0; dragL0 = 0L; dragF0 = 0f; clock = 0f
        decalKey.clear(); textMats.clear()
    }

    // ================================================================ nodes
    private lateinit var unitBox: MeshData
    private lateinit var ball: MeshData
    private lateinit var grainMesh: MeshData
    private lateinit var cyl: MeshData
    private lateinit var cone: MeshData
    private lateinit var groups: Array<Node>
    private lateinit var tokens: Array<Node>
    private lateinit var plates: Array<Node>
    private lateinit var poolGlow: Node
    // ①
    private class Grain(val node: Node, val sq: Int, val x: Float, val y: Float, val z: Float, val delay: Float)
    private lateinit var grains: List<Grain>
    private lateinit var cols: Array<Node>               // [n], n = 1..64 (index 0 unused)
    private lateinit var arrows: Array<Node?>
    private lateinit var ceiling: List<Node>
    private lateinit var sqNext: Node
    private lateinit var sqNextArrow: Node
    private lateinit var sqTag: Node
    private lateinit var passTag: Node
    private lateinit var knobN: Node
    private lateinit var linRuler: List<Node>
    private lateinit var logLadder: List<Node>
    private lateinit var logTog: Node
    private lateinit var togLin: Node
    private lateinit var togLog: Node
    private lateinit var togPlate: Node
    // ②
    private lateinit var bars: Array<Array<Node>>        // [f][n], f = 0 n, 1 n^k, 2 2^n
    private lateinit var segs: Array<Array<Node>>        // [f][n] joins n -> n+1
    private lateinit var grid: Array<Node>
    private lateinit var gridTxt: Array<Node>
    private lateinit var legend: Array<Node>
    private lateinit var polyTog: Node
    private lateinit var ties: Array<Node>
    private lateinit var gate: Node
    private lateinit var gateTxt: Node
    private lateinit var raceKnob: Node
    // ③
    private lateinit var prin: Node
    private lateinit var layers: Array<Node>
    private lateinit var cursor: Node
    private lateinit var timeKnob: Node
    private lateinit var compKnob: Node
    private lateinit var cmpHi: Array<Node>
    // ④
    private lateinit var cases: Array<Node>
    private lateinit var links: Array<Node>
    private lateinit var zero: Node
    private lateinit var r0Knob: Node

    // ================================================================ small node helpers
    private fun Node.at(x: Float, y: Float, z: Float) { val p = pos; if (p.x != x || p.y != y || p.z != z) pos = P3(x, y, z) }
    private fun Node.dims(x: Float, y: Float, z: Float) { val s = scale; if (s.x != x || s.y != y || s.z != z) scale = P3(x, y, z) }
    private fun Node.turn(q: Q) { if (rot != q) rot = q }
    private fun Node.show(v: Boolean) { visible = v }
    private fun sm(t: Float): Float { val x = t.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun approach(v: Float, target: Float, rate: Float, dt: Float): Float =
        if (v < target) min(target, v + rate * dt) else max(target, v - rate * dt)

    /** A thin bar between two points (unit cylinder along Y). */
    private fun Node.span(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, r: Float) {
        val dx = x1 - x0; val dy = y1 - y0; val dz = z1 - z0
        val l = sqrt(dx * dx + dy * dy + dz * dz)
        at((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2)
        dims(r, max(l, 1e-4f), r)
        turn(if (l < 1e-6f) Q.identity() else Q.fromTo(0f, 1f, 0f, dx, dy, dz))
    }

    private fun sqX(n: Int) = BX - 4 * SQ + (((n - 1) % 8) + 0.5f) * SQ
    private fun sqZ(n: Int) = BZ + 4 * SQ - (((n - 1) / 8) + 0.5f) * SQ

    // ================================================================ build

    override fun buildSpec(cacheDir: File): Node {
        resetState()
        val imgs = BakeCache.load(cacheDir, "exponential_text", TEX_VERSION) {
            listOf(TextAtlas.bakeImage(ATLAS_ITEMS), ProcTex.bakePlanet(256, 128, Rgb(0.07f, 0.075f, 0.085f), Rgb(0.12f, 0.125f, 0.14f), Rgb(0.20f, 0.205f, 0.22f), 0f, 77))
        }
        atlas = TextAtlas.of(imgs[0], ATLAS_ITEMS)
        unitBox = MeshGen.box(1f, 1f, 1f)
        ball = MeshGen.sphere(1f, 12, 24, MeshGen.SphereUv.EQUIRECT)
        grainMesh = MeshGen.sphere(1f, 5, 8, MeshGen.SphereUv.EQUIRECT)
        cyl = MeshGen.cylinder(1f, 1f, 10)
        cone = coneMesh(14)

        val root = Node("ExpRoot")
        buildBench(root, imgs[1])
        groups = Array(4) { k -> root.add(Node("St${k + 1}")) }
        build1(groups[0]); build2(groups[1]); build3(groups[2]); build4(groups[3])
        buildLogTog(root)
        buildTokens(root)
        update(0f)
        return root
    }

    private fun coneMesh(seg: Int): MeshData {
        // apex at y = 1, base radius 1 at y = 0; flat-ish normals are fine for a glowing arrow head
        val pos = ArrayList<Float>(); val nrm = ArrayList<Float>(); val uv = ArrayList<Float>(); val idx = ArrayList<Int>()
        fun v(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float): Int { pos += x; pos += y; pos += z; nrm += nx; nrm += ny; nrm += nz; uv += 0.5f; uv += 0.5f; return pos.size / 3 - 1 }
        for (s in 0 until seg) {
            val a0 = 2.0 * PI * s / seg; val a1 = 2.0 * PI * (s + 1) / seg; val am = (a0 + a1) / 2
            val c0 = cos(a0).toFloat(); val s0 = sin(a0).toFloat(); val c1 = cos(a1).toFloat(); val s1 = sin(a1).toFloat()
            val nx = cos(am).toFloat() * 0.707f; val nz = sin(am).toFloat() * 0.707f
            val a = v(c0, 0f, s0, nx, 0.707f, nz); val b = v(0f, 1f, 0f, nx, 0.707f, nz); val c = v(c1, 0f, s1, nx, 0.707f, nz)
            idx += a; idx += b; idx += c
            val d = v(0f, 0f, 0f, 0f, -1f, 0f); val e = v(c0, 0f, s0, 0f, -1f, 0f); val f = v(c1, 0f, s1, 0f, -1f, 0f)
            idx += d; idx += e; idx += f
        }
        return MeshData(pos.toFloatArray(), nrm.toFloatArray(), uv.toFloatArray(), idx.toIntArray())
    }

    private fun buildBench(root: Node, slabTex: Image) {
        root.add("Plinth", MeshGen.box(1.74f, 0.035f, 0.92f), Mat.Pbr(Rgb(1f, 1f, 1f), metallic = 0f, roughness = 0.55f, tex = slabTex), P3(0f, PLINTH_Y, -0.04f))
        root.add("PlinthBand", MeshGen.box(1.75f, 0.008f, 0.93f), alu, P3(0f, PLINTH_Y, -0.04f))
        val pool = OptGeo.lumTex(64, 64) { u, v -> val dx = (u - 0.5f) * 2f; val dy = (v - 0.5f) * 2f; val r2 = dx * dx + dy * dy; if (r2 >= 1f) 0f else (1f - r2) * (1f - r2) }
        poolGlow = root.add("Glow", OptGeo.quad(1f, 1f), Mat.Unlit(Rgb(1.0f, 0.62f, 0.30f), alpha = 0.22f, tex = pool, blend = Blend.ADD)).also {
            it.rot = Q.axisAngle(1f, 0f, 0f, -PI.toFloat() / 2); it.scale = P3(1.30f, 0.70f, 1f); it.pos = P3(-0.10f, PLINTH_Y + 0.019f, 0f)
        }
    }

    // ---------------------------------------------------------------- ① the board
    private fun boardTex(): Image {
        val n = 256; val img = Image(n, n); val c = n / 8
        for (y in 0 until n) for (x in 0 until n) {
            val f = x / c; val r = 7 - y / c                            // row 0 = back of the board = rank 8
            val dark = (f + r) % 2 == 0                                 // a1 is a dark square
            val ex = min(x % c, c - 1 - x % c); val ey = min(y % c, c - 1 - y % c)
            val edge = min(ex, ey) == 0
            val v = if (edge) 0.30f else if (dark) 0.085f else 0.19f
            val b = if (edge) 0.42f else if (dark) 0.11f else 0.24f
            img.px[y * n + x] = ProcTex.argb(1f, v, v * 1.03f, b)
        }
        return img
    }

    private fun build1(g: Node) {
        val side = 8 * SQ
        g.add("Board", MeshGen.box(side, BOARD_T, side), Mat.Pbr(Rgb(1f, 1f, 1f), metallic = 0.1f, roughness = 0.42f, tex = boardTex()), P3(BX, Y_TOP - BOARD_T / 2, BZ))
        g.add("BoardRim", MeshGen.box(side + 0.024f, BOARD_T - 0.006f, side + 0.024f), alu, P3(BX, Y_TOP - BOARD_T / 2 - 0.002f, BZ))
        // grains of squares 1..8, pooled: one shared mesh, one material
        val grainMat = Mat.Pbr(RICE, metallic = 0f, roughness = 0.45f, emissive = Rgb(0.22f, 0.21f, 0.18f))
        val gl = ArrayList<Grain>()
        for (n in 1..Rice.GRAIN_SQUARES) {
            val c = Rice.pileCells(n); val cell = Rice.CELL_MM.map { (it * 1e-3).toFloat() }
            val cnt = c[0] * c[1] * c[2]; var i = 0
            for (j in 0 until c[1]) for (k in 0 until c[2]) for (ii in 0 until c[0]) {
                val h = ProcTex.hash(n * 31 + ii, j * 17 + k, i, 2027)
                val x = (ii + 0.5f) * cell[0] - c[0] * cell[0] / 2 + (h - 0.5f) * 0.0008f
                val y = (j + 0.5f) * cell[1]
                val z = (k + 0.5f) * cell[2] - c[2] * cell[2] / 2
                val node = g.add("Gr_${n}_$i", grainMesh, grainMat)
                node.scale = P3(0.0035f, 0.00095f, 0.00098f)
                node.rot = Q.axisAngle(0f, 1f, 0f, (h - 0.5f) * 0.30f)
                gl += Grain(node, n, x, y, z, 0.55f * i / cnt)
                i++
            }
        }
        grains = gl
        cols = Array(65) { n -> if (n == 0) Node("Col_0") else g.add("Col_$n", unitBox, solid(colN(n))).also { it.scale = P3(1e-4f, 1e-4f, 1e-4f) } }
        val firstClip = (1..64).first { Rice.columnM(Chessboard.grainsD(it)) > H_CLIP }
        arrows = Array(65) { n -> if (n < firstClip) null else g.add("Arr_$n", cone, solid(ProcTex.okLerp(colN(n), WHITE, 0.35f), 0.55f, 0.3f)).also { it.visible = false } }
        // the ceiling of the drawing: a faint sheet + glowing rim where true-scale columns are cut
        val cy = Y_TOP + H_CLIP; val hw = side / 2 + 0.03f
        val sheet = g.add("Ceil", OptGeo.quad(1f, 1f), Mat.Unlit(ICE, alpha = 0.07f, blend = Blend.ADD), P3(BX, cy, BZ)).also {
            it.rot = Q.axisAngle(1f, 0f, 0f, -PI.toFloat() / 2); it.scale = P3(2 * hw, 2 * hw, 1f) }
        val rimMat = Mat.Unlit(ICE, alpha = 0.55f, blend = Blend.ADD)
        val rims = listOf(
            g.add("CeilR0", cyl, rimMat).also { it.span(BX - hw, cy, BZ + hw, BX + hw, cy, BZ + hw, 0.0018f) },
            g.add("CeilR1", cyl, rimMat).also { it.span(BX - hw, cy, BZ - hw, BX + hw, cy, BZ - hw, 0.0018f) },
            g.add("CeilR2", cyl, rimMat).also { it.span(BX - hw, cy, BZ - hw, BX - hw, cy, BZ + hw, 0.0018f) },
            g.add("CeilR3", cyl, rimMat).also { it.span(BX + hw, cy, BZ - hw, BX + hw, cy, BZ + hw, 0.0018f) })
        ceiling = listOf(sheet) + rims
        // the next empty square: a glowing frame + a bobbing arrow (one pinch target)
        sqNext = g.add(Node("SqNext")).also { it.tapRadius = 0.05f }
        val fMat = Mat.Unlit(Rgb(0.55f, 0.85f, 1.0f), blend = Blend.ADD)
        val h = SQ * 0.47f
        sqNext.add("SqNextF0", cyl, fMat).span(-h, 0f, h, h, 0f, h, 0.0022f)
        sqNext.add("SqNextF1", cyl, fMat).span(-h, 0f, -h, h, 0f, -h, 0.0022f)
        sqNext.add("SqNextF2", cyl, fMat).span(-h, 0f, -h, -h, 0f, h, 0.0022f)
        sqNext.add("SqNextF3", cyl, fMat).span(h, 0f, -h, h, 0f, h, 0.0022f)
        sqNextArrow = sqNext.add("SqNextA", cone, Mat.Pbr(Rgb(0.55f, 0.85f, 1.0f), roughness = 0.3f, emissive = Rgb(0.25f, 0.45f, 0.70f))).also {
            it.rot = Q.axisAngle(1f, 0f, 0f, PI.toFloat()); it.scale = P3(0.012f, 0.022f, 0.012f) }
        sqTag = decal(g, "SqTag", "2^0", 0.034f, Rgb(1f, 0.95f, 0.80f)).also { it.billboard = true }
        // the landmark the latest column has passed (true scale only; in LOG the landmarks are levels)
        passTag = decal(g, "PassTag", LANDMARK_TEXT.getValue("hand"), 0.026f, Rgb(1.0f, 0.86f, 0.50f)).also { it.billboard = true; it.visible = false }
        // the n rail (= the log axis: square n sits at log2(grains) = n - 1 doublings)
        val railTop = Y_TOP + 63 * K_LOG + LOG_BASE
        g.add("Rail", cyl, alu).span(X_RAIL, Y_TOP - 0.01f, BZ, X_RAIL, railTop + 0.012f, BZ, 0.0035f)
        for ((i, k) in (0..18 step 3).withIndex()) {
            val y = Y_TOP + LOG_BASE + K_LOG * (k * ln(10.0) / ln(2.0)).toFloat()
            g.add("RailT$i", cyl, Mat.Unlit(Rgb(0.80f, 0.84f, 0.92f))).span(X_RAIL - 0.012f, y, BZ, X_RAIL + 0.012f, y, BZ, 0.0016f)
            val key = if (k == 0) "1" else "10^$k"
            decal(g, "RailL$i", key, 0.018f, DIM, P3(X_RAIL - 0.022f - decalWidth(key, 0.018f) / 2, y, BZ))
        }
        knobN = g.add("KnobN", ball, solid(Rgb(0.55f, 0.85f, 1.0f), 0.35f, 0.22f)).also { it.uniformScale(0.016f); it.tapRadius = 0.05f / 0.016f }
        // linear (true-scale) ruler at the right edge, level with rank 2 where the visible columns stand
        val rz = sqZ(9); val rl = ArrayList<Node>()
        rl += g.add("LinRuler", cyl, alu).also { it.span(X_LINRULER, Y_TOP, rz, X_LINRULER, Y_TOP + H_CLIP, rz, 0.003f) }
        for (k in 0..5) { val y = Y_TOP + k * 0.1f; rl += g.add("LinT$k", cyl, Mat.Unlit(Rgb(0.80f, 0.84f, 0.92f))).also { it.span(X_LINRULER - 0.01f, y, rz, X_LINRULER + 0.01f, y, rz, 0.0015f) } }
        rl += decal(g, "Lin30", "30 cm", 0.017f, DIM, P3(X_LINRULER + 0.016f + decalWidth("30 cm", 0.017f) / 2, Y_TOP + 0.30f, rz))
        rl += decal(g, "Lin50", "50 cm", 0.017f, DIM, P3(X_LINRULER + 0.016f + decalWidth("50 cm", 0.017f) / 2, Y_TOP + 0.50f, rz))
        val handTxt = LANDMARK_TEXT["hand"]!!
        rl += g.add("LinHandT", cyl, Mat.Unlit(Rgb(1.0f, 0.85f, 0.45f), blend = Blend.ADD)).also { it.span(X_LINRULER - 0.018f, Y_TOP + 0.18f, rz, X_LINRULER + 0.018f, Y_TOP + 0.18f, rz, 0.0018f) }
        rl += decal(g, "LinHand", handTxt, 0.020f, Rgb(1.0f, 0.86f, 0.50f), P3(X_LINRULER + 0.022f + decalWidth(handTxt, 0.020f) / 2, Y_TOP + 0.18f, rz))
        linRuler = rl
        // LOG mode: the landmarks become evenly spaced levels (icon + true height on the rail, a level line at the back)
        val ll = ArrayList<Node>()
        val zBack = BZ - 4 * SQ - 0.006f
        for ((i, lm) in Rice.LANDMARKS.withIndex()) {
            val y = Y_TOP + LOG_BASE + K_LOG * Rice.log2Grains(lm.heightM).toFloat()
            val c = Rgb(1.0f, 0.86f, 0.50f)
            val lmMat = Mat.Unlit(c, alpha = 0.45f, blend = Blend.ADD)
            val xr = BX + 4 * SQ + 0.012f
            ll += g.add("LvB$i", cyl, lmMat).also { it.span(X_RAIL, y, zBack, xr, y, zBack, 0.0015f) }
            ll += g.add("LvR$i", cyl, lmMat).also { it.span(xr, y, zBack, xr, y, BZ, 0.0015f) }
            val t = LANDMARK_TEXT[lm.key]!!
            // the landmark (icon + true height) right of the board, where the level line comes forward
            ll += decal(g, "LvT$i", t, 0.023f, c, P3(xr + 0.012f + decalWidth(t, 0.023f) / 2, y, BZ))
        }
        logLadder = ll
    }

    /** The scale switch (stations ① and ②): a plaque reading "lin | log". Hangs off the root: both stations use it. */
    private fun buildLogTog(root: Node) {
        logTog = root.add(Node("LogTog")).also { it.pos = LOGTOG; it.tapRadius = 0.055f }
        togPlate = logTog.add("LogTogP", MeshGen.box(0.150f, 0.062f, 0.012f), plateOff, P3(0f, 0f, -0.012f))
        togLin = decal(logTog, "LogTogA", "lin", 0.030f, WHITE, P3(-0.036f, 0.002f, -0.004f))
        togLog = decal(logTog, "LogTogB", "log", 0.030f, WHITE, P3(0.036f, 0.002f, -0.004f))
    }

    // ---------------------------------------------------------------- ② the race
    private fun build2(g: Node) {
        g.add("Floor2", MeshGen.box(1.30f, 0.010f, 0.13f), Mat.Pbr(Rgb(0.10f, 0.11f, 0.14f), metallic = 0.3f, roughness = 0.3f), P3(-0.05f, Y2 - 0.005f, 0f))
        val cols = arrayOf(N_COL, SQ_COL, EXP_COL)
        bars = Array(3) { f -> Array(Race.N_MAX + 1) { n -> if (n == 0) Node("Bar${f}_0") else g.add("Bar${f}_$n", unitBox, solid(cols[f], 0.32f)).also { it.scale = P3(1e-4f, 1e-4f, 1e-4f) } } }
        segs = Array(3) { f -> Array(Race.N_MAX) { n -> g.add("Seg${f}_$n", cyl, Mat.Unlit(ProcTex.okLerp(cols[f], WHITE, 0.35f), blend = Blend.ADD)).also { it.visible = false } } }
        grid = Array(7) { i -> g.add("Grid$i", cyl, Mat.Unlit(ICE, alpha = 0.22f, blend = Blend.ADD)).also { it.visible = false } }
        gridTxt = Array(7) { i -> decal(g, "GridT$i", "1", 0.021f, DIM).also { it.visible = false } }
        for ((i, n) in listOf(1, 5, 10, 15, 20).withIndex()) decal(g, "XT$i", "$n", 0.022f, DIM, P3(X2_0 + (n - 1) * DX2, Y2 - 0.026f, 0.066f))
        legend = arrayOf(
            decal(g, "Leg0", "n", 0.036f, ProcTex.okLerp(N_COL, WHITE, 0.25f), P3(-0.53f, 0.392f, 0f)),
            decal(g, "Leg1", "n^2", 0.036f, ProcTex.okLerp(SQ_COL, WHITE, 0.25f), P3(-0.43f, 0.392f, 0f)),
            decal(g, "Leg2", "2^n", 0.036f, ProcTex.okLerp(EXP_COL, WHITE, 0.25f), P3(-0.31f, 0.392f, 0f)))
        polyTog = g.add(Node("PolyTog")).also { it.pos = P3(-0.43f, 0.392f, 0.01f); it.tapRadius = 0.05f }
        ties = Array(2) { decal(g, "Tie$it", "=", 0.040f, WHITE).also { n -> n.visible = false } }
        gate = g.add("Gate", cyl, Mat.Unlit(Rgb(1.0f, 0.82f, 0.45f), alpha = 0.85f, blend = Blend.ADD)).also { it.visible = false }
        gateTxt = decal(g, "GateT", "n ≥ 5", 0.028f, Rgb(1.0f, 0.86f, 0.50f)).also { it.visible = false }
        raceKnob = g.add("RaceNext", ball, solid(Rgb(0.55f, 0.85f, 1.0f), 0.35f, 0.22f)).also { it.uniformScale(0.016f); it.tapRadius = 0.05f / 0.016f }
    }

    // ---------------------------------------------------------------- ③ compounding
    private fun build3(g: Node) {
        g.add("Floor3", MeshGen.box(1.34f, 0.010f, 0.13f), Mat.Pbr(Rgb(0.10f, 0.11f, 0.14f), metallic = 0.3f, roughness = 0.3f), P3(-0.08f, Y3 - 0.005f, 0f))
        prin = g.add("Prin", unitBox, solid(GOLD, 0.26f, 0.30f))
        prin.at((X3_0 + X3_E) / 2, Y3 + S3 / 2, 0f); prin.dims(X3_E - X3_0, S3, D3)
        val ma = solid(GREEN_A, 0.30f); val mb = solid(GREEN_B, 0.30f)
        layers = Array(MAX_LAYERS) { j -> g.add("Lay_$j", unitBox, if (j % 2 == 0) ma else mb).also { it.visible = false } }
        cursor = g.add("Cursor", cyl, Mat.Unlit(Rgb(0.60f, 0.85f, 1.0f), alpha = 0.9f, blend = Blend.ADD)).also { it.visible = false }
        timeKnob = g.add("TimeKnob", ball, solid(Rgb(0.55f, 0.85f, 1.0f), 0.35f, 0.22f)).also { it.uniformScale(0.015f); it.tapRadius = 0.05f / 0.015f }
        compKnob = g.add("CompKnob", ball, solid(Rgb(0.55f, 0.85f, 1.0f), 0.35f, 0.22f)).also { it.uniformScale(0.016f); it.tapRadius = 0.05f / 0.016f }
        decal(g, "T0", "t = 0", 0.020f, DIM, P3(X3_0 + 0.02f, Y3 - 0.024f, D3 / 2 + 0.01f))
        decal(g, "T1", "t = 1", 0.020f, DIM, P3(X3_1, Y3 - 0.024f, D3 / 2 + 0.01f))
        decal(g, "Fn", "f_n", 0.040f, WHITE, P3(-0.40f, 0.34f, 0f))
        // the year-end stacks for n = 1, 2, 4, 12, 365, ∞ (static: built once)
        val prinC = solid(GOLD, 0.26f, 0.30f)
        cmpHi = Array(COMPARE.size) { i ->
            val n = COMPARE[i]; val x = XC_0 + i * DXC
            g.add("CPrin$i", unitBox, prinC, P3(x, Y3 + S3 / 2, 0f)).also { it.scale = P3(WC, S3, 0.07f) }
            if (n in 1..12) {
                for ((j, l) in Compound.layers(n, MAX_LAYERS).withIndex()) {
                    val y0 = Y3 + S3 * l.lo.toFloat(); val y1 = Y3 + S3 * l.hi.toFloat()
                    g.add("CLay${i}_$j", unitBox, if (j % 2 == 0) ma else mb, P3(x, (y0 + y1) / 2, 0f)).also { it.scale = P3(WC, y1 - y0, 0.07f) }
                }
            } else {
                val y1 = Y3 + S3 * Compound.f(n).toFloat()
                g.add("CLay${i}_s", unitBox, ma, P3(x, (Y3 + S3 + y1) / 2, 0f)).also { it.scale = P3(WC, y1 - Y3 - S3, 0.07f) }
            }
            val top = Y3 + S3 * Compound.f(n).toFloat()
            val key = if (n == Compound.CONTINUOUS) "∞" else "$n"
            decal(g, "CLab$i", key, 0.024f, WHITE, P3(x, top + 0.03f, 0.0f))
            g.add("CHi$i", cyl, Mat.Unlit(Rgb(0.60f, 0.85f, 1.0f), blend = Blend.ADD)).also {
                it.span(x - WC / 2 - 0.006f, Y3 + 0.002f, 0.040f, x + WC / 2 + 0.006f, Y3 + 0.002f, 0.040f, 0.0025f); it.visible = false }
        }
        // the ceiling no compounding can break: e
        val ye = Y3 + S3 * E.toFloat()
        g.add("ELine", cyl, Mat.Unlit(E_COL, blend = Blend.ADD)).span(X3_0 - 0.03f, ye, 0.05f, XC_0 + 5 * DXC + 0.04f, ye, 0.05f, 0.0026f)
        g.add("EGlow", cyl, Mat.Unlit(E_COL, alpha = 0.18f, blend = Blend.ADD)).span(X3_0 - 0.03f, ye, 0.05f, XC_0 + 5 * DXC + 0.04f, ye, 0.05f, 0.008f)
        decal(g, "ETxt", "e_full", 0.036f, WHITE, P3(-0.215f, ye + 0.034f, 0.05f))
    }

    // ---------------------------------------------------------------- ④ spread
    private fun arcMesh(r: Float, w: Float, a0: Float, a1: Float, seg: Int): MeshData {
        val md = OptGeo.grid(2, seg + 1)
        for (k in 0..seg) {
            val a = a0 + (a1 - a0) * k / seg; val c = cos(a); val s = sin(a)
            OptGeo.setPos(md, k, (r + w) * c, (r + w) * s, 0f); OptGeo.setUv(md, k, k.toFloat() / seg, 0f)
            OptGeo.setPos(md, seg + 1 + k, (r - w) * c, (r - w) * s, 0f); OptGeo.setUv(md, seg + 1 + k, k.toFloat() / seg, 1f)
        }
        return md
    }

    private fun build4(g: Node) {
        val a0 = (12.0 * PI / 180).toFloat(); val a1 = (168.0 * PI / 180).toFloat()
        for (gen in 1..Spread.GENERATIONS) g.add("Arc$gen", arcMesh(RG * gen, 0.0012f, a0, a1, 48), Mat.Unlit(genCol(gen), alpha = 0.16f, blend = Blend.ADD), P3(X4, Y4, -0.02f * gen))
        val maxCases = Spread.tree(2.0).sumOf { it.size }
        cases = Array(maxCases) { i -> g.add("Case_$i", ball, solid(genCol(0))).also { it.visible = false; it.uniformScale(0.010f) } }
        links = Array(maxCases) { i -> g.add("Link_$i", cyl, Mat.Unlit(Rgb(1.0f, 0.62f, 0.42f), alpha = 0.55f, blend = Blend.ADD)).also { it.visible = false } }
        zero = cases[0]; zero.tapRadius = 0.05f / 0.010f
        // the R0 rail: 0.5 .. 2.0, the threshold R0 = 1 glowing
        g.add("R0Rail", cyl, alu).span(R0_X0, R0_Y, R0_Z, R0_X0 + 1.5f * R0_M, R0_Y, R0_Z, 0.0032f)
        for ((i, v) in listOf(0.5f, 1.0f, 1.5f, 2.0f).withIndex()) {
            val x = R0_X0 + (v - 0.5f) * R0_M
            val thr = v == 1.0f
            g.add("R0T$i", cyl, Mat.Unlit(if (thr) Rgb(1.0f, 0.45f, 0.45f) else Rgb(0.80f, 0.84f, 0.92f), blend = if (thr) Blend.ADD else Blend.OPAQUE))
                .span(x, R0_Y - 0.012f, R0_Z, x, R0_Y + 0.012f, R0_Z, if (thr) 0.0025f else 0.0016f)
            val key = if (v == 1.0f) "1" else if (v == 2.0f) "2" else if (v == 0.5f) "0.5" else "1.5"
            decal(g, "R0L$i", key, 0.019f, if (thr) Rgb(1.0f, 0.55f, 0.55f) else DIM, P3(x, R0_Y - 0.030f, R0_Z))
        }
        decal(g, "R0Name", "R_0", 0.024f, WHITE, P3(R0_X0 - 0.05f, R0_Y, R0_Z))
        r0Knob = g.add("R0Knob", ball, solid(Rgb(1.0f, 0.62f, 0.42f), 0.35f, 0.22f)).also { it.uniformScale(0.016f); it.tapRadius = 0.05f / 0.016f }
    }

    // ---------------------------------------------------------------- the station shelf
    private fun buildTokens(root: Node) {
        val plateMesh = MeshGen.box(0.130f, 0.105f, 0.012f)
        tokens = Array(4) { k -> root.add(Node("Tok$k")).also { it.pos = P3(TOK_X, TOK_Y[k], 0.02f); it.tapRadius = 0.06f } }
        plates = Array(4) { k -> tokens[k].add("Plate$k", plateMesh, plateOff, P3(0f, 0f, -0.02f)) }
        val z = -0.006f
        fun mini(p: Node, name: String, w: Float, h: Float, d: Float, x: Float, y: Float, zz: Float, c: Rgb) =
            p.add(name, MeshGen.box(w, h, d), solid(c, 0.35f), P3(x, y, zz))
        // ① a ramp of columns (the log board)
        for (i in 0 until 8) { val h = 0.006f + 0.0085f * i; mini(tokens[0], "Tm0_$i", 0.0105f, h, 0.010f, -0.042f + i * 0.012f, -0.040f + h / 2, z, colN(1 + i * 9)) }
        // ② n, n², 2ⁿ
        for ((i, c) in listOf(N_COL, SQ_COL, EXP_COL).withIndex()) { val h = listOf(0.020f, 0.045f, 0.080f)[i]; mini(tokens[1], "Tm1_$i", 0.020f, h, 0.010f, -0.028f + i * 0.028f, -0.040f + h / 2, z, c) }
        // ③ a gold bar and its interest layers
        mini(tokens[2], "Tm2_p", 0.080f, 0.024f, 0.010f, 0f, -0.028f, z, GOLD)
        for (i in 0 until 4) { val h = 0.009f + 0.002f * i; val y0 = -0.016f + (0 until i).sumOf { (0.009 + 0.002 * it) }.toFloat()
            mini(tokens[2], "Tm2_$i", 0.080f - 0.016f * i, h, 0.010f, 0.008f * i, y0 + h / 2, z, if (i % 2 == 0) GREEN_A else GREEN_B) }
        // ④ a little fan: 1 -> 2 -> 4
        val dots = listOf(0f to -0.035f, -0.018f to -0.008f, 0.018f to -0.008f, -0.036f to 0.024f, -0.012f to 0.030f, 0.012f to 0.030f, 0.036f to 0.024f)
        for ((i, d) in dots.withIndex()) tokens[3].add("Tm3_$i", ball, solid(genCol(if (i == 0) 0 else if (i < 3) 2 else 5), 0.4f), P3(d.first, d.second, z)).also { it.uniformScale(0.0075f) }
    }

    // ================================================================ per frame

    override fun update(dt: Float) {
        clock += dt
        for (s in 0 until 4) { groups[s].show(s == station); plates[s].setMat(if (s == station) plateOn else plateOff) }
        when (station) { 0 -> update1(dt); 1 -> update2(dt); 2 -> update3(dt); else -> update4(dt) }
        logTog.show(station <= 1)
        val tr0 = if (station <= 1) 0.055f else 0f
        if (logTog.tapRadius != tr0) logTog.tapRadius = tr0
        if (station == 1) logTog.at(-0.10f, 0.392f, 0.0f) else logTog.at(LOGTOG.x, LOGTOG.y, LOGTOG.z)
        val on = if (station == 0) log1 else log2
        togLin.setMat(textMat(if (on) DIM else WHITE)); togLog.setMat(textMat(if (on) Rgb(1.0f, 0.86f, 0.50f) else DIM))
        togPlate.setMat(if (on) plateOn else plateOff)
        poolGlow.at(when (station) { 0 -> BX; 1 -> -0.05f; 2 -> -0.10f; else -> X4 }, PLINTH_Y + 0.019f, 0f)
        updateReadout()
        updateLabels()
    }

    // ---------------------------------------------------------------- ①
    /** Drawn (width, height, depth) of square [n] in true scale (before the growth animation). */
    private fun linDims(n: Int): FloatArray = when {
        n <= Rice.GRAIN_SQUARES -> Rice.pileM(n).map { it.toFloat() }.toFloatArray()
        n < Rice.FIRST_COLUMN -> { val c = Rice.cubeM(Chessboard.grainsD(n)).toFloat(); floatArrayOf(c, c, c) }
        else -> floatArrayOf(COL_W * SQ, min(H_CLIP, Rice.columnM(Chessboard.grainsD(n)).toFloat()), COL_W * SQ)
    }
    private fun logH(n: Int) = LOG_BASE + K_LOG * (n - 1)
    private fun clipped(n: Int) = Rice.columnM(Chessboard.grainsD(n)) > H_CLIP

    private fun update1(dt: Float) {
        // the logic first: auto-fill, the cascade toward `filled`, growth, the scale morph
        if (auto1) {
            autoT1 += dt * spd
            if (autoT1 >= 0.45f) { autoT1 = 0f; if (filled < 64) filled++ else auto1 = false }
        }
        shown = approach(shown, filled.toFloat(), 30f * spd, dt)
        for (n in 1..64) grow[n] = approach(grow[n], if (n <= shown + 1e-3f) 1f else 0f, spd / 0.55f, dt)
        morph1 = approach(morph1, if (log1) 1f else 0f, spd / 0.8f, dt)
        val m = sm(morph1)
        // grains (squares 1..8, true scale): they rain in, square by square
        val showGrains = m < 0.5f
        for (gr in grains) {
            val g0 = grow[gr.sq]
            val p = ((g0 - gr.delay) / 0.45f).coerceIn(0f, 1f)
            val on = showGrains && p > 0f
            gr.node.show(on)
            if (on) gr.node.at(sqX(gr.sq) + gr.x, Y_TOP + gr.y + 0.10f * (1f - p) * (1f - p), sqZ(gr.sq) + gr.z)
        }
        var anyClip = false
        for (n in 1..64) {
            val g = sm(grow[n])
            val ld = linDims(n)
            val w = lerp(ld[0], COL_W * SQ, m); val d = lerp(ld[2], COL_W * SQ, m)
            val h = lerp(ld[1], logH(n), m) * g
            val on = g > 0.002f && (n > Rice.GRAIN_SQUARES || m >= 0.5f)
            val c = cols[n]
            c.show(on)
            if (on) { c.at(sqX(n), Y_TOP + h / 2, sqZ(n)); c.dims(w, max(h, 1e-4f), d) }
            val a = arrows[n]
            if (a != null) {
                val aOn = m < 0.5f && grow[n] > 0.92f
                a.show(aOn)
                if (aOn) { anyClip = true; val s = 0.6f + 0.4f * sm((grow[n] - 0.92f) / 0.08f); a.at(sqX(n), Y_TOP + H_CLIP + 0.002f, sqZ(n)); a.dims(0.016f * s, 0.030f * s, 0.016f * s) }
            }
        }
        ceiling.forEach { it.show(anyClip) }
        // the next empty square
        val nx = if (filled < 64) filled + 1 else 1
        sqNext.at(sqX(nx), Y_TOP + 0.003f, sqZ(nx))
        sqNextArrow.at(0f, 0.050f + 0.010f * sin(clock * 3.2f), 0f)
        // the square just filled: its exponent floats over its column (above the arrow if it is cut)
        val f = filled
        sqTag.show(f >= 1 && grow[f] > 0.3f)
        if (f >= 1) {
            setDecal(sqTag, "2^${f - 1}", 0.034f)
            // a cut column's tag floats over the FRONT rim of the ceiling, in line with its column (never hidden behind it)
            val cut = m < 0.5f && clipped(f)
            val top = if (cut) Y_TOP + H_CLIP + 0.05f else Y_TOP + lerp(linDims(f)[1], logH(f), m) * sm(grow[f]) + 0.035f
            sqTag.at(sqX(f), top, if (cut) BZ + 4 * SQ + 0.03f else sqZ(f) + 0.01f)
        }
        // past the ruler's hand mark, the landmark a (cut) column has passed rides next to its tag
        val lm = if (f >= 1 && clipped(f)) Rice.passed(f) else null
        passTag.show(lm != null && m < 0.5f && grow[f] > 0.5f)
        if (lm != null) {
            val key = LANDMARK_TEXT.getValue(lm.key)
            setDecal(passTag, key, 0.026f)
            val x = sqX(f) + decalWidth("2^${f - 1}", 0.034f) / 2 + 0.014f + decalWidth(key, 0.026f) / 2
            passTag.at(x, sqTag.pos.y - 0.004f, sqTag.pos.z)
        }
        // the n knob rides the rail at log2(grains of the last square) = filled - 1
        knobN.at(X_RAIL, Y_TOP + LOG_BASE + K_LOG * max(0, filled - 1), BZ)
        linRuler.forEach { it.show(m < 0.5f) }
        logLadder.forEach { it.show(m >= 0.5f) }
    }

    // ---------------------------------------------------------------- ②
    private fun value(f: Int, n: Int): Long = when (f) { 0 -> n.toLong(); 1 -> Race.poly(n, polyK); else -> Race.pow2(n) }
    private fun log2Max(nn: Int): Double { var mx = 1L; for (n in 1..nn) for (f in 0..2) mx = max(mx, value(f, n)); return ln(mx.toDouble()) / ln(2.0) }
    private fun x2(n: Int) = X2_0 + (n - 1) * DX2
    private fun barH(f: Int, n: Int, m: Float): Float {
        val v = value(f, n).toDouble()
        val lin = (H2 * v / 2.0.pow(scaleL.toDouble())).toFloat()
        val lg = (H2 * (ln(v) / ln(2.0)) / Race.N_MAX).toFloat()
        return max(0.0015f, lerp(lin, lg, m))
    }

    private fun niceStep(top: Double): Double {
        val raw = top / 4.0; val e = floor(log10(raw)); val b = 10.0.pow(e)
        return max(1.0, listOf(1.0, 2.0, 5.0, 10.0).map { it * b }.first { it >= raw * 0.999 })
    }

    private fun update2(dt: Float) {
        if (auto2) {
            autoT2 += dt * spd
            if (autoT2 >= 0.7f) { autoT2 = 0f; if (raceN < Race.N_MAX) raceN++ else auto2 = false }
        }
        for (n in 1..Race.N_MAX) barGrow[n] = approach(barGrow[n], if (n <= raceN) 1f else 0f, spd / 0.45f, dt)
        scaleL = approach(scaleL, log2Max(raceN).toFloat(), spd * 2.2f, dt)
        morph2 = approach(morph2, if (log2) 1f else 0f, spd / 0.8f, dt)
        val m = sm(morph2)
        val tops = Array(3) { FloatArray(Race.N_MAX + 1) }
        for (f in 0..2) for (n in 1..Race.N_MAX) {
            val g = sm(barGrow[n]); val b = bars[f][n]
            val h = barH(f, n, m) * g
            tops[f][n] = Y2 + h
            b.show(g > 0.01f)
            if (g > 0.01f) { b.at(x2(n) + BAR_OFF[f], Y2 + h / 2, BAR_Z[f]); b.dims(BAR_W, h, 0.018f) }
        }
        for (f in 0..2) for (n in 1 until Race.N_MAX) {
            val s = segs[f][n]
            val on = barGrow[n + 1] > 0.98f && barGrow[n] > 0.98f
            s.show(on)
            if (on) s.span(x2(n) + BAR_OFF[f], tops[f][n], BAR_Z[f] + 0.0105f, x2(n + 1) + BAR_OFF[f], tops[f][n + 1], BAR_Z[f] + 0.0105f, 0.0021f)
        }
        // grid: linear = nice steps of the current zoom, log = powers of ten
        val xl = X2_0 - 0.035f; val xr = x2(Race.N_MAX) + 0.03f
        if (m < 0.5f) {
            val top = 2.0.pow(scaleL.toDouble()); val step = niceStep(top)
            for (i in 0 until 7) {
                val v = step * (i + 1); val on = i < 5 && v <= top * 1.001
                grid[i].show(on); gridTxt[i].show(on)
                if (on) {
                    val y = Y2 + (H2 * v / top).toFloat()
                    grid[i].span(xl, y, -0.035f, xr, y, -0.035f, 0.0011f)
                    val key = grp(v.roundToLong())
                    if (atlas.has(key)) { setDecal(gridTxt[i], key, 0.021f); gridTxt[i].at(xl - 0.008f - decalWidth(key, 0.021f) / 2, y, -0.035f) } else gridTxt[i].show(false)
                }
            }
        } else {
            for (i in 0 until 7) {
                val y = Y2 + (H2 * (i * ln(10.0) / ln(2.0)) / Race.N_MAX).toFloat()
                grid[i].show(true); gridTxt[i].show(true)
                grid[i].span(xl, y, -0.035f, xr, y, -0.035f, 0.0011f)
                val key = if (i == 0) "1" else if (i == 1) "10" else "10^$i"
                setDecal(gridTxt[i], key, 0.021f); gridTxt[i].at(xl - 0.008f - decalWidth(key, 0.021f) / 2, y, -0.035f)
            }
        }
        setDecal(legend[1], if (polyK == 2) "n^2" else "n^3", 0.036f)
        // ties (2^n == n^k) and the gate from where 2^n leads forever
        val tl = Race.ties(polyK)
        for (i in 0 until 2) {
            val n = tl.getOrNull(i); val on = n != null && barGrow[n] > 0.95f
            ties[i].show(on)
            if (on && n != null) ties[i].at(x2(n), max(tops[1][n], tops[2][n]) + 0.03f, 0.03f)
        }
        val n0 = Race.foreverFrom(polyK)
        val gOn = raceN >= n0 && barGrow[n0] > 0.95f
        gate.show(gOn); gateTxt.show(gOn)
        if (gOn) {
            val gx = x2(n0) - DX2 / 2
            gate.span(gx, Y2, 0.03f, gx, Y2 + H2 * 0.80f, 0.03f, 0.0018f)
            setDecal(gateTxt, if (polyK == 2) "n ≥ 5" else "n ≥ 10", 0.026f)
            gateTxt.at(gx + 0.01f + decalWidth(if (polyK == 2) "n ≥ 5" else "n ≥ 10", 0.026f) / 2, Y2 + H2 * 0.80f + 0.02f, 0.03f)
        }
        raceKnob.at(x2(raceN) + 0.040f, Y2 + 0.016f, 0.05f)
    }

    // ---------------------------------------------------------------- ③
    private fun x3(t: Float) = X3_0 + (X3_1 - X3_0) * t

    private fun update3(dt: Float) {
        if (yearPlay) { yearT += dt * spd / 4f; if (yearT >= 1f) { yearT = 1f; yearPlay = false } }
        val ls = if (compN == Compound.CONTINUOUS) continuousLayers() else Compound.layers(compN, MAX_LAYERS)
        for (j in 0 until MAX_LAYERS) {
            val l = ls.getOrNull(j); val node = layers[j]
            val on = l != null && l.t <= yearT + 1e-6
            node.show(on)
            if (on && l != null) {
                val x0 = x3(l.t.toFloat()); val y0 = Y3 + S3 * l.lo.toFloat(); val y1 = Y3 + S3 * l.hi.toFloat()
                node.at((x0 + X3_E) / 2, (y0 + y1) / 2, 0f); node.dims(max(X3_E - x0, 0.002f), max(y1 - y0, 0.0004f), D3 * 0.98f)
            }
        }
        val playing = yearPlay || yearT < 1f
        cursor.show(playing)
        val cx = x3(yearT)
        if (playing) cursor.span(cx, Y3, D3 / 2 + 0.006f, cx, Y3 + S3 * 2.9f, D3 / 2 + 0.006f, 0.0018f)
        timeKnob.at(cx, Y3 - 0.024f, D3 / 2 + 0.022f)
        val bal = ls.lastOrNull { it.t <= yearT + 1e-6 }?.hi ?: 1.0
        compKnob.at(X3_E - 0.03f, Y3 + S3 * bal.toFloat() + 0.024f, 0f)
        for (i in COMPARE.indices) cmpHi[i].show(COMPARE[i] == compN)
    }

    /** Continuous compounding drawn as 64 merged steps of e^t (the top is exactly e). */
    private fun continuousLayers(): List<Compound.Layer> = List(MAX_LAYERS) { j ->
        Compound.Layer(j.toDouble() / MAX_LAYERS, exp(j.toDouble() / MAX_LAYERS), exp((j + 1.0) / MAX_LAYERS), 0)
    }

    // ---------------------------------------------------------------- ④
    private fun caseMat(g: Int, w: Double): Mat {
        val a = ((w * 10).roundToInt() / 10f).coerceIn(0.1f, 1f)
        return caseMats.getOrPut(g * 100 + (a * 10).roundToInt()) {
            val c = genCol(g)
            if (a >= 0.999f) solid(c, 0.40f, 0.30f) else Mat.Unlit(c, alpha = a * 0.85f, blend = Blend.TRANSPARENT)
        }
    }
    private val caseMats = HashMap<Int, Mat>()

    /** Positions of the drawn cases (flattened tree order) and each case's parent slot; rebuilt only when R0 changes. */
    private var casePos: Array<FloatArray> = emptyArray()
    private var caseParent = IntArray(0)

    private fun layoutTree() {
        tree = Spread.tree(r0.toDouble())
        val a0 = 12f; val a1 = 168f
        val pos = ArrayList<FloatArray>(); val par = ArrayList<Int>()
        val start = IntArray(tree.size)
        for ((g, gen) in tree.withIndex()) {
            start[g] = pos.size
            val cnt = gen.size
            val step = min((a1 - a0) / max(1, cnt), 26f)
            for (c in gen) {
                val ang = ((90f + ((cnt - 1) / 2f - c.idx) * step) * PI / 180).toFloat()
                val r = RG * g
                pos += floatArrayOf(X4 + r * cos(ang), Y4 + r * sin(ang), -0.02f * g)
                par += if (c.parent >= 0) start[g - 1] + c.parent else -1
            }
        }
        casePos = pos.toTypedArray(); caseParent = par.toIntArray()
    }

    private fun update4(dt: Float) {
        if (spreadPlay) { spreadG += dt * spd / 0.7f; if (spreadG >= Spread.GENERATIONS) { spreadG = Spread.GENERATIONS.toFloat(); spreadPlay = false } }
        if (r0 != lastR0) { lastR0 = r0; layoutTree() }
        var k = 0
        for ((g, gen) in tree.withIndex()) {
            val grow = (spreadG - g + 1f).coerceIn(0f, 1f)
            val vis = grow > 0.01f
            for (c in gen) {
                val p = casePos[k]
                val node = cases[k]
                node.show(vis || g == 0)
                node.at(p[0], p[1], p[2])
                val sc = if (g == 0) 0.013f else 0.0105f * sm(grow).coerceAtLeast(0.05f)
                node.dims(sc, sc, sc)
                node.setMat(caseMat(g, c.weight))
                val ln = links[k]; val pi = caseParent[k]
                if (pi >= 0 && vis) {
                    val pp = casePos[pi]; val t = sm(grow)
                    ln.show(true)
                    ln.span(pp[0], pp[1], pp[2], lerp(pp[0], p[0], t), lerp(pp[1], p[1], t), lerp(pp[2], p[2], t), 0.0022f)
                    ln.setMat(if (c.weight >= 0.999) linkOn else linkFaint)
                } else ln.show(false)
                k++
            }
        }
        for (i in k until cases.size) { cases[i].show(false); links[i].show(false) }
        r0Knob.at(R0_X0 + (r0 - 0.5f) * R0_M, R0_Y, R0_Z)
    }
    private val linkOn = Mat.Unlit(Rgb(1.0f, 0.62f, 0.42f), alpha = 0.60f, blend = Blend.ADD)
    private val linkFaint = Mat.Unlit(Rgb(1.0f, 0.62f, 0.42f), alpha = 0.22f, blend = Blend.ADD)

    // ================================================================ formatting
    private val SUPS = "⁰¹²³⁴⁵⁶⁷⁸⁹"
    private fun sup(k: Int): String = k.toString().map { if (it == '-') '⁻' else SUPS[it - '0'] }.joinToString("")
    private fun sig3(x: Double): String = BigDecimal(x).round(MathContext(3)).stripTrailingZeros().toPlainString().let {
        if (it.contains('.') || it.length <= 3) it else grp(it.toLong()) }
    private fun sci(x: Double): String {
        if (x < 1e6) return sig3(x)
        var e = floor(log10(x)).toInt(); var mm = x / 10.0.pow(e)
        if (mm >= 9.95) { mm /= 10; e++ }
        return String.format(Locale.US, "%.1f×10%s", mm, sup(e))
    }
    private fun fx(x: Double, d: Int): String = String.format(Locale.US, "%.${d}f", x)
    private fun fx(x: Float, d: Int): String = fx(x.toDouble(), d)
    private fun lenStr(m: Double): String = when {
        m < 0.01 -> String.format(Locale.US, "%.1f mm", m * 1e3)
        m < 1.0 -> "${sig3(m * 100)} cm"
        m < 1000.0 -> "${sig3(m)} m"
        m < 1e7 -> "${sig3(m / 1e3)} km"
        else -> "${sci(m / 1e3)} km"
    }
    private fun massStr(kg: Double): String = when {
        kg < 1.0 -> "${sig3(kg * 1e3)} g"
        kg < 1e3 -> "${sig3(kg)} kg"
        else -> tr("${sci(kg / 1e3)} 吨", "${sci(kg / 1e3)} t")
    }
    private fun bigStr(n: Int): String = if (n <= 40) grp(Chessboard.grains(n)) else sci(Chessboard.grainsD(n))
    private fun worldStr(kg: Double): String {
        val y = Rice.worldYears(kg)
        return when {
            y < 1.0 / 365 -> tr("${sig3(y * 8766)} 小时", "${sig3(y * 8766)} hours")
            y < 1.0 -> tr("${sig3(y * 365.25)} 天", "${sig3(y * 365.25)} days")
            else -> tr("${y.roundToInt()} 年", "${y.roundToInt()} years")
        }
    }
    private fun lmName(lm: Rice.Landmark) = tr(lm.zh, lm.en)
    /** "1.5×10⁸ km" (the decal text without its pictogram, superscript as Unicode). */
    private fun landmarkHeight(lm: Rice.Landmark) = LANDMARK_TEXT.getValue(lm.key).drop(2).replace("^8", "⁸")
    private fun compName(n: Long): String = when (n) {
        Compound.CONTINUOUS -> tr("连续复利", "continuous")
        1L -> tr("一年一次", "yearly"); 2L -> tr("半年一次", "half-yearly"); 4L -> tr("每季度", "quarterly")
        12L -> tr("每月", "monthly"); 52L -> tr("每周", "weekly"); 365L -> tr("每天", "daily")
        8760L -> tr("每小时", "hourly"); else -> tr("一年 $n 次", "$n times a year")
    }

    // ================================================================ readout + labels

    private fun updateReadout() {
        readoutText = when (station) {
            0 -> readout1()
            1 -> readout2()
            2 -> readout3()
            else -> readout4()
        }
    }

    private fun readout1(): String {
        val f = filled
        if (f == 0) return tr("空棋盘：捏发光的「下一格」放下第 1 粒米", "Empty board: pinch the glowing 'next square' to place grain 1") + "\n" +
            tr("规则：第 n 格放 2ⁿ⁻¹ 粒 —— 1, 2, 4, 8 … 每格翻倍，直到第 64 格", "The rule: square n gets 2ⁿ⁻¹ grains — 1, 2, 4, 8 … doubling up to square 64") + "\n" +
            tr("真实比例：一粒米 25 mg，棋盘一格 6.25 cm", "True scale: a grain is 25 mg, a square is 6.25 cm")
        val l1 = tr("第 $f 格：2${sup(f - 1)} = ${grp(Chessboard.grains(f))} 粒 · 累计 2${sup(f)} − 1 = ${grp(Chessboard.total(f))} 粒",
            "Square $f: 2${sup(f - 1)} = ${grp(Chessboard.grains(f))} grains · total 2${sup(f)} − 1 = ${grp(Chessboard.total(f))}")
        val g = Chessboard.grainsD(f); val kg = Rice.massKg(g); val tKg = Rice.massKg(Chessboard.totalD(f))
        val h = Rice.columnM(g)
        val l2 = when {
            f <= Rice.GRAIN_SQUARES -> tr("这一格 ${massStr(kg)}：一小撮米，真实大小（约 ${sig3(Rice.pileM(f).max() * 100)} cm）",
                "This square: ${massStr(kg)}, a pinch of rice at true size (about ${sig3(Rice.pileM(f).max() * 100)} cm)")
            f < Rice.FIRST_COLUMN -> tr("这一格 ${massStr(kg)}：边长 ${lenStr(Rice.cubeM(g))} 的米块（真实大小）",
                "This square: ${massStr(kg)}, a cube of rice ${lenStr(Rice.cubeM(g))} on a side (true size)")
            else -> {
                val lm = Rice.passed(f)
                val lmH = if (lm != null) landmarkHeight(lm) else ""
                val past = if (lm != null) tr("，超过${lmName(lm)}（$lmH）", ", past ${lmName(lm)} ($lmH)") else ""
                val tot = if (Rice.worldYears(tKg) >= 1.0 / 8766) tr(" · 累计 ≈ 全世界 ${worldStr(tKg)}的大米", " · total ≈ ${worldStr(tKg)} of world rice") else ""
                tr("这一格 ${massStr(kg)}：铺满一格堆成 ${lenStr(h)} 高的米柱", "${massStr(kg)} here: a column ${lenStr(h)} tall on its square") + past + tot
            }
        }
        val l3 = when {
            log1 -> tr("对数高度：每格只高一级（×2）—— 指数增长在对数轴上是一条笔直的斜坡",
                "Log height: every square is one step higher (×2) — on a log axis exponential growth is a straight ramp")
            f == 64 -> tr("单是第 64 格就 ≈ 全世界 ${worldStr(kg)}的大米（FAO 每年约 5.2 亿吨）—— 切到对数看看",
                "Square 64 alone ≈ ${worldStr(kg)} of world rice (FAO: ≈ 520 Mt a year) — try the log scale")
            f < 16 -> tr("一开始很温和：前 $f 格加起来才 ${massStr(tKg)}。继续填，看第 17、18 格……",
                "Gentle at first: all $f squares hold just ${massStr(tKg)}. Keep going — watch squares 17 and 18…")
            else -> tr("第 $f 格一格就比前面 ${f - 1} 格加起来还多 1 粒 —— 翻倍先温和，然后爆炸",
                "Square $f alone holds 1 grain more than all ${f - 1} before it — doubling is gentle, then it explodes")
        }
        return "$l1\n$l2\n$l3"
    }

    private fun readout2(): String {
        val n = raceN; val k = polyK
        val pk = if (k == 2) "n²" else "n³"
        val c = Race.cmp(n, k)
        val lead = when { c > 0 -> tr("2ⁿ 领先", "2ⁿ leads"); c < 0 -> tr("$pk 领先", "$pk leads"); else -> tr("打平", "a tie") }
        val l1 = tr("n = $n：n = $n · $pk = ${grp(Race.poly(n, k))} · 2ⁿ = ${grp(Race.pow2(n))} —— $lead",
            "n = $n: n = $n · $pk = ${grp(Race.poly(n, k))} · 2ⁿ = ${grp(Race.pow2(n))} — $lead")
        val n0 = Race.foreverFrom(k)
        val l2 = if (n < n0) tr("谁会赢？先预测 2ⁿ 从哪一步起永远超过 $pk，再一步步往后走", "Who wins? Predict from which n on 2ⁿ stays ahead of $pk for good, then step on")
            else if (k == 2) tr("n = 2、4 打平，n = 3 时 n² 领先（9 > 8）；从 n = 5 起 2ⁿ 永远领先", "Ties at n = 2 and 4, n² ahead at n = 3 (9 > 8); from n = 5 on 2ⁿ leads forever")
            else tr("n = 2…9 时 n³ 领先；从 n = 10 起 2ⁿ 永远领先（1024 > 1000）", "n³ leads for n = 2…9; from n = 10 on 2ⁿ leads forever (1024 > 1000)")
        val r = ((n + 1.0) / n).pow(k)
        val l3 = if (log2) tr("对数轴：2ⁿ 是一条直线，$pk 和 n 越来越平 —— 任何多项式最终都追不上指数",
            "Log axis: 2ⁿ is a straight line, $pk and n flatten out — no polynomial keeps up with an exponential")
            else tr("为什么？2ⁿ 每步 ×2；$pk 这一步只 ×${fx(r, 2)}，而且越来越接近 ×1",
            "Why? 2ⁿ doubles every step; $pk grows only ×${fx(r, 2)} this step, ever closer to ×1")
        return "$l1\n$l2\n$l3"
    }

    private fun readout3(): String {
        val n = compN
        val f = Compound.f(n)
        val l1 = if (n == Compound.CONTINUOUS) tr("年利率 100 %，连续复利：e¹ = ${fx(f, 6)}", "100 % a year, compounded continuously: e¹ = ${fx(f, 6)}")
            else tr("年利率 100 %，${compName(n)}复利（n = ${grp(n)}）：(1 + 1/n)ⁿ = ${fx(f, 6)}",
                "100 % a year, compounded ${compName(n)} (n = ${grp(n)}): (1 + 1/n)ⁿ = ${fx(f, 6)}")
        val sf = Compound.shortfall(n) * 100
        val merged = if (n > MAX_LAYERS) tr(" · 图中每级合并约 ${(n / MAX_LAYERS)} 期", " · each drawn step merges ≈ ${(n / MAX_LAYERS)} periods") else ""
        val l2 = tr("e = 2.718282… · 还差 ${fx(sf, 3)} %", "e = 2.718282… · short by ${fx(sf, 3)} %") + merged
        val l3 = tr("分得越细，利滚利越多 —— 但永远到不了 e 之上（Bernoulli 1683）：每天复利也只有 2.7146",
            "Finer splits earn interest on interest — yet never pass e (Bernoulli 1683): even daily gives only 2.7146")
        return "$l1\n$l2\n$l3"
    }

    private fun readout4(): String {
        val r = r0.toDouble()
        val rs = fx(r, 2).trimEnd('0').trimEnd('.')
        val e6 = Spread.expected(r, 6); val tot = Spread.totalExpected(r, 6)
        val l1 = tr("R0 = $rs：第 g 代平均 R0^g 人 · 第 6 代 ≈ ${sig3(e6)} 人 · 前 7 代共 ≈ ${sig3(tot)} 人",
            "R0 = $rs: generation g has R0^g cases on average · gen 6 ≈ ${sig3(e6)} · gens 0–6 ≈ ${sig3(tot)}")
        val l2 = when {
            r < 0.999 -> tr("R0 < 1：永远加起来也只有 1/(1 − R0) = ${sig3(Spread.limitTotal(r))} 人 —— 疫情自己熄灭",
                "R0 < 1: even forever the total is only 1/(1 − R0) = ${sig3(Spread.limitTotal(r))} cases — it dies out")
            r < 1.001 -> tr("R0 = 1：每代 1 人，只是线性增长（g 代后累计 g + 1 人）", "R0 = 1: one case per generation, only linear growth (g + 1 after g generations)")
            else -> tr("R0 > 1：每代 ×$rs，第 20 代 ≈ ${sci(Spread.expected(r, 20))} 人 —— 指数爆发", "R0 > 1: ×$rs per generation, gen 20 ≈ ${sci(Spread.expected(r, 20))} cases — exponential")
        }
        val l3 = tr("门槛就在 R0 = 1：把 R0 压到 1 以下（隔离、疫苗），指数增长就变成指数衰减",
            "The threshold is R0 = 1: push R0 below 1 (isolation, vaccines) and growth turns into decay")
        return "$l1\n$l2\n$l3"
    }

    // Chips (SpatialUI on device) — clear of the lesson card (upper left) and of the shelf (right).
    private fun updateLabels() {
        labels.clear()
        when (station) {
            0 -> {
                val f = filled
                if (f >= 1) {
                    val clip = !log1 && clipped(f)
                    labels += LabelSpec(tr("第 $f 格 · ${bigStr(f)} 粒", "Square $f · ${bigStr(f)} grains"),
                        P3(sqX(f), min(0.43f, sqTag.pos.y + 0.05f), sqTag.pos.z + 0.01f))
                    if (f >= Rice.FIRST_COLUMN) labels += LabelSpec(
                        if (clip) tr("↑ 真高 ${lenStr(Rice.columnM(Chessboard.grainsD(f)))}（截断）", "↑ really ${lenStr(Rice.columnM(Chessboard.grainsD(f)))} (cut)")
                        else lenStr(Rice.columnM(Chessboard.grainsD(f))),
                        if (clip) P3(sqX(f), Y_TOP + H_CLIP - 0.045f, BZ + 4 * SQ + 0.05f) else P3(sqX(f) + 0.11f, Y_TOP + 0.05f, sqZ(f) + 0.03f))
                }
                labels += LabelSpec(if (log1) tr("对数高度：每格 +1 级 = ×2", "Log height: +1 step = ×2 per square")
                    else tr("真实比例 1:1 · 56 cm 以上截断", "True scale 1:1 · cut above 56 cm"), P3(BX, Y_TOP + H_CLIP + 0.025f, BZ - 0.30f))
                labels += LabelSpec(tr("累计 ${if (f <= 40) grp(Chessboard.total(f)) else sci(Chessboard.totalD(f))} 粒", "Total ${if (f <= 40) grp(Chessboard.total(f)) else sci(Chessboard.totalD(f))} grains"),
                    P3(BX, Y_TOP - 0.05f, BZ + 4 * SQ + 0.06f))
            }
            1 -> {
                val n = raceN; val pk = if (polyK == 2) "n²" else "n³"
                labels += LabelSpec("2ⁿ = ${grp(Race.pow2(n))}", P3(x2(n) + 0.10f, Y2 + 0.25f, 0.06f))
                labels += LabelSpec("$pk = ${grp(Race.poly(n, polyK))}", P3(x2(n) + 0.10f, Y2 + 0.15f, 0.06f))
                labels += LabelSpec("n = $n", P3(x2(n) + 0.10f, Y2 + 0.05f, 0.06f))
                labels += LabelSpec(if (log2) tr("对数纵轴：每格 ×10", "Log axis: ×10 per gridline") else tr("纵轴随 2ⁿ 自动缩放", "Axis zooms to fit 2ⁿ"), P3(0.28f, Y2 + H2 + 0.04f, -0.04f))
            }
            2 -> {
                val f = Compound.f(compN)
                labels += LabelSpec("(1 + 1/n)ⁿ = ${fx(f, 5)}", P3(X3_E + 0.02f, Y3 + S3 * f.toFloat() + 0.07f, 0.06f))
                labels += LabelSpec(if (compN == Compound.CONTINUOUS) tr("n → ∞", "n → ∞") else "n = ${grp(compN)} · ${compName(compN)}", P3(-0.38f, Y3 - 0.05f, 0.10f))
                labels += LabelSpec(tr("本金 1", "principal 1"), P3(X3_0 + 0.08f, Y3 + S3 / 2, D3 / 2 + 0.03f))
            }
            else -> {
                val r = r0.toDouble(); val rs = fx(r, 2).trimEnd('0').trimEnd('.')
                labels += LabelSpec("R0 = $rs", P3(R0_X0 + (r0 - 0.5f) * R0_M, R0_Y + 0.06f, R0_Z + 0.02f))
                labels += LabelSpec(tr("第 6 代 ≈ ${sig3(Spread.expected(r, 6))} 人", "gen 6 ≈ ${sig3(Spread.expected(r, 6))} cases"), P3(X4 + 0.50f, Y4 + 0.42f, 0f))
                labels += LabelSpec(tr("淡色 = 不足 1 人的平均值", "faint = a fraction of a case (average)"), P3(X4 + 0.42f, Y4 - 0.02f, 0.06f))
            }
        }
    }

    // ================================================================ interaction

    private val TOK_NAMES = listOf("Tok0", "Tok1", "Tok2", "Tok3")
    private fun stationTargets(s: Int): List<String> = when (s) {
        0 -> listOf("SqNext", "KnobN", "LogTog")
        1 -> listOf("RaceNext", "PolyTog", "LogTog")
        2 -> listOf("CompKnob", "TimeKnob")
        else -> listOf("Case_0", "R0Knob")
    }
    /** Every pinchable node of station [s] (and the four plaques), for tests. */
    fun allTargets(s: Int) = stationTargets(s) + TOK_NAMES

    override val tapTargets: List<TapTarget> get() {
        val t = ArrayList<TapTarget>()
        when (station) {
            0 -> {
                t += TapTarget("SqNext", "下一格 → 放米", "Next square → fill")
                t += TapTarget("KnobN", "n 把手 → +8 格", "n knob → +8")
                t += TapTarget("LogTog", "刻度 → 线性/对数", "Scale → lin/log") { log1 }
            }
            1 -> {
                t += TapTarget("RaceNext", "赛跑 → 下一步", "Race → next step")
                t += TapTarget("PolyTog", "n² 牌 → 换 n³", "n² tag → swap n³") { polyK == 3 }
                t += TapTarget("LogTog", "刻度 → 线性/对数", "Scale → lin/log") { log2 }
            }
            2 -> {
                t += TapTarget("CompKnob", "n 把手 → 下一档", "n knob → next")
                t += TapTarget("TimeKnob", "时间 → 走完一年", "Time → play a year") { yearPlay }
            }
            else -> {
                t += TapTarget("Case_0", "零号病例 → 重新传播", "Case zero → spread again") { spreadPlay }
                t += TapTarget("R0Knob", "R0 把手 → 下一档", "R0 knob → next")
            }
        }
        t += TapTarget("Tok0", "① 棋盘", "① Board", enabled = { station != 0 }, why = Bi("已经在这一站：① 棋盘", "Already on this station: ① Board")) { station == 0 }
        t += TapTarget("Tok1", "② 赛跑", "② Race", enabled = { station != 1 }, why = Bi("已经在这一站：② 赛跑", "Already on this station: ② Race")) { station == 1 }
        t += TapTarget("Tok2", "③ 复利", "③ Interest", enabled = { station != 2 }, why = Bi("已经在这一站：③ 复利", "Already on this station: ③ Interest")) { station == 2 }
        t += TapTarget("Tok3", "④ 传播", "④ Spread", enabled = { station != 3 }, why = Bi("已经在这一站：④ 传播", "Already on this station: ④ Spread")) { station == 3 }
        return t
    }

    override fun isTarget(name: String) = name in stationTargets(station) || name in TOK_NAMES

    private val COMP_PRESETS = longArrayOf(1L, 2L, 4L, 12L, 52L, 365L, 1000L, 8760L, Compound.CONTINUOUS)
    private val R0_PRESETS = floatArrayOf(0.8f, 1.0f, 1.5f, 2.0f)

    override fun onTap(name: String) {
        when {
            name.startsWith("Tok") -> name.removePrefix("Tok").toIntOrNull()?.let { selectStation(it) }
            name == "SqNext" -> fillTo(if (filled < 64) filled + 1 else 0)
            name == "KnobN" -> fillTo(if (filled >= 64) 0 else min(64, (filled / 8 + 1) * 8))
            name == "LogTog" -> if (station == 1) useLog2(!log2) else useLog1(!log1)
            name == "RaceNext" -> raceTo(if (raceN >= Race.N_MAX) 1 else raceN + 1)
            name == "PolyTog" -> { polyK = if (polyK == 2) 3 else 2; bump() }
            name == "CompKnob" -> { val i = COMP_PRESETS.indexOfFirst { it == compN }; periodsTo(COMP_PRESETS[if (i < 0) 0 else (i + 1) % COMP_PRESETS.size]) }
            name == "TimeKnob" -> playYear()
            name == "Case_0" -> playSpread()
            name == "R0Knob" -> { val i = R0_PRESETS.indexOfFirst { abs(it - r0) < 1e-3f }; r0To(R0_PRESETS[if (i < 0) 0 else (i + 1) % R0_PRESETS.size]) }
        }
    }

    override fun tapMessage(name: String): String? = when {
        name.startsWith("Tok") -> when (station) {
            0 -> tr("① 米粒棋盘：每格翻倍，真实比例", "① Rice on a chessboard: doubling, true scale")
            1 -> tr("② 赛跑：n、n² 与 2ⁿ，谁会赢？", "② The race: n, n² and 2ⁿ — who wins?")
            2 -> tr("③ 复利：(1 + 1/n)ⁿ 爬向 e", "③ Compounding: (1 + 1/n)ⁿ climbs to e")
            else -> tr("④ 传播：每人传 R0 人", "④ Spread: each case infects R0 more")
        }
        name == "SqNext" || name == "KnobN" -> if (filled == 0) tr("清空棋盘", "Board cleared")
            else tr("第 $filled 格：${bigStr(filled)} 粒 = ${massStr(Rice.massKg(Chessboard.grainsD(filled)))}", "Square $filled: ${bigStr(filled)} grains = ${massStr(Rice.massKg(Chessboard.grainsD(filled)))}")
        name == "LogTog" -> if ((station == 1 && log2) || (station == 0 && log1)) tr("对数高度：每 ×2 升一级", "Log height: one step per ×2")
            else tr("线性高度（真实比例）", "Linear height (true scale)")
        name == "RaceNext" -> tr("n = $raceN：2ⁿ = ${grp(Race.pow2(raceN))}", "n = $raceN: 2ⁿ = ${grp(Race.pow2(raceN))}")
        name == "PolyTog" -> if (polyK == 3) tr("对手换成 n³", "Opponent is now n³") else tr("对手换回 n²", "Opponent is n² again")
        name == "CompKnob" -> if (compN == Compound.CONTINUOUS) tr("连续复利：e", "Continuous: e")
            else tr("n = ${grp(compN)}：${fx(Compound.f(compN), 5)}", "n = ${grp(compN)}: ${fx(Compound.f(compN), 5)}")
        name == "TimeKnob" -> tr("播放一年：每期付息，利息也生息", "Playing the year: interest earns interest")
        name == "Case_0" -> tr("从零号病例重新传播", "Spreading again from case zero")
        name == "R0Knob" -> tr("R0 = ${fx(r0, 1)}", "R0 = ${fx(r0, 1)}")
        else -> null
    }

    // ---- drag: the n knob up/down, the race knob sideways, the compounding knob up/down, time and R0 sideways
    override fun isDragTarget(name: String) = when (station) {
        0 -> name == "KnobN"
        1 -> name == "RaceNext"
        2 -> name == "CompKnob" || name == "TimeKnob"
        else -> name == "R0Knob"
    }

    override fun onDragStart(name: String) {
        dragAcc = 0f; dragI0 = when (name) { "KnobN" -> filled; "RaceNext" -> raceN; else -> 0 }
        dragL0 = if (compN == Compound.CONTINUOUS) 10_000L else compN
        dragF0 = if (name == "TimeKnob") yearT else r0
        if (name == "KnobN") auto1 = false
        if (name == "RaceNext") auto2 = false
        if (name == "TimeKnob") yearPlay = false
    }

    override fun onDrag(name: String, dx: Float, dy: Float, dz: Float) {
        when (name) {
            "KnobN" -> {
                // the knob rides the log rail: one square per doubling step (8.5 mm), with hysteresis against hand jitter
                dragAcc += dy
                val want = dragI0 + dragAcc / K_LOG
                if (abs(want - filled) > 0.7f) fillTo(want.roundToInt().coerceIn(0, 64))
            }
            "RaceNext" -> {
                dragAcc += dx
                val want = dragI0 + dragAcc / DX2
                if (abs(want - raceN) > 0.7f) raceTo(want.roundToInt().coerceIn(1, Race.N_MAX))
            }
            "CompKnob" -> {
                // up = more periods: ×2 per 3 cm of hand travel
                dragAcc += dy
                periodsTo((dragL0 * 2.0.pow(dragAcc / 0.03)).roundToLong().coerceIn(1L, 10_000L))
            }
            "TimeKnob" -> { dragAcc += dx; yearT = (dragF0 + dragAcc / (X3_1 - X3_0)).coerceIn(0f, 1f) }
            "R0Knob" -> { dragAcc += dx; r0To(((dragF0 + dragAcc / R0_M) * 20f).roundToInt() / 20f) }
        }
    }

    override fun onDragEnd(name: String) { bump() }

    // ---- actions (also what the panel, the lesson autoplay and the tests call)
    fun selectStation(s: Int) { station = s.coerceIn(0, 3); bump() }
    fun fillTo(v: Int) { filled = v.coerceIn(0, 64); bump() }
    fun useLog1(on: Boolean) { log1 = on; bump() }
    fun useLog2(on: Boolean) { log2 = on; bump() }
    fun raceTo(v: Int) { raceN = v.coerceIn(1, Race.N_MAX); bump() }
    fun periodsTo(v: Long) { compN = if (v == Compound.CONTINUOUS) v else v.coerceIn(1L, 10_000L); bump() }
    fun r0To(v: Float) { r0 = v.coerceIn(0.5f, 2.0f); bump() }
    fun playYear() { yearT = 0f; yearPlay = true; bump() }
    fun playSpread() { spreadG = 0f; spreadPlay = true; bump() }
    fun toggleAuto1() { if (!auto1 && filled >= 64) filled = 0; auto1 = !auto1; autoT1 = 0f; bump() }
    fun toggleAuto2() { if (!auto2 && raceN >= Race.N_MAX) raceN = 1; auto2 = !auto2; autoT2 = 0f; bump() }

    /** Finish every animation now (review renders, previews). */
    fun settle() {
        shown = filled.toFloat(); for (n in 1..64) grow[n] = if (n <= filled) 1f else 0f; morph1 = if (log1) 1f else 0f
        for (n in 1..Race.N_MAX) barGrow[n] = if (n <= raceN) 1f else 0f; scaleL = log2Max(raceN).toFloat(); morph2 = if (log2) 1f else 0f
        yearT = 1f; yearPlay = false; spreadG = Spread.GENERATIONS.toFloat(); spreadPlay = false
        auto1 = false; auto2 = false
    }

    // ================================================================ guided track (导学)

    // ---- background card (📖): context for a viewer who has never met this topic
    override val about = About(listOf(
        About.Page(
            Bi("这个实验在做什么？", "What is this experiment?"),
            Bi("四个站（右侧四块牌切换）：① 真实比例的米粒棋盘，每格米翻倍；② n、n²、2ⁿ 三根柱子赛跑；③ 存钱复利，一年分几次付息；④ 一个病例传给 R0 个人，一代代扩散。捏发光的“下一格”或拖小球把手往前走；捏「lin | log」牌在普通刻度和对数刻度间切换。", "Four stations (switch with the plaques on the right): 1) a rice chessboard at true scale, doubling on every square; 2) a race between n, n² and 2ⁿ; 3) compound interest, paid in more and more instalments a year; 4) an infection where each case passes it to R0 people. Pinch the glowing 'next square' or drag the ball knobs to step on; pinch the 'lin | log' plaque to switch to a log scale."),
        ),
        About.Page(
            Bi("国王的米", "The king's rice"),
            Bi("传说国际象棋的发明者向国王讨赏：第 1 格放 1 粒米，第 2 格 2 粒，以后每格翻倍。国王笑着答应，结果全国粮仓都不够。这是传说，但算术是真的。1683 年，雅各布·伯努利研究复利，发现付息次数越多，结果越逼近一个固定的数，后来记作 e ≈ 2.718。", "A legend says the inventor of chess asked a king for 1 grain of rice on the first square, 2 on the second, doubling each time. The king laughed and agreed, and his granaries ran dry. It is a legend, but the arithmetic is real. In 1683 Jacob Bernoulli, studying compound interest, found that paying interest more and more often approaches a fixed limit, later called e ≈ 2.718."),
        ),
        About.Page(
            Bi("核心：加 vs 乘", "Key idea: add vs multiply"),
            Bi("线性增长每一步“加”同样多，指数增长每一步“乘”同样的倍数。一开始看不出差别，后来天差地别。想象一张 0.1 毫米厚的纸，每对折一次厚度翻倍：折 10 次约 10 厘米，折 42 次就超过地球到月球的距离。我们的直觉习惯按“加”来想，所以总会低估翻倍。", "Linear growth adds the same amount each step; exponential growth multiplies by the same factor. At first you can't tell them apart; later they are worlds apart. Picture paper 0.1 mm thick that doubles with every fold: 10 folds make about 10 cm, and 42 folds would reach past the Moon. Our intuition thinks in 'add', so it always underestimates doubling."),
        ),
        About.Page(
            Bi("怎么看这个 3D 场景", "How to read the 3-D scene"),
            Bi("① 白色小颗粒 = 真实大小的米粒；米柱从米白、金、橙、红变到紫，表示格子越来越靠后；柱顶发光的“天花板”加箭头 = 太高画不下，读数给出真实高度。② 蓝柱 = n，绿柱 = n²，橙柱 = 2ⁿ。③ 一层层绿色 = 每次付的利息，粉色横线 = e。④ 小球 = 病例，由黄变红 = 第几代，连线 = 谁传给了谁。", "1) White specks = real-size rice grains; columns shift from cream to gold, orange, red and purple as the square number climbs; a glowing ceiling with an arrow = too tall to draw, and the readout gives the true height. 2) Blue bar = n, green = n², orange = 2ⁿ. 3) Each green layer = one interest payment; the pink line = e. 4) Balls = cases, yellow to red by generation; lines = who infected whom."),
        ),
        About.Page(
            Bi("一个关键数字", "One number to remember"),
            Bi("第 20 格已有 52 万多粒米（约 13 千克），米柱 4.2 米高；可前 13 格加起来才 0.2 千克。第 64 格是 2⁶³ ≈ 9.2×10¹⁸ 粒，约等于全世界 440 年的大米产量。换成对数刻度，这条爆炸的曲线变成一道笔直的斜坡：每格都 ×2，每级台阶一样高。", "Square 20 already holds over 520,000 grains (about 13 kg), a column 4.2 m tall, while squares 1 to 13 together hold just 0.2 kg. Square 64 holds 2⁶³ ≈ 9.2 × 10¹⁸ grains, about 440 years of the world's rice harvest. On a log scale the explosion becomes a straight ramp: every square is ×2, so every step is the same height."),
        ),
        About.Page(
            Bi("今天在哪里？", "Where is it today?"),
            Bi("信用卡欠款和存款利息都按复利滚动。疫情里 R0 大于 1 就越滚越大，小于 1 就自己熄灭 —— 这就是“压低传播”的意义。芯片上的晶体管数几十年来大约每两年翻一番（摩尔定律）。反过来，放射性原子每过一个半衰期减半，碳-14 测年就靠它。", "Card debt and savings grow by compound interest. In an epidemic, R0 above 1 snowballs and below 1 burns out on its own, which is why slowing the spread matters. Transistors on a chip roughly doubled every two years for decades (Moore's law). Run backwards, radioactive atoms halve every half-life, and carbon-14 dating relies on it."),
        ),
    ))

    override val lesson: Lesson by lazy {
        Lesson(
            listOf(
                LessonStep(
                    say = Bi("① 米粒棋盘：第 1 格放 1 粒米，以后每格翻倍。你猜第 64 格上一共有多少米？", "① Rice on a chessboard: 1 grain on square 1, doubling on every square. How much rice is on square 64?"),
                    options = listOf(Bi("一袋（约 25 kg）", "A bag (about 25 kg)"), Bi("一卡车（约 25 吨）", "A truckload (about 25 t)"), Bi("全世界几百年的大米产量", "Centuries of the world's rice harvest")),
                    answer = 2,
                    onEnter = { if (station != 0) selectStation(0) },
                ),
                LessonStep(
                    say = Bi("捏棋盘上发光的「下一格」（或拖左边的 n 把手），一直填到第 20 格。", "Pinch the glowing 'next square' on the board (or drag the n knob on the left) until square 20 is filled."),
                    target = "SqNext",
                    onEnter = { selectStation(0); if (log1) useLog1(false); if (filled >= 20) fillTo(16) },
                    done = { station == 0 && filled >= 20 && grow[20] > 0.95f },
                    autoplay = { selectStation(0); fillTo(20) },
                    insight = Bi("第 20 格已有 524,288 粒（13 kg），米柱 4.2 m 高，超过一个人 —— 可前 13 格加起来才 0.2 kg。", "Square 20 already holds 524,288 grains (13 kg), a 4.2 m column taller than a person — yet squares 1–13 together hold just 0.2 kg."),
                ),
                LessonStep(
                    say = Bi("真实比例已经画不下了。捏「lin | log」刻度牌切到对数高度，再一路填满 64 格。", "True scale no longer fits. Pinch the 'lin | log' plaque to switch to log height, then fill all 64 squares."),
                    target = "LogTog",
                    onEnter = { selectStation(0) },
                    done = { station == 0 && log1 && filled == 64 && morph1 > 0.95f && grow[64] > 0.95f },
                    autoplay = { selectStation(0); useLog1(true); fillTo(64) },
                    reveal = true,
                    insight = Bi("第 64 格：2⁶³ ≈ 9.2×10¹⁸ 粒 ≈ 2.3×10¹¹ 吨 ≈ 全世界 440 年的大米；整盘 2⁶⁴ − 1 粒 ≈ 890 年（FAO：每年约 5.2 亿吨）。在对数轴上它只是一道笔直的斜坡：每格都 ×2。", "Square 64: 2⁶³ ≈ 9.2×10¹⁸ grains ≈ 2.3×10¹¹ t ≈ 440 years of the world's rice; the whole board 2⁶⁴ − 1 ≈ 890 years (FAO: ≈ 520 Mt a year). On a log axis it is just a straight ramp: ×2 per square."),
                ),
                LessonStep(
                    say = Bi("② 赛跑：n、n²、2ⁿ 三根柱子一起长。现在 n = 3，n² 还领先（9 > 8）。2ⁿ 从哪一步起永远超过 n²？", "② The race: n, n² and 2ⁿ grow side by side. At n = 3, n² leads (9 > 8). From which n on does 2ⁿ stay ahead of n² for good?"),
                    options = listOf(Bi("从 n = 4 起", "From n = 4"), Bi("从 n = 5 起", "From n = 5"), Bi("从 n = 10 起", "From n = 10"), Bi("永远追不上", "Never")),
                    answer = 1,
                    onEnter = { selectStation(1); polyK = 2; useLog2(false); raceTo(3) },
                ),
                LessonStep(
                    say = Bi("捏柱子右边的蓝色把手（或拖着它往右走），一直走到 n = 8，看谁领先。", "Pinch the blue knob right of the bars (or drag it to the right) and step on to n = 8. Who leads?"),
                    target = "RaceNext",
                    onEnter = { selectStation(1) },
                    done = { station == 1 && raceN >= 8 && barGrow[8] > 0.95f },
                    autoplay = { selectStation(1); raceTo(8) },
                    reveal = true,
                    insight = Bi("n = 2、4 打平，n = 3 时 n² 领先；从 n = 5 起 2ⁿ 永远领先：它每步 ×2，而 n² 每步最多 ×1.44，还越来越接近 ×1。到 n = 20：1,048,576 对 400。", "Ties at n = 2 and 4, n² ahead at 3; from n = 5 on 2ⁿ leads forever: it doubles each step while n² grows at most ×1.44, ever closer to ×1. At n = 20: 1,048,576 vs 400."),
                ),
            ),
            challenge = Challenge(
                say = Bi("③ 复利（第三块牌）：年利率 100 %，一年分 n 次付息。把 n 调大，让结果与 e 相差不到 0.1 % —— 每天复利够吗？", "③ Compounding (third plaque): 100 % a year paid in n instalments. Raise n until the result is within 0.1 % of e — is daily enough?"),
                progress = {
                    val n = compN
                    if (n == Compound.CONTINUOUS) tr("连续复利正好是 e —— 换一个有限的 n 试试", "Continuous gives exactly e — try a finite n")
                    else tr("n = ${grp(n)}：${fx(Compound.f(n), 5)}，比 e 少 ${fx(Compound.shortfall(n) * 100, 3)} %（目标 < 0.1 %）",
                        "n = ${grp(n)}: ${fx(Compound.f(n), 5)}, ${fx(Compound.shortfall(n) * 100, 3)} % short of e (goal < 0.1 %)")
                },
                done = { station == 2 && compN != Compound.CONTINUOUS && Compound.shortfall(compN) < 1e-3 },
                autoplay = { selectStation(2); periodsTo(1000L) },
            ),
        )
    }

    // ================================================================ review states

    override fun reviewVariants(): Map<String, () -> Unit> = mapOf(
        "board_full" to { selectStation(0); useLog1(false); fillTo(64); settle() },
        "board_log" to { selectStation(0); useLog1(true); fillTo(64); settle() },
        "race" to { selectStation(1); polyK = 2; useLog2(false); raceTo(8); settle() },
        "race_log" to { selectStation(1); polyK = 2; useLog2(true); raceTo(20); settle() },
        "interest" to { selectStation(2); periodsTo(12L); settle() },
        "spread" to { selectStation(3); r0To(1.5f); settle() },
    )

    // ================================================================ panel

    @Composable
    override fun Panel() {
        @Suppress("UNUSED_VARIABLE") val epoch = panelEpoch
        val st by FieldRef(::station)
        LabPanel(readout) {
            LabRow {
                SegmentControl {
                    SegmentItem(selected = speed == 0, onClick = { speed = 0; bump() }, title = { Text(tr("慢", "Slow")) })
                    SegmentItem(selected = speed == 1, onClick = { speed = 1; bump() }, title = { Text(tr("中", "Medium")) })
                    SegmentItem(selected = speed == 2, onClick = { speed = 2; bump() }, title = { Text(tr("快", "Fast")) })
                }
                when (st) {
                    0 -> {
                        Button(onClick = { toggleAuto1() }) { Text(if (auto1) tr("⏸ 暂停", "⏸ Pause") else tr("▶ 自动填", "▶ Auto-fill")) }
                        Button(onClick = { fillTo(0) }, enabled = filled > 0) { Text(tr("清空", "Clear")) }
                        LabSlider(tr("格数", "Squares"), filled.toFloat(), 0f..64f, "$filled", width = 380) { fillTo(it.roundToInt()) }
                    }
                    1 -> {
                        Button(onClick = { toggleAuto2() }) { Text(if (auto2) tr("⏸ 暂停", "⏸ Pause") else tr("▶ 赛跑", "▶ Run")) }
                        LabSlider("n", raceN.toFloat(), 1f..Race.N_MAX.toFloat(), "$raceN", width = 380) { raceTo(it.roundToInt()) }
                    }
                    2 -> {
                        Button(onClick = { playYear() }) { Text(tr("▶ 一年", "▶ One year")) }
                        LabSwitch(tr("连续 ∞", "Continuous ∞"), compN == Compound.CONTINUOUS) { periodsTo(if (it) Compound.CONTINUOUS else 365L) }
                        LabSlider("n", if (compN == Compound.CONTINUOUS) 4f else log10(compN.toFloat()), 0f..4f,
                            if (compN == Compound.CONTINUOUS) "∞" else grp(compN), width = 360) { periodsTo(10f.pow(it).roundToLong()) }
                    }
                    else -> {
                        Button(onClick = { playSpread() }) { Text(tr("▶ 传播", "▶ Spread")) }
                        LabSlider("R0", r0, 0.5f..2.0f, fx(r0, 2), width = 380) { r0To((it * 20f).roundToInt() / 20f) }
                    }
                }
            }
        }
    }
}
