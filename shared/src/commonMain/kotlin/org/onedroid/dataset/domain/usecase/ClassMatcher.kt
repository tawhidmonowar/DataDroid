package org.onedroid.dataset.domain.usecase

import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Where an image belongs. [classId] is the class it's like, or null when it's unlike every class (unknown).
 * [closestClassId] is the nearest class either way (null only when no class has images), and [similarity]
 * how alike it is to that class, 0 to 1.
 */
data class ClassMatch(val classId: Int?, val closestClassId: Int?, val similarity: Float) {
    val isUnknown: Boolean get() = classId == null
}

/**
 * Sorts images into the classes they look like, from their embeddings, with no training.
 *
 * Embeddings are normalised and centred on the dataset's mean image, so what every image has in common
 * doesn't count as likeness. An image's score for a class blends its cosine similarity to the class centroid
 * (robust for small classes) with its mean similarity to the class's [NEIGHBOURS] most alike images (which
 * follows classes with several looks, like a cup from the side and from above). Near-duplicates within a
 * class, such as one burst of camera frames, are grouped and together weigh as one image, so a long burst
 * can't drag a class towards one shot.
 *
 * Each class also gets a radius: the distance within which [COVERAGE] of its own images lie when each is
 * scored against the rest of the class. An image goes to the best-scoring class, unless it lies outside that
 * class's radius (scaled by a margin set by strictness), when it's unknown.
 */
class ClassMatcher private constructor(
    private val classIds: List<Int>,
    /** The mean image embeddings are centred on, or null with fewer than two classes. */
    private val mean: FloatArray?,
    private val classes: List<ClassModel>,
    /** Per-class radius in score distance, before any margin. */
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
        val query = centred(embedding.normalized(), mean)
        var best = 0
        var bestScore = -Float.MAX_VALUE
        for (i in classes.indices) {
            val score = classes[i].score(query)
            if (score > bestScore) {
                best = i
                bestScore = score
            }
        }
        val distance = 1f - bestScore
        val familiar = strictness <= 0 || distance <= radii[best] * marginFor(strictness)
        return ClassMatch(
            classId = if (familiar) classIds[best] else null,
            closestClassId = classIds[best],
            similarity = bestScore.coerceIn(0f, 1f),
        )
    }

    companion object {
        const val DEFAULT_STRICTNESS = 50

        /**
         * Share of a class's own images its radius covers; the rest count as outliers. Well below 1, so a few
         * misfiled images far from the rest can't stretch the radius over everything.
         */
        const val COVERAGE = 0.8f

        /** Smallest radius, in score distance, so a class of very alike images still accepts slight changes. */
        const val MIN_RADIUS = 0.12f

        /**
         * Radius when no class has enough distinct images to measure one: about the tightest a typical class
         * measures on real datasets.
         */
        const val FALLBACK_RADIUS = 0.75f

        /**
         * Cosine similarity above which two images of a class count as near-duplicates (one burst of frames).
         * Neighbouring camera frames measure above 0.86; different photos of one thing rarely pass 0.75.
         */
        const val DUPLICATE_SIMILARITY = 0.85f

        /** How many of a class's most alike images (counting each group of near-duplicates once) are averaged. */
        const val NEIGHBOURS = 5

        /**
         * Builds the matcher from each class's image embeddings, by class id. Classes without images are left
         * out; classes with too few distinct images to measure their spread borrow the typical radius of the
         * others.
         */
        fun fit(embeddingsByClass: Map<Int, List<FloatArray>>): ClassMatcher {
            val byClass = embeddingsByClass.filterValues { it.isNotEmpty() }
            val ids = byClass.keys.toList()
            val units = ids.map { id -> byClass.getValue(id).map { it.normalized() } }
            val groups = units.map { duplicateGroups(it) }
            val weights = groups.map { group ->
                val sizes = IntArray(group.max() + 1).also { sizes -> group.forEach { sizes[it]++ } }
                FloatArray(group.size) { 1f / sizes[group[it]] }
            }
            val mean = if (ids.size < 2) null else weightedMean(units.flatten(), weights.flatMap { it.asList() })
            val classes = ids.indices.map { i ->
                ClassModel(units[i].map { centred(it, mean) }.toTypedArray(), weights[i], groups[i])
            }
            val measured = classes.map { it.measureRadius() }
            val typical = measured.filterNotNull().sorted().let { if (it.isEmpty()) FALLBACK_RADIUS else it[it.size / 2] }
            val radii = FloatArray(ids.size) { (measured[it] ?: typical).coerceAtLeast(MIN_RADIUS) }
            return ClassMatcher(ids, mean, classes, radii)
        }

        /**
         * Labels each of [units] with its group of near-duplicates: the first earlier image it's at least
         * [DUPLICATE_SIMILARITY] like joins that image's group, otherwise it starts one. Labels run 0 until the number of groups.
         */
        fun duplicateGroups(units: List<FloatArray>): IntArray {
            val leaders = ArrayList<FloatArray>()
            return IntArray(units.size) { i ->
                var group = leaders.indexOfFirst { dot(it, units[i]) >= DUPLICATE_SIMILARITY }
                if (group < 0) {
                    group = leaders.size
                    leaders += units[i]
                }
                group
            }
        }

        /** Maps strictness 1..100 onto the radius margin: lenient accepts up to 1.3 radii, strict 0.9. */
        internal fun marginFor(strictness: Int): Float {
            val t = strictness.coerceIn(0, 100) / 100f
            return LENIENT_MARGIN + (STRICT_MARGIN - LENIENT_MARGIN) * t
        }

        private const val LENIENT_MARGIN = 1.3f
        private const val STRICT_MARGIN = 0.9f
    }
}

/**
 * One class's images as centred unit vectors, each weighted by 1 / the size of its near-duplicate group so a
 * group counts once.
 */
private class ClassModel(
    private val members: Array<FloatArray>,
    private val weights: FloatArray,
    private val groups: IntArray,
) {
    private val weightedSum = FloatArray(members.first().size).also { sum ->
        for (m in members.indices) for (i in sum.indices) sum[i] += weights[m] * members[m][i]
    }
    private val centroid = weightedSum.normalized()

    fun score(query: FloatArray): Float = score(query, centroid, excludedGroup = -1)

    /**
     * Centroid similarity blended with the mean similarity of the [ClassMatcher.NEIGHBOURS] nearest images,
     * leaving out [excludedGroup]'s images.
     */
    private fun score(query: FloatArray, centroid: FloatArray, excludedGroup: Int): Float {
        val similarities = FloatArray(members.size) { if (groups[it] == excludedGroup) -Float.MAX_VALUE else dot(query, members[it]) }
        val nearest = similarities.indices.sortedByDescending { similarities[it] }
        var total = 0f
        var taken = 0f
        for (m in nearest) {
            if (groups[m] == excludedGroup) break
            val take = min(weights[m], ClassMatcher.NEIGHBOURS - taken)
            total += take * similarities[m]
            taken += take
            if (taken >= ClassMatcher.NEIGHBOURS - WEIGHT_EPSILON) break
        }
        val toCentroid = dot(query, centroid)
        return (toCentroid + if (taken > 0f) total / taken else toCentroid) / 2
    }

    /**
     * The [ClassMatcher.COVERAGE] quantile of score distances when each group of this class (or an even spread
     * of [MAX_PROBES] of them) is scored against the others. Null with fewer than [MIN_GROUPS] groups.
     */
    fun measureRadius(): Float? {
        val groupCount = groups.max() + 1
        if (groupCount < MIN_GROUPS) return null
        val leaders = IntArray(groupCount).also { leaders -> for (m in members.indices.reversed()) leaders[groups[m]] = m }
        val probes = min(groupCount, MAX_PROBES)
        val distances = FloatArray(probes) { p ->
            val group = (p.toLong() * groupCount / probes).toInt()
            val others = weightedSum.copyOf()
            for (m in members.indices) if (groups[m] == group) for (i in others.indices) others[i] -= weights[m] * members[m][i]
            1f - score(members[leaders[group]], others.normalized(), excludedGroup = group)
        }.sorted()
        return distances[(ceil(ClassMatcher.COVERAGE * distances.size).toInt() - 1).coerceIn(distances.indices)]
    }

    private companion object {
        const val MIN_GROUPS = 5

        /** Most groups scored to measure the radius, bounding the cost of a refit on big classes. */
        const val MAX_PROBES = 32
        const val WEIGHT_EPSILON = 1e-4f
    }
}

private fun dot(a: FloatArray, b: FloatArray): Float {
    var sum = 0f
    for (i in a.indices) sum += a[i] * b[i]
    return sum
}

internal fun FloatArray.normalized(): FloatArray {
    val norm = sqrt(dot(this, this))
    return if (norm == 0f) copyOf() else FloatArray(size) { this[it] / norm }
}

/** [unit] moved so [mean] is the origin, renormalised; [unit] itself when there's no mean. */
private fun centred(unit: FloatArray, mean: FloatArray?): FloatArray =
    if (mean == null) unit else FloatArray(unit.size) { unit[it] - mean[it] }.normalized()

private fun weightedMean(vectors: List<FloatArray>, weights: List<Float>): FloatArray {
    val sum = FloatArray(vectors.first().size)
    for (v in vectors.indices) for (i in sum.indices) sum[i] += weights[v] * vectors[v][i]
    val total = weights.sum()
    return FloatArray(sum.size) { sum[it] / total }
}
