# 智能药箱APP - 开发文档

## 📋 项目概述

智能药箱Android APP是一个基于MQTT协议与智能硬件通信的应用，用于远程监控和控制智能药箱设备。

### 技术栈
- **开发语言**: Kotlin
- **UI框架**: Jetpack Compose + Material Design 3
- **架构模式**: MVVM (Model-View-ViewModel)
- **数据存储**: Room Database
- **异步处理**: Kotlin Coroutines + Flow
- **网络通信**: MQTT (Eclipse Paho)
- **依赖注入**: 手动单例模式

---

## 🏗️ 架构设计

### 项目结构

```
app/src/main/java/com/smartmedicine/
├── SmartMedicineApp.kt              # Application入口
├── mqtt/
│   └── MqttManager.kt               # MQTT连接管理
├── data/
│   ├── model/                       # 数据模型
│   │   ├── SensorData.kt           # 传感器数据
│   │   ├── DeviceStatus.kt         # 设备状态
│   │   ├── CommandResponse.kt      # 命令响应
│   │   ├── EnvironmentData.kt      # 环境数据
│   │   └── MotionData.kt           # 运动数据
│   └── db/                          # Room数据库
│       ├── AppDatabase.kt          # 数据库实例
│       ├── SensorDataEntity.kt     # 数据实体
│       └── SensorDataDao.kt        # 数据访问对象
├── repository/
│   └── HistoryRepository.kt         # 历史数据仓库
├── notification/
│   └── NotificationManager.kt       # 系统通知管理
├── box/                             # 业务逻辑模块
│   ├── manager/                     # 管理器
│   │   ├── OfflineDetector.kt      # 离线检测
│   │   ├── AlertManager.kt         # 告警管理
│   │   └── CommandManager.kt       # 命令管理
│   ├── service/                     # 服务
│   │   └── MedicineBoxMqttService.kt
│   └── data/model/                  # 扩展数据模型
└── ui/                              # UI层
    ├── MainActivity.kt              # 主Activity和ViewModel
    ├── screens/                     # 屏幕页面
    │   ├── HomeScreen.kt           # 主界面
    │   ├── SettingsScreen.kt       # 设置界面
    │   └── HistoryScreen.kt        # 历史数据界面
    ├── components/                  # 可复用组件
    │   ├── EnvironmentCard.kt      # 环境数据卡片
    │   ├── StatusIndicator.kt      # 状态指示器
    │   ├── ControlButtons.kt       # 控制按钮
    │   └── DataCharts.kt           # 数据图表
    ├── theme/                       # 主题配置
    │   ├── Color.kt
    │   ├── Theme.kt
    │   └── Type.kt
    └── preview/                     # 预览文件
        └── Previews.kt
```

### MVVM架构图

```
┌─────────────────────────────────────────────────────────────┐
│                          UI Layer                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ HomeScreen   │  │SettingsScreen│  │HistoryScreen │      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
└─────────┼─────────────────┼─────────────────┼──────────────┘
          │                 │                 │
          └─────────────────┼─────────────────┘
                            │
┌───────────────────────────▼───────────────────────────────┐
│                      ViewModel Layer                      │
│                    MainViewModel                          │
│  ┌─────────────────────────────────────────────────────┐ │
│  │  - uiState: StateFlow<HomeUiState>                 │ │
│  │  - settingsState: StateFlow<SettingsUiState>       │ │
│  │  - handleSensorData()                              │ │
│  │  - handleDeviceStatus()                            │ │
│  │  - connect/disconnect/publishNow/resetDevice       │ │
│  └─────────────────────────────────────────────────────┘ │
└───────────────────────────┬───────────────────────────────┘
                            │
          ┌─────────────────┼─────────────────┐
          │                 │                 │
┌─────────▼──────┐ ┌───────▼────────┐ ┌──────▼─────────┐
│   Repository   │ │ MQTT Manager   │ │ Notification   │
│    Layer       │ │                │ │    Manager     │
│ HistoryRepo    │ │ MqttManager    │ │ NotificationMgr│
└────────┬───────┘ └───────┬────────┘ └──────┬─────────┘
         │                 │                  │
┌────────▼─────────────────▼──────────────────▼─────────┐
│                    Data Layer                         │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────┐ │
│  │  Room DB     │  │MQTT Service  │  │ System      │ │
│  │SensorDataDao │  │Paho Client   │  │ Notification│ │
│  └──────────────┘  └──────────────┘  └─────────────┘ │
└───────────────────────────────────────────────────────┘
```

---

## 📡 MQTT通信协议

### 连接配置

```kotlin
val options = MqttConnectOptions().apply {
    connectionTimeout = 10      // 连接超时10秒
    keepAliveInterval = 20      // 心跳间隔20秒
    isCleanSession = true       // 干净会话
    isAutomaticReconnect = true // 自动重连
}
```

### 主题定义

| 主题 | 方向 | 说明 | QoS |
|------|------|------|-----|
| `medicine/{device_id}/sensors` | 订阅 | 传感器数据 | 0 |
| `medicine/{device_id}/status` | 订阅 | 设备状态 | 1 |
| `medicine/{device_id}/control` | 发布 | 控制命令 | 1 |
| `medicine/{device_id}/control/response` | 订阅 | 命令响应 | 1 |

### 命令列表

| 命令 | 参数 | 说明 |
|------|------|------|
| `reset` | 无 | 重置设备 |
| `publish_now` | 无 | 立即上报数据 |
| `set_interval` | value: Int | 设置上报间隔（秒） |

### 数据格式示例

```json
{
    "timestamp": 1234567890,
    "device_id": "medicine_box_001",
    "state": "closed",
    "environment": {
        "temperature": 25.30,
        "humidity": 55.50,
        "pressure": 101325.00,
        "altitude": 0.00
    },
    "motion": {
        "accel_x": 0.015,
        "accel_y": -0.008,
        "accel_z": 0.995,
        "gyro_x": 0.50,
        "gyro_y": -0.30,
        "gyro_z": 0.10,
        "pitch": 2.15,
        "roll": -1.02,
        "vibration": 0.005
    },
    "valid": 1
}
```

---

## 💾 数据存储

### Room数据库

**实体类**: `SensorDataEntity`
- 存储所有传感器历史数据
- 自动清理超过30天的数据
- 最大存储10000条记录

**DAO接口**: `SensorDataDao`
- 支持按设备ID查询
- 支持时间范围查询
- 支持统计数据查询（平均值、最小/最大值）

### 数据存储策略

```kotlin
// 保存传感器数据
historyRepository.saveSensorData(deviceId, sensorData)

// 获取最近24小时数据
historyRepository.getHistoryByTimeRange(deviceId, 24)

// 获取统计数据
historyRepository.getTemperatureStats(deviceId, 24)
```

---

## 🔔 通知系统

### 通知渠道

| 渠道ID | 名称 | 优先级 | 说明 |
|--------|------|--------|------|
| `medicine_box_alerts` | 设备告警 | HIGH | 温度/湿度异常、离线 |
| `medicine_box_status` | 设备状态 | DEFAULT | 在线/离线状态 |
| `medicine_box_general` | 一般通知 | LOW | 药箱打开等 |

### 告警触发条件

| 条件 | 级别 | 通知方式 |
|------|------|----------|
| 设备离线15秒+ | Critical | 振动+声音 |
| 温度 > 30°C 或 < 10°C | Warning | 振动+声音 |
| 湿度 > 70% 或 < 30% | Warning | 振动+声音 |
| 药箱倾斜 | Warning | 振动+声音 |
| 药箱打开 | Info | 静默通知 |

---

## 🔍 离线检测机制

### 检测逻辑

```kotlin
// 设备每5秒发布一次数据
// 15秒（3个间隔）未收到数据判定为离线
const val OFFLINE_TIMEOUT_MS = 15000L

// 检查间隔1秒
const val CHECK_INTERVAL_MS = 1000L
```

### 流程图

```
收到传感器数据 ──→ 更新心跳时间 ──→ 重置离线状态
      ↑                                    │
      └──────── 5秒间隔 ←──────────────────┘
                        
超时检测(1秒间隔) ──→ 检查当前时间 - 最后心跳 > 15秒?
                              │
                    是 ──→ 触发离线回调 ──→ 发送通知
                    否 ──→ 继续检测
```

---

## 🎨 UI组件说明

### 状态指示器

| 状态 | 颜色 | 说明 |
|------|------|------|
| 在线 | 🟢 绿色 | 设备正常连接 |
| 离线 | 🔴 红色 | 设备离线 |
| 连接中 | 🟡 黄色 | 正在建立连接 |

### 药箱状态

| 状态 | 颜色 | 说明 |
|------|------|------|
| closed | 🟢 绿色 | 已关闭 |
| opened | 🔵 蓝色 | 已打开 |
| moving | 🟠 橙色 | 移动中 |
| tilted | 🔴 红色 | 倾斜状态 |

---

## 🔧 开发环境配置

### 系统要求
- Android Studio Hedgehog (2023.1.1) 或更新版本
- JDK 17+
- Android SDK 34
- Kotlin 1.9.0+

### 依赖配置

```gradle
// build.gradle (Project)
plugins {
    id 'com.android.application' version '8.1.0'
    id 'org.jetbrains.kotlin.android' version '1.9.0'
    id 'org.jetbrains.kotlin.kapt'
}

// build.gradle (App)
dependencies {
    // Compose BOM
    implementation platform('androidx.compose:compose-bom:2024.02.00')
    implementation 'androidx.compose.material3:material3'
    
    // Navigation
    implementation 'androidx.navigation:navigation-compose:2.7.7'
    
    // MQTT
    implementation 'org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5'
    
    // Room
    implementation "androidx.room:room-runtime:2.6.1"
    implementation "androidx.room:room-ktx:2.6.1"
    kapt "androidx.room:room-compiler:2.6.1"
    
    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    
    // Gson
    implementation 'com.google.code.gson:gson:2.10.1'
    
    // Timber (日志)
    implementation 'com.jakewharton.timber:timber:5.0.1'
}
```

---

## 🚀 构建和运行

### 构建APK

```bash
# 调试版本
./gradlew assembleDebug

# 发布版本
./gradlew assembleRelease
```

### 运行测试

```bash
# 单元测试
./gradlew test

# 仪器测试
./gradlew connectedAndroidTest
```

---

## 📝 代码规范

### Kotlin代码风格
- 使用CamelCase命名法
- 类名首字母大写，函数/变量首字母小写
- 常量使用全大写下划线分隔
- 可空类型使用`?`标记，避免使用`!!`

### 架构规范
- ViewModel负责业务逻辑，不直接操作UI
- Repository负责数据源管理
- UI层只观察和显示状态
- 使用StateFlow进行状态管理

### 异常处理
```kotlin
try {
    // 可能抛出异常的代码
} catch (e: MqttException) {
    Timber.e(e, "MQTT操作失败")
    // 用户友好的错误提示
} catch (e: Exception) {
    Timber.e(e, "未知错误")
}
```

---

## 🔐 安全和隐私

### 注意事项
1. 不要在代码中硬编码敏感信息（如MQTT密码）
2. 使用`android:usesCleartextTraffic="true"`仅用于开发环境
3. 生产环境应使用SSL/TLS连接MQTT
4. 用户数据存储在本地，不上传云端

---

## 📚 参考资料

- [MQTT协议规范](http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html)
- [Jetpack Compose文档](https://developer.android.com/jetpack/compose)
- [Room数据库指南](https://developer.android.com/training/data-storage/room)
- [Eclipse Paho MQTT客户端](https://www.eclipse.org/paho/)

---

## 👥 贡献指南

1. Fork项目到个人仓库
2. 创建功能分支 `git checkout -b feature/xxx`
3. 提交更改 `git commit -m "feat: xxx"`
4. 推送到分支 `git push origin feature/xxx`
5. 创建Pull Request

---

## 📄 许可证

MIT License

Copyright (c) 2024 SmartMedicineBox Team
