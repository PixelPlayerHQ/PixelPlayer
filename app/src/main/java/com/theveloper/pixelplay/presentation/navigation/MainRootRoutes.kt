package com.theveloper.pixelplay.presentation.navigation

internal fun isMainRootRoute(route: String?): Boolean = when (route) {
    Screen.Home.route,
    Screen.Radio.route,
    Screen.Search.route,
    Screen.Playlists.route,
    Screen.Liked.route -> true
    else -> false
}

internal fun mainRootRouteIndex(route: String?): Int? = when (route) {
    Screen.Home.route -> 0
    Screen.Radio.route -> 1
    Screen.Search.route -> 2
    Screen.Playlists.route -> 3
    Screen.Liked.route -> 4
    else -> null
}
