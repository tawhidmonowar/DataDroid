package org.onedroid.dataset.presentation.capture

import org.onedroid.dataset.core.platform.camera.CameraFacing
import org.onedroid.dataset.domain.model.ImageSource
import org.onedroid.dataset.domain.model.UnknownImage
import org.onedroid.dataset.domain.usecase.ClassMatch
import org.onedroid.dataset.domain.usecase.ClassMatcher
import org.onedroid.dataset.presentation.components.TransferUi
import org.onedroid.dataset.presentation.components.UiText
import org.onedroid.dataset.presentation.detail.ClassUi

/** Where captured images go: to the class each looks like ([Auto]), or all to one class. */
sealed interface CaptureTarget {
    data object Auto : CaptureTarget
    data class Class(val classId: Int) : CaptureTarget
}

data class CaptureState(
    val datasetId: String,
    val loading: Boolean = true,
    val name: String = "",
    val classes: List<ClassUi> = emptyList(),
    val target: CaptureTarget = CaptureTarget.Auto,
    val facing: CameraFacing = CameraFacing.Back,
    val recording: Boolean = false,
    /** A tap on the shutter asked for one image, which the next frame provides. */
    val shotPending: Boolean = false,
    val autoSort: AutoSortStatus = AutoSortStatus(),
    /** How alike an image must be to a class for auto-sort to put it there; 0 never leaves it unknown. */
    val strictness: Int = ClassMatcher.DEFAULT_STRICTNESS,
    /** Images saved to each class (by id) since the screen opened. */
    val saved: Map<Int, Int> = emptyMap(),
    /** Images auto-sort couldn't place, oldest first. */
    val unknowns: List<UnknownImage> = emptyList(),
    /** Images added since the screen opened, newest first: saved to a class, or waiting under Unknown. */
    val recent: List<RecentCapture> = emptyList(),
    /** Picked photos being added or sorted, or unknowns being filed. */
    val transfer: TransferUi? = null,
    /** The dataset's images are being converted, so nothing is added meanwhile. */
    val converting: Boolean = false,
) {
    val targetClass: ClassUi? get() = (target as? CaptureTarget.Class)?.let { t -> classes.firstOrNull { it.id == t.classId } }

    /** Whether images can be added now. */
    val canEdit: Boolean get() = !loading && transfer == null && !converting

    /** Whether the shutter works: auto-sort must be ready to sort what it captures. */
    val canCapture: Boolean get() = canEdit && (target is CaptureTarget.Class || autoSort.ready)

    /** Whether camera frames should be captured: while recording, or for a single tapped shot. */
    val capturing: Boolean get() = recording || shotPending

    val savedCount: Int get() = saved.values.sum()
}

/** An image added while the capture screen is open. */
sealed interface RecentCapture {
    /** Identifies the item in a list, across both kinds. */
    val key: String

    data class Saved(val imageId: Long, val classId: Int) : RecentCapture {
        override val key: String get() = "image-$imageId"
    }

    data class Unknown(val image: UnknownImage) : RecentCapture {
        override val key: String get() = "unknown-${image.id}"
    }
}

/**
 * Auto-sort's readiness. While [preparing], the dataset's images are being analysed so new ones can be
 * compared with them ([prepared] of [toPrepare], both 0 until known). [prediction] is where the latest camera
 * frame would go.
 */
data class AutoSortStatus(
    val preparing: Boolean = true,
    val prepared: Int = 0,
    val toPrepare: Int = 0,
    val unavailable: Boolean = false,
    val prediction: ClassMatch? = null,
) {
    val ready: Boolean get() = !preparing && !unavailable
}

sealed interface CaptureAction {
    data object OnBack : CaptureAction
    data class OnSelectTarget(val target: CaptureTarget) : CaptureAction

    /** Adds a class named [name] and captures into it. */
    data class OnAddClass(val name: String) : CaptureAction
    data object OnFlipCamera : CaptureAction
    data object OnStartRecording : CaptureAction
    data object OnStopRecording : CaptureAction

    /** A JPEG frame from the camera; may arrive on a background thread. */
    class OnCapture(val jpeg: ByteArray) : CaptureAction
    data class OnStrictnessChange(val strictness: Int) : CaptureAction

    /** Photos picked from the library: sorted by auto-sort, or added to the chosen class. */
    data class OnPhotosPicked(val images: List<ImageSource>) : CaptureAction
    data class OnUnknownsToClass(val classId: Int) : CaptureAction
    data class OnUnknownsToNewClass(val name: String) : CaptureAction

    /** Discards the unknowns with [imageIds], or all of them when null. */
    data class OnDiscardUnknowns(val imageIds: Set<Long>? = null) : CaptureAction
    data object OnCancelTransfer : CaptureAction
}

sealed interface CaptureEvent {
    data object NavigateBack : CaptureEvent
    data class ShowMessage(val message: UiText) : CaptureEvent
}
