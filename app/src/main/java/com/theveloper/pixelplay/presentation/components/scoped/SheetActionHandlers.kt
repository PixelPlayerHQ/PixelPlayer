package com.theveloper.pixelplay.presentation.components.scoped

import com.theveloper.pixelplay.presentation.navigation.navigateSafely

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class SheetActionHandlers(
    val openQueueSheet: () -> Unit,
    val animateQueueSheet: (Boolean) -> Unit,
    val beginQueueDrag: () -> Unit,
    val dragQueueBy: (Float) -> Unit,
    val endQueueDrag: (Float, Float) -> Unit,
    val onSelectedSongForInfoChange: (Song?) -> Unit,
    val onLaunchSaveQueueOverlay: (List<Song>, String, (String, Set<String>) -> Unit) -> Unit,
    val onNavigateToAlbum: (Song) -> Unit,
    val onNavigateToArtist: (Song) -> Unit,
    val onNavigateToGenre: (Song) -> Unit
)

@OptIn(UnstableApi::class)
@Composable
internal fun rememberSheetActionHandlers(
    scope: CoroutineScope,
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    sheetMotionController: SheetMotionController,
    queueSheetController: QueueSheetController,
    sheetModalOverlayController: SheetModalOverlayController,
    sheetCollapsedTargetY: Float
): SheetActionHandlers {
    val queueSheetControllerState = rememberUpdatedState(queueSheetController)
    val sheetModalOverlayControllerState = rememberUpdatedState(sheetModalOverlayController)
    val sheetMotionControllerState = rememberUpdatedState(sheetMotionController)
    val sheetCollapsedTargetYState = rememberUpdatedState(sheetCollapsedTargetY)
    val playerViewModelState = rememberUpdatedState(playerViewModel)

    val openQueueSheet = remember {
        { queueSheetControllerState.value.animate(true) }
    }
    val animateQueueSheet = remember {
        { expanded: Boolean -> queueSheetControllerState.value.animate(expanded) }
    }
    val beginQueueDrag = remember {
        { queueSheetControllerState.value.beginDrag() }
    }
    val dragQueueBy = remember {
        { dragAmount: Float -> queueSheetControllerState.value.dragBy(dragAmount) }
    }
    val endQueueDrag = remember {
        { totalDrag: Float, velocity: Float ->
            queueSheetControllerState.value.endDrag(totalDrag, velocity)
        }
    }
    val onSelectedSongForInfoChange = remember {
        { song: Song? -> sheetModalOverlayControllerState.value.updateSelectedSongForInfo(song) }
    }
    val onLaunchSaveQueueOverlay = remember {
        { songs: List<Song>, defaultName: String, onConfirm: (String, Set<String>) -> Unit ->
            sheetModalOverlayControllerState.value.launchSaveQueueOverlay(
                songs = songs,
                defaultName = defaultName,
                onConfirm = onConfirm
            )
        }
    }
    val onNavigateToAlbum = remember(scope, navController) {
        { song: Song ->
            scope.launch {
                sheetMotionControllerState.value.snapCollapsed(sheetCollapsedTargetYState.value)
            }
            playerViewModelState.value.collapsePlayerSheet()
            queueSheetControllerState.value.animate(false)
            sheetModalOverlayControllerState.value.updateSelectedSongForInfo(null)
            if (song.albumId != -1L) {
                navController.navigateSafely(Screen.AlbumDetail.createRoute(song.albumId))
            }
        }
    }
    val onNavigateToArtist = remember(scope, navController) {
        { song: Song ->
            scope.launch {
                sheetMotionControllerState.value.snapCollapsed(sheetCollapsedTargetYState.value)
            }
            playerViewModelState.value.collapsePlayerSheet()
            queueSheetControllerState.value.animate(false)
            sheetModalOverlayControllerState.value.updateSelectedSongForInfo(null)
            if (song.artistId != -1L) {
                navController.navigateSafely(Screen.ArtistDetail.createRoute(song.artistId))
            }
        }
    }
    val onNavigateToGenre = remember(scope, navController) {
        { song: Song ->
            scope.launch {
                sheetMotionControllerState.value.snapCollapsed(sheetCollapsedTargetYState.value)
            }
            playerViewModelState.value.collapsePlayerSheet()
            queueSheetControllerState.value.animate(false)
            sheetModalOverlayControllerState.value.updateSelectedSongForInfo(null)
            if (!song.genre.isNullOrEmpty()) {
                val encodedGenre = java.net.URLEncoder.encode(song.genre, "UTF-8")
                navController.navigateSafely(Screen.GenreDetail.createRoute(encodedGenre))
            }
        }
    }

    return SheetActionHandlers(
        openQueueSheet = openQueueSheet,
        animateQueueSheet = animateQueueSheet,
        beginQueueDrag = beginQueueDrag,
        dragQueueBy = dragQueueBy,
        endQueueDrag = endQueueDrag,
        onSelectedSongForInfoChange = onSelectedSongForInfoChange,
        onLaunchSaveQueueOverlay = onLaunchSaveQueueOverlay,
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToArtist = onNavigateToArtist,
        onNavigateToGenre = onNavigateToGenre
    )
}
