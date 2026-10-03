package com.example.saxtrainer.learning

import com.example.saxtrainer.core.fingering.TenorSaxophoneFingerings
import com.example.saxtrainer.core.learning.LessonProgressState
import com.example.saxtrainer.core.learning.LessonStep
import com.example.saxtrainer.core.music.SpelledNote
import com.example.saxtrainer.learning.catalog.steps.longTonePresetOf
import com.example.saxtrainer.learning.catalog.steps.noteQuizPresetOf
import com.example.saxtrainer.learning.catalog.steps.noteTrainerPresetOf
import com.example.saxtrainer.learning.catalog.steps.sequencePresetOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.example.saxtrainer.learning.catalog.units.CHROMATIC_SHARPS_AND_FLATS_UNIT_ID
import com.example.saxtrainer.learning.catalog.units.CHROMATIC_SHARPS_UNIT_ID
import com.example.saxtrainer.learning.catalog.units.C_MAJOR_TWO_OCTAVES_UNIT_ID
import com.example.saxtrainer.learning.catalog.units.D_MAJOR_NOTES_UNIT_ID

/**
 * The shape of the whole curriculum.
 *
 * `Curriculum` already refuses to be built with duplicate ids or a prerequisite pointing the wrong
 * way, so simply touching it here is half the check. The rest is that every lesson can actually be
 * played on the instrument the app models, and that the launch types stay isolated from each other
 * as more trainers are added.
 */
class CurriculumStructureTest {

    /** The units that are rungs of one ladder, in order: each holds every pitch before it. */
    private val LADDER_UNIT_IDS = listOf(
        C_MAJOR_TWO_OCTAVES_UNIT_ID,
        D_MAJOR_NOTES_UNIT_ID,
        CHROMATIC_SHARPS_UNIT_ID,
        CHROMATIC_SHARPS_AND_FLATS_UNIT_ID,
    )

    private val lessons = SaxTrainerCurriculum.lessons
    private val steps: List<LessonStep> = lessons.flatMap { it.steps }

    /** Every way a step can be read, so a step read by two of them would be caught. */
    private val readers: List<Pair<String, (LessonStep) -> Any?>> = listOf(
        "note quiz" to ::noteQuizPresetOf,
        "note trainer" to ::noteTrainerPresetOf,
        "sequence" to ::sequencePresetOf,
        "long tones" to ::longTonePresetOf,
    )

    /** The written notes a step practises, whichever trainer it belongs to. */
    private fun notesOf(step: LessonStep): List<SpelledNote> = listOfNotNull(
        noteQuizPresetOf(step)?.notes,
        noteTrainerPresetOf(step)?.notes,
        longTonePresetOf(step)?.notes,
    ).flatten()

    @Test
    fun `the curriculum is well formed`() {
        // Construction validates; this is the assertion that it was reached at all.
        assertTrue(SaxTrainerCurriculum.units.isNotEmpty())
        assertEquals(lessons.size, lessons.distinct().size)
        assertTrue(steps.isNotEmpty())
    }

    @Test
    fun `every step carries a configuration exactly one trainer recognises`() {
        // This is what keeps the trainers apart as more of them arrive: a launch is opaque to the
        // Learning layer, and only its own adapter can open it.
        steps.forEach { step ->
            val recognised = readers.filter { (_, read) -> read(step) != null }

            assertEquals(
                1,
                recognised.size,
                "a ${step.trainerId} step was read by ${recognised.joinToString { it.first }}",
            )
        }
    }

    @Test
    fun `a step names the trainer whose configuration it carries`() {
        steps.forEach { step ->
            val preset = when (step.trainerId) {
                com.example.saxtrainer.core.learning.TrainerId.NOTE_QUIZ -> noteQuizPresetOf(step)
                com.example.saxtrainer.core.learning.TrainerId.NOTE_TRAINER ->
                    noteTrainerPresetOf(step)

                com.example.saxtrainer.core.learning.TrainerId.SEQUENCE -> sequencePresetOf(step)
                com.example.saxtrainer.core.learning.TrainerId.LONG_TONES -> longTonePresetOf(step)
                else -> null
            }
            assertNotNull(preset, "a ${step.trainerId} step carries no ${step.trainerId} preset")
        }
    }

    @Test
    fun `no lesson asks for a note the instrument cannot play`() {
        steps.forEach { step ->
            notesOf(step).forEach { note ->
                assertTrue(
                    TenorSaxophoneFingerings.supports(note.note),
                    "${step.trainerId} asks for $note, which has no fingering",
                )
            }
        }
    }

    @Test
    fun `a lesson never lists the same written note twice`() {
        // As written: D sharp and E flat in one lesson are two things to read, which is the point
        // of the last group. The same spelling twice would only be a mistake in the data.
        steps.forEach { step ->
            val notes = notesOf(step)
            assertEquals(notes.distinct().size, notes.size, "${step.trainerId} repeats a note")
        }
    }

    @Test
    fun `the units of the ladder build on each other`() {
        // By pitch: a later group plays everything an earlier one did, whatever it is written as.
        val pitchesPerUnit = LADDER_UNIT_IDS.map { id ->
            val unit = assertNotNull(SaxTrainerCurriculum.unitOf(id), "no unit $id")
            unit.lessons.flatMap { it.steps }.flatMap(::notesOf).map { it.note }.toSet()
        }

        pitchesPerUnit.zipWithNext().forEach { (earlier, later) ->
            assertTrue(later.containsAll(earlier), "a unit dropped notes an earlier one taught")
        }
    }

    @Test
    fun `the whole catalog is open while it is being written`() {
        val nothingDone = LessonProgressState()

        lessons.forEach { lesson ->
            assertNull(lesson.requires, "${lesson.title} is gated")
            assertTrue(SaxTrainerCurriculum.isUnlocked(lesson.id, nothingDone))
        }
    }

    @Test
    fun `a lesson can be found from its id, and so can the unit it belongs to`() {
        lessons.forEach { lesson ->
            assertEquals(lesson, SaxTrainerCurriculum.lessonOf(lesson.id))
            assertNotNull(
                SaxTrainerCurriculum.unitOfLesson(lesson.id),
                "${lesson.title} belongs to no unit",
            )
        }
    }
}
