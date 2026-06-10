package com.sinema.util

object TimeFormat {
    fun formatSeconds(seconds: Double): String = formatTotalSeconds(seconds.toInt())

    fun formatMs(ms: Long): String = formatTotalSeconds((ms / 1000).toInt())

    private fun formatTotalSeconds(totalSecs: Int): String {
        val h = totalSecs / 3600
        val m = (totalSecs % 3600) / 60
        val s = totalSecs % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }
}
