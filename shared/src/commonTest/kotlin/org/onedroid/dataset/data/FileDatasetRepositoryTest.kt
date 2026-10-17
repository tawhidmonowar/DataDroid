package org.onedroid.dataset.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import org.onedroid.dataset.domain.DataError
import org.onedroid.dataset.domain.Result
import org.onedroid.dataset.data.archive.ZipReader
import org.onedroid.dataset.data.storage.DatasetMetadata
import org.onedroid.dataset.data.storage.FileDatasetRepository
import org.onedroid.dataset.data.storage.StoragePaths
import org.onedroid.dataset.domain.model.Dataset
import org.onedroid.dataset.domain.usecase.ClassNaming
import org.onedroid.dataset.domain.usecase.ImageFolders
import org.onedroid.dataset.testutil.FakeImageProcessor
import org.onedroid.dataset.testutil.fakeJpeg
import org.onedroid.dataset.testutil.fakePng
import org.onedroid.dataset.testutil.source
import org.onedroid.dataset.testutil.tempStorage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileDatasetRepositoryTest {
    private val paths = tempStorage()
    private var now = 1_000L
    private val repository = repository(paths)

    private fun repository(paths: StoragePaths) = FileDatasetRepository(paths, FakeImageProcessor(), clock = { now++ })

    private fun <T> Result<T, *>.value(): T = (this as Result.Success).data

    private suspend fun dataset(id: String): Dataset = requireNotNull(repository.observeDataset(id).first())

    @Test
    fun newDatasetHasOneStarterClass() = runTest {
        val id = repository.createDataset("Animals").value()
        val dataset = dataset(id)
        assertEquals("Animals", dataset.name)
        assertEquals(listOf("Class 1"), dataset.classes.map { it.name })
        assertEquals(1, repository.observeDatasets().first().size)
    }

    @Test
    fun duplicateDatasetNamesGetASuffix() = runTest {
        repository.createDataset("Animals").value()
        val second = repository.createDataset("animals").value()
        assertEquals("animals 2", dataset(second).name)
    }

    @Test
    fun addImagesSkipsUndecodableOnes() = runTest {
        val id = repository.createDataset("Animals").value()
        val classId = dataset(id).classes.first().id
        val result = repository.addImages(
            id,
            classId,
            listOf(source(fakeJpeg("a")), source(fakeJpeg(FakeImageProcessor.CORRUPT_MARKER)), source("not an image".encodeToByteArray())),
        ).value()

        assertEquals(1, result.added)
        assertEquals(2, result.skipped)
        val imageId = dataset(id).classes.first().imageIds.single()
        assertContentEquals(fakeJpeg("a"), repository.readImage(id, imageId).value())
        assertContentEquals("thumb:192".encodeToByteArray(), repository.readThumbnail(id, imageId).value())
    }

    @Test
    fun recordSurvivesReload() = runTest {
        val id = repository.createDataset("Animals").value()
        val classId = dataset(id).classes.first().id
        // More than one save batch, so both the periodic and the final save are exercised.
        repository.addImages(id, classId, List(30) { source(fakeJpeg("$it")) }).value()

        val reloaded = repository(paths).observeDataset(id).first()
        assertEquals(30, reloaded?.imageCount)
    }

    @Test
    fun moveAndDeleteImages() = runTest {
        val id = repository.createDataset("Animals").value()
        repository.addClass(id, "Class 2").value()
        val (first, second) = dataset(id).classes
        repository.addImages(id, first.id, listOf(source(fakeJpeg("a")), source(fakeJpeg("b")))).value()
        val (a, b) = dataset(id).classes.first().imageIds

        repository.moveImages(id, setOf(a), second.id).value()
        assertEquals(listOf(b), dataset(id).classes[0].imageIds)
        assertEquals(listOf(a), dataset(id).classes[1].imageIds)

        repository.deleteImages(id, setOf(b)).value()
        assertEquals(emptyList(), dataset(id).classes[0].imageIds)
        assertEquals(DataError.Local.NOT_FOUND, (repository.readImage(id, b) as Result.Error).error)
    }

    @Test
    fun deletingADatasetRemovesIt() = runTest {
        val id = repository.createDataset("Animals").value()
        repository.deleteDataset(id).value()
        assertNull(repository.observeDataset(id).first())
        assertTrue(repository.observeDatasets().first().isEmpty())
        assertTrue(!SystemFileSystem.exists(Path(paths.datasetsRoot, id)))
    }

    @Test
    fun importLabeledImagesMergesClassesByName() = runTest {
        val id = repository.createDataset("Animals").value()
        repository.renameClass(id, dataset(id).classes.first().id, "cat").value()
        repository.importLabeledImages(
            id,
            mapOf("Cat" to listOf(source(fakeJpeg("c1"))), "dog" to listOf(source(fakePng("d1")), source(fakePng("d2")))),
        ).value()

        val classes = dataset(id).classes
        assertEquals(listOf("cat", "dog"), classes.map { it.name })
        assertEquals(listOf(1, 2), classes.map { it.imageIds.size })
    }

    @Test
    fun exportWritesDatasetFolderWithMetadata() = runTest {
        val id = repository.createDataset("Animal Dataset").value()
        repository.addClass(id, "Class 2").value()
        val (cat, dog) = dataset(id).classes
        repository.renameClass(id, cat.id, "cat").value()
        repository.renameClass(id, dog.id, "dog").value()
        repository.addClass(id, "bird").value()
        repository.addImages(id, cat.id, listOf(source(fakeJpeg("c1")), source(fakeJpeg("c2")))).value()
        repository.addImages(id, dog.id, listOf(source(fakePng("d1")))).value()

        val file = repository.exportDataset(id).value()
        assertTrue(file.path.endsWith("Animal_Dataset.zip"))
        val entries = ZipReader.read(SystemFileSystem.source(Path(file.path)).buffered().use { it.readByteArray() })

        assertEquals(
            setOf("Animal_Dataset/cat/cat_0001.jpg", "Animal_Dataset/cat/cat_0002.jpg", "Animal_Dataset/dog/dog_0001.png", "Animal_Dataset/metadata.json"),
            entries.keys,
        )
        assertContentEquals(fakeJpeg("c2"), entries.getValue("Animal_Dataset/cat/cat_0002.jpg"))
        val metadata = Json.decodeFromString(DatasetMetadata.serializer(), entries.getValue("Animal_Dataset/metadata.json").decodeToString())
        // The empty "bird" class is left out.
        assertEquals(DatasetMetadata("Animal Dataset", listOf("cat", "dog"), 3), metadata)
    }

    @Test
    fun exportingAnEmptyDatasetFails() = runTest {
        val id = repository.createDataset("Empty").value()
        assertEquals(DataError.Archive.EMPTY_DATASET, (repository.exportDataset(id) as Result.Error).error)
    }

    @Test
    fun exportedZipImportsAsANewDataset() = runTest {
        val id = repository.createDataset("Animal Dataset").value()
        repository.addClass(id, "Class 2").value()
        val (first, second) = dataset(id).classes
        repository.addImages(id, first.id, listOf(source(fakeJpeg("a")), source(fakeJpeg("b")))).value()
        repository.addImages(id, second.id, listOf(source(fakePng("c")))).value()
        val zip = SystemFileSystem.source(Path(repository.exportDataset(id).value().path)).buffered().use { it.readByteArray() }

        val contents = repository.readArchive(zip).value()
        val groups = ImageFolders.analyze(contents.images).groups(ClassNaming.TOP_FOLDER, mergeSplits = true)
        val importedId = repository.importDataset(contents.name, groups).value()
        val imported = dataset(importedId)
        assertEquals("Animal Dataset 2", imported.name)
        assertEquals(listOf("Class_1" to 2, "Class_2" to 1), imported.classes.map { it.name to it.imageIds.size })
    }

    @Test
    fun importRejectsNonZipAndZipWithoutClassFolders() = runTest {
        assertEquals(DataError.Archive.NOT_A_ZIP, (repository.readArchive("nope".encodeToByteArray()) as Result.Error).error)

        val buffer = kotlinx.io.Buffer()
        org.onedroid.dataset.data.archive.ZipWriter(buffer).apply { add("notes.txt", "x".encodeToByteArray()); finish() }
        assertEquals(DataError.Archive.NO_IMAGES, (repository.readArchive(buffer.readByteArray()) as Result.Error).error)

        val result = repository.importDataset("Empty", emptyMap())
        assertIs<Result.Error<*>>(result)
        assertEquals(DataError.Archive.NO_IMAGES, result.error)
        assertTrue(repository.observeDatasets().first().isEmpty())
    }

    @Test
    fun zipNameComesFromItsWrapperFolderEvenWithAStrayImage() = runTest {
        val buffer = kotlinx.io.Buffer()
        org.onedroid.dataset.data.archive.ZipWriter(buffer).apply {
            add("My_Pets/cat/1.jpg", fakeJpeg("1"))
            add("My_Pets/cover.jpg", fakeJpeg("2"))
            finish()
        }
        assertEquals("My Pets", repository.readArchive(buffer.readByteArray()).value().name)
    }
}
