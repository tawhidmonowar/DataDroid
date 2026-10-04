package org.onedroid.dataset.platform.picker

import androidx.compose.runtime.Composable
import org.onedroid.dataset.domain.model.ImageSource
import org.onedroid.dataset.domain.model.LabeledImage

/** A file the user picked; read lazily, failing past [maxSize] bytes. */
fun interface PickedFile {
    suspend fun readBytes(maxSize: Long): ByteArray
}

/** A folder the user picked; [images] walks it lazily and lists every image file inside. */
fun interface PickedFolder {
    suspend fun images(): List<LabeledImage>
}

/** Returns a launcher for the system photo picker, allowing several images. */
@Composable
expect fun rememberImagePicker(onPicked: (List<ImageSource>) -> Unit): () -> Unit

/** Returns a launcher for the system folder picker. */
@Composable
expect fun rememberFolderPicker(onPicked: (PickedFolder) -> Unit): () -> Unit

/** Returns a launcher for the system document picker, limited to zip archives. */
@Composable
expect fun rememberArchivePicker(onPicked: (PickedFile) -> Unit): () -> Unit
