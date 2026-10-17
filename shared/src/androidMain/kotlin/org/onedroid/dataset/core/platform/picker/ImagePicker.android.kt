package org.onedroid.dataset.core.platform.picker

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onedroid.dataset.domain.model.ImageSource

@Composable
actual fun rememberImagePicker(onPicked: (List<ImageSource>) -> Unit): () -> Unit {
    val resolver = LocalContext.current.applicationContext.contentResolver
    val currentOnPicked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        currentOnPicked(
            uris.map { uri ->
                ImageSource {
                    withContext(Dispatchers.IO) {
                        requireNotNull(resolver.openInputStream(uri)) { "Could not open $uri" }.use { it.readBytes() }
                    }
                }
            }
        )
    }
    return remember(launcher) {
        { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
}
