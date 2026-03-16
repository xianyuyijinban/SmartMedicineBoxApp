package com.smartmedicine.data.model

import java.util.Locale

/**
 * 设备时间戳格式化工具
 *
 * 固件侧 timestamp 为设备启动后的毫秒计时，不是 Unix epoch。
 */
object DeviceTimestampFormatter {

    fun formatUptime(timestampMs: Long): String {
        val safeMs = timestampMs.coerceAtLeast(0L)
        val hours = safeMs / 3_600_000L
        val minutes = (safeMs % 3_600_000L) / 60_000L
        val seconds = (safeMs % 60_000L) / 1_000L
        val millis = safeMs % 1_000L

        return String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d.%03d",
            hours,
            minutes,
            seconds,
            millis
        )
    }
}
