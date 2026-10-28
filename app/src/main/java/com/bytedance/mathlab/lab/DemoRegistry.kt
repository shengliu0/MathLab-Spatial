package com.bytedance.mathlab.lab

import com.bytedance.mathlab.demos.algebra.AlgebraDemo
import com.bytedance.mathlab.demos.exponential.ExponentialDemo
import com.bytedance.mathlab.demos.linear.LinearDemo

/** Subjects = the catalogue's tabs. */
enum class Subject(private val zh: String, private val en: String) {
    ALGEBRA("代数", "Algebra"), GEOMETRY("几何", "Geometry"), ANALYSIS("分析与概率", "Calculus & Probability");  // i18n-ok (zh/en pairs)
    val label: String get() = tr(zh, en)
}

/**
 * The catalogue. Order inside a subject = order on the cards. Adding a demo = one line here, plus a
 * thumbnail (render it with LabReviewTest, then `python3 tools/make_thumbs.py`).
 * Every math demo turns an idea into something you can hold, move and walk around (3Blue1Brown spirit).
 */
object DemoRegistry {
    val catalog: List<Pair<Demo, Subject>> = listOf(
        AlgebraDemo to Subject.ALGEBRA,       // 代数的几何 · 乘法即面积  Euclid, Elements II
        ExponentialDemo to Subject.ALGEBRA,   // 指数增长 · 棋盘上的米    Sissa's chessboard
        LinearDemo to Subject.ALGEBRA,        // 线性变换与行列式         Cayley 1858
        com.bytedance.mathlab.demos.conics.ConicsDemo to Subject.GEOMETRY,
        com.bytedance.mathlab.demos.sphere.SphereDemo to Subject.GEOMETRY,
        com.bytedance.mathlab.demos.fractal.FractalDemo to Subject.GEOMETRY,
        com.bytedance.mathlab.demos.euler.EulerDemo to Subject.ANALYSIS,
        com.bytedance.mathlab.demos.galton.GaltonDemo to Subject.ANALYSIS,
        com.bytedance.mathlab.demos.collisions.CollisionsDemo to Subject.ANALYSIS,
    )
    val all: List<Demo> = catalog.map { it.first }

    /** The demo the app opens on. */
    const val FIRST = "algebra"

    /** English titles for the catalogue / top bar (each demo's own `title` is the Chinese one). */
    private val TITLE_EN = mapOf<String, String>(
        "fractal" to "Fractals · Menger Sponge",
        "sphere" to "Sphere Area · Archimedes",
        "collisions" to "Colliding Blocks Compute π",
        "galton" to "Galton Board · Central Limit Theorem",
        "euler" to "Euler's Formula · Rotation",
        "conics" to "Conic Sections · Dandelin Spheres",
        "algebra" to "Algebra as Geometry",
        "exponential" to "Exponential Growth",
        "linear" to "Linear Transformations · Determinant",
    )
    fun titleOf(d: Demo): String = tr(d.title, TITLE_EN[d.id] ?: d.title)
    /** Catalogue-card title: one line in a 225 dp card, so English drops its " · subtitle" (Chinese too when > 12 chars). */
    fun cardTitleOf(d: Demo): String = tr(if (d.title.length > 12) d.title.substringBefore(" · ") else d.title,
        (CARD_EN[d.id] ?: TITLE_EN[d.id] ?: d.title).substringBefore(" · "))
    private val CARD_EN = mapOf<String, String>(
        "fractal" to "Fractals",
        "sphere" to "Sphere Area",
        "collisions" to "Blocks Compute π",
        "galton" to "Galton Board",
        "euler" to "Euler's Formula",
        "conics" to "Conic Sections",
        "algebra" to "Algebra as Geometry",
        "exponential" to "Exponential Growth",
        "linear" to "Linear Transformations",
    )
    fun subjectOf(id: String) = catalog.firstOrNull { it.first.id == id }?.second
}
