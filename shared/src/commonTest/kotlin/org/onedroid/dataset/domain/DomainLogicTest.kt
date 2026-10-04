package org.onedroid.dataset.domain

import org.onedroid.dataset.core.domain.Result
import org.onedroid.dataset.domain.model.BalanceIssue
import org.onedroid.dataset.domain.model.DatasetClass
import org.onedroid.dataset.domain.model.LabeledImage
import org.onedroid.dataset.domain.usecase.ClassNaming
import org.onedroid.dataset.domain.usecase.ExportNaming
import org.onedroid.dataset.domain.usecase.ImageFolders
import org.onedroid.dataset.domain.usecase.NameError
import org.onedroid.dataset.domain.usecase.computeClassBalance
import org.onedroid.dataset.domain.usecase.uniqueName
import org.onedroid.dataset.domain.usecase.validateName
import org.onedroid.dataset.testutil.fakeJpeg
import org.onedroid.dataset.testutil.source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomainLogicTest {

    private fun imageClass(id: Int, name: String, count: Int) = DatasetClass(id, name, List(count) { it.toLong() + id * 1000 })

    @Test
    fun balancedDatasetHasNoIssues() {
        val balance = computeClassBalance(listOf(imageClass(1, "cat", 30), imageClass(2, "dog", 25)))
        assertTrue(balance.isBalanced)
        assertEquals(55, balance.totalImages)
        assertEquals(27.5f, balance.meanCount)
        assertEquals(1.2f, balance.imbalanceRatio)
    }

    @Test
    fun reportsEmptySmallAndImbalancedClasses() {
        val balance = computeClassBalance(listOf(imageClass(1, "cat", 100), imageClass(2, "dog", 5), imageClass(3, "bird", 0)))
        assertEquals(
            listOf(
                BalanceIssue.EmptyClasses(listOf("bird")),
                BalanceIssue.FewImages(listOf("dog"), 20),
                BalanceIssue.Imbalanced(20f, "cat", "dog"),
            ),
            balance.issues,
        )
    }

    @Test
    fun singleClassNeedsAnother() {
        val balance = computeClassBalance(listOf(imageClass(1, "cat", 30)))
        assertEquals(listOf<BalanceIssue>(BalanceIssue.NeedsTwoClasses), balance.issues)
        assertNull(balance.imbalanceRatio)
    }

    @Test
    fun nameValidation() {
        assertEquals(NameError.BLANK, (validateName("  ") as Result.Error).error)
        assertEquals(NameError.TOO_LONG, (validateName("x".repeat(41)) as Result.Error).error)
        assertEquals(NameError.DUPLICATE, (validateName(" Cat ", listOf("cat")) as Result.Error).error)
        assertTrue(validateName("Cat", listOf("Dog")) is Result.Success)
        assertEquals("Class 3", uniqueName("Class 3", listOf("Class 1")))
        assertEquals("Class 3 3", uniqueName("Class 3", listOf("class 3", "Class 3 2")))
    }

    @Test
    fun groupsImagesByClassFolderSkippingWrappersAndJunk() {
        val images = listOf(
            LabeledImage(listOf("Animals", "cat"), "1.jpg", source(fakeJpeg("1"))),
            LabeledImage(listOf("Animals", "Cat"), "2.JPG", source(fakeJpeg("2"))),
            LabeledImage(listOf("Animals", "dog"), "3.png", source(fakeJpeg("3"))),
            LabeledImage(listOf("Animals", "dog"), "notes.txt", source(fakeJpeg("4"))),
            LabeledImage(listOf("__MACOSX", "Animals", "dog"), "._3.png", source(fakeJpeg("5"))),
        )
        val groups = ImageFolders.analyze(images).groups(ClassNaming.TOP_FOLDER, mergeSplits = true)
        assertEquals(listOf("cat", "dog"), groups.keys.toList())
        assertEquals(listOf(2, 1), groups.values.map { it.size })
    }

    @Test
    fun exportNamesAreSafeAndUnique() {
        assertEquals("Animal_Dataset", ExportNaming.rootFolder(" Animal Dataset "))
        assertEquals("dataset", ExportNaming.rootFolder("///"))
        assertEquals(listOf("cat", "Cat_2", "a_b", "class_4"), ExportNaming.classFolders(listOf("cat", "Cat", "a/ b", "??")))
    }
}
