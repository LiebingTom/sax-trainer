package com.example.saxtrainer.learning

import com.example.saxtrainer.core.learning.Curriculum
import com.example.saxtrainer.learning.catalog.units.accidentalsUnit
import com.example.saxtrainer.learning.catalog.units.firstNotesUnit
import com.example.saxtrainer.learning.catalog.units.octaveUnit
import com.example.saxtrainer.learning.catalog.units.upperOctaveUnit

/**
 * Everything the app teaches, as units of a topic.
 *
 * One line per unit, in the order they are meant to be worked through. A unit is a group of notes
 * or a skill, never a trainer: its lessons use whichever trainers the topic needs, and a lesson may
 * use more than one. A new trainer adds a step adapter beside `catalog/steps/FingeringSteps.kt` and
 * appears in whichever units want it; a new unit adds a file beside `catalog/units/OctaveUnit.kt`
 * and a line here. Neither touches the other.
 *
 * The [Curriculum] checks itself as it is built, so a mistake in the lesson data shows up the first
 * time the app starts rather than when a player taps a level.
 */
val SaxTrainerCurriculum: Curriculum = Curriculum(
    units = listOf(
        firstNotesUnit,
        octaveUnit,
        accidentalsUnit,
        upperOctaveUnit,
    ),
)
