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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.admin.mymusicplayer.MusicPlayerApplication
import com.admin.mymusicplayer.ui.player.PlayerScreen
import com.admin.mymusicplayer.ui.player.PlayerViewModel
import com.admin.mymusicplayer.ui.playlists.PlaylistDetailScreen
import com.admin.mymusicplayer.ui.playlists.PlaylistDetailViewModel
import com.admin.mymusicplayer.ui.playlists.PlaylistsScreen
import com.admin.mymusicplayer.ui.playlists.PlaylistsViewModel
import com.admin.mymusicplayer.ui.queue.QueueScreen
import com.admin.mymusicplayer.ui.queue.QueueViewModel
import com.admin.mymusicplayer.ui.search.SearchScreen
import com.admin.mymusicplayer.ui.search.SearchViewModel
import com.admin.mymusicplayer.ui.settings.SettingsScreen
import com.admin.mymusicplayer.ui.settings.SettingsViewModel

private data class Destination(val route: String, val label: String)

private val topLevelDestinations = listOf(
    Destination("search", "Search"),
    Destination("playlists", "Playlists"),
    Destination("queue", "Queue"),
    Destination("player", "Now Playing"),
    Destination("settings", "Settings"),
)

@Composable
fun MusicPlayerApp() {
    val application = LocalContext.current.applicationContext as MusicPlayerApplication
    val libraryRepository = application.container.libraryRepository
    val sessionRepository = application.container.sessionRepository
    val playbackClient = application.container.playbackClient
    val navController = rememberNavController()
    val searchFactory = remember(libraryRepository, sessionRepository, playbackClient) {
        viewModelFactory {
            initializer {
                SearchViewModel(
                    provider = application.container.searchProvider,
                    libraryRepository = libraryRepository,
                    sessionRepository = sessionRepository,
                    playbackController = playbackClient,
                )
            }
        }
    }
    val searchViewModel: SearchViewModel = viewModel(key = "search", factory = searchFactory)
    val shareRequest by application.shareRequests.collectAsStateWithLifecycle()
    LaunchedEffect(shareRequest?.id) {
        val request = shareRequest ?: return@LaunchedEffect
        navController.navigate("search") {
            popUpTo("search") { inclusive = false }
            launchSingleTop = true
        }
        searchViewModel.onEvent(com.admin.mymusicplayer.ui.search.SearchEvent.QueryChanged(request.text))
        searchViewModel.onEvent(com.admin.mymusicplayer.ui.search.SearchEvent.Submit)
        application.consumeShareRequest(request.id)
    }
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
                        composable("search") {
                            SearchScreen(searchViewModel)
                        }
                        composable("playlists") {
                            val factory = remember(libraryRepository) {
                                viewModelFactory {
                                    initializer {
                                        PlaylistsViewModel(
                                            libraryRepository,
                                            application.container.playlistImporter,
                                        )
                                    }
                                }
                            }
                            PlaylistsScreen(
                                onOpenPlaylist = { id -> navController.navigate("playlist/$id") },
                                viewModel = viewModel(factory = factory),
                            )
                        }
                        composable("queue") {
                            val factory = remember(sessionRepository) {
                                viewModelFactory { initializer { QueueViewModel(sessionRepository) } }
                            }
                            QueueScreen(viewModel(factory = factory))
                        }
                        composable("player") {
                            val factory = remember(playbackClient) {
                                viewModelFactory { initializer { PlayerViewModel(playbackClient) } }
                            }
                            PlayerScreen(viewModel(factory = factory))
                        }
                        composable("settings") {
                            val backupManager = application.container.backupManager
                            val factory = remember(backupManager, playbackClient) {
                                viewModelFactory {
                                    initializer {
                                        SettingsViewModel(
                                            backupManager,
                                            playbackClient,
                                            application.container.diagnostics,
                                        )
                                    }
                                }
                            }
                            SettingsScreen(viewModel(factory = factory))
                        }
                        composable(
                            route = "playlist/{playlistId}",
                            arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
                        ) { entry ->
                            val playlistId = entry.arguments?.getLong("playlistId") ?: return@composable
                            val factory = remember(playlistId, libraryRepository, playbackClient) {
                                viewModelFactory {
                                    initializer {
                                        PlaylistDetailViewModel(playlistId, libraryRepository, playbackClient)
                                    }
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
