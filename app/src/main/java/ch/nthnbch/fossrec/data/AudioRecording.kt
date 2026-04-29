package ch.nthnbch.fossrec.data

import java.io.File

data class AudioRecording(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val durationMs: Long
)
