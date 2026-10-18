package org.onedroid.dataset.domain.usecase

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Where an image belongs. [classId] is the class it's like, or null when it's unlike every class (unknown).
 * [closestClassId] is the nearest class either way (null only when no class has images), and [similarity]
 * its cosine similarity to that class, 0 to 1 for typical embeddings.
 */
data class ClassMatch(val classId: Int?, val closestClassId: Int?, val similarity: Float) {
    val isUnknown: Boolean get() = classId == null
}

/**
 * Sorts images into the classes they look like, from their embeddings, with no training. Each class is
 * summarised by the centroid of its images' normalised embeddings and a radius: the cosine distance within
 * which [COVERAGE] of its own images lie. An image goes to the nearest centroid, unless it lies outside that
 * class's radius (scaled by a margin set by strictness), when it's unknown.
 */
class ClassMatcher private constructor(
    private val classIds: List<Int>,
    private val centroids: List<FloatArray>,
    /** Per-class radius in cosine distance, before any margin. */
    private val radii: FloatArray,
) {
    /** True when no class has images, so every image is unknown. */
    val isEmpty: Boolean get() = classIds.isEmpty()

    /**
     * The class [embedding] belongs to. [strictness] runs from 0 (never unknown: always the nearest class)
     * through 100 (only images very like a class's own belong to it).
     */
    fun match(embedding: FloatArray, strictness: Int): ClassMatch {
        if (classIds.isEmpty()) return ClassMatch(classId = null, closestClassId = null, similarity = 0f)
        val unit = embedding.normalized()
        var best = 0
        var bestDistance = Float.MAX_VALUE
        for (i in centroids.indices) {
            val distance = 1f - dot(unit, centroids[i])
            if (distance < bestDistance) {
                best = i
                bestDistance = distance
            }
        }
        val familiar = strictness <= 0 || bestDistance <= radii[best] * marginFor(strictness)
        return ClassMatch(
            classId = if (familiar) classIds[best] else null,
            closestClassId = classIds[best],
            similarity = (1f - bestDistance).coerceIn(0f, 1f),
        )
    }

    companion object {
        const val DEFAULT_STRICTNESS = 50

        /**
         * Share of a class's own images its radius covers; the rest count as outliers. Well below 1, so a few
         * misfiled images far from the rest can't stretch the radius over everything.
         */
        const val COVERAGE = 0.8f

        /**
         * Smallest radius, in cosine distance. A class of near-identical images (one burst of camera frames)
         * would otherwise reject the slightest change in angle or light.
         */
        const val MIN_RADIUS = 0.12f

        /**
         * Builds the matcher from each class's image embeddings, by class id. Classes without images are left
         * out; classes with too few to measure their spread borrow the typical radius of the others.
         */
        fun fit(embeddingsByClass: Map<Int, List<FloatArray>>): ClassMatcher {
            val classes = embeddingsByClass.filterValues { it.isNotEmpty() }
            val ids = classes.keys.toList()
            val centroids = ids.map { id -> centroid(classes.getValue(id).map { it.normalized() }) }
            val measured = ids.mapIndexed { index, id ->
                val samples = classes.getValue(id)
                if (samples.size < MIN_SAMPLES_FOR_SPREAD) {
                    null
                } else {
                    val distances = samples.map { 1f - dot(it.normalized(), centroids[index]) }.sorted()
                    distances[(ceil(COVERAGE * distances.size).toInt() - 1).coerceIn(distances.indices)]
                }
            }
            val typical = measured.filterNotNull().sorted().let { if (it.isEmpty()) MIN_RADIUS else it[it.size / 2] }
            val radii = FloatArray(ids.size) { (measured[it] ?: typical).coerceAtLeast(MIN_RADIUS) }
            return ClassMatcher(ids, centroids, radii)
        }

        /** Maps strictness 1..100 onto the radius margin: lenient accepts up to 2.5 radii, strict just one. */
        internal fun marginFor(strictness: Int): Float {
            val t = strictness.coerceIn(0, 100) / 100f
            return LENIENT_MARGIN + (STRICT_MARGIN - LENIENT_MARGIN) * t
        }

        private const val MIN_SAMPLES_FOR_SPREAD = 5
        private const val LENIENT_MARGIN = 2.5f
        private const val STRICT_MARGIN = 1f
    }
}

private fun dot(a: FloatArray, b: FloatArray): Float {
    var sum = 0f
    for (i in a.indices) sum += a[i] * b[i]
    return sum
}

private fun FloatArray.normalized(): FloatArray {
    val norm = sqrt(dot(this, this))
    return if (norm == 0f) copyOf() else FloatArray(size) { this[it] / norm }
}

private fun centroid(units: List<FloatArray>): FloatArray {
    val sum = FloatArray(units.first().size)
    for (unit in units) for (i in sum.indices) sum[i] += unit[i]
    return sum.normalized()
}
