package com.example.saxtrainer.home

import com.example.saxtrainer.R
import com.example.saxtrainer.core.designsystem.component.IconSource
import com.example.saxtrainer.learning.SaxTrainerCurriculum
import com.example.saxtrainer.learning.catalog.FINGERING_PATH_ID
import com.example.saxtrainer.navigation.SaxTrainerDestination

/**
 * A single item on the landing page. The home screen renders whatever is in the lists below, so a
 * future tool (tuner, ear training, ...) is added by appending one entry plus one navigation
 * destination - the home layout itself never changes.
 */
data class HomeEntry(
    val id: String,
    val title: String,
    val description: String,
    val icon: IconSource,
    val route: String,
)

/**
 * Guided lessons, one entry per learning path.
 *
 * Derived from the curriculum rather than written out, so a trainer that gains lessons appears on
 * the landing page without this file or the home screen being edited.
 */
val learningEntries: List<HomeEntry> = SaxTrainerCurriculum.paths.map { path ->
    HomeEntry(
        id = "learning-${path.id}",
        title = path.title,
        description = path.description,
        icon = IconSource.Drawable(iconForPath(path.id)),
        route = SaxTrainerDestination.learningPath(path.id),
    )
}

/** A path borrows its trainer's icon where there is one. */
private fun iconForPath(pathId: String): Int = when (pathId) {
    FINGERING_PATH_ID -> R.drawable.ic_fingering
    else -> R.drawable.ic_learning
}

val practiceEntries: List<HomeEntry> = listOf(
    HomeEntry(
        id = "metronome",
        title = "Metronome",
        description = "Steady click with accented downbeat and tap tempo.",
        icon = IconSource.Drawable(R.drawable.ic_metronome),
        route = SaxTrainerDestination.METRONOME,
    ),
    HomeEntry(
        id = "longtones",
        title = "Long Tones",
        description = "Hold a note steadily with live level and spectrum feedback.",
        icon = IconSource.Drawable(R.drawable.ic_long_tone),
        route = SaxTrainerDestination.LONG_TONES,
    ),
    HomeEntry(
        id = "fingering",
        title = "Fingering Trainer",
        description = "Learn which keys to press for each written note.",
        icon = IconSource.Drawable(R.drawable.ic_fingering),
        route = SaxTrainerDestination.FINGERING,
    ),
)

val appEntries: List<HomeEntry> = listOf(
    HomeEntry(
        id = "settings",
        title = "Settings",
        description = "Instrument and transposition used across all tools.",
        icon = IconSource.Drawable(R.drawable.ic_settings),
        route = SaxTrainerDestination.SETTINGS,
    ),
)
