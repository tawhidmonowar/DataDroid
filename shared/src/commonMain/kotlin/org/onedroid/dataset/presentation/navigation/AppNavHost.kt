package org.onedroid.dataset.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.onedroid.dataset.presentation.capture.CaptureRoot
import org.onedroid.dataset.presentation.datasets.DatasetListRoot
import org.onedroid.dataset.presentation.detail.DatasetDetailRoot
import org.onedroid.dataset.presentation.settings.SettingsRoot

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = DatasetListRoute,
        // Whatever shows between two screens mid-transition is the app's background, never the window's.
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        enterTransition = { enter() },
        exitTransition = { exit() },
        popEnterTransition = { popEnter() },
        popExitTransition = { popExit() },
    ) {
        composable<DatasetListRoute> { entry ->
            DatasetListRoot(
                onOpenDataset = { id -> entry.ifResumed { navController.navigate(DatasetDetailRoute(id)) } },
                onOpenSettings = { entry.ifResumed { navController.navigate(SettingsRoute) } },
            )
        }
        composable<SettingsRoute> { entry ->
            SettingsRoot(onBack = { navController.navigateUpFrom(entry) })
        }
        composable<DatasetDetailRoute> { entry ->
            val datasetId = entry.toRoute<DatasetDetailRoute>().datasetId
            DatasetDetailRoot(
                datasetId = datasetId,
                onBack = { navController.navigateUpFrom(entry) },
                onOpenCapture = { classId -> entry.ifResumed { navController.navigate(CaptureRoute(datasetId, classId)) } },
            )
        }
        composable<CaptureRoute> { entry ->
            val route = entry.toRoute<CaptureRoute>()
            CaptureRoot(
                datasetId = route.datasetId,
                classId = route.classId,
                onBack = { navController.navigateUpFrom(entry) },
            )
        }
    }
}

/**
 * Runs [navigate] only while this destination is settled on screen. An entry is resumed once its enter
 * transition has finished and until another navigation starts, so a second tap, or a tap on a screen that's
 * already leaving, can't stack a duplicate destination.
 */
private inline fun NavBackStackEntry.ifResumed(navigate: () -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) navigate()
}

/**
 * Leaves [entry], unless it is already leaving. Unlike forward navigation this doesn't wait for the entry to
 * settle: a screen may need to close itself (its dataset was deleted) while it's still animating in.
 */
private fun NavHostController.navigateUpFrom(entry: NavBackStackEntry) {
    if (currentBackStackEntry == entry) navigateUp()
}

// The platform's own screen transition (Android 14+): the new screen slides in a short way and fades in over
// the old one, which stays opaque underneath. Going back is the reverse, so the leaving screen fades out on
// top. Only the top screen is ever translucent, so nothing shows through, and a predictive back gesture
// scrubs the same pop animation.

private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(): EnterTransition =
    slideInHorizontally(tween(SLIDE_MS, easing = EmphasizedDecelerate)) { it / SLIDE_FRACTION } +
        fadeIn(tween(FADE_MS, easing = LinearEasing))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(): ExitTransition =
    slideOutHorizontally(tween(SLIDE_MS, easing = EmphasizedDecelerate)) { -it / SLIDE_FRACTION }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
    slideInHorizontally(tween(SLIDE_MS, easing = EmphasizedDecelerate)) { -it / SLIDE_FRACTION }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    slideOutHorizontally(tween(SLIDE_MS, easing = EmphasizedDecelerate)) { it / SLIDE_FRACTION } +
        fadeOut(tween(FADE_MS, easing = LinearEasing))

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private const val SLIDE_MS = 350
private const val FADE_MS = 200

/** Screens move a tenth of their width, enough to show direction without a full-width swipe. */
private const val SLIDE_FRACTION = 10
