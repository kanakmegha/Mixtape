package com.example.privatemusiclibrary.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.privatemusiclibrary.ui.screens.*
import com.example.privatemusiclibrary.ui.components.MiniPlayer

@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    
    Scaffold(
        bottomBar = {
            Column {
                MiniPlayer(onClick = { navController.navigate("player") })
                BottomNavigationBar(navController = navController)
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(navController = navController, startDestination = NavigationItem.Home.route) {
                composable("login") { LoginScreen() }
                composable("register") { RegisterScreen() }
                composable(NavigationItem.Home.route) { HomeScreen(
                    onNavigateToLibrary = { navController.navigate(NavigationItem.Library.route) },
                    onNavigateToImport = { navController.navigate(NavigationItem.Import.route) }
                ) }
                composable(NavigationItem.Library.route) { LibraryScreen() }
                composable(NavigationItem.Import.route) { ImportScreen() }
                composable(NavigationItem.Settings.route) { SettingsScreen() }
                composable("player") { 
                    PlayerScreen(onNavigateBack = { navController.popBackStack() }) 
                }
            }
        }
    }
}
