package com.example.virtualkeyboard.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.virtualkeyboard.ui.screens.*
import com.example.virtualkeyboard.ui.viewmodels.MainViewModel
import com.example.virtualkeyboard.ui.viewmodels.QRScannerViewModel
import com.example.virtualkeyboard.ui.viewmodels.events.MainEvent

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    mainViewModel: MainViewModel = viewModel()
) {
    val qrScannerViewModel: QRScannerViewModel = viewModel()
    val mainState by mainViewModel.state.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                state = mainState,
                onConnectClick = { navController.navigate(Screen.Connect.route) },
                onDisconnectClick = { mainViewModel.onEvent(MainEvent.Disconnect) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Connect.route) {
            ConnectScreen(
                onManualConnect = { info ->
                    mainViewModel.onEvent(MainEvent.SetConnectionInfo(info))
                    mainViewModel.onEvent(MainEvent.Connect)
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onQRScanClick = { navController.navigate(Screen.QRScanner.route) },
                onBackPressed = { navController.popBackStack() }
            )
        }

        composable(Screen.QRScanner.route) {
            QRScannerScreen(
                onQRCodeScanned = { result ->
                    mainViewModel.onEvent(MainEvent.SetConnectionInfo(result))
                    mainViewModel.onEvent(MainEvent.Connect)
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onBackPressed = { navController.popBackStack() },
                viewModel = qrScannerViewModel
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBackPressed = { navController.popBackStack() }
            )
                }

        composable(Screen.About.route) {
            AboutScreen(
                onBackPressed = { navController.popBackStack() }
            )
        }
    }
}
