package org.onedroid.dataset.domain.model

/** How images are spread over a dataset's classes, and what may need attention before training. */
data class ClassBalance(
    val classes: List<ClassShare>,
    val totalImages: Int,
    val minCount: Int,
    val maxCount: Int,
    val meanCount: Float,
    /** Largest class over smallest non-empty class; null when fewer than two classes have images. */
    val imbalanceRatio: Float?,
    val issues: List<BalanceIssue>,
) {
    val isBalanced: Boolean get() = issues.isEmpty()
}

data class ClassShare(val classId: Int, val name: String, val count: Int, val share: Float)

sealed interface BalanceIssue {
    /** Classification needs at least two classes with images. */
    data object NeedsTwoClasses : BalanceIssue

    data class EmptyClasses(val names: List<String>) : BalanceIssue

    data class FewImages(val names: List<String>, val recommended: Int) : BalanceIssue

    data class Imbalanced(val ratio: Float, val largest: String, val smallest: String) : BalanceIssue
}
