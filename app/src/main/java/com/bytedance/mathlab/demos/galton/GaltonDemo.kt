// 高尔顿板 · 中心极限定理 · Galton Board · Central Limit Theorem (Galton, "Natural Inheritance", 1889).
//
// THE AHA: each ball makes many tiny independent left/right choices; adding them up ALWAYS piles into a bell —
// whatever the single choice looks like (a coin, a fair die, a loaded die, a die that only shows 1 or 6).
//   ① The board: an upright glass Galton board, 8–16 rows of glowing pegs, a funnel. Balls (≤ 220 pooled nodes,
//      one shared mesh) hop peg to peg — a stylised model: each peg is a coin flip with P(right) = p, the hop is
//      a smooth arc, not rigid-body physics — and stack in the bins. Each drawn ball in a stack stands for u real
//      balls (u = 1, 2, 5, 10 …, shown in the readout) so 10⁴ balls still fit. Overlays: the exact pmf B(n, p)
//      as a gold step outline and the normal density N(np, np(1−p)) as a pink curve. Tilt the board (drag the
//      tilt knob) to bias every peg: p = 0.5 + 0.45·θ/10° (a stylised mapping); the peak walks.
//   ② Sums of anything: the exact distribution of the SUM of k dice for k = 1, 2, 3, 5, 10, 30 as a 3-D wall of
//      histograms (one row per k, rising toward the back), each standardised (S − kμ)/(σ√k) and drawn as a
//      density so the same N(0, 1) curve overlays every row; the KS distance to the normal shrinks with k for a
//      fair, a loaded (1:2:4:8:16:32) and a two-valued {1, 6} die. Exact convolutions from core/galton.
//
// Everything moves by node transforms (pooled balls, stack slots, pegs, bars, curve segments); the only texture
// is one text atlas baked once (GaltonText + BakeCache). No mesh is rebuilt after buildSpec.

package com.bytedance.mathlab.demos.galton

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.bytedance.mathlab.core.P3
import com.bytedance.mathlab.core.galton.Binomial
import com.bytedance.mathlab.core.galton.Die
import com.bytedance.mathlab.core.galton.GaltonSim
import com.bytedance.mathlab.core.galton.Normal
import com.bytedance.mathlab.core.galton.Rng
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
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object GaltonDemo : SpecDemo() {
    override val id = "galton"
    override val title = "高尔顿板 · 中心极限定理"  // i18n-ok (zh half of DemoRegistry.titleOf; English in TITLE_EN)
    override val credit = "Galton · 1889"

    // ================================================================ layout (volume metres)
    private const val PLINTH_Y = -0.262f
    // ① the board (pivot = tilt axis; children are placed relative to it)
    private const val BX = -0.13f
    private const val BY = 0.10f
    private const val DX = 0.045f                      // peg pitch = bin width
    private const val YF = 0.372f                      // funnel exit
    private const val Y0 = 0.334f                      // peg row 0
    private const val SPAN = 0.245f                    // max height of the peg field
    private const val YD = 0.062f                      // top of the bin dividers
    private const val YB = -0.19f                     // bin floor
    private const val RB = 0.0088f                     // ball radius
    private const val RP = 0.0048f                     // peg radius
    private const val REST = RB + RP + 0.0006f
    private const val SLOT_H = 0.0185f
    private const val SLOTS = 13
    private const val N_MAX = 16
    private const val N_MIN = 8
    private const val POOL = 220
    private const val T_ROW = 0.11f                    // seconds per row at "medium"
    private const val FALL_U = 1.7f                    // the last hop + fall into the bin, in row units
    private const val EMIT_RATE = 30f                  // balls per second at "medium"
    private const val TILT_MAX = 8f                   // degrees at p = 0.05 / 0.95
    private const val ROWS_X = 0.46f
    private const val ROWS_Y0 = -0.14f
    private const val ROWS_DY = 0.055f                 // metres per row on the rows rail
    // ② the convergence wall
    private const val WX = -0.13f
    private const val XS = 0.145f                      // metres per standard deviation
    private const val ZMAX = 3.35
    private const val H2 = 0.11f                       // height of the N(0,1) peak
    private const val BAR_D = 0.05f
    private const val CURVE_SEG = 36
    private const val BARS = 64
    private fun rowZ(r: Int) = 0.22f - r * 0.10f
    private fun rowY(r: Int) = -0.172f + r * 0.074f
    // shelves
    private const val SHELF_X = 0.76f
    private val TOK_Y = floatArrayOf(0.31f, 0.17f)
    private val DIE_Y = floatArrayOf(0.03f, -0.075f, -0.18f)

    // ================================================================ colours (sRGB)
    private val WHITE = Rgb(0.97f, 0.97f, 1.0f)
    private val BLUE = Rgb(0.35f, 0.77f, 0.95f)
    private val GOLD = Rgb(1.00f, 0.80f, 0.30f)
    private val PINK = Rgb(1.00f, 0.42f, 0.72f)
    private val ICE = Rgb(0.55f, 0.80f, 1.00f)
    private val DIM = Rgb(0.55f, 0.59f, 0.68f)
    private fun rowCol(r: Int) = ProcTex.okLerp(Rgb(0.66f, 0.46f, 1.0f), Rgb(0.28f, 0.92f, 0.80f), r / 5f)
    private fun glow(c: Rgb, k: Float = 0.30f) = Rgb(c.r * k, c.g * k, c.b * k)
    private fun solid(c: Rgb, e: Float = 0.30f, rough: Float = 0.36f) = Mat.Pbr(c, metallic = 0f, roughness = rough, emissive = glow(c, e))
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.32f)
    private val plateOff = Mat.Pbr(Rgb(0.10f, 0.11f, 0.13f), metallic = 0.3f, roughness = 0.35f)
    private val plateOn = Mat.Pbr(Rgb(0.16f, 0.24f, 0.40f), metallic = 0.2f, roughness = 0.30f, emissive = Rgb(0.10f, 0.24f, 0.50f))

    // ================================================================ text atlas
    private const val TEX_VERSION = 1
    private const val EM_PX = 56
    private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)
    private fun f3(v: Double) = String.format(Locale.US, "%.3f", v)
    private fun grp(v: Long) = String.format(Locale.US, "%,d", v)
    private fun pKey(p100: Int) = "p = " + f2(p100 / 100.0)
    private fun ksKey(d: Int, r: Int) = "KS " + f3(KS_TABLE[d][r])

    /** KS distance of every (die, k) — exact convolution, computed once (cheap: k ≤ 30). */
    private val KS_TABLE: Array<DoubleArray> by lazy { Array(3) { d -> DoubleArray(6) { r -> Die.ALL[d].ks(Die.KS[r]) } } }

    private val ATLAS_ITEMS: List<GaltonAtlas.Item> by lazy {
        val keys = LinkedHashMap<String, GaltonAtlas.Item>()
        fun add(t: String, c: Rgb = WHITE) { if (t !in keys) keys[t] = GaltonAtlas.Item(t, listOf(GaltonText.Span(t, c)), EM_PX) }
        for (p in 5..95) add(pKey(p))
        for (n in N_MIN..N_MAX) add("n = $n")
        for (k in Die.KS) add("k = $k")
        for (d in 0..2) for (r in 0..5) add(ksKey(d, r))
        add("B(n, p)", GOLD); add("N(μ, σ^2)", PINK); add("N(0, 1)", PINK)
        keys.values.toList()
    }
    private lateinit var atlas: GaltonAtlas
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

    // ================================================================ state
    /** 0 ① board, 1 ② sums of dice. */
    var station = 0
    /** 0 slow, 1 medium, 2 fast. */
    var speed = 1
    private val SPEEDS = floatArrayOf(0.35f, 1f, 2.5f)
    private val spd get() = SPEEDS[speed]
    var rows = 12
    /** P(right) at every peg, in hundredths (0.05 … 0.95). */
    var p100 = 50
    val p: Double get() = p100 / 100.0
    var overlay = true
    var burst = 10
    private var counts = LongArray(N_MAX + 1)
    /** Balls currently in the bins. */
    var landed = 0L; private set
    /** Every ball that ever landed (lesson progress; never reset). */
    var landedEver = 0L; private set
    private var pending = 0
    private var emitAcc = 0f
    private var unit = 1L
    private var stacksDirty = true
    private var tiltShown = Float.NaN
    private var rowsShown = -1
    // live balls
    private val active = BooleanArray(POOL)
    private val bits = IntArray(POOL)
    private val uRow = FloatArray(POOL)
    private val ballRows = IntArray(POOL)
    private var sim = GaltonSim(SEED)
    private var rng = Rng(SEED + 1)
    // ②
    var die = 0
    var kIdx = 5
    private val barH = Array(6) { FloatArray(BARS) }
    private var dieShown = -1
    private var clock = 0f
    private var dragAcc = 0f
    private var dragP0 = 50
    private var dragI0 = 0
    private var panelEpoch by mutableIntStateOf(0)
    private fun bump() { panelEpoch++ }
    private const val SEED = 1889L

    private fun resetState() {
        station = 0; speed = 1; rows = 12; p100 = 50; overlay = true; burst = 10
        counts = LongArray(N_MAX + 1); landed = 0L; landedEver = 0L; pending = 0; emitAcc = 0f; unit = 1L
        stacksDirty = true; tiltShown = Float.NaN; rowsShown = -1
        active.fill(false); bits.fill(0); uRow.fill(0f); ballRows.fill(0)
        sim = GaltonSim(SEED); rng = Rng(SEED + 1)
        die = 0; kIdx = 5; barH.forEach { it.fill(0f) }; dieShown = -1
        clock = 0f; dragAcc = 0f; dragP0 = 50; dragI0 = 0
        decalKey.clear(); textMats.clear()
    }

    // ================================================================ nodes
    private lateinit var unitBox: MeshData
    private lateinit var ball: MeshData
    private lateinit var pegMesh: MeshData
    private lateinit var cyl: MeshData
    private lateinit var cone: MeshData
    private lateinit var groups: Array<Node>
    private lateinit var tokens: Array<Node>
    private lateinit var plates: Array<Node>
    private lateinit var poolGlow: Node
    // ①
    private lateinit var board: Node
    private lateinit var backGlass: Node
    private lateinit var railL: Node
    private lateinit var railR: Node
    private lateinit var floorBar: Node
    private lateinit var pegs: Array<Node>             // row-major, row r has r + 1 pegs
    private lateinit var dividers: Array<Node>
    private lateinit var stacks: Array<Array<Node>>    // [bin][slot]
    private lateinit var balls: Array<Node>
    private lateinit var tops: Array<Node>             // pmf outline, horizontal caps
    private lateinit var risers: Array<Node>           // pmf outline, verticals
    private lateinit var curve: Array<Node>            // normal density
    private lateinit var funnel: Node
    private lateinit var tiltKnob: Node
    private lateinit var pTag: Node
    private lateinit var legendB: Node
    private lateinit var legendN: Node
    private lateinit var rowsKnob: Node
    private lateinit var nTag: Node
    // ②
    private lateinit var bars: Array<Array<Node>>      // [row][bar]
    private lateinit var barMatOn: Array<Mat>
    private lateinit var barMatOff: Array<Mat>
    private lateinit var rowBase: Array<Node>
    private lateinit var ksTags: Array<Node>
    private lateinit var kTags: Array<Node>
    private lateinit var kKnob: Node
    private lateinit var dieTok: Array<Node>
    private lateinit var diePlates: Array<Node>
    /** Per row: (x, width, density) of every drawn bar for the current die. */
    private var barGeo: Array<FloatArray> = emptyArray()

    // ================================================================ small node helpers
    private fun Node.at(x: Float, y: Float, z: Float) { val q = pos; if (q.x != x || q.y != y || q.z != z) pos = P3(x, y, z) }
    private fun Node.dims(x: Float, y: Float, z: Float) { val s = scale; if (s.x != x || s.y != y || s.z != z) scale = P3(x, y, z) }
    private fun Node.turn(q: Q) { if (rot != q) rot = q }
    private fun Node.show(v: Boolean) { visible = v }
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

    // board geometry (board-local: relative to the pivot (BX, BY, 0))
    private fun dyRow(n: Int) = min(0.030f, SPAN / (n - 1))
    private fun pegX(r: Int, j: Int) = (j - r / 2f) * DX
    private fun pegY(r: Int, n: Int) = Y0 - r * dyRow(n) - BY
    private fun binX(k: Int, n: Int) = (k - n / 2f) * DX
    private fun halfW(n: Int) = (n + 1) / 2f * DX
    private fun stackTop(k: Int): Float = YB - BY + visibleIn(k) * SLOT_H
    private fun visibleIn(k: Int): Int = if (unit <= 0L) 0 else min(SLOTS.toLong(), (counts[k] + unit / 2) / unit).toInt()

    // ================================================================ build

    override fun buildSpec(cacheDir: File): Node {
        resetState()
        val imgs = BakeCache.load(cacheDir, "galton_text", TEX_VERSION) {
            listOf(GaltonAtlas.bakeImage(ATLAS_ITEMS), ProcTex.bakePlanet(256, 128, Rgb(0.07f, 0.075f, 0.085f), Rgb(0.12f, 0.125f, 0.14f), Rgb(0.20f, 0.205f, 0.22f), 0f, 77))
        }
        atlas = GaltonAtlas.of(imgs[0], ATLAS_ITEMS)
        unitBox = MeshGen.box(1f, 1f, 1f)
        ball = MeshGen.sphere(1f, 10, 16, MeshGen.SphereUv.EQUIRECT)
        pegMesh = MeshGen.sphere(1f, 6, 10, MeshGen.SphereUv.EQUIRECT)
        cyl = MeshGen.cylinder(1f, 1f, 10)
        cone = coneMesh(18)

        val root = Node("GaltonRoot")
        buildBench(root, imgs[1])
        groups = Array(2) { k -> root.add(Node("St${k + 1}")) }
        build1(groups[0]); build2(groups[1])
        buildTokens(root)
        // a welcoming first look: 600 balls already in the bins, a burst on its way
        fastFill(600); pending = 30
        update(0f)
        return root
    }

    private fun coneMesh(seg: Int): MeshData {
        // apex at y = 1, base radius 1 at y = 0 (open-ish funnel when flipped)
        val pos = ArrayList<Float>(); val nrm = ArrayList<Float>(); val uv = ArrayList<Float>(); val idx = ArrayList<Int>()
        fun v(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float): Int { pos += x; pos += y; pos += z; nrm += nx; nrm += ny; nrm += nz; uv += 0.5f; uv += 0.5f; return pos.size / 3 - 1 }
        for (s in 0 until seg) {
            val a0 = 2.0 * PI * s / seg; val a1 = 2.0 * PI * (s + 1) / seg; val am = (a0 + a1) / 2
            val c0 = cos(a0).toFloat(); val s0 = sin(a0).toFloat(); val c1 = cos(a1).toFloat(); val s1 = sin(a1).toFloat()
            val nx = cos(am).toFloat() * 0.707f; val nz = sin(am).toFloat() * 0.707f
            val a = v(c0, 0f, s0, nx, 0.707f, nz); val b = v(0f, 1f, 0f, nx, 0.707f, nz); val c = v(c1, 0f, s1, nx, 0.707f, nz)
            idx += a; idx += b; idx += c
        }
        return MeshData(pos.toFloatArray(), nrm.toFloatArray(), uv.toFloatArray(), idx.toIntArray())
    }

    private fun buildBench(root: Node, slabTex: Image) {
        root.add("Plinth", MeshGen.box(1.74f, 0.035f, 0.92f), Mat.Pbr(Rgb(1f, 1f, 1f), metallic = 0f, roughness = 0.55f, tex = slabTex), P3(0f, PLINTH_Y, -0.04f))
        root.add("PlinthBand", MeshGen.box(1.75f, 0.008f, 0.93f), alu, P3(0f, PLINTH_Y, -0.04f))
        val pool = OptGeo.lumTex(64, 64) { u, v -> val dx = (u - 0.5f) * 2f; val dy = (v - 0.5f) * 2f; val r2 = dx * dx + dy * dy; if (r2 >= 1f) 0f else (1f - r2) * (1f - r2) }
        poolGlow = root.add("Glow", OptGeo.quad(1f, 1f), Mat.Unlit(BLUE, alpha = 0.22f, tex = pool, blend = Blend.ADD)).also {
            it.rot = Q.axisAngle(1f, 0f, 0f, -PI.toFloat() / 2); it.scale = P3(1.20f, 0.66f, 1f); it.pos = P3(BX, PLINTH_Y + 0.019f, 0f)
        }
    }

    // ---------------------------------------------------------------- ① the board
    private fun build1(g: Node) {
        board = g.add(Node("Board")).also { it.pos = P3(BX, BY, 0f) }
        backGlass = board.add("BackGlass", unitBox, Mat.Pbr(Rgb(0.10f, 0.16f, 0.30f), metallic = 0.1f, roughness = 0.08f, alpha = 0.45f, emissive = Rgb(0.02f, 0.04f, 0.09f)))
        railL = board.add("RailL", unitBox, alu); railR = board.add("RailR", unitBox, alu)
        floorBar = board.add("Floor", unitBox, alu)
        val pegMat = Mat.Unlit(Rgb(0.92f, 0.96f, 1.0f), blend = Blend.OPAQUE)
        pegs = Array(N_MAX * (N_MAX + 1) / 2) { i -> board.add("Peg_$i", pegMesh, pegMat).also { it.uniformScale(RP) } }
        val divMat = Mat.Pbr(Rgb(0.70f, 0.85f, 1.0f), metallic = 0.2f, roughness = 0.1f, alpha = 0.55f, emissive = Rgb(0.05f, 0.10f, 0.16f))
        dividers = Array(N_MAX + 2) { i -> board.add("Div_$i", unitBox, divMat) }
        // stacked balls: one shared mesh + material per colour band
        val ballMat = Mat.Pbr(BLUE, metallic = 0.25f, roughness = 0.25f, emissive = glow(BLUE, 0.35f))
        stacks = Array(N_MAX + 1) { k -> Array(SLOTS) { s -> board.add("Stk_${k}_$s", ball, ballMat).also { it.uniformScale(RB); it.visible = false } } }
        val liveMat = Mat.Pbr(Rgb(0.80f, 0.95f, 1.0f), metallic = 0.3f, roughness = 0.2f, emissive = glow(Rgb(0.6f, 0.9f, 1.0f), 0.5f))
        balls = Array(POOL) { i -> board.add("Ball_$i", ball, liveMat).also { it.uniformScale(RB); it.visible = false } }
        val goldMat = Mat.Unlit(GOLD, blend = Blend.ADD)
        tops = Array(N_MAX + 1) { k -> board.add("Top_$k", cyl, goldMat) }
        risers = Array(N_MAX + 2) { k -> board.add("Rise_$k", cyl, goldMat) }
        val pinkMat = Mat.Unlit(PINK, blend = Blend.ADD)
        curve = Array(48) { i -> board.add("Crv_$i", cyl, pinkMat) }
        // funnel (pinch = release a burst) with a few balls waiting in it
        funnel = board.add(Node("Funnel")).also { it.pos = P3(0f, YF - BY + 0.030f, 0f); it.tapRadius = 0.06f }
        funnel.add("FunnelCone", cone, Mat.Pbr(Rgb(0.70f, 0.88f, 1.0f), metallic = 0.1f, roughness = 0.06f, alpha = 0.45f, emissive = Rgb(0.06f, 0.12f, 0.20f))).also {
            it.rot = Q.axisAngle(1f, 0f, 0f, PI.toFloat()); it.pos = P3(0f, 0.032f, 0f); it.scale = P3(0.075f, 0.062f, 0.030f) }
        funnel.add("FunnelRim", cyl, alu).also { it.span(-0.076f, 0.033f, 0f, 0.076f, 0.033f, 0f, 0.003f) }
        for ((i, x) in floatArrayOf(-0.03f, -0.012f, 0.007f, 0.025f, -0.002f).withIndex())
            funnel.add("Wait_$i", ball, ballMat, P3(x, if (i == 4) 0.022f else 0.011f + abs(x) * 0.6f, 0f)).also { it.uniformScale(RB) }
        // tilt knob on the right shoulder (rotates with the board)
        tiltKnob = board.add("TiltKnob", ball, solid(GOLD, 0.45f, 0.22f)).also { it.uniformScale(0.016f); it.tapRadius = 0.05f / 0.016f }
        pTag = decal(board, "PTag", pKey(50), 0.030f, GOLD)
        legendB = decal(board, "LegB", "B(n, p)", 0.030f, GOLD)
        legendN = decal(board, "LegN", "N(μ, σ^2)", 0.030f, PINK)
        // rows rail + knob (outside the board so it does not tilt)
        val railTop = ROWS_Y0 + (N_MAX - N_MIN) * ROWS_DY
        g.add("RowsRail", cyl, alu).span(ROWS_X, ROWS_Y0 - 0.02f, 0f, ROWS_X, railTop + 0.02f, 0f, 0.003f)
        for (n in N_MIN..N_MAX step 2) g.add("RowsT$n", cyl, Mat.Unlit(Rgb(0.80f, 0.84f, 0.92f))).span(ROWS_X - 0.010f, ROWS_Y0 + (n - N_MIN) * ROWS_DY, 0f, ROWS_X + 0.010f, ROWS_Y0 + (n - N_MIN) * ROWS_DY, 0f, 0.0015f)
        rowsKnob = g.add("RowsKnob", ball, solid(ICE, 0.40f, 0.22f)).also { it.uniformScale(0.016f); it.tapRadius = 0.05f / 0.016f }
        nTag = decal(g, "NTag", "n = 12", 0.024f, ICE)
    }

    /** Re-place everything that depends on the number of rows (only when [rows] changes). */
    private fun layoutBoard() {
        val n = rows
        val hw = halfW(n) + 0.012f
        val top = Y0 - BY + 0.018f; val bot = YB - BY - 0.008f
        backGlass.at(0f, (top + bot) / 2 + 0.01f, -0.022f); backGlass.dims(2 * hw + 0.02f, top - bot + 0.02f, 0.006f)
        railL.at(-hw, (top + bot) / 2, 0f); railL.dims(0.008f, top - bot, 0.034f)
        railR.at(hw, (top + bot) / 2, 0f); railR.dims(0.008f, top - bot, 0.034f)
        floorBar.at(0f, bot, 0f); floorBar.dims(2 * hw + 0.008f, 0.008f, 0.034f)
        var i = 0
        for (r in 0 until N_MAX) for (j in 0..r) {
            val pg = pegs[i++]
            if (r < n) { pg.show(true); pg.at(pegX(r, j), pegY(r, n), 0f) } else pg.show(false)
        }
        for (d in dividers.indices) {
            val on = d <= n + 1
            dividers[d].show(on)
            if (on) { val x = (d - (n + 1) / 2f) * DX; dividers[d].at(x, (YD + YB) / 2 - BY, 0f); dividers[d].dims(0.0022f, YD - YB, 0.030f) }
        }
        for (k in 0..N_MAX) for (s in 0 until SLOTS) stacks[k][s].at(binX(k, n), YB - BY + (s + 0.5f) * SLOT_H, 0f)
        tiltKnob.at(hw + 0.034f, top - 0.01f, 0f)
        pTag.at(hw + 0.034f, top + 0.036f, 0f)
        legendB.at(-hw - 0.02f - decalWidth("B(n, p)", 0.030f) / 2, top - 0.02f, 0f)
        legendN.at(-hw - 0.02f - decalWidth("N(μ, σ^2)", 0.030f) / 2, top - 0.065f, 0f)
        rowsKnob.at(ROWS_X, ROWS_Y0 + (n - N_MIN) * ROWS_DY, 0f)
        setDecal(nTag, "n = $n", 0.024f)
        nTag.at(ROWS_X + 0.022f + decalWidth("n = $n", 0.024f) / 2, ROWS_Y0 + (n - N_MIN) * ROWS_DY, 0f)
        rowsShown = n; stacksDirty = true
    }

    // ---------------------------------------------------------------- ② the convergence wall
    private fun build2(g: Node) {
        barMatOn = Array(6) { r -> solid(rowCol(r), 0.55f, 0.30f) }
        barMatOff = Array(6) { r -> Mat.Pbr(ProcTex.okLerp(rowCol(r), Rgb(0.35f, 0.38f, 0.45f), 0.30f), roughness = 0.4f, alpha = 0.72f, emissive = glow(rowCol(r), 0.20f)) }
        bars = Array(6) { r -> Array(BARS) { b -> g.add("Bar_${r}_$b", unitBox, barMatOff[r]).also { it.visible = false } } }
        val baseMat = Mat.Pbr(Rgb(0.09f, 0.10f, 0.13f), metallic = 0.4f, roughness = 0.35f)
        val pinkMat = Mat.Unlit(PINK, blend = Blend.ADD)
        val w = (2 * ZMAX * XS).toFloat() + 0.04f
        rowBase = Array(6) { r -> g.add("RowBase_$r", unitBox, baseMat, P3(WX, rowY(r) - 0.004f, rowZ(r))).also { it.scale = P3(w, 0.008f, BAR_D + 0.02f) } }
        for (r in 0 until 6) {
            // the same N(0, 1) density on every row (the rows are standardised), slightly in front of the bars
            val zc = rowZ(r) + BAR_D / 2 + 0.004f
            for (i in 0 until CURVE_SEG) {
                val a = -ZMAX + 2 * ZMAX * i / CURVE_SEG; val b = -ZMAX + 2 * ZMAX * (i + 1) / CURVE_SEG
                g.add("Gss_${r}_$i", cyl, pinkMat).span(WX + (a * XS).toFloat(), rowY(r) + H2 * exp(-a * a / 2).toFloat(), zc,
                    WX + (b * XS).toFloat(), rowY(r) + H2 * exp(-b * b / 2).toFloat(), zc, 0.0017f)
            }
        }
        kTags = Array(6) { r -> decal(g, "KTag_$r", "k = ${Die.KS[r]}", 0.026f, rowCol(r)).also {
            it.pos = P3(WX - (ZMAX * XS).toFloat() - 0.03f - decalWidth("k = ${Die.KS[r]}", 0.026f) / 2, rowY(r) + 0.012f, rowZ(r) + 0.03f) } }
        ksTags = Array(6) { r -> decal(g, "KsTag_$r", ksKey(0, r), 0.028f, WHITE).also { it.pos = P3(WX + (ZMAX * XS).toFloat() + 0.075f, rowY(r) + 0.012f, rowZ(r) + 0.03f) } }
        decal(g, "LegN01", "N(0, 1)", 0.026f, PINK, P3(WX, rowY(5) + H2 + 0.05f, rowZ(5)))
        kKnob = g.add("KKnob", ball, solid(ICE, 0.45f, 0.22f)).also { it.uniformScale(0.016f); it.tapRadius = 0.05f / 0.016f }
    }

    /** Bars of every row for die [d]: (x, width, density φ-units) triplets, |z| ≤ ZMAX. */
    private fun computeBars(d: Int) {
        val die = Die.ALL[d]
        barGeo = Array(6) { r ->
            val k = Die.KS[r]; val dist = die.sumDist(k)
            val mu = k * die.mean; val sd = sqrt(k * die.variance); val h = die.step.toDouble()
            val out = ArrayList<Float>()
            for (i in dist.indices) {
                if (dist[i] <= 0.0) continue
                val z = (k + i - mu) / sd
                if (abs(z) > ZMAX + 0.5 * h / sd) continue
                out += WX + (z * XS).toFloat(); out += (h / sd * XS * 0.86).toFloat(); out += (dist[i] * sd / h).toFloat()
                if (out.size / 3 >= BARS) break
            }
            out.toFloatArray()
        }
        for (r in 0 until 6) {
            val geo = barGeo[r]; val nb = geo.size / 3
            for (b in 0 until BARS) {
                val n = bars[r][b]
                if (b < nb) { n.show(true); n.at(geo[b * 3], rowY(r), rowZ(r)) } else n.show(false)
            }
            barH[r].fill(0f)
            setDecal(ksTags[r], ksKey(d, r), 0.028f)
        }
        dieShown = d
    }

    // ---------------------------------------------------------------- shelves
    private fun buildTokens(root: Node) {
        val plateMesh = MeshGen.box(0.130f, 0.095f, 0.012f)
        tokens = Array(2) { k -> root.add(Node("Tok$k")).also { it.pos = P3(SHELF_X, TOK_Y[k], 0.02f); it.tapRadius = 0.06f } }
        plates = Array(2) { k -> tokens[k].add("Plate$k", plateMesh, plateOff, P3(0f, 0f, -0.02f)) }
        val z = -0.006f
        // ① a little triangle of pegs over a bell of bars
        var i = 0
        for (r in 0 until 3) for (j in 0..r) tokens[0].add("TkP${i++}", pegMesh, Mat.Unlit(WHITE), P3((j - r / 2f) * 0.016f, 0.030f - r * 0.013f, z)).also { it.uniformScale(0.0035f) }
        for ((b, h) in floatArrayOf(0.008f, 0.020f, 0.030f, 0.020f, 0.008f).withIndex())
            tokens[0].add("TkB$b", unitBox, solid(BLUE, 0.4f), P3((b - 2) * 0.014f, -0.040f + h / 2, z)).also { it.scale = P3(0.011f, h, 0.010f) }
        // ② three rows of bars turning into a bell
        val shapes = listOf(floatArrayOf(0.02f, 0.02f, 0.02f, 0.02f, 0.02f), floatArrayOf(0.008f, 0.016f, 0.024f, 0.016f, 0.008f), floatArrayOf(0.004f, 0.014f, 0.026f, 0.014f, 0.004f))
        for ((r, sh) in shapes.withIndex()) for ((b, h) in sh.withIndex())
            tokens[1].add("TkW${r}_$b", unitBox, solid(rowCol(r * 2 + 1), 0.4f), P3((b - 2) * 0.013f + r * 0.006f, -0.038f + r * 0.026f + h / 2, z - r * 0.004f)).also { it.scale = P3(0.010f, h, 0.008f) }
        // die shelf (station ② only): the face weights of each die as six little bars
        dieTok = Array(3) { d -> groups[1].add(Node("Die$d")).also { it.pos = P3(SHELF_X, DIE_Y[d], 0.02f); it.tapRadius = 0.05f } }
        val dieMesh = MeshGen.box(0.130f, 0.085f, 0.012f)
        diePlates = Array(3) { d -> dieTok[d].add("DiePlate$d", dieMesh, plateOff, P3(0f, 0f, -0.02f)) }
        for (d in 0..2) {
            val pr = Die.ALL[d].prob; val mx = pr.max()
            for (f in 0 until 6) {
                val h = (0.050 * pr[f] / mx).toFloat().coerceAtLeast(0.0015f)
                dieTok[d].add("DieB${d}_$f", unitBox, solid(if (d == 0) Rgb(0.6f, 0.8f, 1f) else if (d == 1) Rgb(1f, 0.6f, 0.3f) else Rgb(0.6f, 1f, 0.6f), 0.4f),
                    P3((f - 2.5f) * 0.017f, -0.030f + h / 2, z)).also { it.scale = P3(0.012f, h, 0.010f) }
            }
        }
    }

    // ================================================================ per frame

    override fun update(dt: Float) {
        clock += dt
        for (s in 0 until 2) { groups[s].show(s == station); plates[s].setMat(if (s == station) plateOn else plateOff) }
        update1(dt)
        if (station == 1) update2(dt)
        poolGlow.setMat(Mat.Unlit(if (station == 0) BLUE else rowCol(3), alpha = 0.22f, tex = (poolGlow.mat as Mat.Unlit).tex, blend = Blend.ADD))
        updateReadout()
        updateLabels()
    }

    private fun update1(dt: Float) {
        val n = rows
        if (rowsShown != n) layoutBoard()
        val tilt = ((p100 - 50) / 45f * TILT_MAX).toFloat()
        if (tilt != tiltShown) { tiltShown = tilt; board.turn(Q.axisAngle(0f, 0f, 1f, -tilt * PI.toFloat() / 180f)); setDecal(pTag, pKey(p100), 0.030f) }
        // emit
        if (pending > 0) {
            emitAcc += dt * spd * EMIT_RATE
            while (emitAcc >= 1f && pending > 0) {
                val slot = active.indexOfFirst { !it }
                if (slot < 0) break
                active[slot] = true; bits[slot] = sim.path(n, p); uRow[slot] = 0f; ballRows[slot] = n
                pending--; emitAcc -= 1f
            }
            if (pending == 0) emitAcc = 0f
        } else emitAcc = 0f
        // move
        val du = dt * spd / T_ROW
        val dy = dyRow(n)
        for (i in 0 until POOL) {
            val node = balls[i]
            if (!active[i]) { if (node.visible) node.show(false); continue }
            uRow[i] += du
            val u = uRow[i]; val b = bits[i]
            var x: Float; var y: Float
            when {
                u < 1f -> { x = 0f; val yA = YF - BY; val yB = pegY(0, n) + REST; y = yA + (yB - yA) * u * u }
                u < n.toFloat() -> {
                    val s = u.toInt(); val t = u - s                                       // hop from row s-1 to row s
                    val j0 = Integer.bitCount(b and ((1 shl (s - 1)) - 1))
                    val dir = if ((b shr (s - 1)) and 1 == 1) 1f else -1f
                    x = pegX(s - 1, j0) + dir * DX / 2 * t
                    y = pegY(s - 1, n) + REST + 0.35f * dy * sin(PI.toFloat() * t) - dy * t * t
                }
                else -> {
                    val t = ((u - n) / FALL_U).coerceAtMost(1f)
                    val k = Integer.bitCount(b)
                    val j0 = Integer.bitCount(b and ((1 shl (n - 1)) - 1))
                    val x0 = pegX(n - 1, j0); val xb = binX(k, n)
                    x = x0 + (xb - x0) * min(1f, 2.2f * t)
                    val y0 = pegY(n - 1, n) + REST; val yl = stackTop(k) + RB
                    y = y0 + 0.30f * dy * sin(PI.toFloat() * min(1f, 2.2f * t)) - (y0 - yl) * t * t
                    if (t >= 1f) {
                        active[i] = false; node.show(false)
                        counts[k]++; landed++; landedEver++; stacksDirty = true
                        continue
                    }
                }
            }
            node.show(true); node.at(x, y, 0f)
        }
        if (stacksDirty) refreshStacks()
    }

    private val NICE = longArrayOf(1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10_000, 20_000, 50_000, 100_000, 200_000, 500_000, 1_000_000)

    private fun refreshStacks() {
        stacksDirty = false
        val n = rows
        val f = Binomial.pmf(n, p)
        val need = max(counts.max().toDouble(), landed * f.max()) / SLOTS
        unit = NICE.firstOrNull { it >= need } ?: NICE.last()
        for (k in 0..N_MAX) {
            val m = if (k <= n) visibleIn(k) else 0
            for (s in 0 until SLOTS) stacks[k][s].show(s < m)
        }
        // overlays: B(n, p) outline and the N(np, np(1-p)) density, both in "drawn ball" units
        val on = overlay && landed > 0
        val scale = landed.toFloat() / unit * SLOT_H
        val yb = YB - BY
        val z = RB + 0.004f
        for (k in 0..N_MAX) {
            val t = tops[k]
            if (!on || k > n) { t.show(false); continue }
            t.show(true); val h = yb + (f[k] * scale).toFloat(); val x = binX(k, n)
            t.span(x - DX / 2, h, z, x + DX / 2, h, z, 0.0017f)
        }
        for (k in 0..N_MAX + 1) {
            val rr = risers[k]
            if (!on || k > n + 1) { rr.show(false); continue }
            val hl = if (k == 0) yb else yb + (f[k - 1] * scale).toFloat()
            val hr = if (k == n + 1) yb else yb + (f[k] * scale).toFloat()
            val x = binX(k, n) - DX / 2
            if (abs(hr - hl) < 1e-4f) { rr.show(false); continue }
            rr.show(true); rr.span(x, hl, z, x, hr, z, 0.0017f)
        }
        val mu = n * p; val sd = sqrt(n * p * (1 - p))
        val x0 = -(n / 2f + 0.5f); val x1 = n / 2f + 0.5f
        for (i in curve.indices) {
            val c = curve[i]
            if (!on) { c.show(false); continue }
            val a = x0 + (x1 - x0) * i / curve.size; val b = x0 + (x1 - x0) * (i + 1) / curve.size
            fun yy(v: Float) = yb + (Normal.pdf((v + n / 2.0 - mu) / sd) / sd * scale).toFloat().coerceAtMost(SLOTS * SLOT_H * 1.15f)
            c.show(true); c.span(a * DX, yy(a), z + 0.002f, b * DX, yy(b), z + 0.002f, 0.0019f)
        }
    }

    private fun update2(dt: Float) {
        if (dieShown != die) computeBars(die)
        for (r in 0 until 6) {
            val geo = barGeo[r]; val nb = geo.size / 3; val sel = r == kIdx
            for (b in 0 until nb) {
                val target = (H2 * geo[b * 3 + 2] / Normal.pdf(0.0)).toFloat()
                val h = approach(barH[r][b], target, 0.35f, dt).let { if (dt == 0f && barH[r][b] == 0f) target * 0.999f else it }
                barH[r][b] = h
                val n = bars[r][b]
                n.dims(geo[b * 3 + 1], max(h, 0.0005f), BAR_D); n.at(geo[b * 3], rowY(r) + h / 2, rowZ(r))
                n.setMat(if (sel) barMatOn[r] else barMatOff[r])
            }
        }
        kKnob.at(WX + (ZMAX * XS).toFloat() + 0.16f, rowY(kIdx) + 0.02f, rowZ(kIdx))
        for (d in 0..2) diePlates[d].setMat(if (d == die) plateOn else plateOff)
    }

    private fun dieName(d: Int) = when (d) { 0 -> tr("公平骰子", "fair die"); 1 -> tr("很歪的骰子", "loaded die"); else -> tr("只出 1 或 6 的骰子", "1-or-6 die") }

    private fun updateReadout() {
        val n = rows
        val ut = if (unit > 1) tr(" · 1 颗 = $unit 球", " · 1 ball = $unit") else ""
        val l1 = tr("① 球 ${grp(landed)} · 行 n = $n · p = ${f2(p)} · 均值 ${f2(GaltonSim.mean(counts))}（np = ${f2(n * p)}）· 方差 ${f2(GaltonSim.variance(counts))}（np(1−p) = ${f2(n * p * (1 - p))}）$ut",
            "① Balls ${grp(landed)} · rows n = $n · p = ${f2(p)} · mean ${f2(GaltonSim.mean(counts))} (np = ${f2(n * p)}) · var ${f2(GaltonSim.variance(counts))} (np(1−p) = ${f2(n * p * (1 - p))})$ut")
        val k = Die.KS[kIdx]
        val l2 = tr("② ${dieName(die)} × k = $k 个相加：与正态的距离 KS = ${f3(KS_TABLE[die][kIdx])}（k = 1 时 ${f3(KS_TABLE[die][0])}）",
            "② Sum of k = $k × ${dieName(die)}: distance to the normal KS = ${f3(KS_TABLE[die][kIdx])} (k = 1: ${f3(KS_TABLE[die][0])})")
        val l3 = if (station == 0) tr("每个球 = $n 次独立左右选择之和：通往中间的路径最多，于是堆成钟形（钉子的弹跳为示意）",
            "Each ball = the sum of $n independent left/right choices: most paths lead to the middle, so a bell piles up (stylised bounces)")
        else tr("不管单个骰子长什么样，独立相加得越多越像钟形 —— 中心极限定理（各行已标准化）",
            "Whatever one die looks like, the more independent ones you add, the closer to a bell — the CLT (rows standardised)")
        readoutText = "$l1\n$l2\n$l3"
    }

    private fun updateLabels() {
        labels.clear()
        if (station == 0) {
            val hw = halfW(rows)
            val pk = peakBin()
            labels += LabelSpec(tr("峰值：从左数第 ${pk + 1} 格", "peak: bin ${pk + 1} from the left"), P3(BX + binX(pk, rows), YB + SLOTS * SLOT_H + 0.06f, 0.06f))
            labels += LabelSpec(tr("倾斜 → p", "tilt → p"), P3(BX + hw + 0.10f, Y0 + 0.07f, 0.03f))
            labels += LabelSpec(tr("行数", "rows"), P3(ROWS_X, ROWS_Y0 - 0.06f, 0.03f))
        } else {
            labels += LabelSpec(dieName(die), P3(SHELF_X, DIE_Y[0] + 0.08f, 0.04f))
            labels += LabelSpec(tr("标准化：(和 − kμ)/(σ√k)", "standardised: (sum − kμ)/(σ√k)"), P3(WX, rowY(0) - 0.05f, rowZ(0) + 0.08f))
        }
    }

    /** The highest bin of the current counts, or the pmf mode while empty. */
    private fun peakBin(): Int = if (landed == 0L) Binomial.mode(rows, p) else (0..rows).maxByOrNull { counts[it] } ?: 0

    // ================================================================ interaction

    private fun stationTargets(s: Int): List<String> = if (s == 0) listOf("Funnel", "TiltKnob", "RowsKnob") else listOf("KKnob", "Die0", "Die1", "Die2")
    private val TOK_NAMES = listOf("Tok0", "Tok1")

    override val tapTargets: List<TapTarget> get() {
        val t = ArrayList<TapTarget>()
        if (station == 0) {
            t += TapTarget("Funnel", "漏斗 → 放 $burst 个球", "Funnel → drop $burst")
            t += TapTarget("TiltKnob", "倾斜把手 → 换 p", "Tilt knob → next p") { p100 != 50 }
            t += TapTarget("RowsKnob", "行数把手 → +2 行", "Rows knob → +2 rows")
        } else {
            t += TapTarget("KKnob", "k 把手 → 下一行", "k knob → next row")
            t += TapTarget("Die0", "公平骰子", "Fair die") { die == 0 }
            t += TapTarget("Die1", "歪骰子", "Loaded die") { die == 1 }
            t += TapTarget("Die2", "1 或 6 骰子", "1-or-6 die") { die == 2 }
        }
        t += TapTarget("Tok0", "① 高尔顿板", "① Board") { station == 0 }
        t += TapTarget("Tok1", "② 骰子之和", "② Dice sums") { station == 1 }
        return t
    }

    override fun isTarget(name: String) = name in stationTargets(station) || name in TOK_NAMES

    private val P_PRESETS = intArrayOf(50, 70, 30)

    override fun onTap(name: String) {
        when (name) {
            "Tok0" -> selectStation(0)
            "Tok1" -> selectStation(1)
            "Funnel" -> release(burst)
            "TiltKnob" -> { val i = P_PRESETS.indexOf(p100); setP(P_PRESETS[if (i < 0) 0 else (i + 1) % P_PRESETS.size] / 100.0) }
            "RowsKnob" -> rowsTo(if (rows >= N_MAX) N_MIN else min(N_MAX, rows + 2))
            "KKnob" -> setK((kIdx + 1) % 6)
            "Die0" -> dieTo(0)
            "Die1" -> dieTo(1)
            "Die2" -> dieTo(2)
        }
    }

    override fun tapMessage(name: String): String? = when (name) {
        "Tok0" -> tr("① 高尔顿板：每颗钉子一次左右选择", "① Galton board: a left/right choice at every peg")
        "Tok1" -> tr("② 把 k 个骰子加起来：形状走向钟形", "② Add k dice: the shape marches to a bell")
        "Funnel" -> tr("放出 $burst 个球", "Dropping $burst balls")
        "TiltKnob" -> tr("p = ${f2(p)}（示意：倾斜 → 每颗钉向右的概率），箱子已清空", "p = ${f2(p)} (stylised: tilt → chance of going right), bins cleared")
        "RowsKnob" -> tr("$rows 行钉子 → ${rows + 1} 个箱子", "$rows rows of pegs → ${rows + 1} bins")
        "KKnob" -> tr("k = ${Die.KS[kIdx]}：KS = ${f3(KS_TABLE[die][kIdx])}", "k = ${Die.KS[kIdx]}: KS = ${f3(KS_TABLE[die][kIdx])}")
        "Die0", "Die1", "Die2" -> tr("换成${dieName(die)}：看最后一行", "Now a ${dieName(die)}: watch the back row")
        else -> null
    }

    override fun isDragTarget(name: String) = (station == 0 && (name == "TiltKnob" || name == "RowsKnob")) || (station == 1 && name == "KKnob")

    override fun onDragStart(name: String) { dragAcc = 0f; dragP0 = p100; dragI0 = if (name == "RowsKnob") rows else kIdx }

    override fun onDrag(name: String, dx: Float, dy: Float, dz: Float) {
        when (name) {
            // right/down = tilt the right side down = more balls go right; 12 cm of hand = the full 0.05..0.95 range
            "TiltKnob" -> { dragAcc += dx - dy; val want = (dragP0 + dragAcc / 0.12f * 45f).roundToInt().coerceIn(5, 95); if (want != p100) setP(want / 100.0) }
            "RowsKnob" -> { dragAcc += dy; val want = (dragI0 + dragAcc / ROWS_DY).roundToInt().coerceIn(N_MIN, N_MAX); if (want != rows) rowsTo(want) }
            "KKnob" -> { dragAcc += dy; val want = (dragI0 + dragAcc / 0.05f).roundToInt().coerceIn(0, 5); if (want != kIdx) setK(want) }
        }
    }

    override fun onDragEnd(name: String) { bump() }

    // ---- actions (panel, lesson autoplay and tests call these)
    fun selectStation(s: Int) { station = s.coerceIn(0, 1); bump() }
    fun release(m: Int) { pending = min(pending + m, 100_000); bump() }
    fun fastFill(m: Int = 1000) { sim.fill(counts, rows, p, m); landed += m; landedEver += m; stacksDirty = true; bump() }
    fun clearBins() { counts.fill(0L); landed = 0L; pending = 0; active.fill(false); stacksDirty = true; bump() }
    fun rowsTo(n: Int) { val v = n.coerceIn(N_MIN, N_MAX); if (v != rows) { rows = v; clearBins() }; bump() }
    fun setP(v: Double) { val q = (v * 100).roundToInt().coerceIn(5, 95); if (q != p100) { p100 = q; clearBins() }; bump() }
    fun overlayTo(on: Boolean) { overlay = on; stacksDirty = true; bump() }
    fun dieTo(d: Int) { die = d.coerceIn(0, 2); bump() }
    fun setK(i: Int) { kIdx = i.coerceIn(0, 5); bump() }

    /** Land every ball in flight now and finish the bar morphs (review renders). */
    fun settle() {
        for (i in 0 until POOL) if (active[i]) { active[i] = false; counts[Integer.bitCount(bits[i])]++; landed++; landedEver++ }
        if (pending > 0) { fastFill(pending); pending = 0 }
        stacksDirty = true
        if (dieShown != die) computeBars(die)
        for (r in 0 until 6) { val g = barGeo[r]; for (b in 0 until g.size / 3) barH[r][b] = (H2 * g[b * 3 + 2] / Normal.pdf(0.0)).toFloat() }
    }

    // ================================================================ guided track (导学)

    private var base = 0L

    override val lesson: Lesson by lazy {
        val c12 = Binomial.choose(12, 6); val pc = Binomial.pmf(12, 0.5)[6]
        val ks1 = f3(KS_TABLE[1][0]); val ks30 = f3(KS_TABLE[1][5])
        Lesson(
            listOf(
                LessonStep(
                    say = Bi("① 每个球在 12 排钉子上各做一次「左或右」的随机选择。放很多球，箱子里会堆成什么形状？", "① At each of 12 rows of pegs a ball makes a random left-or-right choice. Drop many balls: what shape piles up in the bins?"),
                    options = listOf(Bi("平的（每格差不多）", "Flat (every bin about equal)"), Bi("钟形（中间高）", "A bell (high in the middle)"), Bi("两头高", "High at both ends")),
                    answer = 1,
                    onEnter = { selectStation(0); rowsTo(12); setP(0.5); clearBins(); overlayTo(false); burst = 100 },
                ),
                LessonStep(
                    say = Bi("捏顶上的漏斗（或点面板「▶ 放球」），放 100 个球，看它们一颗颗跳下来。", "Pinch the funnel at the top (or tap '▶ Drop' on the panel) to drop 100 balls, and watch them hop down."),
                    target = "Funnel",
                    onEnter = { selectStation(0); base = landedEver },
                    done = { landedEver - base >= 100 },
                    autoplay = { selectStation(0); release(100) },
                ),
                LessonStep(
                    say = Bi("打开「曲线」叠上精确的 B(12, ½)（金）和正态曲线（粉），再按「⚡ 快速 1000」加球。", "Switch on 'Curves' to overlay the exact B(12, ½) (gold) and the normal curve (pink), then tap '⚡ Fast 1000'."),
                    target = "Funnel",
                    onEnter = { selectStation(0); base = landed },
                    done = { station == 0 && overlay && landed >= base + 1000 },
                    autoplay = { selectStation(0); overlayTo(true); fastFill(1000) },
                    reveal = true,
                    insight = Bi("中心格要 6 左 6 右：有 C(12,6) = $c12 条路径，概率 $c12/4096 ≈ ${f3(pc)}；最边上一格只有 1 条路，1/4096。路径数就是杨辉三角 —— 钟形就是这么来的。", "The centre bin needs 6 lefts and 6 rights: C(12,6) = $c12 paths, probability $c12/4096 ≈ ${f3(pc)}; an end bin has a single path, 1/4096. Path counts are Pascal's triangle — that is where the bell comes from."),
                ),
                LessonStep(
                    say = Bi("② 换一个很歪的骰子（大多出 6，很少出 1）。把 30 个这样的骰子加起来，和的分布像什么？", "② Take a badly loaded die (mostly 6, rarely 1). Add up 30 of them: what does the distribution of the sum look like?"),
                    options = listOf(Bi("还是歪的", "Still lopsided"), Bi("钟形", "A bell"), Bi("平的", "Flat")),
                    answer = 1,
                    onEnter = { selectStation(1); dieTo(0); setK(0) },
                ),
                LessonStep(
                    say = Bi("在右边的骰子架上捏「歪骰子」，再把 k 把手拖到最后一行 k = 30。", "On the die shelf at the right pinch the loaded die, then move the k knob to the back row, k = 30."),
                    target = "Die1",
                    onEnter = { selectStation(1) },
                    done = { station == 1 && die == 1 && kIdx == 5 },
                    autoplay = { selectStation(1); dieTo(1); setK(5) },
                    reveal = true,
                    insight = Bi("一个歪骰子离正态很远：KS = $ks1；30 个相加只剩 $ks30，柱子已贴着钟形。单个选择长什么样都行 —— 只要独立地加很多次，和就趋向 N(kμ, kσ²)：中心极限定理。", "One loaded die is far from normal: KS = $ks1; the sum of 30 is down to $ks30, the bars hug the bell. Any single choice will do — add many independent ones and the sum tends to N(kμ, kσ²): the central limit theorem."),
                ),
            ),
            challenge = Challenge(
                say = Bi("挑战：回到高尔顿板（12 行），倾斜板子，让峰值落在从左数第 10 格（p ≈ 0.7）。", "Challenge: back on the board (12 rows), tilt it so the peak lands in bin 10 from the left (p ≈ 0.7)."),
                progress = {
                    val m = Binomial.mode(rows, p) + 1
                    if (rows != 12) tr("先把行数调回 12（现在 $rows 行）", "Set the rows back to 12 (now $rows)")
                    else tr("p = ${f2(p)}：B(12, p) 的峰值在第 $m 格（目标第 10 格）", "p = ${f2(p)}: the peak of B(12, p) is bin $m (goal: bin 10)")
                },
                done = { station == 0 && rows == 12 && Binomial.mode(12, p) == 9 },
                autoplay = { selectStation(0); rowsTo(12); setP(0.70); release(100) },
                onEnter = { selectStation(0) },
            ),
        )
    }

    // ================================================================ review states

    override fun reviewVariants(): Map<String, () -> Unit> = mapOf(
        "tilted" to { selectStation(0); rowsTo(16); setP(0.70); overlayTo(true); fastFill(3000); release(40) },
        "board8" to { selectStation(0); rowsTo(8); setP(0.5); overlayTo(true); fastFill(300); settle() },
        "fair" to { selectStation(1); dieTo(0); setK(2); settle() },
        "skewed" to { selectStation(1); dieTo(1); setK(5); settle() },
        "twovalued" to { selectStation(1); dieTo(2); setK(5); settle() },
    )

    // ================================================================ panel

    @Composable
    override fun Panel() {
        @Suppress("UNUSED_VARIABLE") val epoch = panelEpoch
        LabPanel(readout) {
            LabRow {
                if (station == 0) {
                    SegmentControl {
                        SegmentItem(selected = speed == 0, onClick = { speed = 0; bump() }, title = { Text(tr("慢", "Slow")) })
                        SegmentItem(selected = speed == 1, onClick = { speed = 1; bump() }, title = { Text(tr("中", "Medium")) })
                        SegmentItem(selected = speed == 2, onClick = { speed = 2; bump() }, title = { Text(tr("快", "Fast")) })
                    }
                    SegmentControl {
                        for (b in intArrayOf(1, 10, 100)) SegmentItem(selected = burst == b, onClick = { burst = b; bump() }, title = { Text("×$b") })
                    }
                    Button(onClick = { release(burst) }) { Text(tr("▶ 放球", "▶ Drop")) }
                    Button(onClick = { fastFill(1000) }) { Text(tr("⚡ 快速 1000", "⚡ Fast 1000")) }
                    Button(onClick = { clearBins() }) { Text(tr("清空", "Clear")) }
                    LabSwitch(tr("曲线", "Curves"), overlay) { overlayTo(it) }
                } else {
                    SegmentControl {
                        for (d in 0..2) SegmentItem(selected = die == d, onClick = { dieTo(d) }, title = { Text(dieName(d)) })
                    }
                    SegmentControl {
                        for (i in 0..5) SegmentItem(selected = kIdx == i, onClick = { setK(i) }, title = { Text("k = ${Die.KS[i]}") })
                    }
                }
            }
            if (station == 0) LabRow {
                LabSlider(tr("行数", "Rows"), rows.toFloat(), N_MIN.toFloat()..N_MAX.toFloat(), "$rows", width = 360) { rowsTo(it.roundToInt()) }
                LabSlider(tr("倾斜 p", "Tilt p"), p100.toFloat(), 5f..95f, f2(p), width = 360) { setP(it.roundToInt() / 100.0) }
            }
        }
    }
}
