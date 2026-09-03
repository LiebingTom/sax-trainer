package com.example.saxtrainer.core.learning

/**
 * Which trainer a lesson step runs in.
 *
 * Deliberately an identifier and never a type reference: this module must stay buildable without a
 * single trainer on the classpath, so a lesson can name a trainer without knowing anything about it.
 */
enum class TrainerId {
    FINGERING,
    LONG_TONES,
    METRONOME,
}

/** Identifies a lesson everywhere: in the catalog, in a navigation route, in stored progress. */
@JvmInline
value class LessonId(val value: String) {
    init {
        require(value.isNotBlank()) { "a lesson needs an id" }
    }
}

/**
 * A trainer configuration a lesson carries but the Learning layer never looks inside.
 *
 * Only the module that can see both a lesson and a trainer's own configuration type - the app's
 * catalog - creates and unwraps these. That is what keeps the trainers free of any lesson
 * vocabulary and this module free of any trainer.
 */
interface TrainerLaunch

/** One exercise inside a lesson: which trainer, configured how. */
data class LessonStep(
    val trainerId: TrainerId,
    val launch: TrainerLaunch,
)

/**
 * One thing a player sits down to do.
 *
 * [steps] is a list from the start so that a lesson made of several exercises later - fingering,
 * then pitch, then rhythm - needs no change to this model. Today every lesson holds exactly one.
 */
data class Lesson(
    val id: LessonId,
    val title: String,
    val subtitle: String,
    val steps: List<LessonStep>,
    /** The lesson that has to be finished first, or null while the catalog is open. */
    val requires: LessonId? = null,
) {
    init {
        require(steps.isNotEmpty()) { "lesson ${id.value} has no exercise" }
        require(requires != id) { "lesson ${id.value} cannot require itself" }
    }

    /** The single step of a one-exercise lesson, which is every lesson so far. */
    val firstStep: LessonStep get() = steps.first()
}

/**
 * The lessons for one trainer, in teaching order - "Learning > Fingering" and its levels.
 *
 * A path carries no icon or colour: how it is drawn is the landing page's business, and this module
 * has no Android on its classpath to describe it with.
 */
data class LessonPath(
    val id: String,
    val title: String,
    val description: String,
    val lessons: List<Lesson>,
) {
    init {
        require(id.isNotBlank()) { "a lesson path needs an id" }
        require(lessons.isNotEmpty()) { "lesson path $id has no lessons" }
    }
}
