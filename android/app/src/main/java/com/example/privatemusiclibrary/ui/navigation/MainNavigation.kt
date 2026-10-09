package com.example.privatemusiclibrary.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.privatemusiclibrary.ui.components.MiniPlayer
import com.example.privatemusiclibrary.ui.screens.*
import com.example.privatemusiclibrary.ui.screens.auth.AuthViewModel
import com.example.privatemusiclibrary.ui.screens.auth.AuthState
import com.example.privatemusiclibrary.ui.screens.auth.LoginScreen as ActualLoginScreen
import com.example.privatemusiclibrary.ui.screens.auth.RegisterScreen as ActualRegisterScreen

@Composable
fun MainNavigation(
    sharedUrl: String? = null,
    onSharedUrlHandled: () -> Unit = {},
    invitationToken: String? = null
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsState()

    LaunchedEffect(invitationToken, authState) {
        if (!invitationToken.isNullOrBlank() &&
            authState == AuthState.Unauthenticated
        ) {
            navController.navigate("register/${Uri.encode(invitationToken)}") {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(sharedUrl, authState) {
        if (sharedUrl != null && authState == AuthState.Authenticated) {
            navController.navigate(NavigationItem.Import.route) {
                popUpTo(NavigationItem.Home.route) { inclusive = false }
            }
            onSharedUrlHandled()
        }
    }

    if (authState == AuthState.Checking) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    if (authState == AuthState.Unauthenticated) {
        NavHost(navController = navController, startDestination = "login") {
            composable("login") {
                ActualLoginScreen(
                    viewModel = authViewModel,
                    onNavigateToRegister = {
                        navController.navigate("register")
                    }
                )
            }

            composable("register") {
                ActualRegisterScreen(
                    viewModel = authViewModel,
                    onNavigateToLogin = {
                        navController.navigate("login") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = "register/{token}",
                arguments = listOf(
                    navArgument("token") { type = NavType.StringType }
                )
            ) { entry ->
                val token = entry.arguments?.getString("token").orEmpty()

                ActualRegisterScreen(
                    viewModel = authViewModel,
                    onNavigateToLogin = {
                        navController.navigate("login") {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                    initialInvitationToken = token
                )
            }
        }
        return
    }

    Scaffold(
        bottomBar = {
            if (currentRoute != "player") {
                Column {
                    MiniPlayer(onClick = { navController.navigate("player") })
                    BottomNavigationBar(navController = navController)
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = NavigationItem.Home.route
            ) {
                composable(NavigationItem.Home.route) {
                    HomeScreen(
                        onNavigateToLibrary = {
                            navController.navigate(NavigationItem.Library.route)
                        },
                        onNavigateToImport = {
                            navController.navigate(NavigationItem.Import.route)
                        },
                        onNavigateToPlayer = {
                            navController.navigate("player")
                        }
                    )
                }

                composable(NavigationItem.Library.route) {
                    LibraryScreen(
                        onNavigateToPlayer = {
                            navController.navigate("player")
                        }
                    )
                }

                composable(NavigationItem.Playlists.route) {
                    PlaylistsScreen(
                        onNavigateToPlayer = {
                            navController.navigate("player")
                        },
                        onNavigateToPlaylist = { id ->
                            navController.navigate("playlist_detail/$id")
                        }
                    )
                }

                composable("playlist_detail/{playlistId}") { entry ->
                    val id = entry.arguments?.getString("playlistId")
                        ?: return@composable

                    PlaylistDetailScreen(
                        playlistId = id,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToPlayer = {
                            navController.navigate("player")
                        }
                    )
                }

                composable(NavigationItem.Import.route) {
                    ImportScreen(
                        sharedUrl = sharedUrl,
                        onSharedUrlHandled = onSharedUrlHandled
                    )
                }

                composable(NavigationItem.Settings.route) {
                    SettingsScreen(authViewModel = authViewModel)
                }

                composable("player") {
                    PlayerScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
