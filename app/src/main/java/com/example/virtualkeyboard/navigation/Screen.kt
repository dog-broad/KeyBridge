package com.example.virtualkeyboard.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Connect : Screen("connect")
    object QRScanner : Screen("qr_scanner")
    object Settings : Screen("settings")
    object About : Screen("about")

    companion object {
        fun fromRoute(route: String?): Screen {
            return when (route) {
                Home.route -> Home
                Connect.route -> Connect
                QRScanner.route -> QRScanner
                Settings.route -> Settings
                About.route -> About
                null -> Home
                else -> throw IllegalArgumentException("Route $route is not recognized.")
            }
        }
    }
} 