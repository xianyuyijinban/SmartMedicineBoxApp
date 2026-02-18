package com.smartmedicine.box.monitor

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*

/**
 * 设备连接监控器
 * 
 * 功能：
 * 1. 心跳机制：监控设备数据上报（设备每5秒发布一次数据）
 * 2. 离线检测：若15秒内未收到数据，判定为离线
 * 3. Last Will机制支持（通过MQTT Broker配置）
 * 4. 在线状态变化回调
 * 
 * 使用方式：
 * 1. 每次收到设备数据时调用 updateLastDataTime()
 * 2. 调用 startMonitoring() 启动监控
 * 3. 通过 setOnConnectionChangeListener() 设置状态变化回调
 * 4. 不再使用时调用 stopMonitoring() 停止监控
 */
class ConnectionMonitor {

    companion object {
        private const val TAG = "ConnectionMonitor"
        
        // 默认离线检测超时时间（15秒）
        const val DEFAULT_OFFLINE_TIMEOUT = 15000L
        
        // 默认检测间隔（1秒）
        const val DEFAULT_CHECK_INTERVAL = 1000L
        
        // 单例实例
        @Volatile
        private var instance: ConnectionMonitor? = null

        /**
         * 获取ConnectionMonitor单例实例
         * 
         * @return ConnectionMonitor实例
         */
        fun getInstance(): ConnectionMonitor {
            return instance ?: synchronized(this) {
                instance ?: ConnectionMonitor().also {
                    instance = it
                }
            }
        }

        /**
         * 清除单例实例
         */
        fun clearInstance() {
            instance?.stopMonitoring()
            instance = null
        }
    }

    // 最后收到数据的时间戳（毫秒）
    private var lastDataTime: Long = System.currentTimeMillis()
    
    // 离线检测超时时间（毫秒）
    private val offlineTimeout: Long = DEFAULT_OFFLINE_TIMEOUT
    
    // 检测间隔（毫秒）
    private val checkInterval: Long = DEFAULT_CHECK_INTERVAL
    
    // 当前是否离线
    private var isOffline: Boolean = false
    
    // 是否正在监控
    private var isMonitoring: Boolean = false
    
    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // 监控任务
    private var monitorJob: Job? = null
    
    // 主线程Handler（用于回调）
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 连接状态变化监听器
    private var connectionChangeListener: ((Boolean) -> Unit)? = null
    
    // 离线时长（毫秒）
    private var offlineDuration: Long = 0
    
    // 上次记录的状态（用于检测变化）
    private var lastReportedStatus: Boolean? = null

    /**
     * 更新最后收到数据的时间
     * 应在每次收到传感器数据时调用此方法
     * 
     * 如果设备之前处于离线状态，调用此方法会触发上线回调
     */
    fun updateLastDataTime() {
        val previousTime = lastDataTime
        lastDataTime = System.currentTimeMillis()
        
        // 如果之前是离线状态，现在恢复在线
        if (isOffline) {
            isOffline = false
            offlineDuration = 0
            val offlineTime = lastDataTime - previousTime
            Log.i(TAG, "设备恢复在线，离线时长: ${offlineTime}ms")
            notifyConnectionChange(false)
        }
    }

    /**
     * 检查当前是否离线
     * 
     * @return true-离线，false-在线
     */
    fun isOffline(): Boolean {
        val elapsed = System.currentTimeMillis() - lastDataTime
        return elapsed > offlineTimeout
    }

    /**
     * 获取离线时长（毫秒）
     * 如果在线返回0
     * 
     * @return 离线时长（毫秒）
     */
    fun getOfflineDuration(): Long {
        return if (isOffline) offlineDuration else 0
    }

    /**
     * 获取距离上次收到数据的时间（毫秒）
     * 
     * @return 经过的时间（毫秒）
     */
    fun getElapsedTime(): Long {
        return System.currentTimeMillis() - lastDataTime
    }

    /**
     * 获取剩余超时时间（毫秒）
     * 
     * @return 剩余时间（毫秒），已离线则返回0
     */
    fun getRemainingTimeout(): Long {
        val elapsed = getElapsedTime()
        return (offlineTimeout - elapsed).coerceAtLeast(0)
    }

    /**
     * 启动离线监控
     * 开始定期检查设备是否离线
     */
    fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "监控已在运行中")
            return
        }
        
        isMonitoring = true
        Log.d(TAG, "开始设备连接监控，超时时间: ${offlineTimeout}ms")
        
        monitorJob = scope.launch {
            while (isActive) {
                checkOfflineStatus()
                delay(checkInterval)
            }
        }
    }

    /**
     * 停止离线监控
     */
    fun stopMonitoring() {
        isMonitoring = false
        monitorJob?.cancel()
        monitorJob = null
        Log.d(TAG, "设备连接监控已停止")
    }

    /**
     * 检查离线状态
     * 内部方法，定期检查设备是否离线
     */
    private fun checkOfflineStatus() {
        val elapsed = System.currentTimeMillis() - lastDataTime
        val currentlyOffline = elapsed > offlineTimeout
        
        // 更新离线时长
        if (currentlyOffline) {
            offlineDuration = elapsed
        }
        
        // 检测状态变化
        if (currentlyOffline != isOffline) {
            isOffline = currentlyOffline
            
            if (currentlyOffline) {
                Log.w(TAG, "设备离线检测！已 ${elapsed}ms 未收到数据")
                notifyConnectionChange(true)
            } else {
                Log.i(TAG, "设备在线")
                notifyConnectionChange(false)
            }
        }
    }

    /**
     * 设置连接状态变化监听器
     * 
     * @param listener 回调函数，参数为isOffline（true-离线，false-在线）
     */
    fun setOnConnectionChangeListener(listener: (Boolean) -> Unit) {
        this.connectionChangeListener = listener
    }

    /**
     * 通知连接状态变化
     * 
     * @param offline 是否离线
     */
    private fun notifyConnectionChange(offline: Boolean) {
        // 避免重复通知相同状态
        if (lastReportedStatus == offline) return
        lastReportedStatus = offline
        
        mainHandler.post {
            try {
                connectionChangeListener?.invoke(offline)
            } catch (e: Exception) {
                Log.e(TAG, "连接状态回调执行失败", e)
            }
        }
    }

    /**
     * 重置监控器
     * 清除所有状态，重新开始监控
     */
    fun reset() {
        lastDataTime = System.currentTimeMillis()
        isOffline = false
        offlineDuration = 0
        lastReportedStatus = null
        Log.d(TAG, "连接监控器已重置")
    }

    /**
     * 获取当前监控状态
     * 
     * @return 是否正在监控
     */
    fun isMonitoring(): Boolean {
        return isMonitoring
    }

    /**
     * 获取连接状态信息
     * 用于调试和显示
     * 
     * @return 状态信息字符串
     */
    fun getStatusInfo(): String {
        return buildString {
            append("监控状态: ${if (isMonitoring) "运行中" else "已停止"}\n")
            append("设备状态: ${if (isOffline) "离线" else "在线"}\n")
            append("上次数据: ${getElapsedTime()}ms前\n")
            append("剩余超时: ${getRemainingTimeout()}ms\n")
            if (isOffline) {
                append("离线时长: ${offlineDuration}ms")
            }
        }
    }

    /**
     * 资源释放
     * 停止监控并清理资源
     */
    fun release() {
        stopMonitoring()
        scope.cancel()
        connectionChangeListener = null
        Log.d(TAG, "ConnectionMonitor已释放")
    }
}
