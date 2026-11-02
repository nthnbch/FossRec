package ch.nthnbch.fossrec.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

class Mp4SanitizerTest {

    private fun box(type: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            writeInt(8 + payload.size)
            write(type.toByteArray(Charsets.ISO_8859_1))
            write(payload)
        }
        return out.toByteArray()
    }

    private val creation = 0xE28C1D00.toInt()

    private fun timedBox(type: String): ByteArray {
        val payload = ByteArrayOutputStream()
        DataOutputStream(payload).apply {
            writeInt(0)          // version + flags
            writeInt(creation)   // creation time
            writeInt(creation + 1) // modification time
            writeInt(1000)       // timescale
        }
        return box(type, payload.toByteArray())
    }

    @Test
    fun clearsTimestampsAndMetadataWithoutMovingAnything() {
        val audio = ByteArray(64) { it.toByte() }
        val udta = box("udta", box("meta", "com.android.version=14".toByteArray()))
        val trak = box("trak", timedBox("tkhd") + box("mdia", timedBox("mdhd")))
        val moov = box("moov", timedBox("mvhd") + trak + udta)
        val ftyp = box("ftyp", "isom".toByteArray())
        val mdat = box("mdat", audio)
        val original = ftyp + mdat + moov

        val file = File.createTempFile("sanitize", ".m4a").apply { writeBytes(original) }
        assertTrue(Mp4Sanitizer.sanitize(file))
        val result = file.readBytes()
        file.delete()

        assertEquals(original.size, result.size)
        assertArrayEquals(mdat, result.copyOfRange(ftyp.size, ftyp.size + mdat.size))
        val text = result.toString(Charsets.ISO_8859_1)
        assertFalse(text.contains("com.android.version"))
        assertFalse(text.contains("udta"))
        assertFalse(text.contains("meta"))
        assertTrue(text.contains("free"))
        val stamp = byteArrayOf(0xE2.toByte(), 0x8C.toByte(), 0x1D, 0x00)
        assertTrue(indexOf(original, stamp) >= 0)
        assertEquals(-1, indexOf(result, stamp))
    }

    @Test
    fun leavesGarbageUntouched() {
        val file = File.createTempFile("garbage", ".m4a").apply { writeBytes(ByteArray(40) { 0x7F }) }
        val before = file.readBytes()
        Mp4Sanitizer.sanitize(file)
        assertArrayEquals(before, file.readBytes())
        file.delete()
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray): Int {
        outer@ for (i in 0..data.size - pattern.size) {
            for (j in pattern.indices) if (data[i + j] != pattern[j]) continue@outer
            return i
        }
        return -1
    }
}
