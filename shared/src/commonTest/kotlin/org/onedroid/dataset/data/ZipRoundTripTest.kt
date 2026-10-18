package org.onedroid.dataset.data

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.onedroid.dataset.data.archive.ZipReader
import org.onedroid.dataset.data.archive.ZipWriter
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
