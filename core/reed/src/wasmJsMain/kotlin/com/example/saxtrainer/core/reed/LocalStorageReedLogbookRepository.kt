package com.example.saxtrainer.core.reed

import kotlinx.browser.localStorage
import org.w3c.dom.Storage
import org.w3c.dom.get

/**
 * [ReedLogbookRepository] backed by the browser's `localStorage`, under the same name and in the
 * same format as on Android.
 *
 * [storage] is a parameter only so a test can hand in a store of its own; the app uses the default.
 */
class LocalStorageReedLogbookRepository(
    storage: Storage = localStorage,
) : StringBackedReedLogbookRepository(
    read = { storage[StringBackedReedLogbookRepository.KEY] },
    save = { stored -> storage.setItem(StringBackedReedLogbookRepository.KEY, stored) },
)
