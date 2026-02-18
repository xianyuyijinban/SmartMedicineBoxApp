package com.smartmedicine.box.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smartmedicine.box.alert.AlertData
import com.smartmedicine.box.alert.AlertLevel
import com.smartmedicine.box.alert.AlertType

/**
 * 告警通知服务
 * 
 * 功能：
 * 1. 作为前台服务运行，确保告警推送的可靠性
 * 2. 不同级别使用不同通知样式
 * 3. 告警发生时弹出系统通知
 * 4. 支持点击通知打开应用
 * 
 * 使用方法：
 * 1. 在AndroidManifest.xml中注册此服务
 * 2. 调用 startService() 启动服务
 * 3. 调用 showAlertNotification() 显示告警通知
 * 4. 调用 stopService() 停止服务
 * 
 * 通知渠道：
 * - 严重告警（CRITICAL）：高优先级，声音+振动
 * - 警告告警（WARNING）：高优先级，声音
 * - 注意告警（NOTICE）：默认优先级
 * - 信息告警（INFO）：低优先级，静默
 */
class AlertNotificationService : Service() {

    companion object {
        private const val TAG = "AlertNotificationService"
        
        // 通知渠道ID
        const val CHANNEL_CRITICAL = "alert_channel_critical"
        const val CHANNEL_WARNING = "alert_channel_warning"
        const val CHANNEL_NOTICE = "alert_channel_notice"
        const val CHANNEL_INFO = "alert_channel_info"
        
        // 前台服务通知ID
        private const val FOREGROUND_NOTIFICATION_ID = 1000
        
        // 基础告警通知ID（各类型在此基础上偏移）
        private const val BASE_ALERT_NOTIFICATION_ID = 2000
        
        // 启动服务的Action
        const val ACTION_START = "com.smartmedicine.START_ALERT_SERVICE"
        const val ACTION_SHOW_ALERT = "com.smartmedicine.SHOW_ALERT"
        const val ACTION_STOP = "com.smartmedicine.STOP_ALERT_SERVICE"
        
        // Extra键名
        const val EXTRA_ALERT_TYPE = "alert_type"
        const val EXTRA_ALERT_LEVEL = "alert_level"
        const val EXTRA_ALERT_TITLE = "alert_title"
        const val EXTRA_ALERT_MESSAGE = "alert_message"
        const val EXTRA_ALERT_TIMESTAMP = "alert_timestamp"

        /**
         * 启动告警通知服务
         * 
         * @param context 上下文
         */
        fun start(context: Context) {
            val intent = Intent(context, AlertNotificationService::class.java).apply {
                action = ACTION_START
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "启动告警通知服务")
        }

        /**
         * 停止告警通知服务
         * 
         * @param context 上下文
         */
        fun stop(context: Context) {
            val intent = Intent(context, AlertNotificationService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
            Log.d(TAG, "停止告警通知服务")
        }

        /**
         * 显示告警通知（便捷方法）
         * 
         * @param context 上下文
         * @param alert 告警数据
         */
        fun showAlertNotification(context: Context, alert: AlertData) {
            val intent = Intent(context, AlertNotificationService::class.java).apply {
                action = ACTION_SHOW_ALERT
                putExtra(EXTRA_ALERT_TYPE, alert.type.name)
                putExtra(EXTRA_ALERT_LEVEL, alert.level.name)
                putExtra(EXTRA_ALERT_TITLE, alert.getTitle())
                putExtra(EXTRA_ALERT_MESSAGE, alert.message)
                putExtra(EXTRA_ALERT_TIMESTAMP, alert.timestamp)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    // 通知管理器
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
        Log.d(TAG, "服务已创建")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground()
            }
            ACTION_SHOW_ALERT -> {
                handleShowAlert(intent)
            }
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "服务已销毁")
    }

    /**
     * 启动前台服务
     */
    private fun startForeground() {
        val notification = createForegroundNotification()
        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        Log.d(TAG, "前台服务已启动")
    }

    /**
     * 创建前台服务通知
     */
    private fun createForegroundNotification(): android.app.Notification {
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CHANNEL_INFO
        } else {
            ""
        }

        return NotificationCompat.Builder(this, channelId).apply {
            setContentTitle("智能药箱监控中")
            setContentText("正在监控药箱状态...")
            setSmallIcon(android.R.drawable.ic_menu_info_details)
            setOngoing(true)
            priority = NotificationCompat.PRIORITY_LOW
        }.build()
    }

    /**
     * 处理显示告警通知
     */
    private fun handleShowAlert(intent: Intent) {
        val typeName = intent.getStringExtra(EXTRA_ALERT_TYPE) ?: AlertType.DEVICE_OFFLINE.name
        val levelName = intent.getStringExtra(EXTRA_ALERT_LEVEL) ?: AlertLevel.WARNING.name
        val title = intent.getStringExtra(EXTRA_ALERT_TITLE) ?: "告警"
        val message = intent.getStringExtra(EXTRA_ALERT_MESSAGE) ?: ""
        val timestamp = intent.getLongExtra(EXTRA_ALERT_TIMESTAMP, System.currentTimeMillis())

        val type = try {
            AlertType.valueOf(typeName)
        } catch (e: IllegalArgumentException) {
            AlertType.DEVICE_OFFLINE
        }

        val level = try {
            AlertLevel.valueOf(levelName)
        } catch (e: IllegalArgumentException) {
            AlertLevel.WARNING
        }

        showNotification(type, level, title, message, timestamp)
    }

    /**
     * 显示系统通知
     * 
     * @param type 告警类型
     * @param level 告警级别
     * @param title 通知标题
     * @param message 通知内容
     * @param timestamp 时间戳
     */
    private fun showNotification(
        type: AlertType,
        level: AlertLevel,
        title: String,
        message: String,
        timestamp: Long
    ) {
        // 确定渠道和通知ID
        val (channelId, notificationId) = when (level) {
            AlertLevel.CRITICAL -> CHANNEL_CRITICAL to getNotificationId(type)
            AlertLevel.WARNING -> CHANNEL_WARNING to getNotificationId(type)
            AlertLevel.NOTICE -> CHANNEL_NOTICE to getNotificationId(type)
            AlertLevel.INFO -> CHANNEL_INFO to getNotificationId(type)
        }

        // 构建通知
        val builder = NotificationCompat.Builder(this, channelId).apply {
            setContentTitle(title)
            setContentText(message)
            setSmallIcon(getNotificationIcon(type))
            setWhen(timestamp)
            setShowWhen(true)
            setAutoCancel(true)
            
            // 设置优先级
            priority = when (level) {
                AlertLevel.CRITICAL -> NotificationCompat.PRIORITY_MAX
                AlertLevel.WARNING -> NotificationCompat.PRIORITY_HIGH
                AlertLevel.NOTICE -> NotificationCompat.PRIORITY_DEFAULT
                AlertLevel.INFO -> NotificationCompat.PRIORITY_LOW
            }
            
            // 严重告警使用长文本样式
            if (level == AlertLevel.CRITICAL) {
                setStyle(NotificationCompat.BigTextStyle().bigText(message))
            }
            
            // 设置点击动作（打开主Activity）
            setContentIntent(createPendingIntent())
            
            // 根据级别设置通知行为
            when (level) {
                AlertLevel.CRITICAL -> {
                    // 严重告警：声音+振动+LED闪烁
                    setDefaults(NotificationCompat.DEFAULT_SOUND or 
                               NotificationCompat.DEFAULT_VIBRATE or 
                               NotificationCompat.DEFAULT_LIGHTS)
                }
                AlertLevel.WARNING -> {
                    // 警告：声音
                    setDefaults(NotificationCompat.DEFAULT_SOUND)
                }
                else -> {
                    // 其他：静默
                    setDefaults(0)
                }
            }
        }

        notificationManager.notify(notificationId, builder.build())
        Log.d(TAG, "显示通知: $title (${level.displayName})")
    }

    /**
     * 创建PendingIntent（点击通知打开应用）
     */
    private fun createPendingIntent(): PendingIntent {
        // 获取主Activity的Intent（需要根据实际包名修改）
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        // 严重告警渠道
        val criticalChannel = NotificationChannel(
            CHANNEL_CRITICAL,
            "严重告警",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "设备离线等严重问题的告警通知"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            setBypassDnd(true) // 绕过勿扰模式
        }

        // 警告告警渠道
        val warningChannel = NotificationChannel(
            CHANNEL_WARNING,
            "警告告警",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "温度、湿度异常等警告通知"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
        }

        // 注意告警渠道
        val noticeChannel = NotificationChannel(
            CHANNEL_NOTICE,
            "注意告警",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "药箱移动中等注意通知"
        }

        // 信息告警渠道
        val infoChannel = NotificationChannel(
            CHANNEL_INFO,
            "信息通知",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "药箱打开、设备上线等信息通知"
        }

        // 创建所有渠道
        notificationManager.createNotificationChannels(listOf(
            criticalChannel,
            warningChannel,
            noticeChannel,
            infoChannel
        ))

        Log.d(TAG, "通知渠道已创建")
    }

    /**
     * 获取通知图标
     */
    private fun getNotificationIcon(type: AlertType): Int {
        return when (type) {
            AlertType.TEMPERATURE_HIGH,
            AlertType.TEMPERATURE_LOW -> android.R.drawable.ic_menu_sort_by_size
            
            AlertType.HUMIDITY_HIGH,
            AlertType.HUMIDITY_LOW -> android.R.drawable.ic_menu_compass
            
            AlertType.BOX_OPENED -> android.R.drawable.ic_menu_day
            
            AlertType.BOX_MOVING -> android.R.drawable.ic_menu_directions
            
            AlertType.DEVICE_OFFLINE -> android.R.drawable.ic_menu_close_clear_cancel
            
            AlertType.DEVICE_ONLINE -> android.R.drawable.ic_menu_check
        }
    }

    /**
     * 获取通知ID（基于告警类型）
     */
    private fun getNotificationId(type: AlertType): Int {
        return BASE_ALERT_NOTIFICATION_ID + type.ordinal
    }
}

/**
 * 通知帮助类
 * 提供便捷的静态方法显示通知
 */
object AlertNotificationHelper {

    /**
     * 显示告警通知（无需启动服务）
     * 适用于不需要前台服务保活的场景
     * 
     * @param context 上下文
     * @param alert 告警数据
     */
    fun showNotification(context: Context, alert: AlertData) {
        // 直接调用服务方法显示通知
        AlertNotificationService.showAlertNotification(context, alert)
    }

    /**
     * 取消指定类型的通知
     * 
     * @param context 上下文
     * @param type 告警类型
     */
    fun cancelNotification(context: Context, type: AlertType) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = 2000 + type.ordinal
        notificationManager.cancel(notificationId)
    }

    /**
     * 取消所有告警通知
     * 
     * @param context 上下文
     */
    fun cancelAllNotifications(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
    }
}
