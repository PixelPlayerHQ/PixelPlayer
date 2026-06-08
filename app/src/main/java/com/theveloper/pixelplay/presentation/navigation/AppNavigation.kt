package com.theveloper.pixelplay.presentation.navigation

import DelimiterConfigScreen
import com.theveloper.pixelplay.presentation.screens.WordDelimiterConfigScreen
import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.theveloper.pixelplay.data.preferences.LaunchTab
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.presentation.screens.AlbumDetailScreen
import com.theveloper.pixelplay.presentation.screens.AccountsScreen
import com.theveloper.pixelplay.presentation.screens.ArtistDetailScreen
import com.theveloper.pixelplay.presentation.screens.ArtistSettingsScreen
import com.theveloper.pixelplay.presentation.screens.DailyMixScreen
import com.theveloper.pixelplay.presentation.screens.EditTransitionScreen
import com.theveloper.pixelplay.presentation.screens.EasterEggScreen
import com.theveloper.pixelplay.presentation.screens.ExperimentalSettingsScreen
import com.theveloper.pixelplay.presentation.screens.GenreDetailScreen
import com.theveloper.pixelplay.presentation.screens.HomeScreen
import com.theveloper.pixelplay.presentation.screens.LibraryScreen
import com.theveloper.pixelplay.presentation.screens.MashupScreen
import com.theveloper.pixelplay.presentation.screens.NavBarCornerRadiusScreen
import com.theveloper.pixelplay.presentation.screens.PaletteStyleSettingsScreen
import com.theveloper.pixelplay.presentation.screens.PlaylistDetailScreen
import com.theveloper.pixelplay.presentation.screens.RecentlyPlayedScreen

import com.theveloper.pixelplay.presentation.screens.AboutScreen
import com.theveloper.pixelplay.presentation.screens.SearchScreen
import com.theveloper.pixelplay.presentation.screens.StatsScreen
import com.theveloper.pixelplay.presentation.screens.SettingsScreen
import com.theveloper.pixelplay.presentation.screens.SettingsCategoryScreen
import com.theveloper.pixelplay.presentation.screens.EqualizerScreen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import kotlinx.coroutines.flow.first
import com.theveloper.pixelplay.presentation.components.ScreenWrapper

                            categoryId = categoryId,
                            navController = navController,
                            playerViewModel = playerViewModel,
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                }
            }
            composable(
                Screen.PaletteStyle.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    PaletteStyleSettingsScreen(
                        playerViewModel = playerViewModel,
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }
            composable(
                Screen.Experimental.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    ExperimentalSettingsScreen(
                        navController = navController,
                        playerViewModel = playerViewModel,
                        onNavigationIconClick = { navController.popBackStack() }
                    )
                }
            }
            composable(
                Screen.DailyMixScreen.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    DailyMixScreen(
                        playerViewModel = playerViewModel,
                        navController = navController
                    )
                }
            }
                    // 1. Recently Played Screen Route
        composable(
            Screen.RecentlyPlayed.route,
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() },
            popEnterTransition = { popEnterTransition() },
            popExitTransition = { popExitTransition() },
        ) {
            val recentlyPlayedSongs by playerViewModel.recentlyPlayedSongs.collectAsStateWithLifecycle(initialValue = emptyList())
            ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                com.theveloper.pixelplay.presentation.screens.DynamicCategoryScreen(
                    title = "Recently Played",
                    baseSongs = recentlyPlayedSongs,
                    playerViewModel = playerViewModel,
                    onBackClick = { navController.popBackStack() },
                    onSongClick = { song -> 
                        playerViewModel.showAndPlaySong(song, recentlyPlayedSongs, "Recently Played") 
                    }
                )
            }
        }

                                    playerViewModel = playerViewModel
                        )
                    }
                }
            }
            composable(
                "nav_bar_corner_radius",
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    NavBarCornerRadiusScreen(navController)
                }
            }
            composable(
                route = Screen.EditTransition.route,
                arguments = listOf(navArgument("playlistId") {
                    type = NavType.StringType
                    nullable = true
                }),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    EditTransitionScreen(navController = navController)
                }
            }
            composable(
                Screen.About.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    AboutScreen(
                        navController = navController,
                        viewModel = playerViewModel,
                        onNavigationIconClick = { navController.popBackStack() }
                    )
                }
            }
            composable(
                Screen.EasterEgg.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    EasterEggScreen(
                        viewModel = playerViewModel,
                        onNavigationIconClick = { navController.popBackStack() },
                    )
                }
            }
            composable(
                Screen.ArtistSettings.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    ArtistSettingsScreen(navController = navController)
                }
            }
            composable(
                Screen.DelimiterConfig.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    DelimiterConfigScreen(navController = navController)
                }
            }
            composable(
                Screen.WordDelimiterConfig.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    WordDelimiterConfigScreen(navController = navController)
                }
            }
            composable(
                Screen.Equalizer.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    EqualizerScreen(
                        navController = navController,
                        playerViewModel = playerViewModel
                    )
                }
            }
            composable(
                Screen.DeviceCapabilities.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {

                private fun String.toRoute(): String = when (this) {
    LaunchTab.SEARCH -> Screen.Search.route
    LaunchTab.LIBRARY -> Screen.Library.route
    else -> Screen.Home.route
}

private enum class MainRootDirection {
    FORWARD,
    BACKWARD
}

// Base duration for bottom-nav switches at 1x — at 0.5x system scale = ~190 ms.
private const val BOTTOM_NAV_TRANSITION_DURATION = 380

// MD3 Expressive easing for bottom-nav switches
private val BottomNavEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private val MAIN_ROOT_TRANSITION_SPEC =
    tween<IntOffset>(durationMillis = BOTTOM_NAV_TRANSITION_DURATION, easing = BottomNavEasing)

private val MAIN_ROOT_FADE_SPEC =
    tween<Float>(durationMillis = BOTTOM_NAV_TRANSITION_DURATION / 2, easing = BottomNavEasing)

private fun mainRootDirection(
    fromRoute: String?,
    toRoute: String?
): MainRootDirection? {
    val fromIndex = mainRootRouteIndex(fromRoute) ?: return null
    val toIndex = mainRootRouteIndex(toRoute) ?: return null
    if (fromIndex == toIndex) return null
    return if (toIndex > fromIndex) MainRootDirection.FORWARD else MainRootDirection.BACKWARD
}

private fun mainRootEnterTransition(
    fromRoute: String?,
    toRoute: String?,
    fallback: EnterTransition
): EnterTransition = when (mainRootDirection(fromRoute, toRoute)) {
    MainRootDirection.FORWARD -> {
        slideInHorizontally(
            animationSpec = MAIN_ROOT_TRANSITION_SPEC,
            initialOffsetX = { (it * 0.5f).toInt() }
        ) + fadeIn(animationSpec = MAIN_ROOT_FADE_SPEC)
    }
    MainRootDirection.BACKWARD -> {
        slideInHorizontally(
            animationSpec = MAIN_ROOT_TRANSITION_SPEC,
            initialOffsetX = { -(it * 0.5f).toInt() }
        ) + fadeIn(animationSpec = MAIN_ROOT_FADE_SPEC)
    }
    null -> fallback
}

private fun mainRootExitTransition(
    fromRoute: String?,
    toRoute: String?,
    fallback: ExitTransition
): ExitTransition = when (mainRootDirection(fromRoute, toRoute)) {
    MainRootDirection.FORWARD -> {
        slideOutHorizontally(
            animationSpec = MAIN_ROOT_TRANSITION_SPEC,
            targetOffsetX = { -(it * 0.5f).toInt() }
        ) + fadeOut(animationSpec = MAIN_ROOT_FADE_SPEC)
    }
    MainRootDirection.BACKWARD -> {
        slideOutHorizontally(
            animationSpec = MAIN_ROOT_TRANSITION_SPEC,
            targetOffsetX = { (it * 0.5f).toInt() }
        ) + fadeOut(animationSpec = MAIN_ROOT_FADE_SPEC)
    }
    null -> fallback
}
