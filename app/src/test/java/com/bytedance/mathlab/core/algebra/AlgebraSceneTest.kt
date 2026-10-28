package com.bytedance.mathlab.core.algebra

import com.bytedance.mathlab.demos.algebra.AlgebraDemo
import com.bytedance.mathlab.lab.Lang
import com.bytedance.mathlab.lab.spec.Node
import com.bytedance.mathlab.lab.spec.SoftRender
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * What the user SEES is the proof: after the demo settles, every piece's node sits exactly where the
 * exact dissection (AlgebraCore) puts it — station ① pieces = the four rectangles of (a+b)², station ②'s
 * swung strip = the target slot of the rigid motion, station ③'s blocks = the 8 boxes of the cube,
 * station ④'s visible cells = n². Plus: every pinch target of every station answers in both languages,
 * drags change a / b / the strip / the cube, and the readout's two sides always agree.
 */
class AlgebraSceneTest {
    private val cache = File("build/review-cache").apply { mkdirs() }
    private val d = AlgebraDemo
    private val eps = 1e-5f

    @After fun back() = Lang.set(false)

    private fun settle(root: Node) { repeat(150) { d.update(1f / 60f) } }
    private fun n(root: Node, name: String) = assertNotNull(name, root.find(name)).let { root.find(name)!! }

    @Test fun piecesSitWhereTheProofSays() {
        val root = d.runHeadless(cache, 0.2f)
        
        var checked = 0
        for (a in Algebra.A_MIN..Algebra.A_MAX step 5) for (b in Algebra.B_MIN..minOf(Algebra.B_MAX, a - Algebra.GAP) step 5) {
            d.setAB(a, b, keepA = true)
            // ① assembled: a², ab, ab, b² exactly on the rectangles of Algebra.squarePieces
            d.selectStation(0); d.explode1 = false; d.abOut.fill(false); settle(root)
            var s2 = d.unitOf(0)
            assertEquals("① fitted: the square is always the same size", 0.53f, (a + b) / 10f * s2, 1e-5f)
            for ((p, id) in Algebra.squarePieces(a, b).zip(listOf("P1_a2", "P1_ab1", "P1_ab2", "P1_b2"))) {
                val hub = n(root, id); val body = n(root, "${id}_m")
                assertEquals("$id x", (p.rect.x0 + p.rect.x1) / 20f * s2, hub.pos.x, eps)
                assertEquals("$id y", (p.rect.y0 + p.rect.y1) / 20f * s2, hub.pos.y, eps)
                assertEquals("$id w", p.rect.w / 10f * s2, body.scale.x, eps)
                assertEquals("$id h", p.rect.h / 10f * s2, body.scale.y, eps)
            }
            // ② after the swing: bottom + strip tile the (a+b) x (a-b) rectangle; the strip's drawn box IS the slot
            d.selectStation(1); d.stage2 = 2; settle(root)
            s2 = d.unitOf(1)
            val target = Algebra.stripTarget(a, b)
            val strip = n(root, "P2_strip"); val sb = n(root, "P2_strip_m")
            val w = SoftRender.Aff.local(strip).mul(sb.scale.x, 0f, 0f); val h = SoftRender.Aff.local(strip).mul(0f, sb.scale.y, 0f)
            val ext = floatArrayOf(abs(w[0]) + abs(h[0]), abs(w[1]) + abs(h[1]))       // drawn width / height after the turn
            assertEquals("strip cx", (target.x0 + target.x1) / 20f * s2, strip.pos.x, eps)
            assertEquals("strip cy", (target.y0 + target.y1) / 20f * s2, strip.pos.y, eps)
            assertEquals("strip w", target.w / 10f * s2, ext[0], eps)
            assertEquals("strip h", target.h / 10f * s2, ext[1], eps)
            val bottom = n(root, "P2_bottom")
            val bRect = Algebra.cutPieces(a, b)[0].rect
            assertEquals((bRect.x0 + bRect.x1) / 20f * s2, bottom.pos.x, eps)
            assertEquals(Algebra.productRect(a, b), Algebra.bounds(listOf(bRect, target)))
            // ③ assembled: the 8 blocks are the 8 boxes (cube-centred)
            d.selectStation(2); d.explode3 = false; d.lifted = -1; settle(root)
            val s3 = d.unitOf(2)
            val half = (a + b) / 20f * s3
            for (blk in Algebra.cubeBlocks(a, b)) {
                val hub = n(root, "B3_${blk.index}"); val body = n(root, "B3_${blk.index}_m")
                val bx = blk.box
                assertEquals((bx.x0 + bx.x1) / 20f * s3 - half, hub.pos.x, eps)
                assertEquals((bx.y0 + bx.y1) / 20f * s3 - half, hub.pos.y, eps)
                assertEquals((bx.z0 + bx.z1) / 20f * s3 - half, hub.pos.z, eps)
                assertEquals((bx.x1 - bx.x0) / 10f * s3 * (bx.y1 - bx.y0) / 10f * s3 * (bx.z1 - bx.z0) / 10f * s3,
                    body.scale.x * body.scale.y * body.scale.z, 1e-7f)
            }
            // the readout's two sides agree
            assertTrue(d.readoutText.lines()[0].trimEnd().endsWith("✓"))
            checked++
        }
        // ④: exactly n² cells visible, layer k drawn with 2k - 1 of them
        d.selectStation(3)
        for (k in 1..10) {
            d.setN(k); settle(root)
            var vis = 0; root.walk { if (it.name.startsWith("T4_") && it.visible) vis++ }
            assertEquals(k * k, vis)
        }
        println("ALGEBRA scene = proof for $checked (a,b) pairs (stations ①②③), ④ n = 1..10")
        assertEquals(29, checked)             // a 1.5..4.5 x b 0.5..min(2.5, a-0.5), step 0.5
    }

    @Test fun everyTargetOfEveryStationAnswers() {
        val cjk = Regex("[\\u4e00-\\u9fff\\u3000-\\u303f\\uff00-\\uffef]")
        var n = 0
        for (en in listOf(false, true)) {
            Lang.set(en)
            for (s in 0..3) {
                d.runHeadless(cache, 0.5f); d.selectStation(s); settle(d.root!!)
                assertEquals(s, d.station)
                val listed = d.tapTargets.map { it.node }.toSet()
                for (name in d.allTargets(s)) {
                    assertTrue("$name listed as a panel twin (station $s)", name in listed)
                    assertTrue("$name is pinchable (station $s)", d.isTarget(name))
                    assertTrue("$name has a gaze radius", d.root!!.find(name)!!.tapRadius > 0f)
                    d.onTap(name); d.update(1f / 60f)
                    val msg = d.tapMessage(name)
                    assertNotNull("message for $name", msg)
                    if (en) assertTrue("CJK in EN note '$msg'", !cjk.containsMatchIn(msg!!))
                    val at = d.anchorOf(name)!!
                    assertTrue("$name anchor ${at.toList()}", at.all { it.isFinite() } && abs(at[0]) < 1.1f && at[1] in -0.6f..0.75f && abs(at[2]) < 1.1f)
                    if (name.startsWith("Tok")) d.selectStation(s)      // stay on the station under test
                    n++
                }
                assertTrue(d.labels.size <= 6)
                // device budget: < ~600 visible nodes (halos included)
                d.selectStation(s); d.update(1f / 60f)
                var vis = 0
                fun count(nd: Node) { if (!nd.visible) return; vis++; nd.children.forEach { count(it) } }
                count(d.root!!)
                if (!en) println("ALGEBRA station ${s + 1}: $vis visible nodes")
                assertTrue("station $s shows $vis nodes", vis < 600)
                assertEquals(3, d.readoutText.lines().size)
            }
        }
        // toggles really toggle
        d.runHeadless(cache, 0.2f)
        d.onTap("P1_ab1"); assertTrue(d.abOut[0]); d.onTap("P1_ab1"); assertTrue(!d.abOut[0])
        d.selectStation(1); d.onTap("P2_corner"); assertEquals(1, d.stage2); d.onTap("P2_strip"); assertEquals(2, d.stage2); d.onTap("P2_strip"); assertEquals(1, d.stage2)
        d.selectStation(2); d.onTap("B3_7"); assertEquals(7, d.lifted); assertTrue(d.seen[7]); d.onTap("B3_7"); assertEquals(-1, d.lifted)
        d.selectStation(3); val n0 = d.n4; d.onTap("KnobN_4"); assertEquals(n0 + 1, d.n4)
        println("ALGEBRA taps: $n target × language pairs over 4 stations answered")
    }

    @Test fun dragsChangeTheGeometry() {
        val root = d.runHeadless(cache, 0.2f)
        // knob a: 1.5 units of hand travel along the ruler (the vertical part is ignored) -> a = 3.0 + 1.5 = 4.5
        d.selectStation(0)
        d.onDragStart("KnobA_1"); d.onDrag("KnobA_1", 1.0f * d.unitOf(0), 0f, 0f); d.onDrag("KnobA_1", 0.5f * d.unitOf(0), 0.02f, 0f); d.onDragEnd("KnobA_1")
        assertEquals(45L, d.aT)
        // knob b cannot pass a - 0.5
        d.onDragStart("KnobB_1"); d.onDrag("KnobB_1", 10 * d.unitOf(0), 0f, 0f); d.onDragEnd("KnobB_1")
        assertEquals(25L, d.bT)
        d.onDragStart("KnobB_1"); d.onDrag("KnobB_1", -1.0f * d.unitOf(0), 0f, 0f); d.onDragEnd("KnobB_1")
        assertEquals(15L, d.bT)
        // ②: dragging the strip along its chord swings it home; released past halfway it snaps in
        d.setAB(30, 20, keepA = true); d.selectStation(1); settle(root)
        val a = 3f * d.unitOf(1); val b = 2f * d.unitOf(1)
        d.onDragStart("P2_strip"); assertEquals(1, d.stage2)
        d.onDrag("P2_strip", (a / 2 + b) * 0.7f, (-a / 2) * 0.7f, 0f)
        d.onDragEnd("P2_strip"); assertEquals(2, d.stage2)
        // ③: dragging a block turns the cube
        d.selectStation(2); settle(root)
        val r0 = root.find("St3")!!.rot
        d.onDragStart("B3_0"); d.onDrag("B3_0", 0.10f, 0f, 0f); d.onDragEnd("B3_0"); d.update(1f / 60f)
        assertTrue(root.find("St3")!!.rot != r0)
        // ④: knob n follows the hand in whole cells
        d.selectStation(3); d.setN(5)
        d.onDragStart("KnobN_4"); d.onDrag("KnobN_4", 2.4f * d.unitOf(3), 0f, 0f); d.onDragEnd("KnobN_4")
        assertEquals(7, d.n4)
        println("ALGEBRA drags: a -> ${d.aT / 10.0}, strip stage ${d.stage2}, n -> ${d.n4}")
    }
}
