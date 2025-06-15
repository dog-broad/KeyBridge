package com.example.virtualkeyboard.ui.viewmodels.events

sealed class MainEvent {
    data class SetConnectionInfo(val info: String) : MainEvent()
    object Connect : MainEvent()
    object Disconnect : MainEvent()
    object ClearError : MainEvent()
} 