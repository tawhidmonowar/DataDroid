package org.onedroid.dataset.testutil

import org.onedroid.dataset.domain.usecase.ClassMatch

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * The nearest-centroid matcher auto-sort used before centring, neighbours and near-duplicate grouping, kept
 * to measure the current [org.onedroid.dataset.domain.usecase.ClassMatcher] against.
 *
 * Sorts images into the classes they look like, from their embeddings, with no training. Each class is
 * summarised by the centroid of its images' normalised embeddings and a radius: the cosine distance within
 * which [COVERAGE] of its own images lie. An image goes to the nearest centroid, unless it lies outside that
 * class's radius (scaled by a margin set by strictness), when it's unknown.
 */
class LegacyClassMatcher private constructor(
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
        val unit = embedding.unit()
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
        fun fit(embeddingsByClass: Map<Int, List<FloatArray>>): LegacyClassMatcher {
            val classes = embeddingsByClass.filterValues { it.isNotEmpty() }
            val ids = classes.keys.toList()
            val centroids = ids.map { id -> centroid(classes.getValue(id).map { it.unit() }) }
            val measured = ids.mapIndexed { index, id ->
                val samples = classes.getValue(id)
                if (samples.size < MIN_SAMPLES_FOR_SPREAD) {
                    null
                } else {
                    val distances = samples.map { 1f - dot(it.unit(), centroids[index]) }.sorted()
                    distances[(ceil(COVERAGE * distances.size).toInt() - 1).coerceIn(distances.indices)]
                }
            }
            val typical = measured.filterNotNull().sorted().let { if (it.isEmpty()) MIN_RADIUS else it[it.size / 2] }
            val radii = FloatArray(ids.size) { (measured[it] ?: typical).coerceAtLeast(MIN_RADIUS) }
            return LegacyClassMatcher(ids, centroids, radii)
        }

        /** Maps strictness 1..100 onto the radius margin: lenient accepts up to 2.5 radii, strict just one. */
        private fun marginFor(strictness: Int): Float {
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

private fun FloatArray.unit(): FloatArray {
    val norm = sqrt(dot(this, this))
    return if (norm == 0f) copyOf() else FloatArray(size) { this[it] / norm }
}

private fun centroid(units: List<FloatArray>): FloatArray {
    val sum = FloatArray(units.first().size)
    for (unit in units) for (i in sum.indices) sum[i] += unit[i]
    return sum.unit()
}
