package com.bytedance.mathlab.core.euler

import com.bytedance.mathlab.demos.euler.EulerDemo
import com.bytedance.mathlab.lab.spec.Node
import com.bytedance.mathlab.lab.spec.SoftRender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * The Euler demo's scene against the brief's device rules, in every station and review state:
 *  - visible nodes < 600, every pinch target's WORLD radius >= 0.045 m;
 *  - everything drawn stays inside the volume content box (|x| < 0.9, |z| < 0.6, -0.27 < y < 0.45);
 *  - the helix meshes are rebuilt only when ω or σ change (the point runs by transform), and the running
 *    point sits on the drawn helix: (R cos ωt, R sin ωt) in the rig frame.
 */
class EulerSceneTest {
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

    @Test fun everyStateFitsTheVolumeAndTheBudget() {
        val d = EulerDemo
        val root = d.runHeadless(cache, 2f)
        val states = linkedMapOf<String, () -> Unit>("default" to {}) + d.reviewVariants()
        val bad = ArrayList<String>()
        for ((name, apply) in states) {
            apply(); repeat(90) { d.update(1f / 60f) }
            val vis = visibleCount(root)
            val lo = floatArrayOf(9f, 9f, 9f); val hi = floatArrayOf(-9f, -9f, -9f)
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

    @Test fun helixIsRebuiltOnlyOnParameterChangeAndThePointRidesIt() {
        val d = EulerDemo
        val root = d.runHeadless(cache, 1f)
        val b0 = d.helixBuilds
        repeat(300) { d.update(1f / 60f) }                       // 5 s of running time, nothing changed
        assertEquals("helix rebuilt while only time ran", b0, d.helixBuilds)
        d.omegaTo(2f); repeat(30) { d.update(1f / 60f) }
        assertEquals(b0 + 1, d.helixBuilds)
        // the point at time t is (R cos ωt, R sin ωt, −t·L/T) in the frame, and lies on the tube's centre line
        val p = root.find("Point1")!!.pos
        val t = d.t1.toDouble()
        val r = 0.15; val z = Euler.helix(2.0, 0.0, t)
        assertEquals(r * z.re, p.x.toDouble(), 1e-5); assertEquals(r * z.im, p.y.toDouble(), 1e-5)
        assertEquals(-t * 0.64 / (4 * PI), p.z.toDouble(), 1e-5)
        val helix = root.find("Helix")!!.mesh!!
        var best = Double.MAX_VALUE
        for (k in 0 until helix.vertexCount) {
            val dx = helix.pos[k * 3] - p.x; val dy = helix.pos[k * 3 + 1] - p.y; val dz = helix.pos[k * 3 + 2] - p.z
            best = minOf(best, sqrt((dx * dx + dy * dy + dz * dz).toDouble()))
        }
        println("SCENE point-to-helix-surface distance %.4f m (tube radius 0.0042, sample spacing ≈ 0.0055 m)".format(best))
        assertTrue(best < 0.0042 + 0.004)
        // throttle: a sweep of ω during one second rebuilds at most every 0.12 s
        val b1 = d.helixBuilds
        for (i in 0 until 60) { d.omegaTo(1f + i / 20f); d.update(1f / 60f) }
        println("SCENE ω swept 60 times in 1 s -> ${d.helixBuilds - b1} rebuilds")
        assertTrue(d.helixBuilds - b1 <= 9)
    }
}
