package ch.nthnbch.fossrec.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Records the microphone to an M4A (MPEG-4 / AAC) file. */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null

    /** Returns false (and leaves no half-written file) if the recorder could not start. */
    fun start(outputFile: File): Boolean {
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return try {
            r.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(BITRATE)
                setAudioSamplingRate(SAMPLE_RATE)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            recorder = r
            true
        } catch (e: Exception) {
            runCatching { r.release() }
            outputFile.delete()
            false
        }
    }

    fun pause() {
        runCatching { recorder?.pause() }
    }

    fun resume() {
        runCatching { recorder?.resume() }
    }

    /** Returns true if the file was finalised correctly (it may be invalid for a near-instant recording). */
    fun stop(): Boolean {
        val r = recorder ?: return false
        recorder = null
        return try {
            r.stop()
            true
        } catch (e: RuntimeException) {
            false
        } finally {
            runCatching { r.release() }
        }
    }

    companion object {
        const val BITRATE = 192_000 // bits per second
        const val SAMPLE_RATE = 44_100

        /** Rough audio length that fits in [megabytes] MB at the configured bitrate. */
        fun minutesForMegabytes(megabytes: Int): Int =
            ((megabytes * 1_000_000L * 8) / BITRATE / 60).toInt()
    }
}
