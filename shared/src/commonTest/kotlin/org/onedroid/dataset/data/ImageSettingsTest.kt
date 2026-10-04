package org.onedroid.dataset.data

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.write
import org.onedroid.dataset.core.domain.Result
import org.onedroid.dataset.data.settings.FileSettingsRepository
import org.onedroid.dataset.data.storage.FileDatasetRepository
import org.onedroid.dataset.data.storage.StoragePaths
import org.onedroid.dataset.domain.model.Dataset
import org.onedroid.dataset.domain.model.ImageSettings
import org.onedroid.dataset.domain.model.ImportResult
import org.onedroid.dataset.domain.model.OutputFormat
import org.onedroid.dataset.domain.model.ResizeMode
import org.onedroid.dataset.domain.usecase.ImageConversions
import org.onedroid.dataset.testutil.FakeImageProcessor
import org.onedroid.dataset.testutil.fakePng
import org.onedroid.dataset.testutil.source
import org.onedroid.dataset.testutil.tempStorage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageSettingsTest {
    private val paths = tempStorage()
    private var defaults = ImageSettings()
    private val repository = repository(paths)

    private val square = ImageSettings(resize = ResizeMode.SQUARE, size = 224, format = OutputFormat.JPEG, quality = 80)

    private fun repository(paths: StoragePaths) =
        FileDatasetRepository(paths, FakeImageProcessor(), defaultImageSettings = { defaults })

    private fun <T> Result<T, *>.value(): T = (this as Result.Success).data

    private suspend fun dataset(id: String): Dataset = requireNotNull(repository.observeDataset(id).first())

    private fun imageFiles(id: String): Set<String> =
        SystemFileSystem.list(Path(paths.datasetsRoot, id, "images")).map { it.name }.toSet()

    private fun read(path: Path) = SystemFileSystem.source(path).buffered().use { it.readByteArray() }

    private fun write(path: Path, bytes: ByteArray) = SystemFileSystem.sink(path).buffered().use { it.write(bytes) }

    @Test
    fun settingsDefaultUntilSavedAndSurviveReload() = runTest {
        val settings = FileSettingsRepository(paths.settingsFile)
        assertEquals(ImageSettings(), settings.observeImageSettings().first())

        settings.saveImageSettings(square).value()

        assertEquals(square, FileSettingsRepository(paths.settingsFile).observeImageSettings().first())
    }

    @Test
    fun damagedOrOutOfRangeSettingsFallBackToSafeValues() = runTest {
        write(paths.settingsFile, "{ not json".encodeToByteArray())
        assertEquals(ImageSettings(), FileSettingsRepository(paths.settingsFile).observeImageSettings().first())

        FileSettingsRepository(paths.settingsFile).saveImageSettings(square.copy(size = 1, quality = 500)).value()
        val loaded = FileSettingsRepository(paths.settingsFile).observeImageSettings().first()
        assertEquals(ImageSettings.MIN_SIZE, loaded.size)
        assertEquals(100, loaded.quality)
    }

    @Test
    fun newDatasetsUseTheDefaultsAndConvertAddedImages() = runTest {
        defaults = square
        val id = repository.createDataset("Animals").value()
        val classId = dataset(id).classes.first().id
        repository.addImages(id, classId, listOf(source(fakePng("a")))).value()

        val dataset = dataset(id)
        assertEquals(square, dataset.imageSettings)
        assertEquals(0, dataset.outdatedImageCount)
        val imageId = dataset.classes.first().imageIds.single()
        assertEquals(setOf("$imageId.jpg"), imageFiles(id))
        assertTrue(repository.readImage(id, imageId).value().decodeToString().startsWith("converted(SQUARE:224:jpg:80):"))
    }

    @Test
    fun changingDefaultsDoesNotChangeExistingDatasets() = runTest {
        val id = repository.createDataset("Animals").value()
        defaults = square
        assertEquals(ImageSettings(), dataset(id).imageSettings)
    }

    @Test
    fun convertingReplacesOutdatedImagesAndSurvivesReload() = runTest {
        val id = repository.createDataset("Animals").value()
        val classId = dataset(id).classes.first().id
        repository.addImages(id, classId, listOf(source(fakePng("a")), source(fakePng("b")))).value()
        val (a, b) = dataset(id).classes.first().imageIds

        repository.updateImageSettings(id, square).value()
        assertEquals(2, dataset(id).outdatedImageCount)

        val progress = mutableListOf<Pair<Int, Int>>()
        assertEquals(ImportResult(added = 2, skipped = 0), repository.convertImages(id) { done, total -> progress += done to total }.value())
        assertEquals(0 to 2, progress.first())
        assertEquals(2 to 2, progress.last())
        assertEquals(0, dataset(id).outdatedImageCount)
        // The old PNG files are gone once the record points at the JPEGs.
        assertEquals(setOf("$a.jpg", "$b.jpg"), imageFiles(id))
        assertContentEquals("converted(SQUARE:224:jpg:80):".encodeToByteArray() + fakePng("a"), repository.readImage(id, a).value())

        val reloaded = repository(paths)
        assertEquals(0, reloaded.observeDataset(id).first()?.outdatedImageCount)
        assertContentEquals(repository.readImage(id, b).value(), reloaded.readImage(id, b).value())
        assertEquals(setOf("$a.jpg", "$b.jpg"), imageFiles(id))
    }

    @Test
    fun undecodableImagesAreSkippedAndKept() = runTest {
        val id = repository.createDataset("Animals").value()
        val classId = dataset(id).classes.first().id
        repository.addImages(id, classId, listOf(source(fakePng("a")))).value()
        val imageId = dataset(id).classes.first().imageIds.single()
        val file = Path(paths.datasetsRoot, id, "images", "$imageId.png")
        write(file, fakePng(FakeImageProcessor.CORRUPT_MARKER))

        repository.updateImageSettings(id, square).value()
        assertEquals(ImportResult(added = 0, skipped = 1), repository.convertImages(id).value())

        assertEquals(0, dataset(id).outdatedImageCount)
        assertContentEquals(fakePng(FakeImageProcessor.CORRUPT_MARKER), read(file))
    }

    @Test
    fun switchingBackToOriginalLeavesNothingToConvert() = runTest {
        val id = repository.createDataset("Animals").value()
        val classId = dataset(id).classes.first().id
        repository.updateImageSettings(id, square).value()
        repository.addImages(id, classId, listOf(source(fakePng("a")))).value()

        repository.updateImageSettings(id, ImageSettings()).value()

        assertEquals(0, dataset(id).outdatedImageCount)
    }

    @Test
    fun savingTheSameSettingsKeepsImagesCurrent() = runTest {
        defaults = square
        val id = repository.createDataset("Animals").value()
        repository.addImages(id, dataset(id).classes.first().id, listOf(source(fakePng("a")))).value()

        repository.updateImageSettings(id, square).value()

        assertEquals(0, dataset(id).outdatedImageCount)
    }

    @Test
    fun loadingRemovesFilesLeftByAnInterruptedConversion() = runTest {
        val id = repository.createDataset("Animals").value()
        repository.addImages(id, dataset(id).classes.first().id, listOf(source(fakePng("a")))).value()
        val imageId = dataset(id).classes.first().imageIds.single()
        val images = Path(paths.datasetsRoot, id, "images")
        write(Path(images, "$imageId.jpg"), "half-converted".encodeToByteArray())
        write(Path(images, "$imageId.png.tmp"), "partial".encodeToByteArray())

        repository(paths).observeDataset(id).first()

        assertEquals(setOf("$imageId.png"), imageFiles(id))
    }

    @Test
    fun conversionsRunInTheBackgroundAndReportTheirOutcome() = runTest {
        val id = repository.createDataset("Animals").value()
        repository.addImages(id, dataset(id).classes.first().id, listOf(source(fakePng("a")))).value()
        repository.updateImageSettings(id, square).value()
        val conversions = ImageConversions(repository, backgroundScope)

        val outcome = async(start = CoroutineStart.UNDISPATCHED) { conversions.outcomes.first() }
        conversions.start(id)
        conversions.start(id) // Already running: ignored.

        assertEquals(ImportResult(added = 1, skipped = 0), outcome.await().result.value())
        assertEquals(0, dataset(id).outdatedImageCount)
    }
}
