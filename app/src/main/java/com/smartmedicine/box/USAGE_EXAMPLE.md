# 智能药箱APP - 设备控制与告警功能使用说明

## 模块结构

```
com.smartmedicine.box/
├── control/
│   └── CommandManager.kt       # 设备控制命令管理
├── alert/
│   ├── AlertData.kt            # 告警数据模型
│   └── AlertManager.kt         # 告警检测管理
├── monitor/
│   └── ConnectionMonitor.kt    # 离线检测监控
├── log/
│   └── AlertLogManager.kt      # 告警日志管理
└── service/
    └── AlertNotificationService.kt  # 告警通知服务
```

## 1. 设备控制模块 (CommandManager)

### 功能
- 发送控制命令（reset, publishNow, setInterval）
- 监听命令响应
- 处理错误码

### 使用示例

```kotlin
// 初始化
val commandManager = CommandManager(mqttManager)
commandManager.initialize(deviceId)

// 设置命令响应监听器
commandManager.setOnCommandResponseListener { response ->
    when (response.result) {
        ResultType.SUCCESS -> {
            Log.d("Command", "命令执行成功: ${response.cmd}")
        }
        ResultType.ERROR -> {
            val errorMsg = commandManager.getFullErrorMessage(
                response.errorCode ?: -1, 
                response.errorMsg
            )
            Log.e("Command", "命令执行失败: $errorMsg")
        }
    }
}

// 发送命令
commandManager.sendResetCommand()           // 重置设备
commandManager.sendPublishNowCommand()      // 立即上报数据
commandManager.sendSetIntervalCommand(10)   // 设置10秒上报间隔

// 释放资源
commandManager.release()
```

### 错误码处理

| 错误码 | 说明 |
|--------|------|
| 400 | 参数错误 - 命令参数无效 |
| 401 | 权限错误 - 未授权的操作 |
| 500 | 设备错误 - 设备执行失败 |
| 503 | 服务不可用 - 设备当前不可用 |

## 2. 告警管理模块 (AlertManager)

### 功能
- 检测各类告警条件
- 管理告警监听器
- 维护告警历史

### 使用示例

```kotlin
// 获取实例
val alertManager = AlertManager.getInstance(context)

// 设置告警监听器
alertManager.setOnAlertListener { alert ->
    when (alert.level) {
        AlertLevel.CRITICAL -> {
            // 严重告警 - 推送+显示
            showCriticalAlert(alert)
        }
        AlertLevel.WARNING -> {
            // 警告 - 通知
            showWarning(alert)
        }
        AlertLevel.NOTICE -> {
            // 注意 - 可选通知
            Log.d("Alert", alert.message)
        }
        AlertLevel.INFO -> {
            // 信息 - 记录日志
            Log.i("Alert", alert.message)
        }
    }
}

// 检查传感器数据告警
alertManager.checkAlerts(sensorData)

// 检查设备离线状态
alertManager.checkOfflineStatus(isOffline, offlineDurationMs)

// 获取告警历史
val history = alertManager.getAlertHistory()
val criticalAlerts = alertManager.getAlertHistoryByLevel(AlertLevel.CRITICAL)

// 清空历史
alertManager.clearAlertHistory()

// 更新阈值配置
alertManager.updateThresholdConfig(
    AlertThreshold(
        temperatureMax = 35f,
        temperatureMin = 5f,
        humidityMax = 80f,
        humidityMin = 20f
    )
)
```

### 告警条件

| 条件 | 级别 | 动作 |
|------|------|------|
| 温度 > 30°C 或 < 10°C | WARNING | 通知 |
| 湿度 > 70% 或 < 30% | WARNING | 通知 |
| 药箱被打开 | INFO | 记录日志 |
| 药箱移动中 | NOTICE | 可选通知 |
| 设备离线 | CRITICAL | 推送+显示 |

## 3. 离线检测模块 (ConnectionMonitor)

### 功能
- 心跳机制监控
- 15秒超时检测
- 在线状态回调

### 使用示例

```kotlin
// 获取实例
val connectionMonitor = ConnectionMonitor.getInstance()

// 设置状态变化监听器
connectionMonitor.setOnConnectionChangeListener { isOffline ->
    if (isOffline) {
        // 设备离线
        val duration = connectionMonitor.getOfflineDuration()
        Log.e("Connection", "设备离线，已 ${duration}ms 未收到数据")
    } else {
        // 设备上线
        Log.i("Connection", "设备恢复在线")
    }
}

// 启动监控
connectionMonitor.startMonitoring()

// 每次收到传感器数据时更新心跳时间
// 通常在MQTT回调中调用
mqttManager.setOnSensorDataReceived { sensorData ->
    connectionMonitor.updateLastDataTime()
    // ... 其他处理
}

// 检查当前状态
val isOffline = connectionMonitor.isOffline()
val elapsedTime = connectionMonitor.getElapsedTime()
val remainingTime = connectionMonitor.getRemainingTimeout()

// 停止监控
connectionMonitor.stopMonitoring()

// 释放资源
connectionMonitor.release()
```

### 关键参数

- **心跳间隔**：设备每5秒发布一次数据
- **超时时间**：15秒未收到数据判定为离线
- **检测间隔**：每秒检查一次状态

## 4. 日志管理模块 (AlertLogManager)

### 功能
- 本地存储告警历史
- 查询、筛选告警
- 导出导入功能

### 使用示例

```kotlin
// 获取实例
val logManager = AlertLogManager.getInstance(context)

// 保存单条告警
val alert = AlertData(
    type = AlertType.TEMPERATURE_HIGH,
    level = AlertLevel.WARNING,
    message = "温度过高警告"
)
logManager.saveAlert(alert)

// 批量保存
logManager.saveAlerts(alertList)

// 查询所有告警
val allAlerts = logManager.getAllAlerts()

// 按类型查询
val tempAlerts = logManager.getAlertsByType(AlertType.TEMPERATURE_HIGH)

// 按级别查询
val criticalAlerts = logManager.getAlertsByLevel(AlertLevel.CRITICAL)

// 按时间范围查询
val recentAlerts = logManager.getAlertsByTimeRange(
    startTime = System.currentTimeMillis() - 24 * 60 * 60 * 1000, // 24小时前
    endTime = System.currentTimeMillis()
)

// 获取最近的N条
val recent10 = logManager.getRecentAlerts(10)

// 获取统计信息
val stats = logManager.getAlertStatistics()
Log.d("Stats", "总告警数: ${stats.totalCount}")
Log.d("Stats", "严重告警: ${stats.criticalCount}")

// 导出日志（JSON格式）
val jsonExport = logManager.exportToJson()
// 保存到文件或分享

// 导入日志
val importedCount = logManager.importFromJson(jsonString)

// 清空日志
logManager.clearLogs()

// 删除单条记录
logManager.deleteAlert(alertId)

// 检查存储状态
val count = logManager.getLogCount()
val isFull = logManager.isStorageFull()
```

## 5. 通知服务 (AlertNotificationService)

### 功能
- 前台服务保活
- 系统通知推送
- 不同级别不同样式

### 使用示例

### AndroidManifest.xml 配置

```xml
<service 
    android:name=".service.AlertNotificationService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="dataSync" />
```

### 代码中使用

```kotlin
// 启动服务
AlertNotificationService.start(context)

// 显示告警通知
val alert = AlertData(
    type = AlertType.DEVICE_OFFLINE,
    level = AlertLevel.CRITICAL,
    message = "设备已离线"
)
AlertNotificationService.showAlertNotification(context, alert)

// 或使用便捷方法
AlertNotificationHelper.showNotification(context, alert)

// 取消特定类型通知
AlertNotificationHelper.cancelNotification(context, AlertType.DEVICE_OFFLINE)

// 取消所有通知
AlertNotificationHelper.cancelAllNotifications(context)

// 停止服务
AlertNotificationService.stop(context)
```

### 通知渠道

| 渠道 | 级别 | 说明 |
|------|------|------|
| 严重告警 | IMPORTANCE_HIGH | 声音+振动+LED |
| 警告告警 | IMPORTANCE_HIGH | 声音 |
| 注意告警 | IMPORTANCE_DEFAULT | 默认 |
| 信息通知 | IMPORTANCE_LOW | 静默 |

## 6. 完整集成示例

```kotlin
class MainActivity : AppCompatActivity() {

    private lateinit var mqttManager: MqttManager
    private lateinit var commandManager: CommandManager
    private lateinit var alertManager: AlertManager
    private lateinit var connectionMonitor: ConnectionMonitor
    private lateinit var logManager: AlertLogManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化各模块
        initModules()
        
        // 连接MQTT
        connectMqtt()
        
        // 启动监控
        startMonitoring()
    }

    private fun initModules() {
        // MQTT管理器
        mqttManager = MqttManager()
        
        // 命令管理器
        commandManager = CommandManager(mqttManager)
        commandManager.initialize("device_001")
        commandManager.setOnCommandResponseListener { response ->
            handleCommandResponse(response)
        }
        
        // 告警管理器
        alertManager = AlertManager.getInstance(this)
        alertManager.setOnAlertListener { alert ->
            handleAlert(alert)
        }
        
        // 连接监控器
        connectionMonitor = ConnectionMonitor.getInstance()
        connectionMonitor.setOnConnectionChangeListener { isOffline ->
            handleConnectionChange(isOffline)
        }
        
        // 日志管理器
        logManager = AlertLogManager.getInstance(this)
    }

    private fun connectMqtt() {
        mqttManager.connect(
            brokerUrl = "tcp://192.168.1.100:1883",
            clientId = "android_client_001",
            deviceId = "device_001",
            onConnected = {
                Log.d("MQTT", "已连接")
            },
            onSensorDataReceived = { sensorData ->
                // 更新心跳时间
                connectionMonitor.updateLastDataTime()
                
                // 检查告警
                alertManager.checkAlerts(sensorData)
            },
            onError = { error ->
                Log.e("MQTT", "错误: $error")
            }
        )
    }

    private fun startMonitoring() {
        // 启动离线检测
        connectionMonitor.startMonitoring()
        
        // 启动通知服务
        AlertNotificationService.start(this)
    }

    private fun handleCommandResponse(response: CommandResponse) {
        when (response.result) {
            ResultType.SUCCESS -> {
                showToast("命令执行成功: ${response.cmd}")
            }
            ResultType.ERROR -> {
                val errorMsg = commandManager.getFullErrorMessage(
                    response.errorCode ?: -1
                )
                showToast("命令失败: $errorMsg")
            }
        }
    }

    private fun handleAlert(alert: AlertData) {
        // 保存到日志
        logManager.saveAlert(alert)
        
        // 显示系统通知
        AlertNotificationHelper.showNotification(this, alert)
        
        // 根据级别处理
        when (alert.level) {
            AlertLevel.CRITICAL -> showCriticalDialog(alert)
            AlertLevel.WARNING -> showWarningSnackbar(alert)
            else -> {}
        }
    }

    private fun handleConnectionChange(isOffline: Boolean) {
        // 通知告警管理器
        alertManager.checkOfflineStatus(
            isOffline, 
            connectionMonitor.getOfflineDuration()
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // 释放资源
        commandManager.release()
        connectionMonitor.release()
        alertManager.release()
        mqttManager.release()
        
        // 停止服务
        AlertNotificationService.stop(this)
    }
}
```

## 注意事项

1. **权限要求**
   - `INTERNET` - MQTT通信
   - `FOREGROUND_SERVICE` - 前台服务
   - `POST_NOTIFICATIONS` (Android 13+) - 通知权限

2. **生命周期管理**
   - 在Activity/Fragment销毁时释放资源
   - 使用ApplicationContext避免内存泄漏

3. **线程安全**
   - 所有回调都在主线程执行
   - 耗时操作在后台线程执行

4. **存储限制**
   - AlertLogManager最多保存200条记录
   - 超出时自动删除旧记录
