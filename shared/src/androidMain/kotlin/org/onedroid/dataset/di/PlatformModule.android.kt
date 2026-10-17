package org.onedroid.dataset.di

import kotlinx.io.files.Path
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import org.onedroid.dataset.data.storage.StoragePaths
import org.onedroid.dataset.platform.ml.BackboneLoader
import org.onedroid.dataset.platform.ml.AndroidBackboneLoader

actual val platformModule: Module = module {
    single {
        val context = androidContext()
        StoragePaths(
            datasetsRoot = Path(context.filesDir.absolutePath, "datasets"),
            // Shared through FileProvider; see res/xml/datadroid_export_paths.xml.
            exportDirectory = Path(context.cacheDir.absolutePath, "exports"),
            settingsFile = Path(context.filesDir.absolutePath, "settings.json"),
        )
    }
    single<BackboneLoader> { AndroidBackboneLoader }
}
