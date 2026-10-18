package org.onedroid.dataset.core.platform.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.suspendCancellableCoroutine
import org.onedroid.dataset.domain.model.ImageSource
import org.onedroid.dataset.core.platform.image.toByteArray
import platform.Foundation.NSItemProvider
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
actual fun rememberImagePicker(onPicked: (List<ImageSource>) -> Unit): () -> Unit {
    val currentOnPicked by rememberUpdatedState(onPicked)
    // The picker holds its delegate weakly, so it must outlive each presentation.
    val delegate = remember { PickerDelegate { currentOnPicked(it) } }
    return remember {
        {
            val configuration = PHPickerConfiguration().apply {
                selectionLimit = 0
                filter = PHPickerFilter.imagesFilter
            }
            val picker = PHPickerViewController(configuration = configuration)
            picker.delegate = delegate
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class PickerDelegate(
    private val onPicked: (List<ImageSource>) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val providers = didFinishPicking.map { (it as PHPickerResult).itemProvider }
        if (providers.isEmpty()) return
        onPicked(providers.map { provider -> ImageSource { provider.loadImageBytes() } })
    }
}

private suspend fun NSItemProvider.loadImageBytes(): ByteArray = suspendCancellableCoroutine { continuation ->
    loadDataRepresentationForTypeIdentifier("public.image") { data, error ->
        if (data != null) {
            continuation.resume(data.toByteArray())
        } else {
            continuation.resumeWithException(IllegalStateException(error?.localizedDescription ?: "Could not load image"))
        }
    }
}
