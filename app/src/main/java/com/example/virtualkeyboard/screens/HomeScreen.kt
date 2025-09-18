package com.example.virtualkeyboard.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Window
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.virtualkeyboard.viewmodel.WebSocketViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.virtualkeyboard.viewmodel.WebSocketViewModel.ServerFeatures

data class KeyboardKey(
    val label: String,
    val icon: ImageVector? = null,
    val action: String,
    val isSpecial: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: WebSocketViewModel
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val lastError by viewModel.lastError.collectAsState()
    val serverFeatures by viewModel.serverFeatures.collectAsState() // Ensure serverFeatures is resolved
    val isConnected = connectionState == WebSocketViewModel.ConnectionState.CONNECTED || 
                     connectionState == WebSocketViewModel.ConnectionState.AUTHENTICATED
    var textInput by remember { mutableStateOf("") }
    
    // Connection status animation
    val connectionColor by animateColorAsState(
        targetValue = if (isConnected) 
            MaterialTheme.colorScheme.primary 
        else 
            MaterialTheme.colorScheme.error,
        label = "connectionColor"
    )
    
    val connectionAlpha by animateFloatAsState(
        targetValue = if (isConnected) 1f else 0.6f,
        label = "connectionAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with connection status
        val serverUrl by viewModel.serverUrl.collectAsState()
        ConnectionStatusCard(
            isConnected = isConnected,
            connectionState = connectionState,
            connectionColor = connectionColor,
            connectionAlpha = connectionAlpha,
            lastError = lastError,
            onConnectClick = { 
                navController.navigate("qr_scanner")
            },
            onClearError = { viewModel.clearError() },
            onDisconnect = { viewModel.disconnect() },
            serverUrl = serverUrl,
            serverFeatures = serverFeatures // Pass serverFeatures to ConnectionStatusCard
        )

        // Text input section
        TextInputSection(
            textInput = textInput,
            onTextChange = { textInput = it },
            onSendText = { 
                if (isConnected && textInput.isNotBlank()) {
                    viewModel.sendText(textInput)
                    textInput = ""
                }
            },
            isConnected = isConnected
        )

        // Keyboard controls
        KeyboardControlsSection(
            isConnected = isConnected,
            viewModel = viewModel,
            onKeyPress = { key -> 
                if (isConnected) {
                    if (key.action.contains("+")) {
                        // Send as key combo (simultaneous key press)
                        val keys = key.action.split("+")
                        viewModel.sendKeyCombo(keys)
                    } else {
                        // For single keys, send press and release
                        viewModel.sendKeyPressAndRelease(key.action)
                    }
                }
            }
        )
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
    serverUrl: String,
    serverFeatures: ServerFeatures // Add serverFeatures parameter
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(connectionAlpha),
        colors = CardDefaults.cardColors(
            containerColor = connectionColor.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                    contentDescription = null,
                    tint = connectionColor
                )
                Text(
                    text = when (connectionState) {
                        WebSocketViewModel.ConnectionState.DISCONNECTED -> "Disconnected"
                        WebSocketViewModel.ConnectionState.CONNECTING -> "Connecting..."
                        WebSocketViewModel.ConnectionState.CONNECTED -> "Connected"
                        WebSocketViewModel.ConnectionState.AUTHENTICATING -> "Authenticating..."
                        WebSocketViewModel.ConnectionState.AUTHENTICATED -> "Authenticated ✓"
                        WebSocketViewModel.ConnectionState.ERROR -> "Connection Error"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = connectionColor,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (!isConnected) {
                Button(
                    onClick = onConnectClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = connectionColor
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan QR Code")
                }
            } else {
                // Show server URL and security features when connected
                if (serverUrl.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Connected to: ${serverUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = connectionColor,
                        textAlign = TextAlign.Center
                    )

                    // Show security features
                    if (serverFeatures.authentication || serverFeatures.encryption) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (serverFeatures.authentication) {
                                Text(
                                    text = "🔐 Auth",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = connectionColor
                                )
                            }
                            if (serverFeatures.encryption) {
                                if (serverFeatures.authentication) {
                                    Text(
                                        text = " • ",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = connectionColor
                                    )
                                }
                                Text(
                                    text = "🔒 Encrypted",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = connectionColor
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent
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
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Text Input",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            OutlinedTextField(
                value = textInput,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Type text to send...") },
                enabled = isConnected,
                minLines = 2,
                maxLines = 4
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onSendText,
                    enabled = isConnected && textInput.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send")
                }
            }
        }
    }
}

@Composable
fun KeyboardControlsSection(
    isConnected: Boolean,
    viewModel: WebSocketViewModel,
    onKeyPress: (KeyboardKey) -> Unit
) {
    val specialKeys = listOf(
        KeyboardKey("Ctrl", Icons.Filled.ControlCamera, "ctrl", true),
        KeyboardKey("Alt", Icons.Filled.AlternateEmail, "alt", true),
        KeyboardKey("Shift", Icons.Filled.ArrowUpward, "shift", true),
        KeyboardKey("Tab", Icons.Filled.Tab, "tab", true),
        KeyboardKey("Enter", Icons.Filled.KeyboardReturn, "enter", true),
        KeyboardKey("Space", Icons.Filled.SpaceBar, "space", true),
        KeyboardKey("Backspace", Icons.Filled.Backspace, "backspace", true),
        KeyboardKey("Delete", Icons.Filled.Delete, "delete", true),
        KeyboardKey("Esc", Icons.Filled.Close, "esc", true)
    )
    
    val functionKeys = listOf(
        KeyboardKey("F1", null, "f1"),
        KeyboardKey("F2", null, "f2"),
        KeyboardKey("F3", null, "f3"),
        KeyboardKey("F4", null, "f4"),
        KeyboardKey("F5", null, "f5"),
        KeyboardKey("F6", null, "f6"),
        KeyboardKey("F7", null, "f7"),
        KeyboardKey("F8", null, "f8"),
        KeyboardKey("F9", null, "f9"),
        KeyboardKey("F10", null, "f10"),
        KeyboardKey("F11", null, "f11"),
        KeyboardKey("F12", null, "f12")
    )

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Keyboard Controls",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            // Special Keys Section
            Text(
                text = "Special Keys",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(200.dp)
            ) {
                items(specialKeys) { key ->
                    KeyButton(
                        key = key,
                        onClick = { onKeyPress(key) },
                        enabled = isConnected
                    )
                }
            }
            
            // Function Keys Section
            Text(
                text = "Function Keys",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(150.dp)
            ) {
                items(functionKeys) { key ->
                    KeyButton(
                        key = key,
                        onClick = { onKeyPress(key) },
                        enabled = isConnected
                    )
                }
            }
            
            // Common Hotkeys Section
            Text(
                text = "Common Hotkeys",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            val commonHotkeys = listOf(
                KeyboardKey("Copy", Icons.Filled.ContentCopy, "ctrl+c", true),
                KeyboardKey("Paste", Icons.Filled.ContentPaste, "ctrl+v", true),
                KeyboardKey("Cut", Icons.Filled.ContentCut, "ctrl+x", true),
                KeyboardKey("Undo", Icons.Filled.Undo, "ctrl+z", true),
                KeyboardKey("Redo", Icons.Filled.Redo, "ctrl+y", true),
                KeyboardKey("Select All", Icons.Filled.SelectAll, "ctrl+a", true),
                KeyboardKey("Save", Icons.Filled.Save, "ctrl+s", true),
                KeyboardKey("Find", Icons.Filled.Search, "ctrl+f", true),
                KeyboardKey("Alt+Tab", Icons.Filled.Tab, "alt+tab", true),
                KeyboardKey("Win", Icons.Filled.Window, "cmd", true)
            )
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(120.dp)
            ) {
                items(commonHotkeys) { key ->
                    KeyButton(
                        key = key,
                        onClick = { 
                            if (isConnected) {
                                                        if (key.action.contains("+")) {
                            // Send as key combo (simultaneous key press)
                            val keys = key.action.split("+")
                            viewModel.sendKeyCombo(keys)
                        } else {
                            onKeyPress(key)
                        }
                            }
                        },
                        enabled = isConnected
                    )
                }
            }
        }
    }
}

@Composable
fun KeyButton(
    key: KeyboardKey,
    onClick: () -> Unit,
    enabled: Boolean
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        label = "keyScale"
    )

    OutlinedButton(
        onClick = {
            onClick()
            isPressed = true
            // Reset animation after a short delay
        },
        enabled = enabled,
        modifier = Modifier
            .scale(scale)
            .height(48.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (key.isSpecial) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        if (key.icon != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = key.icon,
                    contentDescription = key.label,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = key.label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Text(
                text = key.label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center
            )
        }
    }
    
    // Reset pressed state
    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(100)
            isPressed = false
        }
    }
} 