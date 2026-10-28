// 代数的几何 · 乘法即面积 (Euclid, Elements II) — "multiplication IS area, cubing IS volume".
//
// LESSON (3Blue1Brown spirit): an algebraic identity is a statement about pieces you can move.
//   ① (a+b)² = a² + 2ab + b²   the big square is a², b² AND two a×b rectangles. Pinch an ab
//      rectangle out and the "a² + b²" guess is left with two holes — the missing 2ab is visible.
//   ② (a+b)(a−b) = a² − b²     cut the b² corner off a², then swing the top strip a quarter turn
//      (pinch or drag it): the L becomes an (a+b) × (a−b) rectangle. One cut, one turn, same area.
//   ③ (a+b)³ = a³ + 3a²b + 3ab² + b³   the cube cut at a on all three axes: 8 blocks, 1-3-3-1
//      (Pascal). Only 3-D shows this — walk around it, drag to turn it, pinch a block to lift it out.
//   ④ 1 + 3 + 5 + … + (2n−1) = n²   the square peeled into L-shaped layers (gnomons).
// Drag the knobs on the rulers (the figures' bottom edges) to change a, b, n: the geometry and both
// sides of the identity update live and stay equal (exact arithmetic on a 0.1 grid, core/algebra).
//
// Every piece is a UNIT box moved/scaled by its transform; no mesh is rebuilt after buildSpec.

package com.bytedance.mathlab.demos.algebra

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bytedance.mathlab.core.P3
import com.bytedance.mathlab.core.algebra.Algebra
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

object AlgebraDemo : SpecDemo() {
    override val id = "algebra"
    override val title = "代数的几何 · 乘法即面积"  // i18n-ok (zh half of DemoRegistry.titleOf; English in TITLE_EN)
    override val credit = "Euclid · Elements II"

    // ---------------------------------------------------------------- layout (volume metres)
    // Each station is FITTED to a fixed display size — the square's side, the cube's edge, the grid — so
    // the pieces always fill the stage; the ruler's unit ticks carry the scale. The fit is FROZEN while a
    // knob is held (the knob stays under the hand: a bigger a really is a bigger square), and the view
    // zooms smoothly to the new fit when it is let go.
    private const val H1 = 0.53f              // ① side of the (a+b) square
    private const val H2 = 0.53f              // ② side of the a square, at most ...
    private const val W2 = 1.10f              // ② ... and the (a+b) rectangle + parked b² at most this wide
    private const val L3 = 0.37f              // ③ edge of the (a+b) cube
    private const val H4 = 0.48f              // ④ grid side (n > 5) ...
    private const val U4_MAX = 0.096f         // ④ ... cells never bigger than this
    private const val XC = -0.06f             // centre line of the stage (= the board's)
    private const val X1_PARKED = -0.62f      // ① origin while the ab pieces are parked on the right
    private const val Y0 = -0.155f            // bottom edge of the 2-D figures
    private const val Z0 = 0.10f              // the 2-D figures stand a little in front of the volume centre
    private const val T = 0.028f              // slab thickness
    private const val ER = 0.0085f            // edge-bar thickness
    private const val FRONT = T / 2 + 0.006f  // pinch hub of a slab piece: just in front of its face
    private const val RULER_Y = -0.032f       // ruler below the figure (figure-local)
    private const val GAP1 = 0.035f           // explode gap, station ①
    private const val PARK1 = 0.065f          // gap between the square and the parked ab pieces
    private const val G3 = 0.033f             // explode gap, station ③
    private const val PEEL4 = 0.070f          // total peel spread, station ④
    private val C3 = P3(-0.24f, 0.115f, 0.03f)     // cube centre
    private val LIFT3 = P3(0.34f, 0.08f, 0.16f)    // where a lifted block is shown (world)
    // the board hangs ABOVE the action band: seen from 2.5 m the figures (top ~0.41 m, 0.55 m in front of
    // it) project up to ~0.49 m on its plane, so its face starts there (inside the 0.6 m volume half-height)
    private const val BOARD_Y = 0.537f
    private const val BOARD_X = -0.06f
    private const val BOARD_Z = -0.45f
    private const val TOK_X = 0.755f
    private val TOK_Y = floatArrayOf(0.30f, 0.16f, 0.02f, -0.12f)
    private const val PLINTH_Y = -0.262f
    private val CUBE_ROT0 = Q.axisAngle(1f, 0f, 0f, Math.toRadians(16.0).toFloat()) * Q.axisAngle(0f, 1f, 0f, Math.toRadians(-35.0).toFloat())
    private const val DRAG_TURN = 3.2f        // rad per metre of hand motion (cube)

    /** Current metres per unit length of station [s] (④: per cell) — read by AlgebraSceneTest. */
    internal fun unitOf(s: Int) = sc[s]

    // ---------------------------------------------------------------- colours (sRGB)
    private val A_FILL = Rgb(0.22f, 0.47f, 0.95f)
    private val B_FILL = Rgb(0.98f, 0.66f, 0.15f)
    private val AB_FILL = Rgb(0.20f, 0.74f, 0.45f)
    private val A2B_FILL = Rgb(0.13f, 0.64f, 0.78f)
    private val AB2_FILL = Rgb(0.56f, 0.79f, 0.20f)
    private val STRIP_FILL = Rgb(0.36f, 0.60f, 1.00f)
    private val A_EDGE = Rgb(0.62f, 0.82f, 1.00f)
    private val B_EDGE = Rgb(1.00f, 0.87f, 0.46f)
    private val D_EDGE = Rgb(0.84f, 0.70f, 1.00f)          // a − b
    private val WHITE = Rgb(0.98f, 0.98f, 1.00f)
    private val LAYER = listOf(
        Rgb(0.25f, 0.50f, 1.00f), Rgb(0.13f, 0.72f, 0.95f), Rgb(0.10f, 0.78f, 0.68f), Rgb(0.25f, 0.80f, 0.40f),
        Rgb(0.60f, 0.82f, 0.20f), Rgb(0.96f, 0.80f, 0.20f), Rgb(1.00f, 0.62f, 0.18f), Rgb(1.00f, 0.45f, 0.35f),
        Rgb(0.95f, 0.40f, 0.62f), Rgb(0.68f, 0.45f, 0.98f))
    private fun cubeFill(nb: Int) = when (nb) { 0 -> A_FILL; 1 -> A2B_FILL; 2 -> AB2_FILL; else -> B_FILL }

    // ---------------------------------------------------------------- texts (baked once, BakeCache)
    private const val TX_A = 0; private const val TX_B = 1; private const val TX_AMB = 2
    private const val TX_A2 = 3; private const val TX_AB = 4; private const val TX_B2 = 5
    private const val TX_A3 = 6                 // +nb: a³, a²b, ab², b³
    private const val TX_AAMB = 10; private const val TX_BAMB = 11; private const val TX_N = 12
    private const val TX_ODD = 13               // +k: "1", "3", ..., "19"
    private const val TX_F = 23                 // +station: the identity on the board
    private const val TEX_VERSION = 3
    private const val EM_DECAL = 84
    private const val EM_BOARD = 112

    private fun sp(t: String, c: Rgb = WHITE) = MathText.Span(t, c)
    private val FA = Rgb(0.62f, 0.80f, 1.00f); private val FB = Rgb(1.00f, 0.84f, 0.45f); private val FAB = Rgb(0.50f, 0.93f, 0.66f)
    private val FA2B = Rgb(0.45f, 0.88f, 0.96f); private val FAB2 = Rgb(0.80f, 0.94f, 0.42f); private val FG = Rgb(0.60f, 0.64f, 0.72f)
    private val FW = Rgb(0.95f, 0.96f, 0.98f)

    /** (spans, px per em, outlined?) in TX_* order. */
    private val TEXTS: List<Triple<List<MathText.Span>, Int, Boolean>> by lazy {
        val d = ArrayList<Triple<List<MathText.Span>, Int, Boolean>>()
        for (s in listOf("a", "b", "a−b", "a^2", "ab", "b^2", "a^3", "a^2b", "ab^2", "b^3", "a(a−b)", "b(a−b)", "n")) d += Triple(listOf(sp(s)), EM_DECAL, true)
        for (k in 1..10) d += Triple(listOf(sp("${2 * k - 1}")), EM_DECAL, true)
        d += Triple(listOf(sp("(a+b)^2", FW), sp(" = ", FG), sp("a^2", FA), sp(" + ", FG), sp("2ab", FAB), sp(" + ", FG), sp("b^2", FB)), EM_BOARD, false)
        d += Triple(listOf(sp("(a+b)(a−b)", FW), sp(" = ", FG), sp("a^2", FA), sp(" − ", FG), sp("b^2", FB)), EM_BOARD, false)
        d += Triple(listOf(sp("(a+b)^3", FW), sp(" = ", FG), sp("a^3", FA), sp(" + ", FG), sp("3a^2b", FA2B), sp(" + ", FG), sp("3ab^2", FAB2), sp(" + ", FG), sp("b^3", FB)), EM_BOARD, false)
        d += Triple(listOf(sp("1", lighten(LAYER[0])), sp(" + ", FG), sp("3", lighten(LAYER[1])), sp(" + ", FG), sp("5", lighten(LAYER[2])), sp(" + ", FG),
            sp("…", FG), sp(" + ", FG), sp("(2n−1)", FW), sp(" = ", FG), sp("n^2", FW)), EM_BOARD, false)
        d
    }
    private fun lighten(c: Rgb) = ProcTex.okLerp(c, Rgb(1f, 1f, 1f), 0.30f)

    private class TexInfo(val img: Image, emPx: Int) {
        val aspect = img.w.toFloat() / img.h          // quad width / height
        val emFrac = emPx.toFloat() / img.h            // em height / quad height
        val emW = img.w.toFloat() / emPx               // quad width in em
        val mat = Mat.Unlit(Rgb(1f, 1f, 1f), tex = img, blend = Blend.TRANSPARENT)
        fun tinted(c: Rgb) = Mat.Unlit(c, tex = img, blend = Blend.TRANSPARENT)
    }
    private lateinit var tex: List<TexInfo>
    private var slabTex: Image? = null

    // ---------------------------------------------------------------- state
    /** 0 ① (a+b)², 1 ② (a+b)(a−b), 2 ③ (a+b)³, 3 ④ odd numbers. */
    var station = 0
    /** a and b in TENTHS (exact; 30 = 3.0). */
    var aT = 30L
    var bT = 20L
    var n4 = 5
    var explode1 = false
    val abOut = BooleanArray(2)
    /** ②: 0 = whole a², 1 = b² corner cut off, 2 = strip swung into place. */
    var stage2 = 0
    var explode3 = false
    var lifted = -1
    val seen = BooleanArray(8)
    var peel4 = false

    private var e1 = 0f
    private val abV = FloatArray(2)
    private var cutV = 0f
    private var m2 = 0f
    private var drag2 = false
    private var e3 = 0f
    private val liftV = FloatArray(8)
    private var e4 = 0f
    private var cubeRot = CUBE_ROT0
    /** Current display scale (m per unit) and figure origin x of each station, easing toward the fit. */
    private val sc = FloatArray(4)
    private val ox = FloatArray(4)
    private var knobHeld = false
    private var dragAcc = 0f
    private var dragA0 = 0L
    private var dragB0 = 0L
    private var dragN0 = 0
    private var stepA0 = 0L
    private var stepB0 = 0L

    private fun resetState() {
        station = 0; aT = 30L; bT = 20L; n4 = 5
        explode1 = false; abOut.fill(false); stage2 = 0; explode3 = false; lifted = -1; seen.fill(false); peel4 = false
        e1 = 0f; abV.fill(0f); cutV = 0f; m2 = 0f; drag2 = false; e3 = 0f; liftV.fill(0f); e4 = 0f
        cubeRot = CUBE_ROT0; knobHeld = false
        for (st in 0..3) { sc[st] = fitScale(st); ox[st] = fitOrigin(st, 0f) }
        dragAcc = 0f; dragA0 = aT; dragB0 = bT; dragN0 = n4; stepA0 = aT; stepB0 = bT
    }

    // ---------------------------------------------------------------- scene nodes
    private class Piece2(val hub: Node, val body: Node, val edges: List<Node>, val label: Node, val tex: Int)
    private class Ruler(val grp: Node, val segA: Node, val segB: Node?, val knobA: Node, val knobB: Node?,
                        val ticks: List<Node>, val txtA: Node, val txtB: Node?)
    private class Blk(val hub: Node, val body: Node, val edges: List<Node>, val faces: List<Node>, val i: Int, val j: Int, val k: Int) {
        val nb get() = i + j + k
    }

    private lateinit var unitBox: MeshData
    private lateinit var quad: MeshData
    private lateinit var ball: MeshData
    private lateinit var groups: Array<Node>
    private lateinit var tokens: Array<Node>
    private lateinit var plates: Array<Node>
    private lateinit var glow: Node
    private lateinit var formulas: Array<Node>
    // ①
    private lateinit var p1: Array<Piece2>            // a², ab (right), ab (top), b²
    private lateinit var frame1: List<Node>
    private lateinit var ghost1: Array<Node>
    private lateinit var ruler1: Ruler
    private lateinit var side1: Array<Node>           // "a", "b" up the left side
    // ②
    private lateinit var p2: Array<Piece2>            // bottom, strip, corner
    private lateinit var ghostSq2: List<Node>
    private lateinit var slot2: Node
    private lateinit var slotEdges2: List<Node>
    private lateinit var cut2: Node
    private lateinit var ruler2: Ruler
    private lateinit var side2: Array<Node>           // "a−b", "b"
    // ③
    private lateinit var blocks: Array<Blk>
    private lateinit var ruler3: Ruler
    private lateinit var liftTag: Node
    // ④
    private lateinit var tiles: Array<Node>           // c + 10 r
    private lateinit var oddTags: Array<Node>
    private lateinit var stack4: Node
    private lateinit var ruler4: Ruler

    private val edgeMats by lazy { arrayOf(Mat.Unlit(A_EDGE), Mat.Unlit(B_EDGE), Mat.Unlit(D_EDGE)) }
    private fun fill(c: Rgb) = Mat.Pbr(c, metallic = 0f, roughness = 0.36f, emissive = Rgb(c.r * 0.26f, c.g * 0.26f, c.b * 0.26f))
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.32f)
    private val plateOff = Mat.Pbr(Rgb(0.10f, 0.11f, 0.13f), metallic = 0.3f, roughness = 0.35f)
    private val plateOn = Mat.Pbr(Rgb(0.16f, 0.24f, 0.40f), metallic = 0.2f, roughness = 0.30f, emissive = Rgb(0.10f, 0.24f, 0.50f))

    // ---------------------------------------------------------------- build

    override fun buildSpec(cacheDir: File): Node {
        resetState()
        val imgs = BakeCache.load(cacheDir, "algebra_text", TEX_VERSION) {
            TEXTS.map { (spans, em, outlined) -> MathText.bake(spans, em, outline = if (outlined) Rgb(0.03f, 0.04f, 0.07f) else null) } +
                listOf(ProcTex.bakePlanet(256, 128, Rgb(0.07f, 0.075f, 0.085f), Rgb(0.12f, 0.125f, 0.14f), Rgb(0.20f, 0.205f, 0.22f), 0f, 77))
        }
        tex = TEXTS.mapIndexed { i, t -> TexInfo(imgs[i], t.second) }
        slabTex = imgs.last()

        unitBox = MeshGen.box(1f, 1f, 1f)
        quad = OptGeo.quad(1f, 1f)
        ball = MeshGen.sphere(1f, 16, 32, MeshGen.SphereUv.EQUIRECT)
        val root = Node("AlgebraRoot")
        buildBench(root)
        groups = Array(4) { k -> root.add(Node("St${k + 1}")) }
        groups[0].pos = P3(ox[0], Y0, Z0); groups[1].pos = P3(ox[1], Y0, Z0); groups[3].pos = P3(ox[3], Y0, Z0)
        build1(groups[0]); build2(groups[1]); build3(groups[2], root); build4(groups[3])
        buildTokens(root)
        updateLabels()
        return root
    }

    private fun buildBench(root: Node) {
        val slab = Mat.Pbr(Rgb(1f, 1f, 1f), metallic = 0f, roughness = 0.55f, tex = slabTex)
        root.add("Plinth", MeshGen.box(1.74f, 0.035f, 0.92f), slab, P3(0f, PLINTH_Y, -0.04f))
        root.add("PlinthBand", MeshGen.box(1.75f, 0.008f, 0.93f), alu, P3(0f, PLINTH_Y, -0.04f))
        // a soft pool of light on the bench under the active figure
        val pool = OptGeo.lumTex(64, 64) { u, v -> val dx = (u - 0.5f) * 2f; val dy = (v - 0.5f) * 2f; val r2 = dx * dx + dy * dy; if (r2 >= 1f) 0f else (1f - r2) * (1f - r2) }
        glow = root.add("Glow", quad, Mat.Unlit(Rgb(0.30f, 0.50f, 1.0f), alpha = 0.30f, tex = pool, blend = Blend.ADD)).also {
            it.rot = Q.axisAngle(1f, 0f, 0f, -PI.toFloat() / 2); it.scale = P3(1.25f, 0.62f, 1f); it.pos = P3(-0.10f, PLINTH_Y + 0.019f, Z0)
        }
        // the formula board: the identity, colour-coded like the pieces
        root.add("BoardFrame", MeshGen.box(1.30f, 0.094f, 0.010f), alu, P3(BOARD_X, BOARD_Y, BOARD_Z - 0.006f))
        root.add("Board", MeshGen.box(1.28f, 0.082f, 0.014f), Mat.Pbr(Rgb(0.045f, 0.05f, 0.062f), metallic = 0.1f, roughness = 0.28f), P3(BOARD_X, BOARD_Y, BOARD_Z))
        formulas = Array(4) { k ->
            val t = tex[TX_F + k]
            root.add("Formula$k", quad, t.mat, P3(BOARD_X, BOARD_Y - 0.003f, BOARD_Z + 0.009f)).also {
                val em = min(0.058f, 1.20f / t.emW)
                val h = em / t.emFrac
                it.scale = P3(h * t.aspect, h, 1f)
            }
        }
    }

    private fun buildPiece(parent: Node, name: String, fillMat: Mat, hType: Int, vType: Int, t: Int, tap: Boolean = false): Piece2 {
        val hub = parent.add(Node(name))
        val body = hub.add("${name}_m", unitBox, fillMat, P3(0f, 0f, -FRONT))
        val edges = (0 until 4).map { k -> hub.add("${name}_e$k", unitBox, edgeMats[if (k < 2) hType else vType]) }
        val label = hub.add("${name}_t", quad, tex[t].mat)
        if (tap) hub.tapRadius = 0.05f
        return Piece2(hub, body, edges, label, t)
    }

    private fun decal(parent: Node, name: String, t: Int, tint: Rgb? = null): Node =
        parent.add(name, quad, if (tint != null) tex[t].tinted(tint) else tex[t].mat).also { it.scale = P3(1e-3f, 1e-3f, 1f) }

    private fun buildRuler(parent: Node, tag: String, unitTick: Int, txtA: Int, colA: Rgb, txtB: Int?, colB: Rgb?, knobAName: String, knobBName: String?): Ruler {
        val grp = parent.add(Node("Ruler_$tag"))
        val segA = grp.add("RulerA_$tag", unitBox, Mat.Unlit(colA))
        val segB = if (colB != null) grp.add("RulerB_$tag", unitBox, Mat.Unlit(colB)) else null
        val ticks = (0..unitTick).map { k -> grp.add("Tick_${tag}_$k", unitBox, Mat.Unlit(Rgb(0.85f, 0.88f, 0.94f))).also { it.scale = P3(0.0034f, 0.024f, 0.0034f) } }
        fun knob(name: String, c: Rgb) = grp.add(name, ball, Mat.Pbr(c, metallic = 0.1f, roughness = 0.22f, emissive = Rgb(c.r * 0.35f, c.g * 0.35f, c.b * 0.35f))).also {
            it.uniformScale(0.021f); it.tapRadius = 0.054f / 0.021f
        }
        val kA = knob(knobAName, colA)
        val kB = if (knobBName != null && colB != null) knob(knobBName, colB) else null
        val tA = decal(grp, "RulerTxtA_$tag", txtA, colA)
        val tB = if (txtB != null && colB != null) decal(grp, "RulerTxtB_$tag", txtB, colB) else null
        return Ruler(grp, segA, segB, kA, kB, ticks, tA, tB)
    }

    private fun build1(g: Node) {
        frame1 = (0 until 4).map { g.add("F1_e$it", unitBox, Mat.Unlit(Rgb(0.80f, 0.84f, 0.92f))) }
        ghost1 = Array(2) { g.add("G1_ab$it", unitBox, Mat.Unlit(AB_FILL, alpha = 0.06f, blend = Blend.TRANSPARENT)) }
        p1 = arrayOf(
            buildPiece(g, "P1_a2", fill(A_FILL), 0, 0, TX_A2),
            buildPiece(g, "P1_ab1", fill(AB_FILL), 1, 0, TX_AB, tap = true),
            buildPiece(g, "P1_ab2", fill(AB_FILL), 0, 1, TX_AB, tap = true),
            buildPiece(g, "P1_b2", fill(B_FILL), 1, 1, TX_B2),
        )
        ruler1 = buildRuler(g, "1", 7, TX_A, A_EDGE, TX_B, B_EDGE, "KnobA_1", "KnobB_1")
        side1 = arrayOf(decal(g, "Side1_a", TX_A, A_EDGE), decal(g, "Side1_b", TX_B, B_EDGE))
    }

    private fun build2(g: Node) {
        ghostSq2 = (0 until 4).map { g.add("G2_e$it", unitBox, Mat.Unlit(Rgb(0.75f, 0.80f, 0.90f), alpha = 0.45f, blend = Blend.TRANSPARENT)) }
        slot2 = g.add("G2_slot", unitBox, Mat.Unlit(STRIP_FILL, alpha = 0.16f, blend = Blend.TRANSPARENT))
        slotEdges2 = (0 until 4).map { g.add("G2_s$it", unitBox, Mat.Unlit(A_EDGE, alpha = 0.55f, blend = Blend.TRANSPARENT)) }
        p2 = arrayOf(
            buildPiece(g, "P2_bottom", fill(A_FILL), 0, 2, TX_AAMB),
            buildPiece(g, "P2_strip", fill(STRIP_FILL), 2, 1, TX_BAMB, tap = true),
            buildPiece(g, "P2_corner", fill(B_FILL), 1, 1, TX_B2, tap = true),
        )
        cut2 = g.add("Cut2", unitBox, Mat.Unlit(Rgb(0.95f, 0.97f, 1f), blend = Blend.ADD))
        ruler2 = buildRuler(g, "2", 7, TX_A, A_EDGE, TX_B, B_EDGE, "KnobA_2", "KnobB_2")
        side2 = arrayOf(decal(g, "Side2_d", TX_AMB, D_EDGE), decal(g, "Side2_b", TX_B, B_EDGE))
    }

    private fun build3(g: Node, root: Node) {
        blocks = Array(8) { n ->
            val i = n and 1; val j = (n shr 1) and 1; val k = (n shr 2) and 1
            val hub = g.add(Node("B3_$n"))
            val body = hub.add("B3_${n}_m", unitBox, fill(cubeFill(i + j + k)))
            // 12 edges: 4 along x (length a|b by i), 4 along y (j), 4 along z (k)
            val edges = (0 until 12).map { e -> hub.add("B3_${n}_e$e", unitBox, edgeMats[when (e / 4) { 0 -> i; 1 -> j; else -> k }]) }
            // the term on its three OUTWARD faces (+x if i = 1, -x if i = 0, ...)
            val faces = (0 until 3).map { f -> decal(hub, "B3_${n}_t$f", TX_A3 + i + j + k) }
            hub.tapRadius = 0.05f
            Blk(hub, body, edges, faces, i, j, k)
        }
        ruler3 = buildRuler(g, "3", 7, TX_A, A_EDGE, TX_B, B_EDGE, "KnobA_3", "KnobB_3")
        liftTag = root.add("Lift3_t", quad, tex[TX_A3].mat).also { it.billboard = true; it.visible = false }
    }

    private fun build4(g: Node) {
        val mats = LAYER.map { fill(it) }
        tiles = Array(100) { idx ->
            val c = idx % 10; val r = idx / 10
            g.add("T4_${c}_$r", unitBox, mats[max(c, r)]).also { it.scale = P3(0.04f, 0.04f, T) }
        }
        oddTags = Array(10) { k -> decal(g, "Odd4_$k", TX_ODD + k) }
        stack4 = g.add(Node("Stack4")).also { it.tapRadius = 0.06f }
        ruler4 = buildRuler(g, "4", 10, TX_N, Rgb(0.90f, 0.92f, 0.98f), null, null, "KnobN_4", null)
    }

    /** Four station plaques on the right, each with a miniature of its station; pinch one to go there. */
    private fun buildTokens(root: Node) {
        // the pinch hub sits in front of the plate (its halo ring circles the miniature, not buried in the plate)
        val plateMesh = MeshGen.box(0.130f, 0.105f, 0.012f)
        tokens = Array(4) { k ->
            root.add(Node("Tok$k")).also { it.pos = P3(TOK_X, TOK_Y[k], 0.02f); it.tapRadius = 0.06f }
        }
        plates = Array(4) { k -> tokens[k].add("Plate$k", plateMesh, plateOff, P3(0f, 0f, -0.02f)) }
        val z = -0.004f
        fun mini(p: Node, name: String, w: Float, h: Float, d: Float, x: Float, y: Float, zz: Float, c: Rgb) =
            p.add(name, MeshGen.box(w, h, d), fill(c), P3(x, y, zz))
        // ① the split square
        run {
            val t = tokens[0]; val a = 0.040f; val b = 0.026f; val o = -(a + b) / 2; val s = 0.0025f
            mini(t, "Tm0_a", a - s, a - s, 0.008f, o + a / 2, o + a / 2, z, A_FILL)
            mini(t, "Tm0_b", b - s, a - s, 0.008f, o + a + b / 2, o + a / 2, z, AB_FILL)
            mini(t, "Tm0_c", a - s, b - s, 0.008f, o + a / 2, o + a + b / 2, z, AB_FILL)
            mini(t, "Tm0_d", b - s, b - s, 0.008f, o + a + b / 2, o + a + b / 2, z, B_FILL)
        }
        // ② an L with its b² corner lifting away
        run {
            val t = tokens[1]; val a = 0.050f; val b = 0.022f; val o = -0.032f
            mini(t, "Tm1_a", a, a - b, 0.008f, o + a / 2, o + (a - b) / 2, z, A_FILL)
            mini(t, "Tm1_b", a - b - 0.002f, b, 0.008f, o + (a - b) / 2 - 0.001f, o + a - b / 2 + 0.001f, z, STRIP_FILL)
            mini(t, "Tm1_c", b, b, 0.008f, o + a - b / 2 + 0.012f, o + a - b / 2 + 0.012f, z + 0.004f, B_FILL)
        }
        // ③ the exploded cube, turned like the big one
        run {
            val g = tokens[2].add(Node("Tm2")).also { it.pos = P3(0f, 0f, 0.01f); it.rot = CUBE_ROT0 }
            val a = 0.022f; val b = 0.014f; val gap = 0.004f; val h = (a + b) / 2
            for (n in 0 until 8) {
                val i = n and 1; val j = (n shr 1) and 1; val k = (n shr 2) and 1
                fun c(bit: Int) = if (bit == 0) -h + a / 2 - gap else -h + a + b / 2 + gap
                fun l(bit: Int) = if (bit == 0) a else b
                mini(g, "Tm2_$n", l(i), l(j), l(k), c(i), c(j), c(k), cubeFill(i + j + k))
            }
        }
        // ④ 4 x 4 cells in L layers
        run {
            val t = tokens[3]; val u = 0.016f; val o = -2 * u
            for (r in 0 until 4) for (c in 0 until 4) mini(t, "Tm3_${c}_$r", u * 0.86f, u * 0.86f, 0.006f, o + (c + 0.5f) * u, o + (r + 0.5f) * u, z, LAYER[max(c, r)])
        }
    }

    // ---------------------------------------------------------------- small node helpers
    private fun Node.at(x: Float, y: Float, z: Float) { val p = pos; if (p.x != x || p.y != y || p.z != z) pos = P3(x, y, z) }
    private fun Node.dims(x: Float, y: Float, z: Float) { val s = scale; if (s.x != x || s.y != y || s.z != z) scale = P3(x, y, z) }
    private fun Node.turnZ(rad: Float) { val q = Q.axisAngle(0f, 0f, 1f, rad); if (rot != q) rot = q }
    private fun Node.turn(q: Q) { if (rot != q) rot = q }
    private fun Node.show(v: Boolean) { visible = v }
    private fun Node.grab(r: Float) { val q = (r * 100f).roundToInt() / 100f; if (tapRadius != q) tapRadius = q }
    private fun sm(t: Float): Float { val x = t.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** Size a text decal: em height (m) limited by [cap] and by the visible box [vw] x [vh] it sits on. */
    private fun fitEm(t: Int, vw: Float, vh: Float, cap: Float): Float =
        min(cap, min(0.46f * vh, 0.84f * vw / tex[t].emW)).coerceAtLeast(0.004f)
    /** Size a decal to [em] metres per em; text under ~1.1 cm is unreadable at 2.5 m, so it is hidden. */
    private fun setDecal(n: Node, t: Int, em: Float) {
        val e = if (em < 0.011f) 1e-4f else em
        val h = e / tex[t].emFrac; n.dims(h * tex[t].aspect, h, 1f)
    }

    private fun layoutPiece(p: Piece2, cx: Float, cy: Float, cz: Float, w: Float, h: Float, rot: Float, emCap: Float = 0.068f) {
        // the hub sits just in FRONT of the slab, so its pinch halo ring is not buried inside the slab
        p.hub.at(cx, cy, cz + FRONT); p.hub.turnZ(rot)
        p.body.dims(w, h, T)
        val ez = T / 2 - FRONT
        p.edges[0].at(0f, -h / 2, ez); p.edges[0].dims(w + ER, ER, ER)
        p.edges[1].at(0f, h / 2, ez); p.edges[1].dims(w + ER, ER, ER)
        p.edges[2].at(-w / 2, 0f, ez); p.edges[2].dims(ER, h + ER, ER)
        p.edges[3].at(w / 2, 0f, ez); p.edges[3].dims(ER, h + ER, ER)
        val c = abs(cos(rot)); val s = abs(sin(rot))
        p.label.turnZ(-rot)                                    // the text stays upright while the piece turns
        p.label.at(0f, 0f, T / 2 + 0.0045f - FRONT)
        setDecal(p.label, p.tex, fitEm(p.tex, w * c + h * s, w * s + h * c, emCap))
        if (p.hub.tapRadius > 0f) p.hub.grab(max(0.05f, 0.40f * min(w, h)))
    }

    private fun layoutRuler(r: Ruler, lenA: Float, lenB: Float, unit: Float) {
        r.segA.at(lenA / 2, 0f, 0f); r.segA.dims(lenA, 0.010f, 0.010f)
        r.segB?.at(lenA + lenB / 2, 0f, 0f); r.segB?.dims(lenB, 0.010f, 0.010f)
        r.knobA.at(lenA, 0f, 0f)
        r.knobB?.at(lenA + lenB, 0f, 0f)
        r.ticks.forEachIndexed { k, t -> val x = k * unit; t.show(x <= lenA + lenB + 1e-4f); t.at(x, 0f, -0.002f) }
        r.txtA.at(lenA / 2, -0.036f, 0f); setDecal(r.txtA, if (r.segB == null) TX_N else TX_A, 0.046f)
        r.txtB?.let { it.at(lenA + lenB / 2, -0.036f, 0f); setDecal(it, TX_B, 0.046f) }
    }

    private fun outline(rods: List<Node>, x0: Float, y0: Float, x1: Float, y1: Float, z: Float, th: Float) {
        val w = x1 - x0; val h = y1 - y0
        rods[0].at((x0 + x1) / 2, y0, z); rods[0].dims(w + th, th, th)
        rods[1].at((x0 + x1) / 2, y1, z); rods[1].dims(w + th, th, th)
        rods[2].at(x0, (y0 + y1) / 2, z); rods[2].dims(th, h + th, th)
        rods[3].at(x1, (y0 + y1) / 2, z); rods[3].dims(th, h + th, th)
    }

    // ---------------------------------------------------------------- the fit (zoom) of each station

    private fun fitScale(st: Int): Float = when (st) {
        0 -> H1 / (aM + bM)
        1 -> min(H2 / aM, (W2 - 0.06f) / (aM + 2 * bM))
        2 -> L3 / (aM + bM)
        else -> min(U4_MAX, H4 / n4)
    }
    /** x of the figure's bottom-left corner, centred on the stage ([parked] slides ① left for the parked pieces). */
    private fun fitOrigin(st: Int, parked: Float): Float = when (st) {
        0 -> lerp(XC - H1 / 2, X1_PARKED, parked)
        1 -> XC - ((aM + 2 * bM) * fitScale(1) + 0.06f) / 2
        3 -> XC - n4 * fitScale(3) / 2
        else -> 0f
    }

    // ---------------------------------------------------------------- per frame

    override fun update(dt: Float) {
        val k = if (dt > 0f) 1f - exp(-8f * dt) else 0f
        fun ap(v: Float, target: Float): Float { val nv = v + (target - v) * k; return if (abs(target - nv) < 1e-4f) target else nv }
        e1 = ap(e1, if (explode1) 1f else 0f)
        for (i in 0..1) abV[i] = ap(abV[i], if (abOut[i]) 1f else 0f)
        cutV = ap(cutV, if (stage2 >= 1) 1f else 0f)
        if (!drag2) m2 = ap(m2, if (stage2 >= 2) 1f else 0f)
        e3 = ap(e3, if (explode3) 1f else 0f)
        for (i in 0 until 8) liftV[i] = ap(liftV[i], if (lifted == i) 1f else 0f)
        e4 = ap(e4, if (peel4) 1f else 0f)
        // zoom: ease every station toward its fit, except while a knob is held (then the scale stays put)
        if (!knobHeld) for (st in 0..3) {
            sc[st] = ap(sc[st], fitScale(st))
            ox[st] = ap(ox[st], fitOrigin(st, sm(max(abV[0], abV[1]))))
        }
        groups[0].at(ox[0], Y0, Z0); groups[1].at(ox[1], Y0, Z0); groups[3].at(ox[3], Y0, Z0)

        for (s in 0 until 4) groups[s].show(s == station)
        for (s in 0 until 4) { formulas[s].show(s == station); plates[s].setMat(if (s == station) plateOn else plateOff) }
        when (station) { 0 -> layout1(); 1 -> layout2(); 2 -> layout3(); else -> layout4() }
        val gx = when (station) { 0 -> ox[0] + (aM + bM) * sc[0] / 2 + 0.08f; 1 -> ox[1] + (aM + bM) * sc[1] / 2 + 0.05f; 2 -> C3.x + 0.18f; else -> ox[3] + n4 * sc[3] / 2 }
        glow.at(gx, PLINTH_Y + 0.019f, if (station == 2) C3.z else Z0)
        liftTag.show(station == 2 && lifted >= 0 && liftV[lifted] > 0.6f)
        updateReadout()
        updateLabels()
    }

    private val aM get() = aT / 10f
    private val bM get() = bT / 10f

    private fun layout1() {
        val u = sc[0]; val a = aM * u; val b = bM * u; val L = a + b
        val g = sm(e1) * GAP1
        outline(frame1, -0.011f, -0.011f, L + 0.011f, L + 0.011f, -T / 2, 0.0035f)
        ghost1[0].at(a + b / 2 + g, a / 2 - g, -T * 0.2f); ghost1[0].dims(b, a, T * 0.5f)
        ghost1[1].at(a / 2 - g, a + b / 2 + g, -T * 0.2f); ghost1[1].dims(a, b, T * 0.5f)
        layoutPiece(p1[0], a / 2 - g, a / 2 - g, 0f, a, a, 0f)
        layoutPiece(p1[3], a + b / 2 + g, a + b / 2 + g, 0f, b, b, 0f)
        // the two ab pieces: in their slots, or parked to the right, standing side by side (ab = ba)
        val px1 = L + PARK1 + b / 2; val px2 = L + PARK1 + b + 0.035f + b / 2
        run {
            val t = sm(abV[0])
            layoutPiece(p1[1], lerp(a + b / 2 + g, px1, t), lerp(a / 2 - g, a / 2, t), 0.06f * sin(PI.toFloat() * t), b, a, 0f)
        }
        run {
            val t = sm(abV[1])
            layoutPiece(p1[2], lerp(a / 2 - g, px2, t), lerp(a + b / 2 + g, a / 2, t), 0.06f * sin(PI.toFloat() * t), a, b, -PI.toFloat() / 2 * t)
        }
        ruler1.grp.at(0f, RULER_Y, T / 2); layoutRuler(ruler1, a, b, u)
        side1[0].at(-0.045f, a / 2, T / 2); setDecal(side1[0], TX_A, 0.046f)
        side1[1].at(-0.045f, a + b / 2, T / 2); setDecal(side1[1], TX_B, 0.046f)
    }

    private fun layout2() {
        val u = sc[1]; val a = aM * u; val b = bM * u; val d = a - b
        val cut = sm(cutV)
        val m = if (drag2) m2.coerceIn(0f, 1f) else sm(m2)
        layoutPiece(p2[0], a / 2, d / 2, 0f, a, d, 0f)
        // the b² corner: cut off and parked up and to the right of where the strip will land
        layoutPiece(p2[2], lerp(a - b / 2, a + b + 0.05f + b / 2, cut), lerp(a - b / 2, a - b / 2 + 0.02f, cut), 0.05f * sin(PI.toFloat() * cut), b, b, 0f)
        // the strip swings a quarter turn clockwise about the pivot (a/2, a/2 - b) — Algebra.stripMotion
        val px = a / 2; val py = a / 2 - b
        val sx = d / 2 - px; val sy = a - b / 2 - py
        val th = -PI.toFloat() / 2 * m
        val cx = px + sx * cos(th) - sy * sin(th); val cy = py + sx * sin(th) + sy * cos(th)
        layoutPiece(p2[1], cx, cy, 0.05f * sin(PI.toFloat() * m), d, b, th)
        // guides: the original a² outline once the corner is gone, the target slot until it is filled
        ghostSq2.forEach { it.show(cutV > 0.02f) }
        outline(ghostSq2, 0f, 0f, a, a, -T / 2 - 0.002f, 0.003f)
        val slotOn = cutV > 0.3f && m2 < 0.97f
        slot2.show(slotOn); slotEdges2.forEach { it.show(slotOn) }
        slot2.at(a + b / 2, d / 2, -T * 0.2f); slot2.dims(b, d, T * 0.5f)
        outline(slotEdges2, a, 0f, a + b, d, T / 2, 0.0035f)
        cut2.show(cutV > 0.5f && m < 0.03f)
        cut2.at(d / 2, d, T / 2 + 0.003f); cut2.dims(d + 0.02f, 0.004f, 0.004f)
        ruler2.grp.at(0f, RULER_Y, T / 2); layoutRuler(ruler2, a, b, u)
        side2[0].at(-0.068f, d / 2, T / 2); setDecal(side2[0], TX_AMB, min(0.046f, 0.9f * d))
        side2[1].show(m < 0.5f); side2[1].at(-0.045f, a - b / 2, T / 2); setDecal(side2[1], TX_B, 0.046f)
    }

    private fun layout3() {
        val g = groups[2]
        g.at(C3.x, C3.y, C3.z); g.turn(cubeRot)
        val u = sc[2]; val a = aM * u; val b = bM * u; val h = (a + b) / 2
        val gap = sm(e3) * G3
        val lw = cubeRot.conj().rotate(floatArrayOf(LIFT3.x - C3.x, LIFT3.y - C3.y, LIFT3.z - C3.z))
        for (bl in blocks) {
            val lx = if (bl.i == 0) a else b; val ly = if (bl.j == 0) a else b; val lz = if (bl.k == 0) a else b
            fun ctr(bit: Int) = if (bit == 0) -h + a / 2 - gap else -h + a + b / 2 + gap
            val t = sm(liftV[bl.i + 2 * bl.j + 4 * bl.k])
            // on the way out the block also rises, so it clears its neighbours
            val bump = 0.10f * sin(PI.toFloat() * t)
            val up = cubeRot.conj().rotate(floatArrayOf(0f, bump, bump * 0.6f))
            bl.hub.at(lerp(ctr(bl.i), lw[0], t) + up[0], lerp(ctr(bl.j), lw[1], t) + up[1], lerp(ctr(bl.k), lw[2], t) + up[2])
            bl.body.dims(lx, ly, lz)
            bl.hub.grab(max(0.05f, 0.45f * min(lx, min(ly, lz))))
            var e = 0
            for (sy in intArrayOf(-1, 1)) for (sz in intArrayOf(-1, 1)) { bl.edges[e].at(0f, sy * ly / 2, sz * lz / 2); bl.edges[e].dims(lx + ER, ER, ER); e++ }
            for (sx in intArrayOf(-1, 1)) for (sz in intArrayOf(-1, 1)) { bl.edges[e].at(sx * lx / 2, 0f, sz * lz / 2); bl.edges[e].dims(ER, ly + ER, ER); e++ }
            for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) { bl.edges[e].at(sx * lx / 2, sy * ly / 2, 0f); bl.edges[e].dims(ER, ER, lz + ER); e++ }
            // term decals on the outward faces
            val tx = TX_A3 + bl.nb; val off = 0.0045f
            val fx = if (bl.i == 1) 1f else -1f; val fy = if (bl.j == 1) 1f else -1f; val fz = if (bl.k == 1) 1f else -1f
            bl.faces[0].at(fx * (lx / 2 + off), 0f, 0f); bl.faces[0].turn(Q.axisAngle(0f, 1f, 0f, fx * PI.toFloat() / 2)); setDecal(bl.faces[0], tx, fitEm(tx, lz, ly, 0.062f))
            bl.faces[1].at(0f, fy * (ly / 2 + off), 0f); bl.faces[1].turn(Q.axisAngle(1f, 0f, 0f, -fy * PI.toFloat() / 2)); setDecal(bl.faces[1], tx, fitEm(tx, lx, lz, 0.062f))
            bl.faces[2].at(0f, 0f, fz * (lz / 2 + off)); bl.faces[2].turn(if (fz > 0) Q.identity() else Q.axisAngle(0f, 1f, 0f, PI.toFloat())); setDecal(bl.faces[2], tx, fitEm(tx, lx, ly, 0.062f))
        }
        ruler3.grp.at(-h, -h - gap - 0.030f, h + gap + 0.030f); layoutRuler(ruler3, a, b, u)
        if (lifted >= 0) {
            val bl = blocks[lifted]
            val ly = (if (bl.j == 0) a else b); val lmax = max(if (bl.i == 0) a else b, max(ly, if (bl.k == 0) a else b))
            liftTag.setMat(tex[TX_A3 + bl.nb].mat)
            liftTag.at(LIFT3.x, LIFT3.y + 0.62f * lmax + 0.06f, LIFT3.z + 0.02f)
            setDecal(liftTag, TX_A3 + bl.nb, 0.066f)
        }
    }

    private fun layout4() {
        val u = sc[3]
        val g4 = PEEL4 / max(1, n4 - 1)
        val off = FloatArray(11) { k -> sm(e4) * max(0, k - 1) * g4 }
        for (idx in 0 until 100) {
            val c = idx % 10; val r = idx / 10
            val on = c < n4 && r < n4
            tiles[idx].show(on)
            if (on) { val o = off[max(c, r) + 1]; tiles[idx].at((c + 0.5f) * u + o, (r + 0.5f) * u + o, 0f); tiles[idx].dims(u * 0.90f, u * 0.90f, T) }
        }
        for (k in 1..10) {
            val tg = oddTags[k - 1]
            tg.show(k <= n4)
            if (k <= n4) {
                val o = off[k]; tg.at((k - 0.5f) * u + o, (k - 0.5f) * u + o, T / 2 + 0.004f)
                // digits are 0.72 em tall: fill ~60 % of the cell (>= 3 cm cap height up to n = 6)
                setDecal(tg, TX_ODD + k - 1, min(0.62f * 0.9f * u, 0.84f * 0.9f * u / tex[TX_ODD + k - 1].emW))
            }
        }
        val mid = n4 * u / 2 + off[(n4 + 1) / 2 + 1]
        stack4.at(mid, mid, T)
        ruler4.grp.at(0f, RULER_Y, T / 2); layoutRuler(ruler4, n4 * u, 0f, u)
    }

    // ---------------------------------------------------------------- readout + labels

    private fun f2(v: Long) = Algebra.fmt(v, 2)
    private fun f1(v: Long) = Algebra.fmt(v, 1)
    private fun f3(v: Long) = Algebra.fmt(v, 3)

    /** Line 1: both sides of the identity with the current numbers (exact), and whether they agree. */
    private fun sides(lhs: String, rhs: String, equal: Boolean) =
        tr("左边 $lhs   ·   右边 $rhs", "Left $lhs   ·   right $rhs") + if (equal) "   ✓" else "   ✗"

    private fun updateReadout() {
        val a = aT; val b = bT; val A = f1(a); val B = f1(b)
        readoutText = when (station) {
            0 -> {
                val (l, r) = Algebra.squareSides(a, b)
                sides("(a+b)² = ($A+$B)² = ${f2(l)}", "a² + 2ab + b² = ${f2(a * a)} + ${f2(2 * a * b)} + ${f2(b * b)} = ${f2(r)}", l == r) + "\n" +
                    tr("蓝 a² = $A×$A   ·   绿 ab = $A×$B（两块）   ·   黄 b² = $B×$B",
                        "blue a² = $A×$A   ·   green ab = $A×$B (two of them)   ·   amber b² = $B×$B") + "\n" +
                    if (abOut[0] || abOut[1]) tr("a² + b² = ${f2(a * a + b * b)} 只占两个角；空出的正是 ab + ab = ${f2(2 * a * b)}",
                        "a² + b² = ${f2(a * a + b * b)} fills only two corners; the holes are exactly ab + ab = ${f2(2 * a * b)}")
                    else tr("为什么不是 a² + b²？大正方形里还有两块 a×b：多出的 2ab = ${f2(2 * a * b)}",
                        "Why not a² + b²? The square also holds two a×b rectangles: 2ab = ${f2(2 * a * b)} more")
            }
            1 -> {
                val (l, r) = Algebra.diffSides(a, b)
                sides("(a+b)(a−b) = ${f1(a + b)} × ${f1(a - b)} = ${f2(l)}", "a² − b² = ${f2(a * a)} − ${f2(b * b)} = ${f2(r)}", l == r) + "\n" +
                    tr("蓝 a² 剪掉黄角 b² → L 形；L 的上条 b×(a−b) 转 90° 接到右边 → (a+b)×(a−b) = ${f1(a + b)}×${f1(a - b)}",
                        "blue a² minus the amber b² corner → an L; its top strip b×(a−b) turns 90° → (a+b)×(a−b) = ${f1(a + b)}×${f1(a - b)}") + "\n" +
                    if (stage2 < 2) tr("为什么相等？剩下的 L 形 = a² − b²，剪一刀、转一下就拼成 (a+b) 宽、(a−b) 高的长方形",
                        "Why equal? The L left over is a² − b²; one cut and a turn rebuild it as (a+b) wide, (a−b) tall")
                    else tr("剪一刀、转 90°，面积一点没变：a² − b² 就是 (a+b)(a−b)",
                        "One cut, one quarter turn, not a scrap of area lost: a² − b² IS (a+b)(a−b)")
            }
            2 -> {
                val (l, r) = Algebra.cubeSides(a, b)
                val line2 = if (lifted >= 0) {
                    val bl = blocks[lifted]; val nb = bl.nb
                    val dims = (List(3 - nb) { A } + List(nb) { B }).joinToString("×")
                    var v = 1L; repeat(3 - nb) { v *= a }; repeat(nb) { v *= b }
                    tr("捏起的这块：${Algebra.cubeTerm(nb)} = $dims = ${f3(v)}（同类共 ${Algebra.c3(nb)} 块）   ·   已认出 ${seen.count { it }} / 8 块",
                        "This block: ${Algebra.cubeTerm(nb)} = $dims = ${f3(v)} (${Algebra.c3(nb)} like it)   ·   identified ${seen.count { it }} / 8")
                } else tr("蓝 a³ ×1   ·   青 a²b ×3（厚板）   ·   绿 ab² ×3（长条）   ·   黄 b³ ×1",
                    "blue a³ ×1   ·   teal a²b ×3 (slabs)   ·   green ab² ×3 (rods)   ·   amber b³ ×1")
                sides("(a+b)³ = ($A+$B)³ = ${f3(l)}", "a³ + 3a²b + 3ab² + b³ = ${f3(a * a * a)} + ${f3(3 * a * a * b)} + ${f3(3 * a * b * b)} + ${f3(b * b * b)} = ${f3(r)}", l == r) + "\n" +
                    line2 + "\n" +
                    tr("每条棱在 a 处切一刀 → 2×2×2 = 8 块；含 0、1、2、3 个 b 的块各有 1、3、3、1 块（杨辉三角）",
                        "Cut every edge at a → 2×2×2 = 8 blocks; 1, 3, 3, 1 of them hold 0, 1, 2, 3 factors of b (Pascal's row)")
            }
            else -> {
                val n = n4
                val sum = (1..n).joinToString(" + ") { "${2 * it - 1}" }
                "$sum = ${n * n} = $n²\n" +
                    tr("第 k 层 L 形 = 2k − 1 格：横 k 格 + 竖 k 格，拐角那格共用",
                        "Layer k is an L of 2k − 1 cells: k across plus k down, sharing the corner cell") + "\n" +
                    tr("每包一层 L，边长 +1：(n+1)² − n² = 2n + 1 —— 正是 ① 里 b = 1 时的 2ab + b²",
                        "Each L grows the side by 1: (n+1)² − n² = 2n + 1 — the 2ab + b² of ① with b = 1")
            }
        }
    }

    // Chips (SpatialUI on device): values at the lower left of each figure — clear of the board above and
    // of the lesson card's footprint (upper left, x < -0.74 m at the figures' depth) — and under parked pieces.
    private fun updateLabels() {
        labels.clear()
        val a = aT; val b = bT; val A = f1(a); val B = f1(b)
        when (station) {
            0 -> {
                val u = sc[0]; val L = (aM + bM) * u; val x = ox[0]
                labels += LabelSpec("a = $A   b = $B", P3(x - 0.15f, Y0 + RULER_Y, Z0 + 0.03f))
                labels += LabelSpec(if (abOut[0] && abOut[1]) tr("a² + b² = ${f2(a * a + b * b)}，不是 ${f2((a + b) * (a + b))}", "a² + b² = ${f2(a * a + b * b)}, not ${f2((a + b) * (a + b))}")
                    else "(a+b)² = ${f2((a + b) * (a + b))}", P3(x - 0.17f, Y0 + 0.10f, Z0 + 0.03f))
                if (abV[0] > 0.5f || abV[1] > 0.5f)
                    labels += LabelSpec("ab = $A × $B = ${f2(a * b)}", P3(x + L + PARK1 + bM * u + 0.02f, Y0 - 0.075f, Z0 + 0.04f))
            }
            1 -> {
                val u = sc[1]; val x = ox[1]
                labels += LabelSpec("a = $A   b = $B", P3(x - 0.15f, Y0 + RULER_Y, Z0 + 0.03f))
                labels += LabelSpec(if (stage2 >= 2) "(a+b)(a−b) = ${f1(a + b)} × ${f1(a - b)} = ${f2((a + b) * (a - b))}"
                    else "a² − b² = ${f2(a * a)} − ${f2(b * b)} = ${f2(a * a - b * b)}", P3(x - 0.19f, Y0 + 0.11f, Z0 + 0.03f))
                if (cutV > 0.5f) labels += LabelSpec("b² = ${f2(b * b)}", P3(x + (aM + 1.5f * bM) * u + 0.05f, Y0 + (aM - bM) * u - 0.03f, Z0 + 0.06f))
            }
            2 -> {
                val r = SoftRender.worldOf(ruler3.grp).t
                labels += LabelSpec("a = $A   b = $B", P3(r[0] - 0.12f, r[1] - 0.02f, r[2]))
                labels += LabelSpec("(a+b)³ = ${f3((a + b) * (a + b) * (a + b))}", P3(C3.x - 0.36f, C3.y + 0.02f, C3.z + 0.08f))
                if (lifted >= 0 && liftV[lifted] > 0.6f) {
                    val bl = blocks[lifted]
                    var v = 1L; repeat(3 - bl.nb) { v *= a }; repeat(bl.nb) { v *= b }
                    val dims = (List(3 - bl.nb) { A } + List(bl.nb) { B }).joinToString("·")
                    val u = sc[2]; val lmax = (if (bl.nb < 3) aM else bM) * u
                    labels += LabelSpec("${Algebra.cubeTerm(bl.nb)} = $dims = ${f3(v)}", P3(LIFT3.x, LIFT3.y - 0.62f * lmax - 0.05f, LIFT3.z + 0.04f))
                }
            }
            else -> {
                val x = ox[3]
                labels += LabelSpec("n = $n4", P3(x - 0.10f, Y0 + RULER_Y, Z0 + 0.03f))
                labels += LabelSpec("1 + 3 + … + ${2 * n4 - 1} = ${n4 * n4} = $n4²", P3(x - 0.22f, Y0 + 0.11f, Z0 + 0.03f))
            }
        }
    }

    // ---------------------------------------------------------------- interaction

    private val TOK_NAMES = listOf("Tok0", "Tok1", "Tok2", "Tok3")
    private fun stationTargets(s: Int): List<String> = when (s) {
        0 -> listOf("P1_ab1", "P1_ab2", "KnobA_1", "KnobB_1")
        1 -> listOf("P2_corner", "P2_strip", "KnobA_2", "KnobB_2")
        2 -> (0 until 8).map { "B3_$it" } + listOf("KnobA_3", "KnobB_3")
        else -> listOf("Stack4", "KnobN_4")
    }
    /** Every pinchable node of station [s] (and the four plaques), for tests. */
    fun allTargets(s: Int) = stationTargets(s) + TOK_NAMES

    override val tapTargets: List<TapTarget> get() {
        val t = ArrayList<TapTarget>()
        when (station) {
            0 -> {
                t += TapTarget("P1_ab1", "右 ab → 拿出/放回", "Right ab → out / in") { abOut[0] }
                t += TapTarget("P1_ab2", "上 ab → 拿出/放回", "Top ab → out / in") { abOut[1] }
            }
            1 -> {
                t += TapTarget("P2_corner", "b² 角 → 剪掉/放回", "b² corner → cut / back") { stage2 >= 1 }
                t += TapTarget("P2_strip", "细条 → 转过去/转回", "Strip → swing / back") { stage2 >= 2 }
            }
            2 -> for (n in 0 until 8) {
                val term = Algebra.cubeTerm(Integer.bitCount(n))
                t += TapTarget("B3_$n", "$term → 拿起", "$term → lift") { lifted == n }
            }
            else -> t += TapTarget("Stack4", "L 层 → 剥开/合拢", "L layers → peel / join") { peel4 }
        }
        when (station) {
            3 -> t += TapTarget("KnobN_4", "n 把手 → +1", "n knob → +1")
            else -> {
                val s = station + 1
                t += TapTarget("KnobA_$s", "a 把手 → +0.5", "a knob → +0.5")
                t += TapTarget("KnobB_$s", "b 把手 → +0.5", "b knob → +0.5")
            }
        }
        t += TapTarget("Tok0", "① 站", "① station", enabled = { station != 0 }, why = Bi("已经在这一站：① 站", "Already on this station: ① station")) { station == 0 }
        t += TapTarget("Tok1", "② 站", "② station", enabled = { station != 1 }, why = Bi("已经在这一站：② 站", "Already on this station: ② station")) { station == 1 }
        t += TapTarget("Tok2", "③ 站", "③ station", enabled = { station != 2 }, why = Bi("已经在这一站：③ 站", "Already on this station: ③ station")) { station == 2 }
        t += TapTarget("Tok3", "④ 站", "④ station", enabled = { station != 3 }, why = Bi("已经在这一站：④ 站", "Already on this station: ④ station")) { station == 3 }
        return t
    }

    override fun isTarget(name: String) = name in stationTargets(station) || name in TOK_NAMES

    override fun onTap(name: String) {
        when {
            name.startsWith("Tok") -> name.removePrefix("Tok").toIntOrNull()?.let { selectStation(it) }
            name.startsWith("KnobA_") -> stepA()
            name.startsWith("KnobB_") -> stepB()
            name == "KnobN_4" -> setN(if (n4 >= Algebra.N_MAX) Algebra.N_MIN else n4 + 1)
            name == "P1_ab1" -> abOut[0] = !abOut[0]
            name == "P1_ab2" -> abOut[1] = !abOut[1]
            name == "P2_corner" -> stage2 = if (stage2 == 0) 1 else 0
            name == "P2_strip" -> stage2 = if (stage2 < 2) 2 else 1
            name.startsWith("B3_") -> name.removePrefix("B3_").toIntOrNull()?.let { liftBlock(it) }
            name == "Stack4" -> peel4 = !peel4
        }
    }

    override fun tapMessage(name: String): String? {
        val A = f1(aT); val B = f1(bT)
        return when {
            name.startsWith("Tok") -> when (station) {
                0 -> tr("① (a+b)² = a² + 2ab + b²：拿出两块 ab 看看", "① (a+b)² = a² + 2ab + b²: take the two ab pieces out")
                1 -> tr("② (a+b)(a−b) = a² − b²：剪掉角，转动细条", "② (a+b)(a−b) = a² − b²: cut the corner, swing the strip")
                2 -> tr("③ (a+b)³：拖动旋转，捏起一块认一认", "③ (a+b)³: drag to turn it, pinch a block to lift it")
                else -> tr("④ 1 + 3 + 5 + … = n²：捏方块剥开 L 层", "④ 1 + 3 + 5 + … = n²: pinch the square to peel the L layers")
            }
            name.startsWith("KnobA_") -> tr("a → $A（按住拖动可连续调）", "a → $A (pinch and drag to slide)")
            name.startsWith("KnobB_") -> tr("b → $B（按住拖动可连续调）", "b → $B (pinch and drag to slide)")
            name == "KnobN_4" -> tr("n → $n4：${n4 * n4} 格 = $n4 层 L", "n → $n4: ${n4 * n4} cells = $n4 L layers")
            name == "P1_ab1" || name == "P1_ab2" -> {
                val out = abOut[if (name == "P1_ab1") 0 else 1]
                if (out) tr("拿出 ab = $A×$B = ${f2(aT * bT)}：大正方形空了一块", "ab = $A×$B = ${f2(aT * bT)} taken out: a hole in the square")
                else tr("ab 放回：空缺又补上了", "ab back in: the hole is filled again")
            }
            name == "P2_corner" -> if (stage2 >= 1) tr("剪掉 b² = ${f2(bT * bT)}：剩下的 L 形 = a² − b² = ${f2(aT * aT - bT * bT)}", "b² = ${f2(bT * bT)} cut away: the L left is a² − b² = ${f2(aT * aT - bT * bT)}")
                else tr("b² 角放回：又是完整的 a²", "b² corner back: a whole a² again")
            name == "P2_strip" -> if (stage2 >= 2) tr("细条转 90° 接到右边：(a+b)×(a−b) = ${f1(aT + bT)}×${f1(aT - bT)}", "The strip swings into place: (a+b)×(a−b) = ${f1(aT + bT)}×${f1(aT - bT)}")
                else tr("细条转回原处", "The strip swings back")
            name.startsWith("B3_") -> {
                val n = name.removePrefix("B3_").toIntOrNull() ?: return null
                val term = Algebra.cubeTerm(blocks[n].nb)
                if (lifted == n) tr("$term：同类共 ${Algebra.c3(blocks[n].nb)} 块 · 已认出 ${seen.count { it }}/8", "$term: ${Algebra.c3(blocks[n].nb)} like it · identified ${seen.count { it }}/8")
                else tr("$term 放回立方体", "$term back in the cube")
            }
            name == "Stack4" -> if (peel4) tr("剥开 $n4 层：1, 3, 5, … , ${2 * n4 - 1}", "Peeled into $n4 layers: 1, 3, 5, … , ${2 * n4 - 1}")
                else tr("合拢：拼回 $n4 × $n4 正方形", "Joined: back to the $n4 × $n4 square")
            else -> null
        }
    }

    // ---- drag: knobs slide a, b, n along their ruler; the strip follows the hand; blocks turn the cube
    override fun isDragTarget(name: String) = when (station) {
        0 -> name == "KnobA_1" || name == "KnobB_1"
        1 -> name == "KnobA_2" || name == "KnobB_2" || name == "P2_strip"
        2 -> name == "KnobA_3" || name == "KnobB_3" || name.startsWith("B3_")
        else -> name == "KnobN_4"
    }

    override fun onDragStart(name: String) {
        dragAcc = 0f; dragA0 = aT; dragB0 = bT; dragN0 = n4
        if (name.startsWith("Knob")) knobHeld = true
        if (name == "P2_strip") { if (stage2 == 0) stage2 = 1; drag2 = true }
    }

    override fun onDrag(name: String, dx: Float, dy: Float, dz: Float) {
        when {
            name.startsWith("Knob") -> {
                val dir = if (station == 2) cubeRot.rotate(floatArrayOf(1f, 0f, 0f)) else floatArrayOf(1f, 0f, 0f)
                dragAcc += dx * dir[0] + dy * dir[1] + dz * dir[2]
                val unit = sc[station]
                when {
                    name == "KnobN_4" -> setN(dragN0 + (dragAcc / unit).roundToInt())
                    name.startsWith("KnobA_") -> setAB(dragA0 + (dragAcc / unit * 10f).roundToLong(), bT, keepA = false)
                    else -> setAB(aT, dragB0 + (dragAcc / unit * 10f).roundToLong(), keepA = true)
                }
            }
            name == "P2_strip" -> {
                // progress along the chord from the strip's start to its slot (metres -> 0..1)
                val a = aM * sc[1]; val b = bM * sc[1]
                val ddx = a / 2 + b; val ddy = -a / 2
                m2 = (m2 + (dx * ddx + dy * ddy) / (ddx * ddx + ddy * ddy)).coerceIn(0f, 1f)
            }
            name.startsWith("B3_") -> {
                cubeRot = (Q.axisAngle(0f, 1f, 0f, dx * DRAG_TURN) * Q.axisAngle(1f, 0f, 0f, -dy * DRAG_TURN) * cubeRot).let {
                    val l = sqrt(it.x * it.x + it.y * it.y + it.z * it.z + it.w * it.w); Q(it.x / l, it.y / l, it.z / l, it.w / l)
                }
            }
        }
    }

    override fun onDragEnd(name: String) {
        knobHeld = false
        if (name == "P2_strip") { drag2 = false; stage2 = if (m2 > 0.5f) 2 else 1 }
    }

    // ---- actions (also what the panel, the lesson autoplay and the tests call)
    fun selectStation(s: Int) { station = s.coerceIn(0, 3); if (station != 1) drag2 = false }
    fun setAB(a: Long, b: Long, keepA: Boolean) { val (aa, bb) = Algebra.clamp(a, b, keepA); aT = aa; bT = bb }
    fun stepA() { setAB(if (aT + 5 > Algebra.A_MAX) Algebra.A_MIN else aT + 5, bT, keepA = true) }
    fun stepB() { val nb = bT + 5; setAB(aT, if (nb > minOf(Algebra.B_MAX, aT - Algebra.GAP)) Algebra.B_MIN else nb, keepA = true) }
    fun setN(n: Int) { n4 = n.coerceIn(Algebra.N_MIN, Algebra.N_MAX) }
    fun liftBlock(k: Int) { if (lifted == k) lifted = -1 else { lifted = k; seen[k] = true } }
    fun resetAll() {
        aT = 30L; bT = 20L; n4 = 5; explode1 = false; abOut.fill(false); stage2 = 0; drag2 = false
        explode3 = false; lifted = -1; peel4 = false; cubeRot = CUBE_ROT0
    }

    // ---------------------------------------------------------------- guided track (导学)

    private fun bothOut() = abOut[0] && abOut[1] && abV[0] > 0.95f && abV[1] > 0.95f
    private fun bothIn() = !abOut[0] && !abOut[1] && abV[0] < 0.05f && abV[1] < 0.05f

    // ---- background card (📖): context for a viewer who has never met this topic
    override val about = About(listOf(
        About.Page(
            Bi("这个实验在做什么？", "What is this experiment?"),
            Bi("四个“拼图站”（右侧四块小牌切换）：① 边长 a+b 的正方形；② 剪一刀再转一下的 L 形；③ 切成 8 块的立方体；④ 一圈圈 L 形拼成的方阵。捏起小块可以拿出、放回，拖动尺子上的把手改变 a、b、n。头顶的黑板写着对应的公式，两边的读数始终相等。", "Four puzzle stations (switch with the four plaques on the right): 1) a square of side a + b; 2) an L-shape you cut and swing; 3) a cube cut into 8 blocks; 4) a square built from L-shaped layers. Pinch pieces to lift them out or put them back, and drag the knobs on the rulers to change a, b and n. The board overhead shows the formula; both sides of the readout always agree."),
        ),
        About.Page(
            Bi("代数以前的代数", "Algebra before letters"),
            Bi("用字母写公式其实很晚：16 世纪末的韦达、1637 年的笛卡儿才把它定型。更早的人用图形做代数。欧几里得《几何原本》第二卷（约公元前 300 年）就是靠切拼正方形和长方形来证明这些等式；约公元 820 年，花拉子米解二次方程时，真的是画一个正方形去“配方”。", "Writing formulas with letters is recent: Viete in the late 1500s and Descartes in 1637 made it standard. Before that, people did algebra with shapes. Book II of Euclid's Elements (about 300 BC) proves these identities by cutting and rearranging squares and rectangles, and around 820 AD al-Khwarizmi solved quadratic equations by literally drawing a square and 'completing' it."),
        ),
        About.Page(
            Bi("核心：乘法就是面积", "Key idea: multiplying is area"),
            Bi("a × b 就是长 a、宽 b 的长方形的面积；a × a × a 就是立方体的体积。一个恒等式说的其实是：同一堆碎片换一种摆法，总面积不变。就像一块蛋糕，不管切成几块、怎么挪，蛋糕总量都一样。所以只要拼法对了，a、b 取什么数都成立。", "a × b is the area of a rectangle a long and b wide; a × a × a is the volume of a cube. An identity simply says: the same pile of pieces, arranged another way, has the same total area. Like a cake: however you cut it and shuffle the slices, there is the same amount of cake. So once the dissection works, it works for every a and b."),
        ),
        About.Page(
            Bi("怎么看这个 3D 场景", "How to read the 3-D scene"),
            Bi("颜色就是符号：蓝色 = a 做成的块（a²、a³），琥珀黄 = b（b²、b³），绿色长方形 = ab。立方体里青色 = a²b，黄绿 = ab²。② 站的浅蓝细条是要转过去的那一条。④ 站每一圈彩色 L = 一个奇数 1、3、5……。底边的尺子 = 长度，上面的小球把手改 a、b、n。黑板上的字也用同样的颜色。", "Colour is the symbol: blue = pieces made of a (a², a³), amber = b (b², b³), green rectangles = ab. In the cube, teal = a²b and lime = ab². At station 2 the light-blue strip is the one that swings. At station 4 each coloured L-layer = one odd number 1, 3, 5 ... The ruler along the bottom edge is length; its ball knobs set a, b, n. The board's formula uses the same colours."),
        ),
        About.Page(
            Bi("最常见的错", "The classic slip"),
            Bi("很多人以为 (a+b)² = a² + b²。取 a = 3、b = 2：左边 5² = 25，右边 9 + 4 = 13，差了 12 —— 正好是两块 3×2 的绿色长方形。在 ① 站把它们拿出来，空洞一眼就能看到。立方体也一样：8 块按 1、3、3、1 分成四类，这串数正是杨辉三角的一行。", "Many people think (a+b)² = a² + b². Try a = 3, b = 2: the left is 5² = 25, the right is 9 + 4 = 13. The missing 12 is exactly two green 3 × 2 rectangles; lift them out at station 1 and you see the holes. The cube works the same way: its 8 blocks fall into groups of 1, 3, 3, 1, a row of Pascal's triangle."),
        ),
        About.Page(
            Bi("今天在哪里？", "Where is it today?"),
            Bi("心算：21² = 400 + 40 + 1 = 441；51 × 49 = 50² − 1² = 2499。量房子：正方形房间边长各加 10%，面积多出 21% 而不是 20% —— 多的 1% 就是角上那块小 b²。抛硬币算概率、把 (x+y)ⁿ 展开，用的都是 1、3、3、1 这串数。", "Mental maths: 21² = 400 + 40 + 1 = 441, and 51 × 49 = 50² − 1² = 2499. Floor plans: make a square room 10% wider each way and its area grows by 21%, not 20%; the extra 1% is the little b² corner. Coin-toss probabilities and expanding (x + y)ⁿ use the same 1, 3, 3, 1 pattern."),
        ),
    ))

    override val lesson: Lesson by lazy {
        Lesson(
            listOf(
                LessonStep(
                    say = Bi("① 站：大正方形边长 a + b。你猜 (a+b)² 等于 a² + b² 吗？", "Station ①: a square of side a + b. Is (a+b)² equal to a² + b²?"),
                    options = listOf(Bi("相等", "Equal"), Bi("(a+b)² 更大", "(a+b)² is bigger"), Bi("要看 a、b 取多少", "Depends on a and b")),
                    answer = 1,
                ),
                LessonStep(
                    say = Bi("捏住两块绿色 ab 长方形，把它们拿出来。剩下的 a² + b² 能填满大正方形吗？", "Pinch the two green ab rectangles to take them out. Do a² + b² alone fill the big square?"),
                    target = "P1_ab1",
                    onEnter = { if (station != 0) selectStation(0) },
                    done = { bothOut() },
                    autoplay = { selectStation(0); if (!abOut[0]) onTap("P1_ab1"); if (!abOut[1]) onTap("P1_ab2") },
                ),
                LessonStep(
                    say = Bi("大正方形空了两块。把两块 ab 放回去，看它们是否正好补满。", "Two holes are left. Put both ab pieces back — do they fill the holes exactly?"),
                    target = "P1_ab2",
                    done = { bothIn() },
                    autoplay = { if (abOut[0]) onTap("P1_ab1"); if (abOut[1]) onTap("P1_ab2") },
                    reveal = true,
                    insight = Bi("a² + b² 只占两个角，缺的正是两块 a×b：(a+b)² = a² + 2ab + b²，比 a² + b² 多 2ab。", "a² + b² only fills two corners; the gap is two a×b rectangles: (a+b)² = a² + 2ab + b², 2ab more than a² + b²."),
                ),
                LessonStep(
                    say = Bi("拖动尺子上的 a 或 b 把手（或捏一下）：拼图跟着变，看读数两边是否总相等。", "Drag the a or b knob on the ruler (or pinch it): the pieces resize — do both sides of the readout stay equal?"),
                    target = "KnobA_1",
                    onEnter = { stepA0 = aT; stepB0 = bT },
                    done = { abs(aT - stepA0) >= 5 || abs(bT - stepB0) >= 5 },
                    autoplay = { onTap("KnobA_1") },
                    insight = Bi("数字变了，拼法没变——所以它对任何 a、b 都成立，这就叫“恒等式”。", "The numbers change, the dissection does not — so it holds for every a and b: that is what an identity means."),
                ),
                LessonStep(
                    say = Bi("② 站：捏黄色角块 b² 剪掉，再把蓝色细条拖（或捏）进右下角的虚线框。", "Station ②: pinch the amber b² corner to cut it off, then drag (or pinch) the blue strip into the dashed slot."),
                    target = "P2_strip",
                    onEnter = { if (station != 1) selectStation(1) },
                    done = { station == 1 && stage2 == 2 && m2 > 0.97f },
                    autoplay = { selectStation(1); onTap("P2_strip") },
                    insight = Bi("剪一刀、转 90°：a² − b² 的 L 形变成 (a+b) 宽、(a−b) 高的长方形，面积一点没少。", "One cut, a quarter turn: the a² − b² L becomes an (a+b)-wide, (a−b)-tall rectangle, without losing any area."),
                ),
            ),
            challenge = Challenge(
                say = Bi("③ 站（右侧第三块牌）：把 (a+b)³ 的 8 块逐一捏起来认一认——a³、a²b、ab²、b³ 各几块？", "Station ③ (third plaque on the right): pinch each of the 8 blocks of (a+b)³ — how many a³, a²b, ab², b³?"),
                progress = {
                    val c = IntArray(4); val t = IntArray(4)
                    for (k in 0 until 8) { val nb = (k and 1) + ((k shr 1) and 1) + ((k shr 2) and 1); t[nb]++; if (seen[k]) c[nb]++ }
                    tr("已认出 %d / 8   ·   a³ %d/%d   a²b %d/%d   ab² %d/%d   b³ %d/%d", "Identified %d / 8   ·   a³ %d/%d   a²b %d/%d   ab² %d/%d   b³ %d/%d")
                        .format(seen.count { it }, c[0], t[0], c[1], t[1], c[2], t[2], c[3], t[3])
                },
                done = { seen.all { it } },
                autoplay = { selectStation(2); explode3 = true; for (k in 0 until 8) if (!seen[k]) onTap("B3_$k") },
            ),
        )
    }

    // ---------------------------------------------------------------- review states

    override fun reviewVariants(): Map<String, () -> Unit> = mapOf(
        "st1_out" to { selectStation(0); abOut[0] = true; abOut[1] = true },
        "st2_cut" to { selectStation(1); stage2 = 1 },
        "st2_moved" to { selectStation(1); stage2 = 2 },
        "st3" to { selectStation(2); explode3 = true; lifted = -1 },
        "st3_lift" to { selectStation(2); explode3 = true; liftBlock(1) },
        "st4" to { selectStation(3); setN(6); peel4 = true },
    )

    // ---------------------------------------------------------------- panel

    private val STATION_TABS = listOf("① (a+b)²", "② (a+b)(a−b)", "③ (a+b)³", "④ 1+3+5+…")

    @Composable
    override fun Panel() {
        val st by FieldRef(::station)
        LabPanel(readout) {
            LabRow {
                SegmentControl {
                    STATION_TABS.forEachIndexed { k, t -> SegmentItem(selected = st == k, onClick = { selectStation(k) }, title = { Text(t) }) }
                }
                Spacer(Modifier.width(16.dp))
                when (st) {
                    0 -> LabSwitch(tr("拆开", "Explode"), explode1) { explode1 = it }
                    1 -> Button(onClick = { stage2 = (stage2 + 1) % 3 }) {
                        Text(when (stage2) { 0 -> tr("✂ 剪掉 b²", "✂ Cut b²"); 1 -> tr("↻ 转动细条", "↻ Swing strip"); else -> tr("↺ 复原", "↺ Undo") })
                    }
                    2 -> LabSwitch(tr("拆开", "Explode"), explode3) { explode3 = it }
                    else -> LabSwitch(tr("剥开 L 层", "Peel layers"), peel4) { peel4 = it }
                }
                Button(onClick = { resetAll() }) { Text(tr("重置", "Reset")) }
            }
            LabRow {
                if (st == 3) LabSlider("n", n4.toFloat(), Algebra.N_MIN.toFloat()..Algebra.N_MAX.toFloat(), "$n4") { setN(it.roundToInt()) }
                else {
                    LabSlider("a", aT / 10f, Algebra.A_MIN / 10f..Algebra.A_MAX / 10f, f1(aT)) { setAB((it * 10f).roundToLong(), bT, keepA = false) }
                    LabSlider("b", bT / 10f, Algebra.B_MIN / 10f..Algebra.B_MAX / 10f, f1(bT)) { setAB(aT, (it * 10f).roundToLong(), keepA = true) }
                }
            }
        }
    }
}
