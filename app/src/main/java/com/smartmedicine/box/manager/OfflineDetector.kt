package com.smartmedicine.box.manager

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 离线检测器
 * 
 * 功能:
 * - 15秒超时检测（3个publish间隔，设备每5秒发布一次数据）
 * - 心跳更新机制
 * - 离线状态回调
 * 
 * 根据接口文档:
 * - 设备每5秒发布一次传感器数据作为心跳
 * - 若APP在15秒内未收到数据，判定设备离线
 */
class OfflineDetector private constructor(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val checkIntervalMs: Long = DEFAULT_CHECK_INTERVAL_MS,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    companion object {
        private const val TAG = "OfflineDetector"
        
        // 默认15秒超时
        const val DEFAULT_TIMEOUT_MS = 15000L
        
        // 默认检查间隔 1秒
        const val DEFAULT_CHECK_INTERVAL_MS = 1000L
        
        @Volatile
        private var instance: OfflineDetector? = null

        /**
         * 获取OfflineDetector实例（单例模式）
         */
        fun getInstance(
            timeoutMs: Long = DEFAULT_TIMEOUT_MS,
            checkIntervalMs: Long = DEFAULT_CHECK_INTERVAL_MS,
            coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        ): OfflineDetector {
            return instance ?: synchronized(this) {
                instance ?: OfflineDetector(timeoutMs, checkIntervalMs, coroutineScope).also {
                    instance = it
                }
            }
        }

        /**
         * 清除实例
         */
        fun clearInstance() {
            instance?.stop()
            instance = null
        }
    }

    // 离线状态流
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    // 最后心跳时间流
    private val _lastHeartbeatTime = MutableStateFlow(0L)
    val lastHeartbeatTime: StateFlow<Long> = _lastHeartbeatTime.asStateFlow()

    // 离线持续时间（毫秒）
    private val _offlineDuration = MutableStateFlow(0L)
    val offlineDuration: StateFlow<Long> = _offlineDuration.asStateFlow()

    // 回调接口集合
    private val callbacks = mutableSetOf<OfflineCallback>()

    // 检查任务
    private var checkJob: Job? = null

    // 是否正在运行
    private var isRunning = false

    // 上次检测到的状态（用于状态变化检测）
    private var wasOffline = false

    // 主线程Handler（用于回调）
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 离线状态回调接口
     */
    interface OfflineCallback {
        /**
         * 设备离线时调用
         * @param offlineDuration 已离线时长（毫秒）
         */
        fun onDeviceOffline(offlineDuration: Long)

        /**
         * 设备上线时调用
         * @param offlineDuration 之前离线时长（毫秒）
         */
        fun onDeviceOnline(offlineDuration: Long)

        /**
         * 心跳更新时调用
         * @param heartbeatTime 心跳时间戳
         */
        fun onHeartbeatUpdated(heartbeatTime: Long) {}
    }

    /**
     * 启动离线检测
     */
    fun start() {
        if (isRunning) return
        
        isRunning = true
        Log.d(TAG, "OfflineDetector started with timeout: ${timeoutMs}ms")
        
        checkJob = coroutineScope.launch {
            while (isActive) {
                checkOfflineStatus()
                delay(checkIntervalMs)
            }
        }
    }

    /**
     * 停止离线检测
     */
    fun stop() {
        isRunning = false
        checkJob?.cancel()
        checkJob = null
        Log.d(TAG, "OfflineDetector stopped")
    }

    /**
     * 更新心跳时间
     * 应在每次收到设备数据时调用
     */
    fun updateHeartbeat(timestamp: Long = System.currentTimeMillis()) {
        val lastTime = _lastHeartbeatTime.value
        _lastHeartbeatTime.value = timestamp
        _offlineDuration.value = 0L
        
        // 如果之前是离线状态，现在恢复在线
        if (_isOffline.value) {
            _isOffline.value = false
            val previousOfflineDuration = System.currentTimeMillis() - lastTime
            notifyDeviceOnline(previousOfflineDuration)
            Log.i(TAG, "Device back online, was offline for ${previousOfflineDuration}ms")
        }
        
        // 通知心跳更新
        notifyHeartbeatUpdated(timestamp)
    }

    /**
     * 检查离线状态
     */
    private fun checkOfflineStatus() {
        val currentTime = System.currentTimeMillis()
        val lastTime = _lastHeartbeatTime.value
        
        if (lastTime == 0L) {
            // 还未收到过心跳
            return
        }
        
        val elapsed = currentTime - lastTime
        val offline = elapsed > timeoutMs
        
        _offlineDuration.value = if (offline) elapsed else 0L
        
        if (offline != _isOffline.value) {
            _isOffline.value = offline
            
            if (offline) {
                notifyDeviceOffline(elapsed)
                Log.w(TAG, "Device offline detected, elapsed: ${elapsed}ms")
            } else {
                notifyDeviceOnline(elapsed)
                Log.i(TAG, "Device online detected")
            }
        }
    }

    /**
     * 检查当前是否离线
     */
    fun checkOffline(): Boolean {
        val lastTime = _lastHeartbeatTime.value
        if (lastTime == 0L) return false
        
        val elapsed = System.currentTimeMillis() - lastTime
        val offline = elapsed > timeoutMs
        
        // 更新状态但不触发回调（回调在checkJob中处理）
        _isOffline.value = offline
        if (offline) {
            _offlineDuration.value = elapsed
        }
        
        return offline
    }

    /**
     * 获取距离上次心跳的时间（毫秒）
     */
    fun getElapsedTimeSinceLastHeartbeat(): Long {
        val lastTime = _lastHeartbeatTime.value
        if (lastTime == 0L) return 0L
        return System.currentTimeMillis() - lastTime
    }

    /**
     * 获取剩余超时时间（毫秒）
     */
    fun getRemainingTimeoutMs(): Long {
        val elapsed = getElapsedTimeSinceLastHeartbeat()
        return (timeoutMs - elapsed).coerceAtLeast(0L)
    }

    /**
     * 重置检测器
     */
    fun reset() {
        _lastHeartbeatTime.value = 0L
        _isOffline.value = false
        _offlineDuration.value = 0L
        wasOffline = false
        Log.d(TAG, "OfflineDetector reset")
    }

    /**
     * 添加离线状态回调
     */
    fun addCallback(callback: OfflineCallback) {
        callbacks.add(callback)
    }

    /**
     * 移除离线状态回调
     */
    fun removeCallback(callback: OfflineCallback) {
        callbacks.remove(callback)
    }

    /**
     * 清除所有回调
     */
    fun clearCallbacks() {
        callbacks.clear()
    }

    /**
     * 通知设备离线
     */
    private fun notifyDeviceOffline(offlineDuration: Long) {
        mainHandler.post {
            callbacks.forEach { callback ->
                try {
                    callback.onDeviceOffline(offlineDuration)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in offline callback", e)
                }
            }
        }
    }

    /**
     * 通知设备上线
     */
    private fun notifyDeviceOnline(offlineDuration: Long) {
        mainHandler.post {
            callbacks.forEach { callback ->
                try {
                    callback.onDeviceOnline(offlineDuration)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in online callback", e)
                }
            }
        }
    }

    /**
     * 通知心跳更新
     */
    private fun notifyHeartbeatUpdated(heartbeatTime: Long) {
        mainHandler.post {
            callbacks.forEach { callback ->
                try {
                    callback.onHeartbeatUpdated(heartbeatTime)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in heartbeat callback", e)
                }
            }
        }
    }

    /**
     * 资源清理
     */
    fun destroy() {
        stop()
        clearCallbacks()
        coroutineScope.cancel()
        Log.d(TAG, "OfflineDetector destroyed")
    }
}

/**
 * 简化的离线回调适配器
 */
abstract class SimpleOfflineCallback : OfflineDetector.OfflineCallback {
    override fun onDeviceOffline(offlineDuration: Long) {}
    override fun onDeviceOnline(offlineDuration: Long) {}
    override fun onHeartbeatUpdated(heartbeatTime: Long) {}
}
