package ch.nthnbch.fossrec.data

/** User-configurable recording options. */
data class RecorderSettings(
    val splitBySize: Boolean = false,
    val maxSizeMb: Int = 100,
    val splitByDuration: Boolean = false,
    val maxDurationMinutes: Int = 15
) {
    val splitEnabled: Boolean get() = splitBySize || splitByDuration

    /** Size threshold in bytes, or 0 when disabled. Decimal MB, with a 2 % safety margin for the MP4 index. */
    val maxSizeBytes: Long
        get() = if (splitBySize) (maxSizeMb * 1_000_000L * 98) / 100 else 0L

    /** Duration threshold in seconds, or 0 when disabled. */
    val maxDurationSeconds: Long
        get() = if (splitByDuration) maxDurationMinutes * 60L else 0L

    companion object {
        val SIZE_PRESETS_MB = listOf(25, 50, 100, 200, 500)
        val DURATION_PRESETS_MIN = listOf(5, 10, 15, 30, 60)
    }
}
