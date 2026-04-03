package com.theveloper.pixelplay.presentation.components.scoped

import com.theveloper.pixelplay.presentation.navigation.navigateSafely

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavHostController
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun PlayerArtistNavigationEffect(
    navController: NavHostController,
    sheetCollapsedTargetY: Float,
    sheetMotionController: SheetMotionController,
    playerViewModel: PlayerViewModel
) {
    val latestSheetCollapsedTargetY by rememberUpdatedState(sheetCollapsedTargetY)
    LaunchedEffect(navController) {
        playerViewModel.artistNavigationRequests.collectLatest { artistId ->
            sheetMotionController.snapCollapsed(latestSheetCollapsedTargetY)
            playerViewModel.collapsePlayerSheet()

            navController.navigateSafely(Screen.ArtistDetail.createRoute(artistId)) {
                // Allow navigating from one artist detail to another by replacing
                // the current instance instead of blocking with launchSingleTop.
                launchSingleTop = false
                // Pop the existing ArtistDetail (if any) so screens don't stack.
                navController.currentBackStackEntry?.destination?.route?.let { currentRoute ->
                    if (currentRoute == Screen.ArtistDetail.route) {
                        popUpTo(Screen.ArtistDetail.route) { inclusive = true }
                    }
                }
            }
        }
    }
}
