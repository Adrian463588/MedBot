package com.medbot.app.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    val themeMode: Flow<String?> = dataStore.data.map { preferences ->
        preferences[THEME_MODE]
    }

    val activePersonaTone: Flow<String?> = dataStore.data.map { preferences ->
        preferences[ACTIVE_PERSONA_TONE]
    }

    val activePersonaDepth: Flow<String?> = dataStore.data.map { preferences ->
        preferences[ACTIVE_PERSONA_DEPTH]
    }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    suspend fun setActivePersonaTone(tone: String) {
        dataStore.edit { preferences ->
            preferences[ACTIVE_PERSONA_TONE] = tone
        }
    }

    suspend fun setActivePersonaDepth(depth: String) {
        dataStore.edit { preferences ->
            preferences[ACTIVE_PERSONA_DEPTH] = depth
        }
    }

    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACTIVE_PERSONA_TONE = stringPreferencesKey("active_persona_tone")
        val ACTIVE_PERSONA_DEPTH = stringPreferencesKey("active_persona_depth")
    }
}
