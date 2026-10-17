package org.onedroid.dataset.core.platform.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.onedroid.dataset.domain.usecase.ImageFolders
import org.onedroid.dataset.domain.model.LabeledImage
import org.onedroid.dataset.domain.model.ImageSource
import org.onedroid.dataset.core.platform.image.toByteArray
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.darwin.NSObject

@Composable
actual fun rememberFolderPicker(onPicked: (PickedFolder) -> Unit): () -> Unit {
    val currentOnPicked by rememberUpdatedState(onPicked)
    // The picker holds its delegate weakly, so it must outlive each presentation.
    val delegate = remember { FolderPickerDelegate { currentOnPicked(it) } }
    return remember {
        {
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder))
            picker.delegate = delegate
            picker.allowsMultipleSelection = false
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class FolderPickerDelegate(
    private val onPicked: (PickedFolder) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val root = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        onPicked(PickedFolder { withContext(Dispatchers.IO) { root.withAccess { listImages(root) } } })
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun listImages(root: NSURL): List<LabeledImage> {
    val rootPath = requireNotNull(root.path) { "Folder has no path" }
    val subpaths = NSFileManager.defaultManager.subpathsOfDirectoryAtPath(rootPath, error = null).orEmpty()
    return subpaths.mapNotNull { subpath ->
        val parts = (subpath as String).split('/')
        val name = parts.last()
        if (!ImageFolders.isImageFile(name)) return@mapNotNull null
        val url = requireNotNull(root.URLByAppendingPathComponent(subpath))
        LabeledImage(parts.dropLast(1), name, ImageSource {
            withContext(Dispatchers.IO) {
                root.withAccess { requireNotNull(NSData.dataWithContentsOfURL(url)) { "Could not read $name" }.toByteArray() }
            }
        })
    }
}

/** Files outside the sandbox can only be read while the picked folder's security scope is open. */
private inline fun <T> NSURL.withAccess(block: () -> T): T {
    val accessing = startAccessingSecurityScopedResource()
    try {
        return block()
    } finally {
        if (accessing) stopAccessingSecurityScopedResource()
    }
}
