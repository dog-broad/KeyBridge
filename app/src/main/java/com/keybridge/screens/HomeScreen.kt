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

package com.keybridge.screens

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresPermission
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.KeyboardTab
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.keybridge.R
import com.keybridge.protocol.DeliveryState
import com.keybridge.ui.rememberReducedMotion
import com.keybridge.viewmodel.PreferencesViewModel
import com.keybridge.viewmodel.WebSocketViewModel
import com.keybridge.viewmodel.WebSocketViewModel.ServerFeatures
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class KeyboardKey(
    val label: String,
    val icon: ImageVector? = null,
    val action: String,
    val isSpecial: Boolean = false,
    val width: Float = 1f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: WebSocketViewModel,
    preferencesViewModel: PreferencesViewModel
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val lastError by viewModel.lastError.collectAsState()
    val serverFeatures by viewModel.serverFeatures.collectAsState()
    val textDelivery by viewModel.textDelivery.collectAsState()
    val hapticFeedback by preferencesViewModel.hapticFeedback.collectAsState()
    val typingDelay by preferencesViewModel.typingDelay.collectAsState()
    val keyRepeatRate by preferencesViewModel.keyRepeatRate.collectAsState()
    val macMode by preferencesViewModel.macMode.collectAsState()
    
    val isConnected = connectionState == WebSocketViewModel.ConnectionState.CONNECTED || 
                     connectionState == WebSocketViewModel.ConnectionState.AUTHENTICATED
    var textInput by remember { mutableStateOf("") }
    
    // Toggleable modifier keys state
    var ctrlToggled by remember { mutableStateOf(false) }
    var altToggled by remember { mutableStateOf(false) }
    var shiftToggled by remember { mutableStateOf(false) }
    var winToggled by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Vibrator for haptic feedback
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
    
    @RequiresPermission(Manifest.permission.VIBRATE)
    fun performHapticFeedback() {
        if (hapticFeedback) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(30)
            }
        }
    }
    
    // Connection status animation
    val connectionColor by animateColorAsState(
        targetValue = if (isConnected) 
            MaterialTheme.colorScheme.primary 
        else 
            MaterialTheme.colorScheme.error,
        label = "connectionColor"
    )
    
    val connectionAlpha by animateFloatAsState(
        targetValue = if (isConnected) 1f else 0.7f,
        label = "connectionAlpha"
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with connection status
            val serverUrl by viewModel.serverUrl.collectAsState()
            ConnectionStatusCard(
                isConnected = isConnected,
                connectionState = connectionState,
                connectionColor = connectionColor,
                connectionAlpha = connectionAlpha,
                lastError = lastError,
                onConnectClick = { navController.navigate("qr_scanner") },
                onClearError = { viewModel.clearError() },
                onDisconnect = {
                    viewModel.disconnect()
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.snackbar_disconnected_from_server))
                    }
                },
                onReconnect = {
                    if (serverUrl.isNotBlank()) {
                        viewModel.reconnect()
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.snackbar_reconnecting))
                        }
                    }
                },
                serverUrl = serverUrl,
                serverFeatures = serverFeatures
            )

            // Clear the field only once the host has confirmed delivery — never on send.
            LaunchedEffect(textDelivery) {
                val state = textDelivery
                if (state is DeliveryState.Delivered) {
                    textInput = ""
                    viewModel.consumeTextDelivery()
                    snackbarHostState.showSnackbar(context.getString(R.string.snackbar_delivered))
                }
            }

            // Text input section
            TextInputSection(
                textInput = textInput,
                onTextChange = { textInput = it },
                onSendText = {
                    if (isConnected && textInput.isNotBlank()) {
                        performHapticFeedback()
                        viewModel.sendText(textInput, typingDelay.toInt())
                    }
                },
                onRetry = {
                    performHapticFeedback()
                    viewModel.retryTextDelivery()
                },
                deliveryState = textDelivery,
                isConnected = isConnected
            )

            // Keyboard controls
            KeyboardControlsSection(
                isConnected = isConnected,
                viewModel = viewModel,
                keyRepeatRate = keyRepeatRate,
                macMode = macMode,
                ctrlToggled = ctrlToggled,
                altToggled = altToggled,
                shiftToggled = shiftToggled,
                winToggled = winToggled,
                onModifierToggle = { modifier, toggled ->
                    when (modifier) {
                        "ctrl" -> {
                            ctrlToggled = toggled
                            if (toggled) viewModel.sendKeyPress("ctrl") else viewModel.sendKeyRelease("ctrl")
                        }
                        "alt" -> {
                            altToggled = toggled
                            if (toggled) viewModel.sendKeyPress("alt") else viewModel.sendKeyRelease("alt")
                        }
                        "shift" -> {
                            shiftToggled = toggled
                            if (toggled) viewModel.sendKeyPress("shift") else viewModel.sendKeyRelease("shift")
                        }
                        "cmd" -> {
                            winToggled = toggled
                            if (toggled) viewModel.sendKeyPress("cmd") else viewModel.sendKeyRelease("cmd")
                        }
                    }
                    performHapticFeedback()
                },
                onKeyPress = { key ->
                    if (isConnected) {
                        performHapticFeedback()
                        if (key.action.contains("+")) {
                            val keys = key.action.split("+")
                            viewModel.sendKeyCombo(keys)
                        } else {
                            // Toggled modifiers are already held down on the host and stay held
                            // until tapped off (the "tap to hold" contract). Just send the key —
                            // the held modifiers apply to it and remain held for the next key.
                            viewModel.sendKeyPressAndRelease(key.action)
                        }
                    }
                },
                onHapticFeedback = { performHapticFeedback() }
            )
            
            // Add some bottom padding for better scrolling
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun ConnectionStatusCard(
    isConnected: Boolean,
    connectionState: WebSocketViewModel.ConnectionState,
    connectionColor: Color,
    connectionAlpha: Float,
    lastError: String,
    onConnectClick: () -> Unit,
    onClearError: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
    serverUrl: String,
    serverFeatures: ServerFeatures
) {
    // Pulse animation for connection indicator (held static when the user minimises motion).
    val reducedMotion = rememberReducedMotion()
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val animatedScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val pulseScale = if (reducedMotion) 1f else animatedScale
    val pulseAlpha = if (reducedMotion) 1f else animatedAlpha
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(connectionAlpha),
        colors = CardDefaults.cardColors(
            containerColor = connectionColor.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.width(8.dp))
                // Pulsing connection indicator
                Box(contentAlignment = Alignment.Center) {
                    if (isConnected) {
                        // Pulse ring behind the icon
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .scale(pulseScale)
                                .alpha(pulseAlpha * 0.3f)
                                .clip(CircleShape)
                                .background(connectionColor)
                        )
                    }
                    Icon(
                        imageVector = if (isConnected) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                        contentDescription = null,
                        tint = connectionColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (connectionState) {
                        WebSocketViewModel.ConnectionState.DISCONNECTED -> stringResource(R.string.status_disconnected)
                        WebSocketViewModel.ConnectionState.CONNECTING -> stringResource(R.string.status_connecting)
                        WebSocketViewModel.ConnectionState.CONNECTED -> stringResource(R.string.status_connected)
                        WebSocketViewModel.ConnectionState.AUTHENTICATED -> stringResource(R.string.status_ready)
                        WebSocketViewModel.ConnectionState.ERROR -> stringResource(R.string.status_connection_error)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = connectionColor,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (!isConnected) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Scan QR Code button
                    FilledTonalButton(
                        onClick = onConnectClick,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = connectionColor.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.button_scan_qr_short), fontWeight = FontWeight.Medium)
                    }
                    
                    // Quick reconnect button (only if we have a previous URL)
                    if (serverUrl.isNotBlank()) {
                        OutlinedButton(
                            onClick = onReconnect,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.button_reconnect), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            } else {
                // Show server URL and security features when connected
                if (serverUrl.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = serverUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            
                            if (serverFeatures.authentication || serverFeatures.encryption) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (serverFeatures.authentication) {
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(stringResource(R.string.feature_chip_auth), style = MaterialTheme.typography.labelSmall) },
                                            leadingIcon = { 
                                                Icon(
                                                    Icons.Filled.Lock, 
                                                    null, 
                                                    modifier = Modifier.size(14.dp)
                                                ) 
                                            },
                                            modifier = Modifier.height(28.dp)
                                        )
                                    }
                                    if (serverFeatures.encryption) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(stringResource(R.string.feature_chip_encrypted), style = MaterialTheme.typography.labelSmall) },
                                            leadingIcon = { 
                                                Icon(
                                                    Icons.Filled.Shield, 
                                                    null, 
                                                    modifier = Modifier.size(14.dp)
                                                ) 
                                            },
                                            modifier = Modifier.height(28.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                OutlinedButton(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.WifiOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.button_disconnect))
                }
            }
            
            // Show error message if there's an error
            if (lastError.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = lastError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onClearError,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cd_clear_error),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextInputSection(
    textInput: String,
    onTextChange: (String) -> Unit,
    onSendText: () -> Unit,
    onRetry: () -> Unit,
    deliveryState: DeliveryState,
    isConnected: Boolean
) {
    val isSending = deliveryState is DeliveryState.Sending

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Keyboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.text_input_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedTextField(
                value = textInput,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.text_input_placeholder_dots)) },
                enabled = isConnected,
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp)
            )

            // Delivery feedback: progress while sending, failure + retry on failure.
            when (deliveryState) {
                is DeliveryState.Sending -> {
                    val total = deliveryState.totalChunks
                    val progress = if (total > 0) deliveryState.ackedChunks.toFloat() / total else 0f
                    val sendingPartsCd = stringResource(
                        R.string.delivery_sending_parts_cd,
                        deliveryState.ackedChunks,
                        total
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    contentDescription = sendingPartsCd
                                }
                        )
                        Text(
                            text = if (total > 1) {
                                stringResource(
                                    R.string.delivery_sending_progress,
                                    deliveryState.ackedChunks,
                                    total
                                )
                            } else {
                                stringResource(R.string.delivery_sending)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is DeliveryState.Failed -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = deliveryState.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        if (deliveryState.retryable) {
                            TextButton(onClick = onRetry, enabled = isConnected) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.button_retry))
                            }
                        }
                    }
                }
                else -> {}
            }

            Button(
                onClick = onSendText,
                enabled = isConnected && textInput.isNotBlank() && !isSending,
                modifier = Modifier.align(Alignment.End),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isSending) stringResource(R.string.delivery_sending) else stringResource(R.string.button_send),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun KeyboardControlsSection(
    isConnected: Boolean,
    viewModel: WebSocketViewModel,
    keyRepeatRate: Float,
    macMode: Boolean,
    ctrlToggled: Boolean,
    altToggled: Boolean,
    shiftToggled: Boolean,
    winToggled: Boolean,
    onModifierToggle: (String, Boolean) -> Unit,
    onKeyPress: (KeyboardKey) -> Unit,
    onHapticFeedback: () -> Unit
) {
    // Calculate repeat interval based on rate (base 100ms, rate adjusts speed)
    val repeatIntervalMs = (100 / keyRepeatRate).toLong().coerceIn(50, 500)
    
    // Modifier keys are toggleable - labels change based on Mac mode
    val modifierKeys = if (macMode) {
        listOf(
            Triple(KeyboardKey("⌃ Ctrl", Icons.Filled.ControlCamera, "ctrl", true), ctrlToggled, "ctrl"),
            Triple(KeyboardKey("⌥ Opt", Icons.Filled.AlternateEmail, "alt", true), altToggled, "alt"),
            Triple(KeyboardKey("⇧ Shift", Icons.Filled.KeyboardArrowUp, "shift", true), shiftToggled, "shift"),
            Triple(KeyboardKey("⌘ Cmd", Icons.Filled.Window, "cmd", true), winToggled, "cmd")
        )
    } else {
        listOf(
            Triple(KeyboardKey("Ctrl", Icons.Filled.ControlCamera, "ctrl", true), ctrlToggled, "ctrl"),
            Triple(KeyboardKey("Alt", Icons.Filled.AlternateEmail, "alt", true), altToggled, "alt"),
            Triple(KeyboardKey("Shift", Icons.Filled.KeyboardArrowUp, "shift", true), shiftToggled, "shift"),
            Triple(KeyboardKey("Win", Icons.Filled.Window, "cmd", true), winToggled, "cmd")
        )
    }
    
    val navigationKeys = listOf(
        KeyboardKey("↑", Icons.Filled.KeyboardArrowUp, "up", true),
        KeyboardKey("↓", Icons.Filled.KeyboardArrowDown, "down", true),
        KeyboardKey("←", Icons.AutoMirrored.Filled.KeyboardArrowLeft, "left", true),
        KeyboardKey("→", Icons.AutoMirrored.Filled.KeyboardArrowRight, "right", true),
        KeyboardKey("Home", Icons.Filled.FirstPage, "home", true),
        KeyboardKey("End", Icons.AutoMirrored.Filled.LastPage, "end", true)
    )
    
    // Extended navigation keys - some don't exist on Mac
    val extendedNavKeys = if (macMode) {
        listOf(
            KeyboardKey("PgUp", Icons.Filled.ExpandLess, "page_up", true),
            KeyboardKey("PgDn", Icons.Filled.ExpandMore, "page_down", true),
            KeyboardKey("Fn+Del", Icons.Filled.Delete, "delete", true),  // Mac: Fn+Delete = Forward Delete
            KeyboardKey("⌘⇧3", Icons.Filled.Screenshot, "cmd+shift+3", true)  // Mac screenshot
        )
    } else {
        listOf(
            KeyboardKey("PgUp", Icons.Filled.ExpandLess, "page_up", true),
            KeyboardKey("PgDn", Icons.Filled.ExpandMore, "page_down", true),
            KeyboardKey("Ins", Icons.Filled.Add, "insert", true),
            KeyboardKey("PrtSc", Icons.Filled.Screenshot, "print_screen", true)
        )
    }
    
    // Lock and system keys - varies by platform
    val systemKeys = if (macMode) {
        listOf(
            KeyboardKey("Caps", Icons.Filled.TextFields, "caps_lock", true),
            KeyboardKey("Fn", Icons.Filled.Tune, "fn", true),
            KeyboardKey("Globe", Icons.Filled.Language, "fn", true)  // Globe key on newer Macs
        )
    } else {
        listOf(
            KeyboardKey("Caps", Icons.Filled.TextFields, "caps_lock", true),
            KeyboardKey("Num", Icons.Filled.Pin, "num_lock", true),
            KeyboardKey("Scroll", Icons.Filled.Lock, "scroll_lock", true),
            KeyboardKey("Menu", Icons.Filled.Menu, "menu", true),
            KeyboardKey("Pause", Icons.Filled.Pause, "pause", true)
        )
    }
    
    val actionKeys = listOf(
        KeyboardKey("Tab", Icons.AutoMirrored.Filled.KeyboardTab, "tab", true),
        KeyboardKey(if (macMode) "Return" else "Enter", Icons.AutoMirrored.Filled.KeyboardReturn, "enter", true),
        KeyboardKey("Space", Icons.Filled.SpaceBar, "space", true),
        KeyboardKey("⌫", Icons.AutoMirrored.Filled.Backspace, "backspace", true),
        KeyboardKey("Del", Icons.Filled.Delete, "delete", true),
        KeyboardKey("Esc", Icons.Filled.Close, "esc", true)
    )
    
    // Media control keys
    val mediaKeys = listOf(
        KeyboardKey("⏮", Icons.Filled.SkipPrevious, "media_previous", true),
        KeyboardKey("⏯", Icons.Filled.PlayArrow, "media_play_pause", true),
        KeyboardKey("⏭", Icons.Filled.SkipNext, "media_next", true),
        KeyboardKey("🔇", Icons.Filled.VolumeOff, "media_volume_mute", true),
        KeyboardKey("🔉", Icons.AutoMirrored.Filled.VolumeDown, "media_volume_down", true),
        KeyboardKey("🔊", Icons.AutoMirrored.Filled.VolumeUp, "media_volume_up", true)
    )
    
    val functionKeys = (1..12).map { n ->
        KeyboardKey("F$n", null, "f$n")
    }

    // Common hotkeys - use Cmd on Mac, Ctrl on Windows/Linux
    val commonHotkeys = if (macMode) {
        listOf(
            KeyboardKey("Copy", Icons.Filled.ContentCopy, "cmd+c", true),
            KeyboardKey("Paste", Icons.Filled.ContentPaste, "cmd+v", true),
            KeyboardKey("Cut", Icons.Filled.ContentCut, "cmd+x", true),
            KeyboardKey("Undo", Icons.AutoMirrored.Filled.Undo, "cmd+z", true),
            KeyboardKey("Redo", Icons.AutoMirrored.Filled.Redo, "cmd+shift+z", true),  // Mac uses Cmd+Shift+Z
            KeyboardKey("All", Icons.Filled.SelectAll, "cmd+a", true),
            KeyboardKey("Save", Icons.Filled.Save, "cmd+s", true),
            KeyboardKey("Find", Icons.Filled.Search, "cmd+f", true),
            KeyboardKey("Switch", Icons.Filled.SwapHoriz, "cmd+tab", true)  // Cmd+Tab on Mac
        )
    } else {
        listOf(
            KeyboardKey("Copy", Icons.Filled.ContentCopy, "ctrl+c", true),
            KeyboardKey("Paste", Icons.Filled.ContentPaste, "ctrl+v", true),
            KeyboardKey("Cut", Icons.Filled.ContentCut, "ctrl+x", true),
            KeyboardKey("Undo", Icons.AutoMirrored.Filled.Undo, "ctrl+z", true),
            KeyboardKey("Redo", Icons.AutoMirrored.Filled.Redo, "ctrl+y", true),
            KeyboardKey("All", Icons.Filled.SelectAll, "ctrl+a", true),
            KeyboardKey("Save", Icons.Filled.Save, "ctrl+s", true),
            KeyboardKey("Find", Icons.Filled.Search, "ctrl+f", true),
            KeyboardKey("Alt+Tab", Icons.Filled.SwapHoriz, "alt+tab", true)
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Keyboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.keyboard_controls_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Modifier Keys (Toggleable)
            KeySection(title = stringResource(R.string.section_modifiers)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    modifierKeys.forEach { (key, isToggled, modifierId) ->
                        Box(modifier = Modifier.weight(1f)) {
                            ToggleableKeyButton(
                                key = key,
                                isToggled = isToggled,
                                onToggle = { onModifierToggle(modifierId, !isToggled) },
                                enabled = isConnected
                            )
                        }
                    }
                }
            }
            
            // Navigation Keys
            KeySection(title = stringResource(R.string.section_navigation)) {
                KeyGridRow(
                    keys = navigationKeys,
                    isConnected = isConnected,
                    onKeyPress = onKeyPress,
                    repeatIntervalMs = repeatIntervalMs,
                    onHapticFeedback = onHapticFeedback
                )
                Spacer(modifier = Modifier.height(6.dp))
                KeyGridRow(
                    keys = extendedNavKeys,
                    isConnected = isConnected,
                    onKeyPress = onKeyPress,
                    repeatIntervalMs = repeatIntervalMs,
                    onHapticFeedback = onHapticFeedback
                )
            }
            
            // Action Keys
            KeySection(title = stringResource(R.string.section_actions)) {
                KeyGridRow(
                    keys = actionKeys,
                    isConnected = isConnected,
                    onKeyPress = onKeyPress,
                    repeatIntervalMs = repeatIntervalMs,
                    onHapticFeedback = onHapticFeedback
                )
            }
            
            // Media Keys
            KeySection(title = stringResource(R.string.section_media_controls)) {
                KeyGridRow(
                    keys = mediaKeys,
                    isConnected = isConnected,
                    onKeyPress = onKeyPress,
                    repeatIntervalMs = repeatIntervalMs,
                    onHapticFeedback = onHapticFeedback
                )
            }
            
            // System/Lock Keys
            KeySection(title = stringResource(R.string.section_system_keys)) {
                KeyGridRow(
                    keys = systemKeys,
                    isConnected = isConnected,
                    onKeyPress = onKeyPress,
                    repeatIntervalMs = repeatIntervalMs,
                    onHapticFeedback = onHapticFeedback
                )
            }
            
            // Function Keys
            KeySection(title = stringResource(R.string.function_keys_title)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.height(100.dp),
                    userScrollEnabled = false
                ) {
                    items(functionKeys) { key ->
                        KeyButton(
                            key = key,
                            onClick = { onKeyPress(key) },
                            enabled = isConnected,
                            repeatIntervalMs = repeatIntervalMs,
                            onLongPressRepeat = { onKeyPress(key); onHapticFeedback() }
                        )
                    }
                }
            }
            
            // Common Hotkeys
            KeySection(title = stringResource(R.string.section_quick_actions)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.height(145.dp),
                    userScrollEnabled = false
                ) {
                    items(commonHotkeys) { key ->
                        KeyButton(
                            key = key,
                            onClick = { 
                                if (isConnected) {
                                    onHapticFeedback()
                                    if (key.action.contains("+")) {
                                        val keys = key.action.split("+")
                                        viewModel.sendKeyCombo(keys)
                                    } else {
                                        onKeyPress(key)
                                    }
                                }
                            },
                            enabled = isConnected,
                            repeatIntervalMs = repeatIntervalMs,
                            onLongPressRepeat = null // Hotkeys don't repeat
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KeySection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.semantics { heading() }
        )
        content()
    }
}

@Composable
fun KeyGridRow(
    keys: List<KeyboardKey>,
    isConnected: Boolean,
    onKeyPress: (KeyboardKey) -> Unit,
    repeatIntervalMs: Long,
    onHapticFeedback: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        keys.forEach { key ->
            Box(modifier = Modifier.weight(1f)) {
                KeyButton(
                    key = key,
                    onClick = { onKeyPress(key) },
                    enabled = isConnected,
                    repeatIntervalMs = repeatIntervalMs,
                    onLongPressRepeat = { onKeyPress(key); onHapticFeedback() }
                )
            }
        }
    }
}

@Composable
fun KeyButton(
    key: KeyboardKey,
    onClick: () -> Unit,
    enabled: Boolean,
    repeatIntervalMs: Long = 100,
    onLongPressRepeat: (() -> Unit)? = null
) {
    var isPressed by remember { mutableStateOf(false) }
    var isLongPressing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()

    val scale by animateFloatAsState(
        targetValue = if (isPressed || isLongPressing) 0.92f else 1f,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.4f, stiffness = 400f),
        label = "keyScale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            isPressed || isLongPressing -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            key.isSpecial -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "keyBackground"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .scale(scale)
            .pointerInput(enabled, onLongPressRepeat) {
                if (enabled) {
                    detectTapGestures(
                        onPress = { offset ->
                            isPressed = true
                            val pressStartTime = System.currentTimeMillis()
                            
                            // Wait for release or long press timeout
                            val released = tryAwaitRelease()
                            val pressDuration = System.currentTimeMillis() - pressStartTime
                            
                            isPressed = false
                            isLongPressing = false
                            
                            if (released && pressDuration < 300) {
                                // Short tap - trigger single click
                                onClick()
                            }
                        },
                        onLongPress = {
                            if (onLongPressRepeat != null) {
                                isLongPressing = true
                                // Start repeating
                                scope.launch {
                                    while (isLongPressing) {
                                        onLongPressRepeat()
                                        delay(repeatIntervalMs)
                                    }
                                }
                            }
                        }
                    )
                }
            },
        shape = RoundedCornerShape(10.dp),
        color = backgroundColor,
        border = if (key.isSpecial) null else CardDefaults.outlinedCardBorder()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (key.icon != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = key.icon,
                        contentDescription = key.label,
                        modifier = Modifier.size(if (key.label.length <= 3) 18.dp else 14.dp),
                        tint = if (enabled) 
                            MaterialTheme.colorScheme.onSurface 
                        else 
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    if (key.label.length > 1 && !key.label.startsWith("↑") && !key.label.startsWith("↓") && !key.label.startsWith("←") && !key.label.startsWith("→") && !key.label.startsWith("⏮") && !key.label.startsWith("⏯") && !key.label.startsWith("⏭") && !key.label.startsWith("🔇") && !key.label.startsWith("🔉") && !key.label.startsWith("🔊")) {
                        Text(
                            text = key.label,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color = if (enabled) 
                                MaterialTheme.colorScheme.onSurface 
                            else 
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                Text(
                    text = key.label,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) 
                        MaterialTheme.colorScheme.onSurface 
                    else 
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
fun ToggleableKeyButton(
    key: KeyboardKey,
    isToggled: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean
) {
    val reducedMotion = rememberReducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (isToggled) 0.95f else 1f,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.4f, stiffness = 400f),
        label = "toggleScale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            isToggled -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        },
        label = "toggleBackground"
    )
    
    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            isToggled -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurface
        },
        label = "toggleContent"
    )

    Surface(
        onClick = { if (enabled) onToggle() },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .scale(scale),
        shape = RoundedCornerShape(10.dp),
        color = backgroundColor
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (key.icon != null) {
                    Icon(
                        imageVector = key.icon,
                        contentDescription = key.label,
                        modifier = Modifier.size(16.dp),
                        tint = contentColor
                    )
                }
                Text(
                    text = key.label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    fontWeight = if (isToggled) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor
                )
                if (isToggled) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(contentColor)
                    )
                }
            }
        }
    }
}
