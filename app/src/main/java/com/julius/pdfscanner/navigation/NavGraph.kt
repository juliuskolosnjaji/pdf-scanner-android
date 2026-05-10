package com.julius.pdfscanner.navigation

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.julius.pdfscanner.data.ScanMode
import com.julius.pdfscanner.ui.home.HomeScreen
import com.julius.pdfscanner.ui.preview.PreviewScreen
import com.julius.pdfscanner.ui.result.ResultScreen
import com.julius.pdfscanner.ui.scanmode.ScanModeScreen
import com.julius.pdfscanner.viewmodel.ScanViewModel

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object ScanMode : Screen("scanmode")
    object Preview : Screen("preview")
    object Result : Screen("result")
}

private const val ANIM_MS = 320

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val scanViewModel: ScanViewModel = viewModel()
    val context = LocalContext.current
    val activity = context as Activity

    // ML Kit scanner lives here so it can be launched from multiple routes
    val scanner = GmsDocumentScanning.getClient(
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(if (scanViewModel.scanMode.value == ScanMode.ID_CARD) 2 else 20)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    )

    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = GmsDocumentScanningResult
                .fromActivityResultIntent(result.data)
                ?.pages?.map { it.imageUri } ?: emptyList()
            if (uris.isNotEmpty()) {
                scanViewModel.setPages(uris)
                navController.navigate(Screen.Preview.route)
            }
        }
    }

    fun launchScanner() {
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { scannerLauncher.launch(IntentSenderRequest.Builder(it).build()) }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(ANIM_MS)) + fadeIn(tween(ANIM_MS)) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(ANIM_MS)) + fadeOut(tween(ANIM_MS)) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(ANIM_MS)) + fadeIn(tween(ANIM_MS)) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(ANIM_MS)) + fadeOut(tween(ANIM_MS)) }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = scanViewModel,
                onScanModeSelect = { navController.navigate(Screen.ScanMode.route) },
                onScanComplete = { navController.navigate(Screen.Preview.route) }
            )
        }
        composable(Screen.ScanMode.route) {
            ScanModeScreen(
                onModeSelected = { mode ->
                    scanViewModel.setScanMode(mode)
                    launchScanner()
                    navController.popBackStack()
                },
                onClose = { navController.popBackStack() }
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
