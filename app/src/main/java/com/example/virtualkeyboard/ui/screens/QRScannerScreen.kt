package com.example.virtualkeyboard.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.virtualkeyboard.R
import com.example.virtualkeyboard.ui.viewmodels.QRScannerEvent
import com.example.virtualkeyboard.ui.viewmodels.QRScannerViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerScreen(
    onQRCodeScanned: (String) -> Unit,
    onBackPressed: () -> Unit,
    viewModel: QRScannerViewModel
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    // Check initial camera permission
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onEvent(QRScannerEvent.SetCameraPermission(hasPermission))
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            viewModel.onEvent(QRScannerEvent.SetCameraPermission(granted))
            if (granted) {
                viewModel.onEvent(QRScannerEvent.StartScanning)
            }
        }
    )

    val barcodeLauncher = rememberLauncherForActivityResult(
        contract = ScanContract(),
        onResult = { result ->
            result.contents?.let { contents ->
                viewModel.onEvent(QRScannerEvent.SetScannedResult(contents))
            }
        }
    )

    // Launch scanner when scanning state changes
    LaunchedEffect(state.isScanning) {
        if (state.isScanning && state.hasCameraPermission) {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Scan QR Code")
                setCameraId(0)
                setBeepEnabled(false)
                setBarcodeImageEnabled(true)
                setOrientationLocked(false)
            }
            barcodeLauncher.launch(options)
        }
    }

    // Result Dialog
    state.scannedResult?.let { result ->
        AlertDialog(
            onDismissRequest = {
                viewModel.onEvent(QRScannerEvent.ClearScannedResult)
            },
            title = { Text("QR Code Scanned") },
            text = { Text(result) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onQRCodeScanned(result)
                        viewModel.onEvent(QRScannerEvent.ClearScannedResult)
                    }
                ) {
                    Text("Connect")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.onEvent(QRScannerEvent.ClearScannedResult)
                        viewModel.onEvent(QRScannerEvent.StartScanning)
                    }
                ) {
                    Text("Scan Again")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan QR Code") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!state.hasCameraPermission) {
                Icon(
                    painter = painterResource(id = R.drawable.qr_code_scanner),
                    contentDescription = "QR Scanner",
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Camera permission is required to scan QR codes",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        launcher.launch(Manifest.permission.CAMERA)
                    }
                ) {
                    Text("Grant Permission")
                }
            } else if (!state.isScanning) {
                Icon(
                    painter = painterResource(id = R.drawable.qr_code_scanner),
                    contentDescription = "QR Scanner",
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Ready to scan",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        viewModel.onEvent(QRScannerEvent.StartScanning)
                    }
                ) {
                    Text("Start Scanning")
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Scanning for QR code...",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
 