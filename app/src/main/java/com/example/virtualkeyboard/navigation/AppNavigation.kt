package com.example.virtualkeyboard.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.virtualkeyboard.ui.screens.ConnectScreen
import com.example.virtualkeyboard.ui.screens.KeyboardScreen
import com.example.virtualkeyboard.ui.screens.SettingsScreen

sealed class Screen(val route: String) {
    object Connect : Screen("connect")
    object Keyboard : Screen("keyboard")
    object Settings : Screen("settings")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Connect.route) {
        composable(Screen.Connect.route) {
            ConnectScreen(
                onNavigateToKeyboard = {
                    navController.navigate(Screen.Keyboard.route)
                }
            )
        }
        composable(Screen.Keyboard.route) {
            KeyboardScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
