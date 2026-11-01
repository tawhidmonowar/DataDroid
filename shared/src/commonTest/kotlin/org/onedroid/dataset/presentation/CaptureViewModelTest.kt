package org.onedroid.dataset.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.onedroid.dataset.core.platform.image.RgbImage
import org.onedroid.dataset.core.platform.ml.Backbone
import org.onedroid.dataset.core.platform.ml.BackboneLoader
import org.onedroid.dataset.core.platform.ml.MobileNetV3Small
import org.onedroid.dataset.core.platform.ml.SharedBackbone
import org.onedroid.dataset.data.settings.FileSettingsRepository
import org.onedroid.dataset.data.sorting.EmbeddingImageSorter
import org.onedroid.dataset.data.storage.FileDatasetRepository
import org.onedroid.dataset.data.storage.FileUnknownImageRepository
import org.onedroid.dataset.domain.Result
import org.onedroid.dataset.domain.usecase.ExportDataset
import org.onedroid.dataset.domain.usecase.FileUnknownImages
import org.onedroid.dataset.domain.usecase.KeepUnknownImages
import org.onedroid.dataset.domain.usecase.ImageConversions
import org.onedroid.dataset.presentation.capture.CaptureAction
import org.onedroid.dataset.presentation.capture.CaptureState
import org.onedroid.dataset.presentation.capture.CaptureTarget
import org.onedroid.dataset.presentation.capture.RecentCapture
import org.onedroid.dataset.presentation.capture.CaptureViewModel
import org.onedroid.dataset.presentation.detail.DatasetDetailAction
import org.onedroid.dataset.presentation.detail.DatasetDetailState
import org.onedroid.dataset.presentation.detail.DatasetDetailViewModel
import org.onedroid.dataset.testutil.FakeImageProcessor
import org.onedroid.dataset.testutil.fakeJpeg
import org.onedroid.dataset.testutil.source
import org.onedroid.dataset.testutil.tempStorage
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {
    private val paths = tempStorage()
    private val repository = FileDatasetRepository(paths, FakeImageProcessor())
    private val settings = FileSettingsRepository(paths.settingsFile)
    private val conversions = ImageConversions(repository)
    private val viewModels = mutableListOf<ViewModel>()

    /** "Decodes" a fake JPEG to a solid square whose color is named in its bytes. */
    private val decode = { bytes: ByteArray, size: Int ->
        val text = bytes.decodeToString()
        val color = when {
            "red" in text -> 0xFFFF0000.toInt()
            "blue" in text -> 0xFF0000FF.toInt()
            "green" in text -> 0xFF00FF00.toInt()
            else -> throw IllegalArgumentException("Unsupported image format")
        }
        RgbImage(size, size, IntArray(size * size) { color })
    }

    /** Embeds an image as its mean color, padded to the real embedding size. */
    private val backbone = object : Backbone {
        override fun embed(input: FloatArray): FloatArray {
            val out = FloatArray(MobileNetV3Small.EMBEDDING_SIZE)
            for (i in input.indices) out[i % 3] += input[i]
            return out
        }

        override fun close() = Unit
    }

    private val sorter = EmbeddingImageSorter(
        paths = paths,
        repository = repository,
        backbone = SharedBackbone(BackboneLoader { backbone }, CoroutineScope(SupervisorJob() + Dispatchers.Default), readModel = { ByteArray(0) }),
        imageProcessor = FakeImageProcessor(),
        decode = decode,
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /**
     * Runs [block], then stops every view model it made and waits for them, so none is still finishing work
     * on the main dispatcher when the next test replaces it.
     */
    private fun viewModelTest(block: suspend TestScope.() -> Unit) = runTest {
        try {
            block()
        } finally {
            viewModels.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() }
        }
    }

    /** A capture screen with its own unknown image store, as the app has after a restart. */
    private fun capture(datasetId: String, classId: Int? = null): CaptureViewModel {
        val unknownImages = FileUnknownImageRepository(paths)
        return CaptureViewModel(
            datasetId = datasetId,
            initialClassId = classId,
            repository = repository,
            conversions = conversions,
            sorter = sorter,
            unknownImages = unknownImages,
            fileUnknowns = FileUnknownImages(repository, unknownImages),
        ).also { viewModels += it }
    }

    private fun overview(datasetId: String): DatasetDetailViewModel {
        val unknownImages = FileUnknownImageRepository(paths)
        return DatasetDetailViewModel(
            datasetId = datasetId,
            repository = repository,
            settingsRepository = settings,
            conversions = conversions,
            unknownImages = unknownImages,
            fileUnknowns = FileUnknownImages(repository, unknownImages),
            exportDataset = ExportDataset(repository, unknownImages),
            keepUnknownImages = KeepUnknownImages(sorter, unknownImages),
            appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        ).also { viewModels += it }
    }

    private fun <T> Result<T, *>.value(): T = (this as Result.Success).data

    private suspend fun CaptureViewModel.awaitState(predicate: (CaptureState) -> Boolean) = state.first(predicate)

    private suspend fun DatasetDetailViewModel.awaitState(predicate: (DatasetDetailState) -> Boolean) = state.first(predicate)

    /** A dataset with a "red" class and a "blue" class, three images each; returns its id and red's id. */
    private suspend fun redAndBlue(): Pair<String, Int> {
        val id = repository.createDataset("Colors").value()
        val red = requireNotNull(repository.observeDataset(id).first()).classes.single().id
        val blue = repository.addClass(id, "Blue").value()
        repository.addImages(id, red, List(3) { source(fakeJpeg("red$it")) })
        repository.addImages(id, blue, List(3) { source(fakeJpeg("blue$it")) })
        return id to red
    }

    @Test
    fun unknownImagesOutlastTheCameraAndTheApp() = viewModelTest {
        val (id, red) = redAndBlue()
        val camera = capture(id)
        camera.awaitState { it.autoSort.ready }

        // A tapped shot of something like no class waits under Unknown.
        camera.onAction(CaptureAction.OnStartRecording)
        camera.onAction(CaptureAction.OnCapture(fakeJpeg("green")))
        val held = camera.awaitState { it.unknowns.size == 1 && it.recent.isNotEmpty() }
        // It shows among the recent captures too, marked unknown.
        assertEquals(listOf(held.unknowns.single().id), held.recent.map { (it as RecentCapture.Unknown).image.id })

        // Leaving the camera keeps it, and so does a restart; the dataset screen shows it too.
        camera.viewModelScope.coroutineContext.job.cancelAndJoin()
        val dataset = overview(id)
        dataset.awaitState { it.unknowns.size == 1 }

        // Filing it moves it into the class and off the list.
        dataset.onAction(DatasetDetailAction.OnUnknownsToClass(red))
        val filed = dataset.awaitState { it.unknowns.isEmpty() && it.transfer == null }
        assertEquals(4, filed.classes.first { it.id == red }.imageIds.size)
    }

    @Test
    fun autoSortSavesCapturesToTheClassTheyLookLike() = viewModelTest {
        val (id, red) = redAndBlue()
        val camera = capture(id)
        assertEquals(CaptureTarget.Auto, camera.awaitState { it.autoSort.ready }.target)

        camera.onAction(CaptureAction.OnStartRecording)
        camera.onAction(CaptureAction.OnCapture(fakeJpeg("red-new")))
        // Counted once it's saved, which the class shows first.
        val saved = camera.awaitState { state -> state.classes.first { it.id == red }.imageIds.size == 4 && state.saved.isNotEmpty() }
        assertEquals(emptyList(), saved.unknowns)
        assertEquals(mapOf(red to 1), saved.saved)
        // Only the new image counts as recent, not the ones the dataset already had.
        val newest = saved.classes.first { it.id == red }.imageIds.last()
        assertEquals(listOf(RecentCapture.Saved(newest, red)), camera.awaitState { it.recent.isNotEmpty() }.recent)
    }

    @Test
    fun aChosenClassGetsEveryCapture() = viewModelTest {
        val (id, red) = redAndBlue()
        val camera = capture(id, classId = red)
        camera.awaitState { !it.loading }

        // Even an image that looks like no class goes where the user chose.
        camera.onAction(CaptureAction.OnStartRecording)
        camera.onAction(CaptureAction.OnCapture(fakeJpeg("green")))
        camera.awaitState { state -> state.classes.first { it.id == red }.imageIds.size == 4 }
        assertEquals(emptyList(), camera.state.value.unknowns)
    }

    @Test
    fun aNewDatasetStartsWithItsFirstClass() = viewModelTest {
        val id = repository.createDataset("Empty").value()
        val first = requireNotNull(repository.observeDataset(id).first()).classes.single().id
        assertEquals(CaptureTarget.Class(first), capture(id).awaitState { !it.loading }.target)
    }

    @Test
    fun deletedImagesCanBeBroughtBack() = viewModelTest {
        val (id, red) = redAndBlue()
        val dataset = overview(id)
        val image = dataset.awaitState { !it.loading }.classes.first { it.id == red }.imageIds.first()

        dataset.onAction(DatasetDetailAction.OnDeleteImages(setOf(image)))
        dataset.awaitState { state -> image !in state.classes.first { it.id == red }.imageIds }
        dataset.onAction(DatasetDetailAction.OnUndoDelete(setOf(image)))
        dataset.awaitState { state -> image in state.classes.first { it.id == red }.imageIds }
        assertIs<Result.Success<*>>(repository.readImage(id, image))
    }

    @Test
    fun deletesWithoutUndoAreKeptWhenTheScreenGoes() = viewModelTest {
        val (id, red) = redAndBlue()
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val unknownImages = FileUnknownImageRepository(paths)
        val dataset = DatasetDetailViewModel(
            id, repository, settings, conversions, unknownImages, FileUnknownImages(repository, unknownImages),
            ExportDataset(repository, unknownImages), KeepUnknownImages(sorter, unknownImages), appScope,
        )
        val image = dataset.awaitState { !it.loading }.classes.first { it.id == red }.imageIds.first()

        dataset.onAction(DatasetDetailAction.OnDeleteImages(setOf(image)))
        dataset.awaitState { state -> image !in state.classes.first { it.id == red }.imageIds }
        // The screen closing finishes the delete rather than dropping it.
        val store = ViewModelStore()
        store.put("detail", dataset)
        store.clear()
        repository.observeDataset(id).first { dataset -> image !in dataset!!.classes.first { it.id == red }.imageIds }
    }
}
