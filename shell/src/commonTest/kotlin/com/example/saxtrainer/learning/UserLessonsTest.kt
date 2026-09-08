package com.example.saxtrainer.learning

import com.example.saxtrainer.core.learning.Curriculum
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.core.settings.ExerciseSetup
import com.example.saxtrainer.core.settings.UserLessonBook
import com.example.saxtrainer.core.settings.exerciseSetup
import com.example.saxtrainer.core.settings.plusGroup
import com.example.saxtrainer.core.settings.plusLesson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A practice the player saved, drawn as a lesson like any other.
 *
 * The point of the whole feature in one file: if a saved exercise is a real [Lesson] in a real
 * [LearningUnit] then everything that runs, walks, scores and draws a lesson works on it already.
 */
class UserLessonsTest {

    private val notes = exerciseSetup {
        put("note_set", "CUSTOM")
        putInts("custom_notes", listOf(60, 62, 64, 65))
    }

    private fun bookWith(trainer: TrainerId, setup: ExerciseSetup = notes) = UserLessonBook()
        .plusLesson("Ledger lines", UserLessonBook.DEFAULT_GROUP.id, trainer.name, setup)

    @Test
    fun `a group becomes a unit and a saved practice becomes a lesson of one step`() {
        val units = userUnitsOf(bookWith(TrainerId.NOTE_TRAINER))

        val unit = assertNotNull(units.singleOrNull())
        assertEquals(UserLessonBook.DEFAULT_GROUP.name, unit.title)
        val lesson = assertNotNull(unit.lessons.singleOrNull())
        assertEquals("Ledger lines", lesson.title)
        assertEquals(1, lesson.stepCount)
        assertEquals(TrainerId.NOTE_TRAINER, lesson.firstStep.trainerId)
    }

    @Test
    fun `every trainer that can be configured can be saved`() {
        val savable = listOf(
            TrainerId.NOTE_TRAINER to notes,
            TrainerId.FINGERING to notes,
            TrainerId.NOTE_QUIZ to notes,
            TrainerId.SEQUENCE to exerciseSetup { put("sequence", "stepwise-up") },
            TrainerId.LONG_TONES to exerciseSetup {
                put("pattern", "single-note")
                put("root_note", 69)
            },
        )

        savable.forEach { (trainer, setup) ->
            val units = userUnitsOf(bookWith(trainer, setup))
            assertEquals(
                trainer,
                units.single().lessons.single().firstStep.trainerId,
                "$trainer should be startable from a saved setup",
            )
        }
    }

    @Test
    fun `a trainer this version does not know is passed over rather than failing`() {
        val book = UserLessonBook()
            .plusLesson("Ear training", UserLessonBook.DEFAULT_GROUP.id, "EAR_TRAINING", notes)

        assertEquals(emptyList(), userUnitsOf(book))
    }

    @Test
    fun `a metronome is not a practice worth saving`() {
        assertEquals(emptyList(), userUnitsOf(bookWith(TrainerId.METRONOME)))
    }

    @Test
    fun `a configuration that can no longer be run is passed over`() {
        // A quiz needs enough notes to offer a choice between; one is not a question.
        val tooFew = exerciseSetup {
            put("note_set", "CUSTOM")
            putInts("custom_notes", listOf(60))
        }

        assertEquals(emptyList(), userUnitsOf(bookWith(TrainerId.NOTE_QUIZ, tooFew)))
    }

    @Test
    fun `a group with nothing startable in it is not a unit`() {
        val book = UserLessonBook().plusGroup("Week 2")

        assertEquals(emptyList(), userUnitsOf(book), "an empty group is drawn, but nothing runs it")
    }

    @Test
    fun `the player's lessons sit beside the catalog's without colliding`() {
        val userUnits = userUnitsOf(bookWith(TrainerId.NOTE_TRAINER))

        // The real check: Curriculum refuses duplicate unit and lesson ids when it is built, so
        // this constructing at all is the guarantee that a saved lesson can never shadow a taught
        // one.
        val whole = Curriculum(units = SaxTrainerCurriculum.units + userUnits)

        assertEquals(SaxTrainerCurriculum.units.size + 1, whole.units.size)
        assertTrue(
            whole.lessons.map { it.id.value }.all { it.isNotBlank() },
            "every lesson still has an id",
        )
    }

    @Test
    fun `a saved lesson is found by the id it is started with`() {
        val book = bookWith(TrainerId.NOTE_TRAINER)
        val saved = book.lessons.single()
        val whole = Curriculum(units = SaxTrainerCurriculum.units + userUnitsOf(book))

        val id = lessonIdOf(saved.id)

        assertNotNull(whole.lessonOf(id), "the route resolves it back out of the curriculum")
        assertEquals(saved.id, savedLessonIdOf(id), "and it is known to be the player's own")
    }

    @Test
    fun `a lesson of the catalog is not one of the player's`() {
        val taught = SaxTrainerCurriculum.lessons.first().id

        assertEquals(null, savedLessonIdOf(taught))
    }

    /**
     * A setup nobody has written yet is still a practice.
     *
     * Nothing is on record until a control is changed, so an exercise saved straight off its
     * defaults arrives here as an empty setup - and reading that as "no notes chosen" would save a
     * lesson that then quietly refuses to start.
     */
    @Test
    fun `a practice saved before anything was changed is still startable`() {
        val untouched = listOf(TrainerId.NOTE_TRAINER, TrainerId.FINGERING, TrainerId.NOTE_QUIZ)

        untouched.forEach { trainer ->
            val units = userUnitsOf(bookWith(trainer, ExerciseSetup.EMPTY))
            assertEquals(
                trainer,
                units.single().lessons.single().firstStep.trainerId,
                "$trainer saved off its defaults should still run",
            )
        }
    }

}
