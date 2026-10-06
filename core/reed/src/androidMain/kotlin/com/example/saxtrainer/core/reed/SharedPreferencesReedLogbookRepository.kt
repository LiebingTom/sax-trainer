package com.example.saxtrainer.core.reed

import android.content.Context
import androidx.core.content.edit

/**
 * [ReedLogbookRepository] backed by a [android.content.SharedPreferences] file of its own.
 *
 * Its own file rather than the settings', because this is the player's record and not a preference:
 * it grows with every session, and nothing about it should be in the way of reading the settings.
 * What is written is [StoredReedLogbook], shared with every other platform.
 */
class SharedPreferencesReedLogbookRepository(
    context: Context,
) : StringBackedReedLogbookRepository(
    read = { preferencesOf(context).getString(StringBackedReedLogbookRepository.KEY, null) },
    save = { stored -> preferencesOf(context).edit { putString(StringBackedReedLogbookRepository.KEY, stored) } },
)

private fun preferencesOf(context: Context) =
    context.applicationContext.getSharedPreferences(StringBackedReedLogbookRepository.KEY, Context.MODE_PRIVATE)
