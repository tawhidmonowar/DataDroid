package org.onedroid.dataset.core.platform.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.DialogProperties

/** iOS picks the status bar's style from the content behind it. */
@Composable
actual fun LightSystemBarIcons() = Unit

@OptIn(ExperimentalComposeUiApi::class)
actual fun fullScreenDialogProperties() = DialogProperties(usePlatformDefaultWidth = false, usePlatformInsets = false)
