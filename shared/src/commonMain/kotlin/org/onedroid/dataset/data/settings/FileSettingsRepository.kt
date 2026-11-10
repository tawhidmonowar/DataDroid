package org.onedroid.dataset.data.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.onedroid.dataset.domain.DataError
import org.onedroid.dataset.domain.EmptyResult
import org.onedroid.dataset.domain.Result
import org.onedroid.dataset.data.storage.writeAtomically
import org.onedroid.dataset.domain.model.EmbeddingModel
import org.onedroid.dataset.domain.model.ImageSettings
import org.onedroid.dataset.domain.repository.SettingsRepository

/** `settings.json`: the app's preferences. */
@Serializable
internal data class SettingsRecord(
    val version: Int = 1,
    val imageSettings: ImageSettings = ImageSettings(),
    val embeddingModel: EmbeddingModel = EmbeddingModel.DEFAULT,
)

/**
 * Preferences kept in one JSON [file]. A missing or unreadable file means the defaults, so a damaged file
 * never stops the app; the next save replaces it.
 */
class FileSettingsRepository(
    private val file: Path,
    private val fileSystem: FileSystem = SystemFileSystem,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SettingsRepository {
    // coerceInputValues: a model this version doesn't know (saved by a newer one) reads as the default.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    private val mutex = Mutex()

    /** Null until first loaded from disk. */
    private val record = MutableStateFlow<SettingsRecord?>(null)

    override fun observeImageSettings(): Flow<ImageSettings> = flow {
        locked { }
        emitAll(record.filterNotNull().map { it.imageSettings })
    }

    override suspend fun saveImageSettings(settings: ImageSettings): EmptyResult<DataError.Local> =
        save { it.copy(imageSettings = settings.sanitized()) }

    override fun observeEmbeddingModel(): Flow<EmbeddingModel> = flow {
        locked { }
        emitAll(record.filterNotNull().map { it.embeddingModel }.distinctUntilChanged())
    }

    override suspend fun saveEmbeddingModel(model: EmbeddingModel): EmptyResult<DataError.Local> =
        save { it.copy(embeddingModel = model) }

    private suspend fun save(change: (SettingsRecord) -> SettingsRecord): EmptyResult<DataError.Local> = try {
        locked {
            val updated = change(requireNotNull(record.value))
            fileSystem.writeAtomically(file, json.encodeToString(SettingsRecord.serializer(), updated).encodeToByteArray())
            record.value = updated
        }
        Result.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.Error(DataError.Local.UNKNOWN)
    }

    private suspend fun <T> locked(block: () -> T): T = mutex.withLock {
        withContext(ioDispatcher) {
            if (record.value == null) record.value = load()
            block()
        }
    }

    private fun load(): SettingsRecord = runCatching {
        if (!fileSystem.exists(file)) return@runCatching null
        val text = fileSystem.source(file).buffered().use { it.readByteArray() }.decodeToString()
        json.decodeFromString(SettingsRecord.serializer(), text).let { it.copy(imageSettings = it.imageSettings.sanitized()) }
    }.getOrNull() ?: SettingsRecord()
}
