package org.onedroid.dataset.data

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.onedroid.dataset.data.archive.FileZipSource
import org.onedroid.dataset.data.archive.ZipReader
import org.onedroid.dataset.data.archive.ZipWriter
import org.onedroid.dataset.testutil.tempFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ZipRoundTripTest {

    @Test
    fun entriesWrittenAreReadBackUnchanged() {
        val files = linkedMapOf(
            "Animals/metadata.json" to """{"name":"Animals"}""".encodeToByteArray(),
            "Animals/cat/cat_0001.jpg" to ByteArray(1000) { it.toByte() },
            "Animals/ünïcödé/empty.png" to ByteArray(0),
        )
        val buffer = Buffer()
        val writer = ZipWriter(buffer)
        files.forEach { (name, data) -> writer.add(name, data) }
        writer.finish()

        val read = ZipReader.read(buffer.readByteArray())
        assertEquals(files.keys.toList(), read.keys.toList())
        files.forEach { (name, data) -> assertContentEquals(data, read.getValue(name)) }
    }

    @Test
    fun entriesAreReadFromAFileOnDemand() {
        val buffer = Buffer()
        ZipWriter(buffer).apply {
            add("a/1.bin", ByteArray(70_000) { (it % 251).toByte() })
            add("a/2.bin", "two".encodeToByteArray())
            finish()
        }
        val entries = ZipReader.entries(FileZipSource(tempFile(buffer.readByteArray())))
        assertEquals(listOf("a/1.bin", "a/2.bin"), entries.map { it.name })
        assertContentEquals("two".encodeToByteArray(), entries[1].read())
        assertContentEquals(ByteArray(70_000) { (it % 251).toByte() }, entries[0].read())
    }

    @Test
    fun missingOrEmptyFileIsNotAZip() {
        assertFailsWith<IllegalArgumentException> { ZipReader.entries(FileZipSource(tempFile(ByteArray(0)))) }
        assertFailsWith<IllegalArgumentException> { ZipReader.entries(FileZipSource("/nonexistent/archive.zip")) }
    }

    @Test
    fun garbageIsNotAZip() {
        assertFailsWith<IllegalArgumentException> { ZipReader.entries(ByteArray(100) { 7 }) }
    }

    @Test
    fun cannotAddAfterFinish() {
        val writer = ZipWriter(Buffer())
        writer.finish()
        assertFailsWith<IllegalStateException> { writer.add("a", ByteArray(1)) }
    }
}
