package com.example.saxtrainer.core.designsystem.component

import com.example.saxtrainer.core.fingering.Fingering
import com.example.saxtrainer.core.fingering.FingeringKind
import com.example.saxtrainer.core.fingering.TenorSaxophoneFingerings
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Writes the whole fingering chart to disk as SVG so the diagram can be checked against a printed
 * chart without building or running the app.
 *
 * It is a test only because that is the cheapest way to run JVM code with the module's classpath;
 * it asserts little and exists to produce files. Run it on its own:
 *
 * ```
 * gradlew.bat :core:designsystem:testDebugUnitTest --tests "*FingeringChartExportTest*"
 * ```
 *
 * Output lands in `additional/fingering-chart/` unless `-Dfingering.chart.dir=...` says otherwise:
 * one SVG per fingering plus `index.html`, which lays them all out and prints to PDF from a browser.
 *
 * The geometry comes from [SaxophoneDiagramMetrics], the same source the Compose renderer draws
 * from, so what you see here is what the app draws.
 */
class FingeringChartExportTest {

    private val width = 120f
    private val height = width * SaxophoneDiagramMetrics.HEIGHT_RATIO

    private val pressedColour = "#111111"
    private val openColour = "#9e9e9e"
    private val bodyColour = "#d0d0d0"

    @Test
    fun `export every fingering as svg`() {
        val moduleDir = File(System.getProperty("user.dir") ?: ".")
        val outputDir = File(
            System.getProperty("fingering.chart.dir")
                ?: moduleDir.resolve("../../additional/fingering-chart").path,
        )
        outputDir.mkdirs()
        outputDir.listFiles()?.forEach(File::delete)

        val cards = mutableListOf<Card>()

        TenorSaxophoneFingerings.supportedNotes.forEach { note ->
            val fingerings = TenorSaxophoneFingerings.fingeringsFor(note)!!
            fingerings.all.forEachIndexed { index, fingering ->
                val suffix = if (fingering.kind == FingeringKind.ALTERNATE) "-alt$index" else ""
                val name = "${note.label.replace("#", "sharp")}$suffix.svg"
                File(outputDir, name).writeText(svgOf(fingering))
                cards += Card(
                    title = note.label,
                    subtitle = if (fingering.kind == FingeringKind.ALTERNATE) {
                        "alternate $index"
                    } else {
                        "standard"
                    },
                    keys = fingering.label,
                    file = name,
                )
            }
        }

        File(outputDir, "index.html").writeText(indexHtml(cards))

        assertTrue("nothing was written", cards.size >= TenorSaxophoneFingerings.supportedNotes.size)
        println("Fingering chart written to ${outputDir.canonicalPath} (${cards.size} diagrams)")
    }

    private data class Card(
        val title: String,
        val subtitle: String,
        val keys: String,
        val file: String,
    )

    private fun svgOf(fingering: Fingering): String = buildString {
        append("""<svg xmlns="http://www.w3.org/2000/svg" """)
        append("""width="$width" height="$height" viewBox="0 0 $width $height">""")
        append("\n")

        SaxophoneDiagramMetrics.bodyGuides(width, height).forEach { guide ->
            append(
                """  <line x1="${guide.startX.f()}" y1="${guide.startY.f()}" """ +
                    """x2="${guide.endX.f()}" y2="${guide.endY.f()}" """ +
                    """stroke="$bodyColour" stroke-width="${SaxophoneDiagramMetrics.bodyStrokeWidth(width).f()}"""" +
                    if (guide.dashed) """ stroke-dasharray="${(width * 0.04f).f()}"/>""" else "/>",
            )
            append("\n")
        }

        val stroke = SaxophoneDiagramMetrics.strokeWidth(width)
        SaxophoneDiagramMetrics.resolve(width, height).forEach { resolved ->
            val pressed = fingering.isPressed(resolved.key)
            val fill = if (pressed) pressedColour else "none"
            val outline = if (pressed) pressedColour else openColour
            when (val geometry = resolved.geometry) {
                is KeyGeometry.Ellipse -> append(
                    """  <circle cx="${geometry.centreX.f()}" cy="${geometry.centreY.f()}" """ +
                        """r="${geometry.radius.f()}" fill="$fill" stroke="$outline" """ +
                        """stroke-width="${stroke.f()}"><title>${resolved.key.label}</title></circle>""",
                )

                is KeyGeometry.Bar -> append(
                    """  <rect x="${geometry.left.f()}" y="${geometry.top.f()}" """ +
                        """width="${geometry.width.f()}" height="${geometry.height.f()}" """ +
                        """rx="${geometry.cornerRadius.f()}" fill="$fill" stroke="$outline" """ +
                        """stroke-width="${stroke.f()}"><title>${resolved.key.label}</title></rect>""",
                )
            }
            append("\n")
        }
        append("</svg>\n")
    }

    private fun indexHtml(cards: List<Card>): String = buildString {
        append("<!doctype html>\n<html><head><meta charset=\"utf-8\">\n")
        append("<title>Tenor saxophone fingering chart</title>\n<style>\n")
        append("body{font-family:system-ui,sans-serif;margin:24px;background:#fff;color:#111}\n")
        append("h1{font-size:20px}\n")
        append(".grid{display:flex;flex-wrap:wrap;gap:16px}\n")
        append(".card{border:1px solid #ddd;border-radius:8px;padding:8px;width:150px;")
        append("page-break-inside:avoid;text-align:center}\n")
        append(".card h2{font-size:16px;margin:0}\n")
        append(".card .kind{font-size:11px;color:#666}\n")
        append(".card .keys{font-size:10px;color:#666;margin-top:4px;word-break:break-word}\n")
        append("@media print{.card{width:130px}}\n")
        append("</style></head><body>\n")
        append("<h1>Tenor saxophone fingering chart - ${cards.size} diagrams</h1>\n")
        append("<p>Generated from the app's own layout. Print to PDF from the browser if you want one file.</p>\n")
        append("<div class=\"grid\">\n")
        cards.forEach { card ->
            append("<div class=\"card\">")
            append("<h2>${card.title.escaped()}</h2>")
            append("<div class=\"kind\">${card.subtitle}</div>")
            append("<img src=\"${card.file}\" width=\"110\" alt=\"${card.title.escaped()}\">")
            append("<div class=\"keys\">${card.keys.escaped()}</div>")
            append("</div>\n")
        }
        append("</div>\n</body></html>\n")
    }

    private fun Float.f(): String = String.format(Locale.ROOT, "%.2f", this)

    private fun String.escaped(): String = replace("&", "&amp;").replace("<", "&lt;")
}
