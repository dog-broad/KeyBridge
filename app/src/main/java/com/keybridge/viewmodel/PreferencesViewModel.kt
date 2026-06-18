/*
 * Copyright 2025 Rushyendra Guntupalli (dog-broad) and Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.keybridge.viewmodel

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val LEGACY_PREFS_NAME = "keybridge_prefs"

// One DataStore for the whole process. On first access it runs a one-time migration of the
// old SharedPreferences file, so existing users keep all their settings.
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "keybridge_settings",
    produceMigrations = { ctx -> listOf(SharedPreferencesMigration(ctx, LEGACY_PREFS_NAME)) },
)

class PreferencesViewModel(context: Context) : ViewModel() {

    private val dataStore = context.applicationContext.settingsDataStore

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DARK_THEME = booleanPreferencesKey("dark_theme")  // legacy boolean, for migration only
        val REPEAT_RATE = floatPreferencesKey("key_repeat_rate")
        val TYPING_DELAY = floatPreferencesKey("typing_delay")
        val HAPTIC = booleanPreferencesKey("haptic_feedback")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val LAST_SERVER_URL = stringPreferencesKey("last_server_url")
        val MAC_MODE = booleanPreferencesKey("mac_mode")
    }

    // Seed the flows from a one-time synchronous snapshot so the very first frame already has
    // the stored values (no theme flash). This first read also triggers the migration above.
    // Writes go through DataStore asynchronously.
    private val initial: Preferences = runBlocking { dataStore.data.first() }

    private val _themeMode = MutableStateFlow(resolveInitialThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _keyRepeatRate = MutableStateFlow(initial[Keys.REPEAT_RATE] ?: 1.0f)
    val keyRepeatRate: StateFlow<Float> = _keyRepeatRate.asStateFlow()

    private val _typingDelay = MutableStateFlow(initial[Keys.TYPING_DELAY] ?: 50f)
    val typingDelay: StateFlow<Float> = _typingDelay.asStateFlow()

    private val _hapticFeedback = MutableStateFlow(initial[Keys.HAPTIC] ?: true)
    val hapticFeedback: StateFlow<Boolean> = _hapticFeedback.asStateFlow()

    private val _autoConnect = MutableStateFlow(initial[Keys.AUTO_CONNECT] ?: false)
    val autoConnect: StateFlow<Boolean> = _autoConnect.asStateFlow()

    private val _lastServerUrl = MutableStateFlow(initial[Keys.LAST_SERVER_URL] ?: "")
    val lastServerUrl: StateFlow<String> = _lastServerUrl.asStateFlow()

    private val _macMode = MutableStateFlow(initial[Keys.MAC_MODE] ?: false)
    val macMode: StateFlow<Boolean> = _macMode.asStateFlow()

    private fun resolveInitialThemeMode(): ThemeMode = when {
        initial[Keys.THEME_MODE] != null -> ThemeMode.fromStorage(initial[Keys.THEME_MODE])
        // Migrate the legacy boolean: existing users keep their look; fresh installs get SYSTEM.
        initial[Keys.DARK_THEME] != null -> if (initial[Keys.DARK_THEME] == true) ThemeMode.DARK else ThemeMode.LIGHT
        else -> ThemeMode.SYSTEM
    }

    fun setThemeMode(mode: ThemeMode) = update(_themeMode, mode) {
        it[Keys.THEME_MODE] = mode.storageValue
        it.remove(Keys.DARK_THEME)
    }

    fun setKeyRepeatRate(rate: Float) = update(_keyRepeatRate, rate) { it[Keys.REPEAT_RATE] = rate }
    fun setTypingDelay(delay: Float) = update(_typingDelay, delay) { it[Keys.TYPING_DELAY] = delay }
    fun setHapticFeedback(enabled: Boolean) = update(_hapticFeedback, enabled) { it[Keys.HAPTIC] = enabled }
    fun setAutoConnect(enabled: Boolean) = update(_autoConnect, enabled) { it[Keys.AUTO_CONNECT] = enabled }
    fun setLastServerUrl(url: String) = update(_lastServerUrl, url) { it[Keys.LAST_SERVER_URL] = url }
    fun setMacMode(enabled: Boolean) = update(_macMode, enabled) { it[Keys.MAC_MODE] = enabled }

    /** Update the in-memory flow immediately and persist the change to DataStore. */
    private fun <T> update(flow: MutableStateFlow<T>, value: T, edit: suspend (MutablePreferences) -> Unit) {
        flow.value = value
        viewModelScope.launch { dataStore.edit { edit(it) } }
    }

    fun clearPreferences() {
        _themeMode.value = ThemeMode.SYSTEM
        _keyRepeatRate.value = 1.0f
        _typingDelay.value = 50f
        _hapticFeedback.value = true
        _autoConnect.value = false
        _lastServerUrl.value = ""
        _macMode.value = false
        viewModelScope.launch { dataStore.edit { it.clear() } }
    }
}

// ViewModelFactory to inject Context
class PreferencesViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PreferencesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PreferencesViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
