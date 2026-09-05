package com.example.saxtrainer.core.fingering

import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Checks that the chart the app was given is stored faithfully.
 *
 * These tests say nothing about whether a fingering is musically right - that is the chart's
 * business. They only prove the data survived the trip into code intact.
 */
class TenorSaxophoneFingeringsTest {

    private val chart = TenorSaxophoneFingerings

    /** The chart as written, note by note: standard first, then any alternates. */
    private val expected: List<Pair<String, List<String>>> = listOf(
        "Bb3" to listOf("L1 L2 L3 LH-Bb R1 R2 R3 RH-C"),
        "B3" to listOf("L1 L2 L3 LH-B R1 R2 R3 RH-C"),
        "C4" to listOf("L1 L2 L3 R1 R2 R3 RH-C"),
        "C#4" to listOf("L1 L2 L3 LH-C# R1 R2 R3 RH-C"),
        "D4" to listOf("L1 L2 L3 R1 R2 R3"),
        "Eb4" to listOf("L1 L2 L3 R1 R2 R3 RH-Eb"),
        "E4" to listOf("L1 L2 L3 R1 R2"),
        "F4" to listOf("L1 L2 L3 R1"),
        "F#4" to listOf("L1 L2 L3 R2", "L1 L2 L3 R1 Side-F#"),
        "G4" to listOf("L1 L2 L3"),
        "G#4" to listOf("L1 L2 L3 LH-G#"),
        "A4" to listOf("L1 L2"),
        "Bb4" to listOf("L1 Bis", "L1 L2 Side-Bb", "L1 R1"),
        "B4" to listOf("L1"),
        "C5" to listOf("L2"),
        "C#5" to listOf("", "L3"),
        "D5" to listOf("Octave L1 L2 L3 R1 R2 R3"),
        "Eb5" to listOf("Octave L1 L2 L3 R1 R2 R3 RH-Eb"),
        "E5" to listOf("Octave L1 L2 L3 R1 R2"),
        "F5" to listOf("Octave L1 L2 L3 R1"),
        "F#5" to listOf("Octave L1 L2 L3 R2", "Octave L1 L2 L3 R1 Side-F#"),
        "G5" to listOf("Octave L1 L2 L3"),
        "G#5" to listOf("Octave L1 L2 L3 LH-G#"),
        "A5" to listOf("Octave L1 L2"),
        "Bb5" to listOf("Octave L1 Bis", "Octave L1 L2 Side-Bb", "Octave L1 R1"),
        "B5" to listOf("Octave L1"),
        "C6" to listOf("Octave L2"),
        "C#6" to listOf("Octave"),
        "D6" to listOf("Octave Palm-D"),
        "Eb6" to listOf("Octave Palm-D Palm-Eb"),
        "E6" to listOf("Octave Palm-D Palm-Eb Palm-F", "Octave Front-F L2 L3 R1 R2"),
        "F6" to listOf("Octave Palm-D Palm-Eb Palm-F Side-E", "Octave Front-F L2"),
        "F#6" to listOf(
            "Octave Palm-D Palm-Eb Palm-F Side-E High-F#",
            "Octave Front-F L2 R1 Side-Bb",
        ),
    )

    /** Written note names as the chart prints them, e.g. "Bb3" or "C#4". */
    private fun noteOf(chartName: String): Note {
        val octave = chartName.last().digitToInt()
        return when (val letter = chartName.dropLast(1)) {
            "C" -> Note(NoteName.C, octave)
            "C#" -> Note(NoteName.C_SHARP, octave)
            "D" -> Note(NoteName.D, octave)
            "Eb" -> Note(NoteName.D_SHARP, octave)
            "E" -> Note(NoteName.E, octave)
            "F" -> Note(NoteName.F, octave)
            "F#" -> Note(NoteName.F_SHARP, octave)
            "G" -> Note(NoteName.G, octave)
            "G#" -> Note(NoteName.G_SHARP, octave)
            "A" -> Note(NoteName.A, octave)
            "Bb" -> Note(NoteName.A_SHARP, octave)
            "B" -> Note(NoteName.B, octave)
            else -> error("unknown note $letter")
        }
    }

    /**
     * Keys of a fingering as chart labels. A grip is a set of keys, not a sequence, so the
     * comparison ignores the order the chart happens to print them in.
     */
    private fun keysOf(fingering: Fingering): Set<String> =
        fingering.pressedKeys.map { it.label }.toSet()

    /** The same for a line of the chart, e.g. "L1 L2 Side-Bb". */
    private fun keysOf(chartLine: String): Set<String> =
        chartLine.split(" ").filter { it.isNotBlank() }.toSet()

    @Test
    fun `every note of the chart is present`() {
        assertEquals(expected.map { noteOf(it.first) }, chart.supportedNotes)
        assertEquals(33, chart.supportedNotes.size)
    }

    @Test
    fun `every note has at least one fingering`() {
        chart.supportedNotes.forEach { note ->
            val fingerings = chart.fingeringsFor(note)!!
            assertTrue(fingerings.all.isNotEmpty(), note.label)
        }
    }

    @Test
    fun `every standard fingering matches the chart`() {
        expected.forEach { (name, keys) ->
            val note = noteOf(name)
            assertEquals(keysOf(keys.first()), keysOf(chart.fingeringsFor(note)!!.standard), name)
        }
    }

    @Test
    fun `every alternate fingering is kept, in the order the chart lists them`() {
        expected.forEach { (name, keys) ->
            val alternates = chart.fingeringsFor(noteOf(name))!!.alternates
            assertEquals(keys.drop(1).map(::keysOf), alternates.map(::keysOf), name)
        }
    }

    @Test
    fun `the notes with alternates are exactly the ones the chart gives alternates for`() {
        val withAlternates = chart.supportedNotes
            .filter { chart.fingeringsFor(it)!!.hasAlternates }
            .map { it.label }

        assertEquals(
            listOf("F#4", "A#4", "C#5", "F#5", "A#5", "E6", "F6", "F#6"),
            withAlternates,
        )
        assertEquals(3, chart.fingeringsFor(noteOf("Bb4"))!!.all.size)
    }

    @Test
    fun `c sharp 5 is played with everything open`() {
        val fingerings = chart.fingeringsFor(noteOf("C#5"))!!

        assertTrue(
            fingerings.standard.pressedKeys.isEmpty(),
            "the standard fingering presses nothing",
        )
        assertEquals("all open", fingerings.standard.label)
        assertEquals(emptySet<String>(), keysOf(fingerings.standard))
        assertEquals(listOf(SaxKey.LH3), fingerings.alternates.single().pressedKeys.toList())
    }

    @Test
    fun `standard and alternate fingerings are distinguishable`() {
        val bFlat4 = chart.fingeringsFor(noteOf("Bb4"))!!

        assertEquals(FingeringKind.STANDARD, bFlat4.standard.kind)
        assertTrue(bFlat4.alternates.all { it.kind == FingeringKind.ALTERNATE })
        assertEquals(bFlat4.standard, bFlat4.all.first())
        assertEquals(setOf(SaxKey.LH1, SaxKey.BIS), bFlat4.standard.pressedKeys)
    }

    @Test
    fun `looking a note up returns its own fingerings`() {
        val g4 = chart.fingeringsFor(Note(NoteName.G, 4))!!

        assertEquals(Note(NoteName.G, 4), g4.note)
        assertEquals(setOf(SaxKey.LH1, SaxKey.LH2, SaxKey.LH3), g4.standard.pressedKeys)
        assertEquals(g4.standard, chart.fingeringFor(Note(NoteName.G, 4)))
        // The octave above is a different grip.
        assertEquals(
            setOf(SaxKey.OCTAVE, SaxKey.LH1, SaxKey.LH2, SaxKey.LH3),
            chart.fingeringFor(Note(NoteName.G, 5))!!.pressedKeys,
        )
    }

    @Test
    fun `notes outside the chart have nothing`() {
        assertNull(chart.fingeringsFor(Note(NoteName.A, 3)))
        assertNull(chart.fingeringFor(Note(NoteName.G, 6)))
        assertFalse(chart.supports(Note(NoteName.C, 3)))
    }

    @Test
    fun `no fingering uses a key the chart never names`() {
        val used = chart.supportedNotes
            .flatMap { chart.fingeringsFor(it)!!.all }
            .flatMap { it.pressedKeys }
            .toSet()
        val named = expected
            .flatMap { it.second }
            .flatMap { it.split(" ") }
            .filter { it.isNotBlank() }
            .mapNotNull(SaxKey::byLabel)
            .toSet()

        assertEquals(named, used)
        assertTrue(used.all { SaxKey.byLabel(it.label) == it }, "every key label resolves")
    }

    @Test
    fun `the only key no fingering uses is the one the chart never presses`() {
        val used = chart.supportedNotes
            .flatMap { chart.fingeringsFor(it)!!.all }
            .flatMap { it.pressedKeys }
            .toSet()

        // Side C is on the instrument and on the diagram, but this chart has no fingering for it.
        // Anything else unused would be dead vocabulary.
        assertEquals(setOf(SaxKey.SIDE_C), SaxKey.entries.toSet() - used)
    }

    @Test
    fun `supported notes are sorted and free of duplicates`() {
        val notes = chart.supportedNotes

        assertEquals(notes.sortedBy { it.midiNumber }, notes)
        assertEquals(notes.size, notes.distinct().size)
    }

    @Test
    fun `no two notes are fingered the same way`() {
        // A quiz that shows a fingering and asks which note it is only has an answer while this
        // holds. It has always been true of this chart; nothing said so until now.
        val byKeys = chart.supportedNotes
            .flatMap { note -> chart.fingeringsFor(note)!!.all.map { it.pressedKeys to note } }
            .groupBy({ it.first }, { it.second })

        val shared = byKeys.filterValues { it.distinct().size > 1 }

        assertTrue(shared.isEmpty(), "the same grip is listed for " + shared.values.joinToString { notes ->
                notes.distinct().joinToString("/") { it.label }
            })
    }

    @Test
    fun `the shared chart is the tenor data`() {
        assertEquals(TenorSaxophoneFingerings.supportedNotes, SaxophoneFingerings.supportedNotes)
        assertEquals(
            TenorSaxophoneFingerings.fingeringsFor(Note(NoteName.A_SHARP, 4)),
            SaxophoneFingerings.fingeringsFor(Note(NoteName.A_SHARP, 4)),
        )
    }
}
