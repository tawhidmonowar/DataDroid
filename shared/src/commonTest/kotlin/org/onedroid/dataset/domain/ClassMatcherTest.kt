package org.onedroid.dataset.domain

import org.onedroid.dataset.domain.usecase.ClassMatcher
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClassMatcherTest {
    /** A unit vector in the x-y plane at [degrees], with a little spread in z so classes have a radius. */
    private fun at(degrees: Double, z: Float = 0f): FloatArray {
        val radians = degrees * kotlin.math.PI / 180
        return floatArrayOf(cos(radians).toFloat(), sin(radians).toFloat(), z)
    }

    private fun cluster(degrees: Double) = List(6) { at(degrees + it - 2.5, z = (it - 2.5f) / 50f) }

    private val matcher = ClassMatcher.fit(mapOf(1 to cluster(0.0), 2 to cluster(90.0)))

    @Test
    fun imagesGoToTheNearestClass() {
        assertEquals(1, matcher.match(at(5.0), ClassMatcher.DEFAULT_STRICTNESS).classId)
        assertEquals(2, matcher.match(at(85.0), ClassMatcher.DEFAULT_STRICTNESS).classId)
    }

    @Test
    fun imagesUnlikeEveryClassAreUnknown() {
        val match = matcher.match(at(45.0), ClassMatcher.DEFAULT_STRICTNESS)
        assertTrue(match.isUnknown)
        // Still reports where it came closest.
        assertTrue(match.closestClassId == 1 || match.closestClassId == 2)
    }

    @Test
    fun strictnessZeroNeverReturnsUnknown() {
        assertEquals(1, matcher.match(at(40.0), strictness = 0).classId)
        assertEquals(2, matcher.match(at(50.0), strictness = 0).classId)
    }

    @Test
    fun stricterSettingsTurnBorderlineImagesUnknown() {
        val borderline = at(35.0)
        assertEquals(1, matcher.match(borderline, strictness = 1).classId)
        assertTrue(matcher.match(borderline, strictness = 100).isUnknown)
    }

    @Test
    fun classesWithoutImagesAreIgnored() {
        val partial = ClassMatcher.fit(mapOf(1 to cluster(0.0), 2 to emptyList()))
        assertEquals(1, partial.match(at(80.0), strictness = 0).classId)
    }

    @Test
    fun withNoImagesEverythingIsUnknown() {
        val empty = ClassMatcher.fit(mapOf(1 to emptyList()))
        assertTrue(empty.isEmpty)
        val match = empty.match(at(0.0), strictness = 0)
        assertNull(match.classId)
        assertNull(match.closestClassId)
    }

    @Test
    fun aSingleImageClassStillAcceptsCloseImages() {
        val single = ClassMatcher.fit(mapOf(7 to listOf(at(0.0))))
        assertEquals(7, single.match(at(3.0), ClassMatcher.DEFAULT_STRICTNESS).classId)
    }
}
