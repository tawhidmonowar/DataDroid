package org.onedroid.dataset.di

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.files.Path
import org.koin.core.module.Module
import org.koin.dsl.module
import org.onedroid.dataset.data.storage.StoragePaths
import org.onedroid.dataset.platform.ml.BackboneLoader
import org.onedroid.dataset.platform.ml.IosBackboneLoader
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual val platformModule: Module = module {
    single {
        val supportDir = NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
        val base = requireNotNull(supportDir?.path) { "Application Support directory unavailable" }
        StoragePaths(
            datasetsRoot = Path(base, "datasets"),
            exportDirectory = Path(NSTemporaryDirectory(), "exports"),
            settingsFile = Path(base, "settings.json"),
        )
    }
    single<BackboneLoader> { IosBackboneLoader }
}
