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
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PreferencesViewModel(private val context: Context) : ViewModel() {
    
    companion object {
        private const val PREFS_NAME = "keybridge_prefs"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_REPEAT_RATE = "key_repeat_rate"
        private const val KEY_TYPING_DELAY = "typing_delay"
        private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
        private const val KEY_AUTO_CONNECT = "auto_connect"
        private const val KEY_LAST_SERVER_URL = "last_server_url"
        private const val KEY_MAC_MODE = "mac_mode"
    }
    
    private val sharedPrefs: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Theme preferences
    private val _isDarkTheme = MutableStateFlow(
        sharedPrefs.getBoolean(KEY_DARK_THEME, false)
    )
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()
    
    // Keyboard preferences
    private val _keyRepeatRate = MutableStateFlow(
        sharedPrefs.getFloat(KEY_REPEAT_RATE, 1.0f)
    )
    val keyRepeatRate: StateFlow<Float> = _keyRepeatRate.asStateFlow()
    
    private val _typingDelay = MutableStateFlow(
        sharedPrefs.getFloat(KEY_TYPING_DELAY, 50f)
    )
    val typingDelay: StateFlow<Float> = _typingDelay.asStateFlow()
    
    private val _hapticFeedback = MutableStateFlow(
        sharedPrefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
    )
    val hapticFeedback: StateFlow<Boolean> = _hapticFeedback.asStateFlow()
    
    // Connection preferences
    private val _autoConnect = MutableStateFlow(
        sharedPrefs.getBoolean(KEY_AUTO_CONNECT, false)
    )
    val autoConnect: StateFlow<Boolean> = _autoConnect.asStateFlow()
    
    private val _lastServerUrl = MutableStateFlow(
        sharedPrefs.getString(KEY_LAST_SERVER_URL, "") ?: ""
    )
    val lastServerUrl: StateFlow<String> = _lastServerUrl.asStateFlow()
    
    // Mac mode - use Mac-specific labels and hotkeys
    private val _macMode = MutableStateFlow(
        sharedPrefs.getBoolean(KEY_MAC_MODE, false)
    )
    val macMode: StateFlow<Boolean> = _macMode.asStateFlow()
    
    fun setDarkTheme(isDark: Boolean) {
        viewModelScope.launch {
            _isDarkTheme.value = isDark
            sharedPrefs.edit().putBoolean(KEY_DARK_THEME, isDark).apply()
        }
    }
    
    fun setKeyRepeatRate(rate: Float) {
        viewModelScope.launch {
            _keyRepeatRate.value = rate
            sharedPrefs.edit().putFloat(KEY_REPEAT_RATE, rate).apply()
        }
    }
    
    fun setTypingDelay(delay: Float) {
        viewModelScope.launch {
            _typingDelay.value = delay
            sharedPrefs.edit().putFloat(KEY_TYPING_DELAY, delay).apply()
        }
    }
    
    fun setHapticFeedback(enabled: Boolean) {
        viewModelScope.launch {
            _hapticFeedback.value = enabled
            sharedPrefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, enabled).apply()
        }
    }
    
    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch {
            _autoConnect.value = enabled
            sharedPrefs.edit().putBoolean(KEY_AUTO_CONNECT, enabled).apply()
        }
    }
    
    fun setLastServerUrl(url: String) {
        viewModelScope.launch {
            _lastServerUrl.value = url
            sharedPrefs.edit().putString(KEY_LAST_SERVER_URL, url).apply()
        }
    }
    
    fun setMacMode(enabled: Boolean) {
        viewModelScope.launch {
            _macMode.value = enabled
            sharedPrefs.edit().putBoolean(KEY_MAC_MODE, enabled).apply()
        }
    }
    
    // Clear all preferences
    fun clearPreferences() {
        viewModelScope.launch {
            sharedPrefs.edit().clear().apply()
            
            // Reset all state flows to defaults
            _isDarkTheme.value = false
            _keyRepeatRate.value = 1.0f
            _typingDelay.value = 50f
            _hapticFeedback.value = true
            _autoConnect.value = false
            _lastServerUrl.value = ""
            _macMode.value = false
        }
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