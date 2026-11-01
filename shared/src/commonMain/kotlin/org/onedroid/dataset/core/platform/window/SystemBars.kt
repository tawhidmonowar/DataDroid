package org.onedroid.dataset.core.platform.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

/** Makes the system bar icons light, for dark content behind them, while this is in the composition. */
@Composable
expect fun LightSystemBarIcons()

/** A dialog that covers the whole screen, system bars included, for full-screen viewers. */
expect fun fullScreenDialogProperties(): DialogProperties
