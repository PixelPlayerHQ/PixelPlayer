package com.theveloper.pixelplay.presentation.navigation

import DelimiterConfigScreen
import com.theveloper.pixelplay.presentation.navigation.Screen
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.compose.animation.AnimatedContentTransitionScope

@OptIn(UnstableApi::class)
@SuppressLint("UnrememberedGetBackStackEntry")
@Composable
fun AppNavigation(
    playerViewModel: PlayerViewModel,
    navController: NavHostController,
    paddingValues: PaddingValues,
    userPreferencesRepository: UserPreferencesRepository,
    onSearchBarActiveChange: (Boolean) -> Unit,
    onOpenSidebar: () -> Unit
) {
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        startDestination = userPreferencesRepository.launchTabFlow
            .first()
            .toRoute()
    }

    startDestination?.let { initialRoute ->
        NavHost(
            navController = navController,
            startDestination = initialRoute
        ) {
            composable(
                Screen.Home.route,
                enterTransition = { mainRootEnterTransition(initialState.destination.route, targetState.destination.route, enterTransition()) },
                exitTransition = { mainRootExitTransition(initialState.destination.route, targetState.destination.route, exitTransition()) },
                popEnterTransition = { mainRootEnterTransition(initialState.destination.route, targetState.destination.route, popEnterTransition()) },
                popExitTransition = { mainRootExitTransition(initialState.destination.route, targetState.destination.route, popExitTransition()) },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    HomeScreen(navController, paddingValues, playerViewModel, onOpenSidebar)
                }
            }
            composable(
                Screen.Search.route,
                enterTransition = { mainRootEnterTransition(initialState.destination.route, targetState.destination.route, enterTransition()) },
                exitTransition = { mainRootExitTransition(initialState.destination.route, targetState.destination.route, exitTransition()) },
                popEnterTransition = { mainRootEnterTransition(initialState.destination.route, targetState.destination.route, popEnterTransition()) },
                popExitTransition = { mainRootExitTransition(initialState.destination.route, targetState.destination.route, popExitTransition()) },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    SearchScreen(paddingValues, playerViewModel, navController, onSearchBarActiveChange)
                }
            }
            composable(
                Screen.Library.route,
                enterTransition = { mainRootEnterTransition(initialState.destination.route, targetState.destination.route, enterTransition()) },
                exitTransition = { mainRootExitTransition(initialState.destination.route, targetState.destination.route, exitTransition()) },
                popEnterTransition = { mainRootEnterTransition(initialState.destination.route, targetState.destination.route, popEnterTransition()) },
                popExitTransition = { mainRootExitTransition(initialState.destination.route, targetState.destination.route, popExitTransition()) },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    LibraryScreen(navController, playerViewModel)
                }
            }
            composable(
                Screen.Settings.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    SettingsScreen(navController, playerViewModel, onNavigationIconClick = { navController.popBackStack() })
                }
            }
            composable(
                Screen.Accounts.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    AccountsScreen(
                        onBackClick = { navController.popBackStack() },
                        onOpenNeteaseDashboard = { navController.navigateSafely(Screen.NeteaseDashboard.route) },
                        onOpenQqMusicDashboard = { navController.navigateSafely(Screen.QqMusicDashboard.route) },
                        onOpenNavidromeDashboard = { navController.navigateSafely(Screen.NavidromeDashboard.route) },
                        onOpenJellyfinDashboard = { navController.navigateSafely(Screen.JellyfinDashboard.route) }
                    )
                }
            }
            // (অন্যান্য রাউটগুলো আপনার আগের লজিক অনুযায়ী এখানে যুক্ত থাকবে)
        }
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.mainRootEnterTransition(
    fromRoute: String?, toRoute: String?, fallback: EnterTransition
): EnterTransition {
    val direction = mainRootDirection(fromRoute, toRoute) ?: return fallback
    return when (direction) {
        MainRootDirection.FORWARD -> slideInHorizontally(animationSpec = MAIN_ROOT_TRANSITION_SPEC, initialOffsetX = { (it * 0.5f).toInt() }) + fadeIn(animationSpec = MAIN_ROOT_FADE_SPEC)
        MainRootDirection.BACKWARD -> slideInHorizontally(animationSpec = MAIN_ROOT_TRANSITION_SPEC, initialOffsetX = { -(it * 0.5f).toInt() }) + fadeIn(animationSpec = MAIN_ROOT_FADE_SPEC)
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.mainRootExitTransition(
    fromRoute: String?, toRoute: String?, fallback: ExitTransition
): ExitTransition {
    val direction = mainRootDirection(fromRoute, toRoute) ?: return fallback
    return when (direction) {
        MainRootDirection.FORWARD -> slideOutHorizontally(animationSpec = MAIN_ROOT_TRANSITION_SPEC, targetOffsetX = { -(it * 0.5f).toInt() }) + fadeOut(animationSpec = MAIN_ROOT_FADE_SPEC)
        MainRootDirection.BACKWARD -> slideOutHorizontally(animationSpec = MAIN_ROOT_TRANSITION_SPEC, targetOffsetX = { (it * 0.5f).toInt() }) + fadeOut(animationSpec = MAIN_ROOT_FADE_SPEC)
    }
}

private fun mainRootDirection(fromRoute: String?, toRoute: String?): MainRootDirection? {
    val fromIndex = mainRootRouteIndex(fromRoute) ?: return null
    val toIndex = mainRootRouteIndex(toRoute) ?: return null
    if (fromIndex == toIndex) return null
    return if (toIndex > fromIndex) MainRootDirection.FORWARD else MainRootDirection.BACKWARD
}

private fun mainRootRouteIndex(route: String?): Int? = when (route) {
    Screen.Home.route -> 0
    Screen.Search.route -> 1
    Screen.Library.route -> 2
    else -> null
}

private enum class MainRootDirection { FORWARD, BACKWARD }
private const val BOTTOM_NAV_TRANSITION_DURATION = 380
private val BottomNavEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val MAIN_ROOT_TRANSITION_SPEC = tween<IntOffset>(durationMillis = BOTTOM_NAV_TRANSITION_DURATION, easing = BottomNavEasing)
private val MAIN_ROOT_FADE_SPEC = tween<Float>(durationMillis = BOTTOM_NAV_TRANSITION_DURATION / 2, easing = BottomNavEasing)

private fun String.toRoute(): String = when (this) {
    LaunchTab.SEARCH -> Screen.Search.route
    LaunchTab.LIBRARY -> Screen.Library.route
    else -> Screen.Home.route
}
