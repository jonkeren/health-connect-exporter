package com.fozzels.healthexporter.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fozzels.healthexporter.ui.screens.DashboardScreen
import com.fozzels.healthexporter.ui.screens.HistoricalSyncScreen
import com.fozzels.healthexporter.ui.screens.PermissionsScreen
import com.fozzels.healthexporter.ui.screens.SettingsScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Filled.Dashboard)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
    object Permissions : Screen("permissions", "Permissions", Icons.Filled.Security)
}

private const val ROUTE_HISTORICAL_SYNC = "historical_sync"

val bottomNavItems = listOf(Screen.Dashboard, Screen.Settings, Screen.Permissions)

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToHistoricalSync = {
                        navController.navigate(ROUTE_HISTORICAL_SYNC)
                    }
                )
            }
            composable(Screen.Settings.route) { SettingsScreen() }
            composable(Screen.Permissions.route) { PermissionsScreen() }
            composable(ROUTE_HISTORICAL_SYNC) {
                HistoricalSyncScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
