package org.onedroid.dataset.domain.usecase

/** Names used inside an exported dataset zip, so the UI can preview exactly what the export will hold. */
object ExportNaming {

    /** Suggested file name for a dataset's export. */
    fun fileName(datasetName: String): String = "${folderName(datasetName, fallback = "dataset")}.zip"

    /**
     * [name] made safe as a folder name on every desktop OS: spaces become underscores, and characters
     * that are reserved or awkward in paths are dropped.
     */
    fun folderName(name: String, fallback: String): String {
        val safe = buildString {
            for (c in name.trim()) {
                when {
                    c.isWhitespace() -> append('_')
                    c.isLetterOrDigit() || c in "-_.()+" -> append(c)
                }
            }
        }.trim('.', '_')
        return safe.ifEmpty { fallback }
    }

    /** The dataset's top-level folder in the zip. */
    fun rootFolder(datasetName: String): String = folderName(datasetName, fallback = "dataset")

    /** Folder names for [classNames], suffixed where they'd clash once made safe (case-insensitively). */
    fun classFolders(classNames: List<String>): List<String> {
        val used = mutableSetOf<String>()
        return classNames.mapIndexed { index, name ->
            val base = folderName(name, fallback = "class_${index + 1}")
            var candidate = base
            var n = 2
            while (!used.add(candidate.lowercase())) candidate = "${base}_${n++}"
            candidate
        }
    }

    const val METADATA_FILE = "metadata.json"
}
