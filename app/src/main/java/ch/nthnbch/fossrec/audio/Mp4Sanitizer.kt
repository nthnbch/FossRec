package ch.nthnbch.fossrec.audio

import java.io.File
import java.io.RandomAccessFile

/**
 * Removes identifying metadata from an MP4/M4A file, in place and without re-encoding.
 *
 * Android's MediaRecorder never writes GPS unless asked to, but its MP4 writer does embed creation /
 * modification timestamps (movie, track and media headers) and a `meta` box carrying the Android
 * version. This:
 *  - sets every creation / modification time to 0,
 *  - turns any `udta` / `meta` box into a zero-filled `free` box of the same size,
 * so no offset in the file changes and the audio stays bit-identical.
 */
object Mp4Sanitizer {

    private val CONTAINERS = setOf("moov", "trak", "mdia", "minf", "stbl", "edts")
    private val STRIPPED = setOf("udta", "meta")

    fun sanitize(file: File): Boolean = try {
        RandomAccessFile(file, "rw").use { raf -> walk(raf, 0L, raf.length()) }
        true
    } catch (e: Exception) {
        false
    }

    private fun walk(raf: RandomAccessFile, start: Long, end: Long) {
        var pos = start
        while (pos + 8 <= end) {
            raf.seek(pos)
            val size32 = raf.readInt().toLong() and 0xFFFFFFFFL
            val type = ByteArray(4).also { raf.readFully(it) }.toString(Charsets.ISO_8859_1)
            var header = 8L
            val size = when (size32) {
                0L -> end - pos
                1L -> { header = 16L; raf.readLong() }
                else -> size32
            }
            if (size < header || pos + size > end) return // malformed: stop rather than corrupt

            val payload = pos + header
            when (type) {
                "mvhd", "tkhd", "mdhd" -> zeroTimes(raf, payload)
                in STRIPPED -> {
                    raf.seek(pos + header - 4)
                    raf.write("free".toByteArray(Charsets.ISO_8859_1))
                    raf.write(ByteArray((pos + size - payload).toInt()))
                }
                in CONTAINERS -> walk(raf, payload, pos + size)
            }
            pos += size
        }
    }

    /** Full box: version(1) flags(3), then creation + modification time (4 bytes each, 8 if version 1). */
    private fun zeroTimes(raf: RandomAccessFile, payload: Long) {
        raf.seek(payload)
        val version = raf.readUnsignedByte()
        raf.seek(payload + 4)
        raf.write(ByteArray(if (version == 1) 16 else 8))
    }
}
