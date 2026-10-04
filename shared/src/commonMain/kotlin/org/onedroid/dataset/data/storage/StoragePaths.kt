package org.onedroid.dataset.data.storage

import kotlinx.io.files.Path

/** Where datasets live ([datasetsRoot], one folder each) and where exports are written before sharing. */
class StoragePaths(val datasetsRoot: Path, val exportDirectory: Path)
