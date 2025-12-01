package com.keybridge.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.keybridge.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.keybridge.viewmodel.PreferencesViewModel
import com.keybridge.viewmodel.WebSocketViewModel
import java.util.concurrent.Executors
import androidx.compose.ui.graphics.Color
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

@Composable
fun QRScannerScreen(
    navController: NavHostController,
    webSocketViewModel: WebSocketViewModel,
    preferencesViewModel: PreferencesViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    var qrCodeDetected by remember { mutableStateOf(false) }
    var detectedUrl by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var showManualConnection by remember { mutableStateOf(false) }
    var manualUrl by remember { mutableStateOf("") }
    
    // Camera permission launcher
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }
    
    // Launch camera permission request
    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }
    
    // Function to handle connection
    fun connectToServer(url: String) {
        isProcessing = true
        // Save the server URL for auto-connect
        preferencesViewModel.setLastServerUrl(url)
        webSocketViewModel.connectToServer(url)
        // Navigate back to home after connection attempt
        navController.navigate("home") {
            popUpTo("home") { inclusive = true }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // KeyBridge Logo
                Image(
                    painter = painterResource(id = R.drawable.ic_keybridge_logo),
                    contentDescription = "KeyBridge Logo",
                    modifier = Modifier.size(56.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Connect to KeyBridge Server",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Scan QR code or enter URL manually",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Mode toggle buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { 
                            showManualConnection = false
                            isProcessing = false
                            qrCodeDetected = false
                            detectedUrl = ""
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (!showManualConnection) 
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
                            else 
                                Color.Transparent
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("QR Scan")
                    }
                    
                    OutlinedButton(
                        onClick = { 
                            showManualConnection = true
                            isProcessing = false
                            qrCodeDetected = false
                            detectedUrl = ""
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (showManualConnection) 
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
                            else 
                                Color.Transparent
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Manual")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        
        // Camera preview or permission request
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    showManualConnection -> {
                        ManualConnectionContent(
                            url = manualUrl,
                            onUrlChange = { manualUrl = it },
                            isProcessing = isProcessing,
                            onConnect = {
                                if (manualUrl.isNotBlank()) {
                                    connectToServer(manualUrl)
                                }
                            }
                        )
                    }
                    
                    !hasCameraPermission -> {
                        PermissionRequestContent(
                            onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) }
                        )
                    }
                    
                    qrCodeDetected -> {
                        QRCodeDetectedContent(
                            url = detectedUrl,
                            isProcessing = isProcessing,
                            onConnect = {
                                connectToServer(detectedUrl)
                            },
                            onScanAgain = {
                                qrCodeDetected = false
                                detectedUrl = ""
                                isProcessing = false
                            }
                        )
                    }
                    
                    else -> {
                        CameraPreview(
                            onQrCodeDetected = { url ->
                                detectedUrl = url
                                qrCodeDetected = true
                            }
                        )
                    }
                }
            }
        }
        
        // Instructions
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "How to Connect",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (showManualConnection) {
                        "1. Run the KeyBridge Server on your PC\n" +
                        "2. Note the WebSocket URL (usually ws://YOUR_IP:8765)\n" +
                        "3. Enter the URL above and tap Connect"
                    } else {
                        "1. Run the KeyBridge Server on your PC\n" +
                        "2. A QR code will appear on your screen\n" +
                        "3. Point your camera at the QR code"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PermissionRequestContent(onRequestPermission: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(24.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Camera Permission Required",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "We need camera access to scan QR codes for quick connection setup.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onRequestPermission,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Grant Permission")
        }
    }
}

@Composable
fun QRCodeDetectedContent(
    url: String,
    isProcessing: Boolean,
    onConnect: () -> Unit,
    onScanAgain: () -> Unit
) {
    // Parse the URL/JSON to extract useful information
    val parsedInfo = remember(url) {
        try {
            val json = org.json.JSONObject(url)
            mapOf(
                "Server" to json.optString("url", ""),
                "Version" to json.optString("version", "1.0"),
                "Protocol" to json.optString("protocol", "keybridge-v1"),
                "Auth" to if (json.has("auth")) "Enabled" else "None"
            )
        } catch (e: Exception) {
            // Not JSON, just a URL
            mapOf("Server" to url)
        }
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = "Server Found!",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                parsedInfo.forEach { (label, value) ->
                    if (value.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onScanAgain,
                enabled = !isProcessing,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Again")
            }
            
            Button(
                onClick = onConnect,
                enabled = !isProcessing,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isProcessing) "Connecting..." else "Connect")
            }
        }
    }
}

@Composable
fun CameraPreview(
    onQrCodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Camera executor
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                Log.d("CameraPreview", "Camera provider obtained")
                
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                Log.d("CameraPreview", "Preview configured")
                
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, QrCodeAnalyzer { url ->
                            Log.d("CameraPreview", "QR code detected in callback: $url")
                            onQrCodeDetected(url)
                        })
                    }
                Log.d("CameraPreview", "Image analyzer configured")
                
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalyzer
                    )
                    Log.d("CameraPreview", "Camera use cases bound successfully")
                } catch (exc: Exception) {
                    Log.e("CameraPreview", "Use case binding failed", exc)
                }
                
            }, ContextCompat.getMainExecutor(ctx))
            
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
    
    // Overlay with scanning frame
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.size(250.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            border = CardDefaults.outlinedCardBorder().copy(
                width = 3.dp,
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {}
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 300.dp)
        ) {
            Text(
                text = "Position QR code within the frame",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

// QR Code analyzer implementation
class QrCodeAnalyzer(
    private val onQrCodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {
    
    private val scanner = BarcodeScanning.getClient()
    
    @ExperimentalGetImage
    override fun analyze(image: androidx.camera.core.ImageProxy) {
        val mediaImage = image.image
        if (mediaImage != null) {
            val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
            
            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    Log.d("QrCodeAnalyzer", "Found ${barcodes.size} barcodes")
                    for (barcode in barcodes) {
                        barcode.rawValue?.let { rawValue ->
                            Log.d("QrCodeAnalyzer", "Detected barcode: $rawValue")
                            
                            try {
                                // First, try to parse as JSON (new format)
                                val jsonObject = org.json.JSONObject(rawValue)
                                if (jsonObject.has("url")) {
                                    Log.d("QrCodeAnalyzer", "JSON connection data detected: $rawValue")
                                    onQrCodeDetected(rawValue)
                                    return@let
                                }
                            } catch (e: Exception) {
                                Log.d("QrCodeAnalyzer", "Not JSON format, checking legacy formats")
                            }
                            
                            // Check if it's a WebSocket URL or any URL that might be a server
                            when {
                                rawValue.startsWith("ws://") || rawValue.startsWith("wss://") -> {
                                    Log.d("QrCodeAnalyzer", "Valid WebSocket URL detected: $rawValue")
                                    onQrCodeDetected(rawValue)
                                }
                                rawValue.startsWith("http://") || rawValue.startsWith("https://") -> {
                                    // Convert HTTP to WebSocket URL
                                    val wsUrl = rawValue.replace("http://", "ws://").replace("https://", "wss://")
                                    Log.d("QrCodeAnalyzer", "Converting HTTP to WebSocket: $wsUrl")
                                    onQrCodeDetected(wsUrl)
                                }
                                rawValue.contains(":") && (rawValue.contains("8765") || rawValue.contains("localhost") || rawValue.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) -> {
                                    // Assume it's an IP:PORT and convert to WebSocket
                                    val wsUrl = if (rawValue.startsWith("ws://")) rawValue else "ws://$rawValue"
                                    Log.d("QrCodeAnalyzer", "Converting IP:PORT to WebSocket: $wsUrl")
                                    onQrCodeDetected(wsUrl)
                                }
                                else -> {
                                    Log.d("QrCodeAnalyzer", "Non-WebSocket barcode ignored: $rawValue")
                                }
                            }
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("QrCodeAnalyzer", "QR code detection failed", exception)
                }
                .addOnCompleteListener {
                    image.close()
                }
        } else {
            Log.w("QrCodeAnalyzer", "Media image is null")
            image.close()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualConnectionContent(
    url: String,
    onUrlChange: (String) -> Unit,
    isProcessing: Boolean,
    onConnect: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(24.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Link,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = "Manual Connection",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = "Enter the WebSocket URL from your computer",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Server URL") },
            placeholder = { Text("ws://192.168.1.100:8765") },
            enabled = !isProcessing,
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Wifi,
                    contentDescription = null
                )
            },
            shape = RoundedCornerShape(12.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = onConnect,
            enabled = !isProcessing && url.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isProcessing) "Connecting..." else "Connect",
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "Example URLs",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• ws://192.168.1.XXX:8765\n• ws://10.0.0.XXX:8765\n• ws://YOUR_PC_IP:8765",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
