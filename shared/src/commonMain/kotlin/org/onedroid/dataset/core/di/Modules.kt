package org.onedroid.dataset.core.di

import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.bind
import org.koin.dsl.module
import org.onedroid.dataset.data.image.ImageProcessor
import org.onedroid.dataset.data.image.PlatformImageProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import org.onedroid.dataset.data.sorting.EmbeddingImageSorter
import org.onedroid.dataset.domain.repository.ImageSorter
import org.onedroid.dataset.core.platform.ml.SharedBackbone
import org.onedroid.dataset.data.settings.FileSettingsRepository
import org.onedroid.dataset.data.storage.FileDatasetRepository
import org.onedroid.dataset.data.storage.FileUnknownImageRepository
import org.onedroid.dataset.data.storage.StoragePaths
import org.onedroid.dataset.domain.repository.DatasetRepository
import org.onedroid.dataset.domain.repository.SettingsRepository
import org.onedroid.dataset.domain.repository.UnknownImageRepository
import org.onedroid.dataset.domain.usecase.ExportDataset
import org.onedroid.dataset.domain.usecase.FileUnknownImages
import org.onedroid.dataset.domain.usecase.KeepUnknownImages
import org.onedroid.dataset.domain.usecase.ImageConversions
import org.onedroid.dataset.presentation.components.ImageLoader
import org.onedroid.dataset.presentation.capture.CaptureViewModel
import org.onedroid.dataset.presentation.datasets.DatasetListViewModel
import org.onedroid.dataset.presentation.detail.DatasetDetailViewModel
import org.onedroid.dataset.presentation.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel

/** Platform specifics: where files are stored, and the on-device image model (a `BackboneLoader`). */
expect val platformModule: Module

val dataModule = module {
    singleOf(::PlatformImageProcessor) bind ImageProcessor::class
    single<SettingsRepository> { FileSettingsRepository(file = get<StoragePaths>().settingsFile) }
    // Lambda form: the repository's dispatcher, file system and clock keep their defaults.
    single<DatasetRepository> {
        val settings = get<SettingsRepository>()
        FileDatasetRepository(
            paths = get(),
            imageProcessor = get(),
            defaultImageSettings = { settings.observeImageSettings().first() },
        )
    }
    single { ImageConversions(get()) }
    single<UnknownImageRepository> { FileUnknownImageRepository(paths = get()) }
    singleOf(::FileUnknownImages)
    singleOf(::ExportDataset)
    singleOf(::KeepUnknownImages)
    // For work that must finish after the screen that started it has gone.
    single { ApplicationScope() }
    // Loaded on first use and kept for the app's lifetime; shared by every dataset.
    single { SharedBackbone(loader = getOrNull(), scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    single<ImageSorter> { EmbeddingImageSorter(paths = get(), repository = get(), backbone = get(), imageProcessor = get()) }
}

val presentationModule = module {
    singleOf(::ImageLoader)
    viewModelOf(::DatasetListViewModel)
    viewModelOf(::SettingsViewModel)
    viewModel { (datasetId: String) ->
        DatasetDetailViewModel(datasetId, get(), get(), get(), get(), get(), get(), get(), appScope = get<ApplicationScope>())
    }
    viewModel { (datasetId: String, classId: Int?) -> CaptureViewModel(datasetId, classId, get(), get(), get(), get(), get()) }
}

/** Starts Koin once per process; [config] adds platform setup such as the Android context. */
fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(platformModule, dataModule, presentationModule)
    }
}

/** A scope that lives as long as the app. */
class ApplicationScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default)
