package com.example.virtualkeyboard.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.virtualkeyboard.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    onManualConnect: (String) -> Unit,
    onQRScanClick: () -> Unit,
    onBackPressed: () -> Unit
) {
    var connectionInfo by remember { mutableStateOf("") }
    var testMode by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Test Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Test Mode")
                Switch(
                    checked = testMode,
                    onCheckedChange = { testMode = it }
                )
            }

            // Connection Info Input
            OutlinedTextField(
                value = connectionInfo,
                onValueChange = { connectionInfo = it },
                label = { Text("Connection Info") },
                modifier = Modifier.fillMaxWidth()
            )

            // Connect Button
            Button(
                onClick = {
                    if (testMode) {
                        // Test mode: Use a predefined connection string
                        onManualConnect("test://localhost:8080")
                    } else {
                        onManualConnect(connectionInfo)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Connect")
            }

            // QR Scanner Button
            OutlinedButton(
                onClick = onQRScanClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.qr_code_scanner),
                    contentDescription = "Scan QR Code",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan QR Code")
            }

            // Test Mode Info
            if (testMode) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Test Mode Active",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Using test connection: test://localhost:8080",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
