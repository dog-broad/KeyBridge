package com.keybridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keybridge.navigation.AppNavigation
import com.keybridge.ui.theme.KeyBridgeTheme
import com.keybridge.viewmodel.PreferencesViewModel
import com.keybridge.viewmodel.PreferencesViewModelFactory
import com.keybridge.viewmodel.WebSocketViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val preferencesViewModel: PreferencesViewModel = viewModel(
                factory = PreferencesViewModelFactory(applicationContext)
            )
            val isDarkTheme by preferencesViewModel.isDarkTheme.collectAsState()
            val autoConnect by preferencesViewModel.autoConnect.collectAsState()
            val lastServerUrl by preferencesViewModel.lastServerUrl.collectAsState()
            
            KeyBridgeTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        preferencesViewModel = preferencesViewModel,
                        autoConnect = autoConnect,
                        lastServerUrl = lastServerUrl
                    )
                }
            }
        }
    }
}
