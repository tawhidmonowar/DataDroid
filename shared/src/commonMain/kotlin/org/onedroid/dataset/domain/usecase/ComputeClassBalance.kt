package org.onedroid.dataset.domain.usecase

import org.onedroid.dataset.domain.model.BalanceIssue
import org.onedroid.dataset.domain.model.ClassBalance
import org.onedroid.dataset.domain.model.ClassShare
import org.onedroid.dataset.domain.model.DatasetClass

/**
 * Summarises how [classes] are balanced. A class counts as small below [recommendedPerClass] images,
 * and the dataset as imbalanced once its largest class is [maxImbalanceRatio] times its smallest.
 */
fun computeClassBalance(
    classes: List<DatasetClass>,
    recommendedPerClass: Int = RECOMMENDED_IMAGES_PER_CLASS,
    maxImbalanceRatio: Float = MAX_IMBALANCE_RATIO,
): ClassBalance {
    val total = classes.sumOf { it.imageIds.size }
    val counts = classes.map { it.imageIds.size }
    val nonEmpty = classes.filter { it.imageIds.isNotEmpty() }
    val largest = nonEmpty.maxByOrNull { it.imageIds.size }
    val smallest = nonEmpty.minByOrNull { it.imageIds.size }
    val ratio = if (nonEmpty.size >= 2 && largest != null && smallest != null) {
        largest.imageIds.size.toFloat() / smallest.imageIds.size
    } else {
        null
    }

    val issues = buildList {
        if (nonEmpty.size < 2) add(BalanceIssue.NeedsTwoClasses)
        val empty = classes.filter { it.imageIds.isEmpty() }
        if (empty.isNotEmpty() && total > 0) add(BalanceIssue.EmptyClasses(empty.map { it.name }))
        val few = nonEmpty.filter { it.imageIds.size < recommendedPerClass }
        if (few.isNotEmpty()) add(BalanceIssue.FewImages(few.map { it.name }, recommendedPerClass))
        if (ratio != null && ratio >= maxImbalanceRatio) {
            add(BalanceIssue.Imbalanced(ratio, largest!!.name, smallest!!.name))
        }
    }

    return ClassBalance(
        classes = classes.map { ClassShare(it.id, it.name, it.imageIds.size, if (total == 0) 0f else it.imageIds.size.toFloat() / total) },
        totalImages = total,
        minCount = counts.minOrNull() ?: 0,
        maxCount = counts.maxOrNull() ?: 0,
        meanCount = if (classes.isEmpty()) 0f else total.toFloat() / classes.size,
        imbalanceRatio = ratio,
        issues = issues,
    )
}

const val RECOMMENDED_IMAGES_PER_CLASS = 20
const val MAX_IMBALANCE_RATIO = 1.5f
