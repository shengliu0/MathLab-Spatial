package com.bytedance.mathlab.core.collisions

import com.bytedance.mathlab.demos.collisions.CollisionsDemo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The colliding-blocks clacks: driving the demo frame by frame (60 Hz), the demo requests exactly one CLACK per
 * collision, in the core's event order (block / wall kinds), at M/m = 1, 100, 10⁴ and every speed. Also models
 * the device's 18 ms same-name merge on the frame clock: what is audible is ≤ the requests, and the sparse
 * 1 : 1 run is heard in full (3 clacks).
 */
class CollisionsSoundTest {
    private val cache = File("build/review-cache").apply { mkdirs() }

    /** The core's own event sequence (independent BlockSim, one event at a time). */
    private fun coreKinds(ratio: Double): List<Int> {
        val sim = BlockSim(ratio, 0.05, 0.20, 0.45, -0.30)
        val out = ArrayList<Int>()
        while (!sim.done) sim.advance(sim.next().first, 1) { out += it }
        assertEquals(Collisions.countByAngle(ratio), sim.count)
        return out
    }

    @Test fun oneClackPerCollisionInEventOrder() {
        val d = CollisionsDemo
        d.runHeadless(cache, 0.5f)
        val ratios = doubleArrayOf(1.0, 1e2, 1e4)
        for ((idx, ratio) in ratios.withIndex()) for (speed in 0..2) {
            d.setRatio(idx); d.speed = speed
            val log = ArrayList<Int>(); d.clackLog = log
            d.launch()
            assertEquals(0L, d.runClacks)
            // frame clock: device timestamps of each request (ms), for the 18 ms merge model
            val stamps = ArrayList<Long>()
            var frame = 0
            com.bytedance.mathlab.lab.Sfx.clearQueue(); val rel0 = com.bytedance.mathlab.lab.Sfx.released
            while (d.state == CollisionsDemo.Run.RUNNING && frame < 60 * 600) {
                val before = log.size
                d.update(1f / 60f)
                val ms = frame * 1000L / 60
                repeat(log.size - before) { stamps += ms }
                com.bytedance.mathlab.lab.Sfx.tick(ms)
                frame++
            }
            repeat(120) { k -> com.bytedance.mathlab.lab.Sfx.tick((frame + k) * 1000L / 60) }     // let the queue drain
            val heard = com.bytedance.mathlab.lab.Sfx.released - rel0
            d.clackLog = null
            assertEquals("run did not finish (ratio $ratio speed $speed)", CollisionsDemo.Run.DONE, d.state)
            val core = coreKinds(ratio)
            assertEquals("clacks vs core count (ratio $ratio speed $speed)", core.size.toLong(), d.runClacks)
            assertEquals("clack kinds vs core event order (ratio $ratio speed $speed)", core, log)
            assertEquals(d.lastRunCount, d.runClacks)
            // device merge: same-name calls closer than 18 ms collapse
            var last = Long.MIN_VALUE / 2; var audible = 0
            for (t in stamps) if (t - last >= 18) { audible++; last = t }
            println("CLACKS ratio=$ratio speed=$speed requested=${d.runClacks} core=${core.size} heard(queue) =$heard  (old 18ms merge: $audible) frames=$frame")
            if (ratio <= 1e2 && speed <= 1) assertEquals("every clack heard (ratio $ratio speed $speed)", core.size, heard)
            assertTrue(audible in 1..core.size)
            if (ratio == 1.0) assertEquals("1:1 clacks are far apart, all audible", 3, audible)
        }
    }

    @Test fun finishNowIsSilent() {
        val d = CollisionsDemo
        d.runHeadless(cache, 0.1f)
        d.setRatio(1)
        val log = ArrayList<Int>(); d.clackLog = log
        d.launch(); d.finishNow()
        d.clackLog = null
        assertEquals(0, log.size)
        assertEquals(0L, d.runClacks)
    }
}
