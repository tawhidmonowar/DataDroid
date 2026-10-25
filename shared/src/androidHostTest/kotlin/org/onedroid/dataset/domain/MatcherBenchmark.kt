package org.onedroid.dataset.domain

import org.onedroid.dataset.domain.usecase.ClassMatch
import org.onedroid.dataset.domain.usecase.ClassMatcher
import org.onedroid.dataset.domain.usecase.LabeledEmbedding
import org.onedroid.dataset.domain.usecase.LeaveOneOut
import org.onedroid.dataset.testutil.LegacyClassMatcher
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random
import kotlin.test.Test

/**
 * Compares [ClassMatcher] with [LegacyClassMatcher] on real embeddings. Skipped unless
 * DATADROID_EMBEDDINGS names one or more embedding files (comma-separated), as written by
 * tools/embed_dataset.py: little-endian int magic "DDEV", int dimensions, then per image int class, int source image,
 * int frame (0 for the photo itself, 1.. for simulated burst frames), and the embedding.
 */
class MatcherBenchmark {
    private class Image(val sample: LabeledEmbedding, val frame: Int)

    private class Scenario(val name: String, val photos: Int, val maxBursts: Int)

    private val scenarios = listOf(
        Scenario("5 photos/class", 5, 0),
        Scenario("20 photos/class", 20, 0),
        Scenario("60 photos/class", 60, 0),
        Scenario("10 photos + 0-4 bursts", 10, 4),
        Scenario("30 photos + 0-4 bursts", 30, 4),
    )

    private val matchers: List<Pair<String, (Map<Int, List<FloatArray>>) -> (FloatArray, Int) -> ClassMatch>> = listOf(
        "before" to { byClass -> LegacyClassMatcher.fit(byClass)::match },
        "after" to { byClass -> ClassMatcher.fit(byClass)::match },
    )

    @Test
    fun compareMatchers() {
        val files = System.getenv("DATADROID_EMBEDDINGS")?.split(',')?.filter { it.isNotBlank() } ?: return
        for (path in files) {
            val images = read(File(path))
            val classes = images.map { it.sample.classId }.distinct().sorted()
            println("\n== ${File(path).name}: ${classes.size} classes, $REPEATS draws per scenario ==")
            println("Closed-set leave-one-out accuracy (nearest class is the right one)")
            for (scenario in scenarios) {
                // Both matchers see the same draws, so their difference is compared draw by draw.
                val results = matchers.map { (_, fit) ->
                    List(REPEATS) { draw ->
                        val samples = draw(images, scenario, Random(draw))
                        val heldOut = samples.filter { it.frame == 0 }.map { it.sample }
                        LeaveOneOut.evaluate(samples.map { it.sample }, strictness = 0, heldOut = heldOut, fit = fit).accuracy.toDouble()
                    }
                }
                val differences = results[1].indices.map { results[1][it] - results[0][it] }
                val halfWidth = T_975_9 * differences.sd() / kotlin.math.sqrt(REPEATS.toDouble())
                println(
                    "  %-24s before %.3f ± %.3f   after %.3f ± %.3f   difference %+.1f points (95%% CI %+.1f to %+.1f, after better in %d/%d draws)".format(
                        scenario.name, results[0].average(), results[0].sd(), results[1].average(), results[1].sd(),
                        differences.average() * 100, (differences.average() - halfWidth) * 100, (differences.average() + halfWidth) * 100,
                        differences.count { it > 0 }, REPEATS,
                    ),
                )
            }

            println("Unknown detection: one class left out as unknown; known images kept correctly / unknown images rejected")
            val strictnesses = listOf(0, 25, 50, 75, 100)
            for ((name, fit) in matchers) {
                val kept = DoubleArray(strictnesses.size)
                val rejected = DoubleArray(strictnesses.size)
                var known = 0
                var unknown = 0
                scenarios.forEach { scenario ->
                    repeat(REPEATS) { draw ->
                        val samples = draw(images, scenario, Random(draw))
                        val unknownClass = classes[draw % classes.size]
                        val knownSamples = samples.filter { it.sample.classId != unknownClass }
                        for (image in knownSamples.filter { it.frame == 0 }) {
                            val rest = knownSamples.filter { it.sample.group != image.sample.group }
                            val match = fit(rest.groupBy({ it.sample.classId }, { it.sample.embedding }))
                            strictnesses.forEachIndexed { i, s -> if (match(image.sample.embedding, s).classId == image.sample.classId) kept[i]++ }
                            known++
                        }
                        val match = fit(knownSamples.groupBy({ it.sample.classId }, { it.sample.embedding }))
                        for (image in samples.filter { it.sample.classId == unknownClass && it.frame == 0 }) {
                            strictnesses.forEachIndexed { i, s -> if (match(image.sample.embedding, s).isUnknown) rejected[i]++ }
                            unknown++
                        }
                    }
                }
                println("  $name: " + strictnesses.indices.joinToString("   ") { i ->
                    "s=${strictnesses[i]} %.3f/%.3f".format(kept[i] / known, rejected[i] / unknown)
                })
            }
        }
    }

    /**
     * Refit and match time as the dataset grows: each file's images are shuffled per class and cut to a range of
     * sizes. Prints `cost,<matcher>,<images>,<median fit ms>,<mean match ms>` lines.
     */
    @Test
    fun measureCost() {
        val files = System.getenv("DATADROID_EMBEDDINGS")?.split(',')?.filter { it.isNotBlank() } ?: return
        for (path in files) {
            val byClass = read(File(path)).groupBy { it.sample.classId }.mapValues { (_, images) -> images.shuffled(Random(0)) }
            val total = byClass.values.sumOf { it.size }
            println("\n== ${File(path).name}: cost by dataset size (JVM, median of $COST_RUNS fits after warm-up) ==")
            for (size in COST_SIZES.filter { it <= total } + total) {
                val subset = byClass.mapValues { (_, images) -> images.take(size * images.size / total).map { it.sample.embedding } }
                val queries = subset.values.flatten().take(COST_QUERIES)
                for ((name, fit) in matchers) {
                    repeat(COST_WARMUPS) { fit(subset) }
                    val fits = List(COST_RUNS) { kotlin.system.measureNanoTime { fit(subset) } / 1e6 }.sorted()
                    val fitted = fit(subset)
                    queries.forEach { fitted(it, 50) }
                    val matchMillis = kotlin.system.measureNanoTime { queries.forEach { fitted(it, 50) } } / 1e6 / queries.size
                    println("cost,$name,${subset.values.sumOf { it.size }},%.2f,%.4f".format(fits[fits.size / 2], matchMillis))
                }
            }
        }
    }

    /** Per class, [Scenario.photos] single photos and 0 to [Scenario.maxBursts] bursts with all their frames. */
    private fun draw(images: List<Image>, scenario: Scenario, random: Random): List<Image> =
        images.groupBy { it.sample.classId }.values.flatMap { classImages ->
            val bySource = classImages.groupBy { it.sample.group }
            val (bursts, photos) = bySource.values.partition { frames -> frames.size > 1 }
            val bursted = if (scenario.maxBursts == 0) 0 else random.nextInt(scenario.maxBursts + 1)
            photos.shuffled(random).take(scenario.photos).flatten() + bursts.shuffled(random).take(bursted).flatten()
        }

    private fun read(file: File): List<Image> {
        val buffer = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        check(buffer.int == MAGIC) { "$file isn't an embeddings file" }
        val dimensions = buffer.int
        val images = ArrayList<Image>()
        while (buffer.remaining() >= 12 + dimensions * 4) {
            val classId = buffer.int
            val source = buffer.int
            val frame = buffer.int
            val embedding = FloatArray(dimensions) { buffer.float }
            images += Image(LabeledEmbedding(classId, embedding, source), frame)
        }
        return images
    }

    /** Sample standard deviation. */
    private fun List<Double>.sd(): Double {
        val mean = average()
        return kotlin.math.sqrt(sumOf { (it - mean) * (it - mean) } / (size - 1))
    }

    private companion object {
        /** "DDEV": DataDroid evaluation embeddings. */
        const val MAGIC = 0x44444556
        const val REPEATS = 10
        val COST_SIZES = listOf(250, 500, 1000, 2000, 3000, 4000, 5000)
        const val COST_WARMUPS = 5
        const val COST_RUNS = 9
        const val COST_QUERIES = 200

        /** Student's t 97.5th percentile with REPEATS - 1 = 9 degrees of freedom. */
        const val T_975_9 = 2.262
    }
}
