package com.example.saxtrainer.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * One hue at every lightness, from black (tone 0) to white (tone 100).
 *
 * This is how Material 3 builds a colour scheme: a role is never a colour of its own but a tone of a
 * palette - `primary` is tone 40 of the primary palette in light mode and tone 80 in dark - so light
 * and dark stay mirror images and a theme is only as many decisions as it has palettes.
 *
 * Tone is CIE L*, the lightness contrast is computed from, so two colours ten tones apart are the
 * same distance apart whatever their hue. Hue and chroma are OKLCH, which keeps a hue looking like
 * itself as it lightens; CIELAB's own hue drifts blue towards purple. Where a tone cannot carry the
 * requested chroma inside sRGB, chroma gives way and tone and hue are kept.
 */
internal class TonalPalette(
    /** OKLCH hue in degrees. */
    val hue: Double,
    /** OKLCH chroma; about 0.37 is the most sRGB can show at any hue. */
    val chroma: Double,
) {
    private val cache = HashMap<Int, Color>()

    fun tone(tone: Int): Color = cache.getOrPut(tone) { colorAt(hue, chroma, tone.toDouble()) }

    companion object {
        /** The hue and chroma [seed] has, so a palette built from it runs through the seed itself. */
        fun of(seed: Color, minChroma: Double = 0.0): TonalPalette {
            val lab = linearToOklab(linear(seed.red), linear(seed.green), linear(seed.blue))
            val hue = (atan2(lab[2], lab[1]) * 180.0 / PI).mod(360.0)
            return TonalPalette(hue, maxOf(hypot(lab[1], lab[2]), minChroma))
        }
    }
}

private fun colorAt(hue: Double, chroma: Double, tone: Double): Color {
    if (tone <= 0.0) return Color(0f, 0f, 0f)
    if (tone >= 100.0) return Color(1f, 1f, 1f)
    val luminance = luminanceOfTone(tone)

    var rgb = atLuminance(hue, chroma, luminance)
    if (!inGamut(rgb)) {
        var low = 0.0
        var high = chroma
        repeat(GAMUT_STEPS) {
            val mid = (low + high) / 2
            if (inGamut(atLuminance(hue, mid, luminance))) low = mid else high = mid
        }
        rgb = atLuminance(hue, low, luminance)
    }
    return Color(
        red = gamma(rgb[0]).toFloat(),
        green = gamma(rgb[1]).toFloat(),
        blue = gamma(rgb[2]).toFloat(),
    )
}

/** The linear sRGB colour of [hue] and [chroma] whose luminance is [luminance]. */
private fun atLuminance(hue: Double, chroma: Double, luminance: Double): DoubleArray {
    val radians = hue * PI / 180.0
    val a = chroma * cos(radians)
    val b = chroma * sin(radians)
    var low = 0.0
    var high = 1.0
    repeat(LIGHTNESS_STEPS) {
        val mid = (low + high) / 2
        if (luminanceOf(oklabToLinear(mid, a, b)) < luminance) low = mid else high = mid
    }
    return oklabToLinear((low + high) / 2, a, b)
}

private fun luminanceOfTone(tone: Double): Double {
    val f = (tone + 16.0) / 116.0
    return if (tone > 8.0) f * f * f else tone / KAPPA
}

private fun luminanceOf(rgb: DoubleArray): Double = 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]

private fun inGamut(rgb: DoubleArray): Boolean = rgb.all { it in -GAMUT_EPSILON..1.0 + GAMUT_EPSILON }

private fun linear(channel: Float): Double {
    val c = channel.toDouble()
    return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

private fun gamma(channel: Double): Double {
    val c = channel.coerceIn(0.0, 1.0)
    return if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1.0 / 2.4) - 0.055
}

// Björn Ottosson's OKLab matrices, for linear sRGB.

private fun linearToOklab(r: Double, g: Double, b: Double): DoubleArray {
    val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    return doubleArrayOf(
        0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
        1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
        0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
    )
}

private fun oklabToLinear(lightness: Double, a: Double, b: Double): DoubleArray {
    val l = cube(lightness + 0.3963377774 * a + 0.2158037573 * b)
    val m = cube(lightness - 0.1055613458 * a - 0.0638541728 * b)
    val s = cube(lightness - 0.0894841775 * a - 1.2914855480 * b)
    return doubleArrayOf(
        4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
    )
}

private fun cube(x: Double): Double = x * x * x

/** CIE's (29/3)^3: below tone 8, lightness is linear in luminance. */
private const val KAPPA = 24389.0 / 27.0

private const val LIGHTNESS_STEPS = 32
private const val GAMUT_STEPS = 24
private const val GAMUT_EPSILON = 1e-4
