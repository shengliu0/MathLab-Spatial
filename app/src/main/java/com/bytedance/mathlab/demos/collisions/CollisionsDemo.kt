// 碰撞数出 π · Colliding Blocks Compute π (G. Galperin, "Playing pool with π", 2003; 3Blue1Brown 2019).
//
// THE AHA: a big block slides into a small one in front of a wall (no friction, elastic). With M/m = 100^n the
// total number of collisions is 3, 31, 314, 3141, 31415, 314159 — the digits of π. Why: scale the velocities
// by √mass, x = √M·V, y = √m·v. Energy conservation is a CIRCLE, momentum conservation a family of parallel
// lines (slope −√(M/m)); each collision is a chord: vertical for the wall, along a momentum line for the blocks.
// The chord endpoints cut the circle into EQUAL arcs 2θ (inscribed angle θ = arctan √(m/M)); the state walks
// round from angle π until it lands in the end zone 0…θ, so the count is the number of θ that fit below π.
//
//   Track station (front): a wall, a small brown cube and a big blue cube (size ∝ ∛mass, capped) on a glowing
//     rail, clack flashes, a big digit counter and, at the end, "π = 3.14159…" with the matched digits lit.
//     Exact event-driven simulation (core/collisions BlockSim: closed-form collision times). A frame processes
//     at most a capped number of collisions — when the cap bites, time stops at the last collision, i.e. an
//     automatic slow motion through the burst; nothing is skipped, the count stays exact.
//   Phase board (behind): the energy circle, momentum lines, the state point hopping along chords, the equal
//     arcs coloured at the end, the end-zone wedge. "3-D" lifts the chords along a depth axis = collision
//     number: the zig-zag winds round a cylinder, 2θ per wall–block pair.
// Chords live in 12 chunk meshes of 32 quads (at most 2 chunks uploaded per frame); arcs, wedge and momentum
// lines are rebuilt only when the mass ratio changes. Above ~384 collisions the chords are SAMPLED (odd stride,
// stated in the readout).

package com.bytedance.mathlab.demos.collisions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.bytedance.mathlab.core.P3
import com.bytedance.mathlab.core.collisions.BlockSim
import com.bytedance.mathlab.core.collisions.Collisions
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
import com.bytedance.mathlab.lab.ui.LabPanel
import com.bytedance.mathlab.lab.ui.SegmentControl
import com.bytedance.mathlab.lab.ui.SegmentItem
import com.bytedance.mathlab.render.Image
import com.bytedance.mathlab.render.MeshData
import com.bytedance.mathlab.render.MeshGen
import com.bytedance.mathlab.render.OptGeo
import com.bytedance.mathlab.render.Rgb
import com.pico.spatial.ui.design.Text
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object CollisionsDemo : SpecDemo() {
    override val id = "collisions"
    override val title = "碰撞数出 π"  // i18n-ok (zh half of DemoRegistry.titleOf; English in TITLE_EN)
    override val credit = "Galperin · 2003"

    // ================================================================ layout (volume metres)
    private const val PLINTH_Y = -0.262f
    private const val TRACK_Y = -0.180f                // top of the rail (blocks stand on it)
    private const val TRACK_Z = 0.20f
    private const val WALL_X = -0.78f                  // sim x = 0 (the wall's face)
    private const val TRACK_END = 1.60                 // sim x where the rail ends (volume x = 0.82)
    private const val W1 = 0.05                        // small block size (sim and drawn)
    private const val X1 = 0.20                        // small block start (left face)
    private const val X2 = 0.45                        // big block start (left face)
    private const val V0 = -0.30                       // launch speed (m/s, simulated)
    private const val BIG_CAP = 0.18f
    // counter
    private const val CNT_X = -0.76f
    private const val CNT_Y = 0.30f
    private const val CNT_Z = 0.02f
    private const val CNT_EM = 0.11f
    private const val PI_Y = 0.175f
    private const val PI_EM = 0.040f
    // the mass-ratio plaque
    private val PLAQ = P3(-0.52f, 0.055f, 0.12f)
    // the phase board (centre; board plane = local z 0)
    private val BOARD = P3(0.37f, 0.16f, -0.22f)
    private const val RB = 0.185f                      // energy-circle radius (phase unit -> metres)
    private const val BW = 0.60f
    private const val BH = 0.54f
    private const val DEPTH = 0.50f                    // 3-D trail depth (collision number axis)
    // chords
    private const val CH_PER = 32
    private const val CH_CHUNKS = 12
    private const val CHORD_MAX = CH_PER * CH_CHUNKS   // 384
    private const val CHORD_W = 0.0030f
    private const val ARC_Q = 200                      // quads per arc-colour mesh
    private const val ARC_MAX = 330                    // above this many arcs: no per-arc colouring
    private const val WEDGE_N = 18

    // ================================================================ colours (sRGB, 3Blue1Brown-like)
    private val WHITE = Rgb(0.96f, 0.97f, 1.0f)
    private val DIM = Rgb(0.55f, 0.60f, 0.70f)
    private val BLUE = Rgb(0.35f, 0.77f, 0.87f)        // 3b1b blue
    private val BROWN = Rgb(0.80f, 0.55f, 0.33f)       // 3b1b brown
    private val GOLD = Rgb(1.0f, 0.80f, 0.30f)
    private val GREEN = Rgb(0.48f, 0.88f, 0.55f)
    private val CHORD = Rgb(0.80f, 0.90f, 1.0f)
    private fun glow(c: Rgb, a: Float) = Mat.Unlit(c, alpha = a, blend = Blend.ADD)
    private fun core(c: Rgb) = Mat.Unlit(c)
    private fun solid(c: Rgb, e: Float = 0.35f) = Mat.Pbr(c, metallic = 0.1f, roughness = 0.35f, emissive = Rgb(c.r * e, c.g * e, c.b * e))
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.32f)
    private val plateOff = Mat.Pbr(Rgb(0.10f, 0.11f, 0.13f), metallic = 0.3f, roughness = 0.35f)
    private val plateOn = Mat.Pbr(Rgb(0.16f, 0.24f, 0.40f), metallic = 0.2f, roughness = 0.30f, emissive = Rgb(0.10f, 0.24f, 0.50f))
    private val pane = Mat.Unlit(Rgb(0.03f, 0.04f, 0.065f), alpha = 0.84f, blend = Blend.TRANSPARENT)

    // ================================================================ ratios
    private val RATIOS = doubleArrayOf(1.0, 1e2, 1e4, 1e6, 1e8, 1e10)
    private val RATIO_KEY = arrayOf("1", "100", "10^4", "10^6", "10^8", "10^{10}")
    private val RATIO_UI = arrayOf("1", "100", "10⁴", "10⁶", "10⁸", "10¹⁰")
    private fun ratio() = RATIOS[ratioIdx]
    /** 10^8 and 10^10: the counter races, chords are sampled — "count only" fast mode. */
    val countOnly get() = ratioIdx >= 4

    // ================================================================ text atlas
    private const val TEX_VERSION = 1
    private const val EM_PX = 64
    private const val PI_STR = "3.14159265358…"
    private fun sp(t: String, c: Rgb = WHITE) = CollideText.Span(t, c)
    private val ATLAS_ITEMS: List<CollideAtlas.Item> by lazy {
        val keys = LinkedHashMap<String, CollideAtlas.Item>()
        fun add(t: String, spans: List<CollideText.Span> = listOf(sp(t)), key: String = t, em: Int = EM_PX) { if (key !in keys) keys[key] = CollideAtlas.Item(key, spans, em) }
        for (d in 0..9) add("$d", em = 96)
        for (k in RATIO_KEY) { add("$k kg"); add("$k : 1") }
        listOf("M : m", "√M·V", "√m·v", "θ", "2θ", "k", "MV^2 + mv^2 = 2E", "MV + mv = p").forEach { add(it) }
        // π with its first `n` significant digits lit (n = 0: none)
        for (n in 0..6) {
            val lit = if (n == 0) "" else PI_STR.substring(0, if (n == 1) 1 else n + 1)
            add("pi$n", listOf(sp("π = ", WHITE), sp(lit, GOLD), sp(PI_STR.substring(lit.length), DIM)), key = "pi$n")
        }
        keys.values.toList()
    }
    private lateinit var atlas: CollideAtlas
    private val textMats = HashMap<Rgb, Mat>()
    private fun textMat(c: Rgb) = textMats.getOrPut(c) { Mat.Unlit(c, tex = atlas.image, blend = Blend.TRANSPARENT) }
    private val decalKey = java.util.IdentityHashMap<Node, String>()

    private fun decal(parent: Node, name: String, key: String, em: Float, tint: Rgb = WHITE, pos: P3 = P3(0f, 0f, 0f), bb: Boolean = false): Node {
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
    enum class Run { READY, RUNNING, DONE }

    /** 0 slow-mo, 1 medium, 2 fast. */
    var speed = 1
    private val RATE = floatArrayOf(0.15f, 0.5f, 1.0f)          // simulated seconds per real second
    private val BURST_S = floatArrayOf(14f, 6f, 2.5f)           // the longest burst lasts about this long
    private val BASE_CAP = intArrayOf(1, 6, 60)                 // collisions per frame at least allowed
    var ratioIdx = 1; private set
    /** 0 closed, 1 flat, 2 3-D. */
    var phaseView = 1; private set
    var state = Run.READY; private set
    private var sim: BlockSim? = null
    /** Completed runs (lesson steps compare against a baseline), and the last one's ratio and count. */
    var runsDone = 0; private set
    var lastRunRatio = -1; private set
    var lastRunCount = 0L; private set
    private var shownCount = 0L
    /** READY after a completed run: keep showing its count and the π reveal. */
    private var keepResult = false
    private var doneT = 0f
    private var slow = 1f                                       // achieved / requested time rate (smoothed)
    private var clock = 0f
    private var flashWallT = 0f
    private var flashBlockT = 0f
    private var pulse = 0f
    private var yaw = 0f
    private var nTotal = 3L
    private var stride = 1L
    private var drawn = 0
    private var prevX = -1.0; private var prevY = 0.0
    private var curX = -1.0; private var curY = 0.0
    private val chA = FloatArray(CHORD_MAX * 2)
    private val chB = FloatArray(CHORD_MAX * 2)
    private val chK = LongArray(CHORD_MAX)
    private val dirty = BooleanArray(CH_CHUNKS)
    private var arcsReady = false
    private var panelEpoch by mutableIntStateOf(0)
    private fun bump() { panelEpoch++ }

    private fun resetState() {
        speed = 1; ratioIdx = 1; phaseView = 1; state = Run.READY; sim = null; keepResult = false
        runsDone = 0; lastRunRatio = -1; lastRunCount = 0L; shownCount = 0L; doneT = 0f; slow = 1f; clock = 0f
        flashWallT = 0f; flashBlockT = 0f; pulse = 0f; yaw = 0f
        nTotal = 3L; stride = 1L; drawn = 0; prevX = -1.0; prevY = 0.0; curX = -1.0; curY = 0.0
        chA.fill(0f); chB.fill(0f); chK.fill(0L); dirty.fill(false); arcsReady = false
        decalKey.clear(); textMats.clear()
    }

    // ================================================================ nodes
    private lateinit var cyl: MeshData
    private lateinit var ball: MeshData
    private lateinit var cube: MeshData
    private lateinit var glowQuad: MeshData
    private lateinit var small: Node
    private lateinit var smallBox: Node
    private lateinit var smallGlow: Node
    private lateinit var big: Node
    private lateinit var bigBox: Node
    private lateinit var bigGlow: Node
    private lateinit var smallLbl: Node
    private lateinit var bigLbl: Node
    private lateinit var flashWall: Node
    private lateinit var flashBlock: Node
    private lateinit var digits: Array<Node>
    private lateinit var cntGlow: Node
    private lateinit var piLine: Node
    private lateinit var plaque: Node
    private lateinit var plaqueTxt: Node
    // phase board
    private lateinit var rig: Node
    private lateinit var inner: Node
    private lateinit var tab: Node
    private lateinit var tabPlate: Node
    private lateinit var chordMd: Array<MeshData>
    private lateinit var chordNodes: Array<Node>
    private lateinit var arcMd: Array<MeshData>
    private lateinit var arcNodes: Array<Node>
    private lateinit var plainArc: Node
    private lateinit var wedgeMd: MeshData
    private lateinit var wedge: Node
    private lateinit var mLines: Array<Node>
    private lateinit var activeLine: Node
    private lateinit var statePt: Node
    private lateinit var stateGlow: Node
    private lateinit var depthAxis: Node
    private lateinit var depthLbl: Node
    private lateinit var thetaLbl: Node
    private lateinit var arcLbl: Node

    // ================================================================ small node helpers
    private fun Node.at(x: Float, y: Float, z: Float) { val p = pos; if (p.x != x || p.y != y || p.z != z) pos = P3(x, y, z) }
    private fun Node.dims(x: Float, y: Float, z: Float) { val s = scale; if (s.x != x || s.y != y || s.z != z) scale = P3(x, y, z) }
    private fun Node.turn(q: Q) { if (rot != q) rot = q }
    private fun Node.show(v: Boolean) { if (visible != v) visible = v }

    /** A unit cylinder (along Y) from a to b, radius r; hidden when degenerate. */
    private fun Node.span(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, r: Float) {
        val dx = x1 - x0; val dy = y1 - y0; val dz = z1 - z0
        val l = sqrt(dx * dx + dy * dy + dz * dz)
        if (l < 1e-5f) { show(false); return }
        show(true)
        at((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2)
        dims(r, l, r)
        turn(Q.fromTo(0f, 1f, 0f, dx, dy, dz))
    }

    private fun ringMesh(r: Float, tube: Float, sides: Int = 6, n: Int = 120): MeshData {
        val p = FloatArray(n * 3)
        for (k in 0 until n) { val a = 2 * PI * k / n; p[k * 3] = (r * cos(a)).toFloat(); p[k * 3 + 1] = (r * sin(a)).toFloat() }
        return MeshGen.tube(p, tube, sides, true)
    }

    private fun radialTex(): Image = OptGeo.lumTex(64, 64) { u, v ->
        val dx = (u - 0.5f) * 2f; val dy = (v - 0.5f) * 2f; val r2 = dx * dx + dy * dy
        if (r2 >= 1f) 0f else (1f - r2) * (1f - r2)
    }

    /** [n] double-sided quads, positions to be written in place. */
    private fun quads(n: Int): MeshData {
        val nrm = FloatArray(n * 12); for (i in 0 until n * 4) nrm[i * 3 + 2] = 1f
        val idx = IntArray(n * 12)
        for (q in 0 until n) {
            val a = q * 4; val t = q * 12
            idx[t] = a; idx[t + 1] = a + 2; idx[t + 2] = a + 1; idx[t + 3] = a + 1; idx[t + 4] = a + 2; idx[t + 5] = a + 3
            idx[t + 6] = a; idx[t + 7] = a + 1; idx[t + 8] = a + 2; idx[t + 9] = a + 1; idx[t + 10] = a + 3; idx[t + 11] = a + 2
        }
        return MeshData(FloatArray(n * 12), nrm, FloatArray(n * 8), idx)
    }

    /** Write quad [q] of [md] as a ribbon of width [w] from a to b (width ⟂ to the segment, in the xy plane). */
    private fun quadInto(md: MeshData, q: Int, ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, w: Float) {
        val dx = bx - ax; val dy = by - ay; val l = sqrt(dx * dx + dy * dy)
        val nx = if (l < 1e-7f) 0f else -dy / l * w / 2; val ny = if (l < 1e-7f) w / 2 else dx / l * w / 2
        val v = q * 4
        OptGeo.setPos(md, v, ax + nx, ay + ny, az); OptGeo.setPos(md, v + 1, ax - nx, ay - ny, az)
        OptGeo.setPos(md, v + 2, bx + nx, by + ny, bz); OptGeo.setPos(md, v + 3, bx - nx, by - ny, bz)
    }
    private fun quadClear(md: MeshData, q: Int) { for (i in q * 12 until q * 12 + 12) md.pos[i] = 0f }

    // ================================================================ build

    override fun buildSpec(cacheDir: File): Node {
        resetState()
        val imgs = BakeCache.load(cacheDir, "collide_text", TEX_VERSION) { listOf(CollideAtlas.bakeImage(ATLAS_ITEMS)) }
        atlas = CollideAtlas.of(imgs[0], ATLAS_ITEMS)
        cyl = MeshGen.cylinder(1f, 1f, 10)
        ball = MeshGen.sphere(1f, 12, 24, MeshGen.SphereUv.EQUIRECT)
        cube = MeshGen.box(1f, 1f, 1f)
        glowQuad = OptGeo.quad(1f, 1f)
        val rtex = radialTex()

        val root = Node("CollideRoot")
        root.add("Plinth", MeshGen.box(1.74f, 0.035f, 0.92f), Mat.Pbr(Rgb(0.10f, 0.105f, 0.12f), metallic = 0.2f, roughness = 0.5f), P3(0f, PLINTH_Y, -0.04f))
        root.add("PlinthBand", MeshGen.box(1.75f, 0.008f, 0.93f), alu, P3(0f, PLINTH_Y, -0.04f))
        root.add("Glow", glowQuad, Mat.Unlit(Rgb(0.45f, 0.70f, 1.0f), alpha = 0.18f, tex = rtex, blend = Blend.ADD)).also {
            it.rot = Q.axisAngle(1f, 0f, 0f, -PI.toFloat() / 2); it.scale = P3(1.40f, 0.70f, 1f); it.pos = P3(0f, PLINTH_Y + 0.019f, 0.05f)
        }
        buildTrack(root, rtex)
        buildCounter(root, rtex)
        buildBoard(root, rtex)
        // the opening state: a 100 : 1 run already played (the counter reads 31, the board shows its 31 chords);
        // the guided track resets to 1 : 1 at its first step
        setRatio(1); launch(); finishNow()
        update(0f)
        return root
    }

    private fun buildTrack(root: Node, rtex: Image) {
        val tl = (TRACK_END + 0.02).toFloat()
        val cx = WALL_X + tl / 2
        // the rail: a dark bed with two glowing edges
        root.add("RailBed", MeshGen.box(tl, 0.012f, 0.16f), Mat.Pbr(Rgb(0.07f, 0.08f, 0.10f), metallic = 0.4f, roughness = 0.4f), P3(cx, TRACK_Y - 0.006f, TRACK_Z))
        root.add("RailFront", cyl, core(BLUE)).span(WALL_X, TRACK_Y + 0.001f, TRACK_Z + 0.081f, WALL_X + tl, TRACK_Y + 0.001f, TRACK_Z + 0.081f, 0.0025f)
        root.add("RailBack", cyl, glow(BLUE, 0.6f)).span(WALL_X, TRACK_Y + 0.001f, TRACK_Z - 0.081f, WALL_X + tl, TRACK_Y + 0.001f, TRACK_Z - 0.081f, 0.0020f)
        root.add("RailGlow", glowQuad, Mat.Unlit(BLUE, alpha = 0.16f, tex = rtex, blend = Blend.ADD)).also {
            it.rot = Q.axisAngle(1f, 0f, 0f, -PI.toFloat() / 2); it.scale = P3(tl * 1.05f, 0.30f, 1f); it.pos = P3(cx, TRACK_Y + 0.002f, TRACK_Z)
        }
        // distance ticks every 0.1 m
        for (k in 1..15) {
            val x = WALL_X + k * 0.1f
            root.add("Tick$k", cyl, glow(WHITE, if (k % 5 == 0) 0.55f else 0.25f)).span(x, TRACK_Y + 0.001f, TRACK_Z + 0.070f, x, TRACK_Y + 0.001f, TRACK_Z + 0.081f, 0.0012f)
        }
        // the wall: a dark slab with a glowing face
        root.add("Wall", MeshGen.box(0.03f, 0.27f, 0.20f), Mat.Pbr(Rgb(0.20f, 0.22f, 0.26f), metallic = 0.6f, roughness = 0.35f), P3(WALL_X - 0.015f, TRACK_Y + 0.135f, TRACK_Z))
        root.add("WallFace", cyl, core(WHITE)).span(WALL_X + 0.001f, TRACK_Y, TRACK_Z + 0.10f, WALL_X + 0.001f, TRACK_Y + 0.27f, TRACK_Z + 0.10f, 0.0022f)
        root.add("WallGlow", glowQuad, Mat.Unlit(WHITE, alpha = 0.12f, tex = rtex, blend = Blend.ADD)).also { it.pos = P3(WALL_X + 0.002f, TRACK_Y + 0.13f, TRACK_Z); it.rot = Q.axisAngle(0f, 1f, 0f, PI.toFloat() / 2); it.scale = P3(0.24f, 0.36f, 1f) }
        // the blocks (parents unscaled -> clean pinch spheres; the box child is scaled)
        small = root.add(Node("SmallBlock"))
        smallBox = small.add("SmallBox", cube, solid(BROWN))
        smallGlow = small.add("SmallGlow", glowQuad, Mat.Unlit(BROWN, alpha = 0.35f, tex = rtex, blend = Blend.ADD)).also { it.billboard = true }
        big = root.add(Node("BigBlock")).also { it.tapRadius = 0.10f }
        bigBox = big.add("BigBox", cube, solid(BLUE))
        bigGlow = big.add("BigGlow", glowQuad, Mat.Unlit(BLUE, alpha = 0.30f, tex = rtex, blend = Blend.ADD)).also { it.billboard = true }
        smallLbl = decal(root, "SmallLbl", "1 kg", 0.024f, BROWN, bb = true)
        bigLbl = decal(root, "BigLbl", "100 kg", 0.028f, BLUE, bb = true)
        flashWall = root.add("FlashWall", glowQuad, Mat.Unlit(Rgb(1f, 0.95f, 0.80f), alpha = 0.9f, tex = rtex, blend = Blend.ADD)).also { it.billboard = true; it.uniformScale(0.001f) }
        flashBlock = root.add("FlashBlock", glowQuad, Mat.Unlit(Rgb(1f, 0.95f, 0.80f), alpha = 0.9f, tex = rtex, blend = Blend.ADD)).also { it.billboard = true; it.uniformScale(0.001f) }
        // the mass-ratio plaque
        plaque = root.add(Node("RatioPlaque")).also { it.pos = PLAQ; it.tapRadius = 0.07f }
        plaque.add("PlaquePlate", MeshGen.box(0.24f, 0.10f, 0.012f), plateOn, P3(0f, 0f, -0.012f))
        decal(plaque, "PlaqueHead", "M : m", 0.024f, DIM, P3(0f, 0.031f, -0.004f))
        plaqueTxt = decal(plaque, "PlaqueTxt", "100 : 1", 0.040f, WHITE, P3(0f, -0.012f, -0.004f))
    }

    private fun buildCounter(root: Node, rtex: Image) {
        cntGlow = root.add("CountGlow", glowQuad, Mat.Unlit(GOLD, alpha = 0.22f, tex = rtex, blend = Blend.ADD)).also { it.pos = P3(CNT_X + 0.18f, CNT_Y + 0.03f, CNT_Z - 0.01f); it.scale = P3(0.50f, 0.20f, 1f) }
        digits = Array(6) { k -> decal(root, "Digit$k", "0", CNT_EM, GOLD) }
        piLine = decal(root, "PiLine", "pi0", PI_EM, WHITE)
    }

    private fun buildBoard(root: Node, rtex: Image) {
        rig = root.add(Node("PhaseRig")).also { it.pos = BOARD }
        rig.add("BoardPane", OptGeo.quad(BW, BH), pane, P3(0f, 0f, -0.006f))
        rig.add("BoardFrame", ringMesh(1f, 0.004f, 4, 4), glow(BLUE, 0.35f), P3(0f, 0f, -0.004f)).also {
            it.rot = Q.axisAngle(0f, 0f, 1f, PI.toFloat() / 4); it.scale = P3(BW / 1.414f, BH / 1.414f, 1f)
        }
        // the tab you pinch to open / lift / close the board
        tab = rig.add(Node("PhaseBoard")).also { it.pos = P3(0.225f, 0.205f, 0.004f); it.tapRadius = 0.06f }
        tabPlate = tab.add("TabPlate", MeshGen.box(0.11f, 0.075f, 0.010f), plateOff, P3(0f, 0f, -0.006f))
        tab.add("TabRing", ringMesh(0.024f, 0.0018f, 5, 48), glow(GOLD, 0.9f), P3(0f, 0f, 0.001f))
        tab.add("TabChord1", cyl, core(CHORD)).span(-0.018f, 0.016f, 0.001f, -0.018f, -0.016f, 0.001f, 0.0016f)
        tab.add("TabChord2", cyl, core(CHORD)).span(-0.018f, -0.016f, 0.001f, 0.005f, 0.023f, 0.001f, 0.0016f)

        inner = rig.add(Node("PhaseInner"))
        inner.add("AxisX", cyl, core(Rgb(0.70f, 0.74f, 0.82f))).span(-RB * 1.30f, 0f, 0f, RB * 1.32f, 0f, 0f, 0.0016f)
        inner.add("AxisY", cyl, core(Rgb(0.70f, 0.74f, 0.82f))).span(0f, -RB * 1.30f, 0f, 0f, RB * 1.30f, 0f, 0.0016f)
        decal(inner, "LblX", "√M·V", 0.024f, BLUE, P3(RB * 1.30f, -0.024f, 0.002f))
        decal(inner, "LblY", "√m·v", 0.024f, BROWN, P3(0.050f, RB * 1.30f, 0.002f))
        inner.add("Energy", ringMesh(RB, 0.0022f), core(GOLD))
        inner.add("EnergyGlow", ringMesh(RB, 0.007f, 4), glow(GOLD, 0.18f))
        decal(inner, "LblE", "MV^2 + mv^2 = 2E", 0.023f, GOLD, P3(-0.13f, -0.238f, 0.002f))
        decal(inner, "LblP", "MV + mv = p", 0.023f, GREEN, P3(0.16f, -0.238f, 0.002f))
        mLines = Array(9) { inner.add("MLine$it", cyl, glow(GREEN, 0.22f)) }
        activeLine = inner.add("MActive", cyl, glow(GREEN, 0.75f))
        // end zone wedge (0 ≤ angle ≤ θ) and the equal arcs
        wedgeMd = run {
            val n = WEDGE_N + 2
            val idx = IntArray(WEDGE_N * 6)
            for (k in 0 until WEDGE_N) { idx[k * 6] = 0; idx[k * 6 + 1] = k + 1; idx[k * 6 + 2] = k + 2; idx[k * 6 + 3] = 0; idx[k * 6 + 4] = k + 2; idx[k * 6 + 5] = k + 1 }
            MeshData(FloatArray(n * 3), FloatArray(n * 3) { if (it % 3 == 2) 1f else 0f }, FloatArray(n * 2), idx)
        }
        wedge = inner.add("EndZone", wedgeMd, glow(GREEN, 0.30f))
        arcMd = Array(2) { quads(ARC_Q) }
        arcNodes = arrayOf(inner.add("ArcsA", arcMd[0], core(BLUE)), inner.add("ArcsB", arcMd[1], core(BROWN)))
        plainArc = inner.add("ArcsPlain", ringMesh(RB, 0.0055f, 4), glow(Rgb(0.6f, 0.75f, 1f), 0.55f))
        thetaLbl = decal(inner, "LblTheta", "θ", 0.026f, GREEN)
        arcLbl = decal(inner, "LblArc", "2θ", 0.024f, BLUE)
        chordMd = Array(CH_CHUNKS) { quads(CH_PER) }
        chordNodes = Array(CH_CHUNKS) { c -> inner.add("Chords$c", chordMd[c], glow(CHORD, 0.85f)) }
        statePt = inner.add("StatePt", ball, Mat.Unlit(Rgb(1f, 0.97f, 0.88f))).also { it.uniformScale(0.010f) }
        stateGlow = inner.add("StateGlow", glowQuad, Mat.Unlit(GOLD, alpha = 0.6f, tex = rtex, blend = Blend.ADD)).also { it.billboard = true; it.uniformScale(0.055f) }
        depthAxis = inner.add("DepthAxis", cyl, glow(WHITE, 0.45f)).also { it.span(0f, 0f, 0f, 0f, 0f, DEPTH + 0.03f, 0.0014f) }
        depthLbl = decal(inner, "LblK", "k", 0.026f, WHITE, P3(0.02f, 0.02f, DEPTH + 0.04f), bb = true)
    }

    // ================================================================ ratio-dependent geometry

    private fun bigSize(): Float = min(W1.toFloat() * cbrt(ratio()).toFloat(), BIG_CAP)

    /** Many chords pile up (ADD): dim them so the board never burns out (less so in 3-D, where they spread). */
    private fun chordAlpha() {
        val expect = min((nTotal / stride + 1).toFloat(), CHORD_MAX.toFloat())
        val ca = if (expect <= 40f) 0.9f else (0.9f * sqrt(20f / expect) * (if (phaseView == 2) 1.8f else 1f) * 20f).toInt() / 20f
        for (cn in chordNodes) cn.setMat(glow(CHORD, min(0.9f, ca)))
    }

    private fun rebuildForRatio() {
        val r = ratio(); val th = Collisions.theta(r)
        nTotal = Collisions.countByAngle(r)
        stride = max(1L, ceil(nTotal.toDouble() / CHORD_MAX).toLong()).let { if (it > 1 && it % 2 == 0L) it + 1 else it }
        chordAlpha()
        // momentum lines: n̂·P = ρ with n̂ = (cos θ, sin θ); segment inside a disk of radius 1.25
        val c = cos(th).toFloat(); val s = sin(th).toFloat()
        for (i in 0 until 9) {
            val rho = (i - 4) * 0.25f
            val h = sqrt(max(0f, 1.25f * 1.25f - rho * rho))
            val px = rho * c; val py = rho * s
            mLines[i].span((px - h * s) * RB, (py + h * c) * RB, -0.001f, (px + h * s) * RB, (py - h * c) * RB, -0.001f, 0.0011f)
        }
        // end-zone wedge, drawn at least 4° wide (marked θ)
        val a1 = max(th, 0.07)
        OptGeo.setPos(wedgeMd, 0, 0f, 0f, -0.0005f)
        for (k in 0..WEDGE_N) {
            val a = a1 * k / WEDGE_N
            OptGeo.setPos(wedgeMd, k + 1, (RB * cos(a)).toFloat(), (RB * sin(a)).toFloat(), -0.0005f)
        }
        meshEdited(wedgeMd)
        thetaLbl.at((RB * 1.14f * cos(a1 / 2)).toFloat(), (RB * 1.14f * sin(a1 / 2)).toFloat() + 0.008f, 0.002f)
        // equal arcs (blue / brown alternating): upper chain π − 2jθ, lower chain −(π − 2jθ)
        val upper = (nTotal / 2).toInt(); val lower = ((nTotal + 1) / 2).toInt()
        val arcs = upper + max(0, lower - 1)
        arcsReady = arcs in 1..ARC_MAX
        val q = intArrayOf(0, 0)
        for (m in 0..1) for (i in 0 until ARC_Q) quadClear(arcMd[m], i)
        if (arcsReady) {
            fun arc(a0: Double, a1: Double, col: Int) {
                val n = max(1, ceil(abs(a1 - a0) / 0.07).toInt())
                for (k in 0 until n) {
                    if (q[col] >= ARC_Q) return
                    val u = a0 + (a1 - a0) * k / n; val w = a0 + (a1 - a0) * (k + 1) / n
                    // a band just outside the energy circle
                    val r0 = RB + 0.0060f; val r1 = RB + 0.0140f
                    val v = q[col] * 4; val md = arcMd[col]
                    OptGeo.setPos(md, v, (r1 * cos(u)).toFloat(), (r1 * sin(u)).toFloat(), 0.001f)
                    OptGeo.setPos(md, v + 1, (r0 * cos(u)).toFloat(), (r0 * sin(u)).toFloat(), 0.001f)
                    OptGeo.setPos(md, v + 2, (r1 * cos(w)).toFloat(), (r1 * sin(w)).toFloat(), 0.001f)
                    OptGeo.setPos(md, v + 3, (r0 * cos(w)).toFloat(), (r0 * sin(w)).toFloat(), 0.001f)
                    q[col]++
                }
            }
            for (j in 0 until upper) arc(PI - 2 * j * th, PI - 2 * (j + 1) * th, j % 2)
            for (j in 1 until lower) arc(-(PI - 2 * j * th), -(PI - 2 * (j + 1) * th), (j + 1) % 2)
        }
        meshEdited(arcMd[0]); meshEdited(arcMd[1])
        arcLbl.at((RB * 1.16f * cos(PI - th)).toFloat() - 0.012f, (RB * 1.16f * sin(PI - th)).toFloat() + 0.006f, 0.002f)
        // the blocks' sizes and mass labels
        val bs = bigSize()
        smallBox.dims(W1.toFloat(), W1.toFloat(), W1.toFloat()); smallBox.at(0f, 0f, 0f)
        smallGlow.uniformScale(0.13f)
        bigBox.dims(bs, bs, bs)
        bigGlow.uniformScale(bs * 2.6f)
        big.tapRadius = max(0.06f, bs * 0.75f)
        setDecal(bigLbl, "${RATIO_KEY[ratioIdx]} kg", 0.028f)
        setDecal(plaqueTxt, "${RATIO_KEY[ratioIdx]} : 1", 0.040f)
    }

    // ================================================================ chords

    private fun depthOf(k: Long): Float = if (phaseView == 2) DEPTH * k.toFloat() / nTotal.coerceAtLeast(1L).toFloat() else 0f

    private fun writeChord(i: Int) {
        val k = chK[i]
        quadInto(chordMd[i / CH_PER], i % CH_PER, chA[i * 2] * RB, chA[i * 2 + 1] * RB, depthOf(k - 1) + 0.0015f,
            chB[i * 2] * RB, chB[i * 2 + 1] * RB, depthOf(k) + 0.0015f, if (nTotal > 40) CHORD_W * 0.6f else CHORD_W)
        dirty[i / CH_PER] = true
    }

    private fun clearChords() {
        drawn = 0
        for (c in 0 until CH_CHUNKS) { for (i in 0 until CH_PER) quadClear(chordMd[c], i); dirty[c] = true }
    }

    private fun rewriteChords() { for (c in 0 until CH_CHUNKS) dirty[c] = true; for (i in 0 until drawn) writeChord(i) }

    private fun onEvent(kind: Int) {
        val s = sim ?: return
        if (kind == 2) flashWallT = FLASH_S else flashBlockT = FLASH_S
        pulse = 1f
        val p = Collisions.phase(s.ratio, s.vs, s.vb, V0)
        val k = s.count
        if ((k - 1) % stride == 0L && drawn < CHORD_MAX) {
            chA[drawn * 2] = prevX.toFloat(); chA[drawn * 2 + 1] = prevY.toFloat()
            chB[drawn * 2] = p[0].toFloat(); chB[drawn * 2 + 1] = p[1].toFloat()
            chK[drawn] = k
            writeChord(drawn); drawn++
        }
        prevX = p[0]; prevY = p[1]; curX = p[0]; curY = p[1]
    }
    private const val FLASH_S = 0.16f

    // ================================================================ actions (pinch, panel, lesson, tests)

    fun setRatio(i: Int) {
        ratioIdx = i.coerceIn(0, RATIOS.size - 1)
        state = Run.READY; sim = null; shownCount = 0L; doneT = 0f; keepResult = false
        rebuildForRatio(); clearChords()
        prevX = -1.0; prevY = 0.0; curX = -1.0; curY = 0.0
        bump()
    }

    fun setPhase(v: Int) {
        val old = phaseView
        phaseView = v.coerceIn(0, 2)
        if ((old == 2) != (phaseView == 2)) { rewriteChords(); chordAlpha() }
        bump()
    }

    /** Launch the big block (from the start positions; a running run restarts). */
    fun launch() {
        sim = BlockSim(ratio(), W1, X1, X2, V0)
        state = Run.RUNNING; shownCount = 0L; doneT = 0f; slow = 1f; keepResult = false
        clearChords()
        prevX = -1.0; prevY = 0.0; curX = -1.0; curY = 0.0
        bump()
    }

    /** Play the current run to the end at once (review renders, the opening state). */
    fun finishNow() {
        val s = sim ?: return
        if (state != Run.RUNNING) return
        while (!s.done) s.advance(s.next().first, Int.MAX_VALUE) { onEvent(it) }
        flashWallT = 0f; flashBlockT = 0f
        markDone(s)
    }

    private fun markDone(s: BlockSim) {
        state = Run.DONE; doneT = 0f
        runsDone++; lastRunRatio = ratioIdx; lastRunCount = s.count
        bump()
    }

    private fun respawn() { state = Run.READY; sim = null; keepResult = true; bump() }

    private fun capFor(): Int = max(BASE_CAP[speed], ceil(nTotal / (BURST_S[speed] * 60.0)).toInt())

    // ================================================================ per frame

    override fun update(dt: Float) {
        clock += dt
        // ---- the simulation
        val s = sim
        if (s != null && state != Run.READY) {
            val want = dt * RATE[speed]
            val t0 = s.t
            s.advance(s.t + want, capFor()) { onEvent(it) }
            if (want > 0f) slow += ((((s.t - t0) / want).toFloat().coerceIn(0f, 1f)) - slow) * min(1f, dt * 6f)
            shownCount = s.count
            if (state == Run.RUNNING && s.done) markDone(s)
            if (state == Run.DONE) {
                doneT += dt
                if ((s.x2 > TRACK_END && doneT > 1f) || doneT > 25f) respawn()
            }
        }
        flashWallT = max(0f, flashWallT - dt); flashBlockT = max(0f, flashBlockT - dt); pulse = max(0f, pulse - dt * 4f)

        // ---- blocks
        val x1 = sim?.x1 ?: X1; val x2 = sim?.x2 ?: X2
        val bs = bigSize(); val ss = W1.toFloat()
        val sx = WALL_X + x1.toFloat() + ss / 2; val bx = WALL_X + x2.toFloat() + bs / 2
        small.at(sx, TRACK_Y + ss / 2, TRACK_Z); big.at(bx, TRACK_Y + bs / 2, TRACK_Z)
        val sOn = x1 < TRACK_END; val bOn = x2 < TRACK_END
        small.show(sOn); big.show(bOn); smallLbl.show(sOn); bigLbl.show(bOn)
        smallLbl.at(sx, TRACK_Y + ss + 0.030f, TRACK_Z)
        bigLbl.at(bx, TRACK_Y + bs + 0.032f, TRACK_Z)
        val fy = TRACK_Y + ss / 2
        flashWall.at(WALL_X + 0.004f, fy, TRACK_Z + 0.03f); flashWall.uniformScale(0.001f + 0.11f * flashWallT / FLASH_S)
        flashBlock.at(WALL_X + x2.toFloat(), fy, TRACK_Z + 0.03f); flashBlock.uniformScale(0.001f + 0.12f * flashBlockT / FLASH_S)

        // ---- counter
        val n = if (state == Run.READY && keepResult) lastRunCount else shownCount
        val str = n.toString()
        val dw = CNT_EM * 0.61f
        for (k in 0 until 6) {
            val on = k < str.length
            digits[k].show(on)
            if (on) { setDecal(digits[k], str[k].toString(), CNT_EM); digits[k].at(CNT_X + dw * (k + 0.5f), CNT_Y, CNT_Z) }
        }
        cntGlow.dims(0.10f + dw * str.length, 0.17f + 0.04f * pulse, 1f)
        cntGlow.at(CNT_X + dw * str.length / 2, CNT_Y + 0.03f, CNT_Z - 0.01f)
        val revealed = state == Run.DONE || (state == Run.READY && keepResult)
        piLine.show(revealed)
        if (revealed) {
            val m = Collisions.piDigitsMatched(n)
            val key = "pi${m.coerceIn(0, 6)}"
            setDecal(piLine, key, PI_EM)
            piLine.at(CNT_X + decalWidth(key, PI_EM) / 2 - 0.004f, PI_Y, CNT_Z)
        }

        // ---- phase board
        inner.show(phaseView > 0)
        tabPlate.setMat(if (phaseView > 0) plateOn else plateOff)
        val yawT = if (phaseView == 2) -65f else 0f
        yaw += (yawT - yaw) * min(1f, dt * 4f)
        if (dt == 0f) yaw = yawT
        rig.turn(Q.axisAngle(0f, 1f, 0f, Math.toRadians(yaw.toDouble()).toFloat()))
        val px = curX.toFloat() * RB; val py = curY.toFloat() * RB
        val pz = depthOf(sim?.count ?: 0L) + 0.003f
        statePt.at(px, py, pz); stateGlow.at(px, py, pz + 0.002f)
        stateGlow.uniformScale(0.050f + 0.03f * pulse)
        // the momentum line through the current state (the next block chord lies on it)
        run {
            val th = Collisions.theta(ratio()); val c = cos(th).toFloat(); val sn = sin(th).toFloat()
            val rho = curX.toFloat() * c + curY.toFloat() * sn
            val h = sqrt(max(0f, 1.25f * 1.25f - rho * rho))
            activeLine.span((rho * c - h * sn) * RB, (rho * sn + h * c) * RB, pz - 0.002f, (rho * c + h * sn) * RB, (rho * sn - h * c) * RB, pz - 0.002f, 0.0016f)
        }
        val ended = revealed
        arcNodes[0].show(ended && arcsReady); arcNodes[1].show(ended && arcsReady); arcLbl.show(ended && arcsReady)
        plainArc.show(ended && !arcsReady)
        depthAxis.show(phaseView == 2); depthLbl.show(phaseView == 2)
        // upload at most two dirty chord chunks per frame (≤ 256 vertices)
        var up = 0
        for (c in 0 until CH_CHUNKS) if (dirty[c] && up < 2) { meshEdited(chordMd[c]); dirty[c] = false; up++ }

        readoutText = readout()
        updateLabels(revealed)
    }

    // ================================================================ readout + labels
    private fun num(v: Double, d: Int): String {
        val r = if (abs(v) < 0.5 * 10.0.pow(-d.toDouble())) 0.0 else v
        return String.format(Locale.US, "%.${d}f", r).replace('-', '−')
    }
    private fun signed(v: Double, d: Int) = (if (v > 0.5 * 10.0.pow(-d.toDouble())) "+" else "") + num(v, d)
    private fun ratioText() = "${RATIO_UI[ratioIdx]} : 1"
    private fun thetaText(th: Double): String {
        if (th >= 0.001) return num(th, 4)
        val e = kotlin.math.floor(kotlin.math.log10(th)).toInt(); val m = th / 10.0.pow(e)
        val sup = "⁰¹²³⁴⁵⁶⁷⁸⁹"
        return num(m, 2) + "×10⁻" + (-e).toString().map { sup[it - '0'] }.joinToString("")
    }

    private fun readout(): String {
        val s = sim
        val n = if (state == Run.READY && keepResult) lastRunCount else shownCount
        val v = (s?.vs ?: 0.0) / abs(V0); val vb = (s?.vb ?: (if (state == Run.READY) 0.0 else -1.0)) / abs(V0)
        val st = when (state) { Run.READY -> tr("待发射", "ready"); Run.RUNNING -> tr("进行中", "running"); Run.DONE -> tr("结束", "done") }
        val l1 = tr("质量比 M:m = ${ratioText()} · 碰撞 $n 次（$st）· 小块 v = ${signed(v, 2)} · 大块 V = ${signed(vb, 3)}（单位 |V₀|）",
            "Mass ratio M:m = ${ratioText()} · $n collisions ($st) · small v = ${signed(v, 2)} · big V = ${signed(vb, 3)} (units of |V₀|)")
        val r = ratio(); val th = Collisions.theta(r); val q = Collisions.piOverTheta(r)
        val samp = if (stride > 1) tr(" · 弦每 $stride 条画 1 条", " · 1 chord in $stride drawn") else ""
        val l2 = tr("θ = arctan√(m/M) = ${thetaText(th)} rad · π/θ = ${num(q, 2)} → π 以下放得下 $nTotal 个 θ = 总次数$samp",
            "θ = arctan√(m/M) = ${thetaText(th)} rad · π/θ = ${num(q, 2)} → $nTotal steps of θ fit below π = the total$samp")
        val l3 = when {
            state == Run.RUNNING && slow < 0.6f -> tr("碰撞太密：每帧最多算 ${capFor()} 次，时间自动慢放 ×1/${num(1.0 / max(slow, 0.001f), 0)} —— 一次都不跳过",
                "Collisions too dense: at most ${capFor()} per frame, time slows down ×1/${num(1.0 / max(slow, 0.001f), 0)} — none is skipped")
            state == Run.RUNNING -> tr("每次碰撞 = 相空间里一条弦：撞墙是竖直弦，块撞块沿动量线（斜率 −√(M/m)）",
                "Each collision = one chord in phase space: the wall gives a vertical chord, block on block runs along a momentum line (slope −√(M/m))")
            countOnly -> tr("只计数快进：M/m = 100ⁿ → θ ≈ 10⁻ⁿ → 次数 ≈ π·10ⁿ，π 的前 n+1 位", "Count-only fast mode: M/m = 100ⁿ → θ ≈ 10⁻ⁿ → count ≈ π·10ⁿ, the first n+1 digits of π")
            else -> tr("弦把能量圆切成相等的弧 2θ；走进 0~θ 的终点区就不再相撞：次数 = π 里能放下几个 θ",
                "The chords cut the energy circle into equal arcs 2θ; once in the 0…θ end zone nothing collides again: count = how many θ fit in π")
        }
        return "$l1\n$l2\n$l3"
    }

    private fun boardWorld(x: Float, y: Float, z: Float): P3 {
        val r = Q.axisAngle(0f, 1f, 0f, Math.toRadians(yaw.toDouble()).toFloat()).rotate(floatArrayOf(x, y, z))
        return P3(BOARD.x + r[0], BOARD.y + r[1], BOARD.z + r[2])
    }

    private fun updateLabels(revealed: Boolean) {
        labels.clear()
        labels += LabelSpec(tr("碰撞次数", "Collisions"), P3(CNT_X + 0.10f, CNT_Y + 0.10f, CNT_Z))
        if (phaseView > 0) {
            labels += LabelSpec(tr("相空间：速度 × √质量", "Phase space: velocity × √mass"), boardWorld(0.02f, BH / 2 + 0.035f, 0f))
            labels += LabelSpec(tr("终点区", "End zone"), boardWorld(RB * 1.25f, 0.045f, 0.01f))
        } else labels += LabelSpec(tr("相空间（关）", "Phase space (closed)"), boardWorld(0f, 0.05f, 0f))
        if (phaseView == 2) labels += LabelSpec(tr("纵深 = 第 k 次碰撞", "Depth = collision k"), boardWorld(0f, -0.05f, DEPTH + 0.05f))
        if (state == Run.RUNNING && slow < 0.6f) labels += LabelSpec(tr("自动慢放 ×1/${num(1.0 / max(slow, 0.001f), 0)}", "Auto slow-mo ×1/${num(1.0 / max(slow, 0.001f), 0)}"),
            P3(WALL_X + 0.16f, TRACK_Y + 0.25f, TRACK_Z))
        else if (revealed) labels += LabelSpec(tr("= π 的前 ${Collisions.piDigitsMatched(lastRunCount.coerceAtLeast(shownCount))} 位", "= first ${Collisions.piDigitsMatched(lastRunCount.coerceAtLeast(shownCount))} digits of π"),
            P3(CNT_X + 0.52f, PI_Y + 0.06f, CNT_Z))
    }

    // ================================================================ interaction

    private val TARGETS = listOf("BigBlock", "RatioPlaque", "PhaseBoard")

    override val tapTargets: List<TapTarget> get() = listOf(
        TapTarget("BigBlock", "大方块 → 发射", "Big block → launch") { state == Run.RUNNING },
        TapTarget("RatioPlaque", "质量比牌 → 换", "Ratio plaque → next"),
        TapTarget("PhaseBoard", "相空间板 → 开/立体/关", "Phase board → open/3-D/close") { phaseView > 0 },
    )

    override fun isTarget(name: String) = name in TARGETS

    override fun onTap(name: String) {
        when (name) {
            "BigBlock" -> launch()
            "RatioPlaque" -> setRatio((ratioIdx + 1) % RATIOS.size)
            "PhaseBoard" -> setPhase((phaseView + 1) % 3)
        }
    }

    override fun tapMessage(name: String): String? = when (name) {
        "BigBlock" -> tr("发射！${RATIO_UI[ratioIdx]} 倍质量的大块撞过去 —— 数一数", "Launched! The ${RATIO_UI[ratioIdx]}× block slides in — count the clacks")
        "RatioPlaque" -> if (countOnly) tr("M:m = ${ratioText()}（只计数快进）", "M:m = ${ratioText()} (count-only fast mode)") else tr("M:m = ${ratioText()}", "M:m = ${ratioText()}")
        "PhaseBoard" -> when (phaseView) {
            1 -> tr("相空间打开：能量 = 圆，动量 = 斜线", "Phase space open: energy = circle, momentum = sloped lines")
            2 -> tr("立体：纵深 = 第几次碰撞，弦绕着圆柱走", "3-D: depth = collision number, the chords wind round a cylinder")
            else -> tr("相空间已收起", "Phase space closed")
        }
        else -> null
    }

    // ================================================================ guided track (导学)

    private var base = 0

    // ---- background card (📖): context for a viewer who has never met this topic
    override val about = About(listOf(
        About.Page(
            Bi("这个实验在做什么？", "What is this experiment?"),
            Bi("一条发光轨道，左边是墙。棕色小方块（1 千克）停在墙前，蓝色大方块从右边滑过来。捏大方块发射，金色计数器数一共撞了几次。捏质量比牌，让大块重 1 倍、100 倍、1 万倍……直到 100 亿倍。后面的相空间板（捏它右上角的小牌打开）用一个圆把整个过程画出来。", "A glowing track with a wall on the left. A small brown block (1 kg) waits in front of the wall; a big blue block slides in from the right. Pinch the big block to launch it, and the gold counter counts every collision. Pinch the ratio plaque to make the big block 1, 100, 10,000 ... up to 10 billion times heavier. The phase board behind (pinch the tab at its top right) draws the whole run with a circle."),
        ),
        About.Page(
            Bi("撞出来的 π", "Pi from collisions"),
            Bi("2003 年，数学家加尔佩林发表了一个奇怪的发现：在理想情况下（没有摩擦、碰撞不损失能量），质量比为 1、100、1 万、100 万时，总碰撞次数是 3、31、314、3141 —— 正是 π 的数字！2019 年 3Blue1Brown 的视频让它广为人知。方块明明是方的，π 从哪来？", "In 2003 the mathematician Gregory Galperin published a strange discovery: in an ideal world (no friction, no energy lost in collisions), mass ratios of 1, 100, 10,000 and a million give 3, 31, 314 and 3,141 collisions in total: the digits of π! A 2019 3Blue1Brown video made it famous. The blocks are square, so where does π come from?"),
        ),
        About.Page(
            Bi("先认识：两条守恒律", "First: two conservation laws"),
            Bi("碰撞中有两样东西总量不变。能量：和速度的平方有关，完全弹性碰撞不损失。动量：质量 × 速度，两块相撞时总量不变（撞墙时墙会吸走动量，所以只在块与块之间守恒）。把速度乘上 √质量 再画图，“能量不变”就变成“状态点永远在一个圆上”。π 就藏在这个圆里。", "Two totals matter in these collisions. Energy depends on speed squared and is not lost in a perfectly elastic bounce. Momentum, mass × velocity, is kept when the blocks hit each other (the wall soaks up momentum, so only block-block hits keep it). Scale each speed by the square root of its mass, and 'energy is kept' becomes 'the state point always stays on one circle'. That circle is where π hides."),
        ),
        About.Page(
            Bi("核心：在圆上走小步", "Key idea: small steps round a circle"),
            Bi("每撞一次，状态点沿一条弦跳到圆上另一点。妙的是，这些弦把圆切成一段段相等的弧，每段对应一个小角 θ。从半圈处出发，进入终点区就停，所以次数 = π 里放得下几个 θ。就像绕圆桌迈固定的小步，步子越小，半圈的步数越接近 π ÷ 步长。质量比 100ⁿ 时 θ ≈ 10⁻ⁿ，于是数出 π 的前几位。", "Each collision makes the state point jump along a chord to another point on the circle. Remarkably, the chords cut the circle into equal arcs, each worth a small angle θ. Starting half way round, the run stops once it enters the end zone, so the count is how many θ fit into π. Like walking round a table in fixed small steps: the smaller the step, the closer half a lap is to π ÷ step. For mass ratio 100ⁿ, θ ≈ 10⁻ⁿ, so the count spells π."),
        ),
        About.Page(
            Bi("怎么看这个 3D 场景", "How to read the 3-D scene"),
            Bi("前面：棕色 = 小方块，蓝色 = 大方块（越重画得越大），碰撞时会闪光；金色大数字 = 碰撞次数，π 里亮起的数字 = 对上了几位。后面的相空间板：横轴 √M·V（蓝），纵轴 √m·v（棕）；金色圆 = 能量守恒；绿色斜线 = 动量守恒；浅色折线 = 每次碰撞的弦，状态点沿它跳。切到「立体」，弦沿纵深排开，绕成螺旋。", "In front: brown = small block, blue = big block (bigger when heavier), flashing at each hit; big gold digits = the count, and the lit digits of π show how many match. On the phase board: across = √M·V (blue), up = √m·v (brown); gold circle = energy kept; green slanted lines = momentum kept; the pale zig-zag = one chord per collision, which the state point hops along. In '3-D' the chords spread out in depth into a coil."),
        ),
        About.Page(
            Bi("关键数字与今天", "The numbers, and today"),
            Bi("1 : 1 撞 3 次，100 : 1 撞 31 次，10⁴ 撞 314 次……10¹⁰ 撞 314159 次。每多一位 π，质量要再乘 100，碰撞多 10 倍 —— 这不是算 π 的好办法，却是看懂“守恒律就是几何”的好例子。2019 年有物理学家指出，量子计算的 Grover 搜索算法也是同一幅图：在圆上一小步一小步地转。", "1 : 1 gives 3 hits, 100 : 1 gives 31, 10⁴ gives 314 ... 10¹⁰ gives 314,159. Each extra digit needs 100 times the mass and 10 times the collisions: a poor way to compute π, but a great way to see that conservation laws are geometry. In 2019 a physicist pointed out the same picture inside Grover's quantum search algorithm: both turn round a circle in small equal steps."),
        ),
    ))

    override val lesson: Lesson by lazy {
        Lesson(
            listOf(
                LessonStep(
                    say = Bi("大方块和小方块质量一样（1 : 1）。大块撞向小块，小块后面是墙，地面无摩擦、碰撞完全弹性。一共会撞几次？", "The big and small block weigh the same (1 : 1). The big one slides into the small one, which sits in front of a wall; no friction, perfectly elastic. How many collisions in total?"),
                    options = listOf(Bi("2 次", "2"), Bi("3 次", "3"), Bi("4 次", "4"), Bi("无数次", "Infinitely many")),
                    answer = 1,
                    onEnter = { setPhase(0); setRatio(0) },
                ),
                LessonStep(
                    say = Bi("捏大方块（或面板「发射」），数一数计数器。", "Pinch the big block (or 'Launch' on the panel) and watch the counter."),
                    target = "BigBlock",
                    onEnter = { base = runsDone },
                    done = { runsDone > base && lastRunRatio == 0 },
                    autoplay = { setRatio(0); launch() },
                    insight = Bi("3 次：大块撞小块后停下，小块撞墙弹回，再撞大块把动量全还给它，两块都向右走，再也追不上。", "3: the big block stops, the small one bounces off the wall and hands all the momentum back; both move right and never meet again."),
                ),
                LessonStep(
                    say = Bi("把大块换成 100 倍重（100 : 1）。现在会撞几次？", "Make the big block 100 times heavier (100 : 1). How many collisions now?"),
                    options = listOf(Bi("10 次", "10"), Bi("31 次", "31"), Bi("100 次", "100")),
                    answer = 1,
                    onEnter = { setRatio(1) },
                ),
                LessonStep(
                    say = Bi("捏质量比牌换到 100 : 1，再发射，看计数器停在几。", "Set the ratio plaque to 100 : 1, launch, and see where the counter stops."),
                    target = "BigBlock",
                    onEnter = { base = runsDone },
                    done = { runsDone > base && lastRunRatio == 1 },
                    autoplay = { setRatio(1); launch() },
                    reveal = true,
                    insight = Bi("31 次 —— π = 3.1…！再重 100 倍（10⁴）是 314 次，10⁶ 是 3141 次：每多两个 0，就多一位 π。π 是从哪里冒出来的？", "31 — π = 3.1…! 100 times heavier again (10⁴) gives 314, 10⁶ gives 3141: two more zeros, one more digit of π. Where does π come from?"),
                ),
                LessonStep(
                    say = Bi("捏后面相空间板右上角的小牌（或面板「平面」）打开它，再发射一次：看状态点在圆上沿着弦一跳一跳。", "Pinch the tab at the top right of the phase board (or 'Flat' on the panel) to open it, then launch again: watch the state point hop along chords on the circle."),
                    target = "PhaseBoard",
                    onEnter = { base = runsDone },
                    done = { phaseView > 0 && runsDone > base },
                    autoplay = { setPhase(1); launch() },
                    insight = Bi("横轴 √M·V、纵轴 √m·v：能量守恒是一个圆，动量守恒是斜率 −√(M/m) 的平行线。每次碰撞是一条弦，弦端把圆切成相等的弧 2θ，θ = arctan√(m/M)。走进 0~θ 的终点区就停：次数 = π 里放得下几个 θ（⌊π/θ⌋）。θ ≈ 10⁻ⁿ，所以是 π 的数字。", "Across √M·V, up √m·v: energy is a circle, momentum lines have slope −√(M/m). Each collision is a chord; the chord ends cut the circle into equal arcs 2θ, θ = arctan√(m/M). It stops in the 0…θ end zone: count = how many θ fit in π (⌊π/θ⌋). θ ≈ 10⁻ⁿ, hence π's digits."),
                ),
            ),
            challenge = Challenge(
                say = Bi("挑战：让计数器给出 π 的前 4 位 —— 撞出 3141 次。", "Challenge: make the counter show the first 4 digits of π — 3141 collisions."),
                progress = {
                    tr("质量比 ${ratioText()} · 上一次 ${if (lastRunRatio >= 0) lastRunCount else 0} 次 · 目标 3141（需要 M:m = 10⁶ : 1）",
                        "Ratio ${ratioText()} · last run ${if (lastRunRatio >= 0) lastRunCount else 0} · goal 3141 (needs M:m = 10⁶ : 1)")
                },
                onEnter = { base = runsDone },
                done = { runsDone > base && lastRunCount == 3141L },
                autoplay = { setRatio(3); launch() },
            ),
        )
    }

    // ================================================================ review states

    private fun completeRun(i: Int, phase: Int) { setRatio(i); setPhase(phase); launch(); finishNow(); yaw = if (phase == 2) -65f else 0f }

    override fun reviewVariants(): Map<String, () -> Unit> = mapOf(
        "equal" to { completeRun(0, 1) },
        "r100" to { completeRun(1, 1) },
        "r100_run" to { setRatio(1); setPhase(1); launch(); repeat(150) { update(1f / 60f) } },
        "r1e6_burst" to { setRatio(3); setPhase(1); launch(); speed = 1; repeat(240) { update(1f / 60f) } },
        "r100_3d" to { completeRun(1, 2) },
        "r1e4_3d" to { completeRun(2, 2) },
        "r1e6" to { completeRun(3, 1) },
        "r1e10" to { completeRun(5, 1) },
        "closed" to { completeRun(1, 0) },
    )

    // ================================================================ panel

    @Composable
    override fun Panel() {
        @Suppress("UNUSED_VARIABLE") val epoch = panelEpoch
        LabPanel(readout) {
            SegmentControl {
                SegmentItem(selected = speed == 0, onClick = { speed = 0; bump() }, title = { Text(tr("慢放", "Slow-mo")) })
                SegmentItem(selected = speed == 1, onClick = { speed = 1; bump() }, title = { Text(tr("中速", "Medium")) })
                SegmentItem(selected = speed == 2, onClick = { speed = 2; bump() }, title = { Text(tr("快", "Fast")) })
            }
            Button(onClick = { launch() }) { Text(if (state == Run.RUNNING) tr("↺ 重新发射", "↺ Relaunch") else tr("▶ 发射", "▶ Launch")) }
            SegmentControl {
                for (i in RATIOS.indices) SegmentItem(selected = ratioIdx == i, onClick = { setRatio(i) }, title = { Text(RATIO_UI[i]) })
            }
            SegmentControl {
                SegmentItem(selected = phaseView == 0, onClick = { setPhase(0) }, title = { Text(tr("相空间关", "Phase off")) })
                SegmentItem(selected = phaseView == 1, onClick = { setPhase(1) }, title = { Text(tr("平面", "Flat")) })
                SegmentItem(selected = phaseView == 2, onClick = { setPhase(2) }, title = { Text(tr("立体", "3-D")) })
            }
        }
    }
}
