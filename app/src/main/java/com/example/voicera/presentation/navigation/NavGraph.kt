package com.example.voicera.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.voicera.presentation.recordings.RecordingListScreen
import com.example.voicera.presentation.video.VideoScreen

/**
 * Navigation graph for the app.
 * This defines the navigation routes and destinations.
 */
@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.VideoScreen.route
    ) {
        composable(route = Screen.VideoScreen.route) {
            VideoScreen(navController = navController)
        }
        
        composable(route = Screen.RecordingListScreen.route) {
            RecordingListScreen(navController = navController)
        }
    }
}

/**
 * Sealed class representing the screens in the app.
 */
sealed class Screen(val route: String) {
    /**
     * Video recording screen.
     */
    data object VideoScreen : Screen("video_screen")
    
    /**
     * Recording list screen.
     */
    data object RecordingListScreen : Screen("recording_list_screen")
}
