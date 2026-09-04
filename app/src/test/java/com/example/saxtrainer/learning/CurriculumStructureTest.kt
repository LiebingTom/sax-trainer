package com.example.saxtrainer.learning

import com.example.saxtrainer.core.fingering.TenorSaxophoneFingerings
import com.example.saxtrainer.core.learning.LessonProgressState
import com.example.saxtrainer.core.learning.LessonStep
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.learning.catalog.steps.fingeringPresetOf
import com.example.saxtrainer.learning.catalog.steps.noteQuizPresetOf
import com.example.saxtrainer.learning.catalog.steps.noteTrainerPresetOf
import com.example.saxtrainer.learning.catalog.steps.sequencePresetOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shape of the whole curriculum.
 *
 * `Curriculum` already refuses to be built with duplicate ids or a prerequisite pointing the wrong
 * way, so simply touching it here is half the check. The rest is that every lesson can actually be
 * played on the instrument the app models, and that the launch types stay isolated from each other
 * as more trainers are added.
 */
class CurriculumStructureTest {

    private val lessons = SaxTrainerCurriculum.lessons
    private val steps: List<LessonStep> = lessons.flatMap { it.steps }

    /** Every way a step can be read, so a step read by two of them would be caught. */
    private val readers: List<Pair<String, (LessonStep) -> Any?>> = listOf(
        "note quiz" to ::noteQuizPresetOf,
        "fingering" to ::fingeringPresetOf,
        "note trainer" to ::noteTrainerPresetOf,
        "sequence" to ::sequencePresetOf,
    )

    /** The notes a step practises, whichever trainer it belongs to. */
    private fun notesOf(step: LessonStep): List<Note> = listOfNotNull(
        noteQuizPresetOf(step)?.notes,
        fingeringPresetOf(step)?.notes,
        noteTrainerPresetOf(step)?.notes,
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
                "a ${step.trainerId} step was read by ${recognised.joinToString { it.first }}",
                1,
                recognised.size,
            )
        }
    }

    @Test
    fun `a step names the trainer whose configuration it carries`() {
        steps.forEach { step ->
            val preset = when (step.trainerId) {
                com.example.saxtrainer.core.learning.TrainerId.NOTE_QUIZ -> noteQuizPresetOf(step)
                com.example.saxtrainer.core.learning.TrainerId.FINGERING -> fingeringPresetOf(step)
                com.example.saxtrainer.core.learning.TrainerId.NOTE_TRAINER ->
                    noteTrainerPresetOf(step)

                com.example.saxtrainer.core.learning.TrainerId.SEQUENCE -> sequencePresetOf(step)
                else -> null
            }
            assertNotNull("a ${step.trainerId} step carries no ${step.trainerId} preset", preset)
        }
    }

    @Test
    fun `no lesson asks for a note the instrument cannot play`() {
        steps.forEach { step ->
            notesOf(step).forEach { note ->
                assertTrue(
                    "${step.trainerId} asks for ${note.label}, which has no fingering",
                    TenorSaxophoneFingerings.supports(note),
                )
            }
        }
    }

    @Test
    fun `a lesson never practises the same note twice in one selection`() {
        steps.forEach { step ->
            val notes = notesOf(step)
            if (notes.isNotEmpty()) {
                assertEquals("${step.trainerId} repeats a note", notes.distinct().size, notes.size)
            }
        }
    }

    @Test
    fun `the units build on each other`() {
        // A later unit contains everything an earlier one taught, so the ladder cannot develop a
        // gap as levels are added.
        val notesPerUnit = SaxTrainerCurriculum.units.map { unit ->
            unit.lessons.flatMap { it.steps }.flatMap(::notesOf).toSet()
        }

        notesPerUnit.zipWithNext().forEach { (earlier, later) ->
            assertTrue("a unit dropped notes an earlier one taught", later.containsAll(earlier))
        }
    }

    @Test
    fun `the whole catalog is open while it is being written`() {
        val nothingDone = LessonProgressState()

        lessons.forEach { lesson ->
            assertNull("${lesson.title} is gated", lesson.requires)
            assertTrue(SaxTrainerCurriculum.isUnlocked(lesson.id, nothingDone))
        }
    }

    @Test
    fun `a lesson can be found from its id, and so can the unit it belongs to`() {
        lessons.forEach { lesson ->
            assertEquals(lesson, SaxTrainerCurriculum.lessonOf(lesson.id))
            assertNotNull(
                "${lesson.title} belongs to no unit",
                SaxTrainerCurriculum.unitOfLesson(lesson.id),
            )
        }
    }
}
