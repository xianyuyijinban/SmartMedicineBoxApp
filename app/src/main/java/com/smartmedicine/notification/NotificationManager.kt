package com.smartmedicine.notification

import android.app.NotificationChannel
import android.app.NotificationManager as AndroidNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import com.smartmedicine.ui.MainActivity

/**
 * 通知管理器
 * 
 * 处理系统通知的创建和显示
 * 根据接口文档要求，当设备异常时推送通知
 */
class NotificationManager(private val context: Context) {

    companion object {
        private const val TAG = "NotificationManager"
        
        // 通知渠道ID
        const val CHANNEL_ID_ALERTS = "medicine_box_alerts"
        const val CHANNEL_ID_STATUS = "medicine_box_status"
        const val CHANNEL_ID_GENERAL = "medicine_box_general"
        
        // 通知ID
        private const val NOTIFICATION_ID_OFFLINE = 1001
        private const val NOTIFICATION_ID_TEMPERATURE = 1002
        private const val NOTIFICATION_ID_HUMIDITY = 1003
        private const val NOTIFICATION_ID_TILTED = 1004
        private const val NOTIFICATION_ID_OPENED = 1005
        
        // 单例实例
        @Volatile
        private var instance: NotificationManager? = null
        
        fun getInstance(context: Context): NotificationManager {
            return instance ?: synchronized(this) {
                instance ?: NotificationManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val notificationManager: AndroidNotificationManager by lazy {
        context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
    }

    init {
        createNotificationChannels()
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 告警通知渠道 - 高优先级
            val alertChannel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "设备告警",
                AndroidNotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "设备离线、温度/湿度异常等重要告警"
                setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, 
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }

            // 状态通知渠道 - 默认优先级
            val statusChannel = NotificationChannel(
                CHANNEL_ID_STATUS,
                "设备状态",
                AndroidNotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "设备在线/离线状态变化通知"
                enableVibration(false)
            }

            // 一般通知渠道 - 低优先级
            val generalChannel = NotificationChannel(
                CHANNEL_ID_GENERAL,
                "一般通知",
                AndroidNotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "药箱打开、移动等一般事件"
                enableVibration(false)
            }

            notificationManager.createNotificationChannels(
                listOf(alertChannel, statusChannel, generalChannel)
            )
        }
    }

    /**
     * 获取打开MainActivity的PendingIntent
     */
    private fun getMainActivityPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 发送设备离线通知
     * @param offlineDuration 离线时长（毫秒）
     */
    fun notifyDeviceOffline(deviceId: String, offlineDuration: Long) {
        val durationText = formatDuration(offlineDuration)
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ 设备离线")
            .setContentText("$deviceId 已离线 $durationText")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$deviceId 已离线 $durationText，请检查设备网络连接"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(getMainActivityPendingIntent())
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))

        notificationManager.notify(NOTIFICATION_ID_OFFLINE, builder.build())
    }

    /**
     * 发送设备恢复在线通知
     */
    fun notifyDeviceOnline(deviceId: String) {
        // 取消离线通知
        notificationManager.cancel(NOTIFICATION_ID_OFFLINE)
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_STATUS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("✅ 设备恢复在线")
            .setContentText("$deviceId 已重新连接")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(getMainActivityPendingIntent())
            .setAutoCancel(true)

        notificationManager.notify(NOTIFICATION_ID_OFFLINE + 100, builder.build())
        
        // 3秒后自动清除在线通知
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            notificationManager.cancel(NOTIFICATION_ID_OFFLINE + 100)
        }, 3000)
    }

    /**
     * 发送温度异常通知
     * @param temperature 当前温度值
     * @param minThreshold 最低温度阈值（默认10°C）
     * @param maxThreshold 最高温度阈值（默认30°C）
     */
    fun notifyTemperatureAlert(
        deviceId: String, 
        temperature: Double,
        minThreshold: Double = 10.0,
        maxThreshold: Double = 30.0
    ) {
        val isHigh = temperature > maxThreshold
        val alertType = if (isHigh) "过高" else "过低"
        val thresholdValue = if (isHigh) maxThreshold else minThreshold
        val suggestion = if (isHigh) 
            "建议将药箱移至阴凉处" else "建议将药箱移至温暖处"
        // 根据温度异常程度设置不同颜色图标
        val color = if (isHigh) 0xFF0000 else 0x0066FF

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🌡️ 温度异常")
            .setContentText("当前温度 ${String.format("%.1f", temperature)}°C，$alertType（阈值：${String.format("%.0f", thresholdValue)}°C）")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$deviceId 温度$alertType\n当前：${String.format("%.1f", temperature)}°C\n阈值：${String.format("%.0f", minThreshold)}°C ~ ${String.format("%.0f", maxThreshold)}°C\n$suggestion"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(getMainActivityPendingIntent())
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 100, 300))
            .setColor(color)

        notificationManager.notify(NOTIFICATION_ID_TEMPERATURE, builder.build())
    }

    /**
     * 发送湿度异常通知
     * @param humidity 当前湿度值
     */
    fun notifyHumidityAlert(deviceId: String, humidity: Double) {
        val alertType = if (humidity > 70) "过高" else "过低"
        val suggestion = if (humidity > 70) 
            "建议使用除湿设备" else "建议使用加湿器"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("💧 湿度异常")
            .setContentText("$deviceId 湿度$alertType: ${String.format("%.1f", humidity)}%")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$deviceId 湿度$alertType: ${String.format("%.1f", humidity)}%\n$suggestion"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(getMainActivityPendingIntent())
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 100, 300))

        notificationManager.notify(NOTIFICATION_ID_HUMIDITY, builder.build())
    }

    /**
     * 发送环境异常通知（由 alert 主题驱动）
     */
    fun notifyEnvironmentAbnormal(
        deviceId: String,
        temperature: Double?,
        humidity: Double?,
        ratedTemperature: Double?,
        ratedHumidity: Double?,
        temperatureAbnormal: Boolean,
        humidityAbnormal: Boolean
    ) {
        if (!temperatureAbnormal && !humidityAbnormal) {
            return
        }

        val detailParts = mutableListOf<String>()
        if (temperatureAbnormal && temperature != null) {
            detailParts.add("温度 ${String.format("%.1f", temperature)}°C")
        }
        if (humidityAbnormal && humidity != null) {
            detailParts.add("湿度 ${String.format("%.1f", humidity)}%")
        }
        if (detailParts.isEmpty()) {
            detailParts.add("温湿度超出阈值")
        }

        val ratedText = buildString {
            if (ratedTemperature != null) {
                append("温度额定 ${String.format("%.1f", ratedTemperature)}°C")
            }
            if (ratedHumidity != null) {
                if (isNotEmpty()) append("，")
                append("湿度额定 ${String.format("%.1f", ratedHumidity)}%")
            }
        }.ifBlank { "额定值未上报" }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ 环境异常")
            .setContentText(detailParts.joinToString("，"))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$deviceId 环境异常\n${detailParts.joinToString("，")}\n$ratedText"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(getMainActivityPendingIntent())
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 100, 300))

        notificationManager.notify(NOTIFICATION_ID_TEMPERATURE, builder.build())
    }

    /**
     * 发送环境恢复通知
     */
    fun notifyEnvironmentRecovered(deviceId: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_STATUS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("✅ 环境恢复正常")
            .setContentText("$deviceId 温湿度已恢复到额定范围")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(getMainActivityPendingIntent())
            .setAutoCancel(true)

        notificationManager.notify(NOTIFICATION_ID_HUMIDITY + 100, builder.build())
    }

    /**
     * 发送药箱跌落/倾斜告警通知
     * 高优先级通知，播放警告音效
     */
    fun notifyTiltedAlert(deviceId: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ 药箱异常")
            .setContentText("检测到药箱跌落或倾斜，请检查药品安全")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$deviceId 检测到药箱跌落或倾斜，请立即检查药品安全状况"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(getMainActivityPendingIntent())
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))

        notificationManager.notify(NOTIFICATION_ID_TILTED, builder.build())
    }

    /**
     * 发送跌落告警（加速度 > 6G 且持续 > 20ms）
     */
    fun notifyDropDetected(deviceId: String, acceleration: Double?, durationMs: Int?) {
        val accelText = acceleration?.let { String.format("%.2f", it) } ?: "--"
        val durationText = durationMs ?: 20

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 跌落告警")
            .setContentText("检测到药箱跌落：${accelText}G，持续 ${durationText}ms")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$deviceId 检测到跌落冲击\n加速度: ${accelText}G\n持续时间: ${durationText}ms\n设备已触发蜂鸣报警"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(getMainActivityPendingIntent())
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))

        notificationManager.notify(NOTIFICATION_ID_TILTED, builder.build())
    }

    /**
     * 发送跌落报警取消通知
     */
    fun notifyDropAlarmCancelled(deviceId: String, stopPush: Boolean) {
        val message = if (stopPush) {
            "已按KEY2消警，后续异常消息已静默"
        } else {
            "跌落报警已取消"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_GENERAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🔕 跌落报警取消")
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$deviceId $message"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(getMainActivityPendingIntent())
            .setAutoCancel(true)

        notificationManager.notify(NOTIFICATION_ID_TILTED + 100, builder.build())
    }

    /**
     * 清除温度异常通知
     * 当温度恢复正常时调用
     */
    fun clearTemperatureAlert() {
        notificationManager.cancel(NOTIFICATION_ID_TEMPERATURE)
    }

    /**
     * 清除湿度异常通知
     */
    fun clearHumidityAlert() {
        notificationManager.cancel(NOTIFICATION_ID_HUMIDITY)
    }

    /**
     * 清除倾斜/跌落告警通知
     * 当药箱恢复平稳状态时调用
     */
    fun clearTiltedAlert() {
        notificationManager.cancel(NOTIFICATION_ID_TILTED)
    }

    /**
     * 发送药箱打开通知
     */
    fun notifyBoxOpened(deviceId: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_GENERAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📦 药箱已打开")
            .setContentText("$deviceId 被打开")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(getMainActivityPendingIntent())
            .setAutoCancel(true)

        notificationManager.notify(NOTIFICATION_ID_OPENED, builder.build())
    }

    /**
     * 发送一般信息通知
     */
    fun notifyInfo(title: String, message: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_GENERAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(getMainActivityPendingIntent())
            .setAutoCancel(true)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    /**
     * 取消所有通知
     */
    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }

    /**
     * 取消指定通知
     */
    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    /**
     * 格式化时长
     */
    private fun formatDuration(durationMs: Long): String {
        val seconds = durationMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return when {
            hours > 0 -> "$hours 小时 ${minutes % 60} 分钟"
            minutes > 0 -> "$minutes 分钟 ${seconds % 60} 秒"
            else -> "$seconds 秒"
        }
    }
}
