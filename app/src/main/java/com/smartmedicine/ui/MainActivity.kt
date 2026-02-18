package com.smartmedicine.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smartmedicine.box.manager.OfflineDetector
import com.smartmedicine.data.model.CommandResponse
import com.smartmedicine.data.model.DeviceStatus
import com.smartmedicine.data.model.SensorData
import com.smartmedicine.mqtt.MqttManager
import com.smartmedicine.notification.NotificationManager
import com.smartmedicine.repository.HistoryRepository
import com.smartmedicine.ui.screens.*
import com.smartmedicine.ui.theme.SmartMedicineBoxTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import androidx.lifecycle.viewModelScope
import timber.log.Timber

/**
 * 主界面Activity - Jetpack Compose版本
 * 智能药箱监控APP主入口
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var notificationManager: NotificationManager
    
    // 默认配置
    companion object {
        // 本地MQTT Broker配置
        const val DEFAULT_MQTT_BROKER = "tcp://192.168.1.100:1883"
        const val DEFAULT_DEVICE_ID = "medicine_box_001"
        
        // 阿里云IoT MQTT配置（示例）
        // 请替换为你的实际设备信息
        const val ALIYUN_PRODUCT_KEY = "your_product_key"
        const val ALIYUN_DEVICE_NAME = "your_device_name"
        const val ALIYUN_DEVICE_SECRET = "your_device_secret"
        const val ALIYUN_REGION = "cn-shanghai"
    }
    
    // 通知权限请求（Android 13+）
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Timber.d("通知权限已授予")
        } else {
            Timber.w("通知权限被拒绝")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化Timber日志
        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }
        
        // 初始化通知管理器
        notificationManager = NotificationManager.getInstance(this)
        viewModel.setNotificationManager(notificationManager)
        
        // 请求通知权限（Android 13+）
        requestNotificationPermission()

        setContent {
            SmartMedicineBoxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmartMedicineBoxApp(viewModel = viewModel)
                }
            }
        }
    }
    
    /**
     * 请求通知权限
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Timber.d("通知权限已存在")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // 可以在这里显示解释为什么需要通知权限
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.disconnect()
    }
}

/**
 * 智能药箱应用导航
 */
@Composable
fun SmartMedicineBoxApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    
    // 收集状态
    val uiState by viewModel.uiState.collectAsState()
    val settingsState by viewModel.settingsState.collectAsState()
    
    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        // 主屏幕
        composable("home") {
            HomeScreen(
                uiState = uiState,
                onRefresh = { viewModel.publishNow() },
                onReset = { viewModel.resetDevice() },
                onSetInterval = { interval -> viewModel.setInterval(interval) },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateToHistory = {
                    navController.navigate("history")
                }
            )
        }
        
        // 设置屏幕
        composable("settings") {
            SettingsScreen(
                settingsState = settingsState,
                onSaveSettings = { broker, deviceId ->
                    viewModel.updateSettings(broker, deviceId)
                },
                onConnect = { viewModel.connect() },
                onDisconnect = { viewModel.disconnect() },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // 历史数据屏幕
        composable("history") {
            HistoryScreen(
                deviceId = settingsState.deviceId,
                historyRepository = viewModel.getHistoryRepository(),
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

/**
 * 主ViewModel
 * 管理UI状态和业务逻辑
 */
class MainViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    
    private val mqttManager = MqttManager()
    
    // 离线检测器 - 15秒超时（3个publish间隔）
    private val offlineDetector = OfflineDetector.getInstance()
    
    // 通知管理器（由Activity注入）
    private var notificationManager: NotificationManager? = null
    
    // 历史数据仓库
    private val historyRepository = HistoryRepository.getInstance(application)
    
    // UI状态
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    // 设置状态
    private val _settingsState = MutableStateFlow(SettingsUiState())
    val settingsState: StateFlow<SettingsUiState> = _settingsState.asStateFlow()
    
    // 上次通知状态（避免重复通知）
    private var lastAlertStates = mutableMapOf<String, Boolean>()
    
    init {
        // 初始化状态
        _uiState.value = HomeUiState(
            deviceId = MainActivity.DEFAULT_DEVICE_ID
        )
        _settingsState.value = SettingsUiState(
            mqttBroker = MainActivity.DEFAULT_MQTT_BROKER,
            deviceId = MainActivity.DEFAULT_DEVICE_ID
        )
        
        // 设置离线检测回调
        setupOfflineDetector()
    }
    
    /**
     * 设置通知管理器（由Activity注入）
     */
    fun setNotificationManager(manager: NotificationManager) {
        this.notificationManager = manager
    }
    
    /**
     * 获取历史数据仓库
     */
    fun getHistoryRepository(): HistoryRepository = historyRepository
    
    /**
     * 设置离线检测器回调
     */
    private fun setupOfflineDetector() {
        offlineDetector.addCallback(object : OfflineDetector.OfflineCallback {
            override fun onDeviceOffline(offlineDuration: Long) {
                val deviceId = _settingsState.value.deviceId
                _uiState.update { 
                    it.copy(
                        isOnline = false,
                        offlineDuration = offlineDuration
                    )
                }
                // 发送离线通知
                notificationManager?.notifyDeviceOffline(deviceId, offlineDuration)
                Timber.w("设备离线检测触发，已离线 ${offlineDuration}ms")
            }

            override fun onDeviceOnline(offlineDuration: Long) {
                val deviceId = _settingsState.value.deviceId
                _uiState.update { it.copy(isOnline = true) }
                // 发送恢复在线通知
                notificationManager?.notifyDeviceOnline(deviceId)
                Timber.i("设备恢复在线，之前离线 ${offlineDuration}ms")
            }

            override fun onHeartbeatUpdated(heartbeatTime: Long) {
                // 心跳更新，可以在这里更新UI显示最后心跳时间
            }
        })
    }
    
    /**
     * 连接到MQTT Broker
     */
    fun connect() {
        val settings = _settingsState.value
        
        _settingsState.update { it.copy(isConnecting = true, errorMessage = null) }
        _uiState.update { it.copy(isConnecting = true) }
        
        // 重置离线检测器
        offlineDetector.reset()
        
        try {
            val clientId = "AndroidApp_${System.currentTimeMillis()}"
            
            mqttManager.connect(
                brokerUrl = settings.mqttBroker,
                clientId = clientId,
                deviceId = settings.deviceId,
                onConnected = {
                    _settingsState.update { it.copy(isConnected = true, isConnecting = false) }
                    _uiState.update { 
                        it.copy(
                            isOnline = true, 
                            isConnecting = false,
                            deviceId = settings.deviceId
                        )
                    }
                    // 启动离线检测
                    offlineDetector.start()
                    Timber.d("MQTT连接成功，离线检测已启动")
                },
                onDisconnected = {
                    _settingsState.update { it.copy(isConnected = false, isConnecting = false) }
                    _uiState.update { 
                        it.copy(
                            isOnline = false, 
                            isConnecting = false
                        )
                    }
                    // 停止离线检测
                    offlineDetector.stop()
                    Timber.d("MQTT连接断开，离线检测已停止")
                },
                onSensorDataReceived = { data ->
                    // 更新心跳（收到传感器数据视为心跳）
                    offlineDetector.updateHeartbeat()
                    handleSensorData(data)
                },
                onStatusReceived = { status ->
                    // 状态消息也更新心跳
                    if (status.isOnline()) {
                        offlineDetector.updateHeartbeat()
                    }
                    handleDeviceStatus(status)
                },
                onCommandResponseReceived = { response ->
                    handleCommandResponse(response)
                },
                onError = { error ->
                    _settingsState.update { 
                        it.copy(
                            isConnecting = false, 
                            errorMessage = error
                        )
                    }
                    _uiState.update { it.copy(isConnecting = false) }
                    Timber.e("MQTT错误: $error")
                }
            )
        } catch (e: Exception) {
            _settingsState.update { 
                it.copy(
                    isConnecting = false, 
                    errorMessage = e.message
                )
            }
            _uiState.update { it.copy(isConnecting = false) }
            Timber.e(e, "连接失败")
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        mqttManager.disconnect()
        offlineDetector.stop()
        _settingsState.update { it.copy(isConnected = false, isConnecting = false) }
        _uiState.update { it.copy(isOnline = false, isConnecting = false) }
    }
    
    /**
     * 更新设置
     */
    fun updateSettings(mqttBroker: String, deviceId: String) {
        _settingsState.update { 
            it.copy(
                mqttBroker = mqttBroker,
                deviceId = deviceId
            )
        }
        _uiState.update { it.copy(deviceId = deviceId) }
    }
    
    /**
     * 处理传感器数据
     */
    private fun handleSensorData(data: SensorData) {
        val deviceId = _settingsState.value.deviceId
        // 检查告警条件
        val alerts = mutableListOf<AlertItem>()
        
        data.environment?.let { env ->
            // 温度告警
            if (env.temperature > 30 || env.temperature < 10) {
                alerts.add(AlertItem(
                    message = "温度异常: ${env.temperature}°C",
                    level = AlertLevel.WARNING
                ))
                // 发送温度异常通知（避免重复通知）
                if (lastAlertStates["temperature"] != true) {
                    notificationManager?.notifyTemperatureAlert(deviceId, env.temperature)
                    lastAlertStates["temperature"] = true
                }
            } else {
                lastAlertStates["temperature"] = false
            }
            
            // 湿度告警
            if (env.humidity > 70 || env.humidity < 30) {
                alerts.add(AlertItem(
                    message = "湿度异常: ${env.humidity}%",
                    level = AlertLevel.WARNING
                ))
                // 发送湿度异常通知（避免重复通知）
                if (lastAlertStates["humidity"] != true) {
                    notificationManager?.notifyHumidityAlert(deviceId, env.humidity)
                    lastAlertStates["humidity"] = true
                }
            } else {
                lastAlertStates["humidity"] = false
            }
        }
        
        // 检查药箱状态告警
        when (data.state) {
            "tilted" -> {
                alerts.add(AlertItem(
                    message = "药箱处于倾斜状态",
                    level = AlertLevel.ERROR
                ))
                // 发送倾斜通知（避免重复通知）
                if (lastAlertStates["tilted"] != true) {
                    notificationManager?.notifyTiltedAlert(deviceId)
                    lastAlertStates["tilted"] = true
                }
            }
            "opened" -> {
                // 药箱打开通知（每次打开都通知）
                notificationManager?.notifyBoxOpened(deviceId)
                lastAlertStates["tilted"] = false
            }
            else -> {
                lastAlertStates["tilted"] = false
            }
        }
        
        _uiState.update { currentState ->
            currentState.copy(
                sensorData = data,
                isOnline = true,
                isConnecting = false,
                lastUpdateTime = java.text.SimpleDateFormat(
                    "HH:mm:ss", 
                    java.util.Locale.getDefault()
                ).format(java.util.Date()),
                alerts = alerts
            )
        }
        
        // 保存到历史数据库
        viewModelScope.launch {
            historyRepository.saveSensorData(deviceId, data)
        }
    }
    
    /**
     * 处理设备状态
     */
    private fun handleDeviceStatus(status: DeviceStatus) {
        _uiState.update { currentState ->
            currentState.copy(
                deviceStatus = status,
                isOnline = status.isOnline()
            )
        }
        
        _settingsState.update { currentState ->
            currentState.copy(
                isConnected = status.isOnline()
            )
        }
    }
    
    /**
     * 处理命令响应
     */
    private fun handleCommandResponse(response: CommandResponse) {
        if (response.isSuccess()) {
            Timber.i("命令执行成功: ${response.cmd}")
            when (response.cmd) {
                "reset" -> {
                    notificationManager?.notifyInfo("设备重置", "设备正在重置，请稍候...")
                }
                "set_interval" -> {
                    notificationManager?.notifyInfo("设置成功", "数据上报间隔已更新")
                }
            }
        } else {
            Timber.w("命令执行失败: ${response.cmd} - ${response.getFullErrorMessage()}")
            notificationManager?.notifyInfo(
                "命令失败",
                "${response.cmd}: ${response.getErrorCodeDescription()}"
            )
        }
    }
    
    /**
     * 发送立即上报命令
     */
    fun publishNow() {
        val deviceId = _settingsState.value.deviceId
        mqttManager.publishCommand(deviceId, "publish_now")
        Timber.d("发送立即上报命令")
    }
    
    /**
     * 发送重置设备命令
     */
    fun resetDevice() {
        val deviceId = _settingsState.value.deviceId
        mqttManager.publishCommand(deviceId, "reset")
        Timber.d("发送重置设备命令")
    }
    
    /**
     * 发送设置上报间隔命令
     */
    fun setInterval(interval: Int) {
        val deviceId = _settingsState.value.deviceId
        mqttManager.publishCommand(deviceId, "set_interval", interval)
        Timber.d("发送设置上报间隔命令: $interval")
    }
    
    override fun onCleared() {
        super.onCleared()
        offlineDetector.destroy()
        disconnect()
    }
}
