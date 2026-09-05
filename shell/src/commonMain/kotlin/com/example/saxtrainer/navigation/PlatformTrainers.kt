package com.example.saxtrainer.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.example.saxtrainer.AppContainer
import com.example.saxtrainer.core.learning.TrainerId

/**
 * The trainers a platform can run beyond the ones the shell carries itself.
 *
 * Two things that have to agree, so they are handed over together: which trainers exist, and how
 * their destinations are registered. [ids] decides what the landing page offers and which lessons
 * the catalog is trimmed to; [destinations] is the ordinary navigation graph builder for them.
 *
 * This exists because the microphone trainers are still Android-only. `:app` names them here;
 * `:webApp` names none, so the web gets exactly the destinations it can serve. When M8 makes those
 * trainers multiplatform this shrinks to nothing and the shell registers everything itself.
 */
class PlatformTrainers(
    val ids: Set<TrainerId>,
    val destinations: NavGraphBuilder.(NavHostController, AppContainer) -> Unit = { _, _ -> },
) {
    companion object {
        /** Only what the shell has: no extra destinations, no extra tools on the landing page. */
        val None = PlatformTrainers(ids = emptySet())
    }
}
