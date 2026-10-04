package org.onedroid.dataset.domain.usecase

import org.onedroid.dataset.domain.model.ImageSource
import org.onedroid.dataset.domain.model.LabeledImage

/** Turns folders of images into classes: one class per subfolder, named after it. */
object ImageFolders {
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif")

    /** Finder's "Compress" stores resource forks here, mirroring the real files' paths. */
    private const val MAC_RESOURCE_FORK_DIR = "__MACOSX"

    /** True for image files by extension, skipping hidden files like macOS's `._photo.jpg` resource forks. */
    fun isImageFile(name: String): Boolean =
        !name.startsWith(".") && name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    /** True for folders whose contents aren't dataset images: hidden folders and `__MACOSX`. */
    fun isIgnoredFolder(name: String): Boolean = name.startsWith(".") || name == MAC_RESOURCE_FORK_DIR

    /** Every usable image in [images], ordered by path, for adding a whole folder to one class. */
    fun allImages(images: List<LabeledImage>): List<ImageSource> =
        usable(images)
            .sortedBy { (it.folders + it.name).joinToString("/") }
            .map { it.source }

    /**
     * Groups [images] by class, the first folder below the root, in folder-name order. Folders that wrap
     * every class folder, as when a folder is zipped, are skipped. Images directly in the root, or in
     * ignored folders, belong to no class and are left out.
     */
    fun groupByClass(images: List<LabeledImage>): Map<String, List<ImageSource>> {
        var remaining = usable(images)
        while (remaining.isNotEmpty() && remaining.all { it.folders.size >= 2 } && remaining.map { it.folders.first() }.distinct().size == 1) {
            remaining = remaining.map { LabeledImage(it.folders.drop(1), it.name, it.source) }
        }
        // Folder names differing only in case make one class, named as the first one found.
        return remaining
            .filter { it.folders.isNotEmpty() && it.folders.first().isNotBlank() }
            .groupBy { it.folders.first().trim().lowercase() }
            .values
            .sortedBy { it.first().folders.first().trim().lowercase() }
            .associate { members -> members.first().folders.first().trim() to members.sortedBy { it.name }.map { it.source } }
    }

    private fun usable(images: List<LabeledImage>) =
        images.filter { image -> isImageFile(image.name) && image.folders.none(::isIgnoredFolder) }
}
