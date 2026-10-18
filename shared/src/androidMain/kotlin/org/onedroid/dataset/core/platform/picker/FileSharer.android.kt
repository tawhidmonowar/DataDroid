package org.onedroid.dataset.core.platform.picker

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

@Composable
actual fun rememberFileSharer(): FileSharer {
    val context = LocalContext.current
    return remember(context) { AndroidFileSharer(context) }
}

private class AndroidFileSharer(private val context: Context) : FileSharer {
    override fun share(path: String, mimeType: String) {
        // The file must sit under a path declared in res/xml/datadroid_export_paths.xml.
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, null)
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
