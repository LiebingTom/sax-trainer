package com.example.saxtrainer.learning

import com.example.saxtrainer.core.learning.Curriculum
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.core.settings.ExerciseSetup
import com.example.saxtrainer.core.settings.UserLessonBook
import com.example.saxtrainer.core.settings.exerciseSetup
import com.example.saxtrainer.core.settings.plusGroup
import com.example.saxtrainer.core.settings.plusLesson
import com.example.saxtrainer.feature.notequiz.QuizMode
import com.example.saxtrainer.learning.catalog.steps.noteQuizPresetOf
import com.example.saxtrainer.learning.catalog.steps.noteTrainerPresetOf
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
    fun `a saved practice is started with the spellings it was saved with`() {
        // As the setup screens write it: the pitches, and beside them how each is written.
        val spelled = exerciseSetup {
            put("note_set", "CUSTOM")
            putInts("custom_notes", listOf(60, 63, 68, 70))
            putStrings("custom_written_notes", listOf("C4", "Eb4", "Ab4", "Bb4"))
        }

        val trainer = userUnitsOf(bookWith(TrainerId.NOTE_TRAINER, spelled)).single().lessons.single()
        val quiz = userUnitsOf(bookWith(TrainerId.NOTE_QUIZ, spelled)).single().lessons.single()

        val expected = listOf("C4", "Eb4", "Ab4", "Bb4")
        assertEquals(expected, assertNotNull(noteTrainerPresetOf(trainer.firstStep)).notes.map { it.token })
        assertEquals(expected, assertNotNull(noteQuizPresetOf(quiz.firstStep)).notes.map { it.token })
    }

    @Test
    fun `a practice saved before spellings were kept is spelled as it always was`() {
        val trainer = userUnitsOf(bookWith(TrainerId.NOTE_TRAINER)).single().lessons.single()

        assertEquals(
            listOf("C4", "D4", "E4", "F4"),
            assertNotNull(noteTrainerPresetOf(trainer.firstStep)).notes.map { it.token },
        )
    }

    @Test
    fun `every trainer that can be configured can be saved`() {
        val savable = listOf(
            TrainerId.NOTE_TRAINER to notes,
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
    fun `a practice saved for the retired fingering trainer is passed over`() {
        // Saved by a version that still had the trainer: not offered, and not an error.
        val book = UserLessonBook()
            .plusLesson("Old grips", UserLessonBook.DEFAULT_GROUP.id, "FINGERING", notes)

        assertEquals(emptyList(), userUnitsOf(book))
        assertEquals(null, trainerNameOf("FINGERING"))
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
        val untouched = listOf(TrainerId.NOTE_TRAINER, TrainerId.NOTE_QUIZ)

        untouched.forEach { trainer ->
            val units = userUnitsOf(bookWith(trainer, ExerciseSetup.EMPTY))
            assertEquals(
                trainer,
                units.single().lessons.single().firstStep.trainerId,
                "$trainer saved off its defaults should still run",
            )
        }
    }

    @Test
    fun `an edited lesson step is run again as it was edited`() {
        // What the quiz's setup screen writes when the player switches to naming the note and
        // allows alternate fingerings - the edit that used to be lost after the app was closed.
        val edited = exerciseSetup {
            put("note_set", "CUSTOM")
            putInts("custom_notes", listOf(60, 62, 64))
            put("mode", QuizMode.NOTE_TO_NAME.name)
            put("all_fingerings", true)
        }

        val preset = assertNotNull(noteQuizPresetOf(assertNotNull(lessonStepOf(TrainerId.NOTE_QUIZ, edited))))

        assertEquals(QuizMode.NOTE_TO_NAME, preset.mode)
        assertTrue(preset.allFingerings)
    }
}
