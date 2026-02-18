# 智能药箱 Android APP

一款基于MQTT协议与智能硬件通信的Android应用，用于远程监控和控制智能药箱设备。

## 功能特性

- 🔗 MQTT协议通信
- 📊 实时传感器数据监控（温度、湿度、光照）
- 📱 设备状态监测（电量、WiFi信号、在线状态）
- 🎮 远程控制（开关盖、LED控制、闹钟设置）
- 🔄 自动重连机制

## 项目结构

```
SmartMedicineBoxApp/
├── app/
│   ├── src/main/java/com/smartmedicine/
│   │   ├── mqtt/
│   │   │   └── MqttManager.kt          # MQTT核心管理器
│   │   ├── model/
│   │   │   └── SensorData.kt           # 数据模型
│   │   ├── repository/
│   │   │   └── MedicineBoxRepository.kt # 数据仓库
│   │   ├── ui/
│   │   │   ├── MainActivity.kt         # 主界面
│   │   │   └── MedicineBoxViewModel.kt # 视图模型
│   │   └── SmartMedicineApp.kt         # 应用入口
│   └── src/main/res/                   # 界面资源
├── build.gradle                        # 项目级构建配置
└── settings.gradle                     # 项目设置
```

## MQTT主题规范

| 主题 | 类型 | 说明 |
|------|------|------|
| `medicine/{device_id}/sensors` | 订阅 | 传感器数据 |
| `medicine/{device_id}/status` | 订阅 | 设备状态 |
| `medicine/{device_id}/control/response` | 订阅 | 命令响应 |
| `medicine/{device_id}/control` | 发布 | 控制命令 |

## MQTT连接参数

- **协议**: MQTT over TCP
- **端口**: 1883
- **QoS**: 0
- **KeepAlive**: 20秒
- **Clean Session**: true
- **Connection Timeout**: 10秒
- **Automatic Reconnect**: true

## 快速开始

### 1. 配置MQTT Broker

在 `MainActivity.kt` 中修改连接配置：

```kotlin
companion object {
    const val MQTT_BROKER = "tcp://your-mqtt-broker.com:1883"
    const val DEVICE_ID = "medicine_box_001"
}
```

### 2. 构建项目

```bash
./gradlew build
```

### 3. 运行应用

```bash
./gradlew installDebug
```

## 依赖库

- [Eclipse Paho MQTT Client](https://www.eclipse.org/paho/) - MQTT通信
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) - 异步处理
- [Gson](https://github.com/google/gson) - JSON解析
- [Timber](https://github.com/JakeWharton/timber) - 日志管理

## 许可证

MIT License
