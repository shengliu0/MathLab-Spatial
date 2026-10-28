// 欧拉公式 · 复数即旋转 · Euler's Formula · Rotation (Euler, Introductio 1748).
//
// THE AHA: multiplying by a complex number = rotate + scale; e^{iθ} is the point you reach by walking θ
// along the unit circle; seen through TIME, e^{iωt} is a HELIX whose two shadows are cos and sin — one
// 3-D object, three graphs.
//   ① The helix: the complex plane stands facing the viewer (Re right, Im up), time runs into depth. The
//      glowing helix e^{(σ+iω)t} with a point running along it; its shadow on the floor (Re–t) is cos ωt,
//      on the side wall (Im–t) sin ωt, on the front plane a circle (σ < 0: a spiral, damped oscillation).
//      Grab the end of the time axis to swing the whole object round (views: 3/4, front, side, top).
//      ω and σ are pinch-drag knobs on rails; the helix is rebuilt ONLY when they change (throttled), the
//      running point moves by transform.
//   ② Multiplication: drag z and w; z·w has the lengths multiplied and the angles ADDED (arcs colour-coded,
//      the composite arc lands on z·w); the triangles (0, 1, w) and (0, z, zw) are similar; the gold grid is
//      the whole plane multiplied by w. "×i" turns z a quarter turn; twice = −z: i² = −1.
//   ③ e^{iπ} = −1: walk θ along the unit circle; the series 1 + iθ + (iθ)²/2! + … is drawn as a chain of
//      arrows, each the previous one turned 90° and scaled by θ/k (3Blue1Brown's picture), spiralling onto
//      the walker. At θ = π it lands on −1: e^{iπ} + 1 = 0.
// Maths + tests: core/euler (EulerTest, EulerSceneTest). The only texture is one text atlas, baked once.

package com.bytedance.mathlab.demos.euler

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.bytedance.mathlab.core.P3
import com.bytedance.mathlab.core.euler.Complex
import com.bytedance.mathlab.core.euler.Euler
import com.bytedance.mathlab.lab.BakeCache
import com.bytedance.mathlab.lab.About
import com.bytedance.mathlab.lab.Bi
import com.bytedance.mathlab.lab.Challenge
import com.bytedance.mathlab.lab.Lesson
import com.bytedance.mathlab.lab.LessonStep
import com.bytedance.mathlab.lab.Q
import com.bytedance.mathlab.lab.Sfx
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
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object EulerDemo : SpecDemo() {
    override val id = "euler"
    override val title = "欧拉公式 · 复数即旋转"  // i18n-ok (zh half of DemoRegistry.titleOf; English in TITLE_EN)
    override val credit = "Euler · 1748"

    // ================================================================ layout (volume metres)
    private const val PLINTH_Y = -0.262f
    // ① the helix rig: pivot = centre of the time axis; frame = complex plane at t = 0, time along −z
    private const val P1X = -0.06f
    private const val P1Z = -0.04f
    private const val R1 = 0.15f                       // metres per unit (radius of e^{iωt})
    private const val L1 = 0.64f                       // drawn length of t in [0, T_MAX]
    private val T_MAX = (4 * PI).toFloat()
    private val KT = L1 / T_MAX
    private const val WALL = 0.195f                    // walls at Re = −WALL (side) and Im = −WALL (floor)
    private const val FZ = 0.035f                      // front plane (frame z)
    private const val NS = 161                         // helix samples (6 turns at |ω| = 3: 27 per turn)
    private const val RATE1 = 1.2f                     // time units per second at medium speed
    private const val RAIL_Y = -0.200f
    private const val RAIL_Z = 0.38f
    private const val OM_X0 = -0.30f                   // ω = 0 on its rail ...
    private const val OM_K = 0.075f                    // ... metres per unit of ω (−3 .. 3)
    private const val SG_X1 = 0.40f                    // σ = 0 on its rail ...
    private const val SG_K = 0.75f                     // ... metres per unit of σ (−0.4 .. 0)
    // ② multiplication
    private val O2 = P3(-0.08f, 0.09f, 0f)
    private const val U2 = 0.145f
    private const val B2 = 2.0f                        // grid half-size (units)
    // ③ the series
    private val O3 = P3(0.20f, 0.06f, 0f)
    private const val U3 = 0.10f
    private const val N_CHAIN = 26
    // station shelf
    private const val TOK_X = 0.77f
    private val TOK_Y = floatArrayOf(0.30f, 0.15f, 0.00f)

    // ================================================================ colours (sRGB, 3Blue1Brown-like)
    private val WHITE = Rgb(0.96f, 0.97f, 1.0f)
    private val DIM = Rgb(0.55f, 0.60f, 0.70f)
    private val YELLOW = Rgb(1.0f, 0.86f, 0.32f)
    private val BLUE = Rgb(0.35f, 0.78f, 0.96f)
    private val RED = Rgb(0.99f, 0.42f, 0.38f)
    private val GREEN = Rgb(0.48f, 0.88f, 0.48f)
    private val PURPLE = Rgb(0.74f, 0.55f, 1.0f)
    private val GOLD = Rgb(1.0f, 0.72f, 0.30f)
    private val CHAIN_COL = arrayOf(BLUE, GREEN, RED, PURPLE)       // direction 1, i, −1, −i: the 90° rhythm
    private fun glow(c: Rgb, a: Float) = Mat.Unlit(c, alpha = a, blend = Blend.ADD)
    private fun core(c: Rgb) = Mat.Unlit(c)
    private fun solid(c: Rgb, e: Float = 0.45f) = Mat.Pbr(c, metallic = 0f, roughness = 0.3f, emissive = Rgb(c.r * e, c.g * e, c.b * e))
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.32f)
    private val plateOff = Mat.Pbr(Rgb(0.10f, 0.11f, 0.13f), metallic = 0.3f, roughness = 0.35f)
    private val plateOn = Mat.Pbr(Rgb(0.16f, 0.24f, 0.40f), metallic = 0.2f, roughness = 0.30f, emissive = Rgb(0.10f, 0.24f, 0.50f))
    private val pane = Mat.Unlit(Rgb(0.03f, 0.04f, 0.065f), alpha = 0.82f, blend = Blend.TRANSPARENT)

    // ================================================================ text atlas
    private const val TEX_VERSION = 3
    private const val EM_PX = 64
    private fun sp(t: String, c: Rgb = WHITE) = EulerText.Span(t, c)
    private val ATLAS_ITEMS: List<EulerAtlas.Item> by lazy {
        val keys = LinkedHashMap<String, EulerAtlas.Item>()
        fun add(t: String, spans: List<EulerText.Span> = listOf(sp(t)), key: String = t) { if (key !in keys) keys[key] = EulerAtlas.Item(key, spans, EM_PX) }
        listOf("Re", "Im", "t", "1", "−1", "i", "−i", "ω", "σ", "0", "−3", "3", "−0.4", "−0.2", "z", "w", "z·w", "×i", "θ", "90°",
            "i^2 = −1", "i^3 = −i", "i^4 = 1", "iθ", "(iθ)^2/2!", "(iθ)^3/3!", "(iθ)^k/k!", "e^{iθ}", "e^{iωt}", "e^{(σ+iω)t}",
            "`cos` ωt", "`sin` ωt", "e^{σt}`cos` ωt", "e^{σt}`sin` ωt").forEach { add(it) }
        add("eipi", listOf(sp("e^{iπ}", GOLD), sp(" + 1 = 0")))
        add("series", listOf(sp("e^{iθ}", GOLD), sp(" = 1 + iθ + (iθ)^2/2! + (iθ)^3/3! + …")))
        add("absf", listOf(sp("|z·w|", YELLOW), sp(" = "), sp("|z|", BLUE), sp("·"), sp("|w|", RED)))
        add("argf", listOf(sp("`arg` z·w", YELLOW), sp(" = "), sp("`arg` z", BLUE), sp(" + "), sp("`arg` w", RED)))
        keys.values.toList()
    }
    private lateinit var atlas: EulerAtlas
    private val textMats = HashMap<Rgb, Mat>()
    private fun textMat(c: Rgb) = textMats.getOrPut(c) { Mat.Unlit(c, tex = atlas.image, blend = Blend.TRANSPARENT) }
    private val decalKey = java.util.IdentityHashMap<Node, String>()

    /** A text decal: [key] in the atlas, [em] metres per em, centred at its node. */
    private fun decal(parent: Node, name: String, key: String, em: Float, tint: Rgb = WHITE, pos: P3 = P3(0f, 0f, 0f), bb: Boolean = true): Node {
        val d = atlas.decal(key)
        val n = parent.add(name, d.mesh, textMat(tint), pos)
        decalKey[n] = key; n.billboard = bb
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
    enum class View(val yaw: Float, val pitch: Float) { QUARTER(-36f, 24f), FRONT(0f, 0f), SIDE(-90f, 0f), TOP(0f, 90f), CUSTOM(0f, 0f) }

    /** 0 ① helix, 1 ② multiplication, 2 ③ e^{iπ}. */
    var station = 0
    /** 0 slow, 1 medium, 2 fast. */
    var speed = 1
    private val SPEEDS = floatArrayOf(0.4f, 1f, 2.2f)
    private val spd get() = SPEEDS[speed]
    // ①
    var omega = 1.0f
    var sigma = 0.0f
    var play1 = true
    var view = View.QUARTER
    var t1 = 0f
    private var yaw = 0f; private var pitch = 0f; private var yawT = 0f; private var pitchT = 0f
    private var builtOmega = Float.NaN; private var builtSigma = Float.NaN
    private var cooldown = 0f
    /** How many times the helix meshes were rebuilt (tests: only on parameter change). */
    var helixBuilds = 0; private set
    private var rigQ = Q.identity(); private var rigS = 1f; private var rigY = 0.10f
    // ②
    var z = Complex.polar(1.2, PI / 6); private set
    var w = Complex.polar(0.8, PI / 4); private set
    var showWGrid = false
    /** Completed ×i quarter turns since z was last set by hand. */
    var quarterTurns = 0; private set
    private var pendingTurns = 0
    private var animS = -1f                        // < 0: no ×i animation running
    private var zFrom = Complex.ONE
    private var ghostT = 0f
    private var zPreset = 0; private var wPreset = 0
    private var lastZW: Complex? = null
    // ③
    var theta = 0.0; private set
    private var thetaT = 0.0
    var walking = false; private set
    var chainN = 0f; private set
    private var chainPlay = false
    private var s3 = U3
    private var lastTheta = Double.NaN; private var lastS3 = -1f
    // drag
    private var dragging: String? = null
    private var accX = 0f; private var accY = 0f
    private var drag0 = 0f; private var drag1 = 0f
    private var dragZ0 = Complex.ONE
    private var clock = 0f
    private var panelEpoch by mutableIntStateOf(0)
    private fun bump() { panelEpoch++ }

    private fun resetState() {
        station = 0; speed = 1
        omega = 1f; sigma = 0f; play1 = true; view = View.QUARTER; t1 = 0.9f
        yaw = View.QUARTER.yaw; pitch = View.QUARTER.pitch; yawT = yaw; pitchT = pitch
        builtOmega = Float.NaN; builtSigma = Float.NaN; cooldown = 0f; helixBuilds = 0
        rigQ = Q.identity(); rigS = 1f; rigY = 0.10f
        z = Complex.polar(1.2, PI / 6); w = Complex.polar(0.8, PI / 4); showWGrid = false
        quarterTurns = 0; pendingTurns = 0; animS = -1f; zFrom = z; ghostT = 0f; zPreset = 0; wPreset = 0; lastZW = null
        theta = 0.0; thetaT = 0.0; walking = false; chainN = 0f; chainPlay = false; s3 = U3; lastTheta = Double.NaN; lastS3 = -1f
        dragging = null; accX = 0f; accY = 0f; drag0 = 0f; drag1 = 0f; dragZ0 = Complex.ONE; clock = 0f
        decalKey.clear(); textMats.clear()
    }

    // ================================================================ nodes
    private lateinit var cyl: MeshData
    private lateinit var cone: MeshData
    private lateinit var ball: MeshData
    private lateinit var glowQuad: MeshData
    private lateinit var groups: Array<Node>
    private lateinit var tokens: Array<Node>
    private lateinit var plates: Array<Node>
    private lateinit var poolGlow: Node
    // ①
    private lateinit var rig: Node
    private lateinit var helixMd: MeshData
    private lateinit var helixGlowMd: MeshData
    private lateinit var cosMd: MeshData
    private lateinit var sinMd: MeshData
    private lateinit var frontMd: MeshData
    private lateinit var point1: Node
    private lateinit var point1Glow: Node
    private lateinit var dotFloor: Node
    private lateinit var dotWall: Node
    private lateinit var dotFront: Node
    private lateinit var projFloor: Node
    private lateinit var projWall: Node
    private lateinit var projFront: Node
    private lateinit var phShaft: Node
    private lateinit var phHead: Node
    private lateinit var tMark: Node
    private lateinit var lblHelix: Node
    private lateinit var lblCos: Node
    private lateinit var lblSin: Node
    private lateinit var turnKnob: Node
    private lateinit var omegaKnob: Node
    private lateinit var sigmaKnob: Node
    // ②
    private lateinit var grid2: Array<Node>
    private lateinit var wgrid: Array<Node>
    private lateinit var zShaft: Node; private lateinit var zHead: Node
    private lateinit var wShaft: Node; private lateinit var wHead: Node
    private lateinit var zwShaft: Node; private lateinit var zwHead: Node
    private lateinit var gShaft: Node; private lateinit var gHead: Node
    private lateinit var zKnob: Node
    private lateinit var wKnob: Node
    private lateinit var zwDot: Node
    private lateinit var zwGlow: Node
    private lateinit var arcMd: Array<MeshData>       // arg z, arg w, composite (z part), composite (w part), quarter turn
    private lateinit var arcNodes: Array<Node>
    private lateinit var triMd: Array<MeshData>
    private lateinit var triEdge: Array<Node>
    private lateinit var lblZ: Node; private lateinit var lblW: Node; private lateinit var lblZW: Node
    private lateinit var powLbl: Node
    private lateinit var timesI: Node
    // ③
    private lateinit var circle3: Node
    private lateinit var walkMd: MeshData
    private lateinit var walker: Node
    private lateinit var walkerGlow: Node
    private lateinit var radius3: Node
    private lateinit var tanShaft: Node; private lateinit var tanHead: Node
    private lateinit var chainShaft: Array<Node>
    private lateinit var chainHead: Array<Node>
    private lateinit var sumDot: Node
    private lateinit var termLbl: Array<Node>
    private lateinit var thetaLbl: Node
    private lateinit var eLbl: Node
    private lateinit var eipi: Node
    private lateinit var ticks3: Array<Node>
    private lateinit var tickLbl3: Array<Node>

    // ================================================================ small node helpers
    private fun Node.at(x: Float, y: Float, z: Float) { val p = pos; if (p.x != x || p.y != y || p.z != z) pos = P3(x, y, z) }
    private fun Node.dims(x: Float, y: Float, z: Float) { val s = scale; if (s.x != x || s.y != y || s.z != z) scale = P3(x, y, z) }
    private fun Node.turn(q: Q) { if (rot != q) rot = q }
    private fun approach(v: Float, target: Float, rate: Float, dt: Float): Float =
        if (v < target) min(target, v + rate * dt) else max(target, v - rate * dt)
    private fun sm(t: Float): Float { val x = t.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }

    /** A unit cylinder (along Y) from a to b, radius r; hidden when degenerate. */
    private fun Node.span(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, r: Float) {
        val dx = x1 - x0; val dy = y1 - y0; val dz = z1 - z0
        val l = sqrt(dx * dx + dy * dy + dz * dz)
        if (l < 1e-5f) { visible = false; return }
        visible = true
        at((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2)
        dims(r, l, r)
        turn(Q.fromTo(0f, 1f, 0f, dx, dy, dz))
    }

    /** Arrow from a to b: shaft + cone head (head never longer than 40 % of the arrow). */
    private fun arrow(shaft: Node, head: Node, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, r: Float, hl0: Float, hr0: Float, show: Boolean = true) {
        val dx = x1 - x0; val dy = y1 - y0; val dz = z1 - z0
        val l = sqrt(dx * dx + dy * dy + dz * dz)
        if (!show || l < 2e-4f) { shaft.visible = false; head.visible = false; return }
        val hl = min(hl0, 0.4f * l); val hr = hr0 * hl / hl0
        val k = (l - hl) / l
        shaft.span(x0, y0, z0, x0 + dx * k, y0 + dy * k, z0 + dz * k, r)
        if (l - hl < 1e-4f) shaft.visible = false
        head.visible = true
        head.at(x0 + dx * k, y0 + dy * k, z0 + dz * k); head.dims(hr, hl, hr)
        head.turn(Q.fromTo(0f, 1f, 0f, dx, dy, dz))
    }

    private fun coneMesh(seg: Int): MeshData {
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

    /** A flat strip of [n] points, edited in place by [stripInto]. */
    private fun strip(n: Int): MeshData {
        val md = OptGeo.grid(2, n)
        for (k in 0 until n) { OptGeo.setUv(md, k, k / (n - 1f), 0f); OptGeo.setUv(md, n + k, k / (n - 1f), 1f) }
        return md
    }

    /** Write a ribbon of width [w] through 2-D points (a_k, b_k) of the plane o + a·A + b·B into [md] (a [strip]). */
    private fun stripInto(md: MeshData, n: Int, a: FloatArray, b: FloatArray, w: Float, o: FloatArray, A: FloatArray, B: FloatArray) {
        var nx = 0f; var ny = 1f
        for (k in 0 until n) {
            val k0 = max(0, k - 1); val k1 = min(n - 1, k + 1)
            val da = a[k1] - a[k0]; val db = b[k1] - b[k0]
            val l = sqrt(da * da + db * db)
            if (l > 1e-7f) { nx = -db / l; ny = da / l }
            for (side in 0..1) {
                val s = if (side == 0) w / 2 else -w / 2
                val u = a[k] + nx * s; val v = b[k] + ny * s
                OptGeo.setPos(md, side * n + k, o[0] + u * A[0] + v * B[0], o[1] + u * A[1] + v * B[1], o[2] + u * A[2] + v * B[2])
            }
        }
    }

    private val XY = floatArrayOf(1f, 0f, 0f) to floatArrayOf(0f, 1f, 0f)
    private const val ARC_N = 33

    /** Circular arc (radius r metres, angles a0 → a1) in the plane z = zc, centre (cx, cy). */
    private fun arcInto(md: MeshData, cx: Float, cy: Float, zc: Float, r: Float, a0: Double, a1: Double, w: Float, n: Int = ARC_N) {
        val a = FloatArray(n); val b = FloatArray(n)
        for (k in 0 until n) { val t = a0 + (a1 - a0) * k / (n - 1); a[k] = (r * cos(t)).toFloat(); b[k] = (r * sin(t)).toFloat() }
        stripInto(md, n, a, b, w, floatArrayOf(cx, cy, zc), XY.first, XY.second)
    }

    private fun ringMesh(r: Float, tube: Float, sides: Int = 6, n: Int = 96): MeshData {
        val p = FloatArray(n * 3)
        for (k in 0 until n) { val a = 2 * PI * k / n; p[k * 3] = (r * cos(a)).toFloat(); p[k * 3 + 1] = (r * sin(a)).toFloat() }
        return MeshGen.tube(p, tube, sides, true)
    }

    private fun radialTex(): Image = OptGeo.lumTex(64, 64) { u, v ->
        val dx = (u - 0.5f) * 2f; val dy = (v - 0.5f) * 2f; val r2 = dx * dx + dy * dy
        if (r2 >= 1f) 0f else (1f - r2) * (1f - r2)
    }

    /** Graph paper for the shadow walls: dark, faint value lines (±0.5, ±1) and time lines every π/2. */
    private fun wallTex(): Image {
        val w = 256; val h = 96; val img = Image(w, h)
        val len = L1 + 0.06f
        fun near(p: Float, q: Float, px: Float) = abs(p - q) < px
        for (y in 0 until h) for (x in 0 until w) {
            val zM = (x + 0.5f) / w * len - 0.03f                     // metres along t from t = 0
            val vM = ((y + 0.5f) / h * 2f - 1f) * WALL                 // metres across (value)
            var line = 0f
            for (k in 0..8) if (near(zM, k * (PI.toFloat() / 2) * KT, 0.0016f)) line = max(line, if (k % 2 == 0) 0.55f else 0.30f)
            for (q in floatArrayOf(-1f, -0.5f, 0.5f, 1f)) if (near(vM, q * R1, 0.0016f)) line = max(line, if (abs(q) == 1f) 0.40f else 0.22f)
            val edge = x < 2 || x >= w - 2 || y < 2 || y >= h - 2
            if (edge) line = 0.7f
            val r = 0.035f + 0.25f * line; val g = 0.045f + 0.33f * line; val b = 0.075f + 0.45f * line
            img.px[y * w + x] = ProcTex.argb(0.86f, r, g, b)
        }
        return img
    }

    // ================================================================ build

    override fun buildSpec(cacheDir: File): Node {
        resetState()
        val imgs = BakeCache.load(cacheDir, "euler_text", TEX_VERSION) { listOf(EulerAtlas.bakeImage(ATLAS_ITEMS), wallTex()) }
        atlas = EulerAtlas.of(imgs[0], ATLAS_ITEMS)
        cyl = MeshGen.cylinder(1f, 1f, 10)
        cone = coneMesh(14)
        ball = MeshGen.sphere(1f, 12, 24, MeshGen.SphereUv.EQUIRECT)
        glowQuad = OptGeo.quad(1f, 1f)
        val rtex = radialTex()

        val root = Node("EulerRoot")
        root.add("Plinth", MeshGen.box(1.74f, 0.035f, 0.92f), Mat.Pbr(Rgb(0.10f, 0.105f, 0.12f), metallic = 0.2f, roughness = 0.5f), P3(0f, PLINTH_Y, -0.04f))
        root.add("PlinthBand", MeshGen.box(1.75f, 0.008f, 0.93f), alu, P3(0f, PLINTH_Y, -0.04f))
        poolGlow = root.add("Glow", glowQuad, Mat.Unlit(Rgb(0.45f, 0.70f, 1.0f), alpha = 0.20f, tex = rtex, blend = Blend.ADD)).also {
            it.rot = Q.axisAngle(1f, 0f, 0f, -PI.toFloat() / 2); it.scale = P3(1.20f, 0.70f, 1f); it.pos = P3(0f, PLINTH_Y + 0.019f, 0f)
        }
        groups = Array(3) { k -> root.add(Node("St${k + 1}")) }
        build1(groups[0], imgs[1], rtex); build2(groups[1], rtex); build3(groups[2], rtex)
        buildTokens(root)
        update(0f)
        return root
    }

    // ---------------------------------------------------------------- ① the helix
    private fun build1(g: Node, wallImg: Image, rtex: Image) {
        rig = g.add(Node("Rig"))
        val f = rig.add(Node("Frame")).also { it.pos = P3(0f, 0f, L1 / 2) }
        // shadow walls: floor (Re–t) and side wall (Im–t), graph paper, time lines every π/2
        val wallMat = Mat.Unlit(Rgb(1f, 1f, 1f), tex = wallImg, blend = Blend.TRANSPARENT)
        fun wall(name: String, o: FloatArray, U: FloatArray, V: FloatArray) {
            val md = OptGeo.grid(2, 2)
            for (i in 0..1) for (j in 0..1) {
                val k = j * 2 + i
                OptGeo.setPos(md, k, o[0] + U[0] * i + V[0] * j, o[1] + U[1] * i + V[1] * j, o[2] + U[2] * i + V[2] * j)
                OptGeo.setUv(md, k, i.toFloat(), j.toFloat())
            }
            f.add(name, md, wallMat)
        }
        val len = L1 + 0.06f
        wall("Floor", floatArrayOf(-WALL, -WALL, 0.03f), floatArrayOf(0f, 0f, -len), floatArrayOf(2 * WALL, 0f, 0f))
        wall("SideWall", floatArrayOf(-WALL, -WALL, 0.03f), floatArrayOf(0f, 0f, -len), floatArrayOf(0f, 2 * WALL, 0f))
        // the graphs' own time axes (value 0) on each wall
        f.add("FloorAxis", cyl, glow(WHITE, 0.45f)).span(0f, -WALL + 0.002f, 0.03f, 0f, -WALL + 0.002f, -L1 - 0.03f, 0.0014f)
        f.add("WallAxis", cyl, glow(WHITE, 0.45f)).span(-WALL + 0.002f, 0f, 0.03f, -WALL + 0.002f, 0f, -L1 - 0.03f, 0.0014f)
        // the front plane: Re and Im axes, the unit circle, ticks
        f.add("ReAxis", cyl, core(Rgb(0.75f, 0.78f, 0.85f))).span(-WALL, 0f, FZ, WALL + 0.01f, 0f, FZ, 0.0018f)
        f.add("ImAxis", cyl, core(Rgb(0.75f, 0.78f, 0.85f))).span(0f, -WALL, FZ, 0f, WALL + 0.01f, FZ, 0.0018f)
        f.add("Unit1", ringMesh(R1, 0.0016f), glow(WHITE, 0.55f), P3(0f, 0f, FZ))
        f.add("FrameSq", ringMesh(WALL * 1.414f, 0.0012f, 4, 4), glow(BLUE, 0.35f), P3(0f, 0f, FZ)).also { it.rot = Q.axisAngle(0f, 0f, 1f, PI.toFloat() / 4) }
        decal(f, "LRe", "Re", 0.026f, DIM, P3(WALL + 0.03f, 0.022f, FZ + 0.02f))
        decal(f, "LIm", "Im", 0.026f, DIM, P3(0f, WALL + 0.03f, FZ))
        decal(f, "L1", "1", 0.022f, DIM, P3(R1 + 0.016f, -0.022f, FZ))
        decal(f, "Li", "i", 0.022f, DIM, P3(-0.018f, R1 + 0.02f, FZ))
        // time axis into depth, with an arrow head and "t"
        f.add("TAxis", cyl, core(Rgb(0.75f, 0.78f, 0.85f))).span(0f, 0f, FZ, 0f, 0f, -L1 - 0.03f, 0.0020f)
        f.add("THead", cone, core(Rgb(0.80f, 0.83f, 0.90f))).also { it.pos = P3(0f, 0f, -L1 - 0.03f); it.rot = Q.fromTo(0f, 1f, 0f, 0f, 0f, -1f); it.scale = P3(0.009f, 0.024f, 0.009f) }
        decal(f, "LT", "t", 0.030f, WHITE, P3(0.025f, 0.025f, -L1 - 0.04f))
        // the helix and its three shadows (rebuilt on ω/σ change only)
        val zero = FloatArray(NS * 3) { if (it % 3 == 2) -it / 3 * 0.001f else 0f }
        helixMd = MeshGen.tube(zero, 0.0042f, 6, false)
        helixGlowMd = MeshGen.tube(zero, 0.011f, 4, false)
        f.add("Helix", helixMd, core(YELLOW))
        f.add("HelixGlow", helixGlowMd, glow(YELLOW, 0.22f))
        cosMd = strip(NS); sinMd = strip(NS); frontMd = strip(NS)
        f.add("CosShadow", cosMd, core(BLUE))
        f.add("SinShadow", sinMd, core(RED))
        f.add("FrontShadow", frontMd, glow(YELLOW, 0.55f))
        // the running point, its projections and the phasor on the front plane
        point1 = f.add("Point1", ball, Mat.Unlit(Rgb(1f, 0.97f, 0.85f))).also { it.uniformScale(0.011f); it.tapRadius = 0.06f / 0.011f }
        point1Glow = f.add("Point1G", glowQuad, Mat.Unlit(YELLOW, alpha = 0.55f, tex = rtex, blend = Blend.ADD)).also { it.billboard = true; it.uniformScale(0.06f) }
        dotFloor = f.add("DotFloor", ball, core(BLUE)).also { it.uniformScale(0.0075f) }
        dotWall = f.add("DotWall", ball, core(RED)).also { it.uniformScale(0.0075f) }
        dotFront = f.add("DotFront", ball, core(YELLOW)).also { it.uniformScale(0.0075f) }
        projFloor = f.add("ProjFloor", cyl, glow(BLUE, 0.75f))
        projWall = f.add("ProjWall", cyl, glow(RED, 0.75f))
        projFront = f.add("ProjFront", cyl, glow(YELLOW, 0.45f))
        phShaft = f.add("PhShaft", cyl, core(YELLOW)); phHead = f.add("PhHead", cone, core(YELLOW))
        tMark = f.add("TMark", ball, core(WHITE)).also { it.uniformScale(0.006f) }
        lblHelix = decal(f, "LHelix", "e^{iωt}", 0.036f, YELLOW, P3(R1 * 0.8f, R1 + 0.075f, -L1 * 0.42f))
        lblCos = decal(f, "LCos", "`cos` ωt", 0.030f, BLUE, P3(WALL + 0.07f, -WALL, -L1 * 0.80f))
        lblSin = decal(f, "LSin", "`sin` ωt", 0.030f, RED, P3(-WALL, WALL + 0.035f, -L1 * 0.80f))
        // grab the end of the time axis to swing the whole object round
        turnKnob = f.add("TurnKnob", ball, solid(Rgb(0.55f, 0.85f, 1.0f), 0.5f)).also { it.pos = P3(0f, 0f, -L1 - 0.075f); it.uniformScale(0.016f); it.tapRadius = 0.06f / 0.016f }
        // ω and σ rails (world-fixed, in front, below the action)
        g.add("OmRail", cyl, alu).span(OM_X0 - 3 * OM_K - 0.01f, RAIL_Y, RAIL_Z, OM_X0 + 3 * OM_K + 0.01f, RAIL_Y, RAIL_Z, 0.0030f)
        for (k in -3..3) {
            val x = OM_X0 + k * OM_K
            g.add("OmT${k + 3}", cyl, if (k == 0) glow(WHITE, 0.9f) else core(DIM)).span(x, RAIL_Y - 0.012f, RAIL_Z, x, RAIL_Y + 0.012f, RAIL_Z, if (k == 0) 0.0022f else 0.0014f)
        }
        decal(g, "OmL", "ω", 0.034f, YELLOW, P3(OM_X0 - 3 * OM_K - 0.045f, RAIL_Y, RAIL_Z))
        decal(g, "OmLm", "−3", 0.020f, DIM, P3(OM_X0 - 3 * OM_K, RAIL_Y - 0.030f, RAIL_Z))
        decal(g, "OmL0", "0", 0.020f, DIM, P3(OM_X0, RAIL_Y - 0.030f, RAIL_Z))
        decal(g, "OmLp", "3", 0.020f, DIM, P3(OM_X0 + 3 * OM_K, RAIL_Y - 0.030f, RAIL_Z))
        g.add("SgRail", cyl, alu).span(SG_X1 - 0.4f * SG_K - 0.01f, RAIL_Y, RAIL_Z, SG_X1 + 0.01f, RAIL_Y, RAIL_Z, 0.0030f)
        for (k in 0..4) {
            val x = SG_X1 - k * 0.1f * SG_K
            g.add("SgT$k", cyl, if (k == 0) glow(WHITE, 0.9f) else core(DIM)).span(x, RAIL_Y - 0.012f, RAIL_Z, x, RAIL_Y + 0.012f, RAIL_Z, if (k == 0) 0.0022f else 0.0014f)
        }
        decal(g, "SgL", "σ", 0.034f, GREEN, P3(SG_X1 - 0.4f * SG_K - 0.045f, RAIL_Y, RAIL_Z))
        decal(g, "SgL0", "0", 0.020f, DIM, P3(SG_X1, RAIL_Y - 0.030f, RAIL_Z))
        decal(g, "SgLm", "−0.4", 0.020f, DIM, P3(SG_X1 - 0.4f * SG_K, RAIL_Y - 0.030f, RAIL_Z))
        omegaKnob = g.add("OmegaKnob", ball, solid(YELLOW)).also { it.uniformScale(0.015f); it.tapRadius = 0.05f / 0.015f }
        sigmaKnob = g.add("SigmaKnob", ball, solid(GREEN)).also { it.uniformScale(0.015f); it.tapRadius = 0.05f / 0.015f }
    }

    /** Rebuild the helix tube and its three shadows for the current ω, σ (in place; ~2.9k vertices). */
    private fun rebuildHelix() {
        val p = Euler.helixPath(omega.toDouble(), sigma.toDouble(), T_MAX.toDouble(), NS)
        val path = FloatArray(NS * 3); val xs = FloatArray(NS); val ys = FloatArray(NS); val zs = FloatArray(NS)
        for (k in 0 until NS) {
            xs[k] = R1 * p[k * 3].toFloat(); ys[k] = R1 * p[k * 3 + 1].toFloat(); zs[k] = -KT * p[k * 3 + 2].toFloat()
            path[k * 3] = xs[k]; path[k * 3 + 1] = ys[k]; path[k * 3 + 2] = zs[k]
        }
        MeshGen.tubeInto(helixMd, path, 0.0042f, 6, false)
        MeshGen.tubeInto(helixGlowMd, path, 0.011f, 4, false)
        // floor: (x, z) at y = −WALL; side wall: (y, z) at x = −WALL; front: (x, y) at z = FZ
        val nz = FloatArray(NS) { -zs[it] }      // B = −z so the strip faces UP (opaque strips are back-face culled)
        stripInto(cosMd, NS, xs, nz, 0.0065f, floatArrayOf(0f, -WALL + 0.003f, 0f), floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 0f, -1f))
        stripInto(sinMd, NS, ys, zs, 0.0065f, floatArrayOf(-WALL + 0.003f, 0f, 0f), floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 0f, 1f))
        stripInto(frontMd, NS, xs, ys, 0.0040f, floatArrayOf(0f, 0f, FZ + 0.001f), XY.first, XY.second)
        meshEdited(helixMd); meshEdited(helixGlowMd); meshEdited(cosMd); meshEdited(sinMd); meshEdited(frontMd)
        builtOmega = omega; builtSigma = sigma; cooldown = 0.12f; helixBuilds++
    }

    // ---------------------------------------------------------------- ② multiplication
    private fun build2(g: Node, rtex: Image) {
        val o = O2
        g.add("Pane2", OptGeo.quad(2 * B2 * U2 + 0.04f, 2 * B2 * U2 + 0.04f), pane, P3(o.x, o.y, o.z - 0.012f))
        grid2 = Array(18) { i ->
            val k = i % 9 - 4; val vertical = i < 9; val axis = k == 0
            val n = g.add("Grid$i", cyl, if (axis) core(Rgb(0.75f, 0.78f, 0.85f)) else glow(BLUE, 0.28f))
            val c = k * 0.5f * U2; val e = B2 * U2
            if (vertical) n.span(o.x + c, o.y - e, o.z, o.x + c, o.y + e, o.z, if (axis) 0.0018f else 0.0011f)
            else n.span(o.x - e, o.y + c, o.z, o.x + e, o.y + c, o.z, if (axis) 0.0018f else 0.0011f)
            n
        }
        wgrid = Array(18) { i -> g.add("WGrid$i", cyl, glow(GOLD, if (i % 9 == 4) 0.55f else 0.26f)) }
        g.add("Unit2", ringMesh(U2, 0.0013f), glow(WHITE, 0.45f), P3(o.x, o.y, o.z + 0.002f))
        decal(g, "L2Re", "Re", 0.024f, DIM, P3(o.x + B2 * U2 + 0.035f, o.y, 0f), false)
        decal(g, "L2Im", "Im", 0.024f, DIM, P3(o.x, o.y + B2 * U2 + 0.025f, 0f), false)
        decal(g, "L21", "1", 0.022f, DIM, P3(o.x + U2 + 0.012f, o.y - 0.02f, 0.004f), false)
        decal(g, "L2i", "i", 0.022f, DIM, P3(o.x - 0.016f, o.y + U2 + 0.018f, 0.004f), false)
        decal(g, "L2m1", "−1", 0.022f, DIM, P3(o.x - U2 - 0.02f, o.y - 0.02f, 0.004f), false)
        // similar triangles (0, 1, w) and (0, z, zw)
        triMd = Array(2) { MeshData(FloatArray(9), floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f), FloatArray(6), intArrayOf(0, 1, 2)) }
        g.add("Tri1", triMd[0], glow(RED, 0.13f)); g.add("Tri2", triMd[1], glow(YELLOW, 0.13f))
        triEdge = arrayOf(g.add("TriE1", cyl, glow(RED, 0.55f)), g.add("TriE2", cyl, glow(YELLOW, 0.55f)))
        // angle arcs: arg z, arg w, and arg z + arg w as one composite arc (blue then red), quarter-turn arc
        arcMd = Array(5) { strip(ARC_N) }
        val arcCol = arrayOf(BLUE, RED, BLUE, RED, GREEN)
        arcNodes = Array(5) { g.add("Arc$it", arcMd[it], if (it >= 2) core(arcCol[it]) else glow(arcCol[it], 0.85f)) }
        // arrows
        gShaft = g.add("GShaft", cyl, glow(BLUE, 0.35f)); gHead = g.add("GHead", cone, glow(BLUE, 0.35f))
        zShaft = g.add("ZShaft", cyl, core(BLUE)); zHead = g.add("ZHead", cone, core(BLUE))
        wShaft = g.add("WShaft", cyl, core(RED)); wHead = g.add("WHead", cone, core(RED))
        zwShaft = g.add("ZWShaft", cyl, core(YELLOW)); zwHead = g.add("ZWHead", cone, core(YELLOW))
        zKnob = g.add("ZKnob", ball, solid(BLUE)).also { it.uniformScale(0.014f); it.tapRadius = 0.05f / 0.014f }
        wKnob = g.add("WKnob", ball, solid(RED)).also { it.uniformScale(0.014f); it.tapRadius = 0.05f / 0.014f }
        zwDot = g.add("ZWDot", ball, Mat.Unlit(Rgb(1f, 0.95f, 0.75f))).also { it.uniformScale(0.010f) }
        zwGlow = g.add("ZWGlow", glowQuad, Mat.Unlit(YELLOW, alpha = 0.5f, tex = rtex, blend = Blend.ADD)).also { it.billboard = true; it.uniformScale(0.05f) }
        lblZ = decal(g, "LZ", "z", 0.036f, BLUE); lblW = decal(g, "LW", "w", 0.036f, RED); lblZW = decal(g, "LZW", "z·w", 0.036f, YELLOW)
        // the ×i plaque and the power of i it has applied
        timesI = g.add(Node("TimesI")).also { it.pos = P3(0.36f, 0.28f, 0.02f); it.tapRadius = 0.06f }
        timesI.add("TimesIP", MeshGen.box(0.13f, 0.080f, 0.012f), plateOff, P3(0f, 0f, -0.012f))
        decal(timesI, "TimesIT", "×i", 0.048f, GREEN, P3(0f, 0.004f, -0.004f), false)
        powLbl = decal(g, "PowL", "i^2 = −1", 0.036f, GREEN, P3(0.36f, 0.18f, 0.02f), false)
        decal(g, "AbsF", "absf", 0.026f, WHITE, P3(0.40f, 0.05f, 0.0f), false)
        decal(g, "ArgF", "argf", 0.026f, WHITE, P3(0.42f, -0.03f, 0.0f), false)
    }

    // ---------------------------------------------------------------- ③ the series
    private fun build3(g: Node, rtex: Image) {
        val o = O3
        g.add("Pane3", OptGeo.quad(0.70f, 0.62f), pane, P3(0.07f, 0.11f, -0.015f))
        g.add("Re3", cyl, core(Rgb(0.75f, 0.78f, 0.85f))).span(-0.265f, o.y, 0f, 0.41f, o.y, 0f, 0.0016f)
        g.add("Im3", cyl, core(Rgb(0.75f, 0.78f, 0.85f))).span(o.x, -0.18f, 0f, o.x, 0.41f, 0f, 0.0016f)
        circle3 = g.add("Unit3", ringMesh(1f, 0.016f), glow(WHITE, 0.6f), P3(o.x, o.y, 0.001f))
        ticks3 = Array(4) { g.add("Tk3_$it", cyl, core(WHITE)) }
        tickLbl3 = arrayOf(decal(g, "Tl3_0", "−1", 0.022f, DIM), decal(g, "Tl3_1", "i", 0.022f, DIM))
        walkMd = strip(49)
        g.add("Walk", walkMd, core(GOLD))
        radius3 = g.add("Rad3", cyl, glow(WHITE, 0.55f))
        chainShaft = Array(N_CHAIN) { k -> g.add("ChS$k", cyl, core(CHAIN_COL[k % 4])) }
        chainHead = Array(N_CHAIN) { k -> g.add("ChH$k", cone, core(CHAIN_COL[k % 4])) }
        sumDot = g.add("SumDot", ball, core(WHITE)).also { it.uniformScale(0.0075f) }
        tanShaft = g.add("TanS", cyl, glow(GREEN, 0.8f)); tanHead = g.add("TanH", cone, glow(GREEN, 0.8f))
        walker = g.add("Walker", ball, solid(GOLD, 0.6f)).also { it.uniformScale(0.014f); it.tapRadius = 0.05f / 0.014f }
        walkerGlow = g.add("WalkerG", glowQuad, Mat.Unlit(GOLD, alpha = 0.55f, tex = rtex, blend = Blend.ADD)).also { it.billboard = true; it.uniformScale(0.06f) }
        termLbl = arrayOf(decal(g, "Tm0", "1", 0.024f, CHAIN_COL[0]), decal(g, "Tm1", "iθ", 0.024f, CHAIN_COL[1]),
            decal(g, "Tm2", "(iθ)^2/2!", 0.024f, CHAIN_COL[2]), decal(g, "Tm3", "(iθ)^3/3!", 0.024f, CHAIN_COL[3]))
        thetaLbl = decal(g, "ThL", "θ", 0.026f, GOLD)
        eLbl = decal(g, "EL", "e^{iθ}", 0.030f, GOLD)
        // the left column: the series (definition) and, at θ = π, the identity
        decal(g, "Series", "series", 0.027f, WHITE, P3(-0.50f, 0.33f, 0.01f), false)
        eipi = decal(g, "Eipi", "eipi", 0.056f, WHITE, P3(-0.47f, 0.17f, 0.01f), false)
        val tag = g.add(Node("SeriesTag")).also { it.pos = P3(-0.47f, 0.02f, 0.02f); it.tapRadius = 0.06f }
        tag.add("SeriesTagP", MeshGen.box(0.20f, 0.075f, 0.012f), plateOff, P3(0f, 0f, -0.012f))
        decal(tag, "SeriesTagT", "(iθ)^k/k!", 0.032f, WHITE, P3(0f, 0.004f, -0.004f), false)
    }

    // ---------------------------------------------------------------- the station shelf
    private fun buildTokens(root: Node) {
        val plateMesh = MeshGen.box(0.130f, 0.105f, 0.012f)
        tokens = Array(3) { k -> root.add(Node("Tok$k")).also { it.pos = P3(TOK_X, TOK_Y[k], 0.02f); it.tapRadius = 0.06f } }
        plates = Array(3) { k -> tokens[k].add("Plate$k", plateMesh, plateOff, P3(0f, 0f, -0.02f)) }
        val zz = -0.008f
        // ① a little helix along x
        val n = 60; val hp = FloatArray(n * 3)
        for (k in 0 until n) { val t = k / (n - 1f); hp[k * 3] = -0.045f + 0.09f * t; hp[k * 3 + 1] = 0.026f * cos(t * 4 * PI).toFloat(); hp[k * 3 + 2] = 0.026f * sin(t * 4 * PI).toFloat() }
        tokens[0].add("Tm0H", MeshGen.tube(hp, 0.0028f, 5, false), core(YELLOW), P3(0f, 0.004f, zz))
        tokens[0].add("Tm0A", cyl, core(DIM)).span(-0.05f, 0.004f, zz, 0.05f, 0.004f, zz, 0.0012f)
        // ② z, w and z·w
        fun miniArrow(p: Node, name: String, a: Double, l: Float, c: Rgb) {
            val x = (l * cos(a)).toFloat(); val y = (l * sin(a)).toFloat()
            arrow(p.add("${name}S", cyl, core(c)), p.add("${name}H", cone, core(c)), -0.035f, -0.03f, zz, -0.035f + x, -0.03f + y, zz, 0.0022f, 0.014f, 0.006f)
        }
        miniArrow(tokens[1], "Tm1z", PI / 9, 0.060f, BLUE); miniArrow(tokens[1], "Tm1w", PI / 3.2, 0.045f, RED); miniArrow(tokens[1], "Tm1zw", PI * 0.45 + 0.1, 0.070f, YELLOW)
        // ③ the unit circle, a half turn and −1
        tokens[2].add("Tm2C", ringMesh(0.032f, 0.0016f, 5, 48), glow(WHITE, 0.7f), P3(0.0f, 0f, zz))
        val half = strip(ARC_N); arcInto(half, 0f, 0f, zz + 0.002f, 0.032f, 0.0, PI, 0.005f)
        tokens[2].add("Tm2A", half, core(GOLD))
        tokens[2].add("Tm2D", ball, solid(GOLD, 0.6f), P3(-0.032f, 0f, zz)).also { it.uniformScale(0.008f) }
    }

    // ================================================================ per frame

    override fun update(dt: Float) {
        clock += dt
        for (s in 0 until 3) { groups[s].visible = s == station; plates[s].setMat(if (s == station) plateOn else plateOff) }
        when (station) { 0 -> update1(dt); 1 -> update2(dt); else -> update3(dt) }
        poolGlow.at(when (station) { 0 -> -0.06f; 1 -> O2.x; else -> 0.0f }, PLINTH_Y + 0.019f, 0f)
        readoutText = when (station) { 0 -> readout1(); 1 -> readout2(); else -> readout3() }
        updateLabels()
    }

    /** Frame-local point (helix rig) -> volume space. */
    private fun frameToWorld(x: Float, y: Float, zz: Float): P3 {
        val r = rigQ.rotate(floatArrayOf(x * rigS, y * rigS, (zz + L1 / 2) * rigS))
        return P3(P1X + r[0], rigY + r[1], P1Z + r[2])
    }

    // ---------------------------------------------------------------- ①
    private fun update1(dt: Float) {
        val turn0 = floor(abs(builtOmega) * t1 / (2 * PI.toFloat()))
        if (play1) t1 = (t1 + dt * spd * RATE1) % T_MAX
        // the running point completes one more turn of the helix (one period of cos ωt and sin ωt): a quiet tick
        if (play1 && dt > 0f && floor(abs(builtOmega) * t1 / (2 * PI.toFloat())) > turn0) Sfx.play(Sfx.TICK, 0.18f, 1.1f, P1X / 0.9f)
        cooldown -= dt
        if ((omega != builtOmega || sigma != builtSigma) && cooldown <= 0f) rebuildHelix()
        // the rig: swing toward the chosen view
        yaw = approach(yaw, yawT, 160f * spd, dt); pitch = approach(pitch, pitchT, 160f * spd, dt)
        val up = max(0f, pitch) / 90f
        rigS = 1f - 0.22f * up; rigY = 0.085f + 0.02f * up
        rigQ = Q.axisAngle(1f, 0f, 0f, Math.toRadians(pitch.toDouble()).toFloat()) * Q.axisAngle(0f, 1f, 0f, Math.toRadians(yaw.toDouble()).toFloat())
        rig.turn(rigQ); rig.dims(rigS, rigS, rigS); rig.at(P1X, rigY, P1Z)
        // the running point (on the DRAWN helix: built parameters) and its three shadows
        val zc = Euler.helix(builtOmega.toDouble(), builtSigma.toDouble(), t1.toDouble())
        val x = R1 * zc.re.toFloat(); val y = R1 * zc.im.toFloat(); val zz = -t1 * KT
        point1.at(x, y, zz); point1Glow.at(x, y, zz)
        dotFloor.at(x, -WALL + 0.004f, zz); dotWall.at(-WALL + 0.004f, y, zz); dotFront.at(x, y, FZ + 0.002f)
        projFloor.span(x, y, zz, x, -WALL + 0.004f, zz, 0.0013f)
        projWall.span(x, y, zz, -WALL + 0.004f, y, zz, 0.0013f)
        projFront.span(x, y, zz, x, y, FZ + 0.002f, 0.0010f)
        arrow(phShaft, phHead, 0f, 0f, FZ + 0.002f, x, y, FZ + 0.002f, 0.0026f, 0.022f, 0.009f)
        tMark.at(0f, 0f, zz)
        val damped = builtSigma < -1e-4f
        setDecal(lblHelix, if (damped) "e^{(σ+iω)t}" else "e^{iωt}", 0.034f)
        setDecal(lblCos, if (damped) "e^{σt}`cos` ωt" else "`cos` ωt", 0.030f)
        setDecal(lblSin, if (damped) "e^{σt}`sin` ωt" else "`sin` ωt", 0.030f)
        lblCos.at(WALL + 0.02f + decalWidth(if (damped) "e^{σt}`cos` ωt" else "`cos` ωt", 0.030f) / 2, -WALL, -L1 * 0.80f)
        omegaKnob.at(OM_X0 + omega * OM_K, RAIL_Y, RAIL_Z)
        sigmaKnob.at(SG_X1 + sigma * SG_K, RAIL_Y, RAIL_Z)
    }

    // ---------------------------------------------------------------- ②
    private fun p2(c: Complex) = floatArrayOf(O2.x + U2 * c.re.toFloat(), O2.y + U2 * c.im.toFloat())

    /** Liang–Barsky: the part of segment a→b inside the square |re|, |im| ≤ B2 (null = none). */
    private fun clip(a: Complex, b: Complex): Pair<Complex, Complex>? {
        var t0 = 0.0; var t1c = 1.0
        val dx = b.re - a.re; val dy = b.im - a.im
        val p = doubleArrayOf(-dx, dx, -dy, dy); val q = doubleArrayOf(a.re + B2, B2 - a.re, a.im + B2, B2 - a.im)
        for (i in 0..3) {
            if (abs(p[i]) < 1e-12) { if (q[i] < 0) return null; continue }
            val r = q[i] / p[i]
            if (p[i] < 0) t0 = max(t0, r) else t1c = min(t1c, r)
            if (t0 > t1c) return null
        }
        return Complex(a.re + dx * t0, a.im + dy * t0) to Complex(a.re + dx * t1c, a.im + dy * t1c)
    }

    private fun update2(dt: Float) {
        // ×i animation: z turns a quarter turn about the origin, |z| fixed
        if (animS >= 0f) {
            animS += dt * spd / 0.9f
            if (animS >= 1f) {
                z = Euler.quarterTurns(zFrom, 1); animS = -1f; quarterTurns++; ghostT = 3f
                // ×i lands: z has turned a quarter; on the 2nd turn (i² = −1) a bell, otherwise a pop rising with the power
                if (quarterTurns % 4 == 2) Sfx.play(Sfx.CHIME, 0.5f, 1f, O2.x / 0.9f)
                else Sfx.play(Sfx.POP, 0.45f, 0.9f + 0.1f * (quarterTurns % 4), O2.x / 0.9f)
                if (pendingTurns > 0) { pendingTurns--; startTurn() }
                bump()
            } else z = zFrom * Euler.cis(PI / 2 * sm(animS))
        } else if (ghostT > 0f) ghostT -= dt
        val zw = z * w
        val zc = 0.012f
        val pz = p2(z); val pw = p2(w); val pzw = p2(zw)
        arrow(zShaft, zHead, O2.x, O2.y, zc, pz[0], pz[1], zc, 0.0036f, 0.026f, 0.011f)
        arrow(wShaft, wHead, O2.x, O2.y, zc, pw[0], pw[1], zc, 0.0036f, 0.026f, 0.011f)
        arrow(zwShaft, zwHead, O2.x, O2.y, zc + 0.002f, pzw[0], pzw[1], zc + 0.002f, 0.0042f, 0.028f, 0.012f)
        zKnob.at(pz[0], pz[1], zc); wKnob.at(pw[0], pw[1], zc); zwDot.at(pzw[0], pzw[1], zc + 0.002f)
        zwGlow.at(pzw[0], pzw[1], zc + 0.004f)
        // ghost of z before the last quarter turn + the 90° arc
        val ghostOn = animS >= 0f || ghostT > 0f
        val pf = p2(zFrom)
        arrow(gShaft, gHead, O2.x, O2.y, zc - 0.002f, pf[0], pf[1], zc - 0.002f, 0.0026f, 0.020f, 0.009f, ghostOn)
        arcNodes[4].visible = ghostOn
        // decals at the tips (outward)
        fun tip(n: Node, c: Complex, extra: Float) {
            val l = c.abs.toFloat().coerceAtLeast(1e-3f); val ux = c.re.toFloat() / l; val uy = c.im.toFloat() / l
            n.at(O2.x + U2 * c.re.toFloat() + ux * extra, O2.y + U2 * c.im.toFloat() + uy * extra, 0.03f)
        }
        tip(lblZ, z, 0.04f); tip(lblW, w, 0.04f); tip(lblZW, zw, 0.05f)
        // the grid multiplied by w (1 -> w, i -> iw): the whole plane rotates by arg w, scales by |w|
        for (i in 0 until 18) {
            val n = wgrid[i]
            if (!showWGrid) { n.visible = false; continue }
            val k = (i % 9 - 4) * 0.5
            val a = if (i < 9) Complex(k, -B2.toDouble()) else Complex(-B2.toDouble(), k)
            val b = if (i < 9) Complex(k, B2.toDouble()) else Complex(B2.toDouble(), k)
            val c = clip(w * a, w * b)
            if (c == null) { n.visible = false; continue }
            val q0 = p2(c.first); val q1 = p2(c.second)
            n.span(q0[0], q0[1], 0.004f, q1[0], q1[1], 0.004f, if (k == 0.0) 0.0016f else 0.0011f)
        }
        // arcs + triangles: mesh edits only when something changed (≤ 5 × 66 + 6 vertices)
        if (zw != lastZW || animS >= 0f) {
            lastZW = zw
            val az = z.arg; val aw = w.arg
            arcInto(arcMd[0], O2.x, O2.y, zc - 0.004f, 0.056f, 0.0, az, 0.0035f)
            arcInto(arcMd[1], O2.x, O2.y, zc - 0.004f, 0.076f, 0.0, aw, 0.0035f)
            arcInto(arcMd[2], O2.x, O2.y, zc - 0.004f, 0.106f, 0.0, az, 0.0050f)
            arcInto(arcMd[3], O2.x, O2.y, zc - 0.004f, 0.106f, az, az + aw, 0.0050f)
            val a0 = zFrom.arg
            arcInto(arcMd[4], O2.x, O2.y, zc - 0.004f, max(0.03f, 0.6f * U2 * z.abs.toFloat()), a0, a0 + PI / 2 * (if (animS >= 0f) sm(animS) else 1f), 0.0045f)
            for (m in arcMd) meshEdited(m)
            val one = p2(Complex.ONE)
            fun tri(md: MeshData, b: FloatArray, c: FloatArray) {
                OptGeo.setPos(md, 0, O2.x, O2.y, 0.006f); OptGeo.setPos(md, 1, b[0], b[1], 0.006f); OptGeo.setPos(md, 2, c[0], c[1], 0.006f); meshEdited(md)
            }
            tri(triMd[0], one, pw); tri(triMd[1], pz, pzw)
            triEdge[0].span(one[0], one[1], 0.007f, pw[0], pw[1], 0.007f, 0.0016f)
            triEdge[1].span(pz[0], pz[1], 0.007f, pzw[0], pzw[1], 0.007f, 0.0016f)
        }
        val m = quarterTurns % 4
        powLbl.visible = quarterTurns > 0
        if (quarterTurns > 0) setDecal(powLbl, when (m) { 1 -> "90°"; 2 -> "i^2 = −1"; 3 -> "i^3 = −i"; else -> "i^4 = 1" }, 0.036f)
    }

    // ---------------------------------------------------------------- ③
    private val LANDMARKS = doubleArrayOf(PI / 2, PI, 3 * PI / 2, 2 * PI)
    private fun chainFull(): Int = Euler.termsFor(theta, 2e-3, N_CHAIN).coerceIn(1, N_CHAIN)

    private fun update3(dt: Float) {
        if (walking) {
            val step = 0.9 * spd * dt
            val th0 = theta
            theta = if (abs(thetaT - theta) <= step) thetaT else theta + step * Math.signum(thetaT - theta)
            // the walker reaches θ = π: e^{iπ} = −1 — the identity rings
            if ((th0 < PI - 1e-9 && theta >= PI - 1e-9) || (th0 > PI + 1e-9 && theta <= PI + 1e-9)) Sfx.play(Sfx.CHIME, 0.65f, 1f, O3.x / 0.9f)
            if (theta == thetaT) { walking = false; playSeries() }
        }
        val full = chainFull()
        if (chainPlay) {
            val n0 = floor(chainN)
            chainN = min(full.toFloat(), chainN + 2.5f * spd * dt); if (chainN >= full) chainPlay = false
            // one more term (iθ)ᵏ/k! of the series snaps onto the chain: a quiet tick, rising with k
            if (floor(chainN) > n0) Sfx.play(Sfx.TICK, 0.2f, 0.8f + 0.06f * floor(chainN), O3.x / 0.9f)
        }
        else chainN = full.toFloat()
        val sums = Euler.partialSums(theta, N_CHAIN)
        // zoom: the chain must fit the pane (it swings out to |S| ≈ 4 at π, ≈ 80 at 2π)
        var nRe = 1.0; var pRe = 1.0; var nIm = 1.0; var pIm = 1.0
        for (k in 0 until full) { val s = sums[k]; nRe = max(nRe, -s.re); pRe = max(pRe, s.re); nIm = max(nIm, -s.im); pIm = max(pIm, s.im) }
        val target = minOf(U3.toDouble(), 0.455 / nRe, 0.19 / pRe, 0.335 / pIm, 0.225 / nIm).toFloat()
        s3 = if (dt <= 0f || abs(s3 - target) < 1e-4f) target else s3 + (target - s3) * min(1f, 6f * dt)
        val s = s3
        fun px(c: Complex) = O3.x + s * c.re.toFloat()
        fun py(c: Complex) = O3.y + s * c.im.toFloat()
        circle3.uniformScale(s)
        val e = Euler.cis(theta)
        if (theta != lastTheta || s != lastS3) {
            lastTheta = theta; lastS3 = s
            arcInto(walkMd, O3.x, O3.y, 0.004f, s, 0.0, max(theta, 1e-4), 0.0065f, 49); meshEdited(walkMd)
        }
        walker.at(px(e), py(e), 0.012f); walkerGlow.at(px(e), py(e), 0.014f)
        radius3.span(O3.x, O3.y, 0.003f, px(e), py(e), 0.003f, 0.0012f)
        val ix = -e.im.toFloat(); val iy = e.re.toFloat()           // velocity i·e^{iθ}: perpendicular to the radius
        arrow(tanShaft, tanHead, px(e), py(e), 0.010f, px(e) + 0.065f * ix, py(e) + 0.065f * iy, 0.010f, 0.0024f, 0.018f, 0.008f)
        // ticks at ±1, ±i
        val tk = arrayOf(Complex.ONE, Complex.I, Complex(-1.0, 0.0), Complex(0.0, -1.0))
        for (i in 0..3) { val c = tk[i]; val nx = (-c.im).toFloat() * 0.012f; val ny = c.re.toFloat() * 0.012f
            ticks3[i].span(px(c) - nx, py(c) - ny, 0.002f, px(c) + nx, py(c) + ny, 0.002f, 0.0015f) }
        tickLbl3[0].at(px(tk[2]) - 0.018f, O3.y - 0.022f, 0.006f); tickLbl3[1].at(O3.x - 0.016f, py(tk[1]) + 0.018f, 0.006f)
        // the chain: arrow k from S_k to S_{k+1}; the one being drawn grows
        val nFull = floor(chainN).toInt(); val frac = chainN - nFull
        var head = Complex.ZERO
        for (k in 0 until N_CHAIN) {
            val a = if (k == 0) Complex.ZERO else sums[k - 1]
            val b = sums[k]
            val show = k < nFull || (k == nFull && frac > 0.02f && k < full)
            val bb = if (k < nFull) b else a + (b - a) * frac.toDouble()
            arrow(chainShaft[k], chainHead[k], px(a), py(a), 0.016f + 0.0004f * k, px(bb), py(bb), 0.016f + 0.0004f * k, 0.0030f, 0.020f, 0.0085f, show)
            if (show) head = bb
        }
        sumDot.visible = chainN > 0.02f
        sumDot.at(px(head), py(head), 0.03f)
        // term labels, outside the spiral (away from the limit point e^{iθ})
        for (k in 0..3) {
            val l = termLbl[k]
            val a = if (k == 0) Complex.ZERO else sums[k - 1]; val b = sums[k]
            val len = (b - a).abs * s
            l.visible = k < nFull && len > 0.07f
            if (!l.visible) continue
            val m = (a + b) * 0.5
            var nx = -(b.im - a.im); var ny = b.re - a.re; val nl = sqrt(nx * nx + ny * ny); nx /= nl; ny /= nl
            if ((m.re - e.re) * nx + (m.im - e.im) * ny < 0) { nx = -nx; ny = -ny }
            val key = when (k) { 0 -> "1"; 1 -> "iθ"; 2 -> "(iθ)^2/2!"; else -> "(iθ)^3/3!" }
            val off = 0.022f + 0.5f * (if (abs(nx) > abs(ny)) decalWidth(key, 0.024f) else 0.024f)
            l.at(px(m) + off * nx.toFloat(), py(m) + off * ny.toFloat(), 0.03f)
        }
        // θ on the arc, e^{iθ} beyond the walker
        val mid = Euler.cis(theta / 2)
        thetaLbl.visible = theta > 0.35
        thetaLbl.at(O3.x + 0.62f * s * mid.re.toFloat(), O3.y + 0.62f * s * mid.im.toFloat(), 0.02f)
        eLbl.at(px(e) + 0.05f * e.re.toFloat() + (if (e.re < 0) -0.012f else 0.012f), py(e) + 0.045f * e.im.toFloat() + 0.012f, 0.03f)
        eipi.visible = abs(theta - PI) < 0.02 && !walking
    }

    // ================================================================ readout + labels
    private fun num(v: Double, d: Int): String {
        val r = if (abs(v) < 0.5 * Math.pow(10.0, -d.toDouble())) 0.0 else v
        return String.format(Locale.US, "%.${d}f", r).replace('-', '−')
    }
    private fun deg(a: Double): String = num(Math.toDegrees(a), 0) + "°"
    private fun cart(c: Complex, d: Int = 2) = num(c.re, d) + (if (c.im < -0.5 * Math.pow(10.0, -d.toDouble())) " − " else " + ") + num(abs(c.im), d) + "i"
    private fun pol(c: Complex) = num(c.abs, 2) + "∠" + deg(c.arg)
    private fun thetaStr(t: Double): String {
        val q = t / (PI / 2); val r = q.roundToInt()
        return if (abs(q - r) < 0.01) when (r) { 0 -> "0"; 1 -> "π/2"; 2 -> "π"; 3 -> "3π/2"; else -> "2π" } else num(t, 2)
    }

    private fun readout1(): String {
        val zc = Euler.helix(builtOmega.toDouble(), builtSigma.toDouble(), t1.toDouble())
        val f = if (sigma < -1e-4f) "e^((σ+iω)t)" else "e^(iωt)"
        val l1 = "$f: t = ${num(t1.toDouble(), 2)} · z = ${pol(zc)} = ${cart(zc)}"
        val dir = when { omega > 0f -> tr("逆时针", "counter-clockwise"); omega < 0f -> tr("顺时针", "clockwise"); else -> tr("不转", "not turning") }
        val per = if (omega == 0f) "∞" else num(Euler.period(omega.toDouble()), 2)
        val l2 = tr("ω = ${num(omega.toDouble(), 2)}（$dir）· σ = ${num(sigma.toDouble(), 2)} · 一圈 2π/|ω| = $per · 半径 e^(σt) = ${num(Euler.radius(sigma.toDouble(), t1.toDouble()), 2)}",
            "ω = ${num(omega.toDouble(), 2)} ($dir) · σ = ${num(sigma.toDouble(), 2)} · one turn 2π/|ω| = $per · radius e^(σt) = ${num(Euler.radius(sigma.toDouble(), t1.toDouble()), 2)}")
        val l3 = when {
            sigma < -1e-4f -> tr("σ < 0：半径按 e^(σt) 收缩成螺旋 —— 墙上的影子就是阻尼振动（弹簧、RLC 电路）",
                "σ < 0: the radius shrinks as e^(σt) — the shadows on the walls are damped oscillations (springs, RLC circuits)")
            view == View.SIDE -> tr("侧面看：螺旋只剩上下起伏 —— 它的影子就是正弦波 sin ωt", "From the side the helix only goes up and down — its shadow is the sine wave sin ωt")
            view == View.TOP -> tr("俯视：只剩左右摆动 —— 地面上的影子是 cos ωt", "From above it only swings left and right — the floor shadow is cos ωt")
            view == View.FRONT -> tr("正面看：时间被压扁，只剩单位圆 —— e^(iωt) 永远在 |z| = 1 上转", "Face on, time collapses: just the unit circle — e^(iωt) always turns on |z| = 1")
            else -> tr("一个三维螺旋，三张图：正面是圆，侧面是 sin ωt，地面是 cos ωt", "One 3-D helix, three graphs: a circle from the front, sin ωt from the side, cos ωt on the floor")
        }
        return "$l1\n$l2\n$l3"
    }

    private fun readout2(): String {
        val zw = z * w
        val l1 = "z = ${pol(z)} = ${cart(z)} · w = ${pol(w)} = ${cart(w)} · z·w = ${pol(zw)} = ${cart(zw)}"
        val sumA = Math.toDegrees(z.arg) + Math.toDegrees(w.arg)
        val l2 = tr("长度相乘：${num(z.abs, 2)} × ${num(w.abs, 2)} = ${num(zw.abs, 2)} · 角度相加：${deg(z.arg)} + ${deg(w.arg)} = ${num(sumA, 0)}°",
            "Lengths multiply: ${num(z.abs, 2)} × ${num(w.abs, 2)} = ${num(zw.abs, 2)} · angles add: ${deg(z.arg)} + ${deg(w.arg)} = ${num(sumA, 0)}°") +
            (if (abs(sumA) > 180.5) tr("（≡ ${deg(zw.arg)}）", " (≡ ${deg(zw.arg)})") else "")
        val l3 = if (quarterTurns == 0) tr("乘以 w = 把整个平面转 arg w、放大 |w| 倍（开「w × 平面」看金色网格）：三角形 (0, 1, w) 与 (0, z, zw) 相似",
                "×w turns the whole plane by arg w and scales it by |w| (switch on 'w × plane'): triangles (0, 1, w) and (0, z, zw) are similar")
            else if (quarterTurns % 4 == 2) tr("×i 两次 = 转 180°：z 变成 −z —— i² = −1 就是两个直角转弯", "×i twice = a 180° turn: z becomes −z — i² = −1 is just two quarter turns")
            else tr("×i：|i| = 1、arg i = 90° —— 乘以 i 就是逆时针转 90°，长度不变（已转 $quarterTurns 次）",
                "×i: |i| = 1, arg i = 90° — multiplying by i is a 90° counter-clockwise turn, same length ($quarterTurns so far)")
        return "$l1\n$l2\n$l3"
    }

    private fun readout3(): String {
        val e = Euler.cis(theta)
        val n = max(1, floor(chainN).toInt())
        val sN = Euler.partialSum(theta, n)
        val l1 = "θ = ${thetaStr(theta)} · e^(iθ) = ${pol(e)} = ${cart(e)} · S_$n = ${cart(sN, 3)}"
        val err = (sN - e).abs
        val zoom = if (s3 < U3 * 0.98f) tr(" · 缩放 1:${num((U3 / s3).toDouble(), 1)}", " · zoom 1:${num((U3 / s3).toDouble(), 1)}") else ""
        val l2 = tr("前 $n 项，误差 |S_n − e^(iθ)| = ${sci(err)} ≤ θⁿ/n! = ${sci(Euler.remainderBound(theta, n))} · 每项 = 上一项 × iθ/k：转 90°$zoom",
            "$n terms, error |S_n − e^(iθ)| = ${sci(err)} ≤ θⁿ/n! = ${sci(Euler.remainderBound(theta, n))} · each term = previous × iθ/k: a 90° turn$zoom")
        val l3 = when {
            abs(theta - PI) < 0.02 -> tr("θ = π：走了半圈，正好落在 −1 —— e^(iπ) + 1 = 0", "θ = π: half a turn lands exactly on −1 — e^(iπ) + 1 = 0")
            abs(theta - 2 * PI) < 0.02 -> tr("θ = 2π：走完一整圈回到 1 —— e^(2πi) = 1", "θ = 2π: one full turn, back at 1 — e^(2πi) = 1")
            else -> tr("e^(iθ) 的速度 = i·e^(iθ)，永远垂直于半径（绿箭头）：所以它沿单位圆走，走过的弧长就是 θ",
                "The velocity of e^(iθ) is i·e^(iθ), always perpendicular to the radius (green): so it walks the unit circle, arc length θ")
        }
        return "$l1\n$l2\n$l3"
    }

    private fun sci(v: Double): String = if (v >= 0.01) num(v, 3) else if (v == 0.0) "0" else String.format(Locale.US, "%.1e", v).replace("e-0", "e−").replace("e-", "e−")

    private fun updateLabels() {
        labels.clear()
        when (station) {
            0 -> {
                val zc = Euler.helix(builtOmega.toDouble(), builtSigma.toDouble(), t1.toDouble())
                val x = R1 * zc.re.toFloat(); val y = R1 * zc.im.toFloat(); val zz = -t1 * KT
                labels += LabelSpec("t = ${num(t1.toDouble(), 2)}", frameToWorld(x, y + 0.045f, zz))
                labels += LabelSpec("Re = ${num(zc.re, 2)}", frameToWorld(x, -WALL - 0.03f, zz))
                labels += LabelSpec("Im = ${num(zc.im, 2)}", frameToWorld(-WALL - 0.05f, y, zz))
                labels += LabelSpec("ω = ${num(omega.toDouble(), 2)}", P3(OM_X0 + omega * OM_K, RAIL_Y + 0.045f, RAIL_Z))
                labels += LabelSpec("σ = ${num(sigma.toDouble(), 2)}", P3(SG_X1 + sigma * SG_K, RAIL_Y + 0.045f, RAIL_Z))
            }
            1 -> {
                val zw = z * w
                fun at(c: Complex, k: Float) = P3(O2.x + U2 * c.re.toFloat() * k, O2.y + U2 * c.im.toFloat() * k + 0.035f, 0.05f)
                labels += LabelSpec("|z| = ${num(z.abs, 2)} · ${deg(z.arg)}", at(z, 1.15f))
                labels += LabelSpec("|w| = ${num(w.abs, 2)} · ${deg(w.arg)}", at(w, 1.15f))
                labels += LabelSpec("|zw| = ${num(zw.abs, 2)} · ${deg(zw.arg)}", at(zw, 1.10f))
            }
            else -> {
                labels += LabelSpec("θ = ${thetaStr(theta)}", P3(walker.pos.x, walker.pos.y + 0.05f, 0.05f))
                labels += LabelSpec(tr("前 ${max(1, floor(chainN).toInt())} 项", "${max(1, floor(chainN).toInt())} terms"), P3(sumDot.pos.x, sumDot.pos.y - 0.04f, 0.05f))
            }
        }
    }

    // ================================================================ interaction

    private val TOK_NAMES = listOf("Tok0", "Tok1", "Tok2")
    private fun stationTargets(s: Int): List<String> = when (s) {
        0 -> listOf("Point1", "TurnKnob", "OmegaKnob", "SigmaKnob")
        1 -> listOf("ZKnob", "WKnob", "TimesI")
        else -> listOf("Walker", "SeriesTag")
    }
    /** Every pinchable node of station [s] (and the shelf), for tests. */
    fun allTargets(s: Int) = stationTargets(s) + TOK_NAMES

    override val tapTargets: List<TapTarget> get() {
        val t = ArrayList<TapTarget>()
        when (station) {
            0 -> {
                t += TapTarget("Point1", "运动点 → 暂停", "Point → pause") { !play1 }
                t += TapTarget("TurnKnob", "时间轴末端 → 换视角", "Time-axis end → next view")
                t += TapTarget("OmegaKnob", "ω 把手 → 反向", "ω knob → reverse")
                t += TapTarget("SigmaKnob", "σ 把手 → 阻尼", "σ knob → damping") { sigma < -1e-4f }
            }
            1 -> {
                t += TapTarget("ZKnob", "z → 换一个", "z → next")
                t += TapTarget("WKnob", "w → 换一个", "w → next")
                t += TapTarget("TimesI", "×i 牌 → 转 90°", "×i plaque → turn 90°")
            }
            else -> {
                t += TapTarget("Walker", "金点 → 下一站", "Gold point → next stop")
                t += TapTarget("SeriesTag", "级数牌 → 重放", "Series plaque → replay")
            }
        }
        t += TapTarget("Tok0", "① 螺旋", "① Helix", enabled = { station != 0 }, why = Bi("已经在这一站：① 螺旋", "Already on this station: ① Helix")) { station == 0 }
        t += TapTarget("Tok1", "② 乘法", "② Multiply", enabled = { station != 1 }, why = Bi("已经在这一站：② 乘法", "Already on this station: ② Multiply")) { station == 1 }
        t += TapTarget("Tok2", "③ e^iπ", "③ e^iπ", enabled = { station != 2 }, why = Bi("已经在这一站：③ e^iπ", "Already on this station: ③ e^iπ")) { station == 2 }
        return t
    }

    override fun isTarget(name: String) = name in stationTargets(station) || name in TOK_NAMES

    private val Z_PRESETS = listOf(Complex.polar(1.2, PI / 6), Complex.I, Complex.polar(1.5, 0.0), Complex.polar(0.8, 5 * PI / 6))
    private val W_PRESETS = listOf(Complex.polar(0.8, PI / 4), Complex.I, Complex.polar(1.25, -PI / 6), Complex(-1.0, 0.0))

    override fun onTap(name: String) {
        when {
            name.startsWith("Tok") -> name.removePrefix("Tok").toIntOrNull()?.let { selectStation(it) }
            name == "Point1" -> { play1 = !play1; bump() }
            name == "TurnKnob" -> viewTo(when (view) { View.QUARTER -> View.FRONT; View.FRONT -> View.SIDE; View.SIDE -> View.TOP; else -> View.QUARTER })
            name == "OmegaKnob" -> omegaTo(if (omega == 0f) 1f else -omega)
            name == "SigmaKnob" -> sigmaTo(if (sigma < -1e-4f) 0f else -0.15f)
            name == "ZKnob" -> { zPreset = (zPreset + 1) % Z_PRESETS.size; setZ(Z_PRESETS[zPreset]) }
            name == "WKnob" -> { wPreset = (wPreset + 1) % W_PRESETS.size; setW(W_PRESETS[wPreset]) }
            name == "TimesI" -> timesI()
            name == "Walker" -> walkTo(LANDMARKS.firstOrNull { it > theta + 0.01 } ?: 0.0)
            name == "SeriesTag" -> playSeries()
        }
    }

    override fun tapMessage(name: String): String? = when {
        name.startsWith("Tok") -> when (station) {
            0 -> tr("① 螺旋：e^(iωt) 随时间画出的形状", "① The helix: e^(iωt) drawn through time")
            1 -> tr("② 乘法：长度相乘，角度相加", "② Multiplication: lengths multiply, angles add")
            else -> tr("③ 沿单位圆走 θ：e^(iθ) 与级数", "③ Walk θ round the circle: e^(iθ) and its series")
        }
        name == "Point1" -> if (play1) tr("时间继续走", "Time runs again") else tr("时间暂停：看三个影子", "Time paused: compare the three shadows")
        name == "TurnKnob" -> when (view) {
            View.FRONT -> tr("正面：只看到一个圆", "Front: just a circle")
            View.SIDE -> tr("侧面：影子是 sin ωt", "Side: the shadow is sin ωt")
            View.TOP -> tr("俯视：地面的影子是 cos ωt", "Top: the floor shadow is cos ωt")
            else -> tr("3/4 视角：三张图一起看", "3/4 view: all three graphs at once")
        }
        name == "OmegaKnob" -> if (omega < 0) tr("ω = ${num(omega.toDouble(), 2)}：顺时针旋转", "ω = ${num(omega.toDouble(), 2)}: turning clockwise")
            else tr("ω = ${num(omega.toDouble(), 2)}：逆时针旋转", "ω = ${num(omega.toDouble(), 2)}: turning counter-clockwise")
        name == "SigmaKnob" -> if (sigma < -1e-4f) tr("σ = ${num(sigma.toDouble(), 2)}：螺旋收缩成阻尼振动", "σ = ${num(sigma.toDouble(), 2)}: the helix shrinks, a damped oscillation") else tr("σ = 0：不衰减", "σ = 0: no decay")
        name == "ZKnob" -> tr("z = ${pol(z)}", "z = ${pol(z)}")
        name == "WKnob" -> tr("w = ${pol(w)}", "w = ${pol(w)}")
        name == "TimesI" -> tr("z 再乘一次 i：逆时针转 90°", "z times i once more: a 90° counter-clockwise turn")
        name == "Walker" -> tr("走到 θ = ${thetaStr(thetaT)}", "Walking to θ = ${thetaStr(thetaT)}")
        name == "SeriesTag" -> tr("级数一项项加上：每项转 90°", "Adding the series term by term: 90° per term")
        else -> null
    }

    // ---- drag
    override fun isDragTarget(name: String) = when (station) {
        0 -> name == "TurnKnob" || name == "OmegaKnob" || name == "SigmaKnob"
        1 -> name == "ZKnob" || name == "WKnob"
        else -> name == "Walker"
    }

    override fun onDragStart(name: String) {
        dragging = name; accX = 0f; accY = 0f
        when (name) {
            "TurnKnob" -> { drag0 = yaw; drag1 = pitch }
            "OmegaKnob" -> drag0 = omega
            "SigmaKnob" -> drag0 = sigma
            "ZKnob", "WKnob" -> { finishTurns(); dragZ0 = if (name == "ZKnob") z else w }
            "Walker" -> { walking = false; chainPlay = false }
        }
    }

    override fun onDrag(name: String, dx: Float, dy: Float, dz: Float) {
        accX += dx; accY += dy
        when (name) {
            "TurnKnob" -> { view = View.CUSTOM; yawT = (drag0 - accX / 0.25f * 90f).coerceIn(-120f, 60f); pitchT = (drag1 + accY / 0.25f * 90f).coerceIn(-15f, 90f); yaw = yawT; pitch = pitchT }
            "OmegaKnob" -> omegaTo(((drag0 + accX / OM_K) * 4f).roundToInt() / 4f)
            "SigmaKnob" -> sigmaTo(((drag0 + accX / SG_K) * 50f).roundToInt() / 50f)
            "ZKnob" -> setZ(dragZ0 + Complex((accX / U2).toDouble(), (accY / U2).toDouble()))
            "WKnob" -> setW(dragZ0 + Complex((accX / U2).toDouble(), (accY / U2).toDouble()))
            "Walker" -> {
                // the tangential part of the hand motion, over the drawn radius
                val e = Euler.cis(theta)
                val d = (-e.im * dx + e.re * dy) / s3
                var t = (theta + d).coerceIn(0.0, 2 * PI)
                for (l in LANDMARKS) if (abs(t - l) < 0.04) t = l
                theta = t; thetaT = t
            }
        }
    }

    override fun onDragEnd(name: String) { dragging = null; if (name == "Walker") playSeries(); bump() }

    // ---- actions (also what the panel, the lesson autoplay and the tests call)
    fun selectStation(s: Int) { station = s.coerceIn(0, 2); bump() }
    fun omegaTo(v: Float) { omega = v.coerceIn(-3f, 3f); bump() }
    fun sigmaTo(v: Float) { sigma = v.coerceIn(-0.4f, 0f); bump() }
    fun viewTo(v: View) { view = v; if (v != View.CUSTOM) { yawT = v.yaw; pitchT = v.pitch }; bump() }
    private fun clampLen(c: Complex, other: Complex): Complex {
        val l = c.abs
        val hi = min(1.6, 1.95 / max(other.abs, 1e-6))
        return when { l < 1e-9 -> Complex(0.25, 0.0); l < 0.25 -> c * (0.25 / l); l > hi -> c * (hi / l); else -> c }
    }
    fun setZ(v: Complex) { finishTurns(); z = clampLen(v, w); zFrom = z; quarterTurns = 0; ghostT = 0f; bump() }
    fun setW(v: Complex) { w = clampLen(v, z); bump() }
    fun timesI() { if (animS >= 0f) pendingTurns++ else startTurn(); bump() }
    private fun startTurn() { zFrom = z; animS = 0f }
    private fun finishTurns() {
        if (animS >= 0f) { z = Euler.quarterTurns(zFrom, 1); animS = -1f; quarterTurns++ }
        while (pendingTurns > 0) { pendingTurns--; zFrom = z; z = Euler.quarterTurns(z, 1); quarterTurns++ }
    }
    fun setTheta(v: Double) { theta = v.coerceIn(0.0, 2 * PI); thetaT = theta; walking = false; bump() }
    fun walkTo(v: Double) { thetaT = v.coerceIn(0.0, 2 * PI); walking = true; chainPlay = false; bump() }
    fun playSeries() { chainN = 0f; chainPlay = true; bump() }
    /** |z·w − i|: the challenge's distance. */
    fun challengeError() = (z * w - Complex.I).abs
    /** Turns of the ×i plaque still animating or queued. */
    val turning get() = animS >= 0f || pendingTurns > 0

    /** Finish every animation now (review renders, previews). */
    fun settle() {
        finishTurns(); ghostT = 0f
        if (walking) { theta = thetaT; walking = false }
        chainPlay = false; chainN = chainFull().toFloat()
        yaw = yawT; pitch = pitchT; cooldown = 0f
    }

    // ================================================================ guided track (导学)

    private var turnsBase = 0

    // ---- background card (📖): context for a viewer who has never met this topic
    override val about = About(listOf(
        About.Page(
            Bi("这个实验在做什么？", "What is this experiment?"),
            Bi("三个站（右侧牌切换）。① 螺旋：一个点沿发光螺旋往纵深跑，地面和侧墙上是它的影子；拖 ω、σ 把手改转速和衰减，捏时间轴末端换视角。② 乘法：拖蓝色 z 和红色 w，看黄色 z·w 落在哪；捏绿色「×i」牌把 z 转 90°。③ e^iπ：拖单位圆上的金点走半圈，看一串箭头盘旋着落到 −1。", "Three stations (plaques on the right). 1) Helix: a point runs along a glowing helix into depth, casting shadows on floor and side wall; the ω and σ knobs set spin and decay; pinch the time-axis end to change view. 2) Multiplication: drag blue z and red w, see where yellow z·w lands; the green 'x i' plaque turns z by 90°. 3) e^iπ: walk the gold point half way round the unit circle; a chain of arrows spirals onto −1."),
        ),
        About.Page(
            Bi("先认识复数", "First: complex numbers"),
            Bi("没有哪个普通的数平方后等于 −1。16 世纪，意大利的卡尔达诺、邦贝利解三次方程时，中途不得不用 √−1，最后却得到正确的实数答案。笛卡儿嫌弃地叫它“虚数”。约 1800 年，韦塞尔、阿尔冈等人看出：把 a + bi 画成平面上的一点（向右 a、向上 b），一切就清楚了。i 就是“向上一格”。", "No ordinary number squares to −1. In the 1500s Cardano and Bombelli in Italy, solving cubic equations, had to pass through √−1 midway, yet still reached correct real answers. Descartes dismissively called such numbers 'imaginary'. Around 1800 Wessel, Argand and others saw the fix: draw a + bi as a point in a plane, a to the right and b up. Then i is simply 'one step up'."),
        ),
        About.Page(
            Bi("核心：乘法就是旋转", "Key idea: multiplying is turning"),
            Bi("复数相乘：长度相乘，角度相加。乘 i 就是逆时针转直角；乘两次转 180°，所以 i² = −1。1748 年欧拉写出 e^(iθ) = cos θ + i sin θ。想象用绳子甩小球：速度总垂直于绳子，球只转圈不飞远。e^(iθ) 也是“侧着长”，于是沿单位圆走了 θ 那么远。", "Multiply two complex numbers: lengths multiply, angles add. Times i is a quarter turn counter-clockwise; twice is 180°, so i² = −1. In 1748 Euler wrote e^(iθ) = cos θ + i sin θ. Think of whirling a ball on a string: its velocity is always at right angles to the string, so it circles instead of flying off. e^(iθ) grows 'sideways' the same way, walking a distance θ round the unit circle."),
        ),
        About.Page(
            Bi("怎么看这个 3D 场景", "How to read the 3-D scene"),
            Bi("① 正对你的平面 = 复平面（右实部、上虚部），纵深 = 时间。黄螺旋 = e^(iωt)，蓝色地面影子 = cos ωt，红色墙上影子 = sin ωt。② 蓝箭头 = z，红 = w，黄 = z·w；彩色圆弧 = 角度相加；金色网格 = 整个平面乘以 w。③ 白圈 = 单位圆，金点 = e^(iθ)；彩色箭头链 = 级数各项，每支比上一支转 90°。", "1) The plane facing you = the complex plane (right = real, up = imaginary); depth = time. Yellow helix = e^(iωt); the blue floor shadow = cos ωt, the red wall shadow = sin ωt. 2) Blue arrow = z, red = w, yellow = z·w; coloured arcs = angles adding; the gold grid = the whole plane multiplied by w. 3) White ring = unit circle, gold point = e^(iθ); the blue, green, red, purple arrow chain = the series' terms, each turned 90° from the last."),
        ),
        About.Page(
            Bi("一个关键等式", "One key equation"),
            Bi("θ = π 时，沿单位圆走 π 的弧长正好半圈，落在 −1：e^(iπ) + 1 = 0。它把 0、1、π、e、i 这五个最重要的数连在一起。常见误解：“虚数是假的、没用的”。“虚”只是历史留下的名字。复数和负数一样真实：负数表示“反方向”，复数表示“转一个角度”。", "At θ = π, an arc of length π is exactly half a turn and lands on −1: e^(iπ) + 1 = 0. It ties together five of maths' most important numbers: 0, 1, π, e and i. A common misconception: 'imaginary numbers are fake and useless'. The name is just history. Complex numbers are as real as negatives: a negative means 'the opposite way', a complex number means 'turned by an angle'."),
        ),
        About.Page(
            Bi("今天在哪里？", "Where is it today?"),
            Bi("我国的交流电每秒转 50 圈，电气工程师就用旋转的复数算电压和电流。手机通话、Wi-Fi、MP3、JPEG 都靠傅里叶变换：把信号拆成许多以不同速度转圈的 e^(iωt)。量子力学的波函数本身就是复数。一个旋转的圆从侧面看就是波 —— 世上的振动和波，几乎都能这样描述。", "Mains power in China cycles 50 times a second, and engineers compute voltages and currents with rotating complex numbers. Phone calls, Wi-Fi, MP3 and JPEG rely on the Fourier transform, which splits a signal into many e^(iωt) spinning at different speeds. Quantum wavefunctions are complex numbers. A turning circle seen side-on is a wave, and almost every vibration and wave can be described this way."),
        ),
    ))

    override val lesson: Lesson by lazy {
        Lesson(
            listOf(
                LessonStep(
                    say = Bi("③ 指数里多了一个 i：e^(iθ) 是复平面上的一个点。先猜：e^(iπ) 落在哪里？", "③ An i in the exponent: e^(iθ) is a point of the complex plane. Guess first: where does e^(iπ) land?"),
                    options = listOf(Bi("1", "1"), Bi("−1", "−1"), Bi("i", "i"), Bi("π（实轴上 3.14 处）", "π (at 3.14 on the real axis)")),
                    answer = 1,
                    onEnter = { selectStation(2); setTheta(0.0) },
                ),
                LessonStep(
                    say = Bi("拖动单位圆上的金色点（或面板的 θ 滑块），从 1 出发逆时针走到 θ = π，看级数的箭头链落在哪里。", "Drag the gold point on the unit circle (or the θ slider) from 1 counter-clockwise to θ = π, and watch where the arrow chain of the series lands."),
                    target = "Walker",
                    onEnter = { selectStation(2) },
                    done = { station == 2 && abs(theta - PI) < 0.02 && !walking && !chainPlay },
                    autoplay = { selectStation(2); walkTo(PI) },
                    reveal = true,
                    insight = Bi("e^(iπ) = −1。走 π 的弧长 = 半圈。级数 1 + iπ + (iπ)²/2! + … 的每支箭头都比上一支转 90°、缩短 π/k，链条盘旋着收敛到 −1（12 项后误差 < 0.002）。所以 e^(iπ) + 1 = 0。", "e^(iπ) = −1. An arc of length π is half a turn. Each arrow of 1 + iπ + (iπ)²/2! + … is the previous one turned 90° and scaled by π/k, and the chain spirals onto −1 (error < 0.002 after 12 terms). So e^(iπ) + 1 = 0."),
                ),
                LessonStep(
                    say = Bi("② 乘法台：蓝色箭头是 z。把 z 乘以 i，它会怎样？", "② Multiplication: the blue arrow is z. What happens when you multiply z by i?"),
                    options = listOf(Bi("长度变成 i 倍", "Its length becomes i times as long"), Bi("逆时针转 90°，长度不变", "It turns 90° counter-clockwise, same length"), Bi("直接变成 −z", "It becomes −z"), Bi("不变", "Nothing changes")),
                    answer = 1,
                    onEnter = { selectStation(1) },
                ),
                LessonStep(
                    say = Bi("捏绿色的「×i」牌（或面板 ×i）两次，看 z 转到哪里。", "Pinch the green '×i' plaque (or ×i on the panel) twice and see where z ends up."),
                    target = "TimesI",
                    onEnter = { selectStation(1); turnsBase = quarterTurns },
                    done = { station == 1 && quarterTurns - turnsBase >= 2 && !turning },
                    autoplay = { selectStation(1); timesI(); timesI() },
                    reveal = true,
                    insight = Bi("×i 就是逆时针转 90°：|i| = 1 不改长度，arg i = 90° 加到角度上。两次 = 180°，z 变成 −z —— i² = −1 不神秘，只是两个直角转弯。", "×i is a 90° counter-clockwise turn: |i| = 1 keeps the length, arg i = 90° adds to the angle. Twice = 180°, z becomes −z — i² = −1 is no mystery, just two quarter turns."),
                ),
                LessonStep(
                    say = Bi("① 螺旋台：时间朝纵深走，e^(iωt) 画出一条螺旋。捏时间轴末端的蓝色把手（或面板「侧面」）从侧面看它。", "① The helix: time runs into depth and e^(iωt) draws a helix. Pinch the blue knob at the end of the time axis (or 'Side' on the panel) to look at it from the side."),
                    target = "TurnKnob",
                    onEnter = { selectStation(0) },
                    done = { station == 0 && view == View.SIDE && abs(yaw - View.SIDE.yaw) < 1f && abs(pitch) < 1f },
                    autoplay = { selectStation(0); viewTo(View.SIDE) },
                    insight = Bi("侧面看，螺旋的影子是正弦波 sin ωt；从上往下看地面是 cos ωt；正对着看是一个圆。一个三维物体，三张图 —— 这就是 e^(iωt) = cos ωt + i sin ωt。", "From the side the helix's shadow is the sine wave sin ωt; from above, the floor shows cos ωt; face on, a circle. One 3-D object, three graphs — that is e^(iωt) = cos ωt + i sin ωt."),
                ),
            ),
            challenge = Challenge(
                say = Bi("② 乘法台挑战：拖动红色的 w（或面板 |w|、arg w 滑块），让 z·w 正好落在 i 上（误差 < 0.05）。", "② Challenge: drag the red w (or the |w| and arg w sliders) so that z·w lands on i (error < 0.05)."),
                progress = {
                    val zw = z * w
                    if (station != 1) tr("先捏右边的 ② 牌回到乘法台", "First pinch the ② plaque on the right")
                    else tr("|z·w − i| = ${num(challengeError(), 3)}（目标 < 0.05）· z·w = ${pol(zw)}，i = 1.00∠90°",
                        "|z·w − i| = ${num(challengeError(), 3)} (goal < 0.05) · z·w = ${pol(zw)}, i = 1.00∠90°")
                },
                done = { station == 1 && challengeError() < 0.05 && dragging == null && !turning },
                autoplay = { selectStation(1); setW(Complex.I / z) },
            ),
        )
    }

    // ================================================================ review states

    override fun reviewVariants(): Map<String, () -> Unit> = mapOf(
        "front" to { selectStation(0); sigmaTo(0f); omegaTo(1f); viewTo(View.FRONT); settle() },
        "side" to { selectStation(0); viewTo(View.SIDE); settle() },
        "top" to { selectStation(0); viewTo(View.TOP); settle() },
        "damped" to { selectStation(0); omegaTo(2f); sigmaTo(-0.2f); viewTo(View.QUARTER); settle() },
        "multiply" to { selectStation(1); showWGrid = false; setZ(Z_PRESETS[0]); setW(W_PRESETS[0]); settle() },
        "wplane" to { selectStation(1); showWGrid = true; lastZW = null; setZ(Z_PRESETS[0]); setW(Complex.polar(1.2, PI / 3)); settle() },
        "times_i" to { selectStation(1); showWGrid = false; setZ(Z_PRESETS[0]); timesI(); timesI(); settle() },
        "theta_1" to { selectStation(2); setTheta(1.0); settle() },
        "euler_pi" to { selectStation(2); setTheta(PI); settle() },
        "two_pi" to { selectStation(2); setTheta(2 * PI); settle() },
    )

    // ================================================================ panel

    @Composable
    override fun Panel() {
        @Suppress("UNUSED_VARIABLE") val epoch = panelEpoch
        LabPanel(readout) {
            SegmentControl {
                SegmentItem(selected = speed == 0, onClick = { speed = 0; bump() }, title = { Text(tr("慢", "Slow")) })
                SegmentItem(selected = speed == 1, onClick = { speed = 1; bump() }, title = { Text(tr("中", "Medium")) })
                SegmentItem(selected = speed == 2, onClick = { speed = 2; bump() }, title = { Text(tr("快", "Fast")) })
            }
            when (station) {
                0 -> {
                    Button(onClick = { play1 = !play1; bump() }) { Text(if (play1) tr("⏸ 暂停", "⏸ Pause") else tr("▶ 播放", "▶ Play")) }
                    SegmentControl {
                        SegmentItem(selected = view == View.QUARTER, onClick = { viewTo(View.QUARTER) }, title = { Text("3/4") })
                        SegmentItem(selected = view == View.FRONT, onClick = { viewTo(View.FRONT) }, title = { Text(tr("正面", "Front")) })
                        SegmentItem(selected = view == View.SIDE, onClick = { viewTo(View.SIDE) }, title = { Text(tr("侧面", "Side")) })
                        SegmentItem(selected = view == View.TOP, onClick = { viewTo(View.TOP) }, title = { Text(tr("俯视", "Top")) })
                    }
                    LabSlider("ω", omega, -3f..3f, num(omega.toDouble(), 2), width = 380) { omegaTo((it * 4f).roundToInt() / 4f) }
                    LabSlider("σ", sigma, -0.4f..0f, num(sigma.toDouble(), 2), width = 380) { sigmaTo((it * 50f).roundToInt() / 50f) }
                }
                1 -> {
                    Button(onClick = { timesI() }) { Text("×i") }
                    LabSwitch(tr("w × 平面", "w × plane"), showWGrid) { showWGrid = it; lastZW = null; bump() }
                    Button(onClick = { zPreset = 0; wPreset = 0; setZ(Z_PRESETS[0]); setW(W_PRESETS[0]) }, enabled = z != Z_PRESETS[0] || w != W_PRESETS[0]) { Text(tr("重置", "Reset")) }
                    LabSlider("|w|", w.abs.toFloat(), 0.25f..1.6f, num(w.abs, 2), width = 380) { setW(Complex.polar(it.toDouble(), w.arg)) }
                    LabSlider("arg w", Math.toDegrees(w.arg).toFloat(), -180f..180f, deg(w.arg), width = 380) { setW(Complex.polar(w.abs, Math.toRadians(it.roundToInt().toDouble()))) }
                }
                else -> {
                    Button(onClick = { walkTo(PI) }, enabled = abs(theta - PI) > 1e-6 || chainPlay) { Text(tr("▶ 走到 π", "▶ Walk to π")) }
                    Button(onClick = { walkTo(2 * PI) }, enabled = abs(theta - 2 * PI) > 1e-6 || chainPlay) { Text(tr("▶ 走到 2π", "▶ Walk to 2π")) }
                    Button(onClick = { playSeries() }) { Text(tr("▶ 级数", "▶ Series")) }
                    LabSlider("θ", theta.toFloat(), 0f..(2 * PI).toFloat(), thetaStr(theta), width = 420) {
                        var t = it.toDouble(); for (l in LANDMARKS) if (abs(t - l) < 0.05) t = l
                        setTheta(t)
                    }
                }
            }
        }
    }
}
