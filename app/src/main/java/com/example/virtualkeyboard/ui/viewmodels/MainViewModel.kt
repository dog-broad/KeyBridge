package com.example.virtualkeyboard.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.example.virtualkeyboard.ui.viewmodels.events.MainEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MainState(
    val connectionInfo: String? = null,
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

class MainViewModel : ViewModel() {
    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()

    fun onEvent(event: MainEvent) {
        when (event) {
            is MainEvent.SetConnectionInfo -> {
                _state.update { it.copy(connectionInfo = event.info) }
            }
            is MainEvent.Connect -> {
                _state.update { it.copy(isLoading = true, error = null) }
                // TODO: Implement actual connection logic
                _state.update { it.copy(isLoading = false, isConnected = true) }
            }
            is MainEvent.Disconnect -> {
                _state.update { it.copy(isLoading = true, error = null) }
                // TODO: Implement actual disconnection logic
                _state.update { 
                    it.copy(
                        isLoading = false,
                        isConnected = false,
                        connectionInfo = null
                    )
                }
            }
            is MainEvent.ClearError -> {
                _state.update { it.copy(error = null) }
            }
        }
    }
} 