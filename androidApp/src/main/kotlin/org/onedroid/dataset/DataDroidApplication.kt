package org.onedroid.dataset

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.onedroid.dataset.core.di.initKoin

class DataDroidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@DataDroidApplication) }
    }
}
