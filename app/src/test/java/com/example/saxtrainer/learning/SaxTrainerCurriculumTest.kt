package com.example.saxtrainer.learning

import com.example.saxtrainer.core.fingering.TenorSaxophoneFingerings
import com.example.saxtrainer.core.learning.LessonProgressState
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.settings.NoteDisplay
import com.example.saxtrainer.feature.fingering.FingeringResult
import com.example.saxtrainer.feature.fingering.PractisedNote
import com.example.saxtrainer.learning.catalog.FINGERING_PATH_ID
import com.example.saxtrainer.learning.catalog.fingeringOutcomeOf
import com.example.saxtrainer.learning.catalog.fingeringPresetOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lesson data itself is checked here.
 *
 * `Curriculum` already refuses to be built with duplicate ids or a prerequisite pointing the wrong
 * way, so simply touching it in a test is half the check; the rest is that every lesson can actually
 * be played on the instrument the app models.
 */
class SaxTrainerCurriculumTest {

    private val path = SaxTrainerCurriculum.pathOf(FINGERING_PATH_ID)!!

    @Test
    fun `the curriculum is well formed`() {
        // Construction validates; this is the assertion that it was reached at all.
        assertTrue(SaxTrainerCurriculum.paths.isNotEmpty())
        assertEquals(SaxTrainerCurriculum.lessons.size, SaxTrainerCurriculum.lessons.distinct().size)
    }

    @Test
    fun `every fingering lesson runs in the fingering trainer with a configuration`() {
        assertEquals(4, path.lessons.size)

        path.lessons.forEach { lesson ->
            val step = lesson.firstStep
            assertEquals(lesson.title, TrainerId.FINGERING, step.trainerId)
            assertNotNull("${lesson.title} carries no preset", fingeringPresetOf(step))
        }
    }

    @Test
    fun `no lesson asks for a note the instrument cannot play`() {
        path.lessons.forEach { lesson ->
            val preset = fingeringPresetOf(lesson.firstStep)!!
            preset.notes.forEach { note ->
                assertTrue(
                    "${lesson.title} asks for ${note.label}, which has no fingering",
                    TenorSaxophoneFingerings.supports(note),
                )
            }
            assertEquals(
                "${lesson.title} repeats a note",
                preset.notes.size,
                preset.notes.distinct().size,
            )
        }
    }

    @Test
    fun `the levels build on each other and speed up`() {
        val presets = path.lessons.map { fingeringPresetOf(it.firstStep)!! }

        presets.zipWithNext { earlier, later ->
            assertTrue(
                "a level must not drop notes the one before it taught",
                later.notes.containsAll(earlier.notes),
            )
            assertTrue(
                "a level must not be slower than the one before",
                later.secondsPerNote <= earlier.secondsPerNote,
            )
        }
    }

    @Test
    fun `the whole catalog is open while it is being written`() {
        path.lessons.forEach { lesson ->
            assertNull("${lesson.title} is gated", lesson.requires)
            assertTrue(SaxTrainerCurriculum.isUnlocked(lesson.id, LessonProgressState()))
        }
    }

    @Test
    fun `a lesson can leave the presentation of each note to chance`() {
        val random = path.lessons
            .mapNotNull { fingeringPresetOf(it.firstStep) }
            .filter { it.noteDisplay == NoteDisplay.RANDOM }

        assertEquals("one level should teach reading both ways", 1, random.size)
        // Nothing about carrying it is special: the preset already took any presentation.
        assertTrue(random.single().notes.size > 1)
    }

    // --- results become progress ---------------------------------------------------------------

    private val c4 = Note(NoteName.C, 4)

    private fun result(practised: Int, planned: Int, checked: Boolean, correct: Int = 0) =
        FingeringResult(
            notes = List(practised) { index -> PractisedNote(c4, playedCorrectly = index < correct) },
            plannedNoteCount = planned,
            checked = checked,
        )

    @Test
    fun `reaching the last note completes a lesson`() {
        val outcome = fingeringOutcomeOf(result(practised = 4, planned = 4, checked = false))

        assertTrue(outcome.completed)
        assertNull("nothing was listened to, so nothing is scored", outcome.score)
    }

    @Test
    fun `stopping early does not complete a lesson`() {
        assertFalse(fingeringOutcomeOf(result(practised = 2, planned = 4, checked = false)).completed)
    }

    @Test
    fun `a listened exercise is scored on the notes it planned`() {
        val outcome = fingeringOutcomeOf(result(practised = 4, planned = 4, checked = true, correct = 3))

        assertEquals(0.75f, outcome.score!!, 1e-6f)
    }
}
