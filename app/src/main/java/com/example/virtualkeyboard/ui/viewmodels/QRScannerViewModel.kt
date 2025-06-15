package com.example.virtualkeyboard.ui.viewmodels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ConnectionInfo(
    val ip: String,
    val port: Int
)

data class QRScannerState(
    val hasCameraPermission: Boolean = false,
    val isScanning: Boolean = false,
    val scannedResult: String? = null
)

sealed class QRScannerEvent {
    data class SetCameraPermission(val granted: Boolean) : QRScannerEvent()
    object StartScanning : QRScannerEvent()
    data class SetScannedResult(val result: String) : QRScannerEvent()
    object ClearScannedResult : QRScannerEvent()
}

class QRScannerViewModel : ViewModel() {
    private val _state = MutableStateFlow(QRScannerState())
    val state: StateFlow<QRScannerState> = _state.asStateFlow()

    fun onEvent(event: QRScannerEvent) {
        when (event) {
            is QRScannerEvent.SetCameraPermission -> {
                _state.update { it.copy(hasCameraPermission = event.granted) }
            }
            is QRScannerEvent.StartScanning -> {
                _state.update { it.copy(isScanning = true) }
            }
            is QRScannerEvent.SetScannedResult -> {
                _state.update { 
                    it.copy(
                        isScanning = false,
                        scannedResult = event.result
                    )
                }
            }
            is QRScannerEvent.ClearScannedResult -> {
                _state.update { it.copy(scannedResult = null) }
            }
        }
    }

    fun onQRCodeScanned(contents: String) {
        onEvent(QRScannerEvent.SetScannedResult(contents))
    }

    fun startScanning() {
        onEvent(QRScannerEvent.StartScanning)
    }

    private fun parseConnectionString(contents: String): Pair<String, Int> {
        val parts = contents.split(":")
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid connection string format")
        }

        val ip = parts[0]
        val port = parts[1].toIntOrNull() ?: throw IllegalArgumentException("Invalid port number")

        // Basic IP validation
        if (!ip.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))) {
            throw IllegalArgumentException("Invalid IP address format")
        }

        // Basic port validation
        if (port !in 1..65535) {
            throw IllegalArgumentException("Port number must be between 1 and 65535")
        }

        return Pair(ip, port)
    }
} 