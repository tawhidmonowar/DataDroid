package org.onedroid.dataset.core.platform.picker

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onedroid.dataset.domain.usecase.ImageFolders
import org.onedroid.dataset.domain.model.LabeledImage
import org.onedroid.dataset.domain.model.ImageSource

@Composable
actual fun rememberFolderPicker(onPicked: (PickedFolder) -> Unit): () -> Unit {
    val resolver = LocalContext.current.applicationContext.contentResolver
    val currentOnPicked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree == null) return@rememberLauncherForActivityResult
        currentOnPicked(PickedFolder { withContext(Dispatchers.IO) { resolver.listImages(tree) } })
    }
    return remember(launcher) { { launcher.launch(null) } }
}

private fun ContentResolver.listImages(tree: Uri): List<LabeledImage> {
    val images = mutableListOf<LabeledImage>()
    fun walk(documentId: String, folders: List<String>) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        query(children, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                if (cursor.getString(2) == Document.MIME_TYPE_DIR) {
                    if (folders.size < MAX_DEPTH && !ImageFolders.isIgnoredFolder(name)) walk(id, folders + name)
                } else if (ImageFolders.isImageFile(name)) {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    images += LabeledImage(folders, name, ImageSource {
                        withContext(Dispatchers.IO) {
                            requireNotNull(openInputStream(uri)) { "Could not open $name" }.use { it.readBytes() }
                        }
                    })
                }
            }
        }
    }
    walk(DocumentsContract.getTreeDocumentId(tree), emptyList())
    return images
}

/** Deep enough for a wrapper folder, class folders and some nesting inside them. */
private const val MAX_DEPTH = 8
