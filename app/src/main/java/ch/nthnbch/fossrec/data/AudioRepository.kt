package ch.nthnbch.fossrec.data

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AudioRepository(private val context: Context) {

    private val directory: File by lazy {
        // App-specific external storage. No storage permission needed for read/write here
        context.getExternalFilesDir(null) ?: context.filesDir
    }

    fun createNewRecordingFile(): File {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val dateString = formatter.format(Date())
        val fileName = "Recording_$dateString.m4a"
        return File(directory, fileName)
    }

    fun getRecordings(): List<AudioRecording> {
        val files = directory.listFiles()?.filter { it.extension == "m4a" } ?: emptyList()
        val retriever = MediaMetadataRetriever()
        
        val recordings = mutableListOf<AudioRecording>()
        for (file in files) {
            var duration = 0L
            try {
                retriever.setDataSource(file.absolutePath)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                duration = durationStr?.toLongOrNull() ?: 0L
            } catch (e: Exception) {
                // Ignore retriever failure
            }
            
            recordings.add(
                AudioRecording(
                    file = file,
                    name = file.nameWithoutExtension,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    durationMs = duration
                )
            )
        }
        
        try {
            retriever.release()
        } catch (e: Exception) {
            // Ignore release failure
        }
        
        return recordings.sortedByDescending { it.lastModified }
    }

    fun renameRecording(file: File, newName: String): Boolean {
        val newFile = File(directory, "$newName.m4a")
        if (newFile.exists() && newFile.absolutePath != file.absolutePath) return false
        return file.renameTo(newFile)
    }

    fun deleteRecording(file: File): Boolean {
        return file.delete()
    }
}
