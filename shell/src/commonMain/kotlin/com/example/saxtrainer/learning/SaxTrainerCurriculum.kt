package com.example.saxtrainer.learning

import com.example.saxtrainer.core.learning.Curriculum
import com.example.saxtrainer.learning.catalog.units.blues1Unit
import com.example.saxtrainer.learning.catalog.units.blues2Unit
import com.example.saxtrainer.learning.catalog.units.cMajorTwoOctavesUnit
import com.example.saxtrainer.learning.catalog.units.chromaticSharpsAndFlatsUnit
import com.example.saxtrainer.learning.catalog.units.chromaticSharpsUnit
import com.example.saxtrainer.learning.catalog.units.dMajorNotesUnit
import com.example.saxtrainer.learning.catalog.units.impro1Unit

/**
 * Everything the app teaches, as units of a topic.
 *
 * One line per unit, in the order they are meant to be worked through. A unit is a group of notes
 * or a skill, never a trainer: its lessons use whichever trainers the topic needs, and a lesson may
 * use more than one. A new trainer adds a step adapter beside `catalog/steps/NoteQuizSteps.kt` and
 * appears in whichever units want it; a new unit adds a file beside `catalog/units/OctaveUnit.kt`
 * and a line here. Neither touches the other.
 *
 * The four note groups are one ladder: each holds every pitch of the one before it, and the last
 * two are the same pitches written two ways - sharps only, then every black key as a sharp and as a
 * flat. After them the blues - its notes and scale, then melodies on it - built from the scale and
 * pattern content files - and then Impro, songs on the major pentatonic read as numbers. The
 * units this replaced are kept, unbuilt, in `shell/archive/learning`.
 *
 * The [Curriculum] checks itself as it is built, so a mistake in the lesson data shows up the first
 * time the app starts rather than when a player taps a level.
 */
val SaxTrainerCurriculum: Curriculum = Curriculum(
    units = listOf(
        cMajorTwoOctavesUnit,
        dMajorNotesUnit,
        chromaticSharpsUnit,
        chromaticSharpsAndFlatsUnit,
        blues1Unit,
        blues2Unit,
        impro1Unit,
    ),
)
