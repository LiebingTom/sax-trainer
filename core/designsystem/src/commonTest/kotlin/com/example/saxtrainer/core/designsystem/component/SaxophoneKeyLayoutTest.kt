package com.example.saxtrainer.core.designsystem.component

import com.example.saxtrainer.core.fingering.SaxKey
import com.example.saxtrainer.core.fingering.TenorSaxophoneFingerings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Structural checks on the diagram layout.
 *
 * Nothing here judges how the drawing looks - that is for the previews. These tests only guarantee
 * that every key the fingering database can ask for has somewhere to be drawn, and that no
 * placement is nonsense.
 */
class SaxophoneKeyLayoutTest {

    private val layout = SaxophoneKeyLayout.keys

    @Test
    fun `every key the app models has a place on the diagram`() {
        assertEquals(SaxKey.entries.toSet(), SaxophoneKeyLayout.drawnKeys)
        assertEquals(23, layout.size)
    }

    @Test
    fun `every key used by the fingering database is drawn`() {
        val used = TenorSaxophoneFingerings.supportedNotes
            .flatMap { TenorSaxophoneFingerings.fingeringsFor(it)!!.all }
            .flatMap { it.pressedKeys }
            .toSet()

        val missing = used - SaxophoneKeyLayout.drawnKeys
        assertEquals(emptySet<SaxKey>(), missing, "keys the diagram would silently swallow")
    }

    @Test
    fun `no key is placed twice`() {
        assertEquals(layout.size, layout.map { it.key }.distinct().size)
    }

    @Test
    fun `every placement sits inside the panel`() {
        layout.forEach { placement ->
            assertTrue(placement.x in 0.05f..0.95f, "${placement.key} x=${placement.x}")
            assertTrue(placement.y in 0.02f..0.98f, "${placement.key} y=${placement.y}")
            assertTrue(placement.size > 0f, "${placement.key} size=${placement.size}")
        }
    }

    @Test
    fun `no two keys are drawn on top of each other`() {
        // Uses the geometry the renderer itself draws with, so this catches real overlaps rather
        // than guessing from the fractions.
        val width = 120f
        val resolved = SaxophoneDiagramMetrics.resolve(
            width = width,
            height = width * SaxophoneDiagramMetrics.HEIGHT_RATIO,
        )

        resolved.forEachIndexed { index, first ->
            resolved.drop(index + 1).forEach { second ->
                assertFalse(
                    overlap(first.geometry, second.geometry),
                    "${first.key} overlaps ${second.key}",
                )
            }
        }
    }

    @Test
    fun `every key stays inside the panel`() {
        val width = 120f
        val height = width * SaxophoneDiagramMetrics.HEIGHT_RATIO

        SaxophoneDiagramMetrics.resolve(width, height).forEach { resolved ->
            val (left, top, right, bottom) = bounds(resolved.geometry)
            assertTrue(left >= 0f, "${resolved.key} left")
            assertTrue(top >= 0f, "${resolved.key} top")
            assertTrue(right <= width, "${resolved.key} right")
            assertTrue(bottom <= height, "${resolved.key} bottom")
        }
    }

    private data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

    private fun bounds(geometry: KeyGeometry): Bounds = when (geometry) {
        is KeyGeometry.Ellipse -> Bounds(
            geometry.centreX - geometry.radius,
            geometry.centreY - geometry.radius,
            geometry.centreX + geometry.radius,
            geometry.centreY + geometry.radius,
        )

        is KeyGeometry.Bar -> Bounds(
            geometry.left,
            geometry.top,
            geometry.left + geometry.width,
            geometry.top + geometry.height,
        )
    }

    private fun overlap(first: KeyGeometry, second: KeyGeometry): Boolean = when {
        first is KeyGeometry.Ellipse && second is KeyGeometry.Ellipse -> {
            val dx = first.centreX - second.centreX
            val dy = first.centreY - second.centreY
            sqrt(dx * dx + dy * dy) < first.radius + second.radius
        }

        first is KeyGeometry.Ellipse && second is KeyGeometry.Bar -> circleTouchesBar(first, second)
        first is KeyGeometry.Bar && second is KeyGeometry.Ellipse -> circleTouchesBar(second, first)

        else -> {
            val a = bounds(first)
            val b = bounds(second)
            a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
        }
    }

    private fun circleTouchesBar(circle: KeyGeometry.Ellipse, bar: KeyGeometry.Bar): Boolean {
        val nearestX = circle.centreX.coerceIn(bar.left, bar.left + bar.width)
        val nearestY = circle.centreY.coerceIn(bar.top, bar.top + bar.height)
        val dx = circle.centreX - nearestX
        val dy = circle.centreY - nearestY
        return sqrt(dx * dx + dy * dy) < circle.radius
    }

    @Test
    fun `the six tone hole keys share the main column`() {
        val column = listOf(SaxKey.LH1, SaxKey.LH2, SaxKey.LH3, SaxKey.RH1, SaxKey.RH2, SaxKey.RH3)

        column.forEach { key ->
            val placement = SaxophoneKeyLayout.placementOf(key)!!
            assertEquals(SaxophoneKeyLayout.MAIN_COLUMN_X, placement.x, 1e-6f, key.name)
            assertEquals(KeyShape.Circle, placement.shape, key.name)
        }
    }

    @Test
    fun `the left hand is drawn above the right hand`() {
        val leftHand = listOf(SaxKey.LH1, SaxKey.LH2, SaxKey.LH3, SaxKey.BIS, SaxKey.FRONT_F)
        val rightHand = listOf(SaxKey.RH1, SaxKey.RH2, SaxKey.RH3)

        leftHand.forEach { key ->
            val y = SaxophoneKeyLayout.placementOf(key)!!.y
            assertTrue(y < SaxophoneKeyLayout.HAND_DIVIDER_Y, key.name)
        }
        rightHand.forEach { key ->
            val y = SaxophoneKeyLayout.placementOf(key)!!.y
            assertTrue(y > SaxophoneKeyLayout.HAND_DIVIDER_Y, key.name)
        }
    }

    @Test
    fun `fingers run downwards in playing order`() {
        val order = listOf(SaxKey.LH1, SaxKey.LH2, SaxKey.LH3, SaxKey.RH1, SaxKey.RH2, SaxKey.RH3)
            .map { SaxophoneKeyLayout.placementOf(it)!!.y }

        assertEquals(order.sorted(), order)
    }

    @Test
    fun `palm keys sit beside the left hand and above the divider`() {
        listOf(SaxKey.PALM_D, SaxKey.PALM_EB, SaxKey.PALM_F).forEach { key ->
            val placement = SaxophoneKeyLayout.placementOf(key)!!
            assertEquals(KeyGroup.PALM, placement.group)
            assertTrue(placement.x > SaxophoneKeyLayout.MAIN_COLUMN_X, key.name)
            assertTrue(placement.y < SaxophoneKeyLayout.HAND_DIVIDER_Y, key.name)
            assertTrue(placement.shape is KeyShape.RoundedRect, "palm keys are bars")
        }
    }

    @Test
    fun `the left little finger cluster is grouped below the left hand`() {
        val cluster = listOf(SaxKey.LH_GS, SaxKey.LH_LOW_CS, SaxKey.LH_LOW_B, SaxKey.LH_LOW_BB)

        cluster.forEach { key ->
            val placement = SaxophoneKeyLayout.placementOf(key)!!
            assertEquals(KeyGroup.LEFT_PINKY, placement.group)
            assertTrue(placement.shape is KeyShape.RoundedRect, "cluster keys are bars")
            assertTrue(placement.x > SaxophoneKeyLayout.MAIN_COLUMN_X, key.name)
        }
        // Below the third finger, above the hand divider.
        val lh3 = SaxophoneKeyLayout.placementOf(SaxKey.LH3)!!
        cluster.forEach { key ->
            val y = SaxophoneKeyLayout.placementOf(key)!!.y
            assertTrue(y > lh3.y - 0.06f && y < SaxophoneKeyLayout.HAND_DIVIDER_Y, key.name)
        }
    }

    @Test
    fun `side keys run down the left of the right hand`() {
        listOf(
            SaxKey.SIDE_E,
            SaxKey.SIDE_C,
            SaxKey.SIDE_BB,
            SaxKey.SIDE_F_SHARP,
            SaxKey.HIGH_F_SHARP,
        ).forEach { key ->
            val placement = SaxophoneKeyLayout.placementOf(key)!!
            assertEquals(KeyGroup.SIDE, placement.group)
            assertTrue(placement.x < SaxophoneKeyLayout.MAIN_COLUMN_X, key.name)
            assertTrue(placement.y > SaxophoneKeyLayout.HAND_DIVIDER_Y, key.name)
            assertTrue(placement.shape is KeyShape.RoundedRect, "side keys are bars")
        }
    }

    @Test
    fun `side keys are distinguishable from the right hand tone holes`() {
        val sideShapes = listOf(SaxKey.SIDE_BB, SaxKey.SIDE_F_SHARP, SaxKey.HIGH_F_SHARP)
            .map { SaxophoneKeyLayout.placementOf(it)!! }
        val toneHoles = listOf(SaxKey.RH1, SaxKey.RH2, SaxKey.RH3)
            .map { SaxophoneKeyLayout.placementOf(it)!! }

        sideShapes.forEach { side ->
            toneHoles.forEach { hole ->
                assertTrue(side.shape != hole.shape, "shape differs")
                assertTrue(abs(side.x - hole.x) > 0.1f, "position differs")
            }
        }
    }

    @Test
    fun `the right little finger keys close the bottom of the diagram`() {
        listOf(SaxKey.RH_C, SaxKey.RH_EB).forEach { key ->
            val placement = SaxophoneKeyLayout.placementOf(key)!!
            assertEquals(KeyGroup.RIGHT_PINKY, placement.group)
            assertTrue(placement.y > SaxophoneKeyLayout.placementOf(SaxKey.RH3)!!.y, key.name)
        }
    }

    @Test
    fun `the octave key stands apart as the thumb key`() {
        val octave = SaxophoneKeyLayout.placementOf(SaxKey.OCTAVE)!!

        assertEquals(KeyGroup.THUMB, octave.group)
        assertTrue(octave.x < SaxophoneKeyLayout.MAIN_COLUMN_X - 0.2f)
        assertTrue(octave.y < SaxophoneKeyLayout.HAND_DIVIDER_Y)
    }

    @Test
    fun `the f sharp keys stand between the side keys and the tone holes`() {
        val highFSharp = SaxophoneKeyLayout.placementOf(SaxKey.HIGH_F_SHARP)!!
        val sideFSharp = SaxophoneKeyLayout.placementOf(SaxKey.SIDE_F_SHARP)!!
        val sideBFlat = SaxophoneKeyLayout.placementOf(SaxKey.SIDE_BB)!!

        assertNotNull(highFSharp)
        // Their own column: right of the side keys, left of the main tone holes.
        listOf(highFSharp, sideFSharp).forEach { placement ->
            assertTrue(placement.x > sideBFlat.x)
            assertTrue(placement.x < SaxophoneKeyLayout.MAIN_COLUMN_X)
            assertTrue(placement.y > SaxophoneKeyLayout.HAND_DIVIDER_Y)
            assertTrue(placement.shape is KeyShape.RoundedRect, "distinct from a tone hole")
        }
        assertEquals(highFSharp.x, sideFSharp.x, 1e-6f, "the two share a column")
        assertTrue(highFSharp.y < sideFSharp.y, "high F sharp sits above F sharp")

        // High F sharp is the long bar: its top lines up with side B flat.
        val width = 120f
        val resolved = SaxophoneDiagramMetrics
            .resolve(width, width * SaxophoneDiagramMetrics.HEIGHT_RATIO)
            .associateBy { it.key }
        val highBar = resolved[SaxKey.HIGH_F_SHARP]!!.geometry as KeyGeometry.Bar
        val bFlatBar = resolved[SaxKey.SIDE_BB]!!.geometry as KeyGeometry.Bar
        assertEquals(bFlatBar.top, highBar.top, 1.0f, "tops line up")
        assertTrue(highBar.height > bFlatBar.height, "and it is the longer bar")
    }

    @Test
    fun `the outer side keys share one column in playing order`() {
        val e = SaxophoneKeyLayout.placementOf(SaxKey.SIDE_E)!!
        val bFlat = SaxophoneKeyLayout.placementOf(SaxKey.SIDE_BB)!!

        val c = SaxophoneKeyLayout.placementOf(SaxKey.SIDE_C)!!

        assertEquals(e.x, bFlat.x, 1e-6f)
        assertEquals(e.x, c.x, 1e-6f)
        assertTrue(e.y < c.y && c.y < bFlat.y, "high E, then side C, then side B flat")
        assertEquals(e.shape, bFlat.shape)
        assertEquals(e.shape, c.shape)
    }

    @Test
    fun `an all open fingering presses nothing the diagram would fill`() {
        val cSharp5 = TenorSaxophoneFingerings.fingeringFor(
            TenorSaxophoneFingerings.supportedNotes.first { it.label == "C#5" },
        )!!

        assertTrue(SaxophoneKeyLayout.keys.none { cSharp5.isPressed(it.key) })
    }

    @Test
    fun `alternate fingerings are drawn through the same layout`() {
        val notesWithAlternates = TenorSaxophoneFingerings.supportedNotes
            .mapNotNull { TenorSaxophoneFingerings.fingeringsFor(it) }
            .filter { it.hasAlternates }

        assertTrue(notesWithAlternates.isNotEmpty())
        notesWithAlternates.flatMap { it.alternates }.forEach { alternate ->
            val missing = alternate.pressedKeys - SaxophoneKeyLayout.drawnKeys
            assertEquals(emptySet<SaxKey>(), missing, alternate.label)
        }
    }
}
