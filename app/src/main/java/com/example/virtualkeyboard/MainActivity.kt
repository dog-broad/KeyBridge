package com.example.virtualkeyboard

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
import com.example.virtualkeyboard.navigation.AppNavigation
import com.example.virtualkeyboard.ui.theme.VirtualKeyboardTheme
import com.example.virtualkeyboard.viewmodel.PreferencesViewModel
import com.example.virtualkeyboard.viewmodel.PreferencesViewModelFactory
import com.example.virtualkeyboard.viewmodel.WebSocketViewModel

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
            
            VirtualKeyboardTheme(darkTheme = isDarkTheme) {
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
