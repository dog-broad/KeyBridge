/*
 * Copyright 2025 Rushyendra Guntupalli (dog-broad) and Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.keybridge.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.keybridge.screens.HomeScreen
import com.keybridge.screens.ProfileScreen
import com.keybridge.screens.QRScannerScreen
import com.keybridge.viewmodel.PreferencesViewModel
import com.keybridge.viewmodel.WebSocketViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Filled.Home)
    object Profile : Screen("profile", "Settings", Icons.Filled.Person)
    object QRScanner : Screen("qr_scanner", "Scanner", Icons.Filled.QrCodeScanner)
}

@Composable
fun AppNavigation(
    preferencesViewModel: PreferencesViewModel,
    autoConnect: Boolean = false,
    lastServerUrl: String = ""
) {
    val navController = rememberNavController()
    val webSocketViewModel: WebSocketViewModel = viewModel()
    val screens = listOf(
        Screen.Home,
        Screen.QRScanner,
        Screen.Profile
    )
    
    // Auto-connect on app launch if enabled and we have a saved server URL
    LaunchedEffect(autoConnect, lastServerUrl) {
        if (autoConnect && lastServerUrl.isNotBlank()) {
            webSocketViewModel.connectToServer(lastServerUrl)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            BottomNavigationBar(
                screens = screens,
                navController = navController
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(navController, webSocketViewModel, preferencesViewModel)
            }
            composable(Screen.Profile.route) {
                ProfileScreen(navController, preferencesViewModel)
            }
            composable(Screen.QRScanner.route) {
                QRScannerScreen(navController, webSocketViewModel, preferencesViewModel)
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    screens: List<Screen>,
    navController: NavHostController
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar {
        screens.forEach { screen ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.title
                    )
                },
                label = { Text(screen.title) },
                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                onClick = {
                    navController.navigate(screen.route) {
                        // Pop up to the start destination of the graph to
                        // avoid building up a large stack of destinations
                        // on the back stack as users select items
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination when
                        // reselecting the same item
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item
                        restoreState = true
                    }
                }
            )
        }
    }
}
