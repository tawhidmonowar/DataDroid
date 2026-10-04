package org.onedroid.dataset.di

import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.bind
import org.koin.dsl.module
import org.onedroid.dataset.data.image.ImageProcessor
import org.onedroid.dataset.data.image.PlatformImageProcessor
import org.onedroid.dataset.data.storage.FileDatasetRepository
import org.onedroid.dataset.domain.repository.DatasetRepository
import org.onedroid.dataset.presentation.components.ImageLoader
import org.onedroid.dataset.presentation.datasets.DatasetListViewModel
import org.onedroid.dataset.presentation.detail.DatasetDetailViewModel
import org.koin.core.module.dsl.viewModel

/** Platform specifics: where files are stored. */
expect val platformModule: Module

val dataModule = module {
    singleOf(::PlatformImageProcessor) bind ImageProcessor::class
    // Lambda form: the repository's dispatcher, file system and clock keep their defaults.
    single<DatasetRepository> { FileDatasetRepository(paths = get(), imageProcessor = get()) }
}

val presentationModule = module {
    singleOf(::ImageLoader)
    viewModelOf(::DatasetListViewModel)
    viewModel { (datasetId: String) -> DatasetDetailViewModel(datasetId, get()) }
}

/** Starts Koin once per process; [config] adds platform setup such as the Android context. */
fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(platformModule, dataModule, presentationModule)
    }
}
