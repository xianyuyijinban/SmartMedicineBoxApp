package com.smartmedicine.box.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.smartmedicine.box.data.model.Alert
import com.smartmedicine.box.data.model.AlertCheckRequest
import com.smartmedicine.box.data.model.AlertThresholdConfig
import com.smartmedicine.box.data.model.BoxState
import com.smartmedicine.box.data.model.CommandResponse
import com.smartmedicine.box.data.model.CommandResult
import com.smartmedicine.box.data.model.ConnectionStatus
import com.smartmedicine.box.data.model.ControlCommand
import com.smartmedicine.box.data.model.DeviceStatus
import com.smartmedicine.box.data.model.EnvironmentData
import com.smartmedicine.box.data.model.MotionData
import com.smartmedicine.box.data.model.SensorData
import com.smartmedicine.box.manager.AlertManager
import com.smartmedicine.box.manager.CommandManager
import com.smartmedicine.box.manager.OfflineDetector
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.MqttClient

/**
 * 智能药箱ViewModel
 * 
 * 负责:
 * - 数据状态管理（使用StateFlow/LiveData）
 * - 连接UI与业务逻辑
 * - 整合CommandManager、AlertManager、OfflineDetector
 */
class MedicineBoxViewModel(
    application: Application,
    private val mqttClient: MqttClient,
    private val deviceId: String
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MedicineBoxViewModel"
    }

    // ========== 管理器实例 ==========
    private val commandManager: CommandManager by lazy {
        CommandManager.getInstance(mqttClient, deviceId, viewModelScope)
    }
    
    private val alertManager: AlertManager by lazy {
        AlertManager.getInstance(application)
    }
    
    private val offlineDetector: OfflineDetector by lazy {
        OfflineDetector.getInstance(
            timeoutMs = AlertThresholdConfig.DEFAULT_OFFLINE_TIMEOUT_MS,
            coroutineScope = viewModelScope
        ).apply {
            addCallback(object : OfflineDetector.OfflineCallback {
                override fun onDeviceOffline(offlineDuration: Long) {
                    _uiState.update { it.copy(isOffline = true) }
                }
                
                override fun onDeviceOnline(offlineDuration: Long) {
                    _uiState.update { it.copy(isOffline = false) }
                    alertManager.notifyDeviceOnline(deviceId)
                }
                
                override fun onHeartbeatUpdated(heartbeatTime: Long) {
                    _uiState.update { it.copy(lastUpdateTime = heartbeatTime) }
                }
            })
        }
    }

    // ========== UI状态 ==========
    data class UiState(
        val isLoading: Boolean = false,
        val isConnected: Boolean = false,
        val isOffline: Boolean = false,
        val errorMessage: String? = null,
        val lastUpdateTime: Long = 0L
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // ========== 传感器数据 ==========
    private val _sensorData = MutableStateFlow(SensorData())
    val sensorData: StateFlow<SensorData> = _sensorData.asStateFlow()

    // LiveData兼容（便于在XML中使用数据绑定）
    val sensorDataLiveData: LiveData<SensorData> = _sensorData.asLiveData()

    // ========== 设备状态 ==========
    private val _deviceStatus = MutableStateFlow(DeviceStatus())
    val deviceStatus: StateFlow<DeviceStatus> = _deviceStatus.asStateFlow()

    val deviceStatusLiveData: LiveData<DeviceStatus> = _deviceStatus.asLiveData()

    // ========== 环境数据快捷访问 ==========
    val environmentData: StateFlow<EnvironmentData> = _sensorData
        .map { it.environment }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EnvironmentData())

    val temperature: StateFlow<Float> = environmentData
        .map { it.temperature }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val humidity: StateFlow<Float> = environmentData
        .map { it.humidity }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val pressure: StateFlow<Float> = environmentData
        .map { it.pressure }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    // ========== 运动数据 ==========
    val motionData: StateFlow<MotionData> = _sensorData
        .map { it.motion }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MotionData())

    // ========== 药箱状态 ==========
    val boxState: StateFlow<BoxState> = _sensorData
        .map { it.state }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BoxState.CLOSED)

    val isBoxOpened: StateFlow<Boolean> = boxState
        .map { it == BoxState.OPENED }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isBoxMoving: StateFlow<Boolean> = boxState
        .map { it == BoxState.MOVING }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ========== 告警数据 ==========
    val latestAlert: SharedFlow<Alert> = alertManager.latestAlertFlow

    val alertHistory: StateFlow<List<Alert>> = alertManager.alertHistory

    private val _currentAlerts = MutableStateFlow<List<Alert>>(emptyList())
    val currentAlerts: StateFlow<List<Alert>> = _currentAlerts.asStateFlow()

    val hasCriticalAlert: StateFlow<Boolean> = _currentAlerts
        .map { alerts -> alerts.any { it.level.priority >= 4 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ========== 命令执行状态 ==========
    private val _commandExecuting = MutableStateFlow(false)
    val commandExecuting: StateFlow<Boolean> = _commandExecuting.asStateFlow()

    private val _commandResult = MutableSharedFlow<CommandResult<CommandResponse>>(extraBufferCapacity = 1)
    val commandResult: SharedFlow<CommandResult<CommandResponse>> = _commandResult.asSharedFlow()

    // ========== 初始化 ==========
    init {
        viewModelScope.launch {
            // 收集命令响应
            commandManager.commandResponseFlow.collect { response ->
                Log.d(TAG, "Command response received: ${response.cmd}")
            }
        }

        viewModelScope.launch {
            // 收集告警
            alertManager.latestAlertFlow.collect { alert ->
                Log.d(TAG, "Alert received: ${alert.title}")
            }
        }
    }

    // ========== 数据更新方法 ==========
    
    /**
     * 更新传感器数据
     * 应在收到MQTT传感器数据消息时调用
     */
    fun updateSensorData(data: SensorData) {
        _sensorData.value = data
        
        // 更新离线检测器心跳
        offlineDetector.updateHeartbeat()
        
        // 检查告警
        checkAlerts(data)
        
        Log.d(TAG, "Sensor data updated: temp=${data.environment.temperature}°C, " +
                "humidity=${data.environment.humidity}%, state=${data.state}")
    }

    /**
     * 更新设备状态
     * 应在收到MQTT状态消息时调用
     */
    fun updateDeviceStatus(status: DeviceStatus) {
        _deviceStatus.value = status
        
        when (status.status) {
            ConnectionStatus.ONLINE -> {
                offlineDetector.updateHeartbeat()
                _uiState.update { it.copy(isConnected = true, isOffline = false) }
            }
            ConnectionStatus.OFFLINE -> {
                _uiState.update { it.copy(isConnected = false, isOffline = true) }
            }
        }
        
        Log.d(TAG, "Device status updated: ${status.status}")
    }

    /**
     * 设置连接状态
     */
    fun setConnectionState(connected: Boolean) {
        _uiState.update { it.copy(isConnected = connected) }
        if (connected) {
            // 启动离线检测
            offlineDetector.start()
        } else {
            offlineDetector.stop()
        }
    }

    // ========== 告警相关方法 ==========
    
    /**
     * 检查告警条件
     */
    private fun checkAlerts(sensorData: SensorData) {
        val request = AlertCheckRequest(
            sensorData = sensorData,
            deviceStatus = _deviceStatus.value,
            lastHeartbeatTime = offlineDetector.lastHeartbeatTime.value
        )
        
        val result = alertManager.checkAlerts(request)
        _currentAlerts.value = result.alerts
    }

    /**
     * 更新告警阈值配置
     */
    fun updateAlertThresholdConfig(config: AlertThresholdConfig) {
        alertManager.updateThresholdConfig(config)
    }

    /**
     * 清除所有通知
     */
    fun clearNotifications() {
        alertManager.clearAllNotifications()
    }

    /**
     * 清除告警历史
     */
    fun clearAlertHistory() {
        alertManager.clearHistory()
    }

    // ========== 命令控制方法 ==========
    
    /**
     * 发送控制命令
     */
    fun sendCommand(
        command: ControlCommand,
        value: Any? = null,
        onSuccess: ((CommandResponse) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _commandExecuting.value = true
            _uiState.update { it.copy(errorMessage = null) }
            
            try {
                when (val result = commandManager.sendCommand(command, value)) {
                    is CommandResult.Success -> {
                        _commandResult.emit(result)
                        onSuccess?.invoke(result.data)
                        Log.i(TAG, "Command ${command.command} executed successfully")
                    }
                    is CommandResult.Error -> {
                        _commandResult.emit(result)
                        val errorMsg = commandManager.getFullErrorMessage(result.code, result.message)
                        _uiState.update { it.copy(errorMessage = errorMsg) }
                        onError?.invoke(errorMsg)
                        Log.e(TAG, "Command ${command.command} failed: $errorMsg")
                    }
                }
            } catch (e: Exception) {
                val errorMsg = "命令执行异常: ${e.message}"
                _uiState.update { it.copy(errorMessage = errorMsg) }
                onError?.invoke(errorMsg)
                Log.e(TAG, "Command execution exception", e)
            } finally {
                _commandExecuting.value = false
            }
        }
    }

    /**
     * 重置设备
     */
    fun resetDevice(
        onSuccess: ((CommandResponse) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        sendCommand(ControlCommand.RESET, null, onSuccess, onError)
    }

    /**
     * 立即上报数据
     */
    fun publishNow(
        onSuccess: ((CommandResponse) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        sendCommand(ControlCommand.PUBLISH_NOW, null, onSuccess, onError)
    }

    /**
     * 设置上报间隔
     * @param intervalSeconds 上报间隔（秒），范围1-3600
     */
    fun setPublishInterval(
        intervalSeconds: Int,
        onSuccess: ((CommandResponse) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (intervalSeconds !in 1..3600) {
            onError?.invoke("上报间隔必须在1-3600秒之间")
            return
        }
        sendCommand(ControlCommand.SET_INTERVAL, intervalSeconds, onSuccess, onError)
    }

    // ========== 离线检测方法 ==========
    
    /**
     * 启动离线检测
     */
    fun startOfflineDetection() {
        offlineDetector.start()
    }

    /**
     * 停止离线检测
     */
    fun stopOfflineDetection() {
        offlineDetector.stop()
    }

    /**
     * 手动更新心跳
     */
    fun updateHeartbeat() {
        offlineDetector.updateHeartbeat()
    }

    /**
     * 检查是否离线
     */
    fun checkOffline(): Boolean {
        return offlineDetector.checkOffline()
    }

    // ========== 状态清除方法 ==========
    
    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 重置所有数据
     */
    fun resetAll() {
        _sensorData.value = SensorData()
        _deviceStatus.value = DeviceStatus()
        _currentAlerts.value = emptyList()
        _uiState.value = UiState()
        offlineDetector.reset()
        alertManager.clearHistory()
    }

    // ========== 生命周期 ==========
    
    override fun onCleared() {
        super.onCleared()
        offlineDetector.destroy()
        commandManager.destroy()
        AlertManager.clearInstance()
        OfflineDetector.clearInstance()
        CommandManager.clearInstance()
        Log.d(TAG, "ViewModel cleared")
    }
}

/**
 * ViewModel工厂类
 */
class MedicineBoxViewModelFactory(
    private val application: Application,
    private val mqttClient: MqttClient,
    private val deviceId: String
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MedicineBoxViewModel::class.java)) {
            return MedicineBoxViewModel(application, mqttClient, deviceId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
