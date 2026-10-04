package org.onedroid.dataset.data.storage

import kotlinx.io.files.Path

/**
 * Where datasets live ([datasetsRoot], one folder each), where exports are written before sharing, and the
 * app's [settingsFile].
 */
class StoragePaths(val datasetsRoot: Path, val exportDirectory: Path, val settingsFile: Path)
