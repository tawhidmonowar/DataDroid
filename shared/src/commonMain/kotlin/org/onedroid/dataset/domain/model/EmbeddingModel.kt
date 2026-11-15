package org.onedroid.dataset.domain.model

import kotlinx.serialization.Serializable

/**
 * The on-device network auto-sort uses to compare images. Each model's embeddings are only comparable with
 * its own, so switching models has every dataset's images analysed again (once per model: each model's embeddings are kept, so switching back is free).
 */
@Serializable
enum class EmbeddingModel {
    /** MobileNetV3-Small: about 4 MB, fastest. The matcher's thresholds were tuned on this model. */
    MOBILENET_V3_SMALL,

    /** MobileNetV3-Large: about 11 MB, slower, with richer features. */
    MOBILENET_V3_LARGE,

    /**
     * DINOv2 ViT-S/14 (self-supervised): about 23 MB with int8 weights, roughly 20 to 40 times slower than
     * MobileNetV3-Small, and much more accurate on the benchmark datasets.
     */
    DINOV2_SMALL;

    companion object {
        val DEFAULT = MOBILENET_V3_SMALL
    }
}
