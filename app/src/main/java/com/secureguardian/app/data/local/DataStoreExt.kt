package com.secureguardian.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Single DataStore instance for the app — declared once here to avoid the
 * "multiple DataStore instances with the same name" crash that occurs when the
 * extension property is defined in more than one file.
 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
