package org.onedroid.dataset

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform