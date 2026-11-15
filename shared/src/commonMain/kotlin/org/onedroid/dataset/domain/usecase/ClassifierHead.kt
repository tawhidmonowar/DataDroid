package org.onedroid.dataset.domain.usecase

import kotlin.math.abs
import kotlin.math.exp

/**
 * A linear classifier trained on frozen embeddings: transfer learning's "linear probe". The backbone keeps what it
 * learned from millions of images; only this one layer learns the dataset's classes, on the device, in moments.
 *
 * Each class's weights start at its prototype (its centroid), so before any training the head is exactly a
 * nearest-centroid classifier, and an L2 penalty keeps pulling the weights back towards the prototypes. With few
 * images the head stays close to nearest-centroid; with more, training moves each class's weights towards what
 * tells it apart from the other classes, which a centroid can't do as it never looks at the other classes.
 *
 * Training is full-batch gradient descent with momentum on a temperature-scaled softmax cross-entropy, balanced so
 * every class counts the same however many images it has. It's deterministic: the same examples give the same head.
 */
internal class ClassifierHead private constructor(private val weights: Array<FloatArray>) {

    /** Index of the class [query] (a centred unit vector, like the examples) most likely belongs to. */
    fun predict(query: FloatArray): Int {
        var best = 0
        var bestLogit = -Float.MAX_VALUE
        for (c in weights.indices) {
            val logit = dot(query, weights[c])
            if (logit > bestLogit) {
                best = c
                bestLogit = logit
            }
        }
        return best
    }

    companion object {
        /** Softmax temperature: logits are [SCALE] x the dot product, as embeddings and prototypes are unit vectors. */
        const val SCALE = 10f

        /** Strength of the pull back towards each class's prototype. */
        const val REGULARISATION = 0.02f

        const val EPOCHS = 30
        const val LEARNING_RATE = 0.06f
        const val MOMENTUM = 0.9f

        /** Gap between a probability and its target below which an example no longer moves a class's weights. */
        const val NEGLIGIBLE = 1e-3f

        /**
         * Trains a head on [examples], unit vectors by class index, starting from [prototypes] (one unit vector per
         * class, in the same order). Every class needs at least one example.
         */
        fun train(examples: List<List<FloatArray>>, prototypes: List<FloatArray>): ClassifierHead {
            require(examples.size == prototypes.size && examples.all { it.isNotEmpty() })
            val classCount = prototypes.size
            val dimensions = prototypes.first().size
            val weights = Array(classCount) { prototypes[it].copyOf() }
            val velocity = Array(classCount) { FloatArray(dimensions) }
            val gradient = Array(classCount) { FloatArray(dimensions) }
            val probabilities = FloatArray(classCount)
            repeat(EPOCHS) {
                for (c in 0 until classCount) {
                    for (i in 0 until dimensions) gradient[c][i] = REGULARISATION * (weights[c][i] - prototypes[c][i])
                }
                for (label in 0 until classCount) {
                    // Each class's examples share 1 / classCount of the loss, so classes count alike.
                    val exampleWeight = 1f / (classCount * examples[label].size)
                    for (x in examples[label]) {
                        softmax(weights, x, probabilities)
                        for (c in 0 until classCount) {
                            val target = if (c == label) 1f else 0f
                            val step = exampleWeight * SCALE * (probabilities[c] - target)
                            if (abs(probabilities[c] - target) < NEGLIGIBLE) continue
                            val g = gradient[c]
                            for (i in 0 until dimensions) g[i] += step * x[i]
                        }
                    }
                }
                for (c in 0 until classCount) {
                    val v = velocity[c]
                    val w = weights[c]
                    val g = gradient[c]
                    for (i in 0 until dimensions) {
                        v[i] = MOMENTUM * v[i] - LEARNING_RATE * g[i]
                        w[i] += v[i]
                    }
                }
            }
            return ClassifierHead(weights)
        }

        /** Fills [out] with the softmax of [SCALE] x each class's weights dotted with [x]. */
        private fun softmax(weights: Array<FloatArray>, x: FloatArray, out: FloatArray) {
            var max = -Float.MAX_VALUE
            for (c in weights.indices) {
                out[c] = SCALE * dot(x, weights[c])
                if (out[c] > max) max = out[c]
            }
            var sum = 0f
            for (c in out.indices) {
                out[c] = exp(out[c] - max)
                sum += out[c]
            }
            for (c in out.indices) out[c] /= sum
        }
    }
}
