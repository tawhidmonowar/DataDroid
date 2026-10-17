package org.onedroid.dataset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import org.koin.compose.koinInject
import org.onedroid.dataset.presentation.components.ImageLoader
import org.onedroid.dataset.presentation.components.LocalImageLoader
import org.onedroid.dataset.presentation.navigation.AppNavHost
import org.onedroid.dataset.presentation.theme.DataDroidTheme

@Composable
fun App() {
    DataDroidTheme {
        CompositionLocalProvider(
            LocalImageLoader provides koinInject<ImageLoader>()
        ) {
            AppNavHost()
        }
    }
}
