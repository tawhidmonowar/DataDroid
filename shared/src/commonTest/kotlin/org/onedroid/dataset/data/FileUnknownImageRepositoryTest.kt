package org.onedroid.dataset.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.onedroid.dataset.data.storage.FileDatasetRepository
import org.onedroid.dataset.data.storage.FileUnknownImageRepository
import org.onedroid.dataset.domain.DataError
import org.onedroid.dataset.domain.Result
import org.onedroid.dataset.domain.model.Dataset
import org.onedroid.dataset.domain.repository.ImageSorter
import org.onedroid.dataset.domain.repository.ProgressListener
import org.onedroid.dataset.domain.usecase.ClassNaming
import org.onedroid.dataset.domain.usecase.ExportDataset
import org.onedroid.dataset.domain.usecase.ImageFolders
import org.onedroid.dataset.domain.usecase.KeepUnknownImages
import org.onedroid.dataset.testutil.FakeImageProcessor
import org.onedroid.dataset.testutil.fakeJpeg
import org.onedroid.dataset.testutil.source
import org.onedroid.dataset.testutil.tempStorage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileUnknownImageRepositoryTest {
    private val paths = tempStorage()
    private val datasets = FileDatasetRepository(paths, FakeImageProcessor())

    private fun <T> Result<T, *>.value(): T = (this as Result.Success).data

    private suspend fun newDataset(): String = datasets.createDataset("Unknowns").value()

    @Test
    fun keepsImagesUntilTheyAreRemoved() = runTest {
        val id = newDataset()
        val unknowns = FileUnknownImageRepository(paths)
        val first = unknowns.add(id, fakeJpeg("cup"), "cup preview".encodeToByteArray()).value()
        val second = unknowns.add(id, fakeJpeg("pen"), "pen preview".encodeToByteArray()).value()

        assertEquals(listOf(first, second), unknowns.observe(id).first().map { it.id })
        assertContentEquals(fakeJpeg("pen"), unknowns.read(id, second).value())
        assertEquals("cup preview", unknowns.observe(id).first().first().preview.decodeToString())

        unknowns.remove(id, setOf(first)).value()
        assertEquals(listOf(second), unknowns.observe(id).first().map { it.id })
        assertEquals(Result.Error(DataError.Local.NOT_FOUND), unknowns.read(id, first))
    }

    @Test
    fun imagesOutlastTheApp() = runTest {
        val id = newDataset()
        val kept = FileUnknownImageRepository(paths).add(id, fakeJpeg("cup"), "preview".encodeToByteArray()).value()

        // A new repository, as after a restart, reads them back.
        val restarted = FileUnknownImageRepository(paths)
        assertEquals(listOf(kept), restarted.observe(id).first().map { it.id })
        assertContentEquals(fakeJpeg("cup"), restarted.read(id, kept).value())
        // New ids follow on rather than reusing one.
        assertTrue(restarted.add(id, fakeJpeg("pen"), "preview".encodeToByteArray()).value() > kept)
    }

    @Test
    fun halfWrittenImagesAreCleared() = runTest {
        val id = newDataset()
        val kept = FileUnknownImageRepository(paths).add(id, fakeJpeg("cup"), "preview".encodeToByteArray()).value()
        // An add interrupted before its preview was written, and a temporary file.
        val folder = Path(paths.datasetsRoot, id, "unknown")
        write(Path(folder, "${kept + 1}.image"), fakeJpeg("pen"))
        write(Path(folder, "${kept + 2}.jpg.tmp"), fakeJpeg("tmp"))

        assertEquals(listOf(kept), FileUnknownImageRepository(paths).observe(id).first().map { it.id })
        assertEquals(setOf("$kept.image", "$kept.jpg"), SystemFileSystem.list(folder).map { it.name }.toSet())
    }

    @Test
    fun deletedDatasetsDontComeBack() = runTest {
        val id = newDataset()
        datasets.deleteDataset(id).value()
        val result = FileUnknownImageRepository(paths).add(id, fakeJpeg("cup"), "preview".encodeToByteArray())
        assertEquals(Result.Error(DataError.Local.NOT_FOUND), result)
        assertTrue(!SystemFileSystem.exists(Path(paths.datasetsRoot, id)))
    }

    @Test
    fun unknownImagesSurviveAnExportAndImport() = runTest {
        val id = newDataset()
        val classId = requireNotNull(datasets.observeDataset(id).first()).classes.single().id
        datasets.addImages(id, classId, listOf(source(fakeJpeg("cup")))).value()
        val unknowns = FileUnknownImageRepository(paths)
        unknowns.add(id, fakeJpeg("pen"), "preview".encodeToByteArray()).value()

        val zip = SystemFileSystem.source(Path(ExportDataset(datasets, unknowns)(id).value().path)).buffered().use { it.readByteArray() }
        val contents = datasets.readArchive(zip).value()
        val groups = ImageFolders.analyze(contents.images).groups(ClassNaming.TOP_FOLDER, mergeSplits = true)
        val imported = datasets.importDataset(contents.name, groups).value()
        assertEquals(1, KeepUnknownImages(PreviewingSorter, unknowns)(imported, contents.unknownImages).value())

        // The pen is back under Unknown, not in a class of its own.
        assertEquals(listOf(1), requireNotNull(datasets.observeDataset(imported).first()).classes.map { it.imageIds.size })
        val back = unknowns.observe(imported).first().single()
        assertContentEquals(fakeJpeg("pen"), unknowns.read(imported, back.id).value())
    }

    /** Only previews images, which is all keeping unknowns needs. */
    private object PreviewingSorter : ImageSorter {
        override suspend fun matcherFor(dataset: Dataset, onProgress: ProgressListener) = error("Not used")
        override suspend fun embed(image: ByteArray, saving: Boolean) = error("Not used")
        override suspend fun preview(image: ByteArray): ByteArray = "preview".encodeToByteArray()
    }

    private fun write(path: Path, bytes: ByteArray) = SystemFileSystem.sink(path).buffered().use { it.write(bytes) }
}
