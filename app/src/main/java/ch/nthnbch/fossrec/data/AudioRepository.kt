package ch.nthnbch.fossrec.data

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioRepository(private val context: Context) {

    private val directory: File by lazy {
        // App-specific external storage: no storage permission needed.
        context.getExternalFilesDir(null) ?: context.filesDir
    }

    /** Base name shared by every file of one recording session. */
    fun newSessionName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "Recording_$stamp"
    }

    /** File for a session; [part] is null for a standalone recording, otherwise 1-based. */
    fun fileFor(sessionName: String, part: Int?): File {
        val name = if (part == null) sessionName else "%s_part%02d".format(Locale.US, sessionName, part)
        return File(directory, "$name.$EXTENSION")
    }

    /** Turns `<session>_part01.m4a` back into `<session>.m4a` when a session was never actually split. */
    fun unsplit(file: File): File {
        val match = AudioRecording.PART_REGEX.matchEntire(file.nameWithoutExtension) ?: return file
        val target = File(directory, "${match.groupValues[1]}.$EXTENSION")
        return if (!target.exists() && file.renameTo(target)) target else file
    }

    fun getRecordings(): List<AudioRecording> {
        val files = directory.listFiles()?.filter { it.extension == EXTENSION } ?: emptyList()
        val retriever = MediaMetadataRetriever()
        val recordings = files.map { file ->
            val duration = try {
                retriever.setDataSource(file.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } catch (e: Exception) {
                0L // unreadable or still being written
            }
            AudioRecording(
                file = file,
                name = file.nameWithoutExtension,
                sizeBytes = file.length(),
                lastModified = file.lastModified(),
                durationMs = duration
            )
        }
        runCatching { retriever.release() }
        return recordings.sortedByDescending { it.lastModified }
    }

    fun renameRecording(file: File, newName: String): Boolean {
        val safeName = newName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (safeName.isEmpty()) return false
        val newFile = File(directory, "$safeName.$EXTENSION")
        if (newFile.exists() && newFile.absolutePath != file.absolutePath) return false
        return file.renameTo(newFile)
    }

    fun deleteRecording(file: File): Boolean = file.delete()

    companion object {
        const val EXTENSION = "m4a"
    }
}
