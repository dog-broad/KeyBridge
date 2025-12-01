package com.example.virtualkeyboard.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.virtualkeyboard.viewmodel.PreferencesViewModel
import com.example.virtualkeyboard.viewmodel.WebSocketViewModel
import com.example.virtualkeyboard.viewmodel.WebSocketViewModel.ServerFeatures
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
    val hapticFeedback by preferencesViewModel.hapticFeedback.collectAsState()
    val typingDelay by preferencesViewModel.typingDelay.collectAsState()
    val keyRepeatRate by preferencesViewModel.keyRepeatRate.collectAsState()
    
    val isConnected = connectionState == WebSocketViewModel.ConnectionState.CONNECTED || 
                     connectionState == WebSocketViewModel.ConnectionState.AUTHENTICATED
    var textInput by remember { mutableStateOf("") }
    
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
                        snackbarHostState.showSnackbar("Disconnected from server")
                    }
                },
                onReconnect = {
                    if (serverUrl.isNotBlank()) {
                        viewModel.connectToServer(serverUrl)
                        scope.launch {
                            snackbarHostState.showSnackbar("Reconnecting...")
                        }
                    }
                },
                serverUrl = serverUrl,
                serverFeatures = serverFeatures
            )

            // Text input section
            TextInputSection(
                textInput = textInput,
                onTextChange = { textInput = it },
                onSendText = { 
                    if (isConnected && textInput.isNotBlank()) {
                        viewModel.sendText(textInput, typingDelay.toInt())
                        performHapticFeedback()
                        val sentText = textInput
                        textInput = ""
                        scope.launch {
                            snackbarHostState.showSnackbar("Sent: $sentText")
                        }
                    }
                },
                isConnected = isConnected
            )

            // Keyboard controls
            KeyboardControlsSection(
                isConnected = isConnected,
                viewModel = viewModel,
                keyRepeatRate = keyRepeatRate,
                onKeyPress = { key -> 
                    if (isConnected) {
                        performHapticFeedback()
                        if (key.action.contains("+")) {
                            val keys = key.action.split("+")
                            viewModel.sendKeyCombo(keys)
                        } else {
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
    // Pulse animation for connection indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
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
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                Text(
                    text = when (connectionState) {
                        WebSocketViewModel.ConnectionState.DISCONNECTED -> "Disconnected"
                        WebSocketViewModel.ConnectionState.CONNECTING -> "Connecting..."
                        WebSocketViewModel.ConnectionState.CONNECTED -> "Connected"
                        WebSocketViewModel.ConnectionState.AUTHENTICATING -> "Authenticating..."
                        WebSocketViewModel.ConnectionState.AUTHENTICATED -> "Ready ✓"
                        WebSocketViewModel.ConnectionState.ERROR -> "Connection Error"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = connectionColor,
                    fontWeight = FontWeight.Bold
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
                        Text("Scan QR", fontWeight = FontWeight.Medium)
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
                            Text("Reconnect", fontWeight = FontWeight.Medium)
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
                                            label = { Text("Auth", style = MaterialTheme.typography.labelSmall) },
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
                                            label = { Text("Encrypted", style = MaterialTheme.typography.labelSmall) },
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
                    Text("Disconnect")
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
                                contentDescription = "Clear error",
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
    isConnected: Boolean
) {
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
                    text = "Text Input",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            OutlinedTextField(
                value = textInput,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Type text to send...") },
                enabled = isConnected,
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp)
            )
            
            Button(
                onClick = onSendText,
                enabled = isConnected && textInput.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send", fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun KeyboardControlsSection(
    isConnected: Boolean,
    viewModel: WebSocketViewModel,
    keyRepeatRate: Float,
    onKeyPress: (KeyboardKey) -> Unit,
    onHapticFeedback: () -> Unit
) {
    // Calculate repeat interval based on rate (base 100ms, rate adjusts speed)
    val repeatIntervalMs = (100 / keyRepeatRate).toLong().coerceIn(50, 500)
    val modifierKeys = listOf(
        KeyboardKey("Ctrl", Icons.Filled.ControlCamera, "ctrl", true),
        KeyboardKey("Alt", Icons.Filled.AlternateEmail, "alt", true),
        KeyboardKey("Shift", Icons.Filled.KeyboardArrowUp, "shift", true),
        KeyboardKey("Win", Icons.Filled.Window, "cmd", true)
    )
    
    val navigationKeys = listOf(
        KeyboardKey("↑", Icons.Filled.KeyboardArrowUp, "up", true),
        KeyboardKey("↓", Icons.Filled.KeyboardArrowDown, "down", true),
        KeyboardKey("←", Icons.AutoMirrored.Filled.KeyboardArrowLeft, "left", true),
        KeyboardKey("→", Icons.AutoMirrored.Filled.KeyboardArrowRight, "right", true),
        KeyboardKey("Home", Icons.Filled.FirstPage, "home", true),
        KeyboardKey("End", Icons.AutoMirrored.Filled.LastPage, "end", true)
    )
    
    // Extended navigation keys
    val extendedNavKeys = listOf(
        KeyboardKey("PgUp", Icons.Filled.ExpandLess, "page_up", true),
        KeyboardKey("PgDn", Icons.Filled.ExpandMore, "page_down", true),
        KeyboardKey("Ins", Icons.Filled.Add, "insert", true),
        KeyboardKey("PrtSc", Icons.Filled.Screenshot, "print_screen", true)
    )
    
    // Lock and system keys
    val systemKeys = listOf(
        KeyboardKey("Caps", Icons.Filled.TextFields, "caps_lock", true),
        KeyboardKey("Num", Icons.Filled.Pin, "num_lock", true),
        KeyboardKey("Scroll", Icons.Filled.Lock, "scroll_lock", true),
        KeyboardKey("Menu", Icons.Filled.Menu, "menu", true),
        KeyboardKey("Pause", Icons.Filled.Pause, "pause", true)
    )
    
    val actionKeys = listOf(
        KeyboardKey("Tab", Icons.AutoMirrored.Filled.KeyboardTab, "tab", true),
        KeyboardKey("Enter", Icons.AutoMirrored.Filled.KeyboardReturn, "enter", true),
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

    val commonHotkeys = listOf(
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
                    text = "Keyboard Controls",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            // Modifier Keys
            KeySection(title = "Modifiers") {
                KeyGridRow(
                    keys = modifierKeys,
                    isConnected = isConnected,
                    onKeyPress = onKeyPress,
                    repeatIntervalMs = repeatIntervalMs,
                    onHapticFeedback = onHapticFeedback
                )
            }
            
            // Navigation Keys
            KeySection(title = "Navigation") {
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
            KeySection(title = "Actions") {
                KeyGridRow(
                    keys = actionKeys,
                    isConnected = isConnected,
                    onKeyPress = onKeyPress,
                    repeatIntervalMs = repeatIntervalMs,
                    onHapticFeedback = onHapticFeedback
                )
            }
            
            // Media Keys
            KeySection(title = "Media Controls") {
                KeyGridRow(
                    keys = mediaKeys,
                    isConnected = isConnected,
                    onKeyPress = onKeyPress,
                    repeatIntervalMs = repeatIntervalMs,
                    onHapticFeedback = onHapticFeedback
                )
            }
            
            // System/Lock Keys
            KeySection(title = "System Keys") {
                KeyGridRow(
                    keys = systemKeys,
                    isConnected = isConnected,
                    onKeyPress = onKeyPress,
                    repeatIntervalMs = repeatIntervalMs,
                    onHapticFeedback = onHapticFeedback
                )
            }
            
            // Function Keys
            KeySection(title = "Function Keys") {
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
            KeySection(title = "Quick Actions") {
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
            fontWeight = FontWeight.Medium
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
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed || isLongPressing) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
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
