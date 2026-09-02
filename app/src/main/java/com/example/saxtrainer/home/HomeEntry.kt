package com.example.saxtrainer.home

import com.example.saxtrainer.R
import com.example.saxtrainer.core.designsystem.component.IconSource
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
    val status: String? = null,
)

val practiceEntries: List<HomeEntry> = listOf(
    HomeEntry(
        id = "metronome",
        title = "Metronome",
        description = "Steady click with accented downbeat and tap tempo.",
        icon = IconSource.Drawable(R.drawable.ic_metronome),
        route = SaxTrainerDestination.METRONOME,
        status = "40-240 BPM",
    ),
    HomeEntry(
        id = "longtones",
        title = "Long Tones",
        description = "Hold a note steadily with live level and spectrum feedback.",
        icon = IconSource.Drawable(R.drawable.ic_long_tone),
        route = SaxTrainerDestination.LONG_TONES,
        status = "Uses microphone",
    ),
    HomeEntry(
        id = "fingering",
        title = "Fingering Trainer",
        description = "Learn which keys to press for each written note.",
        icon = IconSource.Drawable(R.drawable.ic_fingering),
        route = SaxTrainerDestination.FINGERING,
        status = "No microphone needed",
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
