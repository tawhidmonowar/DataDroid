package org.onedroid.dataset

import androidx.compose.ui.window.ComposeUIViewController
import org.onedroid.dataset.core.di.initKoin

fun MainViewController() = ComposeUIViewController { App() }

/** Called once from the iOS app's entry point, before any UI is shown. */
fun startDataDroid() = initKoin()
