package ch.nthnbch.fossrec.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing and sizing follow the Fibonacci sequence: each step is ~1.618 (the golden ratio, φ)
 * times the previous one, so every size relates harmoniously to its neighbours.
 */
object Dimens {
    val s1 = 3.dp
    val s2 = 5.dp
    val s3 = 8.dp
    val s4 = 13.dp
    val s5 = 21.dp
    val s6 = 34.dp
    val s7 = 55.dp
    val s8 = 89.dp

    /** Icon glyph size and its touch target (34 × φ ≈ 55). */
    val icon = s6
    val iconTarget = s7

    /** Main record button. */
    val fabMain = s8
    val fabMainIcon = s7
}
