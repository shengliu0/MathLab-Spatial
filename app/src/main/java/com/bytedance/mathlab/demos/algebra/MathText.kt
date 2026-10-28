// MathText — a tiny vector stroke font for the algebra terms ("a²", "a²b", "(a+b)³ = ...") baked ONCE
// into textures, so the pieces carry crisp labels in 3-D (and in the headless review renders, which do
// not draw SpatialUI label chips). Glyphs are polylines in em units; each pixel's coverage comes from
// its exact distance to the nearest stroke (round caps), so the edges are anti-aliased at any size.
// Pure Kotlin (no Android): the same bake runs on the Mac JVM and on the headset (cached by BakeCache).
//
// Markup: '^' raises the next character as a superscript ("a^2b" -> a²b). Letters are math italic.

package com.bytedance.mathlab.demos.algebra

import com.bytedance.mathlab.render.Image
import com.bytedance.mathlab.render.ProcTex
import com.bytedance.mathlab.render.Rgb
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal object MathText {
    class Span(val text: String, val color: Rgb)

    private class Glyph(val adv: Float, val strokes: List<FloatArray>, val italic: Boolean = false)

    // ---------------------------------------------------------------- glyph construction (em units)
    private fun l(vararg p: Float) = p
    private fun arc(cx: Float, cy: Float, rx: Float, ry: Float, d0: Float, d1: Float, n: Int = 18): FloatArray {
        val out = FloatArray((n + 1) * 2)
        for (k in 0..n) {
            val a = (d0 + (d1 - d0) * k / n) * PI.toFloat() / 180f
            out[k * 2] = cx + rx * cos(a); out[k * 2 + 1] = cy + ry * sin(a)
        }
        return out
    }
    private fun ring(cx: Float, cy: Float, rx: Float, ry: Float) = arc(cx, cy, rx, ry, 0f, 360f, 28)
    private fun cat(vararg ps: FloatArray): FloatArray { val o = ArrayList<Float>(); ps.forEach { p -> p.forEach { o += it } }; return o.toFloatArray() }
    private fun rot180(p: FloatArray, w: Float, h: Float) = FloatArray(p.size) { if (it % 2 == 0) w - p[it] else h - p[it] }

    private val six = listOf(ring(0.285f, 0.225f, 0.205f, 0.225f),
        cat(arc(0.37f, 0.40f, 0.30f, 0.33f, 72f, 180f, 14), l(0.08f, 0.24f)))

    private val glyphs: Map<Char, Glyph> = mapOf(
        'a' to Glyph(0.60f, listOf(ring(0.25f, 0.245f, 0.205f, 0.245f), l(0.455f, 0.49f, 0.455f, 0.0f)), italic = true),
        'b' to Glyph(0.60f, listOf(l(0.07f, 0.76f, 0.07f, 0.0f), ring(0.285f, 0.245f, 0.215f, 0.245f)), italic = true),
        'n' to Glyph(0.60f, listOf(l(0.07f, 0.49f, 0.07f, 0.0f), cat(arc(0.26f, 0.28f, 0.19f, 0.21f, 180f, 0f, 14), l(0.45f, 0.0f))), italic = true),
        '0' to Glyph(0.56f, listOf(ring(0.28f, 0.36f, 0.21f, 0.36f))),
        '1' to Glyph(0.56f, listOf(l(0.12f, 0.57f, 0.31f, 0.72f, 0.31f, 0.0f))),
        '2' to Glyph(0.56f, listOf(cat(arc(0.27f, 0.525f, 0.205f, 0.195f, 158f, -32f), l(0.06f, 0.0f, 0.50f, 0.0f)))),
        '3' to Glyph(0.56f, listOf(arc(0.265f, 0.545f, 0.18f, 0.175f, 150f, -90f), arc(0.265f, 0.195f, 0.21f, 0.195f, 90f, -150f))),
        '4' to Glyph(0.56f, listOf(l(0.38f, 0.0f, 0.38f, 0.72f, 0.04f, 0.22f, 0.52f, 0.22f))),
        '5' to Glyph(0.56f, listOf(l(0.46f, 0.72f, 0.12f, 0.72f, 0.10f, 0.41f), arc(0.27f, 0.235f, 0.21f, 0.235f, 128f, -150f))),
        '6' to Glyph(0.56f, six),
        '7' to Glyph(0.56f, listOf(l(0.06f, 0.72f, 0.50f, 0.72f, 0.20f, 0.0f))),
        '8' to Glyph(0.56f, listOf(ring(0.28f, 0.55f, 0.165f, 0.17f), ring(0.28f, 0.20f, 0.205f, 0.20f))),
        '9' to Glyph(0.56f, six.map { rot180(it, 0.56f, 0.72f) }),
        '+' to Glyph(0.62f, listOf(l(0.07f, 0.30f, 0.55f, 0.30f), l(0.31f, 0.06f, 0.31f, 0.54f))),
        '−' to Glyph(0.62f, listOf(l(0.07f, 0.30f, 0.55f, 0.30f))),
        '-' to Glyph(0.62f, listOf(l(0.07f, 0.30f, 0.55f, 0.30f))),
        '=' to Glyph(0.62f, listOf(l(0.07f, 0.40f, 0.55f, 0.40f), l(0.07f, 0.20f, 0.55f, 0.20f))),
        '×' to Glyph(0.62f, listOf(l(0.13f, 0.10f, 0.49f, 0.46f), l(0.13f, 0.46f, 0.49f, 0.10f))),
        '(' to Glyph(0.34f, listOf(arc(0.47f, 0.30f, 0.32f, 0.52f, 128f, 232f))),
        ')' to Glyph(0.34f, listOf(arc(-0.13f, 0.30f, 0.32f, 0.52f, 52f, -52f))),
        '·' to Glyph(0.30f, listOf(l(0.15f, 0.30f, 0.15f, 0.30f))),
        '.' to Glyph(0.24f, listOf(l(0.10f, 0.03f, 0.10f, 0.03f))),
        '…' to Glyph(0.80f, listOf(l(0.12f, 0.03f, 0.12f, 0.03f), l(0.38f, 0.03f, 0.38f, 0.03f), l(0.64f, 0.03f, 0.64f, 0.03f))),
        ' ' to Glyph(0.26f, emptyList()),
    )

    private const val SHEAR = 0.16f        // math italic slant for letters
    private const val SUP_SCALE = 0.62f
    private const val SUP_RAISE = 0.40f
    private const val TRACK = 0.05f
    private const val ASC = 0.84f          // above the baseline (parentheses reach 0.82)
    private const val DESC = 0.24f

    private class Seg(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val r: Float, val col: Int)

    /** Width of the laid-out text in em (no padding). */
    fun widthEm(spans: List<Span>): Float = layout(spans, 1f, 0f).second

    /**
     * Bake [spans] at [emPx] pixels per em with stroke width [strokeEm] em, and a dark [outline] of
     * [outlineEm] em around the strokes (null = none). Transparent pixels carry the nearest stroke's
     * colour (straight alpha + bilinear filtering would otherwise draw a dark fringe).
     */
    fun bake(spans: List<Span>, emPx: Int, strokeEm: Float = 0.092f, outline: Rgb? = Rgb(0.03f, 0.04f, 0.07f),
             outlineEm: Float = 0.06f, padEm: Float = 0.10f): Image {
        val e = emPx.toFloat()
        val (segs, wEm) = layout(spans, e, strokeEm * e / 2f)
        val pad = padEm * e + (if (outline != null) outlineEm * e else 0f)
        val w = (((wEm * e + 2 * pad).toInt() + 3) / 4) * 4
        val h = (((ASC + DESC) * e + 2 * pad).toInt() + 1)
        val ox = pad; val oy = pad + ASC * e              // baseline at y = oy (pixels, y down)
        val dist = FloatArray(w * h) { Float.MAX_VALUE }
        val ci = IntArray(w * h)
        val ow = if (outline != null) outlineEm * e else 0f
        for (s in segs) {
            val x0 = ox + s.x0; val y0 = oy - s.y0; val x1 = ox + s.x1; val y1 = oy - s.y1
            val reach = s.r + ow + 1.5f
            val bx0 = max(0, (min(x0, x1) - reach).toInt()); val bx1 = min(w - 1, (max(x0, x1) + reach).toInt() + 1)
            val by0 = max(0, (min(y0, y1) - reach).toInt()); val by1 = min(h - 1, (max(y0, y1) + reach).toInt() + 1)
            val dx = x1 - x0; val dy = y1 - y0; val ll = dx * dx + dy * dy
            for (py in by0..by1) for (px in bx0..bx1) {
                val qx = px + 0.5f - x0; val qy = py + 0.5f - y0
                val t = if (ll < 1e-9f) 0f else ((qx * dx + qy * dy) / ll).coerceIn(0f, 1f)
                val ex = qx - t * dx; val ey = qy - t * dy
                val d = sqrt(ex * ex + ey * ey) - s.r          // signed distance to the stroke outline
                val i = py * w + px
                if (d < dist[i]) { dist[i] = d; ci[i] = s.col }
            }
        }
        val img = Image(w, h)
        val oc = outline ?: Rgb(0f, 0f, 0f)
        for (i in 0 until w * h) {
            val d = dist[i]
            val c = spans[ci[i]].color
            val ct = (0.5f - d).coerceIn(0f, 1f)                         // stroke coverage
            val co = if (outline != null) (0.5f - (d - ow)).coerceIn(0f, 1f) * 0.92f else 0f
            val a = ct + (1f - ct) * co
            if (a <= 0f) { img.px[i] = ProcTex.argb(0f, c.r, c.g, c.b); continue }
            val k = ct / a
            img.px[i] = ProcTex.argb(a, oc.r + (c.r - oc.r) * k, oc.g + (c.g - oc.g) * k, oc.b + (c.b - oc.b) * k)
        }
        return img
    }

    /** Segments in pixels relative to (pen start, baseline), y up; returns (segments, width in em). */
    private fun layout(spans: List<Span>, e: Float, rPx: Float): Pair<List<Seg>, Float> {
        val segs = ArrayList<Seg>()
        var pen = 0f
        spans.forEachIndexed { si, span ->
            var sup = false
            for (ch in span.text) {
                if (ch == '^') { sup = true; continue }
                val g = glyphs[ch] ?: glyphs[' ']!!
                val sc = if (sup) SUP_SCALE else 1f
                val raise = if (sup) SUP_RAISE else 0f
                val r = if (sup) rPx * 0.82f else rPx
                for (p in g.strokes) {
                    val n = p.size / 2
                    fun px(k: Int): Float { val x = p[k * 2] * sc; val y = p[k * 2 + 1] * sc + raise; return (pen + x + (if (g.italic) SHEAR * y else 0f)) * e }
                    fun py(k: Int): Float = (p[k * 2 + 1] * sc + raise) * e
                    if (n == 1) segs += Seg(px(0), py(0), px(0), py(0), r, si)
                    for (k in 0 until n - 1) segs += Seg(px(k), py(k), px(k + 1), py(k + 1), r, si)
                }
                pen += g.adv * sc + TRACK
                sup = false
            }
        }
        return segs to (pen - TRACK + 0.04f)
    }
}
