package org.onedroid.dataset.presentation.detail

import org.onedroid.dataset.presentation.components.UiText
import org.onedroid.dataset.domain.model.ClassBalance
import org.onedroid.dataset.domain.model.DatasetClass
import org.onedroid.dataset.domain.model.ExportedFile
import org.onedroid.dataset.domain.model.ImageSettings
import org.onedroid.dataset.domain.model.ImageSource
import org.onedroid.dataset.domain.usecase.ClassMatch
import org.onedroid.dataset.domain.usecase.ClassMatcher
import org.onedroid.dataset.domain.usecase.ClassNaming
import org.onedroid.dataset.domain.usecase.computeClassBalance
import org.onedroid.dataset.core.platform.camera.CameraFacing
import org.onedroid.dataset.core.platform.picker.PickedFile
import org.onedroid.dataset.core.platform.picker.PickedFolder
import org.onedroid.dataset.presentation.components.ImportPreviewUi
import org.onedroid.dataset.presentation.components.TransferUi

data class DatasetDetailState(
    val datasetId: String,
    val loading: Boolean = true,
    val name: String = "",
    val classes: List<ClassUi> = emptyList(),
    val balance: ClassBalance = computeClassBalance(emptyList()),
    /** The class the camera is capturing into, or null while it's closed. */
    val cameraClassId: Int? = null,
    /** The auto-sort camera, which saves each image to the class it looks like; null while it's closed. */
    val autoSort: AutoSortUi? = null,
    val cameraFacing: CameraFacing = CameraFacing.Back,
    val recording: Boolean = false,
    /** A tap on the record button asked for one image, which the next frame provides. */
    val shotPending: Boolean = false,
    val transfer: TransferUi? = null,
    /** How images added to this dataset are stored. */
    val imageSettings: ImageSettings = ImageSettings(),
    /** The app's settings for new datasets, offered as a reset. */
    val defaultImageSettings: ImageSettings = ImageSettings(),
    /** Images stored with earlier settings, waiting to be converted. */
    val outdatedImageCount: Int = 0,
    /** The dataset's image conversion, while one runs. */
    val conversion: TransferUi? = null,
    /** Picked folders waiting for the user to choose how they become classes. */
    val importPreview: ImportPreviewUi? = null,
) {
    /** Edits wait while an import, export or conversion runs, so they can't race it. */
    val canEdit: Boolean get() = !loading && transfer == null && conversion == null

    val imageCount: Int get() = classes.sumOf { it.imageIds.size }

    val cameraOpen: Boolean get() = cameraClassId != null || autoSort != null

    /** Whether camera frames should be captured: while recording, or for a single tapped shot. */
    val capturing: Boolean get() = recording || shotPending

    /** Outdated images can be converted (with passthrough settings there's nothing to convert them to). */
    val offersConversion: Boolean get() = conversion == null && outdatedImageCount > 0 && !imageSettings.isPassthrough

    /** Whether a banner sits above the class list. */
    val showsBanner: Boolean get() = transfer != null || conversion != null || offersConversion

    /** List index of the first class: after the banner, if any, and the auto-sort card. */
    val firstClassIndex: Int get() = (if (showsBanner) 1 else 0) + 1
}

/**
 * The auto-sort camera. While [preparing], the dataset's images are being analysed so new ones can be
 * compared with them ([prepared] of [toPrepare] done, both 0 until known). [prediction] is where the latest camera frame would go; [unknowns] are captured images
 * that matched no class, waiting for the user to file or discard them; [sorted] counts the images saved to
 * each class (by id) since it opened.
 */
data class AutoSortUi(
    val preparing: Boolean = true,
    val prepared: Int = 0,
    val toPrepare: Int = 0,
    val unavailable: Boolean = false,
    val prediction: ClassMatch? = null,
    val strictness: Int = ClassMatcher.DEFAULT_STRICTNESS,
    val unknowns: List<UnknownImage> = emptyList(),
    val sorted: Map<Int, Int> = emptyMap(),
) {
    val ready: Boolean get() = !preparing && !unavailable
}

/** A captured or picked image that matched no class; [preview] is a small JPEG of it. */
class UnknownImage(val id: Long, val preview: ByteArray, val source: ImageSource)

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
    data object OnOpenAutoSort : DatasetDetailAction
    data object OnCloseAutoSort : DatasetDetailAction
    data class OnStrictnessChange(val strictness: Int) : DatasetDetailAction

    /** Images picked to be sorted into classes automatically. */
    data class OnAutoSortImagesPicked(val images: List<ImageSource>) : DatasetDetailAction

    /** Files unknown images ([imageIds], or all when null) in a new class named [name]. */
    data class OnUnknownsToNewClass(val name: String, val imageIds: Set<Long>? = null) : DatasetDetailAction
    data class OnUnknownsToClass(val classId: Int, val imageIds: Set<Long>? = null) : DatasetDetailAction
    data class OnDiscardUnknowns(val imageIds: Set<Long>? = null) : DatasetDetailAction

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

    /** Stores new images with [settings]; [convertExisting] also converts the images already stored. */
    data class OnSaveImageSettings(val settings: ImageSettings, val convertExisting: Boolean) : DatasetDetailAction
    data object OnConvertImages : DatasetDetailAction
    data object OnCancelConversion : DatasetDetailAction
    data class OnImportNamingChange(val naming: ClassNaming) : DatasetDetailAction
    data class OnImportMergeSplitsChange(val merge: Boolean) : DatasetDetailAction
    data object OnConfirmImport : DatasetDetailAction
    data object OnDismissImport : DatasetDetailAction
}

sealed interface DatasetDetailEvent {
    data object NavigateBack : DatasetDetailEvent
    data class ShowMessage(val message: UiText) : DatasetDetailEvent
    data class ShareFile(val file: ExportedFile) : DatasetDetailEvent

    /** Scroll the class list to a class that was just added. */
    data class FocusClass(val classId: Int) : DatasetDetailEvent
}

fun DatasetClass.toClassUi(index: Int) = ClassUi(id = id, name = name, colorIndex = index, imageIds = imageIds)
