package org.onedroid.dataset.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onedroid.dataset.domain.getOrNull
import org.onedroid.dataset.domain.repository.DatasetRepository
import org.onedroid.dataset.core.platform.image.decodeFitted
import org.onedroid.dataset.core.platform.image.toImageBitmap

/**
 * Decodes dataset images for display, keeping recently shown thumbnails in memory so scrolling back
 * doesn't decode them again. Call from the main thread; decoding itself runs in the background.
 */
class ImageLoader(private val repository: DatasetRepository) {
    private val thumbnails = LinkedHashMap<String, ImageBitmap>()

    fun cachedThumbnail(datasetId: String, imageId: Long): ImageBitmap? =
        thumbnails.remove(key(datasetId, imageId))?.also { thumbnails[key(datasetId, imageId)] = it }

    suspend fun thumbnail(datasetId: String, imageId: Long): ImageBitmap? {
        cachedThumbnail(datasetId, imageId)?.let { return it }
        val bytes = repository.readThumbnail(datasetId, imageId).getOrNull() ?: return null
        val bitmap = decode(bytes, THUMBNAIL_SIZE) ?: return null
        thumbnails[key(datasetId, imageId)] = bitmap
        while (thumbnails.size > MAX_CACHED_THUMBNAILS) thumbnails.remove(thumbnails.keys.first())
        return bitmap
    }

    /** The image at up to [FULL_IMAGE_SIZE] pixels on its longer side, keeping its aspect ratio. */
    suspend fun fullImage(datasetId: String, imageId: Long): ImageBitmap? {
        val bytes = repository.readImage(datasetId, imageId).getOrNull() ?: return null
        return decode(bytes, FULL_IMAGE_SIZE)
    }

    private suspend fun decode(bytes: ByteArray, maxSide: Int): ImageBitmap? = withContext(Dispatchers.Default) {
        runCatching { decodeFitted(bytes, maxSide).toImageBitmap() }.getOrNull()
    }

    private fun key(datasetId: String, imageId: Long) = "$datasetId/$imageId"

    private companion object {
        const val THUMBNAIL_SIZE = 192
        const val FULL_IMAGE_SIZE = 1280
        /** About 22 MB of 192 px bitmaps: several screens of gallery to scroll back through. */
        const val MAX_CACHED_THUMBNAILS = 150
    }
}

/** Null in previews, where images simply show their placeholder. */
val LocalImageLoader = staticCompositionLocalOf<ImageLoader?> { null }

/** A dataset image's thumbnail, cropped to fill [size]; a tinted placeholder shows while it loads. */
@Composable
fun DatasetThumbnail(
    datasetId: String,
    imageId: Long,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    val loader = LocalImageLoader.current
    val bitmap by produceState(loader?.cachedThumbnail(datasetId, imageId), loader, datasetId, imageId) {
        if (value == null) value = loader?.thumbnail(datasetId, imageId)
    }
    Box(modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        bitmap?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}
