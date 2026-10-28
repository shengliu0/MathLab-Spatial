// 球面积 · 阿基米德 (Archimedes, On the Sphere and Cylinder, ~225 BC) — "why is a sphere's area four times its shadow?"
//
// LESSON (3Blue1Brown spirit):
//   * A glass sphere (radius r, painted in 12 equal-height colour bands) sits in a glass cylinder of radius r and
//     height 2r. Pinch-drag the gold knob on the right rail: one band (latitude θ, thickness h) glows on the sphere
//     and its straight-out projection glows on the cylinder in the same colour. On the left silhouette the two
//     factors are drawn: the band's slant width is h/cosθ (orange, ×1/cosθ) while its radius is r·cosθ (blue, ×cosθ)
//     — near the pole it gets wider AND smaller by exactly the same factor, so every band keeps its area
//     (hat-box theorem). The readout shows the band area measured numerically (inscribed frustums) against 2πrh.
//   * Pinch the sphere: all 12 bands fly straight out onto the cylinder (12 pooled ring nodes, scale only).
//     Pinch the cylinder lid: the painted side slides right and UNROLLS (isometric bending, 16 pre-built stages)
//     into a 2πr × 2r rectangle with the same stripes. Pinch the shadow: the rectangle is cut into 4 strips, each
//     πr²; each strip rolls into a disc of radius r by the exactly area-preserving map (u,t) -> (r√t, 2π(u-½))
//     (10 pre-built stages) — stripes become equal-area rings: 4πr² = 4 × πr², four shadows.
//   * Bonus: pinch Archimedes' tombstone: the sphere's volume, poured into the cylinder, fills 4r/3 = 2/3 of it.
// Science in core/sphere (ArchimedesTest). Per frame only transforms, visibility and setMesh between cached meshes;
// the selected band's own mesh (246 vertices) is edited only when θ or h changes.

package com.bytedance.mathlab.demos.sphere

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bytedance.mathlab.core.P3
import com.bytedance.mathlab.core.sphere.Archimedes
import com.bytedance.mathlab.lab.BakeCache
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
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object SphereDemo : SpecDemo() {
    override val id = "sphere"
    override val title = "球面积 · 阿基米德"  // i18n-ok (zh half of DemoRegistry.titleOf; English in TITLE_EN)
    override val credit = "Archimedes · 225 BC"

    // ---------------------------------------------------------------- geometry (volume metres)
    private const val R = 0.14f
    private val C = P3(-0.44f, 0.09f, 0f)               // sphere = cylinder centre
    private const val NB = 12                           // colour bands
    private val RECT = P3(0.42f, 0.09f, 0f)             // paint node after the slide (the flat sheet lies at z = +R)
    private const val DX = 0.148f                       // disc grid half-spacing
    /** Strip i (left to right) -> disc: TL, BL, TR, BR, so no two strips cross on the way. */
    private val DISC = arrayOf(P3(RECT.x - DX, RECT.y + DX, R), P3(RECT.x - DX, RECT.y - DX, R),
        P3(RECT.x + DX, RECT.y + DX, R), P3(RECT.x + DX, RECT.y - DX, R))
    private const val PLINTH_Y = -0.262f
    private const val PLINTH_TOP = PLINTH_Y + 0.0175f
    private const val K_UNROLL = 16
    private const val M_MORPH = 10
    private const val LAT_MAX = 80f
    private const val H_MAX = 3f                       // cm: the flattened band must fit the 2r-tall board at 80°
    private const val RAIL_L = R + 0.038f              // band rail, globe frame
    private const val TILT = 0.33f                      // rad (19°): the globe leans its north pole toward the viewer
    private val O = P3(0f, 0f, 0f)                      // globe-frame origin = sphere centre
    /** Parallel sunlight for the shadow (down, left, away); the screen stands square to it. */
    private val SUN = norm(-0.45f, -0.35f, -0.82f)
    private const val SHADOW_L = 0.50f
    private val SHADOW_C = P3(C.x + SUN.x * SHADOW_L, C.y + SUN.y * SHADOW_L, C.z + SUN.z * SHADOW_L)
    private val TOMB = P3(-0.10f, PLINTH_TOP + 0.048f, 0.30f)

    private val SPEEDS = floatArrayOf(0.5f, 1f, 2f)
    private val DUR = floatArrayOf(2.4f, 3.2f, 3.2f)     // project, unroll, four discs (s at medium speed)
    private const val VOL_DUR = 3.6f

    // ---------------------------------------------------------------- colours (3Blue1Brown-like, sRGB)
    private val YELLOW = Rgb(1.00f, 0.86f, 0.30f)
    private val ORANGE = Rgb(1.00f, 0.55f, 0.22f)
    private val RED = Rgb(0.99f, 0.38f, 0.33f)
    private val GREEN = Rgb(0.51f, 0.76f, 0.40f)
    private val BLUE = Rgb(0.35f, 0.77f, 0.87f)
    private val TEAL = Rgb(0.36f, 0.82f, 0.70f)
    private val GOLD = Rgb(0.94f, 0.67f, 0.37f)
    private val WHITE = Rgb(0.97f, 0.98f, 1.00f)
    private val GREY = Rgb(0.62f, 0.66f, 0.74f)
    private val SKY = Rgb(0.60f, 0.85f, 1.00f)
    private val WATER = Rgb(0.25f, 0.55f, 0.95f)
    /** The selected band and its twin: white-hot yellow, brighter than every paint stripe. */
    private val HOT = Rgb(1.00f, 0.95f, 0.62f)

    /** Band k (0 = north pole) colour: a blue -> teal -> green -> yellow -> gold -> red sweep, alternating brightness. */
    private val BAND: Array<Rgb> by lazy {
        val stops = listOf(BLUE, TEAL, GREEN, YELLOW, GOLD, RED)
        Array(NB) { k ->
            val f = k / (NB - 1f) * (stops.size - 1); val i = min(stops.size - 2, floor(f).toInt())
            val c = ProcTex.okLerp(stops[i], stops[i + 1], f - i)
            if (k % 2 == 1) Rgb(c.r * 0.80f, c.g * 0.80f, c.b * 0.80f) else c
        }
    }
    private fun lighten(c: Rgb, t: Float = 0.35f) = ProcTex.okLerp(c, WHITE, t)

    // ---------------------------------------------------------------- text atlas
    private const val TEX_VERSION = 4
    private const val EM_PX = 64
    private fun sp(t: String, c: Rgb = WHITE) = SphereText.Span(t, c)
    private val ATLAS_ITEMS: List<SphereAtlas.Item> by lazy {
        val y = YELLOW
        listOf(
            SphereAtlas.Item("slant", listOf(sp("×1/`cos`θ", lighten(ORANGE, 0.2f))), EM_PX),
            SphereAtlas.Item("rad", listOf(sp("×`cos`θ", lighten(BLUE, 0.2f))), EM_PX),
            SphereAtlas.Item("2pir", listOf(sp("2πr", y)), EM_PX),
            SphereAtlas.Item("2r", listOf(sp("2r", y)), EM_PX),
            SphereAtlas.Item("rect", listOf(sp("2πr", y), sp(" · "), sp("2r", y), sp(" = "), sp("4πr^2", WHITE)), EM_PX),
            SphereAtlas.Item("four", listOf(sp("4πr^2", WHITE), sp(" = "), sp("4", y), sp(" × "), sp("πr^2", SKY)), EM_PX),
            SphereAtlas.Item("pir2", listOf(sp("πr^2", WHITE)), EM_PX),
            SphereAtlas.Item("h", listOf(sp("h", lighten(ORANGE, 0.2f))), EM_PX),
            SphereAtlas.Item("hcos", listOf(sp("h/`cos`θ", lighten(ORANGE, 0.2f))), EM_PX),
            SphereAtlas.Item("2pircos", listOf(sp("2πr `cos`θ", lighten(BLUE, 0.2f))), EM_PX),
            SphereAtlas.Item("vol", listOf(sp("V", WATER.let { lighten(it, 0.4f) }), sp(" = 4/3 πr^3 = "), sp("2/3", y), sp(" × 2πr^3")), EM_PX),
        )
    }
    private lateinit var atlas: SphereAtlas
    private val textMats = HashMap<Rgb, Mat>()
    private fun textMat() = textMats.getOrPut(WHITE) { Mat.Unlit(Rgb(1f, 1f, 1f), tex = atlas.image, blend = Blend.TRANSPARENT) }
    private fun decal(parent: Node, name: String, key: String, em: Float, pos: P3, bb: Boolean = true): Node {
        val d = atlas.decal(key)
        val n = parent.add(name, d.mesh, textMat(), pos)
        n.billboard = bb
        val h = em / d.emFrac; n.scale = P3(h * d.aspect, h, 1f)
        return n
    }
    private fun decalWidth(key: String, em: Float): Float { val d = atlas.decal(key); return em / d.emFrac * d.aspect }

    // ---------------------------------------------------------------- state
    /** Latitude of the selected band's centre (degrees, + north). */
    var latDeg = 55f; private set
    /** Band thickness (height) in cm. */
    var hCm = 2.0f; private set
    /** 0 = bands on the sphere, 1 = all projected, 2 = unrolled, 3 = four discs (animated toward [levelT]). */
    var level = 0f; private set
    var levelT = 0f; private set
    /** Volume pour 0..1 toward [volT]. */
    var vol = 0f; private set
    var volT = 0f; private set
    var speed = 1
    /** Largest |latitude| a band was shown at (lesson). */
    var maxLatShown = 0f; private set
    private var builtKey = ""
    private var waterLevelShown = -1
    private var shadowLit = -1
    private var yDrag = 0f
    private var tiltNow = TILT

    private fun resetState() {
        latDeg = 55f; hCm = 2.0f; level = 0f; levelT = 0f; vol = 0f; volT = 0f; speed = 1
        maxLatShown = 0f; builtKey = ""; waterLevelShown = -1; shadowLit = -1; yDrag = 0f; tiltNow = -1f
    }

    val bandVisible get() = level <= 1.0005f
    private val hBand get() = hCm / 100f
    private fun bandY(): Pair<Float, Float> {
        val yc = R * sin(latDeg * PI.toFloat() / 180f)
        return max(-R, yc - hBand / 2) to min(R, yc + hBand / 2)
    }

    // ---------------------------------------------------------------- nodes / meshes
    private lateinit var unitCyl: MeshData
    private lateinit var openCyl: MeshData
    private lateinit var ball: MeshData
    private lateinit var bandMesh: MeshData
    private lateinit var unrollMeshes: Array<MeshData>
    private lateinit var stripMeshes: Array<MeshData>
    private lateinit var paintMat: Mat

    private lateinit var globe: Node
    private lateinit var sphere: Node
    private lateinit var sphereWater: Node
    private lateinit var water: Node
    private lateinit var waterTop: Node
    private lateinit var bandS: Node
    private lateinit var bandC: Node
    private lateinit var bandEdges: Array<Node>
    private lateinit var rays: Array<Node>
    private lateinit var fSlant: Node
    private lateinit var fCyl: Node
    private lateinit var fConn: Array<Node>
    private lateinit var fRadS: Node
    private lateinit var fRadC: Node
    private lateinit var txSlant: Node
    private lateinit var txRad: Node
    private lateinit var knob: Node
    private lateinit var rings: Array<Node>
    private lateinit var paint: Node
    private lateinit var ghost: Array<Node>
    private lateinit var tx2pir: Node
    private lateinit var tx2r: Node
    private lateinit var txRect: Node
    private lateinit var txFour: Node
    private lateinit var strips: Array<Node>
    private lateinit var discRims: Array<Node>
    private lateinit var discTx: Array<Node>
    private lateinit var shadowRim: Node
    private lateinit var txVol: Node
    private lateinit var flatA: Node
    private lateinit var flatB: Node
    private lateinit var flatEdges: Array<Node>
    private lateinit var txFlat: Array<Node>

    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.32f)
    private val rimDim by lazy { Mat.Unlit(SKY, alpha = 0.55f, blend = Blend.ADD) }
    private val rimLit by lazy { Mat.Unlit(SKY, alpha = 1.0f, blend = Blend.ADD) }
    private val sphWaterMats = HashMap<Int, Mat>()

    override fun buildSpec(cacheDir: File): Node {
        val imgs = BakeCache.load(cacheDir, "sphere_tex", TEX_VERSION) {
            listOf(SphereAtlas.bakeImage(ATLAS_ITEMS), stripeTex(384, polar = false), stripeTex(512, polar = true),
                ProcTex.bakePlanet(256, 128, Rgb(0.07f, 0.075f, 0.085f), Rgb(0.12f, 0.125f, 0.14f), Rgb(0.20f, 0.205f, 0.22f), 0f, 77))
        }
        atlas = SphereAtlas.of(imgs[0], ATLAS_ITEMS); textMats.clear(); sphWaterMats.clear()
        val stripes = imgs[1]; val sphereStripes = imgs[2]; val slabTex = imgs[3]
        unitCyl = MeshGen.cylinder(1f, 1f, 8)
        openCyl = openCylinder(64)
        ball = MeshGen.sphere(1f, 18, 32, MeshGen.SphereUv.EQUIRECT)
        paintMat = Mat.Unlit(Rgb(1f, 1f, 1f), alpha = 0.88f, tex = stripes, blend = Blend.TRANSPARENT)
        resetState()

        val root = Node("SphereRoot")
        // ---- bench
        root.add("Plinth", MeshGen.box(1.74f, 0.035f, 0.92f), Mat.Pbr(Rgb(1f, 1f, 1f), roughness = 0.55f, tex = slabTex), P3(0f, PLINTH_Y, -0.04f))
        root.add("PlinthBand", MeshGen.box(1.75f, 0.008f, 0.93f), alu, P3(0f, PLINTH_Y, -0.04f))
        val pool = OptGeo.lumTex(64, 64) { u, v -> val dx = (u - 0.5f) * 2f; val dy = (v - 0.5f) * 2f; val r2 = dx * dx + dy * dy; if (r2 >= 1f) 0f else (1f - r2) * (1f - r2) }
        root.add("Pool", OptGeo.quad(1f, 1f), Mat.Unlit(Rgb(0.30f, 0.55f, 1.0f), alpha = 0.30f, tex = pool, blend = Blend.ADD)).also {
            it.rot = Q.axisAngle(1f, 0f, 0f, -PI.toFloat() / 2); it.scale = P3(0.70f, 0.55f, 1f); it.pos = P3(C.x, PLINTH_TOP + 0.002f, C.z)
        }

        // ---- the stand, cylinder and sphere. The globe (sphere + cylinder + band tools) leans TILT toward the viewer like a
        // desk globe, so polar bands face you; it stands up straight for the volume pour (water must lie level).
        globe = root.add(Node("Globe")).also { it.pos = C; it.rot = Q.axisAngle(1f, 0f, 0f, TILT) }
        globe.add("StandTop", MeshGen.cylinder(R + 0.02f, 0.008f, 48), alu, P3(0f, -R - 0.0045f, 0f))
        val colTop = C.y - (R + 0.0085f) * cos(TILT)
        val colZ = C.z - (R + 0.0085f) * sin(TILT)
        val colH = colTop - PLINTH_TOP
        root.add("StandCol", MeshGen.cylinder(0.012f, colH, 16), alu, P3(C.x, PLINTH_TOP + colH / 2, colZ))
        root.add("StandFoot", MeshGen.cylinder(0.075f, 0.008f, 40), alu, P3(C.x, PLINTH_TOP + 0.004f, colZ))
        globe.add("CylGlass", openCyl, Mat.Pbr(Rgb(0.55f, 0.75f, 0.98f), roughness = 0.06f, alpha = 0.11f, emissive = Rgb(0.02f, 0.05f, 0.08f))).also {
            it.pos = O; it.scale = P3(R * 0.997f, 2 * R, R * 0.997f)
        }
        val rimMesh = MeshGen.tube(ringPath(R, 64), 0.0022f, 6, true)
        globe.add("CylLines", generators(16), Mat.Unlit(lighten(BLUE, 0.5f), alpha = 0.22f, blend = Blend.ADD), O)
        for ((i, y) in listOf(R, -R).withIndex()) globe.add("CylRim$i", rimMesh, Mat.Unlit(lighten(BLUE, 0.5f), alpha = 0.75f, blend = Blend.ADD), P3(O.x, O.y + y, O.z))
        // the lid knob: pinch = unroll
        globe.add("Lid", ball, Mat.Pbr(GOLD, metallic = 0.2f, roughness = 0.25f, emissive = Rgb(GOLD.r * 0.45f, GOLD.g * 0.45f, GOLD.b * 0.45f))).also {
            it.pos = P3(O.x, O.y + R + 0.024f, O.z); it.uniformScale(0.017f); it.tapRadius = 0.05f / 0.017f
        }
        globe.add("LidStem", unitCyl, alu).also { segF(it, O.x, O.y + R, O.z, O.x, O.y + R + 0.012f, O.z, 0.004f) }
        globe.add("SphereGlass", MeshGen.sphere(R * 1.002f, 32, 48, MeshGen.SphereUv.EQUIRECT),
            Mat.Pbr(Rgb(0.80f, 0.90f, 1.0f), roughness = 0.08f, alpha = 0.16f), O)
        sphere = globe.add("Sphere", MeshGen.sphere(R, 48, 64, MeshGen.SphereUv.EQUIRECT),
            Mat.Unlit(Rgb(1f, 1f, 1f), alpha = 0.55f, tex = sphereStripes, blend = Blend.TRANSPARENT), O).also { it.tapRadius = 0.075f }
        sphereWater = globe.add("SphereWater", MeshGen.sphere(R * 0.97f, 24, 36, MeshGen.SphereUv.EQUIRECT), waterMat(0), O).also { it.visible = false }
        water = globe.add("Water", MeshGen.cylinder(1f, 1f, 48), Mat.Pbr(WATER, roughness = 0.1f, alpha = 0.55f, emissive = Rgb(0.06f, 0.16f, 0.36f))).also { it.visible = false }
        waterTop = globe.add("WaterTop", MeshGen.tube(ringPath(R * 0.99f, 64), 0.0030f, 6, true), Mat.Unlit(lighten(WATER, 0.5f), blend = Blend.ADD)).also { it.visible = false }
        globe.add("Axis", unitCyl, Mat.Unlit(GREY, alpha = 0.35f, blend = Blend.ADD)).also { segF(it, O.x, O.y - R, O.z, O.x, O.y + R, O.z, 0.0012f) }

        // ---- the selected band and its twin
        bandMesh = bandStrip(R * 1.004f, 0f, 0.01f, 5, 40)
        bandS = globe.add("BandS", bandMesh, Mat.Unlit(HOT, blend = Blend.ADD), O)
        bandC = globe.add("BandC", openCyl, Mat.Unlit(HOT, alpha = 0.85f, blend = Blend.ADD))
        val edge = MeshGen.tube(ringPath(R * 1.006f, 64), 0.0026f, 6, true)
        bandEdges = Array(2) { globe.add("BandEdge$it", edge, Mat.Unlit(WHITE, blend = Blend.ADD)) }
        rays = Array(6) { globe.add("Ray$it", unitCyl, Mat.Unlit(WHITE, alpha = 0.55f, blend = Blend.ADD)) }
        fSlant = globe.add("FSlant", unitCyl, Mat.Unlit(ORANGE))
        fCyl = globe.add("FCyl", unitCyl, Mat.Unlit(ORANGE))
        fConn = Array(2) { globe.add("FConn$it", unitCyl, Mat.Unlit(WHITE, alpha = 0.6f, blend = Blend.ADD)) }
        fRadS = globe.add("FRadS", unitCyl, Mat.Unlit(BLUE))
        fRadC = globe.add("FRadC", unitCyl, Mat.Unlit(BLUE, alpha = 0.45f, blend = Blend.ADD))
        txSlant = decal(globe, "TxSlant", "slant", 0.030f, O)
        txRad = decal(globe, "TxRad", "rad", 0.030f, O)
        globe.add("Rail", unitCyl, Mat.Unlit(GOLD, alpha = 0.45f, blend = Blend.ADD)).also {
            val yl = R * sin(LAT_MAX * PI.toFloat() / 180f)
            segF(it, RAIL_L, O.y - yl, O.z, RAIL_L, O.y + yl, O.z, 0.0018f)
        }
        knob = globe.add("BandKnob", ball, Mat.Pbr(YELLOW, metallic = 0.1f, roughness = 0.22f, emissive = Rgb(YELLOW.r * 0.5f, YELLOW.g * 0.5f, YELLOW.b * 0.5f))).also {
            it.uniformScale(0.018f); it.tapRadius = 0.05f / 0.018f
        }

        // ---- projection rings (one per colour band)
        rings = Array(NB) { k -> globe.add("Ring$k", openCyl, Mat.Unlit(BAND[k], alpha = 0.80f, blend = Blend.TRANSPARENT)).also { it.visible = false } }

        // ---- the paint sheet: bent stages from the cylinder (kappa = 1) to the flat 2πr × 2r rectangle
        unrollMeshes = Array(K_UNROLL + 1) { k -> sheetMesh(1.0 - k.toDouble() / K_UNROLL) }
        paint = root.add("Paint", unrollMeshes[0], paintMat, C).also { it.visible = false }
        // ghost of where the rectangle will land, with its dimensions
        val w = (2 * PI * R).toFloat(); val zr = RECT.z + R
        val gx0 = RECT.x - w / 2; val gx1 = RECT.x + w / 2; val gy0 = RECT.y - R; val gy1 = RECT.y + R
        val gp = listOf(floatArrayOf(gx0, gy0, gx1, gy0), floatArrayOf(gx1, gy0, gx1, gy1), floatArrayOf(gx1, gy1, gx0, gy1), floatArrayOf(gx0, gy1, gx0, gy0))
        ghost = Array(4) { i -> root.add("Ghost$i", unitCyl, Mat.Unlit(GREY, alpha = 0.25f, blend = Blend.ADD)).also { val p = gp[i]; segF(it, p[0], p[1], zr, p[2], p[3], zr, 0.0016f) } }
        tx2pir = decal(root, "Tx2pir", "2pir", 0.034f, P3(RECT.x, gy0 - 0.032f, zr + 0.01f), bb = false)
        tx2r = decal(root, "Tx2r", "2r", 0.034f, P3(gx0 - 0.010f - decalWidth("2r", 0.034f) / 2, RECT.y, zr + 0.01f), bb = false)
        // the flattened band (true scale): sphere band A = (2π × its arc's centroid radius) × slant arc, cylinder band B = 2πr × h
        val fq = OptGeo.quad(1f, 1f)
        flatB = root.add("FlatB", fq, Mat.Unlit(HOT, alpha = 0.75f, blend = Blend.ADD))
        flatA = root.add("FlatA", fq, Mat.Unlit(HOT, alpha = 0.75f, blend = Blend.ADD))
        flatEdges = Array(8) { root.add("FlatEdge$it", unitCyl, Mat.Unlit(WHITE, alpha = 0.9f, blend = Blend.ADD)) }
        txFlat = arrayOf(decal(root, "TxFlatH", "h", 0.030f, C, bb = false), decal(root, "TxFlatHc", "hcos", 0.030f, C, bb = false),
            decal(root, "TxFlatW", "2pircos", 0.030f, C, bb = false))
        txRect = decal(root, "TxRect", "rect", 0.040f, P3(RECT.x, gy1 + 0.045f, zr + 0.01f), bb = false)
        txFour = decal(root, "TxFour", "four", 0.036f, P3(RECT.x, RECT.y + DX + R + 0.040f, zr + 0.01f), bb = false)

        // ---- four strips -> four discs
        stripMeshes = Array(M_MORPH + 1) { m -> stripMesh(m.toDouble() / M_MORPH) }
        strips = Array(4) { i -> root.add("Strip$i", stripMeshes[0], paintMat).also { it.visible = false } }
        val discRim = MeshGen.tube(ringPathXY(R * 1.004f, 72), 0.0030f, 6, true)
        discRims = Array(4) { i -> root.add("DiscRim$i", discRim, rimDim, P3(DISC[i].x, DISC[i].y, DISC[i].z + 0.002f)).also { it.visible = false } }
        discTx = Array(4) { i -> decal(root, "DiscTx$i", "pir2", 0.032f, P3(DISC[i].x, DISC[i].y, DISC[i].z + 0.006f), bb = false).also { it.visible = false } }

        // ---- the shadow: parallel sunlight, a screen square to it, the shadow is a disc of radius r
        val frame = root.add(Node("ShadowFrame")).also { it.pos = SHADOW_C; it.rot = Q.fromTo(0f, 0f, 1f, -SUN.x, -SUN.y, -SUN.z) }
        frame.add("ShadowScreen", OptGeo.quad(0.31f, 0.31f), Mat.Unlit(Rgb(0.62f, 0.70f, 0.86f), alpha = 0.20f, blend = Blend.TRANSPARENT))
        frame.add("Shadow", discXY(R, 64), Mat.Unlit(Rgb(0.015f, 0.02f, 0.035f), alpha = 0.92f, blend = Blend.TRANSPARENT), P3(0f, 0f, 0.002f)).also { it.tapRadius = 0.105f }
        shadowRim = frame.add("ShadowRim", discRim, rimDim, P3(0f, 0f, 0.004f))
        decal(frame, "ShadowTx", "pir2", 0.030f, P3(0f, 0f, 0.006f), bb = false)
        // light rays grazing the sphere's great circle square to the sun, on to the shadow's rim
        val e1 = norm(SUN.z, 0f, -SUN.x)
        val e2 = P3(SUN.y * e1.z - SUN.z * e1.y, SUN.z * e1.x - SUN.x * e1.z, SUN.x * e1.y - SUN.y * e1.x)
        for (j in 0 until 8) {
            val a = 2 * PI.toFloat() * j / 8 + 0.3f
            val o = e1 * (R * cos(a)) + e2 * (R * sin(a))
            val a0 = C + o; val a1 = SHADOW_C + o
            root.add("Sun$j", unitCyl, Mat.Unlit(GOLD, alpha = 0.35f, blend = Blend.ADD)).also { segF(it, a0.x, a0.y, a0.z, a1.x, a1.y, a1.z, 0.0011f) }
        }

        // ---- Archimedes' tombstone (bonus: volume 2/3)
        val tomb = root.add("Tomb", MeshGen.box(0.10f, 0.096f, 0.03f), Mat.Pbr(Rgb(0.55f, 0.53f, 0.50f), roughness = 0.8f), TOMB).also { it.tapRadius = 0.05f }
        tomb.add("TombRing", MeshGen.tube(ringPathXY(0.026f, 40), 0.0022f, 5, true), Mat.Unlit(GOLD), P3(0f, 0f, 0.016f))
        val sq = 0.026f
        val sqp = listOf(floatArrayOf(-sq, -sq, sq, -sq), floatArrayOf(sq, -sq, sq, sq), floatArrayOf(sq, sq, -sq, sq), floatArrayOf(-sq, sq, -sq, -sq))
        for ((i, p) in sqp.withIndex()) tomb.add("TombSq$i", unitCyl, Mat.Unlit(GOLD)).also { segF(it, p[0], p[1], 0.016f, p[2], p[3], 0.016f, 0.0022f) }
        txVol = decal(root, "TxVol", "vol", 0.030f, P3(TOMB.x, TOMB.y + 0.080f, TOMB.z + 0.01f)).also { it.visible = false }

        update(0f)
        return root
    }

    // ---------------------------------------------------------------- mesh builders
    private fun norm(x: Float, y: Float, z: Float): P3 { val l = sqrt(x * x + y * y + z * z); return P3(x / l, y / l, z / l) }

    private fun ringPath(r: Float, n: Int) = FloatArray(n * 3) { i -> val a = 2 * PI.toFloat() * (i / 3) / n
        when (i % 3) { 0 -> r * cos(a); 1 -> 0f; else -> r * sin(a) } }
    private fun ringPathXY(r: Float, n: Int) = FloatArray(n * 3) { i -> val a = 2 * PI.toFloat() * (i / 3) / n
        when (i % 3) { 0 -> r * cos(a); 1 -> r * sin(a); else -> 0f } }

    /** Side of a cylinder, radius 1, y in [-0.5, 0.5], v = 0 at the top. */
    private fun openCylinder(seg: Int): MeshData {
        val nv = (seg + 1) * 2
        val pos = FloatArray(nv * 3); val nrm = FloatArray(nv * 3); val uv = FloatArray(nv * 2)
        for (s in 0..seg) {
            val a = 2 * PI * s / seg; val c = cos(a).toFloat(); val sn = sin(a).toFloat()
            for (j in 0..1) {
                val v = s * 2 + j
                pos[v * 3] = c; pos[v * 3 + 1] = if (j == 0) 0.5f else -0.5f; pos[v * 3 + 2] = sn
                nrm[v * 3] = c; nrm[v * 3 + 2] = sn
                uv[v * 2] = s.toFloat() / seg; uv[v * 2 + 1] = j.toFloat()
            }
        }
        val idx = IntArray(seg * 6); var t = 0
        for (s in 0 until seg) { val a0 = s * 2; idx[t++] = a0; idx[t++] = a0 + 2; idx[t++] = a0 + 1; idx[t++] = a0 + 1; idx[t++] = a0 + 2; idx[t++] = a0 + 3 }
        return MeshData(pos, nrm, uv, idx)
    }

    /** [n] vertical generator lines of the unit-radius cylinder (radius R, height 2R), one merged mesh. */
    private fun generators(n: Int): MeshData {
        val parts = (0 until n).map { k -> val a = 2 * PI.toFloat() * k / n
            MeshGen.tube(floatArrayOf(R * cos(a), -R, R * sin(a), R * cos(a), R, R * sin(a)), 0.0012f, 4, false) }
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

    /** Flat disc of radius [r] in the XY plane facing +Z. */
    private fun discXY(r: Float, seg: Int): MeshData {
        val pos = FloatArray((seg + 2) * 3); val nrm = FloatArray((seg + 2) * 3); val uv = FloatArray((seg + 2) * 2)
        for (v in 0 until seg + 2) nrm[v * 3 + 2] = 1f
        uv[0] = 0.5f; uv[1] = 0.5f
        for (s in 0..seg) {
            val a = 2 * PI * s / seg; val v = s + 1
            pos[v * 3] = r * cos(a).toFloat(); pos[v * 3 + 1] = r * sin(a).toFloat()
            uv[v * 2] = 0.5f + 0.5f * cos(a).toFloat(); uv[v * 2 + 1] = 0.5f - 0.5f * sin(a).toFloat()
        }
        val idx = IntArray(seg * 3); var t = 0
        for (s in 0 until seg) { idx[t++] = 0; idx[t++] = s + 1; idx[t++] = s + 2 }
        return MeshData(pos, nrm, uv, idx)
    }

    /** A sphere band (radius [r]) between heights [y0] and [y1]: [rings] latitude rings, [lon] segments. */
    private fun bandStrip(r: Float, y0: Float, y1: Float, sub: Int, lon: Int): MeshData {
        val nv = (sub + 1) * (lon + 1)
        val md = MeshData(FloatArray(nv * 3), FloatArray(nv * 3), FloatArray(nv * 2), IntArray(sub * lon * 6))
        var t = 0
        for (i in 0 until sub) for (j in 0 until lon) {
            val a = i * (lon + 1) + j; val b = a + lon + 1
            md.idx[t++] = a; md.idx[t++] = b; md.idx[t++] = a + 1; md.idx[t++] = a + 1; md.idx[t++] = b; md.idx[t++] = b + 1
        }
        fillBand(md, r, y0, y1, sub, lon)
        return md
    }

    private fun fillBand(md: MeshData, r: Float, y0: Float, y1: Float, sub: Int, lon: Int) {
        val p0 = asin((y0 / r).coerceIn(-1f, 1f)); val p1 = asin((y1 / r).coerceIn(-1f, 1f))
        var k = 0
        for (i in 0..sub) {
            val p = p0 + (p1 - p0) * i / sub; val cp = cos(p); val spn = sin(p)
            for (j in 0..lon) {
                val a = 2 * PI.toFloat() * j / lon; val c = cos(a); val s = sin(a)
                md.pos[k * 3] = r * cp * c; md.pos[k * 3 + 1] = r * spn; md.pos[k * 3 + 2] = r * cp * s
                md.nrm[k * 3] = cp * c; md.nrm[k * 3 + 1] = spn; md.nrm[k * 3 + 2] = cp * s
                md.uv[k * 2] = j.toFloat() / lon; md.uv[k * 2 + 1] = i.toFloat() / sub
                k++
            }
        }
    }

    /** The paint sheet bent to curvature [kappa] (Archimedes.bend): 2 rows (y = ±R), 65 columns, v = stripe coordinate. */
    private fun sheetMesh(kappa: Double): MeshData {
        val cols = 65
        val md = OptGeo.grid(2, cols)
        for (row in 0..1) for (c in 0 until cols) {
            val s = -PI * R + 2 * PI * R * c / (cols - 1)
            val y = if (row == 0) R.toDouble() else -R.toDouble()
            val (x, yy, z) = Archimedes.bend(R.toDouble(), s, y, kappa)
            val i = row * cols + c
            OptGeo.setPos(md, i, x.toFloat(), yy.toFloat(), z.toFloat())
            OptGeo.setUv(md, i, c.toFloat() / (cols - 1), row.toFloat())
            val a = if (kappa < 1e-9) 0.0 else s / (R / kappa)
            md.nrm[i * 3] = sin(a).toFloat(); md.nrm[i * 3 + 1] = 0f; md.nrm[i * 3 + 2] = cos(a).toFloat()
        }
        return md
    }

    /** A strip (πr/2 × 2r) morphed a fraction [m] of the way to its disc (Archimedes.stripToDisc). 13 rows on the band boundaries. */
    private fun stripMesh(m: Double): MeshData {
        val rows = NB + 1; val cols = 25
        val md = OptGeo.grid(rows, cols)
        val e = m * m * (3 - 2 * m)
        for (row in 0 until rows) for (c in 0 until cols) {
            val u = c.toDouble() / (cols - 1); val t = row.toDouble() / NB
            val (sx, sy) = Archimedes.stripPoint(R.toDouble(), u, t)
            val (dx, dy) = Archimedes.stripToDisc(R.toDouble(), u, t)
            val i = row * cols + c
            OptGeo.setPos(md, i, (sx + (dx - sx) * e).toFloat(), (sy + (dy - sy) * e).toFloat(), 0f)
            OptGeo.setUv(md, i, u.toFloat(), t.toFloat())
        }
        return md
    }

    /** Stripe texture: v = stripe coordinate t (0 = north pole, 1 = south), or for the sphere v = polar angle / π. */
    private fun stripeTex(h: Int, polar: Boolean): Image {
        val img = Image(4, h)
        for (y in 0 until h) {
            val v = (y + 0.5f) / h
            val t = if (polar) (1f - cos(PI.toFloat() * v)) / 2f else v
            val k = min(NB - 1, floor(t * NB).toInt()); val f = t * NB - k
            val c = BAND[k]
            val sep = if (f < 0.035f || f > 0.965f) 0.22f else 1f
            for (x in 0 until 4) img.px[y * 4 + x] = ProcTex.argb(1f, c.r * sep, c.g * sep, c.b * sep)
        }
        return img
    }

    private fun waterMat(a20: Int): Mat = sphWaterMats.getOrPut(a20) {
        Mat.Pbr(WATER, roughness = 0.1f, alpha = a20 / 20f, emissive = Rgb(0.04f, 0.10f, 0.22f))
    }

    private fun segF(n: Node, ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, r: Float) {
        val dx = bx - ax; val dy = by - ay; val dz = bz - az
        val l = sqrt(dx * dx + dy * dy + dz * dz)
        n.pos = P3((ax + bx) / 2, (ay + by) / 2, (az + bz) / 2)
        n.rot = if (l < 1e-7f) Q.identity() else Q.fromTo(0f, 1f, 0f, dx, dy, dz)
        n.scale = P3(r, max(l, 1e-4f), r)
    }

    private fun ease(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

    // ---------------------------------------------------------------- per frame
    override fun update(dt: Float) {
        // the level animates through project (0..1) -> unroll (1..2) -> four discs (2..3), and back
        val spd = SPEEDS[speed]
        if (dt > 0f && level != levelT) {
            val seg = if (levelT > level) floor(level).toInt() else (kotlin.math.ceil(level).toInt() - 1)
            val step = dt * spd / DUR[seg.coerceIn(0, 2)]
            level = if (levelT > level) min(levelT, level + step) else max(levelT, level - step)
        }
        if (dt > 0f && vol != volT) { val st = dt * spd / VOL_DUR; vol = if (volT > vol) min(volT, vol + st) else max(volT, vol - st) }

        layoutBand()
        layoutRings()
        layoutVolume()
        layoutPaint()
        if (bandVisible) maxLatShown = max(maxLatShown, abs(latDeg))
        updateReadout()
        updateLabels()
    }

    private fun layoutBand() {
        val on = bandVisible
        for (n in listOf(bandS, bandC, fSlant, fCyl, fRadS, fRadC, txSlant, txRad, flatA, flatB) + bandEdges + fConn + rays + flatEdges + txFlat) n.visible = on
        val (y0, y1) = bandY()
        val yc = R * sin(latDeg * PI.toFloat() / 180f)
        knob.pos = P3(RAIL_L, O.y + yc, O.z)
        if (!on) return
        val key = "%.3f/%.3f".format(latDeg, hCm)
        if (key != builtKey) { builtKey = key; fillBand(bandMesh, R * 1.004f, y0, y1, 5, 40); meshEdited(bandMesh) }
        bandC.pos = P3(O.x, O.y + (y0 + y1) / 2, O.z); bandC.scale = P3(R * 1.004f, y1 - y0, R * 1.004f)
        bandEdges[0].pos = P3(O.x, O.y + y0, O.z); bandEdges[1].pos = P3(O.x, O.y + y1, O.z)
        val th = asin((yc / R).coerceIn(-1f, 1f)); val ct = cos(th)
        val rho0 = sqrt(max(0f, R * R - y0 * y0)); val rho1 = sqrt(max(0f, R * R - y1 * y1)); val rho = R * ct
        val z = O.z + 0.004f
        // the left silhouette: the band's slant (orange) vs the cylinder's height h (orange), radii (blue)
        segF(fSlant, O.x - rho0, O.y + y0, z, O.x - rho1, O.y + y1, z, 0.0042f)
        segF(fCyl, O.x - R * 1.006f, O.y + y0, z, O.x - R * 1.006f, O.y + y1, z, 0.0042f)
        segF(fConn[0], O.x - rho0, O.y + y0, z, O.x - R, O.y + y0, z, 0.0013f)
        segF(fConn[1], O.x - rho1, O.y + y1, z, O.x - R, O.y + y1, z, 0.0013f)
        segF(fRadS, O.x, O.y + yc, z, O.x - rho, O.y + yc, z, 0.0032f)
        segF(fRadC, O.x - rho, O.y + yc, z, O.x - R, O.y + yc, z, 0.0018f)
        // straight-out projection rays around the band
        val angs = floatArrayOf(-0.75f, -0.15f, 0.45f, 1.05f, 1.65f, 2.3f)
        for ((i, a) in angs.withIndex()) {
            val c = cos(a); val s = sin(a)
            segF(rays[i], O.x + rho * c, O.y + yc, O.z + rho * s, O.x + R * c, O.y + yc, O.z + R * s, 0.0011f)
        }
        val ax = O.x - R - 0.040f
        val ws = decalWidth("slant", 0.030f); val wr = decalWidth("rad", 0.030f)
        txSlant.pos = P3(ax - ws / 2, O.y + yc + 0.026f, z + 0.02f)
        txRad.pos = P3(ax - wr / 2, O.y + yc - 0.022f, z + 0.02f)
        layoutFlat(y0, y1)
    }

    /** The board on the right (true scale): B = the cylinder band unrolled, A = the sphere band unrolled (Pappus). */
    private fun layoutFlat(y0: Float, y1: Float) {
        val x0 = RECT.x - PI.toFloat() * R; val yb = RECT.y - R; val zf = RECT.z + R
        val h = y1 - y0
        val wB = 2 * PI.toFloat() * R
        val arc = R * (asin((y1 / R).coerceIn(-1f, 1f)) - asin((y0 / R).coerceIn(-1f, 1f)))
        val wA = (2 * PI * R * h / arc).toFloat()            // 2π × centroid radius of the arc = exact area / slant
        val yA = yb + h + 0.022f
        flatB.pos = P3(x0 + wB / 2, yb + h / 2, zf); flatB.scale = P3(wB, h, 1f)
        flatA.pos = P3(x0 + wA / 2, yA + arc / 2, zf); flatA.scale = P3(wA, arc, 1f)
        fun box(i: Int, xa: Float, ya: Float, xb: Float, yb2: Float) {
            segF(flatEdges[i], xa, ya, zf + 0.001f, xb, ya, zf + 0.001f, 0.0016f); segF(flatEdges[i + 1], xb, ya, zf + 0.001f, xb, yb2, zf + 0.001f, 0.0016f)
            segF(flatEdges[i + 2], xb, yb2, zf + 0.001f, xa, yb2, zf + 0.001f, 0.0016f); segF(flatEdges[i + 3], xa, yb2, zf + 0.001f, xa, ya, zf + 0.001f, 0.0016f)
        }
        box(0, x0, yb, x0 + wB, yb + h); box(4, x0, yA, x0 + wA, yA + arc)
        txFlat[0].pos = P3(x0 - 0.012f - decalWidth("h", 0.030f) / 2, yb + h / 2, zf + 0.004f)
        txFlat[1].pos = P3(x0 + wA + 0.014f + decalWidth("hcos", 0.030f) / 2, yA + arc / 2, zf + 0.004f)
        txFlat[2].pos = P3(x0 + wA / 2, yA + arc + 0.024f, zf + 0.004f)
    }

    private fun bandMid(k: Int): Float = R - 2 * R * (k + 0.5f) / NB

    private fun layoutRings() {
        val p = level.coerceIn(0f, 1f)
        val on = level > 0.002f && level <= 1.0005f
        val e = ease(p)
        val hk = 2 * R / NB * 0.965f
        for (k in 0 until NB) {
            val n = rings[k]; n.visible = on
            if (!on) continue
            val y = bandMid(k)
            val rho0 = sqrt(max(0f, R * R - y * y))
            val rr = rho0 + (R * 1.003f - rho0) * e
            n.pos = P3(O.x, O.y + y, O.z); n.scale = P3(rr, hk, rr)
        }
    }

    private fun layoutPaint() {
        val u = (level - 1f).coerceIn(0f, 1f)
        val paintOn = level > 1.0005f && level <= 2.0005f
        paint.visible = paintOn
        if (paintOn) {
            val slide = ease(u / 0.3f)
            paint.pos = P3(C.x + (RECT.x - C.x) * slide, C.y + (RECT.y - C.y) * slide, C.z)
            paint.rot = Q.axisAngle(1f, 0f, 0f, tiltNow * (1f - slide))
            val q = ((u - 0.3f) / 0.7f).coerceIn(0f, 1f)
            val k = (ease(q) * K_UNROLL).roundToInt().coerceIn(0, K_UNROLL)
            if (paint.mesh !== unrollMeshes[k]) paint.setMesh(unrollMeshes[k])
        }
        val f = (level - 2f).coerceIn(0f, 1f)
        val stripOn = level > 2.0005f
        val gap = 0.03f * ease(f / 0.2f)
        val g = ease((f - 0.2f) / 0.8f)
        val m = (g * M_MORPH).roundToInt().coerceIn(0, M_MORPH)
        val sw = (PI * R / 2).toFloat()
        for (i in 0 until 4) {
            val s = strips[i]; s.visible = stripOn
            if (!stripOn) continue
            val sx = RECT.x + (i - 1.5f) * (sw + gap); val sy = RECT.y; val sz = RECT.z + R
            val d = DISC[i]
            s.pos = P3(sx + (d.x - sx) * g, sy + (d.y - sy) * g, sz + 0.001f * i)
            if (s.mesh !== stripMeshes[m]) s.setMesh(stripMeshes[m])
        }
        val done = level > 2.97f
        for (i in 0 until 4) { discRims[i].visible = done; discTx[i].visible = done }
        val lit = if (done) 1 else 0
        if (lit != shadowLit) { shadowLit = lit; shadowRim.setMat(if (done) rimLit else rimDim); discRims.forEach { it.setMat(rimLit) } }
        val ghostOn = level < 1.999f
        ghost.forEach { it.visible = ghostOn }
        tx2pir.visible = level < 2.15f; tx2r.visible = level > 0.999f && level < 2.15f
        txRect.visible = level > 1.97f && level < 2.6f
        txFour.visible = done
    }

    private fun layoutVolume() {
        val t = TILT * (1f - ease(vol / 0.15f))
        if (t != tiltNow) { tiltNow = t; globe.rot = Q.axisAngle(1f, 0f, 0f, t) }
        val a = if (vol < 0.3f) vol / 0.3f else 1f - (vol - 0.3f) / 0.7f
        val a20 = (a * 0.55f * 20f).roundToInt()
        sphereWater.visible = vol > 0.001f && a20 > 0
        if (sphereWater.visible) sphereWater.setMat(waterMat(a20))
        val hFrac = ((vol - 0.3f) / 0.7f).coerceIn(0f, 1f)
        val hh = Archimedes.pouredHeight(R.toDouble()).toFloat() * hFrac
        water.visible = hh > 0.0005f
        val lvl = (hh * 2000f).roundToInt()
        waterTop.visible = water.visible
        if (water.visible && lvl != waterLevelShown) {
            waterLevelShown = lvl
            water.scale = P3(R * 0.985f, hh, R * 0.985f); water.pos = P3(O.x, O.y - R + hh / 2, O.z)
            waterTop.pos = P3(O.x, O.y - R + hh, O.z)
        }
        txVol.visible = vol > 0.97f
    }

    // ---------------------------------------------------------------- numbers
    private fun cm2(m2: Double) = m2 * 1e4
    /** Measured (inscribed frustums) sphere band area and the cylinder band area, cm². */
    fun bandAreas(): Pair<Double, Double> {
        val (y0, y1) = bandY()
        val s = Archimedes.sphereBandAreaFrustums(R.toDouble(), y0.toDouble(), y1.toDouble(), 400)
        val c = Archimedes.cylinderBandArea(R.toDouble(), y0.toDouble(), y1.toDouble())
        return cm2(s) to cm2(c)
    }
    fun ratio(): Double { val (s, c) = bandAreas(); return s / c }

    private fun latText(lat: Float) = if (lat >= 0f) tr("北纬 %.0f°".format(lat), "lat %.0f°N".format(lat)) else tr("南纬 %.0f°".format(-lat), "lat %.0f°S".format(-lat))

    private fun updateReadout() {
        val th = latDeg * PI / 180
        val (sA, cA) = bandAreas()
        val sph = cm2(Archimedes.sphereArea(R.toDouble()))
        val l1 = tr("带：${latText(latDeg)} · 厚 h = %.1f cm · 斜宽 ×1/cosθ = %.2f · 半径 ×cosθ = %.2f · 乘积 = %.2f",
            "Band: ${latText(latDeg)} · h = %.1f cm · slant ×1/cosθ = %.2f · radius ×cosθ = %.2f · product = %.2f")
            .format(hCm, 1 / cos(th), cos(th), (1 / cos(th)) * cos(th))
        val projected = (level.coerceIn(0f, 1f) * NB).toInt()
        val l2 = when {
            level < 1f -> tr("球面带 %.1f cm²（实测）· 柱面带 2πrh = %.1f cm² · 比 = %.3f · 已投影 %d/12 · 累计 %.0f / 4πr² = %.0f cm²",
                "Sphere band %.1f cm² (measured) · cylinder band 2πrh = %.1f cm² · ratio %.3f · projected %d/12 · total %.0f of 4πr² = %.0f cm²")
                .format(sA, cA, sA / cA, projected, sph * projected / NB, sph)
            level < 2.5f -> tr("12 条带全部投到圆柱：圆柱侧面 = 2πr × 2r = %.1f × %.1f cm = %.0f cm² = 4πr²",
                "All 12 bands on the cylinder: its side = 2πr × 2r = %.1f × %.1f cm = %.0f cm² = 4πr²")
                .format(2 * PI * 13, 26.0, sph)
            else -> tr("4 条各 πr/2 × 2r = %.0f cm² → 4 个半径 r 的圆盘，各 πr² = %.0f cm²：总和 %.0f cm²",
                "4 strips of πr/2 × 2r = %.0f cm² → 4 discs of radius r, each πr² = %.0f cm²: total %.0f cm²")
                .format(sph / 4, sph / 4, sph)
        }
        val l3 = when {
            vol > 0.5f -> tr("体积（阿基米德的墓碑）：V球 = 4/3 πr³ = 2/3 × 圆柱 2πr³ —— 倒进圆柱正好到 2/3 高",
                "Volume (Archimedes' tombstone): V = 4/3 πr³ = 2/3 of the cylinder's 2πr³ — poured in, it fills 2/3")
            level < 1f -> tr("靠近极点，带子斜向变宽 ×1/cosθ，一圈却变小 ×cosθ —— 两者抵消，面积和柱面上的投影一样",
                "Near a pole the band is wider on the slant (×1/cosθ) but smaller around (×cosθ): they cancel, same area")
            level < 2f -> tr("每条带投影后面积不变 → 球面积 = 圆柱侧面积", "Every band keeps its area when projected → sphere area = cylinder side")
            level < 3f -> tr("展开：2πr × 2r = 4πr²，条纹和球上一一对应", "Unrolled: 2πr × 2r = 4πr², stripe for stripe the sphere's bands")
            else -> tr("面积相同，形状重排：4πr² = 4 × πr² —— 正好四个影子", "Same area, rearranged: 4πr² = 4 × πr² — exactly four shadows")
        }
        readoutText = "$l1\n$l2\n$l3"
    }

    private fun updateLabels() {
        labels.clear()
        val yc = R * sin(latDeg * PI.toFloat() / 180f)
        labels += LabelSpec(latText(latDeg) + tr(" · 面积比 %.3f", " · ratio %.3f").format(ratio()), SoftRender.worldOf(knob).t.let { P3(it[0] + 0.10f, it[1] + 0.04f, it[2] + 0.04f) }, visible = bandVisible)
        labels += LabelSpec(tr("影子 = πr²", "shadow = πr²"), P3(SHADOW_C.x, SHADOW_C.y + R + 0.05f, SHADOW_C.z + 0.04f))
        labels += when {
            level >= 2.97f -> LabelSpec(tr("4 个影子 = 4πr²", "4 shadows = 4πr²"), P3(RECT.x + DX + R + 0.02f, RECT.y + DX + R, 0.18f))
            level >= 1.97f -> LabelSpec(tr("圆柱侧面 2πr × 2r", "cylinder side 2πr × 2r"), P3(RECT.x + 0.25f, RECT.y + R + 0.10f, 0.18f))
            else -> LabelSpec(tr("圆柱：半径 r，高 2r", "cylinder: radius r, height 2r"), P3(C.x, C.y + R + 0.08f, 0.05f))
        }
        if (vol > 0.5f) labels += LabelSpec(tr("体积 = 圆柱的 2/3", "volume = 2/3 of the cylinder"), P3(TOMB.x + 0.20f, TOMB.y + 0.02f, TOMB.z + 0.03f))
    }

    // ---------------------------------------------------------------- actions (pinch, panel, lesson, tests)
    fun setLatitude(deg: Float) { latDeg = deg.coerceIn(-LAT_MAX, LAT_MAX); if (levelT > 1f) levelT = 1f }
    fun setThickness(cm: Float) { hCm = cm.coerceIn(0.5f, H_MAX) }
    fun goTo(l: Float) { levelT = l.coerceIn(0f, 3f) }
    /** Jump straight to a level (review variants). */
    fun jumpTo(l: Float) { level = l; levelT = l }
    fun toggleVolume() { volT = if (volT > 0.5f) 0f else 1f }
    fun resetAll() { resetState() }

    override val tapTargets: List<TapTarget> get() = listOf(
        TapTarget("BandKnob", "带子把手 → 换纬度（可拖）", "Band knob → next latitude (drag it)"),
        TapTarget("Sphere", "球 → 全部投影", "Sphere → project all bands") { levelT >= 1f },
        TapTarget("Lid", "圆柱盖 → 展开侧面", "Cylinder lid → unroll its side") { levelT >= 2f },
        TapTarget("Shadow", "影子 → ×4", "Shadow → ×4") { levelT >= 3f },
        TapTarget("Tomb", "墓碑 → 体积", "Tombstone → volume") { volT > 0.5f },
    )

    override fun isTarget(name: String) = name in setOf("BandKnob", "Sphere", "Lid", "Shadow", "Tomb")

    override fun onTap(name: String) {
        when (name) {
            "BandKnob" -> setLatitude(listOf(0f, 30f, 60f, 75f).firstOrNull { it > latDeg + 0.5f } ?: 0f)
            "Sphere" -> goTo(if (levelT >= 1f) 0f else 1f)
            "Lid" -> goTo(if (levelT >= 2f) 1f else 2f)
            "Shadow" -> goTo(if (levelT >= 3f) 2f else 3f)
            "Tomb" -> toggleVolume()
        }
        update(0f)
    }

    override fun tapMessage(name: String): String? = when (name) {
        "BandKnob" -> tr("${latText(latDeg)}：斜宽 ×%.2f，半径 ×%.2f，面积比 %.3f".format(1 / cos(latDeg * PI / 180), cos(latDeg * PI / 180), ratio()),
            "${latText(latDeg)}: slant ×%.2f, radius ×%.2f, area ratio %.3f".format(1 / cos(latDeg * PI / 180), cos(latDeg * PI / 180), ratio()))
        "Sphere" -> if (levelT >= 1f) tr("12 条带一起径直投到圆柱上", "All 12 bands fly straight out to the cylinder") else tr("带子回到球上", "Bands back on the sphere")
        "Lid" -> if (levelT >= 2f) tr("圆柱侧面展开：2πr × 2r", "Unrolling the side: 2πr × 2r") else tr("卷回圆柱", "Rolled back up")
        "Shadow" -> if (levelT >= 3f) tr("切成 4 条，各卷成一个影子大小的圆盘", "Cut into 4 strips, each rolls into a shadow-sized disc") else tr("圆盘变回长方形", "Discs back to the rectangle")
        "Tomb" -> if (volT > 0.5f) tr("把球倒进圆柱：正好 2/3 高", "Pour the sphere into the cylinder: exactly 2/3 full") else tr("水倒掉了", "Water poured out")
        else -> null
    }

    override fun isDragTarget(name: String) = name == "BandKnob"
    override fun onDragStart(name: String) { yDrag = R * sin(latDeg * PI.toFloat() / 180f) }
    override fun onDrag(name: String, dx: Float, dy: Float, dz: Float) {
        yDrag = (yDrag + dy * cos(tiltNow) + dz * sin(tiltNow)).coerceIn(-R, R)
        setLatitude(asin(yDrag / R) * 180f / PI.toFloat())
    }
    override fun onDragEnd(name: String) {}

    // ---------------------------------------------------------------- guided track (导学)
    override val lesson: Lesson by lazy {
        Lesson(
            listOf(
                LessonStep(
                    say = Bi("球的表面积是它影子（大圆，πr²）的几倍？", "A sphere's surface area is how many times its shadow (the great circle, πr²)?"),
                    options = listOf(Bi("2 倍", "2 times"), Bi("π 倍", "π times"), Bi("4 倍", "4 times"), Bi("6 倍", "6 times")),
                    answer = 2,
                    onEnter = { goTo(0f); setLatitude(20f) },
                ),
                LessonStep(
                    say = Bi("捏住右边的金色把手往上拖，把发光的带子拖到北纬 60° 以上，看圆柱上和它同色的“双胞胎”。", "Pinch the gold knob on the right and drag the glowing band above 60° north; watch its same-colour twin on the cylinder."),
                    target = "BandKnob",
                    onEnter = { goTo(0f); maxLatShown = abs(latDeg) },
                    done = { maxLatShown > 60f },
                    autoplay = { setLatitude(70f) },
                    insight = Bi("越靠近极点，带子斜着越宽（橙色 ×1/cosθ），一圈却越小（蓝色 ×cosθ）——两个因子正好抵消：面积和圆柱上的投影一模一样。", "Toward the pole the band gets wider on the slant (orange, ×1/cosθ) but smaller around (blue, ×cosθ): the factors cancel, so it has exactly the area of its projection."),
                ),
                LessonStep(
                    say = Bi("捏一下球，让 12 条带全部投到圆柱上；再捏圆柱顶上的盖子，把侧面展开。", "Pinch the sphere to project all 12 bands onto the cylinder, then pinch the lid on top to unroll its side."),
                    target = "Lid",
                    done = { level >= 2f },
                    autoplay = { goTo(2f) },
                    insight = Bi("每条带面积都不变，所以球面积 = 圆柱侧面 = 一个 2πr × 2r 的长方形 = 4πr²。", "Every band keeps its area, so the sphere's area = the cylinder's side = a 2πr × 2r rectangle = 4πr²."),
                ),
                LessonStep(
                    say = Bi("捏左后方的影子：把长方形切成 4 条，看它们各卷成什么。", "Pinch the shadow at the back left: the rectangle is cut into 4 strips — watch what each becomes."),
                    target = "Shadow",
                    done = { level >= 3f },
                    autoplay = { goTo(3f) },
                    reveal = true,
                    insight = Bi("每条 πr/2 × 2r = πr²，正好卷成一个影子大小的圆盘（面积相同，形状重排）：4πr² = 4 × πr²，四个影子。", "Each strip πr/2 × 2r = πr² rolls into one shadow-sized disc (same area, rearranged): 4πr² = 4 × πr², four shadows."),
                ),
            ),
            challenge = Challenge(
                say = Bi("挑战：把带子拖到北纬 75°（±1°），读出球面带和柱面带的面积比。", "Challenge: drag the band to 75° north (±1°) and read the ratio of the sphere band to the cylinder band."),
                progress = { tr("现在：${latText(latDeg)} · 面积比 %.3f（目标 74°–76°）".format(ratio()), "Now: ${latText(latDeg)} · area ratio %.3f (target 74°–76°)".format(ratio())) },
                done = { bandVisible && abs(latDeg - 75f) <= 1f },
                autoplay = { setLatitude(75f) },
                onEnter = { goTo(0f); setLatitude(30f) },
            ),
        )
    }

    // ---------------------------------------------------------------- review states
    override fun reviewVariants(): Map<String, () -> Unit> = mapOf(
        "pole" to { jumpTo(0f); setLatitude(75f) },
        "projected" to { jumpTo(1f) },
        "unrolling" to { jumpTo(1.62f) },
        "unrolled" to { jumpTo(2f) },
        "morphing" to { jumpTo(2.55f) },
        "four" to { jumpTo(3f) },
        "volume" to { jumpTo(0f); volT = 1f; vol = 1f },
    )

    // ---------------------------------------------------------------- panel
    @Composable
    override fun Panel() {
        val lat = latDeg
        val h = hCm
        val sp by FieldRef(::speed)
        LabPanel(readout) {
            LabRow {
                LabSlider(tr("纬度 θ", "Latitude θ"), lat, -LAT_MAX..LAT_MAX, "%.0f°".format(lat)) { setLatitude(it) }
                LabSlider(tr("带厚 h", "Band h"), h, 0.5f..H_MAX, "%.1f cm".format(h)) { setThickness(it) }
            }
            LabRow {
                SegmentControl {
                    listOf(tr("慢", "Slow"), tr("中", "Medium"), tr("快", "Fast")).forEachIndexed { k, s ->
                        SegmentItem(selected = sp == k, onClick = { speed = k }, title = { Text(s) })
                    }
                }
                Spacer(Modifier.width(16.dp))
                Button(onClick = { resetAll() }) { Text(tr("重置", "Reset")) }
            }
        }
    }
}
