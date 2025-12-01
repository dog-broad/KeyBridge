package com.keybridge.viewmodel

import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.SecureRandom
import java.util.concurrent.ConcurrentLinkedQueue
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class WebSocketViewModel : ViewModel() {
    
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        AUTHENTICATING,
        AUTHENTICATED,
        ERROR
    }
    
    data class ConnectionData(
        val version: String,
        val url: String,
        val protocol: String,
        val auth: AuthData? = null
    )
    
    data class AuthData(
        val token: String,
        val expires: Long,
        val type: String
    )
    
    data class ServerFeatures(
        val authentication: Boolean = false,
        val encryption: Boolean = false,
        val compression: Boolean = false
    )
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()
    
    private val _lastError = MutableStateFlow("")
    val lastError: StateFlow<String> = _lastError.asStateFlow()
    
    private val _serverFeatures = MutableStateFlow(ServerFeatures())
    val serverFeatures: StateFlow<ServerFeatures> = _serverFeatures.asStateFlow()
    
    private var webSocketClient: WebSocketClient? = null
    private var keepAliveJob: Job? = null
    private var connectionHealthJob: Job? = null
    private var reconnectionJob: Job? = null
    private var lastPongTime: Long = 0L
    
    // Security and protocol features
    private var connectionData: ConnectionData? = null
    private var sessionId: String? = null
    private var protocolVersion: String = "1.0"
    private var isEncryptionEnabled: Boolean = false
    private var encryptionKey: SecretKeySpec? = null
    
    // Message queuing for offline support
    private val messageQueue = ConcurrentLinkedQueue<String>()
    private var maxRetryAttempts = 3
    private var reconnectionAttempts = 0
    private var baseReconnectionDelay = 1000L // 1 second
    
    // Message acknowledgment system
    private val pendingMessages = mutableMapOf<String, PendingMessage>()
    private val acknowledgmentTimeout = 5000L // 5 seconds
    private var acknowledgmentJob: Job? = null
    
    data class PendingMessage(
        val messageId: String,
        val message: String,
        val timestamp: Long,
        val retryCount: Int = 0,
        val maxRetries: Int = 3,
        val requiresAck: Boolean = true
    )
    
    companion object {
        private const val TAG = "WebSocketViewModel"
        private const val KEEP_ALIVE_INTERVAL = 30000L // 30 seconds
        private const val PING_INTERVAL = 15000L // 15 seconds
        private const val CONNECTION_HEALTH_CHECK_INTERVAL = 60000L // 60 seconds
        private const val MAX_PONG_DELAY = 90000L // 90 seconds (3 ping intervals)
    }
    
    fun connectToServer(qrData: String) {
        if (_connectionState.value == ConnectionState.CONNECTING || 
            _connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.AUTHENTICATED) {
            return
        }
        
        viewModelScope.launch {
            try {
                // Close any existing connection first
                webSocketClient?.close()
                webSocketClient = null
                
                // Reset encryption state for fresh connection
                isEncryptionEnabled = false
                encryptionKey = null
                _serverFeatures.value = ServerFeatures()
                reconnectionAttempts = 0
                
                _connectionState.value = ConnectionState.CONNECTING
                _lastError.value = ""
                
                // Parse connection data from QR code
                connectionData = parseConnectionData(qrData)
                val url = connectionData?.url ?: qrData
                _serverUrl.value = url
                
                protocolVersion = connectionData?.version ?: "1.0"
                
                val uri = URI(url)
                webSocketClient = object : WebSocketClient(uri) {
                    override fun onOpen(handshake: ServerHandshake?) {
                        Log.d(TAG, "WebSocket connected")
                        viewModelScope.launch {
                            _connectionState.value = ConnectionState.CONNECTED
                            _lastError.value = ""
                            lastPongTime = System.currentTimeMillis()
                            startKeepAlive()
                            startConnectionHealthCheck()
                        }
                    }
                    
                    override fun onMessage(message: String?) {
                        Log.d(TAG, "Received message: $message")
                        message?.let { 
                            viewModelScope.launch {
                                handleIncomingMessage(it) 
                            }
                        }
                    }
                    
                    override fun onClose(code: Int, reason: String?, remote: Boolean) {
                        Log.d(TAG, "WebSocket closed: $reason")
                        viewModelScope.launch {
                            _connectionState.value = ConnectionState.DISCONNECTED
                            stopKeepAlive()
                            stopConnectionHealthCheck()
                        }
                    }
                    
                    override fun onError(ex: Exception?) {
                        Log.e(TAG, "WebSocket error", ex)
                        viewModelScope.launch {
                            _connectionState.value = ConnectionState.ERROR
                            _lastError.value = ex?.message ?: "Unknown error"
                            stopKeepAlive()
                            stopConnectionHealthCheck()
                            // Attempt auto-reconnection
                            startAutoReconnection()
                        }
                    }
                }
                
                webSocketClient?.connect()
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect", e)
                _connectionState.value = ConnectionState.ERROR
                _lastError.value = e.message ?: "Connection failed"
            }
        }
    }
    
    fun disconnect() {
         viewModelScope.launch {
             stopKeepAlive()
             stopConnectionHealthCheck()
             stopAcknowledgmentMonitoring()
             reconnectionJob?.cancel()
             reconnectionJob = null
             webSocketClient?.close()
             webSocketClient = null
             // Reset encryption state to prevent stale encryption on reconnect
             isEncryptionEnabled = false
             encryptionKey = null
             connectionData = null
             reconnectionAttempts = 0
             _connectionState.value = ConnectionState.DISCONNECTED
             _serverFeatures.value = ServerFeatures()
         }
    }
    
    fun sendText(text: String, typingDelayMs: Int = 0) {
        if (_connectionState.value != ConnectionState.CONNECTED && 
            _connectionState.value != ConnectionState.AUTHENTICATED) {
            Log.w(TAG, "Cannot send text - not connected (state: ${_connectionState.value})")
            return
        }
        
        viewModelScope.launch {
            try {
                val message = JSONObject().apply {
                    put("command", "type")
                    put("text", text)
                    if (typingDelayMs > 0) {
                        put("delay_ms", typingDelayMs)
                    }
                }
                
                 sendMessage(message.toString(), requiresAck = false) // Don't require ack to avoid infinite retries
                 Log.d(TAG, "Sent text: $text (delay: ${typingDelayMs}ms)")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send text", e)
                _lastError.value = "Failed to send text: ${e.message}"
            }
        }
    }
    
    fun sendKeyPress(key: String) {
        if (_connectionState.value != ConnectionState.CONNECTED && 
            _connectionState.value != ConnectionState.AUTHENTICATED) {
            Log.w(TAG, "Cannot send key press - not connected (state: ${_connectionState.value})")
            return
        }
        
        viewModelScope.launch {
            try {
                val message = JSONObject().apply {
                    put("command", "key_press")
                    put("key", key)
                }
                
                 sendMessage(message.toString(), requiresAck = false) // Disabled ack to avoid infinite retries
                 Log.d(TAG, "Sent key press: $key")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send key press", e)
                _lastError.value = "Failed to send key press: ${e.message}"
            }
        }
    }
    
    fun sendKeyRelease(key: String) {
        if (_connectionState.value != ConnectionState.CONNECTED && 
            _connectionState.value != ConnectionState.AUTHENTICATED) {
            Log.w(TAG, "Cannot send key release - not connected (state: ${_connectionState.value})")
            return
        }
        
        viewModelScope.launch {
            try {
                val message = JSONObject().apply {
                    put("command", "key_release")
                    put("key", key)
                }
                
                 sendMessage(message.toString(), requiresAck = false) // Disabled ack to avoid infinite retries
                 Log.d(TAG, "Sent key release: $key")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send key release", e)
                _lastError.value = "Failed to send key release: ${e.message}"
            }
        }
    }
    
    fun sendHotkey(keys: List<String>) {
        if (_connectionState.value != ConnectionState.CONNECTED && 
            _connectionState.value != ConnectionState.AUTHENTICATED) {
            Log.w(TAG, "Cannot send hotkey - not connected (state: ${_connectionState.value})")
            return
        }
        
        viewModelScope.launch {
            try {
                val keysArray = JSONArray(keys)
                val message = JSONObject().apply {
                    put("command", "hotkey")
                    put("keys", keysArray)
                }
                
                 sendMessage(message.toString(), requiresAck = false)
                 Log.d(TAG, "Sent hotkey: ${keys.joinToString("+")}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send hotkey", e)
                _lastError.value = "Failed to send hotkey: ${e.message}"
            }
        }
    }
    
    fun sendKeyCombo(keys: List<String>) {
        if (_connectionState.value != ConnectionState.CONNECTED && 
            _connectionState.value != ConnectionState.AUTHENTICATED) {
            Log.w(TAG, "Cannot send key combo - not connected (state: ${_connectionState.value})")
            return
        }
        
        viewModelScope.launch {
            try {
                val keysArray = JSONArray(keys)
                val message = JSONObject().apply {
                    put("command", "key_combo")
                    put("keys", keysArray)
                }
                
                 sendMessage(message.toString(), requiresAck = false)
                 Log.d(TAG, "Sent key combo: ${keys.joinToString("+")}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send key combo", e)
                _lastError.value = "Failed to send key combo: ${e.message}"
            }
        }
    }
    
    fun sendKeyPressAndRelease(key: String) {
        if (_connectionState.value != ConnectionState.CONNECTED && 
            _connectionState.value != ConnectionState.AUTHENTICATED) {
            Log.w(TAG, "Cannot send key press and release - not connected (state: ${_connectionState.value})")
            return
        }
        
        viewModelScope.launch {
            try {
                // Send key press
                val pressMessage = JSONObject().apply {
                    put("command", "key_press")
                    put("key", key)
                }
                 sendMessage(pressMessage.toString(), requiresAck = true) // Key commands require acknowledgment
                 
                 // Small delay
                 kotlinx.coroutines.delay(50)
                 
                 // Send key release
                 val releaseMessage = JSONObject().apply {
                     put("command", "key_release")
                     put("key", key)
                 }
                 sendMessage(releaseMessage.toString(), requiresAck = true) // Key commands require acknowledgment
                
                Log.d(TAG, "Sent key press and release: $key")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send key press and release", e)
                _lastError.value = "Failed to send key press and release: ${e.message}"
            }
        }
    }
    
    fun sendMouseClick(x: Int, y: Int, button: String = "left") {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            Log.w(TAG, "Cannot send mouse click - not connected")
            return
        }
        
        viewModelScope.launch {
            try {
                val message = JSONObject().apply {
                    put("command", "mouse_click")
                    put("x", x)
                    put("y", y)
                    put("button", button)
                }
                
                webSocketClient?.send(message.toString())
                Log.d(TAG, "Sent mouse click: ($x, $y) $button")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send mouse click", e)
                _lastError.value = "Failed to send mouse click: ${e.message}"
            }
        }
    }
    
    private fun startKeepAlive() {
        stopKeepAlive() // Stop any existing keep-alive job
        
        keepAliveJob = viewModelScope.launch {
            try {
                while (_connectionState.value == ConnectionState.CONNECTED) {
                    delay(PING_INTERVAL)
                    
                    if (_connectionState.value == ConnectionState.CONNECTED && webSocketClient?.isOpen == true) {
                        // Send a ping to keep the connection alive
                        try {
                            webSocketClient?.sendPing()
                            Log.d(TAG, "Sent ping to keep connection alive")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to send ping", e)
                        }
                        
                        // Also send a keep-alive message every 30 seconds
                        if (System.currentTimeMillis() % KEEP_ALIVE_INTERVAL < PING_INTERVAL) {
                            try {
                                val keepAliveMessage = JSONObject().apply {
                                    put("command", "ping")
                                    put("timestamp", System.currentTimeMillis())
                                }
                                sendMessage(keepAliveMessage.toString())
                                Log.d(TAG, "Sent keep-alive message")
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to send keep-alive message", e)
                            }
                        }
                    } else {
                        Log.d(TAG, "Connection not active, stopping keep-alive")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Keep-alive job error", e)
            }
        }
    }
    
    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }
    
    private fun startConnectionHealthCheck() {
        stopConnectionHealthCheck() // Stop any existing health check job
        
        connectionHealthJob = viewModelScope.launch {
            try {
                while (_connectionState.value == ConnectionState.CONNECTED) {
                    delay(CONNECTION_HEALTH_CHECK_INTERVAL)
                    
                    val currentTime = System.currentTimeMillis()
                    val timeSinceLastPong = currentTime - lastPongTime
                    
                    if (timeSinceLastPong > MAX_PONG_DELAY) {
                        Log.w(TAG, "No pong received for ${timeSinceLastPong}ms, connection may be dead")
                        _connectionState.value = ConnectionState.ERROR
                        _lastError.value = "Connection lost - no response from server"
                        stopKeepAlive()
                        break
                    } else {
                        Log.d(TAG, "Connection health check passed - last pong: ${timeSinceLastPong}ms ago")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection health check error", e)
            }
        }
    }
    
    private fun stopConnectionHealthCheck() {
        connectionHealthJob?.cancel()
        connectionHealthJob = null
    }
    
    fun clearError() {
        _lastError.value = ""
    }
    
    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
    
    // Helper functions for enhanced protocol support
    
    private fun parseConnectionData(qrData: String): ConnectionData? {
        return try {
            val jsonObject = JSONObject(qrData)
            val authData = if (jsonObject.has("auth")) {
                val authObj = jsonObject.getJSONObject("auth")
                AuthData(
                    token = authObj.getString("token"),
                    expires = authObj.getLong("expires"),
                    type = authObj.getString("type")
                )
            } else null
            
            ConnectionData(
                version = jsonObject.optString("version", "1.0"),
                url = jsonObject.getString("url"),
                protocol = jsonObject.optString("protocol", "keybridge-v1"),
                auth = authData
            )
        } catch (e: Exception) {
            Log.d(TAG, "QR data is not JSON, treating as legacy format: $qrData")
            // Legacy format - just a WebSocket URL or IP:port
            val url = if (qrData.startsWith("ws://") || qrData.startsWith("wss://")) {
                qrData
            } else {
                "ws://$qrData"
            }
            ConnectionData(
                version = "1.0",
                url = url,
                protocol = "keybridge-v1"
            )
        }
    }
    
    private suspend fun handleIncomingMessage(message: String) {
        try {
            // Check if message is already plain text JSON (starts with { or [)
            val isPlainTextJson = message.startsWith("{") || message.startsWith("[")
            
            // Don't decrypt plain text messages or until encryption is fully set up
            val decryptedMessage = if (isEncryptionEnabled && _serverFeatures.value.encryption && !isPlainTextJson) {
                decryptMessage(message) ?: message
            } else {
                message
            }
            
            val jsonObject = JSONObject(decryptedMessage)
            
            // Handle specific message types first
            when (jsonObject.optString("type")) {
                "handshake" -> handleHandshake(jsonObject)
                "connection_status" -> handleConnectionStatus(jsonObject)
                else -> {
                     // Handle acknowledgment command first
                     val command = jsonObject.optString("command", "")
                     if (command == "ack") {
                         // This is an acknowledgment from server - don't process further
                         val ackMessageId = jsonObject.optString("ack_message_id", "")
                         Log.d(TAG, "Received acknowledgment command for message: $ackMessageId")
                         return
                     }
                     
                     // Check for message acknowledgments (server responses that need our ack)
                     val messageId = jsonObject.optString("message_id", "")
                     val requiresAck = jsonObject.optBoolean("requires_ack", false)
                     
                     if (requiresAck && messageId.isNotEmpty()) {
                         // Send acknowledgment back to server
                         sendAcknowledgment(messageId)
                     }
                     
                     // Handle responses to our messages (remove from pending)
                     if (messageId.isNotEmpty() && pendingMessages.containsKey(messageId)) {
                         Log.d(TAG, "Received response for our message: $messageId")
                         pendingMessages.remove(messageId)
                     }
                     
                     // Handle authentication responses
                     val status = jsonObject.optString("status")
                     when (status) {
                         "success" -> {
                             val successMessage = jsonObject.optString("message", "")
                             when (successMessage) {
                                 "Authentication successful" -> {
                                     Log.d(TAG, "Authentication successful - updating state to AUTHENTICATED")
                                     sessionId = jsonObject.optString("session_id")
                                     _connectionState.value = ConnectionState.AUTHENTICATED
                                     _lastError.value = ""
                                 }
                                 "pong" -> {
                                     Log.d(TAG, "Received pong response")
                                     lastPongTime = System.currentTimeMillis()
                                 }
                                 "Acknowledgment received" -> {
                                     Log.d(TAG, "Server acknowledged our acknowledgment")
                                 }
                                 else -> {
                                     Log.d(TAG, "Received success message: $successMessage")
                                 }
                             }
                         }
                         "error" -> {
                             val errorMessage = jsonObject.optString("message", "Unknown error")
                             val code = jsonObject.optString("code", "")
                             Log.e(TAG, "Received error response: $errorMessage (code: $code)")
                             _connectionState.value = ConnectionState.ERROR
                             _lastError.value = errorMessage
                         }
                         else -> {
                             // Handle regular message responses
                             when (jsonObject.optString("message")) {
                                 "pong" -> {
                                     Log.d(TAG, "Received pong response")
                                     lastPongTime = System.currentTimeMillis()
                                 }
                                 else -> {
                                     Log.d(TAG, "Received message: ${jsonObject.optString("message", "unknown")}")
                                 }
                             }
                         }
                     }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse incoming message", e)
        }
    }
    
    private suspend fun handleHandshake(jsonObject: JSONObject) {
        try {
            protocolVersion = jsonObject.optString("protocol_version", "1.0")
            sessionId = jsonObject.optString("session_id")
            
            if (jsonObject.has("features")) {
                val features = jsonObject.getJSONObject("features")
                _serverFeatures.value = ServerFeatures(
                    authentication = features.optBoolean("authentication", false),
                    encryption = features.optBoolean("encryption", false),
                    compression = features.optBoolean("compression", false)
                )
                
                Log.d(TAG, "Server features: ${_serverFeatures.value}")
                
                // Set up encryption if supported
                if (_serverFeatures.value.encryption) {
                    setupEncryption()
                }
                
                // Authenticate if required
                if (_serverFeatures.value.authentication && connectionData?.auth != null) {
                    authenticateWithServer()
                } else {
                    _connectionState.value = ConnectionState.AUTHENTICATED
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling handshake", e)
        }
    }
    
    private suspend fun authenticateWithServer() {
        try {
            Log.d(TAG, "Starting authentication process")
            _connectionState.value = ConnectionState.AUTHENTICATING
            
            val authToken = connectionData?.auth?.token
            if (authToken != null) {
                val authMessage = JSONObject().apply {
                    put("command", "authenticate")
                    put("token", authToken)
                }
                
                Log.d(TAG, "Sending authentication request with token: ${authToken.substring(0, minOf(20, authToken.length))}...")
                sendMessage(authMessage.toString())
                Log.d(TAG, "Authentication request sent successfully")
            } else {
                Log.e(TAG, "No authentication token available")
                _connectionState.value = ConnectionState.ERROR
                _lastError.value = "No authentication token available"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Authentication request failed", e)
            _connectionState.value = ConnectionState.ERROR
            _lastError.value = "Authentication failed: ${e.message}"
        }
    }
    
    private suspend fun handleConnectionStatus(jsonObject: JSONObject) {
        val status = jsonObject.optString("status")
        when (status) {
            "connected" -> {
                sessionId = jsonObject.optString("session_id")
                Log.d(TAG, "Connection confirmed with session ID: $sessionId")
            }
            "disconnected" -> {
                Log.d(TAG, "Server confirmed disconnection")
            }
        }
    }
    
    private fun setupEncryption() {
        try {
            // Use the exact same key derivation as the server
            // Server uses PBKDF2HMAC with SHA256, salt="keybridge_salt", 100000 iterations
            val secretKey = "keybridge-secret-key-change-in-production"
            val salt = "keybridge_salt"
            
            // Derive key using PBKDF2 (same as server)
            val derivedKey = deriveKey(secretKey, salt, 100000, 32)
            
            // Create AES-256 key from derived key
            encryptionKey = SecretKeySpec(derivedKey, "AES")
            
            isEncryptionEnabled = true
            Log.d(TAG, "AES-GCM encryption enabled")
            Log.d(TAG, "Secret key: $secretKey")
            Log.d(TAG, "Salt: $salt")
            Log.d(TAG, "Derived key (hex): ${derivedKey.joinToString("") { "%02x".format(it) }}")
            Log.d(TAG, "Derived key length: ${derivedKey.size} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup encryption", e)
            isEncryptionEnabled = false
        }
    }
    
    private fun deriveKey(password: String, salt: String, iterations: Int, keyLength: Int): ByteArray {
        // Use Android's built-in PBKDF2 implementation
        // Note: PBKDF2WithHmacSHA256 should match Python's PBKDF2HMAC with SHA256
        val secretKeyFactory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keySpec = javax.crypto.spec.PBEKeySpec(
            password.toCharArray(),
            salt.toByteArray(),
            iterations,
            keyLength * 8 // keyLength in bits
        )
        val secretKey = secretKeyFactory.generateSecret(keySpec)
        val keyBytes = secretKey.encoded
        
        // Log key derivation details for debugging
        Log.d(TAG, "PBKDF2 key derivation:")
        Log.d(TAG, "Password: $password")
        Log.d(TAG, "Salt: $salt")
        Log.d(TAG, "Iterations: $iterations")
        Log.d(TAG, "Key length requested: $keyLength bytes")
        Log.d(TAG, "Key length generated: ${keyBytes.size} bytes")
        Log.d(TAG, "Key (hex): ${keyBytes.joinToString("") { "%02x".format(it) }}")
        
        return keyBytes
    }
    
    private fun encryptMessage(message: String): String? {
        return try {
            if (!isEncryptionEnabled || encryptionKey == null) return message
            
            // Generate random nonce (12 bytes for GCM)
            val nonce = ByteArray(12)
            SecureRandom().nextBytes(nonce)
            
            // Create GCM cipher
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, nonce) // 128-bit tag length
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, gcmSpec)
            
            // Encrypt the message
            val ciphertext = cipher.doFinal(message.toByteArray())
            
            // Combine nonce + ciphertext (tag is appended by doFinal)
            val encryptedData = nonce + ciphertext
            
            // Encode as base64
            val encrypted = Base64.encodeToString(encryptedData, Base64.URL_SAFE or Base64.NO_PADDING)
            Log.d(TAG, "Encrypted message length: ${encrypted.length}")
            Log.d(TAG, "Encrypted message preview: ${encrypted.substring(0, minOf(50, encrypted.length))}")
            encrypted
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            message
        }
    }
    
    private fun decryptMessage(encryptedMessage: String): String? {
        return try {
            if (!isEncryptionEnabled || encryptionKey == null) return encryptedMessage
            
            Log.d(TAG, "Attempting to decrypt message: ${encryptedMessage.substring(0, minOf(50, encryptedMessage.length))}...")
            
            // Decode from base64
            val encryptedData = Base64.decode(encryptedMessage, Base64.URL_SAFE or Base64.NO_PADDING)
            Log.d(TAG, "Decoded encrypted data length: ${encryptedData.size} bytes")
            
            // Extract nonce (first 12 bytes) and ciphertext with tag (rest)
            val nonce = encryptedData.sliceArray(0..11)
            val ciphertextWithTag = encryptedData.sliceArray(12 until encryptedData.size)
            
            // Create GCM cipher
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, nonce) // 128-bit tag length
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, gcmSpec)
            
            // Decrypt the message
            val decrypted = cipher.doFinal(ciphertextWithTag)
            String(decrypted)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            null
        }
    }
    
     private suspend fun sendMessage(message: String, requiresAck: Boolean = false) {
         try {
             val messageId = if (requiresAck) {
                 java.util.UUID.randomUUID().toString()
             } else null
             
             // Add message ID and acknowledgment flag if required
             val messageToSend = if (requiresAck && messageId != null) {
                 val jsonMessage = JSONObject(message)
                 jsonMessage.put("message_id", messageId)
                 jsonMessage.put("requires_ack", true)
                 
                 val messageWithAck = jsonMessage.toString()
                 
                 // Store for retry mechanism
                 val pendingMessage = PendingMessage(
                     messageId = messageId,
                     message = messageWithAck,
                     timestamp = System.currentTimeMillis(),
                     requiresAck = true
                 )
                 pendingMessages[messageId] = pendingMessage
                 
                 // Start acknowledgment monitoring if not already running
                 startAcknowledgmentMonitoring()
                 
                 messageWithAck
             } else {
                 message
             }
             
             // Encrypt if needed
             val finalMessage = if (isEncryptionEnabled && _connectionState.value == ConnectionState.AUTHENTICATED) {
                 encryptMessage(messageToSend) ?: messageToSend
             } else {
                 messageToSend
             }
             
             webSocketClient?.send(finalMessage)
             Log.d(TAG, "Message sent${if (requiresAck) " (requires ack: $messageId)" else ""}")
             
         } catch (e: Exception) {
             Log.e(TAG, "Failed to send message", e)
             throw e
         }
     }
     
     private suspend fun sendAcknowledgment(messageId: String) {
         try {
             val ackMessage = JSONObject().apply {
                 put("command", "ack")
                 put("ack_message_id", messageId)
             }
             
             sendMessage(ackMessage.toString(), requiresAck = false)
             Log.d(TAG, "Sent acknowledgment for message: $messageId")
         } catch (e: Exception) {
             Log.e(TAG, "Failed to send acknowledgment", e)
         }
     }
     
     private fun startAcknowledgmentMonitoring() {
         if (acknowledgmentJob?.isActive == true) return
         
         acknowledgmentJob = viewModelScope.launch {
             while (_connectionState.value == ConnectionState.AUTHENTICATED || 
                    _connectionState.value == ConnectionState.CONNECTED) {
                 delay(1000) // Check every second
                 
                 val currentTime = System.currentTimeMillis()
                 val expiredMessages = pendingMessages.values.filter { 
                     currentTime - it.timestamp > acknowledgmentTimeout 
                 }
                 
                 for (expiredMessage in expiredMessages) {
                     if (expiredMessage.retryCount < expiredMessage.maxRetries) {
                         // Retry the message
                         val retryMessage = expiredMessage.copy(
                             retryCount = expiredMessage.retryCount + 1,
                             timestamp = currentTime
                         )
                         pendingMessages[expiredMessage.messageId] = retryMessage
                         
                         try {
                             val finalMessage = if (isEncryptionEnabled && _connectionState.value == ConnectionState.AUTHENTICATED) {
                                 encryptMessage(expiredMessage.message) ?: expiredMessage.message
                             } else {
                                 expiredMessage.message
                             }
                             
                             webSocketClient?.send(finalMessage)
                             Log.w(TAG, "Retrying message ${expiredMessage.messageId} (attempt ${retryMessage.retryCount})")
                         } catch (e: Exception) {
                             Log.e(TAG, "Failed to retry message ${expiredMessage.messageId}", e)
                         }
                     } else {
                         // Max retries reached, remove from pending
                         pendingMessages.remove(expiredMessage.messageId)
                         Log.e(TAG, "Message ${expiredMessage.messageId} failed after ${expiredMessage.maxRetries} retries")
                         
                         // Optionally notify user of failed message
                         _lastError.value = "Message delivery failed after retries"
                     }
                 }
             }
         }
     }
     
     private fun stopAcknowledgmentMonitoring() {
         acknowledgmentJob?.cancel()
         acknowledgmentJob = null
         pendingMessages.clear()
     }
    
    private suspend fun startAutoReconnection() {
        reconnectionJob?.cancel()
        
        // Don't reconnect if we're already connecting or connected
        if (_connectionState.value == ConnectionState.CONNECTING ||
            _connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.AUTHENTICATED) {
            return
        }
        
        reconnectionJob = viewModelScope.launch {
            while (_connectionState.value == ConnectionState.ERROR && reconnectionAttempts < maxRetryAttempts) {
                reconnectionAttempts++
                val delayTime = baseReconnectionDelay * (1L shl (reconnectionAttempts - 1)) // Exponential backoff
                Log.d(TAG, "Attempting reconnection in ${delayTime}ms (attempt $reconnectionAttempts)")
                
                delay(delayTime)
                
                // Check state again after delay
                if (_connectionState.value != ConnectionState.ERROR) {
                    Log.d(TAG, "Connection state changed, stopping reconnection")
                    break
                }
                
                try {
                    val url = connectionData?.url ?: _serverUrl.value
                    if (url.isNotBlank()) {
                        // Reset state before reconnecting
                        _connectionState.value = ConnectionState.DISCONNECTED
                        connectToServer(url)
                    }
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Reconnection attempt failed", e)
                    _connectionState.value = ConnectionState.ERROR
                }
            }
            
            if (reconnectionAttempts >= maxRetryAttempts) {
                _lastError.value = "Max reconnection attempts reached"
                Log.e(TAG, "Max reconnection attempts reached")
            }
        }
    }
}
