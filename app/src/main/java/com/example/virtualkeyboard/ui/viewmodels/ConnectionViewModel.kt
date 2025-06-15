package com.example.virtualkeyboard.ui.viewmodels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConnectionState(
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val error: String? = null,
    val ipAddress: String = "",
    val port: String = "",
    val lastScannedQR: String? = null
)

sealed class ConnectionEvent {
    data class UpdateIpAddress(val ip: String) : ConnectionEvent()
    data class UpdatePort(val port: String) : ConnectionEvent()
    data class Connect(val ip: String, val port: String) : ConnectionEvent()
    data class QRCodeScanned(val connectionString: String) : ConnectionEvent()
    object Disconnect : ConnectionEvent()
    object ClearError : ConnectionEvent()
    object ClearLastScannedQR : ConnectionEvent()
}

class ConnectionViewModel : ViewModel() {
    private val _state = MutableStateFlow(ConnectionState())
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    fun onEvent(event: ConnectionEvent) {
        when (event) {
            is ConnectionEvent.UpdateIpAddress -> {
                _state.value = _state.value.copy(
                    ipAddress = event.ip,
                    error = null
                )
            }
            is ConnectionEvent.UpdatePort -> {
                _state.value = _state.value.copy(
                    port = event.port,
                    error = null
                )
            }
            is ConnectionEvent.Connect -> {
                connect(event.ip, event.port)
            }
            is ConnectionEvent.QRCodeScanned -> {
                handleQRCodeScanned(event.connectionString)
            }
            is ConnectionEvent.Disconnect -> {
                disconnect()
            }
            is ConnectionEvent.ClearError -> {
                _state.value = _state.value.copy(error = null)
            }
            is ConnectionEvent.ClearLastScannedQR -> {
                _state.value = _state.value.copy(lastScannedQR = null)
            }
        }
    }

    private fun connect(ip: String, port: String) {
        try {
            validateConnectionInfo(ip, port)
            _state.value = _state.value.copy(
                isConnecting = true,
                error = null
            )
            // TODO: Implement actual connection logic
            _state.value = _state.value.copy(
                isConnecting = false,
                isConnected = true,
                ipAddress = ip,
                port = port
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isConnecting = false,
                error = e.message
            )
        }
    }

    private fun handleQRCodeScanned(connectionString: String) {
        try {
            val (ip, port) = parseConnectionString(connectionString)
            _state.value = _state.value.copy(
                lastScannedQR = connectionString,
                ipAddress = ip,
                port = port.toString()
            )
            connect(ip, port.toString())
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                error = "Invalid QR code format: ${e.message}",
                lastScannedQR = connectionString
            )
        }
    }

    private fun disconnect() {
        // TODO: Implement actual disconnection logic
        _state.value = _state.value.copy(
            isConnected = false,
            error = null
        )
    }

    private fun validateConnectionInfo(ip: String, port: String) {
        if (!ip.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))) {
            throw IllegalArgumentException("Invalid IP address format")
        }
        
        val portNum = port.toIntOrNull()
        if (portNum == null || portNum !in 1..65535) {
            throw IllegalArgumentException("Port must be a number between 1 and 65535")
        }
    }

    private fun parseConnectionString(contents: String): Pair<String, Int> {
        val parts = contents.split(":")
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid connection string format")
        }

        val ip = parts[0]
        val port = parts[1].toIntOrNull() ?: throw IllegalArgumentException("Invalid port number")

        validateConnectionInfo(ip, port.toString())
        return Pair(ip, port)
    }
} 