package com.admin.mymusicplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.admin.mymusicplayer.ui.player.PlayerScreen
import com.admin.mymusicplayer.ui.playlists.PlaylistDetailScreen
import com.admin.mymusicplayer.ui.playlists.PlaylistDetailViewModel
import com.admin.mymusicplayer.ui.playlists.PlaylistsScreen
import com.admin.mymusicplayer.ui.queue.QueueScreen
import com.admin.mymusicplayer.ui.search.SearchScreen

private data class Destination(val route: String, val label: String)

private val topLevelDestinations = listOf(
    Destination("search", "Search"),
    Destination("playlists", "Playlists"),
    Destination("queue", "Queue"),
    Destination("player", "Now Playing"),
)

@Composable
fun MusicPlayerApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = topLevelDestinations.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    MaterialTheme {
        Surface {
            Scaffold(
                bottomBar = {
                    if (showBottomBar) {
                        NavigationBar {
                            topLevelDestinations.forEach { destination ->
                                NavigationBarItem(
                                    selected = currentDestination?.hierarchy?.any {
                                        it.route == destination.route
                                    } == true,
                                    onClick = {
                                        navController.navigate(destination.route) {
                                            popUpTo("search") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Text(destination.label.take(1)) },
                                    label = { Text(destination.label) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    NavHost(navController = navController, startDestination = "search") {
                        composable("search") { SearchScreen() }
                        composable("playlists") {
                            PlaylistsScreen(onOpenPlaylist = { id ->
                                navController.navigate("playlist/$id")
                            })
                        }
                        composable("queue") { QueueScreen() }
                        composable("player") { PlayerScreen() }
                        composable(
                            route = "playlist/{playlistId}",
                            arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
                        ) { entry ->
                            val playlistId = entry.arguments?.getLong("playlistId") ?: return@composable
                            val factory = remember(playlistId) {
                                viewModelFactory {
                                    initializer { PlaylistDetailViewModel(playlistId) }
                                }
                            }
                            val detailViewModel: PlaylistDetailViewModel = viewModel(
                                key = "playlist-$playlistId",
                                factory = factory,
                            )
                            PlaylistDetailScreen(
                                viewModel = detailViewModel,
                                onBack = navController::popBackStack,
                            )
                        }
                    }
                }
            }
        }
    }
}

