// 圆锥曲线 · Dandelin 球 (Apollonius ~200 BC, Dandelin 1822) — "circle, ellipse, parabola and hyperbola are ONE
// family: slices of one double cone at different tilts".
//
// LESSON (3Blue1Brown spirit):
//   * A glass double cone stands on the bench; a glowing cutting plane crosses it. Pinch-drag the gold knob
//     (right end of the plane) to TILT it, the teal knob (left end) to SHIFT it along its normal. The slice
//     glows and is named live: 圆 / 椭圆 / 抛物线 / 双曲线, e = cos β / cos α (β = plane <-> axis, α = the cone's
//     half-angle, 30°). When the plane becomes parallel to a generator (β = α) it SNAPS there with a flash:
//     the parabola is the knife-edge between the closed ellipses and the two-branch hyperbolas.
//   * The Dandelin spheres (red: between apex and plane, green: the other side / other nappe) each touch the
//     cone along a circle and the plane at one point — exactly the FOCI. A point P runs round the curve with
//     taut strings to F1, F2 and the two segments down its generator to the contact circles. Tangent segments
//     from one point to one sphere are equal (PF1 = PQ1, PF2 = PQ2), so PF1 + PF2 = Q1Q2, the generator piece
//     between the two circles — the same for every P. That is the whole proof, in one glance.
//   * The board on the right shows the slice in the plane's own 2-D frame, with foci and directrices.
// Geometry is exact (core/conics, ConicsTest). Per frame only transforms change: the curve, the contact
// circles and the board curve are pools of unit-cylinder segments moved by their transforms.

package com.bytedance.mathlab.demos.conics

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bytedance.mathlab.core.P3
import com.bytedance.mathlab.core.conics.ConicType
import com.bytedance.mathlab.core.conics.Conics
import com.bytedance.mathlab.core.conics.Section
import com.bytedance.mathlab.core.conics.V3d
import com.bytedance.mathlab.lab.BakeCache
import com.bytedance.mathlab.lab.About
import com.bytedance.mathlab.lab.Bi
import com.bytedance.mathlab.lab.Challenge
import com.bytedance.mathlab.lab.Lesson
import com.bytedance.mathlab.lab.LessonStep
import com.bytedance.mathlab.lab.Q
import com.bytedance.mathlab.lab.SpecDemo
import com.bytedance.mathlab.lab.TapTarget
import com.bytedance.mathlab.lab.tr
import com.bytedance.mathlab.lab.spec.Blend
import com.bytedance.mathlab.lab.spec.LabelSpec
import com.bytedance.mathlab.lab.spec.Mat
import com.bytedance.mathlab.lab.spec.Node
import com.bytedance.mathlab.lab.spec.SoftRender
import com.bytedance.mathlab.lab.ui.Button
import com.bytedance.mathlab.lab.ui.FieldRef
import com.bytedance.mathlab.lab.ui.LabPanel
import com.bytedance.mathlab.lab.ui.LabRow
import com.bytedance.mathlab.lab.ui.LabSlider
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object ConicsDemo : SpecDemo() {
    override val id = "conics"
    override val title = "圆锥曲线 · Dandelin 球"  // i18n-ok (zh half of DemoRegistry.titleOf; English in TITLE_EN)
    override val credit = "Apollonius · 200 BC · Dandelin 1822"

    // ---------------------------------------------------------------- geometry (volume metres)
    private const val ALPHA_DEG = 30.0
    private val ALPHA = ALPHA_DEG * PI / 180
    private const val H_UP = 0.50f            // upper nappe drawn from the apex up to here (cone frame)
    private const val H_DN = 0.11f            // lower nappe drawn down to -H_DN
    private val CONE = P3(-0.25f, -0.10f, -0.04f)   // apex in the volume
    /** The plane's uphill direction points right and AWAY from the viewer, so tilted slices face the viewer. */
    private val YAW = (55.0 * PI / 180).toFloat()
    private val TAN_A = kotlin.math.tan(ALPHA).toFloat()
    private const val XM = 0.38f              // sheet half-extent across (cone frame x)
    private const val WM = 0.25f              // sheet half-extent along canonical z
    private const val Y_TOP = 0.53f           // sheet clipped to these cone-frame heights
    private const val Y_BOT = -0.14f
    private const val D_MIN = 0.03f
    private const val D_MAX = 0.20f
    private const val PLINTH_Y = -0.262f
    // board (2-D view of the slice)
    private val BOARD = P3(0.56f, 0.10f, -0.08f)
    private const val BW = 0.64f
    private const val BH = 0.42f
    private val PLAQUE = P3(0.56f, 0.375f, -0.06f)

    private const val N_CURVE = 132
    private const val N_BOARD = 96
    private const val N_RING = 32
    private val SPEEDS = floatArrayOf(0.035f, 0.07f, 0.14f)

    // ---------------------------------------------------------------- colours (3Blue1Brown-like, sRGB)
    private val YELLOW = Rgb(1.00f, 0.86f, 0.30f)
    private val RED = Rgb(0.99f, 0.38f, 0.33f)
    private val GREEN = Rgb(0.51f, 0.76f, 0.40f)
    private val BLUE = Rgb(0.35f, 0.77f, 0.87f)
    private val TEAL = Rgb(0.36f, 0.82f, 0.70f)
    private val GOLD = Rgb(0.94f, 0.67f, 0.37f)
    private val WHITE = Rgb(0.97f, 0.98f, 1.00f)
    private val GREY = Rgb(0.62f, 0.66f, 0.74f)

    // ---------------------------------------------------------------- texts (baked once)
    private const val TX_F1 = 0; private const val TX_F2 = 1; private const val TX_F = 2; private const val TX_P = 3
    private const val TX_Q1 = 4; private const val TX_Q2 = 5
    private const val TX_PLAQUE = 6           // + ConicType.ordinal
    private const val TX_LAW = 10             // + ConicType.ordinal
    private const val TEX_VERSION = 2
    private fun sp(t: String, c: Rgb = WHITE) = ConicText.Span(t, c)
    private val TEXTS: List<Pair<List<ConicText.Span>, Int>> by lazy {
        val l = ArrayList<Pair<List<ConicText.Span>, Int>>()
        l += listOf(sp("F_1", lighten(RED))) to 72; l += listOf(sp("F_2", lighten(GREEN))) to 72
        l += listOf(sp("F", lighten(RED))) to 72; l += listOf(sp("P", WHITE)) to 72
        l += listOf(sp("Q_1", lighten(RED))) to 64; l += listOf(sp("Q_2", lighten(GREEN))) to 64
        val ic = listOf(ConicText.CIRCLE, ConicText.ELLIPSE, ConicText.PARABOLA, ConicText.HYPERBOLA)
        val ee = listOf("e = 0", "e < 1", "e = 1", "e > 1")
        for (k in 0..3) l += listOf(sp("${ic[k]}", YELLOW), sp("   " + ee[k], WHITE)) to 96
        val g = GREY
        l += listOf(sp("PF", WHITE), sp(" = ", g), sp("r", YELLOW)) to 80
        l += listOf(sp("PF_1", lighten(RED)), sp(" + ", g), sp("PF_2", lighten(GREEN)), sp(" = ", g), sp("2a", YELLOW)) to 80
        l += listOf(sp("PF", lighten(RED)), sp(" = ", g), sp("Pd", GREY)) to 80
        l += listOf(sp("|", g), sp("PF_1", lighten(RED)), sp(" − ", g), sp("PF_2", lighten(GREEN)), sp("|", g), sp(" = ", g), sp("2a", YELLOW)) to 80
        l
    }
    private fun lighten(c: Rgb) = ProcTex.okLerp(c, Rgb(1f, 1f, 1f), 0.35f)

    private class TexInfo(val img: Image, emPx: Int) {
        val aspect = img.w.toFloat() / img.h
        val emFrac = emPx.toFloat() / img.h
        val mat = Mat.Unlit(Rgb(1f, 1f, 1f), tex = img, blend = Blend.TRANSPARENT)
    }
    private lateinit var tex: List<TexInfo>

    // ---------------------------------------------------------------- state
    /** Plane tilt from horizontal, degrees (snapped: 0 = circle, 90 - alpha = parabola). */
    var tiltDeg = 0f; private set
    /** Plane distance from the apex (m). */
    var dist = 0.11f; private set
    var dandelin = true
    var running = true
    var speed = 1
    private var raw = 0f                       // unsnapped tilt while dragging
    private var pathG = 0f                     // P's progress: 0..1 closed, 0..2 ping-pong when open
    /** Laps (curve lengths) P has run since [lapsFrom] was reset. */
    var laps = 0f; private set
    private var flashT = 0f
    private var flashPending = false
    private var dragK0 = V3d(0.0, 0.0, 0.0)
    private var dragN0 = V3d(0.0, 1.0, 0.0)
    private var dragD0 = 0f
    private var dragAcc = FloatArray(3)
    private var dragT0 = 0f
    private var builtKey = ""
    private var boardK = 1f; private var boardCx = 0f; private var boardCy = 0f
    private var boardInit = false
    private var curveMatLevel = -1

    lateinit var sec: Section; private set
    private var branches: List<Section.Branch> = emptyList()
    private var primary: Section.Branch? = null
    private var primPhi = DoubleArray(0)
    private var primCum = DoubleArray(0)
    private var sLo = 0.0; private var sHi = 0.0
    private var pNow = V3d(0.0, 0.1, 0.0)

    val type: ConicType get() = sec.type
    val ecc: Double get() = sec.e
    private val parabolaDeg = (90.0 - ALPHA_DEG).toFloat()

    private fun resetState() {
        tiltDeg = Conics.tiltFor(0.6, ALPHA).let { (it * 180 / PI).toFloat() }; raw = tiltDeg
        dist = 0.125f; dandelin = true; running = true; speed = 1
        pathG = 0.08f; laps = 0f; flashT = 0f; flashPending = false
        builtKey = ""; boardInit = false; curveMatLevel = -1
        dragAcc = FloatArray(3)
        recompute()
    }

    // ---------------------------------------------------------------- nodes
    private lateinit var unitCyl: MeshData
    private lateinit var ball: MeshData
    private lateinit var quad: MeshData
    private lateinit var cone: Node
    private lateinit var sheet: Node
    private lateinit var sheetEdges: Array<Node>
    private lateinit var tiltKnob: Node
    private lateinit var shiftKnob: Node
    private lateinit var parGen: Node
    private lateinit var curve: Array<Node>
    private lateinit var glow: Array<Node>
    private lateinit var spheres: Array<Node>
    private lateinit var rings: Array<Array<Node>>
    private lateinit var foci: Array<Node>
    private lateinit var fociTxt: Array<Node>
    private lateinit var pt: Node
    private lateinit var ptTxt: Node
    private lateinit var strings: Array<Node>        // P-F1, P-F2
    private lateinit var gens: Array<Node>           // P-Q1, P-Q2
    private lateinit var qDots: Array<Node>
    private lateinit var qTxt: Array<Node>
    private lateinit var board: Node
    private lateinit var bCurve: Array<Node>
    private lateinit var bAxis: Node
    private lateinit var bFoci: Array<Node>
    private lateinit var bFociTxt: Array<Node>
    private lateinit var bDir: Array<Node>
    private lateinit var bP: Node
    private lateinit var bPTxt: Node
    private lateinit var bStr: Array<Node>           // P-F1, P-F2, P-directrix
    private lateinit var bLaw: Array<Node>
    private lateinit var plaque: Node
    private lateinit var plaqueTxt: Array<Node>
    private lateinit var plaqueGlow: Node

    private val curveMats by lazy { Array(4) { k -> Mat.Unlit(ProcTex.okLerp(YELLOW, WHITE, k / 3f), blend = Blend.ADD) } }
    private val glowMats by lazy { Array(4) { k -> Mat.Unlit(ProcTex.okLerp(YELLOW, WHITE, k / 3f), alpha = 0.22f + 0.1f * k, blend = Blend.ADD) } }
    private fun sphereOn(c: Rgb) = Mat.Pbr(c, metallic = 0f, roughness = 0.12f, alpha = 0.40f, emissive = Rgb(c.r * 0.38f, c.g * 0.38f, c.b * 0.38f))
    private fun sphereOff(c: Rgb) = Mat.Pbr(c, metallic = 0f, roughness = 0.2f, alpha = 0.10f)
    private val sphMats by lazy { arrayOf(sphereOn(RED), sphereOn(GREEN), sphereOff(RED), sphereOff(GREEN)) }
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.32f)

    override fun buildSpec(cacheDir: File): Node {
        val imgs = BakeCache.load(cacheDir, "conics_text", TEX_VERSION) {
            TEXTS.map { (spans, em) -> ConicText.bake(spans, em) } +
                listOf(ProcTex.bakePlanet(256, 128, Rgb(0.07f, 0.075f, 0.085f), Rgb(0.12f, 0.125f, 0.14f), Rgb(0.20f, 0.205f, 0.22f), 0f, 77))
        }
        tex = TEXTS.mapIndexed { i, t -> TexInfo(imgs[i], t.second) }
        val slabTex = imgs.last()
        unitCyl = MeshGen.cylinder(1f, 1f, 8)
        ball = MeshGen.sphere(1f, 18, 32, MeshGen.SphereUv.EQUIRECT)
        quad = OptGeo.quad(1f, 1f)

        val root = Node("ConicsRoot")
        // bench
        val slab = Mat.Pbr(Rgb(1f, 1f, 1f), metallic = 0f, roughness = 0.55f, tex = slabTex)
        root.add("Plinth", MeshGen.box(1.74f, 0.035f, 0.92f), slab, P3(0f, PLINTH_Y, -0.04f))
        root.add("PlinthBand", MeshGen.box(1.75f, 0.008f, 0.93f), alu, P3(0f, PLINTH_Y, -0.04f))
        val pool = OptGeo.lumTex(64, 64) { u, v -> val dx = (u - 0.5f) * 2f; val dy = (v - 0.5f) * 2f; val r2 = dx * dx + dy * dy; if (r2 >= 1f) 0f else (1f - r2) * (1f - r2) }
        root.add("Pool", quad, Mat.Unlit(Rgb(0.30f, 0.55f, 1.0f), alpha = 0.32f, tex = pool, blend = Blend.ADD)).also {
            it.rot = Q.axisAngle(1f, 0f, 0f, -PI.toFloat() / 2); it.scale = P3(0.95f, 0.80f, 1f); it.pos = P3(CONE.x, PLINTH_Y + 0.019f, CONE.z)
        }

        cone = root.add(Node("Cone")).also { it.pos = CONE; it.rot = Q.axisAngle(0f, 1f, 0f, YAW) }
        buildCone(cone)
        buildPlane(cone)
        buildSection(cone)
        buildBoard(root)
        resetState()
        update(0f)
        return root
    }

    private fun buildCone(g: Node) {
        g.add("ConeGlass", coneSurface(48), Mat.Pbr(Rgb(0.45f, 0.70f, 0.95f), metallic = 0f, roughness = 0.08f, alpha = 0.13f,
            emissive = Rgb(0.03f, 0.07f, 0.11f)))
        g.add("ConeWire", coneWire(), Mat.Unlit(BLUE, alpha = 0.55f, blend = Blend.ADD))
        // stand: a ring at the lower rim and three legs down to the bench
        val rimR = H_DN * TAN_A
        g.add("StandRing", MeshGen.tube(ringPath(rimR, -H_DN, 48), 0.0045f, 6, true), alu)
        val legTop = -H_DN; val legBot = PLINTH_Y + 0.018f - CONE.y
        for (k in 0 until 3) {
            val a = 2 * PI.toFloat() * k / 3 + 0.5f
            val x = rimR * cos(a); val z = rimR * sin(a)
            g.add("Leg$k", MeshGen.tube(floatArrayOf(x, legTop, z, x * 1.6f, legBot, z * 1.6f), 0.004f, 6, false), alu)
        }
        g.add("Foot", MeshGen.cylinder(rimR * 1.9f, 0.006f, 32), alu, P3(0f, legBot, 0f))
        // the generator a parabola's plane runs parallel to (lit near the parabola tilt)
        parGen = g.add("ParGen", unitCyl, Mat.Unlit(GOLD, alpha = 0.0f, blend = Blend.ADD))
        val sa = sin(ALPHA).toFloat(); val ca = cos(ALPHA).toFloat()
        val l1 = H_UP / ca; val l0 = H_DN / ca
        segF(parGen, -sa * l0, -H_DN, 0f, sa * l1, H_UP, 0f, 0.0042f)
    }

    private fun coneSurface(lon: Int): MeshData {
        val rings = floatArrayOf(-H_DN, -H_DN * 0.5f, -0.002f, 0.002f, H_UP * 0.33f, H_UP * 0.66f, H_UP)
        val nv = rings.size * (lon + 1)
        val pos = FloatArray(nv * 3); val nrm = FloatArray(nv * 3); val uv = FloatArray(nv * 2)
        var k = 0
        for ((i, y) in rings.withIndex()) for (j in 0..lon) {
            val p = 2 * PI * j / lon; val c = cos(p).toFloat(); val s = sin(p).toFloat()
            val r = abs(y) * TAN_A
            pos[k * 3] = r * c; pos[k * 3 + 1] = y; pos[k * 3 + 2] = r * s
            val ny = if (y >= 0) -TAN_A else TAN_A; val l = sqrt(1 + ny * ny)
            nrm[k * 3] = c / l; nrm[k * 3 + 1] = ny / l; nrm[k * 3 + 2] = s / l
            uv[k * 2] = j.toFloat() / lon; uv[k * 2 + 1] = i.toFloat() / (rings.size - 1); k++
        }
        val idx = ArrayList<Int>()
        for (i in 0 until rings.size - 1) {
            if (i == 2) continue                                  // no quad across the apex
            for (j in 0 until lon) {
                val a = i * (lon + 1) + j; val b = a + lon + 1
                idx += a; idx += b; idx += a + 1; idx += a + 1; idx += b; idx += b + 1
            }
        }
        return MeshData(pos, nrm, uv, idx.toIntArray())
    }

    private fun ringPath(r: Float, y: Float, n: Int) = FloatArray(n * 3) { i -> val k = i / 3; val a = 2 * PI.toFloat() * k / n
        when (i % 3) { 0 -> r * cos(a); 1 -> y; else -> r * sin(a) } }

    private fun coneWire(): MeshData {
        val parts = ArrayList<MeshData>()
        val sa = sin(ALPHA).toFloat(); val ca = cos(ALPHA).toFloat()
        for (k in 0 until 24) {
            val p = 2 * PI.toFloat() * k / 24; val gx = sa * cos(p); val gz = sa * sin(p)
            val l1 = H_UP / ca; val l0 = H_DN / ca
            parts += MeshGen.tube(floatArrayOf(-gx * l0, -H_DN, -gz * l0, gx * l1, H_UP, gz * l1), 0.0016f, 4, false)
        }
        for (y in floatArrayOf(H_UP, H_UP * 0.75f, H_UP * 0.5f, H_UP * 0.25f, -H_DN * 0.5f, -H_DN))
            parts += MeshGen.tube(ringPath(abs(y) * TAN_A, y, 64), if (y == H_UP || y == -H_DN) 0.0030f else 0.0016f, 4, true)
        return merge(parts)
    }

    private fun merge(parts: List<MeshData>): MeshData {
        val nv = parts.sumOf { it.vertexCount }; val ni = parts.sumOf { it.idx.size }
        val pos = FloatArray(nv * 3); val nrm = FloatArray(nv * 3); val uv = FloatArray(nv * 2); val idx = IntArray(ni)
        var v = 0; var t = 0
        for (m in parts) {
            System.arraycopy(m.pos, 0, pos, v * 3, m.pos.size); System.arraycopy(m.nrm, 0, nrm, v * 3, m.nrm.size)
            System.arraycopy(m.uv, 0, uv, v * 2, m.uv.size)
            for (i in m.idx) idx[t++] = i + v
            v += m.vertexCount
        }
        return MeshData(pos, nrm, uv, idx)
    }

    private fun buildPlane(g: Node) {
        val grid = OptGeo.lumTex(128, 96) { u, v ->
            val gx = abs(((u * 12f) % 1f) - 0.5f); val gy = abs(((v * 9f) % 1f) - 0.5f)
            val line = if (gx > 0.46f || gy > 0.46f) 0.30f else 0f
            val edge = min(min(u, 1 - u), min(v, 1 - v))
            val rim = if (edge < 0.02f) 0.7f else 0f
            0.07f + line + rim
        }
        sheet = g.add("Sheet", quad, Mat.Unlit(Rgb(0.55f, 0.70f, 1.0f), alpha = 0.55f, tex = grid, blend = Blend.ADD))
        sheetEdges = Array(4) { g.add("SheetEdge$it", unitCyl, Mat.Unlit(Rgb(0.62f, 0.76f, 1.0f), alpha = 0.8f, blend = Blend.ADD)) }
        fun knob(name: String, c: Rgb) = g.add(name, ball, Mat.Pbr(c, metallic = 0.1f, roughness = 0.22f, emissive = Rgb(c.r * 0.45f, c.g * 0.45f, c.b * 0.45f))).also {
            it.uniformScale(0.022f); it.tapRadius = 0.055f / 0.022f
        }
        tiltKnob = knob("TiltKnob", GOLD)
        shiftKnob = knob("ShiftKnob", TEAL)
    }

    private fun buildSection(g: Node) {
        curve = Array(N_CURVE) { g.add("Cv$it", unitCyl, curveMats[0]) }
        glow = Array(N_CURVE) { g.add("Cg$it", unitCyl, glowMats[0]) }
        spheres = arrayOf(RED, GREEN).mapIndexed { i, c -> g.add(if (i == 0) "SphereNear" else "SphereFar", ball, sphereOn(c)) }.toTypedArray()
        rings = Array(2) { i -> Array(N_RING) { k -> g.add("Ring${i}_$k", unitCyl, Mat.Unlit(if (i == 0) RED else GREEN, blend = Blend.ADD)) } }
        foci = arrayOf(RED, GREEN).mapIndexed { i, c -> g.add("Focus$i", ball, Mat.Unlit(lighten(c))).also { it.uniformScale(0.011f) } }.toTypedArray()
        fociTxt = Array(2) { i -> decal(g, "FocusTxt$i", if (i == 0) TX_F1 else TX_F2, 0.034f).also { it.billboard = true } }
        strings = arrayOf(RED, GREEN).mapIndexed { i, c -> g.add("Str$i", unitCyl, Mat.Unlit(lighten(c))) }.toTypedArray()
        gens = arrayOf(RED, GREEN).mapIndexed { i, c -> g.add("Gen$i", unitCyl, Mat.Unlit(c, alpha = 0.9f, blend = Blend.ADD)) }.toTypedArray()
        qDots = arrayOf(RED, GREEN).mapIndexed { i, c -> g.add("Qdot$i", ball, Mat.Unlit(lighten(c))).also { it.uniformScale(0.008f) } }.toTypedArray()
        qTxt = Array(2) { i -> decal(g, "QTxt$i", if (i == 0) TX_Q1 else TX_Q2, 0.026f).also { it.billboard = true } }
        pt = g.add("Pt", ball, Mat.Unlit(WHITE)).also { it.uniformScale(0.014f); it.tapRadius = 0.05f / 0.014f }
        ptTxt = decal(g, "PtTxt", TX_P, 0.036f).also { it.billboard = true }
        spheres[0].tapRadius = 1f      // set per frame (local units of the scaled sphere)
    }

    private fun buildBoard(root: Node) {
        board = root.add(Node("Board")).also { it.pos = BOARD }
        board.add("BoardFrame", MeshGen.box(BW + 0.02f, BH + 0.02f, 0.010f), alu, P3(0f, 0f, -0.012f))
        board.add("BoardFace", MeshGen.box(BW, BH, 0.012f), Mat.Pbr(Rgb(0.04f, 0.045f, 0.058f), metallic = 0.1f, roughness = 0.3f), P3(0f, 0f, -0.004f))
        bAxis = board.add("BAxis", unitCyl, Mat.Unlit(Rgb(0.35f, 0.38f, 0.46f)))
        bDir = Array(2) { i -> board.add("BDir$i", unitCyl, Mat.Unlit(ProcTex.okLerp(if (i == 0) RED else GREEN, GREY, 0.55f), alpha = 0.8f, blend = Blend.ADD)) }
        bCurve = Array(N_BOARD) { board.add("Bc$it", unitCyl, curveMats[0]) }
        bStr = Array(3) { i -> board.add("BStr$i", unitCyl, Mat.Unlit(when (i) { 0 -> lighten(RED); 1 -> lighten(GREEN); else -> GREY })) }
        bFoci = arrayOf(RED, GREEN).mapIndexed { i, c -> board.add("BFocus$i", ball, Mat.Unlit(lighten(c))).also { it.uniformScale(0.009f) } }.toTypedArray()
        bFociTxt = Array(2) { i -> decal(board, "BFocusTxt$i", if (i == 0) TX_F1 else TX_F2, 0.028f) }
        bP = board.add("BP", ball, Mat.Unlit(WHITE)).also { it.uniformScale(0.011f) }
        bPTxt = decal(board, "BPTxt", TX_P, 0.028f)
        bLaw = Array(4) { k -> decal(board, "BLaw$k", TX_LAW + k, 0.036f).also { it.pos = P3(0f, BH / 2 - 0.040f, 0.006f) } }
        // the name plaque: pinch it to cycle circle -> ellipse -> parabola -> hyperbola
        // the pinch hub sits on the plaque's icon (left end), so its halo ring frames the icon, not the text
        plaque = root.add(Node("Plaque")).also { it.pos = P3(PLAQUE.x - 0.105f, PLAQUE.y, PLAQUE.z); it.tapRadius = 0.05f }
        plaque.add("PlaqueSlab", MeshGen.box(0.31f, 0.082f, 0.014f), Mat.Pbr(Rgb(0.10f, 0.11f, 0.14f), metallic = 0.3f, roughness = 0.35f), P3(0.105f, 0f, -0.018f))
        plaqueGlow = plaque.add("PlaqueGlow", MeshGen.box(0.314f, 0.086f, 0.008f), Mat.Unlit(YELLOW, alpha = 0f, blend = Blend.ADD), P3(0.105f, 0f, -0.022f))
        plaqueTxt = Array(4) { k -> decal(plaque, "PlaqueTxt$k", TX_PLAQUE + k, 0.044f).also { it.pos = P3(0.5f * it.scale.x - 0.035f, 0f, -0.009f) } }
    }

    private fun decal(parent: Node, name: String, t: Int, em: Float): Node =
        parent.add(name, quad, tex[t].mat).also { val h = em / tex[t].emFrac; it.scale = P3(h * tex[t].aspect, h, 1f) }

    // ---------------------------------------------------------------- segment helpers
    private fun segF(n: Node, ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, r: Float) {
        val dx = bx - ax; val dy = by - ay; val dz = bz - az
        val l = sqrt(dx * dx + dy * dy + dz * dz)
        n.pos = P3((ax + bx) / 2, (ay + by) / 2, (az + bz) / 2)
        n.rot = if (l < 1e-7f) Q.identity() else Q.fromTo(0f, 1f, 0f, dx, dy, dz)
        n.scale = P3(r, max(l, 1e-4f), r)
    }
    private fun seg(n: Node, a: V3d, b: V3d, r: Float) =
        segF(n, a.x.toFloat(), a.y.toFloat(), a.z.toFloat(), b.x.toFloat(), b.y.toFloat(), b.z.toFloat(), r)
    private fun Node.at(p: V3d, dy: Float = 0f) { pos = P3(p.x.toFloat(), p.y.toFloat() + dy, p.z.toFloat()) }

    // ---------------------------------------------------------------- the slice (recomputed on change only)

    private fun recompute() {
        val th = tiltDeg.toDouble() * PI / 180
        sec = Section(ALPHA, th, dist.toDouble(), eps = 1e-6)
        branches = sec.branches(-H_DN.toDouble(), H_UP.toDouble(), 720)
        primary = branches.filter { b -> b.pts[b.pts.size / 2].y > 0 }.maxByOrNull { it.length }
        primary?.let { b ->
            val m = b.pts.size + if (b.closed) 1 else 0
            primPhi = DoubleArray(m) { k -> if (k < b.pts.size) b.phi[k] else b.phi[0] + 2 * PI }
            primCum = DoubleArray(m)
            for (k in 1 until m) primCum[k] = primCum[k - 1] + b.pts[k % b.pts.size].dist(b.pts[k - 1])
        }
        // the sheet covers the cone's bounding box: s range where |x| <= XM and Y_BOT <= y <= Y_TOP
        var lo = -1e9; var hi = 1e9
        if (sec.ct > 1e-6) { lo = max(lo, (-XM + dist * sec.st) / sec.ct); hi = min(hi, (XM + dist * sec.st) / sec.ct) }
        if (sec.st > 1e-6) { lo = max(lo, (Y_BOT - dist * sec.ct) / sec.st); hi = min(hi, (Y_TOP - dist * sec.ct) / sec.st) }
        sLo = lo; sHi = hi
    }

    /** Points every [n] equal arc-length steps along a branch (n + 1 points; closed: last = first). */
    private fun resample(b: Section.Branch, n: Int): List<V3d> {
        val pts = if (b.closed) b.pts + b.pts[0] else b.pts
        val cum = DoubleArray(pts.size); for (k in 1 until pts.size) cum[k] = cum[k - 1] + pts[k].dist(pts[k - 1])
        val out = ArrayList<V3d>(n + 1); var j = 0
        for (i in 0..n) {
            val s = cum.last() * i / n
            while (j < pts.size - 2 && cum[j + 1] < s) j++
            val f = if (cum[j + 1] > cum[j]) (s - cum[j]) / (cum[j + 1] - cum[j]) else 0.0
            out += pts[j] + (pts[j + 1] - pts[j]) * f.coerceIn(0.0, 1.0)
        }
        return out
    }

    private fun layoutCurve() {
        val total = branches.sumOf { it.length }
        var used = 0
        val flashK = 1f + 1.2f * flashT
        if (total > 1e-9) {
            val alloc = branches.map { max(4, (N_CURVE * it.length / total).toInt()) }.toMutableList()
            while (alloc.sum() > N_CURVE) { val i = alloc.indexOf(alloc.max()); alloc[i]-- }
            for ((bi, b) in branches.withIndex()) {
                val p = resample(b, alloc[bi])
                for (k in 0 until alloc[bi]) {
                    seg(curve[used], p[k], p[k + 1], 0.0052f * flashK); seg(glow[used], p[k], p[k + 1], 0.010f * flashK)
                    curve[used].visible = true; glow[used].visible = true; used++
                }
            }
        }
        for (k in used until N_CURVE) { curve[k].visible = false; glow[k].visible = false }
    }

    private fun layoutSheet() {
        val s = sec
        val mid = (sLo + sHi) / 2; val len = (sHi - sLo).toFloat()
        val c = s.fromPlane(mid, 0.0)
        sheet.at(c)
        sheet.rot = Q.axisAngle(0f, 0f, 1f, s.theta.toFloat()) * Q.axisAngle(1f, 0f, 0f, -PI.toFloat() / 2)
        sheet.scale = P3(len, 2 * WM, 1f)
        val corners = listOf(s.fromPlane(sLo, -WM.toDouble()), s.fromPlane(sHi, -WM.toDouble()), s.fromPlane(sHi, WM.toDouble()), s.fromPlane(sLo, WM.toDouble()))
        for (k in 0 until 4) seg(sheetEdges[k], corners[k], corners[(k + 1) % 4], 0.0028f)
        tiltKnob.at(s.fromPlane(sHi + 0.026, 0.0))
        shiftKnob.at(s.fromPlane(sLo - 0.026, 0.0))
    }

    private fun fits(sp: Section.Sphere) =
        CONE.y + sp.c + sp.r <= 0.445 && CONE.y + sp.c - sp.r >= -0.255 && sp.circleY >= -H_DN && sp.circleY <= H_UP

    private fun focusOnSheet(i: Int): Boolean { val f = sec.fociS.getOrNull(i) ?: return false; return f in sLo..sHi }

    private fun layoutDandelin() {
        val sps = listOf(sec.near, sec.far)
        for (i in 0..1) {
            val sp = sps[i]
            val show = sp != null && fits(sp)
            spheres[i].visible = show
            if (sp != null && show) {
                spheres[i].pos = P3(0f, sp.c.toFloat(), 0f); spheres[i].uniformScale(sp.r.toFloat())
                spheres[i].setMat(sphMats[if (dandelin) i else i + 2])
                if (i == 0) spheres[0].tapRadius = max(0.05f, sp.r.toFloat()) / sp.r.toFloat()
            }
            val ringOn = show && dandelin
            for (k in 0 until N_RING) {
                val n = rings[i][k]; n.visible = ringOn
                if (ringOn && sp != null) {
                    val a0 = 2 * PI * k / N_RING; val a1 = 2 * PI * (k + 1) / N_RING
                    seg(n, V3d(sp.circleR * cos(a0), sp.circleY, sp.circleR * sin(a0)), V3d(sp.circleR * cos(a1), sp.circleY, sp.circleR * sin(a1)), 0.0032f)
                }
            }
            val fOn = sp != null && focusOnSheet(i)
            foci[i].visible = fOn; fociTxt[i].visible = fOn && !(sec.type == ConicType.CIRCLE && i == 1)
            if (sp != null && fOn) { foci[i].at(sp.focus); fociTxt[i].at(sp.focus, 0.034f) }
        }
        fociTxt[0].setMat(tex[if (sec.type == ConicType.PARABOLA) TX_F else TX_F1].mat)
    }

    // ---------------------------------------------------------------- per frame

    override fun update(dt: Float) {
        val key = "%.4f/%.5f".format(tiltDeg, dist)
        val flashing = flashT > 0f
        if (flashT > 0f) flashT = max(0f, flashT - dt)
        if (flashPending) { flashPending = false; flash("Plaque") }
        val changed = key != builtKey
        if (changed) { builtKey = key; recompute(); layoutSheet(); layoutDandelin() }
        if (changed || flashing) layoutCurve()
        val lvl = (flashT / 0.7f * 3f).roundToInt().coerceIn(0, 3)
        if (lvl != curveMatLevel) {
            curveMatLevel = lvl
            curve.forEach { it.setMat(curveMats[lvl]) }; glow.forEach { it.setMat(glowMats[lvl]) }; bCurve.forEach { it.setMat(curveMats[lvl]) }
        }
        plaqueGlow.setMat(Mat.Unlit(YELLOW, alpha = ((flashT / 0.7f) * 10f).roundToInt() / 10f * 0.9f, blend = Blend.ADD))
        // the parallel generator lights up as the tilt approaches the parabola
        val near = (1f - abs(tiltDeg - parabolaDeg) / 10f).coerceIn(0f, 1f)
        parGen.setMat(Mat.Unlit(GOLD, alpha = (near * 10f).roundToInt() / 10f * 0.9f, blend = Blend.ADD))
        parGen.visible = near > 0.05f

        // P runs along the upper-nappe branch (closed: loops; open: there and back)
        val b = primary
        if (b != null && primCum.isNotEmpty()) {
            val L = primCum.last().toFloat().coerceAtLeast(1e-4f)
            if (running && dt > 0f) {
                val df = SPEEDS[speed] * dt / L
                pathG += df; laps += df
                pathG = if (b.closed) pathG % 1f else pathG % 2f
            }
            val f = if (b.closed) pathG % 1f else { val g = pathG % 2f; if (g <= 1f) g else 2f - g }
            pNow = pointAtArc(f.toDouble() * primCum.last())
        }
        layoutP()
        layoutBoard(dt, changed)
        updateReadout()
        updateLabels()
    }

    private fun pointAtArc(s: Double): V3d {
        var lo = 0; var hi = primCum.size - 1
        while (hi - lo > 1) { val m = (lo + hi) / 2; if (primCum[m] <= s) lo = m else hi = m }
        val f = if (primCum[hi] > primCum[lo]) ((s - primCum[lo]) / (primCum[hi] - primCum[lo])).coerceIn(0.0, 1.0) else 0.0
        return sec.pointAt(primPhi[lo] + (primPhi[hi] - primPhi[lo]) * f)        // exactly on the section
    }

    private fun layoutP() {
        val p = pNow
        pt.at(p); ptTxt.at(p, 0.036f)
        val sps = listOf(sec.near, sec.far)
        for (i in 0..1) {
            val sp = sps[i]
            val on = sp != null && foci[i].visible && sec.type != ConicType.CIRCLE || (sec.type == ConicType.CIRCLE && i == 0)
            strings[i].visible = on
            if (on && sp != null) seg(strings[i], p, sp.focus, 0.0042f)
            val gOn = on && dandelin && spheres[i].visible && sp != null
            gens[i].visible = gOn; qDots[i].visible = gOn; qTxt[i].visible = gOn
            if (gOn && sp != null) {
                val q = sec.contactOnGenerator(p, sp)
                seg(gens[i], p, q, 0.0048f); qDots[i].at(q)
                // the label sits on the outside of the contact circle
                val out = V3d(q.x, 0.0, q.z).let { v -> val l = v.length; if (l > 1e-6) v * (0.03 / l) else V3d(0.03, 0.0, 0.0) }
                qTxt[i].at(q + out, 0.012f)
            }
        }
    }

    /**
     * The slice in its plane's (s, w) frame for the board: the drawn (clipped) branches, except the hyperbola,
     * whose two branches are drawn from the analytic form s = s0 +- a cosh u, w = b sinh u to the SAME extent
     * (the short lower nappe clips the 3-D lower branch; the board shows the curve's true symmetry).
     */
    private fun boardBranches(n: Int): List<List<Pair<Double, Double>>> {
        val s = sec
        if (s.type == ConicType.HYPERBOLA) {
            val wMax = primary?.pts?.maxOfOrNull { abs(s.toPlane(it).second) } ?: (2 * s.b)
            val uMax = kotlin.math.asinh(wMax / s.b)
            val half = n / 2
            return listOf(1.0, -1.0).map { sg -> (0..half).map { j -> val u = -uMax + 2 * uMax * j / half; (s.s0 + sg * s.a * kotlin.math.cosh(u)) to s.b * kotlin.math.sinh(u) } }
        }
        val total = branches.sumOf { it.length }
        if (total < 1e-9) return emptyList()
        return branches.map { b -> resample(b, max(6, (n * b.length / total).toInt())).map { s.toPlane(it) } }
    }

    // ---- the board: the slice in its own plane, (s, w) -> board (x, y)
    private fun layoutBoard(dt: Float, changed: Boolean) {
        val s = sec
        val ptsSW = ArrayList<Pair<Double, Double>>()
        val samples = boardBranches(N_BOARD)
        samples.forEach { ptsSW += it }
        val fociSW = (0..1).filter { foci[it].visible }.map { s.fociS[it] to 0.0 }
        ptsSW += fociSW
        if (ptsSW.isEmpty()) return
        val x0 = ptsSW.minOf { it.first }; val x1 = ptsSW.maxOf { it.first }
        val y0 = ptsSW.minOf { it.second }; val y1 = ptsSW.maxOf { it.second }
        val kT = min((BW - 0.10f) / max(1e-3, x1 - x0).toFloat(), (BH - 0.12f) / max(1e-3, y1 - y0).toFloat()).coerceAtMost(3.5f)
        val cxT = ((x0 + x1) / 2).toFloat(); val cyT = ((y0 + y1) / 2).toFloat()
        val easing: Boolean
        if (!boardInit || dt <= 0f) { boardK = kT; boardCx = cxT; boardCy = cyT; boardInit = true; easing = changed }
        else {
            val a = 1f - exp(-6f * dt)
            val dk = kT - boardK; val dx = cxT - boardCx; val dy = cyT - boardCy
            easing = abs(dk) > 1e-4f || abs(dx) > 1e-5f || abs(dy) > 1e-5f
            boardK += dk * a; boardCx += dx * a; boardCy += dy * a
        }
        val k = boardK; val ox = -0.0f; val oy = -0.025f
        fun bx(sv: Double) = (sv.toFloat() - boardCx) * k + ox
        fun by(wv: Double) = (wv.toFloat() - boardCy) * k + oy
        val z = 0.006f
        if (changed || easing || curveMatLevel > 0) {
            var used = 0
            val per = if (samples.isEmpty()) 0 else N_BOARD / samples.size
            for (p in samples) {
                val n = min(per, p.size - 1)
                for (j in 0 until n) {
                    if (used >= N_BOARD) break
                    segF(bCurve[used], bx(p[j].first), by(p[j].second), z, bx(p[j + 1].first), by(p[j + 1].second), z, 0.0042f)
                    bCurve[used].visible = true; used++
                }
            }
            for (j in used until N_BOARD) bCurve[j].visible = false
            segF(bAxis, -BW / 2 + 0.02f, by(0.0), 0.002f, BW / 2 - 0.02f, by(0.0), 0.002f, 0.0016f)
            for (i in 0..1) {
                val ds = s.directrixS.getOrNull(i)
                val x = if (ds != null) bx(ds) else 99f
                val on = ds != null && abs(x) < BW / 2 - 0.02f && s.type != ConicType.CIRCLE && (i == 0 || s.type != ConicType.PARABOLA)
                bDir[i].visible = on
                if (on) segF(bDir[i], x, -BH / 2 + 0.03f, 0.003f, x, BH / 2 - 0.075f, 0.003f, 0.0022f)
                val fOn = foci[i].visible
                bFoci[i].visible = fOn; bFociTxt[i].visible = fOn && !(s.type == ConicType.CIRCLE && i == 1)
                if (fOn) { val fx = bx(s.fociS[i]); bFoci[i].pos = P3(fx, by(0.0), z + 0.002f); bFociTxt[i].pos = P3(fx, by(0.0) - 0.032f, z + 0.003f) }
            }
            bFociTxt[0].setMat(tex[if (s.type == ConicType.PARABOLA) TX_F else TX_F1].mat)
            for (j in 0..3) bLaw[j].visible = j == s.type.ordinal
            for (j in 0..3) plaqueTxt[j].visible = j == s.type.ordinal
        }
        // P and its strings
        val (ps, pw) = s.toPlane(pNow)
        val px = bx(ps); val py = by(pw)
        bP.pos = P3(px, py, z + 0.003f); bPTxt.pos = P3(px + 0.022f, py + 0.022f, z + 0.004f)
        for (i in 0..1) {
            val on = bFoci[i].visible && s.type != ConicType.CIRCLE || (s.type == ConicType.CIRCLE && i == 0)
            bStr[i].visible = on
            if (on) segF(bStr[i], px, py, z + 0.001f, bx(s.fociS[i]), by(0.0), z + 0.001f, 0.0026f)
        }
        val dOn = s.type == ConicType.PARABOLA && bDir[0].visible
        bStr[2].visible = dOn
        if (dOn) segF(bStr[2], px, py, z + 0.001f, bx(s.directrixS[0]), py, z + 0.001f, 0.0022f)
    }

    // ---------------------------------------------------------------- readout + labels

    private fun cm(v: Double) = "%.1f".format(v * 100)
    private fun typeName(t: ConicType = sec.type) = when (t) {
        ConicType.CIRCLE -> tr("圆", "Circle"); ConicType.ELLIPSE -> tr("椭圆", "Ellipse")
        ConicType.PARABOLA -> tr("抛物线", "Parabola"); ConicType.HYPERBOLA -> tr("双曲线", "Hyperbola")
    }

    /** PF1, PF2 for the current P (m). */
    fun focalDistances(): List<Double> = sec.spheres.map { pNow.dist(it.focus) }

    private fun updateReadout() {
        val s = sec
        val beta = 90f - tiltDeg
        val l1 = "${typeName()} · e = cos β / cos α = %.2f · ".format(s.e) +
            tr("倾角 θ = %.1f°（β = 平面与轴夹角 = %.1f°，α = 半顶角 = 30°）".format(tiltDeg, beta),
                "tilt θ = %.1f° (β = plane-to-axis angle = %.1f°, α = half-angle = 30°)".format(tiltDeg, beta))
        val f = focalDistances()
        val showQ = dandelin && spheres.all { it.visible || s.far == null }
        val l2 = when (s.type) {
            ConicType.CIRCLE -> tr("PF = ${cm(f[0])} cm = 半径 r · 两个焦点重合在圆心 · 2a = 直径 ${cm(s.twoA)} cm",
                "PF = ${cm(f[0])} cm = radius r · both foci coincide at the centre · 2a = diameter ${cm(s.twoA)} cm")
            ConicType.ELLIPSE -> (if (showQ) "PF₁ = PQ₁ = ${cm(f[0])} cm   PF₂ = PQ₂ = ${cm(f[1])} cm   " else "PF₁ = ${cm(f[0])} cm   PF₂ = ${cm(f[1])} cm   ") +
                tr("PF₁ + PF₂ = ${cm(f[0] + f[1])} cm = 2a（不变）", "PF₁ + PF₂ = ${cm(f[0] + f[1])} cm = 2a (constant)")
            ConicType.HYPERBOLA -> (if (showQ) "PF₁ = PQ₁ = ${cm(f[0])} cm   PF₂ = PQ₂ = ${cm(f[1])} cm   " else "PF₁ = ${cm(f[0])} cm   PF₂ = ${cm(f[1])} cm   ") +
                tr("|PF₁ − PF₂| = ${cm(abs(f[0] - f[1]))} cm = 2a（不变）", "|PF₁ − PF₂| = ${cm(abs(f[0] - f[1]))} cm = 2a (constant)")
            ConicType.PARABOLA -> {
                val pd = abs(s.toPlane(pNow).first - s.directrixS[0])
                tr("PF = ${cm(f[0])} cm   P 到准线 d = ${cm(pd)} cm   PF = Pd（e = 1）", "PF = ${cm(f[0])} cm   P to directrix d = ${cm(pd)} cm   PF = Pd (e = 1)")
            }
        }
        val l3 = when (s.type) {
            ConicType.CIRCLE -> tr("平面垂直于轴（β = 90°）：e = 0；两个 Dandelin 球都切在圆心", "Plane square to the axis (β = 90°): e = 0; both Dandelin spheres touch it at the centre")
            ConicType.ELLIPSE -> if (dandelin) tr("同一点到同一球的切线等长：PF₁ = PQ₁、PF₂ = PQ₂ → 和 = Q₁Q₂，两接触圆间的母线段，处处一样",
                    "Tangents from one point to one sphere are equal: PF₁ = PQ₁, PF₂ = PQ₂ → sum = Q₁Q₂, the generator between the circles")
                else tr("平面比母线平（β > α）：只切一叶、曲线闭合 → 椭圆，e < 1", "Plane flatter than a generator (β > α): one nappe, a closed curve → ellipse, e < 1")
            ConicType.PARABOLA -> tr("平面平行于一条母线（β = α）：曲线奔向无穷、永不闭合 → 抛物线，e = 1", "Plane parallel to a generator (β = α): the curve runs off to infinity → parabola, e = 1")
            ConicType.HYPERBOLA -> if (dandelin) tr("PF₂ − PF₁ = Q₁Q₂：穿过锥顶的一段母线，长度不变 → 双曲线 |PF₁ − PF₂| = 2a",
                    "PF₂ − PF₁ = Q₁Q₂: a generator piece through the apex, fixed length → |PF₁ − PF₂| = 2a")
                else tr("平面比母线陡（β < α）：两叶都被切到 → 双曲线的两支，e > 1", "Plane steeper than a generator (β < α): both nappes are cut → two branches, e > 1")
        }
        readoutText = "$l1\n$l2\n$l3"
    }

    private fun world(n: Node): P3 { val t = SoftRender.worldOf(n).t; return P3(t[0], t[1], t[2]) }

    private fun updateLabels() {
        labels.clear()
        labels += LabelSpec(typeName() + "  e = %.2f".format(sec.e), P3(PLAQUE.x - 0.25f, PLAQUE.y, PLAQUE.z + 0.02f))
        val tk = world(tiltKnob)
        labels += LabelSpec(tr("倾斜 θ = %.0f°", "tilt θ = %.0f°").format(tiltDeg), P3(tk.x + 0.05f, tk.y + 0.055f, tk.z + 0.02f))
        val sk = world(shiftKnob)
        labels += LabelSpec(tr("平移 %.0f cm", "shift %.0f cm").format(dist * 100), P3(sk.x - 0.06f, sk.y - 0.06f, sk.z + 0.03f))
        val f = focalDistances()
        val law = when (sec.type) {
            ConicType.ELLIPSE -> "PF₁ + PF₂ = ${cm(f[0] + f[1])} cm"
            ConicType.HYPERBOLA -> "|PF₁ − PF₂| = ${cm(abs(f[0] - f[1]))} cm"
            ConicType.PARABOLA -> "PF = Pd = ${cm(f[0])} cm"
            ConicType.CIRCLE -> "PF = r = ${cm(f[0])} cm"
        }
        labels += LabelSpec(law, P3(BOARD.x, BOARD.y - BH / 2 - 0.035f, BOARD.z + 0.03f))
    }

    // ---------------------------------------------------------------- actions (pinch, panel, lesson, tests)

    private fun snap(raw: Float): Float = when {
        abs(raw - parabolaDeg) < 1.5f -> parabolaDeg
        raw < 1.0f -> 0f
        else -> raw.coerceIn(0f, 90f)
    }

    /** Set the tilt (degrees); snaps to the circle (0) and the parabola (90 - alpha), with a flash there. */
    fun setTilt(deg: Float) {
        raw = deg.coerceIn(0f, 90f)
        val t = snap(raw)
        if (t == parabolaDeg && tiltDeg != parabolaDeg) { flashT = 0.7f; flashPending = true }
        tiltDeg = t
    }
    fun setDist(m: Float) { dist = m.coerceIn(D_MIN, D_MAX) }

    enum class Preset(val tilt: Double, val d: Float) {
        CIRCLE(0.0, 0.15f), ELLIPSE(Conics.tiltFor(0.6, 30 * PI / 180) * 180 / PI, 0.125f), PARABOLA(60.0, 0.09f), HYPERBOLA(84.0, 0.035f)
    }
    fun preset(p: Preset) { setTilt(p.tilt.toFloat()); setDist(p.d); pathG = 0.08f }
    fun cyclePreset() { preset(Preset.entries[(sec.type.ordinal + 1) % 4]) }
    fun setDandelinOn(on: Boolean) { dandelin = on; builtKey = "" }
    fun resetAll() { resetState(); builtKey = "" }

    override val tapTargets: List<TapTarget> get() = listOf(
        TapTarget("Plaque", "名牌 → 换曲线", "Name plaque → next curve"),
        TapTarget("SphereNear", "Dandelin 球 → 显示/隐藏", "Dandelin spheres → show / hide") { dandelin },
        TapTarget("Pt", "P → 跑/停", "P → run / pause") { running },
        TapTarget("TiltKnob", "倾斜把手 → +10°", "Tilt knob → +10°"),
        TapTarget("ShiftKnob", "平移把手 → +2 cm", "Shift knob → +2 cm"),
    )

    override fun isTarget(name: String) = name in setOf("Plaque", "SphereNear", "Pt", "TiltKnob", "ShiftKnob")

    override fun onTap(name: String) {
        when (name) {
            "Plaque" -> cyclePreset()
            "SphereNear" -> setDandelinOn(!dandelin)
            "Pt" -> running = !running
            "TiltKnob" -> setTilt(if (tiltDeg >= 89.5f) 0f else ((tiltDeg / 10f).toInt() + 1) * 10f)
            "ShiftKnob" -> setDist(if (dist >= D_MAX - 1e-4f) D_MIN else min(D_MAX, ((dist * 50f).toInt() + 1) / 50f))
        }
        update(0f)
    }

    override fun tapMessage(name: String): String? = when (name) {
        "Plaque" -> tr("${typeName()}：e = %.2f".format(sec.e), "${typeName()}: e = %.2f".format(sec.e))
        "SphereNear" -> if (dandelin) tr("Dandelin 球：它们切平面的点就是焦点", "Dandelin spheres on: where they touch the plane are the foci")
            else tr("Dandelin 球隐藏", "Dandelin spheres hidden")
        "Pt" -> if (running) tr("P 开跑：盯住读数里的和", "P runs: watch the sum in the readout") else tr("P 停下", "P paused")
        "TiltKnob" -> tr("倾角 θ = %.0f° → ${typeName()}".format(tiltDeg), "Tilt θ = %.0f° → ${typeName()}".format(tiltDeg))
        "ShiftKnob" -> tr("平面离锥顶 %.0f cm：形状不变，只是大小变".format(dist * 100), "Plane %.0f cm from the apex: same shape, new size".format(dist * 100))
        else -> null
    }

    // ---- drag: tilt knob swings the plane about its foot point; shift knob pushes it along its normal
    override fun isDragTarget(name: String) = name == "TiltKnob" || name == "ShiftKnob"

    override fun onDragStart(name: String) {
        dragAcc = FloatArray(3); dragT0 = tiltDeg; dragD0 = dist
        dragN0 = sec.n
        dragK0 = sec.fromPlane(sHi + 0.026, 0.0) - sec.n * dist.toDouble()
    }

    override fun onDrag(name: String, dx: Float, dy: Float, dz: Float) {
        // volume -> canonical (undo the cone's yaw about +y)
        val c = cos(YAW); val s = sin(YAW)
        val cx = dx * c - dz * s; val cz = dx * s + dz * c
        dragAcc[0] += cx; dragAcc[1] += dy; dragAcc[2] += cz
        if (name == "TiltKnob") {
            val vx = dragK0.x + dragAcc[0]; val vy = dragK0.y + dragAcc[1]
            setTilt((atan2(vy, max(vx, 1e-4)) * 180 / PI).toFloat())
        } else {
            setDist(dragD0 + (dragAcc[0] * dragN0.x + dragAcc[1] * dragN0.y + dragAcc[2] * dragN0.z).toFloat())
        }
    }

    override fun onDragEnd(name: String) {}

    // ---------------------------------------------------------------- guided track (导学)

    private val inBand get() = sec.type == ConicType.PARABOLA

    // ---- background card (📖): context for a viewer who has never met this topic
    override val about = About(listOf(
        About.Page(
            Bi("这个实验在做什么？", "What is this experiment?"),
            Bi("一个玻璃双圆锥（两个锥尖对尖），一张发光平面穿过它。拖平面右端的金色把手改变倾斜，拖左端的青色把手平移。切出的曲线会发光，名牌实时显示：圆、椭圆、抛物线或双曲线。捏红色小球显示两个 Dandelin 球，捏白点 P 让它绕曲线跑；右边的黑板把截面摊平给你看。", "A glass double cone (two cones tip to tip) with a glowing plane through it. Drag the gold knob at the plane's right end to tilt it and the teal knob at its left end to slide it. The slice glows and the plaque names it live: circle, ellipse, parabola or hyperbola. Pinch the red ball to show the two Dandelin spheres, and pinch the white point P to send it round; the board on the right shows the slice laid flat."),
        ),
        About.Page(
            Bi("两千年的接力", "A 2,000-year relay"),
            Bi("约公元前 200 年，古希腊的阿波罗尼奥斯写了《圆锥曲线论》，椭圆、抛物线、双曲线这些名字就来自他。那时它只是纯几何。1609 年，开普勒发现行星绕太阳走椭圆，太阳在一个焦点上；伽利略则发现抛出的石头走抛物线。1822 年，丹德林用两个球给出了一个极漂亮的证明。", "Around 200 BC the Greek geometer Apollonius wrote Conics; the names ellipse, parabola and hyperbola come from him. Back then it was pure geometry. In 1609 Kepler found that planets move on ellipses with the Sun at one focus, and Galileo showed that a thrown stone follows a parabola. In 1822 Germinal Dandelin gave a beautiful proof using two spheres."),
        ),
        About.Page(
            Bi("核心：切的角度", "Key idea: the angle of the cut"),
            Bi("拿手电筒照墙：正对着墙，光斑是圆；斜一点，成了椭圆；斜到光锥的边正好和墙平行，光斑一头永远合不上，就是抛物线；再斜，就是双曲线。光锥就是圆锥，墙就是平面。四种曲线是一家人，用离心率 e 区分：圆 e = 0，椭圆 e < 1，抛物线 e = 1，双曲线 e > 1。", "Shine a torch at a wall. Straight on, the spot is a circle; tilt it and it becomes an ellipse; tilt until the edge of the beam runs parallel to the wall and the spot never closes: a parabola; tilt further: a hyperbola. The beam is the cone, the wall is the plane. The four curves are one family, told apart by the eccentricity e: circle e = 0, ellipse e < 1, parabola e = 1, hyperbola e > 1."),
        ),
        About.Page(
            Bi("怎么看这个 3D 场景", "How to read the 3-D scene"),
            Bi("蓝色玻璃锥上的线 = 母线（过锥尖的直线）。发光平面 = 刀，黄色发光曲线 = 截出的曲线。亮起金色的线 = 平面和这条母线平行，此时是抛物线。红球、绿球 = Dandelin 球，卡在锥里又贴着平面：红圈、绿圈是它们贴锥面的地方，贴平面的两点 F₁、F₂ 就是焦点。白点 P 用两根线连着焦点，Q₁、Q₂ 在两个圈上。", "Lines on the blue glass cone = generators (lines through the tip). Glowing plane = the knife; glowing yellow curve = the slice. A gold line lights up when the plane is parallel to it: a parabola. Red and green balls = Dandelin spheres, wedged in the cone and touching the plane; their rings touch the cone, and their touch points F₁, F₂ on the plane are the foci. White P is tied to the foci by strings; Q₁, Q₂ sit on the rings."),
        ),
        About.Page(
            Bi("一眼看懂的证明", "A proof at a glance"),
            Bi("椭圆上任一点到两个焦点的距离之和不变 —— 园丁用两根桩、一根绳就能画椭圆。为什么？从一点到同一个球的切线一样长：PF₁ = PQ₁，PF₂ = PQ₂。所以 PF₁ + PF₂ = Q₁Q₂，就是两个圈之间那段母线，对每个 P 都一样长。一个公式都不用。", "From any point of an ellipse, the distances to the two foci add up to the same total; that is how gardeners draw one with two pegs and a string tied between them. Why? Tangent lines from one point to one sphere are equally long: PF₁ = PQ₁ and PF₂ = PQ₂. So PF₁ + PF₂ = Q₁Q₂, the piece of generator between the two rings, the same for every P. No formula needed."),
        ),
        About.Page(
            Bi("今天在哪里？", "Where is it today?"),
            Bi("卫星锅、汽车大灯、手电筒的反光碗都是抛物面：平行光全部汇到焦点，或从焦点射出变成平行光。椭圆的两个焦点能互相“传话”：医院的体外碎石机把冲击波从一个焦点聚到另一个焦点上的结石。行星、卫星的轨道是椭圆；掠过太阳的彗星可能走双曲线，一去不回。", "Satellite dishes, car headlights and torch reflectors are parabolic: parallel rays all meet at the focus, or leave it as a parallel beam. An ellipse's two foci talk to each other: a lithotripter sends shock waves from one focus to a kidney stone sitting at the other. Planet and satellite orbits are ellipses; a comet swinging past the Sun can follow a hyperbola and never return."),
        ),
    ))

    override val lesson: Lesson by lazy {
        Lesson(
            listOf(
                LessonStep(
                    say = Bi("平面和圆锥的一条母线平行时，截出什么？", "When the plane is parallel to one generator of the cone, what is the slice?"),
                    options = listOf(Bi("圆", "A circle"), Bi("椭圆", "An ellipse"), Bi("抛物线", "A parabola"), Bi("双曲线", "A hyperbola")),
                    answer = 2,
                    onEnter = { setDandelinOn(false); running = false; preset(Preset.ELLIPSE) },
                ),
                LessonStep(
                    say = Bi("捏住平面右端的金色把手往上拖，直到平面和金色母线平行（会“咔”地吸住）。", "Pinch the gold knob at the plane's right end and drag it up until the plane runs parallel to the gold generator (it clicks in)."),
                    target = "TiltKnob",
                    done = { inBand },
                    autoplay = { setTilt(parabolaDeg) },
                    reveal = true,
                    insight = Bi("β = α 时 e = cos β / cos α = 1：曲线永不闭合，是抛物线。比母线平是椭圆，比母线陡就切到两叶，是双曲线——同一族曲线。", "At β = α, e = cos β / cos α = 1: the curve never closes — a parabola. Flatter gives an ellipse, steeper cuts both nappes — a hyperbola. One family."),
                ),
                LessonStep(
                    say = Bi("椭圆上的点到两个焦点的距离之和会怎样变？", "As a point moves round an ellipse, what happens to the sum of its distances to the two foci?"),
                    options = listOf(Bi("越来越大", "It grows"), Bi("忽大忽小", "It goes up and down"), Bi("不变", "It stays the same"), Bi("越来越小", "It shrinks")),
                    answer = 2,
                ),
                LessonStep(
                    say = Bi("捏名牌切到椭圆，捏红色小球显示 Dandelin 球，再捏白点 P 让它绕一圈。", "Pinch the plaque to get an ellipse, pinch the red sphere to show the Dandelin spheres, then pinch the white point P to run it once round."),
                    target = "SphereNear",
                    onEnter = { laps = 0f },
                    done = { dandelin && running && sec.type == ConicType.ELLIPSE && laps >= 1f },
                    autoplay = { preset(Preset.ELLIPSE); setDandelinOn(true); running = true; laps = 0f },
                    reveal = true,
                    insight = Bi("同一点到同一个球的切线一样长：PF₁ = PQ₁、PF₂ = PQ₂，所以 PF₁ + PF₂ = Q₁Q₂，就是两接触圆之间那段母线，对每个 P 都一样 = 2a。", "Tangents from one point to one sphere are equal: PF₁ = PQ₁, PF₂ = PQ₂, so PF₁ + PF₂ = Q₁Q₂ — the generator piece between the two circles, the same for every P: 2a."),
                ),
            ),
            challenge = Challenge(
                say = Bi("挑战：拖动倾斜把手，截出离心率 e = 0.5 ± 0.02 的椭圆。", "Challenge: drag the tilt knob to cut an ellipse with eccentricity e = 0.5 ± 0.02."),
                progress = { tr("现在：${typeName()}  e = %.3f（目标 0.480 – 0.520）".format(sec.e), "Now: ${typeName()}  e = %.3f (target 0.480 – 0.520)".format(sec.e)) },
                done = { sec.type == ConicType.ELLIPSE && abs(sec.e - 0.5) <= 0.02 },
                autoplay = { setTilt((Conics.tiltFor(0.5, ALPHA) * 180 / PI).toFloat()) },
            ),
        )
    }

    // ---------------------------------------------------------------- review states

    override fun reviewVariants(): Map<String, () -> Unit> = mapOf(
        "circle" to { preset(Preset.CIRCLE) },
        "parabola" to { preset(Preset.PARABOLA) },
        "hyperbola" to { preset(Preset.HYPERBOLA) },
        "plain" to { preset(Preset.ELLIPSE); setDandelinOn(false) },
    )

    // ---------------------------------------------------------------- panel

    @Composable
    override fun Panel() {
        val t = tiltDeg
        val d = dist
        val sp by FieldRef(::speed)
        LabPanel(readout) {
            LabRow {
                SegmentControl {
                    Preset.entries.forEachIndexed { k, p ->
                        SegmentItem(selected = sec.type.ordinal == k, onClick = { preset(p) }, title = { Text(typeName(ConicType.entries[k])) })
                    }
                }
                Spacer(Modifier.width(16.dp))
                SegmentControl {
                    listOf(tr("慢", "Slow"), tr("中", "Medium"), tr("快", "Fast")).forEachIndexed { k, s ->
                        SegmentItem(selected = sp == k, onClick = { speed = k }, title = { Text(s) })
                    }
                }
                Button(onClick = { resetAll() }) { Text(tr("重置", "Reset")) }
            }
            LabRow {
                LabSlider(tr("倾角 θ", "Tilt θ"), t, 0f..90f, "%.1f°".format(t)) { setTilt(it) }
                LabSlider(tr("平移 d", "Shift d"), d * 100f, D_MIN * 100f..D_MAX * 100f, "%.1f cm".format(d * 100f)) { setDist(it / 100f) }
            }
        }
    }
}
