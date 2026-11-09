package org.onedroid.dataset.domain

import org.onedroid.dataset.domain.usecase.ClassMatch
import org.onedroid.dataset.domain.usecase.LabeledEmbedding
import org.onedroid.dataset.domain.usecase.LeaveOneOut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LeaveOneOutTest {
    private fun at(x: Float, y: Float) = floatArrayOf(x, y)

    private fun angle(degrees: Double): FloatArray {
        val radians = degrees * kotlin.math.PI / 180
        return floatArrayOf(kotlin.math.cos(radians).toFloat(), kotlin.math.sin(radians).toFloat())
    }

    @Test
    fun separableClassesScorePerfectly() {
        val samples = LeaveOneOut.label(
            mapOf(
                // Far enough apart within a class not to count as near-duplicates.
                1 to listOf(angle(0.0), angle(40.0), angle(-40.0)),
                2 to listOf(angle(180.0), angle(140.0), angle(220.0)),
            ),
        )
        val evaluation = LeaveOneOut.evaluate(samples, strictness = 0)
        assertEquals(6, evaluation.evaluated)
        assertEquals(1f, evaluation.accuracy)
        assertEquals(1f, evaluation.acceptedCorrectly)
        assertEquals(0f, evaluation.falseUnknown)
    }

    @Test
    fun heldOutImagesLeaveTheirNearDuplicatesOut() {
        val seen = mutableListOf<Int>()
        val samples = listOf(
            LabeledEmbedding(1, at(1f, 0f), group = 0),
            LabeledEmbedding(1, at(1f, 0.01f), group = 0),
            LabeledEmbedding(2, at(0f, 1f), group = 1),
        )
        LeaveOneOut.evaluate(samples, heldOut = samples.take(1)) { byClass ->
            seen += byClass.values.sumOf { it.size }
            val unknown: (FloatArray, Int) -> ClassMatch = { _, _ -> ClassMatch(classId = null, closestClassId = null, similarity = 0f) }
            unknown
        }
        // Only the other class's image is left to fit on.
        assertEquals(listOf(1), seen)
    }

    @Test
    fun labelGroupsBurstsWithinEachClass() {
        val labeled = LeaveOneOut.label(mapOf(1 to listOf(at(1f, 0f), at(1f, 0.01f)), 2 to listOf(at(1f, 0f))))
        assertEquals(labeled[0].group, labeled[1].group)
        assertTrue(labeled[2].group != labeled[0].group)
    }
}
