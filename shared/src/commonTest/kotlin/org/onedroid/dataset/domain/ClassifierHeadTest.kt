package org.onedroid.dataset.domain

import org.onedroid.dataset.domain.usecase.ClassMatcher
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClassifierHeadTest {
    private val random = Random(11)

    /** A random direction in a 64-dimensional embedding space. */
    private fun direction() = FloatArray(DIMENSIONS) { random.nextFloat() * 2 - 1 }

    /** A photo of [look]: noisy enough that no two photos count as near-duplicates. */
    private fun photo(look: FloatArray) = FloatArray(DIMENSIONS) { look[it] + random.nextFloat() * 2 - 1 }

    /**
     * Images whose class shows in [looks], plus strong variation along a few [nuisances] shared by every class, like
     * lighting or background: what nearest-class scoring can't tell from class, and a trained head learns to ignore.
     */
    private class Confusing(val looks: List<FloatArray>, val nuisances: List<FloatArray>)

    private fun Confusing.photo(classIndex: Int) = FloatArray(DIMENSIONS) { looks[classIndex][it] + 0.5f * (random.nextFloat() * 2 - 1) }
        .also { image ->
            for (nuisance in nuisances) {
                val amount = 4f * (random.nextFloat() * 2 - 1)
                for (i in image.indices) image[i] += amount * nuisance[i]
            }
        }

    @Test
    fun aHeadIsTrainedOnceEveryClassHasEnoughDistinctImages() {
        val cat = direction()
        val dog = direction()
        val enough = ClassMatcher.MIN_HEAD_EXAMPLES
        assertTrue(ClassMatcher.fit(mapOf(1 to List(enough) { photo(cat) }, 2 to List(enough) { photo(dog) })).usesTrainedHead)
        assertFalse(ClassMatcher.fit(mapOf(1 to List(enough) { photo(cat) }, 2 to List(enough - 1) { photo(dog) })).usesTrainedHead)
        assertFalse(ClassMatcher.fit(mapOf(1 to List(40) { photo(cat) })).usesTrainedHead, "one class has nothing to tell apart")
        assertFalse(ClassMatcher.fit(mapOf(1 to List(40) { photo(cat) }, 2 to List(40) { photo(dog) }), trainHead = false).usesTrainedHead)
    }

    @Test
    fun aBurstCountsAsOneImageTowardsTraining() {
        val cat = direction()
        val shot = photo(cat)
        val burst = List(30) { FloatArray(DIMENSIONS) { i -> shot[i] + 0.02f * (random.nextFloat() * 2 - 1) } }
        val classes = mapOf(1 to List(ClassMatcher.MIN_HEAD_EXAMPLES - 2) { photo(cat) } + burst, 2 to List(20) { photo(direction()) })
        assertFalse(ClassMatcher.fit(classes).usesTrainedHead)
    }

    @Test
    fun aTrainedHeadSortsWhatSharedVariationConfuses() {
        val world = Confusing(looks = List(4) { direction() }, nuisances = List(3) { direction() })
        val training = world.looks.indices.associate { it to List(20) { _ -> world.photo(it) } }
        val queries = world.looks.indices.flatMap { c -> List(100) { c to world.photo(c) } }
        val untrained = ClassMatcher.fit(training, trainHead = false)
        val trained = ClassMatcher.fit(training)
        assertTrue(trained.usesTrainedHead)

        fun accuracy(matcher: ClassMatcher) = queries.count { (c, q) -> matcher.match(q, strictness = 0).classId == c } / queries.size.toFloat()
        val before = accuracy(untrained)
        val after = accuracy(trained)
        assertTrue(before < 0.8f, "nearest class should struggle here, got $before")
        assertTrue(after > 0.95f, "the trained head should sort nearly all, got $after (nearest class $before)")
    }

    @Test
    fun aTrainedHeadStillLeavesUnfamiliarImagesUnknown() {
        val cat = direction()
        val dog = direction()
        val matcher = ClassMatcher.fit(mapOf(1 to List(20) { photo(cat) }, 2 to List(20) { photo(dog) }))
        assertTrue(matcher.usesTrainedHead)
        // A softmax always picks a class; the radius check is what says "none of these".
        val unknowns = List(20) { photo(direction()) }.count { matcher.match(it, ClassMatcher.DEFAULT_STRICTNESS).isUnknown }
        assertTrue(unknowns >= 18, "expected nearly every unfamiliar image unknown, got $unknowns of 20")
        // Familiar images go to their own class, or at worst wait as unknown (the radius covers most, not all, of
        // a class's own images); never to the other class.
        repeat(10) {
            for ((classId, look) in listOf(1 to cat, 2 to dog)) {
                val match = matcher.match(photo(look), ClassMatcher.DEFAULT_STRICTNESS)
                assertEquals(classId, match.closestClassId)
                assertTrue(match.classId == classId || match.isUnknown)
            }
        }
    }

    @Test
    fun trainingIsDeterministic() {
        val looks = List(3) { direction() }
        val training = looks.indices.associate { it to List(15) { _ -> photo(looks[it]) } }
        val queries = List(30) { photo(looks[it % 3]) } + List(10) { direction() }
        val first = ClassMatcher.fit(training)
        val second = ClassMatcher.fit(training)
        assertTrue(first.usesTrainedHead)
        for (query in queries) assertEquals(first.match(query, 50), second.match(query, 50))
    }

    private companion object {
        const val DIMENSIONS = 64
    }
}
