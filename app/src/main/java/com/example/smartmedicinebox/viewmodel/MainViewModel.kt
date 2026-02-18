package com.example.smartmedicinebox.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartmedicinebox.data.model.BoxState
import com.example.smartmedicinebox.data.model.ConnectionStatus
import com.example.smartmedicinebox.data.model.MedicineBoxData
import com.example.smartmedicinebox.ui.SettingsActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 主界面ViewModel
 * 使用MVVM架构管理UI数据和业务逻辑
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    // 上下文
    private val context: Context = application.applicationContext
    
    // 数据刷新任务
    private var refreshJob: Job? = null
    
    // ==================== StateFlow定义 ====================
    
    // 连接状态
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()
    
    // 传感器数据显示
    private val _sensorData = MutableStateFlow(SensorDataDisplay())
    val sensorData: StateFlow<SensorDataDisplay> = _sensorData.asStateFlow()
    
    // 药箱状态
    private val _boxState = MutableStateFlow(BoxState.UNKNOWN)
    val boxState: StateFlow<BoxState> = _boxState.asStateFlow()
    
    // 振动状态（true=正常，false=异常）
    private val _vibrationStatus = MutableStateFlow(true)
    val vibrationStatus: StateFlow<Boolean> = _vibrationStatus.asStateFlow()
    
    // 倾斜状态（true=正常，false=异常）
    private val _tiltStatus = MutableStateFlow(true)
    val tiltStatus: StateFlow<Boolean> = _tiltStatus.asStateFlow()
    
    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // 错误消息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // ==================== 数据类定义 ====================
    
    /**
     * 传感器数据显示类
     */
    data class SensorDataDisplay(
        val temperature: Float = 0f,
        val humidity: Float = 0f,
        val pressure: Float = 0f,
        val altitude: Float = 0f,
        val tempHigh: Float = 30f,
        val tempLow: Float = 10f,
        val humidityHigh: Float = 70f,
        val humidityLow: Float = 30f
    )
    
    // ==================== 初始化 ====================
    
    init {
        // 加载阈值配置
        loadThresholdConfig()
        // 启动自动刷新
        startAutoRefresh()
    }
    
    /**
     * 加载阈值配置
     */
    private fun loadThresholdConfig() {
        _sensorData.value = _sensorData.value.copy(
            tempHigh = SettingsActivity.Config.getTempHigh(context),
            tempLow = SettingsActivity.Config.getTempLow(context),
            humidityHigh = SettingsActivity.Config.getHumidityHigh(context),
            humidityLow = SettingsActivity.Config.getHumidityLow(context)
        )
    }
    
    // ==================== 公共方法 ====================
    
    /**
     * 连接设备
     */
    fun connect() {
        viewModelScope.launch {
            _isLoading.value = true
            _connectionStatus.value = ConnectionStatus.CONNECTING
            
            try {
                // 模拟连接延迟
                delay(1000)
                
                // 实际项目中这里应该调用MQTT连接逻辑
                // MqttManager.connect(broker, deviceId)
                
                _connectionStatus.value = ConnectionStatus.CONNECTED
                refreshData()
            } catch (e: Exception) {
                _connectionStatus.value = ConnectionStatus.ERROR
                _errorMessage.value = "连接失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                // 实际项目中这里应该调用MQTT断开逻辑
                // MqttManager.disconnect()
                
                delay(500)
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                resetData()
            } catch (e: Exception) {
                _errorMessage.value = "断开连接失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 刷新数据
     */
    fun refreshData() {
        if (_connectionStatus.value != ConnectionStatus.CONNECTED) {
            _errorMessage.value = "设备未连接"
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                // 实际项目中这里应该从MQTT获取数据
                // val data = MqttManager.requestData()
                
                // 模拟数据（实际项目中替换为真实数据）
                val mockData = generateMockData()
                updateData(mockData)
                
            } catch (e: Exception) {
                _errorMessage.value = "刷新数据失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 更新数据（供外部调用，如MQTT回调）
     */
    fun updateData(data: MedicineBoxData) {
        viewModelScope.launch {
            _sensorData.value = _sensorData.value.copy(
                temperature = data.temperature,
                humidity = data.humidity,
                pressure = data.pressure,
                altitude = data.altitude
            )
            
            _boxState.value = when {
                data.isTilted -> BoxState.TILTED
                data.isMoving -> BoxState.MOVING
                data.isOpened -> BoxState.OPENED
                else -> BoxState.CLOSED
            }
            
            _vibrationStatus.value = !data.isVibrationAlert
            _tiltStatus.value = !data.isTilted
            
            // 检查告警
            checkAlerts(data)
        }
    }
    
    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * 启动自动刷新
     */
    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                val interval = SettingsActivity.Config.getReportInterval(context)
                delay(interval * 1000L)
                
                if (_connectionStatus.value == ConnectionStatus.CONNECTED) {
                    refreshData()
                }
            }
        }
    }
    
    /**
     * 检查告警条件
     */
    private fun checkAlerts(data: MedicineBoxData) {
        val temp = data.temperature
        val humidity = data.humidity
        
        val currentData = _sensorData.value
        
        when {
            temp > currentData.tempHigh -> {
                // 温度过高告警
                // NotificationHelper.showAlert(context, "温度异常", "当前温度${temp}°C超过高温阈值")
            }
            temp < currentData.tempLow -> {
                // 温度过低告警
                // NotificationHelper.showAlert(context, "温度异常", "当前温度${temp}°C低于低温阈值")
            }
        }
        
        when {
            humidity > currentData.humidityHigh -> {
                // 湿度过高告警
                // NotificationHelper.showAlert(context, "湿度异常", "当前湿度${humidity}%超过高湿阈值")
            }
            humidity < currentData.humidityLow -> {
                // 湿度过低告警
                // NotificationHelper.showAlert(context, "湿度异常", "当前湿度${humidity}%低于低湿阈值")
            }
        }
    }
    
    /**
     * 重置数据
     */
    private fun resetData() {
        _sensorData.value = SensorDataDisplay(
            tempHigh = _sensorData.value.tempHigh,
            tempLow = _sensorData.value.tempLow,
            humidityHigh = _sensorData.value.humidityHigh,
            humidityLow = _sensorData.value.humidityLow
        )
        _boxState.value = BoxState.UNKNOWN
        _vibrationStatus.value = true
        _tiltStatus.value = true
    }
    
    /**
     * 生成模拟数据（用于测试）
     */
    private fun generateMockData(): MedicineBoxData {
        return MedicineBoxData(
            temperature = 20f + (Math.random() * 15).toFloat(), // 20-35度
            humidity = 40f + (Math.random() * 40).toFloat(),    // 40-80%
            pressure = 1000f + (Math.random() * 30).toFloat(),  // 1000-1030 hPa
            altitude = (Math.random() * 100).toFloat(),         // 0-100米
            isOpened = Math.random() > 0.7,
            isMoving = Math.random() > 0.9,
            isTilted = Math.random() > 0.95,
            isVibrationAlert = Math.random() > 0.95
        )
    }
    
    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}
