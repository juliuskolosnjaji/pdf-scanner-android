package com.julius.pdfscanner.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.julius.pdfscanner.ui.home.HomeScreen
import com.julius.pdfscanner.ui.preview.PreviewScreen
import com.julius.pdfscanner.ui.result.ResultScreen
import com.julius.pdfscanner.viewmodel.ScanViewModel

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Preview : Screen("preview")
    object Result : Screen("result")
}

private const val ANIM_MS = 350

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val scanViewModel: ScanViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        enterTransition = {
            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(ANIM_MS)) +
                    fadeIn(animationSpec = tween(ANIM_MS))
        },
        exitTransition = {
            slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(ANIM_MS)) +
                    fadeOut(animationSpec = tween(ANIM_MS))
        },
        popEnterTransition = {
            slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(ANIM_MS)) +
                    fadeIn(animationSpec = tween(ANIM_MS))
        },
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(ANIM_MS)) +
                    fadeOut(animationSpec = tween(ANIM_MS))
        }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = scanViewModel,
                onScanComplete = { navController.navigate(Screen.Preview.route) }
            )
        }
        composable(Screen.Preview.route) {
            PreviewScreen(
                viewModel = scanViewModel,
                onProcess = { navController.navigate(Screen.Result.route) },
                onRetake = {
                    scanViewModel.clearPages()
                    navController.popBackStack(Screen.Home.route, false)
                }
            )
        }
        composable(Screen.Result.route) {
            ResultScreen(
                viewModel = scanViewModel,
                onDone = {
                    scanViewModel.clearPages()
                    navController.popBackStack(Screen.Home.route, false)
                }
            )
        }
    }
}
