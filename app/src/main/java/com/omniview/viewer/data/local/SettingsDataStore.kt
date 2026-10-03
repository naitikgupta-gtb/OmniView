package com.omniview.viewer.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "omniview_settings")

enum class ThemeMode { LIGHT, DARK, SYSTEM }

@Singleton
class SettingsDataStore @Inject constructor(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CODE_FONT_SIZE = intPreferencesKey("code_font_size")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val APP_LOCK_PIN = stringPreferencesKey("app_lock_pin_hash")
        val GRID_VIEW = booleanPreferencesKey("grid_view")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
    }

    val codeFontSize: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.CODE_FONT_SIZE] ?: 14
    }

    val gridView: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.GRID_VIEW] ?: false
    }

    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.APP_LOCK_ENABLED] ?: false
    }

    val appLockPinHash: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.APP_LOCK_PIN]
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setCodeFontSize(size: Int) {
        context.dataStore.edit { it[Keys.CODE_FONT_SIZE] = size }
    }

    suspend fun setGridView(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GRID_VIEW] = enabled }
    }

    suspend fun setAppLock(enabled: Boolean, pinHash: String?) {
        context.dataStore.edit {
            it[Keys.APP_LOCK_ENABLED] = enabled
            if (pinHash != null) it[Keys.APP_LOCK_PIN] = pinHash
        }
    }
}
