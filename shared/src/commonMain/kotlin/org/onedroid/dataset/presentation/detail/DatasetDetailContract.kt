package org.onedroid.dataset.presentation.detail

import org.onedroid.dataset.core.presentation.UiText
import org.onedroid.dataset.domain.model.ClassBalance
import org.onedroid.dataset.domain.model.DatasetClass
import org.onedroid.dataset.domain.model.ExportedFile
import org.onedroid.dataset.domain.model.ImageSource
import org.onedroid.dataset.domain.usecase.computeClassBalance
import org.onedroid.dataset.platform.camera.CameraFacing
import org.onedroid.dataset.platform.picker.PickedFile
import org.onedroid.dataset.platform.picker.PickedFolder
import org.onedroid.dataset.presentation.components.TransferUi

data class DatasetDetailState(
    val datasetId: String,
    val loading: Boolean = true,
    val name: String = "",
    val classes: List<ClassUi> = emptyList(),
    val balance: ClassBalance = computeClassBalance(emptyList()),
    /** The class the camera is capturing into, or null while it's closed. */
    val cameraClassId: Int? = null,
    val cameraFacing: CameraFacing = CameraFacing.Back,
    val recording: Boolean = false,
    val transfer: TransferUi? = null,
) {
    /** Edits wait while an import or export runs, so they can't race it. */
    val canEdit: Boolean get() = !loading && transfer == null

    val imageCount: Int get() = classes.sumOf { it.imageIds.size }
}

/** [colorIndex] picks the class's accent color; [imageIds] are oldest first. */
data class ClassUi(
    val id: Int,
    val name: String,
    val colorIndex: Int,
    val imageIds: List<Long>,
)

sealed interface DatasetDetailAction {
    data object OnBack : DatasetDetailAction
    data class OnRenameDataset(val name: String) : DatasetDetailAction
    data object OnAddClass : DatasetDetailAction
    data class OnRenameClass(val classId: Int, val name: String) : DatasetDetailAction
    data class OnDeleteClass(val classId: Int) : DatasetDetailAction
    data class OnClearClass(val classId: Int) : DatasetDetailAction
    data class OnOpenCamera(val classId: Int) : DatasetDetailAction
    data object OnCloseCamera : DatasetDetailAction
    data object OnFlipCamera : DatasetDetailAction
    data object OnStartRecording : DatasetDetailAction
    data object OnStopRecording : DatasetDetailAction

    /** A JPEG frame from the camera; may arrive on a background thread. */
    class OnCapture(val jpeg: ByteArray) : DatasetDetailAction
    data class OnImagesPicked(val classId: Int, val images: List<ImageSource>) : DatasetDetailAction
    data class OnClassFolderPicked(val classId: Int, val folder: PickedFolder) : DatasetDetailAction
    data class OnDatasetFolderPicked(val folder: PickedFolder) : DatasetDetailAction
    data class OnDatasetZipPicked(val file: PickedFile) : DatasetDetailAction
    data object OnCancelTransfer : DatasetDetailAction
    data class OnDeleteImages(val imageIds: Set<Long>) : DatasetDetailAction
    data class OnMoveImages(val imageIds: Set<Long>, val toClassId: Int) : DatasetDetailAction
    data object OnExport : DatasetDetailAction
}

sealed interface DatasetDetailEvent {
    data object NavigateBack : DatasetDetailEvent
    data class ShowMessage(val message: UiText) : DatasetDetailEvent
    data class ShareFile(val file: ExportedFile) : DatasetDetailEvent

    /** Scroll the class list to a class that was just added. */
    data class FocusClass(val classId: Int) : DatasetDetailEvent
}

fun DatasetClass.toClassUi(index: Int) = ClassUi(id = id, name = name, colorIndex = index, imageIds = imageIds)
