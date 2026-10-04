package org.onedroid.dataset.presentation.datasets

import org.onedroid.dataset.core.presentation.UiText
import org.onedroid.dataset.domain.model.DatasetSummary
import org.onedroid.dataset.domain.model.ExportedFile
import org.onedroid.dataset.platform.picker.PickedFile
import org.onedroid.dataset.presentation.components.TransferUi

data class DatasetListState(
    val loading: Boolean = true,
    val datasets: List<DatasetItemUi> = emptyList(),
    /** An import or export in progress; only one runs at a time. */
    val transfer: TransferUi? = null,
)

data class DatasetItemUi(
    val id: String,
    val name: String,
    val classCount: Int,
    val imageCount: Int,
    val coverImageId: Long?,
)

sealed interface DatasetListAction {
    data class OnOpenDataset(val id: String) : DatasetListAction
    data class OnCreateDataset(val name: String) : DatasetListAction
    data class OnRenameDataset(val id: String, val name: String) : DatasetListAction
    data class OnDeleteDataset(val id: String) : DatasetListAction
    data class OnExportDataset(val id: String) : DatasetListAction
    data class OnImportArchive(val file: PickedFile) : DatasetListAction
    data object OnCancelTransfer : DatasetListAction
}

sealed interface DatasetListEvent {
    data class NavigateToDataset(val id: String) : DatasetListEvent
    data class ShowMessage(val message: UiText) : DatasetListEvent
    data class ShareFile(val file: ExportedFile) : DatasetListEvent
}

fun DatasetSummary.toDatasetItemUi() = DatasetItemUi(
    id = id,
    name = name,
    classCount = classCount,
    imageCount = imageCount,
    coverImageId = coverImageId,
)
