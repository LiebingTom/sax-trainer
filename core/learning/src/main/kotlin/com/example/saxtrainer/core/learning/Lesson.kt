package com.example.saxtrainer.core.learning

/**
 * Which trainer a lesson step runs in.
 *
 * Deliberately an identifier and never a type reference: this module must stay buildable without a
 * single trainer on the classpath, so a lesson can name a trainer without knowing anything about it.
 */
enum class TrainerId {
    NOTE_QUIZ,
    FINGERING,
    NOTE_TRAINER,
    SEQUENCE,
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
    /** Shown while a lesson of several steps is being walked, e.g. "Read them". */
    val title: String = "",
)

/**
 * One thing a player sits down to do, made of one or more exercises.
 *
 * A lesson's [steps] may run different trainers in turn - recognise the notes, then finger them,
 * then play them - and are walked in order. The trainers themselves never learn that a lesson
 * exists: each is handed a configuration and reports a result, and the app's navigation layer is
 * what moves from one step to the next.
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

    val firstStep: LessonStep get() = steps.first()

    val stepCount: Int get() = steps.size

    val isMultiStep: Boolean get() = steps.size > 1

    /** The step at [index], or null past the last one - which is how a lesson says it is over. */
    fun stepAt(index: Int): LessonStep? = steps.getOrNull(index)
}

/**
 * A topic, in teaching order - "Your first five notes" and its lessons.
 *
 * Organised by what is being learnt and never by which trainer teaches it: a unit's lessons may use
 * any trainers the topic needs, and a single lesson may use several. Grouping by trainer instead
 * would teach the ladder sideways, one trainer at a time rather than one group of notes at a time.
 *
 * A unit carries no icon or colour: how it is drawn is the landing page's business, and this module
 * has no Android on its classpath to describe it with.
 *
 * Named `LearningUnit` rather than `Unit`, which is already the name of something else in Kotlin.
 */
data class LearningUnit(
    val id: String,
    val title: String,
    val description: String,
    val lessons: List<Lesson>,
) {
    init {
        require(id.isNotBlank()) { "a learning unit needs an id" }
        require(lessons.isNotEmpty()) { "learning unit $id has no lessons" }
    }
}
