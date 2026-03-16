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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smartmedicine.data.model.AlertEvent
import com.smartmedicine.data.model.CommandResponse
import com.smartmedicine.data.model.DeviceStatus
import com.smartmedicine.data.model.EnvironmentLimitsData
import com.smartmedicine.data.model.SensorData
import com.smartmedicine.mqtt.MqttManager
import com.smartmedicine.notification.NotificationManager
import com.smartmedicine.ui.components.AlertLevel
import com.smartmedicine.ui.screens.AlertItem
import com.smartmedicine.ui.screens.HistoryScreen
import com.smartmedicine.ui.screens.HomeScreen
import com.smartmedicine.ui.screens.HomeUiState
import com.smartmedicine.ui.screens.SettingsScreen
import com.smartmedicine.ui.screens.SettingsUiState
import com.smartmedicine.ui.theme.SmartMedicineBoxTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
        const val DEFAULT_MQTT_BROKER = "ssl://jaf12a6c.ala.cn-hangzhou.emqxsl.cn:8883"
        const val DEFAULT_DEVICE_ID = "box001"
        const val DEFAULT_MQTT_USERNAME = "yunmenglin"
        const val DEFAULT_MQTT_PASSWORD = "12345678y"
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
                onSetEnvRated = { temperature, humidity ->
                    viewModel.setEnvironmentRated(temperature, humidity)
                },
                onSetBuzzerEnabled = { enabled ->
                    viewModel.setBuzzerEnabled(enabled)
                },
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
                onSaveSettings = { broker, deviceId, username, password ->
                    viewModel.updateSettings(broker, deviceId, username, password)
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

    // 通知管理器（由Activity注入）
    private var notificationManager: NotificationManager? = null

    // UI状态
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // 设置状态
    private val _settingsState = MutableStateFlow(SettingsUiState())
    val settingsState: StateFlow<SettingsUiState> = _settingsState.asStateFlow()

    // 上次告警状态（用于UI去抖）
    private val lastAlertStates = mutableMapOf<String, Boolean>()

    // 告警历史记录
    private val alertHistory = mutableListOf<AlertRecord>()

    // 最新的设备事件告警（由 alert 主题上报）
    private var latestEventAlert: AlertItem? = null

    // 由设备 drop_alarm_cancelled(stop_push=1) 控制的 APP 推送静默开关
    private var alertPushSuppressed = false

    companion object {
        private const val DEFAULT_RATED_TEMP = 15.0
        private const val DEFAULT_RATED_HUMIDITY = 50.0
        private const val ENV_ABNORMAL_RATIO = 0.30
    }

    init {
        // 初始化状态
        _uiState.value = HomeUiState(
            deviceId = MainActivity.DEFAULT_DEVICE_ID
        )
        _settingsState.value = SettingsUiState(
            mqttBroker = MainActivity.DEFAULT_MQTT_BROKER,
            deviceId = MainActivity.DEFAULT_DEVICE_ID,
            mqttUsername = MainActivity.DEFAULT_MQTT_USERNAME,
            mqttPassword = MainActivity.DEFAULT_MQTT_PASSWORD
        )
    }

    /**
     * 设置通知管理器（由Activity注入）
     */
    fun setNotificationManager(manager: NotificationManager) {
        this.notificationManager = manager
    }

    /**
     * 连接到MQTT Broker
     */
    fun connect() {
        val settings = _settingsState.value

        _settingsState.update { it.copy(isConnecting = true, errorMessage = null) }
        _uiState.update { it.copy(isConnecting = true) }

        try {
            val clientId = "AndroidApp_${System.currentTimeMillis()}"

            mqttManager.connect(
                brokerUrl = settings.mqttBroker,
                clientId = clientId,
                deviceId = settings.deviceId,
                username = settings.mqttUsername,
                password = settings.mqttPassword,
                onConnected = {
                    _settingsState.update { it.copy(isConnected = true, isConnecting = false) }
                    _uiState.update {
                        it.copy(
                            isOnline = true,
                            isConnecting = false,
                            deviceId = settings.deviceId
                        )
                    }
                    Timber.d("MQTT连接成功")
                },
                onDisconnected = {
                    _settingsState.update { it.copy(isConnected = false, isConnecting = false) }
                    _uiState.update {
                        it.copy(
                            isOnline = false,
                            isConnecting = false
                        )
                    }
                    Timber.d("MQTT连接断开")
                },
                onSensorDataReceived = { data ->
                    handleSensorData(data)
                },
                onStatusReceived = { status ->
                    handleDeviceStatus(status)
                },
                onCommandResponseReceived = { response ->
                    handleCommandResponse(response)
                },
                onAlertEventReceived = { event ->
                    handleAlertEvent(event)
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
        _settingsState.update { it.copy(isConnected = false, isConnecting = false) }
        _uiState.update { it.copy(isOnline = false, isConnecting = false) }
    }

    /**
     * 更新设置
     */
    fun updateSettings(
        mqttBroker: String,
        deviceId: String,
        username: String,
        password: String
    ) {
        _settingsState.update {
            it.copy(
                mqttBroker = mqttBroker,
                deviceId = deviceId,
                mqttUsername = username,
                mqttPassword = password
            )
        }
        _uiState.update { it.copy(deviceId = deviceId) }
    }

    /**
     * 处理传感器数据（主要用于UI刷新）
     * 异常推送由 alert 主题驱动，避免重复推送
     */
    private fun handleSensorData(data: SensorData) {
        val alerts = mutableListOf<AlertItem>()
        val deviceId = _settingsState.value.deviceId

        appendEnvironmentAlerts(data, alerts)
        appendBoxStateAlerts(data, deviceId, alerts)
        latestEventAlert?.let { alerts.add(0, it) }

        updateUiState(data, alerts.distinctBy { it.message })
    }

    /**
     * 依据 sensors 里的 alerts/environment_limits 生成UI告警信息
     */
    private fun appendEnvironmentAlerts(data: SensorData, alerts: MutableList<AlertItem>) {
        val env = data.environment ?: return
        val limits = data.environmentLimits ?: buildFallbackLimits()

        val temperatureAbnormal = data.alerts?.isTemperatureAbnormal()
            ?: (env.temperature < limits.temperatureLow || env.temperature > limits.temperatureHigh)
        val humidityAbnormal = data.alerts?.isHumidityAbnormal()
            ?: (env.humidity < limits.humidityLow || env.humidity > limits.humidityHigh)

        if (temperatureAbnormal) {
            alerts.add(
                AlertItem(
                    message = "温度异常: ${String.format("%.1f", env.temperature)}°C（额定 ${String.format("%.1f", limits.temperatureRated)}°C）",
                    level = AlertLevel.WARNING
                )
            )
        }
        if (humidityAbnormal) {
            alerts.add(
                AlertItem(
                    message = "湿度异常: ${String.format("%.1f", env.humidity)}%（额定 ${String.format("%.1f", limits.humidityRated)}%）",
                    level = AlertLevel.WARNING
                )
            )
        }

        lastAlertStates["temperature"] = temperatureAbnormal
        lastAlertStates["humidity"] = humidityAbnormal
    }

    /**
     * 依据药箱状态生成UI提示
     */
    private fun appendBoxStateAlerts(
        data: SensorData,
        deviceId: String,
        alerts: MutableList<AlertItem>
    ) {
        when (data.state) {
            "tilted", "moving" -> {
                alerts.add(
                    AlertItem(
                        message = "药箱处于倾斜或移动状态",
                        level = AlertLevel.ERROR
                    )
                )
            }
            "opened" -> {
                if (lastAlertStates["opened"] != true) {
                    notificationManager?.notifyBoxOpened(deviceId)
                    lastAlertStates["opened"] = true
                }
            }
            else -> {
                lastAlertStates["opened"] = false
            }
        }
    }

    /**
     * 处理设备主动告警事件（alert主题）
     */
    private fun handleAlertEvent(event: AlertEvent) {
        val deviceId = _settingsState.value.deviceId

        when (event.event) {
            AlertEvent.EVENT_ENV_ABNORMAL -> {
                val temperatureAbnormal = event.temperatureAbnormal == 1
                val humidityAbnormal = event.humidityAbnormal == 1

                val details = mutableListOf<String>()
                if (temperatureAbnormal && event.temperature != null) {
                    details.add("温度 ${String.format("%.1f", event.temperature)}°C")
                }
                if (humidityAbnormal && event.humidity != null) {
                    details.add("湿度 ${String.format("%.1f", event.humidity)}%")
                }
                val message = if (details.isEmpty()) {
                    "环境异常，请检查药箱"
                } else {
                    "环境异常: ${details.joinToString("，")}"
                }

                latestEventAlert = AlertItem(
                    message = message,
                    level = AlertLevel.ERROR
                )
                recordAlert("环境异常", message)

                if (!alertPushSuppressed) {
                    notificationManager?.notifyEnvironmentAbnormal(
                        deviceId = deviceId,
                        temperature = event.temperature,
                        humidity = event.humidity,
                        ratedTemperature = event.ratedTemperature,
                        ratedHumidity = event.ratedHumidity,
                        temperatureAbnormal = temperatureAbnormal,
                        humidityAbnormal = humidityAbnormal
                    )
                }

                lastAlertStates["temperature"] = temperatureAbnormal
                lastAlertStates["humidity"] = humidityAbnormal
            }

            AlertEvent.EVENT_ENV_RECOVERED -> {
                latestEventAlert = AlertItem(
                    message = "环境已恢复正常",
                    level = AlertLevel.INFO
                )
                notificationManager?.notifyEnvironmentRecovered(deviceId)
                notificationManager?.clearTemperatureAlert()
                notificationManager?.clearHumidityAlert()
                recordAlert("环境恢复", "温湿度已恢复正常")
                lastAlertStates["temperature"] = false
                lastAlertStates["humidity"] = false
            }

            AlertEvent.EVENT_DROP_DETECTED -> {
                // 新一轮跌落报警开始，重新允许推送
                alertPushSuppressed = false

                val message = "检测到药箱跌落（加速度>${event.thresholdG ?: 6.0}G）"
                latestEventAlert = AlertItem(
                    message = message,
                    level = AlertLevel.ERROR
                )
                recordAlert("跌落告警", message)

                if (!alertPushSuppressed) {
                    notificationManager?.notifyDropDetected(
                        deviceId = deviceId,
                        acceleration = event.accelMagnitude,
                        durationMs = event.durationMs
                    )
                }

                lastAlertStates["drop"] = true
            }

            AlertEvent.EVENT_DROP_ALARM_CANCELLED -> {
                val stopPush = event.stopPush == 1
                if (stopPush) {
                    alertPushSuppressed = true
                }

                latestEventAlert = AlertItem(
                    message = if (stopPush) {
                        "已按KEY2消警，APP异常推送已静默"
                    } else {
                        "跌落报警已取消"
                    },
                    level = AlertLevel.INFO
                )

                notificationManager?.clearTiltedAlert()
                notificationManager?.notifyDropAlarmCancelled(deviceId, stopPush)
                recordAlert("消警事件", "source=${event.source ?: "unknown"}, stop_push=${event.stopPush ?: 0}")
                lastAlertStates["drop"] = false
            }

            else -> {
                Timber.w("收到未知告警事件: ${event.event}")
            }
        }

        // 告警事件到达时，主动更新一次UI告警区
        _uiState.update { current ->
            val merged = buildList {
                latestEventAlert?.let { add(it) }
                addAll(current.alerts)
            }.distinctBy { it.message }
            current.copy(alerts = merged)
        }
    }

    /**
     * 记录告警历史
     */
    private fun recordAlert(type: String, message: String) {
        val record = AlertRecord(
            type = type,
            message = message,
            timestamp = System.currentTimeMillis()
        )
        alertHistory.add(record)
        Timber.d("记录告警: $type - $message")
    }

    /**
     * 获取告警历史
     */
    fun getAlertHistory(): List<AlertRecord> = alertHistory.toList()

    /**
     * 更新UI状态
     */
    private fun updateUiState(data: SensorData, alerts: List<AlertItem>) {
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
                "set_env_rated" -> {
                    notificationManager?.notifyInfo("设置成功", "温湿度额定值已更新")
                }
                "set_buzzer_enable" -> {
                    notificationManager?.notifyInfo("设置成功", "蜂鸣器开关已更新")
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

    /**
     * 发送设置温湿度额定值命令
     */
    fun setEnvironmentRated(temperature: Double, humidity: Double) {
        val deviceId = _settingsState.value.deviceId
        val ok = mqttManager.publishCommand(
            deviceId = deviceId,
            cmd = "set_env_rated",
            extraParams = mapOf(
                "temperature" to temperature,
                "humidity" to humidity
            )
        )
        if (ok) {
            notificationManager?.notifyInfo(
                "命令已发送",
                "已下发额定值: ${String.format("%.1f", temperature)}°C / ${String.format("%.1f", humidity)}%"
            )
        }
        Timber.d("发送设置额定环境命令: temperature=$temperature, humidity=$humidity")
    }

    /**
     * 设置蜂鸣器开关
     */
    fun setBuzzerEnabled(enabled: Boolean) {
        val deviceId = _settingsState.value.deviceId
        val ok = mqttManager.publishCommand(
            deviceId = deviceId,
            cmd = "set_buzzer_enable",
            value = if (enabled) 1 else 0
        )
        if (ok) {
            _uiState.update { it.copy(buzzerEnabled = enabled) }
            notificationManager?.notifyInfo(
                "命令已发送",
                if (enabled) "已请求开启蜂鸣器" else "已请求关闭蜂鸣器"
            )
        }
        Timber.d("发送蜂鸣器开关命令: $enabled")
    }

    private fun buildFallbackLimits(): EnvironmentLimitsData {
        val tempLow = DEFAULT_RATED_TEMP * (1.0 - ENV_ABNORMAL_RATIO)
        val tempHigh = DEFAULT_RATED_TEMP * (1.0 + ENV_ABNORMAL_RATIO)
        val humidityLow = DEFAULT_RATED_HUMIDITY * (1.0 - ENV_ABNORMAL_RATIO)
        val humidityHigh = DEFAULT_RATED_HUMIDITY * (1.0 + ENV_ABNORMAL_RATIO)
        return EnvironmentLimitsData(
            temperatureRated = DEFAULT_RATED_TEMP,
            temperatureLow = tempLow,
            temperatureHigh = tempHigh,
            humidityRated = DEFAULT_RATED_HUMIDITY,
            humidityLow = humidityLow,
            humidityHigh = humidityHigh
        )
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}

/**
 * 告警记录数据类
 * 用于存储历史告警信息
 */
data class AlertRecord(
    val type: String,        // 告警类型（如"温度异常"、"药箱异常"）
    val message: String,     // 告警详情
    val timestamp: Long      // 告警时间戳
) {
    /**
     * 获取格式化的时间字符串
     */
    fun getFormattedTime(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}
