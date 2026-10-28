package com.bytedance.mathlab.core.exponential

import com.bytedance.mathlab.demos.exponential.ExponentialDemo
import com.bytedance.mathlab.lab.spec.Node
import com.bytedance.mathlab.lab.spec.SoftRender
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.sqrt

/**
 * The exponential demo's scene against the brief's device rules, in every station and review state:
 *  - visible nodes < 600 (device budget), every pinch target's WORLD radius >= 0.045 m;
 *  - everything drawn stays inside the volume content box (|x| < 0.9, |z| < 0.6, -0.27 < y < 0.45);
 *  - the true-scale board: the drawn column of square n equals 2^(n−1) × 8 µm until it is clipped at 0.56 m.
 */
class ExponentialSceneTest {
    private val cache = File("build/review-cache").apply { mkdirs() }

    private fun visibleCount(root: Node): Int {
        var n = 0
        fun rec(x: Node) { if (!x.visible) return; n++; x.children.forEach { rec(it) } }
        rec(root); return n
    }

    private fun worldRadius(n: Node): Float {
        val a = SoftRender.worldOf(n)
        val v = a.mul(n.tapRadius, 0f, 0f)
        return sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
    }

    @Test fun everyStationFitsTheVolumeAndTheBudget() {
        val d = ExponentialDemo
        val root = d.runHeadless(cache, 2f)
        val states = linkedMapOf<String, () -> Unit>("default" to {}) + d.reviewVariants()
        val bad = ArrayList<String>()
        for ((name, apply) in states) {
            apply(); repeat(90) { d.update(1f / 60f) }
            val vis = visibleCount(root)
            var lo = floatArrayOf(9f, 9f, 9f); var hi = floatArrayOf(-9f, -9f, -9f)
            var worst = ""
            SoftRender.forEachMesh(root) { n, w ->
                if (n.name.startsWith("Halo_") || n.name.startsWith("Plinth")) return@forEachMesh
                val m = n.mesh!!
                for (k in 0 until m.vertexCount) {
                    val p = w.apply(m.pos[k * 3], m.pos[k * 3 + 1], m.pos[k * 3 + 2])
                    if (kotlin.math.abs(p[0]) > 0.9f || kotlin.math.abs(p[2]) > 0.6f || p[1] < -0.27f || p[1] > 0.45f) worst = "${n.name} (%.2f, %.2f, %.2f)".format(p[0], p[1], p[2])
                    for (a in 0..2) { lo[a] = minOf(lo[a], p[a]); hi[a] = maxOf(hi[a], p[a]) }
                }
            }
            println("SCENE $name: station ${d.station}, visible nodes $vis, bounds x %.2f..%.2f y %.2f..%.2f z %.2f..%.2f".format(lo[0], hi[0], lo[1], hi[1], lo[2], hi[2]))
            if (vis >= 600) bad += "$name: $vis visible nodes"
            if (worst.isNotEmpty()) bad += "$name: outside the volume: $worst"
            for (t in d.allTargets(d.station)) {
                val n = root.find(t) ?: run { bad += "$name: no node $t"; null } ?: continue
                val r = worldRadius(n)
                if (r < 0.045f) bad += "$name: $t pinch radius %.3f m".format(r)
            }
        }
        bad.forEach { println("SCENE-BAD $it") }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    /** TapFeedbackTest only sees the station the demo opens on; here every station's targets are pinched, in both languages. */
    @Test fun everyStationsTargetsAnswerAPinch() {
        val d = ExponentialDemo
        d.runHeadless(cache, 1f)
        val cjk = Regex("[\\u4e00-\\u9fff]")
        val bad = ArrayList<String>()
        var n = 0
        for (en in listOf(false, true)) {
            com.bytedance.mathlab.lab.Lang.set(en)
            for (st in 0..3) {
                d.selectStation(st); d.update(1f / 60f)
                for (t in d.tapTargets.filter { !it.node.startsWith("Tok") }) {
                    d.onTap(t.node); d.update(1f / 60f)
                    if (d.station != st) d.selectStation(st)
                    val msg = d.tapMessage(t.node)
                    val at = d.anchorOf(t.node)
                    if (msg.isNullOrBlank()) bad += "st$st ${t.node}: no message"
                    if (en && msg != null && cjk.containsMatchIn(msg)) bad += "st$st ${t.node}: CJK in EN '$msg'"
                    if (at == null || at.any { !it.isFinite() } || kotlin.math.abs(at[0]) > 1.1f || at[1] !in -0.6f..0.75f) bad += "st$st ${t.node}: anchor ${at?.toList()}"
                    if (!en) println("TAP st$st ${t.node} -> $msg")
                    n++
                }
            }
        }
        com.bytedance.mathlab.lab.Lang.set(false)
        println("TAP all stations: $n pinches, problems: ${if (bad.isEmpty()) "none" else bad.joinToString("; ")}")
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    /**
     * The readout must never wrap (UiPreviewTest measures only the rendered states). Sweep EVERY state's readout with a
     * conservative width model (CJK / full-width 22 dp, anything else 11 dp at 22 sp; the real font is narrower) and
     * keep each line under the panel's 1504 dp.
     */
    @Test fun noReadoutLineCanWrap() {
        val d = ExponentialDemo
        d.runHeadless(cache, 0.2f)
        fun w(line: String) = line.sumOf { c -> if (c.code in 0x2E80..0xFFEF && c !in "×−·–—…≈≥→⁰¹²³⁴⁵⁶⁷⁸⁹ⁿ∞") 22.0 else 11.0 }
        val worst = HashMap<String, Pair<Double, String>>()
        fun check(tag: String) {
            d.update(1f / 60f)
            for (l in d.readoutText.lines()) { val x = w(l); if (x > (worst[tag]?.first ?: 0.0)) worst[tag] = x to l }
            assertTrue("$tag: ${d.readoutText.lines().size} lines", d.readoutText.lines().size == 3)
        }
        for (en in listOf(false, true)) {
            com.bytedance.mathlab.lab.Lang.set(en)
            val L = if (en) "en" else "zh"
            d.selectStation(0)
            for (lg in listOf(false, true)) for (f in 0..64) { d.useLog1(lg); d.fillTo(f); check("$L/board") }
            d.selectStation(1)
            for (k in 2..3) for (lg in listOf(false, true)) for (n in 1..Race.N_MAX) { d.polyK = k; d.useLog2(lg); d.raceTo(n); check("$L/race") }
            d.selectStation(2)
            for (n in (1L..10_000L).filter { it < 200 || it % 37 == 0L } + Compound.CONTINUOUS) { d.periodsTo(n); check("$L/interest") }
            d.selectStation(3)
            for (k in 10..40) { d.r0To(k / 20f); check("$L/spread") }
        }
        com.bytedance.mathlab.lab.Lang.set(false)
        for ((k, v) in worst.toSortedMap()) println("READOUT widest $k ≈ %.0f dp: ${v.second}".format(v.first))
        val over = worst.filter { it.value.first > 1504.0 }
        assertTrue("lines may wrap: $over", over.isEmpty())
    }

    @Test fun theBoardIsDrawnToScale() {
        val d = ExponentialDemo
        val root = d.runHeadless(cache, 0.5f)
        d.selectStation(0); d.useLog1(false); d.fillTo(64); d.settle(); d.update(1f / 60f)
        for (n in Rice.FIRST_COLUMN..64) {
            val c = root.find("Col_$n")!!
            val want = minOf(0.56, Rice.columnM(Chessboard.grainsD(n)))
            assertTrue("square $n drawn %.4f m, model %.4f m".format(c.scale.y, want), kotlin.math.abs(c.scale.y - want) < 1e-4)
        }
        // squares 1..8 are real grains: 255 of them visible, none of their columns
        var grains = 0
        root.walk { if (it.name.startsWith("Gr_") && it.visible) grains++ }
        assertTrue("grains $grains", grains == 255)
        println("SCENE board: 255 grains, square 16 column %.3f m, square 17 %.3f m, square 18 clipped at %.2f m".format(
            root.find("Col_16")!!.scale.y, root.find("Col_17")!!.scale.y, root.find("Col_18")!!.scale.y))
    }
}
