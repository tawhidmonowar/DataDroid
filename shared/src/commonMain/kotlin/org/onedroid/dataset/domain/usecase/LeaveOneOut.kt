package org.onedroid.dataset.domain.usecase

/** An image's embedding, its class, and its [group] of near-duplicates (images from one burst share a group). */
class LabeledEmbedding(val classId: Int, val embedding: FloatArray, val group: Int)

/**
 * How well a matcher sorts a dataset's own images. [accuracy] counts images whose closest class is their own;
 * [acceptedCorrectly] those also within the class's radius at the strictness evaluated (so not left unknown),
 * and [falseUnknown] those left unknown.
 */
data class Evaluation(val evaluated: Int, val accuracy: Float, val acceptedCorrectly: Float, val falseUnknown: Float)

/**
 * Leave-one-out evaluation: each image is held out, the matcher refitted on the rest, and the image sorted.
 * The held-out image's near-duplicates are left out too, since they'd make any matcher look perfect.
 */
object LeaveOneOut {

    /** Groups each class's images into near-duplicates, for datasets whose bursts aren't known. */
    fun label(embeddingsByClass: Map<Int, List<FloatArray>>): List<LabeledEmbedding> {
        var nextGroup = 0
        return embeddingsByClass.flatMap { (classId, embeddings) ->
            val groups = ClassMatcher.duplicateGroups(embeddings.map { it.normalized() })
            val offset = nextGroup
            nextGroup += (groups.maxOrNull() ?: -1) + 1
            embeddings.mapIndexed { i, embedding -> LabeledEmbedding(classId, embedding, offset + groups[i]) }
        }
    }

    /**
     * Evaluates [fit] on [samples] at [strictness]. [heldOut] picks which samples are held out (all by default),
     * as refitting for each costs time on big datasets.
     */
    fun evaluate(
        samples: List<LabeledEmbedding>,
        strictness: Int = ClassMatcher.DEFAULT_STRICTNESS,
        heldOut: List<LabeledEmbedding> = samples,
        fit: (Map<Int, List<FloatArray>>) -> (embedding: FloatArray, strictness: Int) -> ClassMatch = { byClass ->
            ClassMatcher.fit(byClass)::match
        },
    ): Evaluation {
        var nearest = 0
        var accepted = 0
        var unknown = 0
        for (sample in heldOut) {
            val rest = samples.filter { it.group != sample.group }
            val match = fit(rest.groupBy({ it.classId }, { it.embedding }))(sample.embedding, strictness)
            if (match.closestClassId == sample.classId) nearest++
            if (match.classId == sample.classId) accepted++
            if (match.isUnknown) unknown++
        }
        val count = heldOut.size.coerceAtLeast(1).toFloat()
        return Evaluation(heldOut.size, nearest / count, accepted / count, unknown / count)
    }
}
