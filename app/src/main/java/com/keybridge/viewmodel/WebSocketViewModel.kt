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

import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keybridge.protocol.CHUNK_MAX_CODE_POINTS
import com.keybridge.protocol.DeliveryState
import com.keybridge.protocol.PROTOCOL_VERSION
import com.keybridge.protocol.chunkText
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class WebSocketViewModel : ViewModel() {
    
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        AUTHENTICATED,
        ERROR
    }
    
    data class ConnectionData(
        val version: String,
        val url: String,
        val protocol: String,
        // Base64 pairing secret carried in the QR — the root of trust for the session key.
        val pairingSecret: String? = null
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
    // The full QR payload (URL + auth token) of the last real pairing, kept so a reconnect
    // can re-authenticate. Reconnecting with only the bare URL would drop the token and put
    // the app in a false-authenticated state where it encrypts messages the host can't read.
    private var lastQrData: String? = null
    private var sessionId: String? = null
    private var protocolVersion: String = "1.0"
    private var isEncryptionEnabled: Boolean = false
    private var encryptionKey: SecretKeySpec? = null
    
    // Reconnection backoff
    private var maxRetryAttempts = 3
    private var reconnectionAttempts = 0
    private var baseReconnectionDelay = 1000L // 1 second

    // Confirmed-delivery state for the text field (the load-bearing case). Keys ride the
    // same enveloped transport but surface only failures, via lastError.
    private val _textDelivery = MutableStateFlow<DeliveryState>(DeliveryState.Idle)
    val textDelivery: StateFlow<DeliveryState> = _textDelivery.asStateFlow()

    // In-flight messages keyed by envelope id. Each tracks per-chunk ack state so retry
    // resends only what is unconfirmed, reusing the same id/seq (the host de-dupes, so a
    // resend never types twice).
    private val pending = ConcurrentHashMap<String, Outbound>()
    private var deliveryMonitorJob: Job? = null
    // The most recent failed text message, kept so the user can retry it.
    private var lastFailedText: Outbound? = null

    /** One outbound message and the windowed-delivery state the monitor needs. */
    private class Outbound(
        val id: String,
        val envelopes: List<String>,
        val isText: Boolean
    ) {
        val total: Int = envelopes.size
        val acked: BooleanArray = BooleanArray(total)
        // Chunks [0, sentCount) have been sent at least once; this is the window frontier.
        var sentCount: Int = 0
        // Last time an ack advanced delivery — drives stall detection (not per-chunk age,
        // because a chunk's ack latency grows with queue position and the host's typing delay).
        var lastProgressAt: Long = 0L
        var stallRetries: Int = 0
        var failed: Boolean = false
        val ackedCount: Int get() = acked.count { it }
        val allAcked: Boolean get() = acked.all { it }
        // Sent-but-unacked chunks currently occupying the window.
        val inFlight: Int get() = sentCount - ackedCount
    }

    companion object {
        private const val TAG = "WebSocketViewModel"
        private const val KEEP_ALIVE_INTERVAL = 30000L // 30 seconds
        private const val PING_INTERVAL = 15000L // 15 seconds
        private const val CONNECTION_HEALTH_CHECK_INTERVAL = 60000L // 60 seconds
        private const val MAX_PONG_DELAY = 90000L // 90 seconds (3 ping intervals)

        // Delivery is flow-controlled and progress-driven. At most SEND_WINDOW chunks are
        // in flight (unacked) at once, so the host is never flooded and sending paces to the
        // host's apply rate. Failure is declared only on a *stall* — no ack progress for
        // STALL_TIMEOUT — never on a per-chunk timer, since a chunk deep in a slow (delayed)
        // paste can legitimately take many seconds to be acked. The monitor exits when
        // nothing is pending, so it can never spin unbounded.
        private const val SEND_WINDOW = 8
        private const val DELIVERY_TICK_MS = 1000L
        private const val STALL_TIMEOUT_MS = 10000L
        private const val MAX_STALL_RETRIES = 3
        // Target per-chunk apply time; chunk size shrinks as the per-character delay grows,
        // so one chunk always types quickly and acks arrive well within the stall window.
        private const val CHUNK_BUDGET_MS = 1000
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
                // Remember the full payload (with the pairing secret) so reconnects can re-key.
                if (connectionData?.pairingSecret != null) lastQrData = qrData
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
                            stopDeliveryMonitor()
                            failAllPending("Connection lost before delivery was confirmed")
                        }
                    }
                    
                    override fun onError(ex: Exception?) {
                        Log.e(TAG, "WebSocket error", ex)
                        viewModelScope.launch {
                            _connectionState.value = ConnectionState.ERROR
                            _lastError.value = ex?.message ?: "Unknown error"
                            stopKeepAlive()
                            stopConnectionHealthCheck()
                            stopDeliveryMonitor()
                            failAllPending("Connection lost before delivery was confirmed")
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

    /**
     * Reconnect to the last server, reusing the stored pairing token so the session
     * re-authenticates. Falls back to the bare URL only if we never had a token.
     */
    fun reconnect() {
        val target = lastQrData ?: _serverUrl.value
        if (target.isNotBlank()) connectToServer(target)
    }

    fun disconnect() {
         viewModelScope.launch {
             stopKeepAlive()
             stopConnectionHealthCheck()
             stopDeliveryMonitor()
             failAllPending("Disconnected before delivery was confirmed")
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
        if (!isReady()) {
            Log.w(TAG, "Cannot send text - not connected (state: ${_connectionState.value})")
            return
        }

        val chunks = chunkText(text, maxCodePointsForDelay(typingDelayMs))
        if (chunks.isEmpty()) return

        val id = UUID.randomUUID().toString()
        val total = chunks.size
        val envelopes = chunks.mapIndexed { seq, chunk ->
            val payload = JSONObject().apply {
                put("text", chunk)
                if (typingDelayMs > 0) put("delay_ms", typingDelayMs)
            }
            buildEnvelope(id, seq, total, "type", payload)
        }
        val outbound = Outbound(id, envelopes, isText = true)
        outbound.lastProgressAt = System.currentTimeMillis()
        pending[id] = outbound
        lastFailedText = null
        _textDelivery.value = DeliveryState.Sending(id, 0, total)

        viewModelScope.launch {
            sendWindow(outbound)
            Log.d(TAG, "Sending text id=$id in $total chunk(s), window=$SEND_WINDOW (delay ${typingDelayMs}ms)")
            startDeliveryMonitor()
        }
    }

    /** Shrink the chunk size as the per-character delay grows, so one chunk types quickly. */
    private fun maxCodePointsForDelay(delayMs: Int): Int =
        if (delayMs <= 0) CHUNK_MAX_CODE_POINTS
        else (CHUNK_BUDGET_MS / delayMs).coerceIn(10, CHUNK_MAX_CODE_POINTS)

    /** Send chunks until the window is full (in-flight == SEND_WINDOW) or all are sent. */
    private fun sendWindow(outbound: Outbound) {
        while (outbound.sentCount < outbound.total && outbound.inFlight < SEND_WINDOW) {
            sendRaw(outbound.envelopes[outbound.sentCount])
            outbound.sentCount++
        }
    }
    
    fun sendKeyPress(key: String) =
        sendKeyInput("key_press", JSONObject().put("key", key), "key press: $key")

    fun sendKeyRelease(key: String) =
        sendKeyInput("key_release", JSONObject().put("key", key), "key release: $key")

    fun sendKeyCombo(keys: List<String>) =
        sendKeyInput("key_combo", JSONObject().put("keys", JSONArray(keys)), "key combo: ${keys.joinToString("+")}")

    /** A key tap is a press followed by a release, sent as one one-key combo so it is a single acked message. */
    fun sendKeyPressAndRelease(key: String) =
        sendKeyInput("key_combo", JSONObject().put("keys", JSONArray(listOf(key))), "key tap: $key")

    private fun sendKeyInput(type: String, payload: JSONObject, description: String) {
        if (!isReady()) {
            Log.w(TAG, "Cannot send $description - not connected (state: ${_connectionState.value})")
            return
        }
        val id = UUID.randomUUID().toString()
        val envelope = buildEnvelope(id, 0, 1, type, payload)
        val outbound = Outbound(id, listOf(envelope), isText = false)
        outbound.lastProgressAt = System.currentTimeMillis()
        pending[id] = outbound

        viewModelScope.launch {
            sendWindow(outbound)
            Log.d(TAG, "Sent $description (id=$id)")
            startDeliveryMonitor()
        }
    }

    private fun isReady(): Boolean =
        _connectionState.value == ConnectionState.CONNECTED ||
        _connectionState.value == ConnectionState.AUTHENTICATED
    
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
                                sendRaw(keepAliveMessage.toString())
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
            ConnectionData(
                version = jsonObject.optString("version", "1.0"),
                url = jsonObject.getString("url"),
                protocol = jsonObject.optString("protocol", "keybridge-v1"),
                pairingSecret = if (jsonObject.has("key")) jsonObject.getString("key") else null
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
                "ack" -> handleAck(jsonObject)
                else -> {
                     // Control-plane responses (pong, errors) are flat status objects.
                     val status = jsonObject.optString("status")
                     when (status) {
                         "success" -> {
                             if (jsonObject.optString("message") == "pong") {
                                 lastPongTime = System.currentTimeMillis()
                             }
                         }
                         "error" -> {
                             val errorMessage = jsonObject.optString("message", "Unknown error")
                             val code = jsonObject.optString("code", "")
                             if (code == "RATE_LIMIT_EXCEEDED") {
                                 // Transient: the rate-limited chunk was not acked, so the delivery
                                 // monitor will resend it once the window clears. Don't tear down the
                                 // connection — a burst of chunks must not look like a fatal error.
                                 Log.w(TAG, "Rate limited; unacked chunks will be retried")
                             } else {
                                 Log.e(TAG, "Received error response: $errorMessage (code: $code)")
                                 _connectionState.value = ConnectionState.ERROR
                                 _lastError.value = errorMessage
                             }
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
    
    private fun handleHandshake(jsonObject: JSONObject) {
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

                if (_serverFeatures.value.encryption) {
                    val salt = jsonObject.optString("salt", "")
                    val secret = connectionData?.pairingSecret
                    if (secret.isNullOrEmpty() || salt.isEmpty()) {
                        // No pairing secret (e.g. a reconnect via the bare URL, or never scanned)
                        // or no salt — we cannot derive the session key. Proceeding would send
                        // messages the host can't read; surface a clear re-pair prompt instead.
                        Log.e(TAG, "Encrypted server but no pairing secret/salt; cannot derive a session key")
                        _connectionState.value = ConnectionState.ERROR
                        _lastError.value = "Pairing required — scan the QR code again to reconnect"
                        return
                    }
                    deriveSessionKey(secret, salt)
                }

                // Authorization is implicit: possessing the QR secret lets us derive the session
                // key, and the host will only accept what authenticates under it.
                _connectionState.value = ConnectionState.AUTHENTICATED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling handshake", e)
            _connectionState.value = ConnectionState.ERROR
            _lastError.value = "Failed to establish a secure session"
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
    
    /**
     * Derive this session's AES-256 key = HMAC-SHA256(pairingSecret, salt), matching the host.
     * The pairing secret came from the QR; the salt came from the handshake. Nothing secret is
     * logged.
     */
    private fun deriveSessionKey(pairingSecretB64: String, saltB64: String) {
        val secret = Base64.decode(pairingSecretB64, Base64.URL_SAFE or Base64.NO_PADDING)
        val salt = Base64.decode(saltB64, Base64.URL_SAFE or Base64.NO_PADDING)
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        encryptionKey = SecretKeySpec(mac.doFinal(salt), "AES")
        isEncryptionEnabled = true
        Log.d(TAG, "Session key derived")
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
            
            // Encode as base64 (URL-safe, no padding — matches the host)
            Base64.encodeToString(encryptedData, Base64.URL_SAFE or Base64.NO_PADDING)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            message
        }
    }
    
    private fun decryptMessage(encryptedMessage: String): String? {
        return try {
            if (!isEncryptionEnabled || encryptionKey == null) return encryptedMessage

            // Decode from base64
            val encryptedData = Base64.decode(encryptedMessage, Base64.URL_SAFE or Base64.NO_PADDING)

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
    
    /** Build a serialized input envelope (plaintext; encryption is applied at send time). */
    private fun buildEnvelope(id: String, seq: Int, total: Int, type: String, payload: JSONObject): String =
        JSONObject().apply {
            put("v", PROTOCOL_VERSION)
            put("id", id)
            put("seq", seq)
            put("total", total)
            put("type", type)
            put("payload", payload)
        }.toString()

    /** Encrypt (if the session is encrypted) and send one already-serialized message. */
    private fun sendRaw(message: String) {
        val finalMessage = if (isEncryptionEnabled && _connectionState.value == ConnectionState.AUTHENTICATED) {
            encryptMessage(message) ?: message
        } else {
            message
        }
        webSocketClient?.send(finalMessage)
    }

    /** Apply a host acknowledgement to the matching in-flight message. */
    private fun handleAck(json: JSONObject) {
        val id = json.optString("id")
        val seq = json.optInt("seq", -1)
        val outbound = pending[id] ?: return
        if (seq < 0 || seq >= outbound.total) return

        if (json.optString("status") == "ok") {
            if (!outbound.acked[seq]) {
                outbound.acked[seq] = true
                // Progress: refresh the stall timer and restore the full stall budget.
                outbound.lastProgressAt = System.currentTimeMillis()
                outbound.stallRetries = 0
            }
            if (outbound.allAcked) {
                pending.remove(id)
                if (outbound.isText) _textDelivery.value = DeliveryState.Delivered(id)
                Log.d(TAG, "Delivered id=$id")
            } else {
                sendWindow(outbound)  // an ack freed a window slot; send the next chunk(s)
                if (outbound.isText) _textDelivery.value = DeliveryState.Sending(id, outbound.ackedCount, outbound.total)
            }
        } else {
            // An error ack means the host rejected this input; retrying the same bytes
            // will not help, so fail without retry.
            val reason = json.optString("error", "Server rejected the input")
            failMessage(outbound, reason, retryable = false)
        }
    }

    private fun failMessage(outbound: Outbound, reason: String, retryable: Boolean) {
        if (outbound.failed) return
        outbound.failed = true
        pending.remove(outbound.id)
        if (outbound.isText) {
            lastFailedText = outbound
            _textDelivery.value = DeliveryState.Failed(outbound.id, reason, retryable)
        } else {
            _lastError.value = reason
        }
        Log.w(TAG, "Failed id=${outbound.id}: $reason (retryable=$retryable)")
    }

    private fun failAllPending(reason: String) {
        for (outbound in pending.values.toList()) {
            failMessage(outbound, reason, retryable = true)
        }
    }

    /**
     * Stall-driven retry monitor. While acks keep advancing, the message is healthy no matter
     * how slowly the host types. Only when no ack arrives for [STALL_TIMEOUT_MS] does it resend
     * the in-flight (unacked) chunks — same id/seq, so the host de-dupes — at most
     * [MAX_STALL_RETRIES] times before failing. Exits when nothing is pending or the connection
     * drops, so it cannot spin unbounded.
     */
    private fun startDeliveryMonitor() {
        if (deliveryMonitorJob?.isActive == true) return

        deliveryMonitorJob = viewModelScope.launch {
            while (pending.isNotEmpty() && isReady()) {
                delay(DELIVERY_TICK_MS)
                val now = System.currentTimeMillis()
                for (outbound in pending.values.toList()) {
                    if (outbound.failed) continue
                    // Healthy as long as acks keep advancing; only a true stall is a problem.
                    if (now - outbound.lastProgressAt < STALL_TIMEOUT_MS) continue

                    if (outbound.stallRetries >= MAX_STALL_RETRIES) {
                        failMessage(outbound, "Delivery stalled (no response from the PC)", retryable = true)
                        continue
                    }
                    outbound.stallRetries++
                    outbound.lastProgressAt = now
                    // Resend only the in-flight (sent-but-unacked) chunks — at most the window
                    // size, not the whole message. The host de-dupes any it already applied.
                    for (seq in 0 until outbound.sentCount) {
                        if (!outbound.acked[seq]) {
                            try {
                                sendRaw(outbound.envelopes[seq])
                            } catch (e: Exception) {
                                Log.e(TAG, "Stall resend failed for id=${outbound.id}#$seq", e)
                            }
                        }
                    }
                    Log.w(TAG, "Stall resend id=${outbound.id} (attempt ${outbound.stallRetries})")
                }
            }
            // The connection dropped with chunks still unconfirmed: keep the text, show failure.
            if (!isReady()) failAllPending("Connection lost before delivery was confirmed")
        }
    }

    private fun stopDeliveryMonitor() {
        deliveryMonitorJob?.cancel()
        deliveryMonitorJob = null
    }

    /** Re-send the unconfirmed chunks of the last failed text message, reusing its id/seq. */
    fun retryTextDelivery() {
        val outbound = lastFailedText ?: return
        if (!isReady()) {
            _lastError.value = "Not connected"
            return
        }
        outbound.failed = false
        outbound.stallRetries = 0
        outbound.lastProgressAt = System.currentTimeMillis()
        pending[outbound.id] = outbound
        lastFailedText = null
        _textDelivery.value = DeliveryState.Sending(outbound.id, outbound.ackedCount, outbound.total)

        viewModelScope.launch {
            // Resend the in-flight chunks, then continue filling the window with any not-yet-sent.
            for (seq in 0 until outbound.sentCount) {
                if (!outbound.acked[seq]) sendRaw(outbound.envelopes[seq])
            }
            sendWindow(outbound)
            startDeliveryMonitor()
        }
    }

    /** Called by the UI once it has reacted to a terminal delivery state, so it fires once. */
    fun consumeTextDelivery() {
        _textDelivery.value = DeliveryState.Idle
        lastFailedText = null
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
                    // Reconnect with the full pairing payload (URL + token) so we re-authenticate;
                    // falling back to the bare URL only if we never had a token.
                    val target = lastQrData ?: connectionData?.url ?: _serverUrl.value
                    if (target.isNotBlank()) {
                        // Reset state before reconnecting
                        _connectionState.value = ConnectionState.DISCONNECTED
                        connectToServer(target)
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
