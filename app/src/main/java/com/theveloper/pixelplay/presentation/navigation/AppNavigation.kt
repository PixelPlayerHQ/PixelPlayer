8package com.theveloper.pixelplay.presentation.navigation

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
import androidx.navigation.NavBackStackEntry
import androidx.compose.animation.AnimatedContentTransitionScope
import com.theveloper.pixelplay.presentation.screens.DeviceCapabilitiesScreen
import kotlinx.coroutines.flow.first
import com.theveloper.pixelplay.presentation.components.ScreenWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
    [span_2](start_span)var startDestination by remember { mutableStateOf<String?>(null) }[span_2](end_span)

    LaunchedEffect(Unit) {
        startDestination = userPreferencesRepository.launchTabFlow
            .first()
            [span_3](start_span).toRoute()[span_3](end_span)
    }

    [span_4](start_span)startDestination?.let { initialRoute ->[span_4](end_span)
        NavHost(
            navController = navController,
            startDestination = initialRoute
        [span_5](start_span)) {[span_5](end_span)
            composable(
                Screen.Home.route,
                enterTransition = {
                    mainRootEnterTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = enterTransition()
                    [span_6](start_span))
                },
                exitTransition = {
                    mainRootExitTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = exitTransition()
                    )[span_6](end_span)
                },
                popEnterTransition = {
                    mainRootEnterTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = popEnterTransition()
                    [span_7](start_span))
                },
                popExitTransition = {
                    mainRootExitTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = popExitTransition()
                    )[span_7](end_span)
                },
            ) {
                [span_8](start_span)ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_8](end_span)
                    HomeScreen(
                        navController = navController, 
                        paddingValuesParent = paddingValues, 
                        playerViewModel = playerViewModel,
                        onOpenSidebar = onOpenSidebar
                    [span_9](start_span))
                }
            }
            composable(
                Screen.Search.route,
                enterTransition = {
                    mainRootEnterTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = enterTransition()
                    )[span_9](end_span)
                },
                exitTransition = {
                    mainRootExitTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = exitTransition()
                    [span_10](start_span))
                },
                popEnterTransition = {
                    mainRootEnterTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = popEnterTransition()
                    )[span_10](end_span)
                },
                popExitTransition = {
                    mainRootExitTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = popExitTransition()
                    [span_11](start_span))
                },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_11](end_span)
                    SearchScreen(
                        paddingValues = paddingValues,
                        playerViewModel = playerViewModel,
                        navController = navController,
                        onSearchBarActiveChange = onSearchBarActiveChange
                    [span_12](start_span))
                }
            }
            composable(
                Screen.Library.route,
                enterTransition = {
                    mainRootEnterTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = enterTransition()
                    )[span_12](end_span)
                },
                exitTransition = {
                    mainRootExitTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = exitTransition()
                    [span_13](start_span))
                },
                popEnterTransition = {
                    mainRootEnterTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = popEnterTransition()
                    )[span_13](end_span)
                },
                popExitTransition = {
                    mainRootExitTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = popExitTransition()
                    [span_14](start_span))
                },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_14](end_span)
                    [span_15](start_span)LibraryScreen(navController = navController, playerViewModel = playerViewModel)[span_15](end_span)
                [span_16](start_span)}
            }
            composable(
                Screen.Settings.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },[span_16](end_span)
                popExitTransition = { popExitTransition() },
            ) {
                [span_17](start_span)ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_17](end_span)
                    SettingsScreen(
                        [span_18](start_span)navController = navController,[span_18](end_span)
                        playerViewModel = playerViewModel,
                        onNavigationIconClick = {
                            navController.popBackStack()
                        [span_19](start_span)}
                    )
                }
            }
            composable(
                Screen.Accounts.route,
                enterTransition = { enterTransition() },[span_19](end_span)
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                [span_20](start_span)ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_20](end_span)
                    AccountsScreen(
                        onBackClick = { navController.popBackStack() },
                        onOpenNeteaseDashboard = {
                            navController.navigateSafely(Screen.NeteaseDashboard.route)
                        [span_21](start_span)},[span_21](end_span)
                        onOpenQqMusicDashboard = {
                            navController.navigateSafely(Screen.QqMusicDashboard.route)
                        [span_22](start_span)},[span_22](end_span)
                        onOpenNavidromeDashboard = {
                            navController.navigateSafely(Screen.NavidromeDashboard.route)
                        [span_23](start_span)},[span_23](end_span)
                        onOpenJellyfinDashboard = {
                            navController.navigateSafely(Screen.JellyfinDashboard.route)
                        [span_24](start_span)}
                    )
                }
            }[span_24](end_span)
            composable(
                route = Screen.SettingsCategory.route,
                arguments = listOf(navArgument("categoryId") { type = NavType.StringType }),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                [span_25](start_span)popEnterTransition = { popEnterTransition() },[span_25](end_span)
                popExitTransition = { popExitTransition() },
            ) { backStackEntry ->
                [span_26](start_span)ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_26](end_span)
                    [span_27](start_span)val categoryId = backStackEntry.arguments?.getString("categoryId")[span_27](end_span)
                    [span_28](start_span)if (categoryId != null) {[span_28](end_span)
                        SettingsCategoryScreen(
                            categoryId = categoryId,
                            [span_29](start_span)navController = navController,[span_29](end_span)
                            playerViewModel = playerViewModel,
                            onBackClick = { navController.popBackStack() }
                        )
                    [span_30](start_span)}
                }
            }
            composable(
                Screen.PaletteStyle.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },[span_30](end_span)
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                [span_31](start_span)ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_31](end_span)
                    [span_32](start_span)PaletteStyleSettingsScreen([span_32](end_span)
                        playerViewModel = playerViewModel,
                        onBackClick = { navController.popBackStack() }
                    )
                }
            [span_33](start_span)}
            composable(
                Screen.Experimental.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },[span_33](end_span)
            ) {
                [span_34](start_span)ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_34](end_span)
                    ExperimentalSettingsScreen(
                        navController = navController,
                        [span_35](start_span)playerViewModel = playerViewModel,[span_35](end_span)
                        onNavigationIconClick = { navController.popBackStack() }
                    )
                }
            }
            [span_36](start_span)composable([span_36](end_span)
                Screen.DailyMixScreen.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                [span_37](start_span)popExitTransition = { popExitTransition() },[span_37](end_span)
            ) {
                [span_38](start_span)ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_38](end_span)
                    DailyMixScreen(
                        playerViewModel = playerViewModel,
                        [span_39](start_span)navController = navController[span_39](end_span)
                    )
                }
            }
            
            // 1. Recently Played Screen Route
            composable(
                [span_40](start_span)Screen.RecentlyPlayed.route,[span_40](end_span)
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                [span_41](start_span)val recentlyPlayedSongs by playerViewModel.recentlyPlayedSongs.collectAsStateWithLifecycle(initialValue = emptyList())[span_41](end_span)
                [span_42](start_span)ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_42](end_span)
                    com.theveloper.pixelplay.presentation.screens.DynamicCategoryScreen(
                        title = "Recently Played",
                        baseSongs = recentlyPlayedSongs,
                        playerViewModel = playerViewModel,
                        [span_43](start_span)onBackClick = { navController.popBackStack() },[span_43](end_span)
                        onSongClick = { song -> 
                            playerViewModel.showAndPlaySong(song, recentlyPlayedSongs, "Recently Played") 
                        }
                    [span_44](start_span))
                }
            }

            // 2. Recently Added Screen Route
            composable(
                "recently_added_route",[span_44](end_span)
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                [span_45](start_span)val recentlyAddedSongs by playerViewModel.recentlyAddedSongs.collectAsStateWithLifecycle(initialValue = emptyList())[span_45](end_span)
                [span_46](start_span)ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_46](end_span)
                    com.theveloper.pixelplay.presentation.screens.DynamicCategoryScreen(
                        [span_47](start_span)title = "Recently Added",[span_47](end_span)
                        baseSongs = recentlyAddedSongs,
                        playerViewModel = playerViewModel,
                        onBackClick = { navController.popBackStack() },
                        [span_48](start_span)onSongClick = { song ->[span_48](end_span)
                            playerViewModel.showAndPlaySong(song, recentlyAddedSongs, "Recently Added") 
                        }
                    )
                }
            }

            // 3. Most Played Screen Route
            [span_49](start_span)composable([span_49](end_span)
                "most_played_route",
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                [span_50](start_span)val mostPlayedSongs by playerViewModel.mostPlayedSongs.collectAsStateWithLifecycle(initialValue = emptyList())[span_50](end_span)
                [span_51](start_span)ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_51](end_span)
                    com.theveloper.pixelplay.presentation.screens.DynamicCategoryScreen(
                        title = "Most Played",
                        baseSongs = mostPlayedSongs,
                        [span_52](start_span)playerViewModel = playerViewModel,[span_52](end_span)
                        onBackClick = { navController.popBackStack() },
                        onSongClick = { song -> 
                            playerViewModel.showAndPlaySong(song, mostPlayedSongs, "Most Played") 
                        [span_53](start_span)}
                    )
                }
            }

            // 4. Favorites Screen Route
            composable(
                "favorites_route",
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },[span_53](end_span)
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                [span_54](start_span)val favoriteSongs by playerViewModel.favoriteSongs.collectAsStateWithLifecycle(initialValue = emptyList())[span_54](end_span)
                [span_55](start_span)ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_55](end_span)
                    com.theveloper.pixelplay.presentation.screens.DynamicCategoryScreen(
                        [span_56](start_span)title = "Favorites",[span_56](end_span)
                        baseSongs = favoriteSongs,
                        playerViewModel = playerViewModel,
                        onBackClick = { navController.popBackStack() },
                        [span_57](start_span)onSongClick = { song ->[span_57](end_span)
                            playerViewModel.showAndPlaySong(song, favoriteSongs, "Favorites") 
                        }
                    )
                }
            }
                    
            [span_58](start_span)composable([span_58](end_span)
                Screen.Stats.route,
                enterTransition = { enterTransition() },
                [span_59](start_span)exitTransition = { exitTransition() },[span_59](end_span)
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                [span_60](start_span)ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_60](end_span)
                    [span_61](start_span)StatsScreen([span_61](end_span)
                        navController = navController
                    )
                }
            }
            composable(
                [span_62](start_span)route = Screen.PlaylistDetail.route,[span_62](end_span)
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                [span_63](start_span)popExitTransition = { popExitTransition() },[span_63](end_span)
            ) { backStackEntry ->
                [span_64](start_span)val playlistId = backStackEntry.arguments?.getString("playlistId")[span_64](end_span)
                [span_65](start_span)val playlistViewModel: PlaylistViewModel = hiltViewModel()[span_65](end_span)
                [span_66](start_span)if (playlistId != null) {[span_66](end_span)
                    [span_67](start_span)ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_67](end_span)
                        PlaylistDetailScreen(
                            playlistId = playlistId,
                            playerViewModel = playerViewModel,
                            [span_68](start_span)playlistViewModel = playlistViewModel,[span_68](end_span)
                            onBackClick = { navController.popBackStack() },
                            onDeletePlayListClick = { navController.popBackStack() },
                            [span_69](start_span)navController = navController[span_69](end_span)
                        )
                    }
                }
            }

            [span_70](start_span)composable([span_70](end_span)
                Screen.DJSpace.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                [span_71](start_span)popExitTransition = { popExitTransition() },[span_71](end_span)
            ) {
                [span_72](start_span)ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_72](end_span)
                    MashupScreen()
                }
            }
            composable(
                [span_73](start_span)route = Screen.GenreDetail.route,[span_73](end_span)
                arguments = listOf(navArgument("genreId") { type = NavType.StringType }),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                [span_74](start_span)popExitTransition = { popExitTransition() },[span_74](end_span)
            ) { backStackEntry ->
                [span_75](start_span)val genreId = backStackEntry.arguments?.getString("genreId")[span_75](end_span)
                [span_76](start_span)if (genreId != null) {[span_76](end_span)
                    [span_77](start_span)ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_77](end_span)
                        [span_78](start_span)GenreDetailScreen([span_78](end_span)
                            navController = navController,
                            genreId = genreId,
                            [span_79](start_span)playerViewModel = playerViewModel[span_79](end_span)
                        )
                    }
                } else {
                    [span_80](start_span)Text(stringResource(R.string.nav_error_genre_id_missing), modifier = Modifier)[span_80](end_span)
                }
            }
            composable(
                [span_81](start_span)route = Screen.AlbumDetail.route,[span_81](end_span)
                arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
                enterTransition = { enterTransition() },
                [span_82](start_span)exitTransition = { exitTransition() },[span_82](end_span)
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) { backStackEntry ->
                [span_83](start_span)val albumId = backStackEntry.arguments?.getString("albumId")[span_83](end_span)
                [span_84](start_span)if (albumId != null) {[span_84](end_span)
                    [span_85](start_span)ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_85](end_span)
                        AlbumDetailScreen(
                            albumId = albumId,
                            [span_86](start_span)navController = navController,[span_86](end_span)
                            playerViewModel = playerViewModel
                        )
                    }
                [span_87](start_span)}
            }
            composable(
                route = Screen.ArtistDetail.route,[span_87](end_span)
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
                enterTransition = { enterTransition() },
                [span_88](start_span)exitTransition = { exitTransition() },[span_88](end_span)
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) { backStackEntry ->
                [span_89](start_span)val artistId = backStackEntry.arguments?.getString("artistId")[span_89](end_span)
                [span_90](start_span)if (artistId != null) {[span_90](end_span)
                    [span_91](start_span)ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_91](end_span)
                        ArtistDetailScreen(
                            artistId = artistId,
                            [span_92](start_span)navController = navController,[span_92](end_span)
                            playerViewModel = playerViewModel
                        )
                    }
                [span_93](start_span)}
            }
            composable(
                "nav_bar_corner_radius",
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },[span_93](end_span)
                popExitTransition = { popExitTransition() },
            ) {
                [span_94](start_span)ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_94](end_span)
                    [span_95](start_span)NavBarCornerRadiusScreen(navController)[span_95](end_span)
                [span_96](start_span)}
            }
            composable(
                route = Screen.EditTransition.route,[span_96](end_span)
                arguments = listOf(navArgument("playlistId") {
                    type = NavType.StringType
                    [span_97](start_span)nullable = true[span_97](end_span)
                }),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                [span_98](start_span)popExitTransition = { popExitTransition() },[span_98](end_span)
            ) {
                [span_99](start_span)ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_99](end_span)
                    [span_100](start_span)EditTransitionScreen(navController = navController)[span_100](end_span)
                }
            }
            [span_101](start_span)composable([span_101](end_span)
                Screen.About.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            [span_102](start_span)) {[span_102](end_span)
                [span_103](start_span)ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_103](end_span)
                    AboutScreen(
                        navController = navController,
                        [span_104](start_span)viewModel = playerViewModel,[span_104](end_span)
                        onNavigationIconClick = { navController.popBackStack() }
                    )
                }
            }
            composable(
                [span_105](start_span)Screen.EasterEgg.route,[span_105](end_span)
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            [span_106](start_span)) {[span_106](end_span)
                [span_107](start_span)ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_107](end_span)
                    EasterEggScreen(
                        viewModel = playerViewModel,
                        [span_108](start_span)onNavigationIconClick = { navController.popBackStack() }[span_108](end_span)
                    )
                }
            }
            composable(
                Screen.ArtistSettings.route,
                enterTransition = { enterTransition() },
                [span_109](start_span)exitTransition = { exitTransition() },[span_109](end_span)
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                [span_110](start_span)ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_110](end_span)
                    [span_111](start_span)ArtistSettingsScreen(navController = navController)[span_111](end_span)
                }
            }
            composable(
                Screen.DelimiterConfig.route,
                enterTransition = { enterTransition() },
                [span_112](start_span)exitTransition = { exitTransition() },[span_112](end_span)
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                [span_113](start_span)ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_113](end_span)
                    [span_114](start_span)DelimiterConfigScreen(navController = navController)[span_114](end_span)
                }
            }
            composable(
                Screen.WordDelimiterConfig.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                [span_115](start_span)popEnterTransition = { popEnterTransition() },[span_115](end_span)
                popExitTransition = { popExitTransition() },
            ) {
                [span_116](start_span)ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_116](end_span)
                    [span_117](start_span)WordDelimiterConfigScreen(navController = navController)[span_117](end_span)
                }
            }
            composable(
                Screen.Equalizer.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                [span_118](start_span)popEnterTransition = { popEnterTransition() },[span_118](end_span)
                popExitTransition = { popExitTransition() },
            ) {
                [span_119](start_span)ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {[span_119](end_span)
                    EqualizerScreen(
                        [span_120](start_span)navController = navController,[span_120](end_span)
                        playerViewModel = playerViewModel
                    )
                }
            }
            composable(
                [span_121](start_span)Screen.DeviceCapabilities.route,[span_121](end_span)
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            [span_122](start_span)) {[span_122](end_span)
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    com.theveloper.pixelplay.presentation.screens.DeviceCapabilitiesScreen(
                        navController = navController,
                        playerViewModel = playerViewModel
                    )
                }
            }
        [span_123](start_span)} // Closes NavHost[span_123](end_span)
    [span_124](start_span)} // Closes startDestination?.let[span_124](end_span)
[span_125](start_span)} // Closes AppNavigation Composable[span_125](end_span)

private fun String.toRoute(): String = when (this) {
    [span_126](start_span)LaunchTab.SEARCH -> Screen.Search.route[span_126](end_span)
    LaunchTab.LIBRARY -> Screen.Library.route
    else -> Screen.Home.route
}

private enum class MainRootDirection {
    FORWARD,
    BACKWARD
}

[span_127](start_span)private const val BOTTOM_NAV_TRANSITION_DURATION = 380[span_127](end_span)

[span_128](start_span)private val BottomNavEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)[span_128](end_span)

private val MAIN_ROOT_TRANSITION_SPEC =
    [span_129](start_span)tween<IntOffset>(durationMillis = BOTTOM_NAV_TRANSITION_DURATION, easing = BottomNavEasing)[span_129](end_span)

private val MAIN_ROOT_FADE_SPEC =
    [span_130](start_span)tween<Float>(durationMillis = BOTTOM_NAV_TRANSITION_DURATION / 2, easing = BottomNavEasing)[span_130](end_span)

private fun mainRootDirection(
    fromRoute: String?,
    toRoute: String?
[span_131](start_span)): MainRootDirection?[span_131](end_span)
{
    [span_132](start_span)val fromIndex = mainRootRouteIndex(fromRoute) ?: return null[span_132](end_span)
    val toIndex = mainRootRouteIndex(toRoute) ?: return null
    if (fromIndex == toIndex) return null
    return if (toIndex > fromIndex) MainRootDirection.FORWARD else MainRootDirection.BACKWARD
}

private fun mainRootEnterTransition(
    fromRoute: String?,
    toRoute: String?,
    fallback: EnterTransition
[span_133](start_span)): EnterTransition = when (mainRootDirection(fromRoute, toRoute)) {[span_133](end_span)
    MainRootDirection.FORWARD -> {
        slideInHorizontally(
            animationSpec = MAIN_ROOT_TRANSITION_SPEC,
            [span_134](start_span)initialOffsetX = { (it * 0.5f).toInt() }[span_134](end_span)
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
    [span_135](start_span)fromRoute: String?,[span_135](end_span)
    toRoute: String?,
    fallback: ExitTransition
[span_136](start_span)): ExitTransition = when (mainRootDirection(fromRoute, toRoute)) {[span_136](end_span)
    MainRootDirection.FORWARD -> {
        slideOutHorizontally(
            animationSpec = MAIN_ROOT_TRANSITION_SPEC,
            targetOffsetX = { -(it * 0.5f).toInt() }
        ) + fadeOut(animationSpec = MAIN_ROOT_FADE_SPEC)
    }
    MainRootDirection.BACKWARD -> {
        slideOutHorizontally(
            [span_137](start_span)animationSpec = MAIN_ROOT_TRANSITION_SPEC,[span_137](end_span)
            targetOffsetX = { (it * 0.5f).toInt() }
        ) + fadeOut(animationSpec = MAIN_ROOT_FADE_SPEC)
    }
    null -> fallback
}
