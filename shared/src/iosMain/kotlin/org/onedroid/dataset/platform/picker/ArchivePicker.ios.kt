package org.onedroid.dataset.platform.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.withContext
import org.onedroid.dataset.platform.image.toByteArray
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeZIP
import platform.darwin.NSObject

@Composable
actual fun rememberArchivePicker(onPicked: (PickedFile) -> Unit): () -> Unit {
    val currentOnPicked by rememberUpdatedState(onPicked)
    // The picker holds its delegate weakly, so it must outlive each presentation.
    val delegate = remember { DocumentPickerDelegate { currentOnPicked(it) } }
    return remember {
        {
            // asCopy hands us a file in our sandbox, so no security-scoped access is needed.
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeZIP), asCopy = true)
            picker.delegate = delegate
            picker.allowsMultipleSelection = false
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class DocumentPickerDelegate(
    private val onPicked: (PickedFile) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        onPicked(
            PickedFile { maxSize ->
                withContext(Dispatchers.IO) {
                    // asCopy put a regular file in our sandbox, so its size on disk is reliable.
                    val size = url.path?.let { NSFileManager.defaultManager.attributesOfItemAtPath(it, error = null)?.get(NSFileSize) as? NSNumber }
                    require(size != null && size.longLongValue <= maxSize) { "File is too large" }
                    requireNotNull(NSData.dataWithContentsOfURL(url)) { "Could not read ${url.lastPathComponent}" }.toByteArray()
                }
            }
        )
    }
}
