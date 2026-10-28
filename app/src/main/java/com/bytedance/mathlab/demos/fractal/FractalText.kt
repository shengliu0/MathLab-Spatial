// FractalText — the Galton demo's vector stroke font (demos/galton/GaltonText, itself from ExpText), copied under a
// new name for the fractal demo; pictograms dropped, A, D, s, →, ? added. Glyph edges come from the exact distance
// to each stroke, so the decals stay crisp in 3-D and in the headless renders (which skip SpatialUI label chips).
// FractalAtlas packs every string into ONE texture (baked once through BakeCache); each string gets its own quad.
// Markup: '^' raises the next character (superscript), '_' lowers it (subscript).

package com.bytedance.mathlab.demos.fractal

import com.bytedance.mathlab.render.Image
import com.bytedance.mathlab.render.MeshData
import com.bytedance.mathlab.render.OptGeo
import com.bytedance.mathlab.render.ProcTex
import com.bytedance.mathlab.render.Rgb
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal object FractalText {
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

    private val sunRays: List<FloatArray> = (0 until 8).map { k ->
        val a = k * PI.toFloat() / 4f; val c = cos(a); val s = sin(a)
        l(0.40f + 0.25f * c, 0.36f + 0.25f * s, 0.40f + 0.36f * c, 0.36f + 0.36f * s)
    }

    private val glyphs: Map<Char, Glyph> = mapOf(
        // ---- MathText's set
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
        // ---- added for this demo
        'e' to Glyph(0.56f, listOf(cat(l(0.07f, 0.245f, 0.46f, 0.245f), arc(0.265f, 0.245f, 0.195f, 0.245f, 0f, 318f, 22))), italic = true),
        'm' to Glyph(0.82f, listOf(l(0.07f, 0.49f, 0.07f, 0.0f), cat(arc(0.205f, 0.31f, 0.135f, 0.18f, 180f, 0f, 10), l(0.34f, 0.0f)),
            cat(arc(0.475f, 0.31f, 0.135f, 0.18f, 180f, 0f, 10), l(0.61f, 0.0f)))),
        'k' to Glyph(0.50f, listOf(l(0.07f, 0.76f, 0.07f, 0.0f), l(0.42f, 0.49f, 0.07f, 0.18f), l(0.18f, 0.27f, 0.44f, 0.0f))),
        'c' to Glyph(0.50f, listOf(arc(0.27f, 0.245f, 0.205f, 0.245f, 42f, 318f, 20))),
        't' to Glyph(0.42f, listOf(l(0.18f, 0.68f, 0.18f, 0.08f, 0.24f, 0.0f, 0.36f, 0.02f), l(0.04f, 0.47f, 0.36f, 0.47f)), italic = true),
        'l' to Glyph(0.26f, listOf(l(0.12f, 0.76f, 0.12f, 0.0f))),
        'i' to Glyph(0.26f, listOf(l(0.12f, 0.49f, 0.12f, 0.0f), l(0.12f, 0.67f, 0.12f, 0.67f))),
        'o' to Glyph(0.56f, listOf(ring(0.27f, 0.245f, 0.205f, 0.245f))),
        'g' to Glyph(0.58f, listOf(ring(0.26f, 0.285f, 0.195f, 0.205f), cat(l(0.455f, 0.49f, 0.455f, -0.02f), arc(0.27f, -0.02f, 0.185f, 0.19f, 0f, -160f, 12)))),
        'R' to Glyph(0.60f, listOf(cat(l(0.08f, 0.0f, 0.08f, 0.72f, 0.30f, 0.72f), arc(0.30f, 0.54f, 0.19f, 0.18f, 90f, -90f, 12), l(0.08f, 0.36f)), l(0.30f, 0.36f, 0.52f, 0.0f))),
        '∞' to Glyph(0.80f, listOf(ring(0.23f, 0.30f, 0.16f, 0.14f), ring(0.55f, 0.30f, 0.16f, 0.14f))),
        '%' to Glyph(0.66f, listOf(ring(0.15f, 0.58f, 0.09f, 0.12f), ring(0.49f, 0.14f, 0.09f, 0.12f), l(0.06f, 0.0f, 0.58f, 0.72f))),
        '/' to Glyph(0.44f, listOf(l(0.04f, -0.10f, 0.40f, 0.80f))),
        '>' to Glyph(0.62f, listOf(l(0.10f, 0.54f, 0.52f, 0.32f, 0.10f, 0.10f))),
        '≥' to Glyph(0.62f, listOf(l(0.10f, 0.64f, 0.52f, 0.45f, 0.10f, 0.26f), l(0.10f, 0.06f, 0.52f, 0.06f))),
        ',' to Glyph(0.24f, listOf(l(0.12f, 0.05f, 0.06f, -0.12f))),
        '↑' to Glyph(0.60f, listOf(l(0.30f, 0.0f, 0.30f, 0.72f), l(0.10f, 0.50f, 0.30f, 0.72f, 0.50f, 0.50f))),
        // ---- added for the Galton demo
        'p' to Glyph(0.60f, listOf(l(0.07f, 0.49f, 0.07f, -0.24f), ring(0.285f, 0.245f, 0.215f, 0.245f)), italic = true),
        'd' to Glyph(0.60f, listOf(l(0.455f, 0.76f, 0.455f, 0.0f), ring(0.25f, 0.245f, 0.205f, 0.245f)), italic = true),
        'B' to Glyph(0.60f, listOf(cat(l(0.08f, 0.0f, 0.08f, 0.72f, 0.30f, 0.72f), arc(0.30f, 0.54f, 0.17f, 0.18f, 90f, -90f, 12), l(0.08f, 0.36f)),
            cat(l(0.08f, 0.36f, 0.32f, 0.36f), arc(0.32f, 0.18f, 0.20f, 0.18f, 90f, -90f, 12), l(0.08f, 0.0f)))),
        'N' to Glyph(0.64f, listOf(l(0.08f, 0.0f, 0.08f, 0.72f, 0.54f, 0.0f, 0.54f, 0.72f))),
        'K' to Glyph(0.58f, listOf(l(0.08f, 0.0f, 0.08f, 0.72f), l(0.50f, 0.72f, 0.08f, 0.28f), l(0.22f, 0.42f, 0.52f, 0.0f))),
        'S' to Glyph(0.56f, listOf(cat(arc(0.28f, 0.54f, 0.20f, 0.18f, 20f, 270f, 16), arc(0.28f, 0.18f, 0.21f, 0.18f, 90f, -160f, 16)))),
        'T' to Glyph(0.60f, listOf(l(0.04f, 0.72f, 0.56f, 0.72f), l(0.30f, 0.72f, 0.30f, 0.0f))),
        'V' to Glyph(0.60f, listOf(l(0.04f, 0.72f, 0.30f, 0.0f, 0.56f, 0.72f))),
        'σ' to Glyph(0.62f, listOf(ring(0.25f, 0.245f, 0.20f, 0.245f), l(0.25f, 0.49f, 0.58f, 0.49f)), italic = true),
        'μ' to Glyph(0.60f, listOf(l(0.07f, 0.49f, 0.07f, -0.24f), cat(arc(0.26f, 0.21f, 0.19f, 0.21f, 180f, 360f, 14), l(0.45f, 0.49f)), l(0.45f, 0.21f, 0.45f, 0.0f)), italic = true),
        '≈' to Glyph(0.62f, listOf(cat(arc(0.19f, 0.36f, 0.12f, 0.07f, 180f, 0f, 8), arc(0.43f, 0.36f, 0.12f, 0.07f, 180f, 360f, 8)),
            cat(arc(0.19f, 0.18f, 0.12f, 0.07f, 180f, 0f, 8), arc(0.43f, 0.18f, 0.12f, 0.07f, 180f, 360f, 8)))),
        // ---- added for the fractal demo
        'A' to Glyph(0.60f, listOf(l(0.04f, 0.0f, 0.30f, 0.72f, 0.56f, 0.0f), l(0.13f, 0.25f, 0.47f, 0.25f))),
        'D' to Glyph(0.62f, listOf(cat(l(0.08f, 0.0f, 0.08f, 0.72f, 0.24f, 0.72f), arc(0.24f, 0.36f, 0.32f, 0.36f, 90f, -90f, 16), l(0.08f, 0.0f)))),
        's' to Glyph(0.48f, listOf(cat(arc(0.24f, 0.37f, 0.16f, 0.12f, 20f, 270f, 12), arc(0.24f, 0.125f, 0.17f, 0.125f, 90f, -160f, 12))), italic = true),
        '→' to Glyph(0.78f, listOf(l(0.06f, 0.30f, 0.70f, 0.30f), l(0.50f, 0.48f, 0.70f, 0.30f, 0.50f, 0.12f))),
        '?' to Glyph(0.52f, listOf(cat(arc(0.26f, 0.54f, 0.18f, 0.18f, 160f, -70f, 12), l(0.26f, 0.22f)), l(0.26f, 0.03f, 0.26f, 0.03f))),
    )

    private const val SHEAR = 0.16f        // math italic slant for letters
    private const val SUP_SCALE = 0.62f
    private const val SUP_RAISE = 0.40f
    private const val SUB_LOWER = -0.14f
    private const val TRACK = 0.05f
    private const val ASC = 0.90f          // above the baseline
    private const val DESC = 0.24f

    private class Seg(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val r: Float, val col: Int)

    class Size(val w: Int, val h: Int)

    /** Pixel size [bake] will produce (no baking). */
    fun measure(spans: List<Span>, emPx: Int, strokeEm: Float = 0.092f, outline: Boolean = true, outlineEm: Float = 0.06f, padEm: Float = 0.10f): Size {
        val e = emPx.toFloat()
        val wEm = layout(spans, e, strokeEm * e / 2f).second
        val pad = padEm * e + (if (outline) outlineEm * e else 0f)
        return Size((((wEm * e + 2 * pad).toInt() + 3) / 4) * 4, ((ASC + DESC) * e + 2 * pad).toInt() + 1)
    }

    /** Fraction of the image height that is one em. */
    fun emFrac(spans: List<Span>, emPx: Int, outline: Boolean = true): Float = emPx.toFloat() / measure(spans, emPx, outline = outline).h

    /**
     * Bake [spans] at [emPx] pixels per em with stroke width [strokeEm] em, and a dark [outline] of
     * [outlineEm] em around the strokes (null = none). Transparent pixels carry the nearest stroke's
     * colour (straight alpha + bilinear filtering would otherwise draw a dark fringe).
     */
    fun bake(spans: List<Span>, emPx: Int, strokeEm: Float = 0.092f, outline: Rgb? = Rgb(0.02f, 0.025f, 0.05f),
             outlineEm: Float = 0.06f, padEm: Float = 0.10f): Image {
        val e = emPx.toFloat()
        val (segs, _) = layout(spans, e, strokeEm * e / 2f)
        val size = measure(spans, emPx, strokeEm, outline != null, outlineEm, padEm)
        val w = size.w; val h = size.h
        val pad = padEm * e + (if (outline != null) outlineEm * e else 0f)
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
            var mode = 0                                    // 0 normal, 1 superscript, 2 subscript
            var digits = false                              // a raised run of digits ("10^18") continues
            for (ch in span.text) {
                if (ch == '^') { mode = 1; digits = false; continue }
                if (ch == '_') { mode = 2; digits = false; continue }
                if (mode != 0 && digits && !ch.isDigit()) { mode = 0; digits = false }
                val g = glyphs[ch] ?: glyphs[' ']!!
                val sc = if (mode != 0) SUP_SCALE else 1f
                val raise = when (mode) { 1 -> SUP_RAISE; 2 -> SUB_LOWER; else -> 0f }
                val r = if (mode != 0) rPx * 0.82f else rPx
                for (p in g.strokes) {
                    val n = p.size / 2
                    fun px(k: Int): Float { val x = p[k * 2] * sc; val y = p[k * 2 + 1] * sc + raise; return (pen + x + (if (g.italic) SHEAR * y else 0f)) * e }
                    fun py(k: Int): Float = (p[k * 2 + 1] * sc + raise) * e
                    if (n == 1) segs += Seg(px(0), py(0), px(0), py(0), r, si)
                    for (k in 0 until n - 1) segs += Seg(px(k), py(k), px(k + 1), py(k + 1), r, si)
                }
                pen += g.adv * sc + TRACK
                if (mode != 0 && ch.isDigit()) digits = true else mode = 0
            }
        }
        return segs to (pen - TRACK + 0.04f)
    }
}

/**
 * Many strings in ONE texture. [layout] is deterministic and cheap (no baking), so the cell rectangles are
 * recomputed on every build while the atlas pixels come from the bake cache.
 */
internal class FractalAtlas private constructor(val image: Image, private val cells: Map<String, Decal>) {
    class Item(val key: String, val spans: List<FractalText.Span>, val emPx: Int)
    /** A string's quad: unit height, [aspect] = width / height; one em is [emFrac] of the height. */
    class Decal(val mesh: MeshData, val aspect: Float, val emFrac: Float)

    fun decal(key: String): Decal = cells[key] ?: error("no decal '$key'")
    fun has(key: String) = key in cells

    companion object {
        private const val W = 2048
        private const val GUTTER = 4

        private class Rect(val x: Int, val y: Int, val w: Int, val h: Int)

        private fun layout(items: List<Item>): Pair<Int, List<Rect>> {
            var x = GUTTER; var y = GUTTER; var rowH = 0
            val out = ArrayList<Rect>()
            for (it in items) {
                val s = FractalText.measure(it.spans, it.emPx)
                if (x + s.w + GUTTER > W) { x = GUTTER; y += rowH + GUTTER; rowH = 0 }
                out += Rect(x, y, s.w, s.h)
                x += s.w + GUTTER; rowH = max(rowH, s.h)
            }
            return (((y + rowH + GUTTER) + 3) / 4 * 4) to out
        }

        /** Bake every item into one atlas image (call through BakeCache). */
        fun bakeImage(items: List<Item>): Image {
            val (h, rects) = layout(items)
            val img = Image(W, h)
            items.forEachIndexed { i, it ->
                val b = FractalText.bake(it.spans, it.emPx)
                val r = rects[i]
                for (yy in 0 until b.h) System.arraycopy(b.px, yy * b.w, img.px, (r.y + yy) * W + r.x, b.w)
            }
            return img
        }

        /** Wrap a (possibly cached) atlas [image] baked from [items]. */
        fun of(image: Image, items: List<Item>): FractalAtlas {
            val (h, rects) = layout(items)
            require(image.w == W && image.h == h) { "atlas size mismatch: bump the cache version" }
            val m = HashMap<String, Decal>()
            items.forEachIndexed { i, it ->
                val r = rects[i]
                val md = OptGeo.quad(1f, 1f)
                val u0 = r.x.toFloat() / W; val u1 = (r.x + r.w).toFloat() / W
                val v0 = r.y.toFloat() / h; val v1 = (r.y + r.h).toFloat() / h
                OptGeo.setUv(md, 0, u0, v0); OptGeo.setUv(md, 1, u1, v0); OptGeo.setUv(md, 2, u0, v1); OptGeo.setUv(md, 3, u1, v1)
                m[it.key] = Decal(md, r.w.toFloat() / r.h, it.emPx.toFloat() / r.h)
            }
            return FractalAtlas(image, m)
        }
    }
}
