package com.smartmedicine.notification

import android.app.NotificationChannel
import android.app.NotificationManager
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

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
                NotificationManager.IMPORTANCE_HIGH
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
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "设备在线/离线状态变化通知"
                enableVibration(false)
            }

            // 一般通知渠道 - 低优先级
            val generalChannel = NotificationChannel(
                CHANNEL_ID_GENERAL,
                "一般通知",
                NotificationManager.IMPORTANCE_LOW
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
     */
    fun notifyTemperatureAlert(deviceId: String, temperature: Double) {
        val alertType = if (temperature > 30) "过高" else "过低"
        val suggestion = if (temperature > 30) 
            "建议将药箱移至阴凉处" else "建议将药箱移至温暖处"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🌡️ 温度异常")
            .setContentText("$deviceId 温度$alertType: ${String.format("%.1f", temperature)}°C")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$deviceId 温度$alertType: ${String.format("%.1f", temperature)}°C\n$suggestion"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(getMainActivityPendingIntent())
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 100, 300))

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
     * 发送药箱倾斜通知
     */
    fun notifyTiltedAlert(deviceId: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ 药箱倾斜")
            .setContentText("$deviceId 处于倾斜状态")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$deviceId 处于倾斜状态，请检查药箱放置是否平稳"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(getMainActivityPendingIntent())
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 400, 150, 400))

        notificationManager.notify(NOTIFICATION_ID_TILTED, builder.build())
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
