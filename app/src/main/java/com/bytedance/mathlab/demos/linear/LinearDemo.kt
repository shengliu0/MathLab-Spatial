// 线性变换与行列式 · Linear Transformations and the Determinant (Cayley 1858; presentation after
// 3Blue1Brown's "Essence of Linear Algebra").
//
// LESSON: a 3x3 matrix is just "where î, ĵ, k̂ land" — and the whole of space follows. The user pinch-
// drags the TIP of a basis arrow (red î, green ĵ, blue k̂); the matrix columns are those tips, and the
// lattice, the gold unit cube and an "F" (to show orientation) all move with it.
//   * det = the signed volume of the transformed unit cube (number above the cube, the cube itself);
//     det < 0 -> the F is mirrored and the cube turns pink; det -> 0 -> space collapses onto a glowing
//     plane (or line).
//   * eigen-directions: warm white glowing lines through the origin that only stretch (Av = λv); with
//     the straight-line animation from the identity they stay on their span the WHOLE time while
//     everything else turns. A violet probe v (with Av and its x î′ + y ĵ′ + z k̂′ recipe) lets the
//     user hunt for one.
//   * presets on a shelf of mini-cubes (each shows its own transform): identity, 90° turn, shear,
//     scale, projection (det 0), reflection, random, and compose "A then B = BA ≠ AB".
//
// Everything moves by NODE TRANSFORMS: lattice lines / edges / arrows are shared unit meshes placed by
// their transformed end points; the solids (cube, F, minis) hang under an SVD chain R(U)·S(σ)·R(Vᵀ) = M.
// No mesh is rebuilt per frame. Maths + tests: core/linear (LinearTest).

package com.bytedance.mathlab.demos.linear

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bytedance.mathlab.core.P3
import com.bytedance.mathlab.core.linear.CubicRoots
import com.bytedance.mathlab.core.linear.EigenSpace
import com.bytedance.mathlab.core.linear.Linear
import com.bytedance.mathlab.core.linear.LinearPresets
import com.bytedance.mathlab.core.linear.Mat3
import com.bytedance.mathlab.core.linear.Vec3
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
import com.bytedance.mathlab.lab.spec.rgb
import com.bytedance.mathlab.lab.tr
import com.bytedance.mathlab.lab.ui.Button
import com.bytedance.mathlab.lab.ui.FieldRef
import com.bytedance.mathlab.lab.ui.LabPanel
import com.bytedance.mathlab.lab.ui.LabRow
import com.bytedance.mathlab.lab.ui.LabSwitch
import com.bytedance.mathlab.lab.ui.SegmentControl
import com.bytedance.mathlab.lab.ui.SegmentItem
import com.bytedance.mathlab.render.MeshData
import com.bytedance.mathlab.render.MeshGen
import com.bytedance.mathlab.render.ProcTex
import com.bytedance.mathlab.render.Rgb
import com.pico.spatial.ui.design.Text
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object LinearDemo : SpecDemo() {
    override val id = "linear"
    override val title = "线性变换与行列式"  // i18n-ok (zh half of DemoRegistry.titleOf; English in TITLE_EN)
    override val credit = "Cayley · 1858"

    enum class Preset { IDENTITY, ROTATE, SHEAR, SCALE, PROJECT, REFLECT, RANDOM, COMPOSE, WELCOME, CUSTOM }
    private val SHELF = listOf(Preset.IDENTITY, Preset.ROTATE, Preset.SHEAR, Preset.SCALE,
        Preset.PROJECT, Preset.REFLECT, Preset.RANDOM, Preset.COMPOSE)

    // ---------------------------------------------------------------- presentation
    /** Metres per math unit: the unit cube is 26 cm, 6 deg at 2.5 m. */
    private const val S = 0.26f
    /** Where the math origin sits in the volume (lattice spans y -0.22 ... 0.42 m at the identity). */
    private val C = P3(-0.03f, 0.04f, 0f)
    /**
     * Math (x, y, z-up, right-handed) -> volume. î points to the viewer's front-left, ĵ front-right, k̂ up;
     * then pitched 22 deg so the floor (xy-plane) is seen from above, as in a textbook 3-D figure.
     */
    private val VIEW: Mat3 = run {
        val yaw = Math.toRadians(38.0); val pitch = Math.toRadians(22.0)
        val b = Mat3.fromCols(Vec3(-sin(yaw), 0.0, cos(yaw)), Vec3(cos(yaw), 0.0, sin(yaw)), Vec3.Y)
        Mat3.rotation(Vec3.X, pitch) * b
    }
    private val VIEW_T = VIEW.transpose()
    private val VIEW_Q = qOf(VIEW)
    /** Mirror used for the orientation-reversing mesh variants (see LinearMeshes). */
    private val F0 = Mat3.diag(-1.0, 1.0, 1.0)
    /** Smallest node scale on a squashed axis (units): 1 mm thick, never 0. */
    private const val MIN_S = 0.006

    private val SPEEDS = floatArrayOf(0.5f, 1f, 2f)
    private val SPEED_LABELS get() = listOf(tr("慢 ×0.5", "Slow ×0.5"), tr("中 ×1", "Medium ×1"), tr("快 ×2", "Fast ×2"))

    // radii in math units (metres / S)
    private val R_LAT = 0.0011f / S
    private val R_AXIS = 0.0020f / S
    private val R_SHAFT = 0.0045f / S
    private val R_EDGE = 0.0030f / S
    private val R_GHOST = 0.0012f / S
    private val R_ECORE = 0.0013f / S
    private val R_EGLOW = 0.0055f / S
    private val R_TIP = 0.016f / S
    private val R_PROBE = 0.012f / S
    private val HEAD_LEN = 0.036f / S
    private val HEAD_R = 0.012f / S
    private const val EIGEN_L = 1.6

    private val HAT = listOf("î", "ĵ", "k̂")
    private val UNIT_STR = listOf("(1, 0, 0)", "(0, 1, 0)", "(0, 0, 1)")
    private val TIP_NAMES = listOf("TipI", "TipJ", "TipK")
    /** Lattice coordinates: x, y in [-1, 1], z (up) in [-1/2, 1] — the cube stands on the floor z = 0. */
    private val COORDS = doubleArrayOf(-1.0, -0.5, 0.0, 0.5, 1.0)
    private val ZCOORDS = doubleArrayOf(-0.5, 0.0, 0.5, 1.0)
    private val CUBE_EDGES: List<Pair<Int, Int>> = buildList {
        for (i in 1 until 8) for (bit in intArrayOf(1, 2, 4)) if (i and bit == 0) add(i to (i or bit))
    }   // the 9 cube edges that do NOT start at the origin (those 3 are the basis arrows)

    // colours (sRGB), 3Blue1Brown family
    private val RED = rgb(0xFC6255); private val GREEN = rgb(0x83C167); private val BLUE = rgb(0x58C4DD)
    private val BASIS = listOf(RED, GREEN, BLUE)
    private val GOLD = rgb(0xFFD54A); private val PINK = rgb(0xFF5FCB); private val EIGEN = rgb(0xFFF1C8)
    private val VIOLET = rgb(0xA98BFF); private val IVORY = rgb(0xF2EEE4); private val WHITE = Rgb(1f, 1f, 1f)

    // ---------------------------------------------------------------- state
    var preset = Preset.WELCOME
    var speedIdx = 1
    var showEigen = true
    var showProbe = true
    var composeBA = true
    var randomSeed = 0
    private var shown = Mat3.identity()
    private var applied: Mat3? = null
    private var appliedFlags = -1
    private class Stage(val from: Mat3, val to: Mat3, val dur: Float, val delay: Float)
    private val stages = ArrayDeque<Stage>()
    private var stageT = 0f
    private var dragCol = -1
    private var dragRaw = Vec3.ZERO
    private var draggedJ = false
    private var detAtStep = 1.0
    private val PROBE0 = Vec3(-0.5, 0.6, 0.75)
    private var probe = PROBE0
    private var clock = 0f
    private var shelfKey = -1

    // eigen view of the shown matrix (recomputed when it changes)
    private class ELine(val dir: Vec3, val lambda: Double, val plane: Boolean, val labelled: Boolean)
    private var eLines: List<ELine> = emptyList()
    private var eSpaces: List<EigenSpace> = emptyList()
    private var eComplex: CubicRoots? = null
    private var eScalar = false
    private var rank = 3
    private var imgU = Mat3.identity()

    // ---------------------------------------------------------------- nodes
    private class Seg(val node: Node, val a: Vec3, val b: Vec3, val r: Float)
    private class Chain(val outer: Node, val inner: Node) { val parts = ArrayList<Triple<Node, MeshData, MeshData>>() }
    private val lattice = ArrayList<Seg>()
    private val cubeEdges = ArrayList<Seg>()
    private lateinit var space: Node
    private lateinit var shafts: Array<Node>
    private lateinit var heads: Array<Node>
    private lateinit var tips: Array<Node>
    private lateinit var tipGlows: Array<Node>
    private lateinit var solid: Chain
    private lateinit var ghost: Chain
    private lateinit var cubeFaces: Node
    private lateinit var eigCore: Array<Node>
    private lateinit var eigGlow: Array<Node>
    private lateinit var imgPlane: Node
    private lateinit var imgLine: Node
    private lateinit var probeNodes: Array<Node>     // span, v shaft, v head, Av shaft, Av head, tip
    private lateinit var recipe: Array<Node>
    private lateinit var pads: Array<Node>
    private lateinit var minis: Array<Chain>
    private lateinit var under: Array<Node>

    // ---------------------------------------------------------------- meshes + materials
    private val shadeTex by lazy { LinearMeshes.shadeTex() }
    private val rgbTex by lazy { LinearMeshes.paletteTex(BASIS.map { floatArrayOf(it.r, it.g, it.b) }) }
    private val unitCyl by lazy { MeshGen.cylinder(1f, 1f, 10) }
    private val unitBox by lazy { MeshGen.box(1f, 1f, 1f) }
    private val sphere by lazy { MeshGen.sphere(1f, 12, 24, MeshGen.SphereUv.EQUIRECT) }
    private val cone by lazy { LinearMeshes.cone(18) }
    private val quad by lazy { LinearMeshes.quad(1.45f) }
    private val cubeMesh by lazy { LinearMeshes.shadedBoxes(listOf(floatArrayOf(0f, 0f, 0f, 1f, 1f, 1f))) }
    private val cubeMir by lazy { LinearMeshes.mirrored(cubeMesh) }
    private val miniCube by lazy { LinearMeshes.shadedBoxes(listOf(floatArrayOf(-0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f))) }
    private val miniCubeMir by lazy { LinearMeshes.mirrored(miniCube) }
    private val miniEdges by lazy {
        val t = 0.09f; val o = -0.5f
        LinearMeshes.shadedBoxes(listOf(floatArrayOf(o, o, o, 0.5f, o + t, o + t), floatArrayOf(o, o, o, o + t, 0.5f, o + t),
            floatArrayOf(o, o, o, o + t, o + t, 0.5f)), paint = intArrayOf(0, 1, 2))
    }
    private val miniEdgesMir by lazy { LinearMeshes.mirrored(miniEdges) }
    /**
     * The "F": stem + two bars in its own (right, up, out) frame = (ĵ, k̂, î): it stands in the plane
     * x = 0.78 on the floor, left of the cube, facing î — about 40 deg off the line of sight, so it stays
     * readable after a 90 deg turn (facing the viewer squarely it went edge-on) and mirrors under x <-> y.
     */
    private val fMesh by lazy {
        val local = LinearMeshes.shadedBoxes(listOf(
            floatArrayOf(0f, 0f, -0.05f, 0.12f, 0.78f, 0.05f),
            floatArrayOf(0.12f, 0.66f, -0.05f, 0.46f, 0.78f, 0.05f),
            floatArrayOf(0.12f, 0.37f, -0.05f, 0.36f, 0.48f, 0.05f)))
        framed(local, Vec3.Y, Vec3.Z, Vec3.X, Vec3(0.78, -1.0, 0.0))
    }
    private val fMir by lazy { LinearMeshes.mirrored(fMesh) }

    private val matLatHalf = Mat.Unlit(rgb(0x2B7BA6), alpha = 0.22f, blend = Blend.TRANSPARENT)
    private val matLatInt = Mat.Unlit(rgb(0x4FA8DA), alpha = 0.50f, blend = Blend.TRANSPARENT)
    private val matLatAxis = Mat.Unlit(rgb(0xD6ECF8), alpha = 0.90f, blend = Blend.TRANSPARENT)
    private val matGhostEdge = Mat.Unlit(WHITE, alpha = 0.32f, blend = Blend.TRANSPARENT)
    private val matArrow = BASIS.map { Mat.Unlit(it) }
    private val matTipGlow = BASIS.map { Mat.Unlit(it, alpha = 0.30f, blend = Blend.ADD) }
    private val matTipGlowHot = BASIS.map { Mat.Unlit(it, alpha = 0.75f, blend = Blend.ADD) }
    private val matEdgePos = Mat.Unlit(GOLD)
    private val matEdgeNeg = Mat.Unlit(PINK)
    private val matFacePos by lazy { Mat.Unlit(GOLD, alpha = 0.30f, tex = shadeTex, blend = Blend.TRANSPARENT) }
    private val matFaceNeg by lazy { Mat.Unlit(PINK, alpha = 0.30f, tex = shadeTex, blend = Blend.TRANSPARENT) }
    private val matFaceFlat by lazy { Mat.Unlit(GOLD, alpha = 0.75f, tex = shadeTex, blend = Blend.TRANSPARENT) }
    private val matGhostFaces by lazy { Mat.Unlit(WHITE, alpha = 0.13f, tex = shadeTex, blend = Blend.TRANSPARENT) }
    private val matF by lazy { Mat.Unlit(IVORY, tex = shadeTex) }
    private val matECore = Mat.Unlit(EIGEN)
    private val matProbeFaint = Mat.Unlit(VIOLET, alpha = 0.45f, blend = Blend.TRANSPARENT)
    private val matProbeSpan = Mat.Unlit(VIOLET, alpha = 0.28f, blend = Blend.TRANSPARENT)
    private val matProbe = Mat.Unlit(VIOLET)
    private val matRecipe = BASIS.map { Mat.Unlit(it, alpha = 0.55f, blend = Blend.TRANSPARENT) }
    private val matMiniPos by lazy { Mat.Unlit(GOLD, alpha = 0.55f, tex = shadeTex, blend = Blend.TRANSPARENT) }
    private val matMiniNeg by lazy { Mat.Unlit(PINK, alpha = 0.55f, tex = shadeTex, blend = Blend.TRANSPARENT) }
    private val matMiniEdges by lazy { Mat.Unlit(WHITE, tex = rgbTex) }
    private val matPad = Mat.Pbr(rgb(0x1B2130), metallic = 0.1f, roughness = 0.35f, emissive = rgb(0x05080D))
    private val matPadOn = Mat.Pbr(rgb(0x22324A), metallic = 0.1f, roughness = 0.30f, emissive = rgb(0x1C4A78))
    private val matBracket = Mat.Unlit(rgb(0xE8EEF5))
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.32f)
    private val slabTex by lazy { ProcTex.bakePlanet(256, 128, Rgb(0.07f, 0.075f, 0.085f), Rgb(0.12f, 0.125f, 0.14f), Rgb(0.20f, 0.205f, 0.22f), 0f, 77) }

    // ---------------------------------------------------------------- build

    override fun buildSpec(cacheDir: File): Node {
        // REBUILD IDEMPOTENCE: every piece of state back to the opening values
        preset = Preset.WELCOME; speedIdx = 1; showEigen = true; showProbe = true; composeBA = true; randomSeed = 0
        shown = Mat3.identity(); applied = null; appliedFlags = -1; stages.clear(); stageT = 0f
        dragCol = -1; dragRaw = Vec3.ZERO; draggedJ = false; detAtStep = 1.0; probe = PROBE0; clock = 0f; shelfKey = -1
        eLines = emptyList(); eSpaces = emptyList(); eComplex = null; eScalar = false; rank = 3; imgU = Mat3.identity()
        lattice.clear(); cubeEdges.clear()
        // the opening: plain space for a moment, then a general linear map unfolds
        stages += Stage(Mat3.identity(), LinearPresets.WELCOME, 1.5f, 0.2f)

        val root = Node("LinearRoot")
        val slab = Mat.Pbr(Rgb(1f, 1f, 1f), metallic = 0f, roughness = 0.55f, tex = slabTex)
        root.add("Plinth", MeshGen.box(1.76f, 0.03f, 0.95f), slab, P3(0f, -0.255f, -0.02f))
        root.add("PlinthBand", MeshGen.box(1.77f, 0.008f, 0.96f), alu, P3(0f, -0.255f, -0.02f))

        space = root.add(Node("Space")).also { it.pos = C; it.rot = VIEW_Q; it.uniformScale(S) }

        // ---- the lattice: 3 x 25 lines through the 5x5x5 points of [-1, 1]^3 (step 1/2)
        var li = 0
        for (dir in 0 until 3) for (a in COORDS) for (b in if (dir == 2) COORDS else ZCOORDS) {
            val axis = a == 0.0 && b == 0.0
            val whole = a % 1.0 == 0.0 && b % 1.0 == 0.0
            val n = space.add("Lat${li++}", unitCyl, if (axis) matLatAxis else if (whole) matLatInt else matLatHalf)
            val lo = if (dir == 2) ZCOORDS.first() else -1.0; val hi = if (dir == 2) ZCOORDS.last() else 1.0
            lattice += Seg(n, latPoint(dir, lo, a, b), latPoint(dir, hi, a, b), if (axis) R_AXIS else R_LAT)
        }
        // ---- the original unit cube, faint and fixed (compare volumes against it)
        for ((k, e) in CUBE_EDGES.withIndex()) {
            val n = space.add("GhostEdge$k", unitCyl, matGhostEdge)
            placeSeg(n, Linear.CUBE[e.first], Linear.CUBE[e.second], R_GHOST, true)
        }
        // ---- the transformed unit cube + F (one SVD chain), its 9 bright edges
        solid = newChain(space, "Solid")
        cubeFaces = part(solid, "CubeFaces", cubeMesh, cubeMir, matFacePos)
        part(solid, "FGlyph", fMesh, fMir, matF)
        for ((k, e) in CUBE_EDGES.withIndex())
            cubeEdges += Seg(space.add("CubeEdge$k", unitCyl, matEdgePos), Linear.CUBE[e.first], Linear.CUBE[e.second], R_EDGE)
        // the other order of a composition, as a white ghost
        ghost = newChain(space, "Ghost")
        part(ghost, "GhostFaces", cubeMesh, cubeMir, matGhostFaces)

        // ---- basis arrows with draggable glowing tips
        shafts = Array(3) { space.add("Shaft$it", unitCyl, matArrow[it]) }
        heads = Array(3) { space.add("Head$it", cone, matArrow[it]) }
        tips = Array(3) { c ->
            space.add(TIP_NAMES[c], sphere, matArrow[c]).also { it.uniformScale(R_TIP); it.tapRadius = 0.052f / (R_TIP * S) }
        }
        tipGlows = Array(3) { c -> tips[c].add("TipGlow$c", sphere, matTipGlow[c]).also { it.uniformScale(1.55f) } }
        space.add("Origin", sphere, Mat.Unlit(WHITE)).uniformScale(0.006f / S)

        // ---- eigen-lines and the image plane / line of a squashed space
        eigCore = Array(3) { space.add("EigCore$it", unitCyl, matECore).also { n -> n.visible = false } }
        eigGlow = Array(3) { space.add("EigGlow$it", unitCyl, Mat.Unlit(EIGEN, alpha = 0.25f, blend = Blend.ADD)).also { n -> n.visible = false } }
        imgPlane = space.add("ImagePlane", quad, Mat.Unlit(GOLD, alpha = 0.12f, blend = Blend.ADD)).also { it.visible = false }
        imgLine = space.add("ImageLine", unitCyl, Mat.Unlit(GOLD, alpha = 0.4f, blend = Blend.ADD)).also { it.visible = false }

        // ---- the probe v (input), Av (output), v's span and Av's recipe x î′ + y ĵ′ + z k̂′
        probeNodes = arrayOf(
            space.add("ProbeSpan", unitCyl, matProbeSpan), space.add("ProbeVShaft", unitCyl, matProbeFaint),
            space.add("ProbeVHead", cone, matProbeFaint), space.add("ProbeAvShaft", unitCyl, matProbe),
            space.add("ProbeAvHead", cone, matProbe),
            space.add("Probe", sphere, matProbe).also { it.uniformScale(R_PROBE); it.tapRadius = 0.05f / (R_PROBE * S) })
        probeNodes[5].add("ProbeGlow", sphere, Mat.Unlit(VIOLET, alpha = 0.30f, blend = Blend.ADD)).uniformScale(1.6f)
        recipe = Array(3) { space.add("Recipe$it", unitCyl, matRecipe[it]) }

        // ---- the matrix board (left): three coloured column underlines between big brackets; the
        // numbers are the three label chips above them
        under = Array(3) { c -> root.add("Under$c", unitBox, matArrow[c], P3(BOARD_X[c], BOARD_Y - 0.068f, BOARD_Z)).also { it.scale = P3(0.072f, 0.008f, 0.008f) } }
        for ((tag, side) in listOf("L" to -1f, "R" to 1f)) {
            val x = if (side < 0) BOARD_X[0] - 0.068f else BOARD_X[2] + 0.068f
            root.add("Bracket$tag", unitBox, matBracket, P3(x, BOARD_Y - 0.005f, BOARD_Z)).scale = P3(0.006f, 0.15f, 0.006f)
            for ((k, dy) in floatArrayOf(-0.077f, 0.067f).withIndex())
                root.add("BracketTick$tag$k", unitBox, matBracket, P3(x - side * 0.011f, BOARD_Y + dy, BOARD_Z)).scale = P3(0.022f, 0.006f, 0.006f)
        }

        // ---- the preset shelf (right): 2 x 4 pads, each with a mini-cube showing its own transform
        val padMesh = MeshGen.box(0.125f, 0.125f, 0.012f)
        pads = Array(8) { k -> root.add("Shelf$k", padMesh, matPad, shelfPos(k)).also { it.tapRadius = 0.06f } }
        minis = Array(8) { k ->
            val p = shelfPos(k)
            val holder = root.add(Node("Mini$k")).also { it.pos = P3(p.x, p.y, p.z + 0.065f); it.rot = VIEW_Q; it.uniformScale(0.050f) }
            newChain(holder, "Mini${k}_").also { ch ->
                part(ch, "MiniFaces$k", miniCube, miniCubeMir, matMiniPos); part(ch, "MiniEdges$k", miniEdges, miniEdgesMir, matMiniEdges)
            }
        }
        updateShelf()
        return root
    }

    private val BOARD_X = floatArrayOf(-0.80f, -0.69f, -0.58f)
    private const val BOARD_Y = 0.175f
    private const val BOARD_Z = 0.10f
    private fun shelfPos(k: Int) = P3(if (k % 2 == 0) 0.625f else 0.775f, 0.335f - 0.145f * (k / 2), 0.10f)

    private fun latPoint(dir: Int, t: Double, a: Double, b: Double) = when (dir) {
        0 -> Vec3(t, a, b); 1 -> Vec3(a, t, b); else -> Vec3(a, b, t)   // dir 0/1: b is the height
    }

    private fun framed(md: MeshData, r: Vec3, u: Vec3, n: Vec3, o: Vec3): MeshData {
        val pos = FloatArray(md.pos.size); val nrm = FloatArray(md.nrm.size)
        for (k in 0 until md.vertexCount) {
            val p = o + r * md.pos[k * 3].toDouble() + u * md.pos[k * 3 + 1].toDouble() + n * md.pos[k * 3 + 2].toDouble()
            val q = r * md.nrm[k * 3].toDouble() + u * md.nrm[k * 3 + 1].toDouble() + n * md.nrm[k * 3 + 2].toDouble()
            pos[k * 3] = p.x.toFloat(); pos[k * 3 + 1] = p.y.toFloat(); pos[k * 3 + 2] = p.z.toFloat()
            nrm[k * 3] = q.x.toFloat(); nrm[k * 3 + 1] = q.y.toFloat(); nrm[k * 3 + 2] = q.z.toFloat()
        }
        return MeshData(pos, nrm, md.uv.copyOf(), md.idx.copyOf())
    }

    // ---------------------------------------------------------------- transforms

    private fun qOf(m: Mat3): Q { val q = Linear.quat(m); return Q(q[0].toFloat(), q[1].toFloat(), q[2].toFloat(), q[3].toFloat()) }
    private fun p3(v: Vec3) = P3(v.x.toFloat(), v.y.toFloat(), v.z.toFloat())
    /** Volume position of math point [p]. */
    private fun vol(p: Vec3): P3 { val w = VIEW * p; return P3(C.x + S * w.x.toFloat(), C.y + S * w.y.toFloat(), C.z + S * w.z.toFloat()) }

    private fun newChain(parent: Node, name: String): Chain {
        val o = parent.add(Node("${name}U")); val i = o.add(Node("${name}V")); return Chain(o, i)
    }
    private fun part(ch: Chain, name: String, md: MeshData, mir: MeshData, mat: Mat): Node {
        val n = ch.inner.add(name, md, mat); ch.parts += Triple(n, md, mir); return n
    }
    /** Drive a chain so that it applies [m]: U·diag(σ)·Vᵀ, all scales positive (mirror meshes when det < 0). */
    private fun setChain(ch: Chain, m: Mat3) {
        val neg = m.det() < 0
        val svd = Linear.svd(if (neg) m * F0 else m)
        ch.outer.rot = qOf(svd.u)
        ch.outer.scale = P3(max(svd.s.x, MIN_S).toFloat(), max(svd.s.y, MIN_S).toFloat(), max(abs(svd.s.z), MIN_S).toFloat())
        ch.inner.rot = qOf(svd.v.transpose())
        for ((n, a, b) in ch.parts) { val want = if (neg) b else a; if (n.mesh !== want) n.setMesh(want) }
    }

    /** A unit cylinder from a to b (math units), radius r; hidden when degenerate or [show] is false. */
    private fun placeSeg(n: Node, a: Vec3, b: Vec3, r: Float, show: Boolean) {
        val d = b - a; val len = d.norm
        if (!show || len < 1e-4) { n.visible = false; return }
        n.visible = true
        n.pos = p3((a + b) * 0.5)
        n.rot = Q.fromTo(0f, 1f, 0f, d.x.toFloat(), d.y.toFloat(), d.z.toFloat())
        n.scale = P3(r, len.toFloat(), r)
    }

    private fun placeArrow(shaft: Node, head: Node, tip: Vec3, r: Float, show: Boolean, headScale: Float = 1f) {
        val l = tip.norm
        if (!show || l < 1e-4) { shaft.visible = false; head.visible = false; return }
        val dir = tip * (1 / l)
        val hl = min(HEAD_LEN * headScale.toDouble(), 0.45 * l); val hr = HEAD_R * headScale * (hl / (HEAD_LEN * headScale)).toFloat()
        placeSeg(shaft, Vec3.ZERO, dir * (l - hl), r, true)
        head.visible = true
        head.pos = p3(dir * (l - hl))
        head.rot = Q.fromTo(0f, 1f, 0f, dir.x.toFloat(), dir.y.toFloat(), dir.z.toFloat())
        head.scale = P3(hr, hl.toFloat(), hr)
    }

    // ---------------------------------------------------------------- per frame

    override fun update(dt: Float) {
        clock += dt
        advance(dt)
        val flags = (if (showEigen) 1 else 0) or (if (showProbe) 2 else 0) or (if (ghostOn()) 4 else 0) or
            (if (composeBA) 8 else 0) or ((dragCol + 1) shl 4)
        if (applied != shown || flags != appliedFlags) { applyScene(shown); applied = shown; appliedFlags = flags }
        pulse()
        updateShelf()
        updateReadout()
        updateLabels()
    }

    private fun advance(dt: Float) {
        val st = stages.firstOrNull() ?: return
        stageT += dt * SPEEDS[speedIdx]
        val u = ((stageT - st.delay) / st.dur).coerceIn(0f, 1f)
        shown = Linear.path(st.from, st.to, (u * u * (3 - 2 * u)).toDouble())
        if (stageT >= st.delay + st.dur) { shown = st.to; stages.removeFirst(); stageT = 0f }
    }

    private fun ghostOn() = preset == Preset.COMPOSE && stages.isEmpty()

    private fun applyScene(m: Mat3) {
        for (s in lattice) placeSeg(s.node, m * s.a, m * s.b, s.r, true)
        val det = m.det()
        val edgeMat = if (det < 0) matEdgeNeg else matEdgePos
        for (s in cubeEdges) { placeSeg(s.node, m * s.a, m * s.b, s.r, true); s.node.setMat(edgeMat) }
        for (c in 0 until 3) {
            val col = m.col(c)
            placeArrow(shafts[c], heads[c], col, R_SHAFT, true)
            tips[c].pos = p3(col)
            tipGlows[c].setMat(if (dragCol == c) matTipGlowHot[c] else matTipGlow[c])
            under[c].scale = P3(0.072f, if (dragCol == c) 0.016f else 0.008f, 0.008f)
        }
        setChain(solid, m)
        val svd = Linear.svd(m)
        val s0 = max(1.0, svd.s.x)
        rank = listOf(svd.s.x, svd.s.y, abs(svd.s.z)).count { it > 0.03 * s0 }
        imgU = svd.u
        cubeFaces.setMat(if (rank < 3) matFaceFlat else if (det < 0) matFaceNeg else matFacePos)
        // composition: the other order as a ghost
        ghost.outer.visible = ghostOn()
        if (ghostOn()) setChain(ghost, if (composeBA) LinearPresets.COMPOSE_A * LinearPresets.COMPOSE_B else LinearPresets.COMPOSE_B * LinearPresets.COMPOSE_A)
        // squashed space: the plane (rank 2) or line (rank 1) everything lands in
        imgPlane.visible = rank == 2
        if (rank == 2) imgPlane.rot = qOf(imgU)
        if (rank == 1) placeSeg(imgLine, imgU.col(0) * -EIGEN_L, imgU.col(0) * EIGEN_L, R_EGLOW, true) else imgLine.visible = false
        // eigen-directions
        computeEigen(m)
        for (k in 0 until 3) {
            val on = showEigen && k < eLines.size
            val d = if (on) eLines[k].dir else Vec3.Z
            placeSeg(eigCore[k], d * -EIGEN_L, d * EIGEN_L, R_ECORE, on)
            placeSeg(eigGlow[k], d * -EIGEN_L, d * EIGEN_L, R_EGLOW, on)
        }
        // the probe: v, its span, Av and Av's recipe
        val v = probe; val av = m * v
        val pv = showProbe
        placeSeg(probeNodes[0], v.unit() * -EIGEN_L, v.unit() * EIGEN_L, R_GHOST, pv)
        placeArrow(probeNodes[1], probeNodes[2], v, R_GHOST * 1.6f, pv, 0.8f)
        placeArrow(probeNodes[3], probeNodes[4], av, R_SHAFT * 0.8f, pv, 0.9f)
        probeNodes[5].visible = pv; probeNodes[5].pos = p3(v)
        val r1 = m.col(0) * v.x; val r2 = r1 + m.col(1) * v.y
        placeSeg(recipe[0], Vec3.ZERO, r1, R_LAT * 1.6f, pv)
        placeSeg(recipe[1], r1, r2, R_LAT * 1.6f, pv)
        placeSeg(recipe[2], r2, av, R_LAT * 1.6f, pv)
    }

    private fun pulse() {
        val a = ((0.16f + 0.08f * sin(clock * 2f * PI.toFloat() / 1.7f)) * 40f).toInt() / 40f
        val g = Mat.Unlit(EIGEN, alpha = a, blend = Blend.ADD)
        eigGlow.forEach { it.setMat(g) }
        val pa = ((0.12f + 0.08f * sin(clock * 2f * PI.toFloat() / 1.2f)) * 40f).toInt() / 40f
        imgPlane.setMat(Mat.Unlit(GOLD, alpha = pa, blend = Blend.ADD))
        imgLine.setMat(Mat.Unlit(GOLD, alpha = pa * 3f, blend = Blend.ADD))
    }

    private fun computeEigen(m: Mat3) {
        val es = Linear.eigen(m, 0.02)
        eSpaces = es
        val ev = Linear.eigenvalues(m)
        eComplex = if (ev.hasComplexPair && ev.im > 0.02) ev else null
        eScalar = es.size == 1 && es[0].geometric == 3
        val out = ArrayList<ELine>()
        if (!eScalar) for (e in es) {
            if (e.geometric == 1) out += ELine(orient(e.basis[0]), e.lambda, false, true)
            else if (e.geometric == 2) {
                val (p, q) = niceBasis(e.basis[0] cross e.basis[1])
                out += ELine(orient(p), e.lambda, true, true); out += ELine(orient(q), e.lambda, true, false)
            }
        }
        eLines = out.take(3)
    }

    /** Sign the label end of a line toward the viewer / upward, where its label stays readable. */
    private fun orient(d: Vec3): Vec3 { val w = VIEW * d; return if (w.y + 0.6 * w.z < 0) -d else d }

    /** Two directions spanning the plane with normal [n], as close to the coordinate axes as possible (stable, readable). */
    private fun niceBasis(n0: Vec3): Pair<Vec3, Vec3> {
        val n = n0.unit()
        val proj = (0 until 3).map { val e = Vec3.axis(it); e - n * (n dot e) }
        val ia = proj.indices.maxByOrNull { proj[it].norm }!!
        val a = proj[ia].unit()
        val ib = proj.indices.filter { it != ia }.maxByOrNull { (proj[it] - a * (a dot proj[it])).norm }!!
        return a to proj[ib].unit()
    }

    // ---------------------------------------------------------------- readout + labels

    private fun f2(x: Double) = "%.2f".format(if (abs(x) < 0.005) 0.0 else x)
    private fun vecStr(v: Vec3) = "(${f2(v.x)}, ${f2(v.y)}, ${f2(v.z)})"

    private fun lambdaText(): String {
        val reals = eSpaces.flatMap { e -> List(e.algebraic) { e.lambda } }
        val c = eComplex
        val cx = if (c != null) "${f2(c.re)} ± ${f2(c.im)}i" else ""
        return "λ = " + (reals.map { f2(it) } + listOfNotNull(cx.ifEmpty { null })).joinToString(", ")
    }

    private fun presetLine(): String = when (preset) {
        Preset.WELCOME -> tr("一般的线性变换：网格线仍然平行且等距，原点不动 —— 拖动 î、ĵ、k̂ 的箭头尖试试",
            "A general linear map: grid lines stay parallel and evenly spaced, the origin stays — drag the tips of î, ĵ, k̂")
        Preset.IDENTITY -> tr("恒等变换：什么都不动 —— 每个向量都是本征向量（λ = 1）", "Identity: nothing moves — every vector is an eigenvector (λ = 1)")
        Preset.ROTATE -> tr("绕竖直轴转 90°：î → ĵ，ĵ → −î；体积不变，只有转轴（发光线）不转，λ = 1",
            "Turn 90° about the vertical axis: î → ĵ, ĵ → −î; same volume; only the axis (glowing) stays, λ = 1")
        Preset.SHEAR -> tr("剪切：k̂ → k̂ + ĵ，顶面平移，底面和高度都不变（像推一叠扑克牌）",
            "Shear: k̂ → k̂ + ĵ — the top slides, base and height stay (like pushing a deck of cards)")
        Preset.SCALE -> tr("伸缩：x ×1.5，y ×0.6，z ×1.3 —— 三条轴只伸缩不转向，都是本征方向",
            "Scale: x ×1.5, y ×0.6, z ×1.3 — the three axes only stretch: all are eigen-directions")
        Preset.PROJECT -> tr("投影到地面：k̂ → 0，整个空间被压成一个平面（发光的金色平面）",
            "Projection onto the floor: k̂ → 0, all of space is squashed into one plane (the glowing one)")
        Preset.REFLECT -> tr("镜像（交换 î 与 ĵ）：F 左右颠倒，立方体变成粉色", "Reflection (swap î and ĵ): the F is mirrored, the cube turns pink")
        Preset.RANDOM -> tr("随机矩阵 #%d：继续拖动箭头；三维变换总有至少一个实本征方向",
            "Random matrix #%d — keep dragging; a 3-D map always has at least one real eigen-direction").format(randomSeed)
        Preset.COMPOSE -> if (composeBA) tr("组合：先 A（剪切）后 B（旋转）= BA；白色虚影 = 先 B 后 A = AB ≠ BA",
            "Compose: first A (shear), then B (turn) = BA; white ghost = B first, then A = AB ≠ BA")
            else tr("组合：先 B（旋转）后 A（剪切）= AB；白色虚影 = BA —— 顺序不同，结果不同",
            "Compose: first B (turn), then A (shear) = AB; white ghost = BA — the order matters")
        Preset.CUSTOM -> tr("自定义：矩阵的每一列，就是你把 î、ĵ、k̂ 拖到了哪里", "Custom: each column of the matrix is where you dragged î, ĵ, k̂")
    }

    private fun insightLine(det: Double): String = when {
        rank < 3 -> tr("det = 0：空间被压扁，体积为 0 —— 不同的点落到同一处，所以这个变换无法撤销",
            "det = 0: space is squashed flat, volume 0 — different points land together, so it cannot be undone")
        det < 0 -> tr("det < 0：定向翻转（右手系变成左手系）—— F 成了镜像，立方体变成粉色",
            "det < 0: orientation flipped (a right hand becomes a left hand) — the F is mirrored, the cube turns pink")
        else -> tr("det = 单位立方体变换后的有向体积 = î′·(ĵ′×k̂′)；发光线 = 只伸缩不转向的本征方向",
            "det = signed volume of the transformed unit cube = î′·(ĵ′×k̂′); glowing lines = eigen-directions")
    }

    private fun updateReadout() {
        val m = shown
        val cols = (0 until 3).joinToString("  ") { c -> "${HAT[c]}′=${vecStr(m.col(c))}" }
        readoutText = "$cols   det = ${f2(m.det())}   ${lambdaText()}\n${presetLine()}\n${insightLine(m.det())}"
    }

    private fun updateLabels() {
        labels.clear()
        val m = shown
        for (c in 0 until 3) {
            val v = m.col(c)
            labels += LabelSpec("${HAT[c]}′\n${f2(v.x)}\n${f2(v.y)}\n${f2(v.z)}", P3(BOARD_X[c], BOARD_Y, BOARD_Z + 0.01f))
        }
        val det = m.det()
        val reals = eSpaces.flatMap { e -> List(e.algebraic) { e.lambda } }
        val detText = when {
            rank < 3 -> tr("det = 0 · 压扁了", "det = 0 · squashed")
            det < 0 -> tr("det = %s · 翻转", "det = %s · flipped").format(f2(det))
            reals.size == 3 && eSpaces.size == 3 -> "det = ${f2(det)} = ${reals.joinToString(" × ") { f2(it) }}"
            else -> "det = ${f2(det)}"
        }
        val top = vol(m * Vec3(0.5, 0.5, 1.0))
        labels += LabelSpec(detText, P3(top.x, top.y + 0.075f, top.z + 0.04f))
        if (showEigen) {
            if (eScalar) labels += LabelSpec(tr("每个方向都是本征方向 · λ = %s", "Every direction is an eigen-direction · λ = %s").format(f2(eSpaces[0].lambda)),
                vol(Vec3(-0.6, -0.6, 1.25)))
            else for (l in eLines.filter { it.labelled }.take(2)) {
                val p = vol(l.dir * (EIGEN_L + 0.12))
                val t = if (l.plane) tr("λ = %s · 整个平面", "λ = %s · whole plane").format(f2(l.lambda)) else "λ = ${f2(l.lambda)}"
                labels += LabelSpec(t, P3(p.x, p.y + 0.03f, p.z))
            }
        }
    }

    // ---------------------------------------------------------------- presets + animation

    private fun target(p: Preset): Mat3 = when (p) {
        Preset.IDENTITY -> LinearPresets.IDENTITY
        Preset.ROTATE -> LinearPresets.ROTATE
        Preset.SHEAR -> LinearPresets.SHEAR
        Preset.SCALE -> LinearPresets.SCALE
        Preset.PROJECT -> LinearPresets.PROJECT
        Preset.REFLECT -> LinearPresets.REFLECT
        Preset.RANDOM -> LinearPresets.random(randomSeed)
        Preset.COMPOSE -> if (composeBA) LinearPresets.COMPOSE_B * LinearPresets.COMPOSE_A else LinearPresets.COMPOSE_A * LinearPresets.COMPOSE_B
        Preset.WELCOME -> LinearPresets.WELCOME
        Preset.CUSTOM -> shown
    }

    /** Pick a preset (shelf pinch / panel twin). Pinching the active Random rerolls; the active Compose swaps the order. */
    fun choose(p: Preset) {
        if (p == Preset.RANDOM && preset == Preset.RANDOM) randomSeed++
        if (p == Preset.COMPOSE) composeBA = if (preset == Preset.COMPOSE) !composeBA else true
        preset = p
        play(target(p))
    }

    /** Replay the current matrix from the plain grid. */
    fun replay() = play(if (preset == Preset.CUSTOM) (stages.lastOrNull()?.to ?: shown) else target(preset))

    /** Back to the identity (quickly), then unfold [to] — for Compose in two stages, first factor first. */
    private fun play(to: Mat3) {
        dragCol = -1
        stages.clear(); stageT = 0f
        val i = Mat3.identity()
        if (!shown.isClose(i, 1e-9)) stages += Stage(shown, i, 0.55f, 0f)
        if (preset == Preset.COMPOSE) {
            val a = LinearPresets.COMPOSE_A; val b = LinearPresets.COMPOSE_B
            val first = if (composeBA) a else b
            stages += Stage(i, first, 1.3f, 0.15f)
            stages += Stage(first, to, 1.3f, 0.45f)
        } else stages += Stage(i, to, 1.5f, 0.15f)
    }

    /** Finish every pending animation now (review variants). */
    private fun settle() { while (stages.isNotEmpty()) shown = stages.removeFirst().to; stageT = 0f }

    private fun updateShelf() {
        val key = preset.ordinal * 100000 + randomSeed * 2 + if (composeBA) 1 else 0
        if (key == shelfKey) return
        shelfKey = key
        for (k in 0 until 8) {
            pads[k].setMat(if (SHELF[k] == preset) matPadOn else matPad)
            val m = when (SHELF[k]) { Preset.IDENTITY -> Mat3.identity(); else -> target(SHELF[k]) }
            setChain(minis[k], m)
            minis[k].parts[0].first.setMat(if (m.det() < -1e-9) matMiniNeg else matMiniPos)
        }
    }

    // ---------------------------------------------------------------- interaction

    override val tapTargets get() = listOf(
        TapTarget("TipI", "î 箭头尖 → 复位", "î → reset"),
        TapTarget("TipJ", "ĵ 箭头尖 → 复位", "ĵ → reset"),
        TapTarget("TipK", "k̂ 箭头尖 → 复位", "k̂ → reset"),
        TapTarget("Probe", "探针 v → 复位", "Probe v → reset"),
        TapTarget("Shelf0", "恒等 → 播放", "Identity → play") { preset == Preset.IDENTITY },
        TapTarget("Shelf1", "旋转 → 播放", "Rotate → play") { preset == Preset.ROTATE },
        TapTarget("Shelf2", "剪切 → 播放", "Shear → play") { preset == Preset.SHEAR },
        TapTarget("Shelf3", "伸缩 → 播放", "Scale → play") { preset == Preset.SCALE },
        TapTarget("Shelf4", "投影 → 播放", "Project → play") { preset == Preset.PROJECT },
        TapTarget("Shelf5", "镜像 → 播放", "Reflect → play") { preset == Preset.REFLECT },
        TapTarget("Shelf6", "随机 → 掷一个", "Random → roll") { preset == Preset.RANDOM },
        TapTarget("Shelf7", "组合 BA/AB → 播放", "Compose → play") { preset == Preset.COMPOSE },
    )

    override fun isTarget(name: String) = name in TIP_NAMES || name == "Probe" || name.startsWith("Shelf")

    override fun onTap(name: String) {
        val c = TIP_NAMES.indexOf(name)
        when {
            c >= 0 -> resetColumn(c)
            name == "Probe" -> { probe = PROBE0; showProbe = true }
            name.startsWith("Shelf") -> name.removePrefix("Shelf").toIntOrNull()?.let { k -> SHELF.getOrNull(k)?.let { choose(it) } }
        }
    }

    /** Send one basis vector home: the column animates back to the unit vector. */
    private fun resetColumn(c: Int) {
        val base = stages.lastOrNull()?.to ?: shown
        val to = base.withCol(c, Vec3.axis(c))
        dragCol = -1
        stages.clear(); stageT = 0f
        stages += Stage(shown, to, 0.6f, 0f)
        preset = Preset.CUSTOM
    }

    override fun tapMessage(name: String): String? {
        val c = TIP_NAMES.indexOf(name)
        if (c >= 0) return tr("%s′ 回到 %s", "%s′ back to %s").format(HAT[c], UNIT_STR[c])
        if (name == "Probe") return tr("探针 v 复位：拖动它，找 Av 不离开 v 那条直线的方向", "Probe v reset — drag it to find where Av stays on v's line")
        val k = name.removePrefix("Shelf").toIntOrNull() ?: return null
        return when (SHELF.getOrNull(k)) {
            Preset.IDENTITY -> tr("恒等：一切回到原位，det = 1", "Identity: everything back home, det = 1")
            Preset.ROTATE -> tr("旋转 90°：只有转轴（发光线）不转，λ = 1", "Turn 90°: only the axis (glowing line) stays, λ = 1")
            Preset.SHEAR -> tr("剪切：立方体的体积变了吗？盯住 det", "Shear: does the cube's volume change? Watch det")
            Preset.SCALE -> tr("伸缩：三条轴只伸缩不转向，det = 1.5 × 0.6 × 1.3", "Scale: the axes only stretch, det = 1.5 × 0.6 × 1.3")
            Preset.PROJECT -> tr("投影：k̂ → 0，空间被压扁，det = 0", "Projection: k̂ → 0, space is squashed, det = 0")
            Preset.REFLECT -> tr("镜像：F 左右颠倒，立方体变粉色，det = −1", "Reflection: the F flips, the cube turns pink, det = −1")
            Preset.RANDOM -> tr("随机矩阵 #%d：det = %s（再捏一次换一个）", "Random matrix #%d: det = %s (pinch again for another)").format(randomSeed, f2(target(Preset.RANDOM).det()))
            Preset.COMPOSE -> if (composeBA) tr("先 A（剪切）再 B（旋转）= BA；白色虚影 = AB", "A (shear) then B (turn) = BA; white ghost = AB")
                else tr("先 B（旋转）再 A（剪切）= AB；白色虚影 = BA", "B (turn) then A (shear) = AB; white ghost = BA")
            else -> null
        }
    }

    // ---- pinch-and-drag: a basis tip (sets that column) or the probe v
    override fun isDragTarget(name: String) = name in TIP_NAMES || name == "Probe"

    override fun onDragStart(name: String) {
        val c = TIP_NAMES.indexOf(name)
        if (c >= 0) {
            stages.clear(); stageT = 0f                       // freeze wherever an animation was
            dragCol = c; dragRaw = shown.col(c); preset = Preset.CUSTOM
        }
    }

    override fun onDrag(name: String, dx: Float, dy: Float, dz: Float) {
        val d = VIEW_T * Vec3(dx.toDouble(), dy.toDouble(), dz.toDouble()) * (1.0 / S)
        val c = TIP_NAMES.indexOf(name)
        if (c >= 0) {
            if (dragCol != c) onDragStart(name)
            dragRaw = clampLen(dragRaw + d, 0.0, 1.7)
            shown = shown.withCol(c, snapToPlane(c, dragRaw))
            if (c == 1) draggedJ = true
        } else if (name == "Probe") probe = clampLen(probe + d, 0.25, 1.3)
    }

    override fun onDragEnd(name: String) { dragCol = -1 }

    private fun clampLen(v: Vec3, lo: Double, hi: Double): Vec3 {
        val n = v.norm
        return when { n > hi -> v * (hi / n); n < lo -> if (n < 1e-9) Vec3(lo, 0.0, 0.0) else v * (lo / n); else -> v }
    }

    /** Within 0.08 units (~2 cm, above hand jitter) of the plane of the other two columns, a dragged tip clicks INTO it: det = 0 exactly. */
    private fun snapToPlane(c: Int, raw: Vec3): Vec3 {
        val o = (0 until 3).filter { it != c }
        val n = shown.col(o[0]) cross shown.col(o[1]); val nn = n.norm
        if (nn < 0.05) return raw
        val dist = (raw dot n) / nn
        return if (abs(dist) < 0.08) raw - n * (dist / nn) else raw
    }

    /** Move a drag target by a MATH-space step, through the same drag API a hand uses (lesson autoplay). */
    private fun dragBy(name: String, dm: Vec3) {
        val w = VIEW * dm * S.toDouble()
        onDragStart(name); onDrag(name, w.x.toFloat(), w.y.toFloat(), w.z.toFloat()); onDragEnd(name)
    }

    // ---- the probe challenge: find a direction that only stretches
    private fun probeAngleDeg(): Double {
        val av = shown * probe
        if (av.norm < 0.04 * probe.norm) return 0.0
        val c = ((av dot probe) / (av.norm * probe.norm)).coerceIn(-1.0, 1.0)
        val a = Math.toDegrees(acos(c))
        return min(a, 180 - a)
    }
    private fun isScalar(m: Mat3) = (m - Mat3.identity() * (m.trace() / 3)).maxAbs() < 0.02
    private fun probeAligned() = showProbe && stages.isEmpty() && !isScalar(shown) && probeAngleDeg() < 4.0
    private fun probeProgress(): String =
        if (isScalar(shown)) tr("这个矩阵让每个方向都只伸缩 —— 先换一个预设", "This matrix fixes every direction — pick another preset first")
        else tr("v 与 Av 的夹角 %.0f° → 目标 < 4°", "Angle between v and Av: %.0f° → goal < 4°").format(probeAngleDeg())

    private fun aimProbe() {
        showProbe = true
        if (isScalar(shown)) choose(Preset.SCALE)
        settle()
        val es = Linear.eigen(shown, 0.02)
        val e = es.first().basis.first()
        val dir = if (e dot probe < 0) -e else e
        dragBy("Probe", dir * probe.norm - probe)
    }

    // ---------------------------------------------------------------- guided track (导学)

    override val lesson: Lesson by lazy {
        Lesson(
            listOf(
                LessonStep(
                    say = Bi("剪切变换（像推一叠扑克牌）会改变金色立方体的体积吗？", "Does a shear (like pushing a deck of cards) change the gold cube's volume?"),
                    options = listOf(
                        Bi("变大", "It grows"),
                        Bi("不变", "It stays the same"),
                        Bi("变小", "It shrinks"),
                    ),
                    answer = 1,
                ),
                LessonStep(
                    say = Bi("捏一下右侧架子上的「剪切」小立方体（或点下方「剪切」），看金色立方体和 det。", "Pinch the “Shear” mini-cube on the right shelf (or tap “Shear” below); watch the gold cube and det."),
                    target = "Shelf2",
                    done = { preset == Preset.SHEAR && stages.isEmpty() },
                    autoplay = { onTap("Shelf2") },
                    reveal = true,
                    insight = Bi("体积不变：顶面只是平移，底面和高度都没变，所以 det = 1。发光的底面上，每个向量都原地不动（λ = 1）。", "Same volume: the top only slides, base and height are unchanged, so det = 1. Every vector in the glowing floor stays put (λ = 1)."),
                ),
                LessonStep(
                    say = Bi("捏住绿色 ĵ 的箭头尖拖动，把它拉长或缩短：盯住 det 和立方体的大小。", "Pinch the tip of the green ĵ arrow and drag it longer or shorter: watch det and the cube's size."),
                    target = "TipJ",
                    onEnter = { detAtStep = shown.det(); draggedJ = false },
                    done = { draggedJ && dragCol != 1 && abs(shown.det() - detAtStep) > 0.25 },
                    autoplay = { dragBy("TipJ", Vec3(0.0, 0.6, 0.0)) },
                    insight = Bi("det 就是单位立方体变换后的有向体积：det = î′·(ĵ′×k̂′)。它对每一列都是线性的：ĵ′ 拉长 k 倍，体积也变 k 倍。", "det is the signed volume of the transformed unit cube: det = î′·(ĵ′×k̂′). It is linear in each column: stretch ĵ′ k times and the volume scales k times."),
                ),
                LessonStep(
                    say = Bi("如果把 k̂ 压成 0（投影到地面），det 和整个空间会怎样？", "If k̂ is crushed to 0 (projection onto the floor), what happens to det and to space?"),
                    options = listOf(
                        Bi("det = 1，什么都不变", "det = 1, nothing changes"),
                        Bi("det = 0，空间被压成一个平面", "det = 0, space is squashed into a plane"),
                        Bi("det < 0，空间翻成镜像", "det < 0, space turns into its mirror image"),
                    ),
                    answer = 1,
                ),
                LessonStep(
                    say = Bi("捏一下「投影」小立方体：看整个网格落到地面上。", "Pinch the “Project” mini-cube: watch the whole grid fall onto the floor."),
                    target = "Shelf4",
                    done = { preset == Preset.PROJECT && stages.isEmpty() },
                    autoplay = { onTap("Shelf4") },
                    reveal = true,
                    insight = Bi("det = 0：整个三维空间落进一张平面，体积为 0。许多点落到同一处，所以这个变换无法撤销（没有逆矩阵）。", "det = 0: all of 3-D space lands in one plane — zero volume. Many points land on the same spot, so the map cannot be undone (no inverse)."),
                ),
            ),
            challenge = Challenge(
                say = Bi("找一个只伸缩、不转向的方向：拖动紫色探针 v，直到 Av 落在 v 所在的直线上。", "Find a direction that only stretches: drag the violet probe v until Av lands on v's own line."),
                progress = { probeProgress() },
                done = { probeAligned() },
                autoplay = { aimProbe() },
            ),
        )
    }

    // ---------------------------------------------------------------- review states

    override fun reviewVariants(): Map<String, () -> Unit> = mapOf(
        "rotate" to { choose(Preset.ROTATE); settle() },
        "shear" to { choose(Preset.SHEAR); settle() },
        "scale" to { choose(Preset.SCALE); settle() },
        "project" to { choose(Preset.PROJECT); settle() },
        "reflect" to { choose(Preset.REFLECT); settle() },
        "random" to { choose(Preset.RANDOM); settle() },
        "compose" to { choose(Preset.COMPOSE); settle() },
        // halfway through the mirror: the review runs 1 s after this, landing at u = 1/2 where det = 0 (flat)
        "midflip" to { choose(Preset.REFLECT); while (stages.size > 1) shown = stages.removeFirst().to; stageT = -0.1f },
        "drag" to { choose(Preset.IDENTITY); settle(); dragBy("TipK", Vec3(0.55, -0.25, -0.35)); dragBy("Probe", Vec3(0.3, -0.2, 0.0)) },
    )

    // ---------------------------------------------------------------- panel

    @Composable
    override fun Panel() {
        var si by FieldRef(::speedIdx)
        var eg by FieldRef(::showEigen)
        var pr by FieldRef(::showProbe)
        LabPanel(readout) {
            LabRow {
                SegmentControl {
                    SPEED_LABELS.forEachIndexed { k, l -> SegmentItem(selected = si == k, onClick = { si = k }, title = { Text(l) }) }
                }
                Spacer(Modifier.width(20.dp))
                LabSwitch(tr("本征线", "Eigen-lines"), eg) { eg = it }
                LabSwitch(tr("探针 v", "Probe v"), pr) { pr = it }
                Button(onClick = { replay() }) { Text(tr("从恒等重播", "Replay from identity")) }
            }
        }
    }
}
