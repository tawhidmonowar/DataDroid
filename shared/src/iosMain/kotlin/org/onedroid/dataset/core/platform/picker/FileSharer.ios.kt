package org.onedroid.dataset.core.platform.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.popoverPresentationController

@Composable
actual fun rememberFileSharer(): FileSharer = remember {
    FileSharer { path, _ ->
        val presenter = topViewController() ?: return@FileSharer
        val sheet = UIActivityViewController(activityItems = listOf(NSURL.fileURLWithPath(path)), applicationActivities = null)
        // Required on iPad, where the sheet is a popover.
        sheet.popoverPresentationController?.sourceView = presenter.view
        presenter.presentViewController(sheet, animated = true, completion = null)
    }
}
