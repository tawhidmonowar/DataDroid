package org.onedroid.dataset.domain.usecase

import org.onedroid.dataset.domain.model.ImageSource
import org.onedroid.dataset.domain.model.LabeledImage

/** How a nested folder path becomes a class name. */
enum class ClassNaming {
    /** `mammals/cat/a.jpg` goes to class "mammals"; deeper folders are merged into it. */
    TOP_FOLDER,

    /** `mammals/cat/a.jpg` goes to class "cat". */
    LAST_FOLDER,

    /** `mammals/cat/a.jpg` goes to class "mammals / cat". */
    FULL_PATH,
}

/**
 * The images in a picked folder or zip, with wrapper folders removed (see [ImageFolders.analyze]). Every
 * image is inside at least one folder; [looseImageCount] images weren't, and are left out.
 */
class FolderLayout internal constructor(
    val images: List<LabeledImage>,
    /** Top-level train/validation/test folders as named in the source; empty when there are none. */
    val splitFolders: List<String>,
    val looseImageCount: Int,
) {
    val isEmpty: Boolean get() = images.isEmpty()

    /** Whether some class folder holds subfolders, so the [ClassNaming] changes the classes. */
    fun hasNesting(mergeSplits: Boolean): Boolean = images.any { classPath(it, mergeSplits).size > 1 }

    /** Whether the user should choose how folders become classes before importing. */
    val needsChoice: Boolean get() = splitFolders.isNotEmpty() || hasNesting(mergeSplits = false)

    /** The deepest folder path, to show what each [ClassNaming] would do. */
    fun samplePath(mergeSplits: Boolean): List<String> =
        images.map { classPath(it, mergeSplits) }.maxByOrNull { it.size }.orEmpty()

    /**
     * Images grouped by class name, in name order. Names differing only in case make one class, named as
     * the first one found. With [mergeSplits], images in each split folder join the same-named classes.
     */
    fun groups(naming: ClassNaming, mergeSplits: Boolean): Map<String, List<ImageSource>> =
        images
            .mapNotNull { image -> classPath(image, mergeSplits).takeIf { it.isNotEmpty() }?.let { className(it, naming) to image } }
            .groupBy { (name, _) -> name.lowercase() }
            .values
            .sortedBy { it.first().first.lowercase() }
            .associate { members -> members.first().first to members.map { it.second }.sortedBy { it.name }.map { it.source } }

    /** The folders that name [image]'s class, without its split folder when [mergeSplits]. */
    private fun classPath(image: LabeledImage, mergeSplits: Boolean): List<String> =
        if (mergeSplits && splitFolders.isNotEmpty()) image.folders.drop(1) else image.folders

    private fun className(path: List<String>, naming: ClassNaming): String = when (naming) {
        ClassNaming.TOP_FOLDER -> path.first()
        ClassNaming.LAST_FOLDER -> path.last()
        ClassNaming.FULL_PATH -> {
            // Keep the most specific folders when the whole path would be too long for a class name.
            var kept = listOf(path.last())
            for (folder in path.dropLast(1).asReversed()) {
                val longer = listOf(folder) + kept
                if (longer.joinToString(PATH_SEPARATOR).length > MAX_NAME_LENGTH) break
                kept = longer
            }
            kept.joinToString(PATH_SEPARATOR)
        }
    }.trim()

    companion object {
        /** Joins folders in [ClassNaming.FULL_PATH] names; exported folder names turn it into `_`. */
        const val PATH_SEPARATOR = " / "
    }
}

/** Turns folders of images into classes. */
object ImageFolders {
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif")

    /** Folder names that hold a split of a dataset, with the same class folders inside each. */
    private val SPLIT_NAMES = setOf("train", "training", "val", "valid", "validation", "test", "testing")

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
     * Finds the class folders in [images]. Folders that wrap everything, as when a folder is zipped, are
     * removed; images lying loose beside the class folders (a stray cover image, say) don't stop that, and
     * are left out like images in the root. Top-level folders all named like train/val/test are noted as
     * splits.
     */
    fun analyze(images: List<LabeledImage>): FolderLayout {
        var remaining = usable(images).map { it.copy(folders = it.folders.map(String::trim).filter(String::isNotEmpty)) }
        while (true) {
            val inFolders = remaining.filter { it.folders.isNotEmpty() }
            val wrapped = inFolders.map { it.folders.first() }.distinct().size == 1 && inFolders.any { it.folders.size >= 2 }
            if (!wrapped) break
            remaining = remaining.map { if (it.folders.isEmpty()) it else it.copy(folders = it.folders.drop(1)) }
        }
        val (inFolders, loose) = remaining.partition { it.folders.isNotEmpty() }
        val topFolders = inFolders.map { it.folders.first() }.distinctBy { it.lowercase() }
        val isSplit = topFolders.isNotEmpty() && topFolders.all { it.lowercase() in SPLIT_NAMES } && inFolders.any { it.folders.size >= 2 }
        return FolderLayout(inFolders, if (isSplit) topFolders.sortedBy { it.lowercase() } else emptyList(), loose.size)
    }

    private fun usable(images: List<LabeledImage>) =
        images.filter { image -> isImageFile(image.name) && image.folders.none(::isIgnoredFolder) }

    private fun LabeledImage.copy(folders: List<String>) = LabeledImage(folders, name, source)
}
