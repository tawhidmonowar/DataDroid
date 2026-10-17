package org.onedroid.dataset.domain.usecase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.onedroid.dataset.domain.DataError
import org.onedroid.dataset.domain.Result
import org.onedroid.dataset.domain.model.ImportResult
import org.onedroid.dataset.domain.repository.DatasetRepository

/** [done] of [total] images converted so far; [total] is 0 until the work is counted. */
data class ConversionProgress(val done: Int = 0, val total: Int = 0) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

/** How a conversion run that wasn't cancelled ended. */
data class ConversionOutcome(val datasetId: String, val result: Result<ImportResult, DataError.Local>)

/**
 * Runs image conversions in an app-wide [scope], so they keep going when the screen that started them
 * closes. At most one runs per dataset; one cut short (cancelled, or the app stopped) can be started again
 * and picks up the images still outdated.
 */
class ImageConversions(
    private val repository: DatasetRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _progress = MutableStateFlow<Map<String, ConversionProgress>>(emptyMap())

    /** Conversions running now, by dataset id. */
    val progress: StateFlow<Map<String, ConversionProgress>> = _progress.asStateFlow()

    private val _outcomes = MutableSharedFlow<ConversionOutcome>(extraBufferCapacity = 16)
    val outcomes: SharedFlow<ConversionOutcome> = _outcomes.asSharedFlow()

    private val jobs = MutableStateFlow<Map<String, Job>>(emptyMap())

    /** Starts converting the dataset's outdated images, unless that's already running. */
    fun start(datasetId: String) {
        var claimed = false
        _progress.update {
            claimed = datasetId !in it
            if (claimed) it + (datasetId to ConversionProgress()) else it
        }
        if (!claimed) return
        val job = scope.launch {
            val result = repository.convertImages(datasetId) { done, total ->
                _progress.update { if (datasetId in it) it + (datasetId to ConversionProgress(done, total)) else it }
            }
            _outcomes.emit(ConversionOutcome(datasetId, result))
        }
        jobs.update { it + (datasetId to job) }
        job.invokeOnCompletion {
            jobs.update { it - datasetId }
            _progress.update { it - datasetId }
        }
    }

    fun cancel(datasetId: String) {
        jobs.value[datasetId]?.cancel()
    }
}
