package org.onedroid.dataset.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.onedroid.dataset.domain.DataError
import org.onedroid.dataset.domain.Result
import org.onedroid.dataset.data.sorting.EmbeddingImageSorter
import org.onedroid.dataset.data.storage.FileDatasetRepository
import org.onedroid.dataset.domain.model.Dataset
import org.onedroid.dataset.domain.usecase.ClassMatcher
import org.onedroid.dataset.core.platform.image.RgbImage
import org.onedroid.dataset.core.platform.ml.Backbone
import org.onedroid.dataset.core.platform.ml.BackboneLoader
import org.onedroid.dataset.core.platform.ml.spec
import org.onedroid.dataset.domain.model.EmbeddingModel
import org.onedroid.dataset.core.platform.ml.SharedBackbone
import org.onedroid.dataset.testutil.FakeImageProcessor
import org.onedroid.dataset.testutil.fakeJpeg
import org.onedroid.dataset.testutil.source
import org.onedroid.dataset.testutil.tempStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmbeddingImageSorterTest {
    private val paths = tempStorage()
    private val repository = FileDatasetRepository(paths, FakeImageProcessor())
    private var embedCalls = 0

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

    /** Embeds an image as its mean color, padded to [model]'s embedding size. */
    private fun backbone(model: EmbeddingModel) = object : Backbone {
        override fun embed(input: FloatArray): FloatArray {
            embedCalls++
            val out = FloatArray(model.spec.embeddingSize)
            for (i in input.indices) out[i % 3] += input[i]
            return out
        }

        override fun close() = Unit
    }

    /** Loads the fake backbone for the model named by the "model file". */
    private val fakeLoader = BackboneLoader { bytes -> backbone(EmbeddingModel.valueOf(bytes.decodeToString())) }

    private fun sorter(
        loader: BackboneLoader? = fakeLoader,
        model: Flow<EmbeddingModel> = flowOf(EmbeddingModel.DEFAULT),
    ) = EmbeddingImageSorter(
        paths = paths,
        repository = repository,
        backbone = SharedBackbone(
            loader,
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
            model = model,
            readModel = { it.name.encodeToByteArray() },
        ),
        imageProcessor = FakeImageProcessor(),
        decode = decode,
    )

    private fun <T> Result<T, *>.value(): T = (this as Result.Success).data

    private suspend fun dataset(id: String): Dataset = requireNotNull(repository.observeDataset(id).first())

    /** A dataset with a "red" class (id first) and a "blue" class (id second), three images each. */
    private suspend fun redAndBlue(): Triple<String, Int, Int> {
        val id = repository.createDataset("Colors").value()
        val red = dataset(id).classes.single().id
        val blue = repository.addClass(id, "Blue").value()
        repository.addImages(id, red, List(3) { source(fakeJpeg("red$it")) })
        repository.addImages(id, blue, List(3) { source(fakeJpeg("blue$it")) })
        return Triple(id, red, blue)
    }

    private suspend fun EmbeddingImageSorter.classify(datasetId: String, tag: String) =
        matcherFor(dataset(datasetId)).value().match(embed(fakeJpeg(tag)).value()!!, ClassMatcher.DEFAULT_STRICTNESS)

    @Test
    fun sortsImagesIntoTheClassTheyLookLike() = runTest {
        val (id, red, blue) = redAndBlue()
        val sorter = sorter()
        assertEquals(red, sorter.classify(id, "red-new").classId)
        assertEquals(blue, sorter.classify(id, "blue-new").classId)
        assertTrue(sorter.classify(id, "green").isUnknown)
    }

    @Test
    fun embeddingsAreCachedAcrossSorters() = runTest {
        val (id, _, _) = redAndBlue()
        var progress = 0 to 0
        sorter().matcherFor(dataset(id)) { done, total -> progress = done to total }.value()
        assertEquals(6 to 6, progress)
        assertEquals(6, embedCalls)

        // A new sorter, as after an app restart, reads the cache instead of embedding again.
        val fresh = sorter()
        fresh.matcherFor(dataset(id)).value()
        assertEquals(6, embedCalls)

        // Only the new image is embedded.
        repository.addImages(id, dataset(id).classes.first().id, listOf(source(fakeJpeg("red-more"))))
        fresh.matcherFor(dataset(id)).value()
        assertEquals(7, embedCalls)
    }

    @Test
    fun imagesEmbeddedForSavingArentEmbeddedAgain() = runTest {
        val (id, red, _) = redAndBlue()
        val sorter = sorter()
        sorter.matcherFor(dataset(id)).value()
        assertEquals(6, embedCalls)

        // As auto-sort does: embed a frame, save it, refit.
        val frame = fakeJpeg("red-frame")
        sorter.embed(frame, saving = true).value()
        repository.addImages(id, red, listOf(source(frame)))
        sorter.matcherFor(dataset(id)).value()
        assertEquals(7, embedCalls)

        // A preview frame isn't kept, so if it's saved after all it's embedded from the stored image.
        val preview = fakeJpeg("red-preview")
        sorter.embed(preview).value()
        repository.addImages(id, red, listOf(source(preview)))
        sorter.matcherFor(dataset(id)).value()
        assertEquals(9, embedCalls)
    }

    @Test
    fun deletedImagesStopCounting() = runTest {
        val (id, red, blue) = redAndBlue()
        val sorter = sorter()
        sorter.matcherFor(dataset(id)).value()
        repository.deleteImages(id, dataset(id).classes.first { it.id == blue }.imageIds.toSet())
        // With blue empty, a blue image matches nothing.
        assertTrue(sorter.classify(id, "blue-new").isUnknown)
        assertEquals(red, sorter.classify(id, "red-new").classId)
    }

    @Test
    fun undecodableImagesAreLeftOut() = runTest {
        val id = repository.createDataset("Colors").value()
        val classId = dataset(id).classes.single().id
        repository.addImages(id, classId, listOf(source(fakeJpeg("red")), source(fakeJpeg("purple"))))
        val sorter = sorter()
        assertTrue(!sorter.matcherFor(dataset(id)).value().isEmpty)
        assertNull(sorter.embed(fakeJpeg("purple")).value())
    }

    @Test
    fun eachModelKeepsItsOwnEmbeddings() = runTest {
        val (id, red, _) = redAndBlue()
        val model = MutableStateFlow(EmbeddingModel.MOBILENET_V3_SMALL)
        val sorter = sorter(model = model)
        assertEquals(1024, sorter.matcherFor(dataset(id)).value().dimensions)
        assertEquals(6, embedCalls)

        // Switching analyses every image again with the new model, and sorts with it.
        model.value = EmbeddingModel.MOBILENET_V3_LARGE
        val large = sorter.matcherFor(dataset(id)).value()
        assertEquals(1280, large.dimensions)
        assertEquals(12, embedCalls)
        val frame = sorter.embed(fakeJpeg("red-new")).value()!!
        assertEquals(1280, frame.size)
        assertEquals(red, large.match(frame, ClassMatcher.DEFAULT_STRICTNESS).classId)

        // Switching back reads the first model's cache rather than embedding again.
        model.value = EmbeddingModel.MOBILENET_V3_SMALL
        assertEquals(1024, sorter.matcherFor(dataset(id)).value().dimensions)
        assertEquals(13, embedCalls)
    }

    @Test
    fun reportsWhenTheModelIsUnavailable() = runTest {
        val (id, _, _) = redAndBlue()
        val sorter = sorter(loader = null)
        assertIs<Result.Error<*>>(sorter.matcherFor(dataset(id)))
        assertEquals(Result.Error(DataError.Sorting.MODEL_UNAVAILABLE), sorter.embed(fakeJpeg("red")))
    }
}
