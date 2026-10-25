package org.onedroid.dataset.domain

import org.onedroid.dataset.domain.usecase.ClassMatcher
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClassMatcherTest {
    private val random = Random(42)

    /** A random direction in a 64-dimensional embedding space. */
    private fun direction() = FloatArray(DIMENSIONS) { random.nextFloat() * 2 - 1 }

    /** An image of the thing [look] points at: the direction plus noise, so photos of it differ. */
    private fun photo(look: FloatArray, noise: Float = 0.6f) = FloatArray(DIMENSIONS) { look[it] + noise * (random.nextFloat() * 2 - 1) }

    /** Neighbouring camera frames: one photo, barely changed. */
    private fun burst(look: FloatArray, frames: Int): List<FloatArray> {
        val shot = photo(look)
        return List(frames) { FloatArray(DIMENSIONS) { i -> shot[i] + 0.02f * (random.nextFloat() * 2 - 1) } }
    }

    private val cat = direction()
    private val dog = direction()
    private val matcher = ClassMatcher.fit(mapOf(1 to List(12) { photo(cat) }, 2 to List(12) { photo(dog) }))

    @Test
    fun imagesGoToTheNearestClass() {
        repeat(10) {
            assertEquals(1, matcher.match(photo(cat), ClassMatcher.DEFAULT_STRICTNESS).classId)
            assertEquals(2, matcher.match(photo(dog), ClassMatcher.DEFAULT_STRICTNESS).classId)
        }
    }

    @Test
    fun imagesUnlikeEveryClassAreUnknown() {
        val match = matcher.match(photo(direction()), ClassMatcher.DEFAULT_STRICTNESS)
        assertTrue(match.isUnknown)
        // Still reports where it came closest.
        assertTrue(match.closestClassId == 1 || match.closestClassId == 2)
    }

    @Test
    fun strictnessZeroNeverReturnsUnknown() {
        assertTrue(matcher.match(photo(direction()), strictness = 0).classId in setOf(1, 2))
    }

    @Test
    fun stricterSettingsTurnBorderlineImagesUnknown() {
        val borderline = List(40) { photo(cat, noise = 0.85f) }
        val lenient = borderline.count { matcher.match(it, strictness = 1).isUnknown }
        val strict = borderline.count { matcher.match(it, strictness = 100).isUnknown }
        assertTrue(strict > lenient, "strict left $strict unknown, lenient $lenient")
    }

    @Test
    fun aLongBurstDoesNotPullTheClassAway() {
        // One class shot as a few photos plus a 40-frame burst of something that's half a dog.
        val catPhotos = List(5) { photo(cat) }
        val halfDog = FloatArray(DIMENSIONS) { (cat[it] + dog[it]) / 2 }
        val withBurst = ClassMatcher.fit(mapOf(1 to catPhotos + burst(halfDog, 40), 2 to List(6) { photo(dog) }))
        val dogs = List(30) { photo(dog) }
        assertTrue(dogs.count { withBurst.match(it, strictness = 0).classId == 2 } >= 28)
    }

    @Test
    fun classesWithSeveralLooksMatchEachLook() {
        // A mug from the side and from above look nothing alike, but both belong to it.
        val side = direction()
        val above = direction()
        val plate = FloatArray(DIMENSIONS) { (above[it] + direction()[it]) / 2 }
        val fitted = ClassMatcher.fit(mapOf(1 to List(8) { photo(side) } + List(8) { photo(above) }, 2 to List(12) { photo(plate) }))
        assertTrue(List(20) { photo(above) }.count { fitted.match(it, strictness = 0).classId == 1 } >= 18)
    }

    @Test
    fun nearDuplicatesShareAGroup() {
        val frames = burst(cat, 5)
        val groups = ClassMatcher.duplicateGroups(frames.map { it.unit() } + photo(dog).unit())
        assertEquals(listOf(0, 0, 0, 0, 0, 1), groups.toList())
    }

    @Test
    fun classesWithoutImagesAreIgnored() {
        val partial = ClassMatcher.fit(mapOf(1 to List(6) { photo(cat) }, 2 to emptyList()))
        assertEquals(1, partial.match(photo(dog), strictness = 0).classId)
    }

    @Test
    fun withNoImagesEverythingIsUnknown() {
        val empty = ClassMatcher.fit(mapOf(1 to emptyList()))
        assertTrue(empty.isEmpty)
        val match = empty.match(photo(cat), strictness = 0)
        assertNull(match.classId)
        assertNull(match.closestClassId)
    }

    @Test
    fun aSingleImageClassStillAcceptsCloseImages() {
        val single = ClassMatcher.fit(mapOf(7 to listOf(at(0.0))))
        assertEquals(7, single.match(at(3.0), ClassMatcher.DEFAULT_STRICTNESS).classId)
    }

    private fun at(degrees: Double): FloatArray {
        val radians = degrees * kotlin.math.PI / 180
        return floatArrayOf(cos(radians).toFloat(), sin(radians).toFloat(), 0f)
    }

    private fun FloatArray.unit(): FloatArray {
        val norm = kotlin.math.sqrt(sumOf { (it * it).toDouble() }).toFloat()
        return FloatArray(size) { this[it] / norm }
    }

    private companion object {
        const val DIMENSIONS = 64
    }
}
