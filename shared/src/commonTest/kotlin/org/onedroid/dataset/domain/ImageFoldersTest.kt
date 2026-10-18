package org.onedroid.dataset.domain

import org.onedroid.dataset.domain.model.LabeledImage
import org.onedroid.dataset.domain.usecase.ClassNaming
import org.onedroid.dataset.domain.usecase.FolderLayout
import org.onedroid.dataset.domain.usecase.ImageFolders
import org.onedroid.dataset.testutil.fakeJpeg
import org.onedroid.dataset.testutil.source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageFoldersTest {

    /** Images at "a/b/name.jpg"-style paths. */
    private fun layout(vararg paths: String): FolderLayout = ImageFolders.analyze(
        paths.map { path ->
            val parts = path.split('/')
            LabeledImage(parts.dropLast(1), parts.last(), source(fakeJpeg(path)))
        }
    )

    private fun FolderLayout.classes(naming: ClassNaming = ClassNaming.TOP_FOLDER, mergeSplits: Boolean = true) =
        groups(naming, mergeSplits).mapValues { it.value.size }

    @Test
    fun flatClassFoldersNeedNoChoice() {
        val layout = layout("Animals/cat/1.jpg", "Animals/dog/2.jpg")
        assertFalse(layout.needsChoice)
        assertEquals(mapOf("cat" to 1, "dog" to 1), layout.classes())
    }

    @Test
    fun aStrayImageBesideClassFoldersIsSkippedInsteadOfMakingOneClass() {
        val layout = layout("Animals/cat/1.jpg", "Animals/dog/2.jpg", "Animals/cover.jpg", "readme.jpg")
        assertEquals(mapOf("cat" to 1, "dog" to 1), layout.classes())
        assertEquals(2, layout.looseImageCount)
        assertFalse(layout.needsChoice)
    }

    @Test
    fun nestedFoldersAreNamedAsChosen() {
        val layout = layout("zoo/mammals/cat/1.jpg", "zoo/mammals/cat/2.jpg", "zoo/mammals/dog/3.jpg", "zoo/birds/owl/4.jpg")
        assertTrue(layout.needsChoice)
        assertEquals(listOf("mammals", "cat"), layout.samplePath(mergeSplits = true))
        assertEquals(mapOf("birds" to 1, "mammals" to 3), layout.classes(ClassNaming.TOP_FOLDER))
        assertEquals(mapOf("cat" to 2, "dog" to 1, "owl" to 1), layout.classes(ClassNaming.LAST_FOLDER))
        assertEquals(
            mapOf("birds / owl" to 1, "mammals / cat" to 2, "mammals / dog" to 1),
            layout.classes(ClassNaming.FULL_PATH),
        )
    }

    @Test
    fun lastFolderMergesSameNamedFoldersAcrossBranches() {
        val layout = layout("pets/Cat/1.jpg", "wild/cat/2.jpg")
        assertEquals(mapOf("Cat" to 2), layout.classes(ClassNaming.LAST_FOLDER))
    }

    @Test
    fun fullPathKeepsTheMostSpecificFoldersWhenTooLong() {
        val layout = layout("a very long top level folder/another long folder/cat/1.jpg", "x/y/2.jpg")
        assertEquals(setOf("another long folder / cat", "x / y"), layout.classes(ClassNaming.FULL_PATH).keys)
    }

    @Test
    fun trainValTestSplitsMergeIntoTheSameClasses() {
        val layout = layout("data/train/cat/1.jpg", "data/train/dog/2.jpg", "data/Val/cat/3.jpg", "data/test/dog/4.jpg")
        assertEquals(listOf("test", "train", "Val"), layout.splitFolders)
        assertTrue(layout.needsChoice)
        assertFalse(layout.hasNesting(mergeSplits = true))
        assertEquals(mapOf("cat" to 2, "dog" to 2), layout.classes(mergeSplits = true))
        // Kept apart, the split names become the classes, or prefix them.
        assertEquals(mapOf("test" to 1, "train" to 2, "Val" to 1), layout.classes(mergeSplits = false))
        assertEquals(
            setOf("test / dog", "train / cat", "train / dog", "Val / cat"),
            layout.classes(ClassNaming.FULL_PATH, mergeSplits = false).keys,
        )
    }

    @Test
    fun foldersNamedLikeSplitsAlongsideOthersAreOrdinaryClasses() {
        val layout = layout("train/1.jpg", "cars/2.jpg")
        assertTrue(layout.splitFolders.isEmpty())
        assertEquals(mapOf("cars" to 1, "train" to 1), layout.classes())
    }
}
