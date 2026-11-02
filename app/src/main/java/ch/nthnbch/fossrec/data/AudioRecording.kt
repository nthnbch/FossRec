package ch.nthnbch.fossrec.data

import java.io.File

data class AudioRecording(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val durationMs: Long
) {
    private val partMatch: MatchResult? get() = PART_REGEX.matchEntire(name)

    /** Shared name of all parts of one split session, or null for a standalone recording. */
    val groupKey: String? get() = partMatch?.groupValues?.get(1)

    /** 1-based part number inside its session (0 for a standalone recording). */
    val partIndex: Int get() = partMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0

    companion object {
        /** `<session>_partNN` — files produced when a recording is split. */
        val PART_REGEX = Regex("^(.+)_part(\\d+)$")
    }
}

enum class GroupPosition { NONE, MIDDLE, LAST }

/** One row of the recordings list: a standalone recording, a session header, or a part of a session. */
sealed interface ListRow {
    val key: String

    data class Item(
        val recording: AudioRecording,
        val position: GroupPosition = GroupPosition.NONE
    ) : ListRow {
        override val key: String get() = recording.file.absolutePath
    }

    data class GroupHeader(val groupKey: String, val parts: List<AudioRecording>) : ListRow {
        override val key: String get() = "group:$groupKey"
        val totalDurationMs: Long get() = parts.sumOf { it.durationMs }
        val totalSizeBytes: Long get() = parts.sumOf { it.sizeBytes }
    }
}

/** Groups already-sorted recordings: parts of the same session are gathered under one header, in part order. */
fun buildRows(sorted: List<AudioRecording>): List<ListRow> {
    val byGroup = sorted.filter { it.groupKey != null }.groupBy { it.groupKey!! }
    val emitted = HashSet<String>()
    val rows = mutableListOf<ListRow>()
    for (rec in sorted) {
        val key = rec.groupKey
        val parts = key?.let { byGroup[it] }
        if (key == null || parts == null || parts.size < 2) {
            rows += ListRow.Item(rec)
            continue
        }
        if (!emitted.add(key)) continue
        val ordered = parts.sortedBy { it.partIndex }
        rows += ListRow.GroupHeader(key, ordered)
        ordered.forEachIndexed { i, part ->
            rows += ListRow.Item(part, if (i == ordered.lastIndex) GroupPosition.LAST else GroupPosition.MIDDLE)
        }
    }
    return rows
}
