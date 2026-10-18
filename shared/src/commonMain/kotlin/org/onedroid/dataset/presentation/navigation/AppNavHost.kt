package org.onedroid.dataset.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.onedroid.dataset.presentation.datasets.DatasetListRoot
import org.onedroid.dataset.presentation.detail.DatasetDetailRoot
import org.onedroid.dataset.presentation.settings.SettingsRoot

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = DatasetListRoute,
        // A short slide plus fade: forward moves in from the right, back returns from the left.
        enterTransition = { slideInHorizontally(tween(TRANSITION_MS)) { it / 4 } + fadeIn(tween(TRANSITION_MS)) },
        exitTransition = { slideOutHorizontally(tween(TRANSITION_MS)) { -it / 4 } + fadeOut(tween(TRANSITION_MS)) },
        popEnterTransition = { slideInHorizontally(tween(TRANSITION_MS)) { -it / 4 } + fadeIn(tween(TRANSITION_MS)) },
        popExitTransition = { slideOutHorizontally(tween(TRANSITION_MS)) { it / 4 } + fadeOut(tween(TRANSITION_MS)) },
    ) {
        composable<DatasetListRoute> {
            DatasetListRoot(
                onOpenDataset = { id ->
                    navController.navigate(DatasetDetailRoute(id)) { launchSingleTop = true }
                },
                onOpenSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
            )
        }
        composable<SettingsRoute> { entry ->
            SettingsRoot(
                onBack = { if (navController.currentBackStackEntry == entry) navController.navigateUp() },
            )
        }
        composable<DatasetDetailRoute> { entry ->
            DatasetDetailRoot(
                datasetId = entry.toRoute<DatasetDetailRoute>().datasetId,
                onBack = {
                    // Ignore repeated back taps while the screen is already leaving.
                    if (navController.currentBackStackEntry == entry) navController.navigateUp()
                },
            )
        }
    }
}

private const val TRANSITION_MS = 300
